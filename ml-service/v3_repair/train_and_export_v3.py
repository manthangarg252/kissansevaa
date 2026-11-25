"""
Tasks 2 & 3: Retrain classifiers strictly on group-repaired splits, evaluate calibration
and held-out test metrics, export the actual winning estimator with parity test vectors,
pure-NumPy weights, metadata, and SHA256 manifest.
"""
import hashlib
import json
import pickle
import platform
import time
from pathlib import Path

import faiss
import numpy as np
import pandas as pd
import sklearn
from joblib import parallel_backend
from sklearn.calibration import CalibratedClassifierCV
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
    log_loss,
)
from sklearn.svm import LinearSVC


ROOT = Path(__file__).resolve().parents[2]
OLD_CAND_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm'
V1_PROD_DIR = ROOT / 'ml-service' / 'model_assets'
REPAIRED_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm_repaired'
ASSETS_OUT = REPAIRED_DIR / 'model_assets'
EVAL_OUT = REPAIRED_DIR / 'evaluation'


def compute_ece_and_brier(y_true: np.ndarray, probs: np.ndarray, n_bins: int = 15):
    """Compute Expected Calibration Error (ECE), MCE, multiclass Brier score, and reliability bins."""
    probs = np.asarray(probs, dtype=np.float64)
    probs = np.clip(probs, 1e-12, 1.0)
    probs = probs / probs.sum(axis=1, keepdims=True)

    conf = probs.max(axis=1)
    preds = probs.argmax(axis=1)
    correct = (preds == y_true).astype(np.float64)

    n = len(y_true)
    bin_edges = np.linspace(0.0, 1.0, n_bins + 1)
    ece = 0.0
    mce = 0.0
    bins_info = []
    for b in range(n_bins):
        lo, hi = bin_edges[b], bin_edges[b + 1]
        if b == n_bins - 1:
            mask = (conf >= lo) & (conf <= hi)
        else:
            mask = (conf >= lo) & (conf < hi)
        cnt = int(mask.sum())
        if cnt == 0:
            bins_info.append({'bin_lo': round(lo, 3), 'bin_hi': round(hi, 3), 'count': 0, 'avg_conf': None, 'avg_acc': None, 'gap': None})
            continue
        avg_conf = float(conf[mask].mean())
        avg_acc = float(correct[mask].mean())
        gap = abs(avg_acc - avg_conf)
        ece += (cnt / n) * gap
        mce = max(mce, gap)
        bins_info.append({
            'bin_lo': round(lo, 3),
            'bin_hi': round(hi, 3),
            'count': cnt,
            'avg_conf': round(avg_conf, 5),
            'avg_acc': round(avg_acc, 5),
            'gap': round(gap, 5),
        })

    num_classes = probs.shape[1]
    y_oh = np.zeros_like(probs)
    y_oh[np.arange(n), y_true] = 1.0
    brier = float(np.mean(np.sum((probs - y_oh) ** 2, axis=1)))
    nll = float(log_loss(y_true, probs, labels=np.arange(num_classes)))
    return {
        'ece_15bin': round(float(ece), 5),
        'mce_15bin': round(float(mce), 5),
        'brier_multiclass': round(brier, 5),
        'log_loss': round(nll, 5),
        'mean_conf_correct': round(float(conf[correct == 1].mean()), 5) if np.any(correct == 1) else None,
        'mean_conf_wrong': round(float(conf[correct == 0].mean()), 5) if np.any(correct == 0) else None,
    }, bins_info


def score_probs(name: str, y_true: np.ndarray, probs: np.ndarray, latency_ms: float, artifact_mb: float) -> dict:
    preds = np.argmax(probs, axis=1)
    top5 = np.argsort(-probs, axis=1)[:, :5]
    top3_hit = float(np.mean([y_true[i] in top5[i, :3] for i in range(len(y_true))]))
    top5_hit = float(np.mean([y_true[i] in top5[i, :5] for i in range(len(y_true))]))
    cal, _ = compute_ece_and_brier(y_true, probs)
    return {
        'model_name': name,
        'top1_acc': round(float(accuracy_score(y_true, preds)), 5),
        'macro_f1': round(float(f1_score(y_true, preds, average='macro', zero_division=0)), 5),
        'top3_recall': round(top3_hit, 5),
        'top5_recall': round(top5_hit, 5),
        'ece_15bin': cal['ece_15bin'],
        'brier_multiclass': cal['brier_multiclass'],
        'log_loss': cal['log_loss'],
        'latency_ms_per_query': round(float(latency_ms), 4),
        'artifact_size_mb': round(float(artifact_mb), 3),
    }


def knn_weighted_probs(index: faiss.IndexFlatIP, y_gal: np.ndarray, X_q: np.ndarray, num_classes: int, k: int = 3) -> np.ndarray:
    sims, ids = index.search(X_q, k)
    out = np.zeros((len(X_q), num_classes), dtype=np.float64)
    for r, (ss, ii) in enumerate(zip(sims, ids)):
        nbr_y = y_gal[ii]
        w = np.exp(10.0 * (ss - ss.max()))
        row = np.bincount(nbr_y, weights=w, minlength=num_classes).astype(np.float64)
        out[r] = row / max(row.sum(), 1e-12)
    return out


def numpy_calibrated_svm_proba(X: np.ndarray, folds_coefs: np.ndarray, folds_intercepts: np.ndarray, folds_a: np.ndarray, folds_b: np.ndarray) -> np.ndarray:
    """Exact pure-NumPy mirror of CalibratedClassifierCV(method='sigmoid').predict_proba."""
    X64 = np.asarray(X, dtype=np.float64)
    n, n_classes = X64.shape[0], folds_intercepts.shape[1]
    fold_sum = np.zeros((n, n_classes), dtype=np.float64)
    n_folds = folds_coefs.shape[0]
    for f in range(n_folds):
        df = X64 @ folds_coefs[f].T + folds_intercepts[f]
        z = np.clip(folds_a[f][None, :] * df + folds_b[f][None, :], -500.0, 500.0)
        p = 1.0 / (1.0 + np.exp(z))
        p = p / np.maximum(p.sum(axis=1, keepdims=True), 1e-12)
        fold_sum += p
    return fold_sum / n_folds


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    ASSETS_OUT.mkdir(parents=True, exist_ok=True)
    EVAL_OUT.mkdir(parents=True, exist_ok=True)

    classes = json.loads((OLD_CAND_DIR / 'model_assets' / 'classes.json').read_text(encoding='utf-8'))
    v1_classes = json.loads((V1_PROD_DIR / 'classes.json').read_text(encoding='utf-8'))
    assert classes == v1_classes and len(classes) == 67

    v1_meta = json.loads((V1_PROD_DIR / 'model_meta.json').read_text(encoding='utf-8'))

    splits_df = pd.read_csv(REPAIRED_DIR / 'splits_local_65703_repaired.csv')
    idx_all = faiss.read_index(str(OLD_CAND_DIR / 'model_assets' / 'crop_index_v3_full.faiss'))
    labels_all = np.load(OLD_CAND_DIR / 'model_assets' / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    X_all = idx_all.reconstruct_n(0, idx_all.ntotal).astype('float32')

    assert len(splits_df) == len(X_all) == len(labels_all) == 65703
    assert np.array_equal(splits_df['faiss_row_id'].to_numpy(), np.arange(65703))
    assert np.array_equal(splits_df['class_id'].to_numpy(dtype=np.int32), labels_all)

    tr_mask = (splits_df['local_split'] == 'train').to_numpy()
    va_mask = (splits_df['local_split'] == 'val').to_numpy()
    te_mask = (splits_df['local_split'] == 'test').to_numpy()

    X_train, y_train = X_all[tr_mask], labels_all[tr_mask]
    X_val, y_val = X_all[va_mask], labels_all[va_mask]
    X_test, y_test = X_all[te_mask], labels_all[te_mask]

    # Build strict train-only repaired FAISS index
    repaired_index = faiss.IndexFlatIP(768)
    repaired_index.add(X_train)
    repaired_faiss_path = ASSETS_OUT / 'crop_index_v3_repaired.faiss'
    faiss.write_index(repaired_index, str(repaired_faiss_path))
    np.save(ASSETS_OUT / 'train_labels.npy', y_train)
    index_mb = repaired_faiss_path.stat().st_size / (1024 ** 2)

    # Class prototypes normalized (fit on train only)
    centroids = np.vstack([X_train[y_train == c].mean(axis=0) for c in range(67)]).astype('float32')
    faiss.normalize_L2(centroids)

    # Fit candidate classifiers strictly on repaired X_train, y_train
    print(f'Training on repaired local split -> Train={len(X_train)}, Val={len(X_val)}, Test={len(X_test)}', flush=True)
    fitted_models = {}

    t0 = time.perf_counter()
    with parallel_backend('threading', n_jobs=4):
        svm_c1 = CalibratedClassifierCV(
            LinearSVC(C=1.0, class_weight='balanced', dual=True, random_state=42),
            method='sigmoid',
            cv=3,
        ).fit(X_train, y_train)
    t_fit_c1 = round(time.perf_counter() - t0, 2)
    print(f'Fitted svm_linear_cal_C1.0 in {t_fit_c1}s', flush=True)
    fitted_models['svm_linear_cal_C1.0'] = svm_c1

    t0 = time.perf_counter()
    with parallel_backend('threading', n_jobs=4):
        svm_c2 = CalibratedClassifierCV(
            LinearSVC(C=2.0, class_weight='balanced', dual=True, random_state=42),
            method='sigmoid',
            cv=3,
        ).fit(X_train, y_train)
    t_fit_c2 = round(time.perf_counter() - t0, 2)
    print(f'Fitted svm_linear_cal_C2.0 in {t_fit_c2}s', flush=True)
    fitted_models['svm_linear_cal_C2.0'] = svm_c2

    t0 = time.perf_counter()
    logreg_c5 = LogisticRegression(
        C=5.0,
        max_iter=250,
        tol=1e-3,
        class_weight='balanced',
        random_state=42,
    ).fit(X_train, y_train)
    t_fit_lr = round(time.perf_counter() - t0, 2)
    print(f'Fitted linear_logreg_balanced_C5.0 in {t_fit_lr}s (iters={logreg_c5.n_iter_})', flush=True)
    fitted_models['linear_logreg_balanced_C5.0'] = logreg_c5

    # Validate all 4 requested methods + V1 pilot reference strictly on X_val
    val_rows = []
    for mname, est in fitted_models.items():
        t0 = time.perf_counter()
        p_val = est.predict_proba(X_val)
        lat = (time.perf_counter() - t0) * 1000.0 / len(X_val)
        est_mb = len(pickle.dumps(est)) / (1024 ** 2)
        val_rows.append(score_probs(mname, y_val, p_val, lat, est_mb))

    t0 = time.perf_counter()
    p_val_faiss = knn_weighted_probs(repaired_index, y_train, X_val, 67, k=3)
    lat_f = (time.perf_counter() - t0) * 1000.0 / len(X_val)
    val_rows.append(score_probs('full_faiss_k3_weighted', y_val, p_val_faiss, lat_f, index_mb))

    # V1 baseline (2,144 vectors) evaluated on same X_val for transparent baseline anchor
    v1_index = faiss.read_index(str(V1_PROD_DIR / 'crop_index.faiss'))
    v1_labels = np.load(V1_PROD_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    t0 = time.perf_counter()
    v1_sims_v, v1_ids_v = v1_index.search(X_val, 5)
    lat_v1 = (time.perf_counter() - t0) * 1000.0 / len(X_val)
    v1_probs_v = np.zeros((len(X_val), 67), dtype=np.float64)
    for r in range(len(X_val)):
        v1_probs_v[r, v1_labels[v1_ids_v[r, 0]]] += 100.0
        for rk, nb in enumerate(v1_ids_v[r]):
            v1_probs_v[r, v1_labels[nb]] += 1.0 / (rk + 10.0)
        v1_probs_v[r] /= v1_probs_v[r].sum()
    val_rows.append(score_probs('v1_baseline_2144_k1', y_val, v1_probs_v, lat_v1, (V1_PROD_DIR / 'crop_index.faiss').stat().st_size / (1024 ** 2)))

    val_df = pd.DataFrame(val_rows).sort_values(['macro_f1', 'top1_acc'], ascending=False).reset_index(drop=True)
    val_df.to_csv(EVAL_OUT / 'repaired_validation_benchmark.csv', index=False)

    # Select winner strictly from repaired candidate models (exclude v1 pilot baseline)
    cand_df = val_df[val_df['model_name'] != 'v1_baseline_2144_k1'].reset_index(drop=True)
    winner_name = str(cand_df.iloc[0]['model_name'])
    assert winner_name in fitted_models or winner_name == 'full_faiss_k3_weighted'
    print('=== Corrected Validation Benchmark ===')
    print(val_df.to_string(index=False))
    print('Selected Winner on Validation:', winner_name)

    # Evaluate selected winner ONCE cleanly on held-out repaired X_test (+ comparison summary across methods)
    winner_est = fitted_models[winner_name]
    t0 = time.perf_counter()
    winner_probs_test = winner_est.predict_proba(X_test)
    winner_lat_test = (time.perf_counter() - t0) * 1000.0 / len(X_test)
    winner_preds_test = np.argmax(winner_probs_test, axis=1)
    winner_cal_test, reliability_bins = compute_ece_and_brier(y_test, winner_probs_test)

    winner_test_metrics = score_probs(
        winner_name,
        y_test,
        winner_probs_test,
        winner_lat_test,
        len(pickle.dumps(winner_est)) / (1024 ** 2),
    )
    winner_test_metrics.update({
        'mce_15bin': winner_cal_test['mce_15bin'],
        'mean_conf_correct': winner_cal_test['mean_conf_correct'],
        'mean_conf_wrong': winner_cal_test['mean_conf_wrong'],
    })

    # Secondary held-out comparison table (post-selection, strictly observational)
    test_rows = [winner_test_metrics]
    for mname, est in fitted_models.items():
        if mname == winner_name:
            continue
        t0 = time.perf_counter()
        p_t = est.predict_proba(X_test)
        lat_t = (time.perf_counter() - t0) * 1000.0 / len(X_test)
        test_rows.append(score_probs(mname, y_test, p_t, lat_t, len(pickle.dumps(est)) / (1024 ** 2)))

    t0 = time.perf_counter()
    p_t_faiss = knn_weighted_probs(repaired_index, y_train, X_test, 67, k=3)
    lat_tf = (time.perf_counter() - t0) * 1000.0 / len(X_test)
    test_rows.append(score_probs('full_faiss_k3_weighted', y_test, p_t_faiss, lat_tf, index_mb))

    t0 = time.perf_counter()
    v1_sims_t, v1_ids_t = v1_index.search(X_test, 5)
    lat_v1_t = (time.perf_counter() - t0) * 1000.0 / len(X_test)
    v1_probs_t = np.zeros((len(X_test), 67), dtype=np.float64)
    for r in range(len(X_test)):
        v1_probs_t[r, v1_labels[v1_ids_t[r, 0]]] += 100.0
        for rk, nb in enumerate(v1_ids_t[r]):
            v1_probs_t[r, v1_labels[nb]] += 1.0 / (rk + 10.0)
        v1_probs_t[r] /= v1_probs_t[r].sum()
    test_rows.append(score_probs('v1_baseline_2144_k1', y_test, v1_probs_t, lat_v1_t, (V1_PROD_DIR / 'crop_index.faiss').stat().st_size / (1024 ** 2)))

    test_df = pd.DataFrame(test_rows).sort_values(['macro_f1', 'top1_acc'], ascending=False).reset_index(drop=True)
    test_df.to_csv(EVAL_OUT / 'repaired_test_comparison.csv', index=False)
    pd.DataFrame(reliability_bins).to_csv(EVAL_OUT / 'winner_test_reliability_bins.csv', index=False)

    cm_test = confusion_matrix(y_test, winner_preds_test, labels=np.arange(67))
    pd.DataFrame(cm_test, index=classes, columns=classes).to_csv(EVAL_OUT / 'winner_test_confusion_matrix_67x67.csv')

    rep = classification_report(
        y_test, winner_preds_test, labels=np.arange(67), target_names=classes, output_dict=True, zero_division=0
    )
    rep_df = pd.DataFrame(rep).T
    rep_df.to_csv(EVAL_OUT / 'winner_test_per_class.csv')

    # Extract top cross-class confusion pairs on test
    cm_no_diag = cm_test.copy()
    np.fill_diagonal(cm_no_diag, 0)
    top_pairs = []
    flat_order = np.argsort(-cm_no_diag.ravel())[:15]
    for flat_idx in flat_order:
        r_i, c_i = divmod(int(flat_idx), 67)
        cnt = int(cm_no_diag[r_i, c_i])
        if cnt == 0:
            break
        top_pairs.append({'true_class': classes[r_i], 'predicted_class': classes[c_i], 'count': cnt})

    # Task 3: Export ACTUAL fitted winning estimator under versioned truthful name
    safe_win = winner_name.replace('.', '_')
    pkl_filename = f'crop_classifier_{safe_win}_v3_repaired.pkl'
    npz_filename = f'crop_classifier_{safe_win}_v3_repaired_weights.npz'

    (ASSETS_OUT / 'classes.json').write_text(json.dumps(classes, indent=2), encoding='utf-8')

    # Verify exact C on actual exported SVM object (never misname C=1.0 vs C=2.0)
    actual_params = {}
    if hasattr(winner_est, 'calibrated_classifiers_'):
        fold0_est = winner_est.calibrated_classifiers_[0].estimator
        actual_params = {
            'estimator_type': 'CalibratedClassifierCV(LinearSVC)',
            'C': float(fold0_est.C),
            'class_weight': str(fold0_est.class_weight),
            'calibration_method': str(winner_est.method),
            'cv_folds': len(winner_est.calibrated_classifiers_),
        }
        assert f'C{actual_params["C"]}' in winner_name, 'Filename/label C mismatch guard failed!'
    else:
        actual_params = {'estimator_type': type(winner_est).__name__, 'C': float(winner_est.C)}

    export_bundle = {
        'model_name': winner_name,
        'estimator': winner_est,
        'auxiliary_logreg_c5': logreg_c5,
        'centroids': centroids,
        'classes': classes,
        'actual_params': actual_params,
        'sklearn_version': sklearn.__version__,
    }
    with open(ASSETS_OUT / pkl_filename, 'wb') as f:
        pickle.dump(export_bundle, f)

    # Export pure-NumPy weight tensors so FastAPI adapter needs zero runtime sklearn dependency
    save_dict = {
        'logreg_coef': np.asarray(logreg_c5.coef_, dtype=np.float64),
        'logreg_intercept': np.asarray(logreg_c5.intercept_, dtype=np.float64),
        'centroids': centroids,
    }
    if hasattr(winner_est, 'calibrated_classifiers_'):
        folds_coefs = np.stack([np.asarray(fc.estimator.coef_, dtype=np.float64) for fc in winner_est.calibrated_classifiers_], axis=0)
        folds_intercepts = np.stack([np.asarray(fc.estimator.intercept_, dtype=np.float64) for fc in winner_est.calibrated_classifiers_], axis=0)
        folds_a = np.stack([np.array([cal.a_ for cal in fc.calibrators], dtype=np.float64) for fc in winner_est.calibrated_classifiers_], axis=0)
        folds_b = np.stack([np.array([cal.b_ for cal in fc.calibrators], dtype=np.float64) for fc in winner_est.calibrated_classifiers_], axis=0)
        save_dict.update({
            'folds_coefs': folds_coefs,
            'folds_intercepts': folds_intercepts,
            'folds_a': folds_a,
            'folds_b': folds_b,
        })
    np.savez(ASSETS_OUT / npz_filename, **save_dict)

    # Generate 12 deterministic prediction-parity vectors & expected sklearn probabilities
    rng = np.random.default_rng(42)
    correct_idx = np.where(winner_preds_test == y_test)[0]
    wrong_idx = np.where(winner_preds_test != y_test)[0]
    chosen_test_rows = list(correct_idx[:5]) + list(wrong_idx[:4])
    vecs = [X_test[i] for i in chosen_test_rows]
    # Add 3 adversarial normalized vectors (mean prototype, balanced superposition, Gaussian noise)
    v_proto = centroids[0].copy()
    v_mix = centroids[0] + centroids[1]
    v_mix = (v_mix / np.linalg.norm(v_mix)).astype(np.float32)
    v_noise = rng.normal(size=768).astype(np.float32)
    v_noise = (v_noise / np.linalg.norm(v_noise)).astype(np.float32)
    parity_X = np.vstack(vecs + [v_proto, v_mix, v_noise]).astype(np.float32)

    parity_sklearn_probs = winner_est.predict_proba(parity_X)
    if hasattr(winner_est, 'calibrated_classifiers_'):
        parity_numpy_probs = numpy_calibrated_svm_proba(parity_X, folds_coefs, folds_intercepts, folds_a, folds_b)
        full_test_numpy_probs = numpy_calibrated_svm_proba(X_test, folds_coefs, folds_intercepts, folds_a, folds_b)
    else:
        lg_p = parity_X.astype(np.float64) @ save_dict['logreg_coef'].T + save_dict['logreg_intercept']
        lg_p -= lg_p.max(axis=1, keepdims=True)
        parity_numpy_probs = np.exp(lg_p) / np.exp(lg_p).sum(axis=1, keepdims=True)
        lg_t = X_test.astype(np.float64) @ save_dict['logreg_coef'].T + save_dict['logreg_intercept']
        lg_t -= lg_t.max(axis=1, keepdims=True)
        full_test_numpy_probs = np.exp(lg_t) / np.exp(lg_t).sum(axis=1, keepdims=True)
    max_abs_diff = float(np.max(np.abs(parity_sklearn_probs - parity_numpy_probs)))
    top1_match = bool(np.array_equal(np.argmax(parity_sklearn_probs, axis=1), np.argmax(parity_numpy_probs, axis=1)))
    assert max_abs_diff < 1e-6 and top1_match, f'Parity check failed: diff={max_abs_diff}'

    full_test_max_diff = float(np.max(np.abs(winner_probs_test - full_test_numpy_probs)))
    full_test_top1_match_rate = float(np.mean(np.argmax(winner_probs_test, axis=1) == np.argmax(full_test_numpy_probs, axis=1)))

    np.savez(ASSETS_OUT / 'parity_test_vectors.npz', X=parity_X)
    parity_expected = {
        'winner_name': winner_name,
        'sklearn_version': sklearn.__version__,
        'num_vectors': int(len(parity_X)),
        'max_abs_prob_diff_parity_vectors': max_abs_diff,
        'max_abs_prob_diff_full_test_set': full_test_max_diff,
        'full_test_top1_agreement': full_test_top1_match_rate,
        'cases': [
            {
                'case_id': i,
                'expected_class_idx': int(np.argmax(parity_sklearn_probs[i])),
                'expected_label': classes[int(np.argmax(parity_sklearn_probs[i]))],
                'expected_top1_prob': round(float(np.max(parity_sklearn_probs[i])), 8),
                'expected_probs': [float(x) for x in parity_sklearn_probs[i]],
            }
            for i in range(len(parity_X))
        ],
    }
    (ASSETS_OUT / 'parity_expected_outputs.json').write_text(json.dumps(parity_expected, indent=2), encoding='utf-8')

    feature_spec = {
        'model_version': 'v3_svm_repaired',
        'evaluation_scope': 'repaired_65703_cached_subset',
        'winning_estimator_name': winner_name,
        'exported_pickle_filename': pkl_filename,
        'exported_numpy_filename': npz_filename,
        'exported_faiss_filename': 'crop_index_v3_repaired.faiss',
        'feature_dim': 768,
        'l2_normalized': True,
        'backbone': v1_meta.get('model', 'dinov2_vits14'),
        'feature_pooling': 'concat_norm_cls_and_mean_patch_tokens',
        'preprocessing': {
            'resize_short_side': v1_meta.get('resize_short_side', 256),
            'interpolation': 'bilinear',
            'center_crop_size': v1_meta.get('image_size', 224),
            'mean': v1_meta.get('mean', [0.485, 0.456, 0.406]),
            'std': v1_meta.get('std', [0.229, 0.224, 0.225]),
            'exif_transpose': True,
        },
        'num_classes': len(classes),
        'class_map_sha256': sha256_file(ASSETS_OUT / 'classes.json'),
        'training_config': {
            **actual_params,
            'random_state': 42,
            'split_method': 'StratifiedGroupKFold(n_splits=10, shuffle=True, random_state=42)',
            'train_samples': int(len(X_train)),
            'val_samples': int(len(X_val)),
            'test_samples': int(len(X_test)),
            'source_group_overlap': 0,
        },
        'runtime_environment': {
            'python_version': platform.python_version(),
            'scikit_learn_version': sklearn.__version__,
            'numpy_version': np.__version__,
            'faiss_version': faiss.__version__,
        },
    }
    (ASSETS_OUT / 'feature_spec.json').write_text(json.dumps(feature_spec, indent=2), encoding='utf-8')

    summary = {
        'scope': '65,703 cached local vectors split cleanly by source_group_id (zero overlap)',
        'selected_winner': winner_name,
        'winner_actual_params': actual_params,
        'validation_benchmark': val_rows,
        'test_winner_summary': winner_test_metrics,
        'test_all_methods_summary': test_rows,
        'top_test_confusions': top_pairs,
        'worst_10_test_classes': rep_df.iloc[:67].sort_values('f1-score').head(10).round(4).to_dict(orient='index'),
        'parity_verification': {
            'parity_12_max_abs_diff': max_abs_diff,
            'full_test_max_abs_diff': full_test_max_diff,
            'full_test_top1_agreement': full_test_top1_match_rate,
        },
    }
    (EVAL_OUT / 'repaired_training_and_export_report.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')

    # Generate complete SHA256_MANIFEST.json for v3_svm_repaired/
    manifest_files = {}
    for p in sorted(REPAIRED_DIR.rglob('*')):
        if p.is_file() and p.name != 'SHA256_MANIFEST.json':
            rel = str(p.relative_to(REPAIRED_DIR))
            manifest_files[rel] = {'bytes': p.stat().st_size, 'sha256': sha256_file(p)}
    (REPAIRED_DIR / 'SHA256_MANIFEST.json').write_text(
        json.dumps({'version': 'v3_svm_repaired', 'files': manifest_files}, indent=2),
        encoding='utf-8',
    )

    print('=== Held-Out Repaired Test Comparison ===')
    print(test_df.to_string(index=False))
    print(f'Parity check -> 12-vec max_abs_diff={max_abs_diff:.2e} | full X_test ({len(X_test)}) max_abs_diff={full_test_max_diff:.2e}, top1_agree={full_test_top1_match_rate:.6f}')
    print(f'Exported winner artifact: {ASSETS_OUT / pkl_filename}')


if __name__ == '__main__':
    main()

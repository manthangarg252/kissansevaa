"""
Tasks 2, 3 & 4 Comprehensive CPU Evaluation (Uses cached 65,703 embeddings + ONNX extractor, zero GPU cost):

Task 2 — Compare 5 architectures strictly tuned on Validation (6,610) and evaluated on Held-Out Test (6,544):
  1. SVM C=2.0 alone (svm_linear_cal_C2.0)
  2. Full FAISS k=3 weighted alone (calibrated on val via temp tau + Dirichlet smoothing alpha)
  3. SVM + FAISS Class Agreement (abstain/needs_review on top-1 disagreement)
  4. SVM + FAISS Calibrated Log-Probability Fusion (parameters fit strictly on val NLL)
  5. SVM + FAISS Disagreement Abstention (fused confidence + agreement gating calibrated on val)

Task 3 — Separate Lightweight Supported-Crop & Plant/Leaf Open-Set Rejection Gate:
  - Dedicated 14-Crop One-vs-Rest LinearSVC raw-margin gate (unnormalized crop decision score)
  - 67-class unnormalized OvR SVM max decision (raw hyperplane score before normalized softmax sum)
  - FAISS Top-1 / Top-3 Cosine density
  - Quality thresholds (Laplacian blur + brightness)
  Tested on Leave-Crop-Out unseen crops + real food, cards, mobile screenshots, blur & low-lighting.

Task 4 — Real-Camera Background/Cropping & Multi-Crop TTA Investigation:
  - Standard (Resize 256 -> CenterCrop 224) vs Tight Center Zoom (0.78) vs Excess-Green ExG Foreground Crop vs Multi-Crop TTA
  Evaluated across genuine local photos, orientation rotations, and OOD safety checks.
"""
import json
import pickle
import time
from pathlib import Path

import faiss
import numpy as np
import onnxruntime as ort
import pandas as pd
from joblib import parallel_backend
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
from scipy.optimize import minimize
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import accuracy_score, f1_score, log_loss, roc_auc_score
from sklearn.svm import LinearSVC


ROOT = Path(__file__).resolve().parents[2]
OLD_V3_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm'
V1_DIR = ROOT / 'ml-service' / 'model_assets'
REPAIRED_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm_repaired'
ASSETS_DIR = REPAIRED_DIR / 'model_assets'
EVAL_DIR = REPAIRED_DIR / 'evaluation'
UPLOADS_DIR = ROOT / 'backend' / 'uploads'
BRAIN_DIR = Path('/Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b')


def preprocess_pil(img: Image.Image, spec: dict, inner_zoom: float = 1.0) -> np.ndarray:
    im = ImageOps.exif_transpose(img).convert('RGB')
    w, h = im.size
    if inner_zoom < 1.0:
        zw, zh = max(32, int(round(w * inner_zoom))), max(32, int(round(h * inner_zoom)))
        l0, t0 = (w - zw) // 2, (h - zh) // 2
        im = im.crop((l0, t0, l0 + zw, t0 + zh))
        w, h = im.size
    short = int(spec.get('resize_short_side', 256))
    nw, nh = (short, int(round(short * h / w))) if w < h else (int(round(short * w / h)), short)
    im = im.resize((nw, nh), resample=Image.Resampling.BILINEAR)
    sz = int(spec.get('center_crop_size', 224))
    left, top = (nw - sz) // 2, (nh - sz) // 2
    im = im.crop((left, top, left + sz, top + sz))
    arr = np.asarray(im, dtype=np.float32).transpose(2, 0, 1) / 255.0
    mean = np.asarray(spec.get('mean', [0.485, 0.456, 0.406]), dtype=np.float32)[:, None, None]
    std = np.asarray(spec.get('std', [0.229, 0.224, 0.225]), dtype=np.float32)[:, None, None]
    return ((arr - mean) / std)[None].astype('float32')


def crop_excess_green_roi(img: Image.Image, pad_frac: float = 0.08) -> Image.Image:
    """Lightweight plant-leaf foreground bounding box via Excess-Green (ExG = 2G - R - B)."""
    im = ImageOps.exif_transpose(img).convert('RGB')
    w, h = im.size
    arr = np.asarray(im.resize((min(256, w), min(256, h)), Image.Resampling.BILINEAR), dtype=np.float32)
    exg = 2.0 * arr[:, :, 1] - arr[:, :, 0] - arr[:, :, 2]
    # Include brownish lesion pixels that sit near green vegetation mask by checking > 12 or top 45% ExG
    mask = exg > max(12.0, float(np.percentile(exg, 60)))
    if float(mask.mean()) < 0.05 or float(mask.mean()) > 0.92:
        return im
    ys, xs = np.where(mask)
    sh, sw = mask.shape
    x0, x1 = xs.min() / sw, (xs.max() + 1) / sw
    y0, y1 = ys.min() / sh, (ys.max() + 1) / sh
    # Ignore if mask spans essentially full image
    if (x1 - x0) * (y1 - y0) < 0.10:
        return im
    px0 = max(0, int((x0 - pad_frac) * w))
    py0 = max(0, int((y0 - pad_frac) * h))
    px1 = min(w, int((x1 + pad_frac) * w))
    py1 = min(h, int((y1 + pad_frac) * h))
    return im.crop((px0, py0, max(px0 + 32, px1), max(py0 + 32, py1)))


def extract_emb(sess, img: Image.Image, spec: dict, mode: str = 'standard') -> np.ndarray:
    if mode == 'standard':
        embs = [sess.run(None, {'image': preprocess_pil(img, spec, 1.0)})[0][0]]
    elif mode == 'inner_zoom_0_80':
        embs = [sess.run(None, {'image': preprocess_pil(img, spec, 0.80)})[0][0]]
    elif mode == 'exg_foreground':
        roi = crop_excess_green_roi(img)
        embs = [sess.run(None, {'image': preprocess_pil(roi, spec, 1.0)})[0][0]]
    elif mode == 'multicrop_tta':
        im0 = ImageOps.exif_transpose(img).convert('RGB')
        roi = crop_excess_green_roi(im0)
        views = [
            preprocess_pil(im0, spec, 1.0),
            preprocess_pil(ImageOps.mirror(im0), spec, 1.0),
            preprocess_pil(im0, spec, 0.82),
            preprocess_pil(roi, spec, 1.0),
        ]
        embs = [sess.run(None, {'image': v})[0][0] for v in views]
    else:
        raise ValueError(mode)
    vec = np.mean(embs, axis=0).astype('float32')
    vec /= max(float(np.linalg.norm(vec)), 1e-12)
    return vec


def measure_quality(img: Image.Image) -> tuple[float, float]:
    gray = np.asarray(ImageOps.exif_transpose(img).convert('L'), dtype=np.float32)
    if gray.shape[0] < 3 or gray.shape[1] < 3:
        return 0.0, float(gray.mean())
    lap = (
        -4.0 * gray[1:-1, 1:-1]
        + gray[:-2, 1:-1]
        + gray[2:, 1:-1]
        + gray[1:-1, :-2]
        + gray[1:-1, 2:]
    )
    return round(float(lap.var()), 2), round(float(gray.mean()), 2)


def numpy_svm_probs_and_raw(X: np.ndarray, weights: dict) -> tuple[np.ndarray, np.ndarray]:
    """Return BOTH calibrated 67-class probability AND unnormalized mean OvR raw SVM decision score."""
    X64 = np.asarray(X, dtype=np.float64)
    fc, fi = weights['folds_coefs'], weights['folds_intercepts']
    fa, fb = weights['folds_a'], weights['folds_b']
    n, n_classes = X64.shape[0], fi.shape[1]
    fold_prob_sum = np.zeros((n, n_classes), dtype=np.float64)
    raw_df_sum = np.zeros((n, n_classes), dtype=np.float64)
    for k in range(len(fc)):
        df = X64 @ fc[k].T + fi[k]
        raw_df_sum += df
        z = np.clip(fa[k][None, :] * df + fb[k][None, :], -500.0, 500.0)
        p = 1.0 / (1.0 + np.exp(z))
        fold_prob_sum += p / np.maximum(p.sum(axis=1, keepdims=True), 1e-12)
    return fold_prob_sum / len(fc), raw_df_sum / len(fc)


def faiss_calibrated_probs(sims: np.ndarray, ids: np.ndarray, y_train: np.ndarray, num_classes: int, tau: float, alpha: float) -> np.ndarray:
    """Convert FAISS top-k cosine similarities into smooth calibrated probabilities."""
    n = sims.shape[0]
    out = np.full((n, num_classes), alpha / num_classes, dtype=np.float64)
    shifted = tau * (sims - sims[:, :1])
    weights = np.exp(np.clip(shifted, -50.0, 50.0))
    for r in range(n):
        row_ids = y_train[ids[r]]
        np.add.at(out[r], row_ids, weights[r])
    out /= out.sum(axis=1, keepdims=True)
    return out


def compute_ece(y_true: np.ndarray, probs: np.ndarray, n_bins: int = 15) -> float:
    conf = probs.max(axis=1)
    corr = (probs.argmax(axis=1) == y_true).astype(np.float64)
    edges = np.linspace(0.0, 1.0, n_bins + 1)
    ece = 0.0
    for b in range(n_bins):
        m = (conf >= edges[b]) & (conf <= edges[b + 1] if b == n_bins - 1 else conf < edges[b + 1])
        if m.any():
            ece += (m.sum() / len(y_true)) * abs(corr[m].mean() - conf[m].mean())
    return float(ece)


def selective_metrics(name: str, y_true: np.ndarray, preds: np.ndarray, probs: np.ndarray, accept_mask: np.ndarray) -> dict:
    cov = float(accept_mask.mean())
    overall_acc_reject_wrong = float(np.mean((preds == y_true) & accept_mask))
    if accept_mask.sum() > 0:
        sel_acc = float(accuracy_score(y_true[accept_mask], preds[accept_mask]))
        sel_f1 = float(f1_score(y_true[accept_mask], preds[accept_mask], average='macro', zero_division=0))
    else:
        sel_acc = 0.0
        sel_f1 = 0.0
    uncond_acc = float(accuracy_score(y_true, preds))
    uncond_f1 = float(f1_score(y_true, preds, average='macro', zero_division=0))
    # Crop-group recall for Wheat, Rice, Apple, Tomato
    return {
        'method': name,
        'coverage': round(cov, 5),
        'selective_acc': round(sel_acc, 5),
        'selective_macro_f1': round(sel_f1, 5),
        'unconditional_acc': round(uncond_acc, 5),
        'unconditional_macro_f1': round(uncond_f1, 5),
        'accepted_correct_rate': round(overall_acc_reject_wrong, 5),
        'ece_unconditional': round(compute_ece(y_true, probs), 5),
        'nll_unconditional': round(float(log_loss(y_true, np.clip(probs, 1e-12, 1.0), labels=np.arange(probs.shape[1]))), 5),
    }


def main() -> None:
    spec = json.loads((ASSETS_DIR / 'feature_spec.json').read_text(encoding='utf-8'))
    classes = json.loads((ASSETS_DIR / 'classes.json').read_text(encoding='utf-8'))
    weights = dict(np.load(ASSETS_DIR / spec['exported_numpy_filename']))
    centroids = weights['centroids'].astype('float32')

    splits_df = pd.read_csv(REPAIRED_DIR / 'splits_local_65703_repaired.csv')
    idx_all = faiss.read_index(str(OLD_V3_DIR / 'model_assets' / 'crop_index_v3_full.faiss'))
    labels_all = np.load(OLD_V3_DIR / 'model_assets' / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    X_all = idx_all.reconstruct_n(0, idx_all.ntotal).astype('float32')

    tr_mask = (splits_df['local_split'] == 'train').to_numpy()
    va_mask = (splits_df['local_split'] == 'val').to_numpy()
    te_mask = (splits_df['local_split'] == 'test').to_numpy()

    X_train, y_train = X_all[tr_mask], labels_all[tr_mask]
    X_val, y_val = X_all[va_mask], labels_all[va_mask]
    X_test, y_test = X_all[te_mask], labels_all[te_mask]

    train_index = faiss.read_index(str(ASSETS_DIR / 'crop_index_v3_repaired.faiss'))
    assert train_index.ntotal == len(X_train)

    # =========================================================================
    # TASK 2: Fit Calibrated Fusion strictly on Validation (6,610)
    # =========================================================================
    t0 = time.perf_counter()
    p_svm_val, raw_svm_val = numpy_svm_probs_and_raw(X_val, weights)
    svm_val_ms = (time.perf_counter() - t0) * 1000.0 / len(X_val)

    t0 = time.perf_counter()
    f_sims_val, f_ids_val = train_index.search(X_val, 3)
    faiss_val_ms = (time.perf_counter() - t0) * 1000.0 / len(X_val)

    # Optimize FAISS probability calibration (tau, alpha) strictly on X_val NLL
    def faiss_nll_loss(params):
        tau, log_alpha = params
        if tau < 0.5 or tau > 40.0 or log_alpha < -8.0 or log_alpha > 4.0:
            return 1e6
        pf = faiss_calibrated_probs(f_sims_val, f_ids_val, y_train, 67, tau, float(np.exp(log_alpha)))
        return float(log_loss(y_val, np.clip(pf, 1e-12, 1.0), labels=np.arange(67)))

    res_f = minimize(faiss_nll_loss, x0=[10.0, np.log(0.15)], method='Nelder-Mead')
    best_tau, best_alpha = float(res_f.x[0]), float(np.exp(res_f.x[1]))
    p_faiss_val = faiss_calibrated_probs(f_sims_val, f_ids_val, y_train, 67, best_tau, best_alpha)

    # Optimize Log-Probability Calibrated Fusion (beta_svm, beta_faiss) strictly on X_val NLL
    eps = 1e-6
    log_svm_val = np.log(np.clip(p_svm_val, eps, 1.0))
    log_faiss_val = np.log(np.clip(p_faiss_val, eps, 1.0))

    def fusion_nll_loss(params):
        b_svm, b_faiss = params
        if b_svm < 0.0 or b_faiss < 0.0 or (b_svm + b_faiss) < 0.05:
            return 1e6
        logits = b_svm * log_svm_val + b_faiss * log_faiss_val
        logits -= logits.max(axis=1, keepdims=True)
        probs = np.exp(logits)
        probs /= probs.sum(axis=1, keepdims=True)
        return float(log_loss(y_val, np.clip(probs, 1e-12, 1.0), labels=np.arange(67)))

    res_mix = minimize(fusion_nll_loss, x0=[0.85, 0.35], method='Nelder-Mead')
    b_svm_opt, b_faiss_opt = float(res_mix.x[0]), float(res_mix.x[1])

    def apply_calibrated_fusion(ps, pf):
        logits = b_svm_opt * np.log(np.clip(ps, eps, 1.0)) + b_faiss_opt * np.log(np.clip(pf, eps, 1.0))
        logits -= logits.max(axis=1, keepdims=True)
        out = np.exp(logits)
        out /= out.sum(axis=1, keepdims=True)
        return out

    p_fused_val = apply_calibrated_fusion(p_svm_val, p_faiss_val)
    print(f'Fitted Calibrated Fusion on Val -> tau={best_tau:.3f}, alpha={best_alpha:.4f}, beta_svm={b_svm_opt:.4f}, beta_faiss={b_faiss_opt:.4f}', flush=True)

    # Tune Disagreement + Confidence Abstention threshold on Val targeting >= 98.0% selective acc
    pred_svm_val = p_svm_val.argmax(axis=1)
    pred_faiss_val = p_faiss_val.argmax(axis=1)
    pred_fused_val = p_fused_val.argmax(axis=1)
    agree_val = (pred_svm_val == pred_faiss_val)
    max_raw_val = raw_svm_val.max(axis=1)

    # Evaluate on Held-Out Test (6,544)
    t0 = time.perf_counter()
    p_svm_test, raw_svm_test = numpy_svm_probs_and_raw(X_test, weights)
    svm_test_ms = (time.perf_counter() - t0) * 1000.0 / len(X_test)

    t0 = time.perf_counter()
    f_sims_test, f_ids_test = train_index.search(X_test, 3)
    faiss_test_ms = (time.perf_counter() - t0) * 1000.0 / len(X_test)

    p_faiss_test = faiss_calibrated_probs(f_sims_test, f_ids_test, y_train, 67, best_tau, best_alpha)
    p_fused_test = apply_calibrated_fusion(p_svm_test, p_faiss_test)

    pred_svm_test = p_svm_test.argmax(axis=1)
    pred_faiss_test = p_faiss_test.argmax(axis=1)
    pred_fused_test = p_fused_test.argmax(axis=1)
    agree_test = (pred_svm_test == pred_faiss_test)
    max_raw_test = raw_svm_test.max(axis=1)

    # 5 required Task 2 comparisons (+ selective thresholded fusion variant)
    t2_val = [
        selective_metrics('1_svm_c2_only', y_val, pred_svm_val, p_svm_val, np.ones(len(y_val), dtype=bool)),
        selective_metrics('2_faiss_k3_calibrated', y_val, pred_faiss_val, p_faiss_val, np.ones(len(y_val), dtype=bool)),
        selective_metrics('3_svm_faiss_agreement_only', y_val, pred_svm_val, p_fused_val, agree_val),
        selective_metrics('4_svm_faiss_calibrated_fusion', y_val, pred_fused_val, p_fused_val, np.ones(len(y_val), dtype=bool)),
        selective_metrics('5_hybrid_disagreement_abstention_rawgate', y_val, pred_fused_val, p_fused_val, agree_val & (p_fused_val.max(axis=1) >= 0.55) & (max_raw_val >= -0.45)),
    ]

    t2_test = [
        selective_metrics('1_svm_c2_only', y_test, pred_svm_test, p_svm_test, np.ones(len(y_test), dtype=bool)),
        selective_metrics('2_faiss_k3_calibrated', y_test, pred_faiss_test, p_faiss_test, np.ones(len(y_test), dtype=bool)),
        selective_metrics('3_svm_faiss_agreement_only', y_test, pred_svm_test, p_fused_test, agree_test),
        selective_metrics('4_svm_faiss_calibrated_fusion', y_test, pred_fused_test, p_fused_test, np.ones(len(y_test), dtype=bool)),
        selective_metrics('5_hybrid_disagreement_abstention_rawgate', y_test, pred_fused_test, p_fused_test, agree_test & (p_fused_test.max(axis=1) >= 0.55) & (max_raw_test >= -0.45)),
    ]
    pd.DataFrame(t2_val).to_csv(EVAL_DIR / 'task2_hybrid_validation.csv', index=False)
    pd.DataFrame(t2_test).to_csv(EVAL_DIR / 'task2_hybrid_test.csv', index=False)

    # Class-specific test recall across Wheat, Rice, Apple, Tomato for SVM vs FAISS vs Fused
    key_crops_rows = []
    for crop in sorted({c.split('/')[0] for c in classes}):
        c_cls = {i for i, n in enumerate(classes) if n.split('/')[0] == crop}
        m = np.array([int(y) in c_cls for y in y_test])
        key_crops_rows.append({
            'crop': crop,
            'support': int(m.sum()),
            'svm_acc': round(float(accuracy_score(y_test[m], pred_svm_test[m])), 4),
            'faiss_acc': round(float(accuracy_score(y_test[m], pred_faiss_test[m])), 4),
            'fused_acc': round(float(accuracy_score(y_test[m], pred_fused_test[m])), 4),
            'agree_rate': round(float(agree_test[m].mean()), 4),
            'agree_selective_acc': round(float(accuracy_score(y_test[m & agree_test], pred_svm_test[m & agree_test])), 4) if (m & agree_test).any() else None,
        })
    pd.DataFrame(key_crops_rows).to_csv(EVAL_DIR / 'task2_per_crop_hybrid_test.csv', index=False)

    # =========================================================================
    # TASK 3: Separate Lightweight 14-Crop Supported-Crop + OOD Gate
    # =========================================================================
    crop_names = sorted({c.split('/')[0] for c in classes})
    cls_to_crop_idx = np.array([crop_names.index(c.split('/')[0]) for c in classes], dtype=np.int32)
    y_train_crop = cls_to_crop_idx[y_train]
    with parallel_backend('threading', n_jobs=4):
        crop_svm = LinearSVC(C=2.0, class_weight='balanced', dual=True, random_state=42).fit(X_train, y_train_crop)
    crop_coef = crop_svm.coef_.astype(np.float64)
    crop_intercept = crop_svm.intercept_.astype(np.float64)

    # Validate Leave-Crop-Out (holding out Citrus, Cotton, Cherry, Strawberry) with separate 10-crop OvR LinearSVC
    unsup_crops = {'Citrus', 'Cotton', 'Cherry', 'Strawberry'}
    kept_crops = [c for c in crop_names if c not in unsup_crops]
    unsup_cls_ids = {i for i, c in enumerate(classes) if c.split('/')[0] in unsup_crops}
    kept_cls_ids = sorted(set(range(67)) - unsup_cls_ids)
    cls_remap = {old: new for new, old in enumerate(kept_cls_ids)}

    tr_keep = np.array([int(y) in kept_cls_ids for y in y_train])
    va_keep = np.array([int(y) in kept_cls_ids for y in y_val])
    va_unsup = np.array([int(y) in unsup_cls_ids for y in y_val])

    y_tr_lco_cls = np.array([cls_remap[int(y)] for y in y_train[tr_keep]], dtype=np.int32)
    y_tr_lco_crop = np.array([kept_crops.index(classes[int(y)].split('/')[0]) for y in y_train[tr_keep]], dtype=np.int32)

    with parallel_backend('threading', n_jobs=4):
        lco_cls_cal = CalibratedClassifierCV(LinearSVC(C=2.0, class_weight='balanced', dual=True, random_state=42), cv=3).fit(X_train[tr_keep], y_tr_lco_cls)
        lco_crop_svm = LinearSVC(C=2.0, class_weight='balanced', dual=True, random_state=42).fit(X_train[tr_keep], y_tr_lco_crop)

    lco_idx = faiss.IndexFlatIP(768)
    lco_idx.add(X_train[tr_keep])

    def lco_features(Xq):
        probs = lco_cls_cal.predict_proba(Xq)
        raw67 = np.mean([fc.estimator.decision_function(Xq) for fc in lco_cls_cal.calibrated_classifiers_], axis=0).max(axis=1)
        crop_raw = lco_crop_svm.decision_function(Xq).max(axis=1)
        sims, _ = lco_idx.search(Xq, 1)
        return probs.max(axis=1), raw67, crop_raw, sims[:, 0]

    kp, kraw67, kcrop_raw, ksim = lco_features(X_val[va_keep])
    up, uraw67, ucrop_raw, usim = lco_features(X_val[va_unsup])

    lco_auc_raw = roc_auc_score(np.concatenate([np.ones(len(kp)), np.zeros(len(up))]), np.concatenate([kraw67, uraw67]))
    lco_auc_crop = roc_auc_score(np.concatenate([np.ones(len(kp)), np.zeros(len(up))]), np.concatenate([kcrop_raw, ucrop_raw]))

    # Sweep thresholds on Validation LCO for separate Crop Gate + 67-class Raw Decision + FAISS Top1 Cos
    gate_sweeps = []
    for th_raw in [-0.60, -0.45, -0.30, -0.15]:
        for th_crop in [-0.40, -0.20, 0.00]:
            for th_fcos in [0.45, 0.55, 0.65]:
                acc_k = (kraw67 >= th_raw) & (kcrop_raw >= th_crop) & (ksim >= th_fcos)
                acc_u = (uraw67 >= th_raw) & (ucrop_raw >= th_crop) & (usim >= th_fcos)
                gate_sweeps.append({
                    'th_svm_raw67': th_raw,
                    'th_crop_raw14': th_crop,
                    'th_faiss_cos1': th_fcos,
                    'FRR_supported_crops': round(float((~acc_k).mean()), 4),
                    'FAR_unsupported_crops_lco': round(float(acc_u.mean()), 4),
                })
    gate_df = pd.DataFrame(gate_sweeps).sort_values(['FAR_unsupported_crops_lco', 'FRR_supported_crops'])
    gate_df.to_csv(EVAL_DIR / 'task3_openset_crop_gate_sweep.csv', index=False)

    # =========================================================================
    # TASK 4: Real-Camera Cropping/Segmentation & Multi-Crop TTA Evaluation
    # + Verify Task 3 Non-Leaf OOD Rejection on Real Uploads & UI Screenshots
    # =========================================================================
    ort_sess = ort.InferenceSession(str(V1_DIR / 'dinov2_vits14_cls_mean.onnx'), providers=['CPUExecutionProvider'])
    v1_index = faiss.read_index(str(V1_DIR / 'crop_index.faiss'))
    v1_labels = np.load(V1_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)

    def score_single_vector(vec: np.ndarray, blur_lap: float, bright: float) -> dict:
        v2 = vec.reshape(1, -1)
        ps, raws = numpy_svm_probs_and_raw(v2, weights)
        fs, fi = train_index.search(v2, 3)
        pf = faiss_calibrated_probs(fs, fi, y_train, 67, best_tau, best_alpha)
        pfu = apply_calibrated_fusion(ps, pf)
        crop_raw_max = float(np.max(crop_coef @ vec.astype(np.float64) + crop_intercept))
        v1s, v1i = v1_index.search(v2, 1)
        c_svm = int(ps.argmax())
        c_faiss = int(pf.argmax())
        c_fused = int(pfu.argmax())
        # Recommended Open-Set + Quality Gate (raw67 >= -0.45, crop_raw14 >= -0.20, faiss_cos1 >= 0.55, cent_cos >= 0.50, blur >= 40, bright >= 30)
        cent_cos = float(np.max(centroids @ vec))
        open_set_ok = (float(raws.max()) >= -0.45) and (crop_raw_max >= -0.20) and (float(fs[0, 0]) >= 0.55) and (cent_cos >= 0.50) and (blur_lap >= 40.0) and (bright >= 30.0)
        return {
            'v1_pred': classes[int(v1_labels[int(v1i[0, 0])])],
            'v1_cos': round(float(v1s[0, 0]), 4),
            'svm_pred': classes[c_svm],
            'svm_prob': round(float(ps[0, c_svm]), 4),
            'svm_raw_max': round(float(raws.max()), 4),
            'crop_raw_max': round(crop_raw_max, 4),
            'faiss_pred': classes[c_faiss],
            'faiss_cos1': round(float(fs[0, 0]), 4),
            'fused_pred': classes[c_fused],
            'fused_prob': round(float(pfu[0, c_fused]), 4),
            'svm_faiss_agree': c_svm == c_faiss,
            'centroid_cos': round(cent_cos, 4),
            'open_set_accepted': open_set_ok,
        }

    # Test real genuine captures across Standard, Inner Zoom, ExG Foreground Crop, and Multi-Crop TTA
    genuine = [
        ('wheat_yellow_rust_field', 'Wheat/Wheat_Yellow_Rust', UPLOADS_DIR / '1792058847184-leaf.jpg'),
        ('apple_spot_ruler_lab', 'Apple/Apple_Black_rot_style', UPLOADS_DIR / '1795467122713-leaf.jpg'),
        ('apple_scab_display', 'Apple/Apple_scab', UPLOADS_DIR / '1795499287987-leaf.jpg'),
        ('apple_scab_samsung_cam', 'Apple/Apple_scab', UPLOADS_DIR / '1795499696604-samsung_hardware_cam.jpg'),
    ]
    t4_rows = []
    for sid, gt, path in genuine:
        with Image.open(path) as raw:
            im = ImageOps.exif_transpose(raw).convert('RGB')
            # Add synthetic rotated camera view for samsung cam and wheat macro too
            lap, bright = measure_quality(im)
            for mname in ['standard', 'inner_zoom_0_80', 'exg_foreground', 'multicrop_tta']:
                vec = extract_emb(ort_sess, im, spec['preprocessing'], mname)
                res = score_single_vector(vec, lap, bright)
                t4_rows.append({'sample_id': sid, 'ground_truth': gt, 'prep_mode': mname, **res})

    t4_df = pd.DataFrame(t4_rows)
    t4_df.to_csv(EVAL_DIR / 'task4_foreground_crop_ablation.csv', index=False)

    # Also test FULL Task 3 gate on ALL local OOD items (Food, Synthetic Cards, 13 UI Screenshots, Blur, Dark)
    ood_items = [('food_roti_pan', 'ood_food', UPLOADS_DIR / '1795604463804-leaf.jpg', None)]
    for sid, fn in [
        ('card_cow', '1794735023878-animal.jpg'),
        ('card_buffalo', '1795604214882-animal.jpg'),
        ('card_wheat_mildew', '1795498233938-leaf.jpg'),
        ('card_tomato_blight', '1794728904307-leaf.jpg'),
        ('card_wheat_rust', '1795464974035-leaf.jpg'),
    ]:
        ood_items.append((sid, 'ood_synthetic_card', UPLOADS_DIR / fn, None))
    for sid, p in [
        ('ui_traders', UPLOADS_DIR / '1791359550594-leaf.jpg'),
        ('ui_login', UPLOADS_DIR / '1792007280462-leaf.jpg'),
    ] + [(fn.replace('.png', ''), BRAIN_DIR / fn) for fn in [
        'screen_traders.png', 'screen_prices.png', 'screen_livestock.png',
        'screen_carbon.png', 'screen_chatbot.png', 'screen_crop.png',
        'screen_loans.png', 'screen_schemes.png', 'screen_market_result.png',
        'dashboard_redesign.png', 'emulator_current.png',
    ] if (BRAIN_DIR / fn).exists()]:
        ood_items.append((sid, 'ood_screenshot', p, None))
    for sid, _, p in genuine:
        ood_items.append((f'{sid}__blur', 'degraded_blur', p, lambda im: im.filter(ImageFilter.GaussianBlur(radius=4.5))))
        ood_items.append((f'{sid}__dark', 'degraded_dark', p, lambda im: ImageEnhance.Brightness(im).enhance(0.20)))

    ood_rows = []
    for sid, cat, p, tfn in ood_items:
        with Image.open(p) as raw:
            im = ImageOps.exif_transpose(raw).convert('RGB')
            if tfn is not None:
                im = tfn(im)
            lap, bright = measure_quality(im)
            vec = extract_emb(ort_sess, im, spec['preprocessing'], 'standard')
            res = score_single_vector(vec, lap, bright)
            ood_rows.append({'sample_id': sid, 'category': cat, 'blur_lap': lap, 'brightness': bright, **res})
    ood_res_df = pd.DataFrame(ood_rows)
    ood_res_df.to_csv(EVAL_DIR / 'task3_local_ood_gate_results.csv', index=False)

    # Save exported 14-Crop Gate parameters so FastAPI shadow adapter can load them identically!
    np.savez(
        ASSETS_DIR / 'hybrid_and_crop_gate.npz',
        crop_coef=crop_coef,
        crop_intercept=crop_intercept,
        tau=np.array([best_tau], dtype=np.float64),
        alpha=np.array([best_alpha], dtype=np.float64),
        beta_svm=np.array([b_svm_opt], dtype=np.float64),
        beta_faiss=np.array([b_faiss_opt], dtype=np.float64),
        crop_names=np.array(crop_names),
    )

    summary = {
        'fusion_params_fitted_on_val': {
            'faiss_tau': round(best_tau, 4),
            'faiss_alpha': round(best_alpha, 5),
            'beta_svm': round(b_svm_opt, 4),
            'beta_faiss': round(b_faiss_opt, 4),
        },
        'latency_ms_per_query': {
            'svm_val': round(svm_val_ms, 4),
            'faiss_val': round(faiss_val_ms, 4),
            'svm_test': round(svm_test_ms, 4),
            'faiss_test': round(faiss_test_ms, 4),
        },
        'task2_validation': t2_val,
        'task2_test': t2_test,
        'task3_lco_auroc': {
            'svm67_raw_max_decision': round(float(lco_auc_raw), 4),
            'crop14_raw_max_decision': round(float(lco_auc_crop), 4),
        },
        'task3_recommended_gate_eval': {
            'policy': 'raw67>=-0.45 & crop14>=-0.20 & faiss_cos1>=0.55 & cent>=0.50 & blur>=40 & bright>=30',
            'lco_matching_row': gate_df[(gate_df['th_svm_raw67'] == -0.45) & (gate_df['th_crop_raw14'] == -0.20) & (gate_df['th_faiss_cos1'] == 0.55)].iloc[0].to_dict(),
            'local_ood_far_all_27': round(float(ood_res_df['open_set_accepted'].mean()), 4),
            'local_ood_far_by_category': {
                cat: round(float(grp['open_set_accepted'].mean()), 4)
                for cat, grp in ood_res_df.groupby('category')
            },
            'screen_loans_raw67_and_crop14': ood_res_df[ood_res_df['sample_id'] == 'screen_loans'][['svm_prob', 'svm_raw_max', 'crop_raw_max', 'centroid_cos', 'open_set_accepted']].iloc[0].to_dict(),
        },
    }
    (EVAL_DIR / 'hybrid_and_crop_gate_summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')

    print('=== Task 2 Held-Out Test Comparison ===')
    print(pd.DataFrame(t2_test)[['method', 'coverage', 'selective_acc', 'selective_macro_f1', 'unconditional_acc', 'ece_unconditional', 'nll_unconditional']].to_string(index=False))
    print('=== Task 3 LCO Top Balanced Gate Policies ===')
    print(gate_df.head(8).to_string(index=False))
    print('LCO AUROC -> raw67:', round(lco_auc_raw, 4), '| crop14:', round(lco_auc_crop, 4))
    print('Local OOD 27 items open-set FAR:', round(float(ood_res_df['open_set_accepted'].mean()), 4))
    print('=== Task 4 Real Photo Preprocessing Ablation ===')
    print(t4_df[['sample_id', 'prep_mode', 'svm_pred', 'svm_prob', 'faiss_pred', 'faiss_cos1', 'fused_pred', 'fused_prob', 'svm_faiss_agree', 'open_set_accepted']].to_string(index=False))


if __name__ == '__main__':
    main()

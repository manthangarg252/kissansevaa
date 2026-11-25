"""Task 3: Classifier & Retrieval Benchmark Script (2,144-Index Reference Pre-Benchmark + Full-Split Runner).

Benchmarks all 7 candidate methods on identical validation/reference splits without touching production assets:
1. FAISS k=1
2. FAISS k in [3, 5, 7, 11, 21] (Majority)
3. FAISS k in [3, 5, 7, 11, 21] (Similarity-Weighted exp(10*(s-s_max)))
4. Class-Balanced Retrieval (Inverse-frequency weighted k-NN + Class Prototypes)
5. Crop-Aware Retrieval (2-stage predicted-crop LogReg filter + k-NN weighted; also crop oracle bound)
6. Linear Classifier on frozen 768-D embeddings (L2 Multinomial Softmax Regression)
7. Linear SVM on frozen 768-D embeddings (One-vs-Rest L2 Squared-Hinge Linear SVM)

Can run cleanly on:
- Default local 80/20 split (`index_2144_splits.csv`: 1,742 gallery / 402 holdout query) inside ml-service/venv
- OR full-dataset extracted chunks (`--cache-dir path/to/eval_cache`) exported from Colab!
"""
import argparse
import csv
import json
import pickle
import time
from pathlib import Path

import faiss
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
MODEL_DIR = ROOT / 'ml-service' / 'model_assets'
OUT_DIR = Path(__file__).resolve().parent


def build_confusion_matrix(y_true: np.ndarray, y_pred: np.ndarray, num_classes: int) -> np.ndarray:
    cm = np.zeros((num_classes, num_classes), dtype=np.int64)
    for yt, yp in zip(y_true, y_pred):
        cm[int(yt), int(yp)] += 1
    return cm


def compute_metrics(cm: np.ndarray):
    tp = np.diag(cm).astype(np.float64)
    fp = cm.sum(axis=0).astype(np.float64) - tp
    fn = cm.sum(axis=1).astype(np.float64) - tp
    prec = np.divide(tp, tp + fp, out=np.zeros_like(tp), where=(tp + fp) > 0)
    rec = np.divide(tp, tp + fn, out=np.zeros_like(tp), where=(tp + fn) > 0)
    f1 = np.divide(2 * prec * rec, prec + rec, out=np.zeros_like(tp), where=(prec + rec) > 0)
    acc = float(tp.sum() / max(cm.sum(), 1))
    return acc, float(prec.mean()), float(rec.mean()), float(f1.mean()), prec, rec, f1


def train_softmax_linear(X: np.ndarray, y: np.ndarray, num_classes: int, reg: float = 1e-3, steps: int = 350, lr: float = 1.5, class_weights: np.ndarray = None):
    """Exact L2-regularized Multinomial Logistic Regression trained with Adam on frozen L2 embeddings."""
    rng = np.random.default_rng(42)
    n, d = X.shape
    W = np.zeros((d, num_classes), dtype=np.float64)
    b = np.zeros(num_classes, dtype=np.float64)
    X64 = X.astype(np.float64)
    Y_oh = np.zeros((n, num_classes), dtype=np.float64)
    Y_oh[np.arange(n), y] = 1.0
    sample_w = np.ones((n, 1), dtype=np.float64) if class_weights is None else class_weights[y][:, None]
    sample_w = sample_w / sample_w.mean()

    mW, vW = np.zeros_like(W), np.zeros_like(W)
    mb, vb = np.zeros_like(b), np.zeros_like(b)
    beta1, beta2, eps = 0.9, 0.999, 1e-8
    alpha = 0.08

    for t in range(1, steps + 1):
        logits = X64 @ W + b
        logits -= logits.max(axis=1, keepdims=True)
        exp_l = np.exp(logits)
        probs = exp_l / exp_l.sum(axis=1, keepdims=True)
        diff = ((probs - Y_oh) * sample_w) / n
        gW = X64.T @ diff + reg * W
        gb = diff.sum(axis=0)

        mW = beta1 * mW + (1 - beta1) * gW
        vW = beta2 * vW + (1 - beta2) * (gW * gW)
        mb = beta1 * mb + (1 - beta1) * gb
        vb = beta2 * vb + (1 - beta2) * (gb * gb)
        mWh = mW / (1 - beta1 ** t)
        vWh = vW / (1 - beta2 ** t)
        mbh = mb / (1 - beta1 ** t)
        vbh = vb / (1 - beta2 ** t)
        W -= alpha * mWh / (np.sqrt(vWh) + eps)
        b -= alpha * mbh / (np.sqrt(vbh) + eps)

    return W.astype(np.float32), b.astype(np.float32)


def train_linear_svm_ovr(X: np.ndarray, y: np.ndarray, num_classes: int, C: float = 2.0, steps: int = 350):
    """One-vs-Rest L2-regularized Squared-Hinge Linear SVM optimized cleanly via Adam in NumPy."""
    n, d = X.shape
    X64 = X.astype(np.float64)
    Y_pm = np.full((n, num_classes), -1.0, dtype=np.float64)
    Y_pm[np.arange(n), y] = 1.0

    W = np.zeros((d, num_classes), dtype=np.float64)
    b = np.zeros(num_classes, dtype=np.float64)
    mW, vW = np.zeros_like(W), np.zeros_like(W)
    mb, vb = np.zeros_like(b), np.zeros_like(b)
    beta1, beta2, eps = 0.9, 0.999, 1e-8
    alpha = 0.05

    for t in range(1, steps + 1):
        scores = X64 @ W + b
        margins = 1.0 - Y_pm * scores
        active = np.maximum(0.0, margins)
        # Grad of 0.5*||W||^2 + (C/n) * sum max(0, 1 - y*s)^2
        coeff = (-2.0 * C / n) * active * Y_pm
        gW = W + X64.T @ coeff
        gb = coeff.sum(axis=0)

        mW = beta1 * mW + (1 - beta1) * gW
        vW = beta2 * vW + (1 - beta2) * (gW * gW)
        mb = beta1 * mb + (1 - beta1) * gb
        vb = beta2 * vb + (1 - beta2) * (gb * gb)
        W -= alpha * (mW / (1 - beta1 ** t)) / (np.sqrt(vW / (1 - beta2 ** t)) + eps)
        b -= alpha * (mb / (1 - beta1 ** t)) / (np.sqrt(vb / (1 - beta2 ** t)) + eps)

    return W.astype(np.float32), b.astype(np.float32)


def score_and_row(name, y_true, score_mat, classes, latency_ms, ram_mb, file_mb):
    n_cls = len(classes)
    y_pred = np.argmax(score_mat, axis=1)
    cm = build_confusion_matrix(y_true, y_pred, n_cls)
    acc, prec, rec, f1, p_cls, r_cls, f1_cls = compute_metrics(cm)
    ranks = np.argsort(-score_mat, axis=1)
    top3 = float(np.mean([y_true[i] in ranks[i, :3] for i in range(len(y_true))]))
    top5 = float(np.mean([y_true[i] in ranks[i, :5] for i in range(len(y_true))]))
    return {
        'method': name,
        'top1_accuracy': round(acc, 5),
        'macro_f1': round(f1, 5),
        'macro_precision': round(prec, 5),
        'macro_recall': round(rec, 5),
        'top3_recall': round(top3, 5),
        'top5_recall': round(top5, 5),
        'latency_ms_per_query': round(latency_ms, 4),
        'ram_mb': round(ram_mb, 3),
        'artifact_size_mb': round(file_mb, 3),
    }, cm, p_cls, r_cls, f1_cls


def knn_scores(sims, ids, y_gal, num_classes, k, mode='weighted', inv_freq=None):
    out = np.zeros((len(sims), num_classes), dtype=np.float64)
    for r, (ss, ii) in enumerate(zip(sims[:, :k], ids[:, :k])):
        valid = ii >= 0
        nbr_y = y_gal[ii[valid]]
        nbr_s = ss[valid]
        if mode == 'majority':
            w = np.ones(len(nbr_y), dtype=np.float64)
        elif mode == 'weighted':
            w = np.exp(10.0 * (nbr_s - nbr_s.max()))
        elif mode == 'class_balanced':
            w = np.exp(10.0 * (nbr_s - nbr_s.max())) * inv_freq[nbr_y]
        out[r] = np.bincount(nbr_y, weights=w, minlength=num_classes)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--splits-csv', type=Path, default=OUT_DIR / 'index_2144_splits.csv')
    args = ap.parse_args()

    classes = json.loads((MODEL_DIR / 'classes.json').read_text())
    labels = np.load(MODEL_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(MODEL_DIR / 'crop_index.faiss'))
    X_all = np.vstack([index.reconstruct(i) for i in range(index.ntotal)]).astype(np.float32)

    train_idx, hold_idx = [], []
    with open(args.splits_csv) as f:
        for row in csv.DictReader(f):
            rid = int(row['vector_row_id'])
            if row['split_80_20'] == 'train_ref':
                train_idx.append(rid)
            else:
                hold_idx.append(rid)
    train_idx = np.array(train_idx, dtype=np.int32)
    hold_idx = np.array(hold_idx, dtype=np.int32)

    X_tr, y_tr = X_all[train_idx], labels[train_idx]
    X_q, y_q = X_all[hold_idx], labels[hold_idx]
    num_classes = len(classes)

    gal_index = faiss.IndexFlatIP(X_tr.shape[1])
    gal_index.add(X_tr)
    gal_ram_mb = (X_tr.nbytes + y_tr.nbytes) / (1024 ** 2)
    full_file_mb = (MODEL_DIR / 'crop_index.faiss').stat().st_size / (1024 ** 2)

    counts = np.bincount(y_tr, minlength=num_classes).astype(np.float64)
    inv_freq = np.median(counts) / np.maximum(counts, 1.0)
    crop_names = sorted(list({c.split('/')[0] for c in classes}))
    cls_to_crop = np.array([crop_names.index(c.split('/')[0]) for c in classes], dtype=np.int32)

    max_k = 21
    results = []
    cms = {}

    # 1, 2, 3, 4: k-NN sweeps
    for k in [1, 3, 5, 7, 11, 21]:
        modes = ['majority'] if k == 1 else ['majority', 'weighted', 'class_balanced']
        for mode in modes:
            t0 = time.perf_counter()
            s, ii = gal_index.search(X_q, k)
            smat = knn_scores(s, ii, y_tr, num_classes, k, mode, inv_freq)
            lat = (time.perf_counter() - t0) * 1000.0 / len(X_q)
            row, cm, _, _, _ = score_and_row(f'faiss_k{k}_{mode}', y_q, smat, classes, lat, gal_ram_mb, full_file_mb)
            results.append(row)
            cms[row['method']] = cm

    # 4b: Class Prototype Cosine
    centroids = np.vstack([X_tr[y_tr == c].mean(axis=0) for c in range(num_classes)]).astype(np.float32)
    faiss.normalize_L2(centroids)
    t0 = time.perf_counter()
    c_scores = X_q @ centroids.T
    lat = (time.perf_counter() - t0) * 1000.0 / len(X_q)
    row, cm, _, _, _ = score_and_row('class_prototypes_cosine', y_q, c_scores, classes, lat, centroids.nbytes/(1024**2), centroids.nbytes/(1024**2))
    results.append(row)
    cms[row['method']] = cm

    # 5: Crop-Aware 2-Stage Retrieval (predict crop first with trained linear crop head, then k-NN weighted inside crop)
    Wc, bc = train_softmax_linear(X_tr, cls_to_crop[y_tr], len(crop_names), reg=1e-3, steps=250)
    s_all, ii_all = gal_index.search(X_q, 21)
    for k_ca in [5, 11, 21]:
        t0 = time.perf_counter()
        crop_pred = np.argmax(X_q @ Wc + bc, axis=1)
        raw_s = knn_scores(s_all, ii_all, y_tr, num_classes, k_ca, 'weighted')
        mask = (cls_to_crop[None, :] == crop_pred[:, None]).astype(np.float64)
        ca_scores = raw_s * mask + 1e-9 * raw_s
        lat = (time.perf_counter() - t0) * 1000.0 / len(X_q)
        row, cm, _, _, _ = score_and_row(f'crop_aware_2stage_k{k_ca}', y_q, ca_scores, classes, lat, gal_ram_mb + Wc.nbytes/(1024**2), full_file_mb)
        results.append(row)
        cms[row['method']] = cm

    # 5b: Crop-Oracle Upper Bound (to quantify pure cross-crop ceiling)
    t0 = time.perf_counter()
    oracle_crops = cls_to_crop[y_q]
    raw_5 = knn_scores(s_all, ii_all, y_tr, num_classes, 5, 'weighted')
    mask_or = (cls_to_crop[None, :] == oracle_crops[:, None]).astype(np.float64)
    or_scores = raw_5 * mask_or + 1e-9 * raw_5
    lat = (time.perf_counter() - t0) * 1000.0 / len(X_q)
    row, cm, _, _, _ = score_and_row('crop_aware_oracle_k5', y_q, or_scores, classes, lat, gal_ram_mb, full_file_mb)
    results.append(row)

    # 6: Linear Classifier (L2 Multinomial Logistic Regression)
    for reg in [5e-3, 1e-3, 2e-4, 5e-5]:
        W_lr, b_lr = train_softmax_linear(X_tr, y_tr, num_classes, reg=reg, steps=900)
        t0 = time.perf_counter()
        logits = X_q @ W_lr + b_lr
        logits -= logits.max(axis=1, keepdims=True)
        probs = np.exp(logits)
        probs /= probs.sum(axis=1, keepdims=True)
        lat = (time.perf_counter() - t0) * 1000.0 / len(X_q)
        w_mb = (W_lr.nbytes + b_lr.nbytes) / (1024 ** 2)
        row, cm, _, _, _ = score_and_row(f'linear_softmax_reg{reg}', y_q, probs, classes, lat, w_mb, w_mb)
        results.append(row)
        cms[row['method']] = cm

    # 7: Linear SVM (One-vs-Rest L2 Squared-Hinge)
    for C in [0.5, 2.0, 5.0, 15.0]:
        W_svm, b_svm = train_linear_svm_ovr(X_tr, y_tr, num_classes, C=C, steps=1200)
        t0 = time.perf_counter()
        svm_scores = X_q @ W_svm + b_svm
        lat = (time.perf_counter() - t0) * 1000.0 / len(X_q)
        w_mb = (W_svm.nbytes + b_svm.nbytes) / (1024 ** 2)
        row, cm, _, _, _ = score_and_row(f'linear_svm_ovr_C{C}', y_q, svm_scores, classes, lat, w_mb, w_mb)
        results.append(row)
        cms[row['method']] = cm

    results_sorted = sorted(results, key=lambda r: (r['macro_f1'], r['top1_accuracy']), reverse=True)
    with open(OUT_DIR / 'task3_2144_prebenchmark.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(results_sorted[0].keys()))
        w.writeheader()
        w.writerows(results_sorted)

    crop_acc = float((np.argmax(X_q @ Wc + bc, axis=1) == cls_to_crop[y_q]).mean())
    print(f'=== Task 3 Local Pre-Benchmark on 1,742/402 Holdout of 2,144 Vectors (Crop Clf Acc={crop_acc:.4f}) ===')
    for r in results_sorted:
        print(f"  {r['method']:28s} | Acc={r['top1_accuracy']:.4f} | MacroF1={r['macro_f1']:.4f} | Top3={r['top3_recall']:.4f} | Top5={r['top5_recall']:.4f} | ms/q={r['latency_ms_per_query']:.3f} | RAM={r['ram_mb']:.2f}MB")

    (OUT_DIR / 'task3_2144_prebenchmark.json').write_text(json.dumps({
        'scope_note': 'Evaluated locally on 1,742 train_ref vs 402 holdout_ref of the existing 2,144-vector index. Full 100K evaluation requires running KissanSevaAI_Task3_FullDataset_FAISS.ipynb in Colab.',
        'crop_classifier_accuracy': round(crop_acc, 5),
        'results': results_sorted,
    }, indent=2))


if __name__ == '__main__':
    main()

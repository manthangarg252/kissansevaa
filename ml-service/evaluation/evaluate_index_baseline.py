"""Task 2: Reproducible Baseline Evaluation & Audit for Existing 2,144-Vector DINOv2-S/14 + FAISS Index.

Zero external dependencies beyond ml-service requirements (numpy, faiss-cpu, onnxruntime, Pillow).
Evaluates and saves:
1. Deterministic stratified 80/20 reference split + 5-fold CV split over all 2,144 index vectors (`index_2144_splits.csv`).
2. Full LOO-CV & 80/20 holdout reference metrics (Top-1, Top-3, Top-5, Macro-F1, Macro-Precision, Macro-Recall,
   67x67 confusion matrix, per-class P/R/F1 & error analysis for ALL 67 classes).
3. Pairwise vector near-duplicate & cross-boundary leakage audit inside the 2,144 index vectors.
4. Benchmark on all deduplicated genuine leaves, synthetic images, UI screenshots, food/non-leaf photos,
   and corrupted uploads in `backend/uploads/`.
"""
import csv
import hashlib
import json
import time
from pathlib import Path

import faiss
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
MODEL_DIR = ROOT / 'ml-service' / 'model_assets'
OUT_DIR = Path(__file__).resolve().parent
UPLOADS_DIR = ROOT / 'backend' / 'uploads'


def load_assets():
    meta = json.loads((MODEL_DIR / 'model_meta.json').read_text())
    classes = json.loads((MODEL_DIR / 'classes.json').read_text())
    labels = np.load(MODEL_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(MODEL_DIR / 'crop_index.faiss'))
    vectors = np.vstack([index.reconstruct(i) for i in range(index.ntotal)]).astype(np.float32)
    session = ort.InferenceSession(str(MODEL_DIR / 'dinov2_vits14_cls_mean.onnx'), providers=['CPUExecutionProvider'])
    return meta, classes, labels, index, vectors, session


def preprocess(img: Image.Image, meta: dict) -> np.ndarray:
    img = ImageOps.exif_transpose(img).convert('RGB')
    w, h = img.size
    short = int(meta.get('resize_short_side', 256))
    if w < h:
        nw = short
        nh = int(round(short * h / w))
    else:
        nh = short
        nw = int(round(short * w / h))
    img = img.resize((nw, nh), resample=Image.Resampling.BILINEAR)
    size = int(meta.get('image_size', 224))
    left = (nw - size) // 2
    top = (nh - size) // 2
    img = img.crop((left, top, left + size, top + size))
    arr = np.asarray(img, dtype=np.float32).transpose(2, 0, 1) / 255.0
    mean = np.asarray(meta.get('mean', [0.485, 0.456, 0.406]), dtype=np.float32)[:, None, None]
    std = np.asarray(meta.get('std', [0.229, 0.224, 0.225]), dtype=np.float32)[:, None, None]
    return ((arr - mean) / std)[None].astype('float32')


def build_confusion_matrix(y_true: np.ndarray, y_pred: np.ndarray, num_classes: int) -> np.ndarray:
    cm = np.zeros((num_classes, num_classes), dtype=np.int64)
    for yt, yp in zip(y_true, y_pred):
        cm[int(yt), int(yp)] += 1
    return cm


def compute_classification_metrics(cm: np.ndarray):
    tp = np.diag(cm).astype(np.float64)
    fp = cm.sum(axis=0).astype(np.float64) - tp
    fn = cm.sum(axis=1).astype(np.float64) - tp
    support = cm.sum(axis=1).astype(np.int64)

    precision = np.divide(tp, tp + fp, out=np.zeros_like(tp), where=(tp + fp) > 0)
    recall = np.divide(tp, tp + fn, out=np.zeros_like(tp), where=(tp + fn) > 0)
    f1 = np.divide(2 * precision * recall, precision + recall, out=np.zeros_like(tp), where=(precision + recall) > 0)

    acc = float(tp.sum() / max(cm.sum(), 1))
    macro_prec = float(precision.mean())
    macro_rec = float(recall.mean())
    macro_f1 = float(f1.mean())
    weighted_f1 = float((f1 * support).sum() / max(support.sum(), 1))
    return acc, macro_prec, macro_rec, macro_f1, weighted_f1, precision, recall, f1, support


def eval_topk_hits(y_true: np.ndarray, neighbor_labels: np.ndarray, k: int) -> float:
    hits = 0
    for yt, row in zip(y_true, neighbor_labels):
        unique_topk = list(dict.fromkeys(row.tolist()))[:k]
        if int(yt) in unique_topk:
            hits += 1
    return float(hits / len(y_true))


def stratified_split_indices(labels: np.ndarray, test_ratio: float = 0.20, n_folds: int = 5, seed: int = 42):
    rng = np.random.default_rng(seed)
    train_idx, hold_idx = [], []
    fold_col = np.zeros(len(labels), dtype=np.int32)
    for c in np.unique(labels):
        c_idx = np.where(labels == c)[0].copy()
        rng.shuffle(c_idx)
        n_hold = max(1, int(round(len(c_idx) * test_ratio)))
        hold_idx.extend(c_idx[:n_hold].tolist())
        train_idx.extend(c_idx[n_hold:].tolist())
        for i, idx in enumerate(c_idx):
            fold_col[idx] = i % n_folds
    return np.array(sorted(train_idx), dtype=np.int32), np.array(sorted(hold_idx), dtype=np.int32), fold_col


def compute_suite(name: str, y_true: np.ndarray, y_pred: np.ndarray, neighbor_classes: np.ndarray, sims_top1: np.ndarray, classes: list):
    cm = build_confusion_matrix(y_true, y_pred, len(classes))
    acc, macro_prec, macro_rec, macro_f1, weighted_f1, _, _, _, _ = compute_classification_metrics(cm)
    top3_distinct = eval_topk_hits(y_true, neighbor_classes, 3)
    top5_distinct = eval_topk_hits(y_true, neighbor_classes, 5)

    crops = [c.split('/')[0] for c in classes]
    total_errors = int((y_true != y_pred).sum())
    same_crop_errors = sum(1 for yt, yp in zip(y_true, y_pred) if yt != yp and crops[yt] == crops[yp])
    cross_crop_errors = total_errors - same_crop_errors

    print(f'\n--- {name} (n={len(y_true)}, classes={len(classes)}) ---')
    print(f'Top-1 Accuracy:    {acc:.4f} ({int((y_true == y_pred).sum())}/{len(y_true)})')
    print(f'Top-3 Accuracy:    {top3_distinct:.4f}')
    print(f'Top-5 Accuracy:    {top5_distinct:.4f}')
    print(f'Macro-F1:          {macro_f1:.4f}')
    print(f'Macro-Precision:   {macro_prec:.4f}')
    print(f'Macro-Recall:      {macro_rec:.4f}')
    print(f'Weighted-F1:       {weighted_f1:.4f}')
    print(f'Errors breakdown:  total={total_errors} | same-crop={same_crop_errors} | cross-crop={cross_crop_errors}')
    print(f'Top-1 Cosine Sim:  mean={sims_top1.mean():.4f}, median={np.median(sims_top1):.4f}, p10={np.percentile(sims_top1, 10):.4f}, min={sims_top1.min():.4f}, max={sims_top1.max():.4f}')

    return {
        'protocol': name,
        'n_queries': int(len(y_true)),
        'top1_accuracy': round(acc, 5),
        'top3_accuracy': round(top3_distinct, 5),
        'top5_accuracy': round(top5_distinct, 5),
        'macro_f1': round(macro_f1, 5),
        'macro_precision': round(macro_prec, 5),
        'macro_recall': round(macro_rec, 5),
        'weighted_f1': round(weighted_f1, 5),
        'total_errors': total_errors,
        'same_crop_errors': same_crop_errors,
        'cross_crop_errors': cross_crop_errors,
        'top1_similarity_stats': {
            'mean': round(float(sims_top1.mean()), 5),
            'median': round(float(np.median(sims_top1)), 5),
            'p10': round(float(np.percentile(sims_top1, 10)), 5),
            'min': round(float(sims_top1.min()), 5),
            'max': round(float(sims_top1.max()), 5),
        },
    }, cm


def main():
    meta, classes, labels, index, X, session = load_assets()
    n, d = X.shape
    print(f'Loaded {n} L2-normalized {d}-D vectors across {len(classes)} classes. Production k={meta.get("k")}, vote={meta.get("vote_method")}')

    # 1. Deterministic split & 5-Fold assignment saved to disk
    train_idx, hold_idx, fold_col = stratified_split_indices(labels, test_ratio=0.20, n_folds=5, seed=42)
    split_col = np.array(['train_ref'] * n, dtype=object)
    split_col[hold_idx] = 'holdout_ref'

    with open(OUT_DIR / 'index_2144_splits.csv', 'w', newline='') as f:
        w = csv.writer(f)
        w.writerow(['vector_row_id', 'class_id', 'class_name', 'split_80_20', 'cv5_fold'])
        for i in range(n):
            w.writerow([i, int(labels[i]), classes[int(labels[i])], split_col[i], int(fold_col[i])])

    # 2. Full LOO-CV across all 2,144 vectors
    sims_all, nbrs_all = index.search(X, 25)
    loo_nbr_labels = []
    loo_top1_sims = []
    for row_id in range(n):
        keep = nbrs_all[row_id] != row_id
        filtered_nbrs = nbrs_all[row_id][keep][:20]
        filtered_sims = sims_all[row_id][keep][:20]
        loo_nbr_labels.append(labels[filtered_nbrs])
        loo_top1_sims.append(float(filtered_sims[0]))

    loo_nbr_labels = np.array(loo_nbr_labels, dtype=np.int32)
    loo_top1_sims = np.array(loo_top1_sims, dtype=np.float32)
    loo_pred_k1 = loo_nbr_labels[:, 0]

    loo_summary, cm = compute_suite(
        'LOO-CV on 2,144 Reference Vectors (k=1 baseline)',
        labels, loo_pred_k1, loo_nbr_labels, loo_top1_sims, classes
    )

    with open(OUT_DIR / 'baseline_confusion_matrix_67x67.csv', 'w', newline='') as f:
        w = csv.writer(f)
        w.writerow(['true\\pred'] + classes)
        for cid, cname in enumerate(classes):
            w.writerow([cname] + cm[cid].tolist())

    _, _, _, _, _, prec, rec, f1_cls, sup = compute_classification_metrics(cm)
    per_class_rows = []
    for cid, cname in enumerate(classes):
        row_cm = cm[cid].copy()
        row_cm[cid] = 0
        top_err_cid = int(row_cm.argmax())
        top_err_cnt = int(row_cm[top_err_cid])
        per_class_rows.append({
            'class_id': cid,
            'class_name': cname,
            'crop': cname.split('/')[0],
            'precision': round(float(prec[cid]), 4),
            'recall': round(float(rec[cid]), 4),
            'f1_score': round(float(f1_cls[cid]), 4),
            'support': int(sup[cid]),
            'errors': int(sup[cid] - cm[cid, cid]),
            'most_confused_with': classes[top_err_cid] if top_err_cnt > 0 else 'NONE',
            'most_confused_count': top_err_cnt,
        })
    per_class_sorted = sorted(per_class_rows, key=lambda r: (r['f1_score'], r['recall'], r['class_name']))
    with open(OUT_DIR / 'baseline_per_class_67.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(per_class_sorted[0].keys()))
        w.writeheader()
        w.writerows(per_class_sorted)

    print('\nLowest 15 performing classes (LOO-CV k=1 on 2,144 vectors):')
    for r in per_class_sorted[:15]:
        print(f"  {r['class_name']:38s} | P={r['precision']:.3f} | R={r['recall']:.3f} | F1={r['f1_score']:.3f} | err={r['errors']:2d}/32 | top_conf={r['most_confused_with']} ({r['most_confused_count']})")

    # 3. Deterministic 80/20 Stratified Holdout Reference Evaluation
    sub_index = faiss.IndexFlatIP(d)
    sub_index.add(X[train_idx])
    y_gal = labels[train_idx]
    h_sims, h_nbrs = sub_index.search(X[hold_idx], 20)
    h_nbr_labels = y_gal[h_nbrs]
    h_pred_k1 = h_nbr_labels[:, 0]
    holdout_summary, _ = compute_suite(
        'Deterministic 80/20 Reference Split within 2,144 Index (k=1 baseline)',
        labels[hold_idx], h_pred_k1, h_nbr_labels, h_sims[:, 0], classes
    )

    # k sweep inside LOO-CV
    k_compare = []
    print('\n=== k-Value Retrieval Reference inside 2,144 Index (LOO-CV) ===')
    for k in [1, 3, 5, 7, 11, 21]:
        for method in (['majority'] if k == 1 else ['majority', 'weighted']):
            preds = []
            for row_id in range(n):
                keep = nbrs_all[row_id] != row_id
                ids = labels[nbrs_all[row_id][keep][:k]]
                vals = sims_all[row_id][keep][:k]
                w = np.ones(len(ids)) if method == 'majority' else np.exp(10.0 * (vals - vals.max()))
                preds.append(int(np.bincount(ids, weights=w, minlength=len(classes)).argmax()))
            preds = np.array(preds, dtype=np.int32)
            cm_k = build_confusion_matrix(labels, preds, len(classes))
            acc_k, _, _, f1_k, _, _, _, _, _ = compute_classification_metrics(cm_k)
            k_compare.append({'k': k, 'method': method, 'loo_accuracy': round(acc_k, 5), 'loo_macro_f1': round(f1_k, 5)})
            print(f'  k={k:2d} ({method:8s}): acc={acc_k:.4f} | macro_f1={f1_k:.4f}')

    # 4. Pairwise Near-Duplicate & Vector Leakage Audit
    sim_mat = X @ X.T
    iu, ju = np.triu_indices(n, k=1)
    pair_sims = sim_mat[iu, ju]
    same_cls = labels[iu] == labels[ju]

    dup_thresholds = [0.9999, 0.99, 0.98, 0.95, 0.90]
    dup_audit = []
    print('\n=== Pairwise Cosine Similarity / Near-Duplicate Audit (2,144 Vectors) ===')
    for th in dup_thresholds:
        mask = pair_sims >= th
        sc = int((mask & same_cls).sum())
        cc = int((mask & ~same_cls).sum())
        dup_audit.append({'threshold': th, 'same_class_pairs': sc, 'cross_class_pairs': cc, 'total_pairs': sc + cc})
        print(f'  cosine >= {th:.4f}: same_class={sc:4d}, cross_class={cc:4d}, total={sc+cc:4d}')

    cross_split_max = h_sims[:, 0]
    leak_ge_99 = int((cross_split_max >= 0.99).sum())
    leak_ge_98 = int((cross_split_max >= 0.98).sum())
    leak_ge_95 = int((cross_split_max >= 0.95).sum())
    print(f'80/20 Split Cross-Boundary Near-Duplicates: >=0.99: {leak_ge_99}/{len(hold_idx)} | >=0.98: {leak_ge_98}/{len(hold_idx)} | >=0.95: {leak_ge_95}/{len(hold_idx)}')

    cross_idx = np.where(~same_cls)[0]
    top_cross_order = cross_idx[np.argsort(pair_sims[cross_idx])[::-1][:10]]
    cross_class_top_pairs = []
    print('\nTop 10 Cross-Class Closest Pairs Inside 2,144 Index:')
    for idx_pair in top_cross_order:
        i, j = int(iu[idx_pair]), int(ju[idx_pair])
        s = float(pair_sims[idx_pair])
        c1, c2 = classes[int(labels[i])], classes[int(labels[j])]
        cross_class_top_pairs.append({'row_a': i, 'class_a': c1, 'row_b': j, 'class_b': c2, 'cosine': round(s, 5)})
        print(f'  cos={s:.4f} | {c1} (row {i}) <-> {c2} (row {j})')

    # 5. Deduplicated Local Images Audit (Genuine, Synthetic, Non-Leaf, Corrupt)
    seen_hashes = {}
    for p in sorted(UPLOADS_DIR.iterdir()):
        if not p.is_file():
            continue
        h = hashlib.sha256(p.read_bytes()).hexdigest()[:12]
        if h not in seen_hashes:
            seen_hashes[h] = p

    CATEGORY_MAP = {
        '9650f149d622': ('corrupted_truncated_stub', 'Corrupted 134B JPEG stub'),
        'c04bee9d6592': ('corrupted_invalid_bytes', 'Invalid 13B file (bad_image.jpg)'),
        '7fd69521123e': ('genuine_leaf_macro', 'Wheat leaf with yellow/stripe rust pustules'),
        'fb1cd8f2e5cb': ('genuine_leaf_lab', 'Diseased broadleaf with brown spots next to ruler'),
        '214f39816c34': ('genuine_leaf_display', 'Apple spotted leaf display capture'),
        'fff756f4eccb': ('genuine_leaf_camera_rot', 'Samsung camera capture (rotated Apple leaf display)'),
        'd1e2ade673a4': ('non_leaf_food_camera', 'Samsung camera photo of roti/flatbread on pan'),
        '33afe176c56e': ('non_leaf_ui_screenshot', 'App UI screenshot (Traders screen)'),
        'f79897d1c7d4': ('non_leaf_ui_screenshot', 'App UI screenshot (Login screen)'),
        'c30e06e79898': ('synthetic_canvas_leaf', 'Synthetic green card: Tomato Early Blight'),
        '68b662efc773': ('synthetic_canvas_leaf', 'Synthetic green card: Wheat Yellow Rust'),
        '02253d2f3840': ('synthetic_canvas_leaf', 'Synthetic green card: Wheat Yellow Rust v2'),
        'db321b7ec20b': ('synthetic_canvas_animal', 'Synthetic tan card: Livestock Cow'),
        'd391e629a4aa': ('synthetic_canvas_animal', 'Synthetic tan card: Livestock Buffalo'),
    }

    upload_results = []
    print('\n=== Deduplicated Local Image & Non-Leaf Audit (backend/uploads/) ===')
    for h, p in seen_hashes.items():
        cat, desc = CATEGORY_MAP.get(h, ('unknown', p.name))
        entry = {'sha256_12': h, 'filename': p.name, 'category': cat, 'description': desc}
        try:
            t0 = time.perf_counter()
            with Image.open(p) as im:
                im.load()
                tensor = preprocess(im, meta)
            emb = session.run(None, {'image': tensor})[0]
            faiss.normalize_L2(emb)
            sims, nbrs = index.search(emb, 5)
            latency_ms = (time.perf_counter() - t0) * 1000.0
            scores = sims[0]
            nbr_labels = labels[nbrs[0]]
            pred_k1 = classes[int(nbr_labels[0])]
            weights = np.exp(10.0 * (scores - scores.max()))
            votes = np.bincount(nbr_labels, weights=weights, minlength=len(classes))
            agree_5 = float(votes[votes.argmax()] / max(votes.sum(), 1e-12))
            needs_review = bool(agree_5 < 0.60 or float(scores[0]) < 0.50)

            entry.update({
                'valid_image': True,
                'top1_pred': pred_k1,
                'top1_cosine_similarity': round(float(scores[0]), 5),
                'neighbor_agreement_5nn': round(agree_5, 5),
                'needs_review_current': needs_review,
                'latency_ms': round(latency_ms, 2),
                'top3_matches': [
                    {'class': classes[int(c)], 'sim': round(float(s), 4)}
                    for c, s in zip(nbr_labels[:3], scores[:3])
                ],
            })
            print(f'[{cat:25s}] {p.name:40s} -> {pred_k1:30s} | sim={scores[0]:.4f} | 5nn_agree={agree_5:.3f} | rev={needs_review}')
        except Exception as e:
            entry.update({'valid_image': False, 'error': str(e)})
            print(f'[{cat:25s}] {p.name:40s} -> REJECTED CORRUPT ({str(e)[:45]})')
        upload_results.append(entry)

    full_report = {
        'metadata': {
            'model': meta.get('model'),
            'embedding_dim': int(d),
            'index_vectors': int(n),
            'classes': int(len(classes)),
            'production_k': int(meta.get('k', 1)),
            'production_vote_method': meta.get('vote_method'),
            'colab_reported_val_macro_f1': meta.get('validation_macro_f1'),
            'colab_reported_test_accuracy': meta.get('test_accuracy'),
            'colab_reported_test_macro_f1': meta.get('test_macro_f1'),
        },
        'loo_cv_baseline': loo_summary,
        'holdout_80_20_within_index': holdout_summary,
        'k_sweep_within_index': k_compare,
        'near_duplicate_audit': dup_audit,
        'cross_boundary_80_20_leakage': {
            'ge_0_99': leak_ge_99,
            'ge_0_98': leak_ge_98,
            'ge_0_95': leak_ge_95,
            'total_queries': int(len(hold_idx)),
        },
        'top_cross_class_pairs': cross_class_top_pairs,
        'local_upload_benchmark': upload_results,
    }
    (OUT_DIR / 'baseline_report_2144.json').write_text(json.dumps(full_report, indent=2))
    print(f'\nSaved reproducible baseline artifacts to {OUT_DIR}/')


if __name__ == '__main__':
    main()

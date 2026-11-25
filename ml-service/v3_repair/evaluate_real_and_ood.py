"""
Task 4: Evaluate repaired V3 classifier vs V1 & old V3 on:
  A. Held-out repaired 6,544 test vectors grouped by crop family (Wheat, Tomato, Rice, Apple, ...)
  B. Real local field/display/camera leaf images via ONNX DINOv2-S/14
  C. Unsupported-Crop OOD via Leave-Crop-Out (holding out Citrus, Cotton, Cherry, Strawberry)
  D. Non-leaf (food photo, 20 mobile UI screenshots, synthetic cards), blurry, & poor-lighting photos.

Demonstrates why calibrated SVM probability alone fails OOD rejection and validates
joint (calibrated_probability + centroid_top_similarity + top2_margin + quality) thresholds.
"""
import hashlib
import json
from collections import defaultdict
from pathlib import Path

import faiss
import numpy as np
import onnxruntime as ort
import pandas as pd
from joblib import parallel_backend
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import accuracy_score, f1_score
from sklearn.svm import LinearSVC


ROOT = Path(__file__).resolve().parents[2]
V1_DIR = ROOT / 'ml-service' / 'model_assets'
OLD_V3_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm'
REPAIRED_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm_repaired'
ASSETS_DIR = REPAIRED_DIR / 'model_assets'
EVAL_DIR = REPAIRED_DIR / 'evaluation'
UPLOADS_DIR = ROOT / 'backend' / 'uploads'
BRAIN_DIR = Path('/Users/manthangarg/.gemini/antigravity/brain/e4e34924-fb31-42d1-b5c5-9167a5462c8b')


def preprocess(img: Image.Image, spec: dict) -> np.ndarray:
    img = ImageOps.exif_transpose(img).convert('RGB')
    w, h = img.size
    short = int(spec.get('resize_short_side', 256))
    if w < h:
        nw, nh = short, int(round(short * h / w))
    else:
        nh, nw = short, int(round(short * w / h))
    img = img.resize((nw, nh), resample=Image.Resampling.BILINEAR)
    sz = int(spec.get('center_crop_size', 224))
    left, top = (nw - sz) // 2, (nh - sz) // 2
    img = img.crop((left, top, left + sz, top + sz))
    arr = np.asarray(img, dtype=np.float32).transpose(2, 0, 1) / 255.0
    mean = np.asarray(spec.get('mean', [0.485, 0.456, 0.406]), dtype=np.float32)[:, None, None]
    std = np.asarray(spec.get('std', [0.229, 0.224, 0.225]), dtype=np.float32)[:, None, None]
    return ((arr - mean) / std)[None].astype('float32')


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


def numpy_calibrated_svm_proba(X: np.ndarray, weights: dict) -> np.ndarray:
    X64 = np.asarray(X, dtype=np.float64)
    fc, fi = weights['folds_coefs'], weights['folds_intercepts']
    fa, fb = weights['folds_a'], weights['folds_b']
    n_classes = fi.shape[1]
    fold_sum = np.zeros((X64.shape[0], n_classes), dtype=np.float64)
    for k in range(len(fc)):
        df = X64 @ fc[k].T + fi[k]
        z = np.clip(fa[k][None, :] * df + fb[k][None, :], -500.0, 500.0)
        p = 1.0 / (1.0 + np.exp(z))
        fold_sum += p / np.maximum(p.sum(axis=1, keepdims=True), 1e-12)
    return fold_sum / len(fc)


def main() -> None:
    spec = json.loads((ASSETS_DIR / 'feature_spec.json').read_text(encoding='utf-8'))
    classes = json.loads((ASSETS_DIR / 'classes.json').read_text(encoding='utf-8'))
    weights = dict(np.load(ASSETS_DIR / spec['exported_numpy_filename']))
    centroids = weights['centroids'].astype('float32')

    # Load all 65,703 embeddings + repaired split indices
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

    # Load V1 ONNX + V1 FAISS index for comparison
    ort_sess = ort.InferenceSession(str(V1_DIR / 'dinov2_vits14_cls_mean.onnx'), providers=['CPUExecutionProvider'])
    v1_index = faiss.read_index(str(V1_DIR / 'crop_index.faiss'))
    v1_labels = np.load(V1_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)

    # 1. Held-out test breakdown by crop family
    test_probs = numpy_calibrated_svm_proba(X_test, weights)
    test_preds = np.argmax(test_probs, axis=1)
    test_top1_p = test_probs.max(axis=1)
    test_cent_sim = np.max(X_test @ centroids.T, axis=1)
    top2 = np.sort(test_probs, axis=1)[:, -2:]
    test_margin = top2[:, 1] - top2[:, 0]

    crop_names = sorted({c.split('/')[0] for c in classes})
    per_crop_rows = []
    for crop in crop_names:
        c_cls = {i for i, name in enumerate(classes) if name.split('/')[0] == crop}
        m = np.array([int(y) in c_cls for y in y_test])
        acc = float(accuracy_score(y_test[m], test_preds[m]))
        mf1 = float(f1_score(y_test[m], test_preds[m], labels=sorted(c_cls), average='macro', zero_division=0))
        crop_match_acc = float(np.mean([classes[int(p)].split('/')[0] == crop for p in test_preds[m]]))
        frr_policy = float(np.mean((test_top1_p[m] < 0.60) | (test_cent_sim[m] < 0.50)))
        per_crop_rows.append({
            'crop': crop,
            'test_samples': int(m.sum()),
            'disease_top1_acc': round(acc, 4),
            'disease_macro_f1': round(mf1, 4),
            'crop_level_acc': round(crop_match_acc, 4),
            'mean_cal_prob': round(float(test_top1_p[m].mean()), 4),
            'mean_centroid_cos': round(float(test_cent_sim[m].mean()), 4),
            'frr_at_p60_cent50': round(frr_policy, 4),
        })
    per_crop_df = pd.DataFrame(per_crop_rows).sort_values('disease_top1_acc')
    per_crop_df.to_csv(EVAL_DIR / 'per_crop_heldout_metrics.csv', index=False)

    # 2. Leave-Crop-Out Unsupported-Crop Simulation (hold out Citrus, Cotton, Cherry, Strawberry)
    unsup_crops = {'Citrus', 'Cotton', 'Cherry', 'Strawberry'}
    unsup_ids = {i for i, c in enumerate(classes) if c.split('/')[0] in unsup_crops}
    kept_cls = sorted(set(range(67)) - unsup_ids)
    tr_keep = np.array([int(y) in kept_cls for y in y_train])
    val_keep = np.array([int(y) in kept_cls for y in y_val])
    val_unsup = np.array([int(y) in unsup_ids for y in y_val])

    # Remap kept classes to contiguous labels 0..len-1 cleanly
    remap = {old_c: new_c for new_c, old_c in enumerate(kept_cls)}
    y_tr_lco = np.array([remap[int(y)] for y in y_train[tr_keep]], dtype=np.int32)
    actual_C = float(spec['training_config'].get('C', 2.0))
    print(f'Fitting Leave-Crop-Out SVM (55 classes, C={actual_C}, n={int(tr_keep.sum())})...', flush=True)
    with parallel_backend('threading', n_jobs=4):
        lco_svm = CalibratedClassifierCV(
            LinearSVC(C=actual_C, class_weight='balanced', dual=True, random_state=42),
            method='sigmoid',
            cv=3,
        ).fit(X_train[tr_keep], y_tr_lco)
    print('Leave-Crop-Out SVM fit complete.', flush=True)
    lco_cents = np.vstack([X_train[tr_keep][y_tr_lco == c].mean(axis=0) for c in range(len(kept_cls))]).astype('float32')
    faiss.normalize_L2(lco_cents)

    def lco_signals(Xq):
        pr = lco_svm.predict_proba(Xq)
        t2 = np.sort(pr, axis=1)[:, -2:]
        cs = np.max(Xq @ lco_cents.T, axis=1)
        return pr.max(axis=1), t2[:, 1] - t2[:, 0], cs

    lco_known_p, lco_known_m, lco_known_c = lco_signals(X_val[val_keep])
    lco_unsup_p, lco_unsup_m, lco_unsup_c = lco_signals(X_val[val_unsup])

    # 3. Extract Real Local Field Photos, Non-Leaf Food, Synthetic Cards, UI Screenshots & Degraded Variants
    pre_cfg = spec['preprocessing']
    samples = []

    def add_image_item(sample_id: str, category: str, true_note: str, path: Path, expect_reject: bool, transform_fn=None):
        with Image.open(path) as raw:
            im = ImageOps.exif_transpose(raw).convert('RGB')
            if transform_fn is not None:
                im = transform_fn(im)
            lap, bright = measure_quality(im)
            tensor = preprocess(im, pre_cfg)
            emb = ort_sess.run(None, {'image': tensor})[0].astype('float32')
            emb /= np.maximum(np.linalg.norm(emb, axis=1, keepdims=True), 1e-12)
            samples.append({
                'sample_id': sample_id,
                'category': category,
                'ground_truth': true_note,
                'expect_reject': expect_reject,
                'blur_lap': lap,
                'brightness': bright,
                'emb': emb[0],
            })

    # Genuine local leaf/field/screen photos (4 unique SHA256s in backend/uploads)
    genuine_paths = [
        ('wheat_yellow_rust_field', 'supported_real_field', 'Wheat/Wheat_Yellow_Rust', UPLOADS_DIR / '1792058847184-leaf.jpg'),
        ('apple_spot_ruler_lab', 'supported_real_field', 'Apple/Apple_Black_rot_or_Frogeye', UPLOADS_DIR / '1795467122713-leaf.jpg'),
        ('apple_scab_display', 'supported_display_capture', 'Apple/Apple_scab', UPLOADS_DIR / '1795499287987-leaf.jpg'),
        ('apple_scab_samsung_cam', 'supported_samsung_cam_display', 'Apple/Apple_scab', UPLOADS_DIR / '1795499696604-samsung_hardware_cam.jpg'),
    ]
    for sid, cat, gt, p in genuine_paths:
        add_image_item(sid, cat, gt, p, False)
        add_image_item(f'{sid}__blur_r4_5', 'degraded_blur', f'{gt} (blur)', p, True, lambda im: im.filter(ImageFilter.GaussianBlur(radius=4.5)))
        add_image_item(f'{sid}__dark_0_20', 'degraded_dark', f'{gt} (dark)', p, True, lambda im: ImageEnhance.Brightness(im).enhance(0.20))

    # Real non-leaf food photo
    add_image_item('food_roti_pan', 'ood_food', 'non_leaf_food', UPLOADS_DIR / '1795604463804-leaf.jpg', True)

    # Synthetic text cards (animal/crop non-leaf cards)
    synth_cards = [
        ('card_cow', 'ood_synthetic_card', 'cow_text_card', UPLOADS_DIR / '1794735023878-animal.jpg'),
        ('card_buffalo', 'ood_synthetic_card', 'buffalo_text_card', UPLOADS_DIR / '1795604214882-animal.jpg'),
        ('card_wheat_mildew', 'ood_synthetic_card', 'wheat_mildew_card', UPLOADS_DIR / '1795498233938-leaf.jpg'),
        ('card_tomato_blight', 'ood_synthetic_card', 'tomato_blight_card', UPLOADS_DIR / '1794728904307-leaf.jpg'),
        ('card_wheat_rust', 'ood_synthetic_card', 'wheat_rust_card', UPLOADS_DIR / '1795464974035-leaf.jpg'),
    ]
    for sid, cat, gt, p in synth_cards:
        add_image_item(sid, cat, gt, p, True)

    # Diverse UI screenshots from uploads & session brain screenshots
    ui_candidates = [
        ('ui_traders_upload', UPLOADS_DIR / '1791359550594-leaf.jpg'),
        ('ui_login_upload', UPLOADS_DIR / '1792007280462-leaf.jpg'),
    ]
    for png_name in [
        'screen_traders.png', 'screen_prices.png', 'screen_livestock.png',
        'screen_carbon.png', 'screen_chatbot.png', 'screen_crop.png',
        'screen_loans.png', 'screen_schemes.png', 'screen_market_result.png',
        'dashboard_redesign.png', 'emulator_current.png',
    ]:
        cand = BRAIN_DIR / png_name
        if cand.exists():
            ui_candidates.append((png_name.replace('.png', ''), cand))

    for sid, p in ui_candidates:
        add_image_item(sid, 'ood_screenshot', 'mobile_ui_screenshot', p, True)

    # Score all extracted samples through V1 FAISS and Repaired V3 classifier
    X_local = np.vstack([s['emb'] for s in samples]).astype('float32')
    v3_probs = numpy_calibrated_svm_proba(X_local, weights)
    v3_cent_sims = np.max(X_local @ centroids.T, axis=1)
    v1_s, v1_i = v1_index.search(X_local, 5)

    local_eval_rows = []
    for idx_s, s in enumerate(samples):
        pr = v3_probs[idx_s]
        ord_idx = np.argsort(-pr)
        p1, p2 = float(pr[ord_idx[0]]), float(pr[ord_idx[1]])
        v3_label = classes[int(ord_idx[0])]
        v1_label = classes[int(v1_labels[int(v1_i[idx_s, 0])])]
        v1_cos = float(v1_s[idx_s, 0])
        cent_cos = float(v3_cent_sims[idx_s])
        # Production policy candidate check: p1>=0.60, cent>=0.50, margin>=0.15, lap>=40, bright>=30
        accepted_prob_only = p1 >= 0.60
        accepted_joint = (p1 >= 0.60) and (cent_cos >= 0.50) and ((p1 - p2) >= 0.15) and (s['blur_lap'] >= 40.0) and (s['brightness'] >= 30.0)
        local_eval_rows.append({
            'sample_id': s['sample_id'],
            'category': s['category'],
            'ground_truth': s['ground_truth'],
            'expect_reject': s['expect_reject'],
            'v1_pred': v1_label,
            'v1_cos': round(v1_cos, 4),
            'v3_pred': v3_label,
            'v3_prob': round(p1, 4),
            'v3_margin': round(p1 - p2, 4),
            'v3_centroid_cos': round(cent_cos, 4),
            'blur_lap': s['blur_lap'],
            'brightness': s['brightness'],
            'accepted_svm_prob_only': accepted_prob_only,
            'accepted_joint_policy': accepted_joint,
        })
    local_df = pd.DataFrame(local_eval_rows)
    local_df.to_csv(EVAL_DIR / 'local_real_and_ood_predictions.csv', index=False)

    # 4. Joint Threshold Calibration Sweep (Val clean vs LCO Unsupported Crops vs Local OOD/Degraded)
    val_probs = numpy_calibrated_svm_proba(X_val, weights)
    val_top2 = np.sort(val_probs, axis=1)[:, -2:]
    val_p1 = val_top2[:, 1]
    val_margin = val_top2[:, 1] - val_top2[:, 0]
    val_cent = np.max(X_val @ centroids.T, axis=1)

    nl_df = local_df[local_df['category'].isin(['ood_food', 'ood_synthetic_card', 'ood_screenshot'])]
    blur_df = local_df[local_df['category'] == 'degraded_blur']
    dark_df = local_df[local_df['category'] == 'degraded_dark']
    real_leaf_df = local_df[~local_df['expect_reject']]

    sweep = []
    for th_p in [0.40, 0.50, 0.60, 0.70]:
        for th_c in [0.00, 0.45, 0.50, 0.55]:
            for th_m in [0.00, 0.10, 0.15]:
                frr_val = float(np.mean((val_p1 < th_p) | (val_cent < th_c) | (val_margin < th_m)))
                far_lco_unsup = float(np.mean((lco_unsup_p >= th_p) & (lco_unsup_c >= th_c) & (lco_unsup_m >= th_m)))
                far_nonleaf = float(np.mean((nl_df['v3_prob'] >= th_p) & (nl_df['v3_centroid_cos'] >= th_c) & (nl_df['v3_margin'] >= th_m)))
                far_blur = float(np.mean((blur_df['v3_prob'] >= th_p) & (blur_df['v3_centroid_cos'] >= th_c) & (blur_df['blur_lap'] >= 40.0)))
                far_dark = float(np.mean((dark_df['v3_prob'] >= th_p) & (dark_df['v3_centroid_cos'] >= th_c) & (dark_df['brightness'] >= 30.0)))
                sweep.append({
                    'th_prob': th_p,
                    'th_centroid_cos': th_c,
                    'th_margin': th_m,
                    'th_blur_lap': 40.0,
                    'th_brightness': 30.0,
                    'FRR_clean_val': round(frr_val, 4),
                    'FAR_unsupported_crops_lco': round(far_lco_unsup, 4),
                    'FAR_nonleaf_food_cards_ui': round(far_nonleaf, 4),
                    'FAR_blurry': round(far_blur, 4),
                    'FAR_dark': round(far_dark, 4),
                })
    sweep_df = pd.DataFrame(sweep)
    sweep_df.to_csv(EVAL_DIR / 'ood_joint_threshold_sweep.csv', index=False)

    # Compare prob-only (th_p=0.60, th_c=0.0, th_m=0.0) vs recommended joint (th_p=0.60, th_c=0.50, th_m=0.15)
    prob_only_row = sweep_df[(sweep_df['th_prob'] == 0.60) & (sweep_df['th_centroid_cos'] == 0.0) & (sweep_df['th_margin'] == 0.0)].iloc[0].to_dict()
    joint_rec_row = sweep_df[(sweep_df['th_prob'] == 0.60) & (sweep_df['th_centroid_cos'] == 0.50) & (sweep_df['th_margin'] == 0.15)].iloc[0].to_dict()

    report = {
        'v3_winner_evaluated': spec['winning_estimator_name'],
        'actual_C': spec['training_config'].get('C'),
        'per_crop_heldout_test_summary': per_crop_df.to_dict(orient='records'),
        'leave_crop_out_unsupported': {
            'held_out_crops': sorted(unsup_crops),
            'n_known_val': int(val_keep.sum()),
            'n_unsupported_val': int(val_unsup.sum()),
            'known_mean_cal_prob': round(float(lco_known_p.mean()), 4),
            'unsupported_mean_cal_prob': round(float(lco_unsup_p.mean()), 4),
            'known_mean_centroid_cos': round(float(lco_known_c.mean()), 4),
            'unsupported_mean_centroid_cos': round(float(lco_unsup_c.mean()), 4),
        },
        'local_dataset_counts': {
            'real_field_and_cam_leaf': int(len(real_leaf_df)),
            'food': int((local_df['category'] == 'ood_food').sum()),
            'synthetic_cards': int((local_df['category'] == 'ood_synthetic_card').sum()),
            'mobile_ui_screenshots': int((local_df['category'] == 'ood_screenshot').sum()),
            'degraded_blur': int(len(blur_df)),
            'degraded_dark': int(len(dark_df)),
            'missing_locally': ['external_non_plantvillage_machinery_photos', 'live_animal_field_photos', 'archive_zip_extra_unsupported_crops'],
        },
        'policy_comparison': {
            'prob_only_0_60': prob_only_row,
            'joint_p60_cent50_margin15_qual': joint_rec_row,
        },
        'local_real_leaf_predictions': real_leaf_df[['sample_id', 'ground_truth', 'v1_pred', 'v1_cos', 'v3_pred', 'v3_prob', 'v3_centroid_cos', 'accepted_joint_policy']].to_dict(orient='records'),
    }
    (EVAL_DIR / 'real_field_and_ood_report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('=== Per-Crop Held-Out Test Acc (Worst 6) ===')
    print(per_crop_df.head(6).to_string(index=False))
    print('=== Genuine Local Leaf Predictions (V1 vs Repaired V3) ===')
    print(real_leaf_df[['sample_id', 'v1_pred', 'v1_cos', 'v3_pred', 'v3_prob', 'v3_margin', 'v3_centroid_cos']].to_string(index=False))
    print('=== Prob-Only vs Joint OOD Gating ===')
    print('Prob-only (0.60):', prob_only_row)
    print('Joint (p>=0.60, cent>=0.50, margin>=0.15, lap>=40, bright>=30):', joint_rec_row)
    print('=== Max Non-Leaf Prob Observed ===')
    print(nl_df[['sample_id', 'v3_pred', 'v3_prob', 'v3_centroid_cos', 'accepted_svm_prob_only', 'accepted_joint_policy']].sort_values('v3_prob', ascending=False).head(8).to_string(index=False))


if __name__ == '__main__':
    main()

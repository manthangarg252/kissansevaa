"""Task 3 Section 7: Uncertainty & OOD Rejection Evaluation Script (Local Empirical Calibration).

Evaluates held-out OOD, quality degradation, and unsupported crops locally using:
1. Clean held-out reference vectors from 80/20 split (In-Distribution known diseases -> should ACCEPT)
2. Simulated Unsupported Crops via Leave-Crop-Out on real 768-D vectors (e.g., hold out ALL Citrus & Cotton vectors
   so FAISS/classifier has never seen them -> measures Unsupported-Crop FAR vs Known-Crop FRR)
3. Real local Non-Leaf images (`1795604463804-leaf.jpg` roti on pan, UI screenshots, synthetic cards)
4. Genuine Leaf Photos (`1792058847184-leaf.jpg`, `1795467122713-leaf.jpg`, `1795499696604-samsung_hardware_cam.jpg`)
5. Progressive Blur (`GaussianBlur radius=3.5, 5.0`) and Poor Lighting (`Brightness=0.20`) applied to genuine photos.

Outputs threshold sweep table (`ood_local_calibration.csv`) & summary (`ood_local_report.json`).
"""
import csv
import hashlib
import json
from pathlib import Path

import faiss
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageEnhance, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[2]
MODEL_DIR = ROOT / 'ml-service' / 'model_assets'
OUT_DIR = Path(__file__).resolve().parent
UPLOADS_DIR = ROOT / 'backend' / 'uploads'


def preprocess(img: Image.Image, meta: dict) -> np.ndarray:
    img = ImageOps.exif_transpose(img).convert('RGB')
    w, h = img.size
    short = int(meta.get('resize_short_side', 256))
    if w < h:
        nw, nh = short, int(round(short * h / w))
    else:
        nh, nw = short, int(round(short * w / h))
    img = img.resize((nw, nh), resample=Image.Resampling.BILINEAR)
    size = int(meta.get('image_size', 224))
    left, top = (nw - size) // 2, (nh - size) // 2
    img = img.crop((left, top, left + size, top + size))
    arr = np.asarray(img, dtype=np.float32).transpose(2, 0, 1) / 255.0
    mean = np.asarray(meta.get('mean', [0.485, 0.456, 0.406]), dtype=np.float32)[:, None, None]
    std = np.asarray(meta.get('std', [0.229, 0.224, 0.225]), dtype=np.float32)[:, None, None]
    return ((arr - mean) / std)[None].astype('float32')


def measure_image_quality(img: Image.Image):
    """Computes pure-NumPy 4-neighbor discrete Laplacian variance (blur) and brightness without OpenCV dep."""
    gray = ImageOps.exif_transpose(img).convert('L').resize((256, 256), Image.Resampling.BILINEAR)
    g = np.asarray(gray, dtype=np.float32)
    lap = g[:-2, 1:-1] + g[2:, 1:-1] + g[1:-1, :-2] + g[1:-1, 2:] - 4.0 * g[1:-1, 1:-1]
    return float(lap.var()), float(g.mean())


def main():
    meta = json.loads((MODEL_DIR / 'model_meta.json').read_text())
    classes = json.loads((MODEL_DIR / 'classes.json').read_text())
    labels = np.load(MODEL_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(MODEL_DIR / 'crop_index.faiss'))
    X_all = np.vstack([index.reconstruct(i) for i in range(index.ntotal)]).astype(np.float32)
    sess = ort.InferenceSession(str(MODEL_DIR / 'dinov2_vits14_cls_mean.onnx'), providers=['CPUExecutionProvider'])

    # 1. Leave-Crop-Out Unsupported Crop Evaluation on the 80/20 split
    # Hold out ALL classes belonging to Citrus & Cotton as "unsupported crops"
    train_idx, hold_idx = [], []
    with open(OUT_DIR / 'index_2144_splits.csv') as f:
        for row in csv.DictReader(f):
            rid = int(row['vector_row_id'])
            if row['split_80_20'] == 'train_ref':
                train_idx.append(rid)
            else:
                hold_idx.append(rid)
    train_idx, hold_idx = np.array(train_idx), np.array(hold_idx)

    unsupported_crops = {'Citrus', 'Cotton'}
    unsupported_cls_ids = {i for i, c in enumerate(classes) if c.split('/')[0] in unsupported_crops}

    keep_gal = np.array([i for i in train_idx if int(labels[i]) not in unsupported_cls_ids], dtype=np.int32)
    id_known_q = np.array([i for i in hold_idx if int(labels[i]) not in unsupported_cls_ids], dtype=np.int32)
    ood_unsup_q = np.array([i for i in hold_idx if int(labels[i]) in unsupported_cls_ids], dtype=np.int32)

    sub_idx = faiss.IndexFlatIP(X_all.shape[1])
    sub_idx.add(X_all[keep_gal])
    y_sub = labels[keep_gal]

    def query_stats(idx_obj, y_ref, X_q):
        sims, nbrs = idx_obj.search(X_q, 10)
        top1_sim = sims[:, 0]
        # Cosine margin between Top-1 class and best *different* class among top 10
        margins = []
        agrees = []
        for ss, ii in zip(sims[:, :5], nbrs[:, :5]):
            nc = y_ref[ii]
            w = np.exp(10.0 * (ss - ss.max()))
            tallies = np.bincount(nc, weights=w, minlength=len(classes))
            agrees.append(float(tallies.max() / max(tallies.sum(), 1e-9)))
        for ss, ii in zip(sims, nbrs):
            nc = y_ref[ii]
            c1 = nc[0]
            diff = ss[nc != c1]
            c2_sim = float(diff[0]) if len(diff) > 0 else float(ss[-1])
            margins.append(float(ss[0]) - c2_sim)
        return top1_sim, np.array(agrees), np.array(margins)

    id_sim, id_agree, id_margin = query_stats(sub_idx, y_sub, X_all[id_known_q])
    unsup_sim, unsup_agree, unsup_margin = query_stats(sub_idx, y_sub, X_all[ood_unsup_q])

    print(f'In-Distribution Known Crops (n={len(id_known_q)}):    sim mean={id_sim.mean():.4f} p10={np.percentile(id_sim, 10):.4f} | agree mean={id_agree.mean():.3f}')
    print(f'Unsupported Crops Citrus+Cotton (n={len(ood_unsup_q)}): sim mean={unsup_sim.mean():.4f} p90={np.percentile(unsup_sim, 90):.4f} | agree mean={unsup_agree.mean():.3f}')

    # 2. Local Real/Synthetic/Non-Leaf & Stress Degradation (Blur + Dark) Evaluation
    dedup = {}
    for p in sorted(UPLOADS_DIR.iterdir()):
        if not p.is_file():
            continue
        h = hashlib.sha256(p.read_bytes()).hexdigest()[:12]
        if h not in dedup:
            dedup[h] = p

    targets = [
        ('genuine_wheat_macro', dedup['7fd69521123e'], False),
        ('genuine_apple_ruler', dedup['fb1cd8f2e5cb'], False),
        ('genuine_apple_screen', dedup['214f39816c34'], False),
        ('genuine_apple_rot_cam', dedup['fff756f4eccb'], False),
        ('ood_food_roti_cam', dedup['d1e2ade673a4'], True),
        ('ood_ui_traders', dedup['33afe176c56e'], True),
        ('ood_ui_login', dedup['f79897d1c7d4'], True),
        ('ood_synth_wheat', dedup['02253d2f3840'], True),
        ('ood_synth_cow', dedup['db321b7ec20b'], True),
    ]

    stress_rows = []
    print('\n=== Quality & OOD Stress Benchmark on Local Images (2,144 Full Index) ===')
    for name, path, is_ood in targets:
        with Image.open(path) as raw:
            base = ImageOps.exif_transpose(raw).convert('RGB')
            variants = [('orig', base, is_ood)]
            if not is_ood:
                variants.append(('blur_r5', base.filter(ImageFilter.GaussianBlur(radius=5.0)), True))
                variants.append(('dark_0.2', ImageEnhance.Brightness(base).enhance(0.20), True))
            for vname, im, expect_reject in variants:
                lap_var, bright = measure_image_quality(im)
                t = preprocess(im, meta)
                emb = sess.run(None, {'image': t})[0]
                faiss.normalize_L2(emb)
                sim, agree, margin = query_stats(index, labels, emb)
                s0, a0, m0 = float(sim[0]), float(agree[0]), float(margin[0])
                stress_rows.append({
                    'sample': f'{name}__{vname}',
                    'expected_ood_or_unusable': expect_reject,
                    'top1_sim': round(s0, 4),
                    'agree_5nn': round(a0, 4),
                    'margin_top2': round(m0, 4),
                    'laplacian_blur_var': round(lap_var, 2),
                    'brightness': round(bright, 2),
                })
                print(f'  {name}__{vname:8s} | OOD={str(expect_reject):5s} | sim={s0:.4f} | agree={a0:.3f} | margin={m0:.4f} | lap={lap_var:7.1f} | bright={bright:5.1f}')

    # 3. Threshold Sweep (using Leave-Crop-Out + Local Non-Leaf/Degraded Stress Items)
    ood_vecs_sim = np.concatenate([unsup_sim, np.array([r['top1_sim'] for r in stress_rows if r['expected_ood_or_unusable']])])
    ood_vecs_ag = np.concatenate([unsup_agree, np.array([r['agree_5nn'] for r in stress_rows if r['expected_ood_or_unusable']])])
    ood_vecs_blur = np.concatenate([np.full(len(unsup_sim), 200.0), np.array([r['laplacian_blur_var'] for r in stress_rows if r['expected_ood_or_unusable']])])
    ood_vecs_bright = np.concatenate([np.full(len(unsup_sim), 128.0), np.array([r['brightness'] for r in stress_rows if r['expected_ood_or_unusable']])])

    id_vecs_blur = np.full(len(id_sim), 200.0)
    id_vecs_bright = np.full(len(id_sim), 128.0)

    sweeps = []
    for th_sim in [0.45, 0.50, 0.55, 0.60, 0.65, 0.70]:
        for th_ag in [0.40, 0.55, 0.60, 0.70]:
            for th_blur in [0.0, 40.0]:
                acc_id = (id_sim >= th_sim) & (id_agree >= th_ag) & (id_vecs_blur >= th_blur) & (id_vecs_bright >= 30.0)
                acc_ood = (ood_vecs_sim >= th_sim) & (ood_vecs_ag >= th_ag) & (ood_vecs_blur >= th_blur) & (ood_vecs_bright >= 30.0)
                acc_unsup = (unsup_sim >= th_sim) & (unsup_agree >= th_ag)

                frr_id = float((~acc_id).mean())
                far_all_ood = float(acc_ood.mean())
                far_unsup = float(acc_unsup.mean())
                sweeps.append({
                    'th_sim': th_sim,
                    'th_agree': th_ag,
                    'th_blur_lap': th_blur,
                    'FRR_known_crops': round(frr_id, 4),
                    'FAR_unsupported_crops': round(far_unsup, 4),
                    'FAR_combined_ood_and_degraded': round(far_all_ood, 4),
                })

    with open(OUT_DIR / 'ood_local_calibration.csv', 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(sweeps[0].keys()))
        w.writeheader()
        w.writerows(sweeps)

    sweeps_sorted = sorted(sweeps, key=lambda r: (r['FAR_combined_ood_and_degraded'] + 0.8 * r['FRR_known_crops']))
    print('\nTop Balanced Policy Candidates (Minimizing Combined OOD FAR + Known FRR):')
    for s in sweeps_sorted[:10]:
        print(f"  sim>={s['th_sim']:.2f}, agree>={s['th_agree']:.2f}, blur>={s['th_blur_lap']:4.1f} | FRR(known)={s['FRR_known_crops']:.3f} | FAR(unsup)={s['FAR_unsupported_crops']:.3f} | FAR(all_ood)={s['FAR_combined_ood_and_degraded']:.3f}")

    (OUT_DIR / 'ood_local_report.json').write_text(json.dumps({
        'in_dist_count': int(len(id_known_q)),
        'unsupported_crop_leave_out_count': int(len(ood_unsup_q)),
        'stress_image_tests': stress_rows,
        'top_policies': sweeps_sorted[:10],
    }, indent=2))


if __name__ == '__main__':
    main()

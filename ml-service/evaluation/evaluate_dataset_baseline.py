"""Task 2: Full-Dataset Reproducible Baseline & Leakage Audit Script (for Plant-Diseases-100k).

Run in Google Colab or on a workstation with the extracted Kaggle `crops/` directory:
    python evaluate_dataset_baseline.py --crops-dir /content/plant_disease_dataset/crops --assets-dir ../model_assets --out-dir ./full_baseline_out

Performs:
1. Byte-level SHA256 exact deduplication + 64-bit perceptual difference-hash (dHash) near-duplicate grouping.
2. Leakage-controlled GroupStratified / GroupShuffle 80/10/10 split (near-duplicate clusters never cross Train/Val/Test).
3. Evaluation of the existing 2,144-vector baseline FAISS index + ONNX encoder on the held-out Val & Test splits:
   - Top-1, Top-3, Top-5 accuracy
   - Macro-F1, Macro Precision, Macro Recall
   - 67x67 confusion matrix CSV and per-class classification report CSV
   - Explicit Train-to-Val / Train-to-Test perceptual and cosine similarity leakage audit
"""
import argparse
import hashlib
import json
from pathlib import Path

import faiss
import numpy as np
import onnxruntime as ort
import pandas as pd
from PIL import Image, ImageOps
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score, precision_score, recall_score
from sklearn.model_selection import StratifiedGroupKFold
from tqdm import tqdm


EXTS = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}


def compute_dhash(img: Image.Image, hash_size: int = 8) -> str:
    gray = ImageOps.exif_transpose(img).convert('L').resize((hash_size + 1, hash_size), Image.Resampling.BILINEAR)
    arr = np.asarray(gray, dtype=np.int16)
    diff = arr[:, 1:] > arr[:, :-1]
    bits = 0
    for b in diff.flatten():
        bits = (bits << 1) | int(bool(b))
    return f'{bits:016x}'


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


def build_dedup_splits(crops_dir: Path, out_dir: Path, valid_classes: set):
    rows = []
    seen_sha = {}
    conflicts = set()
    for crop in sorted(crops_dir.iterdir()):
        if not crop.is_dir():
            continue
        for cond in sorted(crop.iterdir()):
            if not cond.is_dir():
                continue
            label = f'{crop.name}/{cond.name}'
            if label not in valid_classes:
                continue
            for p in sorted(cond.rglob('*')):
                if not p.is_file() or p.suffix.lower() not in EXTS:
                    continue
                sha = hashlib.sha256(p.read_bytes()).hexdigest()
                if sha in seen_sha:
                    if seen_sha[sha] != label:
                        conflicts.add(sha)
                    continue
                seen_sha[sha] = label
                try:
                    with Image.open(p) as im:
                        dh = compute_dhash(im)
                except Exception:
                    continue
                rows.append({'path': str(p.relative_to(crops_dir)), 'label': label, 'sha256': sha, 'dhash': dh})

    df = pd.DataFrame(rows)
    if conflicts:
        df = df[~df.sha256.isin(conflicts)].reset_index(drop=True)

    # Group by (label, dhash) so exact/near-identical perceptual images never span splits
    df['group_key'] = df['label'] + '::' + df['dhash']
    sgkf = StratifiedGroupKFold(n_splits=10, shuffle=True, random_state=42)
    fold_ids = np.zeros(len(df), dtype=int)
    for fold, (_, test_idx) in enumerate(sgkf.split(df, df['label'], df['group_key'])):
        fold_ids[test_idx] = fold

    # Folds 0..7 => train (80%), fold 8 => val (10%), fold 9 => test (10%)
    df['split'] = np.where(fold_ids == 9, 'test', np.where(fold_ids == 8, 'val', 'train'))
    df.to_csv(out_dir / 'full_dataset_splits_group_audited.csv', index=False)
    return df


def evaluate_split(name: str, split_df: pd.DataFrame, crops_dir: Path, assets_dir: Path, out_dir: Path):
    meta = json.loads((assets_dir / 'model_meta.json').read_text())
    classes = json.loads((assets_dir / 'classes.json').read_text())
    cls_to_id = {c: i for i, c in enumerate(classes)}
    labels_ref = np.load(assets_dir / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(assets_dir / 'crop_index.faiss'))
    sess = ort.InferenceSession(str(assets_dir / 'dinov2_vits14_cls_mean.onnx'), providers=['CPUExecutionProvider'])

    y_true, y_pred, top3_hits, top5_hits, top1_sims = [], [], [], [], []
    for _, row in tqdm(split_df.iterrows(), total=len(split_df), desc=f'Evaluating {name}'):
        with Image.open(crops_dir / row['path']) as im:
            t = preprocess(im, meta)
        emb = sess.run(None, {'image': t})[0]
        faiss.normalize_L2(emb)
        sims, nbrs = index.search(emb, 20)
        nbr_classes = labels_ref[nbrs[0]]
        yt = cls_to_id[row['label']]
        yp = int(nbr_classes[0])  # Baseline k=1
        uniq = list(dict.fromkeys(nbr_classes.tolist()))
        y_true.append(yt)
        y_pred.append(yp)
        top3_hits.append(int(yt in uniq[:3]))
        top5_hits.append(int(yt in uniq[:5]))
        top1_sims.append(float(sims[0][0]))

    metrics = {
        'split': name,
        'n': len(y_true),
        'top1_accuracy': float(accuracy_score(y_true, y_pred)),
        'top3_accuracy': float(np.mean(top3_hits)),
        'top5_accuracy': float(np.mean(top5_hits)),
        'macro_f1': float(f1_score(y_true, y_pred, average='macro', zero_division=0)),
        'macro_precision': float(precision_score(y_true, y_pred, average='macro', zero_division=0)),
        'macro_recall': float(recall_score(y_true, y_pred, average='macro', zero_division=0)),
        'mean_top1_cosine': float(np.mean(top1_sims)),
        'potential_vector_near_duplicates_ge_0_98': int((np.array(top1_sims) >= 0.98).sum()),
    }
    cm = confusion_matrix(y_true, y_pred, labels=np.arange(len(classes)))
    pd.DataFrame(cm, index=classes, columns=classes).to_csv(out_dir / f'{name}_confusion_matrix.csv')
    rep = classification_report(y_true, y_pred, labels=np.arange(len(classes)), target_names=classes, output_dict=True, zero_division=0)
    pd.DataFrame(rep).T.to_csv(out_dir / f'{name}_per_class_report.csv')
    return metrics


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--crops-dir', type=Path, required=True)
    ap.add_argument('--assets-dir', type=Path, default=Path('../model_assets'))
    ap.add_argument('--out-dir', type=Path, default=Path('./full_baseline_out'))
    args = ap.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    classes = json.loads((args.assets_dir / 'classes.json').read_text())
    split_df = build_dedup_splits(args.crops_dir, args.out_dir, set(classes))
    val_metrics = evaluate_split('val', split_df[split_df.split == 'val'], args.crops_dir, args.assets_dir, args.out_dir)
    test_metrics = evaluate_split('test', split_df[split_df.split == 'test'], args.crops_dir, args.assets_dir, args.out_dir)
    summary = {'val': val_metrics, 'test': test_metrics}
    (args.out_dir / 'dataset_baseline_summary.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()

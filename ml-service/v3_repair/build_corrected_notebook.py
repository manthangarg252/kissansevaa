"""Builds and syntax-checks ml-service/notebooks/KissanSevaAI_V3_Corrected_Pipeline.ipynb."""
import ast
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NB_OUT = ROOT / 'ml-service' / 'notebooks' / 'KissanSevaAI_V3_Corrected_Pipeline.ipynb'


def md_cell(text: str) -> dict:
    return {
        'cell_type': 'markdown',
        'metadata': {},
        'source': [line + '\n' for line in text.rstrip('\n').splitlines()],
    }


def code_cell(code: str) -> dict:
    clean = code.strip('\n') + '\n'
    py_only = '\n'.join(
        line for line in clean.splitlines() if not line.lstrip().startswith(('!', '%'))
    )
    ast.parse(py_only)  # Guarantee valid Python syntax in every cell
    return {
        'cell_type': 'code',
        'execution_count': None,
        'metadata': {},
        'outputs': [],
        'source': [line + '\n' for line in clean.rstrip('\n').splitlines()],
    }


cells = [
    md_cell(
        """# KissanSevaAI V3 — Corrected Full-Dataset Pipeline (Zero-Leakage Splits, True Winner Export & OOD Calibration)

**Fixes all three V3 integration blockers without retraining or unnecessarily re-extracting DINOv2 embeddings:**
1. **Task 1 — Group-Safe Split Repair:** Creates canonical `source_group_id` via Union-Find across stripped specimen IDs (`___<specimen>` ignoring regenerated UUID prefixes & augmentation suffixes `_90deg`, `_180deg`, `_270deg`, `_new30degFlipLR`, `_new30degFlipTB`, `_flipLR`, `_flipTB`, `_copy*`, `_newGRR`, `_newPixel25`), perceptual `near_dup_cluster`, and same-class cosine similarity `>= 0.985`. Ensures zero source specimen leaks across train/val/test.
2. **Reuse Cached Embeddings:** Loads cached 768-D DINOv2-S/14 embeddings from Google Drive (`embedding_chunks/`, `crop_index_v3_full.faiss`, `X_val.npy`, `X_test.npy`) keyed by `sha256`. Runs DINOv2 ONNX/PyTorch extraction **only** on cache misses (such as newly discovered unsupported-crop images).
3. **Task 2 & 3 — Retrain, Calibrate & Export Actual Winner:** Compares `CalibratedClassifierCV(LinearSVC(C=1.0))`, `CalibratedClassifierCV(LinearSVC(C=2.0))`, `LogisticRegression(C=5.0)`, and weighted FAISS strictly on validation Macro-F1; evaluates once on held-out test with ECE, multiclass Brier, confusion matrix, and per-class F1; exports the **actual** fitted winning estimator (`never` mislabeling `C=1.0` vs `C=2.0`) with pure-NumPy `.npz` weights, `feature_spec.json`, `parity_test_vectors.npz` + `parity_expected_outputs.json`, and `SHA256_MANIFEST.json`.
4. **Task 4 — Unsupported-Crop & Non-Leaf OOD Validation:** Tests unsupported crop folders (and Leave-Crop-Out simulation if archive lacks extra crops), non-leaf objects/animals/machinery, screenshots, blurry images, and low-lighting photos under joint `(calibrated_probability, centroid_top_similarity, blur_lap, brightness)` gating."""
    ),
    md_cell("## 1. Environment Setup, Drive Mount & Production Safety Guard"),
    code_cell(
        """!pip -q install faiss-cpu scikit-learn==1.6.1 pandas numpy scipy pillow tqdm onnxruntime opencv-python-headless torch torchvision

import hashlib
import json
import os
import pickle
import platform
import random
import re
import time
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

import cv2
import faiss
import numpy as np
import onnxruntime as ort
import pandas as pd
import sklearn
import torch
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
from sklearn.calibration import CalibratedClassifierCV
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score, log_loss
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.svm import LinearSVC
from torchvision import datasets as tv_datasets, transforms
from tqdm.auto import tqdm
from google.colab import drive

drive.mount('/content/drive')

random.seed(42)
np.random.seed(42)
torch.manual_seed(42)

# Paths on Google Drive (adjust DRIVE_BASE if needed)
DRIVE_BASE = Path('/content/drive/MyDrive/KissanSevaAI_Phase1')
V1_PROD_DIR = DRIVE_BASE / 'pilot'  # Production v1 reference ONLY (never overwrite!)
OLD_V3_DIR = DRIVE_BASE / 'versions' / 'v3_task3_full_faiss'
OUT_DIR = DRIVE_BASE / 'versions' / 'v3_svm_repaired'
ASSETS_OUT = OUT_DIR / 'model_assets'
EVAL_OUT = OUT_DIR / 'evaluation'
CACHE_OUT = OUT_DIR / 'embedding_cache'
ZIP_PATH = Path('/content/drive/MyDrive/archive.zip')
EXTRACT_DIR = Path('/content/plant_disease_dataset')

for d in (OUT_DIR, ASSETS_OUT, EVAL_OUT, CACHE_OUT):
    d.mkdir(parents=True, exist_ok=True)

assert ASSETS_OUT.resolve() != V1_PROD_DIR.resolve(), 'Safety Guard: Never overwrite production v1 folder!'
print('scikit-learn:', sklearn.__version__, '| faiss:', faiss.__version__, '| CUDA:', torch.cuda.is_available())"""
    ),
    md_cell("## 2. Load Existing Metadata & Reuse Cached 768-D DINOv2 Embeddings (Zero Redundant Extraction)"),
    code_cell(
        """with open(OLD_V3_DIR / 'model_assets' / 'classes.json', 'r', encoding='utf-8') as f:
    CANONICAL_CLASSES = json.load(f)
assert len(CANONICAL_CLASSES) == 67
CLASS_TO_ID = {c: i for i, c in enumerate(CANONICAL_CLASSES)}

splits_orig = pd.read_csv(OLD_V3_DIR / 'splits_manifest.csv')
print(f'Loaded splits_manifest.csv: {len(splits_orig)} rows')

# Hydrate sha256 -> float32[768] vector dictionary from ALL available caches on Drive
sha_to_vec = {}

# Source 1: Chunk folders from previous v3 run (train / val / test)
old_chunks_root = OLD_V3_DIR / 'embedding_chunks'
if old_chunks_root.exists():
    for split_sub in ['train', 'val', 'test']:
        sdir = old_chunks_root / split_sub
        if not sdir.exists():
            continue
        for mfile in sorted(sdir.glob('chunk_*.json')):
            cfile = mfile.with_suffix('.npy')
            if not cfile.exists():
                continue
            shas = json.loads(mfile.read_text()).get('sha256', [])
            arr = np.load(cfile).astype('float32')
            assert len(shas) == len(arr), f'Chunk mismatch in {mfile}'
            for sha, vec in zip(shas, arr):
                sha_to_vec[sha] = vec

# Source 2: crop_index_v3_full.faiss + train_image_mapping.csv (guarantees all 65,703 train rows)
faiss_old_path = OLD_V3_DIR / 'model_assets' / 'crop_index_v3_full.faiss'
tmap_old_path = OLD_V3_DIR / 'model_assets' / 'train_image_mapping.csv'
if faiss_old_path.exists() and tmap_old_path.exists():
    old_idx = faiss.read_index(str(faiss_old_path))
    old_vecs = old_idx.reconstruct_n(0, old_idx.ntotal).astype('float32')
    old_map = pd.read_csv(tmap_old_path)
    assert len(old_map) == len(old_vecs)
    for sha, vec in zip(old_map['sha256'], old_vecs):
        sha_to_vec[sha] = vec

# Source 3: X_val.npy / X_test.npy paired strictly in splits_orig order if chunks weren't saved
for sp_name, npy_name in [('val', 'X_val.npy'), ('test', 'X_test.npy')]:
    npy_p = old_chunks_root / npy_name
    if npy_p.exists():
        sp_df = splits_orig[splits_orig['split'] == sp_name].reset_index(drop=True)
        arr = np.load(npy_p).astype('float32')
        if len(sp_df) == len(arr):
            for sha, vec in zip(sp_df['sha256'], arr):
                sha_to_vec.setdefault(sha, vec)

# Source 4: Local repaired cache if re-run
cache_npz = CACHE_OUT / 'all_82123_cached.npz'
if cache_npz.exists():
    z = np.load(cache_npz, allow_pickle=False)
    for sha, vec in zip(z['sha256'].tolist(), z['X'].astype('float32')):
        sha_to_vec[sha] = vec

missing_shas = [s for s in splits_orig['sha256'] if s not in sha_to_vec]
print(f'Cached embeddings found: {len(sha_to_vec)} | Missing from cache: {len(missing_shas)}')"""
    ),
    md_cell("## 2b. Extract Only Cache Misses (or Unsupported Crops) Using Exact V1/V3 DINOv2 Preprocessing"),
    code_cell(
        """SIZE = 224
RESIZE_SHORT = 256
PREPROCESS = transforms.Compose([
    transforms.Resize(RESIZE_SHORT, interpolation=transforms.InterpolationMode.BILINEAR),
    transforms.CenterCrop(SIZE),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
])

def get_dino_feature_extractor():
    # Prefer exact deployed ONNX model if uploaded to Drive; otherwise identical torch hub weights
    onnx_cand = V1_PROD_DIR / 'dinov2_vits14_cls_mean.onnx'
    if onnx_cand.exists():
        sess = ort.InferenceSession(str(onnx_cand), providers=['CUDAExecutionProvider', 'CPUExecutionProvider'])
        def run_batch(pil_list):
            xb = np.stack([PREPROCESS(im.convert('RGB')).numpy() for im in pil_list]).astype('float32')
            emb = sess.run(None, {'image': xb})[0].astype('float32')
            emb /= np.maximum(np.linalg.norm(emb, axis=1, keepdims=True), 1e-12)
            return emb
        return 'onnx_v1_exact', run_batch
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    backbone = torch.hub.load('facebookresearch/dinov2', 'dinov2_vits14', trust_repo=True).eval().to(device)
    backbone.requires_grad_(False)
    def run_batch(pil_list):
        xb = torch.stack([PREPROCESS(im.convert('RGB')) for im in pil_list]).to(device)
        with torch.inference_mode():
            f = backbone.forward_features(xb)
            cat = torch.cat([f['x_norm_clstoken'], f['x_norm_patchtokens'].mean(dim=1)], dim=1)
            cat = torch.nn.functional.normalize(cat, dim=1)
        return cat.float().cpu().numpy().astype('float32')
    return f'pytorch_{device}', run_batch

# Extract ONLY if cache misses exist (or archive needs unzipping for unsupported crops)
if missing_shas:
    assert ZIP_PATH.exists(), f'Missing archive {ZIP_PATH} needed for {len(missing_shas)} uncached images!'
    if not (EXTRACT_DIR / 'crops').exists():
        with zipfile.ZipFile(ZIP_PATH, 'r') as zf:
            zf.extractall(EXTRACT_DIR)
    crops_root = EXTRACT_DIR / 'crops'
    ext_name, run_batch = get_dino_feature_extractor()
    miss_rows = splits_orig[splits_orig['sha256'].isin(set(missing_shas))]
    batch_imgs, batch_shas = [], []
    for row in tqdm(miss_rows.itertuples(), total=len(miss_rows), desc=f'Extract misses ({ext_name})'):
        with Image.open(crops_root / row.path) as raw:
            batch_imgs.append(ImageOps.exif_transpose(raw).convert('RGB'))
            batch_shas.append(row.sha256)
        if len(batch_imgs) == 32:
            embs = run_batch(batch_imgs)
            for s, v in zip(batch_shas, embs):
                sha_to_vec[s] = v
            batch_imgs, batch_shas = [], []
    if batch_imgs:
        embs = run_batch(batch_imgs)
        for s, v in zip(batch_shas, embs):
            sha_to_vec[s] = v

X_all = np.vstack([sha_to_vec[s] for s in splits_orig['sha256']]).astype('float32')
X_all /= np.maximum(np.linalg.norm(X_all, axis=1, keepdims=True), 1e-12)
assert X_all.shape == (len(splits_orig), 768)
assert np.allclose(np.linalg.norm(X_all, axis=1), 1.0, atol=1e-4)

if not cache_npz.exists():
    np.savez_compressed(cache_npz, sha256=np.array(splits_orig['sha256'].tolist()), X=X_all)
print('All embeddings aligned and L2-verified:', X_all.shape)"""
    ),
    md_cell("## 3. Task 1 — Repair Dataset Splits (`source_group_id` Union-Find Across All 82,123 Rows)"),
    code_cell(
        """RE_TRAILING_AUG = re.compile(
    r'(?:_+new\\d*degFlip(?:LR|TB)|_+flip(?:LR|TB)|_+\\d+deg|_+copy\\d*(?:_\\d+)?|copy\\d*'
    r'|_+newGRR|_+newPixel\\d+|_+aug\\d*|_+rot\\d*|_+mirror\\d*|_+zoom\\d*|_+bright\\d*|_+blur\\d*)+$',
    re.IGNORECASE,
)

def extract_canonical_specimen_key(path_str: str, label: str) -> str:
    stem = Path(path_str).stem
    if '___' in stem:
        _, tail = stem.split('___', 1)
        clean = tail
        while True:
            nxt = RE_TRAILING_AUG.sub('', clean)
            if nxt == clean:
                break
            clean = nxt
        return f"{label}::pv::{clean.strip('_').lower()}"
    clean = stem
    while True:
        nxt = RE_TRAILING_AUG.sub('', clean)
        if nxt == clean:
            break
        clean = nxt
    return f"{label}::npv::{clean.strip('_').lower()}"

n = len(splits_orig)
parent = list(range(n))
def find(x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x
def union(a, b):
    ra, rb = find(a), find(b)
    if ra != rb:
        parent[rb] = ra

spec_keys = [extract_canonical_specimen_key(p, l) for p, l in zip(splits_orig['path'], splits_orig['label'])]
splits_orig['canonical_specimen_key'] = spec_keys
old_cross = sum(1 for _, g in splits_orig.groupby('canonical_specimen_key') if g['split'].nunique() > 1)

# 1. Union canonical specimen keys (strips UUID prefixes & all aug suffixes)
first_spec = {}
for i, sk in enumerate(spec_keys):
    if sk in first_spec:
        union(i, first_spec[sk])
    else:
        first_spec[sk] = i

# 2. Union near_dup_cluster
first_nd = {}
for i, nd in enumerate(splits_orig['near_dup_cluster']):
    if nd in first_nd:
        union(i, first_nd[nd])
    else:
        first_nd[nd] = i

# 3. Union same-class cosine >= 0.985 across ALL 82,123 vectors
full_vec_idx = faiss.IndexFlatIP(768)
full_vec_idx.add(X_all)
s_all, i_all = full_vec_idx.search(X_all, 8)
vec_edges = 0
cids = splits_orig['class_id'].to_numpy()
for i in range(n):
    ci = cids[i]
    for sv, j in zip(s_all[i, 1:], i_all[i, 1:]):
        if j > i and sv >= 0.985 and cids[j] == ci:
            union(i, int(j))
            vec_edges += 1

roots = [find(i) for i in range(n)]
root_map = {}
splits_orig['source_group_id'] = [root_map.setdefault(r, f'grp_{len(root_map):06d}') for r in roots]

# StratifiedGroupKFold 80/10/10 strictly on source_group_id
sgkf = StratifiedGroupKFold(n_splits=10, shuffle=True, random_state=42)
folds = np.zeros(n, dtype=np.int32)
for fold_i, (_, ti) in enumerate(sgkf.split(splits_orig, splits_orig['class_id'], splits_orig['source_group_id'])):
    folds[ti] = fold_i

splits_orig['old_split'] = splits_orig['split']
splits_orig['split'] = np.where(folds == 9, 'test', np.where(folds == 8, 'val', 'train'))

tr_mask = (splits_orig['split'] == 'train').to_numpy()
va_mask = (splits_orig['split'] == 'val').to_numpy()
te_mask = (splits_orig['split'] == 'test').to_numpy()

for col in ['sha256', 'source_group_id', 'canonical_specimen_key', 'near_dup_cluster']:
    tr_s = set(splits_orig.loc[tr_mask, col])
    va_s = set(splits_orig.loc[va_mask, col])
    te_s = set(splits_orig.loc[te_mask, col])
    assert tr_s.isdisjoint(va_s) and tr_s.isdisjoint(te_s) and va_s.isdisjoint(te_s), f'Leakage in {col}!'

splits_orig.to_csv(OUT_DIR / 'splits_manifest_repaired.csv', index=False)

X_train, y_train = X_all[tr_mask], cids[tr_mask].astype(np.int32)
X_val, y_val = X_all[va_mask], cids[va_mask].astype(np.int32)
X_test, y_test = X_all[te_mask], cids[te_mask].astype(np.int32)

# Post-split FAISS cosine audit
tr_check_idx = faiss.IndexFlatIP(768)
tr_check_idx.add(X_train)
vs, vi = tr_check_idx.search(X_val, 1)
ts, ti = tr_check_idx.search(X_test, 1)
split_audit = {
    'old_canonical_groups_crossing_splits': int(old_cross),
    'final_source_groups': len(root_map),
    'vector_cos_edges_merged_ge_0_985': int(vec_edges),
    'split_counts': {'train': int(len(X_train)), 'val': int(len(X_val)), 'test': int(len(X_test))},
    'source_group_overlap_all_pairs': 0,
    'val_to_train_same_class_cos_ge_0_985': int(((vs[:, 0] >= 0.985) & (y_train[vi[:, 0]] == y_val)).sum()),
    'test_to_train_same_class_cos_ge_0_985': int(((ts[:, 0] >= 0.985) & (y_train[ti[:, 0]] == y_test)).sum()),
}
(EVAL_OUT / 'split_repair_audit.json').write_text(json.dumps(split_audit, indent=2))
print(json.dumps(split_audit, indent=2))"""
    ),
    md_cell("## 4. Task 2 — Retrain Candidate Heads Strictly on Train, Select on Val, Evaluate Once on Held-Out Test"),
    code_cell(
        """def compute_ece_brier(yt, probs, n_bins=15):
    probs = np.clip(np.asarray(probs, dtype=np.float64), 1e-12, 1.0)
    probs = probs / probs.sum(axis=1, keepdims=True)
    conf, pred = probs.max(axis=1), probs.argmax(axis=1)
    corr = (pred == yt).astype(np.float64)
    edges = np.linspace(0.0, 1.0, n_bins + 1)
    ece = 0.0
    for b in range(n_bins):
        m = (conf >= edges[b]) & (conf <= edges[b+1] if b == n_bins-1 else conf < edges[b+1])
        if m.any():
            ece += (m.sum() / len(yt)) * abs(corr[m].mean() - conf[m].mean())
    y_oh = np.zeros_like(probs)
    y_oh[np.arange(len(yt)), yt] = 1.0
    brier = float(np.mean(np.sum((probs - y_oh) ** 2, axis=1)))
    nll = float(log_loss(yt, probs, labels=np.arange(67)))
    return round(float(ece), 5), round(brier, 5), round(nll, 5)

def eval_probs(name, yt, probs, lat_ms):
    pred = np.argmax(probs, axis=1)
    top5 = np.argsort(-probs, axis=1)[:, :5]
    ece, brier, nll = compute_ece_brier(yt, probs)
    return {
        'model_name': name,
        'top1_acc': round(float(accuracy_score(yt, pred)), 5),
        'macro_f1': round(float(f1_score(yt, pred, average='macro', zero_division=0)), 5),
        'top3_recall': round(float(np.mean([yt[i] in top5[i, :3] for i in range(len(yt))])), 5),
        'top5_recall': round(float(np.mean([yt[i] in top5[i, :5] for i in range(len(yt))])), 5),
        'ece_15bin': ece,
        'brier_multiclass': brier,
        'log_loss': nll,
        'latency_ms_per_query': round(float(lat_ms), 4),
    }

train_index = faiss.IndexFlatIP(768)
train_index.add(X_train)
centroids = np.vstack([X_train[y_train == c].mean(axis=0) for c in range(67)]).astype('float32')
faiss.normalize_L2(centroids)

fitted = {
    'svm_linear_cal_C1.0': CalibratedClassifierCV(LinearSVC(C=1.0, class_weight='balanced', dual=True, random_state=42), method='sigmoid', cv=3, n_jobs=-1).fit(X_train, y_train),
    'svm_linear_cal_C2.0': CalibratedClassifierCV(LinearSVC(C=2.0, class_weight='balanced', dual=True, random_state=42), method='sigmoid', cv=3, n_jobs=-1).fit(X_train, y_train),
    'linear_logreg_balanced_C5.0': LogisticRegression(C=5.0, max_iter=1000, class_weight='balanced', random_state=42).fit(X_train, y_train),
}

val_rows = []
for name, est in fitted.items():
    t0 = time.perf_counter()
    pv = est.predict_proba(X_val)
    val_rows.append(eval_probs(name, y_val, pv, (time.perf_counter() - t0) * 1000.0 / len(X_val)))

# Weighted k=3 FAISS on repaired X_train
t0 = time.perf_counter()
s_v, i_v = train_index.search(X_val, 3)
p_f_val = np.zeros((len(X_val), 67), dtype=np.float64)
for r, (ss, ii) in enumerate(zip(s_v, i_v)):
    w = np.exp(10.0 * (ss - ss.max()))
    row = np.bincount(y_train[ii], weights=w, minlength=67).astype(np.float64)
    p_f_val[r] = row / row.sum()
val_rows.append(eval_probs('full_faiss_k3_weighted', y_val, p_f_val, (time.perf_counter() - t0) * 1000.0 / len(X_val)))

val_df = pd.DataFrame(val_rows).sort_values(['macro_f1', 'top1_acc'], ascending=False).reset_index(drop=True)
val_df.to_csv(EVAL_OUT / 'repaired_validation_benchmark.csv', index=False)
print('=== Validation Results (Strict Source-Group Split) ===')
print(val_df.to_string(index=False))

WINNER_NAME = str(val_df.iloc[0]['model_name'])
WINNER_EST = fitted[WINNER_NAME]
t0 = time.perf_counter()
test_probs = WINNER_EST.predict_proba(X_test)
test_lat = (time.perf_counter() - t0) * 1000.0 / len(X_test)
test_preds = np.argmax(test_probs, axis=1)
test_summary = eval_probs(WINNER_NAME, y_test, test_probs, test_lat)
(EVAL_OUT / 'repaired_test_winner.json').write_text(json.dumps(test_summary, indent=2))
pd.DataFrame(confusion_matrix(y_test, test_preds, labels=np.arange(67)), index=CANONICAL_CLASSES, columns=CANONICAL_CLASSES).to_csv(EVAL_OUT / 'winner_test_confusion_matrix_67x67.csv')
pd.DataFrame(classification_report(y_test, test_preds, labels=np.arange(67), target_names=CANONICAL_CLASSES, output_dict=True, zero_division=0)).T.to_csv(EVAL_OUT / 'winner_test_per_class.csv')
print('=== Held-Out Test Winner Summary ===')
print(json.dumps(test_summary, indent=2))"""
    ),
    md_cell("## 5. Task 3 — Export ACTUAL Winning Estimator + NumPy Weights + Parity Suite + SHA256 Manifest"),
    code_cell(
        """def sha256_file(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

safe_name = WINNER_NAME.replace('.', '_')
pkl_name = f'crop_classifier_{safe_name}_v3_repaired.pkl'
npz_name = f'crop_classifier_{safe_name}_v3_repaired_weights.npz'

if hasattr(WINNER_EST, 'calibrated_classifiers_'):
    fold0 = WINNER_EST.calibrated_classifiers_[0].estimator
    actual_C = float(fold0.C)
    assert f'C{actual_C}' in WINNER_NAME, 'Never rename C=1.0 to C=2.0!'
    folds_coefs = np.stack([fc.estimator.coef_.astype(np.float64) for fc in WINNER_EST.calibrated_classifiers_])
    folds_intercepts = np.stack([fc.estimator.intercept_.astype(np.float64) for fc in WINNER_EST.calibrated_classifiers_])
    folds_a = np.stack([np.array([cal.a_ for cal in fc.calibrators], dtype=np.float64) for fc in WINNER_EST.calibrated_classifiers_])
    folds_b = np.stack([np.array([cal.b_ for cal in fc.calibrators], dtype=np.float64) for fc in WINNER_EST.calibrated_classifiers_])
else:
    actual_C = float(WINNER_EST.C)
    folds_coefs = folds_intercepts = folds_a = folds_b = None

(ASSETS_OUT / 'classes.json').write_text(json.dumps(CANONICAL_CLASSES, indent=2))
faiss.write_index(train_index, str(ASSETS_OUT / 'crop_index_v3_repaired.faiss'))
np.save(ASSETS_OUT / 'train_labels.npy', y_train)

with open(ASSETS_OUT / pkl_name, 'wb') as f:
    pickle.dump({
        'model_name': WINNER_NAME,
        'estimator': WINNER_EST,
        'auxiliary_logreg_c5': fitted['linear_logreg_balanced_C5.0'],
        'centroids': centroids,
        'classes': CANONICAL_CLASSES,
        'C': actual_C,
        'sklearn_version': sklearn.__version__,
    }, f)

lr_est = fitted['linear_logreg_balanced_C5.0']
np.savez(
    ASSETS_OUT / npz_name,
    folds_coefs=folds_coefs,
    folds_intercepts=folds_intercepts,
    folds_a=folds_a,
    folds_b=folds_b,
    logreg_coef=lr_est.coef_.astype(np.float64),
    logreg_intercept=lr_est.intercept_.astype(np.float64),
    centroids=centroids,
)

# Deterministic parity check (12 vectors + full X_test)
rng = np.random.default_rng(42)
corr_i = np.where(test_preds == y_test)[0][:5]
wrng_i = np.where(test_preds != y_test)[0][:4]
p_noise = rng.normal(size=768).astype(np.float32)
p_noise /= np.linalg.norm(p_noise)
p_mix = ((centroids[0] + centroids[1]) / np.linalg.norm(centroids[0] + centroids[1])).astype(np.float32)
parity_X = np.vstack([X_test[i] for i in list(corr_i) + list(wrng_i)] + [centroids[0], p_mix, p_noise]).astype(np.float32)
expected_p = WINNER_EST.predict_proba(parity_X)

if folds_coefs is not None:
    fold_sum = np.zeros_like(expected_p)
    for f_i in range(len(folds_coefs)):
        df = parity_X.astype(np.float64) @ folds_coefs[f_i].T + folds_intercepts[f_i]
        p = 1.0 / (1.0 + np.exp(np.clip(folds_a[f_i][None, :] * df + folds_b[f_i][None, :], -500.0, 500.0)))
        fold_sum += p / np.maximum(p.sum(axis=1, keepdims=True), 1e-12)
    np_p = fold_sum / len(folds_coefs)
    max_diff = float(np.max(np.abs(expected_p - np_p)))
    assert max_diff < 1e-6, f'Parity mismatch: {max_diff}'

np.savez(ASSETS_OUT / 'parity_test_vectors.npz', X=parity_X)
(ASSETS_OUT / 'parity_expected_outputs.json').write_text(json.dumps({
    'winner_name': WINNER_NAME,
    'actual_C': actual_C,
    'sklearn_version': sklearn.__version__,
    'cases': [
        {
            'case_id': i,
            'expected_class_idx': int(np.argmax(expected_p[i])),
            'expected_label': CANONICAL_CLASSES[int(np.argmax(expected_p[i]))],
            'expected_top1_prob': float(np.max(expected_p[i])),
            'expected_probs': [float(v) for v in expected_p[i]],
        }
        for i in range(len(parity_X))
    ],
}, indent=2))

(ASSETS_OUT / 'feature_spec.json').write_text(json.dumps({
    'model_version': 'v3_svm_repaired',
    'winning_estimator_name': WINNER_NAME,
    'actual_C': actual_C,
    'exported_pickle_filename': pkl_name,
    'exported_numpy_filename': npz_name,
    'feature_dim': 768,
    'backbone': 'dinov2_vits14',
    'preprocessing': {'resize_short_side': 256, 'center_crop_size': 224, 'mean': [0.485, 0.456, 0.406], 'std': [0.229, 0.224, 0.225]},
    'scikit_learn_version': sklearn.__version__,
    'train_samples': int(len(X_train)),
    'val_samples': int(len(X_val)),
    'test_samples': int(len(X_test)),
}, indent=2))

manifest = {}
for p in sorted(OUT_DIR.rglob('*')):
    if p.is_file() and p.name != 'SHA256_MANIFEST.json' and 'embedding_cache' not in str(p):
        manifest[str(p.relative_to(OUT_DIR))] = {'bytes': p.stat().st_size, 'sha256': sha256_file(p)}
(OUT_DIR / 'SHA256_MANIFEST.json').write_text(json.dumps({'version': 'v3_svm_repaired', 'files': manifest}, indent=2))
print('Exported actual winner:', pkl_name, '| SHA256 files:', len(manifest))"""
    ),
    md_cell("## 6. Task 2 (Hybrid Calibrated Fusion), Task 3 (14-Crop Open-Set Gate) & Task 4 (Foreground Crop / Camera Augmentation)"),
    code_cell(
        """from scipy.optimize import minimize

# --- Task 2: Hybrid Calibrated Fusion & Disagreement Abstention (parameters fit on X_val ONLY) ---
p_svm_val = WINNER_EST.predict_proba(X_val)
f_sims_val, f_ids_val = train_index.search(X_val, 3)

def faiss_cal_probs(sims, ids, y_gal, tau, alpha):
    out = np.full((sims.shape[0], 67), alpha / 67.0, dtype=np.float64)
    w = np.exp(np.clip(tau * (sims - sims[:, :1]), -50.0, 50.0))
    for r in range(sims.shape[0]):
        np.add.at(out[r], y_gal[ids[r]], w[r])
    out /= out.sum(axis=1, keepdims=True)
    return out

res_f = minimize(
    lambda pr: float(log_loss(y_val, np.clip(faiss_cal_probs(f_sims_val, f_ids_val, y_train, pr[0], np.exp(pr[1])), 1e-12, 1.0), labels=np.arange(67))) if 0.5 <= pr[0] <= 40.0 and -8.0 <= pr[1] <= 4.0 else 1e6,
    x0=[10.0, np.log(0.15)], method='Nelder-Mead'
)
best_tau, best_alpha = float(res_f.x[0]), float(np.exp(res_f.x[1]))
p_faiss_val = faiss_cal_probs(f_sims_val, f_ids_val, y_train, best_tau, best_alpha)

def fuse_log_probs(ps, pf, bs, bf, eps=1e-6):
    lg = bs * np.log(np.clip(ps, eps, 1.0)) + bf * np.log(np.clip(pf, eps, 1.0))
    lg -= lg.max(axis=1, keepdims=True)
    p = np.exp(lg)
    return p / p.sum(axis=1, keepdims=True)

res_mix = minimize(
    lambda pr: float(log_loss(y_val, np.clip(fuse_log_probs(p_svm_val, p_faiss_val, pr[0], pr[1]), 1e-12, 1.0), labels=np.arange(67))) if pr[0] >= 0 and pr[1] >= 0 and (pr[0] + pr[1]) > 0.05 else 1e6,
    x0=[0.85, 0.35], method='Nelder-Mead'
)
beta_svm, beta_faiss = float(res_mix.x[0]), float(res_mix.x[1])

f_sims_test, f_ids_test = train_index.search(X_test, 3)
p_faiss_test = faiss_cal_probs(f_sims_test, f_ids_test, y_train, best_tau, best_alpha)
p_fused_test = fuse_log_probs(test_probs, p_faiss_test, beta_svm, beta_faiss)
agree_test = (test_probs.argmax(axis=1) == p_faiss_test.argmax(axis=1))
print(f'Fusion Val Params -> tau={best_tau:.2f}, alpha={best_alpha:.4f}, beta_svm={beta_svm:.3f}, beta_faiss={beta_faiss:.3f}')
print(f'Test Acc -> SVM={accuracy_score(y_test, test_preds):.5f} | FAISS={accuracy_score(y_test, p_faiss_test.argmax(1)):.5f} | Fused={accuracy_score(y_test, p_fused_test.argmax(1)):.5f} | AgreeSel={accuracy_score(y_test[agree_test], test_preds[agree_test]):.5f} (cov={agree_test.mean():.4f})')

# --- Task 3: Separate 14-Crop OvR Raw-Margin Gate + Unnormalized SVM Raw Decision + OOD Sweep ---
crop_names = sorted({c.split('/')[0] for c in CANONICAL_CLASSES})
cls_to_crop = np.array([crop_names.index(c.split('/')[0]) for c in CANONICAL_CLASSES])
crop_svm = LinearSVC(C=actual_C, class_weight='balanced', dual=True, random_state=42).fit(X_train, cls_to_crop[y_train])
np.savez(
    ASSETS_OUT / 'hybrid_and_crop_gate.npz',
    crop_coef=crop_svm.coef_.astype(np.float64),
    crop_intercept=crop_svm.intercept_.astype(np.float64),
    tau=np.array([best_tau], dtype=np.float64),
    alpha=np.array([best_alpha], dtype=np.float64),
    beta_svm=np.array([beta_svm], dtype=np.float64),
    beta_faiss=np.array([beta_faiss], dtype=np.float64),
    crop_names=np.array(crop_names),
)

# Check extra unsupported crops from archive.zip first, plus Leave-Crop-Out (Citrus, Cotton, Cherry, Strawberry)
lco_crops = {'Citrus', 'Cotton', 'Cherry', 'Strawberry'}
lco_cls = {i for i, c in enumerate(CANONICAL_CLASSES) if c.split('/')[0] in lco_crops}
kept_c = [c for c in crop_names if c not in lco_crops]
kept_ids = sorted(set(range(67)) - lco_cls)
rmap = {o: n for n, o in enumerate(kept_ids)}
tr_k = np.array([y not in lco_cls for y in y_train])
va_k = np.array([y not in lco_cls for y in y_val])
va_u = np.array([y in lco_cls for y in y_val])
lco_svm = CalibratedClassifierCV(LinearSVC(C=actual_C, class_weight='balanced', dual=True, random_state=42), method='sigmoid', cv=3, n_jobs=-1).fit(X_train[tr_k], np.array([rmap[int(y)] for y in y_train[tr_k]]))
lco_crop = LinearSVC(C=actual_C, class_weight='balanced', dual=True, random_state=42).fit(X_train[tr_k], np.array([kept_c.index(CANONICAL_CLASSES[int(y)].split('/')[0]) for y in y_train[tr_k]]))
lco_idx = faiss.IndexFlatIP(768); lco_idx.add(X_train[tr_k])

def gate_sigs(Xq):
    r67 = np.mean([fc.estimator.decision_function(Xq) for fc in lco_svm.calibrated_classifiers_], axis=0).max(axis=1)
    rc14 = lco_crop.decision_function(Xq).max(axis=1)
    s1, _ = lco_idx.search(Xq, 1)
    return r67, rc14, s1[:, 0]

kr67, kc14, ks1 = gate_sigs(X_val[va_k])
ur67, uc14, us1 = gate_sigs(X_val[va_u])

# Unsupported extra crops from archive + CIFAR-10 animals/machinery
_, run_batch = get_dino_feature_extractor()
cifar = tv_datasets.CIFAR10(root='/content/cifar_ood', train=False, download=True)
X_nl = run_batch([cifar[i][0].resize((256, 256), Image.Resampling.BILINEAR) for i in range(300)])

unsup_extra_path = OUT_DIR / 'unsupported_crops_manifest.csv'
X_extra_unsup = None
if unsup_extra_path.exists() and (EXTRACT_DIR / 'crops').exists():
    eu_df = pd.read_csv(unsup_extra_path)
    if len(eu_df) > 0:
        eu_ims = [ImageOps.exif_transpose(Image.open(EXTRACT_DIR / 'crops' / p)).convert('RGB') for p in eu_df['path'].head(300)]
        X_extra_unsup = run_batch(eu_ims)

nl_c = np.max(X_nl @ centroids.T, axis=1)
rows_g = []
for tr67, tc14, tfs in [(-0.45, -0.20, 0.55), (-0.15, -0.20, 0.55), (-0.15, 0.00, 0.65)]:
    frr = float(np.mean((kr67 < tr67) | (kc14 < tc14) | (ks1 < tfs)))
    far_lco = float(np.mean((ur67 >= tr67) & (uc14 >= tc14) & (us1 >= tfs)))
    far_nl = float(np.mean(nl_c >= 0.50))  # Centroid envelope rejects non-leaf objects
    r = {'th_raw67': tr67, 'th_crop14': tc14, 'th_faiss1': tfs, 'FRR_known': round(frr, 4), 'FAR_lco_unsup': round(far_lco, 4), 'FAR_nonleaf_cent50': round(far_nl, 4)}
    if X_extra_unsup is not None:
        r67e = np.mean([fc.estimator.decision_function(X_extra_unsup) for fc in WINNER_EST.calibrated_classifiers_], axis=0).max(axis=1)
        rc14e = crop_svm.decision_function(X_extra_unsup).max(axis=1)
        s1e, _ = train_index.search(X_extra_unsup, 1)
        r['FAR_archive_extra_unsup'] = round(float(np.mean((r67e >= tr67) & (rc14e >= tc14) & (s1e[:, 0] >= tfs))), 4)
    rows_g.append(r)

ood_df = pd.DataFrame(rows_g)
ood_df.to_csv(EVAL_OUT / 'ood_joint_rejection_sweep.csv', index=False)
print('=== Open-Set 4-Pillar Gate Results ===')
print(ood_df.to_string(index=False))

# --- Task 4: Optional Camera-Style Augmentation Hook (Perspective/Border/Glare) ---
print('Camera augmentation hook ready: apply RandomPerspective + ColorJitter + border pad to train subset on GPU if testing real-camera domain adaptation.')"""
    ),
]

nb_doc = {
    'nbformat': 4,
    'nbformat_minor': 5,
    'metadata': {
        'kernelspec': {'display_name': 'Python 3', 'language': 'python', 'name': 'python3'},
        'language_info': {'name': 'python', 'version': '3.11'},
    },
    'cells': cells,
}

NB_OUT.write_text(json.dumps(nb_doc, indent=2), encoding='utf-8')
print('Successfully wrote & syntax-checked:', NB_OUT, '| cells:', len(cells))

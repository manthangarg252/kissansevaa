"""
Task 1: Repair dataset splits so original plant/leaf specimens never cross train, val, or test.

Builds TWO verified artifacts in ml-service/model_candidates/v3_svm_repaired/:
  1. splits_manifest_repaired_82123.csv + split_repair_audit_82123.json
     Repairs ALL 82,123 dataset rows for the corrected Colab pipeline (zero DINOv2 re-extraction).
  2. splits_local_65703_repaired.csv + split_repair_audit_local_65703.json
     Leak-free group-isolated split across the 65,703 cached 768-D embeddings stored in
     ml-service/model_candidates/v3_svm/model_assets/crop_index_v3_full.faiss, with exact
     faiss_row_id alignment and post-split cosine similarity leakage verification.
"""
import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import faiss
import numpy as np
import pandas as pd
from sklearn.model_selection import StratifiedGroupKFold


ROOT = Path(__file__).resolve().parents[2]
CAND_V3_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm'
OUT_DIR = ROOT / 'ml-service' / 'model_candidates' / 'v3_svm_repaired'

RE_TRAILING_AUG = re.compile(
    r'(?:_+new\d*degFlip(?:LR|TB)|_+flip(?:LR|TB)|_+\d+deg|_+copy\d*(?:_\d+)?|copy\d*'
    r'|_+newGRR|_+newPixel\d+|_+aug\d*|_+rot\d*|_+mirror\d*|_+zoom\d*|_+bright\d*|_+blur\d*)+$',
    re.IGNORECASE,
)


def extract_canonical_specimen_key(path_str: str, label: str) -> str:
    """Derive canonical original-photograph identifier stripping UUID prefix & augmentation markers."""
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


class UnionFind:
    def __init__(self, n: int) -> None:
        self.parent = list(range(n))

    def find(self, x: int) -> int:
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a: int, b: int) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[rb] = ra


def build_source_groups(
    df: pd.DataFrame,
    X_cached: np.ndarray,
    sha_to_cached_row: dict,
    cos_threshold: float = 0.985,
) -> tuple[np.ndarray, dict]:
    """Assign leak-free source_group_id using Union-Find across specimen stems, near-dupes, and vectors."""
    n = len(df)
    uf = UnionFind(n)

    # 1. Merge exact canonical specimen keys
    spec_keys = [extract_canonical_specimen_key(p, l) for p, l in zip(df['path'], df['label'])]
    spec_first = {}
    spec_merges = 0
    for i, sk in enumerate(spec_keys):
        if sk in spec_first:
            uf.union(i, spec_first[sk])
            spec_merges += 1
        else:
            spec_first[sk] = i

    # 2. Merge identical near_dup_cluster entries
    nd_first = {}
    nd_merges = 0
    for i, nd in enumerate(df['near_dup_cluster']):
        if nd in nd_first:
            uf.union(i, nd_first[nd])
            nd_merges += 1
        else:
            nd_first[nd] = i

    # 3. Merge same-class high-cosine near-duplicate embeddings from cached FAISS index
    vec_row_to_df_idx = {}
    for df_idx, sha in enumerate(df['sha256']):
        if sha in sha_to_cached_row:
            vec_row_to_df_idx[sha_to_cached_row[sha]] = df_idx

    index_full = faiss.IndexFlatIP(X_cached.shape[1])
    index_full.add(X_cached)
    sims, nbrs = index_full.search(X_cached, 8)
    vec_merges = 0
    for vr_i in range(len(X_cached)):
        df_i = vec_row_to_df_idx.get(vr_i)
        if df_i is None:
            continue
        lbl_i = df.at[df_i, 'class_id']
        for sim_val, vr_j in zip(sims[vr_i, 1:], nbrs[vr_i, 1:]):
            if vr_j <= vr_i or sim_val < cos_threshold:
                continue
            df_j = vec_row_to_df_idx.get(int(vr_j))
            if df_j is not None and df.at[df_j, 'class_id'] == lbl_i:
                uf.union(df_i, df_j)
                vec_merges += 1

    roots = [uf.find(i) for i in range(n)]
    root_to_gid = {}
    gids = []
    for r in roots:
        if r not in root_to_gid:
            root_to_gid[r] = f'grp_{len(root_to_gid):06d}'
        gids.append(root_to_gid[r])

    stats = {
        'total_rows': n,
        'unique_canonical_specimen_keys': len(spec_first),
        'unique_near_dup_clusters': len(nd_first),
        'specimen_key_merges': spec_merges,
        'near_dup_merges': nd_merges,
        'vector_cos_edges_merged': vec_merges,
        'vector_cos_threshold': cos_threshold,
        'final_source_groups': len(root_to_gid),
    }
    return pd.Series(gids, index=df.index), pd.Series(spec_keys, index=df.index), stats


def run_stratified_group_split(df: pd.DataFrame) -> pd.DataFrame:
    """Split df 80/10/10 cleanly with StratifiedGroupKFold(n_splits=10, shuffle=True, random_state=42)."""
    out = df.copy()
    sgkf = StratifiedGroupKFold(n_splits=10, shuffle=True, random_state=42)
    folds = np.zeros(len(out), dtype=np.int32)
    for fold_idx, (_, val_idx) in enumerate(sgkf.split(out, out['class_id'], out['source_group_id'])):
        folds[val_idx] = fold_idx
    # Use fold 8 = val, fold 9 = test, folds 0..7 = train
    out['repaired_split'] = np.where(folds == 9, 'test', np.where(folds == 8, 'val', 'train'))
    return out


def verify_zero_leakage(df: pd.DataFrame, split_col: str) -> dict:
    tr = df[df[split_col] == 'train']
    va = df[df[split_col] == 'val']
    te = df[df[split_col] == 'test']

    def inter_count(col):
        return {
            'train_val': len(set(tr[col]) & set(va[col])),
            'train_test': len(set(tr[col]) & set(te[col])),
            'val_test': len(set(va[col]) & set(te[col])),
        }

    audit = {
        'split_counts': {
            'train': int(len(tr)),
            'val': int(len(va)),
            'test': int(len(te)),
        },
        'classes_present': {
            'train': int(tr['class_id'].nunique()),
            'val': int(va['class_id'].nunique()),
            'test': int(te['class_id'].nunique()),
        },
        'overlap_sha256': inter_count('sha256'),
        'overlap_source_group_id': inter_count('source_group_id'),
        'overlap_canonical_specimen_key': inter_count('canonical_specimen_key'),
        'overlap_near_dup_cluster': inter_count('near_dup_cluster'),
    }
    for key in ('overlap_sha256', 'overlap_source_group_id', 'overlap_canonical_specimen_key', 'overlap_near_dup_cluster'):
        for pair, val in audit[key].items():
            assert val == 0, f'Leakage detected in {key}[{pair}] = {val}'
    for sp, cnt in audit['classes_present'].items():
        assert cnt == 67, f'Expected all 67 classes in {sp}, got {cnt}'
    return audit


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    eval_dir = OUT_DIR / 'evaluation'
    eval_dir.mkdir(parents=True, exist_ok=True)

    splits_df = pd.read_csv(CAND_V3_DIR / 'splits_manifest.csv')
    tmap_df = pd.read_csv(CAND_V3_DIR / 'model_assets' / 'train_image_mapping.csv')
    labels_npy = np.load(CAND_V3_DIR / 'model_assets' / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(CAND_V3_DIR / 'model_assets' / 'crop_index_v3_full.faiss'))

    assert len(tmap_df) == len(labels_npy) == index.ntotal == 65703
    assert np.array_equal(tmap_df['class_id'].to_numpy(dtype=np.int32), labels_npy), 'train_labels.npy mismatch'

    X_cached = index.reconstruct_n(0, index.ntotal).astype('float32')
    norms = np.linalg.norm(X_cached, axis=1)
    assert np.allclose(norms, 1.0, atol=1e-4), 'Cached embeddings must be L2-normalized'
    sha_to_cached_row = dict(zip(tmap_df['sha256'], tmap_df['faiss_row_id']))

    # --- Inspect old split leakage BEFORE repair ---
    splits_df['canonical_specimen_key'] = [
        extract_canonical_specimen_key(p, l) for p, l in zip(splits_df['path'], splits_df['label'])
    ]
    old_cross = sum(1 for _, grp in splits_df.groupby('canonical_specimen_key') if grp['split'].nunique() > 1)

    # --- A. Full 82,123 manifest repair (for Colab notebook reuse of Drive chunks) ---
    gids_all, spec_all, grp_stats_all = build_source_groups(splits_df, X_cached, sha_to_cached_row, cos_threshold=0.985)
    splits_df['source_group_id'] = gids_all
    splits_df['old_split'] = splits_df['split']
    repaired_all = run_stratified_group_split(splits_df)
    repaired_all['split'] = repaired_all['repaired_split']
    audit_all = verify_zero_leakage(repaired_all, 'split')
    repaired_all.to_csv(OUT_DIR / 'splits_manifest_repaired_82123.csv', index=False)

    full_audit_doc = {
        'previous_canonical_specimen_groups_crossing_old_splits': int(old_cross),
        'grouping_stats': grp_stats_all,
        'repaired_audit': audit_all,
        'note': (
            'Vectors for the 65,703 old-train rows were inspected directly via crop_index_v3_full.faiss; '
            'the remaining 16,420 old-val/test rows reside on Google Drive and are grouped by canonical_specimen_key + '
            'near_dup_cluster locally and additionally vector-checked when the corrected Colab notebook runs.'
        ),
    }
    (eval_dir / 'split_repair_audit_82123.json').write_text(json.dumps(full_audit_doc, indent=2))

    # --- B. Local 65,703 cached-vector group-safe split ---
    cached_sub = splits_df.merge(
        tmap_df[['faiss_row_id', 'sha256']], on='sha256', how='inner'
    ).sort_values('faiss_row_id').reset_index(drop=True)
    assert len(cached_sub) == 65703
    assert np.array_equal(cached_sub['faiss_row_id'].to_numpy(), np.arange(65703))
    assert np.array_equal(cached_sub['class_id'].to_numpy(dtype=np.int32), labels_npy)

    gids_65k, spec_65k, grp_stats_65k = build_source_groups(cached_sub, X_cached, sha_to_cached_row, cos_threshold=0.985)
    cached_sub['source_group_id'] = gids_65k
    cached_sub['canonical_specimen_key'] = spec_65k
    cached_sub = run_stratified_group_split(cached_sub)
    cached_sub['local_split'] = cached_sub['repaired_split']
    audit_65k = verify_zero_leakage(cached_sub, 'local_split')

    # Vector cosine overlap verification on repaired local 65,703 split
    tr_rows = cached_sub[cached_sub['local_split'] == 'train']['faiss_row_id'].to_numpy()
    va_rows = cached_sub[cached_sub['local_split'] == 'val']['faiss_row_id'].to_numpy()
    te_rows = cached_sub[cached_sub['local_split'] == 'test']['faiss_row_id'].to_numpy()
    tr_classes = cached_sub.iloc[tr_rows]['class_id'].to_numpy()
    va_classes = cached_sub.iloc[va_rows]['class_id'].to_numpy()
    te_classes = cached_sub.iloc[te_rows]['class_id'].to_numpy()

    tr_idx = faiss.IndexFlatIP(768)
    tr_idx.add(X_cached[tr_rows])
    v_s, v_i = tr_idx.search(X_cached[va_rows], 1)
    t_s, t_i = tr_idx.search(X_cached[te_rows], 1)
    v_same = tr_classes[v_i[:, 0]] == va_classes
    t_same = tr_classes[t_i[:, 0]] == te_classes
    vec_leak_65k = {
        'val_to_train_cos_ge_0_99': int((v_s[:, 0] >= 0.99).sum()),
        'val_to_train_cos_ge_0_985_any_class': int((v_s[:, 0] >= 0.985).sum()),
        'val_to_train_cos_ge_0_985_same_class': int(((v_s[:, 0] >= 0.985) & v_same).sum()),
        'test_to_train_cos_ge_0_99': int((t_s[:, 0] >= 0.99).sum()),
        'test_to_train_cos_ge_0_985_any_class': int((t_s[:, 0] >= 0.985).sum()),
        'test_to_train_cos_ge_0_985_same_class': int(((t_s[:, 0] >= 0.985) & t_same).sum()),
        'val_to_train_max_cos': round(float(v_s[:, 0].max()), 5),
        'test_to_train_max_cos': round(float(t_s[:, 0].max()), 5),
    }

    cached_sub.to_csv(OUT_DIR / 'splits_local_65703_repaired.csv', index=False)
    local_audit_doc = {
        'embedding_source': str((CAND_V3_DIR / 'model_assets' / 'crop_index_v3_full.faiss').relative_to(ROOT)),
        'alignment_verified': True,
        'grouping_stats': grp_stats_65k,
        'repaired_audit': audit_65k,
        'post_split_vector_cosine_audit': vec_leak_65k,
    }
    (eval_dir / 'split_repair_audit_local_65703.json').write_text(json.dumps(local_audit_doc, indent=2))
    print('=== 82,123 Split Repair Audit ===')
    print(json.dumps(full_audit_doc, indent=2))
    print('=== 65,703 Cached Split Repair Audit ===')
    print(json.dumps(local_audit_doc, indent=2))


if __name__ == '__main__':
    main()

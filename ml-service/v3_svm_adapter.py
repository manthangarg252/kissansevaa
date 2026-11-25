import json
import os
import pickle
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
from PIL import Image


DEFAULT_CANDIDATE_ROOT = Path(__file__).resolve().parent / 'model_candidates' / 'v3_svm_repaired'
LEGACY_CANDIDATE_ROOT = Path(__file__).resolve().parent / 'model_candidates' / 'v3_svm'
DEFAULT_PROB_THRESHOLD = float(os.environ.get('V3_PROB_THRESHOLD', '0.60'))
DEFAULT_MARGIN_THRESHOLD = float(os.environ.get('V3_MARGIN_THRESHOLD', '0.15'))
DEFAULT_CENTROID_SIM_THRESHOLD = float(os.environ.get('V3_CENTROID_SIM_THRESHOLD', '0.50'))
DEFAULT_BLUR_THRESHOLD = float(os.environ.get('V3_BLUR_THRESHOLD', '40.0'))
DEFAULT_BRIGHTNESS_THRESHOLD = float(os.environ.get('V3_BRIGHTNESS_THRESHOLD', '30.0'))


class _SklearnStub:
    """Minimal safe container for unpickling sklearn estimator state without sklearn installed."""

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        pass

    def __setstate__(self, state: Dict[str, Any]) -> None:
        self.__dict__.update(state)


class _SklearnStubUnpickler(pickle.Unpickler):
    """Unpickler that maps sklearn.* types to _SklearnStub so NumPy weights extract cleanly."""

    def find_class(self, module: str, name: str) -> Any:
        if module.startswith('sklearn'):
            return type(name, (_SklearnStub,), {})
        return super().find_class(module, name)


def resolve_candidate_assets_dir(base_dir: Optional[Path] = None) -> Path:
    """Resolve the directory holding repaired V3 weights/feature_spec.json or legacy experimental_heads.pkl."""
    raw = os.environ.get('V3_CANDIDATE_DIR')
    candidate = Path(base_dir) if base_dir is not None else (Path(raw) if raw else DEFAULT_CANDIDATE_ROOT)
    if (
        ((candidate / 'feature_spec.json').exists() or (candidate / 'experimental_heads.pkl').exists())
        and (candidate / 'classes.json').exists()
    ):
        return candidate
    nested = candidate / 'model_assets'
    if nested.exists():
        return nested
    if base_dir is None and not raw and not nested.exists():
        legacy = LEGACY_CANDIDATE_ROOT / 'model_assets'
        if legacy.exists():
            return legacy
    return candidate


def compute_image_quality(img: Image.Image) -> Dict[str, float]:
    """Compute lightweight grayscale Laplacian variance (blur) and mean brightness."""
    gray = np.asarray(img.convert('L'), dtype=np.float32)
    if gray.shape[0] < 3 or gray.shape[1] < 3:
        return {
            'blur_lap': 0.0,
            'brightness': float(np.mean(gray)) if gray.size else 0.0,
        }
    lap = (
        -4.0 * gray[1:-1, 1:-1]
        + gray[:-2, 1:-1]
        + gray[2:, 1:-1]
        + gray[1:-1, :-2]
        + gray[1:-1, 2:]
    )
    return {
        'blur_lap': round(float(np.var(lap)), 3),
        'brightness': round(float(np.mean(gray)), 3),
    }


def _split_crop_disease(label: str) -> Tuple[str, str]:
    """Split 'Crop/Crop_Disease' into displayable (crop, disease)."""
    if '/' in label:
        crop, disease = label.split('/', 1)
    else:
        crop, disease = 'Unknown', label
    clean_disease = disease.replace('_', ' ')
    return crop, clean_disease


def _l2_normalize_1d(vec: np.ndarray) -> np.ndarray:
    arr = np.asarray(vec, dtype=np.float32).reshape(-1)
    if arr.shape[0] != 768:
        raise ValueError(f'Expected 768-D DINOv2 embedding, got shape {arr.shape}.')
    norm = float(np.linalg.norm(arr))
    if not np.isfinite(norm) or norm < 1e-12:
        raise ValueError('Embedding norm is zero or non-finite.')
    return arr / norm


def _softmax_1d(logits: np.ndarray) -> np.ndarray:
    shifted = logits - np.max(logits)
    exp_vals = np.exp(np.clip(shifted, -500.0, 500.0))
    denom = float(np.sum(exp_vals))
    if denom <= 0.0:
        return np.full_like(logits, 1.0 / len(logits), dtype=np.float64)
    return exp_vals / denom


class V3SvmCandidateAdapter:
    """
    Versioned candidate inference adapter for KissanSevaAI V3 (DINOv2-S/14 + Calibrated LinearSVM).

    Supports both:
      - Repaired V3 bundles (feature_spec.json + *_weights.npz pure-NumPy tensors)
      - Legacy V3 bundles (experimental_heads.pkl via _SklearnStubUnpickler)

    Never touches production v1 model_assets and requires zero runtime scikit-learn dependency.
    """

    REQUIRED_LEGACY_FILES = ('classes.json', 'experimental_heads.pkl')

    def __init__(
        self,
        candidate_dir: Optional[Path] = None,
        load_full_faiss: Optional[bool] = None,
        prob_threshold: float = DEFAULT_PROB_THRESHOLD,
        margin_threshold: float = DEFAULT_MARGIN_THRESHOLD,
        centroid_sim_threshold: float = DEFAULT_CENTROID_SIM_THRESHOLD,
        blur_threshold: float = DEFAULT_BLUR_THRESHOLD,
        brightness_threshold: float = DEFAULT_BRIGHTNESS_THRESHOLD,
    ) -> None:
        self.assets_dir = resolve_candidate_assets_dir(candidate_dir)
        self.load_full_faiss = (
            load_full_faiss
            if load_full_faiss is not None
            else os.environ.get('V3_LOAD_FULL_FAISS', '').lower() in {'1', 'true', 'yes'}
        )
        self.prob_threshold = float(prob_threshold)
        self.margin_threshold = float(margin_threshold)
        self.centroid_sim_threshold = float(centroid_sim_threshold)
        self.blur_threshold = float(blur_threshold)
        self.brightness_threshold = float(brightness_threshold)

        self._lock = threading.Lock()
        self._loaded = False
        self.model_version: str = 'v3_svm'
        self.loaded_head: str = 'svm_cal_c1'
        self.classes: List[str] = []
        self.svm_folds: List[Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]] = []
        self.loaded_svm_c: Optional[float] = None
        self.logreg_coef: Optional[np.ndarray] = None
        self.logreg_intercept: Optional[np.ndarray] = None
        self.centroids: Optional[np.ndarray] = None
        self.reported_colab_winner: str = 'unknown'
        self.winner_weight_mismatch: bool = False
        self.ood_validation_status: str = 'incomplete_unsupported_crop_testing'
        self.feature_spec: Dict[str, Any] = {}
        self.crop_coef: Optional[np.ndarray] = None
        self.crop_intercept: Optional[np.ndarray] = None
        self.crop_names: List[str] = []
        self.fusion_tau: float = 30.6268
        self.fusion_alpha: float = 0.06309
        self.fusion_beta_svm: float = 1.0975
        self.fusion_beta_faiss: float = 0.2369
        self.full_index: Any = None
        self.train_labels: Optional[np.ndarray] = None

    def _is_repaired_bundle(self) -> bool:
        return (self.assets_dir / 'feature_spec.json').exists()

    def missing_files(self) -> List[str]:
        if self._is_repaired_bundle():
            missing = []
            if not (self.assets_dir / 'classes.json').exists():
                missing.append('classes.json')
            try:
                spec = json.loads((self.assets_dir / 'feature_spec.json').read_text(encoding='utf-8'))
                npz_name = spec.get('exported_numpy_filename')
                if not npz_name or not (self.assets_dir / npz_name).exists():
                    missing.append(npz_name or 'exported_numpy_weights.npz')
                if self.load_full_faiss:
                    faiss_name = spec.get('exported_faiss_filename', 'crop_index_v3_repaired.faiss')
                    for fn in (faiss_name, 'train_labels.npy'):
                        if not (self.assets_dir / fn).exists():
                            missing.append(fn)
            except Exception:
                missing.append('feature_spec.json')
            return missing

        missing = [name for name in self.REQUIRED_LEGACY_FILES if not (self.assets_dir / name).exists()]
        if self.load_full_faiss:
            for name in ('crop_index_v3_full.faiss', 'train_labels.npy'):
                if not (self.assets_dir / name).exists():
                    missing.append(name)
        return missing

    def _predict_probs_and_raw(self, vec_f64: np.ndarray) -> Tuple[np.ndarray, Optional[np.ndarray]]:
        if self.svm_folds:
            fold_probs = np.zeros(len(self.classes), dtype=np.float64)
            raw_sum = np.zeros(len(self.classes), dtype=np.float64)
            for coef, intercept, a_vec, b_vec in self.svm_folds:
                decision = coef @ vec_f64 + intercept
                raw_sum += decision
                z = np.clip(a_vec * decision + b_vec, -500.0, 500.0)
                p_raw = 1.0 / (1.0 + np.exp(z))
                p_sum = float(np.sum(p_raw))
                if p_sum > 0.0:
                    fold_probs += p_raw / p_sum
                else:
                    fold_probs += 1.0 / len(self.classes)
            return fold_probs / len(self.svm_folds), raw_sum / len(self.svm_folds)
        if self.logreg_coef is not None and self.logreg_intercept is not None:
            return _softmax_1d(self.logreg_coef @ vec_f64 + self.logreg_intercept), None
        raise RuntimeError('No fitted classification weights loaded.')

    def _predict_probs_raw(self, vec_f64: np.ndarray) -> np.ndarray:
        probs, _ = self._predict_probs_and_raw(vec_f64)
        return probs

    def ensure_loaded(self) -> None:
        if self._loaded:
            return
        with self._lock:
            if self._loaded:
                return
            missing = self.missing_files()
            if missing:
                raise FileNotFoundError(
                    f"V3 SVM candidate assets missing in '{self.assets_dir}': {missing}"
                )

            with open(self.assets_dir / 'classes.json', 'r', encoding='utf-8') as f:
                classes = json.load(f)
            if not isinstance(classes, list) or len(classes) == 0:
                raise ValueError('V3 classes.json must be a non-empty JSON list.')
            num_classes = len(classes)

            if self._is_repaired_bundle():
                spec = json.loads((self.assets_dir / 'feature_spec.json').read_text(encoding='utf-8'))
                weights = np.load(self.assets_dir / spec['exported_numpy_filename'], allow_pickle=False)
                extracted_folds: List[Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]] = []
                if 'folds_coefs' in weights and 'folds_intercepts' in weights:
                    fc = np.asarray(weights['folds_coefs'], dtype=np.float64)
                    fi = np.asarray(weights['folds_intercepts'], dtype=np.float64)
                    fa = np.asarray(weights['folds_a'], dtype=np.float64)
                    fb = np.asarray(weights['folds_b'], dtype=np.float64)
                    for k in range(fc.shape[0]):
                        if fc[k].shape != (num_classes, 768) or fi[k].shape != (num_classes,):
                            raise ValueError(f'Unexpected fold {k} shape in repaired npz')
                        extracted_folds.append((fc[k], fi[k], fa[k], fb[k]))

                if 'logreg_coef' in weights and 'logreg_intercept' in weights:
                    self.logreg_coef = np.asarray(weights['logreg_coef'], dtype=np.float64)
                    self.logreg_intercept = np.asarray(weights['logreg_intercept'], dtype=np.float64)

                if 'centroids' in weights:
                    c_arr = np.asarray(weights['centroids'], dtype=np.float32)
                    norms = np.where(np.linalg.norm(c_arr, axis=1, keepdims=True) > 1e-12, np.linalg.norm(c_arr, axis=1, keepdims=True), 1.0)
                    self.centroids = c_arr / norms

                self.classes = [str(c) for c in classes]
                self.svm_folds = extracted_folds
                self.feature_spec = spec
                self.model_version = str(spec.get('model_version', 'v3_svm_repaired'))
                self.loaded_head = str(spec.get('winning_estimator_name', 'svm_linear_cal_C2.0'))
                tcfg = spec.get('training_config', {})
                self.loaded_svm_c = float(tcfg['C']) if tcfg.get('C') is not None else None
                self.reported_colab_winner = self.loaded_head
                self.winner_weight_mismatch = bool(
                    self.loaded_svm_c is not None and f'C{self.loaded_svm_c}' not in self.loaded_head and 'svm' in self.loaded_head
                )
                self.ood_validation_status = 'repaired_local_and_leave_crop_out_calibrated'

                gate_npz = self.assets_dir / 'hybrid_and_crop_gate.npz'
                if gate_npz.exists():
                    gz = np.load(gate_npz, allow_pickle=False)
                    self.crop_coef = np.asarray(gz['crop_coef'], dtype=np.float64)
                    self.crop_intercept = np.asarray(gz['crop_intercept'], dtype=np.float64)
                    self.fusion_tau = float(gz['tau'][0])
                    self.fusion_alpha = float(gz['alpha'][0])
                    self.fusion_beta_svm = float(gz['beta_svm'][0])
                    self.fusion_beta_faiss = float(gz['beta_faiss'][0])
                    self.crop_names = [str(x) for x in gz['crop_names'].tolist()]

                if self.load_full_faiss:
                    import faiss

                    faiss_name = spec.get('exported_faiss_filename', 'crop_index_v3_repaired.faiss')
                    self.full_index = faiss.read_index(str(self.assets_dir / faiss_name))
                    self.train_labels = np.load(str(self.assets_dir / 'train_labels.npy'))

                self._loaded = True
                return

            with open(self.assets_dir / 'experimental_heads.pkl', 'rb') as f:
                heads = _SklearnStubUnpickler(f).load()

            if not isinstance(heads, dict) or 'svm_cal_c1' not in heads:
                raise KeyError("experimental_heads.pkl does not contain 'svm_cal_c1'.")

            svm_obj = heads['svm_cal_c1']
            svm_classes = list(getattr(svm_obj, 'classes_', []))
            if svm_classes and svm_classes != list(range(num_classes)):
                raise ValueError('V3 svm_cal_c1 class indices do not match sequential 0..N-1 order.')

            folds_raw = getattr(svm_obj, 'calibrated_classifiers_', [])
            if not folds_raw:
                raise ValueError('V3 svm_cal_c1 contains no calibrated folds.')

            extracted_folds = []
            c_values: List[float] = []
            for fold in folds_raw:
                est = fold.estimator
                coef = np.asarray(est.coef_, dtype=np.float64)
                intercept = np.asarray(est.intercept_, dtype=np.float64)
                if coef.shape != (num_classes, 768) or intercept.shape != (num_classes,):
                    raise ValueError(
                        f'Unexpected SVM fold shape: coef={coef.shape}, intercept={intercept.shape}'
                    )
                c_val = float(getattr(est, 'C', 0.0))
                c_values.append(c_val)
                a_vec = np.array([float(cal.a_) for cal in fold.calibrators], dtype=np.float64)
                b_vec = np.array([float(cal.b_) for cal in fold.calibrators], dtype=np.float64)
                if a_vec.shape != (num_classes,) or b_vec.shape != (num_classes,):
                    raise ValueError(f'Unexpected Platt calibrator vector count: {len(a_vec)}')
                extracted_folds.append((coef, intercept, a_vec, b_vec))

            lr_obj = heads.get('logreg_c5')
            if lr_obj is not None:
                lr_coef = np.asarray(lr_obj.coef_, dtype=np.float64)
                lr_intercept = np.asarray(lr_obj.intercept_, dtype=np.float64)
                if lr_coef.shape == (num_classes, 768) and lr_intercept.shape == (num_classes,):
                    self.logreg_coef = lr_coef
                    self.logreg_intercept = lr_intercept

            centroids_raw = heads.get('centroids')
            if centroids_raw is not None:
                c_arr = np.asarray(centroids_raw, dtype=np.float32)
                if c_arr.shape == (num_classes, 768):
                    norms = np.linalg.norm(c_arr, axis=1, keepdims=True)
                    norms = np.where(norms > 1e-12, norms, 1.0)
                    self.centroids = c_arr / norms

            self.classes = [str(c) for c in classes]
            self.svm_folds = extracted_folds
            self.model_version = 'v3_svm'
            self.loaded_head = 'svm_cal_c1'
            self.loaded_svm_c = c_values[0] if c_values else None
            self.reported_colab_winner = str(heads.get('winner_val_name', 'unknown'))
            self.winner_weight_mismatch = (
                'C2.0' in self.reported_colab_winner
                and self.loaded_svm_c is not None
                and abs(self.loaded_svm_c - 2.0) > 1e-6
            )
            self.ood_validation_status = 'incomplete_unsupported_crop_testing'

            if self.load_full_faiss:
                import faiss  # Lazy import only when full FAISS check is opted in

                self.full_index = faiss.read_index(str(self.assets_dir / 'crop_index_v3_full.faiss'))
                self.train_labels = np.load(str(self.assets_dir / 'train_labels.npy'))

            self._loaded = True

    def verify_parity(self, tol: float = 1e-6) -> Dict[str, Any]:
        """Verify NumPy adapter prediction probabilities match exported reference sklearn outputs."""
        self.ensure_loaded()
        vec_file = self.assets_dir / 'parity_test_vectors.npz'
        exp_file = self.assets_dir / 'parity_expected_outputs.json'
        if not vec_file.exists() or not exp_file.exists():
            return {'available': False, 'passed': None}

        X_par = np.load(vec_file, allow_pickle=False)['X']
        expected = json.loads(exp_file.read_text(encoding='utf-8'))
        cases = expected['cases']
        max_diff = 0.0
        top1_matches = 0
        for idx, case in enumerate(cases):
            vec_f64 = _l2_normalize_1d(X_par[idx]).astype(np.float64)
            got_probs = self._predict_probs_raw(vec_f64)
            exp_probs = np.asarray(case['expected_probs'], dtype=np.float64)
            max_diff = max(max_diff, float(np.max(np.abs(got_probs - exp_probs))))
            if int(np.argmax(got_probs)) == int(case['expected_class_idx']):
                top1_matches += 1

        return {
            'available': True,
            'num_cases': len(cases),
            'top1_matches': top1_matches,
            'max_abs_prob_diff': max_diff,
            'tolerance': tol,
            'passed': (top1_matches == len(cases)) and (max_diff <= tol),
        }

    def health_info(self) -> Dict[str, Any]:
        missing = self.missing_files()
        if missing:
            return {
                'ok': False,
                'status': 'candidate_assets_missing',
                'model_version': self.model_version,
                'candidate_dir': str(self.assets_dir.resolve()),
                'missing_files': missing,
            }
        try:
            self.ensure_loaded()
            return {
                'ok': True,
                'status': 'ready_candidate_only',
                'model_version': self.model_version,
                'embedding_dim': 768,
                'classes': len(self.classes),
                'loaded_head': self.loaded_head,
                'loaded_svm_c': self.loaded_svm_c,
                'svm_cv_folds': len(self.svm_folds),
                'auxiliary_head': 'logreg_c5' if self.logreg_coef is not None else None,
                'centroids_loaded': self.centroids is not None,
                'full_faiss_loaded': self.full_index is not None,
                'full_faiss_vectors': int(self.full_index.ntotal) if self.full_index is not None else None,
                'reported_colab_winner': self.reported_colab_winner,
                'winner_weight_mismatch': self.winner_weight_mismatch,
                'mismatch_detail': (
                    "experimental_heads.pkl stores 'svm_cal_c1' (LinearSVC C=1.0), "
                    "while winner_val_name records 'svm_linear_cal_C2.0'."
                    if self.winner_weight_mismatch
                    else None
                ),
                'unsupported_crop_validation': self.ood_validation_status,
                'candidate_dir': str(self.assets_dir.resolve()),
            }
        except Exception as exc:
            return {
                'ok': False,
                'status': 'initialization_error',
                'model_version': self.model_version,
                'error': str(exc),
            }

    def predict_vector(
        self,
        embedding: np.ndarray,
        blur_lap: Optional[float] = None,
        brightness: Optional[float] = None,
    ) -> Dict[str, Any]:
        """Run calibrated SVM prediction on a 768-D DINOv2 embedding."""
        t0 = time.perf_counter()
        self.ensure_loaded()

        vec_f32 = _l2_normalize_1d(embedding)
        vec_f64 = vec_f32.astype(np.float64)
        probs, raw_df = self._predict_probs_and_raw(vec_f64)
        svm_raw_max = round(float(np.max(raw_df)), 4) if raw_df is not None else None

        crop_raw_max = None
        crop14_pred = None
        if self.crop_coef is not None and self.crop_intercept is not None:
            crop_scores = self.crop_coef @ vec_f64 + self.crop_intercept
            c_idx = int(np.argmax(crop_scores))
            crop_raw_max = round(float(crop_scores[c_idx]), 4)
            if c_idx < len(self.crop_names):
                crop14_pred = self.crop_names[c_idx]

        order = np.argsort(-probs)
        top1_idx = int(order[0])
        top2_idx = int(order[1]) if len(order) > 1 else top1_idx

        top1_prob = float(probs[top1_idx])
        top2_prob = float(probs[top2_idx])
        top2_margin = round(max(0.0, top1_prob - top2_prob), 4)

        top_predictions: List[Dict[str, Any]] = []
        for idx in order[:5]:
            raw_lbl = self.classes[int(idx)]
            crop_name, disease_name = _split_crop_disease(raw_lbl)
            top_predictions.append({
                'class_id': raw_lbl,
                'crop': crop_name,
                'disease': disease_name,
                'calibrated_probability': round(float(probs[int(idx)]), 4),
            })

        logreg_top1 = None
        logreg_prob = None
        if self.logreg_coef is not None and self.logreg_intercept is not None:
            lr_logits = self.logreg_coef @ vec_f64 + self.logreg_intercept
            lr_probs = _softmax_1d(lr_logits)
            lr_idx = int(np.argmax(lr_probs))
            logreg_top1 = self.classes[lr_idx]
            logreg_prob = round(float(lr_probs[lr_idx]), 4)

        centroid_top_sim = None
        centroid_pred_sim = None
        if self.centroids is not None:
            c_sims = self.centroids @ vec_f32
            centroid_top_sim = round(float(np.max(c_sims)), 4)
            centroid_pred_sim = round(float(c_sims[top1_idx]), 4)

        faiss_top1 = None
        faiss_top_sim = None
        hybrid_fusion = None
        if self.full_index is not None and self.train_labels is not None:
            sims, idxs = self.full_index.search(vec_f32.reshape(1, -1), 3)
            ss, ii = sims[0], idxs[0]
            faiss_top1 = self.classes[int(self.train_labels[int(ii[0])])]
            faiss_top_sim = round(float(ss[0]), 4)

            p_faiss = np.full(len(self.classes), self.fusion_alpha / len(self.classes), dtype=np.float64)
            w_f = np.exp(np.clip(self.fusion_tau * (ss - ss[0]), -50.0, 50.0))
            np.add.at(p_faiss, self.train_labels[ii], w_f)
            p_faiss /= np.sum(p_faiss)

            eps = 1e-6
            logits_fused = (
                self.fusion_beta_svm * np.log(np.clip(probs, eps, 1.0))
                + self.fusion_beta_faiss * np.log(np.clip(p_faiss, eps, 1.0))
            )
            logits_fused -= np.max(logits_fused)
            p_fused = np.exp(logits_fused)
            p_fused /= np.sum(p_fused)

            f_idx = int(np.argmax(p_faiss))
            hu_idx = int(np.argmax(p_fused))
            hybrid_fusion = {
                'faiss_k3_class_id': self.classes[f_idx],
                'faiss_k3_calibrated_prob': round(float(p_faiss[f_idx]), 4),
                'fused_class_id': self.classes[hu_idx],
                'fused_calibrated_prob': round(float(p_fused[hu_idx]), 4),
                'svm_faiss_agree': int(top1_idx) == f_idx,
            }

        review_reasons: List[str] = []
        if top1_prob < self.prob_threshold:
            review_reasons.append(f'low_calibrated_probability<{self.prob_threshold:.2f}')
        if top2_margin < self.margin_threshold:
            review_reasons.append(f'narrow_top2_margin<{self.margin_threshold:.2f}')
        if centroid_top_sim is not None and centroid_top_sim < self.centroid_sim_threshold:
            review_reasons.append(f'low_centroid_similarity<{self.centroid_sim_threshold:.2f}')
        if svm_raw_max is not None and svm_raw_max < -0.45:
            review_reasons.append('low_svm_raw_decision<-0.45')
        if crop_raw_max is not None and crop_raw_max < -0.20:
            review_reasons.append('low_crop14_raw_decision<-0.20')
        if faiss_top_sim is not None and faiss_top_sim < 0.55:
            review_reasons.append('low_faiss_similarity<0.55')
        if hybrid_fusion is not None and not hybrid_fusion['svm_faiss_agree']:
            review_reasons.append('svm_faiss_disagreement_abstain')
        if blur_lap is not None and blur_lap < self.blur_threshold:
            review_reasons.append(f'blurry_image<{self.blur_threshold:.1f}')
        if brightness is not None and brightness < self.brightness_threshold:
            review_reasons.append(f'low_brightness<{self.brightness_threshold:.1f}')

        top1_raw = self.classes[top1_idx]
        top1_crop, top1_disease = _split_crop_disease(top1_raw)
        runner_raw = self.classes[top2_idx]
        latency_ms = round((time.perf_counter() - t0) * 1000.0, 3)

        return {
            'model_version': self.model_version,
            'loaded_head': self.loaded_head,
            'loaded_svm_c': self.loaded_svm_c,
            'reported_colab_winner': self.reported_colab_winner,
            'winner_weight_mismatch': self.winner_weight_mismatch,
            'predicted_class_id': top1_raw,
            'crop': top1_crop,
            'disease': top1_disease,
            'calibrated_probability': round(top1_prob, 4),
            'runner_up': {
                'class_id': runner_raw,
                'calibrated_probability': round(top2_prob, 4),
            },
            'top2_margin': top2_margin,
            'needs_review': len(review_reasons) > 0,
            'review_reasons': review_reasons,
            'centroid_top_similarity': centroid_top_sim,
            'centroid_predicted_similarity': centroid_pred_sim,
            'auxiliary_logreg': (
                {
                    'head': 'logreg_c5',
                    'predicted_class_id': logreg_top1,
                    'probability': logreg_prob,
                    'agrees_with_svm': logreg_top1 == top1_raw,
                }
                if logreg_top1 is not None
                else None
            ),
            'full_faiss_top1': (
                {'class_id': faiss_top1, 'cosine_similarity': faiss_top_sim}
                if faiss_top1 is not None
                else None
            ),
            'hybrid_fusion': hybrid_fusion,
            'open_set_gate': {
                'svm_raw_max': svm_raw_max,
                'crop14_raw_max': crop_raw_max,
                'crop14_predicted_crop': crop14_pred,
                'centroid_top_similarity': centroid_top_sim,
                'faiss_top_cosine': faiss_top_sim,
            },
            'top_predictions': top_predictions,
            'image_quality': {
                'blur_lap': blur_lap,
                'brightness': brightness,
            },
            'ood_validation_status': self.ood_validation_status,
            'latency_ms': latency_ms,
        }


def make_shadow_comparison_record(
    v1_result: Dict[str, Any],
    v3_result: Dict[str, Any],
    filename: Optional[str] = None,
) -> Dict[str, Any]:
    """Create a concise telemetry comparison record between production v1 FAISS and shadow v3 SVM."""
    v1_class = v1_result.get('label') or v1_result.get('predicted_class_id') or v1_result.get('raw_label')
    v1_disease = v1_result.get('condition') or v1_result.get('disease')
    if not v1_class and v1_result.get('crop') and v1_disease:
        v1_class = f"{v1_result.get('crop')}/{v1_disease}"

    v3_class = v3_result.get('predicted_class_id')
    return {
        'timestamp_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'filename': filename,
        'active_model': 'v1_dinov2_faiss',
        'shadow_model': v3_result.get('loaded_head', 'svm_cal_c1'),
        'shadow_model_version': v3_result.get('model_version', 'v3_svm'),
        'v1_class_id': v1_class,
        'v1_crop': v1_result.get('crop'),
        'v1_condition': v1_disease,
        'v1_top_cosine_similarity': v1_result.get('top_cosine_similarity'),
        'v1_neighbor_agreement': v1_result.get('neighbor_agreement'),
        'v1_needs_review': v1_result.get('needs_review'),
        'v3_class_id': v3_class,
        'v3_crop': v3_result.get('crop'),
        'v3_disease': v3_result.get('disease'),
        'v3_calibrated_probability': v3_result.get('calibrated_probability'),
        'v3_top2_margin': v3_result.get('top2_margin'),
        'v3_centroid_top_similarity': v3_result.get('centroid_top_similarity'),
        'v3_open_set_gate': v3_result.get('open_set_gate'),
        'v3_hybrid_fusion': v3_result.get('hybrid_fusion'),
        'v3_needs_review': v3_result.get('needs_review'),
        'v3_review_reasons': v3_result.get('review_reasons'),
        'models_agree_class_id': (v1_class == v3_class) if v1_class and v3_class else False,
        'models_agree_crop': (v1_result.get('crop') == v3_result.get('crop')),
        'v3_latency_ms': v3_result.get('latency_ms'),
    }

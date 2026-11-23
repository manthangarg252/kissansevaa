"""KissanSevaAI Phase 1: Crop Disease Detection Inference Service.
DINOv2-S/14 ONNX feature extraction + FAISS cosine similarity vector search.
Run: uvicorn app:app --host 0.0.0.0 --port 8000
"""
import io
import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB limit
MAX_PIXELS = 40_000_000               # 40 Megapixels cap to prevent decompression bombs
MODEL_DIR = Path(os.environ.get('CROP_MODEL_DIR', 'model_assets'))

REQUIRED_FILES = [
    'dinov2_vits14_cls_mean.onnx',
    'crop_index.faiss',
    'train_labels.npy',
    'classes.json',
    'model_meta.json',
]

state: Dict[str, Any] = {}

app = FastAPI(
    title='KissanSevaAI Crop Diagnosis API',
    description='Phase 1 DINOv2-S/14 + FAISS vector similarity crop disease diagnosis pipeline',
    version='2.0.0',
)

# CORS configuration
allowed_origins = [s.strip() for s in os.getenv('CORS_ORIGINS', '*').split(',') if s.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins if allowed_origins else ['*'],
    allow_credentials=True,
    allow_methods=['GET', 'POST', 'OPTIONS'],
    allow_headers=['*'],
)


def get_missing_assets() -> List[str]:
    """Check which required model files are missing from MODEL_DIR."""
    return [fname for fname in REQUIRED_FILES if not (MODEL_DIR / fname).exists()]


def load_assets() -> Dict[str, Any]:
    """Load model, index, labels, and metadata. Raises RuntimeError if any files are missing."""
    import faiss
    import onnxruntime as ort

    absent = get_missing_assets()
    if absent:
        raise RuntimeError(f'Missing required model files in {MODEL_DIR.resolve()}: {absent}')

    meta_path = MODEL_DIR / 'model_meta.json'
    classes_path = MODEL_DIR / 'classes.json'
    labels_path = MODEL_DIR / 'train_labels.npy'
    index_path = MODEL_DIR / 'crop_index.faiss'
    onnx_path = MODEL_DIR / 'dinov2_vits14_cls_mean.onnx'

    meta = json.loads(meta_path.read_text())
    classes = json.loads(classes_path.read_text())
    labels = np.load(labels_path, allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(index_path))

    # Initialize ONNX runtime session with CPU execution provider
    session_options = ort.SessionOptions()
    session_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    session = ort.InferenceSession(str(onnx_path), sess_options=session_options, providers=['CPUExecutionProvider'])

    # Validation assertions matching Colab export specification
    if len(classes) != meta.get('class_count', len(classes)):
        raise ValueError(f"Class count mismatch: classes.json has {len(classes)}, metadata expects {meta.get('class_count')}")
    if len(labels) != index.ntotal:
        raise ValueError(f"Index total rows ({index.ntotal}) differs from label array length ({len(labels)})")
    if index.d != meta.get('embedding_dim', index.d):
        raise ValueError(f"Index dimension ({index.d}) differs from metadata ({meta.get('embedding_dim')})")
    if labels.min() < 0 or labels.max() >= len(classes):
        raise ValueError("Label indices out of bounds of class array")

    input_names = [i.name for i in session.get_inputs()]
    if 'image' not in input_names:
        raise ValueError(f"Unexpected ONNX inputs: expected ['image'], got {input_names}")

    # Smoke inference on zero tensor to ensure dimension and normalization
    image_size = meta.get('image_size', 224)
    fake_input = np.zeros((1, 3, image_size, image_size), dtype=np.float32)
    sample_result = session.run(None, {'image': fake_input})[0]
    if sample_result.shape != (1, index.d) or not np.isfinite(sample_result).all():
        raise RuntimeError(f"ONNX smoke run produced invalid output shape or values: {sample_result.shape}")

    if hasattr(index, 'nprobe') and meta.get('nprobe'):
        index.nprobe = int(meta['nprobe'])

    return {
        'meta': meta,
        'classes': classes,
        'labels': labels,
        'index': index,
        'ort': session,
    }


def preprocess(img: Image.Image, meta: Dict[str, Any]) -> np.ndarray:
    """Preprocess image matching Colab pipeline:
    1. EXIF orientation correction
    2. Convert to RGB
    3. Resize shortest side to 256 (Bilinear)
    4. CenterCrop 224x224
    5. Convert to float32 (0.0 to 1.0)
    6. Normalize with ImageNet mean and std
    7. Return (1, 3, 224, 224) float32 array
    """
    img = ImageOps.exif_transpose(img).convert('RGB')
    w, h = img.size
    if w <= 0 or h <= 0:
        raise ValueError('Image has invalid dimensions')

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


def predict_from_vector(vector: np.ndarray, assets: Dict[str, Any]) -> Dict[str, Any]:
    """Search FAISS index with L2-normalized vector and compute neighbor statistics.
    Note on k=1: If model_meta.json specifies k=1, 1-NN neighbor agreement is trivially 1.0,
    which cannot serve as a calibrated uncertainty threshold. We retrieve 5 nearest neighbors
    to populate reference matches and evaluate neighborhood consensus alongside top similarity.
    """
    import faiss

    meta = assets['meta']
    idx = assets['index']
    labels = assets['labels']
    classes = assets['classes']

    q = np.ascontiguousarray(vector, dtype=np.float32)
    if q.shape != (1, idx.d):
        raise ValueError(f"Unexpected query shape {q.shape}, expected (1, {idx.d})")

    # Cosine distance in FAISS requires L2 normalization
    faiss.normalize_L2(q)

    configured_k = int(meta.get('k', 1))
    search_k = max(5, configured_k)
    similarities, neighbors = idx.search(q, search_k)

    scores = similarities[0]
    rows = neighbors[0]

    valid = rows >= 0
    scores = scores[valid]
    rows = rows[valid]

    if len(rows) == 0:
        raise RuntimeError('Vector index returned no valid neighbors')

    neighbor_labels = labels[rows]

    # Weighted exponential voting across retrieved neighbors
    weights = np.exp(10.0 * (scores - scores.max()))
    votes = np.bincount(neighbor_labels, weights=weights, minlength=len(classes))
    consensus_predicted = int(votes.argmax())
    consensus_agreement = float(votes[consensus_predicted] / max(votes.sum(), 1e-12))

    # If configured k=1, top predicted class is row 0
    if configured_k == 1:
        predicted = int(neighbor_labels[0])
        nominal_agreement = 1.0
    else:
        predicted = consensus_predicted
        nominal_agreement = consensus_agreement

    low_thresh = float(meta.get('low_agreement_threshold', 0.60))
    # Flag needs_review if consensus among neighbors is split, or if top cosine similarity is low
    top_similarity = float(scores[0])
    tentative = (configured_k > 1 and nominal_agreement < low_thresh) or (consensus_agreement < low_thresh) or (top_similarity < 0.50)

    predicted_label = classes[predicted]
    crop_part = predicted_label.split('/')[0] if '/' in predicted_label else predicted_label.split('___')[0]
    condition_part = predicted_label.split('/')[-1] if '/' in predicted_label else predicted_label.split('___')[-1]

    matches = [
        {
            'class_name': classes[int(class_id)],
            'cosine_similarity': round(float(sim), 5),
        }
        for class_id, sim in zip(neighbor_labels[:5], scores[:5])
    ]

    warning_msg = (
        'Visual similarity is not a definitive disease diagnosis. '
        'Neighbor agreement reflects k-NN reference voting, not a calibrated medical probability.'
    )
    if configured_k == 1 and tentative:
        warning_msg += ' (k=1 meta limitation: 1-NN agreement is trivially 1.0, but 5-neighbor consensus is low or similarity < 0.50).'

    return {
        'label': predicted_label,
        'crop': crop_part,
        'condition': condition_part,
        'top_cosine_similarity': round(top_similarity, 5),
        'neighbor_agreement': round(consensus_agreement if configured_k > 1 else round(consensus_agreement, 5), 5),
        'k1_nominal_agreement': nominal_agreement,
        'needs_review': tentative,
        'confidence_type': 'similarity_and_neighbor_consensus_not_calibrated_probability',
        'matches': matches,
        'model': meta.get('model', 'dinov2_vits14'),
        'warning': warning_msg,
    }


@app.get('/health')
def health():
    """Health diagnostic endpoint reporting model asset presence and index counts."""
    absent = get_missing_assets()
    if absent:
        return {
            'ok': False,
            'status': 'assets_missing',
            'model_dir': str(MODEL_DIR.resolve()),
            'missing_files': absent,
            'message': f"Model assets missing. Download from Colab Drive and place in '{MODEL_DIR}'.",
        }

    try:
        if not state:
            state.update(load_assets())
        return {
            'ok': True,
            'status': 'healthy',
            'model': state['meta'].get('model', 'dinov2_vits14'),
            'embedding_dim': state['meta'].get('embedding_dim', 768),
            'classes': len(state['classes']),
            'reference_images': int(state['index'].ntotal),
        }
    except Exception as e:
        return {
            'ok': False,
            'status': 'initialization_error',
            'error': str(e),
        }


@app.post('/api/crop/predict')
async def crop_predict(file: UploadFile = File(...)):
    """Phase 1 Rebuild inference endpoint."""
    if file.content_type not in {'image/jpeg', 'image/png', 'image/webp'}:
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported file type '{file.content_type}'. Please upload a JPEG, PNG, or WebP image.",
        )

    data = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f'Image exceeds maximum allowed size of {MAX_UPLOAD_BYTES // (1024 * 1024)} MB.',
        )

    absent = get_missing_assets()
    if absent:
        raise HTTPException(
            status_code=503,
            detail={
                'error': 'ModelAssetsMissing',
                'message': f'DINOv2 Phase 1 model assets missing: {absent}',
                'missing_files': absent,
                'target_directory': str(MODEL_DIR.resolve()),
            },
        )

    try:
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            if img.width * img.height > MAX_PIXELS:
                raise ValueError(f'Image dimensions too large ({img.width}x{img.height}). Maximum is 40 megapixels.')
            if not state:
                state.update(load_assets())
            tensor = preprocess(img, state['meta'])
    except (UnidentifiedImageError, OSError, ValueError) as e:
        raise HTTPException(status_code=400, detail=f'Invalid or corrupted image: {e}')
    except Exception as e:
        raise HTTPException(status_code=500, detail=f'Image preparation error: {str(e)}')

    try:
        embedding = state['ort'].run(None, {'image': tensor})[0]
        return predict_from_vector(embedding, state)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f'Inference failure: {str(e)}')


@app.post('/predict-crop')
async def predict_crop_legacy(file: UploadFile = File(...)):
    """Backward-compatible endpoint for existing Node.js backend.
    Maps Phase 1 predictions to the legacy schema expected by cropController.js.
    """
    absent = get_missing_assets()
    if absent:
        # Return unknown state with explicit absent metadata so backend falls back cleanly to Gemini Vision
        return {
            'error': 'DINOv2 model assets missing from server',
            'is_unknown': True,
            'source': 'model_assets_missing',
            'missing_files': absent,
        }

    try:
        prediction = await crop_predict(file)
        # Map to legacy shape:
        return {
            'predicted_class': prediction['label'],
            'confidence': prediction['neighbor_agreement'],
            'cosine_similarity': prediction['top_cosine_similarity'],
            'needs_review': prediction['needs_review'],
            'crop': prediction['crop'],
            'condition': prediction['condition'],
            'matches': prediction['matches'],
            'warning': prediction['warning'],
            'is_unknown': prediction['needs_review'],
            'source': 'dinov2',
        }
    except HTTPException as he:
        raise he
    except Exception as e:
        return {
            'error': str(e),
            'is_unknown': True,
            'source': 'inference_error',
        }


if __name__ == '__main__':
    import uvicorn
    uvicorn.run('app:app', host='0.0.0.0', port=8000, reload=False)


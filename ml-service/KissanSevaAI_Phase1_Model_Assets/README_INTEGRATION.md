# KissanSevaAI Phase 1 — Inference Assets

Copy the entire `model_assets/` directory into the existing application at:

    KissanSevaAI/ml-service/model_assets/

If the service uses CROP_MODEL_DIR, point it to that directory.

## Files

- `dinov2_vits14_cls_mean.onnx` — exported DINOv2-S/14 embedding network
- `crop_index.faiss` — FAISS IndexFlatIP (2144 reference vectors, 768 dimensions)
- `train_labels.npy` — 2144 class IDs in FAISS row order
- `classes.json` — 67 crop-condition names
- `model_meta.json` — encoder preprocessing and retrieval configuration

## Verified by file inspection

- 5 files present, 67 unique classes
- FAISS index header: IndexFlatIP, d=768, ntotal=2144
- label IDs in [0, 66], all 67 classes represented (32 references/class)
- preprocessing in model_meta.json: resize short side to 256, center crop 224, normalize with ImageNet mean/std
- metadata reports validation macro F1=0.8033, test accuracy=0.75, test macro F1=0.7414

## IMPORTANT — checks still required

1. Runtime ONNX inference output (1,768) and matching normalization has NOT been independently verified.
2. FAISS index vectors need a full runtime check using faiss-cpu.
3. k=1 in model_meta.json makes neighbor agreement 1 for any valid query; it MUST NOT be interpreted as confidence or used as a meaningful uncertainty threshold. Tune k and rejection rules on a held-out validation set before claiming calibrated reliability.
4. No leaf/non-leaf detection is provided by these assets; use an 'unknown' or expert review path for uncertain/OOD images after validation.
5. The model performance is not sufficient for autonomous pesticide or diagnostic decisions. Treatment advice should cite reliable agronomic guidance and disclose uncertainty.
6. Do not silently fall back to mock disease predictions.

## Tests in the existing repo

    cd ml-service
    python -m pip install -r requirements.txt
    python ../KissanSevaAI_Phase1_Assets_Ready/verify_assets.py ./model_assets
    uvicorn app:app --host 127.0.0.1 --port 8000

Then verify GET /health and POST /api/crop/predict with known and unknown images.
Use the existing app backend route to bridge the Android client to the ML service.

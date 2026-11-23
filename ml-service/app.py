from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import onnxruntime as ort
import numpy as np
from PIL import Image
import io
import json
import os

app = FastAPI(title="KissanSevaAI Plant Disease ML Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(BASE_DIR, "models")
ONNX_PATH = os.path.join(MODELS_DIR, "dinov2_vits14.onnx")
CLASSES_PATH = os.path.join(MODELS_DIR, "classes.json")
METADATA_PATH = os.path.join(MODELS_DIR, "vector_metadata.json")
VECTORS_PATH = os.path.join(MODELS_DIR, "train_vectors.bin")

session = None
classes = []
class_ids = np.array([])
train_vectors_norm = None

def load_resources():
    global session, classes, class_ids, train_vectors_norm

    if not os.path.exists(ONNX_PATH):
        print(f"❌ ONNX model not found at {ONNX_PATH}")
        return False

    try:
        with open(CLASSES_PATH, "r", encoding="utf-8") as f:
            classes = json.load(f)

        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            meta = json.load(f)
            class_ids = np.array(meta.get("class_ids", []))

        raw_vectors = np.fromfile(VECTORS_PATH, dtype=np.float32).reshape(len(class_ids), 768)
        norms = np.linalg.norm(raw_vectors, axis=1, keepdims=True)
        train_vectors_norm = raw_vectors / (norms + 1e-8)

        # Optimize ONNX Runtime for CPU/CoreML
        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        session = ort.InferenceSession(ONNX_PATH, sess_options)

        print(f"✅ Plant Disease Model loaded: {len(classes)} classes, {train_vectors_norm.shape[0]} reference vectors.")
        return True
    except Exception as e:
        print(f"❌ Error loading model resources: {e}")
        return False

load_resources()

def preprocess_image(image_bytes: bytes) -> np.ndarray:
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    img = img.resize((224, 224), Image.Resampling.BILINEAR)

    arr = np.array(img, dtype=np.float32) / 255.0
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    norm = (arr - mean) / std

    chw = np.transpose(norm, (2, 0, 1))
    batch = np.expand_dims(chw, axis=0).astype(np.float32)
    return batch

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_loaded": session is not None,
        "classes_count": len(classes),
        "vectors_count": len(class_ids) if class_ids is not None else 0
    }

@app.post("/predict-crop")
async def predict_crop(
    file: UploadFile = File(None),
    image: UploadFile = File(None)
):
    target_file = file or image
    if target_file is None:
        return {"error": "No file uploaded", "is_unknown": True}

    if session is None or train_vectors_norm is None:
        return {"error": "Model not initialized", "is_unknown": True}

    try:
        contents = await target_file.read()
        batch = preprocess_image(contents)

        # 1. Run DINOv2 ONNX forward pass
        emb = session.run(["embeddings"], {"input_images": batch})[0]
        emb_norm = emb / (np.linalg.norm(emb, axis=1, keepdims=True) + 1e-8)

        # 2. Compute Cosine Similarities against train vectors
        sims = (emb_norm @ train_vectors_norm.T)[0]

        # 3. Soft k-NN Classification (k=10, temperature = 0.05)
        k = min(10, len(sims))
        top_k_indices = np.argsort(sims)[-k:]
        top_k_sims = sims[top_k_indices]
        top_k_classes = class_ids[top_k_indices]

        tau = 0.05
        weights = np.exp((top_k_sims - np.max(top_k_sims)) / tau)
        sum_weights = np.sum(weights)

        class_probabilities = {}
        for cls_id, w in zip(top_k_classes, weights):
            prob = float(w / sum_weights)
            class_probabilities[cls_id] = class_probabilities.get(cls_id, 0.0) + prob

        sorted_classes = sorted(class_probabilities.items(), key=lambda x: x[1], reverse=True)
        top_class_id, confidence = sorted_classes[0]

        predicted_class_name = classes[top_class_id] if top_class_id < len(classes) else "Unknown"

        # Also get max raw cosine similarity
        max_raw_sim = float(np.max(sims))
        is_unknown = max_raw_sim < 0.35 or confidence < 0.40

        top_3 = []
        for cls_id, conf in sorted_classes[:3]:
            cls_name = classes[cls_id] if cls_id < len(classes) else f"class_{cls_id}"
            top_3.append({
                "label": cls_name,
                "confidence": round(conf, 4)
            })

        return {
            "predicted_class": predicted_class_name,
            "confidence": round(max(confidence, min(0.98, max_raw_sim)), 2),
            "raw_similarity": round(max_raw_sim, 4),
            "top_3": top_3,
            "is_unknown": is_unknown
        }
    except Exception as e:
        print(f"Error during prediction: {e}")
        return {"error": str(e), "is_unknown": True}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=False)

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import torch
import timm
from PIL import Image
import io
import torchvision.transforms as transforms
import os

app = FastAPI(title="KissanSevaAI ML Service")

# ✅ CORS (allow backend/frontend to call)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # later you can restrict this
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ✅ Robust model path (works even if you run from root folder)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "models", "best_vit_tiny_patch16_224.pth")

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

# ✅ Replace with your real class labels (in exact trained order)
CLASS_NAMES = [f"class_{i}" for i in range(15)]

# ✅ Preprocessing for ViT 224
# NOTE: If you trained using ImageNet normalization, use the ImageNet mean/std.
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225]
    ),
])

model = None

def load_model():
    global model

    if not os.path.exists(MODEL_PATH):
        print(f"❌ Model not found at: {MODEL_PATH}")
        return None

    m = timm.create_model(
        "vit_tiny_patch16_224",
        pretrained=False,
        num_classes=len(CLASS_NAMES)
    )

    state = torch.load(MODEL_PATH, map_location=DEVICE)

    # ✅ If state dict saved with "model_state_dict" format, handle it
    if isinstance(state, dict) and "state_dict" in state:
        state = state["state_dict"]

    m.load_state_dict(state)
    m.to(DEVICE)
    m.eval()

    print("✅ Model loaded successfully!")
    return m

model = load_model()


@app.get("/health")
def health():
    return {
        "status": "ok",
        "device": DEVICE,
        "model_loaded": model is not None,
        "model_path": MODEL_PATH
    }


@app.post("/predict-crop")
async def predict_crop(image: UploadFile = File(...)):
    if model is None:
        return {
            "error": "Model not loaded. Please check model path.",
            "model_path": MODEL_PATH
        }

    img_bytes = await image.read()
    img = Image.open(io.BytesIO(img_bytes)).convert("RGB")

    x = transform(img).unsqueeze(0).to(DEVICE)

    with torch.no_grad():
        logits = model(x)
        probs = torch.softmax(logits, dim=1)[0].cpu().numpy()

    top_idx = int(probs.argmax())
    confidence = float(probs[top_idx])
    predicted_class = CLASS_NAMES[top_idx]

    # ✅ Unknown threshold (you can tune this)
    is_unknown = confidence < 0.65

    # ✅ Top 3 predictions
    top3_idx = probs.argsort()[-3:][::-1]
    top3 = [{"label": CLASS_NAMES[i], "confidence": float(probs[i])} for i in top3_idx]

    return {
        "predicted_class": predicted_class,
        "confidence": confidence,
        "top_3": top3,
        "is_unknown": is_unknown
    }

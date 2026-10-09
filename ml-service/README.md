# KissanSevaAI ML Service

Inference server for Crop Disease Classification using ViT-Tiny.

## Requirements
- Python 3.9+
- PyTorch & Timm

## Setup
1. Create models folder: `mkdir models`
2. Place `best_vit_tiny_patch16_224.pth` inside `models/`
3. Install dependencies: `pip install -r requirements.txt`
4. Run: `uvicorn app:app --reload --port 8000`

## API
- `GET /health`
- `POST /predict-crop` (multipart/form-data)
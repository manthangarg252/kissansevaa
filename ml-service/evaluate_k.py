"""Evaluate k=1 limitation and alternative k values on real images in repository."""
import json
from pathlib import Path
import numpy as np
from PIL import Image
import faiss
import onnxruntime as ort

MODEL_DIR = Path('ml-service/model_assets')
meta = json.loads((MODEL_DIR / 'model_meta.json').read_text())
classes = json.loads((MODEL_DIR / 'classes.json').read_text())
labels = np.load(MODEL_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)
index = faiss.read_index(str(MODEL_DIR / 'crop_index.faiss'))
sess = ort.InferenceSession(str(MODEL_DIR / 'dinov2_vits14_cls_mean.onnx'), providers=['CPUExecutionProvider'])

def preprocess(img_path):
    from PIL import ImageOps
    with Image.open(img_path) as img:
        img = ImageOps.exif_transpose(img).convert('RGB')
        w, h = img.size
        short = 256
        if w < h:
            nw = short
            nh = int(round(short * h / w))
        else:
            nh = short
            nw = int(round(short * w / h))
        img = img.resize((nw, nh), resample=Image.Resampling.BILINEAR)
        size = 224
        left = (nw - size) // 2
        top = (nh - size) // 2
        img = img.crop((left, top, left + size, top + size))
        arr = np.asarray(img, dtype=np.float32).transpose(2, 0, 1) / 255.0
        mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)[:, None, None]
        std = np.array([0.229, 0.224, 0.225], dtype=np.float32)[:, None, None]
        return ((arr - mean) / std)[None].astype('float32')

test_images = [
    ('backend/uploads/1792053583076-leaf.jpg', 'Real Leaf 1'),
    ('backend/uploads/1792059573354-leaf.jpg', 'Real Leaf 2'),
    ('backend/uploads/1791320979138-test_leaf.jpg', 'Synthetic/Generated Leaf'),
    ('backend/uploads/1791320982180-test_cow.jpg', 'Non-leaf (Cow / Out of domain)'),
    ('backend/uploads/1792053552369-animal.jpg', 'Non-leaf (Animal / Out of domain)'),
]

print("=" * 80)
print("EVALUATION OF k VALUES AND UNCERTAINTY IN FAISS RETRIEVAL")
print("=" * 80)

for img_path, desc in test_images:
    p = Path(img_path)
    if not p.exists():
        continue
    try:
        tensor = preprocess(p)
    except Exception as e:
        print(f"\nTarget: {desc} ({p.name}) -> Preprocessing / Validation rejected: {e}")
        continue
    emb = sess.run(None, {'image': tensor})[0]
    faiss.normalize_L2(emb)
    
    print(f"\nTarget: {desc} ({p.name})")
    
    for k in [1, 3, 5, 7, 10]:
        sims, neighbors = index.search(emb, k)
        scores = sims[0]
        rows = neighbors[0]
        neighbor_classes = [classes[labels[r]] for r in rows]
        
        # Weighted exponential voting
        weights = np.exp(10.0 * (scores - scores.max()))
        votes = np.bincount(labels[rows], weights=weights, minlength=len(classes))
        pred_idx = int(votes.argmax())
        pred_label = classes[pred_idx]
        agreement = float(votes[pred_idx] / votes.sum())
        
        # Majority count voting
        counts = np.bincount(labels[rows], minlength=len(classes))
        majority_agreement = float(counts[pred_idx] / k)
        
        print(f"  k={k:2d}: Top Sim={scores[0]:.4f} | Weighted Agree={agreement:.3f} | Maj Agree={majority_agreement:.3f} | Pred: {pred_label}")
        if k == 5:
            print(f"       Top 5 matches:")
            for rank, (score, cls_name) in enumerate(zip(scores, neighbor_classes)):
                print(f"         {rank+1}. {cls_name} (sim={score:.4f})")

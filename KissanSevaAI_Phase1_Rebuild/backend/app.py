"""KissanSevaAI crop inference. Run: uvicorn app:app --host 0.0.0.0 --port 8000"""
import io
import json
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MODEL_DIR = Path(os.environ.get('CROP_MODEL_DIR', 'model_assets'))

# Lazy initialization allows health diagnostics even when assets are not installed.
state = {}
app = FastAPI(title='KissanSevaAI Crop Diagnosis API', version='1.0')
allowed = [s.strip() for s in os.getenv('CORS_ORIGINS', '').split(',') if s.strip()]
if allowed:
    app.add_middleware(CORSMiddleware, allow_origins=allowed, allow_methods=['GET','POST'], allow_headers=['*'])


def load_assets():
    import faiss
    import onnxruntime as ort
    files = ['dinov2_vits14_cls_mean.onnx','crop_index.faiss', 'train_labels.npy',
             'classes.json','model_meta.json']
    absent = [x for x in files if not (MODEL_DIR / x).exists()]
    if absent:
        raise RuntimeError(f'Missing model files in {MODEL_DIR}: {absent}')
    meta = json.loads((MODEL_DIR / 'model_meta.json').read_text())
    classes = json.loads((MODEL_DIR / 'classes.json').read_text())
    labels = np.load(MODEL_DIR / 'train_labels.npy', allow_pickle=False).astype(np.int32)
    index = faiss.read_index(str(MODEL_DIR / 'crop_index.faiss'))
    session = ort.InferenceSession(str(MODEL_DIR / 'dinov2_vits14_cls_mean.onnx'),
                                   providers=['CPUExecutionProvider'])
    assert len(classes)==meta['class_count'], 'Number of classes does not match metadata'
    assert len(labels)==index.ntotal, 'Index row count differs from label mapping'
    assert index.d==meta['embedding_dim'], 'Index dimension differs from metadata'
    assert labels.min()>=0 and labels.max()<len(classes), 'Label IDs out of range'
    input_names=[i.name for i in session.get_inputs()]
    assert input_names==['image'],f'Unexpected ONNX inputs: {input_names}'
    # Smoke inference on zero tensor to ensure dimension and normalization.
    fake=np.zeros((1,3,meta['image_size'],meta['image_size']),dtype=np.float32)
    result=session.run(None, {'image':fake})[0]
    assert result.shape==(1,index.d) and np.isfinite(result).all(), 'ONNX output invalid'
    if hasattr(index,'nprobe') and meta.get('nprobe'):
        index.nprobe=int(meta['nprobe'])
    return {'meta':meta,'classes':classes,'labels':labels,'index':index,'ort':session}


def preprocess(img, meta):
    # Matches Colab: Resize shortest side to 256, CenterCrop 224, ToTensor, ImageNet norm.
    img=ImageOps.exif_transpose(img).convert('RGB')
    w,h=img.size
    if w<=0 or h<=0:
        raise ValueError('Empty image')
    short=meta['resize_short_side']
    if w<h:
        nw=short;nh=int(short*h/w)
    else:
        nh=short;nw=int(short*w/h)
    img=img.resize((nw,nh),resample=Image.Resampling.BILINEAR)
    size=meta['image_size']
    left=(nw-size)//2;top=(nh-size)//2
    img=img.crop((left,top,left+size,top+size))
    arr=np.asarray(img,dtype=np.float32).transpose(2,0,1)/255.0
    mean=np.asarray(meta['mean'],dtype=np.float32)[:,None,None]
    std=np.asarray(meta['std'],dtype=np.float32)[:,None,None]
    return ((arr-mean)/std)[None].astype('float32')


def predict_from_vector(vector, assets):
    import faiss
    meta=assets['meta']; idx=assets['index']; labels=assets['labels']; classes=assets['classes']
    q=np.ascontiguousarray(vector,dtype=np.float32)
    assert q.shape==(1,idx.d),f'Unexpected query shape: {q.shape}'
    faiss.normalize_L2(q)
    k=int(meta['k'])
    similarities, neighbors=idx.search(q,k)
    scores=similarities[0]; rows=neighbors[0]
    valid=rows>=0
    scores=scores[valid]; rows=rows[valid]
    if len(rows)==0:
        raise RuntimeError('Vector index returned no neighbors')
    neighbor_labels=labels[rows]
    if meta['vote_method']=='weighted':
        weights=np.exp(10.0*(scores-scores.max()))
    else:
        weights=np.ones(len(scores))
    votes=np.bincount(neighbor_labels,weights=weights,minlength=len(classes))
    predicted=int(votes.argmax())
    agreement=float(votes[predicted]/max(votes.sum(),1e-12))
    tentative=agreement<float(meta.get('low_agreement_threshold',.6))
    matches=[{'class_name':classes[int(class_id)],'cosine_similarity':round(float(sim),5)}
             for class_id,sim in zip(neighbor_labels,scores)]
    return {'label':classes[predicted], 'crop':classes[predicted].split('/')[0],
            'condition':classes[predicted].split('/')[-1],
            'top_cosine_similarity':float(scores[0]),
            'neighbor_agreement':agreement,
            'needs_review':tentative,
            'confidence_type':'neighbor_agreement_not_calibrated_probability',
            'matches':matches,
            'model':'dinov2_vits14',
            'warning': 'Visual similarity is not a definitive disease diagnosis; unknown or non-leaf inputs are not reliably rejected.'}


@app.get('/health')
def health():
    try:
        if not state: state.update(load_assets())
        return {'ok':True,'model':state['meta']['model'],'classes':len(state['classes']),
                'reference_images':int(state['index'].ntotal)}
    except Exception as e:
        raise HTTPException(status_code=503,detail=str(e))


@app.post('/api/crop/predict')
async def crop_predict(file: UploadFile = File(...)):
    if file.content_type not in {'image/jpeg','image/png','image/webp'}:
        raise HTTPException(status_code=415,detail='Please upload a JPEG, PNG or WebP image')
    data=await file.read(MAX_UPLOAD_BYTES+1)
    if len(data)>MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413,detail='Image exceeds 10 MB')
    try:
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            if img.width*img.height>40_000_000:
                raise ValueError('Too many pixels')
            if not state: state.update(load_assets())
            tensor=preprocess(img,state['meta'])
    except (UnidentifiedImageError,OSError,ValueError) as e:
        raise HTTPException(status_code=400,detail=f'Invalid image: {e}')
    except RuntimeError as e:
        raise HTTPException(status_code=503,detail=str(e))
    try:
        embedding=state['ort'].run(None, {'image':tensor})[0]
        return predict_from_vector(embedding,state)
    except Exception as e:
        raise HTTPException(status_code=500,detail=f'Inference error: {str(e)}')

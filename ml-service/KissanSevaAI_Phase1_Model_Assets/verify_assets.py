#!/usr/bin/env python3
"""Check all Phase 1 asset files with installed inference dependencies."""
import json
import sys
from pathlib import Path
import numpy as np

root=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).parent/'model_assets'
classes=json.loads((root/'classes.json').read_text())
meta=json.loads((root/'model_meta.json').read_text())
labels=np.load(root/'train_labels.npy',allow_pickle=False)
assert len(classes)==meta['class_count'] == 67
assert len(labels)>0 and min(labels)>=0 and max(labels)<len(classes)
print(f'Labels OK: {len(labels)} reference vectors, {len(classes)} classes')

import faiss
idx=faiss.read_index(str(root/'crop_index.faiss'))
assert idx.ntotal==len(labels) and idx.d==meta['embedding_dim']
print(f'FAISS OK: {type(idx).__name__}, {idx.ntotal} x {idx.d}')

import onnxruntime as ort
sess=ort.InferenceSession(str(root/'dinov2_vits14_cls_mean.onnx'),providers=['CPUExecutionProvider'])
inputs=sess.get_inputs(); outputs=sess.get_outputs()
print('ONNX input:',[(v.name,v.shape,v.type) for v in inputs])
print('ONNX output:',[(v.name,v.shape,v.type) for v in outputs])
assert len(inputs)==1, 'Expected one tensor input'
assert len(outputs)==1, 'Expected a single 768-D embedding output, inspect graph if not'
assert inputs[0].shape[-3:]==[3,224,224], 'Input image dimensions mismatch'
print('Running model once on synthetic image...')
a=np.random.default_rng(42).standard_normal((1,3,224,224)).astype(np.float32)
y=sess.run(None,{inputs[0].name:a})[0]
assert y.shape==(1,meta['embedding_dim']),f'Unexpected ONNX output {y.shape}'
assert np.isfinite(y).all()
print('ONNX OK:',y.shape,'norm:',np.linalg.norm(y,axis=1))
print('Asset verification PASSED. Next: test actual leaf images end-to-end.')

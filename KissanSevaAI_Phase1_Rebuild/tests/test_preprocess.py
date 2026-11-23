"""Unit tests for preprocessing and voting logic. Requires backend dependencies."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'))
import numpy as np
from PIL import Image
from app import preprocess, predict_from_vector

META={'resize_short_side':256,'image_size':224,'mean':[.485,.456,.406], 'std':[.229,.224,.225]}

def test_shape():
    x=preprocess(Image.new('RGB',(600,800),'green'),META)
    assert x.shape==(1,3,224,224)
    assert np.isfinite(x).all()


def test_vote():
    class FakeIndex:
        d=3
        def search(self,q,k):
            return np.array([[.9,.8,.7]],dtype='float32'), np.array([[0,1,2]],dtype='int64')
    assets={'meta':{'k':3,'vote_method':'majority','low_agreement_threshold':.6},
            'index':FakeIndex(), 'labels':np.array([0,0,1]),'classes':['Tomato/healthy','Tomato/blight']}
    p=predict_from_vector(np.array([[1,0,0]],dtype='float32'),assets)
    assert p['label']=='Tomato/healthy'
    assert abs(p['neighbor_agreement']-2/3)<.0001

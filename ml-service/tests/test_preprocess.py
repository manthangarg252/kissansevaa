"""Unit tests for Phase 1 DINOv2-S/14 preprocessing and FAISS voting logic.
Matching Colab export specification.
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import pytest
from PIL import Image
from app import preprocess, predict_from_vector

META = {
    'resize_short_side': 256,
    'image_size': 224,
    'mean': [0.485, 0.456, 0.406],
    'std': [0.229, 0.224, 0.225],
    'embedding_dim': 768,
    'k': 3,
    'vote_method': 'weighted',
    'low_agreement_threshold': 0.60,
    'model': 'dinov2_vits14',
}


def test_preprocess_portrait():
    """Portrait image (600x800) resized to short side 256, center cropped to 224x224."""
    img = Image.new('RGB', (600, 800), color=(34, 139, 34))
    tensor = preprocess(img, META)
    assert tensor.shape == (1, 3, 224, 224)
    assert tensor.dtype == np.float32
    assert np.isfinite(tensor).all()


def test_preprocess_landscape():
    """Landscape image (1024x768) resized to short side 256, center cropped to 224x224."""
    img = Image.new('RGB', (1024, 768), color=(255, 100, 50))
    tensor = preprocess(img, META)
    assert tensor.shape == (1, 3, 224, 224)
    assert tensor.dtype == np.float32
    assert np.isfinite(tensor).all()


def test_preprocess_square():
    """Square image (500x500) resized to short side 256, center cropped to 224x224."""
    img = Image.new('RGB', (500, 500), color=(10, 50, 200))
    tensor = preprocess(img, META)
    assert tensor.shape == (1, 3, 224, 224)
    assert tensor.dtype == np.float32
    assert np.isfinite(tensor).all()


def test_preprocess_zero_dimension():
    """Image with 0 dimension must raise ValueError."""
    img = Image.new('RGB', (0, 0))
    with pytest.raises(ValueError):
        preprocess(img, META)


def test_predict_from_vector_weighted_vote():
    """FAISS search with weighted vote correctly identifies dominant class."""
    class FakeIndex:
        d = 768
        def search(self, q, k):
            # Similarities and neighbors
            return np.array([[0.95, 0.90, 0.40]], dtype=np.float32), np.array([[0, 1, 2]], dtype=np.int64)

    assets = {
        'meta': META,
        'index': FakeIndex(),
        'labels': np.array([0, 0, 1]),  # 2 votes for class 0, 1 vote for class 1
        'classes': ['Tomato/Early_blight', 'Potato/Late_blight', 'Corn/Common_rust'],
    }

    query = np.ones((1, 768), dtype=np.float32)
    result = predict_from_vector(query, assets)

    assert result['label'] == 'Tomato/Early_blight'
    assert result['crop'] == 'Tomato'
    assert result['condition'] == 'Early_blight'
    assert result['top_cosine_similarity'] == 0.95
    assert result['neighbor_agreement'] > 0.60
    assert result['needs_review'] is False
    assert len(result['matches']) == 3
    assert 'not a definitive disease diagnosis' in result['warning']


def test_predict_from_vector_uncertain_warning():
    """When neighbor agreement is below threshold, needs_review must be True."""
    class SplitIndex:
        d = 768
        def search(self, q, k):
            # Equal similarity across 3 distinct classes
            return np.array([[0.61, 0.60, 0.59]], dtype=np.float32), np.array([[0, 1, 2]], dtype=np.int64)

    assets = {
        'meta': {**META, 'vote_method': 'majority', 'low_agreement_threshold': 0.60},
        'index': SplitIndex(),
        'labels': np.array([0, 1, 2]),  # 1 vote for class 0, 1 for class 1, 1 for class 2 (33% each)
        'classes': ['Apple/Scab', 'Grape/Black_rot', 'Tomato/Bacterial_spot'],
    }

    query = np.ones((1, 768), dtype=np.float32)
    result = predict_from_vector(query, assets)

    assert result['needs_review'] is True
    assert result['neighbor_agreement'] < 0.60

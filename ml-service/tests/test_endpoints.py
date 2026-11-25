"""Integration and error handling tests for FastAPI inference service."""
import io
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from fastapi.testclient import TestClient
from PIL import Image
from app import app

client = TestClient(app)


def test_health_reports_assets_status():
    """When model assets are present in model_assets/, health reports ok=True and index status."""
    response = client.get('/health')
    assert response.status_code == 200
    data = response.json()
    assert data['ok'] is True
    assert data['status'] == 'healthy'
    assert data['classes'] == 67
    assert data['reference_images'] == 2144
    assert data['embedding_dim'] == 768


def test_predict_real_image_inference():
    """Predict endpoint with real model assets successfully infers crop disease on valid image."""
    img_bytes = io.BytesIO()
    Image.new('RGB', (224, 224), color=(34, 139, 34)).save(img_bytes, format='JPEG')
    img_bytes.seek(0)

    response = client.post(
        '/api/crop/predict',
        files={'file': ('leaf.jpg', img_bytes, 'image/jpeg')},
    )
    assert response.status_code == 200
    data = response.json()
    assert data['source'] == 'dinov2'
    assert data['confidence'] is None
    assert 'label' in data
    assert 'crop' in data
    assert 'top_cosine_similarity' in data
    assert 'neighbor_agreement' in data
    assert 'needs_review' in data
    assert 'matches' in data
    assert len(data['matches']) == 5
    assert 'confidence_type' in data
    assert 'warning' in data


def test_legacy_predict_crop_with_real_model():
    """Legacy /predict-crop returns mapped DINOv2 schema for Node.js backend."""
    img_bytes = io.BytesIO()
    Image.new('RGB', (224, 224), color=(34, 139, 34)).save(img_bytes, format='JPEG')
    img_bytes.seek(0)

    response = client.post(
        '/predict-crop',
        files={'file': ('leaf.jpg', img_bytes, 'image/jpeg')},
    )
    assert response.status_code == 200
    data = response.json()
    assert data['source'] == 'dinov2'
    assert data['confidence'] is None
    assert data['is_unknown'] is False
    assert 'predicted_class' in data
    assert 'neighbor_agreement' in data
    assert 'top_cosine_similarity' in data
    assert 'cosine_similarity' in data
    assert 'needs_review' in data
    assert 'matches' in data


def test_predict_handles_missing_assets_gracefully(tmp_path, monkeypatch):
    """Predict endpoint returns 503 with structured missing asset details when model files are not found."""
    import app as app_module
    monkeypatch.setattr(app_module, 'MODEL_DIR', tmp_path)
    monkeypatch.setattr(app_module, 'state', {})

    img_bytes = io.BytesIO()
    Image.new('RGB', (100, 100), color='green').save(img_bytes, format='JPEG')
    img_bytes.seek(0)

    response = client.post(
        '/api/crop/predict',
        files={'file': ('leaf.jpg', img_bytes, 'image/jpeg')},
    )
    assert response.status_code == 503
    detail = response.json()['detail']
    assert detail['error'] == 'ModelAssetsMissing'
    assert len(detail['missing_files']) == 5


def test_legacy_bridge_endpoint_missing_assets(tmp_path, monkeypatch):
    """Legacy /predict-crop returns is_unknown=True and source='model_assets_missing' when assets are absent."""
    import app as app_module
    monkeypatch.setattr(app_module, 'MODEL_DIR', tmp_path)
    monkeypatch.setattr(app_module, 'state', {})

    img_bytes = io.BytesIO()
    Image.new('RGB', (100, 100), color='green').save(img_bytes, format='JPEG')
    img_bytes.seek(0)

    response = client.post(
        '/predict-crop',
        files={'file': ('leaf.jpg', img_bytes, 'image/jpeg')},
    )
    assert response.status_code == 200
    data = response.json()
    assert data['is_unknown'] is True
    assert data['source'] == 'model_assets_missing'
    assert len(data['missing_files']) == 5

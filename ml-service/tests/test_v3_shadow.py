import hashlib
import io
import json
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

import app as ml_app
from v3_svm_adapter import V3SvmCandidateAdapter


def _make_test_jpeg_bytes(color=(45, 130, 55), size=(280, 280)) -> bytes:
    arr = np.zeros((size[1], size[0], 3), dtype=np.uint8)
    arr[:, :] = color
    # Add mild texture strips so image quality metrics are deterministic and valid
    arr[40:240:8, 40:240:8] = [180, 120, 40]
    img = Image.fromarray(arr, mode='RGB')
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=90)
    return buf.getvalue()


def test_v3_adapter_health_and_honest_weight_disclosure():
    # Default candidate points to repaired V3 bundle (exact svm_linear_cal_C2.0, zero mismatch)
    adapter = V3SvmCandidateAdapter()
    info = adapter.health_info()
    assert info['ok'] is True
    assert info['model_version'] == 'v3_svm_repaired'
    assert info['embedding_dim'] == 768
    assert info['classes'] == 67
    assert info['loaded_head'] == 'svm_linear_cal_C2.0'
    assert info['loaded_svm_c'] == pytest.approx(2.0)
    assert info['reported_colab_winner'] == 'svm_linear_cal_C2.0'
    assert info['winner_weight_mismatch'] is False

    parity = adapter.verify_parity()
    assert parity['available'] is True
    assert parity['num_cases'] == 12
    assert parity['top1_matches'] == 12
    assert parity['max_abs_prob_diff'] < 1e-6
    assert parity['passed'] is True

    # Legacy candidate folder must continue disclosing C=1.0 vs C=2.0 weight mismatch honestly
    legacy = V3SvmCandidateAdapter(candidate_dir=Path('model_candidates/v3_svm'))
    legacy_info = legacy.health_info()
    assert legacy_info['ok'] is True
    assert legacy_info['model_version'] == 'v3_svm'
    assert legacy_info['loaded_head'] == 'svm_cal_c1'
    assert legacy_info['loaded_svm_c'] == pytest.approx(1.0)
    assert legacy_info['winner_weight_mismatch'] is True


def test_v3_adapter_predict_vector_validation_and_review_gating():
    adapter = V3SvmCandidateAdapter()
    with pytest.raises(ValueError, match='768-D'):
        adapter.predict_vector(np.ones(512, dtype=np.float32))

    vec = np.linspace(-1.0, 1.0, 768, dtype=np.float32)
    r1 = adapter.predict_vector(vec, blur_lap=150.0, brightness=110.0)
    r2 = adapter.predict_vector(vec * 3.5, blur_lap=150.0, brightness=110.0)
    # Scaling invariant due to L2 normalization
    assert r1['predicted_class_id'] == r2['predicted_class_id']
    assert r1['calibrated_probability'] == pytest.approx(r2['calibrated_probability'])
    assert len(r1['top_predictions']) == 5
    probs = [p['calibrated_probability'] for p in r1['top_predictions']]
    assert probs == sorted(probs, reverse=True)
    assert r1['top2_margin'] >= 0.0
    assert r1['ood_validation_status'] == 'repaired_local_and_leave_crop_out_calibrated'
    assert r1['open_set_gate']['svm_raw_max'] is not None
    assert r1['open_set_gate']['crop14_raw_max'] is not None

    # Full FAISS hybrid calibration & disagreement abstention test
    hybrid = V3SvmCandidateAdapter(load_full_faiss=True)
    h_res = hybrid.predict_vector(vec, blur_lap=150.0, brightness=110.0)
    assert h_res['hybrid_fusion'] is not None
    assert 'fused_class_id' in h_res['hybrid_fusion']
    assert 'svm_faiss_agree' in h_res['hybrid_fusion']

    # Blur and darkness flags must force needs_review=True even with synthetic vector
    blurry = adapter.predict_vector(vec, blur_lap=12.0, brightness=110.0)
    assert blurry['needs_review'] is True
    assert any('blurry_image' in reason for reason in blurry['review_reasons'])

    dark = adapter.predict_vector(vec, blur_lap=150.0, brightness=10.0)
    assert dark['needs_review'] is True
    assert any('low_brightness' in reason for reason in dark['review_reasons'])


def test_v3_endpoints_and_shadow_mode_preserve_v1_production(monkeypatch, tmp_path):
    shadow_log = tmp_path / 'shadow_comparison.jsonl'
    monkeypatch.setenv('ACTIVE_CROP_MODEL', 'v1')
    monkeypatch.setenv('ENABLE_V3_SHADOW', 'true')
    monkeypatch.setenv('SHADOW_LOG_PATH', str(shadow_log))

    client = TestClient(ml_app.app)

    h = client.get('/health')
    assert h.status_code == 200
    h_json = h.json()
    assert h_json['ok'] is True
    assert h_json['active_model'] == 'v1'
    assert h_json['shadow_mode_enabled'] is True
    assert h_json['reference_images'] == 2144

    vh = client.get('/api/crop/v3/health')
    assert vh.status_code == 200
    vh_json = vh.json()
    assert vh_json['ok'] is True
    assert vh_json['model_version'] == 'v3_svm_repaired'
    assert vh_json['loaded_head'] == 'svm_linear_cal_C2.0'
    assert vh_json['winner_weight_mismatch'] is False
    assert vh_json['active_production_model'] == 'v1'
    assert vh_json['rollback_supported'] is True

    img_bytes = _make_test_jpeg_bytes()

    # Production endpoint still serves v1 FAISS contract intact while shadow-logging v3
    p_v1 = client.post(
        '/api/crop/predict',
        files={'file': ('leaf_test.jpg', img_bytes, 'image/jpeg')},
    )
    assert p_v1.status_code == 200
    v1_json = p_v1.json()
    assert v1_json['source'] == 'dinov2'
    assert v1_json['confidence'] is None
    assert 'top_cosine_similarity' in v1_json
    assert 'neighbor_agreement' in v1_json
    assert 'needs_review' in v1_json
    assert 'shadow_model' in v1_json
    assert v1_json['shadow_model']['active_production_model'] == 'v1'
    assert v1_json['shadow_model']['candidate_version'] == 'v3_svm_repaired'
    assert v1_json['shadow_model']['loaded_head'] == 'svm_linear_cal_C2.0'

    # Shadow JSONL was persisted cleanly
    assert shadow_log.exists()
    rows = [json.loads(line) for line in shadow_log.read_text(encoding='utf-8').splitlines() if line.strip()]
    assert len(rows) == 1
    assert rows[0]['v1_class_id'] == v1_json['label']
    assert rows[0]['v3_class_id'] == v1_json['shadow_model']['predicted_class_id']

    # Dedicated versioned V3 endpoint works directly
    p_v3 = client.post(
        '/api/crop/v3/predict',
        files={'file': ('leaf_test.jpg', img_bytes, 'image/jpeg')},
    )
    assert p_v3.status_code == 200
    v3_json = p_v3.json()
    assert v3_json['model_version'] == 'v3_svm_repaired'
    assert v3_json['loaded_head'] == 'svm_linear_cal_C2.0'
    assert v3_json['v1_baseline_comparison']['predicted_class_id'] == v1_json['label']

    stats = client.get('/api/crop/shadow/stats')
    assert stats.status_code == 200
    stats_json = stats.json()
    assert stats_json['ok'] is True
    assert stats_json['total_comparisons'] == 1
    assert stats_json['active_production_model'] == 'v1'


def test_shadow_mode_can_be_disabled_for_clean_rollback(monkeypatch, tmp_path):
    shadow_log = tmp_path / 'shadow_disabled.jsonl'
    monkeypatch.setenv('ACTIVE_CROP_MODEL', 'v1')
    monkeypatch.setenv('ENABLE_V3_SHADOW', 'false')
    monkeypatch.setenv('SHADOW_LOG_PATH', str(shadow_log))

    client = TestClient(ml_app.app)
    img_bytes = _make_test_jpeg_bytes()
    res = client.post(
        '/api/crop/predict',
        files={'file': ('rollback_check.jpg', img_bytes, 'image/jpeg')},
    )
    assert res.status_code == 200
    body = res.json()
    assert body['source'] == 'dinov2'
    assert 'shadow_model' not in body
    assert not shadow_log.exists()


def test_production_v1_model_assets_remain_unmodified():
    manifest_path = Path('KissanSevaAI_Phase1_Model_Assets/SHA256_MANIFEST.json')
    if not manifest_path.exists():
        pytest.skip('Production SHA256 manifest not present at expected relative path')
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    assets_dir = Path('model_assets')
    files = manifest.get('files', manifest)
    for rel_name, meta in files.items():
        if isinstance(meta, dict):
            expected_sha = meta.get('sha256')
        else:
            expected_sha = meta
        target = assets_dir / Path(rel_name).name
        assert target.exists(), f'Missing production asset {target}'
        if expected_sha:
            actual = hashlib.sha256(target.read_bytes()).hexdigest()
            assert actual == expected_sha

#!/usr/bin/env python3
"""
Unit tests for AirCloak Edge Inference Engine
"""

import pytest
from onnx_pipeline import EdgeInferenceEngine

def test_extract_entities():
    engine = EdgeInferenceEngine(use_npu=False)
    sample_text = "ERROR: Failed auth with token: secret_jwt_payload_9876543210 at server 192.168.1.100"
    entities = engine.extract_entities(sample_text)
    
    types = [e["entity_type"] for e in entities]
    assert "CREDENTIAL_SECRET" in types or "AUTH_BEARER_TOKEN" in types
    assert "IP_ENDPOINT" in types

def test_generate_redaction_masks():
    engine = EdgeInferenceEngine(use_npu=False)
    sample_text = "Target IP is 10.0.0.1 and key is api_key=secret_1234567890abcdef"
    res = engine.generate_redaction_masks(sample_text)
    
    assert res["entities_found"] >= 2
    assert "10.0.0.1" not in res["redacted_text"]
    assert "REDACTED" in res["redacted_text"]

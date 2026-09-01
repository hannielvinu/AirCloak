#!/usr/bin/env python3
"""
Unit tests for AirCloak Ingest Proxy.
Tests heuristic detection, tokenization, reconstitution, encrypted vault, and emergency purge.
"""

import pytest
import json
from proxy_server import AirCloakSanitizer, EncryptedVault, SECRET_PATTERNS

SAMPLE_PAYLOAD_ENV = """
# Production Configuration (.env)
DATABASE_URL=postgresql://db_admin:SuperSecretPass123!@198.51.100.45:5432/app_production
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
STRIPE_SECRET_KEY=sk_live_51NzABC1234567890abcdefghijklm
JWT_AUTH_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJhZG1pbi05OTkiLCJyb2xlIjoic3VwZXJhZG1pbiJ9.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
SERVER_NODE_IP=203.0.113.195
"""

def test_encrypted_vault_store_and_retrieve():
    vault = EncryptedVault()
    token_id = "AC_TOK_TEST123"
    raw_secret = "SuperSecretAPIKey_999"

    vault.store(token_id, raw_secret)
    retrieved = vault.retrieve(token_id)
    assert retrieved == raw_secret
    assert vault.retrieve("NON_EXISTENT") is None

def test_encrypted_vault_emergency_purge():
    vault = EncryptedVault()
    vault.store("TOK_1", "Secret1")
    vault.store("TOK_2", "Secret2")
    assert vault.retrieve("TOK_1") == "Secret1"

    count = vault.purge_all()
    assert count == 2
    assert vault.retrieve("TOK_1") is None
    assert vault.retrieve("TOK_2") is None

def test_sanitize_payload_detects_all_secret_types():
    sanitizer = AirCloakSanitizer()
    sanitized, audit_log = sanitizer.sanitize(SAMPLE_PAYLOAD_ENV)

    assert "AKIAIOSFODNN7EXAMPLE" not in sanitized
    assert "sk_live_51NzABC1234567890abcdefghijklm" not in sanitized
    assert "postgresql://db_admin:SuperSecretPass123!@198.51.100.45:5432/app_production" not in sanitized
    assert "203.0.113.195" not in sanitized

    detected_types = {item["secret_type"] for item in audit_log}
    assert "AWS_ACCESS_KEY" in detected_types
    assert "STRIPE_LIVE_SECRET" in detected_types
    assert "DB_CONNECTION_URI" in detected_types
    assert "JWT_TOKEN" in detected_types

def test_reconstitution_roundtrip():
    sanitizer = AirCloakSanitizer()
    raw_payload = "Connect with postgresql://admin:p@ssword!@203.0.113.50:5432/main_db using AKIAIOSFODNN7EXAMPLE"
    sanitized, audit_log = sanitizer.sanitize(raw_payload)

    # Build token map
    token_map = {item["mock_value"]: item["token_id"] for item in audit_log}

    reconstituted = sanitizer.reconstitute(sanitized, token_map)
    assert reconstituted == raw_payload

def test_ipv4_loopback_exemption():
    sanitizer = AirCloakSanitizer()
    payload = "Localhost is 127.0.0.1 and broadcast is 0.0.0.0, but public is 198.51.100.22"
    sanitized, audit_log = sanitizer.sanitize(payload)

    assert "127.0.0.1" in sanitized
    assert "0.0.0.0" in sanitized
    assert "198.51.100.22" not in sanitized

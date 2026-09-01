#!/usr/bin/env python3
"""
AirCloak - Ingest Proxy Daemon
Edge-native zero-leak credential interceptor and encrypted tokenization vault.
"""

import asyncio
import json
import os
import re
import secrets
import logging
from dataclasses import dataclass
from typing import Dict, List, Tuple, Optional
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [AirCloak-Proxy] %(message)s"
)
logger = logging.getLogger("AirCloakProxy")

# Comprehensive Secret & PII Heuristic Signatures
SECRET_PATTERNS = {
    "AWS_ACCESS_KEY": re.compile(r"\b(AKIA[0-9A-Z]{16})\b"),
    "AWS_SECRET_KEY": re.compile(r"(?i)\baws[_\-\s]?secret[_\-\s]?(?:access[_\-\s]?)?key\s*[:=]\s*['\"]?([A-Za-z0-9/+=]{40})['\"]?"),
    "STRIPE_LIVE_SECRET": re.compile(r"\b(sk_live_[0-9a-zA-Z]{24,34})\b"),
    "STRIPE_TEST_SECRET": re.compile(r"\b(sk_test_[0-9a-zA-Z]{24,34})\b"),
    "STRIPE_RESTRICTED": re.compile(r"\b(rk_live_[0-9a-zA-Z]{24,34})\b"),
    "JWT_TOKEN": re.compile(r"\b(eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9._-]{10,}\.[A-Za-z0-9._-]{10,})\b"),
    "DB_CONNECTION_URI": re.compile(r"\b((?:postgres|postgresql|mysql|mongodb(?:\+srv)?|redis|rediss)://[a-zA-Z0-9_\-\.]+:[^@\s/:]+@[a-zA-Z0-9_\-\.\:]+/[a-zA-Z0-9_\-\.\?\=\&]+)\b"),
    "GENERIC_API_TOKEN": re.compile(r"(?i)\b(?:api[_-]?key|token|auth[_-]?token|bearer)\s*[:=]\s*['\"]?([a-zA-Z0-9_\-]{32,64})['\"]?"),
    "IPV4_ADDRESS": re.compile(r"\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b"),
    "IPV6_ADDRESS": re.compile(r"\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b"),
    "SSH_PRIVATE_KEY_HEADER": re.compile(r"-----BEGIN (?:RSA|OPENSSH|EC|DSA) PRIVATE KEY-----[\s\S]*?-----END (?:RSA|OPENSSH|EC|DSA) PRIVATE KEY-----")
}

# Deterministic Mock Value Generators
MOCK_GENERATORS = {
    "AWS_ACCESS_KEY": lambda: "AKIA" + "MOCK" + secrets.token_hex(6).upper(),
    "AWS_SECRET_KEY": lambda: "mock_aws_secret_" + secrets.token_urlsafe(24)[:40],
    "STRIPE_LIVE_SECRET": lambda: "sk_live_mock_" + secrets.token_hex(12),
    "STRIPE_TEST_SECRET": lambda: "sk_test_mock_" + secrets.token_hex(12),
    "STRIPE_RESTRICTED": lambda: "rk_live_mock_" + secrets.token_hex(12),
    "JWT_TOKEN": lambda: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJtb2NrLWRldi11c2VyIiwiaWF0IjoxNzAwMDAwMDAwfQ.mock_signature_aircloak",
    "DB_CONNECTION_URI": lambda: "postgresql://aircloak_mock_user:mock_secret_pass@localhost:5432/mock_sandbox_db",
    "GENERIC_API_TOKEN": lambda: "aircloak_mock_token_" + secrets.token_hex(16),
    "IPV4_ADDRESS": lambda: "192.0.2." + str(secrets.randbelow(250) + 1),  # RFC 5737 TEST-NET-1
    "IPV6_ADDRESS": lambda: "2001:db8::" + secrets.token_hex(2),  # RFC 3849 documentation prefix
    "SSH_PRIVATE_KEY_HEADER": lambda: "-----BEGIN OPENSSH PRIVATE KEY-----\n[AIRCLOAK REDACTED MOCK KEY]\n-----END OPENSSH PRIVATE KEY-----"
}

@dataclass
class RedactionMatch:
    secret_type: str
    original_value: str
    mock_value: str
    token_id: str
    start_idx: int
    end_idx: int

class EncryptedVault:
    """AES-256-GCM encrypted in-memory lookup vault."""
    def __init__(self):
        self._master_key = AESGCM.generate_key(bit_length=256)
        self._aesgcm = AESGCM(self._master_key)
        # Map: token_id -> (nonce, ciphertext)
        self._encrypted_store: Dict[str, Tuple[bytes, bytes]] = {}
        self._fingerprint_to_token: Dict[str, str] = {}

    def store(self, token_id: str, original_value: str) -> None:
        nonce = secrets.token_bytes(12)
        ciphertext = self._aesgcm.encrypt(nonce, original_value.encode('utf-8'), None)
        self._encrypted_store[token_id] = (nonce, ciphertext)

    def retrieve(self, token_id: str) -> Optional[str]:
        if token_id not in self._encrypted_store:
            return None
        nonce, ciphertext = self._encrypted_store[token_id]
        decrypted_bytes = self._aesgcm.decrypt(nonce, ciphertext, None)
        return decrypted_bytes.decode('utf-8')

    def purge_all(self) -> int:
        """Emergency zeroize routine."""
        count = len(self._encrypted_store)
        self._encrypted_store.clear()
        self._fingerprint_to_token.clear()
        # Rotate master key
        self._master_key = AESGCM.generate_key(bit_length=256)
        self._aesgcm = AESGCM(self._master_key)
        return count

class AirCloakSanitizer:
    """Core heuristic detection, replacement, and reconstitution engine."""
    def __init__(self, vault: Optional[EncryptedVault] = None):
        self.vault = vault or EncryptedVault()

    def sanitize(self, payload: str) -> Tuple[str, List[Dict]]:
        """
        Scans payload, replaces detected credentials with deterministic mock values,
        and saves originals in the encrypted vault.
        """
        matches_found: List[RedactionMatch] = []

        for secret_type, pattern in SECRET_PATTERNS.items():
            for match in pattern.finditer(payload):
                raw_secret = match.group(1) if match.groups() else match.group(0)
                
                # Check for private or loopback IP exemptions if desired
                if secret_type == "IPV4_ADDRESS" and (raw_secret.startswith("127.") or raw_secret == "0.0.0.0"):
                    continue

                token_id = f"AC_TOK_{secrets.token_hex(6).upper()}"
                mock_gen = MOCK_GENERATORS.get(secret_type, lambda: f"AIRCLOAK_REDACTED_{secret_type}")
                mock_value = mock_gen()

                self.vault.store(token_id, raw_secret)

                start_pos = match.start(1) if match.groups() else match.start()
                end_pos = match.end(1) if match.groups() else match.end()

                matches_found.append(
                    RedactionMatch(
                        secret_type=secret_type,
                        original_value=raw_secret,
                        mock_value=mock_value,
                        token_id=token_id,
                        start_idx=start_pos,
                        end_idx=end_pos
                    )
                )

        # Sort matches by start_idx in reverse order to perform safe string replacement
        sanitized_payload = payload
        matches_found.sort(key=lambda m: m.start_idx, reverse=True)

        audit_log = []
        for m in matches_found:
            sanitized_payload = (
                sanitized_payload[:m.start_idx] +
                m.mock_value +
                sanitized_payload[m.end_idx:]
            )
            audit_log.append({
                "secret_type": m.secret_type,
                "token_id": m.token_id,
                "mock_value": m.mock_value,
                "masked_preview": m.original_value[:4] + "..." + m.original_value[-4:] if len(m.original_value) > 8 else "***"
            })

        return sanitized_payload, audit_log

    def reconstitute(self, sanitized_payload: str, token_map: Dict[str, str]) -> str:
        """
        Replaces mock values back with original secrets using the vault.
        token_map: { mock_value: token_id }
        """
        reconstituted = sanitized_payload
        for mock_val, token_id in token_map.items():
            original = self.vault.retrieve(token_id)
            if original:
                reconstituted = reconstituted.replace(mock_val, original)
        return reconstituted

class ProxyServer:
    """Async socket daemon for handling cross-device and workstation payload interception."""
    def __init__(self, host: str = "127.0.0.1", port: int = 8765):
        self.host = host
        self.port = port
        self.sanitizer = AirCloakSanitizer()
        self.server = None

    async def handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        peer = writer.get_extra_info('peername')
        logger.info(f"Connection established with {peer}")

        try:
            while True:
                data = await reader.readline()
                if not data:
                    break
                line = data.decode('utf-8').strip()
                if not line:
                    continue

                try:
                    request = json.loads(line)
                    action = request.get("action", "sanitize")
                    
                    if action == "sanitize":
                        raw_payload = request.get("payload", "")
                        sanitized, audit = self.sanitizer.sanitize(raw_payload)
                        response = {
                            "status": "success",
                            "sanitized_payload": sanitized,
                            "redactions_count": len(audit),
                            "audit_log": audit
                        }
                    elif action == "reconstitute":
                        payload = request.get("payload", "")
                        token_map = request.get("token_map", {})
                        reconstituted = self.sanitizer.reconstitute(payload, token_map)
                        response = {
                            "status": "success",
                            "reconstituted_payload": reconstituted
                        }
                    elif action == "emergency_purge":
                        purged_count = self.sanitizer.vault.purge_all()
                        response = {
                            "status": "success",
                            "message": f"Vault purged. {purged_count} records destroyed."
                        }
                    else:
                        response = {"status": "error", "message": f"Unknown action: {action}"}

                except json.JSONDecodeError:
                    response = {"status": "error", "message": "Invalid JSON format"}
                except Exception as ex:
                    logger.error(f"Error processing request: {ex}", exc_info=True)
                    response = {"status": "error", "message": str(ex)}

                writer.write((json.dumps(response) + "\n").encode('utf-8'))
                await writer.drain()

        except asyncio.CancelledError:
            pass
        finally:
            logger.info(f"Closing connection with {peer}")
            writer.close()
            await writer.wait_closed()

    async def start(self):
        self.server = await asyncio.start_server(self.handle_client, self.host, self.port)
        logger.info(f"AirCloak Ingest Proxy listening on {self.host}:{self.port}")
        async with self.server:
            await self.server.serve_forever()

if __name__ == "__main__":
    server = ProxyServer()
    try:
        asyncio.run(server.start())
    except KeyboardInterrupt:
        logger.info("Proxy server stopped by user.")

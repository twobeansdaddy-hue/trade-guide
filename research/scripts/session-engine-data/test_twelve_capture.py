"""Offline tests: no provider request is sent."""
import hashlib
import json
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
import twelve_capture  # noqa: E402


class FakeResponse:
    def __init__(self, body):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self):
        return self.body


class CaptureTests(unittest.TestCase):
    def setUp(self):
        self.body = json.dumps({
            "meta": {"symbol": "SOXL", "interval": "1day",
                     "exchange_timezone": "America/New_York"},
            "values": [{"datetime": "2026-09-21", "close": "100.0"}],
        }).encode()
        self.requests = []

    def opener(self, request, timeout):
        self.requests.append((request, timeout))
        return FakeResponse(self.body)

    def capture(self, **overrides):
        args = {"symbol": "SOXL", "interval": "1day", "adjust": "splits",
                "outputsize": 2, "api_key": "test-secret", "opener": self.opener,
                "clock": lambda: datetime(2026, 9, 22, 3, 4, tzinfo=timezone.utc)}
        args.update(overrides)
        return twelve_capture.capture(**args)

    def test_explicit_adjustment_and_receipt_are_preserved_without_key(self):
        result = self.capture()
        request, timeout = self.requests[0]
        params = parse_qs(urlparse(request.full_url).query)
        self.assertEqual(params["adjust"], ["splits"])
        self.assertEqual(params["symbol"], ["SOXL"])
        self.assertEqual(timeout, 20)
        self.assertEqual(request.get_header("Authorization"), "apikey test-secret")
        self.assertEqual(result["receivedAt"], "2026-09-22T03:04:00Z")
        self.assertEqual(result["responseSha256"], hashlib.sha256(self.body).hexdigest())
        self.assertNotIn("test-secret", json.dumps(result))
        self.assertNotIn("eventAt", json.dumps(result))
        self.assertNotIn("availableAt", json.dumps(result))

    def test_invalid_adjustment_fails_before_network(self):
        with self.assertRaises(ValueError):
            self.capture(adjust="default")
        self.assertEqual(self.requests, [])

    def test_provider_error_body_cannot_be_saved_as_capture(self):
        self.body = b'{"status":"error","message":"quota"}'
        with self.assertRaises(ValueError):
            self.capture()

    def test_provider_identity_mismatch_is_rejected(self):
        self.body = json.dumps({"meta": {"symbol": "OTHER", "interval": "1day"},
                                "values": [{}]}).encode()
        with self.assertRaises(ValueError):
            self.capture()

    def test_naive_receipt_clock_is_rejected(self):
        with self.assertRaises(ValueError):
            self.capture(clock=lambda: datetime(2026, 9, 22, 3, 4))

    def test_invalid_symbol_fails_before_network(self):
        with self.assertRaises(ValueError):
            self.capture(symbol="SOXL&apikey=leak")
        self.assertEqual(self.requests, [])


if __name__ == "__main__":
    unittest.main()

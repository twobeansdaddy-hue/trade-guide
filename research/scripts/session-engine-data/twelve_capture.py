#!/usr/bin/env python3
"""Capture a Twelve Data time_series response with explicit request provenance.

The response receipt time is when this process finished reading the response,
not when any historical bar was published. No bar-end/session inference occurs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from urllib.parse import urlencode
from urllib.request import Request, urlopen

URL = "https://api.twelvedata.com/time_series"
INTERVALS = ("1min", "5min", "15min", "30min", "1h", "1day", "1week")
ADJUSTMENTS = ("none", "splits", "all")


def capture(symbol, interval, adjust, outputsize, api_key, *, opener=urlopen,
            clock=lambda: datetime.now(timezone.utc)):
    if not isinstance(symbol, str) or not symbol or not all(
            c.isascii() and (c.isalnum() or c in ".-") for c in symbol):
        raise ValueError("invalid symbol")
    if interval not in INTERVALS or adjust not in ADJUSTMENTS:
        raise ValueError("invalid interval or adjustment")
    if type(outputsize) is not int or not 1 <= outputsize <= 5000:
        raise ValueError("invalid outputsize")
    if not isinstance(api_key, str) or not api_key.strip():
        raise ValueError("API key is required")

    params = {"symbol": symbol.upper(), "interval": interval,
              "outputsize": outputsize, "order": "asc", "adjust": adjust}
    request = Request(URL + "?" + urlencode(params),
                      headers={"Authorization": "apikey " + api_key})
    with opener(request, timeout=20) as response:
        body = response.read()
        receipt = clock()
    if not isinstance(receipt, datetime) or receipt.tzinfo is None \
            or receipt.utcoffset() is None:
        raise ValueError("receipt clock must return an aware datetime")
    parsed = json.loads(body)
    if not isinstance(parsed, dict) or not isinstance(parsed.get("values"), list) \
            or not parsed["values"] or not isinstance(parsed.get("meta"), dict):
        raise ValueError("provider response has no usable time series")
    meta = parsed["meta"]
    meta_symbol = meta.get("symbol")
    if not isinstance(meta_symbol, str) or meta_symbol.upper() != params["symbol"] \
            or meta.get("interval") != interval:
        raise ValueError("provider response does not match request")
    return {
        "schema": "twelve-time-series-capture-v1",
        "provider": "TWELVE_DATA",
        "request": params,
        "receivedAt": receipt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
        "responseSha256": hashlib.sha256(body).hexdigest(),
        "normalizationStatus": "PENDING_CALENDAR_AND_SESSION_VERIFICATION",
        "response": parsed,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Capture Twelve Data response for research")
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--interval", required=True, choices=INTERVALS)
    parser.add_argument("--adjust", required=True, choices=ADJUSTMENTS)
    parser.add_argument("--outputsize", type=int, default=500)
    parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    key = os.environ.get("TWELVE_DATA_API_KEY", "")
    try:
        result = capture(args.symbol, args.interval, args.adjust, args.outputsize, key)
        with open(args.output, "x", encoding="utf-8") as handle:
            json.dump(result, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        # Never include provider payloads, URLs, or credentials in CLI errors.
        print(f"Capture failed ({type(exc).__name__}).", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

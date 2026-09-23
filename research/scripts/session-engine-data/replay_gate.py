"""Offline point-in-time gate for one externally supplied guide decision.

This checks data availability, not whether a price/quantity guide is sensible,
fillable, profitable, or safe to submit to a broker.
"""
from __future__ import annotations

from datetime import datetime, timezone

import preflight


def _time(value):
    parsed, code = preflight._parse_timestamp(value)
    if code:
        raise ValueError("timestamp must include an UTC offset")
    return parsed


def _blocked(code, field):
    return {"status": "BLOCKED", "issues": [{"code": code, "field": field}],
            "notice": "시점 적합성 검사일 뿐 전략·체결·수익성 검증이 아니다."}


def validate(dataset, decision):
    """Return PASS/WARN/BLOCKED without changing the supplied inputs."""
    if not isinstance(dataset, dict) or not isinstance(decision, dict):
        return _blocked("INVALID_INPUT", "$")
    manifest, rows = dataset.get("manifest"), dataset.get("rows")
    if not isinstance(manifest, dict) or not isinstance(rows, list):
        return _blocked("INVALID_DATASET", "dataset")
    try:
        decision_at = _time(decision.get("decisionAt"))
        portfolio_known_at = _time(decision.get("portfolioKnownAt"))
    except ValueError:
        return _blocked("INVALID_TIMESTAMP", "decision")
    if portfolio_known_at > decision_at:
        return _blocked("FUTURE_PORTFOLIO_STATE", "decision.portfolioKnownAt")
    instrument = decision.get("instrumentId")
    session = decision.get("session")
    refs = decision.get("barIndices")
    if not isinstance(instrument, str) or not instrument.strip() \
            or not isinstance(session, str) or not session.strip():
        return _blocked("INVALID_DECISION_IDENTITY", "decision")
    if not isinstance(refs, list) or not refs or any(
            type(index) is not int or index < 0 or index >= len(rows) for index in refs) \
            or len(set(refs)) != len(refs):
        return _blocked("INVALID_BAR_REFERENCES", "decision.barIndices")
    pit = decision.get("requiresPitUniverse", False)
    if type(pit) is not bool:
        return _blocked("INVALID_TYPE", "decision.requiresPitUniverse")
    selected = [rows[index] for index in refs]
    if any(not isinstance(row, dict) or row.get("instrumentId") != instrument \
            or row.get("session") != session for row in selected):
        return _blocked("BAR_IDENTITY_MISMATCH", "decision.barIndices")
    # The caller names the exact feature bars. Unreferenced later bars must not
    # affect an earlier decision's evaluation.
    selected.sort(key=lambda row: preflight._parse_timestamp(row.get("eventAt"))[0]
                  or datetime.min.replace(tzinfo=timezone.utc))
    requirements = {
        "market": manifest.get("market"), "currency": manifest.get("currency"),
        "interval": manifest.get("interval"), "session": session,
        "cutoffAt": decision.get("decisionAt"), "requirePitUniverse": pit,
        "requireVintage": False, "strict": True,
        "requireObservedProvenance": True,
    }
    result = preflight.validate({"manifest": manifest, "requirements": requirements,
                                 "rows": selected})
    return {"status": result["status"], "issues": result["issues"],
            "notice": "시점 적합성 검사일 뿐 전략·체결·수익성 검증이 아니다."}

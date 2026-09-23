"""Validate a declared guide input trace without evaluating investment quality.

Hashes detect accidental changes to supplied content; they do not authenticate a
provider or prove that a model disclosed every input it used.
"""
from __future__ import annotations

import hashlib
import json

import preflight
import replay_gate

KINDS = {"MARKET_BAR", "TECHNICAL", "FUNDAMENTAL", "MACRO", "SENTIMENT"}
VINTAGE_KINDS = {"FUNDAMENTAL", "MACRO", "SENTIMENT"}


def digest(value):
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"),
                         ensure_ascii=True, allow_nan=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _time(value):
    parsed, code = preflight._parse_timestamp(value)
    if code:
        raise ValueError("timestamp")
    return parsed


def _nonempty(value):
    return isinstance(value, str) and bool(value.strip())


def _blocked(code, field):
    return {"status": "BLOCKED", "issues": [{"code": code, "field": field}],
            "notice": "선언된 입력의 시점·무결성만 검사한다. 데이터 진위·입력 완전성·성과는 인증하지 않는다."}


def validate(dataset, trace):
    if not isinstance(dataset, dict) or not isinstance(trace, dict):
        return _blocked("INVALID_INPUT", "$")
    decision, snapshot, features = (trace.get(key) for key in
                                    ("decision", "portfolioSnapshot", "features"))
    if not isinstance(decision, dict) or not isinstance(snapshot, dict) \
            or not isinstance(features, list) or not features:
        return _blocked("TRACE_INCOMPLETE", "trace")
    try:
        cutoff = _time(decision.get("decisionAt"))
        captured = _time(snapshot.get("capturedAt"))
        effective = _time(snapshot.get("effectiveAt"))
        actions_through = _time(snapshot.get("corporateActionsThrough"))
    except ValueError:
        return _blocked("INVALID_TIMESTAMP", "trace")
    if effective > captured or captured > cutoff or actions_through > cutoff \
            or decision.get("portfolioKnownAt") != snapshot.get("capturedAt"):
        return _blocked("PORTFOLIO_TIME_MISMATCH", "portfolioSnapshot")
    state = snapshot.get("state")
    if not isinstance(state, dict) or not isinstance(state.get("holdings"), list) \
            or not isinstance(state.get("cashByCurrency"), dict) \
            or not isinstance(state.get("reservations"), list):
        return _blocked("PORTFOLIO_STATE_INCOMPLETE", "portfolioSnapshot.state")
    if not _nonempty(snapshot.get("snapshotId")) or not isinstance(
            snapshot.get("sourceRefs"), list) or not snapshot["sourceRefs"] \
            or not all(_nonempty(ref) for ref in snapshot["sourceRefs"]):
        return _blocked("PORTFOLIO_SOURCE_MISSING", "portfolioSnapshot.sourceRefs")
    try:
        if snapshot.get("contentSha256") != digest(state):
            return _blocked("PORTFOLIO_HASH_MISMATCH", "portfolioSnapshot.contentSha256")
    except (TypeError, ValueError, OverflowError):
        return _blocked("INVALID_PORTFOLIO_CONTENT", "portfolioSnapshot.state")

    rows = dataset.get("rows")
    if not isinstance(rows, list):
        return _blocked("INVALID_DATASET", "dataset.rows")
    declared = decision.get("modelInputIds")
    if not isinstance(declared, list) or not declared \
            or not all(_nonempty(item) for item in declared) \
            or len(set(declared)) != len(declared):
        return _blocked("MODEL_INPUTS_MISSING", "decision.modelInputIds")
    ids = [item.get("featureId") if isinstance(item, dict) else None for item in features]
    if not all(_nonempty(item) for item in ids) or len(set(ids)) != len(ids) \
            or set(ids) != set(declared):
        return _blocked("MODEL_INPUT_SET_MISMATCH", "features")

    known_at = {}
    bar_refs = set()
    dependencies = {}
    for index, feature in enumerate(features):
        field = f"features[{index}]"
        kind = feature.get("kind")
        if not isinstance(kind, str) or kind not in KINDS \
                or not _nonempty(feature.get("sourceRef")) \
                or not _nonempty(feature.get("transformVersion")):
            return _blocked("FEATURE_PROVENANCE_MISSING", field)
        try:
            available = _time(feature.get("availableAt"))
            source_event = _time(feature.get("sourceEventAt"))
            vintage = _time(feature.get("vintageAt")) if kind in VINTAGE_KINDS else None
        except ValueError:
            return _blocked("FEATURE_TIME_MISSING", field)
        if available > cutoff or source_event > cutoff \
                or (vintage is not None and vintage > cutoff):
            return _blocked("FUTURE_FEATURE", field)
        if source_event > available:
            return _blocked("FEATURE_EVENT_AFTER_AVAILABILITY", field)
        if vintage is not None and vintage > available:
            return _blocked("FEATURE_VINTAGE_AFTER_AVAILABILITY", field)
        known_at[feature["featureId"]] = available
        dependencies[feature["featureId"]] = feature.get("dependsOn", [])
        if kind == "MARKET_BAR":
            bar_index = feature.get("barIndex")
            if type(bar_index) is not int or not 0 <= bar_index < len(rows):
                return _blocked("INVALID_BAR_REFERENCE", field)
            row = rows[bar_index]
            if not isinstance(row, dict) or feature.get("availableAt") != row.get("availableAt") \
                    or feature.get("sourceEventAt") != row.get("eventAt"):
                return _blocked("BAR_AVAILABILITY_MISMATCH", field)
            bar_refs.add(bar_index)
            content = row
        else:
            if "payload" not in feature:
                return _blocked("FEATURE_CONTENT_MISSING", field)
            content = feature["payload"]
        try:
            if feature.get("contentSha256") != digest(content):
                return _blocked("FEATURE_HASH_MISMATCH", field)
        except (TypeError, ValueError, OverflowError):
            return _blocked("INVALID_FEATURE_CONTENT", field)
    decision_bars = decision.get("barIndices")
    if not isinstance(decision_bars, list) or any(type(ref) is not int for ref in decision_bars) \
            or bar_refs != set(decision_bars):
        return _blocked("BAR_INPUT_SET_MISMATCH", "decision.barIndices")
    positions = {feature_id: index for index, feature_id in enumerate(ids)}
    for feature_id, refs in dependencies.items():
        if not isinstance(refs, list) or not all(_nonempty(ref) for ref in refs) \
                or len(set(refs)) != len(refs) \
                or any(ref not in known_at or positions[ref] >= positions[feature_id]
                       for ref in refs):
            return _blocked("FEATURE_DEPENDENCY_INVALID", "features.dependsOn")
        if any(known_at[ref] > known_at[feature_id] for ref in refs):
            return _blocked("FEATURE_DEPENDENCY_FUTURE", "features.dependsOn")

    replay = replay_gate.validate(dataset, decision)
    return {"status": replay["status"], "issues": replay["issues"],
            "notice": "선언된 입력의 시점·무결성만 검사한다. 데이터 진위·입력 완전성·성과는 인증하지 않는다."}

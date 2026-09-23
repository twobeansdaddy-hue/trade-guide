#!/usr/bin/env python3
"""세션 예측엔진 데이터 준비 검사기 v1 (오프라인, 표준 라이브러리 전용).

목적
----
JSON 문서 하나(manifest, requirements, rows)의 구조와 내부 일관성만 검사해
PASS / WARN / BLOCKED 를 돌려준다. 네트워크·파일 쓰기·주문 경로와 무관한 순수 함수다.

중요한 한계 (PASS 의 의미)
--------------------------
PASS 는 "이 파일이 정의한 구조검사를 통과했다"는 뜻일 뿐이다. 시장 사실성, 과거 가격의
진실성, 라이선스/이용권, 수익성을 인증하지 않는다. 거래소 캘린더, 호가단위, 체결 순서,
주문 잔여량은 검사하지 않는다. 라이선스 UNKNOWN 은 WARN 이며 운영 승격 보류는 별도 절차다.

실행: python3 research/scripts/session-engine-data/preflight.py INPUT.json
  - stdout 에 JSON 결과. PASS/WARN 은 종료코드 0, BLOCKED 는 2.
  - 읽기 실패/깨진 JSON/인자 오류도 같은 형태의 구조화된 BLOCKED 결과로 낸다.

공개 API: validate(payload) -> {status, issues, rowCount, ...}
"""
from __future__ import annotations

import json
import math
import re
import sys
from datetime import datetime, timezone
from typing import Any, NamedTuple, Optional
from zoneinfo import ZoneInfo

PASS = "PASS"
WARN = "WARN"
BLOCKED = "BLOCKED"
_RANK = {PASS: 0, WARN: 1, BLOCKED: 2}

NOTICE = (
    "이 결과는 입력 JSON의 구조와 내부 일관성만 검사한다. 시장 사실성, 가격의 진실성, "
    "라이선스/이용권, 수익성을 인증하지 않으며 PASS 도 그런 인증이 아니다. 거래소 캘린더, "
    "호가단위, 체결 순서, 주문 잔여량은 검사하지 않았다."
)

MAX_ISSUES = 500
UNATTRIBUTED = "<unattributed>"

MARKETS = ("US", "KR")
ADJUSTMENT_POLICIES = ("RAW", "SPLIT_ADJUSTED", "TOTAL_RETURN_ADJUSTED", "UNKNOWN")
SOURCE_ADJUSTMENTS = {"RAW": "RAW", "SPLITS": "SPLIT_ADJUSTED",
                      "SPLITS_AND_DIVIDENDS": "TOTAL_RETURN_ADJUSTED"}
AVAILABILITY_MODES = (
    "ACTUAL_RECEIPT",
    "PUBLICATION_RECONSTRUCTED",
    "FIXED_LAG_APPROXIMATION",
    "UNKNOWN",
)
UNIVERSE_MODES = ("POINT_IN_TIME", "FIXED_DIAGNOSTIC_SET", "UNKNOWN")

_CURRENCY_RE = re.compile(r"^[A-Z]{3}$")
_INTRADAY_RE = re.compile(r"^([1-9][0-9]*)(m|h)$")
# 분봉이라고 선언했는데 모든 인접 봉 간격이 이 값 이상이면 일봉으로 본다.
_DAILY_LOOKING_GAP_SECONDS = 20 * 3600
_MISSING = object()


class _Issue(NamedTuple):
    severity: str
    code: str
    field: str
    message: str
    instrument: Optional[str]  # None 이면 데이터셋 수준 이슈


def _add(issues, severity, code, field, message, instrument=None):
    issues.append(_Issue(severity, code, field, message, instrument))


# ---------------------------------------------------------------------------
# 값 파싱 헬퍼. 입력 값 자체는 메시지에 싣지 않는다(비밀값/개인정보 유출 방지).
# ---------------------------------------------------------------------------

def _get(obj: dict, key: str):
    value = obj.get(key, _MISSING)
    return _MISSING if value is None else value


def _parse_timestamp(value):
    """오프셋이 있는 ISO-8601 문자열을 UTC datetime 으로. (값, 오류코드) 반환."""
    if not isinstance(value, str):
        return None, "INVALID_TYPE"
    text = value
    if text[-1:] in ("Z", "z"):
        text = text[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None, "INVALID_TIMESTAMP"
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        return None, "NAIVE_TIMESTAMP"
    try:
        return parsed.astimezone(timezone.utc), None
    except (ValueError, OverflowError):
        return None, "INVALID_TIMESTAMP"


def _parse_number(value):
    """유한한 숫자만 float 로. bool/문자열은 거절. (값, 오류코드) 반환."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None, "INVALID_TYPE"
    try:
        number = float(value)
    except OverflowError:
        return None, "NON_FINITE_NUMBER"
    if not math.isfinite(number):
        return None, "NON_FINITE_NUMBER"
    return number, None


def _parse_interval(value):
    """('intraday', 초) | ('daily', None) | ('weekly', None) | None."""
    if not isinstance(value, str):
        return None
    if value == "1d":
        return ("daily", None)
    if value == "1wk":
        return ("weekly", None)
    match = _INTRADAY_RE.match(value)
    if match:
        unit = 60 if match.group(2) == "m" else 3600
        return ("intraday", int(match.group(1)) * unit)
    return None


_TYPE_MESSAGES = {
    "INVALID_TYPE": "필드 타입이 올바르지 않다.",
    "INVALID_TIMESTAMP": "ISO-8601 시각으로 해석할 수 없다.",
    "NAIVE_TIMESTAMP": "UTC 오프셋이 없는 시각은 허용하지 않는다.",
    "NON_FINITE_NUMBER": "유한한 숫자가 아니다(NaN/무한대/표현 범위 초과).",
}


def _str_field(obj, key, prefix, issues, instrument=None):
    value = _get(obj, key)
    field = f"{prefix}.{key}"
    if value is _MISSING:
        _add(issues, BLOCKED, "MISSING_FIELD", field, "필수 필드가 없다.", instrument)
        return None
    if not isinstance(value, str):
        _add(issues, BLOCKED, "INVALID_TYPE", field, "문자열이어야 한다.", instrument)
        return None
    if not value.strip():
        _add(issues, BLOCKED, "MISSING_FIELD", field, "빈 문자열이다.", instrument)
        return None
    return value


def _enum_field(obj, key, prefix, allowed, issues):
    value = _str_field(obj, key, prefix, issues)
    if value is None:
        return None
    if value not in allowed:
        _add(issues, BLOCKED, "INVALID_ENUM", f"{prefix}.{key}",
             "허용된 값(" + "/".join(allowed) + ")이 아니다.")
        return None
    return value


def _currency_field(obj, prefix, issues):
    value = _str_field(obj, "currency", prefix, issues)
    if value is None:
        return None
    if not _CURRENCY_RE.match(value):
        _add(issues, BLOCKED, "INVALID_VALUE", f"{prefix}.currency",
             "대문자 3글자 통화 코드 형태여야 한다.")
        return None
    return value


def _interval_field(obj, prefix, issues):
    value = _str_field(obj, "interval", prefix, issues)
    if value is None:
        return None
    parsed = _parse_interval(value)
    if parsed is None:
        _add(issues, BLOCKED, "INVALID_ENUM", f"{prefix}.interval",
             "인식할 수 없는 interval 이다(예: 1m, 5m, 60m, 1d, 1wk).")
        return None
    return value, parsed


def _bool_field(obj, key, prefix, issues, default=_MISSING):
    value = _get(obj, key)
    field = f"{prefix}.{key}"
    if value is _MISSING:
        if default is _MISSING:
            _add(issues, BLOCKED, "MISSING_FIELD", field, "필수 필드가 없다.")
            return None
        return default
    if not isinstance(value, bool):
        _add(issues, BLOCKED, "INVALID_TYPE", field, "true/false 여야 한다.")
        return None
    return value


# ---------------------------------------------------------------------------
# manifest / requirements
# ---------------------------------------------------------------------------

def _check_manifest(manifest, issues):
    m: dict = {}
    if manifest is None:
        _add(issues, BLOCKED, "MISSING_FIELD", "manifest", "manifest 가 없다.")
        return m
    if not isinstance(manifest, dict):
        _add(issues, BLOCKED, "INVALID_TYPE", "manifest", "객체여야 한다.")
        return m

    _str_field(manifest, "datasetId", "manifest", issues)
    m["market"] = _enum_field(manifest, "market", "manifest", MARKETS, issues)
    m["currency"] = _currency_field(manifest, "manifest", issues)
    interval = _interval_field(manifest, "manifest", issues)
    m["interval"], m["intervalSpec"] = interval if interval else (None, None)

    timezone_name = _str_field(manifest, "timezone", "manifest", issues)
    if timezone_name is not None:
        try:
            ZoneInfo(timezone_name)
        except Exception:  # 잘못된 이름이거나 tzdata 부재. 시각 비교는 오프셋만 쓰므로 WARN.
            _add(issues, WARN, "TIMEZONE_UNVERIFIED", "manifest.timezone",
                 "IANA 시간대 이름을 확인하지 못했다. 이 검사기는 행의 UTC 오프셋만 사용한다.")

    coverage = _get(manifest, "sessionCoverage")
    if coverage is _MISSING:
        _add(issues, BLOCKED, "SESSION_COVERAGE_MISSING", "manifest.sessionCoverage",
             "세션 목록이 없다. interval 만으로 세션을 추정하지 않는다.")
    elif not isinstance(coverage, list):
        _add(issues, BLOCKED, "INVALID_TYPE", "manifest.sessionCoverage", "배열이어야 한다.")
    elif not coverage:
        _add(issues, BLOCKED, "SESSION_COVERAGE_MISSING", "manifest.sessionCoverage",
             "세션 목록이 비어 있다. interval 만으로 세션을 추정하지 않는다.")
    elif not all(isinstance(s, str) and s.strip() for s in coverage):
        _add(issues, BLOCKED, "INVALID_TYPE", "manifest.sessionCoverage",
             "모든 원소가 비어 있지 않은 문자열이어야 한다.")
    else:
        m["sessions"] = set(coverage)

    m["adjustmentPolicy"] = _enum_field(
        manifest, "adjustmentPolicy", "manifest", ADJUSTMENT_POLICIES, issues)
    m["availabilityMode"] = _enum_field(
        manifest, "availabilityMode", "manifest", AVAILABILITY_MODES, issues)
    m["universeMode"] = _enum_field(
        manifest, "universeMode", "manifest", UNIVERSE_MODES, issues)

    license_status = _str_field(manifest, "licenseStatus", "manifest", issues)
    if license_status is not None and license_status.strip().upper() == "UNKNOWN":
        _add(issues, WARN, "LICENSE_UNKNOWN", "manifest.licenseStatus",
             "라이선스 상태가 UNKNOWN 이다. 구조검사와 무관하게 운영 승격은 보류한다.")

    # 선택 필드: 빈티지 근거. 있으면 타입만 확인한다.
    policy = _get(manifest, "vintagePolicy")
    refs = _get(manifest, "evidenceRefs")
    has_evidence = False
    if policy is not _MISSING:
        if isinstance(policy, str) and policy.strip():
            has_evidence = True
        else:
            _add(issues, BLOCKED, "INVALID_TYPE", "manifest.vintagePolicy",
                 "비어 있지 않은 문자열이어야 한다.")
    if refs is not _MISSING:
        if isinstance(refs, list) and all(isinstance(r, str) and r.strip() for r in refs):
            has_evidence = has_evidence or bool(refs)
        else:
            _add(issues, BLOCKED, "INVALID_TYPE", "manifest.evidenceRefs",
                 "비어 있지 않은 문자열의 배열이어야 한다.")
    m["hasVintageEvidence"] = has_evidence
    m["sourceProvider"] = manifest.get("sourceProvider")
    m["sourceAdjustmentMode"] = manifest.get("sourceAdjustmentMode")
    m["adjustmentEvidenceRef"] = manifest.get("adjustmentEvidenceRef")
    return m


def _check_requirements(requirements, issues):
    r: dict = {}
    if requirements is None:
        _add(issues, BLOCKED, "MISSING_FIELD", "requirements", "requirements 가 없다.")
        return r
    if not isinstance(requirements, dict):
        _add(issues, BLOCKED, "INVALID_TYPE", "requirements", "객체여야 한다.")
        return r

    r["market"] = _enum_field(requirements, "market", "requirements", MARKETS, issues)
    r["currency"] = _currency_field(requirements, "requirements", issues)
    interval = _interval_field(requirements, "requirements", issues)
    r["interval"], r["intervalSpec"] = interval if interval else (None, None)
    r["session"] = _str_field(requirements, "session", "requirements", issues)

    cutoff = _get(requirements, "cutoffAt")
    if cutoff is _MISSING:
        _add(issues, BLOCKED, "MISSING_FIELD", "requirements.cutoffAt", "필수 필드가 없다.")
        r["cutoffAt"] = None
    else:
        r["cutoffAt"], code = _parse_timestamp(cutoff)
        if code:
            _add(issues, BLOCKED, code, "requirements.cutoffAt", _TYPE_MESSAGES[code])

    r["requirePitUniverse"] = _bool_field(
        requirements, "requirePitUniverse", "requirements", issues)
    r["requireVintage"] = _bool_field(requirements, "requireVintage", "requirements", issues)
    # strict 는 계약 목록에 없는 선택 필드다. 생략하면 fail-closed 로 strict=True.
    r["strict"] = _bool_field(requirements, "strict", "requirements", issues, default=True)
    r["requireObservedProvenance"] = _bool_field(
        requirements, "requireObservedProvenance", "requirements", issues, default=False)
    return r


def _check_cross(m, r, issues):
    for key, code in (("market", "MARKET_MISMATCH"), ("currency", "CURRENCY_MISMATCH")):
        if m.get(key) and r.get(key) and m[key] != r[key]:
            _add(issues, BLOCKED, code, f"manifest.{key}",
                 f"manifest 와 requirements 의 {key} 가 다르다.")

    m_int, r_int = m.get("interval"), r.get("interval")
    if m_int and r_int and m_int != r_int:
        _add(issues, BLOCKED, "INTERVAL_MISMATCH", "manifest.interval",
             "요구 interval 과 manifest interval 이 다르다. 일봉/주봉을 분봉으로 추정하지 않는다.")

    sessions, wanted = m.get("sessions"), r.get("session")
    if sessions is not None and wanted and wanted not in sessions:
        _add(issues, BLOCKED, "SESSION_NOT_COVERED", "manifest.sessionCoverage",
             "요구 세션이 manifest sessionCoverage 에 없다.")

    strict = r.get("strict")
    lenient = WARN if strict is False else BLOCKED
    suffix = "" if lenient == BLOCKED else " (strict=false 라 WARN 으로 낮춤)"
    if m.get("adjustmentPolicy") == "UNKNOWN":
        _add(issues, lenient, "ADJUSTMENT_POLICY_UNKNOWN", "manifest.adjustmentPolicy",
             "가격 조정 방식이 UNKNOWN 이다. 실행 가능 가격으로 쓸 수 없다." + suffix)
    if m.get("availabilityMode") == "UNKNOWN":
        _add(issues, lenient, "AVAILABILITY_MODE_UNKNOWN", "manifest.availabilityMode",
             "정보 공개 시점 방식이 UNKNOWN 이다." + suffix)
    elif m.get("availabilityMode") == "FIXED_LAG_APPROXIMATION":
        _add(issues, lenient, "AVAILABILITY_FIXED_LAG_APPROXIMATION",
             "manifest.availabilityMode",
             "고정 지연 근사는 실제 공개 시각이 아니다." + suffix)

    universe = m.get("universeMode")
    if r.get("requirePitUniverse") and universe is not None and universe != "POINT_IN_TIME":
        _add(issues, BLOCKED, "PIT_UNIVERSE_REQUIRED", "manifest.universeMode",
             "시점별(PIT) 종목군이 요구되지만 universeMode 가 POINT_IN_TIME 이 아니다.")
    elif universe == "UNKNOWN":
        _add(issues, WARN, "UNIVERSE_MODE_UNKNOWN", "manifest.universeMode",
             "종목군 방식이 UNKNOWN 이다. 시장 전체 성과 주장에 쓸 수 없다.")

    if r.get("requireVintage") and not m.get("hasVintageEvidence"):
        _add(issues, BLOCKED, "VINTAGE_EVIDENCE_UNDECLARED", "manifest.vintagePolicy",
             "빈티지가 요구되지만 manifest 에 vintagePolicy/evidenceRefs 근거가 없다. "
             "행의 vintageAt 만으로는 출처를 확인할 수 없어 차단한다.")
    if r.get("requireObservedProvenance"):
        if m.get("availabilityMode") != "ACTUAL_RECEIPT":
            _add(issues, BLOCKED, "ACTUAL_RECEIPT_REQUIRED", "manifest.availabilityMode",
                 "수집 근거 검사에는 실제 수신 시각이 필요하다.")
        for key in ("sourceProvider", "adjustmentEvidenceRef"):
            value = m.get(key)
            if not isinstance(value, str) or not value.strip():
                _add(issues, BLOCKED, "PROVENANCE_MISSING", f"manifest.{key}",
                     "수집 근거 필드가 없거나 비어 있다.")
        mode = m.get("sourceAdjustmentMode")
        if not isinstance(mode, str) or mode not in SOURCE_ADJUSTMENTS:
            _add(issues, BLOCKED, "SOURCE_ADJUSTMENT_UNKNOWN",
                 "manifest.sourceAdjustmentMode", "요청한 가격 조정 방식이 확인되지 않았다.")
        elif SOURCE_ADJUSTMENTS[mode] != m.get("adjustmentPolicy"):
            _add(issues, BLOCKED, "SOURCE_ADJUSTMENT_MISMATCH",
                 "manifest.sourceAdjustmentMode", "요청 조정 방식과 데이터셋 선언이 다르다.")


# ---------------------------------------------------------------------------
# rows
# ---------------------------------------------------------------------------

def _check_rows(rows, m, r, issues):
    """행을 검사하고 (rowCount, 관측된 종목 집합)을 돌려준다."""
    if rows is None:
        _add(issues, BLOCKED, "MISSING_FIELD", "rows", "rows 가 없다.")
        return 0, set()
    if not isinstance(rows, list):
        _add(issues, BLOCKED, "INVALID_TYPE", "rows", "배열이어야 한다.")
        return 0, set()
    if not rows:
        _add(issues, BLOCKED, "EMPTY_ROWS", "rows", "행이 없다. 빈 데이터는 통과시키지 않는다.")
        return 0, set()

    cutoff = r.get("cutoffAt")
    spec = m.get("intervalSpec")
    intraday_seconds = spec[1] if spec and spec[0] == "intraday" else None

    instruments: set = set()
    seen: set = set()
    last_at: dict = {}
    gaps: dict = {}  # (종목, 세션) -> [(행 index, 간격 초)]
    required_session_rows = 0
    required_session_instruments: set = set()  # 요구 세션 행이 있는 종목
    first_index: dict = {}  # 종목 -> 처음 등장한 행 index

    for i, row in enumerate(rows):
        prefix = f"rows[{i}]"
        if not isinstance(row, dict):
            _add(issues, BLOCKED, "ROW_NOT_OBJECT", prefix, "행은 객체여야 한다.", UNATTRIBUTED)
            instruments.add(UNATTRIBUTED)
            continue

        inst_id = _str_field(row, "instrumentId", prefix, issues, UNATTRIBUTED)
        inst = inst_id if inst_id is not None else UNATTRIBUTED
        instruments.add(inst)
        first_index.setdefault(inst, i)

        def add(severity, code, field, message):
            _add(issues, severity, code, f"{prefix}.{field}", message, inst)

        # 선언(manifest)과 실제 행의 시장/통화/세션 대조
        for key, code in (("market", "MARKET_MISMATCH"), ("currency", "CURRENCY_MISMATCH")):
            value = _get(row, key)
            if value is _MISSING:
                add(BLOCKED, "MISSING_FIELD", key, "필수 필드가 없다.")
            elif not isinstance(value, str):
                add(BLOCKED, "INVALID_TYPE", key, "문자열이어야 한다.")
            elif m.get(key) and value != m[key]:
                add(BLOCKED, code, key, f"행의 {key} 가 manifest 선언과 다르다.")

        session = _get(row, "session")
        if session is _MISSING:
            add(BLOCKED, "MISSING_FIELD", "session", "필수 필드가 없다.")
            session = None
        elif not isinstance(session, str) or not session.strip():
            add(BLOCKED, "INVALID_TYPE", "session", "비어 있지 않은 문자열이어야 한다.")
            session = None
        else:
            if m.get("sessions") is not None and session not in m["sessions"]:
                add(BLOCKED, "SESSION_NOT_DECLARED", "session",
                    "행의 세션이 manifest sessionCoverage 에 없다.")
            if session == r.get("session"):
                required_session_rows += 1
                required_session_instruments.add(inst)

        # 시각
        times = {}
        for key in ("eventAt", "availableAt", "vintageAt"):
            value = _get(row, key)
            if value is _MISSING:
                if key == "vintageAt":
                    if r.get("requireVintage"):
                        add(BLOCKED, "VINTAGE_MISSING", key,
                            "빈티지가 요구되지만 vintageAt 이 없다.")
                else:
                    add(BLOCKED, "MISSING_FIELD", key, "필수 필드가 없다.")
                continue
            parsed, code = _parse_timestamp(value)
            if code:
                add(BLOCKED, code, key, _TYPE_MESSAGES[code])
            else:
                times[key] = parsed
        if cutoff is not None:
            for key, code in (("eventAt", "FUTURE_EVENT_AT"),
                              ("availableAt", "FUTURE_AVAILABLE_AT"),
                              ("vintageAt", "FUTURE_VINTAGE_AT")):
                if key in times and times[key] > cutoff:
                    add(BLOCKED, code, key,
                        f"{key} 가 requirements.cutoffAt 보다 미래다(정보 시점 누출).")
        if "eventAt" in times and "availableAt" in times \
                and times["availableAt"] < times["eventAt"]:
            add(BLOCKED, "AVAILABLE_BEFORE_EVENT", "availableAt",
                "availableAt 이 봉 종료 시각(eventAt)보다 이르다.")
        if r.get("requireObservedProvenance"):
            for key in ("barStartAt", "sourceTimestampAt", "receivedAt"):
                value = _get(row, key)
                if value is _MISSING:
                    add(BLOCKED, "PROVENANCE_MISSING", key, "수집 근거 시각이 없다.")
                    continue
                parsed, code = _parse_timestamp(value)
                if code:
                    add(BLOCKED, code, key, _TYPE_MESSAGES[code])
                else:
                    times[key] = parsed
            meaning = _get(row, "sourceTimestampMeaning")
            if meaning not in ("BAR_OPEN", "BAR_CLOSE"):
                add(BLOCKED, "SOURCE_TIMESTAMP_MEANING_UNKNOWN", "sourceTimestampMeaning",
                    "공급자 시각이 봉 시작인지 종료인지 확인되지 않았다.")
            elif "sourceTimestampAt" in times:
                target = "barStartAt" if meaning == "BAR_OPEN" else "eventAt"
                if target in times and times["sourceTimestampAt"] != times[target]:
                    add(BLOCKED, "SOURCE_TIMESTAMP_MISMATCH", "sourceTimestampAt",
                        "공급자 시각과 선언한 봉 경계가 다르다.")
            if "barStartAt" in times and "eventAt" in times:
                duration = (times["eventAt"] - times["barStartAt"]).total_seconds()
                if duration <= 0 or (intraday_seconds is not None and duration != intraday_seconds):
                    add(BLOCKED, "BAR_DURATION_MISMATCH", "barStartAt",
                        "봉 시작·종료 간격이 선언한 interval 과 다르다.")
            if "receivedAt" in times:
                if "availableAt" in times and times["receivedAt"] != times["availableAt"]:
                    add(BLOCKED, "RECEIPT_AVAILABILITY_MISMATCH", "availableAt",
                        "실제 수신 시각과 availableAt 이 다르다.")
                if cutoff is not None and times["receivedAt"] > cutoff:
                    add(BLOCKED, "FUTURE_RECEIPT_AT", "receivedAt",
                        "실제 수신 시각이 의사결정 시각보다 미래다.")

        # 숫자
        values = {}
        for key in ("open", "high", "low", "close", "volume"):
            raw = _get(row, key)
            if raw is _MISSING:
                add(BLOCKED, "MISSING_FIELD", key, "필수 필드가 없다.")
                continue
            number, code = _parse_number(raw)
            if code:
                add(BLOCKED, code, key, _TYPE_MESSAGES[code])
            elif key == "volume" and number < 0:
                add(BLOCKED, "NEGATIVE_VOLUME", key, "거래량은 0 이상이어야 한다.")
            elif key != "volume" and number <= 0:
                add(BLOCKED, "NON_POSITIVE_PRICE", key, "가격은 0 보다 커야 한다.")
            else:
                values[key] = number
        if all(k in values for k in ("open", "high", "low", "close")):
            o, h, lo, c = (values[k] for k in ("open", "high", "low", "close"))
            if h < max(o, lo, c) or lo > min(o, h, c):
                add(BLOCKED, "INVALID_OHLC", "high",
                    "OHLC 관계가 맞지 않는다(high 는 최대, low 는 최소여야 한다).")

        # 종목·세션별 중복/순서. 다종목이 섞여 있어도 시리즈별로 따로 본다.
        event = times.get("eventAt")
        if inst_id is None or session is None or event is None:
            continue
        key = (inst_id, session)
        if (key, event) in seen:
            add(BLOCKED, "DUPLICATE_BAR", "eventAt", "같은 종목·세션·eventAt 봉이 중복된다.")
            continue
        seen.add((key, event))
        previous = last_at.get(key)
        if previous is None:
            last_at[key] = event
        elif event < previous:
            add(BLOCKED, "OUT_OF_ORDER", "eventAt",
                "같은 종목·세션에서 eventAt 이 앞선 행보다 이르다(UTC 기준 비정렬).")
        else:
            gap = (event - previous).total_seconds()
            gaps.setdefault(key, []).append((i, gap))
            last_at[key] = event
            if intraday_seconds is not None and gap < intraday_seconds:
                add(BLOCKED, "EVENT_SPACING_BELOW_INTERVAL", "eventAt",
                    "인접 봉 간격이 선언된 interval 보다 짧다.")

    if intraday_seconds is not None:
        for (inst_id, _session), series in gaps.items():
            if len(series) >= 2 and all(g >= _DAILY_LOOKING_GAP_SECONDS for _, g in series):
                _add(issues, BLOCKED, "INTERVAL_LOOKS_DAILY", f"rows[{series[0][0]}].eventAt",
                     "분봉으로 선언됐지만 모든 봉 간격이 하루 이상이다. 일봉을 분봉으로 "
                     "취급하지 않는다.", inst_id)

    wanted = r.get("session")
    if wanted and required_session_rows == 0:
        _add(issues, BLOCKED, "REQUIRED_SESSION_NO_ROWS", "rows",
             "요구 세션에 해당하는 행이 하나도 없다.")
    if wanted:
        # 데이터셋 전체에 요구 세션 행이 있어도 종목별로 따로 확인한다.
        for inst in sorted(instruments - required_session_instruments - {UNATTRIBUTED}):
            _add(issues, BLOCKED, "INSTRUMENT_REQUIRED_SESSION_NO_ROWS",
                 f"rows[{first_index[inst]}].session",
                 "이 종목에는 요구 세션에 해당하는 행이 없다.", inst)
    return len(rows), instruments


# ---------------------------------------------------------------------------
# 결과 조립
# ---------------------------------------------------------------------------

def _worst(severities):
    worst = PASS
    for severity in severities:
        if _RANK[severity] > _RANK[worst]:
            worst = severity
    return worst


def _result(issues, row_count, instruments):
    # 데이터셋 수준 이슈는 모든 종목에, 행 수준 이슈는 해당 종목에만 반영한다.
    dataset_level = _worst(i.severity for i in issues if i.instrument is None)
    per_instrument: dict = {}
    for issue in issues:
        if issue.instrument is not None:
            per_instrument[issue.instrument] = _worst(
                [per_instrument.get(issue.instrument, PASS), issue.severity])
    instrument_status = {
        inst: _worst([dataset_level, per_instrument.get(inst, PASS)])
        for inst in sorted(instruments)
    }
    return {
        "status": _worst(i.severity for i in issues),
        "issues": [
            {"code": i.code, "field": i.field, "message": i.message, "severity": i.severity}
            for i in issues[:MAX_ISSUES]
        ],
        "issuesTruncated": len(issues) > MAX_ISSUES,
        "rowCount": row_count,
        "instrumentStatus": instrument_status,
        "notice": NOTICE,
    }


def _validate(payload):
    issues: list = []
    if not isinstance(payload, dict):
        _add(issues, BLOCKED, "INVALID_TYPE", "$", "최상위 값은 객체여야 한다.")
        return _result(issues, 0, set())
    m = _check_manifest(payload.get("manifest"), issues)
    r = _check_requirements(payload.get("requirements"), issues)
    _check_cross(m, r, issues)
    row_count, instruments = _check_rows(payload.get("rows"), m, r, issues)
    return _result(issues, row_count, instruments)


def validate(payload: Any) -> dict:
    """payload 를 검사한다. 입력은 변형하지 않으며 예외 대신 BLOCKED 로 fail closed."""
    try:
        return _validate(payload)
    except Exception as exc:  # 예기치 못한 입력도 traceback 없이 차단한다.
        issue = _Issue(BLOCKED, "INTERNAL_ERROR", "$",
                       f"검사 중 내부 오류({type(exc).__name__}). 입력을 신뢰할 수 없어 차단한다.",
                       None)
        return _result([issue], 0, set())


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _error_result(code, message):
    issue = _Issue(BLOCKED, code, "$", message, None)
    return _result([issue], 0, set())


def _load(path):
    """(payload, 오류 결과). 오류 메시지에는 경로·파일 내용·예외 상세를 싣지 않는다."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            text = handle.read()
    except (OSError, UnicodeDecodeError) as exc:
        reason = exc.strerror if isinstance(exc, OSError) and exc.strerror \
            else type(exc).__name__
        return None, _error_result("INPUT_UNREADABLE", f"입력 파일을 읽지 못했다({reason}).")
    try:
        return json.loads(text), None
    except json.JSONDecodeError as exc:
        return None, _error_result(
            "MALFORMED_JSON", f"JSON 문법 오류(line {exc.lineno}, column {exc.colno}).")
    except (ValueError, RecursionError):
        return None, _error_result("MALFORMED_JSON", "JSON 을 해석하지 못했다.")


def main(argv=None) -> int:
    args = sys.argv[1:] if argv is None else list(argv)
    if len(args) != 1:
        result = _error_result(
            "USAGE_ERROR", "사용법: preflight.py INPUT.json (인자는 파일 경로 하나).")
    else:
        payload, result = _load(args[0])
        if result is None:
            result = validate(payload)
    sys.stdout.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    return 2 if result["status"] == BLOCKED else 0


if __name__ == "__main__":
    sys.exit(main())

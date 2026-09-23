#!/usr/bin/env python3
"""조건부 가격·수량 계산 코어 v1 (오프라인 연구용, 표준 라이브러리 Decimal 전용).

성격
----
합성 입력으로 단일 종목·단일 통화의 BUY / SELL / PROTECTIVE 시나리오 수량과 현금·노출·모델
위험 예약을 결정적으로 계산하는 순수 함수 두 개를 제공한다. 가격 예측기, 채택된 전략, 추천,
실행 가능한 브로커 주문이 아니다. 가격·수량·한도·수수료는 전부 호출자가 명시적으로 넣고,
이 모듈은 투자 기본값을 만들지 않는다. 기존 TradePlanPreviewService 를 대체하지 않는다.

공개 API
--------
build_plan(input) -> {"ok": True, ...plan} | {"ok": False, "error": {code, field, message}}
    input = {"state": {...}, "params": {...}}
    state  : instrumentId, currency(USD|KRW), version(int>=0), quantity>=0, averageCost>=0
             (보유 시 >0), availableCash>=0 (cashScale 자릿수 이내), externalReservationsConfirmed=true
             availableCash 는 외부 예약을 이미 뺀 값이어야 하며 호출자가 그것을 true 로 확인해야 한다.
    params : quantityStep>0, priceTick>0, feeRate[0,1), cashScale 0..8, portfolioValue>0,
             maxExposureRatio(0,1], maxLossRatio(0,1], markPrice>0, stopPrice>0,
             buyLevels/sellLevels = 우선순위 순서 리스트. 각 원소는 levelId(고유), price>0(틱 준수),
             requestedQuantity>0, 선택적으로 instrumentId/currency(있으면 state 와 같아야 함).
    십진수는 소수점 이하 포함 최대 32자의 부호·지수 없는 문자열만 허용한다(bool·float·NaN·Infinity·
    지수표기·공백 거부). version/cashScale 은 bool 이 아닌 int. 알 수 없는 필드는 거부한다.
apply_fill(state, plan, fill) -> {"ok": True, "state": 새 상태, ...} | {"ok": False, "error": ...}
    fill = {scenario, levelId, quantity>0, price>0, fee>=0, [instrumentId], [currency]}
    plan 은 build_plan 이 돌려준 dict 그대로여야 한다. 계획은 입력 전체(state 포함)를 담고 있어,
    apply_fill 이 그 입력으로 계획을 다시 만들어 한 글자라도 다르면 PLAN_TAMPERED 로 거부한다.

원칙
----
- 잘못되거나 지원하지 않는 입력은 예외 대신 안정된 오류 코드로 fail-closed 하고 입력을 바꾸지 않는다.
- 세 시나리오(BUY/SELL/PROTECTIVE)는 서로 배타적인 대안이다. 동시에 안전하게 낼 수 있는 주문이라는
  뜻도, OCO 라는 가정도 없다. brokerSubmissionReady 는 항상 false, requiresReplanAfterFill 은 항상 true.
- 체결이 하나라도 반영되면 상태 version 이 오르고 모든 기존 시나리오가 무효다. 이 모듈은 영속 멱등성이나
  동시 기장 시스템이 아니다. 호출자가 새 version 을 원자적으로 저장한 뒤에 통합해야 한다.
- 손절가는 모델 예산일 뿐이며 갭·슬리피지로 손실이 그보다 커질 수 있다. OHLC 로 체결을 흉내 내지 않는다.
- 평균단가는 기존 HoldingCalculator 와 같은 방식으로 매수 수수료를 취득원가에 포함한다.
  나눗셈이 딱 떨어지지 않으면 소수 10자리에서 HALF_UP 반올림한다.

CLI: plan_core.py build INPUT.json | plan_core.py fill INPUT.json ({"input": build 입력, "fill": {...}}).
     성공 0, 실패(구조화된 오류 JSON) 2.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from decimal import (Context, Decimal, DivisionByZero, Inexact, InvalidOperation, Overflow,
                     ROUND_CEILING, ROUND_FLOOR, ROUND_HALF_UP, localcontext)

SCHEMA = "session-plan-core-v1"
SCENARIOS = ("BUY", "SELL", "PROTECTIVE")
CURRENCIES = ("USD", "KRW")
PROTECTIVE_LEVEL_ID = "PROTECTIVE_STOP"
AVG_COST_SCALE = 10
MAX_LEVELS = 200
MAX_DECIMAL_CHARS = 32
MAX_VERSION = 2 ** 63 - 1

PLAN_NOTICE = (
    "합성 입력으로 계산한 오프라인 연구용 산식 결과다. 가격 예측·추천·채택된 전략·실행 가능한 주문이 "
    "아니다. BUY/SELL/PROTECTIVE 는 동시에 낼 수 있는 주문이 아니라 서로 배타적인 대안이며 OCO 를 "
    "가정하지 않는다. 체결이 하나라도 있으면 모든 시나리오가 무효이므로 새 상태로 재계획해야 한다. "
    "손절가는 모델 예산일 뿐 갭·슬리피지로 손실이 커질 수 있다."
)
FILL_NOTICE = (
    "입력한 체결을 상태에 산술 반영했을 뿐 주문·체결을 검증하거나 전송하지 않았다. 이전 계획은 "
    "전부 무효이며 새 상태로 재계획해야 한다. 영속 멱등성·동시성 보호는 없다."
)

_TRAPS = [InvalidOperation, DivisionByZero, Overflow]
_EXACT = Context(prec=200, Emin=-2000, Emax=2000, traps=_TRAPS + [Inexact])
_ROUND = Context(prec=200, Emin=-2000, Emax=2000, traps=_TRAPS)
_FLOOR_DIV = Context(prec=200, rounding=ROUND_FLOOR, Emin=-2000, Emax=2000, traps=_TRAPS)
_CEIL_DIV = Context(prec=200, rounding=ROUND_CEILING, Emin=-2000, Emax=2000, traps=_TRAPS)

_DEC_RE = re.compile(r"[0-9]+(\.[0-9]+)?")
_IDENT_RE = re.compile(r"[A-Za-z0-9_.:\-]{1,64}")
_MISSING = object()

_STATE_KEYS = {"instrumentId", "currency", "version", "quantity", "averageCost", "availableCash",
               "externalReservationsConfirmed"}
_PARAM_KEYS = {"quantityStep", "priceTick", "feeRate", "cashScale", "portfolioValue",
               "maxExposureRatio", "maxLossRatio", "markPrice", "stopPrice", "buyLevels", "sellLevels"}
_LEVEL_KEYS = {"levelId", "price", "requestedQuantity", "instrumentId", "currency"}
_FILL_KEYS = {"scenario", "levelId", "quantity", "price", "fee", "instrumentId", "currency"}


class _PlanError(Exception):
    def __init__(self, code, field, message):
        super().__init__(code)
        self.code = code
        self.field = field
        self.message = message


def _fail(code, field, message):
    raise _PlanError(code, field, message)


def _err(exc):
    return {"ok": False, "error": {"code": exc.code, "field": exc.field, "message": exc.message}}


# ---------------------------------------------------------------------------
# 입력 파싱. 오류 메시지에는 입력 값을 싣지 않는다.
# ---------------------------------------------------------------------------

def _keys(obj, allowed, path):
    if not isinstance(obj, dict):
        _fail("INVALID_TYPE", path, "객체여야 한다.")
    for key in obj:
        if key not in allowed:
            _fail("UNKNOWN_FIELD", path, "지원하지 않는 필드가 있다.")


def _need(obj, key, path):
    value = obj.get(key, _MISSING)
    if value is _MISSING or value is None:
        _fail("MISSING_FIELD", f"{path}.{key}", "필수 값이 없다. 기본값을 쓰지 않고 차단한다.")
    return value


def _parse_dec(value, field, positive=False):
    if type(value) is not str:
        _fail("INVALID_TYPE", field, "십진수 문자열이어야 한다(bool·float·정수 불가).")
    if len(value) > MAX_DECIMAL_CHARS or _DEC_RE.fullmatch(value) is None:
        _fail("INVALID_DECIMAL", field, "부호·지수·공백 없는 유한 십진수 문자열이어야 한다.")
    number = Decimal(value)
    if positive and number <= 0:
        _fail("OUT_OF_RANGE", field, "0보다 커야 한다.")
    return number


def _dec(obj, key, path, positive=False):
    return _parse_dec(_need(obj, key, path), f"{path}.{key}", positive)


def _int(obj, key, path, low, high):
    value = _need(obj, key, path)
    field = f"{path}.{key}"
    if type(value) is not int:
        _fail("INVALID_TYPE", field, "bool 이 아닌 정수여야 한다.")
    if not low <= value <= high:
        _fail("OUT_OF_RANGE", field, "허용 범위를 벗어났다.")
    return value


def _ident(value, field):
    if type(value) is not str or _IDENT_RE.fullmatch(value) is None:
        _fail("INVALID_ID", field, "영문·숫자·_.:- 로 된 1~64자 식별자여야 한다.")
    return value


def _currency(value, field):
    if type(value) is not str or value not in CURRENCIES:
        _fail("INVALID_CURRENCY", field, "USD 또는 KRW 만 지원한다(환전 없음).")
    return value


def _match_identity(obj, instrument, currency, path):
    if "instrumentId" in obj and obj["instrumentId"] != instrument:
        _fail("INSTRUMENT_MISMATCH", f"{path}.instrumentId", "상태의 종목과 다르다.")
    if "currency" in obj and obj["currency"] != currency:
        _fail("CURRENCY_MISMATCH", f"{path}.currency", "상태의 통화와 다르다. 환전은 지원하지 않는다.")


def _q(number, scale, rounding):
    return number.quantize(Decimal(1).scaleb(-scale), rounding=rounding, context=_ROUND)


def _floor_step(number, step):
    return (number // step) * step


def _fmt(number):
    if number == 0:
        return "0"
    return format(number.normalize(), "f")


def _cash(number, scale):
    return format(_q(number, scale, ROUND_FLOOR), "f")


def _cj(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def _parse_scalars(params):
    _keys(params, _PARAM_KEYS, "params")
    p = {
        "quantityStep": _dec(params, "quantityStep", "params", positive=True),
        "priceTick": _dec(params, "priceTick", "params", positive=True),
        "feeRate": _dec(params, "feeRate", "params"),
        "cashScale": _int(params, "cashScale", "params", 0, 8),
        "portfolioValue": _dec(params, "portfolioValue", "params", positive=True),
        "maxExposureRatio": _dec(params, "maxExposureRatio", "params", positive=True),
        "maxLossRatio": _dec(params, "maxLossRatio", "params", positive=True),
        "markPrice": _dec(params, "markPrice", "params", positive=True),
        "stopPrice": _dec(params, "stopPrice", "params", positive=True),
    }
    if p["feeRate"] >= 1:
        _fail("OUT_OF_RANGE", "params.feeRate", "0 이상 1 미만이어야 한다.")
    for key in ("maxExposureRatio", "maxLossRatio"):
        if p[key] > 1:
            _fail("OUT_OF_RANGE", f"params.{key}", "(0, 1] 범위여야 한다.")
    return p


def _parse_state(state, scale, path="state"):
    _keys(state, _STATE_KEYS, path)
    st = {
        "instrumentId": _ident(_need(state, "instrumentId", path), f"{path}.instrumentId"),
        "currency": _currency(_need(state, "currency", path), f"{path}.currency"),
        "version": _int(state, "version", path, 0, MAX_VERSION),
        "quantity": _dec(state, "quantity", path),
        "averageCost": _dec(state, "averageCost", path),
        "availableCash": _dec(state, "availableCash", path),
    }
    if st["quantity"] > 0 and st["averageCost"] <= 0:
        _fail("OUT_OF_RANGE", f"{path}.averageCost", "보유 중이면 평균단가는 0보다 커야 한다.")
    if st["availableCash"] != _q(st["availableCash"], scale, ROUND_FLOOR):
        _fail("CASH_SCALE_MISMATCH", f"{path}.availableCash", "cashScale 자릿수를 넘는 현금이다.")
    if state.get("externalReservationsConfirmed") is not True:
        _fail("EXTERNAL_RESERVATIONS_UNCONFIRMED", f"{path}.externalReservationsConfirmed",
              "availableCash 가 외부 예약을 이미 뺀 값임을 true 로 확인해야 한다.")
    return st


def _parse_levels(params, key, st, tick):
    path = f"params.{key}"
    raw = _need(params, key, "params")
    if not isinstance(raw, list):
        _fail("INVALID_TYPE", path, "리스트여야 한다.")
    if len(raw) > MAX_LEVELS:
        _fail("OUT_OF_RANGE", path, "레벨이 너무 많다.")
    seen, levels = set(), []
    for index, item in enumerate(raw):
        lpath = f"{path}[{index}]"
        _keys(item, _LEVEL_KEYS, lpath)
        level_id = _ident(_need(item, "levelId", lpath), f"{lpath}.levelId")
        if level_id in seen:
            _fail("DUPLICATE_LEVEL_ID", f"{lpath}.levelId", "레벨 ID 가 중복됐다.")
        seen.add(level_id)
        price = _dec(item, "price", lpath, positive=True)
        if price % tick != 0:
            _fail("TICK_VIOLATION", f"{lpath}.price", "호가단위의 배수가 아니다.")
        requested = _dec(item, "requestedQuantity", lpath, positive=True)
        _match_identity(item, st["instrumentId"], st["currency"], lpath)
        levels.append({"levelId": level_id, "price": price, "requestedQuantity": requested})
    return levels


def _state_out(st, scale):
    return {
        "instrumentId": st["instrumentId"], "currency": st["currency"], "version": st["version"],
        "quantity": _fmt(st["quantity"]), "averageCost": _fmt(st["averageCost"]),
        "availableCash": _cash(st["availableCash"], scale), "externalReservationsConfirmed": True,
    }


def _level_in(levels):
    return [{"levelId": lv["levelId"], "price": _fmt(lv["price"]),
             "requestedQuantity": _fmt(lv["requestedQuantity"])} for lv in levels]


def _level_out(level_id, price, requested, planned, reasons, **extra):
    out = {
        "levelId": level_id, "price": _fmt(price), "requestedQuantity": _fmt(requested),
        "plannedQuantity": _fmt(planned), "unplannedQuantity": _fmt(requested - planned),
        "status": "PLANNED" if planned > 0 else "BLOCKED", "executable": planned > 0,
        "reasonCodes": list(reasons),
    }
    out.update(extra)
    return out


# ---------------------------------------------------------------------------
# 시나리오 계산
# ---------------------------------------------------------------------------

def _plan_buy(st, p, levels):
    scale, step, fee = p["cashScale"], p["quantityStep"], p["feeRate"]
    stop, mark, held = p["stopPrice"], p["markPrice"], st["quantity"]
    exposure_limit = p["portfolioValue"] * p["maxExposureRatio"]
    risk_limit = p["portfolioValue"] * p["maxLossRatio"]
    existing_exposure = held * mark
    existing_risk = held * (mark - stop + stop * fee)
    exposure_left = max(Decimal(0), exposure_limit - existing_exposure)
    risk_left = max(Decimal(0), risk_limit - existing_risk)
    cash_left = st["availableCash"]
    exposure_start, risk_start, cash_start = exposure_left, risk_left, cash_left
    out, total_qty = [], Decimal(0)
    for lv in levels:
        price = lv["price"]
        requested = _floor_step(lv["requestedQuantity"], step)
        per_cash = price * (1 + fee)
        per_risk = price - stop + (price + stop) * fee
        caps = [
            ("CASH_LIMIT", _floor_step(_FLOOR_DIV.divide(cash_left, per_cash), step)),
            ("EXPOSURE_LIMIT", _floor_step(_FLOOR_DIV.divide(exposure_left, price), step)),
            ("RISK_LIMIT", _floor_step(_FLOOR_DIV.divide(risk_left, per_risk), step)),
        ]
        qty = min([requested] + [cap for _, cap in caps])
        if requested == 0:
            reasons = ["BELOW_STEP"]
        else:
            reasons = [name for name, cap in caps if cap < requested and cap == qty]
        cash_used = _q(qty * per_cash, scale, ROUND_CEILING)
        exposure_used = qty * price
        risk_used = qty * per_risk
        if cash_used > cash_left or exposure_used > exposure_left or risk_used > risk_left:
            _fail("INVARIANT_VIOLATION", "$", "반올림 후 한도 초과가 감지되어 계획을 만들지 않는다.")
        cash_left -= cash_used
        exposure_left -= exposure_used
        risk_left -= risk_used
        total_qty += qty
        out.append(_level_out(
            lv["levelId"], price, lv["requestedQuantity"], qty, reasons,
            reservedCash=_cash(cash_used, scale), reservedExposure=_fmt(exposure_used),
            reservedRisk=_fmt(risk_used)))
    return {
        "scenario": "BUY",
        "levels": out,
        "summary": {
            "totalPlannedQuantity": _fmt(total_qty),
            "totalReservedCash": _cash(cash_start - cash_left, scale),
            "remainingCash": _cash(cash_left, scale),
            "existingExposure": _fmt(existing_exposure),
            "exposureLimit": _fmt(exposure_limit),
            "existingExposureBreach": existing_exposure > exposure_limit,
            "totalReservedExposure": _fmt(exposure_start - exposure_left),
            "remainingExposureHeadroom": _fmt(exposure_left),
            "existingModeledRisk": _fmt(existing_risk),
            "riskLimit": _fmt(risk_limit),
            "existingRiskBreach": existing_risk > risk_limit,
            "totalReservedRisk": _fmt(risk_start - risk_left),
            "remainingRiskBudget": _fmt(risk_left),
            "stopIsModeledBudgetOnly": True,
            "sellProceedsCountedAsBuyingPower": False,
        },
    }


def _plan_sell(st, p, levels):
    scale, step, fee = p["cashScale"], p["quantityStep"], p["feeRate"]
    held = st["quantity"]
    sellable = _floor_step(held, step)
    left = sellable
    out = []
    for lv in levels:
        requested = _floor_step(lv["requestedQuantity"], step)
        qty = min(requested, left)
        reasons = ["BELOW_STEP"] if requested == 0 else (["HOLDINGS_LIMIT"] if qty < requested else [])
        left -= qty
        proceeds = _q(lv["price"] * qty * (1 - fee), scale, ROUND_FLOOR)
        out.append(_level_out(lv["levelId"], lv["price"], lv["requestedQuantity"], qty, reasons,
                              estimatedNetProceeds=_cash(proceeds, scale)))
    return {
        "scenario": "SELL",
        "levels": out,
        "summary": {
            "heldQuantity": _fmt(held),
            "sellableQuantity": _fmt(sellable),
            "stepResidualQuantity": _fmt(held - sellable),
            "totalPlannedQuantity": _fmt(sellable - left),
            "unallocatedSellableQuantity": _fmt(left),
            "borrowsFutureBuyQuantity": False,
            "proceedsCountedAsBuyingPower": False,
        },
    }


def _plan_protective(st, p):
    scale, step, fee = p["cashScale"], p["quantityStep"], p["feeRate"]
    held = st["quantity"]
    sellable = _floor_step(held, step)
    proceeds = _q(p["stopPrice"] * sellable * (1 - fee), scale, ROUND_FLOOR)
    level = _level_out(PROTECTIVE_LEVEL_ID, p["stopPrice"], sellable, sellable,
                       [] if sellable > 0 else ["NO_SELLABLE_QUANTITY"],
                       estimatedNetProceeds=_cash(proceeds, scale))
    return {
        "scenario": "PROTECTIVE",
        "levels": [level],
        "summary": {
            "heldQuantity": _fmt(held),
            "sellableQuantity": _fmt(sellable),
            "stepResidualQuantity": _fmt(held - sellable),
            "stopIsModeledBudgetOnly": True,
            "gapBelowStopPossible": True,
        },
    }


def _build_internal(inp):
    _keys(inp, {"state", "params"}, "$")
    raw_state, raw_params = _need(inp, "state", "$"), _need(inp, "params", "$")
    p = _parse_scalars(raw_params)
    st = _parse_state(raw_state, p["cashScale"])
    buy = _parse_levels(raw_params, "buyLevels", st, p["priceTick"])
    sell = _parse_levels(raw_params, "sellLevels", st, p["priceTick"])
    if not p["stopPrice"] < p["markPrice"]:
        _fail("STOP_NOT_BELOW_MARK", "params.stopPrice", "stopPrice 는 markPrice 보다 낮아야 한다.")
    if p["stopPrice"] % p["priceTick"] != 0:
        _fail("TICK_VIOLATION", "params.stopPrice", "호가단위의 배수가 아니다.")
    for index, lv in enumerate(buy):
        if not p["stopPrice"] < lv["price"]:
            _fail("STOP_NOT_BELOW_BUY_PRICE", f"params.buyLevels[{index}].price",
                  "stopPrice 는 모든 매수 가격보다 낮아야 한다.")

    state_out = _state_out(st, p["cashScale"])
    echo = {
        "state": state_out,
        "params": {
            "quantityStep": _fmt(p["quantityStep"]), "priceTick": _fmt(p["priceTick"]),
            "feeRate": _fmt(p["feeRate"]), "cashScale": p["cashScale"],
            "portfolioValue": _fmt(p["portfolioValue"]),
            "maxExposureRatio": _fmt(p["maxExposureRatio"]), "maxLossRatio": _fmt(p["maxLossRatio"]),
            "markPrice": _fmt(p["markPrice"]), "stopPrice": _fmt(p["stopPrice"]),
            "buyLevels": _level_in(buy), "sellLevels": _level_in(sell),
        },
    }
    scenarios = {
        "BUY": _plan_buy(st, p, buy),
        "SELL": _plan_sell(st, p, sell),
        "PROTECTIVE": _plan_protective(st, p),
    }
    for scenario in scenarios.values():
        scenario["brokerSubmissionReady"] = False
        scenario["requiresReplanAfterFill"] = True
    plan = {
        "ok": True,
        "schemaVersion": SCHEMA,
        "stateVersion": st["version"],
        "instrumentId": st["instrumentId"],
        "currency": st["currency"],
        "stateDigest": hashlib.sha256(_cj(state_out).encode("ascii")).hexdigest(),
        "input": echo,
        "scenarioRelation": "MUTUALLY_EXCLUSIVE_ALTERNATIVES",
        "ocoAssumed": False,
        "brokerSubmissionReady": False,
        "requiresReplanAfterFill": True,
        "notice": PLAN_NOTICE,
        "scenarios": scenarios,
    }
    return plan, p, st


def build_plan(input):  # noqa: A002 - 계약의 시그니처 이름
    """입력 state/params 로 세 시나리오 계획을 만든다. 입력은 바꾸지 않는다."""
    try:
        with localcontext(_EXACT):
            return _build_internal(input)[0]
    except _PlanError as exc:
        return _err(exc)
    except Exception:  # fail-closed: 어떤 예외도 구조화된 오류로 바꾼다.
        return _err(_PlanError("CALCULATION_ERROR", "$", "계산할 수 없는 입력이다."))


# ---------------------------------------------------------------------------
# 체결 반영
# ---------------------------------------------------------------------------

def _apply(state, plan, fill):
    if not isinstance(plan, dict) or plan.get("ok") is not True or "input" not in plan:
        _fail("INVALID_PLAN", "plan", "build_plan 이 돌려준 성공 계획이 아니다.")
    try:
        rebuilt, p, plan_state = _build_internal(plan["input"])
        tampered = _cj(rebuilt) != _cj(plan)
    except _PlanError:
        _fail("INVALID_PLAN", "plan", "계획의 입력을 다시 해석할 수 없다.")
    except (TypeError, ValueError):
        _fail("INVALID_PLAN", "plan", "계획을 직렬화할 수 없다.")
    if tampered:
        _fail("PLAN_TAMPERED", "plan", "계획이 입력에서 다시 계산한 결과와 다르다.")

    scale, step = p["cashScale"], p["quantityStep"]
    st = _parse_state(state, scale)
    if st["instrumentId"] != plan_state["instrumentId"]:
        _fail("INSTRUMENT_MISMATCH", "state.instrumentId", "계획의 종목과 다르다.")
    if st["currency"] != plan_state["currency"]:
        _fail("CURRENCY_MISMATCH", "state.currency", "계획의 통화와 다르다. 환전은 지원하지 않는다.")
    if st["version"] != plan_state["version"]:
        _fail("STALE_STATE_VERSION", "state.version", "상태 version 이 계획과 다르다. 재계획이 필요하다.")
    if _cj(_state_out(st, scale)) != _cj(_state_out(plan_state, scale)):
        _fail("STATE_MISMATCH", "state", "같은 version 이지만 상태 내용이 계획이 묶인 상태와 다르다.")
    if st["version"] == MAX_VERSION:
        _fail("STATE_VERSION_EXHAUSTED", "state.version",
              "다음 상태 version 을 표현할 수 없어 체결을 반영하지 않는다.")

    _keys(fill, _FILL_KEYS, "fill")
    scenario = _need(fill, "scenario", "fill")
    if type(scenario) is not str or scenario not in SCENARIOS:
        _fail("UNKNOWN_SCENARIO", "fill.scenario", "BUY, SELL, PROTECTIVE 중 하나여야 한다.")
    level_id = _ident(_need(fill, "levelId", "fill"), "fill.levelId")
    qty = _dec(fill, "quantity", "fill", positive=True)
    price = _dec(fill, "price", "fill", positive=True)
    fee = _dec(fill, "fee", "fill")
    _match_identity(fill, st["instrumentId"], st["currency"], "fill")
    level = next((lv for lv in rebuilt["scenarios"][scenario]["levels"] if lv["levelId"] == level_id),
                 None)
    if level is None:
        _fail("UNKNOWN_LEVEL", "fill.levelId", "해당 시나리오에 그런 레벨이 없다.")
    planned, limit = Decimal(level["plannedQuantity"]), Decimal(level["price"])
    if not level["executable"] or planned <= 0:
        _fail("LEVEL_NOT_EXECUTABLE", "fill.levelId", "계획 수량이 0인 차단된 레벨이다.")
    if qty % step != 0:
        _fail("QUANTITY_STEP_VIOLATION", "fill.quantity", "수량단위의 배수가 아니다.")
    if qty > planned:
        _fail("FILL_EXCEEDS_PLANNED", "fill.quantity", "계획 수량을 넘는 체결이다.")

    held, cash, avg = st["quantity"], st["availableCash"], st["averageCost"]
    if scenario == "BUY":
        if price > limit:
            _fail("BUY_PRICE_ABOVE_LIMIT", "fill.price", "체결가가 계획 매수 지정가를 넘는다.")
        debit = _q(price * qty + fee, scale, ROUND_CEILING)
        if debit > cash:
            _fail("INSUFFICIENT_CASH", "fill.fee", "체결 대금과 수수료가 가용 현금을 넘는다.")
        new_qty = held + qty
        new_avg = _q(_CEIL_DIV.divide(held * avg + qty * price + fee, new_qty), AVG_COST_SCALE,
                     ROUND_HALF_UP)
        new_cash, delta = cash - debit, -debit
    else:
        if scenario == "SELL" and price < limit:
            _fail("SELL_PRICE_BELOW_LIMIT", "fill.price", "체결가가 계획 매도 지정가보다 낮다.")
        if qty > held:
            _fail("FILL_EXCEEDS_HOLDINGS", "fill.quantity", "보유 수량을 넘는다.")
        gross = price * qty - fee
        if gross < 0:
            _fail("NEGATIVE_PROCEEDS", "fill.fee", "수수료가 매도 대금보다 커서 순매도대금이 음수다.")
        credit = _q(gross, scale, ROUND_FLOOR)
        new_qty = held - qty
        new_avg = avg if new_qty > 0 else Decimal(0)
        new_cash, delta = cash + credit, credit

    new_state = _state_out({
        "instrumentId": st["instrumentId"], "currency": st["currency"], "version": st["version"] + 1,
        "quantity": new_qty, "averageCost": new_avg, "availableCash": new_cash}, scale)
    return {
        "ok": True,
        "schemaVersion": SCHEMA,
        "state": new_state,
        "stateDigest": hashlib.sha256(_cj(new_state).encode("ascii")).hexdigest(),
        "previousStateVersion": st["version"],
        "newStateVersion": new_state["version"],
        "fill": {"scenario": scenario, "levelId": level_id, "quantity": _fmt(qty),
                 "price": _fmt(price), "fee": _fmt(fee), "cashDelta": _cash(delta, scale)},
        "oldPlanInvalidated": True,
        "invalidatedScenarios": list(SCENARIOS),
        "requiresReplanAfterFill": True,
        "brokerSubmissionReady": False,
        "notice": FILL_NOTICE,
    }


def apply_fill(state, plan, fill):
    """유효한 계획의 양수 레벨에 대한 부분/전체 체결 하나를 상태에 반영해 새 상태를 돌려준다."""
    try:
        with localcontext(_EXACT):
            return _apply(state, plan, fill)
    except _PlanError as exc:
        return _err(exc)
    except Exception:  # fail-closed
        return _err(_PlanError("CALCULATION_ERROR", "$", "계산할 수 없는 입력이다."))


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _load(path):
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.loads(handle.read()), None
    except (OSError, UnicodeDecodeError):
        return None, _err(_PlanError("INPUT_UNREADABLE", "$", "입력 파일을 읽지 못했다."))
    except (ValueError, RecursionError):
        return None, _err(_PlanError("MALFORMED_JSON", "$", "JSON 을 해석하지 못했다."))


def main(argv=None) -> int:
    args = sys.argv[1:] if argv is None else list(argv)
    if len(args) != 2 or args[0] not in ("build", "fill"):
        result = _err(_PlanError("USAGE_ERROR", "$", "사용법: plan_core.py build|fill INPUT.json"))
    else:
        payload, result = _load(args[1])
        if result is None and args[0] == "build":
            result = build_plan(payload)
        elif result is None:
            if not isinstance(payload, dict) or not isinstance(payload.get("input"), dict) \
                    or "fill" not in payload:
                result = _err(_PlanError("INVALID_TYPE", "$", 'fill 입력은 {"input", "fill"} 객체다.'))
            else:
                result = build_plan(payload["input"])
                if result["ok"]:
                    result = apply_fill(payload["input"].get("state"), result, payload["fill"])
    sys.stdout.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    return 0 if result["ok"] else 2


if __name__ == "__main__":
    sys.exit(main())

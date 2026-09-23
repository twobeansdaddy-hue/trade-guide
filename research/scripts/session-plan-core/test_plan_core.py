"""plan_core.py 합성 데이터 단위 테스트.

전부 손으로 계산 가능한 산술 검증이다. 시장·백테스트·수익성 근거가 아니며 전략 유효성을 뜻하지 않는다.
"""
import copy
import json
import subprocess
import sys
import unittest
from decimal import ROUND_CEILING, Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import plan_core  # noqa: E402

CLI = HERE / "plan_core.py"
EXAMPLES = HERE / "examples"
DEL = object()


def lv(level_id, price, qty, **extra):
    return {"levelId": level_id, "price": price, "requestedQuantity": qty, **extra}


def mk(state=None, params=None):
    """기본 USD 입력(보유 5주, 평균 100, 현금 10000.00)에 덮어쓰기. DEL 이면 필드를 지운다."""
    inp = {
        "state": {"instrumentId": "SYN-A", "currency": "USD", "version": 3, "quantity": "5",
                  "averageCost": "100", "availableCash": "10000.00",
                  "externalReservationsConfirmed": True},
        "params": {"quantityStep": "1", "priceTick": "0.01", "feeRate": "0.001", "cashScale": 2,
                   "portfolioValue": "100000", "maxExposureRatio": "0.5", "maxLossRatio": "0.05",
                   "markPrice": "100", "stopPrice": "95",
                   "buyLevels": [lv("B1", "99.00", "10"), lv("B2", "98.00", "10")],
                   "sellLevels": [lv("S1", "105.00", "3"), lv("S2", "106.00", "4")]},
    }
    for key, patch in (("state", state), ("params", params)):
        for name, value in (patch or {}).items():
            if value is DEL:
                inp[key].pop(name, None)
            else:
                inp[key][name] = value
    return inp


def flat(**params):
    """보유 없음, 한도가 사실상 안 걸리는 입력."""
    base = {"portfolioValue": "1000000", "maxExposureRatio": "1", "maxLossRatio": "0.5"}
    base.update(params)
    return mk(state={"quantity": "0", "averageCost": "0"}, params=base)


def plan_of(inp):
    result = plan_core.build_plan(inp)
    assert result["ok"], result
    return result


def level(plan, scenario, level_id):
    return next(x for x in plan["scenarios"][scenario]["levels"] if x["levelId"] == level_id)


def fill(scenario="BUY", level_id="B1", quantity="4", price="98.50", fee="0.40", **extra):
    return {"scenario": scenario, "levelId": level_id, "quantity": quantity, "price": price,
            "fee": fee, **extra}


def walk(value):
    if isinstance(value, dict):
        for v in value.values():
            yield from walk(v)
    elif isinstance(value, list):
        for v in value:
            yield from walk(v)
    else:
        yield value


class PlanShapeTest(unittest.TestCase):
    def test_never_broker_ready_and_mutually_exclusive(self):
        plan = plan_of(mk())
        self.assertIs(plan["brokerSubmissionReady"], False)
        self.assertIs(plan["requiresReplanAfterFill"], True)
        self.assertIs(plan["ocoAssumed"], False)
        self.assertEqual(plan["scenarioRelation"], "MUTUALLY_EXCLUSIVE_ALTERNATIVES")
        self.assertEqual(set(plan["scenarios"]), {"BUY", "SELL", "PROTECTIVE"})
        for scenario in plan["scenarios"].values():
            self.assertIs(scenario["brokerSubmissionReady"], False)
            self.assertIs(scenario["requiresReplanAfterFill"], True)
        self.assertIn("추천", plan["notice"])
        self.assertIn("OCO", plan["notice"])

    def test_output_is_json_with_decimal_strings_only(self):
        plan = plan_of(mk())
        self.assertEqual(json.loads(json.dumps(plan)), plan)
        self.assertFalse([v for v in walk(plan) if isinstance(v, float)])

    def test_deterministic_and_digest_binds_state(self):
        self.assertEqual(plan_of(mk()), plan_of(mk()))
        other = plan_of(mk(state={"availableCash": "9999.00"}))
        self.assertNotEqual(plan_of(mk())["stateDigest"], other["stateDigest"])


class BuyScenarioTest(unittest.TestCase):
    def test_hand_computed_reservations_and_modeled_risk(self):
        buy = plan_of(mk())["scenarios"]["BUY"]
        b1, b2 = buy["levels"]
        # 99*1.001*10=990.99, 98*1.001*10=980.98
        self.assertEqual((b1["reservedCash"], b2["reservedCash"]), ("990.99", "980.98"))
        self.assertEqual(buy["summary"]["remainingCash"], "8028.03")
        # 위험: (99-95+(99+95)*0.001)*10 = 41.94
        self.assertEqual(b1["reservedRisk"], "41.94")
        # 기존 위험: 5*(100-95+95*0.001)=25.475, 기존 노출 5*100
        self.assertEqual(buy["summary"]["existingModeledRisk"], "25.475")
        self.assertEqual(buy["summary"]["existingExposure"], "500")
        self.assertEqual(buy["summary"]["totalReservedExposure"], "1970")

    def test_cash_is_reserved_cumulatively_with_ceiling_rounding(self):
        inp = flat(stopPrice="30", buyLevels=[lv("B1", "33.33", "2"), lv("B2", "33.20", "5")])
        inp["state"]["availableCash"] = "100.00"
        buy = plan_of(inp)["scenarios"]["BUY"]
        b1, b2 = buy["levels"]
        # 2*33.33*1.001=66.72666 -> 66.73 (올림), 남은 33.27
        self.assertEqual((b1["plannedQuantity"], b1["reservedCash"]), ("2", "66.73"))
        # 33.27/(33.20*1.001)=1.0011 -> 1주, 33.2332 -> 33.24, 남은 0.03
        self.assertEqual((b2["plannedQuantity"], b2["reservedCash"]), ("1", "33.24"))
        self.assertEqual(b2["reasonCodes"], ["CASH_LIMIT"])
        self.assertEqual(buy["summary"]["remainingCash"], "0.03")

    def test_insufficient_cash_blocks_level_with_reason(self):
        inp = mk(state={"availableCash": "10.00"})
        b1 = level(plan_of(inp), "BUY", "B1")
        self.assertEqual((b1["status"], b1["executable"], b1["plannedQuantity"]), ("BLOCKED", False, "0"))
        self.assertEqual(b1["reasonCodes"], ["CASH_LIMIT"])
        self.assertEqual(b1["unplannedQuantity"], "10")

    def test_second_level_blocked_when_first_consumes_cash(self):
        inp = flat(stopPrice="30", buyLevels=[lv("B1", "33.33", "2"), lv("B2", "33.30", "5")])
        inp["state"]["availableCash"] = "100.00"
        b2 = level(plan_of(inp), "BUY", "B2")
        self.assertEqual((b2["status"], b2["reasonCodes"]), ("BLOCKED", ["CASH_LIMIT"]))

    def test_fractional_quantity_step_rounds_down(self):
        inp = flat(quantityStep="0.25", buyLevels=[lv("B1", "99.00", "1.6")])
        b1 = level(plan_of(inp), "BUY", "B1")
        self.assertEqual((b1["plannedQuantity"], b1["unplannedQuantity"]), ("1.5", "0.1"))
        self.assertEqual(b1["reservedCash"], "148.65")  # 1.5*99.099=148.6485 -> 올림

    def test_request_below_step_is_blocked(self):
        b1 = level(plan_of(flat(buyLevels=[lv("B1", "99.00", "0.4")])), "BUY", "B1")
        self.assertEqual((b1["status"], b1["reasonCodes"]), ("BLOCKED", ["BELOW_STEP"]))

    def test_fee_is_part_of_cash_reservation(self):
        with_fee = level(plan_of(mk()), "BUY", "B1")["reservedCash"]
        no_fee = level(plan_of(mk(params={"feeRate": "0"})), "BUY", "B1")["reservedCash"]
        self.assertEqual((with_fee, no_fee), ("990.99", "990.00"))

    def test_exposure_reserved_cumulatively_at_buy_notional(self):
        inp = flat(portfolioValue="10000", maxExposureRatio="0.1", maxLossRatio="0.5",
                   buyLevels=[lv("B1", "99.00", "6"), lv("B2", "98.00", "6")])
        buy = plan_of(inp)["scenarios"]["BUY"]
        b1, b2 = buy["levels"]
        self.assertEqual((b1["plannedQuantity"], b1["reservedExposure"]), ("6", "594"))
        # 여유 406/98=4.14 -> 4주
        self.assertEqual((b2["plannedQuantity"], b2["reasonCodes"]), ("4", ["EXPOSURE_LIMIT"]))
        self.assertEqual(buy["summary"]["remainingExposureHeadroom"], "14")

    def test_risk_budget_reserved_cumulatively(self):
        inp = flat(portfolioValue="100000", maxLossRatio="0.001",
                   buyLevels=[lv("B1", "99.00", "30"), lv("B2", "98.00", "30")])
        buy = plan_of(inp)["scenarios"]["BUY"]
        b1, b2 = buy["levels"]
        # 예산 100. 4.194/주 -> 23주(96.462), 남은 3.538 / 3.193 -> 1주
        self.assertEqual((b1["plannedQuantity"], b1["reasonCodes"]), ("23", ["RISK_LIMIT"]))
        self.assertEqual((b2["plannedQuantity"], b2["reasonCodes"]), ("1", ["RISK_LIMIT"]))
        self.assertEqual(buy["summary"]["remainingRiskBudget"], "0.345")

    def test_existing_exposure_breach_blocks_adds_without_forcing_sale(self):
        plan = plan_of(mk(state={"quantity": "600"}))
        buy = plan["scenarios"]["BUY"]
        self.assertTrue(buy["summary"]["existingExposureBreach"])
        self.assertFalse(buy["summary"]["existingRiskBreach"])
        for entry in buy["levels"]:
            self.assertEqual((entry["status"], entry["reasonCodes"]), ("BLOCKED", ["EXPOSURE_LIMIT"]))
        self.assertEqual(buy["summary"]["remainingExposureHeadroom"], "0")
        self.assertEqual(level(plan, "SELL", "S1")["plannedQuantity"], "3")  # 강제 매도 없음, 선택은 호출자

    def test_existing_risk_breach_blocks_adds(self):
        inp = mk(state={"quantity": "500"},
                 params={"portfolioValue": "1000000", "maxExposureRatio": "1", "maxLossRatio": "0.002"})
        buy = plan_of(inp)["scenarios"]["BUY"]
        self.assertTrue(buy["summary"]["existingRiskBreach"])
        self.assertFalse(buy["summary"]["existingExposureBreach"])
        self.assertEqual(buy["summary"]["existingModeledRisk"], "2547.5")
        for entry in buy["levels"]:
            self.assertEqual((entry["status"], entry["reasonCodes"]), ("BLOCKED", ["RISK_LIMIT"]))

    def test_rounding_never_exceeds_any_limit(self):
        for cash in ("100.00", "1234.56", "99.99"):
            for fee in ("0", "0.001", "0.0033"):
                for step in ("1", "0.25"):
                    with self.subTest(cash=cash, fee=fee, step=step):
                        inp = flat(quantityStep=step, feeRate=fee, portfolioValue="5000", stopPrice="20",
                                   maxExposureRatio="0.3", maxLossRatio="0.02",
                                   buyLevels=[lv("B1", "33.33", "40"), lv("B2", "31.07", "40"),
                                              lv("B3", "30.01", "40")])
                        inp["state"]["availableCash"] = cash
                        buy = plan_of(inp)["scenarios"]["BUY"]
                        rate, stop = Decimal(fee), Decimal("20")
                        cash_sum = exposure_sum = risk_sum = Decimal(0)
                        for entry in buy["levels"]:
                            q, price = Decimal(entry["plannedQuantity"]), Decimal(entry["price"])
                            self.assertEqual(q % Decimal(step), 0)
                            cash_sum += (q * price * (1 + rate)).quantize(Decimal("0.01"), ROUND_CEILING)
                            exposure_sum += q * price
                            risk_sum += q * (price - stop + (price + stop) * rate)
                        self.assertLessEqual(cash_sum, Decimal(cash))
                        self.assertLessEqual(exposure_sum, Decimal("1500"))
                        self.assertLessEqual(risk_sum, Decimal("100"))

    def test_sell_proceeds_are_not_buying_power(self):
        inp = mk(state={"availableCash": "0.00"})
        plan = plan_of(inp)
        self.assertEqual(plan["scenarios"]["BUY"]["summary"]["totalPlannedQuantity"], "0")
        self.assertEqual(level(plan, "SELL", "S1")["plannedQuantity"], "3")
        self.assertIs(plan["scenarios"]["BUY"]["summary"]["sellProceedsCountedAsBuyingPower"], False)
        self.assertIs(plan["scenarios"]["SELL"]["summary"]["proceedsCountedAsBuyingPower"], False)


class SellAndProtectiveTest(unittest.TestCase):
    def test_sell_caps_aggregate_quantity_to_holdings(self):
        sell = plan_of(mk())["scenarios"]["SELL"]
        s1, s2 = sell["levels"]
        self.assertEqual((s1["plannedQuantity"], s2["plannedQuantity"]), ("3", "2"))
        self.assertEqual((s2["unplannedQuantity"], s2["reasonCodes"]), ("2", ["HOLDINGS_LIMIT"]))
        self.assertEqual(sell["summary"]["unallocatedSellableQuantity"], "0")
        self.assertEqual(sell["summary"]["totalPlannedQuantity"], "5")

    def test_sell_never_borrows_buy_quantity_and_keeps_residuals(self):
        plan = plan_of(mk(state={"quantity": "5.5"}))
        sell = plan["scenarios"]["SELL"]["summary"]
        self.assertEqual((sell["sellableQuantity"], sell["stepResidualQuantity"]), ("5", "0.5"))
        self.assertIs(sell["borrowsFutureBuyQuantity"], False)
        flat_plan = plan_of(flat())
        self.assertEqual(level(flat_plan, "SELL", "S1")["status"], "BLOCKED")

    def test_sell_net_proceeds_estimate_uses_floor(self):
        # 105*3*0.999=314.685 -> 314.68
        self.assertEqual(level(plan_of(mk()), "SELL", "S1")["estimatedNetProceeds"], "314.68")

    def test_protective_sells_all_sellable_at_stop(self):
        entry = level(plan_of(mk(state={"quantity": "5.5"})), "PROTECTIVE", "PROTECTIVE_STOP")
        self.assertEqual((entry["price"], entry["plannedQuantity"]), ("95", "5"))
        empty = level(plan_of(flat()), "PROTECTIVE", "PROTECTIVE_STOP")
        self.assertEqual((empty["status"], empty["reasonCodes"]), ("BLOCKED", ["NO_SELLABLE_QUANTITY"]))

    def test_krw_no_fx_and_integer_cash(self):
        inp = flat(quantityStep="1", priceTick="100", feeRate="0.00015", cashScale=0,
                   markPrice="70000", stopPrice="60000", portfolioValue="100000000",
                   buyLevels=[lv("B1", "69900", "10", currency="KRW")], sellLevels=[])
        inp["state"].update({"instrumentId": "SYN-K", "currency": "KRW", "availableCash": "1000000"})
        b1 = level(plan_of(inp), "BUY", "B1")
        # 10*69900*1.00015=699104.85 -> 699105
        self.assertEqual((b1["plannedQuantity"], b1["reservedCash"]), ("10", "699105"))
        inp["params"]["buyLevels"][0]["currency"] = "USD"
        self.assertEqual(plan_core.build_plan(inp)["error"]["code"], "CURRENCY_MISMATCH")


class InvalidInputTest(unittest.TestCase):
    def assertBlocked(self, inp, code):
        before = copy.deepcopy(inp)
        result = plan_core.build_plan(inp)
        self.assertIs(result["ok"], False)
        self.assertEqual(result["error"]["code"], code, result)
        self.assertEqual(set(result["error"]), {"code", "field", "message"})
        self.assertEqual(inp, before)

    def test_rejected_decimal_forms(self):
        cases = [(0.01, "INVALID_TYPE"), (True, "INVALID_TYPE"), (1, "INVALID_TYPE"),
                 ("NaN", "INVALID_DECIMAL"), ("Infinity", "INVALID_DECIMAL"), ("-Infinity", "INVALID_DECIMAL"),
                 ("1e3", "INVALID_DECIMAL"), ("-1", "INVALID_DECIMAL"), ("+1", "INVALID_DECIMAL"),
                 ("", "INVALID_DECIMAL"), (" 1", "INVALID_DECIMAL"), ("1_0", "INVALID_DECIMAL"),
                 (".5", "INVALID_DECIMAL"), ("5.", "INVALID_DECIMAL"), ("１", "INVALID_DECIMAL"),
                 ("1" * 40, "INVALID_DECIMAL"), ("0", "OUT_OF_RANGE")]
        for value, code in cases:
            with self.subTest(value=value):
                self.assertBlocked(mk(params={"quantityStep": value}), code)

    def test_rejected_scalar_and_state_fields(self):
        cases = [
            ("feeRate=1", mk(params={"feeRate": "1"}), "OUT_OF_RANGE"),
            ("maxExposureRatio>1", mk(params={"maxExposureRatio": "1.01"}), "OUT_OF_RANGE"),
            ("maxLossRatio=0", mk(params={"maxLossRatio": "0"}), "OUT_OF_RANGE"),
            ("cashScale=9", mk(params={"cashScale": 9}), "OUT_OF_RANGE"),
            ("cashScale bool", mk(params={"cashScale": True}), "INVALID_TYPE"),
            ("cashScale str", mk(params={"cashScale": "2"}), "INVALID_TYPE"),
            ("portfolioValue=0", mk(params={"portfolioValue": "0"}), "OUT_OF_RANGE"),
            ("missing limit", mk(params={"maxLossRatio": DEL}), "MISSING_FIELD"),
            ("missing fee", mk(params={"feeRate": DEL}), "MISSING_FIELD"),
            ("unknown param", mk(params={"maxExposurRatio": "0.5"}), "UNKNOWN_FIELD"),
            ("version<0", mk(state={"version": -1}), "OUT_OF_RANGE"),
            ("version bool", mk(state={"version": True}), "INVALID_TYPE"),
            ("version str", mk(state={"version": "3"}), "INVALID_TYPE"),
            ("reservations false", mk(state={"externalReservationsConfirmed": False}),
             "EXTERNAL_RESERVATIONS_UNCONFIRMED"),
            ("reservations missing", mk(state={"externalReservationsConfirmed": DEL}),
             "EXTERNAL_RESERVATIONS_UNCONFIRMED"),
            ("currency EUR", mk(state={"currency": "EUR"}), "INVALID_CURRENCY"),
            ("held without cost", mk(state={"averageCost": "0"}), "OUT_OF_RANGE"),
            ("cash finer than scale", mk(state={"availableCash": "100.001"}), "CASH_SCALE_MISMATCH"),
            ("stop == mark", mk(params={"stopPrice": "100"}), "STOP_NOT_BELOW_MARK"),
            ("stop >= buy price", mk(params={"stopPrice": "99"}), "STOP_NOT_BELOW_BUY_PRICE"),
            ("stop off tick", mk(params={"stopPrice": "95.005"}), "TICK_VIOLATION"),
        ]
        for name, inp, code in cases:
            with self.subTest(name):
                self.assertBlocked(inp, code)

    def test_rejected_levels(self):
        cases = [
            ("tick", [lv("B1", "99.005", "1")], "TICK_VIOLATION"),
            ("duplicate id", [lv("B1", "99.00", "1"), lv("B1", "98.00", "1")], "DUPLICATE_LEVEL_ID"),
            ("float price", [lv("B1", 99.0, "1")], "INVALID_TYPE"),
            ("zero qty", [lv("B1", "99.00", "0")], "OUT_OF_RANGE"),
            ("instrument", [lv("B1", "99.00", "1", instrumentId="SYN-B")], "INSTRUMENT_MISMATCH"),
            ("currency", [lv("B1", "99.00", "1", currency="KRW")], "CURRENCY_MISMATCH"),
            ("unknown key", [lv("B1", "99.00", "1", note="x")], "UNKNOWN_FIELD"),
            ("bad id", [lv("", "99.00", "1")], "INVALID_ID"),
            ("not object", ["B1"], "INVALID_TYPE"),
        ]
        for name, levels, code in cases:
            with self.subTest(name):
                self.assertBlocked(mk(params={"buyLevels": levels}), code)
        self.assertBlocked(mk(params={"sellLevels": "S1"}), "INVALID_TYPE")
        self.assertBlocked(mk(params={"sellLevels": DEL}), "MISSING_FIELD")
        self.assertBlocked(mk(params={"sellLevels": [lv("S1", "105.00", "1", currency="KRW")]}),
                           "CURRENCY_MISMATCH")

    def test_non_object_inputs_fail_closed_without_raising(self):
        for bad in (None, [], "x", 3, {}, {"state": {}}, {"state": 1, "params": 2}):
            with self.subTest(bad=repr(bad)):
                result = plan_core.build_plan(bad)
                self.assertIs(result["ok"], False)
                self.assertIn("code", result["error"])

    def test_error_messages_do_not_echo_input_values(self):
        inp = mk(params={"quantityStep": "SECRET-TOKEN"})
        self.assertNotIn("SECRET-TOKEN", json.dumps(plan_core.build_plan(inp), ensure_ascii=False))


class ApplyFillTest(unittest.TestCase):
    def setUp(self):
        self.inp = mk()
        self.state = self.inp["state"]
        self.plan = plan_of(self.inp)

    def apply(self, fill_, state=None, plan=None):
        state = self.state if state is None else state
        plan = self.plan if plan is None else plan
        snapshot = copy.deepcopy((state, plan, fill_))
        result = plan_core.apply_fill(state, plan, fill_)
        self.assertEqual((state, plan, fill_), snapshot, "입력이 변경됨")
        return result

    def assertRejected(self, result, code):
        self.assertIs(result["ok"], False, result)
        self.assertEqual(result["error"]["code"], code, result)

    def test_partial_buy_weighted_average_cash_and_version(self):
        result = self.apply(fill())
        self.assertTrue(result["ok"])
        new = result["state"]
        # 현금: 4*98.5+0.4=394.40 차감. 평균: (5*100+4*98.5+0.4)/9 -> 소수 10자리 HALF_UP
        self.assertEqual((new["quantity"], new["availableCash"], new["averageCost"], new["version"]),
                         ("9", "9605.60", "99.3777777778", 4))
        self.assertEqual(result["fill"]["cashDelta"], "-394.40")
        self.assertTrue(result["oldPlanInvalidated"])
        self.assertEqual(result["invalidatedScenarios"], ["BUY", "SELL", "PROTECTIVE"])
        self.assertIs(result["brokerSubmissionReady"], False)

    def test_exact_weighted_average(self):
        result = self.apply(fill(quantity="5", price="99.00", fee="0"))
        self.assertEqual(result["state"]["averageCost"], "99.5")

    def test_buy_fee_enters_average_cost_without_double_debit(self):
        result = self.apply(fill(quantity="5", price="99.00", fee="1"))
        self.assertEqual(result["state"]["averageCost"], "99.6")
        self.assertEqual(result["state"]["availableCash"], "9504.00")

    def test_partial_sell_keeps_average_and_floors_credit(self):
        result = self.apply(fill("SELL", "S1", "2", "105.00", "0.215"))
        new = result["state"]
        # 210-0.215=209.785 -> 내림 209.78
        self.assertEqual((new["quantity"], new["averageCost"], new["availableCash"]), ("3", "100", "10209.78"))
        self.assertEqual(result["fill"]["cashDelta"], "209.78")

    def test_full_sell_zeroes_average_cost(self):
        result = self.apply(fill("PROTECTIVE", "PROTECTIVE_STOP", "5", "95.00", "1"))
        self.assertEqual((result["state"]["quantity"], result["state"]["averageCost"]), ("0", "0"))

    def test_five_held_three_sold_leaves_two_protective_after_fresh_plan(self):
        result = self.apply(fill("SELL", "S1", "3", "105.00", "0.32"))
        self.assertEqual(result["state"]["quantity"], "2")
        fresh = self.inp
        fresh["state"] = result["state"]
        entry = level(plan_of(fresh), "PROTECTIVE", "PROTECTIVE_STOP")
        self.assertEqual(entry["plannedQuantity"], "2")
        # 옛 시나리오는 재사용 불가
        self.assertRejected(self.apply(fill("SELL", "S2", "2", "106.00", "0"), state=result["state"]),
                            "STALE_STATE_VERSION")

    def test_gap_below_stop_allowed_only_for_protective(self):
        result = self.apply(fill("PROTECTIVE", "PROTECTIVE_STOP", "5", "90.00", "0.5"))
        self.assertEqual(result["state"]["availableCash"], "10449.50")
        self.assertRejected(self.apply(fill("SELL", "S1", "3", "104.99", "0")), "SELL_PRICE_BELOW_LIMIT")
        self.assertTrue(self.apply(fill("SELL", "S1", "3", "106.00", "0"))["ok"])  # 유리한 체결

    def test_buy_price_above_limit_rejected_but_better_price_allowed(self):
        self.assertRejected(self.apply(fill(price="99.01")), "BUY_PRICE_ABOVE_LIMIT")
        self.assertTrue(self.apply(fill(price="98.00"))["ok"])

    def test_negative_sale_proceeds_rejected(self):
        result = self.apply(fill("PROTECTIVE", "PROTECTIVE_STOP", "1", "0.01", "1"))
        self.assertRejected(result, "NEGATIVE_PROCEEDS")

    def test_oversized_fill_rejected(self):
        self.assertRejected(self.apply(fill(quantity="11")), "FILL_EXCEEDS_PLANNED")
        capped = plan_of(mk(state={"availableCash": "300.00"}))  # B1 은 3주로 제한됨
        self.assertEqual(level(capped, "BUY", "B1")["plannedQuantity"], "3")
        self.assertRejected(self.apply(fill(quantity="4"), state=mk(state={"availableCash": "300.00"})["state"],
                                       plan=capped), "FILL_EXCEEDS_PLANNED")
        self.assertRejected(self.apply(fill("SELL", "S1", "4", "105.00", "0")), "FILL_EXCEEDS_PLANNED")

    def test_buy_debit_above_cash_rejected(self):
        self.assertRejected(self.apply(fill(fee="20000")), "INSUFFICIENT_CASH")

    def test_stale_version_rejected(self):
        for version in (2, 4):
            with self.subTest(version=version):
                self.assertRejected(self.apply(fill(), state=dict(self.state, version=version)),
                                    "STALE_STATE_VERSION")

    def test_max_version_cannot_produce_unusable_next_state(self):
        state = dict(self.state, version=plan_core.MAX_VERSION)
        inp = mk(state=state)
        plan = plan_of(inp)
        self.assertRejected(self.apply(fill(), state=state, plan=plan), "STATE_VERSION_EXHAUSTED")

    def test_same_version_different_state_rejected(self):
        for name, value in (("availableCash", "99999.00"), ("quantity", "50"), ("averageCost", "1")):
            with self.subTest(field=name):
                self.assertRejected(self.apply(fill(), state=dict(self.state, **{name: value})),
                                    "STATE_MISMATCH")

    def test_instrument_and_currency_binding(self):
        self.assertRejected(self.apply(fill(), state=dict(self.state, instrumentId="SYN-B")),
                            "INSTRUMENT_MISMATCH")
        self.assertRejected(self.apply(fill(), state=dict(self.state, currency="KRW")), "CURRENCY_MISMATCH")
        self.assertRejected(self.apply(fill(instrumentId="SYN-B")), "INSTRUMENT_MISMATCH")
        self.assertRejected(self.apply(fill(currency="KRW")), "CURRENCY_MISMATCH")

    def test_forged_or_tampered_plan_rejected(self):
        forged = copy.deepcopy(self.plan)
        forged["scenarios"]["SELL"]["levels"][0]["plannedQuantity"] = "5"
        self.assertRejected(self.apply(fill("SELL", "S1", "5", "105.00", "0"), plan=forged), "PLAN_TAMPERED")
        ready = copy.deepcopy(self.plan)
        ready["brokerSubmissionReady"] = 0  # False 와 == 이지만 JSON 으로는 다르다
        self.assertRejected(self.apply(fill(), plan=ready), "PLAN_TAMPERED")
        # 상태·계획 입력을 함께 위조해도 시나리오가 그 입력에서 나온 값이 아니면 거부
        both = copy.deepcopy(self.plan)
        both["input"]["state"]["quantity"] = "50"
        self.assertRejected(self.apply(fill("SELL", "S1", "3", "105.00", "0"),
                                       state=dict(self.state, quantity="50"), plan=both), "PLAN_TAMPERED")

    def test_invalid_plan_objects(self):
        for bad in ("plan", [], {}, {"ok": False}, {"ok": True}, {"ok": True, "input": {"state": 1}}):
            with self.subTest(bad=repr(bad)):
                self.assertRejected(self.apply(fill(), plan=bad), "INVALID_PLAN")
        self.assertRejected(plan_core.apply_fill(self.state, None, fill()), "INVALID_PLAN")
        self.assertRejected(self.apply(fill(), plan=plan_core.build_plan({})), "INVALID_PLAN")

    def test_invalid_fill_fields(self):
        cases = [
            (fill(scenario="HOLD"), "UNKNOWN_SCENARIO"),
            (fill(level_id="B9"), "UNKNOWN_LEVEL"),
            (fill(level_id="S1"), "UNKNOWN_LEVEL"),  # 다른 시나리오의 레벨
            (fill(quantity="0"), "OUT_OF_RANGE"),
            (fill(quantity="0.5"), "QUANTITY_STEP_VIOLATION"),
            (fill(quantity=4), "INVALID_TYPE"),
            (fill(price=98.5), "INVALID_TYPE"),
            (fill(fee="-1"), "INVALID_DECIMAL"),
            (fill(fee="NaN"), "INVALID_DECIMAL"),
            (fill(note="x"), "UNKNOWN_FIELD"),
        ]
        for fill_, code in cases:
            with self.subTest(code=code, fill=fill_):
                self.assertRejected(self.apply(fill_), code)
        missing = fill()
        del missing["price"]
        self.assertRejected(self.apply(missing), "MISSING_FIELD")
        self.assertRejected(self.apply(None), "INVALID_TYPE")

    def test_blocked_level_cannot_be_filled(self):
        inp = mk(state={"availableCash": "10.00"})
        self.assertRejected(self.apply(fill(), state=inp["state"], plan=plan_of(inp)), "LEVEL_NOT_EXECUTABLE")

    def test_no_mutation_on_build_and_repeat_is_deterministic(self):
        inp = mk()
        before = copy.deepcopy(inp)
        plan_core.build_plan(inp)
        self.assertEqual(inp, before)
        self.assertEqual(self.apply(fill()), self.apply(fill()))  # 영속 멱등성은 없다: 같은 입력은 같은 결과


class CliTest(unittest.TestCase):
    def run_cli(self, *args):
        proc = subprocess.run([sys.executable, str(CLI), *map(str, args)], capture_output=True, text=True,
                              env={"PYTHONDONTWRITEBYTECODE": "1"})
        return proc.returncode, json.loads(proc.stdout)

    def test_examples_run(self):
        code, out = self.run_cli("build", EXAMPLES / "usd_three_scenarios.json")
        self.assertEqual((code, out["ok"], out["brokerSubmissionReady"]), (0, True, False))
        code, out = self.run_cli("fill", EXAMPLES / "krw_partial_buy_fill.json")
        self.assertEqual(code, 0)
        # 4*69800+42=279242 차감
        self.assertEqual((out["state"]["availableCash"], out["state"]["quantity"]), ("720758", "4"))

    def test_cli_failures_are_structured(self):
        for args in (("build", HERE / "missing.json"), ("bogus", "x"), ("build",)):
            with self.subTest(args=args):
                code, out = self.run_cli(*args)
                self.assertEqual((code, out["ok"]), (2, False))


if __name__ == "__main__":
    unittest.main()

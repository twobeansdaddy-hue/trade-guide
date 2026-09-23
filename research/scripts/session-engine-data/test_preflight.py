"""preflight.py 합성 데이터 테스트.

이 테스트는 검사기의 구조 검사 동작만 확인한다. 시장 사실성, 라이선스, 수익성,
거래소 캘린더, 체결 순서, 주문 잔여량은 검증 대상이 아니다.
"""
import copy
import json
import math
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import preflight  # noqa: E402

CLI = HERE / "preflight.py"
EXAMPLES = HERE / "examples"


def bar(inst="SYN-A", event="2026-03-06T09:31:00-05:00", available="2026-03-06T09:31:02-05:00",
        o=100.0, h=100.5, lo=99.8, c=100.2, v=1000, **extra):
    row = {"instrumentId": inst, "market": "US", "currency": "USD", "session": "REGULAR",
           "eventAt": event, "availableAt": available,
           "open": o, "high": h, "low": lo, "close": c, "volume": v}
    row.update(extra)
    return row


def make_payload(rows=None, manifest=None, requirements=None):
    payload = {
        "manifest": {
            "datasetId": "synthetic-test", "market": "US", "currency": "USD", "interval": "1m",
            "timezone": "America/New_York", "sessionCoverage": ["REGULAR"],
            "adjustmentPolicy": "RAW", "availabilityMode": "ACTUAL_RECEIPT",
            "universeMode": "FIXED_DIAGNOSTIC_SET", "licenseStatus": "RESEARCH_ONLY_SYNTHETIC",
        },
        "requirements": {
            "market": "US", "currency": "USD", "interval": "1m", "session": "REGULAR",
            "cutoffAt": "2026-03-10T00:00:00Z", "requirePitUniverse": False,
            "requireVintage": False,
        },
        "rows": rows if rows is not None else [
            bar("SYN-A", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00"),
            bar("SYN-B", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00", 50, 50.1, 49.9, 50),
            bar("SYN-A", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00"),
            bar("SYN-B", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00", 50, 50.1, 49.9, 50),
        ],
    }
    payload["manifest"].update(manifest or {})
    payload["requirements"].update(requirements or {})
    return payload


class PreflightTestCase(unittest.TestCase):
    def check(self, payload, status, *codes):
        result = preflight.validate(payload)
        found = {i["code"] for i in result["issues"]}
        self.assertEqual(result["status"], status, msg=str(result["issues"]))
        for code in codes:
            self.assertIn(code, found, msg=str(result["issues"]))
        return result

    def codes(self, result):
        return [i["code"] for i in result["issues"]]

    def del_key(self, payload, section, key):
        del payload[section][key]
        return payload


class NormalDataTests(PreflightTestCase):
    def test_valid_synthetic_data_passes(self):
        result = self.check(make_payload(), preflight.PASS)
        self.assertEqual(result["issues"], [])
        self.assertEqual(result["rowCount"], 4)
        self.assertEqual(result["instrumentStatus"], {"SYN-A": "PASS", "SYN-B": "PASS"})

    def test_result_states_scope_limits_and_is_json_serializable(self):
        result = preflight.validate(make_payload())
        self.assertIn("인증하지 않으며", result["notice"])
        json.dumps(result, allow_nan=False)

    def test_input_is_not_mutated(self):
        for payload in (make_payload(),
                        make_payload(rows=[bar(h=1.0), bar(event="bad")],
                                     manifest={"adjustmentPolicy": "UNKNOWN"})):
            before = copy.deepcopy(payload)
            preflight.validate(payload)
            self.assertEqual(payload, before)

    def test_exact_cutoff_is_not_future(self):
        row = bar(event="2026-03-09T19:00:00-05:00", available="2026-03-09T19:00:00-05:00")
        self.check(make_payload(rows=[row]), preflight.PASS)  # == 2026-03-10T00:00:00Z

    def test_zero_volume_allowed(self):
        self.check(make_payload(rows=[bar(v=0)]), preflight.PASS)

    def test_available_equal_event_allowed(self):
        row = bar(event="2026-03-06T09:31:00-05:00", available="2026-03-06T09:31:00-05:00")
        self.check(make_payload(rows=[row]), preflight.PASS)

    def test_provisional_manifest_values_do_not_block_when_not_strict(self):
        payload = make_payload(
            manifest={"adjustmentPolicy": "UNKNOWN", "availabilityMode": "FIXED_LAG_APPROXIMATION"},
            requirements={"strict": False})
        self.check(payload, preflight.WARN,
                   "ADJUSTMENT_POLICY_UNKNOWN", "AVAILABILITY_FIXED_LAG_APPROXIMATION")


class ObservedProvenanceTests(PreflightTestCase):
    def payload(self):
        row = bar(event="2026-03-06T09:31:00-05:00",
                  available="2026-03-06T09:31:02-05:00",
                  barStartAt="2026-03-06T09:30:00-05:00",
                  sourceTimestampAt="2026-03-06T09:30:00-05:00",
                  sourceTimestampMeaning="BAR_OPEN",
                  receivedAt="2026-03-06T09:31:02-05:00")
        return make_payload(rows=[row], requirements={"requireObservedProvenance": True},
                            manifest={"sourceProvider": "SYNTHETIC",
                                      "sourceAdjustmentMode": "RAW",
                                      "adjustmentEvidenceRef": "synthetic-request"})

    def test_observed_bar_passes(self):
        self.check(self.payload(), preflight.PASS)

    def test_missing_receipt_cannot_be_filled_from_bar_time(self):
        payload = self.payload()
        del payload["rows"][0]["receivedAt"]
        self.check(payload, preflight.BLOCKED, "PROVENANCE_MISSING")

    def test_open_timestamp_cannot_be_used_as_completed_bar_time(self):
        payload = self.payload()
        payload["rows"][0]["eventAt"] = payload["rows"][0]["sourceTimestampAt"]
        self.check(payload, preflight.BLOCKED, "BAR_DURATION_MISMATCH")

    def test_receipt_after_cutoff_is_not_point_in_time(self):
        payload = self.payload()
        payload["requirements"]["cutoffAt"] = "2026-03-06T09:31:01-05:00"
        self.check(payload, preflight.BLOCKED, "FUTURE_RECEIPT_AT")

    def test_declared_adjustment_must_match_request(self):
        payload = self.payload()
        payload["manifest"]["sourceAdjustmentMode"] = "SPLITS"
        self.check(payload, preflight.BLOCKED, "SOURCE_ADJUSTMENT_MISMATCH")

    def test_interval_and_source_timestamp_are_checked(self):
        payload = self.payload()
        payload["rows"][0]["barStartAt"] = "2026-03-06T09:29:00-05:00"
        self.check(payload, preflight.BLOCKED, "SOURCE_TIMESTAMP_MISMATCH",
                   "BAR_DURATION_MISMATCH")


class FailClosedTests(PreflightTestCase):
    def test_non_object_payloads_blocked(self):
        for payload in (None, [], "text", 3, {}):
            result = self.check(payload, preflight.BLOCKED)
            self.assertEqual(result["rowCount"], 0)

    def test_missing_sections_blocked(self):
        for section in ("manifest", "requirements", "rows"):
            payload = make_payload()
            del payload[section]
            self.check(payload, preflight.BLOCKED, "MISSING_FIELD")

    def test_wrong_section_types_blocked(self):
        for section, bad in (("manifest", []), ("requirements", "x"), ("rows", {"a": 1})):
            payload = make_payload()
            payload[section] = bad
            self.check(payload, preflight.BLOCKED, "INVALID_TYPE")

    def test_empty_rows_blocked(self):
        self.check(make_payload(rows=[]), preflight.BLOCKED, "EMPTY_ROWS")

    def test_non_object_row_blocked(self):
        result = self.check(make_payload(rows=[bar(), "oops", None]), preflight.BLOCKED,
                            "ROW_NOT_OBJECT")
        self.assertEqual(result["instrumentStatus"]["<unattributed>"], "BLOCKED")

    def test_missing_manifest_fields_blocked(self):
        for key in ("datasetId", "market", "currency", "interval", "timezone", "sessionCoverage",
                    "adjustmentPolicy", "availabilityMode", "universeMode", "licenseStatus"):
            payload = make_payload()
            del payload["manifest"][key]
            result = preflight.validate(payload)
            self.assertEqual(result["status"], preflight.BLOCKED, msg=key)

    def test_missing_requirement_fields_blocked(self):
        for key in ("market", "currency", "interval", "session", "cutoffAt",
                    "requirePitUniverse", "requireVintage"):
            payload = make_payload()
            del payload["requirements"][key]
            result = preflight.validate(payload)
            self.assertEqual(result["status"], preflight.BLOCKED, msg=key)

    def test_missing_row_fields_blocked(self):
        for key in ("instrumentId", "market", "currency", "session", "eventAt", "availableAt",
                    "open", "high", "low", "close", "volume"):
            row = bar()
            del row[key]
            result = preflight.validate(make_payload(rows=[row]))
            self.assertEqual(result["status"], preflight.BLOCKED, msg=key)

    def test_null_values_treated_as_missing(self):
        self.check(make_payload(rows=[bar(v=None)]), preflight.BLOCKED, "MISSING_FIELD")

    def test_invalid_enums_blocked(self):
        self.check(make_payload(manifest={"market": "JP"}), preflight.BLOCKED, "INVALID_ENUM")
        self.check(make_payload(manifest={"adjustmentPolicy": "ADJUSTED"}), preflight.BLOCKED,
                   "INVALID_ENUM")
        self.check(make_payload(manifest={"interval": "daily"}), preflight.BLOCKED, "INVALID_ENUM")

    def test_flag_types_strict(self):
        self.check(make_payload(requirements={"requireVintage": "false"}), preflight.BLOCKED,
                   "INVALID_TYPE")
        self.check(make_payload(requirements={"requirePitUniverse": 0}), preflight.BLOCKED,
                   "INVALID_TYPE")

    def test_internal_error_fails_closed_without_traceback(self):
        with mock.patch.object(preflight, "_validate", side_effect=RuntimeError("secret-token")):
            result = preflight.validate(make_payload())
        self.assertEqual(result["status"], preflight.BLOCKED)
        self.assertEqual(self.codes(result), ["INTERNAL_ERROR"])
        self.assertNotIn("secret-token", json.dumps(result))

    def test_issue_list_is_capped_but_status_is_complete(self):
        rows = [bar(inst=f"SYN-{n}", h=1.0) for n in range(preflight.MAX_ISSUES + 20)]
        result = self.check(make_payload(rows=rows), preflight.BLOCKED, "INVALID_OHLC")
        self.assertEqual(len(result["issues"]), preflight.MAX_ISSUES)
        self.assertTrue(result["issuesTruncated"])


class IntervalAndSessionTests(PreflightTestCase):
    def test_daily_data_cannot_satisfy_intraday_requirement(self):
        payload = make_payload(manifest={"interval": "1d"})
        result = self.check(payload, preflight.BLOCKED, "INTERVAL_MISMATCH")
        self.assertIn("추정하지 않는다", " ".join(i["message"] for i in result["issues"]))

    def test_weekly_data_cannot_satisfy_daily_requirement(self):
        self.check(make_payload(manifest={"interval": "1wk"}, requirements={"interval": "1d"}),
                   preflight.BLOCKED, "INTERVAL_MISMATCH")

    def test_required_session_not_covered_blocked(self):
        self.check(make_payload(requirements={"session": "PRE"}), preflight.BLOCKED,
                   "SESSION_NOT_COVERED", "REQUIRED_SESSION_NO_ROWS")

    def test_required_session_declared_but_no_rows_blocked(self):
        payload = make_payload(manifest={"sessionCoverage": ["REGULAR", "PRE"]},
                               requirements={"session": "PRE"})
        result = self.check(payload, preflight.BLOCKED, "REQUIRED_SESSION_NO_ROWS")
        self.assertNotIn("SESSION_NOT_COVERED", self.codes(result))

    def test_empty_or_missing_session_coverage_blocked(self):
        self.check(make_payload(manifest={"sessionCoverage": []}), preflight.BLOCKED,
                   "SESSION_COVERAGE_MISSING")
        self.check(make_payload(manifest={"sessionCoverage": [""]}), preflight.BLOCKED,
                   "INVALID_TYPE")

    def test_row_session_must_be_declared(self):
        row = bar()
        row["session"] = "OVERNIGHT"
        self.check(make_payload(rows=[bar(), row]), preflight.BLOCKED, "SESSION_NOT_DECLARED")

    def test_daily_spaced_rows_declared_as_minute_bars_blocked(self):
        rows = [bar(event=f"2026-03-0{d}T16:00:00-05:00", available=f"2026-03-0{d}T16:05:00-05:00")
                for d in (2, 3, 4)]
        self.check(make_payload(rows=rows), preflight.BLOCKED, "INTERVAL_LOOKS_DAILY")

    def test_bars_closer_than_interval_blocked(self):
        rows = [bar(event="2026-03-06T09:31:00-05:00", available="2026-03-06T09:31:01-05:00"),
                bar(event="2026-03-06T09:31:30-05:00", available="2026-03-06T09:31:31-05:00")]
        self.check(make_payload(rows=rows), preflight.BLOCKED, "EVENT_SPACING_BELOW_INTERVAL")


def after_bar(inst, event="2026-03-06T16:01:00-05:00", available="2026-03-06T16:01:02-05:00"):
    row = bar(inst, event, available)
    row["session"] = "AFTER"
    return row


class RequiredSessionPerInstrumentTests(PreflightTestCase):
    """요구 세션 행 존재 여부를 데이터셋 전체뿐 아니라 관측 종목마다 검사한다."""
    COVERAGE = {"sessionCoverage": ["REGULAR", "AFTER"]}

    def test_mixed_missing_instrument_blocked_and_others_stay_pass(self):
        rows = [bar("SYN-A"), after_bar("SYN-NO-REGULAR")]
        result = self.check(make_payload(rows=rows, manifest=self.COVERAGE), preflight.BLOCKED,
                            "INSTRUMENT_REQUIRED_SESSION_NO_ROWS")
        self.assertEqual(result["instrumentStatus"],
                         {"SYN-A": "PASS", "SYN-NO-REGULAR": "BLOCKED"})
        # 데이터셋 전체에는 요구 세션 행이 있으므로 전역 이슈는 없다.
        self.assertNotIn("REQUIRED_SESSION_NO_ROWS", self.codes(result))
        self.assertEqual([i["field"] for i in result["issues"]], ["rows[1].session"])
        self.assertEqual(result["rowCount"], 2)

    def test_pass_example_plus_after_only_instrument_is_blocked(self):
        # Codex 재현: pass 예시에 REGULAR,AFTER 선언 + 첫 행 복제(SYN-NO-REGULAR, AFTER)
        payload = json.loads((EXAMPLES / "pass_us_regular_1m.json").read_text(encoding="utf-8"))
        payload["manifest"]["sessionCoverage"] = ["REGULAR", "AFTER"]
        clone = copy.deepcopy(payload["rows"][0])
        clone.update(instrumentId="SYN-NO-REGULAR", session="AFTER")
        payload["rows"].append(clone)
        result = self.check(payload, preflight.BLOCKED, "INSTRUMENT_REQUIRED_SESSION_NO_ROWS")
        self.assertEqual(result["instrumentStatus"],
                         {"SYN-A": "PASS", "SYN-B": "PASS", "SYN-NO-REGULAR": "BLOCKED"})

    def test_all_instruments_missing_required_session_blocked_globally_and_each(self):
        rows = [after_bar("SYN-A"), after_bar("SYN-B")]
        result = self.check(make_payload(rows=rows, manifest=self.COVERAGE), preflight.BLOCKED,
                            "REQUIRED_SESSION_NO_ROWS", "INSTRUMENT_REQUIRED_SESSION_NO_ROWS")
        self.assertEqual(self.codes(result).count("INSTRUMENT_REQUIRED_SESSION_NO_ROWS"), 2)
        self.assertEqual(result["instrumentStatus"], {"SYN-A": "BLOCKED", "SYN-B": "BLOCKED"})

    def test_all_instruments_covered_passes_even_with_extra_sessions(self):
        rows = [bar("SYN-A"), bar("SYN-B"),
                after_bar("SYN-A"), after_bar("SYN-B", "2026-03-06T16:02:00-05:00",
                                              "2026-03-06T16:02:02-05:00")]
        result = self.check(make_payload(rows=rows, manifest=self.COVERAGE), preflight.PASS)
        self.assertEqual(result["instrumentStatus"], {"SYN-A": "PASS", "SYN-B": "PASS"})

    def test_instrument_with_required_row_in_later_position_is_covered(self):
        # AFTER 행이 REGULAR 행보다 먼저 나와도 종목 단위로 집계한다.
        rows = [after_bar("SYN-A"), bar("SYN-A")]
        self.check(make_payload(rows=rows, manifest=self.COVERAGE), preflight.PASS)

    def test_unattributed_row_does_not_create_instrument_issue(self):
        row = bar()
        del row["instrumentId"]
        result = self.check(make_payload(rows=[bar("SYN-A"), row]), preflight.BLOCKED,
                            "MISSING_FIELD")
        self.assertNotIn("INSTRUMENT_REQUIRED_SESSION_NO_ROWS", self.codes(result))


class MarketCurrencyTests(PreflightTestCase):
    def test_row_currency_mismatch_blocked(self):
        row = bar()
        row["currency"] = "KRW"
        self.check(make_payload(rows=[row]), preflight.BLOCKED, "CURRENCY_MISMATCH")

    def test_manifest_requirement_currency_mismatch_blocked(self):
        self.check(make_payload(requirements={"currency": "KRW"}), preflight.BLOCKED,
                   "CURRENCY_MISMATCH")

    def test_market_mismatch_blocked(self):
        row = bar()
        row["market"] = "KR"
        self.check(make_payload(rows=[row]), preflight.BLOCKED, "MARKET_MISMATCH")
        self.check(make_payload(requirements={"market": "KR"}), preflight.BLOCKED,
                   "MARKET_MISMATCH")

    def test_malformed_currency_code_blocked(self):
        self.check(make_payload(manifest={"currency": "usd"}), preflight.BLOCKED, "INVALID_VALUE")


class PolicyTests(PreflightTestCase):
    def test_unknown_adjustment_blocked_in_strict_default(self):
        self.check(make_payload(manifest={"adjustmentPolicy": "UNKNOWN"}), preflight.BLOCKED,
                   "ADJUSTMENT_POLICY_UNKNOWN")

    def test_unknown_or_fixed_lag_availability_blocked_in_strict(self):
        self.check(make_payload(manifest={"availabilityMode": "UNKNOWN"}), preflight.BLOCKED,
                   "AVAILABILITY_MODE_UNKNOWN")
        self.check(make_payload(manifest={"availabilityMode": "FIXED_LAG_APPROXIMATION"}),
                   preflight.BLOCKED, "AVAILABILITY_FIXED_LAG_APPROXIMATION")

    def test_reconstructed_availability_is_not_blocked(self):
        self.check(make_payload(manifest={"availabilityMode": "PUBLICATION_RECONSTRUCTED"}),
                   preflight.PASS)

    def test_license_unknown_is_warn_not_block(self):
        result = self.check(make_payload(manifest={"licenseStatus": "UNKNOWN"}), preflight.WARN,
                            "LICENSE_UNKNOWN")
        self.assertEqual(result["instrumentStatus"], {"SYN-A": "WARN", "SYN-B": "WARN"})

    def test_pit_universe_required_but_not_declared_blocked(self):
        for mode in ("FIXED_DIAGNOSTIC_SET", "UNKNOWN"):
            self.check(make_payload(manifest={"universeMode": mode},
                                    requirements={"requirePitUniverse": True}),
                       preflight.BLOCKED, "PIT_UNIVERSE_REQUIRED")
        self.check(make_payload(manifest={"universeMode": "POINT_IN_TIME"},
                                requirements={"requirePitUniverse": True}), preflight.PASS)

    def test_unknown_universe_warns_when_pit_not_required(self):
        self.check(make_payload(manifest={"universeMode": "UNKNOWN"}), preflight.WARN,
                   "UNIVERSE_MODE_UNKNOWN")

    def test_require_vintage_without_row_vintage_blocked(self):
        result = self.check(make_payload(requirements={"requireVintage": True}),
                            preflight.BLOCKED, "VINTAGE_MISSING", "VINTAGE_EVIDENCE_UNDECLARED")
        self.assertEqual(self.codes(result).count("VINTAGE_MISSING"), 4)

    def test_require_vintage_with_vintage_and_policy_passes(self):
        rows = [bar(vintageAt="2026-03-06T09:31:05-05:00")]
        payload = make_payload(rows=rows, requirements={"requireVintage": True},
                               manifest={"vintagePolicy": "synthetic-vintage-log"})
        self.check(payload, preflight.PASS)

    def test_require_vintage_with_row_vintage_but_no_manifest_evidence_blocked(self):
        # 행마다 vintageAt 이 있어도 vintagePolicy/evidenceRefs 근거가 없으면 차단한다.
        rows = [bar(vintageAt="2026-03-06T09:31:05-05:00")]
        result = self.check(make_payload(rows=rows, requirements={"requireVintage": True}),
                            preflight.BLOCKED, "VINTAGE_EVIDENCE_UNDECLARED")
        self.assertNotIn("VINTAGE_MISSING", self.codes(result))

    def test_require_vintage_evidence_refs_alone_satisfy_evidence(self):
        rows = [bar(vintageAt="2026-03-06T09:31:05-05:00")]
        payload = make_payload(rows=rows, requirements={"requireVintage": True},
                               manifest={"evidenceRefs": ["synthetic-evidence-1"]})
        self.check(payload, preflight.PASS)

    def test_require_vintage_empty_evidence_refs_is_not_evidence(self):
        rows = [bar(vintageAt="2026-03-06T09:31:05-05:00")]
        payload = make_payload(rows=rows, requirements={"requireVintage": True},
                               manifest={"evidenceRefs": []})
        self.check(payload, preflight.BLOCKED, "VINTAGE_EVIDENCE_UNDECLARED")

    def test_evidence_without_row_vintage_is_still_blocked(self):
        payload = make_payload(requirements={"requireVintage": True},
                               manifest={"vintagePolicy": "synthetic-vintage-log"})
        self.check(payload, preflight.BLOCKED, "VINTAGE_MISSING")

    def test_bad_optional_evidence_types_blocked(self):
        self.check(make_payload(manifest={"evidenceRefs": "one"}), preflight.BLOCKED,
                   "INVALID_TYPE")
        self.check(make_payload(manifest={"vintagePolicy": ""}), preflight.BLOCKED,
                   "INVALID_TYPE")

    def test_unverifiable_timezone_only_warns(self):
        self.check(make_payload(manifest={"timezone": "Not/AZone"}), preflight.WARN,
                   "TIMEZONE_UNVERIFIED")


class TimestampTests(PreflightTestCase):
    def test_future_event_available_vintage_blocked(self):
        cases = {
            "FUTURE_EVENT_AT": bar(event="2026-03-10T00:00:01Z", available="2026-03-10T00:00:01Z"),
            "FUTURE_AVAILABLE_AT": bar(available="2026-03-10T00:00:01Z"),
            "FUTURE_VINTAGE_AT": bar(vintageAt="2026-03-10T00:00:01Z"),
        }
        for code, row in cases.items():
            result = self.check(make_payload(rows=[row]), preflight.BLOCKED, code)
            self.assertIn(code, self.codes(result))

    def test_future_check_uses_utc_across_offsets(self):
        # 2026-03-10T09:00:01+09:00 == 2026-03-10T00:00:01Z -> 미래
        row = bar(available="2026-03-10T09:00:01+09:00")
        self.check(make_payload(rows=[row]), preflight.BLOCKED, "FUTURE_AVAILABLE_AT")
        # 2026-03-10T09:00:00+09:00 == cutoff -> 허용
        row = bar(available="2026-03-10T09:00:00+09:00")
        self.check(make_payload(rows=[row]), preflight.PASS)

    def test_available_before_event_blocked(self):
        row = bar(event="2026-03-06T09:31:00-05:00", available="2026-03-06T09:30:59-05:00")
        self.check(make_payload(rows=[row]), preflight.BLOCKED, "AVAILABLE_BEFORE_EVENT")

    def test_available_before_event_compared_in_utc(self):
        # 벽시계 숫자는 availableAt 이 더 크지만 UTC 로는 더 이르다.
        row = bar(event="2026-03-09T09:31:00-04:00", available="2026-03-09T09:30:00-04:00")
        self.check(make_payload(rows=[row]), preflight.BLOCKED, "AVAILABLE_BEFORE_EVENT")
        row = bar(event="2026-03-09T13:31:00Z", available="2026-03-09T14:00:00+01:00")
        self.check(make_payload(rows=[row]), preflight.BLOCKED, "AVAILABLE_BEFORE_EVENT")

    def test_naive_and_invalid_timestamps_blocked(self):
        self.check(make_payload(rows=[bar(event="2026-03-06T09:31:00")]), preflight.BLOCKED,
                   "NAIVE_TIMESTAMP")
        self.check(make_payload(rows=[bar(event="2026-03-06")]), preflight.BLOCKED,
                   "NAIVE_TIMESTAMP")
        self.check(make_payload(rows=[bar(event="not-a-time")]), preflight.BLOCKED,
                   "INVALID_TIMESTAMP")
        self.check(make_payload(rows=[bar(event=1772807460)]), preflight.BLOCKED, "INVALID_TYPE")
        self.check(make_payload(requirements={"cutoffAt": "2026-03-10T00:00:00"}),
                   preflight.BLOCKED, "NAIVE_TIMESTAMP")

    def test_z_suffix_accepted(self):
        row = bar(event="2026-03-06T14:31:00Z", available="2026-03-06T14:31:02Z")
        self.check(make_payload(rows=[row]), preflight.PASS)


class DstTests(PreflightTestCase):
    def test_rows_across_dst_change_ordered_by_utc(self):
        # 2026-03-08 미국 DST 시작: -05:00 -> -04:00
        rows = [
            bar(event="2026-03-06T15:59:00-05:00", available="2026-03-06T15:59:02-05:00"),  # 20:59Z
            bar(event="2026-03-09T09:31:00-04:00", available="2026-03-09T09:31:02-04:00"),  # 13:31Z
        ]
        self.check(make_payload(rows=rows), preflight.PASS)

    def test_lexical_order_differs_from_utc_order(self):
        # 문자열로는 09:31 > 08:45 라서 비정렬처럼 보이지만 UTC 로는 13:31Z < 13:45Z.
        rows = [
            bar(event="2026-03-09T09:31:00-04:00", available="2026-03-09T09:31:02-04:00"),
            bar(event="2026-03-09T08:45:00-05:00", available="2026-03-09T08:45:02-05:00"),
        ]
        self.check(make_payload(rows=rows, manifest={"interval": "5m"},
                                requirements={"interval": "5m"}), preflight.PASS)
        # 반대로 UTC 로는 역순인 파일은 문자열 순서가 맞아 보여도 차단한다.
        rows.reverse()
        self.check(make_payload(rows=rows, manifest={"interval": "5m"},
                                requirements={"interval": "5m"}), preflight.BLOCKED, "OUT_OF_ORDER")

    def test_same_instant_with_different_offsets_is_duplicate(self):
        rows = [
            bar(event="2026-03-09T09:31:00-04:00", available="2026-03-09T09:31:02-04:00"),
            bar(event="2026-03-09T13:31:00Z", available="2026-03-09T13:31:02Z"),
        ]
        self.check(make_payload(rows=rows), preflight.BLOCKED, "DUPLICATE_BAR")

    def test_fall_back_ambiguous_wall_clock_times_are_distinct(self):
        # 2026-11-01 미국 DST 종료: 벽시계 01:30 이 -04:00 과 -05:00 로 두 번 존재한다.
        rows = [
            bar(event="2026-11-01T01:30:00-04:00", available="2026-11-01T01:30:02-04:00"),  # 05:30Z
            bar(event="2026-11-01T01:30:00-05:00", available="2026-11-01T01:30:02-05:00"),  # 06:30Z
        ]
        payload = make_payload(rows=rows, requirements={"cutoffAt": "2026-11-02T00:00:00Z"})
        self.check(payload, preflight.PASS)

    def test_kr_offset_data_compared_against_us_cutoff(self):
        payload = make_payload(
            manifest={"market": "KR", "currency": "KRW", "timezone": "Asia/Seoul",
                      "sessionCoverage": ["KRX_REGULAR"]},
            requirements={"market": "KR", "currency": "KRW", "session": "KRX_REGULAR",
                          "cutoffAt": "2026-03-09T20:00:00-04:00"},
            rows=[dict(bar(event="2026-03-09T09:01:00+09:00", available="2026-03-09T09:01:02+09:00"),
                       market="KR", currency="KRW", session="KRX_REGULAR")])
        self.check(payload, preflight.PASS)


class DuplicateAndOrderTests(PreflightTestCase):
    def test_duplicate_instrument_session_event_blocked(self):
        self.check(make_payload(rows=[bar(), bar()]), preflight.BLOCKED, "DUPLICATE_BAR")

    def test_same_event_different_instrument_or_session_is_not_duplicate(self):
        other_session = bar()
        other_session["session"] = "PRE"
        payload = make_payload(rows=[bar(), bar(inst="SYN-B"), other_session],
                               manifest={"sessionCoverage": ["REGULAR", "PRE"]})
        self.check(payload, preflight.PASS)

    def test_unsorted_rows_blocked(self):
        rows = [bar(event="2026-03-06T09:32:00-05:00", available="2026-03-06T09:32:02-05:00"),
                bar(event="2026-03-06T09:31:00-05:00", available="2026-03-06T09:31:02-05:00")]
        self.check(make_payload(rows=rows), preflight.BLOCKED, "OUT_OF_ORDER")

    def test_interleaved_multi_symbol_rows_sorted_per_series(self):
        rows = [bar("SYN-A", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00"),
                bar("SYN-B", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00"),
                bar("SYN-A", "2026-03-06T09:33:00-05:00", "2026-03-06T09:33:02-05:00"),
                bar("SYN-B", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00")]
        self.check(make_payload(rows=rows), preflight.PASS)

    def test_out_of_order_only_flagged_for_offending_series(self):
        rows = [bar("SYN-A", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00"),
                bar("SYN-B", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00"),
                bar("SYN-A", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00"),
                bar("SYN-B", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00")]
        result = self.check(make_payload(rows=rows), preflight.BLOCKED, "OUT_OF_ORDER")
        self.assertEqual(result["instrumentStatus"], {"SYN-A": "BLOCKED", "SYN-B": "PASS"})


class NumericTests(PreflightTestCase):
    def test_invalid_ohlc_blocked(self):
        cases = [
            bar(o=100, h=99, lo=98, c=99),      # high < open
            bar(o=100, h=100.5, lo=99, c=101),  # high < close
            bar(o=100, h=100.5, lo=100.2, c=100.3),  # low > open
            bar(o=100, h=100.5, lo=99, c=98),   # low > close
            bar(o=100, h=99, lo=101, c=100),    # high < low
        ]
        for row in cases:
            self.check(make_payload(rows=[row]), preflight.BLOCKED, "INVALID_OHLC")

    def test_flat_bar_is_valid_ohlc(self):
        self.check(make_payload(rows=[bar(o=10, h=10, lo=10, c=10)]), preflight.PASS)

    def test_non_positive_price_blocked(self):
        for key in ("o", "h", "lo", "c"):
            for value in (0, -1.5):
                result = self.check(make_payload(rows=[bar(**{key: value})]), preflight.BLOCKED,
                                    "NON_POSITIVE_PRICE")
                self.assertNotIn("INTERNAL_ERROR", self.codes(result))

    def test_negative_volume_blocked(self):
        self.check(make_payload(rows=[bar(v=-1)]), preflight.BLOCKED, "NEGATIVE_VOLUME")

    def test_nan_and_infinity_blocked(self):
        for value in (math.nan, math.inf, -math.inf):
            self.check(make_payload(rows=[bar(c=value)]), preflight.BLOCKED, "NON_FINITE_NUMBER")
            self.check(make_payload(rows=[bar(v=value)]), preflight.BLOCKED, "NON_FINITE_NUMBER")

    def test_integer_too_large_for_float_blocked_without_crash(self):
        result = self.check(make_payload(rows=[bar(h=10 ** 400)]), preflight.BLOCKED,
                            "NON_FINITE_NUMBER")
        self.assertNotIn("INTERNAL_ERROR", self.codes(result))

    def test_bool_and_string_numbers_rejected(self):
        for value in (True, False, "100.5", None, [1], {"v": 1}):
            result = preflight.validate(make_payload(rows=[bar(o=value)]))
            self.assertEqual(result["status"], preflight.BLOCKED, msg=repr(value))
        self.check(make_payload(rows=[bar(v=True)]), preflight.BLOCKED, "INVALID_TYPE")


class PartialFailureTests(PreflightTestCase):
    def test_one_bad_symbol_does_not_fail_the_others(self):
        rows = [bar("SYN-A", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00"),
                bar("SYN-B", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00", h=1.0),
                bar("SYN-C", "2026-03-06T09:31:00-05:00", "2026-03-06T09:31:02-05:00"),
                bar("SYN-A", "2026-03-06T09:32:00-05:00", "2026-03-06T09:32:02-05:00")]
        result = self.check(make_payload(rows=rows), preflight.BLOCKED, "INVALID_OHLC")
        self.assertEqual(result["instrumentStatus"],
                         {"SYN-A": "PASS", "SYN-B": "BLOCKED", "SYN-C": "PASS"})
        self.assertEqual(result["rowCount"], 4)
        self.assertEqual([i["field"] for i in result["issues"]], ["rows[1].high"])

    def test_dataset_level_block_marks_every_instrument(self):
        result = self.check(make_payload(manifest={"adjustmentPolicy": "UNKNOWN"}),
                            preflight.BLOCKED)
        self.assertEqual(set(result["instrumentStatus"].values()), {"BLOCKED"})

    def test_all_defects_are_reported_not_just_the_first(self):
        row = bar(h=1.0, v=-1, available="2026-03-10T00:00:01Z")
        result = self.check(make_payload(rows=[row]), preflight.BLOCKED)
        self.assertLessEqual({"INVALID_OHLC", "NEGATIVE_VOLUME", "FUTURE_AVAILABLE_AT"},
                             set(self.codes(result)))

    def test_messages_do_not_echo_input_values(self):
        row = bar(inst="SYN-A", event="secret-token-value")
        row["market"] = "secret-market-value"
        text = json.dumps(preflight.validate(make_payload(rows=[row])), ensure_ascii=False)
        self.assertNotIn("secret-token-value", text)
        self.assertNotIn("secret-market-value", text)


class CliTests(unittest.TestCase):
    def run_cli(self, *args):
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1")
        proc = subprocess.run([sys.executable, str(CLI), *map(str, args)], capture_output=True,
                              text=True, env=env, timeout=30)
        return proc

    def run_payload(self, text):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "input.json"
            path.write_text(text, encoding="utf-8")
            return self.run_cli(path)

    def assert_structured_blocked(self, proc, code):
        self.assertEqual(proc.returncode, 2, msg=proc.stderr)
        result = json.loads(proc.stdout)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertEqual(result["rowCount"], 0)
        self.assertEqual(result["issues"][0]["code"], code)
        self.assertNotIn("Traceback", proc.stdout + proc.stderr)
        return result

    def test_examples_produce_expected_status_and_exit_code(self):
        expected = {
            "pass_us_regular_1m.json": ("PASS", 0),
            "warn_kr_license_unknown_1m.json": ("WARN", 0),
            "blocked_daily_for_intraday.json": ("BLOCKED", 2),
            "blocked_partial_failure_multi_symbol.json": ("BLOCKED", 2),
        }
        self.assertEqual({p.name for p in EXAMPLES.glob("*.json")}, set(expected))
        for name, (status, code) in expected.items():
            proc = self.run_cli(EXAMPLES / name)
            self.assertEqual(proc.returncode, code, msg=name + proc.stderr)
            self.assertEqual(json.loads(proc.stdout)["status"], status, msg=name)
            self.assertEqual(proc.stderr, "")

    def test_partial_failure_example_isolates_bad_symbol(self):
        result = json.loads(self.run_cli(EXAMPLES / "blocked_partial_failure_multi_symbol.json").stdout)
        self.assertEqual(result["instrumentStatus"], {"SYN-A": "PASS", "SYN-B": "BLOCKED"})

    def test_daily_example_is_not_accepted_as_intraday(self):
        result = json.loads(self.run_cli(EXAMPLES / "blocked_daily_for_intraday.json").stdout)
        self.assertIn("INTERVAL_MISMATCH", [i["code"] for i in result["issues"]])

    def test_malformed_json_is_structured_error(self):
        proc = self.run_payload('{"manifest": {"datasetId": "secret-token-value",')
        self.assert_structured_blocked(proc, "MALFORMED_JSON")
        self.assertNotIn("secret-token-value", proc.stdout)

    def test_empty_file_is_structured_error(self):
        self.assert_structured_blocked(self.run_payload(""), "MALFORMED_JSON")

    def test_deeply_nested_json_is_structured_error(self):
        self.assert_structured_blocked(self.run_payload("[" * 100000), "MALFORMED_JSON")

    def test_missing_file_is_structured_error(self):
        proc = self.run_cli("/nonexistent-dir/none.json")
        self.assert_structured_blocked(proc, "INPUT_UNREADABLE")
        self.assertNotIn("nonexistent-dir", proc.stdout)

    def test_directory_and_binary_input_are_structured_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assert_structured_blocked(self.run_cli(tmp), "INPUT_UNREADABLE")
            binary = Path(tmp) / "bin.json"
            binary.write_bytes(b"\xff\xfe\x00\x80")
            self.assert_structured_blocked(self.run_cli(binary), "INPUT_UNREADABLE")

    def test_usage_error_is_structured(self):
        self.assert_structured_blocked(self.run_cli(), "USAGE_ERROR")
        self.assert_structured_blocked(self.run_cli("a.json", "b.json"), "USAGE_ERROR")

    def test_non_object_json_top_level_is_blocked(self):
        self.assert_structured_blocked(self.run_payload("[1, 2]"), "INVALID_TYPE")

    def test_nan_literal_in_json_is_blocked_not_crash(self):
        payload = make_payload(rows=[bar()])
        text = json.dumps(payload).replace('"close": 100.2', '"close": NaN')
        proc = self.run_payload(text)
        self.assertEqual(proc.returncode, 2)
        result = json.loads(proc.stdout)
        self.assertIn("NON_FINITE_NUMBER", [i["code"] for i in result["issues"]])
        self.assertEqual(result["rowCount"], 1)


if __name__ == "__main__":
    unittest.main()

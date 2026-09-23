"""Synthetic trace tests; no market data or investment signal is asserted."""
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import decision_trace  # noqa: E402
from test_preflight import bar, make_payload  # noqa: E402


class DecisionTraceTests(unittest.TestCase):
    def setUp(self):
        row = bar(inst="SYN-A", event="2026-03-06T09:31:00-05:00",
                  available="2026-03-06T09:31:02-05:00",
                  barStartAt="2026-03-06T09:30:00-05:00",
                  sourceTimestampAt="2026-03-06T09:30:00-05:00",
                  sourceTimestampMeaning="BAR_OPEN",
                  receivedAt="2026-03-06T09:31:02-05:00")
        self.dataset = make_payload(rows=[row],
                                    manifest={"sourceProvider": "SYNTHETIC",
                                              "sourceAdjustmentMode": "RAW",
                                              "adjustmentEvidenceRef": "request-1"})
        state = {"holdings": [{"instrumentId": "SYN-A", "quantity": "5"}],
                 "cashByCurrency": {"USD": "1000"}, "reservations": []}
        macro_payload = {"syntheticIndex": "100"}
        technical_payload = {"syntheticDerived": "1"}
        self.trace = {
            "decision": {"instrumentId": "SYN-A", "session": "REGULAR",
                         "decisionAt": "2026-03-06T09:32:00-05:00",
                         "portfolioKnownAt": "2026-03-06T09:31:59-05:00",
                         "barIndices": [0], "requiresPitUniverse": False,
                         "modelInputIds": ["bar-0", "macro-0", "technical-0"]},
            "portfolioSnapshot": {
                "snapshotId": "snapshot-1", "capturedAt": "2026-03-06T09:31:59-05:00",
                "effectiveAt": "2026-03-06T09:31:30-05:00",
                "corporateActionsThrough": "2026-03-06T09:30:00-05:00",
                "sourceRefs": ["synthetic-broker-record"], "state": state,
                "contentSha256": decision_trace.digest(state),
            },
            "features": [
                {"featureId": "bar-0", "kind": "MARKET_BAR", "barIndex": 0,
                 "sourceEventAt": row["eventAt"],
                 "availableAt": row["availableAt"], "sourceRef": "capture-1",
                 "transformVersion": "raw-1", "contentSha256": decision_trace.digest(row)},
                {"featureId": "macro-0", "kind": "MACRO",
                 "sourceEventAt": "2026-03-05T16:00:00-05:00",
                 "availableAt": "2026-03-06T09:20:00-05:00",
                 "vintageAt": "2026-03-06T09:19:00-05:00",
                 "sourceRef": "synthetic-vintage-1", "transformVersion": "raw-1",
                 "payload": macro_payload,
                 "contentSha256": decision_trace.digest(macro_payload)},
                {"featureId": "technical-0", "kind": "TECHNICAL",
                 "sourceEventAt": row["eventAt"],
                 "availableAt": row["availableAt"], "sourceRef": "derived-1",
                 "transformVersion": "calc-1", "dependsOn": ["bar-0", "macro-0"],
                 "payload": technical_payload,
                 "contentSha256": decision_trace.digest(technical_payload)},
            ],
        }

    def check(self, status, code=None):
        result = decision_trace.validate(self.dataset, self.trace)
        self.assertEqual(result["status"], status, result["issues"])
        if code:
            self.assertIn(code, {item["code"] for item in result["issues"]})

    def test_complete_trace_passes_without_mutation(self):
        before = copy.deepcopy((self.dataset, self.trace))
        self.check("PASS")
        self.assertEqual((self.dataset, self.trace), before)

    def test_changed_portfolio_state_is_detected(self):
        self.trace["portfolioSnapshot"]["state"]["holdings"][0]["quantity"] = "6"
        self.check("BLOCKED", "PORTFOLIO_HASH_MISMATCH")

    def test_future_portfolio_snapshot_blocks(self):
        self.trace["portfolioSnapshot"]["capturedAt"] = "2026-03-06T09:33:00-05:00"
        self.check("BLOCKED", "PORTFOLIO_TIME_MISMATCH")

    def test_missing_cash_state_blocks(self):
        del self.trace["portfolioSnapshot"]["state"]["cashByCurrency"]
        self.check("BLOCKED", "PORTFOLIO_STATE_INCOMPLETE")

    def test_portfolio_effective_time_after_capture_blocks(self):
        self.trace["portfolioSnapshot"]["effectiveAt"] = "2026-03-06T09:32:00-05:00"
        self.check("BLOCKED", "PORTFOLIO_TIME_MISMATCH")

    def test_future_macro_revision_blocks(self):
        self.trace["features"][1]["vintageAt"] = "2026-03-06T09:33:00-05:00"
        self.check("BLOCKED", "FUTURE_FEATURE")

    def test_missing_macro_vintage_blocks(self):
        del self.trace["features"][1]["vintageAt"]
        self.check("BLOCKED", "FEATURE_TIME_MISSING")

    def test_future_source_event_blocks(self):
        self.trace["features"][1]["sourceEventAt"] = "2026-03-06T09:33:00-05:00"
        self.check("BLOCKED", "FUTURE_FEATURE")

    def test_changed_feature_content_is_detected(self):
        self.trace["features"][1]["payload"]["syntheticIndex"] = "101"
        self.check("BLOCKED", "FEATURE_HASH_MISMATCH")

    def test_unlisted_model_input_blocks(self):
        self.trace["decision"]["modelInputIds"].remove("macro-0")
        self.check("BLOCKED", "MODEL_INPUT_SET_MISMATCH")

    def test_bar_hash_mismatch_blocks(self):
        self.dataset["rows"][0]["close"] = 101.0
        self.check("BLOCKED", "FEATURE_HASH_MISMATCH")

    def test_future_dependency_blocks(self):
        self.trace["features"][2]["availableAt"] = "2026-03-06T09:20:00-05:00"
        self.trace["features"][2]["sourceEventAt"] = "2026-03-06T09:19:00-05:00"
        self.check("BLOCKED", "FEATURE_DEPENDENCY_FUTURE")

    def test_dependency_cycle_blocks(self):
        self.trace["features"][0]["dependsOn"] = ["technical-0"]
        self.check("BLOCKED", "FEATURE_DEPENDENCY_INVALID")

    def test_malformed_kind_blocks_without_exception(self):
        self.trace["features"][1]["kind"] = []
        self.check("BLOCKED", "FEATURE_PROVENANCE_MISSING")


if __name__ == "__main__":
    unittest.main()

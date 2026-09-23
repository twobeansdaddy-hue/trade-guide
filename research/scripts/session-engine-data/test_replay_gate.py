"""Synthetic point-in-time replay checks; no trade or performance simulation."""
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import replay_gate  # noqa: E402
from test_preflight import bar, make_payload  # noqa: E402


class ReplayGateTests(unittest.TestCase):
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
        self.decision = {"instrumentId": "SYN-A", "session": "REGULAR",
                         "decisionAt": "2026-03-06T09:32:00-05:00",
                         "portfolioKnownAt": "2026-03-06T09:31:59-05:00",
                         "barIndices": [0], "requiresPitUniverse": False}

    def check(self, status, code=None):
        result = replay_gate.validate(self.dataset, self.decision)
        self.assertEqual(result["status"], status, result["issues"])
        if code:
            self.assertIn(code, {issue["code"] for issue in result["issues"]})
        return result

    def test_known_bar_and_portfolio_pass(self):
        before = copy.deepcopy((self.dataset, self.decision))
        self.check("PASS")
        self.assertEqual((self.dataset, self.decision), before)

    def test_future_response_cannot_be_feature(self):
        self.decision["decisionAt"] = "2026-03-06T09:31:01-05:00"
        self.decision["portfolioKnownAt"] = "2026-03-06T09:31:00-05:00"
        self.check("BLOCKED", "FUTURE_AVAILABLE_AT")

    def test_future_portfolio_snapshot_blocks(self):
        self.decision["portfolioKnownAt"] = "2026-03-06T09:33:00-05:00"
        self.check("BLOCKED", "FUTURE_PORTFOLIO_STATE")

    def test_missing_actual_receipt_blocks(self):
        del self.dataset["rows"][0]["receivedAt"]
        self.check("BLOCKED", "PROVENANCE_MISSING")

    def test_candidate_needs_pit_universe(self):
        self.decision["requiresPitUniverse"] = True
        self.check("BLOCKED", "PIT_UNIVERSE_REQUIRED")

    def test_other_instrument_reference_blocks(self):
        self.decision["instrumentId"] = "SYN-B"
        self.check("BLOCKED", "BAR_IDENTITY_MISMATCH")

    def test_bad_reference_cannot_select_future_or_other_bar(self):
        self.decision["barIndices"] = [1]
        self.check("BLOCKED", "INVALID_BAR_REFERENCES")

    def test_unreferenced_future_row_does_not_invalidate_prior_decision(self):
        future = copy.deepcopy(self.dataset["rows"][0])
        future["eventAt"] = "2026-03-09T09:31:00-04:00"
        future["barStartAt"] = "2026-03-09T09:30:00-04:00"
        future["sourceTimestampAt"] = future["barStartAt"]
        future["availableAt"] = "2026-03-09T09:31:02-04:00"
        future["receivedAt"] = future["availableAt"]
        self.dataset["rows"].append(future)
        self.check("PASS")


if __name__ == "__main__":
    unittest.main()

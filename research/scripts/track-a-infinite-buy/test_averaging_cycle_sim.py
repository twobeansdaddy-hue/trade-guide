#!/usr/bin/env python3
"""averaging_cycle_sim 수기 계산 검증. 실행: python3 test_averaging_cycle_sim.py"""
import os
import sys
import unittest
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from averaging_cycle_sim import Bars, Params, sim_ac, sim_buy_hold  # noqa: E402


def make_bars(rows):
    """rows: (open, high, low, close). 평일 연속 날짜."""
    dates, d = [], date(2020, 1, 6)
    for _ in rows:
        while d.weekday() >= 5:
            d += timedelta(days=1)
        dates.append(d)
        d += timedelta(days=1)
    return Bars(dates, [r[0] for r in rows], [r[1] for r in rows],
                [r[2] for r in rows], [r[3] for r in rows])


def flat(px, hi=None):
    return (px, hi if hi is not None else px, px, px)


P = dict(slip=0.0, fill="opt", reverse=True)


class AveragingCycleTest(unittest.TestCase):
    def test_second_day_front_half_buys_full_unit_when_below_average(self):
        # N=20: 첫날 1/20 매수. 둘째날 종가=평단이면 절반+절반=1회분 추가, T=2.
        B = make_bars([flat(100), flat(100)])
        r = sim_ac(B, 0, 1, Params(n_splits=20, s0=0.15, **P))
        # 첫날 0.05, 둘째날 unit=0.95/19=0.05 -> 지출 합 0.10, 현금 0.90, 평가액 그대로 1.0
        self.assertAlmostEqual(r["final"], 1.0, places=9)
        self.assertEqual(len(r["cycles"]), 1)
        self.assertTrue(r["cycles"][0]["open"])

    def test_quarter_and_target_sell_close_cycle_with_hand_computed_cash(self):
        # 첫날 100 매수(shares=0.0005). 둘째날 종가 120>=기준선 113.5 -> 1/4를 120에,
        # 고가 121>=목표 115 -> 나머지 3/4를 max(115, 시가100)=115에 매도.
        B = make_bars([flat(100), (100, 121, 100, 120)])
        r = sim_ac(B, 0, 1, Params(n_splits=20, s0=0.15, **P))
        expected = 0.95 + 0.0005 * 0.25 * 120 + 0.0005 * 0.75 * 115
        self.assertAlmostEqual(r["final"], expected, places=12)
        self.assertEqual(len(r["cycles"]), 1)
        self.assertFalse(r["cycles"][0]["open"])
        self.assertAlmostEqual(r["cycles"][0]["ret"], expected - 1, places=12)

    def test_exhaustion_enters_reverse_then_sells_two_over_n_on_first_day(self):
        # N=4, s0=0.2, 종가 100,90,80,70(연속 하락) 후 60. 손계산:
        # d0 shares=.0025 avg100 T1 | d1 .25 추가(@90) T2 | d2 .25 추가(@80) T3 | d3 .25 추가(@70) T4>3 -> 리버스
        # d4: 무조건 매도 2/N=0.5, T=4*0.5=2
        prices = [100, 90, 80, 70, 60]
        B = make_bars([flat(p) for p in prices])
        p = Params(n_splits=4, s0=0.2, **P)
        r = sim_ac(B, 0, 4, p)
        shares_total = 0.25 / 100 + 0.25 / 90 + 0.25 / 80 + 0.25 / 70
        expected_cash = 0.5 * shares_total * 60
        expected_final = expected_cash + 0.5 * shares_total * 60
        self.assertAlmostEqual(r["final"], expected_final, places=12)
        self.assertEqual(r["exhaust"], 1)
        self.assertEqual(r["rev_days"], 1)

    def test_no_reverse_holds_and_stops_buying_after_exhaustion(self):
        prices = [100, 90, 80, 70, 60, 50]
        B = make_bars([flat(p) for p in prices])
        p = Params(n_splits=4, s0=0.2, slip=0.0, fill="opt", reverse=False)
        r = sim_ac(B, 0, 5, p)
        shares_total = 0.25 / 100 + 0.25 / 90 + 0.25 / 80 + 0.25 / 70
        self.assertAlmostEqual(r["final"], shares_total * 50, places=12)
        self.assertEqual(r["exhaust"], 0)

    def test_buy_and_hold_matches_price_ratio_with_slippage(self):
        B = make_bars([flat(100), flat(150)])
        r = sim_buy_hold(B, 0, 1, slip=0.0)
        self.assertAlmostEqual(r["final"], 1.5, places=12)
        self.assertAlmostEqual(r["mdd"], 0.0, places=12)

    def test_tax_is_22_percent_of_positive_realized_gain(self):
        B = make_bars([flat(100), (100, 121, 100, 120)])
        r = sim_ac(B, 0, 1, Params(n_splits=20, s0=0.15, **P))
        gain = 0.0005 * 0.25 * (120 - 100) + 0.0005 * 0.75 * (115 - 100)
        self.assertAlmostEqual(r["final"] - r["final_after_tax"], 0.22 * gain, places=12)


if __name__ == "__main__":
    unittest.main(verbosity=2)

#!/usr/bin/env python3
"""momentum_engine.py 단위 테스트 (표준 라이브러리 unittest만 사용).

이 테스트는 `research/reports/track-b-cross-sectional-momentum-validation.md`가 기록한
Java `CrossSectionalMomentumBacktestEngine` 단위 테스트의 핵심 성질(형성기간 수익률 정확성,
미래 가격이 과거 리밸런싱에 영향을 주지 않음, 비용 있는 결과가 무비용 결과보다 높지 않음,
중복 날짜/비정렬/이력부족/결측 데이터 품질 정책)을 이 Python 재구현 코어에서도 독립적으로
재현·검증한다. Java 코드와 동일한 소스가 아니라 "동등한 보호 장치"임을 명시한다
(작업 계약상 src/**는 이 작업에서 수정·실행할 수 없음).

실행: python3 -m unittest research.scripts.track-b-walk-forward.test_momentum_engine -v
      (또는 이 디렉터리에서 `python3 -m unittest test_momentum_engine -v`)
"""
import datetime
import unittest

import momentum_engine as me


def weekly_dates(start: str, n: int) -> list:
    d0 = datetime.date.fromisoformat(start)
    return [(d0 + datetime.timedelta(weeks=i)).isoformat() for i in range(n)]


def make_series(dates, prices):
    return {d: p for d, p in zip(dates, prices)}


class FormationReturnTest(unittest.TestCase):
    def test_excludes_last_four_weeks_and_uses_52_week_lookback(self):
        dates = weekly_dates("2020-01-06", 60)
        prices = [100.0] * 60
        # 52주 전 가격 = 100, 4주 전 가격 = 120 -> +20%
        prices[59 - 52] = 100.0
        prices[59 - 4] = 120.0
        series = make_series(dates, prices)
        fr = me.formation_return(dates, series, dates[59])
        self.assertAlmostEqual(fr, 20.0, places=6)

    def test_returns_none_when_history_shorter_than_53_weeks(self):
        dates = weekly_dates("2020-01-06", 52)  # 52 rows -> idx 51 < FORMATION_WEEKS(52)
        prices = [100.0] * 52
        series = make_series(dates, prices)
        fr = me.formation_return(dates, series, dates[-1])
        self.assertIsNone(fr)

    def test_returns_value_once_exactly_53_weeks_available(self):
        dates = weekly_dates("2020-01-06", 53)
        prices = [100.0] * 53
        series = make_series(dates, prices)
        fr = me.formation_return(dates, series, dates[-1])
        self.assertIsNotNone(fr)


class NoLookaheadTest(unittest.TestCase):
    def test_future_candles_do_not_affect_past_rebalance_ranking(self):
        dates = weekly_dates("2015-01-05", 80)
        prices_a = [100.0 + i for i in range(80)]
        prices_b = [100.0 for _ in range(80)]
        series = {
            "A": make_series(dates, prices_a),
            "B": make_series(dates, prices_b),
        }
        clean = series
        calendar = dates
        r_date = dates[55]

        ranked_before = me.select_holdings(
            sorted(
                [t for t in clean if me.formation_return(dates, clean[t], r_date) is not None],
                key=lambda t: me.formation_return(dates, clean[t], r_date),
                reverse=True,
            ),
            set(), 0.5, 0.5,
        )

        # 미래(60주차 이후) 가격을 극단적으로 바꿔도 55주차 리밸런싱 판단은 동일해야 한다
        mutated_a = list(prices_a)
        for i in range(60, 80):
            mutated_a[i] = 0.01
        mutated_series = {"A": make_series(dates, mutated_a), "B": make_series(dates, prices_b)}
        ranked_after = me.select_holdings(
            sorted(
                [t for t in mutated_series if me.formation_return(dates, mutated_series[t], r_date) is not None],
                key=lambda t: me.formation_return(dates, mutated_series[t], r_date),
                reverse=True,
            ),
            set(), 0.5, 0.5,
        )
        self.assertEqual(ranked_before, ranked_after)

    def test_backtest_result_identical_when_future_history_truncated(self):
        dates = weekly_dates("2015-01-05", 90)
        prices = {
            "A": [100.0 * (1.01 ** i) for i in range(90)],
            "B": [100.0 * (1.001 ** i) for i in range(90)],
            "C": [100.0 * (0.999 ** i) for i in range(90)],
        }
        clean_full = {t: make_series(dates, p) for t, p in prices.items()}
        clean_truncated = {t: make_series(dates[:70], p[:70]) for t, p in prices.items()}
        calendar_full = dates
        calendar_truncated = dates[:70]

        first_rebalance = me.rebalance_dates(calendar_truncated, 13)[0]
        result_full = me.run_backtest(
            clean_full, calendar_full, first_rebalance, first_rebalance, 13, 0.5, 0.5, 0.0,
        )
        result_truncated = me.run_backtest(
            clean_truncated, calendar_truncated, first_rebalance, first_rebalance, 13, 0.5, 0.5, 0.0,
        )
        self.assertEqual(
            result_full.events[0].holdingsAfter,
            result_truncated.events[0].holdingsAfter,
        )
        self.assertEqual(
            result_full.events[0].rankedFormationReturns,
            result_truncated.events[0].rankedFormationReturns,
        )


class CostNeverImprovesResultTest(unittest.TestCase):
    def test_cost_adjusted_ending_value_never_exceeds_zero_cost(self):
        dates = weekly_dates("2015-01-05", 160)
        import random
        rng = random.Random(42)
        clean = {}
        for name in ["A", "B", "C", "D", "E", "F"]:
            p = 100.0
            series = []
            for _ in dates:
                p *= (1.0 + rng.uniform(-0.03, 0.035))
                series.append(p)
            clean[name] = make_series(dates, series)

        zero = me.run_backtest(clean, dates, dates[52], dates[-1], 13, 0.34, 0.5, 0.0)
        for bps in (20.0, 40.0, 57.0):
            costly = me.run_backtest(clean, dates, dates[52], dates[-1], 13, 0.34, 0.5, bps)
            self.assertLessEqual(costly.endingValue, zero.endingValue + 1e-6)


class DataQualityPolicyTest(unittest.TestCase):
    def test_duplicate_trading_date_excludes_ticker_entirely(self):
        dates = weekly_dates("2020-01-06", 60)
        rows = [(d, 100.0) for d in dates]
        rows.append((dates[10], 999.0))  # 중복 날짜 주입
        universe = {"DUP": rows, "OK": [(d, 100.0) for d in dates]}
        report = me.run_quality_checks(universe)
        self.assertEqual(report.excludedTickers.get("DUP"), "DUPLICATE_TRADING_DATE")
        self.assertNotIn("OK", report.excludedTickers)

    def test_unsorted_history_excludes_ticker_entirely(self):
        dates = weekly_dates("2020-01-06", 10)
        rows = [(d, 100.0) for d in dates]
        rows[3], rows[4] = rows[4], rows[3]  # 순서 뒤바꿈
        universe = {"BAD": rows}
        report = me.run_quality_checks(universe)
        self.assertEqual(report.excludedTickers.get("BAD"), "UNSORTED_HISTORY")

    def test_late_starting_ticker_excluded_only_before_enough_history(self):
        full_dates = weekly_dates("2015-01-05", 140)
        late_dates = full_dates[40:]  # 40주차부터 상장(이력 부족)
        clean = {
            "EARLY": make_series(full_dates, [100.0] * 140),
            "LATE": make_series(late_dates, [100.0] * len(late_dates)),
        }
        calendar = full_dates
        # 리밸런싱 스케줄(13주 간격, 52주차부터): 52, 65, 78, 91, 104, ...
        early_rebalance = full_dates[52]  # LATE는 12주치 이력만 있음(53주 미만, 부족)
        result_early = me.run_backtest(clean, calendar, early_rebalance, early_rebalance, 13, 0.5, 1.0, 0.0)
        self.assertIn("LATE", result_early.events[0].excludedThisDate)
        self.assertEqual(result_early.events[0].excludedThisDate["LATE"], "INSUFFICIENT_HISTORY")

        later_rebalance = full_dates[104]  # LATE 기준 자체 인덱스 64주 이력(53주 이상, 충분)
        result_later = me.run_backtest(clean, calendar, later_rebalance, later_rebalance, 13, 0.5, 1.0, 0.0)
        self.assertNotIn("LATE", result_later.events[0].excludedThisDate)

    def test_missing_exact_date_excluded_only_at_that_rebalance(self):
        dates = weekly_dates("2015-01-05", 80)
        series_b = make_series(dates, [100.0] * 80)
        r_date = dates[65]  # 리밸런싱 스케줄(52,65,78,...) 위에 있는 날짜여야 함
        del series_b[r_date]
        clean = {
            "A": make_series(dates, [100.0] * 80),
            "B": series_b,
        }
        result = me.run_backtest(clean, dates, r_date, r_date, 13, 0.5, 1.0, 0.0)
        self.assertEqual(result.events[0].excludedThisDate.get("B"), "MISSING_HISTORY")


if __name__ == "__main__":
    unittest.main(verbosity=2)

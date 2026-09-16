#!/usr/bin/env python3
"""Track B 횡단면 모멘텀 - 유니버스 확장(S&P100) 워크포워드 실행 스크립트.

`run_walk_forward.py`(DJIA 30종목)와 완전히 동일한 파라미터·분할·비용 가정을 그대로
재사용한다 - 유일한 차이는 입력 CSV가 `track-b-walk-forward-sp100/universe_weekly.csv`
(S&P100 101종목)라는 점뿐이다. 파라미터를 결과를 보고 바꾸지 않기 위해 원본 스크립트를
복사하되 값은 전혀 손대지 않았다.

실행: python3 research/scripts/track-b-walk-forward/run_walk_forward_sp100.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402

TOP_TIER_FRACTION = 0.20
HOLD_TIER_FRACTION = 0.40
REBALANCE_WEEKS = 13
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100000.0

PARTITIONS = {
    "2way": [
        {"id": "2way-A", "label": "2015-01-01 ~ 2019-12-31", "start": "2015-01-01", "end": "2019-12-31"},
        {"id": "2way-B", "label": "2020-01-01 ~ 2026-09-10", "start": "2020-01-01", "end": "2026-09-10"},
    ],
    "4way": [
        {"id": "4way-A", "label": "2015-01-01 ~ 2017-12-31", "start": "2015-01-01", "end": "2017-12-31"},
        {"id": "4way-B", "label": "2018-01-01 ~ 2020-12-31", "start": "2018-01-01", "end": "2020-12-31"},
        {"id": "4way-C", "label": "2021-01-01 ~ 2023-12-31", "start": "2021-01-01", "end": "2023-12-31"},
        {"id": "4way-D", "label": "2024-01-01 ~ 2026-09-10", "start": "2024-01-01", "end": "2026-09-10"},
    ],
}

DATA_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-b-walk-forward-sp100"
)
CSV_PATH = os.path.join(DATA_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(DATA_DIR, "walk_forward_results.json")


def clip_window(calendar, start, end):
    candidates_start = [d for d in calendar if d >= start]
    candidates_end = [d for d in calendar if d <= end]
    if not candidates_start or not candidates_end:
        return None, None
    return candidates_start[0], candidates_end[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def run_all():
    universe = me.load_universe_csv(CSV_PATH)
    quality = me.run_quality_checks(universe)
    clean = me.build_clean_series(universe, quality)
    calendar = me.build_calendar(universe)

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/run_walk_forward_sp100.py",
        "universe": {
            "size": len(universe),
            "excludedByQuality": quality.excludedTickers,
            "calendarGaps": quality.calendarGaps,
            "calendarStart": calendar[0],
            "calendarEnd": calendar[-1],
            "calendarWeeks": len(calendar),
            "pointInTimeUniverse": False,
        },
        "parameters": {
            "formationWeeks": me.FORMATION_WEEKS,
            "excludeRecentWeeks": me.EXCLUDE_RECENT_WEEKS,
            "rebalancePeriodWeeks": REBALANCE_WEEKS,
            "topTierFraction": TOP_TIER_FRACTION,
            "holdTierFraction": HOLD_TIER_FRACTION,
            "costScenariosRoundTripBps": COST_BPS_SCENARIOS,
            "initialCapital": INITIAL_CAPITAL,
            "costModel": "round-trip bps split half on buy leg, half on sell leg; no forced final liquidation cost",
        },
        "partitions": {},
    }

    for partition_name, windows in PARTITIONS.items():
        output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            window_result = {
                "id": w["id"],
                "label": w["label"],
                "requestedStart": w["start"],
                "requestedEnd": w["end"],
                "actualStart": w_start,
                "actualEnd": w_end,
                "byCostBps": {},
            }
            for bps in COST_BPS_SCENARIOS:
                strat = me.run_backtest(
                    clean, calendar, w_start, w_end, REBALANCE_WEEKS,
                    TOP_TIER_FRACTION, HOLD_TIER_FRACTION, bps, INITIAL_CAPITAL,
                )
                bench_start = strat.events[0].date if strat.events else w_start
                bench = me.run_equal_weight_buy_and_hold(
                    clean, calendar, bench_start, w_end, bps, INITIAL_CAPITAL,
                )
                strat_ret = pct_return(strat.endingValue, INITIAL_CAPITAL)
                bench_ret = pct_return(bench.endingValue, INITIAL_CAPITAL)
                avg_holdings = (
                    round(sum(len(e.holdingsAfter) for e in strat.events) / len(strat.events), 1)
                    if strat.events else 0.0
                )
                window_result["byCostBps"][str(bps)] = {
                    "rebalanceCount": len(strat.events),
                    "avgHoldingsCount": avg_holdings,
                    "benchmarkStart": bench_start,
                    "strategyEndingValue": round(strat.endingValue, 2),
                    "strategyReturnPct": round(strat_ret, 3),
                    "strategyMaxDrawdownPct": round(strat.maxDrawdownPct, 3),
                    "strategyMonthlyTurnoverPct": round(strat.monthlyEquivalentTurnoverPct, 3),
                    "benchmarkEndingValue": round(bench.endingValue, 2),
                    "benchmarkReturnPct": round(bench_ret, 3),
                    "benchmarkMaxDrawdownPct": round(bench.maxDrawdownPct, 3),
                    "excessReturnPctPoints": round(strat_ret - bench_ret, 3),
                    "mddGapPctPoints": round(strat.maxDrawdownPct - bench.maxDrawdownPct, 3),
                }
                if bps == 0.0:
                    window_result["zeroCostRebalanceEvents"] = [
                        {
                            "date": e.date,
                            "holdingsAfter": sorted(e.holdingsAfter),
                            "excludedThisDate": e.excludedThisDate,
                            "portfolioValueBeforeTrade": round(e.portfolioValueBeforeTrade, 2),
                            "oneWayTurnover": round(e.oneWayTurnover, 4),
                        }
                        for e in strat.events
                    ]
            output["partitions"][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 110)
    print("Track B 워크포워드 - S&P100 유니버스 확장 - 요약")
    print("=" * 110)
    for partition_name, windows in output["partitions"].items():
        print(f"\n--- 분할체계: {partition_name} ---")
        for w in windows:
            print(f"\n[{w['id']}] {w['label']} (실측 {w['actualStart']} ~ {w['actualEnd']})")
            print(f"{'bps':>5} {'rebal':>6} {'avgHold':>8} {'strat%':>10} {'bench%':>10} {'excess%p':>10} "
                  f"{'stratMDD%':>10} {'benchMDD%':>10} {'turnover%/mo':>13}")
            for bps in COST_BPS_SCENARIOS:
                r = w["byCostBps"][str(bps)]
                print(f"{bps:5.0f} {r['rebalanceCount']:6d} {r['avgHoldingsCount']:8.1f} "
                      f"{r['strategyReturnPct']:10.2f} {r['benchmarkReturnPct']:10.2f} "
                      f"{r['excessReturnPctPoints']:10.2f} {r['strategyMaxDrawdownPct']:10.2f} "
                      f"{r['benchmarkMaxDrawdownPct']:10.2f} {r['strategyMonthlyTurnoverPct']:13.2f}")


if __name__ == "__main__":
    run_all()

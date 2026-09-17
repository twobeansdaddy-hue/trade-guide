#!/usr/bin/env python3
"""Track C 세 번째(마지막) 후보 - 횡단면 모멘텀(Track B 설계 재사용, 분기 리밸런싱)을
KOSPI200에 적용.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**배경**: Track C 첫 두 후보(Track A 규칙 재사용 - 추세추종, RSI/볼린저 - 평균회귀)가
모두 매주 개별 종목 단위 신호로 진입/청산하는 구조라 월환산 회전율이 148~216%까지
치솟아 채택 기준(50%)을 3~4배 초과하며 기각됐다. 실패 원인이 "방향"이 아니라
"리밸런싱 빈도"였을 가능성을 검증하기 위해, **분기 리밸런싱**(형성기간 48주, 4주
제외, 13주 주기)으로 구조적으로 회전율이 훨씬 낮은 Track B 횡단면 모멘텀 설계를
그대로 KOSPI200에 이식한다 - Track B(S&P100) 실측 월환산 회전율이 17.8~21.9%로
채택 기준 이내였다.

**재사용 코어**: `research/scripts/track-b-walk-forward/momentum_engine.py`(이번에
전혀 수정하지 않음) - 형성기간 수익률(52주전→4주전 종가), 상위20%/유지40% 이원기준,
동일가중, 매 리밸런싱 재조정. 파라미터는 Track B(S&P100) 실행과 **완전히 동일**:
`TOP_TIER_FRACTION=0.20`, `HOLD_TIER_FRACTION=0.40`, `REBALANCE_WEEKS=13`,
비용 0/20/40/57bp, 2way/4way 워크포워드 분할.

**유니버스**: KOSPI200 199종목(기존 캐시 재사용, `track-c-kospi200/universe_weekly.csv`
- 42종목 품질 제외 후 157종목, Track C 첫 리포트와 동일).

**위험(사전 명시)**: Track B는 이 방법론 자체(모멘텀 붕괴/부호 반전)로 DJIA·S&P100·
변동성관리 오버레이 3개 독립 실행에서 모두 기각됐다 - "리밸런싱 빈도"가 아니라
"모멘텀이라는 신호 자체"가 문제였다면 국내 시장에서도 같은 패턴이 재현될 수 있다.
이번 검증은 그 두 가설(빈도 문제 vs 신호 자체 문제)을 분리해서 보기 위한 것이다.

**채택 게이트**: Track C 앞선 두 리포트와 동일한 5개 게이트.

**종료 조건(사전 확정)**: 이 후보도 기각되면 Track C는 여기서 마무리하고, 국내
주식은 Track B처럼 스크리닝 수준으로만 지원하는 것으로 결론 낸다 - 추가 후보를
더 시도하지 않는다.

실행: python3 research/scripts/track-c-kospi200/run_cross_sectional_momentum_kospi200.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "track-b-walk-forward"))
import momentum_engine as me  # noqa: E402

TOP_TIER_FRACTION = 0.20
HOLD_TIER_FRACTION = 0.40
REBALANCE_WEEKS = 13
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100_000_000.0  # KRW 1억원 (임의 기준값, 결과는 비율로만 해석)

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
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-c-kospi200"
)
CSV_PATH = os.path.join(DATA_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(DATA_DIR, "cross_sectional_momentum_results.json")


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
        "generatedBy": "research/scripts/track-c-kospi200/run_cross_sectional_momentum_kospi200.py",
        "hypothesis": "Track B 횡단면 모멘텀 설계(48주 형성기간, 분기 리밸런싱, 상위20%/유지40%)를 KOSPI200에 그대로 적용하면 앞선 두 후보(주간 리밸런싱)와 달리 회전율 게이트를 통과할 수 있다.",
        "universe": {
            "size": len(universe),
            "excludedByQuality": quality.excludedTickers,
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
            "initialCapitalKRW": INITIAL_CAPITAL,
        },
        "partitions": {},
    }

    for partition_name, windows in PARTITIONS.items():
        output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            window_result = {
                "id": w["id"], "label": w["label"],
                "actualStart": w_start, "actualEnd": w_end,
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
                    "strategyReturnPct": round(strat_ret, 3),
                    "strategyMaxDrawdownPct": round(strat.maxDrawdownPct, 3),
                    "strategyMonthlyTurnoverPct": round(strat.monthlyEquivalentTurnoverPct, 3),
                    "benchmarkReturnPct": round(bench_ret, 3),
                    "benchmarkMaxDrawdownPct": round(bench.maxDrawdownPct, 3),
                    "excessReturnPctPoints": round(strat_ret - bench_ret, 3),
                    "mddGapPctPoints": round(strat.maxDrawdownPct - bench.maxDrawdownPct, 3),
                }
            output["partitions"][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track C - 횡단면 모멘텀(KOSPI200) - 요약")
    print("=" * 120)
    for partition_name, windows in output["partitions"].items():
        print(f"\n--- 분할체계: {partition_name} ---")
        for w in windows:
            print(f"\n[{w['id']}] {w['label']}")
            print(f"{'bps':>5} {'rebal':>6} {'avgHold':>8} {'strat%':>12} {'bench%':>12} {'excess%p':>12} "
                  f"{'stratMDD%':>10} {'benchMDD%':>10} {'turnover%/mo':>13}")
            for bps in COST_BPS_SCENARIOS:
                r = w["byCostBps"].get(str(bps))
                if not r:
                    continue
                print(f"{bps:5.0f} {r['rebalanceCount']:6d} {r['avgHoldingsCount']:8.1f} "
                      f"{r['strategyReturnPct']:12.2f} {r['benchmarkReturnPct']:12.2f} "
                      f"{r['excessReturnPctPoints']:12.2f} {r['strategyMaxDrawdownPct']:10.2f} "
                      f"{r['benchmarkMaxDrawdownPct']:10.2f} {r['strategyMonthlyTurnoverPct']:13.2f}")


if __name__ == "__main__":
    run_all()

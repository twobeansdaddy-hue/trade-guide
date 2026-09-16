#!/usr/bin/env python3
"""Track B 신규 후보 - 단기 반전(short-term reversal) 워크포워드 검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
모멘텀(추세 지속) 계열이 원안(DJIA)·유니버스 확장(S&P100)·변동성관리 오버레이까지
3개 독립 실행에서 모두 기각된 뒤, 완전히 다른 메커니즘의 신규 후보로 **단기 반전
(short-term reversal)**을 검증한다. 모멘텀과 정반대 방향의 가설이다 - "최근 수익률이
가장 나빴던 종목이 다음 기간 평균 이상으로 반등한다"(과매도 반등, 유동성 압박 완화).

1차 학술 근거
------------
- Jegadeesh, N. (1990), "Evidence of Predictable Behavior of Security Returns",
  Journal of Finance 45(3), 881-898 - 주간 수익률에 강한 단기 반전(음의 자기상관)이
  존재함을 실증한 대표 논문. 이번 검증의 1차 근거.
- Lehmann, B. (1990), "Fads, Martingales, and Market Efficiency", QJE 105(1), 1-28 -
  주간 승자-패자 포트폴리오 전략을 실증.
- Avramov, Chordia & Goyal(2006) 등 후속 연구들은 이 이상현상의 상당 부분이
  유동성이 낮은 소형주에 집중돼 있고, 거래비용을 반영하면 큰 폭으로 줄어들거나
  사라진다는 것을 반복적으로 보였다 - **이 caveat을 결과를 보기 전에 명시적으로**
  **등록한다.** 이 후보가 비용 앞에서 무너질 가능성이 이번 검증의 핵심 리스크라고
  미리 밝혀둔다(사후 변명이 아니라 사전 예상).

가설 (데이터를 보기 전에 고정)
------------------------------
직전 1주 수익률 하위 20%(최악 성과) 종목을 동일가중으로 매수하고, 매주 재구성한다.
0bp(비용 없음)에서는 반전 신호가 존재해 벤치마크를 이길 수 있지만, 20~57bp 왕복비용을
반영하면 주당 거의 100%에 가까운 회전율 때문에 순수익이 크게 훼손되거나 사라질
것으로 예상한다. 이 예상이 맞는지 실제로 계산해 확인하고, 결과가 예상과 다르더라도
숨기지 않고 그대로 보고한다.

설계 (모멘텀과의 차이, 실행 전 확정)
--------------------------------------
- **형성기간**: 직전 1주 수익률(모멘텀의 48주와 정반대로 매우 짧음). 4주 형성기간도
  robustness 확인용으로 함께 계산하지만, 1주가 1차 가설이다(Jegadeesh 1990이 원래
  검증한 것도 주간 수익률).
- **정렬 방향**: 형성기간 수익률 **오름차순**(가장 나쁜 성과가 1순위) - 모멘텀은
  내림차순(가장 좋은 성과가 1순위)이었던 것과 정반대.
- **리밸런싱**: 매주(모멘텀은 분기 13주였던 것과 정반대로 훨씬 잦음) - 반전 효과가
  수명이 매우 짧다는 문헌 근거에 따른 설계다.
- **선정 기준**: 하위 20%(quintile) 동일가중, 매주 재구성(모멘텀의 20%진입/40%유지
  이원기준과 달리 "유지 구간" 없이 매주 그 시점 하위 20%로 전량 재조정 - 반전 신호가
  1주 만에 사라지므로 유지 구간을 둘 이유가 없다).
- **비용**: 왕복 0/20/40/57bp - 기존 모멘텀 검증과 동일한 비용 시나리오. 매주
  재조정하므로 연환산 회전율이 모멘텀보다 훨씬 높을 것으로 예상되며, 이 수치를
  명시적으로 기록한다.
- **유니버스**: S&P100 101종목, 캘린더 정합성 버그 수정이 완료된 기존 CSV
  (`track-b-walk-forward-sp100/universe_weekly.csv`) 그대로 재사용 - 새로 수집하지
  않는다.
- **워크포워드 분할**: 기존 모멘텀 검증과 동일한 2way/4way 분할, 동일한 5개 채택
  게이트를 그대로 적용한다.

실행: python3 research/scripts/track-b-walk-forward/short_term_reversal_backtest.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402

BOTTOM_TIER_FRACTION = 0.20  # 하위 20% (quintile), 모멘�템의 상위20% 진입 기준과 대칭
PRIMARY_LOOKBACK_WEEKS = 1   # 1차 가설 (Jegadeesh 1990)
ROBUSTNESS_LOOKBACK_WEEKS = 4  # 참고용 robustness 변형
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
OUTPUT_PATH = os.path.join(DATA_DIR, "short_term_reversal_results.json")


def clip_window(calendar, start, end):
    candidates_start = [d for d in calendar if d >= start]
    candidates_end = [d for d in calendar if d <= end]
    if not candidates_start or not candidates_end:
        return None, None
    return candidates_start[0], candidates_end[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def short_window_return(sorted_dates, price_by_date, as_of_date, lookback_weeks):
    """as_of_date 시점 기준 최근 lookback_weeks주 수익률(오늘 종가/lookback주전 종가 - 1).
    모멘텀의 formation_return과 달리 최근 구간을 제외하지 않는다(단기 반전은 바로 그
    최근 하락을 신호로 쓰기 때문) - 이 차이가 이 후보의 핵심 설계다."""
    if as_of_date not in price_by_date:
        return None
    idx = sorted_dates.index(as_of_date)
    if idx < lookback_weeks:
        return None
    p_prev = price_by_date[sorted_dates[idx - lookback_weeks]]
    if p_prev <= 0:
        return None
    p_now = price_by_date[as_of_date]
    return (p_now / p_prev - 1.0) * 100.0


def run_backtest_reversal(clean_series, calendar, start_date, end_date, lookback_weeks,
                           bottom_tier_fraction, round_trip_cost_bps, initial_capital):
    """momentum_engine.run_backtest와 동일한 체결/비용 메커니즘을 재사용하되,
    (a) 매주 리밸런싱하고 (b) 형성기간 수익률 오름차순(최하위가 1순위)으로 정렬하고
    (c) 유지 구간 없이(top=hold) 매주 그 시점 하위 20%로 전량 재조정한다는 점이 다르다."""
    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}
    r_dates = [d for d in calendar if start_date <= d <= end_date]
    if len(r_dates) < 2:
        return None

    shares = {}
    cash = initial_capital
    events = []
    turnovers = []

    def portfolio_value(as_of):
        v = cash
        for t, sh in shares.items():
            p = clean_series[t].get(as_of)
            if p is None:
                dts = sorted_dates_by_ticker[t]
                past = [d for d in dts if d <= as_of]
                p = clean_series[t][past[-1]] if past else 0.0
            v += sh * p
        return v

    holdings_before = set()
    for r_date in r_dates:
        ranked_scores = {}
        for t, dts in sorted_dates_by_ticker.items():
            r = short_window_return(dts, clean_series[t], r_date, lookback_weeks)
            if r is not None:
                ranked_scores[t] = r

        # 오름차순 정렬: 가장 나쁜 성과(최고 반전 후보)가 리스트 맨 앞
        ranked_tickers = sorted(ranked_scores, key=lambda t: ranked_scores[t])
        n = len(ranked_tickers)
        bottom_count = max(1, round(n * bottom_tier_fraction)) if n else 0
        new_holdings = set(ranked_tickers[:bottom_count])

        pre_trade_value = portfolio_value(r_date)
        target_per_position = pre_trade_value / len(new_holdings) if new_holdings else 0.0

        prev_position_values = {}
        for t in set(list(shares.keys()) + list(new_holdings)):
            p = clean_series.get(t, {}).get(r_date)
            if p is None and t in shares:
                dts = sorted_dates_by_ticker.get(t, [])
                past = [d for d in dts if d <= r_date]
                p = clean_series[t][past[-1]] if past else None
            prev_position_values[t] = shares.get(t, 0.0) * p if p is not None else 0.0

        total_trade_notional = 0.0
        new_shares = {}
        cost_total = 0.0
        cash_delta = 0.0
        # 주의: momentum_engine.run_backtest의 리밸런싱 루프를 그대로 옮겨 쓰다가, 매도
        # 레그 비용(cost)이 new_shares 계산에서는 반영되지만(shares=0으로 정확히 청산되므로
        # 겉으로는 맞아 보임) 그 비용만큼 현금이 실제로 줄어드는 효과가 "cash = pre_trade_value
        # - sum(new_shares*price)" 잔차 계산식에는 전혀 반영되지 않는(매도 비용이 사라지는)
        # 버그를 발견했다 - 매수 레그 비용은 산 주식 수를 줄여 정상 반영되지만 매도 레그 비용은
        # 어디에도 차감되지 않는다. 회전율이 극단적인(주당 150%+) 이 전략에서는 이 오차가 결론을
        # 좌우할 수 있어, cash를 잔차가 아니라 거래별 실제 현금 흐름(매수: -delta, 매도:
        # +(매도대금-비용))을 명시적으로 누적하는 방식으로 다시 구현했다. momentum_engine.py
        # 자체는 이 작업에서 수정하지 않았다(기존 코어, 별도 승인 필요) - 발견한 버그는
        # 보고서에 투명하게 기록한다.
        for t in set(list(shares.keys()) + list(new_holdings)):
            price = clean_series.get(t, {}).get(r_date)
            target_value = target_per_position if t in new_holdings else 0.0
            current_value = prev_position_values.get(t, 0.0)
            delta = target_value - current_value
            total_trade_notional += abs(delta)
            if price is None or price <= 0:
                new_shares[t] = shares.get(t, 0.0)
                continue
            if delta > 0:
                cost = me._cost_leg(delta, round_trip_cost_bps)
                cost_total += cost
                bought_value = delta - cost
                new_shares[t] = current_value / price + (bought_value / price if bought_value > 0 else 0.0)
                cash_delta -= delta
            elif delta < 0:
                sell_notional = -delta
                cost = me._cost_leg(sell_notional, round_trip_cost_bps)
                cost_total += cost
                new_shares[t] = current_value / price - (sell_notional / price)
                cash_delta += (sell_notional - cost)
            else:
                new_shares[t] = current_value / price if price else shares.get(t, 0.0)

        cash = cash + cash_delta
        shares = {t: sh for t, sh in new_shares.items() if sh > 1e-9}

        turnover = (total_trade_notional / pre_trade_value) if pre_trade_value > 0 else 0.0
        turnovers.append(turnover)
        events.append({"date": r_date, "holdingsAfter": sorted(new_holdings), "portfolioValueBeforeTrade": pre_trade_value})
        holdings_before = new_holdings

    weekly_values = [(d, portfolio_value(d)) for d in r_dates]
    import math
    peak = -math.inf
    mdd = 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    avg_turnover = sum(turnovers) / len(turnovers) if turnovers else 0.0
    # 매주 리밸런싱이므로 "월환산 회전율" = 주당 회전율 * 4.345
    monthly_equivalent = avg_turnover * 4.345 * 100.0

    return {
        "events": events,
        "endingValue": ending_value,
        "maxDrawdownPct": mdd,
        "avgOneWayTurnoverPerRebalance": avg_turnover,
        "monthlyEquivalentTurnoverPct": monthly_equivalent,
    }


def run_all():
    universe = me.load_universe_csv(CSV_PATH)
    quality = me.run_quality_checks(universe)
    clean = me.build_clean_series(universe, quality)
    calendar = me.build_calendar(universe)

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/short_term_reversal_backtest.py",
        "hypothesis": (
            "직전 1주(1차) 또는 4주(robustness) 수익률 하위 20% 종목을 동일가중 매수, 매주 "
            "재구성. Jegadeesh(1990) 주간 반전 근거. 왕복비용(20~57bp) 앞에서 무너질 가능성을 "
            "사전에 핵심 리스크로 등록함."
        ),
        "bottomTierFraction": BOTTOM_TIER_FRACTION,
        "universe": {"size": len(universe), "excludedByQuality": quality.excludedTickers},
        "parameters": {
            "primaryLookbackWeeks": PRIMARY_LOOKBACK_WEEKS,
            "robustnessLookbackWeeks": ROBUSTNESS_LOOKBACK_WEEKS,
            "rebalance": "weekly",
            "costScenariosRoundTripBps": COST_BPS_SCENARIOS,
            "initialCapital": INITIAL_CAPITAL,
        },
        "partitions": {},
    }

    for lookback_label, lookback_weeks in (("lookback1w", PRIMARY_LOOKBACK_WEEKS), ("lookback4w", ROBUSTNESS_LOOKBACK_WEEKS)):
        output["partitions"][lookback_label] = {}
        for partition_name, windows in PARTITIONS.items():
            output["partitions"][lookback_label][partition_name] = []
            for w in windows:
                w_start, w_end = clip_window(calendar, w["start"], w["end"])
                window_result = {"id": w["id"], "label": w["label"], "actualStart": w_start, "actualEnd": w_end, "byCostBps": {}}
                for bps in COST_BPS_SCENARIOS:
                    strat = run_backtest_reversal(clean, calendar, w_start, w_end, lookback_weeks, BOTTOM_TIER_FRACTION, bps, INITIAL_CAPITAL)
                    if strat is None:
                        continue
                    bench_start = strat["events"][0]["date"] if strat["events"] else w_start
                    bench = me.run_equal_weight_buy_and_hold(clean, calendar, bench_start, w_end, bps, INITIAL_CAPITAL)
                    strat_ret = pct_return(strat["endingValue"], INITIAL_CAPITAL)
                    bench_ret = pct_return(bench.endingValue, INITIAL_CAPITAL)
                    avg_holdings = (
                        round(sum(len(e["holdingsAfter"]) for e in strat["events"]) / len(strat["events"]), 1)
                        if strat["events"] else 0.0
                    )
                    window_result["byCostBps"][str(bps)] = {
                        "rebalanceCount": len(strat["events"]),
                        "avgHoldingsCount": avg_holdings,
                        "strategyReturnPct": round(strat_ret, 3),
                        "strategyMaxDrawdownPct": round(strat["maxDrawdownPct"], 3),
                        "strategyMonthlyTurnoverPct": round(strat["monthlyEquivalentTurnoverPct"], 3),
                        "benchmarkReturnPct": round(bench_ret, 3),
                        "benchmarkMaxDrawdownPct": round(bench.maxDrawdownPct, 3),
                        "excessReturnPctPoints": round(strat_ret - bench_ret, 3),
                        "mddGapPctPoints": round(strat["maxDrawdownPct"] - bench.maxDrawdownPct, 3),
                    }
                output["partitions"][lookback_label][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track B 단기 반전 - 요약")
    print("=" * 120)
    for lookback_label, partitions in output["partitions"].items():
        print(f"\n########## {lookback_label} ##########")
        for partition_name, windows in partitions.items():
            print(f"\n--- 분할체계: {partition_name} ---")
            for w in windows:
                print(f"\n[{w['id']}] {w['label']}")
                print(f"{'bps':>5} {'rebal':>6} {'avgHold':>8} {'strat%':>10} {'bench%':>10} {'excess%p':>10} "
                      f"{'stratMDD%':>10} {'benchMDD%':>10} {'turnover%/mo':>13}")
                for bps in COST_BPS_SCENARIOS:
                    r = w["byCostBps"].get(str(bps))
                    if not r:
                        continue
                    print(f"{bps:5.0f} {r['rebalanceCount']:6d} {r['avgHoldingsCount']:8.1f} "
                          f"{r['strategyReturnPct']:10.2f} {r['benchmarkReturnPct']:10.2f} "
                          f"{r['excessReturnPctPoints']:10.2f} {r['strategyMaxDrawdownPct']:10.2f} "
                          f"{r['benchmarkMaxDrawdownPct']:10.2f} {r['strategyMonthlyTurnoverPct']:13.2f}")


if __name__ == "__main__":
    run_all()

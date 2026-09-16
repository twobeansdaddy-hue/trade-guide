#!/usr/bin/env python3
"""Track B - 퀄리티 단독(저변동성 제외) 팩터 재검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
`research/reports/track-b-quality-lowvol-factor-validation.md`가 지적한 대로,
저변동성+퀄리티 팩터의 24개 조합 전부가 기각된 핵심 원인은 "저변동성" 절반이었다 -
그 필터가 NVDA/AMD/AVGO 같은 이 표본 기간의 실제 수익 견인 종목을 변동성이
높다는 이유만으로 구조적으로 배제했다. 이 스크립트는 **저변동성 절반을 완전히**
**제거하고, 퀄리티(수익성 ROE·ROA + 안전성 부채비율)만 단독으로** 같은 파라미터
(상위20%진입/40%유지, 분기 리밸런싱, 연간데이터 75일 지연)로 재검증한다.

**가설**: 저변동성 필터가 없으면 고변동성 고성장주(퀄리티 지표 자체는 나쁘지 않을 수
있는 NVDA/AMD류)를 배제하지 않으므로, 저변동성+퀄리티 조합보다 결과가 개선될
것이다. 다만 사전에 명시한다 - 이 가설이 맞더라도 "퀄리티 단독 필터가 벤치마크를
이긴다"는 것을 보장하지 않는다. 순수하게 "저변동성 제거가 결과를 어느 방향으로,
얼마나 바꾸는지"를 확인하는 것이 이 재검증의 목적이다.

변경점 (저변동성+퀄리티 검증 대비, 그 외 전부 동일)
------------------------------------------------------
- 최종스코어 = 퀄리티스코어(ROE·ROA·부채비율 백분위 평균) 그대로 사용, 저변동성
  요소는 계산하지 않음(0.5/0.5 가중치 결합 자체를 제거).
- 나머지(유니버스, 비용, 워크포워드 분할, 상위20%/40% 이원기준, 분기 리밸런싱,
  75일 지연)는 저변동성+퀄리티 검증과 완전히 동일해 직접 비교 가능하다.

실행: python3 research/scripts/track-b-walk-forward/quality_only_factor_backtest.py
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402
import quality_lowvol_factor_backtest as qlv  # noqa: E402

TOP_TIER_FRACTION = qlv.TOP_TIER_FRACTION
HOLD_TIER_FRACTION = qlv.HOLD_TIER_FRACTION
REBALANCE_WEEKS = qlv.REBALANCE_WEEKS
REPORTING_LAG_DAYS = qlv.REPORTING_LAG_DAYS
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100000.0

PRICE_CSV = qlv.PRICE_CSV
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "quality_only_factor_results.json",
)

PARTITIONS = qlv.PARTITIONS


def run_backtest_quality_only(clean_series, calendar, roe_series, roa_series, debt_series,
                               start_date, end_date, round_trip_cost_bps, initial_capital):
    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}
    r_dates = [d for d in calendar if start_date <= d <= end_date][::REBALANCE_WEEKS]
    all_dates = [d for d in calendar if start_date <= d <= end_date]
    if not r_dates or not all_dates:
        return None
    if all_dates[-1] not in r_dates:
        r_dates.append(all_dates[-1])

    shares, cash = {}, initial_capital
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
        roe_raw = {t: qlv.latest_known_value(roe_series.get(t, []), r_date) for t in clean_series}
        roa_raw = {t: qlv.latest_known_value(roa_series.get(t, []), r_date) for t in clean_series}
        debt_raw = {t: qlv.latest_known_value(debt_series.get(t, []), r_date) for t in clean_series}

        roe_pct = qlv.percentile_ranks(roe_raw)
        roa_pct = qlv.percentile_ranks(roa_raw)
        debt_pct = qlv.percentile_ranks(debt_raw)

        eligible = set(roe_pct) & set(roa_pct) & set(debt_pct)
        scores = {t: (roe_pct[t] + roa_pct[t] + debt_pct[t]) / 3.0 for t in eligible}

        ranked_tickers = sorted(scores, key=lambda t: scores[t], reverse=True)
        n = len(ranked_tickers)
        top_count = max(1, round(n * TOP_TIER_FRACTION)) if n else 0
        hold_count = max(top_count, round(n * HOLD_TIER_FRACTION)) if n else 0
        hold_zone = set(ranked_tickers[:hold_count])
        top_zone = set(ranked_tickers[:top_count])
        kept = {t for t in holdings_before if t in hold_zone}
        new_holdings = kept | {t for t in top_zone if t not in kept}

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
        cash_delta = 0.0
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
                bought_value = delta - cost
                new_shares[t] = current_value / price + (bought_value / price if bought_value > 0 else 0.0)
                cash_delta -= delta
            elif delta < 0:
                sell_notional = -delta
                cost = me._cost_leg(sell_notional, round_trip_cost_bps)
                new_shares[t] = current_value / price - (sell_notional / price)
                cash_delta += (sell_notional - cost)
            else:
                new_shares[t] = current_value / price if price else shares.get(t, 0.0)

        cash = cash + cash_delta
        shares = {t: sh for t, sh in new_shares.items() if sh > 1e-9}
        turnover = (total_trade_notional / pre_trade_value) if pre_trade_value > 0 else 0.0
        turnovers.append(turnover)
        events.append({"date": r_date, "holdingsAfter": sorted(new_holdings)})
        holdings_before = new_holdings

    weekly_values = [(d, portfolio_value(d)) for d in all_dates]
    peak, mdd = -math.inf, 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    avg_turnover = sum(turnovers) / len(turnovers) if turnovers else 0.0
    monthly_equivalent = avg_turnover / (REBALANCE_WEEKS / 4.345) * 100.0

    return {"events": events, "endingValue": ending_value, "maxDrawdownPct": mdd,
            "monthlyEquivalentTurnoverPct": monthly_equivalent}


def run_all():
    universe = me.load_universe_csv(PRICE_CSV)
    quality = me.run_quality_checks(universe)
    clean = me.build_clean_series(universe, quality)
    calendar = me.build_calendar(universe)

    roe_series = {t: qlv.build_annual_known_series(t, "roe") for t in clean}
    roa_series = {t: qlv.build_annual_known_series(t, "roa") for t in clean}
    debt_series = {t: qlv.build_annual_known_series(t, "totalDebtToEquity", invert=True) for t in clean}

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/quality_only_factor_backtest.py",
        "hypothesis": "저변동성+퀄리티 검증과 동일하되 저변동성 요소를 제거하고 퀄리티(ROE/ROA/부채비율)만 단독 사용.",
        "reportingLagDays": REPORTING_LAG_DAYS,
        "topTierFraction": TOP_TIER_FRACTION,
        "holdTierFraction": HOLD_TIER_FRACTION,
        "rebalanceWeeks": REBALANCE_WEEKS,
        "partitions": {},
    }

    for partition_name, windows in PARTITIONS.items():
        output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = qlv.clip_window(calendar, w["start"], w["end"])
            window_result = {"id": w["id"], "label": w["label"], "actualStart": w_start, "actualEnd": w_end, "byCostBps": {}}
            for bps in COST_BPS_SCENARIOS:
                strat = run_backtest_quality_only(clean, calendar, roe_series, roa_series, debt_series,
                                                   w_start, w_end, bps, INITIAL_CAPITAL)
                if strat is None:
                    continue
                bench_start = strat["events"][0]["date"] if strat["events"] else w_start
                bench = me.run_equal_weight_buy_and_hold(clean, calendar, bench_start, w_end, bps, INITIAL_CAPITAL)
                strat_ret = qlv.pct_return(strat["endingValue"], INITIAL_CAPITAL)
                bench_ret = qlv.pct_return(bench.endingValue, INITIAL_CAPITAL)
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
            output["partitions"][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track B 퀄리티 단독(저변동성 제외) 팩터 - 요약")
    print("=" * 120)
    for partition_name, windows in output["partitions"].items():
        print(f"\n--- 분할체계: {partition_name} ---")
        for w in windows:
            print(f"\n[{w['id']}] {w['label']}")
            print(f"{'bps':>5} {'rebal':>6} {'avgHold':>8} {'strat%':>10} {'bench%':>10} {'excess%p':>10} "
                  f"{'stratMDD%':>10} {'benchMDD%':>10} {'turnover%/mo':>13}")
            for bps in [0.0, 20.0, 40.0, 57.0]:
                r = w["byCostBps"].get(str(bps))
                if not r:
                    continue
                print(f"{bps:5.0f} {r['rebalanceCount']:6d} {r['avgHoldingsCount']:8.1f} "
                      f"{r['strategyReturnPct']:10.2f} {r['benchmarkReturnPct']:10.2f} "
                      f"{r['excessReturnPctPoints']:10.2f} {r['strategyMaxDrawdownPct']:10.2f} "
                      f"{r['benchmarkMaxDrawdownPct']:10.2f} {r['strategyMonthlyTurnoverPct']:13.2f}")


if __name__ == "__main__":
    run_all()

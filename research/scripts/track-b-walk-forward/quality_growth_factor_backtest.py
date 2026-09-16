#!/usr/bin/env python3
"""Track B - 퀄리티(수익성+성장성+안전성) 팩터 - QMJ 성장성 축 추가 재검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
`research/reports/track-b-quality-only-factor-retest.md`가 확인한 대로, 저변동성을
빼자 6개 구간 중 3개가 부호 반전됐지만 나머지 3개(특히 2024-2026)는 여전히 크게
졌다 - 실패 원인 조사 결과, 그 구간들의 벤치마크 견인 종목(NVDA/AMD/AVGO 등
반도체주)은 ROE·ROA가 아주 나쁘지는 않지만 최상위권도 아닌 경우가 많고, 대신
**폭발적인 이익 성장률**이 두드러진다는 것이 `research/reports/track-b-market-
concentration-diagnosis.md`의 상위 기여종목 목록에서 반복적으로 관찰됐다.

`research/reports/track-b-strategy-catalogue.md`가 애초에 QMJ(Quality Minus Junk,
Asness/Frazzini/Pedersen)의 4개 축(수익성·성장성·안전성·배당성향) 중 **성장성 축을**
**"이번 데이터로 안정적으로 구성하기 어렵다"는 이유로 제외**했었다. 이제 EPS 연간
시계열(`series.annual.eps`)이 실제로 폭넓게 존재함을 확인했으므로(사전 확인 완료),
그 성장성 축을 원래 이론대로 채워 넣는 것이 이번 재검증이다 - **임의 파라미터
추가가 아니라, 원래 정의된 이론적 요인 중 빠뜨렸던 부분을 보완하는 것**이다.
배당성향 축은 이미 별도 후보(배당성장, 기각됨)로 검증했으므로 여기서는 다루지 않는다.

**이번이 이 진단 방향에서 시도하는 마지막 변형이다** - 이후에도 통과하지 못하면
추가 파라미터 조정 없이 Track B 규칙기반 자동화 조사를 마무리한다(같은 6개 구간에
계속 새 변형을 맞춰보는 것은 사후 튜닝 위험이 커지기 때문).

설계 (실행 전 확정, 저변동성 제외 퀄리티 검증 대비 변경점만)
---------------------------------------------------------------
- **성장성 지표**: 연간 EPS YoY 성장률(%) = (올해 EPS / 작년 EPS - 1) × 100. 작년
  EPS가 0 이하(적자)면 성장률 계산 불가로 결측 처리(0으로 나누기 방지, 적자에서
  흑자 전환은 왜곡된 극단값을 만들 수 있어 제외 - 데이터를 보고 정한 규칙이 아니라
  일반적인 관행).
- **퀄리티 스코어 재정의**: ROE·ROA·EPS성장률·(-부채비율) **4개 지표의 백분위 순위**
  **평균**(동일가중, QMJ 3개 축 중 성장성을 추가한 것 외에는 이전과 동일한 결합
  방식). 저변동성 요소는 이번에도 포함하지 않는다(이미 트레이드오프를 확인했으므로).
- 나머지(유니버스, 비용, 워크포워드 분할, 상위20%/40% 이원기준, 분기 리밸런싱,
  75일 지연)는 전부 동일.

실행: python3 research/scripts/track-b-walk-forward/quality_growth_factor_backtest.py
"""
import datetime
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

FUND_DIR = qlv.FUND_DIR
PRICE_CSV = qlv.PRICE_CSV
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "quality_growth_factor_results.json",
)
PARTITIONS = qlv.PARTITIONS


def parse_date(s):
    return datetime.date.fromisoformat(s)


def build_eps_growth_series(ticker):
    """(known_date, yoy_growth_pct) 오름차순. 전년 EPS<=0이면 결측."""
    path = os.path.join(FUND_DIR, f"{ticker}.json")
    if not os.path.exists(path):
        return []
    data = json.load(open(path, encoding="utf-8"))
    eps_by_period = {e["period"]: e["v"] for e in data.get("series", {}).get("annual", {}).get("eps", []) if e.get("v") is not None}
    periods = sorted(eps_by_period.keys())
    out = []
    for i in range(1, len(periods)):
        prev_eps = eps_by_period[periods[i - 1]]
        cur_eps = eps_by_period[periods[i]]
        if prev_eps is None or prev_eps <= 0 or cur_eps is None:
            continue
        growth_pct = (cur_eps / prev_eps - 1.0) * 100.0
        known_date = (parse_date(periods[i]) + datetime.timedelta(days=REPORTING_LAG_DAYS)).isoformat()
        out.append((known_date, growth_pct))
    out.sort(key=lambda x: x[0])
    return out


def run_backtest_quality_growth(clean_series, calendar, roe_series, roa_series, debt_series, growth_series,
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
        growth_raw = {t: qlv.latest_known_value(growth_series.get(t, []), r_date) for t in clean_series}

        roe_pct = qlv.percentile_ranks(roe_raw)
        roa_pct = qlv.percentile_ranks(roa_raw)
        debt_pct = qlv.percentile_ranks(debt_raw)
        growth_pct = qlv.percentile_ranks(growth_raw)

        eligible = set(roe_pct) & set(roa_pct) & set(debt_pct) & set(growth_pct)
        scores = {t: (roe_pct[t] + roa_pct[t] + debt_pct[t] + growth_pct[t]) / 4.0 for t in eligible}

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
    growth_series = {t: build_eps_growth_series(t) for t in clean}

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/quality_growth_factor_backtest.py",
        "hypothesis": "QMJ 성장성 축(EPS YoY 성장률) 추가: 퀄리티스코어=(ROE+ROA+성장률+(-부채비율))/4, 저변동성 없음.",
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
                strat = run_backtest_quality_growth(clean, calendar, roe_series, roa_series, debt_series, growth_series,
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
    print("Track B 퀄리티(수익성+성장성+안전성) 팩터 - 요약")
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

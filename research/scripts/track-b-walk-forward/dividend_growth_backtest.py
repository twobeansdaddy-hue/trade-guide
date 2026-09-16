#!/usr/bin/env python3
"""Track B 신규 후보 - 배당성장 전략 워크포워드 검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
PEG 밸류에이션·저변동성+퀄리티 팩터까지 카탈로그 후보 4종이 모두 기각된 뒤, 완전히
새로운 가설군으로 시도하는 후보다. PEAD(실적 발표 후 주가 표류)를 먼저 검토했으나
Finnhub 무료 티어의 실적 서프라이즈 이력이 종목당 최근 4개 분기로 제한돼(실제 API
호출로 확인) 2015~2026 워크포워드에 필요한 깊이가 나오지 않아 기각하고, 배당성장
전략으로 대체했다.

1차 근거
--------
- Ned Davis Research의 반복 연구(예: S&P500 구성종목을 "배당 성장/개시", "배당 미변경",
  "배당 미지급", "배당 삭감/중단" 네 그룹으로 나눈 장기 비교)가 배당을 지속적으로
  늘려온 종목군이 배당을 지급하지 않거나 삭감한 종목군보다 위험조정 수익률(특히
  변동성 대비)에서 우월하다는 것을 반복적으로 보고해왔다. 이 프로젝트는 이 원 데이터에
  접근하지 못해 직접 재현하지는 못하지만, "배당을 안정적으로 늘려온 기업 = 현금흐름
  안정성의 시장 신호"라는 메커니즘 가설을 이 회사의 실데이터로 독립 검증한다.
- **사전에 명시하는 한계**: 이 신호는 앞서 기각된 저변동성+퀄리티 팩터(수익성·안전성)와
  메커니즘이 일부 겹칠 수 있다 - 수익성 좋은 기업이 배당도 안정적으로 늘리는 경향이
  있기 때문이다. 완전히 독립적인 가설이 아니라는 것을 결과 해석 시 감안해야 한다.

Finnhub 무료 데이터의 실제 한계 (설계에 반영)
----------------------------------------------
Finnhub 무료 티어의 `/stock/metric` 응답에는 배당 자체의 연간 시계열(주당배당금 이력)이
없다 - `series.annual`에 `payoutRatio`(지급성향, %)와 `eps`(주당순이익)는 있지만
`dividendPerShare` 시계열은 없다(현재 시점 값만 `metric.dividendPerShareAnnual`로 제공).
이 스크립트는 **주당배당금을 직접 관측하는 대신, payoutRatio(%) × EPS ÷ 100 = 주당배당금**
**근사치를 매 회계연도마다 역산**한다. EPS가 음수(적자)인 해는 지급성향이 의미가 없어
그 해의 배당 근사치를 "결측"으로 처리한다(배당컷으로 잘못 해석하지 않기 위함).

설계 (실행 전 확정)
--------------------
- **배당성장 연속 연수**: 회계연도를 오래된 순으로 순회하며, 직전 연도 대비 근사 주당
  배당금이 **증가**한 해가 연속되는 길이를 센다. 결측이나 감소가 나오면 0으로 리셋한다.
- **선정 기준(임계값 필터, 원 제품 스크린과 같은 방식)**: 그 시점 기준 연속 증가 연수
  ≥ **3년** AND 최근 지급성향이 **0%~75%** 범위(과도한 배당 - 지속가능성 우려 - 배제,
  0% 이하 또는 결측은 배당 자체가 없거나 계산 불가하므로 제외). 3년은 S&P Dividend
  Aristocrats의 25년 기준보다 훨씬 낮은 문턱이다 - 101종목 규모의 대형주 표본에서는
  25년 기준을 적용하면 차별화가 거의 안 될 것으로 예상해(사전 판단, 결과를 보고 정한
  것 아님) 낮춘 값이다.
- **공시일 지연**: 연간(10-K) 데이터는 회계연도 종료 + 75일(퀄리티+저변동성 검증과
  동일한 지연 규칙 재사용).
- **포트폴리오**: 조건을 만족하는 모든 종목을 동일가중 매수(순위 기반 아님, 원 제품
  스크린 철학과 일치), 분기(13주) 리밸런싱.
- **유니버스·비용·분할**: S&P100 101종목, 0/20/40/57bp, 2way/4way - 기존 검증들과 동일.

실행: python3 research/scripts/track-b-walk-forward/dividend_growth_backtest.py
"""
import datetime
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402

REPORTING_LAG_DAYS = 75
MIN_GROWTH_STREAK_YEARS = 3
MAX_PAYOUT_RATIO_PCT = 75.0
REBALANCE_WEEKS = 13
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100000.0

FUND_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "finnhub_fundamentals",
)
PRICE_CSV = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "universe_weekly.csv",
)
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "dividend_growth_results.json",
)

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


def parse_date(s):
    return datetime.date.fromisoformat(s)


def build_dividend_signal_series(ticker):
    """(known_date, streak_years, payout_ratio) 오름차순 리스트."""
    path = os.path.join(FUND_DIR, f"{ticker}.json")
    if not os.path.exists(path):
        return []
    data = json.load(open(path, encoding="utf-8"))
    ann = data.get("series", {}).get("annual", {})
    eps_by_period = {e["period"]: e["v"] for e in ann.get("eps", []) if e.get("v") is not None}
    payout_by_period = {e["period"]: e["v"] for e in ann.get("payoutRatio", []) if e.get("v") is not None}
    periods = sorted(set(eps_by_period) & set(payout_by_period))

    dps_by_period = {}
    for p in periods:
        eps, payout = eps_by_period[p], payout_by_period[p]
        if eps is None or eps <= 0 or payout is None:
            continue  # 적자 연도 또는 결측 -> 배당 근사치 계산 불가(결측 처리)
        dps_by_period[p] = (payout / 100.0) * eps

    out = []
    streak = 0
    prev_dps = None
    for p in periods:  # 오래된 -> 최신
        dps = dps_by_period.get(p)
        if dps is None:
            streak = 0
            prev_dps = None
        else:
            if prev_dps is not None and dps > prev_dps:
                streak += 1
            else:
                streak = 0
            prev_dps = dps
        known_date = (parse_date(p) + datetime.timedelta(days=REPORTING_LAG_DAYS)).isoformat()
        out.append((known_date, streak, payout_by_period[p]))

    out.sort(key=lambda x: x[0])
    return out


def latest_known_signal(series, as_of_date):
    result = None
    for known_date, streak, payout in series:
        if known_date <= as_of_date:
            result = (streak, payout)
        else:
            break
    return result


def clip_window(calendar, start, end):
    cs = [d for d in calendar if d >= start]
    ce = [d for d in calendar if d <= end]
    if not cs or not ce:
        return None, None
    return cs[0], ce[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def run_backtest_dividend(clean_series, calendar, signal_by_ticker, start_date, end_date,
                           round_trip_cost_bps, initial_capital):
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

    for r_date in r_dates:
        qualifying = []
        for t in clean_series:
            sig = latest_known_signal(signal_by_ticker.get(t, []), r_date)
            if sig is None:
                continue
            streak, payout = sig
            if streak >= MIN_GROWTH_STREAK_YEARS and 0 < payout <= MAX_PAYOUT_RATIO_PCT:
                qualifying.append(t)

        pre_trade_value = portfolio_value(r_date)
        target_per_position = pre_trade_value / len(qualifying) if qualifying else 0.0
        new_holdings = set(qualifying)

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

    signal_by_ticker = {t: build_dividend_signal_series(t) for t in clean}
    no_dividend_data = [t for t, s in signal_by_ticker.items() if not s]

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/dividend_growth_backtest.py",
        "hypothesis": (
            f"연속 배당성장 >= {MIN_GROWTH_STREAK_YEARS}년 AND 0<지급성향<= {MAX_PAYOUT_RATIO_PCT}% "
            "동시 만족 종목 동일가중 매수, 분기 리밸런싱. 배당은 payoutRatio*EPS/100으로 근사 "
            "(Finnhub 무료 티어에 DPS 시계열 없음). 공시+75일 지연."
        ),
        "minGrowthStreakYears": MIN_GROWTH_STREAK_YEARS,
        "maxPayoutRatioPct": MAX_PAYOUT_RATIO_PCT,
        "reportingLagDays": REPORTING_LAG_DAYS,
        "rebalanceWeeks": REBALANCE_WEEKS,
        "noDividendDataTickers": no_dividend_data,
        "universe": {"size": len(universe), "excludedByQuality": quality.excludedTickers},
        "parameters": {"costScenariosRoundTripBps": COST_BPS_SCENARIOS, "initialCapital": INITIAL_CAPITAL},
        "partitions": {},
    }

    for partition_name, windows in PARTITIONS.items():
        output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            window_result = {"id": w["id"], "label": w["label"], "actualStart": w_start, "actualEnd": w_end, "byCostBps": {}}
            for bps in COST_BPS_SCENARIOS:
                strat = run_backtest_dividend(clean, calendar, signal_by_ticker, w_start, w_end, bps, INITIAL_CAPITAL)
                if strat is None:
                    continue
                invested_dates = [e["date"] for e in strat["events"] if e["holdingsAfter"]]
                bench_start = invested_dates[0] if invested_dates else w_start
                bench = me.run_equal_weight_buy_and_hold(clean, calendar, bench_start, w_end, bps, INITIAL_CAPITAL)
                strat_ret = pct_return(strat["endingValue"], INITIAL_CAPITAL)
                bench_ret = pct_return(bench.endingValue, INITIAL_CAPITAL)
                rebals_with_holdings = [e for e in strat["events"] if e["holdingsAfter"]]
                avg_holdings = (
                    round(sum(len(e["holdingsAfter"]) for e in rebals_with_holdings) / len(rebals_with_holdings), 1)
                    if rebals_with_holdings else 0.0
                )
                pct_invested = round(len(rebals_with_holdings) / len(strat["events"]) * 100.0, 1) if strat["events"] else 0.0
                window_result["byCostBps"][str(bps)] = {
                    "rebalanceCount": len(strat["events"]),
                    "pctRebalancesInvested": pct_invested,
                    "avgHoldingsCountWhenInvested": avg_holdings,
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
    print(f"배당 신호 계산 불가(결측) 종목: {no_dividend_data}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track B 배당성장 전략 - 요약")
    print("=" * 120)
    for partition_name, windows in output["partitions"].items():
        print(f"\n--- 분할체계: {partition_name} ---")
        for w in windows:
            print(f"\n[{w['id']}] {w['label']}")
            print(f"{'bps':>5} {'rebal':>6} {'invested%':>10} {'avgHold':>8} {'strat%':>10} {'bench%':>10} "
                  f"{'excess%p':>10} {'stratMDD%':>10} {'benchMDD%':>10} {'turnover%/mo':>13}")
            for bps in [0.0, 20.0, 40.0, 57.0]:
                r = w["byCostBps"].get(str(bps))
                if not r:
                    continue
                print(f"{bps:5.0f} {r['rebalanceCount']:6d} {r['pctRebalancesInvested']:10.1f} "
                      f"{r['avgHoldingsCountWhenInvested']:8.1f} {r['strategyReturnPct']:10.2f} "
                      f"{r['benchmarkReturnPct']:10.2f} {r['excessReturnPctPoints']:10.2f} "
                      f"{r['strategyMaxDrawdownPct']:10.2f} {r['benchmarkMaxDrawdownPct']:10.2f} "
                      f"{r['strategyMonthlyTurnoverPct']:13.2f}")


if __name__ == "__main__":
    run_all()

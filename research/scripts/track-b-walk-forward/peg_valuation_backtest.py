#!/usr/bin/env python3
"""Track B 신규 후보 - PEG/PER 밸류에이션 밴드 워크포워드 검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
이 프로젝트의 실제 제품에는 이미 "PEG<1 && 현재가>50일 이동평균선"을 후보 발굴
조건(자동 매수 신호 아님)으로 쓰는 스크리닝이 있다(`research/STRATEGY_ENGINE_POLICY.md`
130행, `research/data/candidates.json`). 지금까지는 Finviz 수동 스크린샷으로만
운영됐고 실데이터 워크포워드 검증은 없었다. 이번 작업은 그 검증을 수행한다.

- **가설**: PEG(주가수익성장비율)<1인 저평가 종목을 상승 추세(가격>이동평균선) 조건과
  결합해 매수하면, 동일가중 매수 후 보유 벤치마크를 능가한다(Peter Lynch식 성장 대비
  저평가 + 추세 확인).
- 1차 학술 근거: Lakonishok, Shleifer & Vishny(1994), *Contrarian Investment,
  Extrapolation, and Risk*, Journal of Finance 49(5) - 가치주 전략의 초과수익을 실증.
- **알려진 반론(사전에 명시)**: 순수 가치/저PEG 전략은 2010년대~2020년대 "가치주
  부진(value drought)" 국면처럼 장기 사이클에 취약하다는 것이 널리 알려져 있다 -
  이 프로젝트가 모멘텀 검증에서 반복적으로 겪은 국면 의존성과 유사한 리스크가 있을
  수 있다는 것을 결과를 보기 전에 명시한다.

실제 제품 스크린과의 차이(투명하게 명시)
------------------------------------------
실제 제품의 PEG는 Finviz의 **선행(forward) PE/성장률** 기반이다
(`candidates.json`의 `notes` 필드에 "Fwd P/E" 표기 다수). 이 백테스트는 과거 시점의
선행 애널리스트 추정치를 구할 수 있는 무료 데이터가 없어 **후행(trailing) PEG**로
근사한다 - TTM PE ÷ TTM EPS의 전년 동기 대비 성장률(%). **이것은 실제 제품이 쓰는**
**지표와 다르다** - "그 시점에 이미 알려진 값"을 쓴다는 점만 같다. 이 차이를 결과
해석 시 계속 염두에 둬야 한다.

설계 (실행 전 확정)
--------------------
- **PEG 계산**: 분기별 TTM EPS(직전 4개 분기 EPS 합) ÷ 1년 전 TTM EPS(그 4분기 전
  4개 분기 합) - 1 = YoY TTM EPS 성장률(%). PEG = 그 분기의 peTTM ÷ 성장률(%).
  성장률이 0 이하(역성장·적자 전환)이면 PEG를 계산하지 않는다(원 스크린이 암묵적으로
  가정하는 "성장 중인 저평가주"라는 취지와 일치).
- **공시일 지연(look-ahead 방지, 사전 고정)**: 각 분기 데이터는 분기 종료일 +60일
  이후에야 "이미 알려진" 것으로 취급한다(미국 대형주 10-Q/10-K 제출 기한 관행에
  안전 마진을 더한 값 - 데이터를 보고 고른 값이 아니다).
  이 지연을 지키지 않으면 미래참조가 된다.
- **추세 필터**: 원 스크린의 "50일 이동평균"을 이 프로젝트가 보유한 주봉 데이터에
  맞춰 **10주 이동평균**으로 근사한다(Track A가 이미 쓰는 주봉 이동평균 관행과 일치,
  약 50거래일 ≈ 10주).
- **선정 기준**: 매 리밸런싱 시점에 (a) PEG가 계산 가능하고 0<PEG<1, (b) 현재가>10주
  이동평균 을 **동시에** 만족하는 모든 종목을 동일가중으로 매수(상위 N 랭킹이 아니라
  임계값 필터 - 원 스크린과 동일한 방식). 조건을 만족하는 종목이 하나도 없으면 현금
  보유.
- **리밸런싱 주기**: 4주(월간에 근사) - 분기별로만 갱신되는 펀더멘털 데이터의 특성과
  과도한 회전율(단기반전 검증에서 확인한 위험) 사이의 절충으로 사전 고정.
- **데이터 품질 제외(명시적으로 기록)**: 은행/금융 지주 8종(BAC, BNY, C, COF, JPM,
  USB, WFC 및 유사 금융업종)은 Finnhub 무료 티어에 `eps` 분기 시계열 자체가 없어
  (섹터별 재무제표 체계 차이로 추정) PEG를 계산할 수 없다 - 이 종목들은 원 스크린의
  선정 대상에서 데이터 부재로 항상 제외된다. 이는 "금융업종이 저평가가 아니라서"가
  아니라 "이 무료 데이터가 그 업종의 EPS 시계열을 제공하지 않아서"이며, 결과 해석 시
  반드시 감안해야 한다.
- **유니버스·비용·워크포워드 분할**: S&P100 101종목(기존 확보), 0/20/40/57bp,
  2way/4way - 기존 모멘텀·반전 검증과 동일해 직접 비교 가능하다.

실행: python3 research/scripts/track-b-walk-forward/peg_valuation_backtest.py
"""
import datetime
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402

REPORTING_LAG_DAYS = 60  # 사전 고정
SMA_WEEKS = 10           # 원안 50거래일 근사
REBALANCE_WEEKS = 4      # 사전 고정
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
    "track-b-walk-forward-sp100", "peg_valuation_results.json",
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


def build_peg_series(ticker):
    """(known_date_str, peg_value) 오름차순 리스트. 계산 불가한 분기는 건너뛴다."""
    path = os.path.join(FUND_DIR, f"{ticker}.json")
    if not os.path.exists(path):
        return []
    data = json.load(open(path, encoding="utf-8"))
    q = data.get("series", {}).get("quarterly", {})
    eps_series = q.get("eps", [])
    pe_series = q.get("peTTM", [])
    if not eps_series or not pe_series:
        return []  # 데이터 품질 제외 (예: 은행권 eps 시계열 부재)

    eps_by_period = {e["period"]: e["v"] for e in eps_series if e.get("v") is not None}
    pe_by_period = {e["period"]: e["v"] for e in pe_series if e.get("v") is not None}
    periods = sorted(eps_by_period.keys())  # 오래된 -> 최신

    out = []
    for i in range(7, len(periods)):
        p_i = periods[i]
        if p_i not in pe_by_period:
            continue
        window_now = periods[i - 3:i + 1]
        window_prior = periods[i - 7:i - 3]
        if len(window_now) != 4 or len(window_prior) != 4:
            continue
        ttm_now = sum(eps_by_period[p] for p in window_now)
        ttm_prior = sum(eps_by_period[p] for p in window_prior)
        if ttm_prior <= 0:
            continue
        growth_pct = (ttm_now / ttm_prior - 1.0) * 100.0
        if growth_pct <= 0:
            continue
        pe = pe_by_period[p_i]
        if pe is None or pe <= 0:
            continue
        peg = pe / growth_pct
        known_date = (parse_date(p_i) + datetime.timedelta(days=REPORTING_LAG_DAYS)).isoformat()
        out.append((known_date, peg))

    out.sort(key=lambda x: x[0])
    return out


def latest_known_value(series, as_of_date):
    """series: [(known_date, value), ...] 오름차순. as_of_date 시점에 이미 알려진 마지막 값."""
    result = None
    for known_date, value in series:
        if known_date <= as_of_date:
            result = value
        else:
            break
    return result


def build_sma_series(clean_prices_by_date, sorted_dates, sma_weeks):
    """{date: sma} - 각 날짜 시점까지의 과거 sma_weeks주 종가 평균(그 날짜 포함)."""
    out = {}
    window = []
    for d in sorted_dates:
        window.append(clean_prices_by_date[d])
        if len(window) > sma_weeks:
            window.pop(0)
        if len(window) == sma_weeks:
            out[d] = sum(window) / sma_weeks
    return out


def clip_window(calendar, start, end):
    cs = [d for d in calendar if d >= start]
    ce = [d for d in calendar if d <= end]
    if not cs or not ce:
        return None, None
    return cs[0], ce[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def run_backtest_peg(clean_series, calendar, peg_series_by_ticker, sma_by_ticker,
                      start_date, end_date, round_trip_cost_bps, initial_capital):
    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}
    all_dates = [d for d in calendar if start_date <= d <= end_date]
    if len(all_dates) < 2:
        return None
    r_dates = all_dates[::REBALANCE_WEEKS]
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
            price = clean_series[t].get(r_date)
            sma = sma_by_ticker.get(t, {}).get(r_date)
            if price is None or sma is None or price <= sma:
                continue
            peg = latest_known_value(peg_series_by_ticker.get(t, []), r_date)
            if peg is not None and 0 < peg < 1:
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
        events.append({"date": r_date, "holdingsAfter": sorted(new_holdings), "qualifyingCount": len(qualifying)})

    weekly_values = [(d, portfolio_value(d)) for d in all_dates]
    peak, mdd = -math.inf, 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    avg_turnover = sum(turnovers) / len(turnovers) if turnovers else 0.0
    monthly_equivalent = avg_turnover / (REBALANCE_WEEKS / 4.345) * 100.0

    return {
        "events": events, "endingValue": ending_value, "maxDrawdownPct": mdd,
        "monthlyEquivalentTurnoverPct": monthly_equivalent,
    }


def run_all():
    universe = me.load_universe_csv(PRICE_CSV)
    quality = me.run_quality_checks(universe)
    clean = me.build_clean_series(universe, quality)
    calendar = me.build_calendar(universe)

    peg_series_by_ticker = {}
    excluded_no_eps = []
    for t in clean:
        s = build_peg_series(t)
        peg_series_by_ticker[t] = s
        if not s:
            excluded_no_eps.append(t)

    sma_by_ticker = {}
    for t, prices in clean.items():
        dts = sorted(prices.keys())
        sma_by_ticker[t] = build_sma_series(prices, dts, SMA_WEEKS)

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/peg_valuation_backtest.py",
        "hypothesis": (
            "PEG(TTM PE / YoY TTM EPS 성장률) 0<PEG<1 AND 현재가>10주 이동평균 동시 만족 종목을 "
            "동일가중 매수, 4주 리밸런싱. 공시일+60일 지연 반영. 원 제품 스크린(선행PEG)과 달리 "
            "후행 PEG로 근사."
        ),
        "reportingLagDays": REPORTING_LAG_DAYS,
        "smaWeeks": SMA_WEEKS,
        "rebalanceWeeks": REBALANCE_WEEKS,
        "excludedNoEpsData": excluded_no_eps,
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
                strat = run_backtest_peg(clean, calendar, peg_series_by_ticker, sma_by_ticker, w_start, w_end, bps, INITIAL_CAPITAL)
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
                pct_rebals_invested = round(len(rebals_with_holdings) / len(strat["events"]) * 100.0, 1) if strat["events"] else 0.0
                window_result["byCostBps"][str(bps)] = {
                    "rebalanceCount": len(strat["events"]),
                    "pctRebalancesInvested": pct_rebals_invested,
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
    print(f"EPS 데이터 없어 PEG 계산 불가(제외): {excluded_no_eps}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track B PEG/PER 밸류에이션 - 요약")
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

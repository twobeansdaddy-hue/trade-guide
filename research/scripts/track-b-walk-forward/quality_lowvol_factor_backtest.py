#!/usr/bin/env python3
"""Track B 신규 후보 - 저변동성+퀄리티 팩터 워크포워드 검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
`research/reports/track-b-strategy-catalogue.md`가 "④ 저변동성/퀄리티 팩터"로 소개하고
펀더멘털 데이터 인프라 부재로 보류했던 후보다. `track-b-fundamental-data-provider-
evaluation.md`가 확보한 Finnhub 무료 시계열로 이제 실제 검증이 가능해졌다. Track B의
순수 가격 기반 후보 5종(Track A 재사용/모멘텀 3종/MACD/단기반전)과 PEG 밸류에이션까지
전부 기각된 뒤 시도하는 마지막 카탈로그 후보다.

1차 학술 근거
------------
- Ang, Hodrick, Xing & Zhang (2006), "The Cross-Section of Volatility and Expected
  Returns", Journal of Finance 61(1) - 특이 변동성이 높을수록 향후 평균 수익률이
  낮다는 "저변동성 이상현상" 실증.
- Asness, Frazzini & Pedersen, "Quality Minus Junk", Review of Accounting Studies -
  수익성(profitability)·안전성(safety) 등 퀄리티 요인이 위험조정수익을 개선한다는
  것을 미국 및 24개국에서 실증. 이 검증은 4개 축 중 **수익성(ROE, ROA)**과
  **안전성(부채비율)** 두 축만 쓴다 - 성장성·배당성향 축은 이번 데이터로 안정적으로
  구성하기 어려워 제외했다(전체 QMJ 재현이 아니라 부분 재현임을 명시).

가설 (데이터를 보기 전에 고정)
------------------------------
퀄리티(ROE·ROA 높고 부채비율 낮음)와 저변동성(주가 변동성 낮음)을 동시에 만족하는
종목이 위험조정 기준으로 벤치마크를 능가한다. `track-b-strategy-catalogue.md`가
이미 지적했듯, 저변동성 효과의 상당 부분이 퀄리티/수익성과 공통 요인을 공유한다는
후속 연구 관찰이 있어 - 저변동성 단독보다 이 둘을 결합하는 것이 더 안정적인 근거를
가질 것이라는 것이 이 결합의 동기다.

설계 (실행 전 확정)
--------------------
- **퀄리티 스코어 입력 (연간 데이터, 3개 지표 - 은행 포함 전 종목에 존재하는 필드만
  선택해 PEG 검증에서 겪은 섹터 제외 문제를 피함)**:
  - ROE (높을수록 좋음)
  - ROA (높을수록 좋음)
  - totalDebtToEquity (낮을수록 좋음 - 부호 반전해서 사용)
  세 지표 모두 101종목 전부에 존재함을 사전 확인했다(PEG 검증과 달리 은행 제외 불필요).
- **저변동성 스코어 입력**: 그 시점까지의 trailing 52주 주간수익률 실현 변동성
  (낮을수록 좋음 - 부호 반전).
- **스코어 결합 (사전 고정, 데이터를 보고 가중치를 조정하지 않음)**:
  1. 매 리밸런싱 시점에 그 시점 유효한 종목들 사이에서 4개 원지표(ROE, ROA,
     -부채비율, -실현변동성) 각각을 백분위 순위(0~1, 1이 가장 좋음)로 변환한다.
  2. 퀄리티스코어 = (ROE백분위 + ROA백분위 + 부채비율백분위) / 3.
  3. 최종스코어 = 0.5 × 퀄리티스코어 + 0.5 × 저변동성백분위 (퀄리티와 저변동성을
     동일 가중으로 결합 - 임의로 튜닝하지 않음).
- **공시일 지연**: 연간(10-K) 재무 데이터는 회계연도 종료 + **75일** 이후에만 알려진
  것으로 취급한다(10-K 제출 법정 기한이 대형 가속제출자 기준 60일이라, PEG 검증의
  60일보다 여유를 더 둔 값 - 10-K는 60일 기한 자체가 이미 빠듯해 안전 마진을 키웠다).
- **선정 기준**: 최종스코어 상위 20% 신규 진입, 상위 40% 밖으로 밀려나기 전까지 유지
  (모멘텀 검증과 동일한 이원기준 - 회전율 통제와 비교 가능성을 위해 재사용, 튜닝
  아님). 동일가중, 분기(13주) 리밸런싱(모멘텀과 동일 주기 - 퀄리티·저변동성은
  가격 모멘텀보다 느리게 변하는 지표라는 문헌 관행에 따름).
- **유니버스·비용·워크포워드 분할**: S&P100 101종목, 0/20/40/57bp, 2way/4way -
  기존 검증들과 동일해 직접 비교 가능.

실행: python3 research/scripts/track-b-walk-forward/quality_lowvol_factor_backtest.py
"""
import datetime
import json
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402

REPORTING_LAG_DAYS = 75  # 사전 고정 (연간/10-K 기준, PEG 검증의 60일보다 여유 확대)
VOL_LOOKBACK_WEEKS = 52  # 사전 고정
TOP_TIER_FRACTION = 0.20
HOLD_TIER_FRACTION = 0.40
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
    "track-b-walk-forward-sp100", "quality_lowvol_factor_results.json",
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


def build_annual_known_series(ticker, field, invert=False):
    """(known_date, value) 오름차순. invert=True면 부호 반전(낮을수록 좋은 지표용)."""
    path = os.path.join(FUND_DIR, f"{ticker}.json")
    if not os.path.exists(path):
        return []
    data = json.load(open(path, encoding="utf-8"))
    arr = data.get("series", {}).get("annual", {}).get(field, [])
    out = []
    for e in arr:
        v = e.get("v")
        if v is None:
            continue
        known_date = (parse_date(e["period"]) + datetime.timedelta(days=REPORTING_LAG_DAYS)).isoformat()
        out.append((known_date, -v if invert else v))
    out.sort(key=lambda x: x[0])
    return out


def latest_known_value(series, as_of_date):
    result = None
    for known_date, value in series:
        if known_date <= as_of_date:
            result = value
        else:
            break
    return result


def build_realized_vol_series(prices_by_date, sorted_dates, lookback_weeks):
    """{date: trailing lookback_weeks주 주간수익률 표준편차} (그 날짜 포함 이전만 사용)."""
    out = {}
    rets = []
    prev_price = None
    for d in sorted_dates:
        p = prices_by_date[d]
        if prev_price is not None and prev_price > 0:
            rets.append(p / prev_price - 1.0)
        prev_price = p
        if len(rets) > lookback_weeks:
            rets.pop(0)
        if len(rets) == lookback_weeks:
            out[d] = statistics.pstdev(rets)
    return out


def percentile_ranks(values_by_ticker):
    """{ticker: raw_value} -> {ticker: percentile 0~1, 1=최고}. 값이 없는 종목은 제외."""
    items = [(t, v) for t, v in values_by_ticker.items() if v is not None]
    if not items:
        return {}
    items.sort(key=lambda x: x[1])
    n = len(items)
    out = {}
    for i, (t, _) in enumerate(items):
        out[t] = i / (n - 1) if n > 1 else 1.0
    return out


def clip_window(calendar, start, end):
    cs = [d for d in calendar if d >= start]
    ce = [d for d in calendar if d <= end]
    if not cs or not ce:
        return None, None
    return cs[0], ce[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def run_backtest_quality_lowvol(clean_series, calendar, roe_series, roa_series, debt_series,
                                 vol_by_ticker, start_date, end_date, round_trip_cost_bps, initial_capital):
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
        roe_raw = {t: latest_known_value(roe_series.get(t, []), r_date) for t in clean_series}
        roa_raw = {t: latest_known_value(roa_series.get(t, []), r_date) for t in clean_series}
        debt_raw = {t: latest_known_value(debt_series.get(t, []), r_date) for t in clean_series}
        vol_raw = {t: -vol_by_ticker.get(t, {}).get(r_date) if vol_by_ticker.get(t, {}).get(r_date) is not None else None for t in clean_series}

        roe_pct = percentile_ranks(roe_raw)
        roa_pct = percentile_ranks(roa_raw)
        debt_pct = percentile_ranks(debt_raw)
        vol_pct = percentile_ranks(vol_raw)

        eligible = set(roe_pct) & set(roa_pct) & set(debt_pct) & set(vol_pct)
        scores = {}
        for t in eligible:
            quality = (roe_pct[t] + roa_pct[t] + debt_pct[t]) / 3.0
            scores[t] = 0.5 * quality + 0.5 * vol_pct[t]

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

    roe_series = {t: build_annual_known_series(t, "roe") for t in clean}
    roa_series = {t: build_annual_known_series(t, "roa") for t in clean}
    debt_series = {t: build_annual_known_series(t, "totalDebtToEquity", invert=True) for t in clean}

    vol_by_ticker = {}
    for t, prices in clean.items():
        dts = sorted(prices.keys())
        vol_by_ticker[t] = build_realized_vol_series(prices, dts, VOL_LOOKBACK_WEEKS)

    missing_fields = [t for t in clean if not (roe_series[t] and roa_series[t] and debt_series[t])]

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/quality_lowvol_factor_backtest.py",
        "hypothesis": (
            "최종스코어=0.5*퀄리티(ROE/ROA/부채비율 백분위 평균)+0.5*저변동성(52주 실현변동성 "
            "백분위 역순) 상위20% 진입/40% 유지, 분기 리밸런싱, 연간 데이터 75일 지연."
        ),
        "reportingLagDays": REPORTING_LAG_DAYS,
        "volLookbackWeeks": VOL_LOOKBACK_WEEKS,
        "topTierFraction": TOP_TIER_FRACTION,
        "holdTierFraction": HOLD_TIER_FRACTION,
        "rebalanceWeeks": REBALANCE_WEEKS,
        "missingFundamentalFields": missing_fields,
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
                strat = run_backtest_quality_lowvol(clean, calendar, roe_series, roa_series, debt_series,
                                                     vol_by_ticker, w_start, w_end, bps, INITIAL_CAPITAL)
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
            output["partitions"][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    print(f"펀더멘털 필드 결측 종목: {missing_fields}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track B 저변동성+퀄리티 팩터 - 요약")
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

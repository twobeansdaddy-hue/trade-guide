#!/usr/bin/env python3
"""Track B - "메가캡 집중 상승장" 진단 가설 검증.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
8개 후보(Track A 재사용, 모멘텀 3종, MACD, 단기반전, PEG 밸류에이션, 저변동성+퀄리티,
배당성장)가 전부 기각된 뒤, 개별 리포트들이 반복적으로 제시한 해석 - "이 전략들이
틀려서가 아니라, 2015~2026 S&P100 표본이 소수 메가캡(AMZN/NVDA/TSLA/META/NFLX 등)이
벤치마크 수익률을 비대칭적으로 견인한 특수 국면이었기 때문" - 을 별도 가설로 승격해
직접 검증한다.

**가설(데이터를 보기 전에 고정)**: (1) 벤치마크(동일가중 매수후보유) 총수익에서 상위
소수 종목이 차지하는 기여도 비중이 구간마다 다를 것이고, (2) 이 집중도가 높은 구간일수록
그동안 검증한 방어적 성격의 전략(저변동성+퀄리티, 배당성장 등)의 상대성과가 더 나쁠
것이다, (3) 벤치마크에서 그 상위 기여 종목들을 제외하면, 저변동성+퀄리티 팩터의
초과수익 부호가 뒤집히거나 최소한 격차가 크게 줄어들 것이다.

검증 1: 구간별 벤치마크 수익 기여도 집중도
--------------------------------------------
각 워크포워드 구간에서 동일가중 매수후보유 벤치마크의 총수익 중, 개별 종목이 기여한
비중을 계산한다(그 종목의 최종가치변화 ÷ 전체 포트폴리오 최종가치변화). 상위 1/3/5/10
종목의 누적 기여 비중을 구해 "집중도"로 정의한다.

검증 2: 집중도와 기존 8개 후보 성과의 상관관계
-------------------------------------------------
`research/data/backtests.json`에 이미 기록된 저변동성+퀄리티·배당성장 후보의 구간별
초과수익(%p)을 이 집중도 지표와 나란히 놓고 방향성이 일치하는지 확인한다(정식
통계검정이 아니라 기술적 비교 - 표본이 6개 구간뿐이라 회귀분석은 과도한 정밀도 주장이
될 수 있어 하지 않는다).

검증 3: 상위 기여 종목 제외 벤치마크 대비 재평가 (핵심 인과 검증)
---------------------------------------------------------------
가장 극단적으로 기각됐던 저변동성+퀄리티 팩터를, 원래 벤치마크가 아니라 **그 구간
벤치마크 상위 5개 기여 종목을 제외한 동일가중 벤치마크**와 다시 비교한다. 부호가
뒤집히거나 격차가 크게 줄면 "전략 자체보다 국면이 원인"이라는 해석이 뒷받침된다.
반대로 여전히 크게 진다면 국면 탓만으로 돌릴 수 없다는 뜻이므로 그대로 보고한다.

실행: python3 research/scripts/track-b-walk-forward/market_concentration_diagnosis.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402
import quality_lowvol_factor_backtest as qlv  # noqa: E402

PRICE_CSV = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "universe_weekly.csv",
)
OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache",
    "track-b-walk-forward-sp100", "market_concentration_diagnosis_results.json",
)

PARTITIONS = {
    "2way": [
        {"id": "2way-A", "start": "2015-01-01", "end": "2019-12-31"},
        {"id": "2way-B", "start": "2020-01-01", "end": "2026-09-10"},
    ],
    "4way": [
        {"id": "4way-A", "start": "2015-01-01", "end": "2017-12-31"},
        {"id": "4way-B", "start": "2018-01-01", "end": "2020-12-31"},
        {"id": "4way-C", "start": "2021-01-01", "end": "2023-12-31"},
        {"id": "4way-D", "start": "2024-01-01", "end": "2026-09-10"},
    ],
}

# 기존 리포트에 이미 기록된 0bp 초과수익(%p) - backtests.json에서 그대로 가져옴 (재계산 아님)
EXISTING_EXCESS_RETURNS_0BP = {
    "quality_lowvol": {"2way-A": -47.81, "2way-B": -111.64, "4way-A": -25.03, "4way-B": -21.35, "4way-C": -10.97, "4way-D": -23.23},
    "dividend_growth": {"2way-A": -45.34, "2way-B": -57.59, "4way-A": -23.47, "4way-B": -18.06, "4way-C": -0.09, "4way-D": -10.70},
}


def clip_window(calendar, start, end):
    cs = [d for d in calendar if d >= start]
    ce = [d for d in calendar if d <= end]
    if not cs or not ce:
        return None, None
    return cs[0], ce[-1]


def contribution_concentration(clean_series, calendar, start_date, end_date, initial_capital=100000.0):
    """동일가중 매수후보유 벤치마크에서 종목별 기여도(달러)와 상위 N 누적 비중."""
    eligible = [t for t, prices in clean_series.items() if start_date in prices]
    if not eligible:
        return None
    per_position = initial_capital / len(eligible)
    contributions = {}
    for t in eligible:
        p0 = clean_series[t][start_date]
        dts = sorted(clean_series[t].keys())
        past = [d for d in dts if d <= end_date]
        p1 = clean_series[t][past[-1]] if past else p0
        shares = per_position / p0
        contributions[t] = shares * p1 - per_position  # 이 종목의 달러 기여도

    total_gain = sum(contributions.values())
    ranked = sorted(contributions.items(), key=lambda x: x[1], reverse=True)
    top5_tickers = [t for t, _ in ranked[:5]]

    def top_n_share(n):
        top_sum = sum(v for _, v in ranked[:n])
        return (top_sum / total_gain * 100.0) if total_gain != 0 else None

    return {
        "eligibleCount": len(eligible),
        "totalGainDollars": round(total_gain, 2),
        "top1SharePct": round(top_n_share(1), 2) if top_n_share(1) is not None else None,
        "top3SharePct": round(top_n_share(3), 2) if top_n_share(3) is not None else None,
        "top5SharePct": round(top_n_share(5), 2) if top_n_share(5) is not None else None,
        "top10SharePct": round(top_n_share(10), 2) if top_n_share(10) is not None else None,
        "top5Contributors": [{"ticker": t, "contributionDollars": round(v, 2)} for t, v in ranked[:5]],
        "top5Tickers": top5_tickers,
    }


def run_backtest_excluding_tickers(clean_series, calendar, exclude_tickers, peg_engine_args, start_date, end_date, round_trip_cost_bps, initial_capital):
    """quality_lowvol 전략을 그대로 실행하되, exclude_tickers를 유니버스에서 제외한 clean_series로 벤치마크만 다시 계산."""
    filtered = {t: v for t, v in clean_series.items() if t not in exclude_tickers}
    bench = me.run_equal_weight_buy_and_hold(filtered, calendar, start_date, end_date, round_trip_cost_bps, initial_capital)
    return bench


def main():
    universe = me.load_universe_csv(PRICE_CSV)
    quality = me.run_quality_checks(universe)
    clean = me.build_clean_series(universe, quality)
    calendar = me.build_calendar(universe)

    output = {"concentration": {}, "correlationCheck": [], "counterfactual": {}}

    print("=" * 100)
    print("검증 1: 구간별 벤치마크 수익 기여도 집중도")
    print("=" * 100)
    for partition_name, windows in PARTITIONS.items():
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            conc = contribution_concentration(clean, calendar, w_start, w_end)
            output["concentration"][w["id"]] = conc
            print(f"\n[{w['id']}] 상위1종목 {conc['top1SharePct']}%, 상위3 {conc['top3SharePct']}%, "
                  f"상위5 {conc['top5SharePct']}%, 상위10 {conc['top10SharePct']}%")
            print(f"  상위5 기여종목: {[c['ticker'] for c in conc['top5Contributors']]}")

    print("\n" + "=" * 100)
    print("검증 2: 집중도(상위5 기여비중) vs 기존 후보 초과수익(0bp) 방향 비교")
    print("=" * 100)
    for wid in output["concentration"]:
        top5 = output["concentration"][wid]["top5SharePct"]
        qlv_excess = EXISTING_EXCESS_RETURNS_0BP["quality_lowvol"].get(wid)
        div_excess = EXISTING_EXCESS_RETURNS_0BP["dividend_growth"].get(wid)
        row = {"windowId": wid, "top5ContributionSharePct": top5,
               "qualityLowvolExcessPct": qlv_excess, "dividendGrowthExcessPct": div_excess}
        output["correlationCheck"].append(row)
        print(f"{wid}: 상위5집중도={top5}%  저변동성퀄리티초과수익={qlv_excess}%p  배당성장초과수익={div_excess}%p")

    print("\n" + "=" * 100)
    print("검증 3: 상위5 기여종목 제외 벤치마크 대비 저변동성+퀄리티 팩터 재평가")
    print("=" * 100)
    for partition_name, windows in PARTITIONS.items():
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            top5_tickers = output["concentration"][w["id"]]["top5Tickers"]

            roe_series = {t: qlv.build_annual_known_series(t, "roe") for t in clean}
            roa_series = {t: qlv.build_annual_known_series(t, "roa") for t in clean}
            debt_series = {t: qlv.build_annual_known_series(t, "totalDebtToEquity", invert=True) for t in clean}
            vol_by_ticker = {}
            for t, prices in clean.items():
                dts = sorted(prices.keys())
                vol_by_ticker[t] = qlv.build_realized_vol_series(prices, dts, qlv.VOL_LOOKBACK_WEEKS)

            strat = qlv.run_backtest_quality_lowvol(clean, calendar, roe_series, roa_series, debt_series,
                                                     vol_by_ticker, w_start, w_end, 0.0, 100000.0)
            if strat is None:
                continue
            strat_ret = qlv.pct_return(strat["endingValue"], 100000.0)

            orig_bench = me.run_equal_weight_buy_and_hold(clean, calendar, w_start, w_end, 0.0, 100000.0)
            orig_bench_ret = qlv.pct_return(orig_bench.endingValue, 100000.0)

            counterfactual_bench = run_backtest_excluding_tickers(clean, calendar, set(top5_tickers), None, w_start, w_end, 0.0, 100000.0)
            counterfactual_ret = qlv.pct_return(counterfactual_bench.endingValue, 100000.0)

            row = {
                "windowId": w["id"],
                "top5ExcludedTickers": top5_tickers,
                "strategyReturnPct": round(strat_ret, 2),
                "originalBenchmarkReturnPct": round(orig_bench_ret, 2),
                "originalExcessPct": round(strat_ret - orig_bench_ret, 2),
                "counterfactualBenchmarkReturnPct": round(counterfactual_ret, 2),
                "counterfactualExcessPct": round(strat_ret - counterfactual_ret, 2),
            }
            output["counterfactual"][w["id"]] = row
            print(f"\n[{w['id']}] 제외종목: {top5_tickers}")
            print(f"  전략 수익률: {row['strategyReturnPct']}%")
            print(f"  원 벤치마크: {row['originalBenchmarkReturnPct']}%  (원 초과수익 {row['originalExcessPct']}%p)")
            print(f"  상위5제외 벤치마크: {row['counterfactualBenchmarkReturnPct']}%  (반사실 초과수익 {row['counterfactualExcessPct']}%p)")

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n원자료 저장: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Track A 매크로 오버레이 여섯 번째 가설 - 진입 시점 섹터 상대강도 필터.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**배경**: VIX 레벨·금리 변화율·VIX 급등(즉시/지연확인)·신용스프레드 레벨, 다섯
가지 매크로 가설이 모두 명확한 채택 근거를 내지 못했다. 이 다섯은 전부 "매크로
스트레스 지표"(변동성/금리/신용)였다 - **섹터 상대강도(relative strength)**는
질적으로 다른 신호다: 매크로 스트레스가 아니라 "이 사이클이 이미 시장을 이기고
있는 추세인가"를 확인하는 모멘텀/섹터로테이션 신호다.

**가설**: 진입 시점 직전 13주간, 레버리지 ETF의 비레버리지 기초지수(예: SOXL의
경우 SOXX)가 시장 벤치마크(SPY) 대비 상대강도(outperformance)가 강할수록 - 즉
이미 그 섹터가 시장을 이기고 있는 추세가 확인될수록 - 이후 사이클 수익률이
좋다(모멘텀 지속 가설). 이는 앞선 다섯 가설과 달리 **양(+)의 상관관계를 기대**
하는 가설이다(스트레스 지표들은 전부 음의 상관을 기대했음).

**기초지수 매핑(실행 전 고정)**: SOXL→SOXX, TQQQ→QQQ, TNA→IWM, FAS→XLF.
벤치마크: SPY(전 종목 공통).

**지표 정의**: `relativeStrengthPct` = (기초지수의 13주 수익률) - (SPY의 13주
수익률), 둘 다 진입 주 종가 기준 직전 13주 수익률(%). 양수면 시장 대비 아웃퍼폼.
진입 사이클의 캘린더(SOXL 등)와 기초지수/SPY 캘린더의 요일 앵커가 달라(월요일
vs 화요일) 정확히 같은 날짜가 없을 수 있어 최근접 과거 거래일로 근사한다(VIX/
금리/신용스프레드 검증과 동일한 방식).

**임계값(결과를 보기 전에 고정)**: 1차 relativeStrengthPct>=+5%p(뚜렷한
아웃퍼폼), 2차 lookback을 26주로 바꾼 강건성 확인(같은 +5%p 임계값). 임계값과
무관한 검증으로 피어슨 상관계수도 함께 본다.

**적용 대상·사이클 정의**: 앞선 다섯 가설과 완전히 동일한 42개 유효 사이클
(SOXL/TQQQ/TNA/FAS, CROSS_UP delay=0 진입→CROSS_DOWN 청산) 재사용.

**데이터**: `research/scripts/track-a-macro/fetch_sector_benchmark_universe.py`
로 신규 수집한 SOXX/QQQ/IWM/XLF/SPY 주봉(2008~2026, Yahoo Finance).

실행: python3 research/scripts/track-a-macro/sector_relative_strength_entry_filter_backtest.py
"""
import bisect
import csv
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
UNIVERSE_CSV = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "sector_relative_strength_entry_filter_results.json")

UNDERLYING_MAP = {"SOXL": "SOXX", "TQQQ": "QQQ", "TNA": "IWM", "FAS": "XLF"}
BENCHMARK = "SPY"
LOOKBACKS_WEEKS = [13, 26]
THRESHOLD_PCT = 5.0


def load_universe_csv(path):
    series = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            series.setdefault(row["ticker"], {})[row["datetime"]] = float(row["close"])
    return series


def lookback_return(price_series, sorted_dates, as_of_date, weeks):
    """as_of_date 이전(포함) 가장 최근 거래일부터 weeks주 전까지의 수익률.
    SOXL 등 진입 사이클 캘린더와 SOXX/SPY 캘린더의 요일 앵커가 달라(월요일 vs
    화요일) 정확히 같은 날짜가 없을 수 있으므로, 최근접 과거 거래일로 근사한다
    (VIX/금리/신용스프레드 검증과 동일한 방식)."""
    idx = bisect.bisect_right(sorted_dates, as_of_date) - 1
    if idx < weeks:
        return None
    p_now = price_series[sorted_dates[idx]]
    p_prior = price_series[sorted_dates[idx - weeks]]
    if p_prior <= 0:
        return None
    return (p_now / p_prior - 1.0) * 100.0


def pearson(xs, ys):
    n = len(xs)
    if n < 2:
        return None
    mx, my = sum(xs) / n, sum(ys) / n
    cov = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    varx = sum((x - mx) ** 2 for x in xs)
    vary = sum((y - my) ** 2 for y in ys)
    if varx <= 0 or vary <= 0:
        return None
    return cov / (varx ** 0.5 * vary ** 0.5)


def summarize(vals):
    if not vals:
        return {"n": 0, "winRatePct": None, "avgReturnPct": None, "medianReturnPct": None}
    wins = sum(1 for v in vals if v > 0)
    svals = sorted(vals)
    mid = len(svals) // 2
    median = svals[mid] if len(svals) % 2 == 1 else (svals[mid - 1] + svals[mid]) / 2.0
    return {"n": len(vals), "winRatePct": round(100.0 * wins / len(vals), 1),
            "avgReturnPct": round(sum(vals) / len(vals), 2), "medianReturnPct": round(median, 2)}


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]
    series = load_universe_csv(UNIVERSE_CSV)
    sorted_dates = {t: sorted(prices.keys()) for t, prices in series.items()}

    results_by_lookback = {}
    for lb in LOOKBACKS_WEEKS:
        enriched = []
        skipped = []
        for c in cycles:
            ticker = c["ticker"]
            underlying = UNDERLYING_MAP[ticker]
            entry_date = c["entryDate"]

            underlying_ret = lookback_return(series[underlying], sorted_dates[underlying], entry_date, lb)
            bench_ret = lookback_return(series[BENCHMARK], sorted_dates[BENCHMARK], entry_date, lb)
            if underlying_ret is None or bench_ret is None:
                skipped.append({"ticker": ticker, "entryDate": entry_date, "reason": "INSUFFICIENT_HISTORY"})
                continue
            rel_strength = round(underlying_ret - bench_ret, 3)
            enriched.append({
                "ticker": ticker, "underlying": underlying, "entryDate": entry_date,
                "underlyingLookbackReturnPct": round(underlying_ret, 2),
                "benchmarkLookbackReturnPct": round(bench_ret, 2),
                "relativeStrengthPct": rel_strength,
                "returnPct": c["returnPct"],
            })

        xs = [e["relativeStrengthPct"] for e in enriched]
        ys = [e["returnPct"] for e in enriched]
        corr = pearson(xs, ys)

        below = [e["returnPct"] for e in enriched if e["relativeStrengthPct"] < THRESHOLD_PCT]
        above = [e["returnPct"] for e in enriched if e["relativeStrengthPct"] >= THRESHOLD_PCT]

        results_by_lookback[str(lb)] = {
            "lookbackWeeks": lb,
            "totalValidCycles": len(enriched),
            "skippedCycles": skipped,
            "correlation": {"pearson_relativeStrengthPct_vs_returnPct": round(corr, 3) if corr is not None else None},
            "byThreshold": {
                str(THRESHOLD_PCT): {"belowThreshold": summarize(below), "atOrAboveThreshold": summarize(above)}
            },
            "cycles": enriched,
        }

    output = {
        "generatedBy": "research/scripts/track-a-macro/sector_relative_strength_entry_filter_backtest.py",
        "hypothesis": "진입 시점 직전 N주간 기초지수의 SPY 대비 상대강도가 강할수록(모멘텀 확인) 이후 사이클 수익률이 좋다.",
        "underlyingMap": UNDERLYING_MAP,
        "benchmark": BENCHMARK,
        "preRegisteredThresholdPct": THRESHOLD_PCT,
        "byLookback": results_by_lookback,
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A - 섹터 상대강도 진입필터 - 요약")
    print("=" * 100)
    for lb, res in output["byLookback"].items():
        print(f"\n[lookback={lb}주] 유효 사이클: {res['totalValidCycles']}건 (스킵 {len(res['skippedCycles'])}건)")
        print(f"  피어슨 상관계수: {res['correlation']['pearson_relativeStrengthPct_vs_returnPct']}")
        thr_res = res["byThreshold"][str(output["preRegisteredThresholdPct"])]
        b, a = thr_res["belowThreshold"], thr_res["atOrAboveThreshold"]
        print(f"  미만: n={b['n']}, 승률={b['winRatePct']}%, 평균={b['avgReturnPct']}%, 중앙값={b['medianReturnPct']}%")
        print(f"  이상: n={a['n']}, 승률={a['winRatePct']}%, 평균={a['avgReturnPct']}%, 중앙값={a['medianReturnPct']}%")


if __name__ == "__main__":
    run_all()

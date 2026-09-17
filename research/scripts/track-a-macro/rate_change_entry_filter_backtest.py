#!/usr/bin/env python3
"""Track A 매크로 오버레이 두 번째 가설 - 진입 직전 10년물 금리 변화율 필터.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**배경**: `research/reports/track-a-vix-entry-filter-hypothesis.md`가 진입 시점 VIX
"레벨"은 이후 사이클 수익률과 무상관(상관계수 -0.006)임을 확인해 기각했다. VIX는
가격 추세와 다른 축이지만, "레벨"이 아니라 "변화율"을 봐야 신호가 있을 수 있고,
VIX와 별개의 매크로 축(금리)도 아직 검증하지 않았다.

**가설**: 레버리지 성장주 ETF(SOXL/TQQQ/TNA/FAS)는 할인율(장기금리)에 민감하다 -
**진입 직전 8주간 10년물 국채금리(DGS10)가 급등했을수록 이후 사이클 수익률이**
**나쁘다**고 가정한다. 금리 급등은 성장주 밸류에이션 압박(할인율 상승)과 레버리지
상품의 조달비용 상승을 동시에 의미하므로 VIX 레벨보다 더 직접적인 메커니즘을
가질 수 있다.

**지표 정의(실행 전 고정)**: `rateChangeBps` = (진입 주 DGS10 종가 - 진입 8주 전
DGS10 종가) * 100. 기준일이 휴장일이면 그 날짜 이전 가장 최근 거래일 값을 쓴다
(DGS10은 일봉이므로, 진입 주봉 날짜에 맞춰 최근접 과거 값을 취한다 - VIX 검증과
동일한 근사 방식).

**임계값(결과를 보기 전에 고정)**: 1차 rateChangeBps>=+50(0.5%p 이상 급등), 2차
rateChangeBps>=+100(1%p 이상 급등, 강건성 확인용). 임계값과 무관한 검증으로
rateChangeBps와 사이클 수익률의 피어슨 상관계수도 함께 본다.

**적용 대상·사이클 정의**: VIX 리포트와 완전히 동일한 42개 유효 사이클(SOXL/TQQQ/
TNA/FAS, CROSS_UP delay=0 진입 → CROSS_DOWN 청산)을 `vix_entry_filter_results.json`
에서 그대로 재사용한다 - 사이클 정의를 다시 계산하지 않고 entryDate/exitDate/
returnPct를 그대로 가져와 금리 변화율만 새로 계산해 붙인다.

**데이터**: FRED DGS10(미국 10년물 국채금리, 일봉, 무료·키 불필요,
`research/reports/macro-data-provider-feasibility.md`에서 이미 접근성 확인).

실행: python3 research/scripts/track-a-macro/rate_change_entry_filter_backtest.py
"""
import csv
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
DGS10_PATH = os.path.join(BASE_DIR, "raw", "DGS10.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "rate_change_entry_filter_results.json")

LOOKBACK_WEEKS = 8
THRESHOLDS_BPS = [50.0, 100.0]


def load_dgs10(path):
    """반환: 날짜순 정렬된 (date, rate) 리스트. 결측치('.')는 제외."""
    rows = []
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            val = row["DGS10"]
            if val in (".", "", None):
                continue
            rows.append((row["observation_date"], float(val)))
    rows.sort(key=lambda x: x[0])
    return rows


def rate_asof(dgs10_sorted, dates_index, as_of_date):
    """as_of_date 이전(포함) 가장 최근 거래일 금리값. 못 찾으면 None."""
    import bisect
    idx = bisect.bisect_right(dates_index, as_of_date) - 1
    if idx < 0:
        return None
    return dgs10_sorted[idx][1]


def weeks_before(date_str, weeks):
    from datetime import datetime, timedelta
    d = datetime.strptime(date_str, "%Y-%m-%d") - timedelta(weeks=weeks)
    return d.strftime("%Y-%m-%d")


def pearson(xs, ys):
    n = len(xs)
    if n < 2:
        return None
    mx = sum(xs) / n
    my = sum(ys) / n
    cov = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    varx = sum((x - mx) ** 2 for x in xs)
    vary = sum((y - my) ** 2 for y in ys)
    if varx <= 0 or vary <= 0:
        return None
    return cov / (varx ** 0.5 * vary ** 0.5)


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]
    dgs10 = load_dgs10(DGS10_PATH)
    dates_index = [d for d, _ in dgs10]

    enriched = []
    skipped = []
    for c in cycles:
        entry_date = c["entryDate"]
        prior_date = weeks_before(entry_date, LOOKBACK_WEEKS)
        rate_entry = rate_asof(dgs10, dates_index, entry_date)
        rate_prior = rate_asof(dgs10, dates_index, prior_date)
        if rate_entry is None or rate_prior is None:
            skipped.append({"ticker": c["ticker"], "entryDate": entry_date, "reason": "MISSING_RATE_DATA"})
            continue
        rate_change_bps = round((rate_entry - rate_prior) * 100.0, 2)
        enriched.append({
            "ticker": c["ticker"], "entryDate": entry_date, "exitDate": c["exitDate"],
            "rateAtEntry": rate_entry, "rateAt8WeeksBefore": rate_prior,
            "rateChangeBps": rate_change_bps, "returnPct": c["returnPct"],
        })

    xs = [c["rateChangeBps"] for c in enriched]
    ys = [c["returnPct"] for c in enriched]
    corr = pearson(xs, ys)

    by_threshold = {}
    for thr in THRESHOLDS_BPS:
        below = [c["returnPct"] for c in enriched if c["rateChangeBps"] < thr]
        above = [c["returnPct"] for c in enriched if c["rateChangeBps"] >= thr]

        def summarize(vals):
            if not vals:
                return {"n": 0, "winRatePct": None, "avgReturnPct": None, "medianReturnPct": None}
            wins = sum(1 for v in vals if v > 0)
            svals = sorted(vals)
            mid = len(svals) // 2
            median = svals[mid] if len(svals) % 2 == 1 else (svals[mid - 1] + svals[mid]) / 2.0
            return {
                "n": len(vals),
                "winRatePct": round(100.0 * wins / len(vals), 1),
                "avgReturnPct": round(sum(vals) / len(vals), 2),
                "medianReturnPct": round(median, 2),
            }

        by_threshold[str(thr)] = {"belowThreshold": summarize(below), "atOrAboveThreshold": summarize(above)}

    output = {
        "generatedBy": "research/scripts/track-a-macro/rate_change_entry_filter_backtest.py",
        "hypothesis": "Track A 진입(CROSS_UP delay=0) 직전 8주간 10년물 금리(DGS10) 변화가 클수록(급등) 이후 사이클 수익률이 나쁘다.",
        "lookbackWeeks": LOOKBACK_WEEKS,
        "preRegisteredThresholdsBps": THRESHOLDS_BPS,
        "tickers": sorted({c["ticker"] for c in enriched}),
        "totalValidCycles": len(enriched),
        "skippedCycles": skipped,
        "correlation": {"pearson_rateChangeBps_vs_returnPct": round(corr, 3) if corr is not None else None},
        "byThreshold": by_threshold,
        "cycles": enriched,
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A - 금리 변화율 진입필터 - 요약")
    print("=" * 100)
    print(f"유효 사이클: {output['totalValidCycles']}건 (스킵: {len(output['skippedCycles'])}건)")
    print(f"피어슨 상관계수(rateChangeBps vs returnPct): {output['correlation']['pearson_rateChangeBps_vs_returnPct']}")
    for thr, res in output["byThreshold"].items():
        print(f"\n[임계값 {thr}bps]")
        b, a = res["belowThreshold"], res["atOrAboveThreshold"]
        print(f"  미만: n={b['n']}, 승률={b['winRatePct']}%, 평균={b['avgReturnPct']}%, 중앙값={b['medianReturnPct']}%")
        print(f"  이상: n={a['n']}, 승률={a['winRatePct']}%, 평균={a['avgReturnPct']}%, 중앙값={a['medianReturnPct']}%")


if __name__ == "__main__":
    run_all()

#!/usr/bin/env python3
"""Track A 매크로 오버레이 다섯 번째 가설 - 진입 시점 신용스프레드(회사채-국채) 필터.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**배경**: VIX 레벨(진입)·금리 변화율(진입)·VIX 급등(보유 중 즉시/지연확인청산)
네 가지 매크로 가설이 모두 명확한 채택 근거를 내지 못했다. 이 넷은 전부 "주가
지수 기반 변동성(VIX)" 아니면 "무위험금리(DGS10)"였다 - **신용시장(credit
market)** 축은 아직 검증하지 않았다. 신용스프레드는 VIX(주식시장 심리)와도,
국채금리(무위험금리 수준)와도 다른 정보를 담는다 - 기업의 부도위험/유동성
경색을 반영하며, 역사적으로 VIX보다 더 느리게 움직이고 더 오래 지속되는 경향이
있다고 알려져 있다(2008년 금융위기 등에서 신용스프레드가 먼저 벌어지고 오래
유지됨).

**가설**: 진입 시점의 신용스프레드(Baa 회사채 수익률 - 10년물 국채 수익률,
`BAA10Y`)가 높을수록(신용경색) 이후 사이클 수익률이 나쁘다.

**데이터**: FRED `BAA10Y`(Moody's Baa 회사채 수익률의 10년물 국채 대비 스프레드,
일봉, 무료·키 불필요, 1986~2026 전 구간 확보 - `BAMLH0A0HYM2`(ICE BofA HY OAS)를
먼저 시도했으나 FRED 재배포 라이선스 제약으로 **2023-09-18 이후 데이터만 제공**
되어(과거 데이터 접근 불가) 이번 검증(2012~2026)에는 쓸 수 없었다 - 이 발견도
`research/reports/macro-data-provider-feasibility.md`에 반영할 가치가 있는
신규 정보다.

**임계값(결과를 보기 전에 고정, 역사적 관행 기준 - 이번 사이클 데이터를 보지**
**않고 고정)**: 1차 BAA10Y>=3.0%p(스프레드 확대 국면), 2차 BAA10Y>=4.0%p(위기
수준, 2020년 코로나 국면이 대략 이 수준까지 도달했던 것으로 알려짐 - 강건성
확인용). 임계값과 무관한 검증으로 피어슨 상관계수도 함께 본다.

**적용 대상·사이클 정의**: 앞선 네 가설과 완전히 동일한 42개 유효 사이클(SOXL/
TQQQ/TNA/FAS, CROSS_UP delay=0 진입→CROSS_DOWN 청산) 재사용, 진입 시점 값은
그 주 또는 직전 최근 거래일 값으로 근사(VIX/금리 검증과 동일한 방식).

실행: python3 research/scripts/track-a-macro/credit_spread_entry_filter_backtest.py
"""
import bisect
import csv
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
BAA10Y_PATH = os.path.join(BASE_DIR, "raw", "BAA10Y.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "credit_spread_entry_filter_results.json")

THRESHOLDS = [3.0, 4.0]


def load_series(path, col):
    rows = []
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            val = row[col]
            if val in (".", "", None):
                continue
            rows.append((row["observation_date"], float(val)))
    rows.sort(key=lambda x: x[0])
    return rows


def value_asof(series_sorted, dates_index, as_of_date):
    idx = bisect.bisect_right(dates_index, as_of_date) - 1
    if idx < 0:
        return None
    return series_sorted[idx][1]


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


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]
    baa10y = load_series(BAA10Y_PATH, "BAA10Y")
    dates_index = [d for d, _ in baa10y]

    enriched = []
    skipped = []
    for c in cycles:
        entry_date = c["entryDate"]
        spread = value_asof(baa10y, dates_index, entry_date)
        if spread is None:
            skipped.append({"ticker": c["ticker"], "entryDate": entry_date, "reason": "MISSING_SPREAD_DATA"})
            continue
        enriched.append({
            "ticker": c["ticker"], "entryDate": entry_date, "exitDate": c["exitDate"],
            "entryCreditSpread": spread, "returnPct": c["returnPct"],
        })

    xs = [c["entryCreditSpread"] for c in enriched]
    ys = [c["returnPct"] for c in enriched]
    corr = pearson(xs, ys)

    by_threshold = {}
    for thr in THRESHOLDS:
        below = [c["returnPct"] for c in enriched if c["entryCreditSpread"] < thr]
        above = [c["returnPct"] for c in enriched if c["entryCreditSpread"] >= thr]

        def summarize(vals):
            if not vals:
                return {"n": 0, "winRatePct": None, "avgReturnPct": None, "medianReturnPct": None}
            wins = sum(1 for v in vals if v > 0)
            svals = sorted(vals)
            mid = len(svals) // 2
            median = svals[mid] if len(svals) % 2 == 1 else (svals[mid - 1] + svals[mid]) / 2.0
            return {"n": len(vals), "winRatePct": round(100.0 * wins / len(vals), 1),
                    "avgReturnPct": round(sum(vals) / len(vals), 2), "medianReturnPct": round(median, 2)}

        by_threshold[str(thr)] = {"belowThreshold": summarize(below), "atOrAboveThreshold": summarize(above)}

    output = {
        "generatedBy": "research/scripts/track-a-macro/credit_spread_entry_filter_backtest.py",
        "hypothesis": "Track A 진입(CROSS_UP delay=0) 시점 신용스프레드(BAA10Y)가 높을수록(신용경색) 이후 사이클 수익률이 나쁘다.",
        "dataSourceNote": "BAMLH0A0HYM2(ICE BofA HY OAS)는 FRED 재배포 제약으로 2023-09-18 이후만 제공되어 이번 검증(2012-2026)에 쓸 수 없었음 - BAA10Y(Moody's Baa-10Y Treasury spread, 1986~)로 대체.",
        "preRegisteredThresholds": THRESHOLDS,
        "tickers": sorted({c["ticker"] for c in enriched}),
        "totalValidCycles": len(enriched),
        "skippedCycles": skipped,
        "correlation": {"pearson_entryCreditSpread_vs_returnPct": round(corr, 3) if corr is not None else None},
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
    print("Track A - 신용스프레드 진입필터 - 요약")
    print("=" * 100)
    print(f"유효 사이클: {output['totalValidCycles']}건 (스킵: {len(output['skippedCycles'])}건)")
    print(f"피어슨 상관계수: {output['correlation']['pearson_entryCreditSpread_vs_returnPct']}")
    for thr, res in output["byThreshold"].items():
        print(f"\n[임계값 {thr}]")
        b, a = res["belowThreshold"], res["atOrAboveThreshold"]
        print(f"  미만: n={b['n']}, 승률={b['winRatePct']}%, 평균={b['avgReturnPct']}%, 중앙값={b['medianReturnPct']}%")
        print(f"  이상: n={a['n']}, 승률={a['winRatePct']}%, 평균={a['avgReturnPct']}%, 중앙값={a['medianReturnPct']}%")


if __name__ == "__main__":
    run_all()

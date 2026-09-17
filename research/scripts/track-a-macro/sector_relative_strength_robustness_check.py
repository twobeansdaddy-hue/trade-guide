#!/usr/bin/env python3
"""Track A 섹터 상대강도 가설 - 강건성 추가 검증(lookback 민감도 + 시간분할).

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
`track-a-sector-relative-strength-entry-filter-hypothesis.md`가 13주 lookback
에서 상관계수+임계값 비교가 처음으로 방향 일치를 보였지만(+0.112), 26주에서는
거의 사라졌다(-0.021)는 것을 확인했다. 그 리포트의 "다음 단계"가 제안한 두 가지
추가 검증을 이번에 수행한다 - **둘 다 이번 스크립트 실행 전에 파라미터를 고정**
했고 결과를 보고 바꾸지 않는다.

**검증 (a) - lookback 민감도**: 8/13/17/26주 네 lookback 전부에서 상관계수와
5%p 임계값 비교를 재계산한다. 13주 근방(8, 17)에서도 양(+)의 방향이 유지되는지,
아니면 13주만의 우연인지 확인한다.

**검증 (b) - 시간분할(워크포워드 유사)**: 42개 사이클을 진입일(entryDate) 기준
오름차순 정렬해 정확히 절반(앞 21건/뒤 21건)으로 나눈다. 13주 lookback(원 가설의
lookback)을 기준으로 각 절반에서 독립적으로 상관계수와 5%p 임계값 비교를
계산한다. **판정 기준(고정)**: 두 절반 모두에서 상관계수가 양(+)이고 임계값
이상 그룹의 평균수익률이 미만 그룹보다 높으면 "강건성 확인", 한쪽이라도 방향이
반대이거나 상관계수가 유의미하게(부호가 바뀔 정도로) 다르면 "국면 의존적 -
추가 채택 보류".

실행: python3 research/scripts/track-a-macro/sector_relative_strength_robustness_check.py
"""
import bisect
import csv
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
UNIVERSE_CSV = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "sector_relative_strength_robustness_results.json")

UNDERLYING_MAP = {"SOXL": "SOXX", "TQQQ": "QQQ", "TNA": "IWM", "FAS": "XLF"}
BENCHMARK = "SPY"
LOOKBACKS_FOR_SENSITIVITY = [8, 13, 17, 26]
SPLIT_LOOKBACK = 13
THRESHOLD_PCT = 5.0


def load_universe_csv(path):
    series = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            series.setdefault(row["ticker"], {})[row["datetime"]] = float(row["close"])
    return series


def lookback_return(price_series, sorted_dates, as_of_date, weeks):
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


def enrich_cycles(cycles, series, sorted_dates, lb):
    enriched = []
    for c in cycles:
        ticker = c["ticker"]
        underlying = UNDERLYING_MAP[ticker]
        entry_date = c["entryDate"]
        underlying_ret = lookback_return(series[underlying], sorted_dates[underlying], entry_date, lb)
        bench_ret = lookback_return(series[BENCHMARK], sorted_dates[BENCHMARK], entry_date, lb)
        if underlying_ret is None or bench_ret is None:
            continue
        rel_strength = round(underlying_ret - bench_ret, 3)
        enriched.append({
            "ticker": ticker, "entryDate": entry_date,
            "relativeStrengthPct": rel_strength, "returnPct": c["returnPct"],
        })
    return enriched


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]
    series = load_universe_csv(UNIVERSE_CSV)
    sorted_dates = {t: sorted(prices.keys()) for t, prices in series.items()}

    # 검증 (a): lookback 민감도
    sensitivity = {}
    for lb in LOOKBACKS_FOR_SENSITIVITY:
        enriched = enrich_cycles(cycles, series, sorted_dates, lb)
        xs = [e["relativeStrengthPct"] for e in enriched]
        ys = [e["returnPct"] for e in enriched]
        corr = pearson(xs, ys)
        below = [e["returnPct"] for e in enriched if e["relativeStrengthPct"] < THRESHOLD_PCT]
        above = [e["returnPct"] for e in enriched if e["relativeStrengthPct"] >= THRESHOLD_PCT]
        sensitivity[str(lb)] = {
            "n": len(enriched),
            "correlation": round(corr, 3) if corr is not None else None,
            "belowThreshold": summarize(below),
            "atOrAboveThreshold": summarize(above),
        }

    # 검증 (b): 시간분할
    enriched_13 = enrich_cycles(cycles, series, sorted_dates, SPLIT_LOOKBACK)
    enriched_13_sorted = sorted(enriched_13, key=lambda e: e["entryDate"])
    half = len(enriched_13_sorted) // 2
    first_half = enriched_13_sorted[:half]
    second_half = enriched_13_sorted[half:]

    def split_summary(subset):
        xs = [e["relativeStrengthPct"] for e in subset]
        ys = [e["returnPct"] for e in subset]
        corr = pearson(xs, ys)
        below = [e["returnPct"] for e in subset if e["relativeStrengthPct"] < THRESHOLD_PCT]
        above = [e["returnPct"] for e in subset if e["relativeStrengthPct"] >= THRESHOLD_PCT]
        return {
            "n": len(subset),
            "dateRange": [subset[0]["entryDate"], subset[-1]["entryDate"]] if subset else None,
            "correlation": round(corr, 3) if corr is not None else None,
            "belowThreshold": summarize(below),
            "atOrAboveThreshold": summarize(above),
        }

    first_summary = split_summary(first_half)
    second_summary = split_summary(second_half)

    def direction_positive(s):
        if s["correlation"] is None:
            return None
        avg_below = s["belowThreshold"]["avgReturnPct"]
        avg_above = s["atOrAboveThreshold"]["avgReturnPct"]
        if avg_below is None or avg_above is None:
            return None
        return s["correlation"] > 0 and avg_above > avg_below

    first_positive = direction_positive(first_summary)
    second_positive = direction_positive(second_summary)
    robust = bool(first_positive) and bool(second_positive)

    output = {
        "generatedBy": "research/scripts/track-a-macro/sector_relative_strength_robustness_check.py",
        "sensitivityByLookback": sensitivity,
        "temporalSplit": {
            "splitLookbackWeeks": SPLIT_LOOKBACK,
            "firstHalf": first_summary,
            "secondHalf": second_summary,
            "firstHalfDirectionPositive": first_positive,
            "secondHalfDirectionPositive": second_positive,
            "verdict": "강건성 확인" if robust else "국면 의존적 - 추가 채택 보류",
        },
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A - 섹터 상대강도 강건성 검증 - 요약")
    print("=" * 100)
    print("\n[검증 (a) lookback 민감도]")
    for lb, s in output["sensitivityByLookback"].items():
        b, a = s["belowThreshold"], s["atOrAboveThreshold"]
        print(f"  {lb:>3}주: n={s['n']}, corr={s['correlation']}, "
              f"미만(n={b['n']} avg={b['avgReturnPct']}%) vs 이상(n={a['n']} avg={a['avgReturnPct']}%)")
    ts = output["temporalSplit"]
    print(f"\n[검증 (b) 시간분할, lookback={ts['splitLookbackWeeks']}주]")
    for label, s in [("앞 절반", ts["firstHalf"]), ("뒤 절반", ts["secondHalf"])]:
        print(f"  {label} ({s['dateRange']}): n={s['n']}, corr={s['correlation']}, "
              f"미만avg={s['belowThreshold']['avgReturnPct']}%, 이상avg={s['atOrAboveThreshold']['avgReturnPct']}%")
    print(f"  앞 절반 방향 양(+): {ts['firstHalfDirectionPositive']}, 뒤 절반 방향 양(+): {ts['secondHalfDirectionPositive']}")
    print(f"  판정: {ts['verdict']}")


if __name__ == "__main__":
    run_all()

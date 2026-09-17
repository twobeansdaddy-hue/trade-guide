#!/usr/bin/env python3
"""Track A 매크로 오버레이 세 번째 가설 - 보유 중 VIX 급등 조기청산(리스크관리 레이어).

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**배경**: 앞선 두 가설(진입 시점 VIX 레벨, 진입 직전 금리 변화율)이 모두 기각됐다
- 공통 해석은 "Track A의 지연 진입 신호(10/40주 SMA 골든크로스)가 확정되는 시점에는
  매크로 충격이 이미 지나간 뒤인 경우가 많아, 진입 시점 매크로 지표에 예측력이
  없다"는 것이었다. 이번 가설은 **진입 시점이 아니라 보유 기간 중** 매크로 신호를
  본다 - 진입 필터가 아니라 **리스크관리(엑싯) 레이어**로 방향을 바꾼다.

**가설**: 사이클을 보유하는 도중 VIX가 특정 수준 이상으로 급등하면(패닉/폭락
국면 진입), 원래의 CROSS_DOWN 청산 신호를 기다리지 않고 즉시 조기 청산하는 것이
- 원래 규칙(CROSS_DOWN까지 보유)보다 - 평균적으로 더 낫다(대형 손실을 회피한다).

**지표 정의(실행 전 고정)**: 각 사이클의 진입 다음 주부터(진입 당일 제외) 원래
청산일(exitDate, CROSS_DOWN 확정 주) **직전 주까지** 매주 VIX 종가를 스캔해,
임계값 이상인 첫 주를 찾는다. 그 주가 있으면 그 주의 종가로 조기 청산한
가상 수익률(`earlyExitReturnPct`)을 계산한다. 없으면 원래 사이클 그대로
(수정 없음, `earlyExitReturnPct` = `returnPct`).

**임계값(결과를 보기 전에 고정)**: VIX>=30(고공포), VIX>=35(강건성 확인용) 두
수준을 독립적으로 검증한다 - `track-a-vix-entry-filter-hypothesis.md`가 진입
레벨 필터에서 쓴 25/30과 겹치지 않게, 이번엔 "보유 중 급등"이라는 다른 개념에
맞춰 더 높은 두 임계값을 썼다.

**적용 대상·사이클 정의**: VIX/금리 리포트와 완전히 동일한 42개 유효 사이클
(SOXL/TQQQ/TNA/FAS, CROSS_UP delay=0 진입→CROSS_DOWN 청산)을 재사용한다.

**비교 기준**: (1) 전체 42사이클의 평균/중앙값/승률을 원래 규칙 vs 조기청산 규칙
으로 비교. (2) 원래 규칙에서 대형 손실(-30% 이하)이었던 사이클들에서 조기청산이
손실을 줄였는지 개별 확인. (3) 원래 규칙에서 대형 이익이었던 사이클에서 조기청산이
이익을 얼마나 깎았는지(기회비용)도 함께 본다 - 급등 이후 반등하는 경우가 있으면
조기청산이 오히려 손해일 수 있다.

실행: python3 research/scripts/track-a-macro/vix_spike_hold_exit_backtest.py
"""
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
UNIVERSE_CSV = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "vix_spike_hold_exit_results.json")

VIX_SPIKE_THRESHOLDS = [30.0, 35.0]


def load_universe_csv(path):
    import csv
    series = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            series.setdefault(row["ticker"], {})[row["datetime"]] = float(row["close"])
    return series


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]
    series = load_universe_csv(UNIVERSE_CSV)
    vix_prices = series["VIX"]
    vix_dates = sorted(vix_prices.keys())

    results_by_threshold = {}
    for thr in VIX_SPIKE_THRESHOLDS:
        enriched = []
        for c in cycles:
            ticker = c["ticker"]
            entry_date, exit_date = c["entryDate"], c["exitDate"]
            price_series = series[ticker]
            ticker_dates = sorted(price_series.keys())

            hold_weeks = [d for d in ticker_dates if entry_date < d < exit_date]
            trigger_date = None
            for d in hold_weeks:
                vix_val = vix_prices.get(d)
                if vix_val is None:
                    past = [vd for vd in vix_dates if vd <= d]
                    vix_val = vix_prices[past[-1]] if past else None
                if vix_val is not None and vix_val >= thr:
                    trigger_date = d
                    break

            entry_price = price_series[entry_date]
            if trigger_date is not None:
                early_exit_price = price_series[trigger_date]
                early_exit_return = round((early_exit_price / entry_price - 1.0) * 100.0, 2)
            else:
                early_exit_return = c["returnPct"]

            enriched.append({
                "ticker": ticker, "entryDate": entry_date, "exitDate": exit_date,
                "originalReturnPct": c["returnPct"],
                "vixSpikeTriggerDate": trigger_date,
                "earlyExitReturnPct": early_exit_return,
                "improvement": round(early_exit_return - c["returnPct"], 2),
            })

        n = len(enriched)
        triggered = [e for e in enriched if e["vixSpikeTriggerDate"] is not None]
        orig_vals = [e["originalReturnPct"] for e in enriched]
        early_vals = [e["earlyExitReturnPct"] for e in enriched]

        def summarize(vals):
            wins = sum(1 for v in vals if v > 0)
            svals = sorted(vals)
            mid = len(svals) // 2
            median = svals[mid] if len(svals) % 2 == 1 else (svals[mid - 1] + svals[mid]) / 2.0
            return {"n": len(vals), "winRatePct": round(100.0 * wins / len(vals), 1),
                    "avgReturnPct": round(sum(vals) / len(vals), 2), "medianReturnPct": round(median, 2)}

        large_losses_orig = [e for e in enriched if e["originalReturnPct"] <= -30.0]
        large_gains_orig = [e for e in enriched if e["originalReturnPct"] >= 100.0]

        results_by_threshold[str(thr)] = {
            "totalCycles": n,
            "triggeredCount": len(triggered),
            "triggeredCycles": triggered,
            "original": summarize(orig_vals),
            "withEarlyExit": summarize(early_vals),
            "avgImprovementPctPoints": round(sum(e["improvement"] for e in enriched) / n, 2),
            "largeLossCyclesOriginal": [
                {"ticker": e["ticker"], "entryDate": e["entryDate"], "originalReturnPct": e["originalReturnPct"],
                 "earlyExitReturnPct": e["earlyExitReturnPct"], "improvement": e["improvement"]}
                for e in large_losses_orig
            ],
            "largeGainCyclesOriginal": [
                {"ticker": e["ticker"], "entryDate": e["entryDate"], "originalReturnPct": e["originalReturnPct"],
                 "earlyExitReturnPct": e["earlyExitReturnPct"], "improvement": e["improvement"]}
                for e in large_gains_orig
            ],
        }

    output = {
        "generatedBy": "research/scripts/track-a-macro/vix_spike_hold_exit_backtest.py",
        "hypothesis": "보유 중 VIX가 임계값 이상으로 급등하면 원래 청산 신호(CROSS_DOWN)를 기다리지 않고 조기 청산하는 것이 평균적으로 낫다.",
        "preRegisteredThresholds": VIX_SPIKE_THRESHOLDS,
        "totalValidCycles": len(cycles),
        "byThreshold": results_by_threshold,
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A - 보유 중 VIX 급등 조기청산 - 요약")
    print("=" * 100)
    for thr, res in output["byThreshold"].items():
        print(f"\n[임계값 VIX>={thr}] 발동 사이클: {res['triggeredCount']}/{res['totalCycles']}")
        o, e = res["original"], res["withEarlyExit"]
        print(f"  원래 규칙:   승률={o['winRatePct']}%, 평균={o['avgReturnPct']}%, 중앙값={o['medianReturnPct']}%")
        print(f"  조기청산:    승률={e['winRatePct']}%, 평균={e['avgReturnPct']}%, 중앙값={e['medianReturnPct']}%")
        print(f"  평균 개선폭: {res['avgImprovementPctPoints']}%p")
        print(f"  원래 대형손실(-30%이하) 사이클 수: {len(res['largeLossCyclesOriginal'])}")
        for c in res["largeLossCyclesOriginal"]:
            print(f"    {c['ticker']} {c['entryDate']}: {c['originalReturnPct']}% -> {c['earlyExitReturnPct']}% (개선 {c['improvement']}%p)")
        print(f"  원래 대형이익(+100%이상) 사이클 수: {len(res['largeGainCyclesOriginal'])}")
        for c in res["largeGainCyclesOriginal"]:
            print(f"    {c['ticker']} {c['entryDate']}: {c['originalReturnPct']}% -> {c['earlyExitReturnPct']}% (개선 {c['improvement']}%p)")


if __name__ == "__main__":
    run_all()

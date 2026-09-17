#!/usr/bin/env python3
"""Track A 매크로 오버레이 네 번째 가설 - 보유 중 VIX 급등 "지연 확인" 조기청산.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**배경**: `track-a-vix-spike-hold-exit-hypothesis.md`가 "VIX 급등 시 즉시 청산"을
검증해 평균수익률이 악화됨을 확인했다(-5.02%p @VIX30). 원인은 명확했다 - 2020년
코로나처럼 VIX 급등 직후 시장이 V자로 급반등하는 경우, 즉시 청산이 패닉의 바닥
근처에서 손절하고 그 직후 대형 반등(레버리지 ETF 특유의 극적인 반등)을 통째로
놓쳤다(4건, -119~-138%p). 그 리포트의 "다음 리서치 제안"이 명시한 대로, 이번
가설은 **즉시 청산 대신 지연 확인**을 추가한다 - "급등 후 며칠 지켜봐서 반등하지
않고 계속 나쁘면 그때 청산"하면 V자 반등 오탐(false positive)을 피하면서도 지속
하락 국면에서는 여전히 손실을 줄일 수 있을 것이라는 가설이다.

**가설**: 보유 중 VIX가 임계값 이상으로 급등한 시점(트리거 주)으로부터 K주 후,
가격이 트리거 시점보다 **여전히 낮으면**(반등하지 못하고 계속 하락) 그 시점에
청산한다. K주 후 가격이 트리거 시점보다 **이미 회복(>=)**됐으면 청산하지 않고
원래 사이클(CROSS_DOWN까지 보유)을 그대로 유지한다. 이 "지연 확인" 규칙이
`track-a-vix-spike-hold-exit-hypothesis.md`의 "즉시 청산" 규칙보다 평균수익률이
낫다고 가정한다.

**지표 정의(실행 전 고정)**: 트리거 주 = 앞선 리포트와 동일한 방식(보유 중 VIX가
임계값을 처음 넘는 주). 확인 시점 = 트리거 주 + K주(K주 후 시점이 원래 청산일을
지나면, 원래 청산일의 가격으로 대체 확인 - 사이클 밖으로 나가지 않는다). 확인
시점 가격이 트리거 시점 가격보다 낮으면 그 확인 시점 가격으로 청산, 아니면
청산하지 않음(원래 사이클 유지, 이후 재트리거는 스캔하지 않음 - 앞선 리포트와
동일하게 사이클당 "첫 트리거 1회"만 판단).

**임계값·K(결과를 보기 전에 고정)**: VIX 임계값은 앞선 리포트와 동일하게 30을
1차로 쓴다(35는 트리거 빈도가 같았으므로 반복하지 않는다). 확인 지연 K는 3주
(1차)와 6주(2차, 강건성 확인)를 독립적으로 검증한다.

**적용 대상**: 동일한 42개 유효 사이클(SOXL/TQQQ/TNA/FAS) 재사용.

**비교 기준**: (1) 원래 규칙(CROSS_DOWN까지 보유) vs 즉시청산(기존 리포트) vs
지연확인청산(이번) 3자 비교. (2) 앞선 리포트가 악화시켰던 2020년 4개 대형이익
사이클에서 지연확인이 그 훼손을 되돌리는지 개별 확인. (3) 앞선 리포트가 개선시켰던
2019년 3개 대형손실 사이클에서 지연확인이 그 개선을 유지하는지 확인(너무 오래
기다리면 개선 효과가 사라질 수 있음).

실행: python3 research/scripts/track-a-macro/vix_spike_delayed_confirmation_exit_backtest.py
"""
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
UNIVERSE_CSV = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "vix_spike_delayed_confirmation_exit_results.json")

VIX_THRESHOLD = 30.0
CONFIRMATION_LAG_WEEKS = [3, 6]


def load_universe_csv(path):
    import csv
    series = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            series.setdefault(row["ticker"], {})[row["datetime"]] = float(row["close"])
    return series


def find_trigger(hold_weeks, vix_prices, vix_dates, threshold):
    for d in hold_weeks:
        vix_val = vix_prices.get(d)
        if vix_val is None:
            past = [vd for vd in vix_dates if vd <= d]
            vix_val = vix_prices[past[-1]] if past else None
        if vix_val is not None and vix_val >= threshold:
            return d
    return None


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]
    series = load_universe_csv(UNIVERSE_CSV)
    vix_prices = series["VIX"]
    vix_dates = sorted(vix_prices.keys())

    results_by_lag = {}
    for lag in CONFIRMATION_LAG_WEEKS:
        enriched = []
        for c in cycles:
            ticker = c["ticker"]
            entry_date, exit_date = c["entryDate"], c["exitDate"]
            price_series = series[ticker]
            ticker_dates = sorted(price_series.keys())

            hold_weeks = [d for d in ticker_dates if entry_date < d < exit_date]
            trigger_date = find_trigger(hold_weeks, vix_prices, vix_dates, VIX_THRESHOLD)

            entry_price = price_series[entry_date]
            immediate_exit_return = c["returnPct"]
            if trigger_date is not None:
                immediate_exit_return = round((price_series[trigger_date] / entry_price - 1.0) * 100.0, 2)

            delayed_exit_return = c["returnPct"]
            confirmed = None
            if trigger_date is not None:
                trigger_idx = ticker_dates.index(trigger_date)
                confirm_idx = min(trigger_idx + lag, len(ticker_dates) - 1)
                confirm_date = ticker_dates[confirm_idx]
                if confirm_date >= exit_date:
                    confirm_date = exit_date
                trigger_price = price_series[trigger_date]
                confirm_price = price_series[confirm_date]
                if confirm_price < trigger_price:
                    confirmed = True
                    delayed_exit_return = round((confirm_price / entry_price - 1.0) * 100.0, 2)
                else:
                    confirmed = False
                    delayed_exit_return = c["returnPct"]  # 청산하지 않음, 원래 사이클 유지

            enriched.append({
                "ticker": ticker, "entryDate": entry_date, "exitDate": exit_date,
                "originalReturnPct": c["returnPct"],
                "vixSpikeTriggerDate": trigger_date,
                "immediateExitReturnPct": immediate_exit_return,
                "confirmed": confirmed,
                "delayedExitReturnPct": delayed_exit_return,
            })

        n = len(enriched)

        def summarize(vals):
            wins = sum(1 for v in vals if v > 0)
            svals = sorted(vals)
            mid = len(svals) // 2
            median = svals[mid] if len(svals) % 2 == 1 else (svals[mid - 1] + svals[mid]) / 2.0
            return {"n": len(vals), "winRatePct": round(100.0 * wins / len(vals), 1),
                    "avgReturnPct": round(sum(vals) / len(vals), 2), "medianReturnPct": round(median, 2)}

        orig_vals = [e["originalReturnPct"] for e in enriched]
        immediate_vals = [e["immediateExitReturnPct"] for e in enriched]
        delayed_vals = [e["delayedExitReturnPct"] for e in enriched]

        triggered = [e for e in enriched if e["vixSpikeTriggerDate"] is not None]
        confirmed_true = [e for e in triggered if e["confirmed"] is True]
        confirmed_false = [e for e in triggered if e["confirmed"] is False]

        key_2020_gain_cycles = [
            e for e in enriched
            if e["originalReturnPct"] >= 100.0 and e["entryDate"].startswith(("2019", "2020"))
            and e["immediateExitReturnPct"] != e["originalReturnPct"]
        ]
        key_2019_loss_cycles = [e for e in enriched if e["originalReturnPct"] <= -30.0]

        results_by_lag[str(lag)] = {
            "vixThreshold": VIX_THRESHOLD,
            "confirmationLagWeeks": lag,
            "triggeredCount": len(triggered),
            "confirmedExitCount": len(confirmed_true),
            "avoidedExitCount": len(confirmed_false),
            "original": summarize(orig_vals),
            "immediateExit": summarize(immediate_vals),
            "delayedConfirmationExit": summarize(delayed_vals),
            "avgImprovementVsOriginalPctPoints": round(sum(e["delayedExitReturnPct"] - e["originalReturnPct"] for e in enriched) / n, 2),
            "avgImprovementVsImmediatePctPoints": round(sum(e["delayedExitReturnPct"] - e["immediateExitReturnPct"] for e in enriched) / n, 2),
            "previouslyWorsenedGainCycles": [
                {"ticker": e["ticker"], "entryDate": e["entryDate"], "originalReturnPct": e["originalReturnPct"],
                 "immediateExitReturnPct": e["immediateExitReturnPct"], "delayedExitReturnPct": e["delayedExitReturnPct"],
                 "confirmed": e["confirmed"]}
                for e in enriched if e["entryDate"] in
                {"2020-08-10", "2020-06-15", "2020-10-19", "2020-11-16"}
            ],
            "largeLossCycles": [
                {"ticker": e["ticker"], "entryDate": e["entryDate"], "originalReturnPct": e["originalReturnPct"],
                 "immediateExitReturnPct": e["immediateExitReturnPct"], "delayedExitReturnPct": e["delayedExitReturnPct"],
                 "confirmed": e["confirmed"]}
                for e in key_2019_loss_cycles
            ],
        }

    output = {
        "generatedBy": "research/scripts/track-a-macro/vix_spike_delayed_confirmation_exit_backtest.py",
        "hypothesis": "보유 중 VIX 급등 시 즉시 청산이 아니라 K주 지연 확인(여전히 하락 중일 때만 청산) 후 청산하면 즉시청산의 V자 반등 오탐 문제를 피하면서 대형손실 방어 효과는 유지한다.",
        "totalValidCycles": len(cycles),
        "byLag": results_by_lag,
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 110)
    print("Track A - 보유 중 VIX 급등 지연확인 조기청산 - 요약")
    print("=" * 110)
    for lag, res in output["byLag"].items():
        print(f"\n[K={lag}주, VIX>={res['vixThreshold']}] 발동={res['triggeredCount']}, 확인후청산={res['confirmedExitCount']}, 청산회피={res['avoidedExitCount']}")
        o, i, d = res["original"], res["immediateExit"], res["delayedConfirmationExit"]
        print(f"  원래 규칙:      승률={o['winRatePct']}%, 평균={o['avgReturnPct']}%, 중앙값={o['medianReturnPct']}%")
        print(f"  즉시청산:       승률={i['winRatePct']}%, 평균={i['avgReturnPct']}%, 중앙값={i['medianReturnPct']}%")
        print(f"  지연확인청산:   승률={d['winRatePct']}%, 평균={d['avgReturnPct']}%, 중앙값={d['medianReturnPct']}%")
        print(f"  개선폭(vs 원래): {res['avgImprovementVsOriginalPctPoints']}%p, 개선폭(vs 즉시청산): {res['avgImprovementVsImmediatePctPoints']}%p")
        print(f"  앞선 리포트가 훼손했던 2020년 대형이익 사이클:")
        for c in res["previouslyWorsenedGainCycles"]:
            print(f"    {c['ticker']} {c['entryDate']}: 원래={c['originalReturnPct']}% 즉시청산={c['immediateExitReturnPct']}% 지연확인={c['delayedExitReturnPct']}% (확인결과={c['confirmed']})")
        print(f"  대형손실(-30%이하) 사이클:")
        for c in res["largeLossCycles"]:
            print(f"    {c['ticker']} {c['entryDate']}: 원래={c['originalReturnPct']}% 즉시청산={c['immediateExitReturnPct']}% 지연확인={c['delayedExitReturnPct']}% (확인결과={c['confirmed']})")


if __name__ == "__main__":
    run_all()

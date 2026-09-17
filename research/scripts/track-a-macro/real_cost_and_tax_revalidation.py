#!/usr/bin/env python3
"""Track A 오토매매 사전검토 - 실주문 비용(슬리피지) + 해외주식 양도소득세 반영 재검증.

배경
----
사용자가 Track A 규칙(10주/40주 SMA 골든크로스, 진입 0-4주창)의 오토매매 전환을
검토하면서, 지금까지 이 세션이 인용해 온 Track A 사이클 수익률(예: 42사이클
평균 +44%)이 **거래비용도 세금도 반영하지 않은 총수익률(gross return)**이라는
점을 다시 확인해야 한다고 판단했다. 저장소 전체를 검색한 결과
(`track-a-stoploss-revalidation-and-sizing-design.md`) 편도 슬리피지 0.15%p·
수수료 0%라는 기존 가정은 있었지만, **해외주식 양도소득세는 이 프로젝트 어떤**
**리포트에도 반영된 적이 없다** - 이번 검증의 핵심 신규 기여다.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**적용 대상**: 이번 세션 다섯 매크로 가설과 동일한 42개 유효 사이클(SOXL/TQQQ/
TNA/FAS, CROSS_UP delay=0 진입→CROSS_DOWN 청산).

**비용 계층(고정)**:
1. **거래 마찰(친션) 비용**: `track-a-stoploss-revalidation-and-sizing-design.md`
   가 이미 확정한 가정을 그대로 재사용한다 - 편도 슬리피지 0.15%p, 수수료 0%
   (브로커 확정 전까지 0으로 분리), 왕복 약 0.30%p. 사이클당 진입 1회·청산 1회
   뿐이므로(Track B/C의 매주/분기 리밸런싱과 달리) 사이클 수익률에서 정액
   0.30%p를 차감하는 것으로 근사한다.
2. **해외주식 양도소득세(신규)**: 한국 거주자의 해외 상장주식(SOXL/TQQQ/TNA/FAS
   전부 미국 상장) 양도차익에는 세율 22%(소득세 20%+지방소득세 2%)가 적용되고,
   연간 250만원 기본공제가 있다(소득세법 - 본 프로젝트가 특정 브로커·세무
   자문이 아니라 일반 세법 조항을 인용). **단순화(사전 고정)**: 이 백테스트는
   %수익률 단위로만 작동해 실제 KRW 손익액(따라서 연 250만원 공제 적용 여부)을
   알 수 없으므로, **기본공제는 무시하고 친션비용 차감 후 양(+)의 수익률에만**
   **22%를 곱해 세금으로 차감**하는 보수적 근사를 쓴다(공제를 무시하면 세후
   수익률이 실제보다 다소 낮게 나와 - 보수적 방향의 편향임을 명시). 손실
   사이클은 세금 혜택 없음(같은 해 다른 이익과 상계 가능하지만 이 배치
   백테스트는 사이클별로 독립 계산하므로 상계를 반영하지 않는다 - 이 역시
   보수적 방향).

**비교 시나리오(고정)**: (A) 총수익률(gross, 기존 리포트들이 인용해 온 값),
(B) 친션비용만 반영, (C) 친션비용+양도소득세 반영(세후 순수익률 - 실제 투자자가
"가져가는" 값에 가장 가깝다).

**판정 기준**: 42사이클 평균/중앙값/승률이 (A)→(B)→(C)로 가면서 얼마나 줄어드는지
정량화한다. 특히 이번 세션 평균을 크게 끌어올렸던 대형 승자 사이클(+100%대 이상)
에서 세금의 절대적 영향이 얼마나 큰지 개별로 본다.

실행: python3 research/scripts/track-a-macro/real_cost_and_tax_revalidation.py
"""
import json
import os

BASE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro")
CYCLES_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")
OUTPUT_PATH = os.path.join(BASE_DIR, "real_cost_and_tax_revalidation_results.json")

ROUND_TRIP_FRICTION_PCT_POINTS = 0.30  # 편도 슬리피지 0.15%p x2, track-a-stoploss-revalidation-and-sizing-design.md 재사용
CAPITAL_GAINS_TAX_RATE = 0.22  # 해외주식 양도소득세 22% (기본공제 미반영, 보수적)


def summarize(vals):
    n = len(vals)
    wins = sum(1 for v in vals if v > 0)
    svals = sorted(vals)
    mid = n // 2
    median = svals[mid] if n % 2 == 1 else (svals[mid - 1] + svals[mid]) / 2.0
    return {"n": n, "winRatePct": round(100.0 * wins / n, 1),
            "avgReturnPct": round(sum(vals) / n, 2), "medianReturnPct": round(median, 2),
            "sumReturnPct": round(sum(vals), 2)}


def run_all():
    cycles_data = json.load(open(CYCLES_PATH, encoding="utf-8"))
    cycles = cycles_data["cycles"]

    enriched = []
    for c in cycles:
        gross = c["returnPct"]
        after_friction = round(gross - ROUND_TRIP_FRICTION_PCT_POINTS, 2)
        if after_friction > 0:
            tax = round(after_friction * CAPITAL_GAINS_TAX_RATE, 2)
            after_tax = round(after_friction - tax, 2)
        else:
            tax = 0.0
            after_tax = after_friction
        enriched.append({
            "ticker": c["ticker"], "entryDate": c["entryDate"], "exitDate": c["exitDate"],
            "grossReturnPct": gross,
            "afterFrictionReturnPct": after_friction,
            "taxPctPoints": tax,
            "afterFrictionAndTaxReturnPct": after_tax,
        })

    gross_vals = [e["grossReturnPct"] for e in enriched]
    friction_vals = [e["afterFrictionReturnPct"] for e in enriched]
    net_vals = [e["afterFrictionAndTaxReturnPct"] for e in enriched]

    large_winners = sorted(enriched, key=lambda e: -e["grossReturnPct"])[:10]

    output = {
        "generatedBy": "research/scripts/track-a-macro/real_cost_and_tax_revalidation.py",
        "purpose": "Track A 오토매매 검토를 위해 거래마찰비용(슬리피지)과 해외주식 양도소득세(22%)를 반영한 사이클 수익률 재계산.",
        "assumptions": {
            "roundTripFrictionPctPoints": ROUND_TRIP_FRICTION_PCT_POINTS,
            "capitalGainsTaxRate": CAPITAL_GAINS_TAX_RATE,
            "annualExemptionApplied": False,
            "lossOffsetApplied": False,
        },
        "totalCycles": len(enriched),
        "scenarioA_gross": summarize(gross_vals),
        "scenarioB_afterFriction": summarize(friction_vals),
        "scenarioC_afterFrictionAndTax": summarize(net_vals),
        "top10WinnersImpact": [
            {"ticker": e["ticker"], "entryDate": e["entryDate"],
             "grossReturnPct": e["grossReturnPct"], "taxPctPoints": e["taxPctPoints"],
             "afterFrictionAndTaxReturnPct": e["afterFrictionAndTaxReturnPct"]}
            for e in large_winners
        ],
        "cycles": enriched,
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A - 실비용+양도소득세 반영 재검증 - 요약")
    print("=" * 100)
    for label, key in [("(A) 총수익(gross)", "scenarioA_gross"),
                        ("(B) 친션비용 반영", "scenarioB_afterFriction"),
                        ("(C) 친션비용+양도소득세 반영(세후 순수익)", "scenarioC_afterFrictionAndTax")]:
        s = output[key]
        print(f"\n{label}: n={s['n']}, 승률={s['winRatePct']}%, 평균={s['avgReturnPct']}%, "
              f"중앙값={s['medianReturnPct']}%, 합계={s['sumReturnPct']}%")
    print("\n대형 승자 10건에 대한 세금 영향:")
    for w in output["top10WinnersImpact"]:
        print(f"  {w['ticker']:6s} {w['entryDate']}: gross={w['grossReturnPct']:8.2f}% "
              f"-> tax={w['taxPctPoints']:7.2f}%p -> 세후={w['afterFrictionAndTaxReturnPct']:8.2f}%")


if __name__ == "__main__":
    run_all()

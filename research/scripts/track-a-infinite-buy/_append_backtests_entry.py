#!/usr/bin/env python3
"""research/data/backtests.json에 이번 검증 항목을 한 번 추가한다(중복 id면 건너뜀)."""
import json
import os

PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "backtests.json")
ENTRY_ID = "track-a-infinite-buy-averaging-cycle-validation-2010-2026"

entry = {
    "id": ENTRY_ID,
    "type": "backtest",
    "ticker": "SOXL,TQQQ,FAS,TNA",
    "strategy_id": "track-a-averaging-cycle-candidate",
    "data_range": {"start": "2008-11-19", "end": "2026-08-17", "frequency": "daily"},
    "created_date": "2026-09-21",
    "summary": (
        "외부 '무한매수법 V4.0' 방법론을 일반 원리(예산 N분할, 평단 기준 동적 기준선, 1/4 부분 매도+목표가, 소진 시 리버스)로 "
        "재정의해 SOXL/TQQQ 일봉 3년·5년 롤링 윈도우(학습 ≤2018 / 검증 ≥2019)와 FAS/TNA(2008 위기 포함)에서 검증. "
        "상승장에서 단순 보유에 크게 뒤처지고(SOXL 3년 중앙 세후 2.78→1.73~1.85배, TQQQ 2.60→1.47~1.50배), 종목 간 결과 불일치"
        "(검증 구간 SOXL 승, TQQQ 패). 평균 노출이 30~40%라 동일 노출 벤치마크와 비교하면 N=40에서만 4종목 3년 중앙 +8~15% "
        "소폭 우위이나 MDD는 전체 윈도우 기준 4종목 모두 더 깊음. 학습 p25 상위 설정은 손실 미실현형(정적 기준선+리버스 off)이라 "
        "검증에서 붕괴(SOXL 중앙 0.86배). H1 추가 검증 필요, H2 채택 후보(예산 상한 구조만), H3 기각, H4 기각. 전략 채택 비추천."
    ),
    "metrics": {
        "windows": {"step_days": 21, "horizons": {"3y": 756, "5y": 1260},
                    "train_end": "2018-12-31", "valid_start": "2019-01-01", "grid_configs": 96},
        "median_after_tax_final_3y": {
            "SOXL": {"all": {"buy_hold": 2.78, "track_a": 1.83, "n20": 1.85, "n40": 1.73},
                     "valid": {"buy_hold": 1.32, "track_a": 1.83, "n20": 2.02, "n40": 1.77}},
            "TQQQ": {"all": {"buy_hold": 2.60, "track_a": 1.75, "n20": 1.47, "n40": 1.50},
                     "valid": {"buy_hold": 1.42, "track_a": 1.78, "n20": 0.88, "n40": 0.99}},
        },
        "exposure_matched_relative_median_pct_3y_all": {
            "SOXL": {"n20": 10.4, "n40": 15.3}, "TQQQ": {"n20": -0.4, "n40": 8.4},
            "FAS": {"n20": -1.0, "n40": 10.4}, "TNA": {"n20": -1.7, "n40": 10.8},
        },
        "median_mdd_pct_n40_strategy_vs_matched": {
            "SOXL": [-39, -30], "TQQQ": [-32, -20], "FAS": [-32, -23], "TNA": [-45, -29],
        },
        "median_exposure_pct": "30-40 (FAS/TNA 33-47)",
        "walk_forward_selected": {
            "SOXL": {"config": "n20|s0=0.25|statichalf|rev=0", "train_p25": 2.44,
                     "valid_median": 0.86, "valid_loss_share_pct": 60},
            "TQQQ": {"config": "n20|s0=0.20|statichalf|rev=0", "train_p25": 2.23,
                     "valid_median": 1.10, "valid_loss_share_pct": 42},
        },
        "hypotheses": {"H1": "추가 검증 필요", "H2": "채택 후보(예산 상한 구조만)", "H3": "기각", "H4": "기각"},
        "assumptions": {"slippage_one_way_pct": 0.15, "fee": 0, "capital_gains_tax": 0.22,
                        "fill": "LOC at close; limit target cons(close>=target)/opt(high>=target) sensitivity",
                        "fractional_shares": True},
    },
    "report_path": "research/reports/track-a-infinite-buy-averaging-cycle-validation.md",
    "sources": [
        {"title": "Yahoo Finance v8 chart API - SOXL/TQQQ/FAS/TNA daily (기존 캐시 재사용)",
         "url": "https://finance.yahoo.com/", "accessed": "2026-09-21"},
        {"title": "외부 방법론 게시글 2건(카페, 2026-03-14) - 아이디어 출처로만 참조, 원문 미수록",
         "url": "", "accessed": "2026-09-21"},
    ],
    "confidence": "low-medium",
    "caveats": (
        "3년 윈도우는 21일 간격 중복이라 독립 표본이 약 5개 수준이며 p-value 미계산. 2010년 이후 강세 표본(FAS/TNA만 2008 포함). "
        "LOC 종가 체결·소수 주식·슬리피지 고정 가정, 낙관 체결 시 우위가 커짐. 동일 노출 벤치마크는 단일 대안이며 비용·세금 무시. "
        "기본공제·손실 이월 미반영. 배당 미반영, Yahoo 단일 출처. 파라미터 격자 제한(N 2종, s0 4종). 실제 LOC/MOC 주문 지원 미검증."
    ),
    "last_updated": "2026-09-21",
}

with open(PATH, encoding="utf-8") as f:
    data = json.load(f)
if any(e.get("id") == ENTRY_ID for e in data):
    print("already present, skipped")
else:
    data.append(entry)
    with open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("appended, new length:", len(data))

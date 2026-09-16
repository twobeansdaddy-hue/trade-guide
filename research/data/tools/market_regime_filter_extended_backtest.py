#!/usr/bin/env python3
"""
market_regime_filter_extended_backtest.py

목적
----
`market_regime_filter_backtest.py`(SOXL/TQQQ 20개 사이클, SPY 40주선 필터)는 20개 사이클
전부에서 필터가 단 한 번도 발동하지 않아 "효과가 없다"가 아니라 "검증 불능(inconclusive)"으로
남았다(`research/reports/track-a-market-regime-filter-review.md`의 "다음 리서치 우선순위
제안" 1번). 이 스크립트는 그 제안을 그대로 실행한다: 같은 SPY 40주선 필터를
`track-a-stoploss-revalidation-and-sizing-design.md`에서 이미 확보한 TNA(15사이클)·
FAS(14사이클)에도 적용해, 필터가 실제로 발동하는 사례가 나오는지 확인한다.

이 스크립트는 새 가설을 만들지 않는다 — 기존 리포트가 사전에 고정한 가설(SPY 마지막 완료
주봉 종가 > SPY 40주 이동평균일 때만 신규 BUY 허용)과 워크포워드 분할 시점을 그대로
재사용한다. 유일한 변경은 데이터셋을 SOXL/TQQQ 2종에서 SOXL/TQQQ/TNA/FAS 4종으로
넓히는 것뿐이다.

기존 파일과의 관계
------------------
`market_regime_filter_backtest.py`를 수정하지 않고 그대로 import해서 재사용한다
(analyze_dataset, summarize, walk_forward_summaries, load_spy_above_40w, SPY_CSV,
WALK_FORWARD_SPLITS, DATASETS). DATASETS는 원본 리스트를 복사한 뒤 TNA/FAS 항목만
추가한다 — 원본 리스트 객체 자체는 변경하지 않는다.

사용 예
-------
python3 research/data/tools/market_regime_filter_extended_backtest.py --today 2026-08-18 \
    --json-out research/data/cache/market_regime_filter_extended_backtest_results.json
"""
import argparse
import json
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from market_regime_filter_backtest import (  # noqa: E402
    DATASETS as BASE_DATASETS,
    SPY_CSV,
    WALK_FORWARD_SPLITS,
    parse_date,
    load_spy_above_40w,
    analyze_dataset,
    summarize,
    walk_forward_summaries,
)

EXTRA_DATASETS = [
    {
        "key": "tna_yahoo",
        "ticker": "TNA",
        "csv": "research/data/cache/tna-weekly-yahoo-2008-2026.csv",
        "close_col": "adjclose",
        "low_col": "low",
        "raw_close_col": "close",
        "label": "TNA / Yahoo Finance / weekly / Adjusted Close",
    },
    {
        "key": "fas_yahoo",
        "ticker": "FAS",
        "csv": "research/data/cache/fas-weekly-yahoo-2008-2026.csv",
        "close_col": "adjclose",
        "low_col": "low",
        "raw_close_col": "close",
        "label": "FAS / Yahoo Finance / weekly / Adjusted Close",
    },
]

DATASETS = list(BASE_DATASETS) + EXTRA_DATASETS


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--today", required=True, help="YYYY-MM-DD, 완결 주봉 판정 기준일")
    parser.add_argument("--json-out", default=None)
    args = parser.parse_args()

    today = parse_date(args.today)

    spy_above, spy_dropped, _spy_detail = load_spy_above_40w(SPY_CSV, today)
    print(f"[SPY] {SPY_CSV} 로드 완료. 40주 SMA 계산 가능 주봉 수: {len(spy_above)}")
    if spy_dropped:
        print(f"[SPY] 완결되지 않아 제외한 마지막 행: {[r['date'].isoformat() for r in spy_dropped]}")

    all_cycles = []
    per_dataset = {}
    all_missing_spy = []
    for ds in DATASETS:
        cycle_results, dropped, missing_spy_dates = analyze_dataset(ds, today, spy_above)
        per_dataset[ds["key"]] = {
            "label": ds["label"],
            "cycles": cycle_results,
            "cycle_count": len(cycle_results),
        }
        print(f"[{ds['key']}] {ds['label']}: {len(cycle_results)}개 사이클, "
              f"완결 제외 {len(dropped)}건, SPY 매칭 누락 {len(missing_spy_dates)}건")
        all_cycles.extend(cycle_results)
        all_missing_spy.extend(missing_spy_dates)

    if all_missing_spy:
        print(f"[경고] SPY 데이터에 매칭되지 않은 날짜: {sorted(set(all_missing_spy))}")
    else:
        print("[확인] 모든 사이클 체크포인트 날짜가 SPY 주봉 데이터와 매칭됨")

    # 재현 검증: 기존 SOXL/TQQQ 20개 사이클 부분만 떼어내 기존 리포트와 비교 가능하게 별도 요약
    base_cycles = [c for c in all_cycles if c["dataset"] in {d["key"] for d in BASE_DATASETS}]
    extra_cycles = [c for c in all_cycles if c["dataset"] in {d["key"] for d in EXTRA_DATASETS}]

    overall = summarize(all_cycles, f"전체 통합 (SOXL+TQQQ+TNA+FAS, {len(all_cycles)}개 사이클)")
    base_only = summarize(base_cycles, f"기존 SOXL+TQQQ만 ({len(base_cycles)}개 사이클, 재현 검증용)")
    extra_only = summarize(extra_cycles, f"신규 TNA+FAS만 ({len(extra_cycles)}개 사이클)")

    ticker_summaries = {}
    for ticker in ("SOXL", "TQQQ", "TNA", "FAS"):
        subset = [c for c in all_cycles if c["ticker"] == ticker]
        if subset:
            ticker_summaries[ticker] = summarize(subset, f"{ticker}만")

    wf = walk_forward_summaries(all_cycles)

    result = {
        "methodology": {
            "hypothesis": "SPY 마지막 완료 주봉 종가 > SPY 40주 이동평균일 때만 Track A 신규 BUY 허용 "
                           "(weeksSinceCross 0~4주 매주 재평가, 최초 동시 성립 주에 진입) — "
                           "market_regime_filter_backtest.py와 완전히 동일, 신규 가설 없음",
            "extension": "SOXL/TQQQ(20사이클, 기존 검증됨)에 TNA(15)/FAS(14)를 추가해 49개 사이클로 확장",
            "baseline": "weeksSinceCross<=4 정책, 시장 국면 필터 없음, 진입은 항상 delay=0(교차 당주)",
            "spy_data_source": "Yahoo Finance (query2.finance.yahoo.com v8 chart API), interval=1wk, "
                                "adjclose 사용",
        },
        "overall": overall,
        "base_soxl_tqqq_only_reproduction_check": base_only,
        "extra_tna_fas_only": extra_only,
        "by_ticker": ticker_summaries,
        "walk_forward": wf,
        "per_dataset": per_dataset,
        "spy_missing_dates": sorted(set(all_missing_spy)),
    }

    print("\n=== 전체 통합 결과 (49개 사이클) ===")
    print(json.dumps(overall, ensure_ascii=False, indent=2))
    print("\n=== 기존 SOXL+TQQQ 재현 검증 (20개 사이클, 기존 리포트와 일치해야 함) ===")
    print(json.dumps(base_only, ensure_ascii=False, indent=2))
    print("\n=== 신규 TNA+FAS (29개 사이클) ===")
    print(json.dumps(extra_only, ensure_ascii=False, indent=2))

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"\n[JSON 저장] {args.json_out}")


if __name__ == "__main__":
    main()

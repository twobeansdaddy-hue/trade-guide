#!/usr/bin/env python3
"""
run_chandelier_exit_report.py

track-a-chandelier-exit-review.md 리포트용 실행 스크립트.
run_trailing_stop_report.py와 완전히 동일한 구조·표본·슬리피지·워크포워드 분할을 쓴다 —
유일한 차이는 손절 후보가 고정비율 추적손절 대신 Chandelier Exit(k=2, k=3)라는 점뿐이다.

이 스크립트가 하는 일
--------------------
1. run_stoploss_report_v2.py의 DATASETS(SOXL x2소스, TQQQ, TNA, FAS = 49사이클)를 그대로 재사용.
2. 각 사이클에 대해 delay=0(실제 최초 진입 시점) 기준, 재진입 금지로 다음을 계산한다.
   - 무손절 기준선, 기존 고정비율 -25% 손절(비교용 재인용)
   - Chandelier Exit k=2, k=3 (신규, 사전 고정 가설)
3. 전체 49사이클 vs SOXL 중복 제거 46사이클, 4개 워크포워드 분할(2018/2019/2020/2021-01-01) 전부,
   데이터셋별 분해까지 기존 리포트들과 동일한 틀로 계산한다.

출력: research/data/cache/chandelier_exit_backtest_results.json
"""
import json
import sys
import os
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402
import chandelier_exit_backtest as ceb  # noqa: E402
import run_stoploss_report_v2 as v2  # noqa: E402
from run_trailing_stop_report import summarize, with_baseline_ratio, in_dedup_soxl  # noqa: E402

# run_stoploss_report_v2.REPO는 이 저장소 폴더명이 ".nosync" 없이 하드코딩되어 있어(기존 파일,
# 수정하지 않음) 이 머신의 실제 경로와 다르다. 그 파일을 고치는 대신, 여기서만 실제 저장소 루트로
# 바꾼 경로를 사용한다(v2.DATASETS의 원본 딕셔너리는 변경하지 않고 새 리스트를 만든다).
_ACTUAL_REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))


def _fix_dataset_paths(datasets):
    fixed = []
    for ds in datasets:
        ds2 = dict(ds)
        ds2["weekly_csv"] = ds["weekly_csv"].replace(v2.REPO, _ACTUAL_REPO)
        ds2["daily_csv"] = ds["daily_csv"].replace(v2.REPO, _ACTUAL_REPO)
        fixed.append(ds2)
    return fixed

TODAY = date(2026, 9, 16)
SLIPPAGE_PCT = v2.SLIPPAGE_PCT  # 0.0015, 기존 리포트들과 완전히 동일한 가정
WALK_FORWARD_SPLITS = ["2018-01-01", "2019-01-01", "2020-01-01", "2021-01-01"]  # 사전 고정, 전부 계산

# 사전 고정 가설: Chandelier Exit 표준 배수 k=2, k=3 (데이터를 보기 전에 고정, 튜닝 아님)
K_CONFIGS = {
    "chandelier_k2": 2.0,
    "chandelier_k3": 3.0,
}
FIXED_25_CONFIG = [{"name": "fixed_25pct", "kind": "fixed", "pct": 0.25}]


def main():
    daily_map = {}
    atr_map = {}
    baseline_all = []
    fixed25_all = []
    chandelier_all = {name: [] for name in K_CONFIGS}
    dataset_meta = {}

    for ds in _fix_dataset_paths(v2.DATASETS):
        if ds["daily_csv"] not in daily_map:
            rows = sdb.load_daily_candles(ds["daily_csv"], delimiter=";")
            daily_map[ds["daily_csv"]] = rows
            atr_map[ds["daily_csv"]] = sdb.compute_daily_atr(rows, period=14)

        weekly_candles, cycles, dropped = sdb.load_weekly_cycles(
            ds["weekly_csv"], ds["weekly_delim"], ds["close_col"], ds["low_col"], TODAY,
            raw_close_col=ds["raw_close_col"],
        )
        daily_rows = daily_map[ds["daily_csv"]]
        atr = atr_map[ds["daily_csv"]]

        baseline_trades, cand_results = sdb.run_stoploss_candidates(
            weekly_candles, cycles, daily_rows, atr, ds["label"], FIXED_25_CONFIG,
            slippage_pct=SLIPPAGE_PCT,
        )
        for t in baseline_trades:
            t["dataset"] = ds["key"]
            t["ticker"] = ds["ticker"]
        fixed25_trades = cand_results["fixed_25pct"]["no_reentry"]
        for t in fixed25_trades:
            t["dataset"] = ds["key"]
            t["ticker"] = ds["ticker"]

        chandelier_results = ceb.run_chandelier_exit_candidates(
            weekly_candles, cycles, daily_rows, atr, ds["label"], K_CONFIGS,
            slippage_pct=SLIPPAGE_PCT, dataset_key=ds["key"], ticker=ds["ticker"],
        )

        baseline_all.extend(baseline_trades)
        fixed25_all.extend(fixed25_trades)
        for name in K_CONFIGS:
            chandelier_all[name].extend(chandelier_results[name])

        dataset_meta[ds["key"]] = {
            "ticker": ds["ticker"], "label": ds["label"], "num_cycles": len(cycles),
        }

    out = {
        "generated_at": TODAY.isoformat(),
        "hypothesis": "Chandelier Exit: 손절선 = 진입 이후 일봉 종가 최고값 - k*ATR(14, 일봉, 매일 재계산). "
                      "k=2, k=3은 이 기법의 표준값으로 사전 고정(데이터를 보고 고르지 않음).",
        "slippage_pct_one_way": SLIPPAGE_PCT,
        "commission_pct_one_way": 0.0,
        "reentry": "기본 미허용 (사전 고정 규칙, 기존 리포트들과 동일)",
        "walk_forward_splits": WALK_FORWARD_SPLITS,
        "datasets": dataset_meta,
    }

    for scope_name, flt in (("full_n49", lambda t: True), ("dedup_soxl_n46", in_dedup_soxl)):
        base = [t for t in baseline_all if flt(t)]
        fixed25 = [t for t in fixed25_all if flt(t)]
        scope = {
            "n": len(base),
            "baseline": summarize(base),
            "fixed_25pct": with_baseline_ratio(summarize(fixed25), base),
        }
        for name in K_CONFIGS:
            trades = [t for t in chandelier_all[name] if flt(t)]
            scope[name] = with_baseline_ratio(summarize(trades), base)
        out[f"summary_{scope_name}"] = scope

    per_dataset = {}
    for key in dataset_meta:
        base = [t for t in baseline_all if t["dataset"] == key]
        fixed25 = [t for t in fixed25_all if t["dataset"] == key]
        row = {
            "n": len(base),
            "baseline": summarize(base),
            "fixed_25pct": with_baseline_ratio(summarize(fixed25), base),
        }
        for name in K_CONFIGS:
            trades = [t for t in chandelier_all[name] if t["dataset"] == key]
            row[name] = with_baseline_ratio(summarize(trades), base)
        per_dataset[key] = row
    out["per_dataset_breakdown"] = per_dataset

    walk_forward = {}
    for scope_name, flt in (("full_n49", lambda t: True), ("dedup_soxl_n46", in_dedup_soxl)):
        base_scope = [t for t in baseline_all if flt(t)]
        fixed25_scope = [t for t in fixed25_all if flt(t)]
        chandelier_scope = {name: [t for t in chandelier_all[name] if flt(t)] for name in K_CONFIGS}

        scope_wf = {}
        for cutoff in WALK_FORWARD_SPLITS:
            train_base = [t for t in base_scope if t["cross_date"] < cutoff]
            test_base = [t for t in base_scope if t["cross_date"] >= cutoff]
            split_result = {"cutoff": cutoff, "train_n": len(train_base), "test_n": len(test_base)}

            train_fixed = [t for t in fixed25_scope if t["cross_date"] < cutoff]
            test_fixed = [t for t in fixed25_scope if t["cross_date"] >= cutoff]
            split_result["fixed_25pct"] = {
                "train": with_baseline_ratio(summarize(train_fixed), train_base),
                "test": with_baseline_ratio(summarize(test_fixed), test_base),
            }

            for name in K_CONFIGS:
                train_c = [t for t in chandelier_scope[name] if t["cross_date"] < cutoff]
                test_c = [t for t in chandelier_scope[name] if t["cross_date"] >= cutoff]
                split_result[name] = {
                    "train": with_baseline_ratio(summarize(train_c), train_base),
                    "test": with_baseline_ratio(summarize(test_c), test_base),
                }
            scope_wf[cutoff] = split_result
        walk_forward[scope_name] = scope_wf
    out["walk_forward"] = walk_forward

    out["baseline_trades_delay0"] = baseline_all
    out["fixed_25pct_trades_delay0"] = fixed25_all
    out["chandelier_trades_delay0"] = chandelier_all

    research_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    out_path = os.path.join(research_dir, "data", "cache", "chandelier_exit_backtest_results.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)

    print(f"[저장] {out_path}")
    print("\n=== 전체 49사이클 요약 ===")
    print(json.dumps(out["summary_full_n49"], ensure_ascii=False, indent=2))
    print("\n=== SOXL 중복 제거 46사이클 요약 ===")
    print(json.dumps(out["summary_dedup_soxl_n46"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

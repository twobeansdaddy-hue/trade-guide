#!/usr/bin/env python3
"""
run_trailing_stop_report.py

track-a-trailing-stop-review.md 리포트용 실행 스크립트.

이 스크립트가 하는 일
--------------------
1. run_stoploss_report_v2.py의 DATASETS(SOXL x2소스, TQQQ, TNA, FAS = 49사이클)를 그대로 재사용해
   기존 49사이클 표본을 새로 받지 않고 그대로 쓴다.
2. 각 사이클에 대해 delay=0(엔진의 실제 최초 진입 시점) 기준으로 다음을 재진입 금지로 계산한다.
   - 무손절 기준선 (stoploss_daily_backtest.run_stoploss_candidates 재사용, candidate_configs=[])
   - 기존 고정비율 -25% 손절 (같은 함수, candidate_configs=[fixed_25pct]) — 직전 리포트와 동일한
     슬리피지(0.0015)로 다시 계산해 walk-forward 4분할 전부에 대해 비교 가능하게 한다(직전 리포트는
     2019/2020 두 분할만 계산했다).
   - 추적 손절 3개 후보(최고 종가 대비 -20%/-25%/-30%, trailing_stop_backtest.py 신규)
3. SOXL 중복 소스 민감도: 전체 49사이클(SOXL 중복 포함) vs SOXL 중복 제거 46사이클
   (Twelve Data 프로덕션 소스 우선 사용 + Twelve Data 주봉 데이터 시작일(2021-08-02) 이전 사이클만
   Yahoo로 보충) 두 버전을 모두 계산한다.
4. cross_date 기준 워크포워드 4분할(2018/2019/2020/2021-01-01, 전부 사전 고정)을 전체/중복제거 버전
   모두에 대해 계산한다.
5. 티커/데이터셋별 분해도 함께 낸다.

출력: research/data/cache/trailing_stop_backtest_results.json
"""
import json
import sys
import os
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402
import trailing_stop_backtest as tsb  # noqa: E402
import run_stoploss_report_v2 as v2  # noqa: E402  (DATASETS, SLIPPAGE_PCT 재사용, main()은 호출 안 함)

TODAY = date(2026, 8, 18)
SLIPPAGE_PCT = v2.SLIPPAGE_PCT  # 0.0015, 직전 리포트와 완전히 동일한 가정
TWELVEDATA_WEEKLY_START = "2021-08-02"  # SOXL Twelve Data 주봉 데이터 시작일(soxl-weekly-twelvedata-2021-2026.metadata.json)
WALK_FORWARD_SPLITS = ["2018-01-01", "2019-01-01", "2020-01-01", "2021-01-01"]  # 사전 고정, 전부 계산

TRAIL_CONFIGS = {
    "trailing_20pct": 0.20,
    "trailing_25pct": 0.25,
    "trailing_30pct": 0.30,
}
FIXED_25_CONFIG = [{"name": "fixed_25pct", "kind": "fixed", "pct": 0.25}]


def summarize(trades, label=None):
    if not trades:
        return None
    n = len(trades)
    rets = sorted(t["return_pct"] for t in trades)
    mean_ret = sum(rets) / n
    median_ret = rets[n // 2] if n % 2 == 1 else (rets[n // 2 - 1] + rets[n // 2]) / 2
    mean_mdd_close = sum(t["mdd_close_pct"] for t in trades) / n
    mean_mdd_low = sum(t["mdd_low_pct"] for t in trades) / n
    stops = [t for t in trades if t.get("exit_reason") == "STOP"]
    whipsaws = [t for t in stops if t.get("whipsaw_recovered")]
    gap_losses = [t.get("gap_excess_loss_pct", 0.0) for t in trades if t.get("gap_excess_loss_pct", 0.0) > 0]
    out = {
        "n": n,
        "mean_return_pct": round(mean_ret, 1),
        "median_return_pct": round(median_ret, 1),
        "mean_mdd_close_pct": round(mean_mdd_close, 1),
        "mean_mdd_low_pct": round(mean_mdd_low, 1),
        "num_stop_triggered": len(stops),
        "num_whipsaw": len(whipsaws),
        "whipsaw_rate_pct": round(len(whipsaws) / len(stops) * 100.0, 1) if stops else None,
        "num_gap_excess_loss": len(gap_losses),
        "max_gap_excess_loss_pct": round(max(gap_losses), 2) if gap_losses else 0.0,
    }
    if label:
        out["label"] = label
    return out


def with_baseline_ratio(cand_summary, base_trades):
    if not cand_summary or not base_trades:
        return cand_summary
    base_mean_ret = sum(t["return_pct"] for t in base_trades) / len(base_trades)
    base_mean_mdd = sum(t["mdd_close_pct"] for t in base_trades) / len(base_trades)
    mdd_improve = cand_summary["mean_mdd_close_pct"] - base_mean_mdd
    ret_give_up = base_mean_ret - cand_summary["mean_return_pct"]
    cand_summary["baseline_mean_return_pct"] = round(base_mean_ret, 1)
    cand_summary["baseline_mean_mdd_close_pct"] = round(base_mean_mdd, 1)
    cand_summary["mdd_improve_pct_points"] = round(mdd_improve, 2)
    cand_summary["return_give_up_pct_points"] = round(ret_give_up, 2)
    cand_summary["give_up_per_mdd_improve_pct"] = (
        round(ret_give_up / mdd_improve, 2) if mdd_improve > 0.001 else None
    )
    return cand_summary


def in_dedup_soxl(trade):
    """SOXL 중복 제거 필터: soxl_yahoo 사이클 중 Twelve Data 주봉 데이터 시작일 이후 cross_date는
    Twelve Data(soxl_twelvedata)와 같은 실제 사건의 중복이므로 제외한다. 그 외(다른 티커,
    soxl_twelvedata 전부, TD 시작일 이전 soxl_yahoo)는 전부 포함한다."""
    if trade["dataset"] == "soxl_yahoo" and trade["cross_date"] >= TWELVEDATA_WEEKLY_START:
        return False
    return True


def main():
    daily_map = {}
    atr_map = {}
    baseline_all = []
    fixed25_all = []
    trailing_all = {name: [] for name in TRAIL_CONFIGS}
    dataset_meta = {}

    for ds in v2.DATASETS:
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

        trailing_results = tsb.run_trailing_stop_candidates(
            weekly_candles, cycles, daily_rows, ds["label"], TRAIL_CONFIGS,
            slippage_pct=SLIPPAGE_PCT, dataset_key=ds["key"], ticker=ds["ticker"],
        )

        baseline_all.extend(baseline_trades)
        fixed25_all.extend(fixed25_trades)
        for name in TRAIL_CONFIGS:
            trailing_all[name].extend(trailing_results[name])

        dataset_meta[ds["key"]] = {
            "ticker": ds["ticker"], "label": ds["label"], "num_cycles": len(cycles),
        }

    out = {
        "generated_at": TODAY.isoformat(),
        "slippage_pct_one_way": SLIPPAGE_PCT,
        "commission_pct_one_way": 0.0,
        "trailing_stop_basis": "entry 이후 일봉 종가 최고값(high-water mark), 장중 고가 미사용",
        "reentry": "기본 미허용 (사전 고정 규칙)",
        "twelvedata_weekly_start_used_for_dedup": TWELVEDATA_WEEKLY_START,
        "walk_forward_splits": WALK_FORWARD_SPLITS,
        "datasets": dataset_meta,
    }

    # ---- 전체(49) vs SOXL 중복 제거(46) ----
    for scope_name, flt in (("full_n49", lambda t: True), ("dedup_soxl_n46", in_dedup_soxl)):
        base = [t for t in baseline_all if flt(t)]
        fixed25 = [t for t in fixed25_all if flt(t)]
        scope = {
            "n": len(base),
            "baseline": summarize(base),
            "fixed_25pct": with_baseline_ratio(summarize(fixed25), base),
        }
        for name in TRAIL_CONFIGS:
            trail_trades = [t for t in trailing_all[name] if flt(t)]
            scope[name] = with_baseline_ratio(summarize(trail_trades), base)
        out[f"summary_{scope_name}"] = scope

    # ---- 데이터셋(티커/소스)별 분해 ----
    per_dataset = {}
    for key in dataset_meta:
        base = [t for t in baseline_all if t["dataset"] == key]
        fixed25 = [t for t in fixed25_all if t["dataset"] == key]
        row = {
            "n": len(base),
            "baseline": summarize(base),
            "fixed_25pct": with_baseline_ratio(summarize(fixed25), base),
        }
        for name in TRAIL_CONFIGS:
            trail_trades = [t for t in trailing_all[name] if t["dataset"] == key]
            row[name] = with_baseline_ratio(summarize(trail_trades), base)
        per_dataset[key] = row
    out["per_dataset_breakdown"] = per_dataset

    # ---- 워크포워드 (전체/중복제거 각각, 4분할 전부) ----
    walk_forward = {}
    for scope_name, flt in (("full_n49", lambda t: True), ("dedup_soxl_n46", in_dedup_soxl)):
        base_scope = [t for t in baseline_all if flt(t)]
        fixed25_scope = [t for t in fixed25_all if flt(t)]
        trailing_scope = {name: [t for t in trailing_all[name] if flt(t)] for name in TRAIL_CONFIGS}

        scope_wf = {}
        for cutoff in WALK_FORWARD_SPLITS:
            train_base = [t for t in base_scope if t["cross_date"] < cutoff]
            test_base = [t for t in base_scope if t["cross_date"] >= cutoff]
            split_result = {
                "cutoff": cutoff, "train_n": len(train_base), "test_n": len(test_base),
            }

            train_fixed = [t for t in fixed25_scope if t["cross_date"] < cutoff]
            test_fixed = [t for t in fixed25_scope if t["cross_date"] >= cutoff]
            split_result["fixed_25pct"] = {
                "train": with_baseline_ratio(summarize(train_fixed), train_base),
                "test": with_baseline_ratio(summarize(test_fixed), test_base),
            }

            for name in TRAIL_CONFIGS:
                train_trail = [t for t in trailing_scope[name] if t["cross_date"] < cutoff]
                test_trail = [t for t in trailing_scope[name] if t["cross_date"] >= cutoff]
                split_result[name] = {
                    "train": with_baseline_ratio(summarize(train_trail), train_base),
                    "test": with_baseline_ratio(summarize(test_trail), test_base),
                }
            scope_wf[cutoff] = split_result
        walk_forward[scope_name] = scope_wf
    out["walk_forward"] = walk_forward

    # ---- 원본 trade-level 데이터(감사·재현용) ----
    out["baseline_trades_delay0"] = baseline_all
    out["fixed_25pct_trades_delay0"] = fixed25_all
    out["trailing_trades_delay0"] = trailing_all

    research_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    out_path = os.path.join(research_dir, "data", "cache", "trailing_stop_backtest_results.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)

    print(f"[저장] {out_path}")
    print("\n=== 전체 49사이클 요약 ===")
    print(json.dumps(out["summary_full_n49"], ensure_ascii=False, indent=2))
    print("\n=== SOXL 중복 제거 46사이클 요약 ===")
    print(json.dumps(out["summary_dedup_soxl_n46"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

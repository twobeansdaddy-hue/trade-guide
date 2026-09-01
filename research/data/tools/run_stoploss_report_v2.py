#!/usr/bin/env python3
"""
run_stoploss_report_v2.py

track-a-stoploss-revalidation-and-sizing-design.md 리포트용 확장 백테스트.
stoploss_daily_backtest.py(신규 slippage_pct 파라미터 포함)와
entry_delay_cycle_backtest.py를 그대로 재사용한다. run_stoploss_report.py(기존 스크립트,
수정하지 않음)와 별도 스크립트로 분리했다 — 기존 스크립트의 출력 파일
(research/data/cache/stoploss_backtest_results.json / stoploss_backtest_results_yahoo_daily.json)을
덮어쓰지 않기 위함이다.

이 스크립트가 하는 일
--------------------
1. 5개 데이터셋(soxl_twelvedata, soxl_yahoo, tqqq_yahoo, tna_yahoo, fas_yahoo)의 주봉 사이클을 찾는다.
   TNA/FAS는 새로 확보한 Yahoo Finance 일봉/주봉 데이터를 사용한다(후보 선정 근거는 리포트 본문 참고).
2. delay=0(=weeksSinceCross==0, 엔진의 최초 진입 시점) 기준으로 손절 후보
   (고정비율 -20%/-25%/-30%, ATR14x2/cap35%, ATR14x3/cap40%)를 재진입 금지 조건으로 시뮬레이션한다.
3. 편도 슬리피지 0.15%p(SLIPPAGE_PCT), 수수료 0을 모든 진입·추세청산·손절 체결에 일관 적용한다.
4. 전체 49개 사이클을 cross_date 기준 시간순으로 정렬해 walk-forward 분할
   (기본: 2020-01-01 기준, 로버스트니스 체크로 2019-01-01도 병행)을 수행하고,
   -25%(및 -20%, -30%) 후보를 train/test 구간에 각각 적용해 결과를 비교한다.

출력: research/data/cache/stoploss_backtest_results_extended.json
"""
import json
import sys
import os
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402

TODAY = date(2026, 8, 18)
REPO = "/Users/beansdaaddy/Desktop/_Study/Java/trade-guide"
SLIPPAGE_PCT = 0.0015  # 편도 0.15%p, 근거는 리포트 본문 "수수료·슬리피지 가정" 절 참고
DELAYS = [0]  # 이 스크립트는 delay=0(엔진의 실제 진입 조건) 손절 후보 비교에 집중한다

DATASETS = [
    {
        "key": "soxl_twelvedata",
        "ticker": "SOXL",
        "weekly_csv": f"{REPO}/research/data/cache/soxl-weekly-twelvedata-2021-2026.csv",
        "weekly_delim": ";", "close_col": "close", "low_col": "low", "raw_close_col": None,
        "daily_csv": f"{REPO}/research/data/cache/soxl-daily-twelvedata-2010-2026.csv",
        "daily_provider": "Twelve Data (production source)",
        "label": "SOXL / Twelve Data / interval=1week / adjustment=splits (신호) + SOXL Twelve Data daily(체결/손절)",
    },
    {
        "key": "soxl_yahoo",
        "ticker": "SOXL",
        "weekly_csv": f"{REPO}/research/data/cache/soxl-weekly-yahoo-2010-2026.csv",
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": f"{REPO}/research/data/cache/soxl-daily-yahoo-2010-2026.csv",
        "daily_provider": "Yahoo Finance",
        "label": "SOXL / Yahoo Finance weekly Adjusted Close (신호) + SOXL Yahoo daily(체결/손절)",
    },
    {
        "key": "tqqq_yahoo",
        "ticker": "TQQQ",
        "weekly_csv": f"{REPO}/research/data/cache/tqqq-weekly-yahoo-2010-2026.csv",
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": f"{REPO}/research/data/cache/tqqq-daily-yahoo-2010-2026.csv",
        "daily_provider": "Yahoo Finance",
        "label": "TQQQ / Yahoo Finance weekly Adjusted Close (신호) + TQQQ Yahoo daily(체결/손절)",
    },
    {
        "key": "tna_yahoo",
        "ticker": "TNA",
        "weekly_csv": f"{REPO}/research/data/cache/tna-weekly-yahoo-2008-2026.csv",
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": f"{REPO}/research/data/cache/tna-daily-yahoo-2008-2026.csv",
        "daily_provider": "Yahoo Finance",
        "label": "TNA(Direxion Daily Small Cap Bull 3X) / Yahoo Finance weekly Adjusted Close (신호) + TNA Yahoo daily(체결/손절)",
    },
    {
        "key": "fas_yahoo",
        "ticker": "FAS",
        "weekly_csv": f"{REPO}/research/data/cache/fas-weekly-yahoo-2008-2026.csv",
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": f"{REPO}/research/data/cache/fas-daily-yahoo-2008-2026.csv",
        "daily_provider": "Yahoo Finance",
        "label": "FAS(Direxion Daily Financial Bull 3X) / Yahoo Finance weekly Adjusted Close (신호) + FAS Yahoo daily(체결/손절)",
    },
]

CANDIDATE_CONFIGS = [
    {"name": "fixed_20pct", "kind": "fixed", "pct": 0.20},
    {"name": "fixed_25pct", "kind": "fixed", "pct": 0.25},
    {"name": "fixed_30pct", "kind": "fixed", "pct": 0.30},
    {"name": "atr14_x2_cap35pct", "kind": "atr_cap", "multiplier": 2.0, "cap_pct": 0.35},
    {"name": "atr14_x3_cap40pct", "kind": "atr_cap", "multiplier": 3.0, "cap_pct": 0.40},
]

WALK_FORWARD_SPLITS = ["2020-01-01", "2019-01-01"]  # 첫 값이 주 분할, 나머지는 로버스트니스 체크


def summarize_no_reentry(trades):
    """no_reentry 손절 후보 결과 리스트 -> 요약 지표."""
    if not trades:
        return None
    n = len(trades)
    rets = sorted(t["return_pct"] for t in trades)
    mean_ret = sum(rets) / n
    median_ret = rets[n // 2] if n % 2 == 1 else (rets[n // 2 - 1] + rets[n // 2]) / 2
    mean_mdd_close = sum(t["mdd_close_pct"] for t in trades) / n
    mean_mdd_low = sum(t["mdd_low_pct"] for t in trades) / n
    stops = [t for t in trades if t["exit_reason"] == "STOP"]
    whipsaws = [t for t in stops if t["whipsaw_recovered"]]
    gap_losses = [t["gap_excess_loss_pct"] for t in trades if t["gap_excess_loss_pct"] > 0]
    return {
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


def main():
    daily_map = {}
    atr_map = {}
    for ds in DATASETS:
        if ds["daily_csv"] not in daily_map:
            rows = sdb.load_daily_candles(ds["daily_csv"], delimiter=";")
            daily_map[ds["daily_csv"]] = rows
            atr_map[ds["daily_csv"]] = sdb.compute_daily_atr(rows, period=14)

    all_results = {
        "generated_at": TODAY.isoformat(),
        "slippage_pct_one_way": SLIPPAGE_PCT,
        "commission_pct_one_way": 0.0,
        "candidate_configs": CANDIDATE_CONFIGS,
        "datasets": {},
    }

    # ---- baseline(손절 없음) + 손절 후보, 전 데이터셋 ----
    baseline_all = {}
    candidates_all = {}
    cycle_registry = []  # 전체 사이클 메타(walk-forward 분할용)

    for ds in DATASETS:
        weekly_candles, cycles, dropped = sdb.load_weekly_cycles(
            ds["weekly_csv"], ds["weekly_delim"], ds["close_col"], ds["low_col"], TODAY,
            raw_close_col=ds["raw_close_col"],
        )
        daily_rows = daily_map[ds["daily_csv"]]
        atr = atr_map[ds["daily_csv"]]

        baseline_trades, cand_results = sdb.run_stoploss_candidates(
            weekly_candles, cycles, daily_rows, atr, ds["label"], CANDIDATE_CONFIGS,
            slippage_pct=SLIPPAGE_PCT,
        )

        all_results["datasets"][ds["key"]] = {
            "label": ds["label"],
            "ticker": ds["ticker"],
            "daily_provider": ds["daily_provider"],
            "num_cycles": len(cycles),
            "dropped_incomplete_weekly_rows": [r["date"].isoformat() for r in dropped],
        }
        baseline_all[ds["key"]] = baseline_trades
        candidates_all[ds["key"]] = cand_results

        for bt in baseline_trades:
            cycle_registry.append({
                "dataset": ds["key"], "ticker": ds["ticker"], "cross_date": bt["cross_date"],
            })

    all_results["baseline_trades_delay0"] = baseline_all
    all_results["stoploss_candidates_no_reentry_only"] = {
        ds_key: {name: cand_results[name]["no_reentry"] for name in cand_results}
        for ds_key, cand_results in candidates_all.items()
    }

    # ---- 통합 요약 (전체 49개 사이클, 5개 티커) ----
    baseline_flat = [t for trades in baseline_all.values() for t in trades]
    baseline_summary = {
        "n": len(baseline_flat),
        "mean_return_pct": round(sum(t["return_pct"] for t in baseline_flat) / len(baseline_flat), 1),
        "median_return_pct": round(sorted(t["return_pct"] for t in baseline_flat)[len(baseline_flat) // 2], 1),
        "mean_mdd_close_pct": round(sum(t["mdd_close_pct"] for t in baseline_flat) / len(baseline_flat), 1),
        "mean_mdd_low_pct": round(sum(t["mdd_low_pct"] for t in baseline_flat) / len(baseline_flat), 1),
    }
    all_results["baseline_summary_all_tickers"] = baseline_summary

    candidate_summary_all = {}
    for cfg in CANDIDATE_CONFIGS:
        name = cfg["name"]
        flat = [t for ds_key, cand_results in candidates_all.items() for t in cand_results[name]["no_reentry"]]
        s = summarize_no_reentry(flat)
        if s:
            # mdd는 음수(예: -45.9%)이므로 "개선"(낙폭이 얕아짐)은 candidate - base가 양수여야 한다.
            mdd_improve = s["mean_mdd_close_pct"] - baseline_summary["mean_mdd_close_pct"]
            ret_give_up = baseline_summary["mean_return_pct"] - s["mean_return_pct"]
            s["mdd_improve_pct_points"] = round(mdd_improve, 2)
            s["return_give_up_pct_points"] = round(ret_give_up, 2)
            s["give_up_per_mdd_improve_pct"] = (
                round(ret_give_up / mdd_improve, 2) if mdd_improve > 0.001 else None
            )
        candidate_summary_all[name] = s
    all_results["candidate_summary_all_tickers_n49"] = candidate_summary_all

    # ---- 원 SOXL/TQQQ 20개 사이클만의 재계산치(비교용, 확장 전 표본) ----
    orig_keys = {"soxl_twelvedata", "soxl_yahoo", "tqqq_yahoo"}
    baseline_flat_orig = [t for k, trades in baseline_all.items() if k in orig_keys for t in trades]
    candidate_summary_orig = {}
    for cfg in CANDIDATE_CONFIGS:
        name = cfg["name"]
        flat = [t for k, cand_results in candidates_all.items() if k in orig_keys
                for t in cand_results[name]["no_reentry"]]
        s = summarize_no_reentry(flat)
        candidate_summary_orig[name] = s
    all_results["candidate_summary_soxl_tqqq_only_n20_with_slippage"] = candidate_summary_orig
    all_results["baseline_summary_soxl_tqqq_only_n20_with_slippage"] = {
        "n": len(baseline_flat_orig),
        "mean_return_pct": round(sum(t["return_pct"] for t in baseline_flat_orig) / len(baseline_flat_orig), 1),
        "mean_mdd_close_pct": round(sum(t["mdd_close_pct"] for t in baseline_flat_orig) / len(baseline_flat_orig), 1),
    }

    # ---- walk-forward 분할 ----
    walk_forward = {}
    for cutoff in WALK_FORWARD_SPLITS:
        train_flat_baseline = [t for t in baseline_flat if t["cross_date"] < cutoff]
        test_flat_baseline = [t for t in baseline_flat if t["cross_date"] >= cutoff]

        split_result = {"cutoff": cutoff, "train_n": len(train_flat_baseline), "test_n": len(test_flat_baseline)}
        for cfg in CANDIDATE_CONFIGS:
            name = cfg["name"]
            flat = [t for ds_key, cand_results in candidates_all.items() for t in cand_results[name]["no_reentry"]]
            train = [t for t in flat if t["cross_date"] < cutoff]
            test = [t for t in flat if t["cross_date"] >= cutoff]
            train_base = [t for t in baseline_flat if t["cross_date"] < cutoff]
            test_base = [t for t in baseline_flat if t["cross_date"] >= cutoff]

            def block(cand_trades, base_trades):
                s = summarize_no_reentry(cand_trades)
                if not s or not base_trades:
                    return s
                base_mean_ret = sum(t["return_pct"] for t in base_trades) / len(base_trades)
                base_mean_mdd = sum(t["mdd_close_pct"] for t in base_trades) / len(base_trades)
                mdd_improve = s["mean_mdd_close_pct"] - base_mean_mdd
                ret_give_up = base_mean_ret - s["mean_return_pct"]
                s["baseline_mean_return_pct"] = round(base_mean_ret, 1)
                s["baseline_mean_mdd_close_pct"] = round(base_mean_mdd, 1)
                s["mdd_improve_pct_points"] = round(mdd_improve, 2)
                s["return_give_up_pct_points"] = round(ret_give_up, 2)
                s["give_up_per_mdd_improve_pct"] = (
                    round(ret_give_up / mdd_improve, 2) if mdd_improve > 0.001 else None
                )
                return s

            split_result[name] = {
                "train": block(train, train_base),
                "test": block(test, test_base),
            }
        walk_forward[cutoff] = split_result
    all_results["walk_forward"] = walk_forward

    out_path = f"{REPO}/research/data/cache/stoploss_backtest_results_extended.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(all_results, f, indent=2, ensure_ascii=False)

    print(f"[저장] {out_path}")
    print(f"\n총 사이클 수(5개 데이터셋 합): {len(baseline_flat)}")
    print("\n=== 기준선(손절 없음), 전체 49개 사이클, 슬리피지 0.15%p 반영 ===")
    print(json.dumps(baseline_summary, ensure_ascii=False, indent=2))
    print("\n=== 손절 후보 요약(전체 49개 사이클) ===")
    print(json.dumps(candidate_summary_all, ensure_ascii=False, indent=2))
    print("\n=== walk-forward (cutoff별) ===")
    print(json.dumps(walk_forward, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
run_stoploss_report.py

stoploss_daily_backtest.py를 사용해:
1) 3개 데이터셋(soxl_twelvedata, soxl_yahoo, tqqq_yahoo)의 주봉 사이클을 찾고,
2) 새 체결 가정(주봉신호+다음거래일 시가)으로 지연 그리드(0..12주) 기준선을 재계산하고,
3) 일봉 ATR(14)/가격 비율 분포를 계산해 손절 후보 파라미터 근거를 만들고,
4) delay=0 진입 기준으로 손절 후보(고정비율, ATR+상한cap) 2개를 재진입 금지/허용 두 변형으로 시뮬레이션한다.

결과를 research/data/cache/stoploss_backtest_results.json 에 저장하고 요약을 표준출력으로 인쇄한다.
"""
import json
import sys
import os
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402

TODAY = date(2026, 8, 14)
REPO = "/Users/beansdaaddy/Desktop/_Study/Java/trade-guide"

DATASETS = [
    {
        "key": "soxl_twelvedata",
        "weekly_csv": f"{REPO}/research/data/cache/soxl-weekly-twelvedata-2021-2026.csv",
        "weekly_delim": ";", "close_col": "close", "low_col": "low", "raw_close_col": None,
        "daily_csv": f"{REPO}/research/data/cache/soxl-weekly-twelvedata-2021-2026.csv",  # placeholder, overwritten below
        "label": "SOXL / Twelve Data / interval=1week / adjustment=splits (신호) + SOXL Yahoo daily(체결/손절)",
    },
    {
        "key": "soxl_yahoo",
        "weekly_csv": f"{REPO}/research/data/cache/soxl-weekly-yahoo-2010-2026.csv",
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "label": "SOXL / Yahoo Finance weekly Adjusted Close (신호) + SOXL Yahoo daily(체결/손절)",
    },
    {
        "key": "tqqq_yahoo",
        "weekly_csv": f"{REPO}/research/data/cache/tqqq-weekly-yahoo-2010-2026.csv",
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "label": "TQQQ / Yahoo Finance weekly Adjusted Close (신호) + TQQQ Yahoo daily(체결/손절)",
    },
]

DAILY_SOURCES = {
    "twelvedata": {
        "soxl": f"{REPO}/research/data/cache/soxl-daily-twelvedata-2010-2026.csv",
        "tqqq": f"{REPO}/research/data/cache/tqqq-daily-twelvedata-2010-2026.csv",
        "out": f"{REPO}/research/data/cache/stoploss_backtest_results.json",
        "tag": "Twelve Data(프로덕션 소스) daily",
    },
    "yahoo": {
        "soxl": f"{REPO}/research/data/cache/soxl-daily-yahoo-2010-2026.csv",
        "tqqq": f"{REPO}/research/data/cache/tqqq-daily-yahoo-2010-2026.csv",
        "out": f"{REPO}/research/data/cache/stoploss_backtest_results_yahoo_daily.json",
        "tag": "Yahoo Finance daily",
    },
}

DELAYS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]


def main():
    daily_source = sys.argv[1] if len(sys.argv) > 1 else "twelvedata"
    src = DAILY_SOURCES[daily_source]
    print(f"[일봉 실행 데이터 소스] {src['tag']} ({src['soxl']}, {src['tqqq']})")
    soxl_daily = sdb.load_daily_candles(src["soxl"], delimiter=";")
    tqqq_daily = sdb.load_daily_candles(src["tqqq"], delimiter=";")
    soxl_atr = sdb.compute_daily_atr(soxl_daily, period=14)
    tqqq_atr = sdb.compute_daily_atr(tqqq_daily, period=14)

    daily_map = {
        "soxl_twelvedata": (soxl_daily, soxl_atr),
        "soxl_yahoo": (soxl_daily, soxl_atr),
        "tqqq_yahoo": (tqqq_daily, tqqq_atr),
    }

    all_results = {"generated_at": TODAY.isoformat(), "datasets": {}}
    all_cycles_meta = []
    all_entry_atr_indices = {"SOXL": [], "TQQQ": []}

    for ds in DATASETS:
        weekly_candles, cycles, dropped = sdb.load_weekly_cycles(
            ds["weekly_csv"], ds["weekly_delim"], ds["close_col"], ds["low_col"], TODAY,
            raw_close_col=ds["raw_close_col"],
        )
        daily_rows, atr = daily_map[ds["key"]]

        baseline_grid = sdb.run_baseline_delay_grid(weekly_candles, cycles, daily_rows, DELAYS, ds["label"])

        for cyc in cycles:
            ctx = sdb.build_cycle_daily_context(weekly_candles, cyc, daily_rows)
            if ctx["entry0_idx"] is not None:
                ticker = "SOXL" if "soxl" in ds["key"] else "TQQQ"
                all_entry_atr_indices[ticker].append(ctx["entry0_idx"])
            all_cycles_meta.append({
                "dataset": ds["key"],
                "cross_date": weekly_candles[cyc["cross_index"]]["date"].isoformat(),
                "exit_date": (weekly_candles[cyc["exit_index"]]["date"].isoformat() if not cyc["open"] else None),
                "open": cyc["open"],
            })

        all_results["datasets"][ds["key"]] = {
            "label": ds["label"],
            "num_cycles": len(cycles),
            "dropped_incomplete_weekly_rows": [r["date"].isoformat() for r in dropped],
            "baseline_delay_grid": baseline_grid,
        }

    # ATR 분포 (진입 시점 기준)
    atr_summary = {
        "SOXL": sdb.summarize_atr_pct(soxl_daily, soxl_atr, all_entry_atr_indices["SOXL"], "SOXL 진입일 기준 일봉 ATR(14)/종가 비율"),
        "TQQQ": sdb.summarize_atr_pct(tqqq_daily, tqqq_atr, all_entry_atr_indices["TQQQ"], "TQQQ 진입일 기준 일봉 ATR(14)/종가 비율"),
    }
    # 전체 기간(진입일 한정 아님) 참고용 분포도 계산
    atr_summary_full = {
        "SOXL": sdb.summarize_atr_pct(soxl_daily, soxl_atr, [i for i in range(len(soxl_daily)) if soxl_atr[i] is not None], "SOXL 전체 기간 일봉 ATR(14)/종가 비율"),
        "TQQQ": sdb.summarize_atr_pct(tqqq_daily, tqqq_atr, [i for i in range(len(tqqq_daily)) if tqqq_atr[i] is not None], "TQQQ 전체 기간 일봉 ATR(14)/종가 비율"),
    }
    all_results["atr_pct_summary_at_entry"] = atr_summary
    all_results["atr_pct_summary_full_period"] = atr_summary_full

    print("=== 일봉 ATR(14)/종가 비율 분포 (진입일 20개 기준) ===")
    print(json.dumps(atr_summary, indent=2, ensure_ascii=False))
    print("=== 일봉 ATR(14)/종가 비율 분포 (전체 기간) ===")
    print(json.dumps(atr_summary_full, indent=2, ensure_ascii=False))

    # 손절 후보 파라미터 (ATR 분포 근거로 결정, 아래 print 결과를 보고 report에서 근거 서술)
    candidate_configs = [
        {"name": "fixed_25pct", "kind": "fixed", "pct": 0.25},
        {"name": "atr14_x2_cap35pct", "kind": "atr_cap", "multiplier": 2.0, "cap_pct": 0.35},
        {"name": "atr14_x3_cap40pct", "kind": "atr_cap", "multiplier": 3.0, "cap_pct": 0.40},
        {"name": "fixed_30pct", "kind": "fixed", "pct": 0.30},
        {"name": "fixed_20pct", "kind": "fixed", "pct": 0.20},
    ]

    all_results["candidate_configs"] = candidate_configs
    all_results["stoploss_candidates"] = {}
    all_results["baseline_trades_delay0"] = {}

    for ds in DATASETS:
        weekly_candles, cycles, dropped = sdb.load_weekly_cycles(
            ds["weekly_csv"], ds["weekly_delim"], ds["close_col"], ds["low_col"], TODAY,
            raw_close_col=ds["raw_close_col"],
        )
        daily_rows, atr = daily_map[ds["key"]]
        baseline_trades, cand_results = sdb.run_stoploss_candidates(
            weekly_candles, cycles, daily_rows, atr, ds["label"], candidate_configs
        )
        all_results["baseline_trades_delay0"][ds["key"]] = baseline_trades
        all_results["stoploss_candidates"][ds["key"]] = cand_results

    out_path = src["out"]
    all_results["daily_execution_source"] = src["tag"]
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(all_results, f, indent=2, ensure_ascii=False)
    print(f"\n[저장] {out_path}")

    total_cycles = sum(v["num_cycles"] for v in all_results["datasets"].values())
    print(f"\n총 사이클 수: {total_cycles} (soxl_twelvedata={all_results['datasets']['soxl_twelvedata']['num_cycles']}, "
          f"soxl_yahoo={all_results['datasets']['soxl_yahoo']['num_cycles']}, "
          f"tqqq_yahoo={all_results['datasets']['tqqq_yahoo']['num_cycles']})")


if __name__ == "__main__":
    main()

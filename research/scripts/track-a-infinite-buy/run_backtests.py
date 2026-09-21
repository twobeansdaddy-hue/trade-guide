#!/usr/bin/env python3
"""분할 평균화 사이클 롤링 윈도우 백테스트 러너.

- 종목: SOXL, TQQQ (Yahoo 일봉, 분할 조정 종가, 배당 미반영)
- 윈도우: 21거래일 간격 시작점, 보유 기간 H=3년(756일)/5년(1260일). 각 윈도우는 새로 자본 1.0으로 시작.
- 학습: 윈도우 종료일 <= 2018-12-31. 검증: 윈도우 시작일 >= 2019-01-01. 경계에 걸친 윈도우는 제외.
- 결과는 results/backtest_results.json에 저장한다(분석은 analyze.py).

실행: python3 research/scripts/track-a-infinite-buy/run_backtests.py
"""
import itertools
import json
import os
import sys
from datetime import date
from multiprocessing import Pool

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from averaging_cycle_sim import Bars, Params, sim_ac, sim_buy_hold, sim_const_exposure, sim_track_a  # noqa: E402

CACHE = os.path.join(HERE, "..", "..", "data", "cache")
ASSETS = {
    "SOXL": os.path.join(CACHE, "soxl-daily-yahoo-2010-2026.csv"),
    "TQQQ": os.path.join(CACHE, "tqqq-daily-yahoo-2010-2026.csv"),
}
HORIZONS = {"3y": 756, "5y": 1260}
STEP = 21
TRAIN_END = date(2018, 12, 31)
VALID_START = date(2019, 1, 1)

GRID = {
    "n": [20, 40],
    "s0": [0.10, 0.15, 0.20, 0.25],
    "line": ["dynamic", "static0", "statichalf"],
    "reverse": [True, False],
    "filter": [False, True],
}
CAFE_S0 = {"SOXL": 0.20, "TQQQ": 0.15}

_BARS = {}


def _init():
    for name, path in ASSETS.items():
        _BARS[name] = Bars.load(path)


def cfg_key(n, s0, line, reverse, flt, fill):
    return f"n{n}|s0={s0:.2f}|{line}|rev={int(reverse)}|flt={int(flt)}|{fill}"


def windows(B, horizon):
    out = []
    s = max(B.first_trend_idx, 5)
    while s + horizon - 1 < B.n:
        out.append((s, s + horizon - 1))
        s += STEP
    return out


def slim(r, B=None, s=None, e=None):
    last = r["cycles"][-1] if r["cycles"] else None
    bm_f, bm_mdd = sim_const_exposure(B, s, e, r["expo"]) if B is not None else (None, None)
    return {
        "expo": r["expo"], "bm_f": bm_f, "bm_mdd": bm_mdd,
        "fa": r["final_after_tax"], "f": r["final"], "mdd": r["mdd"], "cagr_at": r["cagr_after_tax"],
        "idle": r["idle_frac"], "ncyc": len(r["cycles"]),
        "open_loss": bool(last and last["open"] and last["ret"] < 0),
        "worst_cycle": min((c["ret"] for c in r["cycles"]), default=0.0),
        "rev_days": r.get("rev_days", 0), "exhaust": r.get("exhaust", 0),
    }


def run_config(args):
    asset, hname, key, n, s0, line, reverse, flt, fill = args
    B = _BARS[asset]
    p = Params(n_splits=n, s0=s0, line_mode=line, reverse=reverse, trend_filter=flt, fill=fill)
    return asset, hname, key, [slim(sim_ac(B, s, e, p), B, s, e) for s, e in windows(B, HORIZONS[hname])]


def run_baselines(args):
    asset, hname = args
    B = _BARS[asset]
    w = windows(B, HORIZONS[hname])
    return asset, hname, {
        "buy_hold": [slim(sim_buy_hold(B, s, e), B, s, e) for s, e in w],
        "track_a": [slim(sim_track_a(B, s, e), B, s, e) for s, e in w],
    }


def main():
    _init()
    meta = {}
    for name, B in _BARS.items():
        meta[name] = {
            "bars": B.n, "first": B.dates[0].isoformat(), "last": B.dates[-1].isoformat(),
            "first_trend_idx": B.first_trend_idx, "first_trend_date": B.dates[B.first_trend_idx].isoformat(),
        }
        for hname, h in HORIZONS.items():
            w = windows(B, h)
            meta[name][hname] = {
                "windows": len(w),
                "train": sum(1 for s, e in w if B.dates[e] <= TRAIN_END),
                "valid": sum(1 for s, e in w if B.dates[s] >= VALID_START),
                "starts": [B.dates[s].isoformat() for s, e in w],
                "ends": [B.dates[e].isoformat() for s, e in w],
            }
    jobs = []
    for asset, hname in itertools.product(ASSETS, HORIZONS):
        for n, s0, line, rev, flt in itertools.product(
                GRID["n"], GRID["s0"], GRID["line"], GRID["reverse"], GRID["filter"]):
            jobs.append((asset, hname, cfg_key(n, s0, line, rev, flt, "cons"), n, s0, line, rev, flt, "cons"))
        # 체결 가정 민감도: cafe 기본형(동적, 리버스 on, 필터 off)을 opt로도 실행
        for n in GRID["n"]:
            s0 = CAFE_S0[asset]
            jobs.append((asset, hname, cfg_key(n, s0, "dynamic", True, False, "opt"), n, s0, "dynamic", True, False, "opt"))
    results = {a: {h: {"configs": {}} for h in HORIZONS} for a in ASSETS}
    with Pool(initializer=_init) as pool:
        for asset, hname, key, res in pool.imap_unordered(run_config, jobs, chunksize=4):
            results[asset][hname]["configs"][key] = res
        for asset, hname, base in pool.imap_unordered(run_baselines, list(itertools.product(ASSETS, HORIZONS))):
            results[asset][hname].update(base)
    out_dir = os.path.join(HERE, "results")
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "backtest_results.json"), "w", encoding="utf-8") as f:
        json.dump({"meta": meta, "results": results}, f)
    print("jobs:", len(jobs), "done")
    for a in ASSETS:
        print(a, {h: meta[a][h]["windows"] for h in HORIZONS}, "train/valid(3y):", meta[a]["3y"]["train"], meta[a]["3y"]["valid"])


if __name__ == "__main__":
    main()

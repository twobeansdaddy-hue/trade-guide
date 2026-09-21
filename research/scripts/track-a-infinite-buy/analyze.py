#!/usr/bin/env python3
"""run_backtests.py 결과를 읽어 워크포워드 선택, 검증 성과, 가설 H1~H4 대조표를 출력한다.

선택 규칙(사전 고정): 학습 윈도우(3년) after-tax 최종자산 배수의 25백분위(p25)가 가장 큰 설정.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from averaging_cycle_sim import percentile  # noqa: E402
from run_backtests import CAFE_S0, GRID, cfg_key  # noqa: E402
from datetime import date  # noqa: E402

D = json.load(open(os.path.join(HERE, "results", "backtest_results.json")))
META, RES = D["meta"], D["results"]
TRAIN_END, VALID_START = date(2018, 12, 31), date(2019, 1, 1)


def idx_sets(asset, h):
    m = META[asset][h]
    starts = [date.fromisoformat(x) for x in m["starts"]]
    ends = [date.fromisoformat(x) for x in m["ends"]]
    train = [i for i, e in enumerate(ends) if e <= TRAIN_END]
    valid = [i for i, s in enumerate(starts) if s >= VALID_START]
    return {"train": train, "valid": valid, "all": list(range(len(starts)))}


def stat(rows):
    fa = [r["fa"] for r in rows]
    return {
        "n": len(rows), "med": percentile(fa, .5), "p25": percentile(fa, .25), "p10": percentile(fa, .10),
        "min": min(fa), "loss": sum(1 for x in fa if x < 1) / len(fa),
        "mdd_med": percentile([r["mdd"] for r in rows], .5), "mdd_worst": min(r["mdd"] for r in rows),
        "open_loss": sum(1 for r in rows if r["open_loss"]) / len(rows),
        "idle": percentile([r["idle"] for r in rows], .5),
        "ncyc": percentile([r["ncyc"] for r in rows], .5),
        "worst_cycle": min(r["worst_cycle"] for r in rows),
    }


def rows_of(asset, h, key, ids):
    src = RES[asset][h]["configs"][key] if key not in ("buy_hold", "track_a") else RES[asset][h][key]
    return [src[i] for i in ids]


def fmt(s):
    return (f"n={s['n']:>3} 중앙={s['med']:.2f}x p25={s['p25']:.2f}x p10={s['p10']:.2f}x 최저={s['min']:.2f}x "
            f"손실={s['loss']*100:>3.0f}% MDD중앙={s['mdd_med']*100:.0f}% MDD최악={s['mdd_worst']*100:.0f}% "
            f"미청산손실={s['open_loss']*100:>3.0f}% 사이클중앙={s['ncyc']:.0f} 최악사이클={s['worst_cycle']*100:.0f}%")


def paired(asset, h, ka, kb, ids):
    a, b = rows_of(asset, h, ka, ids), rows_of(asset, h, kb, ids)
    d = [x["fa"] / y["fa"] - 1 for x, y in zip(a, b)]
    return {"med_rel": percentile(d, .5), "win": sum(1 for x in d if x > 0) / len(d),
            "mdd_delta": percentile([x["mdd"] - y["mdd"] for x, y in zip(a, b)], .5)}


def main():
    out = {}
    all_keys = [cfg_key(n, s0, ln, rv, fl, "cons")
                for n in GRID["n"] for s0 in GRID["s0"] for ln in GRID["line"]
                for rv in GRID["reverse"] for fl in GRID["filter"]]
    for asset in ("SOXL", "TQQQ"):
        print(f"\n{'=' * 20} {asset} {'=' * 20}")
        for h in ("3y", "5y"):
            print(f"\n--- 보유 {h} / 기준선 대조(after-tax 최종자산 배수, 시작 1.0) ---")
            sets = idx_sets(asset, h)
            for name in ("train", "valid", "all"):
                ids = sets[name]
                print(f"[{name}] B&H       ", fmt(stat(rows_of(asset, h, "buy_hold", ids))))
                print(f"[{name}] Track A   ", fmt(stat(rows_of(asset, h, "track_a", ids))))
        sets3 = idx_sets(asset, "3y")
        scored = sorted(all_keys, key=lambda k: -stat(rows_of(asset, "3y", k, sets3["train"]))["p25"])
        print("\n--- 학습(3y) p25 상위 5 → 검증 성과 ---")
        for k in scored[:5]:
            print(k)
            print("   train", fmt(stat(rows_of(asset, "3y", k, sets3["train"]))))
            print("   valid", fmt(stat(rows_of(asset, "3y", k, sets3["valid"]))))
            print("   valid5y", fmt(stat(rows_of(asset, "5y", k, idx_sets(asset, "5y")["valid"]))))
        best_nofilter = next(k for k in scored if "flt=0" in k)
        print("\n필터 없는 최고:", best_nofilter)
        print("   valid", fmt(stat(rows_of(asset, "3y", best_nofilter, sets3["valid"]))))
        out[asset] = {"best": scored[0], "best_nofilter": best_nofilter}

        print("\n--- cafe 기본형(동적선, 리버스 on, 필터 off, s0=%.2f) 검증 3y / 5y / 체결가정 민감도 ---" % CAFE_S0[asset])
        for n in (20, 40):
            for fill in ("cons", "opt"):
                k = cfg_key(n, CAFE_S0[asset], "dynamic", True, False, fill)
                print(f"N={n} {fill}: 3y valid", fmt(stat(rows_of(asset, "3y", k, sets3["valid"]))))
                print(f"        3y all  ", fmt(stat(rows_of(asset, "3y", k, sets3["all"]))))
                print(f"        5y valid", fmt(stat(rows_of(asset, "5y", k, idx_sets(asset, "5y")["valid"]))))

        print("\n--- 가설 대조 (3y 전체 윈도우, 쌍대비교: 중앙 상대차 / 승률 / MDD중앙차) ---")
        ids = sets3["all"]
        for n in (20, 40):
            for s0 in GRID["s0"]:
                base = cfg_key(n, s0, "dynamic", True, False, "cons")
                h1a = paired(asset, "3y", base, cfg_key(n, s0, "static0", True, False, "cons"), ids)
                h1b = paired(asset, "3y", base, cfg_key(n, s0, "statichalf", True, False, "cons"), ids)
                h3 = paired(asset, "3y", base, cfg_key(n, s0, "dynamic", False, False, "cons"), ids)
                h4 = paired(asset, "3y", cfg_key(n, s0, "dynamic", True, True, "cons"), base, ids)
                f = lambda x: f"{x['med_rel']*100:+6.1f}% 승률{x['win']*100:3.0f}% MDD{x['mdd_delta']*100:+5.1f}p"
                print(f"N={n} s0={s0:.2f} | H1 동적vs정적0 {f(h1a)} | 동적vs정적절반 {f(h1b)} | H3 리버스on vs off {f(h3)} | H4 필터on vs off {f(h4)}")
        for n in (20, 40):
            kf = cfg_key(n, CAFE_S0[asset], "dynamic", True, True, "cons")
            print(f"필터 결합 N={n} s0={CAFE_S0[asset]:.2f}: 3y all", fmt(stat(rows_of(asset, "3y", kf, ids))))
    json.dump(out, open(os.path.join(HERE, "results", "selection.json"), "w"), indent=1)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""노출 통제 비교: 전략 평균 노출과 같은 비중을 매일 재조정하는 (종목+현금) 벤치마크 대비 성과.

'낙폭이 작다'가 노출이 낮아서인지 규칙 덕분인지 가른다. 벤치마크는 비용·세금을 무시하므로 전략에 불리한 방향이다.
비교는 세전 최종자산(f)과 MDD로 한다.
"""
import json
import os
import sys
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from averaging_cycle_sim import percentile  # noqa: E402
from run_backtests import CAFE_S0, cfg_key  # noqa: E402

D = json.load(open(os.path.join(HERE, "results", "backtest_results.json")))
META, RES = D["meta"], D["results"]
VALID_START, TRAIN_END = date(2019, 1, 1), date(2018, 12, 31)


def subset(asset, h, which):
    m = META[asset][h]
    st = [date.fromisoformat(x) for x in m["starts"]]
    en = [date.fromisoformat(x) for x in m["ends"]]
    if which == "train":
        return [i for i, e in enumerate(en) if e <= TRAIN_END]
    if which == "valid":
        return [i for i, s in enumerate(st) if s >= VALID_START]
    return list(range(len(st)))


def get(asset, h, key):
    return RES[asset][h]["configs"][key] if key not in ("buy_hold", "track_a") else RES[asset][h][key]


def matched(asset, h, key, which):
    ids = subset(asset, h, which)
    rows = [get(asset, h, key)[i] for i in ids]
    rel = [r["f"] / r["bm_f"] - 1 for r in rows]
    return (f"n={len(rows):>3} 노출={percentile([r['expo'] for r in rows], .5)*100:>3.0f}% "
            f"세전 최종 중앙={percentile([r['f'] for r in rows], .5):.2f}x "
            f"벤치(동일노출) 중앙={percentile([r['bm_f'] for r in rows], .5):.2f}x "
            f"상대차 중앙={percentile(rel, .5)*100:+5.1f}% 승률={sum(1 for x in rel if x > 0)/len(rel)*100:>3.0f}% | "
            f"MDD 전략중앙={percentile([r['mdd'] for r in rows], .5)*100:.0f}% 벤치중앙={percentile([r['bm_mdd'] for r in rows], .5)*100:.0f}% "
            f"| 최저 최종={min(r['f'] for r in rows):.2f}x")


def by_year(asset, keys):
    m = META[asset]["3y"]
    years = sorted({x[:4] for x in m["starts"]})
    print("  시작연도별 3y 세후 최종 중앙(x) - " + " | ".join(n for n, _ in keys))
    for y in years:
        ids = [i for i, s in enumerate(m["starts"]) if s.startswith(y)]
        cells = []
        for _, k in keys:
            rows = [get(asset, "3y", k)[i] for i in ids]
            cells.append(f"{percentile([r['fa'] for r in rows], .5):5.2f}")
        print(f"  {y} (n={len(ids)}): " + " | ".join(cells))


def main():
    for asset in ("SOXL", "TQQQ"):
        s0 = CAFE_S0[asset]
        print(f"\n{'=' * 18} {asset}: 노출 통제 (s0={s0:.2f}, dynamic, filter off, cons) {'=' * 18}")
        for h in ("3y", "5y"):
            for which in ("all", "valid"):
                print(f"\n[{h} / {which}]")
                for n in (20, 40):
                    for rev in (True, False):
                        k = cfg_key(n, s0, "dynamic", rev, False, "cons")
                        print(f"  N={n} 리버스={'on ' if rev else 'off'} {matched(asset, h, k, which)}")
                print(f"  Track A          {matched(asset, h, 'track_a', which)}")
        keys = [("N20", cfg_key(20, s0, "dynamic", True, False, "cons")),
                ("N40", cfg_key(40, s0, "dynamic", True, False, "cons")),
                ("TrackA", "track_a"), ("B&H", "buy_hold")]
        by_year(asset, keys)


if __name__ == "__main__":
    main()

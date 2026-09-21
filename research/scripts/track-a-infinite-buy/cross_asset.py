#!/usr/bin/env python3
"""교차 자산 검증: cafe 기본형(동적선, 필터 off)을 SOXL/TQQQ 외 3배 ETF(FAS, TNA)에서도 검증한다.

FAS/TNA 일봉은 2008년부터라 2008-09 금융위기를 포함한다(SOXL/TQQQ 표본에는 없는 구간).
동일 노출 벤치마크 대비 세전 최종자산, MDD, 3y 윈도우(21일 간격)로 평가한다.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from averaging_cycle_sim import Bars, Params, percentile, sim_ac, sim_buy_hold, sim_const_exposure  # noqa: E402

CACHE = os.path.join(HERE, "..", "..", "data", "cache")
ASSETS = {"FAS": "fas-daily-yahoo-2008-2026.csv", "TNA": "tna-daily-yahoo-2008-2026.csv"}
H, STEP = 756, 21


def main():
    for name, fn in ASSETS.items():
        B = Bars.load(os.path.join(CACHE, fn))
        s0_start = max(B.first_trend_idx, 5)
        wins = []
        s = s0_start
        while s + H - 1 < B.n:
            wins.append((s, s + H - 1))
            s += STEP
        print(f"\n== {name}  {B.dates[0]}~{B.dates[-1]}  windows={len(wins)} (첫 윈도우 {B.dates[wins[0][0]]}) ==")
        bh = [sim_buy_hold(B, a, b) for a, b in wins]
        print(f"B&H       중앙 세후 {percentile([r['final_after_tax'] for r in bh], .5):.2f}x  MDD중앙 {percentile([r['mdd'] for r in bh], .5)*100:.0f}%  최저 {min(r['final_after_tax'] for r in bh):.2f}x")
        for s0 in (0.15, 0.20):
            for n in (20, 40):
                for rev in (True, False):
                    rs = [sim_ac(B, a, b, Params(n_splits=n, s0=s0, reverse=rev)) for a, b in wins]
                    bm = [sim_const_exposure(B, a, b, r["expo"]) for (a, b), r in zip(wins, rs)]
                    rel = [r["final"] / m[0] - 1 for r, m in zip(rs, bm)]
                    print(f"s0={s0:.2f} N={n} 리버스={'on ' if rev else 'off'} 노출{percentile([r['expo'] for r in rs], .5)*100:>3.0f}% "
                          f"세후 중앙 {percentile([r['final_after_tax'] for r in rs], .5):.2f}x 최저 {min(r['final_after_tax'] for r in rs):.2f}x "
                          f"손실윈도우 {sum(1 for r in rs if r['final_after_tax'] < 1)/len(rs)*100:>3.0f}% | 동일노출 대비 중앙 {percentile(rel, .5)*100:+6.1f}% 승률 {sum(1 for x in rel if x > 0)/len(rel)*100:>3.0f}% "
                          f"| MDD 전략 {percentile([r['mdd'] for r in rs], .5)*100:.0f}% 벤치 {percentile([m[1] for m in bm], .5)*100:.0f}%")


if __name__ == "__main__":
    main()

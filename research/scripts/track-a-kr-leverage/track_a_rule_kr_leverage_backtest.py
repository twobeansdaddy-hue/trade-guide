#!/usr/bin/env python3
"""Track A-KR - Track A 프로덕션 규칙을 국내 레버리지 ETF 3종에 그대로 적용.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
Track A(`WeeklyMaCrossoverStrategy.java`)가 실제 운영에 쓰는 규칙을 그대로 재현해,
국내 레버리지 ETF 3종(KODEX 레버리지 122630, KODEX 코스닥150레버리지 233740,
TIGER 200선물레버리지 267770)에 개별 종목 단위로 적용한다. (구)Track C가 "국내 일반주에
레버리지 방법론"을 시도했던 것과 반대로, 이번엔 "레버리지 방법론을 레버리지 자산에"
적용하는 것이라 자산 분류 프레임(`research/STRATEGY_ENGINE_POLICY.md`)의 두 축(데이터
계층, 변동성/구조)이 미국 Track A(SOXL/TQQQ)와 동일하다.

**정확히 재현하는 프로덕션 규칙**:
- trend = ABOVE if SMA(10주 종가) > SMA(40주 종가), 아니면 BELOW.
- CROSS_UP: 직전 주 SMA10<=SMA40, 이번 주 SMA10>SMA40. CROSS_DOWN은 반대.
- weeksSinceCross: 가장 최근 CROSS_UP/DOWN 이벤트로부터 경과 주.
- 진입: trend==ABOVE AND weeksSinceCross 0~4주 (미보유 상태에서는 크로스 당주에 이미
  조건을 만족하므로 지연 없이 크로스 당주 종가로 진입 - 기존 Track A 시장국면필터
  검증(`track-a-market-regime-filter-*.md`)의 "delay=0 베이스라인"과 동일한 정의).
- 청산: 보유 중 trend가 BELOW로 전환되는 즉시(= CROSS_DOWN 당주 종가) 매도. 손절 레이어는
  미국 Track A에서도 채택 후보가 없으므로(v2 보류) 이번에도 추가하지 않는다.

**"사이클" 정의**: 한 종목에서 CROSS_UP으로 시작해 다음 CROSS_DOWN(또는 데이터 종료 시점,
"미종료 사이클"로 별도 표시)까지를 하나의 사이클로 본다. 이는 기존 미국 Track A 검증
(SOXL/TQQQ/TNA/FAS 49사이클, `track-a-market-regime-filter-tna-fas-extension.md`)과
동일한 집계 단위다 - 포트폴리오 동일가중 재조정(Track C 스크립트 방식)이 아니라, 개별
종목·개별 진입 이벤트 단위로 집계한다.

가설: SOXL/TQQQ(3배, 미국)에서 관찰된 효과(대형 낙폭 회피)가 레버리지 상품 고유의 특성
때문이라면, 배수(x2, 국내)가 다르더라도 국내 레버리지 ETF에서도 유사하게 나타나야 한다.
다만 사전에 명시한다 - 3배와 2배는 decay 속도·낙폭 크기가 다를 수 있고, 3종 중 2종은
2015~2017년 상장이라 관측 가능한 하락장 사이클 수 자체가 미국 표본(2010년대 초반부터)보다
적을 수 있다.

비용
----
Track B/C와 동일하게 왕복 0/20/40/57bp 시나리오를 그대로 쓴다 - 국내 레버리지 ETF의
실제 매매비용을 별도로 조사하지 않았다는 것을 caveat으로 남긴다.

실행: python3 research/scripts/track-a-kr-leverage/track_a_rule_kr_leverage_backtest.py
"""
import csv
import json
import math
import os

SHORT_PERIOD = 10
LONG_PERIOD = 40
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]

TICKER_NAMES = {
    "122630": "KODEX 레버리지",
    "233740": "KODEX 코스닥150레버리지",
    "267770": "TIGER 200선물레버리지",
}

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-kr-leverage"
)
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "track_a_rule_backtest_results.json")


def load_universe_csv(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            out.setdefault(row["ticker"], []).append((row["datetime"], float(row["close"])))
    for t in out:
        out[t].sort(key=lambda x: x[0])
    return out


def sma_series(closes):
    sma10, sma40 = [], []
    for i in range(len(closes)):
        if i + 1 >= SHORT_PERIOD:
            sma10.append(sum(closes[i + 1 - SHORT_PERIOD:i + 1]) / SHORT_PERIOD)
        else:
            sma10.append(None)
        if i + 1 >= LONG_PERIOD:
            sma40.append(sum(closes[i + 1 - LONG_PERIOD:i + 1]) / LONG_PERIOD)
        else:
            sma40.append(None)
    return sma10, sma40


def trend_and_events(closes):
    sma10, sma40 = sma_series(closes)
    n = len(closes)
    trend = [None] * n
    for i in range(n):
        if sma10[i] is None or sma40[i] is None:
            continue
        trend[i] = "ABOVE" if sma10[i] > sma40[i] else "BELOW"

    events = [None] * n
    for i in range(1, n):
        if sma10[i] is None or sma40[i] is None or sma10[i - 1] is None or sma40[i - 1] is None:
            continue
        prev_above = sma10[i - 1] > sma40[i - 1]
        cur_above = sma10[i] > sma40[i]
        if not prev_above and cur_above:
            events[i] = "CROSS_UP"
        elif prev_above and not cur_above:
            events[i] = "CROSS_DOWN"
    return trend, events


def leg_cost_fraction(bps):
    """왕복 비용(bps)의 절반을 매수·매도 각 1회(leg)에 적용한다."""
    return bps / 2.0 / 10000.0


def find_cycles(dates, closes, trend, events):
    """CROSS_UP(진입, delay=0) -> CROSS_DOWN(청산) 사이클 목록을 반환한다.
    데이터 종료 시점까지 CROSS_DOWN이 없으면 'open'=True인 미종료 사이클로 남긴다."""
    cycles = []
    i = 0
    n = len(dates)
    while i < n:
        if events[i] == "CROSS_UP":
            entry_idx = i
            exit_idx = None
            for j in range(i + 1, n):
                if events[j] == "CROSS_DOWN":
                    exit_idx = j
                    break
            if exit_idx is not None:
                cycles.append({
                    "entryDate": dates[entry_idx], "entryPrice": closes[entry_idx],
                    "exitDate": dates[exit_idx], "exitPrice": closes[exit_idx],
                    "open": False, "holdWeeks": exit_idx - entry_idx,
                    "pathCloses": closes[entry_idx:exit_idx + 1],
                })
                i = exit_idx + 1
                continue
            else:
                cycles.append({
                    "entryDate": dates[entry_idx], "entryPrice": closes[entry_idx],
                    "exitDate": dates[-1], "exitPrice": closes[-1],
                    "open": True, "holdWeeks": (n - 1) - entry_idx,
                    "pathCloses": closes[entry_idx:],
                })
                break
        i += 1
    return cycles


def cycle_mdd_pct(path_closes):
    peak, mdd = -math.inf, 0.0
    for p in path_closes:
        peak = max(peak, p)
        if peak > 0:
            mdd = min(mdd, (p - peak) / peak * 100.0)
    return mdd


def compute_return_pct(entry_price, exit_price, bps):
    frac = leg_cost_fraction(bps)
    eff_entry = entry_price * (1.0 + frac)
    eff_exit = exit_price * (1.0 - frac)
    return (eff_exit / eff_entry - 1.0) * 100.0


def buy_and_hold_return_and_mdd(closes):
    if not closes:
        return None, None
    ret = (closes[-1] / closes[0] - 1.0) * 100.0
    mdd = cycle_mdd_pct(closes)
    return ret, mdd


def run_all():
    universe = load_universe_csv(CSV_PATH)

    per_ticker_results = {}
    all_closed_cycles = []  # (ticker, cycle) across all tickers, closed only

    for ticker, rows in universe.items():
        dates = [d for d, _ in rows]
        closes = [c for _, c in rows]
        trend, events = trend_and_events(closes)
        cycles = find_cycles(dates, closes, trend, events)
        closed = [c for c in cycles if not c["open"]]
        open_cycles = [c for c in cycles if c["open"]]

        bh_ret, bh_mdd = buy_and_hold_return_and_mdd(closes)

        by_cost = {}
        for bps in COST_BPS_SCENARIOS:
            rets = [compute_return_pct(c["entryPrice"], c["exitPrice"], bps) for c in closed]
            wins = [r for r in rets if r > 0]
            compounded = 1.0
            for r in rets:
                compounded *= (1.0 + r / 100.0)
            by_cost[str(bps)] = {
                "numClosedCycles": len(rets),
                "winRatePct": round(100.0 * len(wins) / len(rets), 1) if rets else None,
                "avgReturnPct": round(sum(rets) / len(rets), 2) if rets else None,
                "medianReturnPct": round(sorted(rets)[len(rets) // 2], 2) if rets else None,
                "sequentialCompoundedReturnPct": round((compounded - 1.0) * 100.0, 2) if rets else None,
            }

        per_ticker_results[ticker] = {
            "name": TICKER_NAMES.get(ticker, ticker),
            "dataRange": {"start": dates[0], "end": dates[-1], "weeklyCandleCount": len(dates)},
            "numClosedCycles": len(closed),
            "numOpenCycles": len(open_cycles),
            "cycles": [
                {
                    "entryDate": c["entryDate"], "entryPrice": round(c["entryPrice"], 2),
                    "exitDate": c["exitDate"], "exitPrice": round(c["exitPrice"], 2),
                    "holdWeeks": c["holdWeeks"], "open": c["open"],
                    "rawReturnPct": round((c["exitPrice"] / c["entryPrice"] - 1.0) * 100.0, 2),
                    "cycleMaxDrawdownPct": round(cycle_mdd_pct(c["pathCloses"]), 2),
                }
                for c in cycles
            ],
            "byCostBps": by_cost,
            "buyAndHoldReturnPct": round(bh_ret, 2) if bh_ret is not None else None,
            "buyAndHoldMaxDrawdownPct": round(bh_mdd, 2) if bh_mdd is not None else None,
        }
        for c in closed:
            all_closed_cycles.append((ticker, c))

    combined_by_cost = {}
    for bps in COST_BPS_SCENARIOS:
        rets = [compute_return_pct(c["entryPrice"], c["exitPrice"], bps) for _, c in all_closed_cycles]
        wins = [r for r in rets if r > 0]
        combined_by_cost[str(bps)] = {
            "numClosedCycles": len(rets),
            "winRatePct": round(100.0 * len(wins) / len(rets), 1) if rets else None,
            "avgReturnPct": round(sum(rets) / len(rets), 2) if rets else None,
            "medianReturnPct": round(sorted(rets)[len(rets) // 2], 2) if rets else None,
        }

    output = {
        "generatedBy": "research/scripts/track-a-kr-leverage/track_a_rule_kr_leverage_backtest.py",
        "hypothesis": "Track A 프로덕션 규칙(10주/40주 SMA 골든크로스, 진입 0-4주=delay 0, "
                       "청산 CROSS_DOWN 당주)을 국내 레버리지 ETF 3종에 개별 종목·사이클 단위로 적용.",
        "tickers": list(universe.keys()),
        "costScenariosRoundTripBps": COST_BPS_SCENARIOS,
        "perTicker": per_ticker_results,
        "combinedAcrossTickers": {
            "numTickers": len(universe),
            "totalClosedCycles": len(all_closed_cycles),
            "totalOpenCycles": sum(r["numOpenCycles"] for r in per_ticker_results.values()),
            "byCostBps": combined_by_cost,
        },
    }

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A-KR 국내 레버리지 ETF - Track A 규칙 사이클 백테스트 요약")
    print("=" * 100)
    for ticker, r in output["perTicker"].items():
        print(f"\n[{ticker}] {r['name']} ({r['dataRange']['start']} ~ {r['dataRange']['end']}, "
              f"주봉 {r['dataRange']['weeklyCandleCount']}개)")
        print(f"  종료 사이클: {r['numClosedCycles']}건, 미종료(진행중) 사이클: {r['numOpenCycles']}건")
        print(f"  단순보유(Buy&Hold) 수익률: {r['buyAndHoldReturnPct']}%, MDD: {r['buyAndHoldMaxDrawdownPct']}%")
        for bps in COST_BPS_SCENARIOS:
            b = r["byCostBps"][str(bps)]
            if b["numClosedCycles"] == 0:
                print(f"    {bps:5.0f}bp: 종료 사이클 없음")
                continue
            print(f"    {bps:5.0f}bp: n={b['numClosedCycles']:>2} 승률={b['winRatePct']}% "
                  f"평균={b['avgReturnPct']}% 중앙값={b['medianReturnPct']}% "
                  f"순차복리={b['sequentialCompoundedReturnPct']}%")
        for c in r["cycles"]:
            status = "진행중" if c["open"] else "종료"
            print(f"    - {c['entryDate']}({c['entryPrice']}) -> {c['exitDate']}({c['exitPrice']}) "
                  f"[{status}] 원수익률={c['rawReturnPct']}% 보유{c['holdWeeks']}주 "
                  f"사이클중MDD={c['cycleMaxDrawdownPct']}%")

    print("\n--- 3종목 통합(종료 사이클 전체) ---")
    for bps in COST_BPS_SCENARIOS:
        c = output["combinedAcrossTickers"]["byCostBps"][str(bps)]
        if c["numClosedCycles"] == 0:
            print(f"  {bps:5.0f}bp: 종료 사이클 없음")
            continue
        print(f"  {bps:5.0f}bp: n={c['numClosedCycles']:>2} 승률={c['winRatePct']}% "
              f"평균={c['avgReturnPct']}% 중앙값={c['medianReturnPct']}%")


if __name__ == "__main__":
    run_all()

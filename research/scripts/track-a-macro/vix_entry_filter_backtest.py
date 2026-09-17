#!/usr/bin/env python3
"""Track A 매크로 오버레이 첫 가설 - 진입 시점 VIX 레벨 필터.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
`research/reports/track-a-market-regime-filter-tna-fas-extension.md`가 검증한 "SPY
40주선 필터"는 49개 사이클 전부에서 단 한 번도 발동하지 않았다 - 그 리포트의 해석대로,
10/40주 이동평균 골든크로스 자체가 이미 "가격이 충분히 올라야" 성립하는 추세추종
신호라, 크로스 시점에는 SPY(넓은 시장)도 이미 상승 추세인 경우가 대부분이라 가격
기반 필터끼리는 서로 거의 항상 일치한다.

VIX는 가격 추세와 다른 축의 정보다(변동성/공포 지표이지 추세 지표가 아니다) - 크로스가
확정되는 시점에도 VIX는 여전히 높을 수 있다(예: 급락 후 반등이 시작돼 가격은 이미
추세를 회복했지만 시장 전체의 불안 심리는 아직 가라앉지 않은 국면). 따라서 SPY
필터보다 실제로 발동할 여지가 있는 다른 축의 가설로 간주한다.

**가설**: Track A 진입(CROSS_UP 당주, delay=0) 시점의 VIX 레벨이 이후 사이클 수익률과
관계가 있다 - 구체적으로 진입 시점 VIX가 높을수록(공포 국면일수록) 이후 사이클 수익률이
나쁘다.

**임계값 (결과를 보기 전에 고정, 이 스크립트 실행 전 리포트에 먼저 기록)**:
- 1차: VIX >= 25 (통상 "변동성 확대" 구간으로 통용되는 값 - CBOE 관례상 20 미만은
  평온, 20~30은 확대, 30 이상은 고공포로 흔히 구분한다).
- 2차(강건성): VIX >= 30 ("고공포" 구간).
- 임계값과 무관한 검증으로 진입 시점 VIX와 사이클 수익률의 상관계수(피어슨)도 함께
  계산한다.

**적용 대상**: SOXL, TQQQ, TNA, FAS(기존 시장국면필터 검증과 동일한 4종 미국 레버리지
ETF, 오늘 새로 재수집 - 기존 캐시를 파일 권한 문제로 재사용 불가해 49사이클과 정확히
일치하지 않을 수 있음).

**사이클 정의**: `research/scripts/track-a-kr-leverage/track_a_rule_kr_leverage_backtest.py`
와 동일 - CROSS_UP(delay=0 진입) -> CROSS_DOWN(청산) 사이클 단위.

실행: python3 research/scripts/track-a-macro/vix_entry_filter_backtest.py
"""
import csv
import json
import math
import os

SHORT_PERIOD = 10
LONG_PERIOD = 40
VIX_THRESHOLDS = [25.0, 30.0]

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro"
)
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "vix_entry_filter_results.json")

ETF_TICKERS = ["SOXL", "TQQQ", "TNA", "FAS"]
VIX_KEY = "VIX"


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
    return events


def find_cycles(dates, closes, events):
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
                    "open": False,
                })
                i = exit_idx + 1
                continue
            else:
                cycles.append({
                    "entryDate": dates[entry_idx], "entryPrice": closes[entry_idx],
                    "exitDate": dates[-1], "exitPrice": closes[-1],
                    "open": True,
                })
                break
        i += 1
    return cycles


def nearest_vix(vix_by_date, sorted_vix_dates, target_date):
    """target_date 이하 중 가장 가까운 VIX 주봉 종가(같은 주 데이터가 없을 때 직전 주로 근사)."""
    candidates = [d for d in sorted_vix_dates if d <= target_date]
    if not candidates:
        return None
    return vix_by_date[candidates[-1]]


def pearson(xs, ys):
    n = len(xs)
    if n < 2:
        return None
    mx = sum(xs) / n
    my = sum(ys) / n
    cov = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    vx = sum((x - mx) ** 2 for x in xs)
    vy = sum((y - my) ** 2 for y in ys)
    if vx <= 0 or vy <= 0:
        return None
    return cov / math.sqrt(vx * vy)


def summarize(cycles):
    if not cycles:
        return None
    rets = [c["returnPct"] for c in cycles]
    wins = [r for r in rets if r > 0]
    return {
        "n": len(rets),
        "winRatePct": round(100.0 * len(wins) / len(rets), 1),
        "avgReturnPct": round(sum(rets) / len(rets), 2),
        "medianReturnPct": round(sorted(rets)[len(rets) // 2], 2),
    }


def run_all():
    universe = load_universe_csv(CSV_PATH)
    vix_rows = sorted(universe[VIX_KEY], key=lambda x: x[0])
    vix_by_date = {d: c for d, c in vix_rows}
    sorted_vix_dates = [d for d, _ in vix_rows]

    all_cycles = []
    per_ticker = {}
    for ticker in ETF_TICKERS:
        rows = universe[ticker]
        dates = [d for d, _ in rows]
        closes = [c for _, c in rows]
        events = trend_and_events(closes)
        cycles = find_cycles(dates, closes, events)
        closed = [c for c in cycles if not c["open"]]
        for c in closed:
            entry_vix = nearest_vix(vix_by_date, sorted_vix_dates, c["entryDate"])
            ret = (c["exitPrice"] / c["entryPrice"] - 1.0) * 100.0
            record = {"ticker": ticker, "entryDate": c["entryDate"], "exitDate": c["exitDate"],
                      "entryVix": entry_vix, "returnPct": round(ret, 2)}
            all_cycles.append(record)
        per_ticker[ticker] = {"numClosedCycles": len(closed), "numOpenCycles": len(cycles) - len(closed)}

    # 진입 VIX 결측(정렬 실패 등) 제외
    valid = [c for c in all_cycles if c["entryVix"] is not None]

    output = {
        "generatedBy": "research/scripts/track-a-macro/vix_entry_filter_backtest.py",
        "hypothesis": "Track A 진입(CROSS_UP delay=0) 시점 VIX 레벨이 높을수록 이후 사이클 수익률이 나쁘다.",
        "preRegisteredThresholds": VIX_THRESHOLDS,
        "tickers": ETF_TICKERS,
        "perTickerCycleCounts": per_ticker,
        "totalValidCycles": len(valid),
        "cycles": all_cycles,
        "byThreshold": {},
        "correlation": {},
    }

    for th in VIX_THRESHOLDS:
        below = [c for c in valid if c["entryVix"] < th]
        above = [c for c in valid if c["entryVix"] >= th]
        output["byThreshold"][str(th)] = {
            "belowThreshold": summarize(below),
            "atOrAboveThreshold": summarize(above),
        }

    xs = [c["entryVix"] for c in valid]
    ys = [c["returnPct"] for c in valid]
    output["correlation"]["pearson_entryVix_vs_returnPct"] = round(pearson(xs, ys), 3) if pearson(xs, ys) is not None else None

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 100)
    print("Track A 매크로 오버레이 - 진입 시점 VIX 필터 가설 검증")
    print("=" * 100)
    print(f"\n유효 사이클(진입 VIX 확보) 수: {output['totalValidCycles']}")
    for ticker, c in output["perTickerCycleCounts"].items():
        print(f"  {ticker}: 종료 {c['numClosedCycles']}건, 미종료 {c['numOpenCycles']}건")

    print(f"\n피어슨 상관계수(진입 VIX vs 사이클 수익률): {output['correlation']['pearson_entryVix_vs_returnPct']}")

    for th, res in output["byThreshold"].items():
        print(f"\n--- 임계값 VIX {th} ---")
        b, a = res["belowThreshold"], res["atOrAboveThreshold"]
        if b:
            print(f"  VIX < {th}: n={b['n']} 승률={b['winRatePct']}% 평균={b['avgReturnPct']}% 중앙값={b['medianReturnPct']}%")
        else:
            print(f"  VIX < {th}: 사이클 없음")
        if a:
            print(f"  VIX >= {th}: n={a['n']} 승률={a['winRatePct']}% 평균={a['avgReturnPct']}% 중앙값={a['medianReturnPct']}%")
        else:
            print(f"  VIX >= {th}: 사이클 없음")

    print("\n--- 개별 사이클(진입일 순) ---")
    for c in sorted(output["cycles"], key=lambda x: x["entryDate"]):
        print(f"  [{c['ticker']}] {c['entryDate']} (VIX={c['entryVix']}) -> {c['exitDate']} "
              f"수익률={c['returnPct']}%")


if __name__ == "__main__":
    run_all()

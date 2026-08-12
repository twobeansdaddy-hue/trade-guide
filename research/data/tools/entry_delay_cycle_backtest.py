#!/usr/bin/env python3
"""
entry_delay_cycle_backtest.py

목적
----
WeeklyMaCrossoverStrategy(src/main/java/com/tradeguide/service/strategy/tracka/WeeklyMaCrossoverStrategy.java)와
동일한 SMA(10주)/SMA(40주) 교차 규칙을 순수 파이썬으로 독립 재구현하여,
"CROSS_UP 이후 진입을 N주 지연시키면 사이클 수익률과 최대낙폭(MDD)이 어떻게 달라지는가"를 계산한다.

전략 규칙 재현 (프로덕션 소스와 대조 완료, 2026-08-12)
----------------------------------------------------
- SMA(period)[i] = candles[i-period+1 .. i] 종가의 단순평균 (SimpleMovingAverageCalculator.calculate와 동일:
  마지막 period개 종가의 평균, BigDecimal HALF_UP 4자리 반올림 대신 파이썬 float를 쓰지만 유의미한 차이는 없음).
- trend[i] = ABOVE_LONG_AVERAGE if SMA10[i] > SMA40[i] else BELOW_LONG_AVERAGE  (i >= long_period-1 에서만 정의)
- signal_event[i] (i >= long_period, 즉 이전 시점도 SMA 계산 가능해야 함):
    prev = i-1 시점 값, curr = i 시점 값
    CROSS_UP   : prev_short <= prev_long AND curr_short > curr_long
    CROSS_DOWN : prev_short >= prev_long AND curr_short < curr_long
    NONE       : 그 외
  (WeeklyMaCrossoverStrategy.determineSignalEvent와 동일한 부등호 방향)

사이클과 진입 지연 정의
----------------------
- "사이클"은 CROSS_UP이 발생한 주(진입 후보 시작점)부터, 그 이후 처음 발생하는 CROSS_DOWN 주(청산 시점)까지다.
  기간 내 CROSS_DOWN이 없으면 "미청산(open)" 사이클로 표시하고, 마지막 완료 주봉을 임시 청산 시점으로 사용한다.
- 진입 지연 k주는 "CROSS_UP이 발생한 주로부터 k주 뒤에 진입한다"는 뜻이다. 즉 진입 인덱스 = cross_index + k.
- 만약 entry_index > exit_index (즉 CROSS_DOWN이 이미 발생한 뒤에야 지연 조건이 충족되는 경우)라면,
  실제 엔진 규칙(trend == ABOVE_LONG_AVERAGE 조건 포함)상 그 시점엔 이미 추세가 꺾여 있으므로 진입 자체가
  발생하지 않는다. 이 경우 수익률을 계산하지 않고 "진입 불가(사이클이 지연 조건 충족 전에 종료)"로 표시한다.
- 사이클 수익률 = exit_close / entry_close - 1 (청산 시점 종가 대비 진입 시점 종가).
- MDD(최대낙폭)는 두 가지로 계산한다.
  1) close 기준: entry_index부터 exit_index까지, 각 시점까지의 누적 최고 종가(peak, entry 종가부터 시작) 대비
     당일 종가의 낙폭 중 최솟값(가장 큰 손실율).
  2) intraweek 기준(더 보수적): peak는 종가 누적 최고치를 쓰되, 낙폭은 그 주의 저가(Low)로 계산한다.
     주간 캔들의 저가가 실제 주중 최저 노출을 더 잘 반영하기 때문이다.

데이터 소스 고지
----------------
이 스크립트는 CSV 파일 하나만 입력받으며 소스/interval/adjustment를 자동으로 알지 못한다.
호출부(리포트)에서 각 실행마다 --label 인자로 소스를 명시하고, 결과를 인용할 때 반드시
데이터 소스명·interval·adjustment 방식을 함께 표기해야 한다 (research/notes/data-source-audit-2026-08-12.md 참고).

사용 예
-------
python3 research/data/tools/entry_delay_cycle_backtest.py \
    --csv research/data/cache/soxl-weekly-twelvedata-2021-2026.csv \
    --delimiter ";" --close-col close --low-col low \
    --label "SOXL / Twelve Data / interval=1week / adjustment=splits" \
    --today 2026-08-12 --json-out /tmp/soxl_twelvedata_result.json
"""
import argparse
import csv
import json
from datetime import datetime, timedelta


def load_candles(path, delimiter, close_col, low_col, today, week_span_days=4, raw_close_col=None):
    """CSV를 읽어 완결된 주봉만 남긴다.

    완결 기준: 캔들 날짜(주 시작으로 가정) + week_span_days(기본 4, 금요일)가
    today보다 이후이면 아직 완결되지 않은 진행 중 주봉으로 보고 제외한다.
    이는 프로덕션의 "완료 주봉만 전략 판단에 사용" 규칙(docs/PROJECT_CONTEXT.md)과 동일한 취지다.

    raw_close_col: Yahoo류 CSV처럼 분할/배당 미조정 원가(close)와 조정종가(close_col, 예: adjclose)가
    별도 컬럼으로 있을 때, low 컬럼은 보통 미조정 원가 기준이다. 이 경우 low에
    (조정종가/원가) 비율을 곱해 조정 저가로 환산해야 intraweek MDD가 peak(조정종가 기준)와
    같은 스케일이 된다. None이면 low를 그대로 사용한다(원가와 조정가가 같은 소스, 예: Twelve Data).
    """
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=delimiter)
        for row in reader:
            d = datetime.strptime(row["datetime"].strip(), "%Y-%m-%d").date()
            close = float(row[close_col])
            low = float(row[low_col]) if low_col in row and row[low_col] not in (None, "") else close
            if raw_close_col:
                raw_close = float(row[raw_close_col])
                ratio = close / raw_close if raw_close != 0 else 1.0
                low = low * ratio
            rows.append({"date": d, "close": close, "low": low})

    rows.sort(key=lambda r: r["date"])

    complete = [r for r in rows if (r["date"] + timedelta(days=week_span_days)) <= today]
    dropped = [r for r in rows if r not in complete]
    return complete, dropped


def sma_series(closes, period):
    """closes[i-period+1..i]의 평균. i < period-1이면 None."""
    n = len(closes)
    out = [None] * n
    running_sum = 0.0
    for i in range(n):
        running_sum += closes[i]
        if i >= period:
            running_sum -= closes[i - period]
        if i >= period - 1:
            out[i] = running_sum / period
    return out


def compute_signals(candles, short_period, long_period):
    closes = [c["close"] for c in candles]
    sma_short = sma_series(closes, short_period)
    sma_long = sma_series(closes, long_period)

    n = len(candles)
    trend = [None] * n
    event = [None] * n

    for i in range(n):
        if sma_short[i] is not None and sma_long[i] is not None:
            trend[i] = "ABOVE" if sma_short[i] > sma_long[i] else "BELOW"

    for i in range(n):
        if i < long_period:
            continue
        if sma_short[i - 1] is None or sma_long[i - 1] is None:
            continue
        prev_short, prev_long = sma_short[i - 1], sma_long[i - 1]
        curr_short, curr_long = sma_short[i], sma_long[i]
        if prev_short <= prev_long and curr_short > curr_long:
            event[i] = "CROSS_UP"
        elif prev_short >= prev_long and curr_short < curr_long:
            event[i] = "CROSS_DOWN"
        else:
            event[i] = "NONE"

    return sma_short, sma_long, trend, event


def find_cycles(candles, event):
    """CROSS_UP 인덱스부터 다음 CROSS_DOWN 인덱스(없으면 마지막 인덱스, open=True)까지."""
    n = len(candles)
    cross_up_indices = [i for i in range(n) if event[i] == "CROSS_UP"]
    cycles = []
    for c in cross_up_indices:
        exit_index = None
        for j in range(c + 1, n):
            if event[j] == "CROSS_DOWN":
                exit_index = j
                break
        if exit_index is None:
            cycles.append({"cross_index": c, "exit_index": n - 1, "open": True})
        else:
            cycles.append({"cross_index": c, "exit_index": exit_index, "open": False})
    return cycles


def mdd_close_and_intraweek(candles, entry_index, exit_index):
    peak = candles[entry_index]["close"]
    mdd_close = 0.0
    mdd_intraweek = 0.0
    for j in range(entry_index, exit_index + 1):
        close_j = candles[j]["close"]
        low_j = candles[j]["low"]
        if close_j > peak:
            peak = close_j
        dd_close = (close_j - peak) / peak
        dd_intraweek = (low_j - peak) / peak
        if dd_close < mdd_close:
            mdd_close = dd_close
        if dd_intraweek < mdd_intraweek:
            mdd_intraweek = dd_intraweek
    return mdd_close, mdd_intraweek


def analyze(candles, short_period, long_period, delays, label):
    sma_short, sma_long, trend, event = compute_signals(candles, short_period, long_period)
    cycles = find_cycles(candles, event)

    result_cycles = []
    for cyc in cycles:
        c = cyc["cross_index"]
        exit_index = cyc["exit_index"]
        cross_date = candles[c]["date"].isoformat()
        exit_date = candles[exit_index]["date"].isoformat()
        entries = {}
        for k in delays:
            entry_index = c + k
            if entry_index > exit_index:
                entries[str(k)] = {
                    "enterable": False,
                    "reason": "cycle_ended_before_delay (CROSS_DOWN이 지연 조건 충족 전에 발생)"
                }
                continue
            if entry_index >= len(candles):
                entries[str(k)] = {
                    "enterable": False,
                    "reason": "insufficient_data (진입 시점 데이터 없음)"
                }
                continue
            entry_price = candles[entry_index]["close"]
            exit_price = candles[exit_index]["close"]
            return_pct = (exit_price / entry_price - 1.0) * 100.0
            mdd_close, mdd_intraweek = mdd_close_and_intraweek(candles, entry_index, exit_index)
            entries[str(k)] = {
                "enterable": True,
                "entry_date": candles[entry_index]["date"].isoformat(),
                "entry_price": round(entry_price, 4),
                "exit_price": round(exit_price, 4),
                "return_pct": round(return_pct, 1),
                "mdd_close_pct": round(mdd_close * 100.0, 1),
                "mdd_intraweek_pct": round(mdd_intraweek * 100.0, 1),
            }
        result_cycles.append({
            "cross_date": cross_date,
            "exit_date": exit_date,
            "open": cyc["open"],
            "entries": entries,
        })

    return {
        "label": label,
        "short_period": short_period,
        "long_period": long_period,
        "candle_count": len(candles),
        "date_range": [candles[0]["date"].isoformat(), candles[-1]["date"].isoformat()],
        "cycles": result_cycles,
    }


def print_report(result):
    print(f"=== {result['label']} ===")
    print(f"캔들 수: {result['candle_count']}, 기간: {result['date_range'][0]} ~ {result['date_range'][1]}")
    print(f"SMA 기간: {result['short_period']}주 / {result['long_period']}주\n")
    for cyc in result["cycles"]:
        status = "미청산(open)" if cyc["open"] else "청산"
        print(f"--- 사이클: {cyc['cross_date']} -> {cyc['exit_date']} ({status}) ---")
        header = f"{'지연':>6} | {'진입일':>10} | {'수익률':>8} | {'MDD(종가)':>10} | {'MDD(주중저가)':>12}"
        print(header)
        for k, e in cyc["entries"].items():
            if not e["enterable"]:
                print(f"{k:>6} | {'-':>10} | {'N/A':>8} | {'-':>10} | {'-':>12}  ({e['reason']})")
            else:
                print(f"{k:>6} | {e['entry_date']:>10} | {e['return_pct']:>7.1f}% | "
                      f"{e['mdd_close_pct']:>9.1f}% | {e['mdd_intraweek_pct']:>11.1f}%")
        print()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--csv", required=True)
    parser.add_argument("--delimiter", default=";")
    parser.add_argument("--close-col", default="close")
    parser.add_argument("--low-col", default="low")
    parser.add_argument("--raw-close-col", default=None,
                         help="원가(미조정) 종가 컬럼명. 지정하면 low에 (close-col/raw-close-col) 비율을 곱해 조정 저가로 환산")
    parser.add_argument("--label", required=True, help="데이터 소스/interval/adjustment를 반드시 포함해 표기")
    parser.add_argument("--today", required=True, help="YYYY-MM-DD, 완결 주봉 판정 기준일")
    parser.add_argument("--short", type=int, default=10)
    parser.add_argument("--long", type=int, default=40)
    parser.add_argument("--delays", default="0,1,2,4,8,12")
    parser.add_argument("--json-out", default=None)
    args = parser.parse_args()

    today = datetime.strptime(args.today, "%Y-%m-%d").date()
    delays = [int(x) for x in args.delays.split(",")]

    candles, dropped = load_candles(
        args.csv, args.delimiter, args.close_col, args.low_col, today,
        raw_close_col=args.raw_close_col
    )
    if dropped:
        print(f"[알림] 완결되지 않은 주봉으로 판단해 제외한 행: {[r['date'].isoformat() for r in dropped]}")

    result = analyze(candles, args.short, args.long, delays, args.label)
    print_report(result)

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"[JSON 저장] {args.json_out}")


if __name__ == "__main__":
    main()

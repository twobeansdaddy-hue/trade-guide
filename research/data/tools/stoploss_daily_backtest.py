#!/usr/bin/env python3
"""
stoploss_daily_backtest.py

목적
----
1) track-a-entry-delay-cutoff-review.md의 기준선(교차 주 "종가"를 진입가로 사용)을
   새 체결 가정(주봉 신호 확정 후 "다음 거래일 시가" 체결, 일봉 기준 MDD)으로 다시 계산한다.
2) 그 위에 손절 레이어 후보(고정비율 / ATR기반+상한cap)를 얹어 MDD·수익률 교환비율을 측정한다.
3) 재진입 금지 / weeksSinceCross<=4 재성립 시 재진입 허용, 두 경우를 모두 시뮬레이션한다.

이 스크립트는 entry_delay_cycle_backtest.py(같은 디렉터리)의 주봉 신호 재구현
(compute_signals/find_cycles/load_candles)을 그대로 재사용해 교차 시점을 찾고,
그 위에 일봉 데이터를 사용한 체결·손절 로직을 새로 얹는다.

체결 가정 (research/CLAUDE.md 세션 지시와 동일, 반드시 그대로 적용)
----------------------------------------------------------------
- 주봉 신호(CROSS_UP/CROSS_DOWN)는 해당 주 금요일 종가(= 주봉 시작일 + 4일) 확정 뒤에만 안다.
- 진입/추세청산 체결가 = 신호가 확정된 금요일 다음 첫 거래일(일봉)의 시가.
- 손절은 진입 이후 매 거래일 저가로 확인한다. 저가 <= 손절가이면 발동.
  단 그 날 시가가 이미 손절가보다 낮으면(갭 하락) 손절가가 아니라 그 날 시가로 체결한다(낙관 금지).
- 추세 청산(주봉 하향 교차) 체결일과 손절 발동일이 같은 날이면, 두 체결가 중 더 낮은(불리한) 쪽을
  최종 체결가로 쓴다(보수적 기본값).
- 트렌드 청산은 주봉 종가에만 확인한다(주 1회). 손절은 일봉마다 확인한다(매일). 이 비대칭적 확인
  주기는 실거래에서도 동일하게 존재한다 — 사람이 장중 손절가를 매일 확인하고, 추세 판단은 주말에만
  한다고 가정.
- 분봉 데이터가 없으므로 하루 안에서 저가가 먼저 찍혔는지 시가가 먼저인지는 알 수 없다. 위 "더 낮은
  쪽 채택" 규칙으로 그 불확실성을 보수적으로 처리한다.

재진입 규칙 (재진입 허용 변형)
------------------------------
StrategyDecisionMaker.decideForCandidate의 실제 조건은
`trend == ABOVE_LONG_AVERAGE && weeksSinceCross != null && weeksSinceCross <= 4`이다.
이 조건은 매주(금요일 확정 후) 재평가된다 — 즉 엔진은 손절로 포지션이 사라진 사실을 모른 채,
매주 이 조건이 성립하면 그대로 BUY를 다시 낸다. 재진입 허용 변형은 이를 그대로 재현한다:
cross_index(c)부터 c+4주까지 5개의 주간 체크포인트에서, 그 시점에 포지션이 없고
(trend == ABOVE && weeksSinceCross(=체크포인트 오프셋) <= 4 && 체크포인트 <= exit_index)이면
그 주 신호 확정 다음 거래일 시가로 재진입한다. weeksSinceCross > 4가 되면(즉 c+5주 이후) 이
사이클에서는 더 이상 신규 진입 신호가 나오지 않는다(엔진 규칙상 WATCH로 고정) — 진짜 새 CROSS_UP이
나기 전까지는 재진입 불가.

ATR
---
일봉 ATR(14)을 사용한다(단순이동평균 방식 — 이 프로젝트의 SimpleMovingAverageCalculator와 동일하게
지수평활이 아닌 단순평균을 사용해 일관성을 유지). 손절은 일봉마다 확인하므로, 그 확인 주기와
같은 시간축의 변동성 척도를 쓰는 것이 타당하다. 이전 리포트(soxl-position-sizing-stoploss.md)가
주간 ATR × 2배를 써서 SOXL 손절폭이 -88%로 나온 문제가 있었으므로, 여기서는 손절을 매일 확인한다는
전제에 맞춰 일봉 ATR로 다시 계산하고, 그 실측 분포를 근거로 배수와 상한(cap)을 정한다(스크립트
실행 결과의 분포 통계를 리포트에 인용).
"""
import argparse
import csv
import json
import sys
import os
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import entry_delay_cycle_backtest as edcb  # noqa: E402


# ---------------------------------------------------------------------------
# 일봉 로딩 + ATR
# ---------------------------------------------------------------------------

def load_daily_candles(path, delimiter=";", close_col="close", open_col="open",
                        high_col="high", low_col="low"):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=delimiter)
        for row in reader:
            d = datetime.strptime(row["datetime"].strip(), "%Y-%m-%d").date()
            rows.append({
                "date": d,
                "open": float(row[open_col]),
                "high": float(row[high_col]),
                "low": float(row[low_col]),
                "close": float(row[close_col]),
            })
    rows.sort(key=lambda r: r["date"])
    return rows


def compute_daily_atr(daily_rows, period=14):
    """단순평균 True Range. atr[i]는 daily_rows[i-period+1..i]의 TR 평균 (i>=period-1일 때만)."""
    n = len(daily_rows)
    tr = [None] * n
    for i in range(n):
        h, l = daily_rows[i]["high"], daily_rows[i]["low"]
        if i == 0:
            tr[i] = h - l
        else:
            prev_close = daily_rows[i - 1]["close"]
            tr[i] = max(h - l, abs(h - prev_close), abs(l - prev_close))
    atr = [None] * n
    running = 0.0
    for i in range(n):
        running += tr[i]
        if i >= period:
            running -= tr[i - period]
        if i >= period - 1:
            atr[i] = running / period
    return atr


def index_by_date(daily_rows):
    return {r["date"]: i for i, r in enumerate(daily_rows)}


def next_trading_day_on_or_after(daily_rows, target_date):
    """target_date보다 크거나 같은 첫 거래일의 인덱스. 없으면 None."""
    # daily_rows는 날짜순 정렬되어 있다고 가정 -> 이분 탐색 가능하지만 개수가 많지 않으므로 선형 탐색.
    for i, r in enumerate(daily_rows):
        if r["date"] >= target_date:
            return i
    return None


def friday_of_week(week_start_date):
    return week_start_date + timedelta(days=4)


def execution_index_after_friday(daily_rows, week_start_date):
    """주봉 신호가 확정되는 금요일(week_start+4일) 다음 첫 거래일 인덱스."""
    friday = friday_of_week(week_start_date)
    day_after_friday = friday + timedelta(days=1)
    return next_trading_day_on_or_after(daily_rows, day_after_friday)


# ---------------------------------------------------------------------------
# 사이클 로딩 (주봉, entry_delay_cycle_backtest.py 재사용)
# ---------------------------------------------------------------------------

def load_weekly_cycles(weekly_csv, delimiter, close_col, low_col, today,
                        raw_close_col=None, short=10, long=40):
    candles, dropped = edcb.load_candles(
        weekly_csv, delimiter, close_col, low_col, today, raw_close_col=raw_close_col
    )
    sma_short, sma_long, trend, event = edcb.compute_signals(candles, short, long)
    cycles = edcb.find_cycles(candles, event)
    return candles, cycles, dropped


# ---------------------------------------------------------------------------
# 일봉 기준 체결 시뮬레이션
# ---------------------------------------------------------------------------

def mdd_over_window(daily_rows, entry_idx, exit_idx, entry_price):
    """entry_idx~exit_idx(포함) 구간 일봉 종가/저가 기준 MDD. peak은 entry_price에서 시작."""
    peak = entry_price
    mdd_close = 0.0
    mdd_low = 0.0
    for j in range(entry_idx, exit_idx + 1):
        c = daily_rows[j]["close"]
        lo = daily_rows[j]["low"]
        if c > peak:
            peak = c
        dd_close = (c - peak) / peak
        dd_low = (lo - peak) / peak
        if dd_close < mdd_close:
            mdd_close = dd_close
        if dd_low < mdd_low:
            mdd_low = dd_low
    return mdd_close, mdd_low


def simulate_stop_leg(daily_rows, atr, entry_idx, entry_price, hard_exit_idx,
                       hard_exit_is_trend, stop_price, slippage_pct=0.0):
    """entry_idx(그 날 시가로 이미 체결됨)부터 hard_exit_idx까지 매일 저가로 손절을 확인한다.

    hard_exit_idx: 자연 청산(추세 하향교차 체결일 또는 미청산이면 데이터 마지막 날) 인덱스.
    hard_exit_is_trend: hard_exit_idx가 추세 청산 체결(시가 체결)인지 여부. False면 미청산 마크투마켓(종가).
    slippage_pct: 편도 슬리피지(0.0~1.0). 매도성 체결가(추세청산, 손절)에만 불리한 방향으로 적용한다.
        기본값 0.0이면 기존 호출부(run_stoploss_report.py)와 완전히 동일한 결과를 낸다.
        gap_excess_loss_pct는 갭하락 자체의 크기(슬리피지 적용 전 원가 기준)를 그대로 보여주기 위해
        슬리피지 적용 전 가격으로 계산하고, 슬리피지는 그 이후 별도로 최종 체결가에 얹는다.

    반환: dict(exit_idx, exit_price, exit_reason, gap_excess_loss, mdd_close, mdd_low)
    exit_reason: "STOP" | "TREND" | "OPEN_MARK"
    gap_excess_loss: 갭하락으로 손절가보다 더 불리하게 체결된 경우 (stop_price - fill_price)/entry_price*100 (양수 = 초과손실 %p)
    """
    for j in range(entry_idx, hard_exit_idx + 1):
        low_j = daily_rows[j]["low"]
        open_j = daily_rows[j]["open"]
        stop_hit = low_j <= stop_price

        is_hard_exit_day = (j == hard_exit_idx)

        if stop_hit:
            if open_j < stop_price:
                stop_fill_raw = open_j
                gap = True
            else:
                stop_fill_raw = stop_price
                gap = False
            stop_fill = apply_sell_slippage(stop_fill_raw, slippage_pct)

            if is_hard_exit_day and hard_exit_is_trend:
                trend_fill_raw = open_j
                trend_fill = apply_sell_slippage(trend_fill_raw, slippage_pct)
                # 같은 날 두 체결이 모두 가능하면 더 낮은(불리한) 쪽 채택 (보수적 기본값).
                # 비교는 슬리피지 적용 전 원가 기준(체결 우선순위는 시장가 문제이지 슬리피지 문제가 아님).
                if trend_fill_raw <= stop_fill_raw:
                    mdd_close, mdd_low = mdd_over_window(daily_rows, entry_idx, j, entry_price)
                    return {
                        "exit_idx": j, "exit_price": trend_fill, "exit_reason": "TREND",
                        "gap_excess_loss_pct": 0.0,
                        "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                        "tie_break_note": "같은 날 추세청산과 손절이 겹쳐 더 낮은 가격(추세청산 시가) 채택",
                    }
            mdd_close, mdd_low = mdd_over_window(daily_rows, entry_idx, j, entry_price)
            gap_excess = ((stop_price - stop_fill_raw) / entry_price * 100.0) if gap else 0.0
            return {
                "exit_idx": j, "exit_price": stop_fill, "exit_reason": "STOP",
                "gap_excess_loss_pct": gap_excess,
                "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                "tie_break_note": None,
            }

        if is_hard_exit_day:
            if hard_exit_is_trend:
                exit_price = apply_sell_slippage(open_j, slippage_pct)
                reason = "TREND"
            else:
                # 미청산(open) 사이클의 마크투마켓 평가는 실제 매도 체결이 아니므로 슬리피지를 적용하지 않는다.
                exit_price = daily_rows[j]["close"]
                reason = "OPEN_MARK"
            mdd_close, mdd_low = mdd_over_window(daily_rows, entry_idx, j, entry_price)
            return {
                "exit_idx": j, "exit_price": exit_price, "exit_reason": reason,
                "gap_excess_loss_pct": 0.0,
                "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                "tie_break_note": None,
            }
    raise RuntimeError("도달 불가 상태 (entry_idx > hard_exit_idx?)")


def build_cycle_daily_context(weekly_candles, cycle, daily_rows):
    """사이클의 cross/exit 주봉 인덱스를 일봉 실행 인덱스로 변환."""
    c = cycle["cross_index"]
    e = cycle["exit_index"]
    is_open = cycle["open"]

    cross_week_start = weekly_candles[c]["date"]
    entry0_idx = execution_index_after_friday(daily_rows, cross_week_start)

    if is_open:
        hard_exit_idx = len(daily_rows) - 1
        hard_exit_is_trend = False
    else:
        exit_week_start = weekly_candles[e]["date"]
        hard_exit_idx = execution_index_after_friday(daily_rows, exit_week_start)
        hard_exit_is_trend = True

    return {
        "cross_week_start": cross_week_start.isoformat(),
        "exit_week_start": (weekly_candles[e]["date"].isoformat() if not is_open else None),
        "entry0_idx": entry0_idx,
        "hard_exit_idx": hard_exit_idx,
        "hard_exit_is_trend": hard_exit_is_trend,
        "is_open": is_open,
    }


def stop_price_fixed_pct(entry_price, pct):
    return entry_price * (1.0 - pct)


def stop_price_atr_cap(entry_price, atr_value, multiplier, cap_pct):
    if atr_value is None:
        return None
    distance = min(atr_value * multiplier, entry_price * cap_pct)
    return entry_price - distance


# ---------------------------------------------------------------------------
# 수수료/슬리피지 (신규 — track-a-stoploss-revalidation 리포트용)
# ---------------------------------------------------------------------------
# 기본값 0.0으로 두어, 이 파라미터를 넘기지 않는 기존 호출부(run_stoploss_report.py)의
# 결과가 바이트 단위로 그대로 재현되도록 보존한다. 슬리피지는 "체결가"에만 적용하고
# 손절 트리거 판정(저가<=손절가)이나 MDD 계산에는 적용하지 않는다 — 실제로 손절이
# 발동했는지 여부와 낙폭 측정은 시장가 자체의 문제이고, 슬리피지는 그 체결을 실제로
# 얼마에 받았는지에 대한 별도 비용이기 때문이다. 손절가(stop_price) 자체는 실제 체결된
# (=슬리피지가 반영된) 진입가를 기준으로 계산한다 — 실거래에서 손절 지정가는 본인의
# 실제 매수 단가를 기준으로 걸기 때문이다.
def apply_buy_slippage(price, slippage_pct):
    return price * (1.0 + slippage_pct)


def apply_sell_slippage(price, slippage_pct):
    return price * (1.0 - slippage_pct)


def run_baseline_delay_grid(weekly_candles, cycles, daily_rows, delays, label, slippage_pct=0.0):
    """새 체결 가정(다음 거래일 시가)으로 지연 그리드(0..N주) 기준선을 계산한다.
    (entry_delay_cycle_backtest.py의 종가체결 기준선과 비교하기 위한 절).
    slippage_pct 기본값 0.0 -> 기존 호출부와 동일한 결과."""
    out_cycles = []
    for cyc in cycles:
        c = cyc["cross_index"]
        e = cyc["exit_index"]
        is_open = cyc["open"]
        ctx = build_cycle_daily_context(weekly_candles, cyc, daily_rows)
        hard_exit_idx = ctx["hard_exit_idx"]
        hard_exit_is_trend = ctx["hard_exit_is_trend"]

        entries = {}
        for k in delays:
            wk_idx = c + k
            if wk_idx > e or wk_idx >= len(weekly_candles):
                entries[str(k)] = {"enterable": False, "reason": "cycle_ended_before_delay_or_no_data"}
                continue
            week_start = weekly_candles[wk_idx]["date"]
            entry_idx = execution_index_after_friday(daily_rows, week_start)
            if entry_idx is None or entry_idx > hard_exit_idx:
                entries[str(k)] = {"enterable": False, "reason": "no_daily_execution_data"}
                continue
            entry_price = apply_buy_slippage(daily_rows[entry_idx]["open"], slippage_pct)
            if hard_exit_is_trend:
                exit_price = apply_sell_slippage(daily_rows[hard_exit_idx]["open"], slippage_pct)
            else:
                exit_price = daily_rows[hard_exit_idx]["close"]
            ret = (exit_price / entry_price - 1.0) * 100.0
            mdd_close, mdd_low = mdd_over_window(daily_rows, entry_idx, hard_exit_idx, entry_price)
            entries[str(k)] = {
                "enterable": True,
                "entry_date": daily_rows[entry_idx]["date"].isoformat(),
                "entry_price": round(entry_price, 4),
                "exit_date": daily_rows[hard_exit_idx]["date"].isoformat(),
                "exit_price": round(exit_price, 4),
                "return_pct": round(ret, 1),
                "mdd_close_pct": round(mdd_close * 100.0, 1),
                "mdd_low_pct": round(mdd_low * 100.0, 1),
            }
        out_cycles.append({
            "cross_date": weekly_candles[c]["date"].isoformat(),
            "exit_date": (weekly_candles[e]["date"].isoformat() if not is_open else None),
            "open": is_open,
            "entries": entries,
        })
    return {"label": label, "cycles": out_cycles}


def run_stoploss_candidates(weekly_candles, cycles, daily_rows, atr, label, candidate_configs,
                             slippage_pct=0.0):
    """delay=0(=weeksSinceCross==0, 실제 엔진의 최초 진입 시점) 기준으로 손절 후보를 시뮬레이션한다.
    각 candidate_config: {"name":..., "kind":"fixed"/"atr_cap", "pct":..., "multiplier":..., "cap_pct":...}
    각 후보에 대해 no_reentry / reentry 두 변형을 모두 계산한다.
    slippage_pct 기본값 0.0 -> 기존 호출부(run_stoploss_report.py)와 동일한 결과.
    손절가(stop_price)는 슬리피지가 반영된 실제 체결 진입가(entry0_price) 기준으로 계산한다."""
    results = {cfg["name"]: {"no_reentry": [], "reentry": []} for cfg in candidate_configs}
    baseline_trades = []

    for cyc in cycles:
        c = cyc["cross_index"]
        e = cyc["exit_index"]
        ctx = build_cycle_daily_context(weekly_candles, cyc, daily_rows)
        entry0_idx = ctx["entry0_idx"]
        hard_exit_idx = ctx["hard_exit_idx"]
        hard_exit_is_trend = ctx["hard_exit_is_trend"]

        if entry0_idx is None or entry0_idx > hard_exit_idx:
            continue

        entry0_price = apply_buy_slippage(daily_rows[entry0_idx]["open"], slippage_pct)
        entry0_atr = atr[entry0_idx]

        if hard_exit_is_trend:
            baseline_exit_price = apply_sell_slippage(daily_rows[hard_exit_idx]["open"], slippage_pct)
        else:
            baseline_exit_price = daily_rows[hard_exit_idx]["close"]
        baseline_ret = (baseline_exit_price / entry0_price - 1.0) * 100.0
        mdd_c, mdd_l = mdd_over_window(daily_rows, entry0_idx, hard_exit_idx, entry0_price)
        baseline_trades.append({
            "cross_date": weekly_candles[c]["date"].isoformat(),
            "entry_date": daily_rows[entry0_idx]["date"].isoformat(),
            "entry_price": round(entry0_price, 4),
            "exit_date": daily_rows[hard_exit_idx]["date"].isoformat(),
            "exit_price": round(baseline_exit_price, 4),
            "return_pct": round(baseline_ret, 1),
            "mdd_close_pct": round(mdd_c * 100.0, 1),
            "mdd_low_pct": round(mdd_l * 100.0, 1),
            "open": cyc["open"],
        })

        for cfg in candidate_configs:
            if cfg["kind"] == "fixed":
                sp0 = stop_price_fixed_pct(entry0_price, cfg["pct"])
            elif cfg["kind"] == "atr_cap":
                sp0 = stop_price_atr_cap(entry0_price, entry0_atr, cfg["multiplier"], cfg["cap_pct"])
                if sp0 is None:
                    continue
            else:
                raise ValueError(cfg["kind"])

            # ---- no_reentry ----
            leg = simulate_stop_leg(daily_rows, atr, entry0_idx, entry0_price,
                                     hard_exit_idx, hard_exit_is_trend, sp0,
                                     slippage_pct=slippage_pct)
            ret = (leg["exit_price"] / entry0_price - 1.0) * 100.0
            whipsaw = False
            if leg["exit_reason"] == "STOP":
                for j in range(leg["exit_idx"] + 1, hard_exit_idx + 1):
                    if daily_rows[j]["close"] >= sp0:
                        whipsaw = True
                        break
            results[cfg["name"]]["no_reentry"].append({
                "cross_date": weekly_candles[c]["date"].isoformat(),
                "entry_date": daily_rows[entry0_idx]["date"].isoformat(),
                "entry_price": round(entry0_price, 4),
                "stop_price": round(sp0, 4),
                "exit_date": daily_rows[leg["exit_idx"]]["date"].isoformat(),
                "exit_price": round(leg["exit_price"], 4),
                "exit_reason": leg["exit_reason"],
                "return_pct": round(ret, 1),
                "mdd_close_pct": round(leg["mdd_close_pct"], 1),
                "mdd_low_pct": round(leg["mdd_low_pct"], 1),
                "gap_excess_loss_pct": round(leg["gap_excess_loss_pct"], 2),
                "whipsaw_recovered": whipsaw,
                "atr_at_entry": round(entry0_atr, 4) if entry0_atr else None,
            })

            # ---- reentry (weeksSinceCross<=4 재성립 시 재진입) ----
            # 체크포인트 목록: k=0..4 중 실제로 그 주에 BUY 조건(trend==ABOVE && weeksSinceCross<=4)이
            # 성립할 수 있는(=사이클이 아직 안 끝난) 주간 실행 시점만 모은다.
            checkpoints = []
            for k in range(0, 5):
                wk_idx = c + k
                if wk_idx > e or wk_idx >= len(weekly_candles):
                    break
                week_start = weekly_candles[wk_idx]["date"]
                chk_idx = execution_index_after_friday(daily_rows, week_start)
                if chk_idx is None or chk_idx > hard_exit_idx:
                    break
                checkpoints.append((k, chk_idx))

            trades = []
            stop_count = 0
            whipsaw_count = 0
            ptr = 0
            while ptr < len(checkpoints):
                k, chk_idx = checkpoints[ptr]
                pos_entry_idx = chk_idx
                pos_entry_price = apply_buy_slippage(daily_rows[chk_idx]["open"], slippage_pct)
                pos_atr = atr[chk_idx]
                if cfg["kind"] == "fixed":
                    pos_stop_price = stop_price_fixed_pct(pos_entry_price, cfg["pct"])
                else:
                    pos_stop_price = stop_price_atr_cap(pos_entry_price, pos_atr, cfg["multiplier"], cfg["cap_pct"])
                    if pos_stop_price is None:
                        break

                # 포지션이 없을 때 진입 -> 자연 청산(hard_exit_idx)까지 손절 여부를 그대로 시뮬레이션.
                # 도중에 스탑이 나면 그 시점 이후의 첫 체크포인트(k'>k, chk_idx'>stop_exit_idx)에서 재진입.
                leg = simulate_stop_leg(daily_rows, atr, pos_entry_idx, pos_entry_price,
                                         hard_exit_idx, hard_exit_is_trend, pos_stop_price,
                                         slippage_pct=slippage_pct)
                trade_ret = (leg["exit_price"] / pos_entry_price - 1.0) * 100.0
                trades.append({
                    "checkpoint_week_offset": k,
                    "entry_date": daily_rows[pos_entry_idx]["date"].isoformat(),
                    "entry_price": round(pos_entry_price, 4),
                    "stop_price": round(pos_stop_price, 4),
                    "exit_date": daily_rows[leg["exit_idx"]]["date"].isoformat(),
                    "exit_price": round(leg["exit_price"], 4),
                    "exit_reason": leg["exit_reason"],
                    "return_pct": round(trade_ret, 1),
                    "gap_excess_loss_pct": round(leg["gap_excess_loss_pct"], 2),
                })

                if leg["exit_reason"] != "STOP":
                    # TREND 또는 OPEN_MARK 도달 -> 사이클 자연 종료, 더 이상 재진입 없음
                    break

                stop_count += 1
                recovered = False
                for j in range(leg["exit_idx"] + 1, hard_exit_idx + 1):
                    if daily_rows[j]["close"] >= pos_stop_price:
                        recovered = True
                        break
                if recovered:
                    whipsaw_count += 1

                # 다음 재진입 가능 체크포인트: 손절 발생일 이후(chronologically) 첫 체크포인트
                next_ptr = None
                for i2 in range(ptr + 1, len(checkpoints)):
                    if checkpoints[i2][1] > leg["exit_idx"]:
                        next_ptr = i2
                        break
                if next_ptr is None:
                    break
                ptr = next_ptr

            if trades:
                compounded = 1.0
                for t in trades:
                    compounded *= (1.0 + t["return_pct"] / 100.0)
                cycle_return_pct = (compounded - 1.0) * 100.0
            else:
                cycle_return_pct = None

            results[cfg["name"]]["reentry"].append({
                "cross_date": weekly_candles[c]["date"].isoformat(),
                "trades": trades,
                "num_stops": stop_count,
                "num_whipsaw_recoveries": whipsaw_count,
                "cycle_compounded_return_pct": round(cycle_return_pct, 1) if cycle_return_pct is not None else None,
            })

    return baseline_trades, results


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def summarize_atr_pct(daily_rows, atr, sample_indices, label):
    vals = []
    for i in sample_indices:
        if atr[i] is not None:
            vals.append(atr[i] / daily_rows[i]["close"] * 100.0)
    vals.sort()
    n = len(vals)
    if n == 0:
        return None
    median = vals[n // 2] if n % 2 == 1 else (vals[n // 2 - 1] + vals[n // 2]) / 2
    return {
        "label": label, "n": n, "min": round(min(vals), 1), "max": round(max(vals), 1),
        "median": round(median, 1), "mean": round(sum(vals) / n, 1),
    }


if __name__ == "__main__":
    print("이 파일은 라이브러리로 사용하세요: 실행 스크립트는 run_stoploss_report.py 참고", file=sys.stderr)

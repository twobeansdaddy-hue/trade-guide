#!/usr/bin/env python3
"""
trailing_stop_backtest.py

track-a-trailing-stop-review.md 리포트용 추적 손절(trailing stop) 시뮬레이션 라이브러리.

목적
----
stoploss_daily_backtest.py(고정비율/ATR 손절)와 entry_delay_cycle_backtest.py(주봉 신호/사이클)를
그대로 재사용하되, 손절선이 진입 후 고정되지 않고 "진입 이후 일봉 종가의 최고값(high-water mark)"을
따라 매일 갱신되는 추적 손절 로직을 새로 추가한다. 기존 두 파일은 이 작업을 위해 수정하지 않았다
(하위호환 유지 확인은 run_trailing_stop_report.py의 회귀 확인 절 참고).

추적 기준 — 절대 규칙 (research/CLAUDE.md 세션 지시)
--------------------------------------------------
추적 기준은 진입 이후 "일봉 종가의 최고값"으로만 계산한다. 장중 고가를 쓰지 않는다.

일별 처리 순서 (선행 정보 누출 방지)
------------------------------------
day j (entry_idx부터 hard_exit_idx까지)를 맞이할 때:
  1. 그 날 아침 시점에 이미 알려진 high-water mark(peak, entry_idx..j-1일 종가 중 최고값, 최초값은
     체결된 진입가)로 그 날의 손절선(stop_price_j = peak * (1 - trail_pct))을 정한다.
  2. 그 날 저가가 stop_price_j 이하이면 손절 발동(갭 하락 시 시가로 체결, 손절가보다 유리하게 체결하지
     않음 — stoploss_daily_backtest.py의 simulate_stop_leg와 동일한 낙관 금지 원칙).
  3. 손절이 발동하지 않고 그 날이 자연청산일(hard_exit)이 아니면, 그 날 종가로 peak을 갱신한다
     (close_j > peak일 때만 peak = close_j).
이 순서는 "그 날의 손절선은 전날까지의 확정 종가만으로 정해진다"는 원칙을 지켜, 당일 종가를 그 날의
손절선 계산에 미리 사용하는 선행 정보 누출을 막는다. 진입일(day 0)의 손절선은 peak=entry_price로
시작하므로 entryPrice*(1-trail_pct)와 같다 — 고정비율 손절의 진입일과 동일한 출발점이다.

같은 날 추세청산·손절 충돌, 갭 초과손실, 슬리피지 처리는 stoploss_daily_backtest.simulate_stop_leg와
동일한 보수적 원칙(더 낮은/불리한 가격 채택, 매수성 체결은 +slippage, 매도성 체결은 -slippage,
미청산 마크투마켓은 슬리피지 미적용)을 그대로 따른다.
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402


def simulate_trailing_stop_leg(daily_rows, entry_idx, entry_price, hard_exit_idx,
                                hard_exit_is_trend, trail_pct, slippage_pct=0.0):
    """진입 이후 종가 최고값(high-water mark) 기준 추적 손절 시뮬레이션.

    반환: dict(exit_idx, exit_price, exit_reason, gap_excess_loss_pct, mdd_close_pct, mdd_low_pct,
               tie_break_note, final_stop_price, peak_at_exit)
    exit_reason: "STOP" | "TREND" | "OPEN_MARK"
    """
    peak = entry_price
    for j in range(entry_idx, hard_exit_idx + 1):
        stop_price_j = peak * (1.0 - trail_pct)
        low_j = daily_rows[j]["low"]
        open_j = daily_rows[j]["open"]
        stop_hit = low_j <= stop_price_j
        is_hard_exit_day = (j == hard_exit_idx)

        if stop_hit:
            if open_j < stop_price_j:
                stop_fill_raw = open_j
                gap = True
            else:
                stop_fill_raw = stop_price_j
                gap = False
            stop_fill = sdb.apply_sell_slippage(stop_fill_raw, slippage_pct)

            if is_hard_exit_day and hard_exit_is_trend:
                trend_fill_raw = open_j
                trend_fill = sdb.apply_sell_slippage(trend_fill_raw, slippage_pct)
                if trend_fill_raw <= stop_fill_raw:
                    mdd_close, mdd_low = sdb.mdd_over_window(daily_rows, entry_idx, j, entry_price)
                    return {
                        "exit_idx": j, "exit_price": trend_fill, "exit_reason": "TREND",
                        "gap_excess_loss_pct": 0.0,
                        "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                        "tie_break_note": "같은 날 추세청산과 손절이 겹쳐 더 낮은 가격(추세청산 시가) 채택",
                        "final_stop_price": stop_price_j, "peak_at_exit": peak,
                    }

            mdd_close, mdd_low = sdb.mdd_over_window(daily_rows, entry_idx, j, entry_price)
            gap_excess = ((stop_price_j - stop_fill_raw) / entry_price * 100.0) if gap else 0.0
            return {
                "exit_idx": j, "exit_price": stop_fill, "exit_reason": "STOP",
                "gap_excess_loss_pct": gap_excess,
                "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                "tie_break_note": None,
                "final_stop_price": stop_price_j, "peak_at_exit": peak,
            }

        if is_hard_exit_day:
            if hard_exit_is_trend:
                exit_price = sdb.apply_sell_slippage(open_j, slippage_pct)
                reason = "TREND"
            else:
                exit_price = daily_rows[j]["close"]
                reason = "OPEN_MARK"
            mdd_close, mdd_low = sdb.mdd_over_window(daily_rows, entry_idx, j, entry_price)
            return {
                "exit_idx": j, "exit_price": exit_price, "exit_reason": reason,
                "gap_excess_loss_pct": 0.0,
                "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                "tie_break_note": None,
                "final_stop_price": stop_price_j, "peak_at_exit": peak,
            }

        close_j = daily_rows[j]["close"]
        if close_j > peak:
            peak = close_j

    raise RuntimeError("도달 불가 상태 (entry_idx > hard_exit_idx?)")


def run_trailing_stop_candidates(weekly_candles, cycles, daily_rows, label, trail_pcts,
                                  slippage_pct=0.0, dataset_key=None, ticker=None):
    """delay=0(엔진의 실제 최초 진입 시점) 기준으로 추적 손절 후보들을 재진입 금지로 시뮬레이션한다.

    trail_pcts: {"trailing_20pct": 0.20, "trailing_25pct": 0.25, "trailing_30pct": 0.30} 형태.
    반환: {name: [trade, ...]} — no_reentry만 계산(사전 고정 규칙: 재진입은 기본 미허용).
    """
    results = {name: [] for name in trail_pcts}

    for cyc in cycles:
        ctx = sdb.build_cycle_daily_context(weekly_candles, cyc, daily_rows)
        entry0_idx = ctx["entry0_idx"]
        hard_exit_idx = ctx["hard_exit_idx"]
        hard_exit_is_trend = ctx["hard_exit_is_trend"]

        if entry0_idx is None or entry0_idx > hard_exit_idx:
            continue

        entry0_price = sdb.apply_buy_slippage(daily_rows[entry0_idx]["open"], slippage_pct)

        for name, trail_pct in trail_pcts.items():
            leg = simulate_trailing_stop_leg(
                daily_rows, entry0_idx, entry0_price, hard_exit_idx, hard_exit_is_trend,
                trail_pct, slippage_pct=slippage_pct,
            )
            ret = (leg["exit_price"] / entry0_price - 1.0) * 100.0
            whipsaw = False
            if leg["exit_reason"] == "STOP":
                ref_level = leg["final_stop_price"]
                for j in range(leg["exit_idx"] + 1, hard_exit_idx + 1):
                    if daily_rows[j]["close"] >= ref_level:
                        whipsaw = True
                        break
            results[name].append({
                "dataset": dataset_key,
                "ticker": ticker,
                "cross_date": weekly_candles[cyc["cross_index"]]["date"].isoformat(),
                "entry_date": daily_rows[entry0_idx]["date"].isoformat(),
                "entry_price": round(entry0_price, 4),
                "exit_date": daily_rows[leg["exit_idx"]]["date"].isoformat(),
                "exit_price": round(leg["exit_price"], 4),
                "exit_reason": leg["exit_reason"],
                "return_pct": round(ret, 1),
                "mdd_close_pct": round(leg["mdd_close_pct"], 1),
                "mdd_low_pct": round(leg["mdd_low_pct"], 1),
                "gap_excess_loss_pct": round(leg["gap_excess_loss_pct"], 2),
                "whipsaw_recovered": whipsaw,
                "final_stop_price": round(leg["final_stop_price"], 4),
                "peak_at_exit": round(leg["peak_at_exit"], 4),
            })

    return results


def run_trailing_stop_reentry(weekly_candles, cycles, daily_rows, trail_pct,
                               slippage_pct=0.0, dataset_key=None, ticker=None):
    """weeksSinceCross<=4 재성립 시 재진입 허용 변형 (stoploss_daily_backtest.run_stoploss_candidates의
    reentry 로직과 동일한 체크포인트 구조를 추적 손절에 적용). 보조 분석에서만 사용한다."""
    trades_by_cycle = []

    for cyc in cycles:
        c = cyc["cross_index"]
        e = cyc["exit_index"]
        ctx = sdb.build_cycle_daily_context(weekly_candles, cyc, daily_rows)
        hard_exit_idx = ctx["hard_exit_idx"]
        hard_exit_is_trend = ctx["hard_exit_is_trend"]

        checkpoints = []
        for k in range(0, 5):
            wk_idx = c + k
            if wk_idx > e or wk_idx >= len(weekly_candles):
                break
            week_start = weekly_candles[wk_idx]["date"]
            chk_idx = sdb.execution_index_after_friday(daily_rows, week_start)
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
            pos_entry_price = sdb.apply_buy_slippage(daily_rows[chk_idx]["open"], slippage_pct)

            leg = simulate_trailing_stop_leg(
                daily_rows, pos_entry_idx, pos_entry_price, hard_exit_idx, hard_exit_is_trend,
                trail_pct, slippage_pct=slippage_pct,
            )
            trade_ret = (leg["exit_price"] / pos_entry_price - 1.0) * 100.0
            trades.append({
                "checkpoint_week_offset": k,
                "entry_date": daily_rows[pos_entry_idx]["date"].isoformat(),
                "entry_price": round(pos_entry_price, 4),
                "exit_date": daily_rows[leg["exit_idx"]]["date"].isoformat(),
                "exit_price": round(leg["exit_price"], 4),
                "exit_reason": leg["exit_reason"],
                "return_pct": round(trade_ret, 1),
                "gap_excess_loss_pct": round(leg["gap_excess_loss_pct"], 2),
            })

            if leg["exit_reason"] != "STOP":
                break

            stop_count += 1
            recovered = False
            for j in range(leg["exit_idx"] + 1, hard_exit_idx + 1):
                if daily_rows[j]["close"] >= leg["final_stop_price"]:
                    recovered = True
                    break
            if recovered:
                whipsaw_count += 1

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

        trades_by_cycle.append({
            "dataset": dataset_key,
            "ticker": ticker,
            "cross_date": weekly_candles[c]["date"].isoformat(),
            "trades": trades,
            "num_stops": stop_count,
            "num_whipsaw_recoveries": whipsaw_count,
            "cycle_compounded_return_pct": round(cycle_return_pct, 1) if cycle_return_pct is not None else None,
        })

    return trades_by_cycle


if __name__ == "__main__":
    print("이 파일은 라이브러리로 사용하세요: 실행 스크립트는 run_trailing_stop_report.py 참고", file=sys.stderr)

#!/usr/bin/env python3
"""
chandelier_exit_backtest.py

목적
----
Track A 손절 레이어의 새 가설: "Chandelier Exit"(Chuck LeBeau 고안 — 진입 이후 일봉 종가
최고값에서 k × ATR(일봉, 매일 재계산)만큼 뺀 값을 손절선으로 삼고, 매일 갱신하는 추적 손절).

이 가설은 기존에 개별적으로 기각된 두 접근의 실패 원인을 동시에 겨냥한다.
- ATR 기반 손절(`track-a-stoploss-drawdown-review.md` 등)의 실패 원인: 진입 시점에 ATR을 1회
  계산해 고정하므로 사이클 중반의 변동성 확대를 따라가지 못한다.
- 추적 손절(`track-a-trailing-stop-review.md`)의 실패 원인: 변동성과 무관한 고정 비율(-20/25/30%)
  을 쓰므로, 레버리지 ETF 특유의 정상적인 20~30%대 눌림목까지 대부분 손절로 처리해버린다.

Chandelier Exit는 "추적"(peak 갱신)과 "변동성 반영"(ATR을 매일 재계산)을 동시에 적용하므로, 두
실패 원인 중 어느 쪽도 그대로 재현되지 않을 수 있다는 것이 사전 가설이다. **이 스크립트는
결과를 계산하기 전에 이 가설과 파라미터(k=2, 3 — Chandelier Exit 기법에서 통상 쓰이는 표준값)를
고정한다. k를 데이터에 맞춰 사후에 고르지 않는다.**

기존 도구와의 관계 (전부 재사용, 수정 없음)
--------------------------------------------
- `stoploss_daily_backtest.py`: `load_daily_candles`, `compute_daily_atr`(일봉 ATR, 매일 갱신되는
  배열), `build_cycle_daily_context`, `apply_buy_slippage`, `apply_sell_slippage`,
  `mdd_over_window`를 그대로 import한다.
- 시뮬레이션 로직(`simulate_chandelier_exit_leg`)은 `trailing_stop_backtest.simulate_trailing_stop_leg`
  와 구조가 동일하다 — 유일한 차이는 손절선 계산식이 `peak * (1 - trail_pct)`(고정 비율) 대신
  `peak - k * atr[j]`(그날의 ATR)라는 점뿐이다. 코드를 복사한 이유는 손절선 계산식 자체가
  다르기 때문이며, peak 갱신·체결·슬리피지·동률 처리 로직은 `trailing_stop_backtest.py`와
  의도적으로 동일하게 맞췄다(교차비교 가능하도록).

사용 예
-------
python3 research/data/tools/run_chandelier_exit_report.py
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402


def simulate_chandelier_exit_leg(daily_rows, atr, entry_idx, entry_price, hard_exit_idx,
                                  hard_exit_is_trend, k, slippage_pct=0.0):
    """진입 이후 일봉 종가 최고값(peak) - k*ATR(그날 기준, 매일 재계산)을 손절선으로 하는
    Chandelier Exit 시뮬레이션. trailing_stop_backtest.simulate_trailing_stop_leg와 동일한
    체결/슬리피지/동률 처리 규칙을 따른다.

    ATR이 아직 계산 불가한 날(초기 구간, atr[j] is None)에는 그 날의 손절선을 걸지 않는다
    (ATR(14)는 최소 14개 관측치가 필요하므로, 상장 초기 구간에서 발생할 수 있다 — 이 경우
    stop_hit 여부를 판정하지 않고 다음 날로 넘어간다).

    반환: dict(exit_idx, exit_price, exit_reason, gap_excess_loss_pct, mdd_close_pct, mdd_low_pct,
               tie_break_note, final_stop_price, peak_at_exit, atr_at_exit)
    exit_reason: "STOP" | "TREND" | "OPEN_MARK" | "ATR_UNAVAILABLE_NO_STOP"(전 구간 ATR 없음, 드묾)
    """
    peak = entry_price
    last_stop_price = None
    last_atr = None
    for j in range(entry_idx, hard_exit_idx + 1):
        atr_j = atr[j]
        is_hard_exit_day = (j == hard_exit_idx)

        if atr_j is not None:
            stop_price_j = peak - k * atr_j
            last_stop_price = stop_price_j
            last_atr = atr_j
            low_j = daily_rows[j]["low"]
            open_j = daily_rows[j]["open"]
            stop_hit = low_j <= stop_price_j

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
                            "final_stop_price": stop_price_j, "peak_at_exit": peak, "atr_at_exit": atr_j,
                        }

                mdd_close, mdd_low = sdb.mdd_over_window(daily_rows, entry_idx, j, entry_price)
                gap_excess = ((stop_price_j - stop_fill_raw) / entry_price * 100.0) if gap else 0.0
                return {
                    "exit_idx": j, "exit_price": stop_fill, "exit_reason": "STOP",
                    "gap_excess_loss_pct": gap_excess,
                    "mdd_close_pct": mdd_close * 100.0, "mdd_low_pct": mdd_low * 100.0,
                    "tie_break_note": None,
                    "final_stop_price": stop_price_j, "peak_at_exit": peak, "atr_at_exit": atr_j,
                }

        if is_hard_exit_day:
            if hard_exit_is_trend:
                exit_price = sdb.apply_sell_slippage(daily_rows[j]["open"], slippage_pct)
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
                "final_stop_price": last_stop_price, "peak_at_exit": peak, "atr_at_exit": last_atr,
            }

        close_j = daily_rows[j]["close"]
        if close_j > peak:
            peak = close_j

    raise RuntimeError("도달 불가 상태 (entry_idx > hard_exit_idx?)")


def run_chandelier_exit_candidates(weekly_candles, cycles, daily_rows, atr, label, k_values,
                                    slippage_pct=0.0, dataset_key=None, ticker=None):
    """delay=0(엔진의 실제 최초 진입 시점) 기준, 재진입 금지로 Chandelier Exit 후보들을 시뮬레이션한다.

    k_values: {"chandelier_k2": 2.0, "chandelier_k3": 3.0} 형태.
    """
    results = {name: [] for name in k_values}

    for cyc in cycles:
        ctx = sdb.build_cycle_daily_context(weekly_candles, cyc, daily_rows)
        entry0_idx = ctx["entry0_idx"]
        hard_exit_idx = ctx["hard_exit_idx"]
        hard_exit_is_trend = ctx["hard_exit_is_trend"]

        if entry0_idx is None or entry0_idx > hard_exit_idx:
            continue

        entry0_price = sdb.apply_buy_slippage(daily_rows[entry0_idx]["open"], slippage_pct)

        for name, k in k_values.items():
            leg = simulate_chandelier_exit_leg(
                daily_rows, atr, entry0_idx, entry0_price, hard_exit_idx, hard_exit_is_trend,
                k, slippage_pct=slippage_pct,
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
                "final_stop_price": round(leg["final_stop_price"], 4) if leg["final_stop_price"] else None,
                "peak_at_exit": round(leg["peak_at_exit"], 4),
                "atr_at_exit": round(leg["atr_at_exit"], 4) if leg["atr_at_exit"] else None,
            })

    return results


if __name__ == "__main__":
    print("이 파일은 라이브러리로 사용하세요: 실행 스크립트는 run_chandelier_exit_report.py 참고", file=sys.stderr)

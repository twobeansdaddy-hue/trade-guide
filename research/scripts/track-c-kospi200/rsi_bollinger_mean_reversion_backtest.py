#!/usr/bin/env python3
"""Track C 두 번째 후보 - RSI/볼린저밴드 평균회귀 가설 검증 (KOSPI200).

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
**가설**: 코스피 개별주는 레버리지 ETF(Track A)와 달리 극단적 추세가 아니라 평균회귀
성향이 강할 수 있다 - 단기 과매도 신호(RSI 급락, 볼린저 하단 이탈) 이후 반등한다는
가설을 두 가지 독립 규칙으로 검증한다. Track A 규칙(추세추종)이 이미 한국 시장에서
기각됐으므로, 정반대 메커니즘을 시도한다(Track B의 모멘텀→단기반전 전환과 동일한
논리 - `track-b-short-term-reversal-validation.md`).

**규칙 A - RSI(14주) 평균회귀**:
- 진입: RSI(14, Wilder 방식, 주봉 종가 기준)가 30을 하향 돌파하는 주(직전 주
  RSI>=30, 이번 주 RSI<30).
- 청산: RSI가 50을 상향 돌파하는 주, 또는 진입 후 8주가 지나도 청산 조건을
  못 채우면 강제 청산(무기한 보유 방지 - Track A 사이클 정의와 달리 평균회귀는
  "회귀 실패"를 무기한 방치하면 안 되므로 이번 검증에서 새로 정함).

**규칙 B - 볼린저밴드(20주, 2표준편차) 평균회귀**:
- 진입: 종가가 하단밴드(SMA20-2*STD20)를 하향 돌파하는 주(직전 주 종가>=하단밴드,
  이번 주 종가<하단밴드).
- 청산: 종가가 중단밴드(SMA20)를 상향 돌파하는 주, 또는 진입 후 8주 강제 청산.

두 규칙은 **독립적으로 검증**한다(같은 종목이 두 신호를 동시에 낼 수 있으나 서로
다른 포트폴리오로 백테스트) - 어느 쪽이 더 나은지, 혹은 둘 다 기각되는지가 검증
목적이다. 8주 강제청산 기준은 두 규칙에 동일하게 적용해 규칙 간 비교가 가능하도록
사전에 고정한다.

**포트폴리오화**: Track C 첫 리포트와 동일 - 매주 그 시점 진입/보유 조건을 만족하는
모든 종목을 동일가중으로 재조정.

**유니버스·비용·분할**: Track C 첫 리포트와 완전히 동일 - KOSPI200 199종목(기존
캐시 재사용, `research/data/cache/track-c-kospi200/universe_weekly.csv`), 비용
0/20/40/57bp, 워크포워드 2way(2015-19/2020-26)·4way(2015-17/18-20/21-23/24-26).

**채택 게이트**: 이번 세션 다른 후보와 동일한 5개 게이트(20-57bp 반영 후 전 구간
순수익 양, 부호 일관성, 월환산 회전율<50%, MDD가 벤치마크보다 뚜렷이 나쁘지 않음,
backtests.json 기록).

실행: python3 research/scripts/track-c-kospi200/rsi_bollinger_mean_reversion_backtest.py
"""
import csv
import json
import math
import os

RSI_PERIOD = 14
RSI_ENTRY_THRESHOLD = 30.0
RSI_EXIT_THRESHOLD = 50.0
BB_PERIOD = 20
BB_STD_MULT = 2.0
MAX_HOLDING_WEEKS = 8
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100_000_000.0  # KRW 1억원 (임의 기준값, 결과는 비율로만 해석)

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-c-kospi200"
)
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "rsi_bollinger_mean_reversion_results.json")

PARTITIONS = {
    "2way": [
        {"id": "2way-A", "label": "2015-01-01 ~ 2019-12-31", "start": "2015-01-01", "end": "2019-12-31"},
        {"id": "2way-B", "label": "2020-01-01 ~ 2026-09-10", "start": "2020-01-01", "end": "2026-09-10"},
    ],
    "4way": [
        {"id": "4way-A", "label": "2015-01-01 ~ 2017-12-31", "start": "2015-01-01", "end": "2017-12-31"},
        {"id": "4way-B", "label": "2018-01-01 ~ 2020-12-31", "start": "2018-01-01", "end": "2020-12-31"},
        {"id": "4way-C", "label": "2021-01-01 ~ 2023-12-31", "start": "2021-01-01", "end": "2023-12-31"},
        {"id": "4way-D", "label": "2024-01-01 ~ 2026-09-10", "start": "2024-01-01", "end": "2026-09-10"},
    ],
}


def load_universe_csv(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            out.setdefault(row["ticker"], []).append((row["datetime"], float(row["close"])))
    return out


def run_quality_checks(universe):
    excluded = {}
    for ticker, rows in universe.items():
        dates = [d for d, _ in rows]
        if len(dates) != len(set(dates)):
            excluded[ticker] = "DUPLICATE_TRADING_DATE"
            continue
        if dates != sorted(dates):
            excluded[ticker] = "UNSORTED_HISTORY"
    return excluded


def rsi_series(closes, period=RSI_PERIOD):
    """Wilder RSI. closes: 날짜순 정렬. 반환: 길이 동일 RSI 리스트(None=계산불가)."""
    n = len(closes)
    rsi = [None] * n
    if n < period + 1:
        return rsi
    gains, losses = [], []
    for i in range(1, n):
        change = closes[i] - closes[i - 1]
        gains.append(max(change, 0.0))
        losses.append(max(-change, 0.0))
    avg_gain = sum(gains[:period]) / period
    avg_loss = sum(losses[:period]) / period
    idx = period  # closes 인덱스: gains[period-1]는 closes[period]-closes[period-1]
    rsi[idx] = 100.0 if avg_loss == 0 else 100.0 - (100.0 / (1.0 + avg_gain / avg_loss))
    for i in range(period, len(gains)):
        avg_gain = (avg_gain * (period - 1) + gains[i]) / period
        avg_loss = (avg_loss * (period - 1) + losses[i]) / period
        idx = i + 1
        rsi[idx] = 100.0 if avg_loss == 0 else 100.0 - (100.0 / (1.0 + avg_gain / avg_loss))
    return rsi


def bollinger_series(closes, period=BB_PERIOD, mult=BB_STD_MULT):
    n = len(closes)
    mid, lower = [None] * n, [None] * n
    for i in range(n):
        if i + 1 < period:
            continue
        window = closes[i + 1 - period:i + 1]
        m = sum(window) / period
        var = sum((x - m) ** 2 for x in window) / period
        sd = math.sqrt(var)
        mid[i] = m
        lower[i] = m - mult * sd
    return mid, lower


def rsi_signals(closes):
    """반환: (in_position_flags, entries, exits) - 규칙 A. 인덱스는 closes와 동일."""
    rsi = rsi_series(closes)
    n = len(closes)
    in_pos = [False] * n
    entries, exits = [False] * n, [False] * n
    holding = False
    entry_idx = None
    for i in range(n):
        if holding:
            weeks_held = i - entry_idx
            exit_now = False
            if rsi[i] is not None and rsi[i - 1] is not None and rsi[i - 1] < RSI_EXIT_THRESHOLD <= rsi[i]:
                exit_now = True
            elif weeks_held >= MAX_HOLDING_WEEKS:
                exit_now = True
            if exit_now:
                exits[i] = True
                holding = False
                entry_idx = None
            else:
                in_pos[i] = True
        if not holding and i > 0 and rsi[i] is not None and rsi[i - 1] is not None:
            if rsi[i - 1] >= RSI_ENTRY_THRESHOLD > rsi[i]:
                entries[i] = True
                holding = True
                entry_idx = i
                in_pos[i] = True
    return in_pos, entries, exits


def bollinger_signals(closes):
    """반환: (in_position_flags, entries, exits) - 규칙 B."""
    mid, lower = bollinger_series(closes)
    n = len(closes)
    in_pos = [False] * n
    entries, exits = [False] * n, [False] * n
    holding = False
    entry_idx = None
    for i in range(n):
        if holding:
            weeks_held = i - entry_idx
            exit_now = False
            if mid[i] is not None and mid[i - 1] is not None and closes[i - 1] < mid[i - 1] <= closes[i]:
                exit_now = True
            elif weeks_held >= MAX_HOLDING_WEEKS:
                exit_now = True
            if exit_now:
                exits[i] = True
                holding = False
                entry_idx = None
            else:
                in_pos[i] = True
        if not holding and i > 0 and lower[i] is not None and lower[i - 1] is not None:
            if closes[i - 1] >= lower[i - 1] and closes[i] < lower[i]:
                entries[i] = True
                holding = True
                entry_idx = i
                in_pos[i] = True
    return in_pos, entries, exits


def _cost_leg(notional, round_trip_bps):
    return notional * (round_trip_bps / 2.0 / 10000.0)


def clip_window(calendar, start, end):
    cs = [d for d in calendar if d >= start]
    ce = [d for d in calendar if d <= end]
    if not cs or not ce:
        return None, None
    return cs[0], ce[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def run_backtest(clean_series, in_pos_by_ticker, calendar, start_date, end_date, round_trip_cost_bps, initial_capital):
    date_index_by_ticker = {t: {d: i for i, d in enumerate(sorted(prices.keys()))} for t, prices in clean_series.items()}
    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}

    all_dates = [d for d in calendar if start_date <= d <= end_date]
    if len(all_dates) < 2:
        return None

    shares, cash = {}, initial_capital
    events = []
    turnovers = []

    def portfolio_value(as_of):
        v = cash
        for t, sh in shares.items():
            p = clean_series[t].get(as_of)
            if p is None:
                past = [d for d in sorted_dates_by_ticker[t] if d <= as_of]
                p = clean_series[t][past[-1]] if past else 0.0
            v += sh * p
        return v

    for r_date in all_dates:
        new_holdings = set()
        for t in clean_series:
            idx_map = date_index_by_ticker[t]
            idx = idx_map.get(r_date)
            if idx is None:
                continue
            if in_pos_by_ticker[t][idx]:
                new_holdings.add(t)

        pre_trade_value = portfolio_value(r_date)
        target_per_position = pre_trade_value / len(new_holdings) if new_holdings else 0.0

        prev_position_values = {}
        for t in set(list(shares.keys()) + list(new_holdings)):
            p = clean_series.get(t, {}).get(r_date)
            if p is None and t in shares:
                past = [d for d in sorted_dates_by_ticker.get(t, []) if d <= r_date]
                p = clean_series[t][past[-1]] if past else None
            prev_position_values[t] = shares.get(t, 0.0) * p if p is not None else 0.0

        total_trade_notional = 0.0
        new_shares = {}
        cash_delta = 0.0
        for t in set(list(shares.keys()) + list(new_holdings)):
            price = clean_series.get(t, {}).get(r_date)
            target_value = target_per_position if t in new_holdings else 0.0
            current_value = prev_position_values.get(t, 0.0)
            delta = target_value - current_value
            total_trade_notional += abs(delta)
            if price is None or price <= 0:
                new_shares[t] = shares.get(t, 0.0)
                continue
            if delta > 0:
                cost = _cost_leg(delta, round_trip_cost_bps)
                bought_value = delta - cost
                new_shares[t] = current_value / price + (bought_value / price if bought_value > 0 else 0.0)
                cash_delta -= delta
            elif delta < 0:
                sell_notional = -delta
                cost = _cost_leg(sell_notional, round_trip_cost_bps)
                new_shares[t] = current_value / price - (sell_notional / price)
                cash_delta += (sell_notional - cost)
            else:
                new_shares[t] = current_value / price if price else shares.get(t, 0.0)

        cash = cash + cash_delta
        shares = {t: sh for t, sh in new_shares.items() if sh > 1e-9}
        turnover = (total_trade_notional / pre_trade_value) if pre_trade_value > 0 else 0.0
        turnovers.append(turnover)
        events.append({"date": r_date, "holdingsAfter": sorted(new_holdings)})

    weekly_values = [(d, portfolio_value(d)) for d in all_dates]
    peak, mdd = -math.inf, 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    avg_turnover = sum(turnovers) / len(turnovers) if turnovers else 0.0
    monthly_equivalent = avg_turnover * 4.345 * 100.0

    return {"events": events, "endingValue": ending_value, "maxDrawdownPct": mdd,
            "monthlyEquivalentTurnoverPct": monthly_equivalent}


def run_equal_weight_buy_and_hold(clean_series, calendar, start_date, end_date, round_trip_cost_bps, initial_capital):
    eligible = [t for t, prices in clean_series.items() if start_date in prices]
    if not eligible:
        return None, None
    per_position = initial_capital / len(eligible)
    shares = {}
    for t in eligible:
        price = clean_series[t][start_date]
        cost = _cost_leg(per_position, round_trip_cost_bps)
        shares[t] = (per_position - cost) / price

    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}
    weekly_values = []
    for d in calendar:
        if d < start_date or d > end_date:
            continue
        v = 0.0
        for t, sh in shares.items():
            p = clean_series[t].get(d)
            if p is None:
                past = [x for x in sorted_dates_by_ticker[t] if x <= d]
                p = clean_series[t][past[-1]] if past else 0.0
            v += sh * p
        weekly_values.append((d, v))

    peak, mdd = -math.inf, 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)
    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    return ending_value, mdd


def run_rule(rule_name, signal_fn, clean, calendar):
    in_pos_by_ticker = {}
    for t, prices in clean.items():
        dts = sorted(prices.keys())
        closes = [prices[d] for d in dts]
        in_pos, _, _ = signal_fn(closes)
        in_pos_by_ticker[t] = in_pos

    date_index_by_ticker = {t: sorted(prices.keys()) for t, prices in clean.items()}
    in_pos_lookup = {t: {date_index_by_ticker[t][i]: flag for i, flag in enumerate(flags)} for t, flags in in_pos_by_ticker.items()}

    rule_output = {"partitions": {}}
    for partition_name, windows in PARTITIONS.items():
        rule_output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            window_result = {"id": w["id"], "label": w["label"], "actualStart": w_start, "actualEnd": w_end, "byCostBps": {}}
            for bps in COST_BPS_SCENARIOS:
                # in_pos_by_ticker indexed by position not date directly usable in run_backtest; rebuild per-ticker date-indexed list matching calendar order used inside run_backtest via date_index_by_ticker map built there.
                strat = run_backtest(clean, {t: [in_pos_lookup[t].get(d, False) for d in date_index_by_ticker[t]] for t in clean},
                                      calendar, w_start, w_end, bps, INITIAL_CAPITAL)
                if strat is None:
                    continue
                invested_dates = [e["date"] for e in strat["events"] if e["holdingsAfter"]]
                bench_start = invested_dates[0] if invested_dates else w_start
                bench_ending, bench_mdd = run_equal_weight_buy_and_hold(clean, calendar, bench_start, w_end, bps, INITIAL_CAPITAL)
                if bench_ending is None:
                    continue
                strat_ret = pct_return(strat["endingValue"], INITIAL_CAPITAL)
                bench_ret = pct_return(bench_ending, INITIAL_CAPITAL)
                rebals_with_holdings = [e for e in strat["events"] if e["holdingsAfter"]]
                avg_holdings = (
                    round(sum(len(e["holdingsAfter"]) for e in rebals_with_holdings) / len(rebals_with_holdings), 1)
                    if rebals_with_holdings else 0.0
                )
                window_result["byCostBps"][str(bps)] = {
                    "avgHoldingsCount": avg_holdings,
                    "strategyReturnPct": round(strat_ret, 3),
                    "strategyMaxDrawdownPct": round(strat["maxDrawdownPct"], 3),
                    "strategyMonthlyTurnoverPct": round(strat["monthlyEquivalentTurnoverPct"], 3),
                    "benchmarkReturnPct": round(bench_ret, 3),
                    "benchmarkMaxDrawdownPct": round(bench_mdd, 3),
                    "excessReturnPctPoints": round(strat_ret - bench_ret, 3),
                    "mddGapPctPoints": round(strat["maxDrawdownPct"] - bench_mdd, 3),
                }
            rule_output["partitions"][partition_name].append(window_result)
    return rule_output


def run_all():
    universe = load_universe_csv(CSV_PATH)
    quality = run_quality_checks(universe)
    clean = {t: {d: c for d, c in rows} for t, rows in universe.items() if t not in quality}
    calendar = sorted({d for rows in universe.values() for d, _ in rows})

    output = {
        "generatedBy": "research/scripts/track-c-kospi200/rsi_bollinger_mean_reversion_backtest.py",
        "hypothesis": "코스피 개별주는 단기 과매도(RSI<30 또는 볼린저 하단 이탈) 이후 평균회귀(반등)한다.",
        "universe": {"size": len(universe), "excludedByQuality": quality},
        "parameters": {
            "rsiPeriod": RSI_PERIOD, "rsiEntryThreshold": RSI_ENTRY_THRESHOLD, "rsiExitThreshold": RSI_EXIT_THRESHOLD,
            "bollingerPeriod": BB_PERIOD, "bollingerStdMult": BB_STD_MULT,
            "maxHoldingWeeks": MAX_HOLDING_WEEKS, "costScenariosRoundTripBps": COST_BPS_SCENARIOS,
            "initialCapitalKRW": INITIAL_CAPITAL,
        },
        "rules": {},
    }

    output["rules"]["ruleA_rsi"] = run_rule("ruleA_rsi", rsi_signals, clean, calendar)
    output["rules"]["ruleB_bollinger"] = run_rule("ruleB_bollinger", bollinger_signals, clean, calendar)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track C - RSI/볼린저밴드 평균회귀 - 요약")
    print("=" * 120)
    for rule_name, rule_output in output["rules"].items():
        print(f"\n########## {rule_name} ##########")
        for partition_name, windows in rule_output["partitions"].items():
            print(f"\n--- 분할체계: {partition_name} ---")
            for w in windows:
                print(f"\n[{w['id']}] {w['label']}")
                print(f"{'bps':>5} {'avgHold':>8} {'strat%':>12} {'bench%':>12} {'excess%p':>12} "
                      f"{'stratMDD%':>10} {'benchMDD%':>10} {'turnover%/mo':>13}")
                for bps in COST_BPS_SCENARIOS:
                    r = w["byCostBps"].get(str(bps))
                    if not r:
                        continue
                    print(f"{bps:5.0f} {r['avgHoldingsCount']:8.1f} "
                          f"{r['strategyReturnPct']:12.2f} {r['benchmarkReturnPct']:12.2f} "
                          f"{r['excessReturnPctPoints']:12.2f} {r['strategyMaxDrawdownPct']:10.2f} "
                          f"{r['benchmarkMaxDrawdownPct']:10.2f} {r['strategyMonthlyTurnoverPct']:13.2f}")


if __name__ == "__main__":
    run_all()

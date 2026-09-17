#!/usr/bin/env python3
"""Track C 첫 가설 - Track A 규칙(10주/40주 SMA 골든크로스)을 KOSPI200에 그대로 적용.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
Track A(`WeeklyMaCrossoverStrategy.java`)가 실제 운영에 쓰는 규칙을 **코드 그대로**
재현해 KOSPI200 199종목에 포트폴리오 단위로 적용한다. 파라미터 변경이나 튜닝은 하지
않는다 - 이번 검증의 목적은 "이 규칙이 다른 시장(한국 코스피)에서도 통하는가"이지
새 규칙을 만드는 것이 아니다.

**정확히 재현하는 프로덕션 규칙** (`src/main/java/com/tradeguide/service/strategy/
tracka/WeeklyMaCrossoverStrategy.java` 그대로):
- trend = ABOVE_LONG_AVERAGE if SMA(10주 종가) > SMA(40주 종가), 아니면 BELOW.
- CROSS_UP: 직전 주 SMA10<=SMA40 이고 이번 주 SMA10>SMA40. CROSS_DOWN은 반대.
- weeksSinceCross: 가장 최근 CROSS_UP/DOWN 이벤트로부터 경과 주.
- `StrategyDecisionMaker`(보유 종목 HOLD/SELL, 후보 BUY/WATCH) 규칙:
  - 보유 종목: trend==ABOVE면 유지, BELOW로 전환되는 즉시 매도.
  - 미보유 후보: trend==ABOVE AND weeksSinceCross가 0~4주 사이일 때만 신규 진입,
    그 외에는 WATCH(진입하지 않음).

**포트폴리오 구성 방식(이 검증에서 새로 정한 부분, 프로덕션에는 없음 - 프로덕션은**
**포지션 사이징을 다루지 않으므로)**: 매주 그 시점 진입 조건을 만족하는 모든 종목과
이미 보유 중이며 아직 매도 조건에 해당하지 않는 종목을 동일가중으로 재조정한다
(Track B의 PEG/배당성장 검증과 동일한 임계값-필터 스타일 - 순위 기반이 아니다).

가설: Track A가 SOXL/TQQQ(레버리지 ETF)에서 검증된 이유(대형 낙폭을 피하는 효과)가
아니라 규칙 자체(추세추종)가 유효하다면, 코스피 개별주에도 어느 정도 통해야 한다.
다만 사전에 명시한다 - `research/reports/two-track-strategy-framework.md`가 이미
"Track A의 우위는 레버리지 ETF 특유의 대형 낙폭을 피한 단일 사건이 거의 전부를
설명하며, 대형 낙폭이 없는 안정적 우량주에는 규칙의 단점(신호 지연·횡보 휩쏘)만
남는다"고 지적한 바 있다(미국 우량주 AAPL/JPM/PG 대상). 코스피 개별주도 마찬가지
결과가 나올 가능성이 있다는 것을 결과를 보기 전에 밝혀둔다.

유니버스·비용·분할
------------------
- KOSPI200 199종목(Wikipedia, 2026-09-16), Yahoo Finance 주봉(KRW), 캘린더 정합성
  보정 완료.
- 비용 시나리오는 Track B와 동일하게 0/20/40/57bp를 그대로 쓴다 - 한국 시장 고유의
  비용 실측치를 별도로 조사하지 않았다는 것을 caveat으로 남긴다(Novy-Marx & Velikov
  2016은 미국 시장 기준).
- 워크포워드 분할도 Track B와 동일: 2way(2015-19/2020-26), 4way(2015-17/18-20/
  21-23/24-26) - 세션 전체에서 비교 가능하도록.

실행: python3 research/scripts/track-c-kospi200/track_a_rule_kospi200_backtest.py
"""
import csv
import json
import math
import os
import sys

SHORT_PERIOD = 10
LONG_PERIOD = 40
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100_000_000.0  # KRW 1억원 (임의 기준값, 결과는 비율로만 해석)

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-c-kospi200"
)
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(BASE_DIR, "track_a_rule_backtest_results.json")

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


def sma_series(closes):
    """closes: 날짜순 정렬된 종가 리스트. 반환: 같은 길이의 (sma10, sma40) 리스트 (None=계산불가)."""
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
    """반환: (trend_list, weeks_since_cross_list) - 인덱스는 closes와 동일.
    trend: 'ABOVE'|'BELOW'|None(계산불가). weeksSinceCross: 정수 또는 None(교차 이력 없음)."""
    sma10, sma40 = sma_series(closes)
    n = len(closes)
    trend = [None] * n
    for i in range(n):
        if sma10[i] is None or sma40[i] is None:
            continue
        trend[i] = "ABOVE" if sma10[i] > sma40[i] else "BELOW"

    events = [None] * n  # 'CROSS_UP' | 'CROSS_DOWN' | None
    for i in range(1, n):
        if sma10[i] is None or sma40[i] is None or sma10[i - 1] is None or sma40[i - 1] is None:
            continue
        prev_above = sma10[i - 1] > sma40[i - 1]
        cur_above = sma10[i] > sma40[i]
        if not prev_above and cur_above:
            events[i] = "CROSS_UP"
        elif prev_above and not cur_above:
            events[i] = "CROSS_DOWN"

    weeks_since_cross = [None] * n
    last_cross_idx = None
    for i in range(n):
        if events[i] is not None:
            last_cross_idx = i
        if last_cross_idx is not None:
            weeks_since_cross[i] = i - last_cross_idx

    return trend, weeks_since_cross


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


def run_backtest(clean_series, trend_by_ticker, weeks_since_cross_by_ticker, calendar,
                  start_date, end_date, round_trip_cost_bps, initial_capital):
    date_index_by_ticker = {t: {d: i for i, (d, _) in enumerate(sorted(prices.items()))} for t, prices in clean_series.items()}
    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}

    all_dates = [d for d in calendar if start_date <= d <= end_date]
    if len(all_dates) < 2:
        return None

    shares, cash = {}, initial_capital
    events = []
    turnovers = []
    holdings_before = set()

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
        qualifying_new = []
        for t in clean_series:
            idx_map = date_index_by_ticker[t]
            idx = idx_map.get(r_date)
            if idx is None:
                continue
            trend = trend_by_ticker[t][idx]
            wsc = weeks_since_cross_by_ticker[t][idx]
            if trend == "ABOVE" and wsc is not None and 0 <= wsc <= 4:
                qualifying_new.append(t)

        kept = set()
        for t in holdings_before:
            idx_map = date_index_by_ticker[t]
            idx = idx_map.get(r_date)
            if idx is None:
                kept.add(t)  # 이 주 데이터가 없으면 보유 유지(매도 판단 불가)
                continue
            trend = trend_by_ticker[t][idx]
            if trend == "ABOVE":
                kept.add(t)
            # BELOW면 매도(제외) - 프로덕션 규칙과 동일

        new_holdings = kept | set(qualifying_new)

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
        holdings_before = new_holdings

    weekly_values = [(d, portfolio_value(d)) for d in all_dates]
    peak, mdd = -math.inf, 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    avg_turnover = sum(turnovers) / len(turnovers) if turnovers else 0.0
    monthly_equivalent = avg_turnover * 4.345 * 100.0  # 매주 리밸런싱 기준

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


def run_all():
    universe = load_universe_csv(CSV_PATH)
    quality = run_quality_checks(universe)
    clean = {t: {d: c for d, c in rows} for t, rows in universe.items() if t not in quality}
    calendar = sorted({d for rows in universe.values() for d, _ in rows})

    trend_by_ticker, wsc_by_ticker = {}, {}
    sorted_series = {}
    for t, prices in clean.items():
        dts = sorted(prices.keys())
        closes = [prices[d] for d in dts]
        trend, wsc = trend_and_events(closes)
        sorted_series[t] = dts
        trend_by_ticker[t] = trend
        wsc_by_ticker[t] = wsc

    output = {
        "generatedBy": "research/scripts/track-c-kospi200/track_a_rule_kospi200_backtest.py",
        "hypothesis": "Track A 프로덕션 규칙(10주/40주 SMA 골든크로스, 진입 0-4주)을 KOSPI200에 그대로 적용, 동일가중 포트폴리오, 매주 리밸런싱.",
        "universe": {"size": len(universe), "excludedByQuality": quality},
        "parameters": {"shortPeriod": SHORT_PERIOD, "longPeriod": LONG_PERIOD, "costScenariosRoundTripBps": COST_BPS_SCENARIOS, "initialCapitalKRW": INITIAL_CAPITAL},
        "partitions": {},
    }

    for partition_name, windows in PARTITIONS.items():
        output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            window_result = {"id": w["id"], "label": w["label"], "actualStart": w_start, "actualEnd": w_end, "byCostBps": {}}
            for bps in COST_BPS_SCENARIOS:
                strat = run_backtest(clean, trend_by_ticker, wsc_by_ticker, calendar, w_start, w_end, bps, INITIAL_CAPITAL)
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
            output["partitions"][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 120)
    print("Track C - Track A 규칙(KOSPI200) - 요약")
    print("=" * 120)
    for partition_name, windows in output["partitions"].items():
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

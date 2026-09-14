#!/usr/bin/env python3
"""횡단면 모멘텀 워크포워드 계산 코어 (Python, 표준 라이브러리만 사용).

이 모듈은 `src/main/java/com/tradeguide/service/backtest/CrossSectionalMomentumBacktestEngine`
(research/reports/track-b-cross-sectional-momentum-validation.md 참고)이 정의한 계산
규칙을 독립적으로 재구현한 것이다. Claude research 모드는 `src/**`를 수정하거나 Gradle
테스트를 새로 추가할 수 없으므로(작업 계약의 변경 허용 범위 = research/**), 동일한 계산
규칙과 데이터 품질/미래참조 방지 안전장치를 이 순수 Python 코어와 `test_momentum_engine.py`의
단위 테스트로 독립적으로 재현·검증한다. 이는 Java 코어를 대체하거나 그 검증을 대신 "통과"
시키는 것이 아니라, 이번 실데이터 실행을 위한 "동등한 보호 장치"임을 명시한다.

계산 규칙 (research/reports/track-b-cross-sectional-momentum-validation.md 그대로 이식)
------------------------------------------------------------------------------------
- 형성기간 수익률 = (리밸런싱 시점의 4주 전 종가 / 52주 전 종가 - 1) * 100. 최소 53주
  완료 주봉이 있어야 계산 가능하며, 부족하면 예외 대신 "판단 불가"로 표시한다.
- 리밸런싱: 유니버스 전체 거래일의 합집합 캘린더 기준으로, 형성기간을 채우는 첫 시점부터
  호출자가 지정한 주기(quarter=13주)마다.
- 상위/유지 이원 기준: 신규 진입은 상위 topTierFraction, 기존 보유는 holdTierFraction
  밖으로 밀려나기 전까지 유지.
- 동일가중: 매 리밸런싱마다 보유 종목 전체를 동일가중으로 재조정.
- 데이터 품질: 중복 거래일/비정렬 이력을 가진 종목은 전체 기간에서 제외(DUPLICATE_
  TRADING_DATE/UNSORTED_HISTORY). 특정 시점 이력 부족은 그 시점만 제외
  (INSUFFICIENT_HISTORY). 특정 시점 캔들 자체 없음은 그 시점만 제외하고 기존 보유는
  마지막 알려진 수량을 이월(MISSING_HISTORY).
- 비용: 왕복 bps를 매수/매도 두 다리에 절반씩 적용한다((bps/2/10000) * 거래대금, 각 다리).
  최종 보유 포지션은 강제 청산하지 않고 마지막 알려진 가격으로 시가평가한다(청산 비용 미반영).
"""
from __future__ import annotations

import csv
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

FORMATION_WEEKS = 52
EXCLUDE_RECENT_WEEKS = 4
MIN_HISTORY_WEEKS = FORMATION_WEEKS + 1  # need index -52 to exist


# ---------------------------------------------------------------------------
# 데이터 로딩 + 품질 검사
# ---------------------------------------------------------------------------

@dataclass
class QualityReport:
    excludedTickers: Dict[str, str] = field(default_factory=dict)  # ticker -> reason
    duplicateDates: Dict[str, List[str]] = field(default_factory=dict)
    unsortedTickers: List[str] = field(default_factory=list)
    perTickerRowCount: Dict[str, int] = field(default_factory=dict)
    calendarGaps: Dict[str, int] = field(default_factory=dict)  # ticker -> missing-week count vs union calendar


def load_universe_csv(path: str) -> Dict[str, List[Tuple[str, float]]]:
    """CSV(ticker;datetime;...;adjclose) -> {ticker: [(date, adjclose), ...]} (원본 순서 그대로, 정렬하지 않음)."""
    out: Dict[str, List[Tuple[str, float]]] = {}
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            out.setdefault(row["ticker"], []).append((row["datetime"], float(row["adjclose"])))
    return out


def run_quality_checks(universe: Dict[str, List[Tuple[str, float]]]) -> QualityReport:
    report = QualityReport()
    for ticker, rows in universe.items():
        report.perTickerRowCount[ticker] = len(rows)
        dates = [d for d, _ in rows]
        seen = set()
        dupes = []
        for d in dates:
            if d in seen:
                dupes.append(d)
            seen.add(d)
        if dupes:
            report.duplicateDates[ticker] = sorted(set(dupes))
            report.excludedTickers[ticker] = "DUPLICATE_TRADING_DATE"
            continue
        if dates != sorted(dates):
            report.unsortedTickers.append(ticker)
            report.excludedTickers[ticker] = "UNSORTED_HISTORY"
            continue
    # 결측 주(합집합 캘린더 대비) 점검 - 배제 사유는 아니고 정보성 기록
    all_dates = sorted({d for rows in universe.values() for d, _ in rows})
    calendar_index = {d: i for i, d in enumerate(all_dates)}
    for ticker, rows in universe.items():
        if ticker in report.excludedTickers:
            continue
        ticker_dates = {d for d, _ in rows}
        first_idx = calendar_index[min(ticker_dates)]
        last_idx = calendar_index[max(ticker_dates)]
        expected = set(all_dates[first_idx:last_idx + 1])
        missing = expected - ticker_dates
        if missing:
            report.calendarGaps[ticker] = len(missing)
    return report


def build_clean_series(
    universe: Dict[str, List[Tuple[str, float]]], quality: QualityReport
) -> Dict[str, Dict[str, float]]:
    """품질 검사를 통과한 종목만 {ticker: {date: adjclose}} 딕셔너리로 변환."""
    clean: Dict[str, Dict[str, float]] = {}
    for ticker, rows in universe.items():
        if ticker in quality.excludedTickers:
            continue
        clean[ticker] = {d: c for d, c in rows}
    return clean


def build_calendar(universe: Dict[str, List[Tuple[str, float]]]) -> List[str]:
    return sorted({d for rows in universe.values() for d, _ in rows})


# ---------------------------------------------------------------------------
# 형성기간 수익률 / 리밸런싱 스케줄
# ---------------------------------------------------------------------------

def formation_return(sorted_dates: List[str], price_by_date: Dict[str, float], as_of_date: str) -> Optional[float]:
    """as_of_date 시점 기준 48주 형성기간 수익률(52주전 -> 4주전 종가). 이력 부족 시 None."""
    if as_of_date not in price_by_date:
        return None
    idx = sorted_dates.index(as_of_date)
    if idx < FORMATION_WEEKS:
        return None
    p_52 = price_by_date[sorted_dates[idx - FORMATION_WEEKS]]
    p_4 = price_by_date[sorted_dates[idx - EXCLUDE_RECENT_WEEKS]]
    if p_52 <= 0:
        return None
    return (p_4 / p_52 - 1.0) * 100.0


def rebalance_dates(calendar: List[str], rebalance_period_weeks: int) -> List[str]:
    if len(calendar) <= FORMATION_WEEKS:
        return []
    dates = []
    i = FORMATION_WEEKS
    while i < len(calendar):
        dates.append(calendar[i])
        i += rebalance_period_weeks
    return dates


def window_rebalance_dates(calendar: List[str], rebalance_period_weeks: int, start: str, end: str) -> List[str]:
    all_dates = rebalance_dates(calendar, rebalance_period_weeks)
    return [d for d in all_dates if start <= d <= end]


# ---------------------------------------------------------------------------
# 상위/유지 이원 기준 선정
# ---------------------------------------------------------------------------

def select_holdings(
    ranked_tickers: List[str],  # 형성기간 수익률 내림차순, 이 시점에 판단 가능한 종목만
    previous_holdings: set,
    top_tier_fraction: float,
    hold_tier_fraction: float,
) -> set:
    n = len(ranked_tickers)
    if n == 0:
        return set()
    top_count = max(1, round(n * top_tier_fraction))
    hold_count = max(top_count, round(n * hold_tier_fraction))
    hold_zone = set(ranked_tickers[:hold_count])
    top_zone = set(ranked_tickers[:top_count])
    kept = {t for t in previous_holdings if t in hold_zone}
    new_entries = {t for t in top_zone if t not in kept}
    return kept | new_entries


# ---------------------------------------------------------------------------
# 백테스트 실행 (전략 + 동일가중 매수후보유 벤치마크)
# ---------------------------------------------------------------------------

@dataclass
class RebalanceEvent:
    date: str
    rankedFormationReturns: Dict[str, float]
    excludedThisDate: Dict[str, str]  # ticker -> reason (INSUFFICIENT_HISTORY / MISSING_HISTORY)
    holdingsBefore: set
    holdingsAfter: set
    portfolioValueBeforeTrade: float
    portfolioValueAfterCost: float
    oneWayTurnover: float  # sum(|trade notional|) / portfolioValueBeforeTrade


@dataclass
class BacktestResult:
    events: List[RebalanceEvent]
    weeklyValues: List[Tuple[str, float]]  # (date, mark-to-market portfolio value)
    endingValue: float
    maxDrawdownPct: float
    avgOneWayTurnoverPerRebalance: float
    monthlyEquivalentTurnoverPct: float


def _cost_leg(notional: float, round_trip_bps: float) -> float:
    return notional * (round_trip_bps / 2.0 / 10000.0)


def run_backtest(
    clean_series: Dict[str, Dict[str, float]],
    calendar: List[str],
    start_date: str,
    end_date: str,
    rebalance_period_weeks: int,
    top_tier_fraction: float,
    hold_tier_fraction: float,
    round_trip_cost_bps: float,
    initial_capital: float = 100000.0,
) -> BacktestResult:
    sorted_dates_by_ticker = {t: sorted(prices.keys()) for t, prices in clean_series.items()}
    r_dates = window_rebalance_dates(calendar, rebalance_period_weeks, start_date, end_date)
    if not r_dates:
        return BacktestResult([], [], initial_capital, 0.0, 0.0, 0.0)

    shares: Dict[str, float] = {}
    cash = initial_capital
    events: List[RebalanceEvent] = []
    turnovers: List[float] = []

    def portfolio_value(as_of: str) -> float:
        v = cash
        for t, sh in shares.items():
            p = clean_series[t].get(as_of)
            if p is None:
                # 마지막 알려진 가격 이월 (MISSING_HISTORY 단순화)
                dts = sorted_dates_by_ticker[t]
                past = [d for d in dts if d <= as_of]
                p = clean_series[t][past[-1]] if past else 0.0
            v += sh * p
        return v

    holdings_before: set = set()
    for r_date in r_dates:
        ranked_scores: Dict[str, float] = {}
        excluded_this_date: Dict[str, str] = {}
        for t, dts in sorted_dates_by_ticker.items():
            fr = formation_return(dts, clean_series[t], r_date)
            if fr is None:
                if r_date not in clean_series[t]:
                    excluded_this_date[t] = "MISSING_HISTORY"
                else:
                    excluded_this_date[t] = "INSUFFICIENT_HISTORY"
            else:
                ranked_scores[t] = fr

        ranked_tickers = sorted(ranked_scores, key=lambda t: ranked_scores[t], reverse=True)
        new_holdings = select_holdings(ranked_tickers, holdings_before, top_tier_fraction, hold_tier_fraction)

        pre_trade_value = portfolio_value(r_date)
        target_per_position = pre_trade_value / len(new_holdings) if new_holdings else 0.0

        prev_position_values = {}
        for t in set(list(shares.keys()) + list(new_holdings)):
            p = clean_series.get(t, {}).get(r_date)
            if p is None and t in shares:
                dts = sorted_dates_by_ticker.get(t, [])
                past = [d for d in dts if d <= r_date]
                p = clean_series[t][past[-1]] if past else None
            prev_position_values[t] = shares.get(t, 0.0) * p if p is not None else 0.0

        total_trade_notional = 0.0
        new_shares: Dict[str, float] = {}
        cost_total = 0.0
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
                cost_total += cost
                bought_value = delta - cost
                new_shares[t] = current_value / price + (bought_value / price if bought_value > 0 else 0.0)
            elif delta < 0:
                sell_notional = -delta
                cost = _cost_leg(sell_notional, round_trip_cost_bps)
                cost_total += cost
                new_shares[t] = current_value / price - (sell_notional / price)
            else:
                new_shares[t] = current_value / price if price else shares.get(t, 0.0)

        cash = pre_trade_value - sum(
            (new_shares.get(t, 0.0)) * clean_series.get(t, {}).get(r_date, 0.0)
            for t in new_shares
        )
        shares = {t: sh for t, sh in new_shares.items() if sh > 1e-9}

        turnover = (total_trade_notional / pre_trade_value) if pre_trade_value > 0 else 0.0
        turnovers.append(turnover)

        events.append(RebalanceEvent(
            date=r_date,
            rankedFormationReturns=ranked_scores,
            excludedThisDate=excluded_this_date,
            holdingsBefore=set(holdings_before),
            holdingsAfter=set(new_holdings),
            portfolioValueBeforeTrade=pre_trade_value,
            portfolioValueAfterCost=pre_trade_value - cost_total,
            oneWayTurnover=turnover,
        ))
        holdings_before = new_holdings

    weekly_values: List[Tuple[str, float]] = []
    for d in calendar:
        if d < r_dates[0] or d > end_date:
            continue
        weekly_values.append((d, portfolio_value(d)))

    peak = -math.inf
    mdd = 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    avg_turnover = sum(turnovers) / len(turnovers) if turnovers else 0.0
    monthly_equivalent = avg_turnover / (rebalance_period_weeks / 4.345) * 100.0

    return BacktestResult(
        events=events,
        weeklyValues=weekly_values,
        endingValue=ending_value,
        maxDrawdownPct=mdd,
        avgOneWayTurnoverPerRebalance=avg_turnover,
        monthlyEquivalentTurnoverPct=monthly_equivalent,
    )


def run_equal_weight_buy_and_hold(
    clean_series: Dict[str, Dict[str, float]],
    calendar: List[str],
    start_date: str,
    end_date: str,
    round_trip_cost_bps: float,
    initial_capital: float = 100000.0,
) -> BacktestResult:
    """비교 벤치마크: start_date에 유효 가격이 있는 모든 종목을 동일가중 매수 후 리밸런싱 없이 보유."""
    eligible = [t for t, prices in clean_series.items() if start_date in prices]
    if not eligible:
        return BacktestResult([], [], initial_capital, 0.0, 0.0, 0.0)

    per_position = initial_capital / len(eligible)
    shares: Dict[str, float] = {}
    cost_total = 0.0
    for t in eligible:
        price = clean_series[t][start_date]
        cost = _cost_leg(per_position, round_trip_cost_bps)
        cost_total += cost
        shares[t] = (per_position - cost) / price

    weekly_values: List[Tuple[str, float]] = []
    for d in calendar:
        if d < start_date or d > end_date:
            continue
        v = 0.0
        for t, sh in shares.items():
            dts = sorted(clean_series[t].keys())
            p = clean_series[t].get(d)
            if p is None:
                past = [x for x in dts if x <= d]
                p = clean_series[t][past[-1]] if past else 0.0
            v += sh * p
        weekly_values.append((d, v))

    peak = -math.inf
    mdd = 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)

    ending_value = weekly_values[-1][1] if weekly_values else initial_capital
    return BacktestResult(
        events=[],
        weeklyValues=weekly_values,
        endingValue=ending_value,
        maxDrawdownPct=mdd,
        avgOneWayTurnoverPerRebalance=0.0,
        monthlyEquivalentTurnoverPct=0.0,
    )

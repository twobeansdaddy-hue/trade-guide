#!/usr/bin/env python3
"""
MACD + trend filter research backtest.

This is a research-only validator. It does not change the application strategy
selector, API, database, or UI.

Compared variants:
- buy_and_hold: passive benchmark
- macd: buy on MACD(12, 26, 9) bullish cross and exit on bearish cross
- trend_macd: the same MACD rule, gated by close > SMA(50)
- trend_macd_breakout: trend_macd plus close > prior 20-day high
- trend_macd_breakout_atr: breakout variant plus a fixed 2 x ATR(14) close-based stop

Signals are calculated only from the close available at day i and executed at
the next available close (day i + 1). This is deliberately conservative about
look-ahead: the signal day close is never also used as the fill price.

The default files are the existing Yahoo daily caches for leveraged ETFs. They
are useful for validating the implementation mechanics, but they are not a
Track B ordinary-stock adoption sample. Pass ordinary-stock CSVs explicitly
before using this script as Track B evidence.
"""

import argparse
import csv
import json
import math
from datetime import date
from pathlib import Path


DEFAULT_DATASETS = [
    ("SOXL", "research/data/cache/soxl-daily-yahoo-2010-2026.csv"),
    ("TQQQ", "research/data/cache/tqqq-daily-yahoo-2010-2026.csv"),
    ("FAS", "research/data/cache/fas-daily-yahoo-2008-2026.csv"),
    ("TNA", "research/data/cache/tna-daily-yahoo-2008-2026.csv"),
]
DEFAULT_SPLITS = ["2010-01-01", "2015-01-01", "2020-01-01"]
WARMUP_DAYS = 60


def parse_date(value):
    return date.fromisoformat(value)


def load_rows(path):
    rows = []
    with open(path, newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source, delimiter=";")
        if not reader.fieldnames or "datetime" not in reader.fieldnames:
            raise ValueError(f"CSV에 datetime 컬럼이 없습니다: {path}")

        price_column = "adjclose" if "adjclose" in reader.fieldnames else "close"
        required = {"open", "high", "low", "close", price_column}
        missing = required.difference(reader.fieldnames)
        if missing:
            raise ValueError(f"CSV 필수 컬럼이 없습니다: {path}: {sorted(missing)}")

        for raw in reader:
            raw_close = float(raw["close"])
            adjusted_close = float(raw[price_column])
            adjustment_factor = adjusted_close / raw_close if raw_close else 1.0
            rows.append({
                "date": parse_date(raw["datetime"].strip()),
                # Adjust OHLC consistently with adjusted close so ATR does not
                # see artificial split discontinuities.
                "open": float(raw["open"]) * adjustment_factor,
                "high": float(raw["high"]) * adjustment_factor,
                "low": float(raw["low"]) * adjustment_factor,
                "close": adjusted_close,
            })

    rows.sort(key=lambda row: row["date"])
    if len(rows) < WARMUP_DAYS + 2:
        raise ValueError(f"백테스트에 필요한 데이터가 부족합니다: {path}")
    return rows, price_column


def sma(values, period):
    result = [None] * len(values)
    running = 0.0
    for index, value in enumerate(values):
        running += value
        if index >= period:
            running -= values[index - period]
        if index >= period - 1:
            result[index] = running / period
    return result


def ema(values, period):
    result = [None] * len(values)
    multiplier = 2.0 / (period + 1)
    if len(values) < period:
        return result

    seed_index = period - 1
    result[seed_index] = sum(values[:period]) / period
    for index in range(period, len(values)):
        result[index] = (
            (values[index] - result[index - 1]) * multiplier
            + result[index - 1]
        )
    return result


def indicator_series(rows):
    closes = [row["close"] for row in rows]
    fast = ema(closes, 12)
    slow = ema(closes, 26)
    macd = [
        fast[index] - slow[index]
        if fast[index] is not None and slow[index] is not None else None
        for index in range(len(rows))
    ]

    macd_values = [value for value in macd if value is not None]
    signal_values = ema(macd_values, 9)
    signal = [None] * len(rows)
    signal_offset = len(rows) - len(macd_values)
    for index, value in enumerate(signal_values):
        signal[index + signal_offset] = value

    true_range = []
    for index, row in enumerate(rows):
        if index == 0:
            true_range.append(row["high"] - row["low"])
            continue
        previous_close = rows[index - 1]["close"]
        true_range.append(max(
            row["high"] - row["low"],
            abs(row["high"] - previous_close),
            abs(row["low"] - previous_close),
        ))

    prior_high20 = [None] * len(rows)
    for index in range(20, len(rows)):
        prior_high20[index] = max(closes[index - 20:index])

    return {
        "sma50": sma(closes, 50),
        "macd": macd,
        "signal": signal,
        "atr14": sma(true_range, 14),
        "prior_high20": prior_high20,
    }


def crossed_up(previous_macd, previous_signal, current_macd, current_signal):
    return (
        previous_macd is not None
        and previous_signal is not None
        and current_macd is not None
        and current_signal is not None
        and previous_macd <= previous_signal
        and current_macd > current_signal
    )


def crossed_down(previous_macd, previous_signal, current_macd, current_signal):
    return (
        previous_macd is not None
        and previous_signal is not None
        and current_macd is not None
        and current_signal is not None
        and previous_macd >= previous_signal
        and current_macd < current_signal
    )


def apply_buy(cash, price, side_cost):
    shares = cash * (1.0 - side_cost) / price
    return 0.0, shares


def apply_sell(shares, price, side_cost):
    return shares * price * (1.0 - side_cost)


def summarize_curve(values):
    if not values:
        return 0.0, 0.0
    peak = values[0]
    max_drawdown = 0.0
    for value in values:
        peak = max(peak, value)
        if peak > 0:
            max_drawdown = max(max_drawdown, (peak - value) / peak)
    return values[-1], max_drawdown


def run_strategy(rows, indicators, variant, start_date, end_date, initial_cash, round_trip_bps):
    side_cost = round_trip_bps / 20000.0
    start_index = next(
        (index for index, row in enumerate(rows) if row["date"] >= start_date),
        None,
    )
    end_index = next(
        (index for index, row in enumerate(rows) if row["date"] > end_date),
        len(rows),
    ) - 1
    if start_index is None or end_index <= start_index:
        return None

    cash = initial_cash
    shares = 0.0
    entry_price = None
    pending_action = None
    trades = []
    curve = []

    # A strategy starts flat at each walk-forward segment. Indicators are
    # calculated from the preceding warm-up rows, but no position is carried in.
    for index in range(start_index, end_index + 1):
        price = rows[index]["close"]

        # Execute the previous day's close signal at today's close. This keeps
        # the signal and fill dates separate and prevents look-ahead valuation.
        if pending_action == "BUY" and shares == 0.0:
            cash, shares = apply_buy(cash, price, side_cost)
            entry_price = price
            trades.append({"date": rows[index]["date"].isoformat(), "action": "BUY"})
        elif pending_action == "SELL" and shares > 0.0:
            cash = apply_sell(shares, price, side_cost)
            shares = 0.0
            entry_price = None
            trades.append({"date": rows[index]["date"].isoformat(), "action": "SELL"})
        pending_action = None

        if variant == "buy_and_hold" and index == start_index:
            cash, shares = apply_buy(cash, price, side_cost)
            entry_price = price
            trades.append({"date": rows[index]["date"].isoformat(), "action": "BUY"})
        elif variant != "buy_and_hold" and index > start_index:
            previous = index - 1
            buy_signal = crossed_up(
                indicators["macd"][previous], indicators["signal"][previous],
                indicators["macd"][index], indicators["signal"][index],
            )
            sell_signal = crossed_down(
                indicators["macd"][previous], indicators["signal"][previous],
                indicators["macd"][index], indicators["signal"][index],
            )
            trend_filter = variant in {
                "trend_macd", "trend_macd_breakout", "trend_macd_breakout_atr"
            }
            breakout_filter = variant in {
                "trend_macd_breakout", "trend_macd_breakout_atr"
            }
            atr_filter = variant == "trend_macd_breakout_atr"

            if trend_filter:
                buy_signal = buy_signal and indicators["sma50"][index] is not None and price > indicators["sma50"][index]
                sell_signal = sell_signal or (
                    indicators["sma50"][index] is not None
                    and price < indicators["sma50"][index]
                )
            if breakout_filter:
                buy_signal = buy_signal and indicators["prior_high20"][index] is not None and price > indicators["prior_high20"][index]
            if atr_filter and shares > 0.0 and entry_price is not None and indicators["atr14"][index] is not None:
                sell_signal = sell_signal or price <= entry_price - 2.0 * indicators["atr14"][index]

            # Signal at close[i] is executed at close[i+1].
            if index < end_index:
                if buy_signal and shares == 0.0:
                    pending_action = "BUY"
                elif sell_signal and shares > 0.0:
                    pending_action = "SELL"

        curve.append(cash + shares * price)

    ending_value, max_drawdown = summarize_curve(curve)
    return {
        "variant": variant,
        "period_start": rows[start_index]["date"].isoformat(),
        "period_end": rows[end_index]["date"].isoformat(),
        "starting_value": round(initial_cash, 6),
        "ending_value": round(ending_value, 6),
        "return_pct": round((ending_value / initial_cash - 1.0) * 100.0, 4),
        "max_drawdown_pct": round(max_drawdown * 100.0, 4),
        "trade_count": len(trades),
        "round_trip_bps": round_trip_bps,
        "trades": trades,
    }


def period_ranges(rows, split_dates):
    dates = [row["date"] for row in rows]
    boundaries = sorted({parse_date(value) for value in split_dates})
    periods = []
    for index, start in enumerate(boundaries):
        end = boundaries[index + 1] if index + 1 < len(boundaries) else dates[-1]
        periods.append((start, end))
    return periods


def parse_dataset(value):
    if "=" not in value:
        raise argparse.ArgumentTypeError("데이터셋은 TICKER=CSV 형식이어야 합니다.")
    ticker, path = value.split("=", 1)
    if not ticker or not path:
        raise argparse.ArgumentTypeError("데이터셋은 TICKER=CSV 형식이어야 합니다.")
    return ticker.upper(), path


def main():
    parser = argparse.ArgumentParser(description="MACD 추세 확인 연구용 백테스트")
    parser.add_argument(
        "--data", action="append", type=parse_dataset,
        help="TICKER=CSV. 생략하면 기존 레버리지 ETF 캐시를 사용합니다.",
    )
    parser.add_argument("--json-out", default="research/data/cache/macd_trend_backtest_results.json")
    parser.add_argument("--report-out", default="research/reports/macd-trend-confirmation-validation.md")
    parser.add_argument("--split", action="append", default=DEFAULT_SPLITS)
    parser.add_argument("--initial-cash", type=float, default=100000.0)
    args = parser.parse_args()

    datasets = args.data or DEFAULT_DATASETS
    all_results = []
    data_manifest = []
    for ticker, path in datasets:
        rows, price_column = load_rows(path)
        indicators = indicator_series(rows)
        periods = period_ranges(rows, args.split)
        data_manifest.append({
            "ticker": ticker,
            "path": path,
            "rows": len(rows),
            "first_date": rows[0]["date"].isoformat(),
            "last_date": rows[-1]["date"].isoformat(),
            "price_column": price_column,
        })
        for period_start, period_end in periods:
            for cost in (20, 40, 57):
                for variant in (
                    "buy_and_hold", "macd", "trend_macd",
                    "trend_macd_breakout", "trend_macd_breakout_atr",
                ):
                    result = run_strategy(
                        rows, indicators, variant, period_start, period_end,
                        args.initial_cash, cost,
                    )
                    if result:
                        result.update({
                            "ticker": ticker,
                            "split_start": period_start.isoformat(),
                            "split_end": period_end.isoformat(),
                        })
                        all_results.append(result)

    payload = {
        "strategy": "macd-trend-confirmation-research",
        "parameters": {
            "macd": "EMA(12) - EMA(26), signal EMA(9)",
            "trend_filter": "close > SMA(50)",
            "breakout_filter": "close > prior 20-day high",
            "atr_stop": "entry price - 2 x ATR(14), checked at close",
            "execution": "next available close after signal close",
            "costs_round_trip_bps": [20, 40, 57],
            "splits": args.split,
            "lookahead_guard": True,
        },
        "datasets": data_manifest,
        "results": all_results,
        "limitations": [
            "기본 데이터셋은 Track B 일반 종목이 아니라 레버리지 ETF이며, 구현 검증용이다.",
            "이 결과만으로 Track B 운영 전략을 채택하지 않는다.",
            "실제 매수·매도 주문이나 손절 주문을 생성하지 않는다.",
        ],
    }

    json_path = Path(args.json_out)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report = render_report(payload)
    report_path = Path(args.report_out)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")
    print(json.dumps({"json": str(json_path), "report": str(report_path), "result_count": len(all_results)}, ensure_ascii=False))


def render_report(payload):
    lines = [
        "# MACD 추세 확인 검증 리포트",
        "",
        "> 이 문서는 연구용 결과다. Track B 운영 전략 채택이나 자동 매매를 의미하지 않는다.",
        "",
        "## 검증 규칙",
        "",
        "- MACD: EMA(12) - EMA(26), 시그널 EMA(9)",
        "- `MACD`: MACD선 상향 교차 진입, 하향 교차 청산",
        "- `trend_macd`: 위 규칙에 종가가 50일 SMA 위라는 조건을 추가",
        "- `trend_macd_breakout`: 위 규칙에 직전 20일 최고 종가 돌파 조건을 추가",
        "- `trend_macd_breakout_atr`: 진입가 - 2×ATR(14) 종가 기준 손실 제한을 추가",
        "- 신호는 해당 일 종가에서 계산하고 다음 거래일 종가에 체결한다.",
        "- 왕복 거래비용은 20/40/57bp를 각각 반영한다.",
        "",
        "## 데이터 한계",
        "",
        "이번 실행 데이터: " + ", ".join(dataset["ticker"] for dataset in payload["datasets"]) + ". 기본 실행의 SOXL·TQQQ·FAS·TNA는 Track B 일반 종목 표본이 아니며, AAPL·JPM·PG를 사용한 경우에도 표본 수와 생존편향 한계가 남는다. 어떤 실행도 운영 전략 채택 근거로 단독 해석하지 않는다.",
        "",
        "## 결과 요약",
        "",
        "| 티커 | 구간 | 비용 | 기준선 | MACD | 추세+MACD | +돌파 | +돌파+ATR | +돌파+ATR MDD |",
        "|---|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    grouped = {}
    for result in payload["results"]:
        key = (
            result["ticker"],
            result["split_start"],
            result["split_end"],
            result["round_trip_bps"],
        )
        grouped.setdefault(key, {})[result["variant"]] = result
    for (ticker, split_start, split_end, cost), variants in sorted(grouped.items()):
        benchmark = variants.get("buy_and_hold")
        macd = variants.get("macd")
        trend_macd = variants.get("trend_macd")
        breakout = variants.get("trend_macd_breakout")
        breakout_atr = variants.get("trend_macd_breakout_atr")
        if not benchmark or not macd or not trend_macd or not breakout or not breakout_atr:
            continue
        lines.append(
            f"| {ticker} | {split_start}~{split_end} | {cost}bp | "
            f"{benchmark['return_pct']:.2f}% | {macd['return_pct']:.2f}% | "
            f"{trend_macd['return_pct']:.2f}% | {breakout['return_pct']:.2f}% | "
            f"{breakout_atr['return_pct']:.2f}% | "
            f"{breakout_atr['max_drawdown_pct']:.2f}% |"
        )
    lines.extend([
        "",
        "## 판정",
        "",
        "AAPL·JPM·PG 3종, 3개 비중첩 구간, 20/40/57bp 비용에서 MACD 조합과 돌파·ATR 확장안 모두 매수 후 보유 기준선을 안정적으로 넘지 못했다. 일부 구간에서는 최대낙폭이 줄었지만 종목·구간별 방향이 달랐고, 고정 2×ATR이 일반 종목에 일관된 개선을 만들지도 않았다. 따라서 이번 검증 게이트는 **불충족**이며, 어떤 변형도 Track B의 `BUY`/`SELL` 운영 엔진으로 활성화하지 않는다.",
        "",
        "## 다음 단계",
        "",
        "1. point-in-time 한계를 줄일 수 있는 더 넓은 일반 종목 유니버스를 사전에 고정한다.",
        "2. 동일한 MACD 기간, 50일 추세 필터, 비용 가정으로 다시 비중첩 워크포워드를 실행한다.",
        "3. 수익률뿐 아니라 MDD, 거래 횟수, 비용 민감도, 구간별 부호 일관성을 확인한다.",
        "4. 게이트 통과 전에는 `TradingStrategy`·API·화면·주문 기능에 연결하지 않는다.",
        "",
    ])
    return "\n".join(lines)


if __name__ == "__main__":
    main()

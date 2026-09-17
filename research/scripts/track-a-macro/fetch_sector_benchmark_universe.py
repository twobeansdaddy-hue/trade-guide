#!/usr/bin/env python3
"""Track A 매크로 오버레이 여섯 번째 가설 - 섹터 상대강도 필터용 기초지수/벤치마크 수집.

각 레버리지 ETF의 비레버리지 기초지수 추종 ETF와 시장 벤치마크(SPY)를 수집한다:
SOXL->SOXX(반도체), TQQQ->QQQ(나스닥100), TNA->IWM(러셀2000), FAS->XLF(금융섹터).
`fetch_macro_universe.py`와 동일한 방식(Yahoo Finance v8 chart API, 주봉)으로
수집해 기존 `track-a-macro/universe_weekly.csv`에 병합한다.

실행: python3 research/scripts/track-a-macro/fetch_sector_benchmark_universe.py
"""
import csv
import datetime
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

TICKERS = {
    "SOXX": "iShares Semiconductor ETF (SOXL 기초)",
    "QQQ": "Invesco QQQ Trust (TQQQ 기초)",
    "IWM": "iShares Russell 2000 ETF (TNA 기초)",
    "XLF": "Financial Select Sector SPDR (FAS 기초)",
    "SPY": "SPDR S&P 500 ETF (시장 벤치마크)",
}

START_DATE = "2008-01-01"
UA = "Mozilla/5.0 (research script; trade-guide track-a-macro)"

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro"
)
RAW_DIR = os.path.join(BASE_DIR, "raw")
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")
MANIFEST_PATH = os.path.join(BASE_DIR, "sector_benchmark_manifest.json")


def epoch(date_str):
    return int(datetime.datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=datetime.timezone.utc).timestamp())


def fetch_one(yahoo_symbol, period1, period2, retries=3):
    encoded_symbol = urllib.parse.quote(yahoo_symbol, safe="")
    url = (
        f"https://query2.finance.yahoo.com/v8/finance/chart/{encoded_symbol}"
        f"?period1={period1}&period2={period2}&interval=1wk&events=div,splits"
    )
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=15) as resp:
                body = resp.read()
            return url, body, None
        except urllib.error.HTTPError as e:
            last_err = f"HTTPError {e.code}: {e.reason}"
        except urllib.error.URLError as e:
            last_err = f"URLError: {e.reason}"
        except Exception as e:  # noqa: BLE001
            last_err = f"{type(e).__name__}: {e}"
        time.sleep(2 * (attempt + 1))
    return url, None, last_err


def parse_chart_json(raw_bytes):
    d = json.loads(raw_bytes)
    chart = d.get("chart", {})
    if chart.get("error"):
        return None, f"Yahoo API error: {chart['error']}"
    result = chart.get("result")
    if not result:
        return None, "empty chart.result"
    r = result[0]
    ts = r.get("timestamp") or []
    quote = r["indicators"]["quote"][0]
    adj = r["indicators"].get("adjclose", [{}])[0].get("adjclose", [None] * len(ts))
    gm_offset = r["meta"].get("gmtoffset", -18000)

    rows = []
    for i in range(len(ts)):
        o, h, l, c, v = quote["open"][i], quote["high"][i], quote["low"][i], quote["close"][i], quote["volume"][i]
        ac = adj[i] if i < len(adj) else None
        if None in (o, h, l, c):
            continue
        local_dt = datetime.datetime.utcfromtimestamp(ts[i] + gm_offset)
        rows.append({
            "date": local_dt.strftime("%Y-%m-%d"),
            "open": o, "high": h, "low": l, "close": c, "volume": v,
            "adjclose": ac if ac is not None else c,
        })
    return rows, None


def main():
    os.makedirs(RAW_DIR, exist_ok=True)
    period1 = epoch(START_DATE)
    period2 = int(time.time())

    existing_rows = []
    with open(CSV_PATH, encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        existing_rows = list(reader)
    existing_tickers = {r["ticker"] for r in existing_rows}

    new_rows = []
    manifest = {"generatedBy": "research/scripts/track-a-macro/fetch_sector_benchmark_universe.py",
                "startDate": START_DATE, "tickers": {}}

    for symbol, name in TICKERS.items():
        if symbol in existing_tickers:
            print(f"[SKIP] {symbol} 이미 universe_weekly.csv에 존재")
            continue
        url, body, err = fetch_one(symbol, period1, period2)
        if err:
            print(f"[FAIL] {symbol}: {err}")
            continue
        rows, perr = parse_chart_json(body)
        if perr:
            print(f"[FAIL] {symbol}: {perr}")
            continue
        with open(os.path.join(RAW_DIR, f"{symbol}.json"), "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, indent=2)
        for row in rows:
            new_rows.append({"ticker": symbol, "datetime": row["date"], "close": row["adjclose"]})
        manifest["tickers"][symbol] = {"name": name, "weeklyCandleCount": len(rows),
                                        "firstDate": rows[0]["date"], "lastDate": rows[-1]["date"]}
        print(f"[OK] {symbol}: {len(rows)} weekly candles, {rows[0]['date']}~{rows[-1]['date']}")
        time.sleep(1)

    all_rows = existing_rows + new_rows
    all_rows.sort(key=lambda r: (r["ticker"], r["datetime"]))
    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["ticker", "datetime", "close"], delimiter=";")
        writer.writeheader()
        writer.writerows(all_rows)

    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"\n병합 후 universe_weekly.csv 총 {len(all_rows)}행")
    print(f"매니페스트 저장: {MANIFEST_PATH}")


if __name__ == "__main__":
    main()

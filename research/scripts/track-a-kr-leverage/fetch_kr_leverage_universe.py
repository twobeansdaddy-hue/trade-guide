#!/usr/bin/env python3
"""Track A-KR - 국내 레버리지 ETF 3종 주봉 데이터 수집.

목적
----
`research/reports/track-a-kr-leverage-etf-data-feasibility.md`에서 확인한 국내 레버리지
ETF 3종(KODEX 레버리지 122630, KODEX 코스닥150레버리지 233740, TIGER 200선물레버리지
267770)의 주봉 데이터를 Track C(KOSPI200)와 동일한 방식(Yahoo Finance 공개 v8 chart API,
`<코드>.KS`, 로그인/키 불필요)으로 수집한다.

실행: python3 research/scripts/track-a-kr-leverage/fetch_kr_leverage_universe.py

산출물
------
- research/data/cache/track-a-kr-leverage/raw/<CODE>.json
- research/data/cache/track-a-kr-leverage/universe_weekly.csv
- research/data/cache/track-a-kr-leverage/universe_manifest.json
"""
import csv
import datetime
import json
import os
import time
import urllib.error
import urllib.request

TICKERS = {
    "122630": "KODEX 레버리지",
    "233740": "KODEX 코스닥150레버리지",
    "267770": "TIGER 200선물레버리지",
}

START_DATE = "2010-01-01"
UA = "Mozilla/5.0 (research script; trade-guide track-a-kr-leverage)"

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-kr-leverage"
)
RAW_DIR = os.path.join(BASE_DIR, "raw")


def epoch(date_str):
    return int(datetime.datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=datetime.timezone.utc).timestamp())


def fetch_one(yahoo_symbol, period1, period2, retries=3):
    url = (
        f"https://query2.finance.yahoo.com/v8/finance/chart/{yahoo_symbol}"
        f"?period1={period1}&period2={period2}&interval=1wk&events=div,splits"
    )
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=20) as resp:
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
    gm_offset = r["meta"].get("gmtoffset", 32400)  # KST = UTC+9

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

    manifest = {"generatedBy": "research/scripts/track-a-kr-leverage/fetch_kr_leverage_universe.py",
                "startDate": START_DATE, "tickers": {}}
    csv_rows = []

    for code, name in TICKERS.items():
        symbol = f"{code}.KS"
        url, body, err = fetch_one(symbol, period1, period2)
        if err:
            manifest["tickers"][code] = {"name": name, "symbol": symbol, "status": "ERROR", "error": err}
            print(f"[FAIL] {symbol} ({name}): {err}")
            continue
        rows, perr = parse_chart_json(body)
        if perr:
            manifest["tickers"][code] = {"name": name, "symbol": symbol, "status": "ERROR", "error": perr}
            print(f"[FAIL] {symbol} ({name}): {perr}")
            continue
        with open(os.path.join(RAW_DIR, f"{code}.json"), "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, indent=2)
        manifest["tickers"][code] = {
            "name": name, "symbol": symbol, "status": "OK",
            "weeklyCandleCount": len(rows),
            "firstDate": rows[0]["date"] if rows else None,
            "lastDate": rows[-1]["date"] if rows else None,
        }
        for row in rows:
            csv_rows.append({"ticker": code, "datetime": row["date"], "close": row["adjclose"]})
        print(f"[OK] {symbol} ({name}): {len(rows)} weekly candles, {rows[0]['date']}~{rows[-1]['date']}")
        time.sleep(1)

    with open(os.path.join(BASE_DIR, "universe_manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    csv_rows.sort(key=lambda r: (r["ticker"], r["datetime"]))
    with open(os.path.join(BASE_DIR, "universe_weekly.csv"), "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["ticker", "datetime", "close"], delimiter=";")
        writer.writeheader()
        writer.writerows(csv_rows)

    print(f"\n매니페스트 저장: {os.path.join(BASE_DIR, 'universe_manifest.json')}")
    print(f"주봉 CSV 저장: {os.path.join(BASE_DIR, 'universe_weekly.csv')}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Track A 매크로 오버레이 - 미국 레버리지 ETF 4종 + VIX 주봉 수집.

목적
----
목표 (B) 매크로 가이드 엔진의 첫 가설(`research/reports/track-a-vix-entry-filter-hypothesis.md`)
검증에 쓸 원자료를 수집한다. Track A 프로덕션 대상(SOXL/TQQQ)과 기존 시장국면필터
확장 검증에 쓰인 TNA/FAS를 재사용하고, 새로 VIX(`^VIX`)를 추가한다.

기존 `track-a-market-regime-filter-tna-fas-extension.md`가 확보해 둔 캐시를 파일
권한 문제로 다시 읽을 수 없어(2026-09-17 확인), 동일 티커를 Yahoo Finance에서 새로
수집한다 - 결과값이 그 리포트의 49사이클과 정확히 일치하지 않을 수 있음(추가된 최근
데이터, 재수집 시점 차이)을 밝혀둔다.

실행: python3 research/scripts/track-a-macro/fetch_macro_universe.py
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
    "SOXL": "Direxion Daily Semiconductor Bull 3X",
    "TQQQ": "ProShares UltraPro QQQ",
    "TNA": "Direxion Daily Small Cap Bull 3X",
    "FAS": "Direxion Daily Financial Bull 3X",
}
MACRO_SYMBOLS = {
    "^VIX": "VIX",
}

START_DATE = "2008-01-01"
UA = "Mozilla/5.0 (research script; trade-guide track-a-macro)"

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-a-macro"
)
RAW_DIR = os.path.join(BASE_DIR, "raw")


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


def fetch_and_save(symbol, cache_key, period1, period2, csv_rows):
    url, body, err = fetch_one(symbol, period1, period2)
    if err:
        print(f"[FAIL] {symbol}: {err}")
        return None
    rows, perr = parse_chart_json(body)
    if perr:
        print(f"[FAIL] {symbol}: {perr}")
        return None
    with open(os.path.join(RAW_DIR, f"{cache_key}.json"), "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=2)
    for row in rows:
        csv_rows.append({"ticker": cache_key, "datetime": row["date"], "close": row["adjclose"]})
    print(f"[OK] {symbol}: {len(rows)} weekly candles, {rows[0]['date']}~{rows[-1]['date']}")
    time.sleep(1)
    return rows


def main():
    os.makedirs(RAW_DIR, exist_ok=True)
    period1 = epoch(START_DATE)
    period2 = int(time.time())

    csv_rows = []
    manifest = {"generatedBy": "research/scripts/track-a-macro/fetch_macro_universe.py",
                "startDate": START_DATE, "tickers": {}, "macro": {}}

    for symbol, name in TICKERS.items():
        rows = fetch_and_save(symbol, symbol, period1, period2, csv_rows)
        if rows:
            manifest["tickers"][symbol] = {"name": name, "weeklyCandleCount": len(rows),
                                            "firstDate": rows[0]["date"], "lastDate": rows[-1]["date"]}

    for symbol, name in MACRO_SYMBOLS.items():
        rows = fetch_and_save(symbol, name, period1, period2, csv_rows)
        if rows:
            manifest["macro"][name] = {"symbol": symbol, "weeklyCandleCount": len(rows),
                                        "firstDate": rows[0]["date"], "lastDate": rows[-1]["date"]}

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

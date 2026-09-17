#!/usr/bin/env python3
"""Track C(국내 주식) - KOSPI200 구성종목 주봉 데이터 수집.

목적
----
Track B(S&P100)에서 확립한 방식을 그대로 한국 시장에 재사용한다. 연구자가 임의로
고른 종목이 아니라 한국거래소(KRX)가 관리하는 공개 지수 KOSPI200의 구성종목 199개
(`kospi200_universe.py`, Wikipedia "KOSPI 200" 2026-09-16 접근)를 그대로 쓴다.

Toss증권 자체 시세 API(candles)는 일봉/분봉만 지원하고(주봉 없음), 앱 레벨 OAuth
클라이언트 자격 증명이 필요해 이 리서치 스크립트에서는 접근할 수 없다(사용자별 브로커
연결과 별개의 운영 자격 증명 - 리서치 목적으로 쓰지 않는다). 대신 Track B와 동일하게
Yahoo Finance 공개 v8 chart API(로그인/키 불필요)를 쓴다 - 005930.KS(삼성전자)로
사전 테스트해 2015년부터 KRW 기준 주봉 559개가 정상 조회됨을 확인했다.

실행: python3 research/scripts/track-c-kospi200/fetch_universe_kospi200.py

산출물
------
- research/data/cache/track-c-kospi200/raw/<CODE>.json
- research/data/cache/track-c-kospi200/universe_weekly.csv
- research/data/cache/track-c-kospi200/universe_manifest.json
"""
import json
import time
import urllib.request
import urllib.error
import datetime
import os
import csv
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kospi200_universe import KOSPI200_TICKERS  # noqa: E402

START_DATE = "2015-01-01"
UA = "Mozilla/5.0 (research script; trade-guide track-c-kospi200)"

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-c-kospi200"
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
    downloaded_at = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    manifest = {
        "provider": "Yahoo Finance (query2.finance.yahoo.com v8 chart API)",
        "interval": "1wk",
        "requestedStart": START_DATE,
        "downloadedAt": downloaded_at,
        "pointInTimeUniverse": False,
        "universeSelectionCriteria": (
            "KOSPI200 오늘 시점 구성종목 199개(원 200개 중 표준 KRX 6자리 숫자 코드가 아닌 "
            "1종목 제외). 한국거래소(KRX)가 관리하는 공개 지수의 구성종목을 그대로 사용 "
            "(연구자 임의 선택 없음). 오늘 시점 구성이며 과거 구성과 다를 수 있음(생존편향)."
        ),
        "universeSource": {
            "title": "KOSPI 200 (Wikipedia)",
            "url": "https://en.wikipedia.org/wiki/KOSPI_200",
            "accessed": "2026-09-16",
        },
        "tickers": {},
    }

    ok, failed = 0, []
    for code, name in KOSPI200_TICKERS:
        yahoo_symbol = f"{code}.KS"
        url, raw_bytes, err = fetch_one(yahoo_symbol, period1, period2)
        entry = {"name": name, "requestUrl": url}
        if err is not None:
            entry["status"] = "FAILED"
            entry["error"] = err
            manifest["tickers"][code] = entry
            failed.append(code)
            print(f"[FAIL] {code} ({name}): {err}")
            time.sleep(1.05)
            continue

        raw_path = os.path.join(RAW_DIR, f"{code}.json")
        with open(raw_path, "wb") as f:
            f.write(raw_bytes)

        rows, parse_err = parse_chart_json(raw_bytes)
        if parse_err is not None:
            entry["status"] = "FAILED"
            entry["error"] = parse_err
            manifest["tickers"][code] = entry
            failed.append(code)
            print(f"[FAIL] {code} ({name}): {parse_err}")
            time.sleep(1.05)
            continue

        entry["status"] = "OK"
        entry["rowCount"] = len(rows)
        entry["startDate"] = rows[0]["date"] if rows else None
        entry["endDate"] = rows[-1]["date"] if rows else None
        entry["rawFile"] = os.path.relpath(raw_path, BASE_DIR)
        manifest["tickers"][code] = entry
        manifest.setdefault("_rows", {})[code] = rows
        ok += 1
        print(f"[OK] {code} ({name}): {len(rows)} rows, {entry['startDate']} ~ {entry['endDate']}")
        time.sleep(1.05)

    all_rows = manifest.pop("_rows", {})
    manifest["summary"] = {
        "universeSize": len(KOSPI200_TICKERS),
        "succeeded": ok,
        "failed": failed,
        "failedCount": len(failed),
    }

    manifest_path = os.path.join(BASE_DIR, "universe_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    csv_path = os.path.join(BASE_DIR, "universe_weekly.csv")
    with open(csv_path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["ticker", "datetime", "open", "high", "low", "close", "volume", "adjclose"])
        for code, _ in KOSPI200_TICKERS:
            for row in all_rows.get(code, []):
                w.writerow([code, row["date"], row["open"], row["high"], row["low"],
                            row["close"], row["volume"], row["adjclose"]])

    print(f"\n완료: {ok}/{len(KOSPI200_TICKERS)} 성공, {len(failed)} 실패 {failed}")
    print(f"manifest -> {manifest_path}")
    print(f"csv -> {csv_path}")


if __name__ == "__main__":
    main()

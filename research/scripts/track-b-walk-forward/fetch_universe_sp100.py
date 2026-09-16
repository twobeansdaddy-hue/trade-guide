#!/usr/bin/env python3
"""Track B 횡단면 모멘텀 - 유니버스 확장(S&P100) 데이터 수집.

목적
----
`research/reports/track-b-walk-forward-data-feasibility.md`의 "다음 단계 권고" 1번
("유니버스 확장이 선행돼야 한다... 최소한 오늘 시점 S&P500 500종목 전체 또는 그 중 유동성
상위 100~200종목으로 확장")을 이행한다. 진짜 시점별(point-in-time) S&P500 편입 이력은
여전히 이 프로젝트가 접근할 수 없는 유료 데이터(CRSP/Compustat류)라 확보하지 못했다.
대신 DJIA(30종목, 가격가중)보다 더 넓고 시가총액가중인 공개 지수인 **S&P100(OEX)**의
오늘 시점 구성 전체(101개 티커, GOOGL/GOOG 두 종류 포함)로 표본을 넓힌다. S&P Dow Jones
Indices 위원회가 관리하는 지수이므로 이번에도 연구자가 개별 종목을 고르지 않는다.

DJIA 30종목 원본 데이터(`track-b-walk-forward/`)는 건드리지 않고, 별도 디렉터리
(`track-b-walk-forward-sp100/`)에 새로 수집한다 — 기존 결과와 비교하기 위해 원본을 보존한다.

고정 연구 유니버스와 선정 기준 (실행 전 확정, 결과를 보고 종목을 바꾸지 않음)
--------------------------------------------------------------------------
- 유니버스: S&P100(OEX) 지수 오늘 시점 구성종목 전체(101개 - GOOGL/GOOG 클래스 A/C 포함).
- 선정 기준: S&P Dow Jones Indices 위원회가 관리하는 시가총액가중 공개 지수의 구성 목록을
  그대로 사용. DJIA(30, 가격가중) 대비 3배 이상 넓은 표본이며 시가총액가중이라는 점에서
  S&P500 실행 방식에 더 가깝다.
- 소스: Wikipedia "S&P 100" 문서의 구성종목 표, 2026-09-16 접근(WebFetch로 조회).
- 이 목록도 **오늘 시점** 구성이며, 2015년 당시 구성과 다르다(예: PLTR·UBER·GEV는 IPO/
  스핀오프가 2019년 이후라 2015~2018년 데이터 자체가 없다; HONA는 2026년 Honeywell
  분할로 신규 상장돼 그 이전 이력이 없다). `pointInTimeUniverse=false`이며, DJIA 실행과
  동일한 방향(오늘 시점 우량주 소급 적용)의 생존 편향이 있다. 다만 이번 유니버스는 DJIA보다
  넓어 상위 20% 진입 시 보유 종목 수가 6개(DJIA)에서 약 20개로 늘어나 개별 종목 집중 위험은
  줄어든다.
- 이력이 짧은 종목(PLTR/UBER/GEV/HONA 등)은 quality-check로 전역 제외하지 않는다 -
  `momentum_engine.formation_return()`이 52+4주 이력이 쌓이기 전까지는 해당 시점 랭킹에서만
  자동으로 제외하고(None 반환), 이력이 쌓인 이후 시점부터는 정상 포함한다. 이는 기존 DJIA
  실행에서 NVDA/SHW/GOOGL 같은 최근 신규 편입 종목을 처리한 것과 동일한 정책이다.

BRK.B는 Yahoo Finance 티커 표기가 BRK-B(하이픈)라 아래 UNIVERSE에서 그렇게 적었다. 다른
모든 티커는 통상 표기 그대로다.

수집 대상 기간: 2015-01-01 ~ 스크립트 실행 시각 (주봉, interval=1wk) - DJIA 실행과 동일.

실행 방법
--------
    python3 research/scripts/track-b-walk-forward/fetch_universe_sp100.py

산출물
------
- research/data/cache/track-b-walk-forward-sp100/raw/<TICKER>.json
- research/data/cache/track-b-walk-forward-sp100/universe_weekly.csv
- research/data/cache/track-b-walk-forward-sp100/universe_manifest.json
"""
import json
import time
import urllib.request
import urllib.error
import datetime
import os
import csv

# S&P100(OEX) 오늘 시점 구성종목 101개 (Wikipedia "S&P 100", 2026-09-16 접근).
# BRK.B -> Yahoo 표기 BRK-B로 변환. 나머지는 표기 그대로.
UNIVERSE = [
    ("AAPL", "Apple"), ("ABBV", "AbbVie"), ("ABT", "Abbott Laboratories"),
    ("ACN", "Accenture"), ("ADBE", "Adobe"), ("AMAT", "Applied Materials"),
    ("AMD", "Advanced Micro Devices"), ("AMGN", "Amgen"), ("AMT", "American Tower"),
    ("AMZN", "Amazon"), ("AVGO", "Broadcom"), ("AXP", "American Express"),
    ("BA", "Boeing"), ("BAC", "Bank of America"), ("BKNG", "Booking Holdings"),
    ("BLK", "BlackRock"), ("BMY", "Bristol Myers Squibb"), ("BNY", "BNY Mellon"),
    ("BRK-B", "Berkshire Hathaway"), ("C", "Citigroup"), ("CAT", "Caterpillar"),
    ("CL", "Colgate-Palmolive"), ("CMCSA", "Comcast"), ("COF", "Capital One"),
    ("COP", "ConocoPhillips"), ("COST", "Costco"), ("CRM", "Salesforce"),
    ("CSCO", "Cisco"), ("CVS", "CVS Health"), ("CVX", "Chevron"),
    ("DE", "Deere & Company"), ("DHR", "Danaher"), ("DIS", "Walt Disney"),
    ("DUK", "Duke Energy"), ("EMR", "Emerson Electric"), ("FDX", "FedEx"),
    ("GD", "General Dynamics"), ("GE", "GE Aerospace"), ("GEV", "GE Vernova"),
    ("GILD", "Gilead Sciences"), ("GM", "General Motors"), ("GOOG", "Alphabet (Class C)"),
    ("GOOGL", "Alphabet (Class A)"), ("GS", "Goldman Sachs"), ("HD", "Home Depot"),
    ("HONA", "Honeywell Aerospace"), ("IBM", "IBM"), ("INTC", "Intel"),
    ("INTU", "Intuit"), ("ISRG", "Intuitive Surgical"), ("JNJ", "Johnson & Johnson"),
    ("JPM", "JPMorgan Chase"), ("KO", "Coca-Cola"), ("LIN", "Linde"),
    ("LLY", "Eli Lilly"), ("LMT", "Lockheed Martin"), ("LOW", "Lowe's"),
    ("LRCX", "Lam Research"), ("MA", "Mastercard"), ("MCD", "McDonald's"),
    ("MDLZ", "Mondelez International"), ("MDT", "Medtronic"), ("META", "Meta Platforms"),
    ("MMM", "3M"), ("MO", "Altria"), ("MRK", "Merck"), ("MS", "Morgan Stanley"),
    ("MSFT", "Microsoft"), ("MU", "Micron Technology"), ("NEE", "NextEra Energy"),
    ("NFLX", "Netflix"), ("NKE", "Nike"), ("NOW", "ServiceNow"), ("NVDA", "Nvidia"),
    ("ORCL", "Oracle"), ("PEP", "PepsiCo"), ("PFE", "Pfizer"),
    ("PG", "Procter & Gamble"), ("PLTR", "Palantir Technologies"),
    ("PM", "Philip Morris International"), ("QCOM", "Qualcomm"), ("RTX", "RTX Corporation"),
    ("SBUX", "Starbucks"), ("SCHW", "Charles Schwab"), ("SO", "Southern Company"),
    ("SPG", "Simon Property Group"), ("T", "AT&T"), ("TMO", "Thermo Fisher Scientific"),
    ("TMUS", "T-Mobile US"), ("TSLA", "Tesla"), ("TXN", "Texas Instruments"),
    ("UBER", "Uber"), ("UNH", "UnitedHealth Group"), ("UNP", "Union Pacific"),
    ("UPS", "United Parcel Service"), ("USB", "U.S. Bancorp"), ("V", "Visa"),
    ("VZ", "Verizon"), ("WFC", "Wells Fargo"), ("WMT", "Walmart"), ("XOM", "ExxonMobil"),
]

START_DATE = "2015-01-01"
UA = "Mozilla/5.0 (research script; trade-guide track-b-momentum-universe-expansion)"

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-b-walk-forward-sp100"
)
RAW_DIR = os.path.join(BASE_DIR, "raw")


def epoch(date_str):
    return int(datetime.datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=datetime.timezone.utc).timestamp())


def fetch_one(ticker, period1, period2, retries=3):
    url = (
        f"https://query2.finance.yahoo.com/v8/finance/chart/{ticker}"
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
        except Exception as e:  # noqa: BLE001 - record any unexpected fetch failure
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
    gm_offset = r["meta"].get("gmtoffset", -14400)

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
            "S&P100(OEX) 오늘 시점 구성종목 전체(101개, GOOGL/GOOG 포함). S&P Dow Jones "
            "Indices가 관리하는 시가총액가중 공개 지수의 위원회 선정 결과를 그대로 사용 "
            "(연구자 임의 선택 없음). DJIA(30종목)보다 넓은 표본으로 유니버스를 확장하기 위해 "
            "track-b-walk-forward-data-feasibility.md의 권고에 따라 선정. 오늘 시점 구성이며 "
            "2015년 당시 구성과 다를 수 있음(생존 편향 가능, PLTR/UBER/GEV/HONA는 이력 자체가 "
            "2015년 시작보다 늦음)."
        ),
        "universeSource": {
            "title": "S&P 100 (Wikipedia)",
            "url": "https://en.wikipedia.org/wiki/S%26P_100",
            "accessed": "2026-09-16",
        },
        "tickers": {},
    }

    ok, failed = 0, []
    for ticker, name in UNIVERSE:
        url, raw_bytes, err = fetch_one(ticker, period1, period2)
        entry = {"name": name, "requestUrl": url}
        if err is not None:
            entry["status"] = "FAILED"
            entry["error"] = err
            manifest["tickers"][ticker] = entry
            failed.append(ticker)
            print(f"[FAIL] {ticker}: {err}")
            continue

        raw_path = os.path.join(RAW_DIR, f"{ticker}.json")
        with open(raw_path, "wb") as f:
            f.write(raw_bytes)

        rows, parse_err = parse_chart_json(raw_bytes)
        if parse_err is not None:
            entry["status"] = "FAILED"
            entry["error"] = parse_err
            manifest["tickers"][ticker] = entry
            failed.append(ticker)
            print(f"[FAIL] {ticker}: {parse_err}")
            continue

        entry["status"] = "OK"
        entry["rowCount"] = len(rows)
        entry["startDate"] = rows[0]["date"] if rows else None
        entry["endDate"] = rows[-1]["date"] if rows else None
        entry["rawFile"] = os.path.relpath(raw_path, BASE_DIR)
        manifest["tickers"][ticker] = entry
        manifest.setdefault("_rows", {})[ticker] = rows
        ok += 1
        print(f"[OK] {ticker}: {len(rows)} rows, {entry['startDate']} ~ {entry['endDate']}")
        time.sleep(1.0)  # 공개 API에 대한 예의상 지연 (rate limit 회피)

    all_rows = manifest.pop("_rows", {})
    manifest["summary"] = {
        "universeSize": len(UNIVERSE),
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
        for ticker, _ in UNIVERSE:
            for row in all_rows.get(ticker, []):
                w.writerow([ticker, row["date"], row["open"], row["high"], row["low"],
                            row["close"], row["volume"], row["adjclose"]])

    print(f"\n완료: {ok}/{len(UNIVERSE)} 성공, {len(failed)} 실패 {failed}")
    print(f"manifest -> {manifest_path}")
    print(f"csv -> {csv_path}")


if __name__ == "__main__":
    main()

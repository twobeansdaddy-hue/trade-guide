#!/usr/bin/env python3
"""Track B 실데이터 워크포워드 1단계 - 고정 연구 유니버스 주봉 데이터 수집.

목적
----
`docs/agent-tasks/track-b-walk-forward-data-feasibility.md`가 요구하는 "고정 연구
유니버스"의 공개 Yahoo Finance 주봉 가격 이력을 재현 가능하게 수집한다. 표준 라이브러리
(urllib, json, csv)만 사용하며 새 의존성을 추가하지 않는다.

고정 연구 유니버스와 선정 기준 (실행 전 확정, 결과를 보고 종목을 바꾸지 않음)
--------------------------------------------------------------------------
- 유니버스: 다우존스 산업평균지수(DJIA) 현재 30개 구성종목 전체.
- 선정 기준: 시가총액 상위 + 섹터 대표성을 S&P Dow Jones Indices 위원회가 이미 적용해
  관리하는 공개 지수(가격가중, 30종목, 위원회가 명시적으로 업종 균형을 고려해 구성)를
  그대로 사용한다. 연구자가 개별 종목을 고르지 않으므로 편의적 선택(cherry-picking) 위험이
  없다.
- 소스: Wikipedia "List of Dow Jones Industrial Average companies" 문서의 구성종목
  표(위키 원문의 {{NYSE link|...}}/{{NASDAQ link|...}} 템플릿에서 추출), 2026-09-11
  접근. 이 표 자체가 S&P Global 공식 페이지와 State Street SPDR DIA ETF 보유종목을
  인용한다.
- 이 목록은 **오늘 시점**의 DJIA 구성이며, 2015년 당시 실제 구성과 다르다(예: NVDA/SHW/
  GOOGL은 비교적 최근에 편입됨). 즉 point-in-time 유니버스가 아니라
  `pointInTimeUniverse=false`이며, 생존 편향(당시 지수에서 이미 퇴출된 종목이 표본에서
  빠짐)이 존재할 수 있음을 결과 해석 시 반드시 감안해야 한다. 이 한계를 회피하지 않고
  그대로 기록한다.

수집 대상 기간: 2015-01-01 ~ 스크립트 실행 시각 (주봉, interval=1wk)

실행 방법
--------
    python3 research/scripts/track-b-walk-forward/fetch_universe.py

산출물
------
- research/data/cache/track-b-walk-forward/raw/<TICKER>.json : Yahoo v8 chart API 원본 응답
- research/data/cache/track-b-walk-forward/universe_weekly.csv : 정규화된 통합 주봉
  (ticker;datetime;open;high;low;close;volume;adjclose)
- research/data/cache/track-b-walk-forward/universe_manifest.json : 종목별 수집 결과
  메타데이터(요청 URL, 수집 시각, 행 수, 시작/종료일, 실패·결측 사유)
"""
import json
import time
import urllib.request
import urllib.error
import datetime
import os
import csv

UNIVERSE = [
    ("MMM", "3M"), ("GOOGL", "Alphabet"), ("AXP", "American Express"),
    ("AMGN", "Amgen"), ("AMZN", "Amazon"), ("AAPL", "Apple"), ("BA", "Boeing"),
    ("CAT", "Caterpillar"), ("CVX", "Chevron"), ("CSCO", "Cisco"),
    ("KO", "Coca-Cola"), ("DIS", "Disney"), ("GS", "Goldman Sachs"),
    ("HD", "Home Depot"), ("HON", "Honeywell"), ("IBM", "IBM"),
    ("JNJ", "Johnson & Johnson"), ("JPM", "JPMorgan Chase"),
    ("MCD", "McDonald's"), ("MRK", "Merck"), ("MSFT", "Microsoft"),
    ("NKE", "Nike"), ("NVDA", "Nvidia"), ("PG", "Procter & Gamble"),
    ("CRM", "Salesforce"), ("SHW", "Sherwin-Williams"), ("TRV", "Travelers"),
    ("UNH", "UnitedHealth"), ("V", "Visa"), ("WMT", "Walmart"),
]

START_DATE = "2015-01-01"
UA = "Mozilla/5.0 (research script; trade-guide track-b-walk-forward-data-feasibility)"

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-b-walk-forward"
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
    """Yahoo v8 chart JSON -> (rows, error). rows: list of dict per completed week."""
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
            "다우존스 산업평균지수(DJIA) 현재 30개 구성종목 전체. S&P Dow Jones Indices가 "
            "관리하는 공개 지수의 위원회 선정 결과를 그대로 사용(연구자 임의 선택 없음). "
            "오늘 시점 구성이며 2015년 당시 구성과 다를 수 있음(생존 편향 가능)."
        ),
        "universeSource": {
            "title": "List of Dow Jones Industrial Average companies (Wikipedia)",
            "url": "https://en.wikipedia.org/wiki/List_of_Dow_Jones_Industrial_Average_companies",
            "accessed": "2026-09-11",
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

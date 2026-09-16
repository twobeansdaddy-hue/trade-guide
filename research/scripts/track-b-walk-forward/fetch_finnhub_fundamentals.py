#!/usr/bin/env python3
"""Track B PEG/PER 밸류에이션 검증 - Finnhub 무료 티어 펀더멘털 시계열 수집.

목적
----
`research/reports/track-b-fundamental-data-provider-evaluation.md`가 실제 API
테스트로 확인한 대로, Finnhub 무료 티어(`/stock/metric?metric=all`)는 PE/PB/ROE/
마진/부채비율 등의 연간·분기 시계열(`series.annual`, `series.quarterly`)을
제공한다. 이 스크립트는 S&P100 101종목(가격 데이터를 이미 수집한
`track-b-walk-forward-sp100/universe_weekly.csv`와 동일한 유니버스) 전체에 대해
이 시계열을 수집한다.

API 키는 사용자가 `.env`(git-ignored)에 `FINNHUB_API_KEY=...`로 저장했다. 이
스크립트는 그 값을 읽어 요청에만 쓰고, 코드·로그·출력 어디에도 실제 키 값을
출력하지 않는다.

무료 티어 제한: 분당 60회. 종목당 1회 호출(`metric=all`)이므로 101회 호출에
안전 마진을 두고 초당 1회(분당 60회)로 제한한다.

실행: python3 research/scripts/track-b-walk-forward/fetch_finnhub_fundamentals.py

산출물
------
- research/data/cache/track-b-walk-forward-sp100/finnhub_fundamentals/<TICKER>.json
  (원본 API 응답 그대로 저장)
- research/data/cache/track-b-walk-forward-sp100/finnhub_fundamentals_manifest.json
  (종목별 수집 결과 메타데이터 - 성공/실패, 시계열 길이, 사용 가능한 series 키)
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
ENV_PATH = os.path.join(REPO_ROOT, ".env")
DATA_DIR = os.path.join(REPO_ROOT, "research", "data", "cache", "track-b-walk-forward-sp100")
OUT_DIR = os.path.join(DATA_DIR, "finnhub_fundamentals")
MANIFEST_PATH = os.path.join(DATA_DIR, "finnhub_fundamentals_manifest.json")

# fetch_universe_sp100.py의 UNIVERSE와 동일한 티커 목록(이름만 제외, 순서 동일).
TICKERS = [
    "AAPL", "ABBV", "ABT", "ACN", "ADBE", "AMAT", "AMD", "AMGN", "AMT", "AMZN", "AVGO",
    "AXP", "BA", "BAC", "BKNG", "BLK", "BMY", "BNY", "BRK-B", "C", "CAT", "CL", "CMCSA",
    "COF", "COP", "COST", "CRM", "CSCO", "CVS", "CVX", "DE", "DHR", "DIS", "DUK", "EMR",
    "FDX", "GD", "GE", "GEV", "GILD", "GM", "GOOG", "GOOGL", "GS", "HD", "HONA", "IBM",
    "INTC", "INTU", "ISRG", "JNJ", "JPM", "KO", "LIN", "LLY", "LMT", "LOW", "LRCX", "MA",
    "MCD", "MDLZ", "MDT", "META", "MMM", "MO", "MRK", "MS", "MSFT", "MU", "NEE", "NFLX",
    "NKE", "NOW", "NVDA", "ORCL", "PEP", "PFE", "PG", "PLTR", "PM", "QCOM", "RTX", "SBUX",
    "SCHW", "SO", "SPG", "T", "TMO", "TMUS", "TSLA", "TXN", "UBER", "UNH", "UNP", "UPS",
    "USB", "V", "VZ", "WFC", "WMT", "XOM",
]

# Finnhub은 클래스주 티커 표기가 하이픈이 아니라 마침표(BRK.B)인 경우가 많다 - 실패 시
# 이 대체 표기로 1회 재시도한다.
FINNHUB_TICKER_ALIASES = {"BRK-B": "BRK.B"}


def load_api_key():
    if not os.path.exists(ENV_PATH):
        raise RuntimeError(f".env 파일이 없습니다: {ENV_PATH}")
    for line in open(ENV_PATH, encoding="utf-8"):
        if line.startswith("FINNHUB_API_KEY="):
            key = line.strip().split("=", 1)[1]
            if key:
                return key
    raise RuntimeError("FINNHUB_API_KEY가 .env에 설정되어 있지 않습니다.")


def fetch_one(symbol, api_key, retries=3):
    url = "https://finnhub.io/api/v1/stock/metric?" + urllib.parse.urlencode({
        "symbol": symbol, "metric": "all", "token": api_key,
    })
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "trade-guide research script"})
            with urllib.request.urlopen(req, timeout=20) as resp:
                body = resp.read()
            data = json.loads(body)
            return data, None
        except urllib.error.HTTPError as e:
            last_err = f"HTTPError {e.code}: {e.reason}"
            if e.code == 429:
                time.sleep(5 * (attempt + 1))
                continue
        except Exception as e:  # noqa: BLE001
            last_err = f"{type(e).__name__}: {e}"
        time.sleep(1.5 * (attempt + 1))
    return None, last_err


def main():
    api_key = load_api_key()
    os.makedirs(OUT_DIR, exist_ok=True)

    manifest = {"provider": "Finnhub (finnhub.io/api/v1/stock/metric, metric=all)", "tickers": {}}
    ok, failed = 0, []

    for ticker in TICKERS:
        symbol_to_try = ticker
        data, err = fetch_one(symbol_to_try, api_key)
        if (data is None or not data.get("metric")) and ticker in FINNHUB_TICKER_ALIASES:
            alias = FINNHUB_TICKER_ALIASES[ticker]
            data2, err2 = fetch_one(alias, api_key)
            if data2 is not None and data2.get("metric"):
                data, err, symbol_to_try = data2, None, alias

        entry = {"symbolUsed": symbol_to_try}
        if data is None or not data.get("metric"):
            entry["status"] = "FAILED"
            entry["error"] = err or "empty metric"
            manifest["tickers"][ticker] = entry
            failed.append(ticker)
            print(f"[FAIL] {ticker}: {entry['error']}")
            time.sleep(1.05)
            continue

        out_path = os.path.join(OUT_DIR, f"{ticker}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        series = data.get("series", {})
        entry["status"] = "OK"
        entry["metricFieldCount"] = len(data.get("metric", {}))
        entry["annualPeCount"] = len(series.get("annual", {}).get("pe", []))
        entry["annualEpsCount"] = len(series.get("annual", {}).get("eps", []))
        entry["quarterlyPeCount"] = len(series.get("quarterly", {}).get("peTTM", []))
        entry["quarterlyEpsCount"] = len(series.get("quarterly", {}).get("eps", []))
        manifest["tickers"][ticker] = entry
        ok += 1
        print(f"[OK] {ticker} ({symbol_to_try}): annual pe={entry['annualPeCount']}, "
              f"quarterly peTTM={entry['quarterlyPeCount']}, quarterly eps={entry['quarterlyEpsCount']}")
        time.sleep(1.05)  # 분당 60회 제한에 안전 마진

    manifest["summary"] = {"universeSize": len(TICKERS), "succeeded": ok, "failed": failed, "failedCount": len(failed)}
    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"\n완료: {ok}/{len(TICKERS)} 성공, {len(failed)} 실패 {failed}")
    print(f"manifest -> {MANIFEST_PATH}")


if __name__ == "__main__":
    main()

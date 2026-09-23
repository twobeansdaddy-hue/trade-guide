#!/usr/bin/env python3
"""분봉 이력 깊이와 세션 범위 소표본 실측 (읽기 전용, 연구용).

작업 계약: docs/agent-tasks/engine-d1-data-coverage-20260923.md

- 토스 `/api/v1/candles?interval=1m`에 여러 과거 시점을 `before`로 넣어 몇 번만 조회한다.
  전체 이력을 넘기지 않으므로 호출 수가 적다(종목당 기준일 수 + 세션 표본 3회).
- 선택적으로 Twelve Data `time_series?interval=1min`을 같은 기준일로 조회한다(TWELVE_DATA_API_KEY가 있을 때만).
- 자격 증명은 환경변수(TOSS_CLIENT_ID, TOSS_CLIENT_SECRET, TWELVE_DATA_API_KEY)에서만 읽는다.
  키·토큰·응답 원문은 출력하지 않는다. 주문·계좌 API는 호출하지 않는다.
- 주의: 토스는 클라이언트당 유효 토큰이 1개라 새 토큰 발급 시 실행 중인 앱의 토큰이 무효화된다. 앱을 끈 상태에서 실행한다.

사용법:
    python3 research/scripts/engine-data-probe/minute_depth_probe.py SOXL 005930
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

TOSS_BASE_URL = os.environ.get("TOSS_BASE_URL", "https://openapi.tossinvest.com")
TWELVE_DATA_BASE_URL = os.environ.get("TWELVE_DATA_BASE_URL", "https://api.twelvedata.com")
KST = ZoneInfo("Asia/Seoul")
NEW_YORK = ZoneInfo("America/New_York")
TIMEOUT_SECONDS = 20
# 토스 공개 FAQ의 이력 시작일(미국 2021-11-30, 국내 2022-11-23) 전후와 이후 연도별 기준일.
PROBE_DATES = ["2021-11-15", "2021-12-15", "2022-06-15", "2022-12-15", "2023-06-15",
               "2024-06-14", "2025-06-13", "2026-06-15"]


def http_json(request):
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return error.code, None
    except (urllib.error.URLError, ValueError) as error:
        return type(error).__name__, None


def issue_toss_token(client_id, client_secret):
    body = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": client_id,
                                   "client_secret": client_secret}).encode()
    status, payload = http_json(urllib.request.Request(
        TOSS_BASE_URL + "/oauth2/token", data=body, method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded"}))
    token = payload.get("access_token") if isinstance(payload, dict) else None
    if not token:
        sys.exit(f"토스 토큰 발급 실패: {status}")
    return token


def toss_minutes(token, symbol, before, count):
    params = {"symbol": symbol, "interval": "1m", "count": count}
    if before:
        params["before"] = before
    status, payload = http_json(urllib.request.Request(
        TOSS_BASE_URL + "/api/v1/candles?" + urllib.parse.urlencode(params),
        headers={"Authorization": "Bearer " + token}))
    result = (payload or {}).get("result") or {}
    stamps = [parse(c.get("timestamp")) for c in (result.get("candles") or []) if c]
    return status, [s for s in stamps if s], result.get("nextBefore")


def twelve_minutes(api_key, symbol, day):
    params = {"symbol": symbol, "interval": "1min", "start_date": f"{day} 00:00:00",
              "end_date": f"{day} 23:59:00", "outputsize": 5000, "order": "asc"}
    status, payload = http_json(urllib.request.Request(
        TWELVE_DATA_BASE_URL + "/time_series?" + urllib.parse.urlencode(params),
        headers={"Authorization": "apikey " + api_key}))
    if isinstance(payload, dict) and payload.get("status") == "error":
        return f"error code {payload.get('code')}", []
    values = (payload or {}).get("values") or []
    return status, [v.get("datetime") for v in values if v.get("datetime")]


def parse(text):
    if not text:
        return None
    try:
        return datetime.fromisoformat(str(text).replace("Z", "+00:00"))
    except ValueError:
        return None


def is_kr(symbol):
    return symbol[:1].isdigit()


def depth(token, symbol):
    print(f"\n## {symbol} 토스 1분봉 이력 깊이 (before 기준일마다 5개 요청)")
    print("| before 기준 | HTTP | 반환 수 | 가장 최근 봉(KST) | 가장 오래된 봉(KST) | 기준일과의 간격(일) |")
    print("|---|---|---|---|---|---|")
    for day in PROBE_DATES:
        before = datetime.fromisoformat(day).replace(hour=23, minute=59, tzinfo=KST).isoformat()
        status, stamps, _ = toss_minutes(token, symbol, before, 5)
        if stamps:
            newest, oldest = max(stamps), min(stamps)
            gap = (datetime.fromisoformat(day).date() - newest.astimezone(KST).date()).days
            print(f"| {day} | {status} | {len(stamps)} | {newest.astimezone(KST):%Y-%m-%d %H:%M} | "
                  f"{oldest.astimezone(KST):%Y-%m-%d %H:%M} | {gap} |")
        else:
            print(f"| {day} | {status} | 0 | - | - | - |")
        time.sleep(0.2)


def sessions(token, symbol):
    """최근 600개 봉의 시각 분포로 어떤 세션 시간대가 포함되는지 본다."""
    stamps, before = [], None
    for _ in range(3):
        status, page, next_before = toss_minutes(token, symbol, before, 200)
        stamps += page
        if status != 200 or not next_before:
            break
        before = next_before
        time.sleep(0.2)
    zone = KST if is_kr(symbol) else NEW_YORK
    label = "KST" if is_kr(symbol) else "ET"
    hours = Counter(stamp.astimezone(zone).hour for stamp in stamps)
    days = sorted({stamp.astimezone(zone).date() for stamp in stamps})
    print(f"\n## {symbol} 최근 1분봉 {len(stamps)}개의 시간대 분포 ({label}, 봉 종료 시각 기준)")
    print(f"- 포함 날짜: {', '.join(str(day) for day in days)}")
    print("- 시(hour)별 봉 수: " + ", ".join(f"{hour:02d}시 {hours[hour]}" for hour in sorted(hours)))


def twelve(api_key, symbol):
    print(f"\n## {symbol} Twelve Data 1분봉 (기준일 하루치)")
    print("| 날짜 | 상태 | 봉 수 | 첫 봉 | 마지막 봉 |")
    print("|---|---|---|---|---|")
    for day in PROBE_DATES:
        status, stamps = twelve_minutes(api_key, symbol, day)
        print(f"| {day} | {status} | {len(stamps)} | {stamps[0] if stamps else '-'} | {stamps[-1] if stamps else '-'} |")
        time.sleep(8)  # 무료 등급 분당 호출 한도를 넘지 않게 한다.


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("symbols", nargs="+")
    args = parser.parse_args()
    client_id, client_secret = os.environ.get("TOSS_CLIENT_ID"), os.environ.get("TOSS_CLIENT_SECRET")
    if not client_id or not client_secret:
        sys.exit("환경변수 TOSS_CLIENT_ID, TOSS_CLIENT_SECRET를 먼저 설정하세요.")
    now = datetime.now(timezone.utc)
    print(f"# 분봉 이력 소표본 실측 ({now.astimezone(KST):%Y-%m-%d %H:%M} KST / {now.astimezone(NEW_YORK):%Y-%m-%d %H:%M} ET)")
    token = issue_toss_token(client_id, client_secret)
    for symbol in args.symbols:
        depth(token, symbol.upper())
        sessions(token, symbol.upper())
    api_key = os.environ.get("TWELVE_DATA_API_KEY")
    if api_key:
        for symbol in [s.upper() for s in args.symbols if not is_kr(s)]:
            twelve(api_key, symbol)
    else:
        print("\n(TWELVE_DATA_API_KEY가 없어 Twelve Data 1분봉 실측은 건너뜀)")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""ALFRED 최초 발표값과 현재 최신값 비교 소표본 실측 (읽기 전용, 연구용).

작업 계약: docs/agent-tasks/engine-d1-data-coverage-20260923.md

- FRED API `series/observations`를 지표마다 두 번 호출한다.
  (1) 기본값: 오늘 기준 최신값, (2) `output_type=4`: 각 관측의 최초 발표값과 최초 발표일(realtime_start).
- 발표 지연(최초 발표일 − 관측 기간 시작일)과 개정 크기(최신값 vs 최초 발표값)를 요약한다.
- API 키는 환경변수 FRED_API_KEY에서만 읽는다. 키가 URL 쿼리에 들어가므로 URL·응답 원문은 출력하지 않는다.

사용법:
    python3 research/scripts/engine-data-probe/alfred_vintage_probe.py PAYEMS CPIAUCSL UNRATE GDP DGS10 BAA10Y
"""

import argparse
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime
from zoneinfo import ZoneInfo

FRED_BASE = os.environ.get("FRED_BASE_URL", "https://api.stlouisfed.org")
TIMEOUT_SECONDS = 30
OBSERVATION_START = "2015-01-01"


def observations(api_key, series_id, first_release):
    params = {"series_id": series_id, "api_key": api_key, "file_type": "json",
              "observation_start": OBSERVATION_START}
    if first_release:
        params.update({"realtime_start": "1776-07-04", "realtime_end": "9999-12-31", "output_type": 4})
    request = urllib.request.Request(FRED_BASE + "/fred/series/observations?" + urllib.parse.urlencode(params))
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            payload = json.loads(response.read().decode())
            return response.status, payload.get("observations") or []
    except urllib.error.HTTPError as error:
        return error.code, []
    except (urllib.error.URLError, ValueError) as error:
        return type(error).__name__, []
    finally:
        time.sleep(0.6)  # FRED 분당 호출 한도보다 충분히 느리게


def number(text):
    try:
        return float(text)
    except (TypeError, ValueError):
        return None  # FRED 결측 표기 '.'


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1))))]


def summarize(api_key, series_id):
    latest_status, latest = observations(api_key, series_id, first_release=False)
    first_status, first = observations(api_key, series_id, first_release=True)
    print(f"\n## {series_id} (최신 HTTP {latest_status}, 최초 발표 HTTP {first_status}, 관측 {OBSERVATION_START} 이후)")
    if not latest or not first:
        print("- 데이터 없음(키·지표 ID·ALFRED 이력 여부 확인 필요)")
        return

    latest_by_date = {row["date"]: number(row.get("value")) for row in latest}
    lags, revisions, revised, compared, largest = [], [], 0, 0, None
    for row in first:
        first_value, current = number(row.get("value")), latest_by_date.get(row["date"])
        if row.get("realtime_start"):
            lags.append((date.fromisoformat(row["realtime_start"]) - date.fromisoformat(row["date"])).days)
        if first_value is None or current is None:
            continue
        compared += 1
        if current != first_value:
            revised += 1
            change = (current - first_value) / abs(first_value) * 100 if first_value else None
            if change is not None:
                revisions.append(abs(change))
                if largest is None or abs(change) > abs(largest[1]):
                    largest = (row["date"], change, first_value, current, row.get("realtime_start"))

    print(f"- 최신 관측 {len(latest)}개, 최초 발표 관측 {len(first)}개, 비교 가능 {compared}개")
    if lags:
        print(f"- 발표 지연(최초 발표일 − 관측 기간 시작일, 일): 최소 {min(lags)}, 중앙값 {statistics.median(lags):g}, "
              f"90% {percentile(lags, 0.9)}, 최대 {max(lags)}")
    if compared:
        print(f"- 최초 발표 후 값이 바뀐 관측: {revised}/{compared} ({revised / compared:.0%})")
    if revisions:
        print(f"- 개정 크기(|최신/최초 − 1|, %): 중앙값 {statistics.median(revisions):.3f}, "
              f"90% {percentile(revisions, 0.9):.3f}, 최대 {max(revisions):.3f}")
    if largest:
        obs_date, change, first_value, current, released = largest
        print(f"- 최대 개정: {obs_date} 최초 {first_value:g}({released} 발표) → 최신 {current:g} ({change:+.3f}%)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("series", nargs="+")
    args = parser.parse_args()
    api_key = os.environ.get("FRED_API_KEY", "").strip()
    if not api_key:
        sys.exit("환경변수 FRED_API_KEY를 먼저 설정하세요.")
    print(f"# ALFRED 최초 발표값 vs 최신값 실측 ({datetime.now(ZoneInfo('Asia/Seoul')):%Y-%m-%d %H:%M} KST)")
    for series_id in args.series:
        summarize(api_key, series_id.upper())


if __name__ == "__main__":
    main()

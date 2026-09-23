#!/usr/bin/env python3
"""SEC 공시 접수 시점과 고정 지연 근사(60·75일) 비교 소표본 실측 (읽기 전용, 연구용).

작업 계약: docs/agent-tasks/engine-d1-data-coverage-20260923.md

- SEC 공개 API만 호출한다: company_tickers.json(티커→CIK), submissions(공시 접수 시각),
  companyfacts(XBRL 재무값과 접수일). 종목당 약 3회 호출.
- SEC 요구에 따라 식별용 User-Agent를 보낸다. 값은 환경변수 SEC_USER_AGENT에서만 읽고 출력하지 않는다.
- 재무값은 공개 데이터라 요약 통계와 재작성 예시만 출력한다.

사용법:
    python3 research/scripts/engine-data-probe/sec_filing_lag_probe.py AAPL MSFT
"""

import argparse
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import date, datetime
from zoneinfo import ZoneInfo

SEC_WWW = os.environ.get("SEC_WWW_BASE", "https://www.sec.gov")
SEC_DATA = os.environ.get("SEC_DATA_BASE", "https://data.sec.gov")
NEW_YORK = ZoneInfo("America/New_York")
TIMEOUT_SECONDS = 30
FIXED_LAGS = (60, 75)  # 기존 연구의 근사: PEG 검증 60일, 품질·저변동성 검증 75일(연간)
CONCEPTS = ("NetIncomeLoss", "RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues")


def get_json(url, user_agent):
    request = urllib.request.Request(url, headers={"User-Agent": user_agent, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return error.code, None
    except (urllib.error.URLError, ValueError) as error:
        return type(error).__name__, None
    finally:
        time.sleep(0.2)  # SEC 공정 이용 한도(초당 10회)보다 충분히 느리게


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1))))]


def period_kind(start, end):
    days = (date.fromisoformat(end) - date.fromisoformat(start)).days
    if 350 <= days <= 380:
        return "연간"
    if 80 <= days <= 100:
        return "분기"
    return None


def facts_summary(facts):
    """회계기간별 최초 접수일로 지연을 재고, 같은 기간 값이 나중 공시에서 바뀐 사례를 센다."""
    lags = defaultdict(list)
    restated = []
    for concept in CONCEPTS:
        entries = ((facts.get("us-gaap") or {}).get(concept) or {}).get("units", {}).get("USD", [])
        by_period = defaultdict(list)
        for entry in entries:
            if entry.get("form") not in ("10-K", "10-Q", "10-K/A", "10-Q/A") or not entry.get("start"):
                continue
            kind = period_kind(entry["start"], entry["end"])
            if kind:
                by_period[(kind, entry["start"], entry["end"])].append(entry)
        for (kind, start, end), group in by_period.items():
            group.sort(key=lambda entry: entry["filed"])
            first = group[0]
            lags[(concept, kind)].append((date.fromisoformat(first["filed"]) - date.fromisoformat(end)).days)
            values = {entry["val"] for entry in group}
            if len(values) > 1:
                later = next(entry for entry in group if entry["val"] != first["val"])
                change = (later["val"] - first["val"]) / abs(first["val"]) * 100 if first["val"] else None
                restated.append((concept, kind, end, first["filed"], later["filed"], later.get("form"), change))
    return lags, restated


def acceptance_summary(submissions):
    recent = (submissions.get("filings") or {}).get("recent") or {}
    rows = zip(recent.get("form", []), recent.get("reportDate", []), recent.get("filingDate", []),
               recent.get("acceptanceDateTime", []))
    buckets, lags, span = defaultdict(int), defaultdict(list), []
    for form, report_date, filing_date, accepted in rows:
        if form not in ("10-K", "10-Q") or not report_date or not accepted:
            continue
        span.append(filing_date)
        lags[form].append((date.fromisoformat(filing_date) - date.fromisoformat(report_date)).days)
        # SEC가 'Z'로 표기하는 접수 시각을 UTC로 해석한 결과다(해석 가정은 보고서에 기록).
        local = datetime.fromisoformat(accepted.replace("Z", "+00:00")).astimezone(NEW_YORK)
        minutes = local.hour * 60 + local.minute
        bucket = "개장 전(<09:30 ET)" if minutes < 570 else "장중(09:30~16:00)" if minutes < 960 else "장 마감 후(>=16:00)"
        buckets[bucket] += 1
    return buckets, lags, (min(span), max(span)) if span else None


def print_lag_table(title, lag_map):
    print(f"\n{title}")
    print("| 구분 | 표본 | 최소 | 중앙값 | 90% | 최대 | >60일 | >75일 |")
    print("|---|---|---|---|---|---|---|---|")
    for key, values in sorted(lag_map.items()):
        if not values:
            continue
        label = " / ".join(key) if isinstance(key, tuple) else key
        over = {limit: sum(v > limit for v in values) for limit in FIXED_LAGS}
        print(f"| {label} | {len(values)} | {min(values)} | {statistics.median(values):g} | "
              f"{percentile(values, 0.9)} | {max(values)} | {over[60]} ({over[60] / len(values):.0%}) | "
              f"{over[75]} ({over[75] / len(values):.0%}) |")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tickers", nargs="+")
    args = parser.parse_args()
    user_agent = os.environ.get("SEC_USER_AGENT", "").strip()
    if not user_agent:
        sys.exit("환경변수 SEC_USER_AGENT(요청자 이름과 연락 이메일)를 먼저 설정하세요.")

    print(f"# SEC 공시 접수 시점 실측 ({datetime.now(NEW_YORK):%Y-%m-%d %H:%M} ET)")
    status, tickers = get_json(f"{SEC_WWW}/files/company_tickers.json", user_agent)
    if not isinstance(tickers, dict):
        sys.exit(f"티커 목록 조회 실패: {status}")
    cik_by_ticker = {row["ticker"].upper(): int(row["cik_str"]) for row in tickers.values()}

    for ticker in (t.upper() for t in args.tickers):
        cik = cik_by_ticker.get(ticker)
        if cik is None:
            print(f"\n## {ticker}: CIK를 찾지 못함")
            continue
        print(f"\n## {ticker} (CIK {cik})")
        status, submissions = get_json(f"{SEC_DATA}/submissions/CIK{cik:010d}.json", user_agent)
        if isinstance(submissions, dict):
            print(f"- SEC 신고자 구분: {submissions.get('category') or '기록 없음'}")
            buckets, filing_lags, span = acceptance_summary(submissions)
            print(f"- submissions(최근 목록) 10-K·10-Q 범위: {span[0]} ~ {span[1]}" if span else "- submissions: 10-K·10-Q 없음")
            print("- 접수 시각 분포(ET): " + ", ".join(f"{k} {v}건" for k, v in sorted(buckets.items())))
            print_lag_table("### 공시 기준 지연(접수일 − 보고 기간 종료일, 일)", filing_lags)
        else:
            print(f"- submissions 조회 실패: {status}")

        status, facts = get_json(f"{SEC_DATA}/api/xbrl/companyfacts/CIK{cik:010d}.json", user_agent)
        if not isinstance(facts, dict):
            print(f"- companyfacts 조회 실패: {status}")
            continue
        lags, restated = facts_summary(facts.get("facts") or {})
        print_lag_table("### XBRL 재무값 기준 지연(기간별 최초 접수일 − 기간 종료일, 일)", lags)
        print(f"\n### 같은 기간 값이 나중 공시에서 바뀐 사례: {len(restated)}건")
        for concept, kind, end, first_filed, later_filed, form, change in sorted(restated, key=lambda r: r[2])[-5:]:
            change_text = f"{change:+.2f}%" if change is not None else "-"
            print(f"- {concept} {kind} {end}: 최초 {first_filed} → {later_filed}({form}) 변화 {change_text}")


if __name__ == "__main__":
    main()

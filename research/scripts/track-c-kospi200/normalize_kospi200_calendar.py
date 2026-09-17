#!/usr/bin/env python3
"""Track C - KOSPI200 캘린더 정합성 보정 (Track B의 S&P100 버그와 동일한 원인).

`research/scripts/track-b-walk-forward/normalize_sp100_calendar.py`에서 이미 확인한
것과 동일한 문제다: 2015년 이전부터 상장된 종목은 Yahoo 주봉이 목요일 기준으로
앵커링되지만, 그 이후 상장·스핀오프된 종목(KakaoBank, LG Energy Solution 등)은
실제 상장일 기준으로 앵커링돼 대부분 월요일 기준이다. KOSPI200은 최근 상장 종목
비중이 S&P100보다 훨씬 높아(199종목 중 42종목, 21%) 이 보정이 더욱 중요하다.

월요일 앵커 종목의 날짜에 +3일을 더하면 정확히 같은 주의 목요일이 되고, 이 목요일은
항상 2015-01-01(목요일) 기준 7일 배수 격자 위에 있다 - S&P100 스크립트와 동일한
방식으로 검증했다.

실행: python3 research/scripts/track-c-kospi200/normalize_kospi200_calendar.py
(fetch_universe_kospi200.py 실행 직후, 백테스트 실행 전에 1회)
"""
import csv
import datetime
import os

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-c-kospi200"
)
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")
REFERENCE_THURSDAY = datetime.date(2015, 1, 1)


def main():
    rows = []
    with open(CSV_PATH, encoding="utf-8") as f:
        reader = csv.reader(f, delimiter=";")
        header = next(reader)
        for row in reader:
            rows.append(row)

    ticker_idx, date_idx = header.index("ticker"), header.index("datetime")

    # 종목별 지배적 요일을 계산해 목요일이 아닌 종목만 보정한다.
    from collections import Counter
    weekday_by_ticker = {}
    for row in rows:
        d = datetime.date.fromisoformat(row[date_idx])
        weekday_by_ticker.setdefault(row[ticker_idx], Counter())[d.weekday()] += 1
    monday_anchored = {
        t for t, counts in weekday_by_ticker.items() if counts.most_common(1)[0][0] == 0
    }

    shifted_count = 0
    bad_alignment = []
    for row in rows:
        ticker = row[ticker_idx]
        if ticker not in monday_anchored:
            continue
        d = datetime.date.fromisoformat(row[date_idx])
        if d.weekday() != 0:
            continue
        shifted = d + datetime.timedelta(days=3)
        offset_weeks = (shifted - REFERENCE_THURSDAY).days / 7
        if offset_weeks != int(offset_weeks):
            bad_alignment.append((ticker, row[date_idx], shifted.isoformat()))
            continue
        row[date_idx] = shifted.isoformat()
        shifted_count += 1

    if bad_alignment:
        raise RuntimeError(f"목요일 격자에 정렬되지 않는 날짜 발견 - 자동 보정 중단: {bad_alignment}")

    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(header)
        w.writerows(rows)

    print(f"보정 완료: {len(monday_anchored)}개 종목, {shifted_count}개 행 날짜를 월요일->목요일(+3일)로 재정렬")
    print(f"파일: {CSV_PATH}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""S&P100 유니버스 확장 실행 중 발견한 캘린더 정합성 버그 수정.

문제
----
`fetch_universe_sp100.py`로 수집한 101종목 중 2019년 이후 상장/분할된 4종목
(PLTR 2020-09-28 IPO, UBER 2019-05-06 IPO, GEV 2024-03-25 스핀오프,
HONA 2026-06-15 스핀오프)은 Yahoo Finance 주봉(`interval=1wk`) 캔들이 **월요일**을
기준으로 앵커링된다. 반면 2015년부터 이미 상장돼 있던 나머지 97종목은 **목요일** 기준으로
앵커링된다(둘 다 Yahoo가 각 종목의 실제 이력 시작일을 기준으로 7일 간격 앵커를 잡기
때문 - 2015-01-01이 마침 목요일이라 오래된 종목들은 전부 목요일로 정렬됨).

`momentum_engine.py`(DJIA 30종목 검증에도 쓰인, 수정하지 않는 기존 코어)는 모든 종목이
동일한 날짜 문자열로 정렬된 공통 주간 캘린더를 공유한다고 가정한다. 이 가정이 깨지자,
리밸런싱 시점(공통 캘린더에서 뽑은 날짜)이 월요일-그리드 종목에만 존재하는 날에 걸릴 때
나머지 97종목 전부가 그 시점에 "MISSING_HISTORY"로 제외되고, 후보가 단 1종목만 남아
포트폴리오 전액이 그 1종목에 쏠리는 리밸런싱이 반복됐다. 이 결과 24회 남짓의 리밸런싱을
거치며 수익률이 10^13~10^16%까지 폭주했다 - 이는 전략 성과가 아니라 명백한 데이터 정합성
버그이며, 이 상태의 결과는 어떤 형태로도 리포트에 반영하지 않는다.

수정 방법 (기계적 정렬 보정, 전략 파라미터나 결과를 보고 조정한 것이 아님)
----------------------------------------------------------------------
월요일 앵커 종목의 각 날짜에 +3일을 더하면 정확히 같은 주의 목요일이 되고, 이 목요일은
항상 2015-01-01(목요일) 기준 7일 배수 격자 위에 있다(검증: (2020-10-01 - 2015-01-01).days
% 7 == 0). 따라서 4개 이상치 종목의 날짜만 +3일 이동해 97종목의 목요일 격자에 정확히
재정렬한다. OHLCV 값 자체는 전혀 바꾸지 않는다 - 그 주의 실제 가격 그대로이며, 그 가격에
붙이는 날짜 라벨만 공통 격자에 맞춘다.

실행: python3 research/scripts/track-b-walk-forward/normalize_sp100_calendar.py
(fetch_universe_sp100.py 실행 직후, run_walk_forward_sp100.py 실행 전에 1회 실행)

산출물: universe_weekly.csv를 제자리에서 덮어쓴다(원본 raw/<TICKER>.json은 그대로 유지 -
언제든 이 정규화 이전 상태로 재현 가능).
"""
import csv
import datetime
import os

BASE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-b-walk-forward-sp100"
)
CSV_PATH = os.path.join(BASE_DIR, "universe_weekly.csv")

MONDAY_ANCHORED_TICKERS = {"PLTR", "UBER", "GEV", "HONA"}
REFERENCE_THURSDAY = datetime.date(2015, 1, 1)


def main():
    rows = []
    with open(CSV_PATH, encoding="utf-8") as f:
        reader = csv.reader(f, delimiter=";")
        header = next(reader)
        for row in reader:
            rows.append(row)

    ticker_idx, date_idx = header.index("ticker"), header.index("datetime")
    shifted_count = 0
    bad_alignment = []
    for row in rows:
        ticker = row[ticker_idx]
        if ticker not in MONDAY_ANCHORED_TICKERS:
            continue
        d = datetime.date.fromisoformat(row[date_idx])
        if d.weekday() != 0:  # 이미 월요일이 아니면(예: 첫 주 등) 건드리지 않는다
            continue
        shifted = d + datetime.timedelta(days=3)
        offset_weeks = (shifted - REFERENCE_THURSDAY).days / 7
        if offset_weeks != int(offset_weeks):
            bad_alignment.append((ticker, row[date_idx], shifted.isoformat()))
            continue
        row[date_idx] = shifted.isoformat()
        shifted_count += 1

    if bad_alignment:
        raise RuntimeError(
            f"목요일 격자에 정렬되지 않는 날짜 발견 - 자동 보정 중단: {bad_alignment}"
        )

    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(header)
        w.writerows(rows)

    print(f"보정 완료: {shifted_count}개 행 날짜를 월요일->목요일(+3일)로 재정렬")
    print(f"파일: {CSV_PATH}")


if __name__ == "__main__":
    main()

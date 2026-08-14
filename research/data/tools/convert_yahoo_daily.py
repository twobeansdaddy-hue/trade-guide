#!/usr/bin/env python3
"""Yahoo Finance v8 chart API(query2.finance.yahoo.com) raw JSON -> 프로젝트 CSV 캐시 포맷 변환.

이 스크립트는 research/data/cache/ 아래 일봉 원본 JSON(예: soxl-daily-yahoo-raw.json)을
기존 주봉 캐시와 동일한 CSV 포맷(datetime;open;high;low;close;volume;adjclose)으로 변환한다.

- adjclose는 Yahoo의 분할+배당 조정 종가다(주봉 캐시와 동일 관례).
- OHLC 중 하나라도 null인 행(비거래일/데이터 공백)은 제외한다.
- datetime은 거래소 로컬 날짜(미국 동부, meta.gmtoffset 기준)로 변환한다.

원본 조회 예 (2026-08-14 기준, curl 사용, WebFetch로도 접근 가능):
  curl -s -A "Mozilla/5.0" \
    "https://query2.finance.yahoo.com/v8/finance/chart/SOXL?period1=<firstTradeDate>&period2=<now>&interval=1d&events=div,splits" \
    -o research/data/cache/soxl-daily-yahoo-raw.json
"""
import json
import sys
import datetime


def convert(in_path, out_path):
    with open(in_path, encoding="utf-8") as f:
        d = json.load(f)
    r = d["chart"]["result"][0]
    ts = r["timestamp"]
    q = r["indicators"]["quote"][0]
    adj = r["indicators"].get("adjclose", [{}])[0].get("adjclose", [None] * len(ts))
    gm_offset = r["meta"].get("gmtoffset", -14400)

    rows = []
    for i in range(len(ts)):
        o, h, l, c, v = q["open"][i], q["high"][i], q["low"][i], q["close"][i], q["volume"][i]
        ac = adj[i] if i < len(adj) else None
        if None in (o, h, l, c):
            continue
        local_dt = datetime.datetime.utcfromtimestamp(ts[i] + gm_offset)
        date_str = local_dt.strftime("%Y-%m-%d")
        rows.append((date_str, o, h, l, c, v, ac if ac is not None else c))

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("datetime;open;high;low;close;volume;adjclose\n")
        for row in rows:
            f.write(f"{row[0]};{row[1]};{row[2]};{row[3]};{row[4]};{row[5]};{row[6]}\n")
    print(f"{in_path} -> {out_path}: {len(rows)} rows, {rows[0][0]} ~ {rows[-1][0]}")


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])

#!/usr/bin/env python3
"""
market_regime_filter_backtest.py

목적
----
Track A(SOXL/TQQQ) 주봉 10/40 이동평균 교차 후보 진입(BUY) 정책(0~4주 컷오프,
research/reports/track-a-entry-delay-cutoff-review.md에서 채택)에 "SPY 40주 이동평균 위/아래"라는
시장 국면 필터를 추가로 적용했을 때, 진입 사이클 수·수익률·MDD·워크포워드 결과가 어떻게 달라지는지
검증한다. 손절(포지션 종료) 규칙이 아니라 "신규 진입 자체를 필터링"하는 규칙이다.

사전 고정 가설 (세션 지시, 결과를 보기 전에 고정)
----------------------------------------------
SPY의 마지막 완료 주봉 종가가 SPY 40주 이동평균 위에 있을 때만 Track A 신규 BUY를 허용한다.
- Track A 자산의 기존 BUY 조건(trend==ABOVE_LONG_AVERAGE && weeksSinceCross<=4)은 그대로 유지 —
  이 필터는 추가 조건이지 대체가 아니다.
- weeksSinceCross 0~4주 동안 매주 SPY 조건을 재평가하며, 두 조건이 동시에 성립하는 첫 주에 진입한다.
- weeksSinceCross>4가 되면(Track A 자산 조건 자체가 이미 WATCH로 전환) 그 사이클엔 더 이상 진입하지
  않는다 — SPY가 그 뒤 회복돼도 이 사이클에서는 진입 없음.

기존 도구와의 관계
------------------
entry_delay_cycle_backtest.py의 load_candles/compute_signals/find_cycles/sma_series/
mdd_close_and_intraweek 함수를 그대로 import해서 재사용한다(그 파일 자체는 수정하지 않음).
Track A 사이클(교차일→청산일) 목록은 그 스크립트가 계산하는 것과 동일한 로직으로 이 스크립트가
직접 재계산한다 — 기존 리포트에 이미 검증된 사이클 집합(20개: soxl_twelvedata 3 + soxl_yahoo 8 +
tqqq_yahoo 9)과 반드시 동일해야 하며, 이 스크립트 실행 시 그 사실을 대조해 출력한다.

베이스라인과 후보
------------------
- 베이스라인: weeksSinceCross<=4 정책, 시장 국면 필터 없음. 진입 시점은 항상 교차 당주(delay=0)다
  (trend==ABOVE_LONG_AVERAGE && weeksSinceCross==0<=4 조건이 교차 당주에 이미 성립하므로, 기존 엔진은
  그 주에 즉시 BUY를 낸다 — "기존 구현이 실제로 하는 그대로").
- 후보: 위 베이스라인 조건에 더해, weeksSinceCross==k(k=0..4)인 매 주마다 "SPY 마지막 완료 주봉
  종가 > SPY 40주 이동평균"인지 재평가한다. Track A 자산의 trend가 ABOVE_LONG_AVERAGE로 유지되는
  동안(= 그 사이클이 아직 청산되지 않은 동안)만 k를 늘려가며, 두 조건이 처음 동시에 성립하는 주에
  진입한다. 5주차 이후는 Track A 자체 정책상 이미 WATCH이므로 확인하지 않는다. 0~4주 동안 한 번도
  SPY 조건이 성립하지 않으면 그 사이클은 "진입 없음(제외)"으로 기록한다.

집계 방식 (전체 사이클 평균 vs 진입 사이클만 평균)
--------------------------------------------------
필터로 제외된 사이클은 실제로는 포지션을 전혀 보유하지 않으므로(현금 대기), "전체 사이클 평균"
지표에서는 수익률 0%, MDD 0%로 취급해 필터 적용 후 실제 전략의 경제적 결과를 반영한다. 별도로
"진입한 사이클만 평균"도 함께 낸다(그 사이클들 자체의 트레이드 품질 비교용).

사용 예
-------
python3 research/data/tools/market_regime_filter_backtest.py --today 2026-08-18 \
    --json-out research/data/cache/market_regime_filter_backtest_results.json
"""
import argparse
import csv
import json
import statistics
import sys
import os
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from entry_delay_cycle_backtest import (  # noqa: E402
    load_candles,
    compute_signals,
    find_cycles,
    sma_series,
    mdd_close_and_intraweek,
)

MAX_DELAY = 4

DATASETS = [
    {
        "key": "soxl_twelvedata",
        "ticker": "SOXL",
        "csv": "research/data/cache/soxl-weekly-twelvedata-2021-2026.csv",
        "close_col": "close",
        "low_col": "low",
        "raw_close_col": None,
        "label": "SOXL / Twelve Data / interval=1week / adjustment=splits",
    },
    {
        "key": "soxl_yahoo",
        "ticker": "SOXL",
        "csv": "research/data/cache/soxl-weekly-yahoo-2010-2026.csv",
        "close_col": "adjclose",
        "low_col": "low",
        "raw_close_col": "close",
        "label": "SOXL / Yahoo Finance / weekly / Adjusted Close",
    },
    {
        "key": "tqqq_yahoo",
        "ticker": "TQQQ",
        "csv": "research/data/cache/tqqq-weekly-yahoo-2010-2026.csv",
        "close_col": "adjclose",
        "low_col": "low",
        "raw_close_col": "close",
        "label": "TQQQ / Yahoo Finance / weekly / Adjusted Close",
    },
]

SPY_CSV = "research/data/cache/spy-weekly-yahoo-1993-2026.csv"

# 사전 고정 워크포워드 분할 시점 (세션 지시, 결과를 보기 전에 고정)
WALK_FORWARD_SPLITS = ["2018-01-01", "2019-01-01", "2020-01-01", "2021-01-01"]

# "큰 수익 기회를 놓쳤는가"의 임계치: 베이스라인(무필터) 20개 사이클 수익률 분포의 상위 사분위수(Q3)
# 이상. 임계치는 필터 결과를 보기 전에, 베이스라인 수익률 분포만으로 정했다(사후 조정 아님).
BIG_OPPORTUNITY_QUANTILE = 0.75


def parse_date(s):
    return date.fromisoformat(s)


def load_spy_above_40w(path, today):
    """SPY 주봉을 로드하고 각 완결 주봉 날짜 -> (해당 주 종가 > 40주 SMA) 불리언 맵을 만든다."""
    candles, dropped = load_candles(path, ";", "adjclose", "low", today, raw_close_col="close")
    closes = [c["close"] for c in candles]
    sma40 = sma_series(closes, 40)
    above = {}
    detail = {}
    for i, c in enumerate(candles):
        if sma40[i] is not None:
            is_above = closes[i] > sma40[i]
            above[c["date"]] = is_above
            detail[c["date"].isoformat()] = {
                "close": round(closes[i], 4),
                "sma40": round(sma40[i], 4),
                "above": is_above,
            }
    return above, dropped, detail


def analyze_dataset(ds, today, spy_above):
    candles, dropped = load_candles(
        ds["csv"], ";", ds["close_col"], ds["low_col"], today, raw_close_col=ds["raw_close_col"]
    )
    sma_short, sma_long, trend, event = compute_signals(candles, 10, 40)
    cycles = find_cycles(candles, event)

    cycle_results = []
    missing_spy_dates = []

    for cyc in cycles:
        c = cyc["cross_index"]
        ex = cyc["exit_index"]
        cross_date = candles[c]["date"]
        exit_date = candles[ex]["date"]
        exit_price = candles[ex]["close"]

        # --- 베이스라인: delay=0 (weeksSinceCross==0에서 즉시 진입, 기존 구현 그대로) ---
        baseline_entry_index = c
        baseline_entry_price = candles[c]["close"]
        baseline_return_pct = (exit_price / baseline_entry_price - 1.0) * 100.0
        b_mdd_close, b_mdd_intraweek = mdd_close_and_intraweek(candles, baseline_entry_index, ex)

        # --- 후보: SPY 40주선 필터, weeksSinceCross 0~4주 매주 재평가 ---
        candidate_entry_index = None
        candidate_k = None
        weekly_checks = []
        for k in range(0, MAX_DELAY + 1):
            idx = c + k
            if idx > ex - 1:
                # Track A 자체 trend가 이미 ABOVE_LONG_AVERAGE를 벗어남(청산됨) -> 이 이후 k도 전부 불가
                break
            d = candles[idx]["date"]
            spy_ok = spy_above.get(d)
            if spy_ok is None:
                missing_spy_dates.append(d.isoformat())
            weekly_checks.append({"weeks_since_cross": k, "date": d.isoformat(), "spy_above_40w": spy_ok})
            if spy_ok:
                candidate_entry_index = idx
                candidate_k = k
                break

        entered = candidate_entry_index is not None
        if entered:
            candidate_entry_price = candles[candidate_entry_index]["close"]
            candidate_return_pct = (exit_price / candidate_entry_price - 1.0) * 100.0
            c_mdd_close, c_mdd_intraweek = mdd_close_and_intraweek(candles, candidate_entry_index, ex)
            candidate_entry_date = candles[candidate_entry_index]["date"].isoformat()
        else:
            candidate_entry_price = None
            candidate_return_pct = None
            c_mdd_close = None
            c_mdd_intraweek = None
            candidate_entry_date = None

        cycle_results.append({
            "dataset": ds["key"],
            "ticker": ds["ticker"],
            "cross_date": cross_date.isoformat(),
            "exit_date": exit_date.isoformat(),
            "open": cyc["open"],
            "baseline": {
                "entry_date": candles[baseline_entry_index]["date"].isoformat(),
                "entry_price": round(baseline_entry_price, 4),
                "exit_price": round(exit_price, 4),
                "return_pct": round(baseline_return_pct, 2),
                "mdd_close_pct": round(b_mdd_close * 100.0, 2),
                "mdd_intraweek_pct": round(b_mdd_intraweek * 100.0, 2),
            },
            "candidate": {
                "entered": entered,
                "weeks_since_cross_at_entry": candidate_k,
                "entry_date": candidate_entry_date,
                "entry_price": round(candidate_entry_price, 4) if entered else None,
                "return_pct": round(candidate_return_pct, 2) if entered else None,
                "mdd_close_pct": round(c_mdd_close * 100.0, 2) if entered else None,
                "mdd_intraweek_pct": round(c_mdd_intraweek * 100.0, 2) if entered else None,
                "weekly_checks": weekly_checks,
            },
        })

    return cycle_results, dropped, missing_spy_dates


def summarize(cycle_results, label):
    n = len(cycle_results)
    entered = [c for c in cycle_results if c["candidate"]["entered"]]
    excluded = [c for c in cycle_results if not c["candidate"]["entered"]]

    baseline_returns = [c["baseline"]["return_pct"] for c in cycle_results]
    baseline_mdds = [c["baseline"]["mdd_close_pct"] for c in cycle_results]
    baseline_mdds_iw = [c["baseline"]["mdd_intraweek_pct"] for c in cycle_results]

    # 전체 사이클 평균 (제외된 사이클 = 수익률 0%, MDD 0% 취급 — 미보유 상태의 실제 경제적 결과)
    all_cycle_candidate_returns = [
        c["candidate"]["return_pct"] if c["candidate"]["entered"] else 0.0 for c in cycle_results
    ]
    all_cycle_candidate_mdds = [
        c["candidate"]["mdd_close_pct"] if c["candidate"]["entered"] else 0.0 for c in cycle_results
    ]
    all_cycle_candidate_mdds_iw = [
        c["candidate"]["mdd_intraweek_pct"] if c["candidate"]["entered"] else 0.0 for c in cycle_results
    ]

    # 진입한 사이클만 평균
    entered_returns = [c["candidate"]["return_pct"] for c in entered]
    entered_mdds = [c["candidate"]["mdd_close_pct"] for c in entered]
    entered_mdds_iw = [c["candidate"]["mdd_intraweek_pct"] for c in entered]

    def avg(xs):
        return round(sum(xs) / len(xs), 2) if xs else None

    def med(xs):
        return round(statistics.median(xs), 2) if xs else None

    baseline_avg_return = avg(baseline_returns)
    baseline_avg_mdd = avg(baseline_mdds)
    candidate_all_avg_return = avg(all_cycle_candidate_returns)
    candidate_all_avg_mdd = avg(all_cycle_candidate_mdds)

    # MDD 1%p 개선당 포기 수익률 (전체 사이클 평균 기준, 기존 손절 리포트들과 동일한 정의)
    mdd_improvement_pp = (abs(baseline_avg_mdd) - abs(candidate_all_avg_mdd)) if (
        baseline_avg_mdd is not None and candidate_all_avg_mdd is not None
    ) else None
    return_given_up_pp = (baseline_avg_return - candidate_all_avg_return) if (
        baseline_avg_return is not None and candidate_all_avg_return is not None
    ) else None
    if mdd_improvement_pp not in (None, 0) and return_given_up_pp is not None:
        exchange_ratio = round(return_given_up_pp / mdd_improvement_pp, 3)
    else:
        exchange_ratio = None

    # 큰 수익 기회 임계치 (베이스라인 분포 상위 사분위, 결과를 보기 전에 고정한 정의를 여기서 재계산)
    if len(baseline_returns) >= 4:
        try:
            q3 = statistics.quantiles(baseline_returns, n=4)[2]
        except statistics.StatisticsError:
            q3 = max(baseline_returns)
    else:
        q3 = max(baseline_returns) if baseline_returns else None

    big_opportunity_cycles = [c for c in cycle_results if q3 is not None and c["baseline"]["return_pct"] >= q3]
    big_opportunity_missed = [c for c in big_opportunity_cycles if not c["candidate"]["entered"]]
    big_opp_missed_rate = (
        round(len(big_opportunity_missed) / len(big_opportunity_cycles) * 100.0, 1)
        if big_opportunity_cycles else None
    )

    return {
        "label": label,
        "n_cycles": n,
        "n_entered": len(entered),
        "n_excluded": len(excluded),
        "excluded_cycles": [
            {"dataset": c["dataset"], "cross_date": c["cross_date"], "exit_date": c["exit_date"],
             "baseline_return_pct": c["baseline"]["return_pct"]}
            for c in excluded
        ],
        "baseline": {
            "avg_return_pct": baseline_avg_return,
            "median_return_pct": med(baseline_returns),
            "avg_mdd_close_pct": baseline_avg_mdd,
            "avg_mdd_intraweek_pct": avg(baseline_mdds_iw),
        },
        "candidate_all_cycles": {
            "note": "제외된 사이클을 수익률 0%, MDD 0%로 취급한 전체 N개 사이클 평균 (필터 적용 후 실제 경제적 결과)",
            "avg_return_pct": candidate_all_avg_return,
            "median_return_pct": med(all_cycle_candidate_returns),
            "avg_mdd_close_pct": candidate_all_avg_mdd,
            "avg_mdd_intraweek_pct": avg(all_cycle_candidate_mdds_iw),
        },
        "candidate_entered_only": {
            "note": "진입한 사이클만의 평균 (트레이드 품질 비교용, 표본 수가 n_entered로 줄어듦)",
            "avg_return_pct": avg(entered_returns),
            "median_return_pct": med(entered_returns),
            "avg_mdd_close_pct": avg(entered_mdds),
            "avg_mdd_intraweek_pct": avg(entered_mdds_iw),
        },
        "mdd_improvement_pp": round(mdd_improvement_pp, 2) if mdd_improvement_pp is not None else None,
        "return_given_up_pp": round(return_given_up_pp, 2) if return_given_up_pp is not None else None,
        "exchange_ratio_return_given_up_per_mdd_pp": exchange_ratio,
        "big_opportunity_threshold_q3_baseline_return_pct": round(q3, 2) if q3 is not None else None,
        "big_opportunity_n": len(big_opportunity_cycles),
        "big_opportunity_missed_n": len(big_opportunity_missed),
        "big_opportunity_missed_rate_pct": big_opp_missed_rate,
    }


def walk_forward_summaries(all_cycles):
    out = {}
    for split in WALK_FORWARD_SPLITS:
        split_date = parse_date(split)
        train = [c for c in all_cycles if parse_date(c["cross_date"]) < split_date]
        test = [c for c in all_cycles if parse_date(c["cross_date"]) >= split_date]
        out[split] = {
            "train": summarize(train, f"train (< {split})") if train else None,
            "test": summarize(test, f"test (>= {split})") if test else None,
            "train_n": len(train),
            "test_n": len(test),
        }
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--today", required=True, help="YYYY-MM-DD, 완결 주봉 판정 기준일")
    parser.add_argument("--json-out", default=None)
    args = parser.parse_args()

    today = parse_date(args.today)

    spy_above, spy_dropped, spy_detail = load_spy_above_40w(SPY_CSV, today)
    print(f"[SPY] {SPY_CSV} 로드 완료. 40주 SMA 계산 가능 주봉 수: {len(spy_above)}")
    if spy_dropped:
        print(f"[SPY] 완결되지 않아 제외한 마지막 행: {[r['date'].isoformat() for r in spy_dropped]}")

    all_cycles = []
    per_dataset = {}
    all_missing_spy = []
    for ds in DATASETS:
        cycle_results, dropped, missing_spy_dates = analyze_dataset(ds, today, spy_above)
        per_dataset[ds["key"]] = {
            "label": ds["label"],
            "cycles": cycle_results,
            "cycle_count": len(cycle_results),
        }
        print(f"[{ds['key']}] {ds['label']}: {len(cycle_results)}개 사이클, "
              f"완결 제외 {len(dropped)}건, SPY 매칭 누락 {len(missing_spy_dates)}건")
        all_cycles.extend(cycle_results)
        all_missing_spy.extend(missing_spy_dates)

    if all_missing_spy:
        print(f"[경고] SPY 데이터에 매칭되지 않은 날짜: {sorted(set(all_missing_spy))}")
    else:
        print("[확인] 모든 Track A 사이클 체크포인트 날짜가 SPY 주봉 데이터와 매칭됨")

    overall = summarize(all_cycles, "전체 통합 (SOXL+TQQQ, 20개 사이클)")

    ticker_summaries = {}
    for ticker in ("SOXL", "TQQQ"):
        subset = [c for c in all_cycles if c["ticker"] == ticker]
        ticker_summaries[ticker] = summarize(subset, f"{ticker}만")

    wf = walk_forward_summaries(all_cycles)

    result = {
        "methodology": {
            "hypothesis": "SPY 마지막 완료 주봉 종가 > SPY 40주 이동평균일 때만 Track A 신규 BUY 허용 "
                           "(weeksSinceCross 0~4주 매주 재평가, 최초 동시 성립 주에 진입)",
            "baseline": "weeksSinceCross<=4 정책, 시장 국면 필터 없음, 진입은 항상 delay=0(교차 당주)",
            "spy_data_source": "Yahoo Finance (query2.finance.yahoo.com v8 chart API), interval=1wk, "
                                "adjclose 사용 — SOXL/TQQQ 프로덕션 소스(Twelve Data)와 다른 소스",
            "big_opportunity_definition": (
                f"베이스라인(무필터) 사이클 수익률 분포의 상위 사분위수(Q3, 상위 25%) 이상인 사이클을 "
                f"'큰 수익 기회'로 정의. 결과를 보기 전에 이 정의(분포 기반 상대 임계치)를 고정했다."
            ),
        },
        "overall": overall,
        "by_ticker": ticker_summaries,
        "walk_forward": wf,
        "per_dataset": per_dataset,
        "spy_missing_dates": sorted(set(all_missing_spy)),
    }

    print("\n=== 전체 통합 결과 ===")
    print(json.dumps(overall, ensure_ascii=False, indent=2))

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"\n[JSON 저장] {args.json_out}")


if __name__ == "__main__":
    main()

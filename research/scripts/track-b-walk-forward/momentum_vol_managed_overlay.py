#!/usr/bin/env python3
"""Track B 횡단면 모멘텀 - 변동성관리(vol-managed) 오버레이 재설계.

사전 등록 (실행 전 확정, 결과를 보고 바꾸지 않음)
--------------------------------------------------
`research/reports/track-b-sp100-momentum-universe-expansion.md`가 확정한 기각
사유는 두 가지였다: (1) 워크포워드 분할 전체에서 초과수익 부호가 불안정하고,
(2) 2021-2023(4way-C) 구간에서 문헌이 경고한 "모멘텀 붕괴(momentum crash)"가
실제 관측됐다(전략 MDD -42.3% vs 벤치마크 -22.5%). 이 문서는 종목 선정 규칙
(형성기간 48주/분기 리밸런싱/상위20%-40% 이원기준)은 그대로 두고, 학술 문헌이
모멘텀 붕괴에 대한 대응으로 제시하는 **변동성관리(vol-managed momentum)**
오버레이 하나만 추가해 재검증한다.

- 출처: Barroso & Santa-Clara(2015) "Momentum Has Its Moments", Daniel &
  Moskowitz(2016) "Momentum Crashes" - 모멘텀 포트폴리오 자체의 실현 변동성이
  높아지는 국면(전형적으로 붕괴 직전/직후)에 노출을 줄이면 붕괴로 인한 손실을
  완화할 수 있다는 것이 두 논문의 핵심 결과다.
- **가설**: 이 오버레이를 적용하면 4way-C(2021-2023)의 MDD 격차가 줄어들고,
  전체 워크포워드 분할에서 초과수익 부호 안정성이 개선될 것이다.
- **메커니즘 (데이터를 보기 전에 고정)**:
  1. 매주 그 시점까지 이미 관측된(미래 데이터 미사용) 전략의 최근 26주 주간
     수익률로 실현 변동성(표준편차)을 계산하고 연율화(×sqrt(52))한다.
  2. 목표 연변동성은 15%로 고정한다(모멘텀/팩터 vol-targeting 오버레이에서
     통상 쓰이는 관행적 수치 - 이 실행 결과를 보고 고른 값이 아니다).
  3. 그 주의 노출 비중 = min(1.0, 목표변동성 / 실현변동성). **레버리지는
     허용하지 않는다**(비중 상한 1.0, 이 프로젝트의 Track B 무레버리지 원칙과
     일치). 26주 실현 변동성을 계산할 수 없는 초반 구간(26주 미만 이력)은
     비중 1.0(완전 투자)으로 둔다 - 데이터 없다고 임의로 다른 값을 넣지 않는다.
  4. 노출이 줄어든 만큼은 **현금 0% 수익률**로 대기한다(무위험 이자 미반영,
     보수적 가정 - 오버레이 효과를 과대평가하지 않는 방향).
- **종목 선정 로직은 전혀 바꾸지 않는다.** `momentum_engine.py`(기존 코어,
  DJIA·S&P100 실행에서 이미 검증됨)가 계산한, 리밸런싱마다 확정되는 종목
  구성과 그 구성의 "레버리지 없는 완전투자 기준" 주간 수익률(`weeklyValues`에서
  파생)을 그대로 가져와서, 그 수익률 시계열에 매주 노출 비중만 곱하는 방식으로
  오버레이를 얹는다. 즉 "어떤 종목을 살지"는 100% 동일하고, "그 종목 조합에
  자산의 몇 %를 넣을지"만 매주 재조정한다. 이 방식은 momentum_engine.py의
  종목 선정·리밸런싱·비용 로직을 전혀 수정하지 않고 위에 얹을 수 있어
  (self-financing overlay), 기존에 검증된 계산 코어를 재사용한다는 이 프로젝트의
  원칙을 지킨다.
- 유니버스·비용·워크포워드 분할은 `run_walk_forward_sp100.py`와 완전히 동일
  (S&P100 101종목, 캘린더 정합성 버그 수정 완료된 CSV, 0/20/40/57bp, 2way/4way).

채택 게이트는 기존 5개를 그대로 적용하고, 추가로 다음을 명시적으로 확인한다:
- 게이트 6(신규, 이 오버레이 전용): 4way-C의 MDD 격차(전략-벤치마크)가 오버레이
  적용 전보다 개선돼야 한다. 개선되지 않으면 오버레이가 목적을 달성하지 못한
  것으로 판정한다.

실행: python3 research/scripts/track-b-walk-forward/momentum_vol_managed_overlay.py
"""
import json
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import momentum_engine as me  # noqa: E402

TOP_TIER_FRACTION = 0.20
HOLD_TIER_FRACTION = 0.40
REBALANCE_WEEKS = 13
COST_BPS_SCENARIOS = [0.0, 20.0, 40.0, 57.0]
INITIAL_CAPITAL = 100000.0

TARGET_ANNUAL_VOL = 0.15  # 사전 고정, 튜닝 아님
VOL_LOOKBACK_WEEKS = 26  # 사전 고정
WEEKS_PER_YEAR = 52.0

PARTITIONS = {
    "2way": [
        {"id": "2way-A", "label": "2015-01-01 ~ 2019-12-31", "start": "2015-01-01", "end": "2019-12-31"},
        {"id": "2way-B", "label": "2020-01-01 ~ 2026-09-10", "start": "2020-01-01", "end": "2026-09-10"},
    ],
    "4way": [
        {"id": "4way-A", "label": "2015-01-01 ~ 2017-12-31", "start": "2015-01-01", "end": "2017-12-31"},
        {"id": "4way-B", "label": "2018-01-01 ~ 2020-12-31", "start": "2018-01-01", "end": "2020-12-31"},
        {"id": "4way-C", "label": "2021-01-01 ~ 2023-12-31", "start": "2021-01-01", "end": "2023-12-31"},
        {"id": "4way-D", "label": "2024-01-01 ~ 2026-09-10", "start": "2024-01-01", "end": "2026-09-10"},
    ],
}

DATA_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "cache", "track-b-walk-forward-sp100"
)
CSV_PATH = os.path.join(DATA_DIR, "universe_weekly.csv")
OUTPUT_PATH = os.path.join(DATA_DIR, "vol_managed_overlay_results.json")


def clip_window(calendar, start, end):
    candidates_start = [d for d in calendar if d >= start]
    candidates_end = [d for d in calendar if d <= end]
    if not candidates_start or not candidates_end:
        return None, None
    return candidates_start[0], candidates_end[-1]


def pct_return(ending, initial):
    return (ending / initial - 1.0) * 100.0


def apply_vol_managed_overlay(weekly_values, initial_capital):
    """weekly_values: [(date, unscaled_portfolio_value), ...] (rebalancing/cost 이미 반영).
    각 주의 미조정 수익률에 그 시점까지의 정보만으로 계산한 노출 비중을 곱해
    새 NAV 경로를 만든다. 반환: (scaled_weekly_values, weekly_scale_used)
    """
    if len(weekly_values) < 2:
        return list(weekly_values), []

    raw_returns = []
    for i in range(1, len(weekly_values)):
        prev_v = weekly_values[i - 1][1]
        cur_v = weekly_values[i][1]
        r = (cur_v / prev_v - 1.0) if prev_v > 0 else 0.0
        raw_returns.append(r)

    target_weekly_vol = TARGET_ANNUAL_VOL / math.sqrt(WEEKS_PER_YEAR)

    scaled_values = [(weekly_values[0][0], initial_capital)]
    scales_used = []
    nav = initial_capital
    for i, r in enumerate(raw_returns):
        # i번째 raw_returns는 weekly_values[i] -> weekly_values[i+1] 구간 수익률.
        # 이 구간의 노출 비중은 그 구간 "시작 시점"(weekly_values[i])까지 이미 관측된
        # 과거 수익률만으로 정해야 미래참조가 없다.
        history = raw_returns[max(0, i - VOL_LOOKBACK_WEEKS):i]
        if len(history) < VOL_LOOKBACK_WEEKS:
            scale = 1.0
        else:
            realized_weekly_vol = statistics.pstdev(history)
            if realized_weekly_vol <= 0:
                scale = 1.0
            else:
                scale = min(1.0, target_weekly_vol / realized_weekly_vol)
        scales_used.append(scale)
        nav = nav * (1.0 + scale * r)
        scaled_values.append((weekly_values[i + 1][0], nav))

    return scaled_values, scales_used


def mdd_from_weekly_values(weekly_values):
    peak = -math.inf
    mdd = 0.0
    for _, v in weekly_values:
        peak = max(peak, v)
        if peak > 0:
            mdd = min(mdd, (v - peak) / peak * 100.0)
    return mdd


def run_all():
    universe = me.load_universe_csv(CSV_PATH)
    quality = me.run_quality_checks(universe)
    clean = me.build_clean_series(universe, quality)
    calendar = me.build_calendar(universe)

    output = {
        "generatedBy": "research/scripts/track-b-walk-forward/momentum_vol_managed_overlay.py",
        "hypothesis": (
            "Barroso & Santa-Clara(2015)/Daniel & Moskowitz(2016)식 변동성관리 오버레이: "
            "전략 자체의 과거 26주 실현 변동성 기준 목표 연변동성 15% 대비 노출을 "
            "min(1, 목표/실현)로 줄이고(무레버리지, 26주 미만 이력은 노출 1.0), 나머지는 "
            "현금 0%로 대기. 종목 선정(형성기간 48주/분기 리밸런싱/상위20-40%)은 불변."
        ),
        "targetAnnualVol": TARGET_ANNUAL_VOL,
        "volLookbackWeeks": VOL_LOOKBACK_WEEKS,
        "universe": {
            "size": len(universe),
            "excludedByQuality": quality.excludedTickers,
        },
        "parameters": {
            "formationWeeks": me.FORMATION_WEEKS,
            "excludeRecentWeeks": me.EXCLUDE_RECENT_WEEKS,
            "rebalancePeriodWeeks": REBALANCE_WEEKS,
            "topTierFraction": TOP_TIER_FRACTION,
            "holdTierFraction": HOLD_TIER_FRACTION,
            "costScenariosRoundTripBps": COST_BPS_SCENARIOS,
            "initialCapital": INITIAL_CAPITAL,
        },
        "partitions": {},
    }

    for partition_name, windows in PARTITIONS.items():
        output["partitions"][partition_name] = []
        for w in windows:
            w_start, w_end = clip_window(calendar, w["start"], w["end"])
            window_result = {
                "id": w["id"], "label": w["label"],
                "actualStart": w_start, "actualEnd": w_end,
                "byCostBps": {},
            }
            for bps in COST_BPS_SCENARIOS:
                unscaled = me.run_backtest(
                    clean, calendar, w_start, w_end, REBALANCE_WEEKS,
                    TOP_TIER_FRACTION, HOLD_TIER_FRACTION, bps, INITIAL_CAPITAL,
                )
                if not unscaled.weeklyValues:
                    continue
                scaled_values, scales_used = apply_vol_managed_overlay(unscaled.weeklyValues, INITIAL_CAPITAL)

                bench_start = unscaled.events[0].date if unscaled.events else w_start
                bench = me.run_equal_weight_buy_and_hold(clean, calendar, bench_start, w_end, bps, INITIAL_CAPITAL)

                unscaled_ret = pct_return(unscaled.endingValue, INITIAL_CAPITAL)
                scaled_ret = pct_return(scaled_values[-1][1], INITIAL_CAPITAL)
                bench_ret = pct_return(bench.endingValue, INITIAL_CAPITAL)
                scaled_mdd = mdd_from_weekly_values(scaled_values)

                window_result["byCostBps"][str(bps)] = {
                    "unscaledStrategyReturnPct": round(unscaled_ret, 3),
                    "unscaledStrategyMaxDrawdownPct": round(unscaled.maxDrawdownPct, 3),
                    "volManagedStrategyReturnPct": round(scaled_ret, 3),
                    "volManagedStrategyMaxDrawdownPct": round(scaled_mdd, 3),
                    "benchmarkReturnPct": round(bench_ret, 3),
                    "benchmarkMaxDrawdownPct": round(bench.maxDrawdownPct, 3),
                    "excessReturnPctPoints_volManagedVsBenchmark": round(scaled_ret - bench_ret, 3),
                    "mddGapPctPoints_volManagedVsBenchmark": round(scaled_mdd - bench.maxDrawdownPct, 3),
                    "mddGapPctPoints_unscaledVsBenchmark": round(unscaled.maxDrawdownPct - bench.maxDrawdownPct, 3),
                    "avgExposureScale": round(sum(scales_used) / len(scales_used), 3) if scales_used else 1.0,
                    "minExposureScale": round(min(scales_used), 3) if scales_used else 1.0,
                    "rebalanceCount": len(unscaled.events),
                }
            output["partitions"][partition_name].append(window_result)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print_summary(output)
    print(f"\n원자료 저장: {OUTPUT_PATH}")
    return output


def print_summary(output):
    print("=" * 130)
    print("Track B 횡단면 모멘텀 - 변동성관리 오버레이 - 요약 (0bp 기준)")
    print("=" * 130)
    for partition_name, windows in output["partitions"].items():
        print(f"\n--- 분할체계: {partition_name} ---")
        for w in windows:
            r = w["byCostBps"].get("0.0")
            if not r:
                continue
            print(f"[{w['id']}] {w['label']}")
            print(f"  무조정 전략: {r['unscaledStrategyReturnPct']:>10.2f}%  MDD {r['unscaledStrategyMaxDrawdownPct']:>8.2f}%")
            print(f"  변동성관리 : {r['volManagedStrategyReturnPct']:>10.2f}%  MDD {r['volManagedStrategyMaxDrawdownPct']:>8.2f}%  "
                  f"평균노출 {r['avgExposureScale']:.2f}  최소노출 {r['minExposureScale']:.2f}")
            print(f"  벤치마크   : {r['benchmarkReturnPct']:>10.2f}%  MDD {r['benchmarkMaxDrawdownPct']:>8.2f}%")
            print(f"  MDD격차(무조정 vs bench): {r['mddGapPctPoints_unscaledVsBenchmark']:>8.2f}%p   "
                  f"MDD격차(변동성관리 vs bench): {r['mddGapPctPoints_volManagedVsBenchmark']:>8.2f}%p   "
                  f"초과수익(변동성관리): {r['excessReturnPctPoints_volManagedVsBenchmark']:>8.2f}%p")


if __name__ == "__main__":
    run_all()

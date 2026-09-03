#!/usr/bin/env python3
"""
backtest_track_a_volatility_targeting.py

research/reports/strategy-engine-evidence-review.md 6.2절 "후보 B — Track A 변동성 타깃 기반
포지션 사이징"의 사전 등록(pre-registration) 계획을 **그대로** 실행하는 스크립트.

사전 등록된 사양 (이 스크립트는 아래 값을 하드코딩하며, 결과를 본 뒤 바꾸지 않는다)
------------------------------------------------------------------------------
- 유니버스: SOXL, TQQQ, TNA, FAS (기존 49사이클 확장 표본, run_stoploss_report_v2.DATASETS와 동일 구성)
- 기간: 각 자산 로컬 캐시 전 구간 (가장 이른 2008-11 ~ 마지막 완료 주봉)
- 진입: CROSS_UP 주(weeksSinceCross == 0, delay=0) **한 시점만**.
  (제품 규칙은 weeksSinceCross <= 4를 허용하지만 이 스크립트는 0주 진입만 시뮬레이션한다.
   아래 "진입 시점 범위" 절을 반드시 읽을 것.)
- 청산: 이후 첫 CROSS_DOWN. 미청산 사이클은 데이터 마지막 날 마크투마켓.
- 변동성 추정: 진입 직전까지 **완료된 일봉 60개 로그수익률**의 표준편차 × sqrt(252). 룩백 60일 하나만.
- 목표 변동성: 연 40% 하나만.
- 포지션 배수 = min(1.0, 0.40 / 추정변동성). 상한 1.0, 하한 없음.
- 사이클 도중 재조정(리밸런싱) 없음 — 진입 시 한 번만 크기를 정한다.
- 편도 슬리피지 0.15%p, 수수료 0%.
- 워크포워드 4분할(cross_date 기준): 2018-01-01 / 2019-01-01 / 2020-01-01 / 2021-01-01.

진입 시점 범위 (중요 한계 — 결과 해석 전 필독)
------------------------------------------------
제품 진입 규칙(StrategyDecisionMaker.decideForCandidate)은
`trend == ABOVE_LONG_AVERAGE && weeksSinceCross <= 4`이므로 **교차 당주부터 4주 뒤까지 5개 주**에
BUY가 유지된다. 이 스크립트는 그중 **weeksSinceCross == 0(교차 당주) 한 시점만** 시뮬레이션한다.

- 0주는 그 규칙이 처음 성립하는 주이므로 "엔진이 BUY를 처음 내는 시점"으로서는 정확하다.
  기존 Track A 리포트(손절·추적손절·국면필터)도 전부 같은 delay=0 기준선을 쓰므로 비교가 유지된다.
- 그러나 이는 **"0~4주 구간 전체를 검증했다"는 뜻이 아니다.** 1~4주에 진입하면 변동성 룩백 창이
  5~20거래일 뒤로 밀려 추정 변동성과 배수 m이 달라지고, 진입가·사이클 길이도 달라진다.
  그 조합은 이번 범위에서 **계산하지 않았다.**
- 1~4주 진입에 대한 배수 분포·꼬리 효과를 확인하려면 별도 사전 등록이 필요하다
  (결과를 본 뒤 진입 지연을 훑는 것은 사전 등록 원칙 2가 금지한 사후 탐색이다).

체결 가정은 기존 Track A 리포트와 완전히 동일하며, 그 구현
(stoploss_daily_backtest.py / entry_delay_cycle_backtest.py)을 **수정 없이 import 재사용**한다.
- 주봉 신호는 그 주 금요일(주봉 시작일+4일) 종가로 확정 -> 다음 첫 거래일 시가에 체결.
- 매수 체결가 = 시가 x (1 + 0.0015), 매도 체결가 = 시가 x (1 - 0.0015).
- 미청산 사이클의 마크투마켓 평가에는 슬리피지를 적용하지 않는다(실제 매도가 아니므로).

포지션 사이징의 수익률·낙폭 계산 (리밸런싱 없음)
------------------------------------------------
자본 1 중 m을 진입가에 넣고 나머지 (1-m)은 현금으로 둔다(현금 수익률 0% 가정 — 무위험이자율을
임의 상수로 넣지 않기 위해 0으로 고정하고, 이는 사이징에 불리한 보수적 가정이다).
  equity(t) = (1 - m) + m * price(t) / entry_price
  cycle_return = equity(exit) - 1 = m * (exit_price / entry_price - 1)
  MDD는 위 equity 경로에서 직접 계산한다(peak은 종가 equity로 갱신, 낙폭은 종가/저가 equity로 측정).
m = 1.0이면 이 계산은 기존 무손절 기준선(stoploss_daily_backtest.mdd_over_window)과 정확히 일치한다
(회귀 검증: --self-check).

출력
----
기본은 표준출력 요약만. --json-out 경로를 주면 사이클 단위 원자료까지 JSON으로 저장한다.
**이 스크립트는 저장소 안의 어떤 파일도 기본값으로 덮어쓰지 않는다**(research/data/cache/**는
이번 작업의 쓰기 허용 범위가 아니다).

사용 예
-------
PYTHONDONTWRITEBYTECODE=1 python3 -B research/data/tools/backtest_track_a_volatility_targeting.py \
    --json-out /tmp/track_a_vol_target_results.json
"""
import argparse
import csv
import json
import math
import os
import sys
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stoploss_daily_backtest as sdb  # noqa: E402  (기존 파일, 수정하지 않음)
import entry_delay_cycle_backtest as edcb  # noqa: E402  (기존 파일, 수정하지 않음)

# --- 사전 등록 상수 (결과를 본 뒤 변경 금지) ---------------------------------
TODAY = date(2026, 8, 18)           # 기존 49사이클 리포트와 동일한 기준일
SLIPPAGE_PCT = 0.0015               # 편도 0.15%p
COMMISSION_PCT = 0.0                # 수수료 0%
VOL_LOOKBACK_OBS = 60               # 완료된 일봉 로그수익률 60개
TRADING_DAYS_PER_YEAR = 252
TARGET_ANNUAL_VOL = 0.40            # 연 40%
MAX_POSITION_MULTIPLIER = 1.0
WALK_FORWARD_SPLITS = ["2018-01-01", "2019-01-01", "2020-01-01", "2021-01-01"]
TAIL_DECILE = 0.10                  # "하위 10% 사이클(최악 낙폭)"
# 판정 임계값 (사전 등록 6.2 성공·실패 조건)
ADOPT_TAIL_MDD_IMPROVE_MIN = 5.0    # %p
ADOPT_RATIO_MAX = 1.5
REJECT_RATIO_MIN = 3.0
REJECT_MDD_IMPROVE_MAX = 2.0        # %p

TWELVEDATA_WEEKLY_START = "2021-08-02"  # SOXL 중복 제거 보조 확인에만 사용

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CACHE = os.path.join(REPO, "research", "data", "cache")


def _c(name):
    return os.path.join(CACHE, name)


DATASETS = [
    {
        "key": "soxl_twelvedata", "ticker": "SOXL",
        "weekly_csv": _c("soxl-weekly-twelvedata-2021-2026.csv"),
        "weekly_delim": ";", "close_col": "close", "low_col": "low", "raw_close_col": None,
        "daily_csv": _c("soxl-daily-twelvedata-2010-2026.csv"),
        "daily_adjclose_col": None,
        "label": "SOXL / Twelve Data 1week splits(신호) + Twelve Data 1day(체결·변동성)",
    },
    {
        "key": "soxl_yahoo", "ticker": "SOXL",
        "weekly_csv": _c("soxl-weekly-yahoo-2010-2026.csv"),
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": _c("soxl-daily-yahoo-2010-2026.csv"),
        "daily_adjclose_col": "adjclose",
        "label": "SOXL / Yahoo weekly adjclose(신호) + Yahoo 1day(체결·변동성)",
    },
    {
        "key": "tqqq_yahoo", "ticker": "TQQQ",
        "weekly_csv": _c("tqqq-weekly-yahoo-2010-2026.csv"),
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": _c("tqqq-daily-yahoo-2010-2026.csv"),
        "daily_adjclose_col": "adjclose",
        "label": "TQQQ / Yahoo weekly adjclose(신호) + Yahoo 1day(체결·변동성)",
    },
    {
        "key": "tna_yahoo", "ticker": "TNA",
        "weekly_csv": _c("tna-weekly-yahoo-2008-2026.csv"),
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": _c("tna-daily-yahoo-2008-2026.csv"),
        "daily_adjclose_col": "adjclose",
        "label": "TNA / Yahoo weekly adjclose(신호) + Yahoo 1day(체결·변동성)",
    },
    {
        "key": "fas_yahoo", "ticker": "FAS",
        "weekly_csv": _c("fas-weekly-yahoo-2008-2026.csv"),
        "weekly_delim": ";", "close_col": "adjclose", "low_col": "low", "raw_close_col": "close",
        "daily_csv": _c("fas-daily-yahoo-2008-2026.csv"),
        "daily_adjclose_col": "adjclose",
        "label": "FAS / Yahoo weekly adjclose(신호) + Yahoo 1day(체결·변동성)",
    },
]


# ---------------------------------------------------------------------------
# 변동성 추정
# ---------------------------------------------------------------------------

def load_daily_extra_column(path, column, delimiter=";"):
    """sdb.load_daily_candles가 읽지 않는 추가 컬럼(예: adjclose)을 날짜순으로 읽는다."""
    if column is None:
        return None
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter=delimiter):
            if column not in row or row[column] in (None, ""):
                return None
            rows.append((row["datetime"].strip(), float(row[column])))
    rows.sort(key=lambda r: r[0])
    return [v for _, v in rows]


def realized_annual_vol(prices, end_idx_exclusive, n_obs=VOL_LOOKBACK_OBS):
    """prices[.. end_idx_exclusive-1] 중 마지막 n_obs개 로그수익률의 표본표준편차 x sqrt(252).

    n_obs개의 로그수익률에는 n_obs+1개의 종가가 필요하다. 데이터가 모자라면 None을 돌려준다
    (해당 사이클은 두 팔 모두에서 제외 — 사후에 임의값으로 채우지 않는다).
    표본표준편차는 불편추정량(ddof=1)을 쓴다.
    """
    first = end_idx_exclusive - (n_obs + 1)
    if first < 0 or end_idx_exclusive > len(prices):
        return None
    window = prices[first:end_idx_exclusive]
    rets = []
    for i in range(1, len(window)):
        p0, p1 = window[i - 1], window[i]
        if p0 <= 0 or p1 <= 0:
            return None
        rets.append(math.log(p1 / p0))
    if len(rets) != n_obs:
        return None
    mean = sum(rets) / n_obs
    var = sum((r - mean) ** 2 for r in rets) / (n_obs - 1)
    return math.sqrt(var) * math.sqrt(TRADING_DAYS_PER_YEAR)


def position_multiplier(est_vol, target=TARGET_ANNUAL_VOL, cap=MAX_POSITION_MULTIPLIER):
    if est_vol is None or est_vol <= 0:
        return None
    return min(cap, target / est_vol)


# ---------------------------------------------------------------------------
# 사이징된 포지션의 수익률/MDD (사이클 중 리밸런싱 없음)
# ---------------------------------------------------------------------------

def sized_leg(daily_rows, entry_idx, exit_idx, entry_price, exit_price, m):
    """equity(t) = (1-m) + m * price(t)/entry_price 경로에서 수익률과 MDD를 계산한다.

    peak은 종가 equity로만 갱신하고(선행정보 없음), 낙폭은 종가·저가 equity 양쪽으로 측정한다 —
    stoploss_daily_backtest.mdd_over_window와 동일한 규약이며 m=1.0이면 결과가 정확히 일치한다.
    """
    peak = (1.0 - m) + m * (entry_price / entry_price)  # == 1.0
    mdd_close = 0.0
    mdd_low = 0.0
    for j in range(entry_idx, exit_idx + 1):
        eq_c = (1.0 - m) + m * (daily_rows[j]["close"] / entry_price)
        eq_l = (1.0 - m) + m * (daily_rows[j]["low"] / entry_price)
        if eq_c > peak:
            peak = eq_c
        dd_c = (eq_c - peak) / peak
        dd_l = (eq_l - peak) / peak
        if dd_c < mdd_close:
            mdd_close = dd_c
        if dd_l < mdd_low:
            mdd_low = dd_l
    ret = ((1.0 - m) + m * (exit_price / entry_price)) - 1.0
    return ret * 100.0, mdd_close * 100.0, mdd_low * 100.0


# ---------------------------------------------------------------------------
# 사이클 수집
# ---------------------------------------------------------------------------

def collect_cycles(ds, extra_multipliers=()):
    """한 데이터셋의 모든 사이클에 대해 기준선(m=1)과 변동성 타깃 결과를 계산한다."""
    weekly_candles, cycles, dropped = sdb.load_weekly_cycles(
        ds["weekly_csv"], ds["weekly_delim"], ds["close_col"], ds["low_col"], TODAY,
        raw_close_col=ds["raw_close_col"],
    )
    daily_rows = sdb.load_daily_candles(ds["daily_csv"], delimiter=";")
    daily_close = [r["close"] for r in daily_rows]
    daily_adj = load_daily_extra_column(ds["daily_csv"], ds.get("daily_adjclose_col"))

    out = []
    skipped = []
    for cyc in cycles:
        ctx = sdb.build_cycle_daily_context(weekly_candles, cyc, daily_rows)
        # entry0_idx = weeksSinceCross == 0(교차 당주) 신호 확정 다음 거래일. 제품 규칙이 허용하는
        # weeksSinceCross 1~4주 진입은 이 스크립트에서 계산하지 않는다(모듈 docstring "진입 시점 범위").
        entry_idx = ctx["entry0_idx"]
        exit_idx = ctx["hard_exit_idx"]
        if entry_idx is None or exit_idx is None or entry_idx > exit_idx:
            skipped.append({"cross_date": weekly_candles[cyc["cross_index"]]["date"].isoformat(),
                            "reason": "no_daily_execution_data"})
            continue

        entry_price = sdb.apply_buy_slippage(daily_rows[entry_idx]["open"], SLIPPAGE_PCT)
        if ctx["hard_exit_is_trend"]:
            exit_price = sdb.apply_sell_slippage(daily_rows[exit_idx]["open"], SLIPPAGE_PCT)
            exit_kind = "TREND"
        else:
            exit_price = daily_rows[exit_idx]["close"]
            exit_kind = "OPEN_MARK"

        est_vol = realized_annual_vol(daily_close, entry_idx)
        if est_vol is None:
            skipped.append({"cross_date": weekly_candles[cyc["cross_index"]]["date"].isoformat(),
                            "reason": "insufficient_daily_history_for_60obs_vol"})
            continue
        m = position_multiplier(est_vol)

        base_ret, base_mdd_c, base_mdd_l = sized_leg(
            daily_rows, entry_idx, exit_idx, entry_price, exit_price, 1.0)
        vt_ret, vt_mdd_c, vt_mdd_l = sized_leg(
            daily_rows, entry_idx, exit_idx, entry_price, exit_price, m)

        rec = {
            "dataset": ds["key"], "ticker": ds["ticker"],
            "cross_date": weekly_candles[cyc["cross_index"]]["date"].isoformat(),
            "entry_date": daily_rows[entry_idx]["date"].isoformat(),
            "exit_date": daily_rows[exit_idx]["date"].isoformat(),
            "exit_kind": exit_kind,
            "open_cycle": cyc["open"],
            "entry_price": round(entry_price, 4),
            "exit_price": round(exit_price, 4),
            "est_annual_vol_pct": round(est_vol * 100.0, 1),
            "position_multiplier": round(m, 4),
            "baseline_return_pct": round(base_ret, 1),
            "baseline_mdd_close_pct": round(base_mdd_c, 1),
            "baseline_mdd_low_pct": round(base_mdd_l, 1),
            "voltarget_return_pct": round(vt_ret, 1),
            "voltarget_mdd_close_pct": round(vt_mdd_c, 1),
            "voltarget_mdd_low_pct": round(vt_mdd_l, 1),
        }

        # 보조(사전 등록 목록 밖): adjclose 기반 변동성 추정 민감도
        if daily_adj is not None:
            est_vol_adj = realized_annual_vol(daily_adj, entry_idx)
            if est_vol_adj is not None:
                m_adj = position_multiplier(est_vol_adj)
                r, c_, l_ = sized_leg(daily_rows, entry_idx, exit_idx, entry_price, exit_price, m_adj)
                rec["adj_est_annual_vol_pct"] = round(est_vol_adj * 100.0, 1)
                rec["adj_position_multiplier"] = round(m_adj, 4)
                rec["adj_return_pct"] = round(r, 1)
                rec["adj_mdd_close_pct"] = round(c_, 1)
                rec["adj_mdd_low_pct"] = round(l_, 1)

        # 보조(사전 등록 목록 밖): 고정 배수 대조군 — "변동성 신호"가 아니라 "그냥 작게 사기"의 효과 분리
        for name, const_m in extra_multipliers:
            r, c_, l_ = sized_leg(daily_rows, entry_idx, exit_idx, entry_price, exit_price, const_m)
            rec[f"{name}_return_pct"] = round(r, 1)
            rec[f"{name}_mdd_close_pct"] = round(c_, 1)
            rec[f"{name}_mdd_low_pct"] = round(l_, 1)

        out.append(rec)
    return out, skipped, len(cycles), dropped


# ---------------------------------------------------------------------------
# 집계
# ---------------------------------------------------------------------------

def _mean(xs):
    return sum(xs) / len(xs) if xs else None


def _median(xs):
    if not xs:
        return None
    s = sorted(xs)
    n = len(s)
    return s[n // 2] if n % 2 == 1 else (s[n // 2 - 1] + s[n // 2]) / 2


def arm_summary(rows, prefix):
    rets = [r[f"{prefix}_return_pct"] for r in rows]
    mdd_c = [r[f"{prefix}_mdd_close_pct"] for r in rows]
    mdd_l = [r[f"{prefix}_mdd_low_pct"] for r in rows]
    return {
        "n": len(rows),
        "mean_return_pct": round(_mean(rets), 1) if rows else None,
        "median_return_pct": round(_median(rets), 1) if rows else None,
        "mean_mdd_close_pct": round(_mean(mdd_c), 1) if rows else None,
        "mean_mdd_low_pct": round(_mean(mdd_l), 1) if rows else None,
        "win_rate_pct": round(sum(1 for x in rets if x > 0) / len(rets) * 100.0, 1) if rows else None,
    }


def tradeoff(rows, prefix, base_prefix="baseline"):
    """교환비율 = 포기 수익률(%p) / MDD 개선폭(%p). 낮을수록(음수일수록) 좋다."""
    if not rows:
        return None
    base_ret = _mean([r[f"{base_prefix}_return_pct"] for r in rows])
    base_mdd = _mean([r[f"{base_prefix}_mdd_close_pct"] for r in rows])
    cand_ret = _mean([r[f"{prefix}_return_pct"] for r in rows])
    cand_mdd = _mean([r[f"{prefix}_mdd_close_pct"] for r in rows])
    mdd_improve = cand_mdd - base_mdd          # 양수 = 낙폭이 덜 나쁨(개선)
    give_up = base_ret - cand_ret              # 양수 = 수익을 포기함
    return {
        "baseline_mean_return_pct": round(base_ret, 1),
        "baseline_mean_mdd_close_pct": round(base_mdd, 1),
        "cand_mean_return_pct": round(cand_ret, 1),
        "cand_mean_mdd_close_pct": round(cand_mdd, 1),
        "mdd_improve_pct_points": round(mdd_improve, 2),
        "return_give_up_pct_points": round(give_up, 2),
        "give_up_per_mdd_improve": round(give_up / mdd_improve, 2) if abs(mdd_improve) > 0.001 else None,
    }


def tail_metrics(rows, prefix, base_prefix="baseline", decile=TAIL_DECILE):
    """사전 등록 '하위 10% 사이클(최악 낙폭 구간)의 평균 MDD' 개선폭.

    paired: 기준선 MDD 기준 최악 10% 사이클을 고른 뒤, 같은 사이클 집합에서 두 팔의 평균 MDD를 비교.
    distributional: 각 팔이 각자의 최악 10% 사이클을 고른 뒤 평균 MDD를 비교.
    """
    if not rows:
        return None
    k = max(1, math.ceil(len(rows) * decile))
    base_sorted = sorted(rows, key=lambda r: r[f"{base_prefix}_mdd_close_pct"])[:k]
    paired_base = _mean([r[f"{base_prefix}_mdd_close_pct"] for r in base_sorted])
    paired_cand = _mean([r[f"{prefix}_mdd_close_pct"] for r in base_sorted])
    cand_sorted = sorted(rows, key=lambda r: r[f"{prefix}_mdd_close_pct"])[:k]
    dist_cand = _mean([r[f"{prefix}_mdd_close_pct"] for r in cand_sorted])
    return {
        "k_worst_cycles": k,
        "paired_baseline_mean_mdd_pct": round(paired_base, 1),
        "paired_cand_mean_mdd_pct": round(paired_cand, 1),
        "paired_improve_pct_points": round(paired_cand - paired_base, 2),
        "distributional_baseline_mean_mdd_pct": round(paired_base, 1),
        "distributional_cand_mean_mdd_pct": round(dist_cand, 1),
        "distributional_improve_pct_points": round(dist_cand - paired_base, 2),
        "worst_cycles": [
            {"dataset": r["dataset"], "cross_date": r["cross_date"],
             "baseline_mdd_close_pct": r[f"{base_prefix}_mdd_close_pct"],
             "cand_mdd_close_pct": r[f"{prefix}_mdd_close_pct"],
             "position_multiplier": r["position_multiplier"]}
            for r in base_sorted
        ],
    }


def direction_label(td):
    """교환비율/MDD 개선폭을 사전 등록 임계값에 대조해 방향을 라벨링한다.

    사전 등록 표는 '방향이 뒤집히지 않는다'의 수치 정의를 두지 않았으므로, 같은 표의 임계값
    (교환비율 1.5 이하 / 3.0 초과, MDD 개선 2%p)을 그대로 재사용해 분할별 방향을 판정한다.
    """
    if td is None:
        return "no_data"
    mdd_imp = td["mdd_improve_pct_points"]
    ratio = td["give_up_per_mdd_improve"]
    if mdd_imp < REJECT_MDD_IMPROVE_MAX:
        return "unfavorable"
    if ratio is None:
        return "undetermined"
    if ratio > REJECT_RATIO_MIN:
        return "unfavorable"
    if ratio <= ADOPT_RATIO_MAX:
        return "favorable"
    return "middle"


def in_dedup_soxl(r):
    return not (r["dataset"] == "soxl_yahoo" and r["cross_date"] >= TWELVEDATA_WEEKLY_START)


# ---------------------------------------------------------------------------
# 자체 회귀 검증
# ---------------------------------------------------------------------------

def self_check(all_rows, datasets):
    """m=1.0 기준선이 기존 stoploss_daily_backtest.run_stoploss_candidates의 무손절 기준선과
    사이클 단위로 완전히 일치하는지 확인한다(수익률·MDD 모두)."""
    problems = []
    checked = 0
    for ds in datasets:
        weekly_candles, cycles, _ = sdb.load_weekly_cycles(
            ds["weekly_csv"], ds["weekly_delim"], ds["close_col"], ds["low_col"], TODAY,
            raw_close_col=ds["raw_close_col"])
        daily_rows = sdb.load_daily_candles(ds["daily_csv"], delimiter=";")
        atr = sdb.compute_daily_atr(daily_rows, period=14)
        base_trades, _ = sdb.run_stoploss_candidates(
            weekly_candles, cycles, daily_rows, atr, ds["label"], [], slippage_pct=SLIPPAGE_PCT)
        by_cross = {t["cross_date"]: t for t in base_trades}
        for r in all_rows:
            if r["dataset"] != ds["key"]:
                continue
            t = by_cross.get(r["cross_date"])
            if t is None:
                problems.append(f"{ds['key']} {r['cross_date']}: 기존 기준선에 대응 사이클 없음")
                continue
            checked += 1
            for a, b, name in (
                (r["baseline_return_pct"], t["return_pct"], "return_pct"),
                (r["baseline_mdd_close_pct"], t["mdd_close_pct"], "mdd_close_pct"),
                (r["baseline_mdd_low_pct"], t["mdd_low_pct"], "mdd_low_pct"),
            ):
                if abs(a - b) > 0.051:
                    problems.append(f"{ds['key']} {r['cross_date']} {name}: {a} vs {b}")
    return checked, problems


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def build_scope(rows, prefixes):
    scope = {"n": len(rows), "baseline": arm_summary(rows, "baseline")}
    for p in prefixes:
        # adj(보조) 팔은 adjclose 컬럼이 있는 데이터셋에만 존재하므로 부분표본으로 집계한다.
        sub = [r for r in rows if f"{p}_return_pct" in r]
        if not sub:
            continue
        scope[p] = {
            "coverage_n": len(sub),
            "covers_full_sample": len(sub) == len(rows),
            "summary": arm_summary(sub, p),
            "tradeoff": tradeoff(sub, p),
            "tail": tail_metrics(sub, p),
        }
    scope["mean_position_multiplier"] = round(_mean([r["position_multiplier"] for r in rows]), 4) if rows else None
    scope["median_position_multiplier"] = round(_median([r["position_multiplier"] for r in rows]), 4) if rows else None
    scope["mean_est_annual_vol_pct"] = round(_mean([r["est_annual_vol_pct"] for r in rows]), 1) if rows else None
    return scope


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json-out", default=None,
                    help="사이클 단위 원자료 포함 전체 결과 JSON 경로 (생략하면 저장하지 않음)")
    ap.add_argument("--self-check", action="store_true",
                    help="m=1.0 기준선이 기존 무손절 기준선과 일치하는지 회귀 검증")
    args = ap.parse_args()

    all_rows = []
    skipped_all = []
    dataset_meta = {}

    # 대조군 배수는 실제 평균 배수를 모른 채 고정하지 않기 위해 2단계로 계산한다:
    # 1차로 사이클을 수집해 평균 배수를 구한 뒤, 그 값을 고정 배수 대조군으로 다시 계산한다.
    for ds in DATASETS:
        rows, skipped, n_cycles, dropped = collect_cycles(ds)
        all_rows.extend(rows)
        skipped_all.extend([dict(s, dataset=ds["key"]) for s in skipped])
        dataset_meta[ds["key"]] = {
            "ticker": ds["ticker"], "label": ds["label"],
            "weekly_csv": os.path.relpath(ds["weekly_csv"], REPO),
            "daily_csv": os.path.relpath(ds["daily_csv"], REPO),
            "num_weekly_cycles": n_cycles, "num_usable_cycles": len(rows),
            "num_dropped_incomplete_weekly_bars": len(dropped),
        }

    mean_m = _mean([r["position_multiplier"] for r in all_rows])
    const_m = round(mean_m, 4)
    all_rows = []
    for ds in DATASETS:
        rows, _, _, _ = collect_cycles(ds, extra_multipliers=[("constm", const_m)])
        all_rows.extend(rows)

    all_rows.sort(key=lambda r: (r["cross_date"], r["dataset"]))
    prefixes = ["voltarget", "adj", "constm"]

    out = {
        "generated_for": "research/reports/track-a-volatility-targeting-backtest.md",
        "preregistration": "research/reports/strategy-engine-evidence-review.md 6.2 (후보 B)",
        "as_of": TODAY.isoformat(),
        "assumptions": {
            "entry_timing_simulated": "weeksSinceCross == 0 (CROSS_UP 주, delay=0) 단일 시점",
            "entry_rule_scope_warning": (
                "제품 규칙은 weeksSinceCross <= 4(교차 당주 포함 5개 주)에서 BUY를 유지하지만, "
                "이번 실행은 0주 진입만 시뮬레이션했다. 1~4주 진입은 변동성 룩백 창과 진입가가 달라져 "
                "배수 m이 바뀌므로 이 결과가 '0~4주 구간 전체 검증'을 뜻하지 않는다."
            ),
            "slippage_pct_one_way": SLIPPAGE_PCT,
            "commission_pct_one_way": COMMISSION_PCT,
            "vol_lookback_log_return_obs": VOL_LOOKBACK_OBS,
            "annualization_trading_days": TRADING_DAYS_PER_YEAR,
            "target_annual_vol": TARGET_ANNUAL_VOL,
            "position_multiplier_formula": "min(1.0, target_vol / estimated_vol)",
            "intra_cycle_rebalancing": False,
            "cash_return_assumption": "0% (무위험이자율을 임의 상수로 넣지 않음, 사이징에 보수적)",
            "walk_forward_splits": WALK_FORWARD_SPLITS,
            "vol_price_series": "각 데이터셋 일봉 close(분할 조정, 배당 미조정)",
        },
        "datasets": dataset_meta,
        "skipped_cycles": skipped_all,
        "constant_multiplier_control": const_m,
    }

    out["summary_full"] = build_scope(all_rows, prefixes)
    dedup_rows = [r for r in all_rows if in_dedup_soxl(r)]
    out["summary_dedup_soxl"] = build_scope(dedup_rows, prefixes)

    per_ds = {}
    for key in dataset_meta:
        rows = [r for r in all_rows if r["dataset"] == key]
        per_ds[key] = build_scope(rows, ["voltarget"])
    out["per_dataset"] = per_ds

    wf = {}
    for scope_name, rows in (("full", all_rows), ("dedup_soxl", dedup_rows)):
        scope_wf = {}
        for cutoff in WALK_FORWARD_SPLITS:
            train = [r for r in rows if r["cross_date"] < cutoff]
            test = [r for r in rows if r["cross_date"] >= cutoff]
            entry = {"cutoff": cutoff, "train_n": len(train), "test_n": len(test)}
            for part_name, part in (("train", train), ("test", test)):
                td = tradeoff(part, "voltarget")
                entry[part_name] = {
                    "summary": arm_summary(part, "voltarget") if part else None,
                    "tradeoff": td,
                    "tail": tail_metrics(part, "voltarget"),
                    "direction": direction_label(td),
                    # 보조(사전 등록 목록 밖, 판정에 사용하지 않음): 고정 배수 대조군
                    "control_constm_tradeoff": tradeoff(part, "constm"),
                    "control_constm_tail": tail_metrics(part, "constm"),
                }
            scope_wf[cutoff] = entry
        wf[scope_name] = scope_wf
    out["walk_forward"] = wf

    # 보조 기록(판정에 사용하지 않음): 샤프/소르티노 — 사이클 수익률 분포 기준
    def ratio_stats(rows, prefix):
        rets = [r[f"{prefix}_return_pct"] for r in rows]
        n = len(rets)
        if n < 2:
            return None
        mu = sum(rets) / n
        var = sum((x - mu) ** 2 for x in rets) / (n - 1)
        sd = math.sqrt(var)
        downside = [x for x in rets if x < 0]
        dsd = math.sqrt(sum(x * x for x in downside) / n) if downside else None
        return {
            "mean_cycle_return_pct": round(mu, 1),
            "stdev_cycle_return_pct": round(sd, 1),
            "cycle_sharpe_like": round(mu / sd, 3) if sd > 0 else None,
            "cycle_sortino_like": round(mu / dsd, 3) if dsd else None,
            "note": "무위험이자율 0, 사이클 길이 미조정. Cederburg et al.(2020) 근거로 채택 사유에 쓰지 않는다.",
        }

    out["auxiliary_ratios"] = {
        "baseline": ratio_stats(all_rows, "baseline"),
        "voltarget": ratio_stats(all_rows, "voltarget"),
    }

    if args.self_check:
        checked, problems = self_check(all_rows, DATASETS)
        out["self_check"] = {"cycles_checked": checked, "mismatches": problems,
                             "passed": len(problems) == 0}

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(dict(out, cycles=all_rows), f, indent=2, ensure_ascii=False)
        print(f"[저장] {args.json_out}")

    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

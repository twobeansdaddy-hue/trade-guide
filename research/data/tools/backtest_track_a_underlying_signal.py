#!/usr/bin/env python3
"""
backtest_track_a_underlying_signal.py

research/reports/strategy-engine-evidence-review.md 6.1절 "후보 A — Track A 신호 산출 기준을
레버리지 ETF에서 기초 지수 ETF로 이전"의 사전 등록(pre-registration) 계획을 **그대로** 실행하는 스크립트.

사전 등록된 사양 (이 스크립트는 아래 값을 하드코딩하며, 결과를 본 뒤 바꾸지 않는다)
------------------------------------------------------------------------------
- 가설: 10/40 주봉 추세 신호를 레버리지 ETF 가격이 아니라 기초 지수 추종 ETF 가격으로 계산해도
  사이클 판정(진입·청산 시점)과 그 결과가 실질적으로 동일하다.
- 유니버스와 매핑: SOXL<-SOXX, TQQQ<-QQQ, TNA<-IWM, FAS<-XLF (4쌍 고정, 추가·교체 없음).
- 신호 계산 구간: 각 기초 ETF 상장 이후 전 구간 ~ 2026-08-31(마지막 완료 주봉). 미완결 주봉 제외.
- 진입: SMA(10) > SMA(40) 이고 weeksSinceCross <= 4인 **첫 주의 종가** = CROSS_UP 주 종가.
- 청산: 이후 첫 CROSS_DOWN 주의 **종가**. 미청산 사이클은 마지막 완료 주봉을 임시 청산으로 쓰고 별도 표기.
- 리밸런싱·분할·손절·재진입 규칙 없음.
- 편도 슬리피지 0.15%p, 수수료 0%.
- 학습 구간: 상장일 ~ 2013-12-31 / 검증 구간: 2014-01-01 ~ 2026-08-31.
- 워크포워드 4분할(사전 고정): 2006-01-01 / 2010-01-01 / 2014-01-01 / 2018-01-01.
- 판정 임계값(사전 고정):
    지지 = 네 쌍 모두 겹치는 구간에서 교차 주 ±1주 이내 일치율 >= 80% 이고
           사이클별 수익률 차이의 중앙값 절댓값 <= 5%p 이며, 두 조건이 4개 분할 전부에서 방향이 뒤집히지 않음
    기각 = 일치율 < 60% 이거나 수익률 차이 중앙값 절댓값 > 15%p
    그 사이 또는 분할별 방향 반전 = 추가 검증 필요

두 팔(arm)의 정의 — 무엇과 무엇을 비교하는가
--------------------------------------------
가설은 "신호를 어디서 계산하는가"에 대한 것이므로, **거래 대상은 두 팔 모두 레버리지 ETF로 같다.**
  - arm LEV (기준선, 현행 엔진): 레버리지 ETF 주봉으로 신호 계산 -> 레버리지 ETF 매매
  - arm UND (후보):            기초 지수 ETF 주봉으로 신호 계산 -> 레버리지 ETF 매매
따라서 "사이클별 수익률 차이"는 **같은 레버리지 ETF를 서로 다른 신호로 타이밍했을 때의 차이**이고,
1배 ETF와 3배 ETF의 수익률을 직접 빼는 것이 아니다(그 비교는 레버리지 배수 때문에 무의미하다).

레버리지 수익률 합성 금지 (사전 등록 6.1 "알려진 한계")
------------------------------------------------------
레버리지 ETF가 존재하지 않던 구간(SOXL/TQQQ 2010 이전, TNA/FAS 2008 이전)에서는 **수익률을 계산하지
않는다.** 그 구간에서 얻는 것은 신호(사이클) 표본뿐이며, 일일 리밸런싱·차입비용·운용보수를 가정한
합성 레버리지 수익률은 이 스크립트가 **일절 만들지 않는다.** 확장 표본 75사이클의 성과 지표는
"기초 ETF 자체를 샀다면"의 실측치로만 기록하고(보조), 사전 등록에 따라 **판정에 사용하지 않는다.**

데이터 소스
-----------
- 기초 ETF 4종: 이번 작업에서 신규 수집한 Yahoo 주봉 캐시
  (soxx/qqq/iwm/xlf-weekly-yahoo-*.csv, 각 .metadata.json에 provider/접근일/조정 방식 기록).
- 레버리지 ETF 4종: 기존 Yahoo 주봉 캐시(신규 수집 없음, 수정 없음).
  두 팔이 같은 제공자(Yahoo)·같은 조정 방식(adjclose = 분할+배당)을 쓰도록 맞췄다. 프로덕션 소스
  (Twelve Data, adjustment=splits)와는 조정 방식이 다르며, 이 차이는 리포트 3절에 기록한다.

주봉 중복 행 처리 (데이터 위생 — 파라미터가 아니다)
--------------------------------------------------
Yahoo 응답 마지막에는 진행 중인 주에 대해 "주 시작 정렬 행"과 "실시간 시세 행"이 함께 오는 경우가 있다
(예: SOXX 2026-08-31 + 2026-09-02, 기존 캐시 SOXL 2026-08-10 + 2026-08-11 — 후자는
track-a-entry-delay-cutoff-review.md에 기록돼 있다). 같은 ISO 주를 가리키는 행이 둘이면 SMA 창이
한 칸 밀리므로, **같은 ISO 주에서는 가장 이른 행 하나만 남긴다.** 8개 시계열 전부에서 이 규칙이
제거하는 행은 위의 실시간 시세 행뿐이며(--self-check가 검증한다), 정상 주봉은 하나도 제거하지 않는다.

기존 구현 재사용
----------------
SMA/교차/사이클 판정은 entry_delay_cycle_backtest.py(기존 파일)를 **수정 없이 import 재사용**한다.
그 구현은 프로덕션 WeeklyMaCrossoverStrategy와 대조가 끝난 코드다.

출력
----
기본은 표준출력 JSON 요약. --json-out 경로를 주면 사이클 단위 원자료까지 저장한다.
이 스크립트는 저장소 안의 어떤 파일도 기본값으로 덮어쓰지 않는다.

사용 예
-------
PYTHONDONTWRITEBYTECODE=1 python3 -B research/data/tools/backtest_track_a_underlying_signal.py \
    --self-check --json-out /tmp/track_a_underlying_signal_results.json
"""
import argparse
import json
import os
import sys
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import entry_delay_cycle_backtest as edcb  # noqa: E402  (기존 파일, 수정하지 않음)

# --- 사전 등록 상수 (결과를 본 뒤 변경 금지) ---------------------------------
SIGNAL_END = date(2026, 8, 31)      # 사전 등록 "~ 2026-08-31(마지막 완료 주봉)"
SHORT_PERIOD = 10
LONG_PERIOD = 40
SLIPPAGE_PCT = 0.0015               # 편도 0.15%p
COMMISSION_PCT = 0.0                # 수수료 0%
MATCH_TOLERANCE_DAYS = 7            # "±1주 이내" = 주봉 1칸 = 7일
TRAIN_END = "2013-12-31"
TEST_START = "2014-01-01"
WALK_FORWARD_SPLITS = ["2006-01-01", "2010-01-01", "2014-01-01", "2018-01-01"]
# 판정 임계값 (사전 등록 6.1 성공·실패 조건)
SUPPORT_MATCH_RATE_MIN = 80.0       # %
SUPPORT_MEDIAN_ABS_DIFF_MAX = 5.0   # %p
REJECT_MATCH_RATE_MAX = 60.0        # % (미만이면 기각)
REJECT_MEDIAN_ABS_DIFF_MIN = 15.0   # %p (초과면 기각)

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CACHE = os.path.join(REPO, "research", "data", "cache")


def _c(name):
    return os.path.join(CACHE, name)


# 사전 등록 6.1 매핑표 그대로. 결과를 본 뒤 추가·교체하지 않는다.
PAIRS = [
    {
        "pair": "SOXL<-SOXX", "lev": "SOXL", "und": "SOXX",
        "und_csv": _c("soxx-weekly-yahoo-2001-2026.csv"),
        "lev_csv": _c("soxl-weekly-yahoo-2010-2026.csv"),
        "und_expected_first_bar": "2001-07-09",
        "und_expected_cross_up_count": 16,   # 사전 등록 표본 크기 사전 확인값
        "und_label": "SOXX / Yahoo 1wk adjclose(분할+배당 조정) — 2026-09-03 신규 수집",
        "lev_label": "SOXL / Yahoo 1wk adjclose(분할+배당 조정) — 기존 캐시 재사용",
    },
    {
        "pair": "TQQQ<-QQQ", "lev": "TQQQ", "und": "QQQ",
        "und_csv": _c("qqq-weekly-yahoo-1999-2026.csv"),
        "lev_csv": _c("tqqq-weekly-yahoo-2010-2026.csv"),
        "und_expected_first_bar": "1999-03-08",
        "und_expected_cross_up_count": 21,
        "und_label": "QQQ / Yahoo 1wk adjclose(분할+배당 조정) — 2026-09-03 신규 수집",
        "lev_label": "TQQQ / Yahoo 1wk adjclose(분할+배당 조정) — 기존 캐시 재사용",
    },
    {
        "pair": "TNA<-IWM", "lev": "TNA", "und": "IWM",
        "und_csv": _c("iwm-weekly-yahoo-2000-2026.csv"),
        "lev_csv": _c("tna-weekly-yahoo-2008-2026.csv"),
        "und_expected_first_bar": "2000-05-22",
        "und_expected_cross_up_count": 19,
        "und_label": "IWM / Yahoo 1wk adjclose(분할+배당 조정) — 2026-09-03 신규 수집",
        "lev_label": "TNA / Yahoo 1wk adjclose(분할+배당 조정) — 기존 캐시 재사용",
    },
    {
        "pair": "FAS<-XLF", "lev": "FAS", "und": "XLF",
        "und_csv": _c("xlf-weekly-yahoo-1998-2026.csv"),
        "lev_csv": _c("fas-weekly-yahoo-2008-2026.csv"),
        "und_expected_first_bar": "1998-12-21",
        "und_expected_cross_up_count": 19,
        "und_label": "XLF / Yahoo 1wk adjclose(분할+배당 조정) — 2026-09-03 신규 수집",
        "lev_label": "FAS / Yahoo 1wk adjclose(분할+배당 조정) — 기존 캐시 재사용",
    },
]

# 기존 Track A 표본과 겹치는 구간(사전 등록 "중복 표본으로 별도 표기")
EXISTING_TRACK_A_SAMPLE_START = {"SOXL": "2010-01-01", "TQQQ": "2010-01-01",
                                 "TNA": "2008-01-01", "FAS": "2008-01-01"}


# ---------------------------------------------------------------------------
# 로드 / 신호
# ---------------------------------------------------------------------------

def dedupe_iso_week(rows):
    """같은 ISO 주를 가리키는 행이 여러 개면 가장 이른 행만 남긴다(모듈 docstring '주봉 중복 행 처리')."""
    kept, dropped, seen = [], [], set()
    for r in sorted(rows, key=lambda x: x["date"]):
        key = r["date"].isocalendar()[:2]
        if key in seen:
            dropped.append(r)
            continue
        seen.add(key)
        kept.append(r)
    return kept, dropped


def load_series(csv_path):
    """Yahoo 주봉 CSV -> 완결 주봉만 남긴 캔들 목록.

    adjclose를 종가로, low는 (adjclose/close) 비율로 환산한다(기존 Track A 리포트와 동일 관례).
    """
    candles, incomplete = edcb.load_candles(
        csv_path, delimiter=";", close_col="adjclose", low_col="low",
        today=SIGNAL_END, raw_close_col="close")
    candles, dup_dropped = dedupe_iso_week(candles)
    return candles, {
        "incomplete_bars_dropped": [r["date"].isoformat() for r in incomplete],
        "duplicate_iso_week_rows_dropped": [r["date"].isoformat() for r in dup_dropped],
    }


def signal_table(candles):
    """캔들 목록 -> (교차 이벤트 목록, 사이클 목록)."""
    _, _, _, event = edcb.compute_signals(candles, SHORT_PERIOD, LONG_PERIOD)
    cycles = edcb.find_cycles(candles, event)
    events = [{"date": candles[i]["date"], "type": event[i]}
              for i in range(len(candles)) if event[i] in ("CROSS_UP", "CROSS_DOWN")]
    return event, events, cycles


def signal_defined_from(candles):
    """교차 이벤트가 정의되기 시작하는 첫 캔들 날짜(인덱스 LONG_PERIOD)."""
    if len(candles) <= LONG_PERIOD:
        return None
    return candles[LONG_PERIOD]["date"]


# ---------------------------------------------------------------------------
# 체결가 (사전 등록: 주봉 종가 + 편도 슬리피지 0.15%p)
# ---------------------------------------------------------------------------

def buy_price(close):
    return close * (1.0 + SLIPPAGE_PCT)


def sell_price(close):
    return close * (1.0 - SLIPPAGE_PCT)


def mdd_over(candles, i0, i1):
    """entry~exit 구간의 MDD(종가 기준, 주중 저가 기준) — entry_delay_cycle_backtest와 동일 규약."""
    return edcb.mdd_close_and_intraweek(candles, i0, i1)


def cycle_trades(trade_candles, cross_dates_with_open_flag, trade_index_by_date):
    """주어진 진입(교차) 주 목록으로 거래 대상 시계열을 매매했을 때의 사이클 결과.

    trade_candles: 거래 대상(레버리지 ETF) 캔들
    cross_dates_with_open_flag: [(cross_date, exit_date, open_flag), ...] — 신호 시계열에서 얻은 날짜
    """
    out = []
    for cross_date, exit_date, is_open in cross_dates_with_open_flag:
        ei = trade_index_by_date.get(cross_date)
        xi = trade_index_by_date.get(exit_date)
        # 미청산 사이클의 임시 청산일은 "마지막 완료 주봉"이다(사전 등록). 신호 시계열과 거래 시계열의
        # 마지막 완료 주봉이 다를 수 있으므로(레버리지 캐시가 더 이르게 끝난다), 거래 시계열 기준으로 맞춘다.
        if is_open and xi is None and ei is not None:
            xi = len(trade_candles) - 1
        if ei is None or xi is None or ei > xi:
            out.append({"cross_date": cross_date.isoformat(), "exit_date": exit_date.isoformat(),
                        "open_cycle": is_open, "tradable": False,
                        "reason": "거래 대상 시계열에 해당 주봉 없음(상장 전 또는 데이터 공백)"})
            continue
        entry_px = buy_price(trade_candles[ei]["close"])
        if is_open:
            exit_px = trade_candles[xi]["close"]          # 마크투마켓: 슬리피지 미적용
        else:
            exit_px = sell_price(trade_candles[xi]["close"])
        mdd_c, mdd_l = mdd_over(trade_candles, ei, xi)
        out.append({
            "cross_date": cross_date.isoformat(),
            "exit_date": trade_candles[xi]["date"].isoformat(),
            "signal_exit_date": exit_date.isoformat(),
            "open_cycle": is_open, "tradable": True,
            "entry_price": round(entry_px, 6), "exit_price": round(exit_px, 6),
            "return_pct": round((exit_px / entry_px - 1.0) * 100.0, 2),
            "mdd_close_pct": round(mdd_c * 100.0, 2),
            "mdd_intraweek_pct": round(mdd_l * 100.0, 2),
        })
    return out


# ---------------------------------------------------------------------------
# 교차 주 일치율
# ---------------------------------------------------------------------------

def match_events(und_dates, lev_dates, tol_days=MATCH_TOLERANCE_DAYS):
    """±tol_days 이내 1:1 매칭(가장 가까운 쌍부터 탐욕적으로). 정의가 하나뿐이 아니므로
    양방향 비율과 대칭 비율을 모두 돌려준다."""
    pairs = []
    for i, u in enumerate(und_dates):
        for j, l in enumerate(lev_dates):
            gap = abs((u - l).days)
            if gap <= tol_days:
                pairs.append((gap, i, j))
    pairs.sort()
    used_u, used_l, matched = set(), set(), []
    for gap, i, j in pairs:
        if i in used_u or j in used_l:
            continue
        used_u.add(i)
        used_l.add(j)
        matched.append({"und_date": und_dates[i].isoformat(),
                        "lev_date": lev_dates[j].isoformat(), "gap_days": gap})
    nu, nl, nm = len(und_dates), len(lev_dates), len(matched)
    return {
        "n_underlying_events": nu,
        "n_leveraged_events": nl,
        "n_matched": nm,
        "matched_pairs": matched,
        "unmatched_underlying": [d.isoformat() for i, d in enumerate(und_dates) if i not in used_u],
        "unmatched_leveraged": [d.isoformat() for j, d in enumerate(lev_dates) if j not in used_l],
        "rate_of_underlying_pct": round(nm / nu * 100.0, 1) if nu else None,
        "rate_of_leveraged_pct": round(nm / nl * 100.0, 1) if nl else None,
        # 1차 지표: 양쪽의 여분 교차를 모두 벌점으로 반영하는 대칭 일치율
        "symmetric_match_rate_pct": round(2.0 * nm / (nu + nl) * 100.0, 1) if (nu + nl) else None,
        "exact_same_week_pct": round(
            sum(1 for m in matched if m["gap_days"] == 0) / nm * 100.0, 1) if nm else None,
    }


# ---------------------------------------------------------------------------
# 통계 도우미
# ---------------------------------------------------------------------------

def _mean(xs):
    return sum(xs) / len(xs) if xs else None


def _median(xs):
    if not xs:
        return None
    s = sorted(xs)
    n = len(s)
    return s[n // 2] if n % 2 == 1 else (s[n // 2 - 1] + s[n // 2]) / 2


def _r(x, nd=2):
    return round(x, nd) if x is not None else None


def return_stats(rows, key="return_pct"):
    vals = [r[key] for r in rows if r.get("tradable") and r.get(key) is not None]
    if not vals:
        return {"n": 0}
    mdd = [r["mdd_close_pct"] for r in rows if r.get("tradable")]
    mdd_l = [r["mdd_intraweek_pct"] for r in rows if r.get("tradable")]
    return {
        "n": len(vals),
        "win_rate_pct": _r(sum(1 for v in vals if v > 0) / len(vals) * 100.0, 1),
        "mean_return_pct": _r(_mean(vals), 1),
        "median_return_pct": _r(_median(vals), 1),
        "min_return_pct": _r(min(vals), 1),
        "max_return_pct": _r(max(vals), 1),
        "mean_mdd_close_pct": _r(_mean(mdd), 1),
        "median_mdd_close_pct": _r(_median(mdd), 1),
        "worst_mdd_close_pct": _r(min(mdd), 1) if mdd else None,
        "mean_mdd_intraweek_pct": _r(_mean(mdd_l), 1),
    }


# ---------------------------------------------------------------------------
# 쌍 단위 실행
# ---------------------------------------------------------------------------

def run_pair(spec):
    und_candles, und_drop = load_series(spec["und_csv"])
    lev_candles, lev_drop = load_series(spec["lev_csv"])

    _, und_events, und_cycles = signal_table(und_candles)
    _, lev_events, lev_cycles = signal_table(lev_candles)

    und_idx = {c["date"]: i for i, c in enumerate(und_candles)}
    lev_idx = {c["date"]: i for i, c in enumerate(lev_candles)}

    # 겹치는 구간: 두 시계열 모두에서 교차 이벤트가 정의되는 첫 주 ~ 두 시계열의 마지막 완결 주봉 중 이른 쪽
    und_from, lev_from = signal_defined_from(und_candles), signal_defined_from(lev_candles)
    overlap_start = max(und_from, lev_from)
    overlap_end = min(und_candles[-1]["date"], lev_candles[-1]["date"])

    def in_overlap(d):
        return overlap_start <= d <= overlap_end

    # --- 교차 주 일치율 (겹치는 구간) ---------------------------------------
    match = {}
    for etype in ("CROSS_UP", "CROSS_DOWN"):
        u = [e["date"] for e in und_events if e["type"] == etype and in_overlap(e["date"])]
        l = [e["date"] for e in lev_events if e["type"] == etype and in_overlap(e["date"])]
        match[etype] = match_events(u, l)
    u_all = [e["date"] for e in und_events if in_overlap(e["date"])]
    l_all = [e["date"] for e in lev_events if in_overlap(e["date"])]
    match["ALL"] = match_events(u_all, l_all)

    # --- 두 팔의 사이클: 거래 대상은 둘 다 레버리지 ETF -----------------------
    def to_triples(candles, cycles):
        return [(candles[c["cross_index"]]["date"], candles[c["exit_index"]]["date"], c["open"])
                for c in cycles]

    und_triples_all = to_triples(und_candles, und_cycles)
    lev_triples = to_triples(lev_candles, lev_cycles)

    # 겹치는 구간(레버리지 ETF가 실제로 존재한 구간)의 사이클만 수익률을 계산한다.
    und_triples_overlap = [t for t in und_triples_all if in_overlap(t[0])]
    lev_triples_overlap = [t for t in lev_triples if in_overlap(t[0])]

    arm_und = cycle_trades(lev_candles, und_triples_overlap, lev_idx)
    arm_lev = cycle_trades(lev_candles, lev_triples_overlap, lev_idx)

    # --- 사이클 페어링: 진입(CROSS_UP) 주가 ±1주 이내인 사이클끼리 ------------
    m = match_events([t[0] for t in und_triples_overlap], [t[0] for t in lev_triples_overlap])
    und_by_cross = {r["cross_date"]: r for r in arm_und}
    lev_by_cross = {r["cross_date"]: r for r in arm_lev}
    paired = []
    for mp in m["matched_pairs"]:
        a = und_by_cross.get(mp["und_date"])
        b = lev_by_cross.get(mp["lev_date"])
        if not a or not b or not a["tradable"] or not b["tradable"]:
            continue
        paired.append({
            "und_cross_date": mp["und_date"], "lev_cross_date": mp["lev_date"],
            "gap_days": mp["gap_days"],
            "und_exit_date": a["exit_date"], "lev_exit_date": b["exit_date"],
            "open_cycle": a["open_cycle"] or b["open_cycle"],
            "und_signal_return_pct": a["return_pct"], "lev_signal_return_pct": b["return_pct"],
            "return_diff_pct_points": round(a["return_pct"] - b["return_pct"], 2),
            "und_signal_mdd_close_pct": a["mdd_close_pct"], "lev_signal_mdd_close_pct": b["mdd_close_pct"],
            "mdd_diff_pct_points": round(a["mdd_close_pct"] - b["mdd_close_pct"], 2),
            "in_existing_track_a_sample":
                mp["und_date"] >= EXISTING_TRACK_A_SAMPLE_START[spec["lev"]],
        })

    # --- 확장 표본(보조, 판정 미사용): 기초 ETF 자체를 매매했을 때 -----------
    # 레버리지 ETF 수익률을 합성하지 않는다. 이 팔의 거래 대상은 기초 ETF 자신이다.
    aux_underlying_own = cycle_trades(und_candles, und_triples_all, und_idx)

    return {
        "pair": spec["pair"], "leveraged": spec["lev"], "underlying": spec["und"],
        "data": {
            "underlying_csv": os.path.relpath(spec["und_csv"], REPO),
            "leveraged_csv": os.path.relpath(spec["lev_csv"], REPO),
            "underlying_label": spec["und_label"], "leveraged_label": spec["lev_label"],
            "underlying_bars": len(und_candles), "leveraged_bars": len(lev_candles),
            "underlying_range": [und_candles[0]["date"].isoformat(), und_candles[-1]["date"].isoformat()],
            "leveraged_range": [lev_candles[0]["date"].isoformat(), lev_candles[-1]["date"].isoformat()],
            "underlying_dropped": und_drop, "leveraged_dropped": lev_drop,
            "signal_defined_from": {"underlying": und_from.isoformat(), "leveraged": lev_from.isoformat()},
            "overlap_window": [overlap_start.isoformat(), overlap_end.isoformat()],
        },
        "cycle_counts": {
            "underlying_cross_up_full_history": len(und_triples_all),
            "underlying_cross_up_in_overlap": len(und_triples_overlap),
            "leveraged_cross_up_in_overlap": len(lev_triples_overlap),
            "underlying_cross_up_before_overlap": len(und_triples_all) - len(und_triples_overlap),
        },
        "cross_week_match": match,
        "arm_underlying_signal": arm_und,
        "arm_leveraged_signal": arm_lev,
        "paired_cycles": paired,
        "aux_underlying_own_cycles": aux_underlying_own,
    }


# ---------------------------------------------------------------------------
# 판정
# ---------------------------------------------------------------------------

def diff_stats(paired):
    diffs = [p["return_diff_pct_points"] for p in paired]
    return {
        "n_paired": len(paired),
        "median_diff_pct_points": _r(_median(diffs)),
        "abs_of_median_diff_pct_points": _r(abs(_median(diffs))) if diffs else None,
        "median_abs_diff_pct_points": _r(_median([abs(d) for d in diffs])),
        "mean_diff_pct_points": _r(_mean(diffs)),
        "max_abs_diff_pct_points": _r(max([abs(d) for d in diffs])) if diffs else None,
        "n_diff_within_5pp": sum(1 for d in diffs if abs(d) <= 5.0),
        "n_diff_over_15pp": sum(1 for d in diffs if abs(d) > 15.0),
    }


def verdict_for(match_rate, abs_median_diff):
    """사전 등록 6.1 판정표를 그대로 적용한다(한 쌍 또는 한 구간 단위)."""
    if match_rate is None or abs_median_diff is None:
        return "no_data"
    if match_rate < REJECT_MATCH_RATE_MAX or abs_median_diff > REJECT_MEDIAN_ABS_DIFF_MIN:
        return "reject"
    if match_rate >= SUPPORT_MATCH_RATE_MIN and abs_median_diff <= SUPPORT_MEDIAN_ABS_DIFF_MAX:
        return "support"
    return "needs_more"


def scope_verdict(pair_results, cross_filter=None, match_key="ALL"):
    """구간(전체/분할)별로 네 쌍 각각의 일치율·수익률 차이와 그 판정을 계산한다."""
    per_pair = {}
    for pr in pair_results:
        paired = pr["paired_cycles"]
        if cross_filter is not None:
            paired = [p for p in paired if cross_filter(p["und_cross_date"])]
            u = [date.fromisoformat(e["und_date"]) for e in pr["cross_week_match"][match_key]["matched_pairs"]]
            # 분할 구간의 일치율은 그 구간에 속한 교차만으로 다시 계산한다.
            mm = pr["cross_week_match"][match_key]
            und_dates = ([date.fromisoformat(d) for d in mm["unmatched_underlying"]] +
                         [date.fromisoformat(m["und_date"]) for m in mm["matched_pairs"]])
            lev_dates = ([date.fromisoformat(d) for d in mm["unmatched_leveraged"]] +
                         [date.fromisoformat(m["lev_date"]) for m in mm["matched_pairs"]])
            und_dates = sorted(d for d in und_dates if cross_filter(d.isoformat()))
            lev_dates = sorted(d for d in lev_dates if cross_filter(d.isoformat()))
            match = match_events(und_dates, lev_dates)
            del u
        else:
            match = pr["cross_week_match"][match_key]
        ds = diff_stats(paired)
        rate = match["symmetric_match_rate_pct"]
        per_pair[pr["pair"]] = {
            "match": {k: v for k, v in match.items() if k != "matched_pairs"},
            "return_diff": ds,
            "verdict": verdict_for(rate, ds["abs_of_median_diff_pct_points"]),
        }
    rates = [v["match"]["symmetric_match_rate_pct"] for v in per_pair.values()
             if v["match"]["symmetric_match_rate_pct"] is not None]
    all_paired = []
    for pr in pair_results:
        paired = pr["paired_cycles"]
        if cross_filter is not None:
            paired = [p for p in paired if cross_filter(p["und_cross_date"])]
        all_paired.extend(paired)
    pooled = diff_stats(all_paired)
    min_rate = min(rates) if rates else None
    overall = verdict_for(min_rate, pooled["abs_of_median_diff_pct_points"])
    # 사전 등록 지지 조건은 "네 쌍 모두"를 요구하므로, 한 쌍이라도 지지가 아니면 지지가 아니다.
    if overall == "support" and any(v["verdict"] != "support" for v in per_pair.values()):
        overall = "needs_more"
    if any(v["verdict"] == "reject" for v in per_pair.values()):
        overall = "reject"
    return {
        "per_pair": per_pair,
        "pooled_return_diff": pooled,
        "min_symmetric_match_rate_pct": min_rate,
        "verdict": overall,
    }


# ---------------------------------------------------------------------------
# 보조 진단 (사전 등록 목록 밖 — 판정에 사용하지 않는다)
# ---------------------------------------------------------------------------

SEQUENCE_TOLERANCE_DAYS = 400   # "가장 가까운 교차끼리" 서술용 정렬. 판정 허용오차(7일)와 무관하다.


def _events_in_overlap(pr, etype):
    """한 쌍의 겹치는 구간 안 교차 날짜 목록을 (기초, 레버리지)로 돌려준다."""
    m = pr["cross_week_match"][etype]
    und = sorted([date.fromisoformat(d) for d in m["unmatched_underlying"]] +
                 [date.fromisoformat(x["und_date"]) for x in m["matched_pairs"]])
    lev = sorted([date.fromisoformat(d) for d in m["unmatched_leveraged"]] +
                 [date.fromisoformat(x["lev_date"]) for x in m["matched_pairs"]])
    return und, lev


def lag_diagnostics(pr):
    """가장 가까운 교차끼리 1:1 정렬했을 때 '레버리지 교차 - 기초 교차'의 지연 분포."""
    out = {}
    for etype in ("CROSS_UP", "CROSS_DOWN"):
        und, lev = _events_in_overlap(pr, etype)
        m = match_events(und, lev, tol_days=SEQUENCE_TOLERANCE_DAYS)
        lags = [(date.fromisoformat(x["lev_date"]) - date.fromisoformat(x["und_date"])).days / 7.0
                for x in m["matched_pairs"]]
        out[etype] = {
            "n_aligned": len(lags),
            "median_lag_weeks": _r(_median(lags), 1),
            "mean_lag_weeks": _r(_mean(lags), 1),
            "min_lag_weeks": _r(min(lags), 1) if lags else None,
            "max_lag_weeks": _r(max(lags), 1) if lags else None,
            "n_leveraged_later": sum(1 for x in lags if x > 0),
            "n_same_week": sum(1 for x in lags if x == 0),
            "n_leveraged_earlier": sum(1 for x in lags if x < 0),
            "lags_weeks": [_r(x, 1) for x in sorted(lags)],
        }
    return out


def tolerance_curve(pr, tolerances_weeks=(1, 2, 3, 4)):
    """허용오차를 넓히면 대칭 일치율이 얼마나 오르는가(서술용). 사전 등록 판정은 ±1주 하나뿐이다."""
    out = {}
    for etype in ("CROSS_UP", "CROSS_DOWN", "ALL"):
        und, lev = _events_in_overlap(pr, etype)
        out[etype] = {f"+-{w}w": match_events(und, lev, tol_days=7 * w)["symmetric_match_rate_pct"]
                      for w in tolerances_weeks}
    return out


def sequence_paired_diff(pr):
    """±1주 조건 없이 가장 가까운 사이클끼리 정렬했을 때의 수익률 차이(서술용)."""
    u = {r["cross_date"]: r for r in pr["arm_underlying_signal"] if r.get("tradable")}
    l = {r["cross_date"]: r for r in pr["arm_leveraged_signal"] if r.get("tradable")}
    m = match_events(sorted(date.fromisoformat(d) for d in u),
                     sorted(date.fromisoformat(d) for d in l),
                     tol_days=SEQUENCE_TOLERANCE_DAYS)
    rows = []
    for x in m["matched_pairs"]:
        a, b = u[x["und_date"]], l[x["lev_date"]]
        rows.append({
            "und_cross_date": x["und_date"], "lev_cross_date": x["lev_date"],
            "lag_weeks": _r(x["gap_days"] / 7.0, 1),
            "und_signal_return_pct": a["return_pct"], "lev_signal_return_pct": b["return_pct"],
            "return_diff_pct_points": round(a["return_pct"] - b["return_pct"], 2),
            "und_signal_mdd_close_pct": a["mdd_close_pct"], "lev_signal_mdd_close_pct": b["mdd_close_pct"],
        })
    diffs = [r["return_diff_pct_points"] for r in rows]
    return {
        "n_aligned": len(rows),
        "n_underlying_cycles_unaligned": len(m["unmatched_underlying"]),
        "n_leveraged_cycles_unaligned": len(m["unmatched_leveraged"]),
        "median_diff_pct_points": _r(_median(diffs)),
        "abs_of_median_diff_pct_points": _r(abs(_median(diffs))) if diffs else None,
        "median_abs_diff_pct_points": _r(_median([abs(d) for d in diffs])),
        "mean_diff_pct_points": _r(_mean(diffs)),
        "cycles": rows,
    }


def post_inception_match(pr, skip_weeks=104):
    """레버리지 ETF 상장 후 skip_weeks 이내 교차를 빼고 다시 계산한 일치율(민감도, 판정 미사용)."""
    lev_start = date.fromisoformat(pr["data"]["leveraged_range"][0])
    cutoff = lev_start + timedelta(weeks=skip_weeks)
    out = {"leveraged_first_bar": lev_start.isoformat(), "cutoff": cutoff.isoformat()}
    for etype in ("CROSS_UP", "CROSS_DOWN", "ALL"):
        und, lev = _events_in_overlap(pr, etype)
        und = [d for d in und if d >= cutoff]
        lev = [d for d in lev if d >= cutoff]
        m = match_events(und, lev)
        out[etype] = {k: v for k, v in m.items() if k != "matched_pairs"}
    return out


# ---------------------------------------------------------------------------
# 자체 검증
# ---------------------------------------------------------------------------

def self_check(pair_results):
    problems = []
    checks = []

    # 1) 기초 ETF 첫 주봉이 사전 등록 표와 일치하는가
    for spec, pr in zip(PAIRS, pair_results):
        got = pr["data"]["underlying_range"][0]
        ok = got == spec["und_expected_first_bar"]
        checks.append({"check": f"{spec['und']} 첫 주봉", "expected": spec["und_expected_first_bar"],
                       "actual": got, "passed": ok})
        if not ok:
            problems.append(f"{spec['und']} 첫 주봉 불일치: {got} != {spec['und_expected_first_bar']}")

    # 2) 확장 표본 CROSS_UP 사이클 수가 사전 등록의 사전 확인값(총 75)과 일치하는가
    total = 0
    for spec, pr in zip(PAIRS, pair_results):
        got = pr["cycle_counts"]["underlying_cross_up_full_history"]
        total += got
        ok = got == spec["und_expected_cross_up_count"]
        checks.append({"check": f"{spec['und']} CROSS_UP 사이클 수",
                       "expected": spec["und_expected_cross_up_count"], "actual": got, "passed": ok})
        if not ok:
            problems.append(f"{spec['und']} CROSS_UP 수 불일치: {got} != {spec['und_expected_cross_up_count']}")
    checks.append({"check": "확장 표본 총 사이클 수", "expected": 75, "actual": total, "passed": total == 75})
    if total != 75:
        problems.append(f"확장 표본 총 사이클 수 불일치: {total} != 75")

    # 3) ISO 주 중복 제거가 실시간 시세 행(주 시작 정렬이 아닌 행)만 제거했는가
    for pr in pair_results:
        for side in ("underlying_dropped", "leveraged_dropped"):
            for d in pr["data"][side]["duplicate_iso_week_rows_dropped"]:
                if date.fromisoformat(d).weekday() == 0:
                    problems.append(f"{pr['pair']} {side}: 월요일 정렬 주봉을 중복으로 제거함 {d}")
    checks.append({"check": "ISO 주 중복 제거가 주 시작 정렬 행을 제거하지 않음",
                   "passed": not any("중복으로 제거" in p for p in problems)})

    # 4) 미완결 주봉이 제외됐는가 (마지막 완결 주봉 + 4일 <= 2026-08-31)
    for pr in pair_results:
        for side, key in (("underlying", "underlying_range"), ("leveraged", "leveraged_range")):
            last = date.fromisoformat(pr["data"][key][1])
            if last + timedelta(days=4) > SIGNAL_END:
                problems.append(f"{pr['pair']} {side}: 미완결 주봉이 남아 있음 {last}")
    checks.append({"check": "모든 시계열의 마지막 주봉이 완결 주봉",
                   "passed": not any("미완결 주봉이 남아" in p for p in problems)})

    # 5) 두 팔이 같은 거래 대상(레버리지 ETF)을 쓰는가 — 같은 교차일에 진입가가 동일해야 한다
    for pr in pair_results:
        u = {r["cross_date"]: r for r in pr["arm_underlying_signal"] if r.get("tradable")}
        l = {r["cross_date"]: r for r in pr["arm_leveraged_signal"] if r.get("tradable")}
        for d in set(u) & set(l):
            if abs(u[d]["entry_price"] - l[d]["entry_price"]) > 1e-9:
                problems.append(f"{pr['pair']} {d}: 두 팔의 진입가가 다름(거래 대상 불일치)")
    checks.append({"check": "두 팔의 거래 대상이 동일(같은 교차일 진입가 일치)",
                   "passed": not any("진입가가 다름" in p for p in problems)})

    return {"checks": checks, "problems": problems, "passed": not problems}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json-out", default=None, help="사이클 단위 원자료 포함 전체 결과 JSON 경로")
    ap.add_argument("--self-check", action="store_true", help="사전 등록 표본 크기·데이터 위생 회귀 검증")
    args = ap.parse_args()

    pair_results = [run_pair(p) for p in PAIRS]

    out = {
        "generated_for": "research/reports/track-a-underlying-etf-signal-backtest.md",
        "preregistration": "research/reports/strategy-engine-evidence-review.md 6.1 (후보 A)",
        "signal_window_end": SIGNAL_END.isoformat(),
        "assumptions": {
            "hypothesis": ("10/40 주봉 신호를 레버리지 ETF 대신 기초 지수 ETF 가격으로 계산해도 "
                           "사이클 판정과 결과가 실질적으로 동일하다"),
            "arms": {
                "arm_leveraged_signal": "레버리지 ETF 주봉으로 신호 계산 -> 레버리지 ETF 매매 (현행 엔진 기준선)",
                "arm_underlying_signal": "기초 지수 ETF 주봉으로 신호 계산 -> 레버리지 ETF 매매 (후보)",
            },
            "entry": "CROSS_UP 주(weeksSinceCross == 0) 종가 x (1 + 0.0015)",
            "exit": "이후 첫 CROSS_DOWN 주 종가 x (1 - 0.0015). 미청산 사이클은 마지막 완결 주봉 종가로 마크투마켓(슬리피지 미적용)",
            "sma": f"{SHORT_PERIOD}/{LONG_PERIOD} 주봉 단순이동평균 (entry_delay_cycle_backtest.py 재사용)",
            "slippage_pct_one_way": SLIPPAGE_PCT,
            "commission_pct_one_way": COMMISSION_PCT,
            "match_tolerance_days": MATCH_TOLERANCE_DAYS,
            "primary_match_metric": "symmetric_match_rate_pct = 2 x 매칭수 / (기초 교차수 + 레버리지 교차수)",
            "train_test_split": {"train_end": TRAIN_END, "test_start": TEST_START},
            "walk_forward_splits": WALK_FORWARD_SPLITS,
            "price_series": "Yahoo 주봉 adjclose(분할+배당 조정), low는 (adjclose/close) 비율로 환산",
            "no_synthetic_leverage": ("레버리지 ETF 상장 이전 구간의 레버리지 수익률을 합성하지 않는다. "
                                      "그 구간은 신호(사이클) 표본으로만 사용한다."),
            "thresholds": {
                "support_match_rate_min_pct": SUPPORT_MATCH_RATE_MIN,
                "support_abs_median_return_diff_max_pp": SUPPORT_MEDIAN_ABS_DIFF_MAX,
                "reject_match_rate_below_pct": REJECT_MATCH_RATE_MAX,
                "reject_abs_median_return_diff_above_pp": REJECT_MEDIAN_ABS_DIFF_MIN,
            },
        },
        "pairs": {pr["pair"]: {k: v for k, v in pr.items()
                               if k not in ("arm_underlying_signal", "arm_leveraged_signal",
                                            "paired_cycles", "aux_underlying_own_cycles")}
                  for pr in pair_results},
    }

    # --- 전 구간 판정 -------------------------------------------------------
    out["verdict_full_overlap"] = scope_verdict(pair_results)

    # --- 학습/검증 분리 -----------------------------------------------------
    out["train_test"] = {
        "train": scope_verdict(pair_results, cross_filter=lambda d: d <= TRAIN_END),
        "test": scope_verdict(pair_results, cross_filter=lambda d: d >= TEST_START),
    }

    # --- 워크포워드 4분할 ---------------------------------------------------
    wf = {}
    for cutoff in WALK_FORWARD_SPLITS:
        wf[cutoff] = {
            "train": scope_verdict(pair_results, cross_filter=lambda d, c=cutoff: d < c),
            "test": scope_verdict(pair_results, cross_filter=lambda d, c=cutoff: d >= c),
        }
    out["walk_forward"] = wf
    directions = []
    for cutoff, e in wf.items():
        directions.append(e["train"]["verdict"])
        directions.append(e["test"]["verdict"])
    out["walk_forward_direction_consistent"] = len(set(d for d in directions if d != "no_data")) <= 1
    out["walk_forward_verdicts"] = directions

    # --- 중복 표본 표기 -----------------------------------------------------
    dup_paired, new_paired = [], []
    for pr in pair_results:
        for p in pr["paired_cycles"]:
            (dup_paired if p["in_existing_track_a_sample"] else new_paired).append(p)
    out["existing_sample_overlap"] = {
        "note": ("기존 Track A 표본과 겹치는 사이클(SOXL/TQQQ 2010~, TNA/FAS 2008~)을 중복 표본으로 표기한다. "
                 "겹치는 구간 자체가 레버리지 ETF 존재 구간이므로 이 백테스트의 수익률 비교는 사실상 전부 중복 표본이다."),
        "n_paired_in_existing_sample": len(dup_paired),
        "n_paired_outside_existing_sample": len(new_paired),
        "dedup_return_diff": diff_stats(new_paired),
    }

    # --- 보조 기록 (판정 미사용): 확장 표본 75사이클, 기초 ETF 자체 수익률 ----
    aux_rows = []
    per_pair_aux = {}
    for pr in pair_results:
        rows = pr["aux_underlying_own_cycles"]
        aux_rows.extend(rows)
        pre = [r for r in rows
               if r["cross_date"] < pr["data"]["overlap_window"][0]]
        per_pair_aux[pr["pair"]] = {
            "underlying": pr["underlying"],
            "n_cycles": len(rows),
            "n_cycles_before_leveraged_etf_existed": len(pre),
            "all": return_stats(rows),
            "before_leveraged_etf_existed": return_stats(pre),
        }
    out["auxiliary_extended_sample"] = {
        "note": ("사전 등록 '보조 기록(판정에는 쓰지 않음)'. 기초 ETF **자체**를 10/40 규칙으로 매매했을 때의 "
                 "실측치이며, 레버리지 수익률이 아니다. 이 수치를 근거로 10/40이나 4주 컷오프를 바꾸자고 "
                 "제안하지 않는다 — 그것은 별도 사전 등록이 필요한 다른 질문이다."),
        "total_cycles": len(aux_rows),
        "pooled": return_stats(aux_rows),
        "per_pair": per_pair_aux,
    }

    # --- 보조 진단 (사전 등록 목록 밖 — 판정에 사용하지 않음) ------------------
    # 판정은 위에서 이미 끝났다. 아래는 "왜 그런 결과가 나왔는가"를 설명하기 위한 서술 통계이며,
    # 사전 등록된 파라미터(±1주, 80%/60%, 5%p/15%p)를 하나도 바꾸지 않는다.
    out["auxiliary_diagnostics"] = {
        "note": ("사전 등록 판정에 사용하지 않는 서술 통계다. 사전 등록 임계값을 바꾸지 않으며, "
                 "이 수치를 근거로 판정을 뒤집지 않는다. 목적은 기각 결과의 원인을 기록하는 것이다."),
        "cross_lag": {pr["pair"]: lag_diagnostics(pr) for pr in pair_results},
        "match_rate_by_tolerance_weeks": {pr["pair"]: tolerance_curve(pr) for pr in pair_results},
        "arm_aggregate_no_pairing": {pr["pair"]: {
            "arm_underlying_signal": return_stats(pr["arm_underlying_signal"]),
            "arm_leveraged_signal": return_stats(pr["arm_leveraged_signal"]),
        } for pr in pair_results},
        "sequence_paired_return_diff": {pr["pair"]: sequence_paired_diff(pr) for pr in pair_results},
        "excluding_leveraged_first_104_weeks": {
            "note": ("레버리지 ETF 상장 직후에는 40주 SMA가 상장 이후 데이터만으로 계산되어 "
                     "기초 ETF의 40주 SMA와 구조적으로 다르다(예: FAS 2009-08-31 CROSS_UP은 상장 41주차). "
                     "이 구조적 왜곡이 기각 결과를 만든 것인지 확인하기 위한 민감도이며, 판정에 쓰지 않는다."),
            "per_pair": {pr["pair"]: post_inception_match(pr) for pr in pair_results},
        },
    }

    if args.self_check:
        out["self_check"] = self_check(pair_results)

    if args.json_out:
        detail = dict(out)
        detail["cycles_detail"] = {
            pr["pair"]: {
                "arm_underlying_signal": pr["arm_underlying_signal"],
                "arm_leveraged_signal": pr["arm_leveraged_signal"],
                "paired_cycles": pr["paired_cycles"],
                "aux_underlying_own_cycles": pr["aux_underlying_own_cycles"],
                "cross_week_match_pairs": {k: v["matched_pairs"] for k, v in pr["cross_week_match"].items()},
            } for pr in pair_results
        }
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(detail, f, ensure_ascii=False, indent=2)
        print(f"[저장] {args.json_out}", file=sys.stderr)

    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

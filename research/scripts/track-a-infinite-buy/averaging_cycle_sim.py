#!/usr/bin/env python3
"""분할 평균화 사이클(averaging cycle) 일봉 시뮬레이터.

외부 "무한매수법 V4.0" 방법론을 일반 원리로 재정의한 검증용 시뮬레이터다.
원문 규칙·표는 옮기지 않았고, 아래 일반 원리와 탐색 파라미터로만 구성한다.

일반 원리
---------
- 종목별 예산(원금) 1.0을 N분할한다. T = 투입 회차(원가 기준 투입 규모 / 1회분).
  1회분 = 잔금 / (N - T). 매수하면 T += 지출/1회분, 매도하면 T *= (1 - 매도비율).
- 기준선 = 평단 * (1 + s(T)). 동적 모드는 s(T) = s0 * (1 - 2T/N)로 T가 커질수록 낮아진다
  (T=N/2에서 0, T=N에서 -s0). 정적 모드는 s를 상수로 고정한다(H1 비교군).
- 매수: 종가가 기준선 이하일 때만 체결(전반전 T<N/2는 1회분의 절반을 기준선에, 나머지
  절반을 평단에 조건부 체결, 후반전은 1회분 전체를 기준선에 조건부 체결).
- 매도: 종가가 기준선 이상이면 보유량의 1/4을 종가에 매도. 나머지(3/4)는 평단*(1+s0)
  지정가.
- 소진(T > N-1): reverse=True이면 시간 기반 점진 감량 모드. 첫날 보유량의 2/N을 종가에
  무조건 매도, 이후 직전 5거래일 종가 평균을 기준선으로 종가>=기준선이면 2/N 매도,
  종가<기준선이면 잔금의 1/4 매수. 종가가 평단*(1-s0)를 넘으면 일반 모드 복귀.
  reverse=False이면 추가 매수 없이 목표가 또는 기간 종료까지 보유한다.
- 체결 가정: 조건부(LOC) 주문은 당일 종가 체결. 지정가 목표는 fill='opt'이면 당일 고가가
  목표 이상일 때 max(목표, 시가)에, fill='cons'이면 종가가 목표 이상일 때 목표가에 체결.
- 비용: 모든 체결에 편도 슬리피지 slip, 수수료 0, 소수 주식 허용(정수 주문 제약 무시).
- 세금(선택): 역년별 실현손익 합이 양수이면 22%. 기본공제·손실 이월 없음.
"""
import math
from dataclasses import dataclass
from datetime import datetime

EPS = 1e-4  # 원 규칙의 "기준선 - 0.01"에 대응하는 상대 오차
TAX_RATE = 0.22


@dataclass(frozen=True)
class Params:
    n_splits: int = 40
    s0: float = 0.15
    line_mode: str = "dynamic"  # dynamic | static0 | statichalf
    reverse: bool = True
    slip: float = 0.0015
    fill: str = "cons"  # opt | cons
    trend_filter: bool = False


class Bars:
    """일봉 + 주봉 추세 파생값."""

    def __init__(self, dates, o, h, l, c):
        self.dates, self.o, self.h, self.l, self.c = dates, o, h, l, c
        self.n = len(c)
        self._build_weekly()

    @staticmethod
    def load(path):
        dates, o, h, l, c = [], [], [], [], []
        with open(path, encoding="utf-8") as f:
            next(f)
            for line in f:
                parts = line.strip().split(";")
                if len(parts) < 5:
                    continue
                try:
                    vals = [float(x) for x in parts[1:5]]
                except ValueError:
                    continue
                dates.append(datetime.strptime(parts[0], "%Y-%m-%d").date())
                o.append(vals[0])
                h.append(vals[1])
                l.append(vals[2])
                c.append(vals[3])
        return Bars(dates, o, h, l, c)

    def _build_weekly(self):
        weeks = []  # (last_daily_index, close)
        cur_key = None
        for i, d in enumerate(self.dates):
            key = d.isocalendar()[:2]
            if key != cur_key:
                weeks.append([i, self.c[i]])
                cur_key = key
            else:
                weeks[-1] = [i, self.c[i]]
        closes = [w[1] for w in weeks]
        sma10 = _sma(closes, 10)
        sma40 = _sma(closes, 40)
        m = len(weeks)
        above = [None] * m
        event = [None] * m
        for k in range(m):
            if sma10[k] is not None and sma40[k] is not None:
                above[k] = sma10[k] > sma40[k]
        for k in range(40, m):
            if sma10[k - 1] is None or sma40[k - 1] is None:
                continue
            ps, pl, cs, cl = sma10[k - 1], sma40[k - 1], sma10[k], sma40[k]
            if ps <= pl and cs > cl:
                event[k] = "UP"
            elif ps >= pl and cs < cl:
                event[k] = "DOWN"
            else:
                event[k] = "NONE"
        weeks_since = [None] * m
        last_up = None
        for k in range(m):
            if event[k] == "UP":
                last_up = k
            weeks_since[k] = (k - last_up) if last_up is not None else None
        # 각 일봉 i에서 "i 이전(엄격히)에 마감한" 최신 주봉 인덱스
        self.week_before = [None] * self.n
        wk = -1
        for i in range(self.n):
            while wk + 1 < m and weeks[wk + 1][0] < i:
                wk += 1
            self.week_before[i] = wk if wk >= 0 else None
        self.w_above, self.w_since = above, weeks_since
        self.first_trend_idx = next(
            (i for i in range(self.n)
             if self.week_before[i] is not None and above[self.week_before[i]] is not None),
            self.n,
        )

    def trend_above(self, i):
        w = self.week_before[i]
        return w is not None and self.w_above[w] is True

    def track_a_entry_ok(self, i):
        w = self.week_before[i]
        if w is None or self.w_above[w] is not True:
            return False
        s = self.w_since[w]
        return s is not None and s <= 4


def _sma(vals, period):
    out = [None] * len(vals)
    run = 0.0
    for i, v in enumerate(vals):
        run += v
        if i >= period:
            run -= vals[i - period]
        if i >= period - 1:
            out[i] = run / period
    return out


class _Tax:
    def __init__(self):
        self.year = None
        self.realized = 0.0
        self.paid = 0.0

    def roll(self, year):
        if self.year is not None and year != self.year:
            if self.realized > 0:
                self.paid += TAX_RATE * self.realized
            self.realized = 0.0
        self.year = year

    def finalize(self, unrealized):
        net = self.realized + unrealized
        return self.paid + (TAX_RATE * net if net > 0 else 0.0)


def _summarize(B, s, e, equity_final, mdd, tax, unrealized, cycles, idle_days, extra=None):
    days = (B.dates[e] - B.dates[s]).days or 1
    tax_total = tax.finalize(unrealized)
    at_final = equity_final - tax_total
    out = {
        "final": equity_final,
        "final_after_tax": at_final,
        "cagr": equity_final ** (365.25 / days) - 1 if equity_final > 0 else -1.0,
        "cagr_after_tax": at_final ** (365.25 / days) - 1 if at_final > 0 else -1.0,
        "mdd": mdd,
        "cycles": cycles,
        "idle_frac": idle_days / (e - s + 1),
    }
    if extra:
        out.update(extra)
    return out


def sim_ac(B, s, e, p, tax_on=True):
    """분할 평균화 사이클 시뮬레이션. s..e(포함) 구간, 시작 자본 1.0."""
    N = p.n_splits
    c, o, h, dates = B.c, B.o, B.h, B.dates
    cash, shares, avg, T = 1.0, 0.0, 0.0, 0.0
    mode = "flat"  # flat | normal | reverse
    rev_first = False
    cyc_start_i = cyc_start_eq = None
    cyc_reverse = False
    cycles = []
    tax = _Tax()
    peak, mdd = 1.0, 0.0
    idle_days = 0
    rev_days = 0
    exhaust = 0
    expo_sum = 0.0

    for i in range(s, e + 1):
        tax.roll(dates[i].year)
        px = c[i]
        buy_px = px * (1 + p.slip)
        sell_px = px * (1 - p.slip)

        if mode == "flat":
            if p.trend_filter and not B.trend_above(i):
                idle_days += 1
            else:
                unit = cash / N
                qty = unit / buy_px
                shares, avg, T = qty, buy_px, 1.0
                cash -= unit
                mode = "normal"
                cyc_start_i, cyc_start_eq, cyc_reverse = i, unit * N, False
        elif mode == "normal":
            sh0 = shares
            if p.line_mode == "dynamic":
                sl = p.s0 * (1 - 2 * T / N)
            elif p.line_mode == "static0":
                sl = 0.0
            else:
                sl = p.s0 / 2
            line = avg * (1 + sl)
            tp = avg * (1 + p.s0)
            can_buy = T <= N - 1
            unit = cash / (N - T) if can_buy else 0.0
            T_start = T
            sold_qty = 0.0
            if px >= line:
                q = 0.25 * sh0
                cash += q * sell_px
                tax.realized += q * (sell_px - avg)
                sold_qty += q
            hit = (h[i] >= tp) if p.fill == "opt" else (px >= tp)
            if hit:
                q = 0.75 * sh0
                fill_px = max(tp, o[i]) if p.fill == "opt" else tp
                fill_px *= (1 - p.slip)
                cash += q * fill_px
                tax.realized += q * (fill_px - avg)
                sold_qty += q
            if sold_qty > 0:
                shares -= sold_qty
                T *= (1 - sold_qty / sh0)
            amt = 0.0
            if can_buy:
                if T_start < N / 2:
                    if px <= line * (1 - EPS):
                        amt += 0.5 * unit
                    if px <= avg:
                        amt += 0.5 * unit
                elif px <= line * (1 - EPS):
                    amt = unit
                amt = min(amt, cash)
            if amt > 0:
                qty = amt / buy_px
                avg = (avg * shares + buy_px * qty) / (shares + qty) if shares > 1e-15 else buy_px
                shares += qty
                cash -= amt
                T += amt / unit
            if shares <= 1e-15:
                shares = 0.0
            if p.reverse and shares > 0 and T > N - 1:
                mode, rev_first = "reverse", True
                exhaust += 1
                cyc_reverse = True
        else:  # reverse
            rev_days += 1
            f = 2.0 / N
            ref = sum(c[i - 5:i]) / 5.0 if i >= 5 else px
            sell = False
            if rev_first:
                sell, rev_first = True, False
            elif px >= ref:
                sell = True
            elif px <= ref * (1 - EPS) and T < N:
                amt = cash * 0.25
                if amt > 0:
                    qty = amt / buy_px
                    avg = (avg * shares + buy_px * qty) / (shares + qty)
                    shares += qty
                    cash -= amt
                    T += (N - T) * 0.25
            if sell and shares > 0:
                q = f * shares
                cash += q * sell_px
                tax.realized += q * (sell_px - avg)
                shares -= q
                T *= (1 - f)
            if shares > 0 and px > avg * (1 - p.s0):
                mode = "normal"

        if mode != "flat" and shares <= 1e-15:
            shares = 0.0
            cycles.append({
                "start": dates[cyc_start_i].isoformat(), "end": dates[i].isoformat(),
                "ret": cash / cyc_start_eq - 1, "days": i - cyc_start_i,
                "reverse": cyc_reverse, "open": False,
            })
            mode, T, avg = "flat", 0.0, 0.0

        eq = cash + shares * px
        expo_sum += shares * px / eq
        peak = max(peak, eq)
        mdd = min(mdd, eq / peak - 1)

    unreal = shares * (c[e] * (1 - p.slip) - avg) if shares > 0 else 0.0
    eq_final = cash + shares * c[e]
    if mode != "flat":
        cycles.append({
            "start": dates[cyc_start_i].isoformat(), "end": dates[e].isoformat(),
            "ret": eq_final / cyc_start_eq - 1, "days": e - cyc_start_i,
            "reverse": cyc_reverse, "open": True,
        })
    return _summarize(B, s, e, eq_final, mdd, tax, unreal, cycles, idle_days,
                      {"rev_days": rev_days, "exhaust": exhaust, "expo": expo_sum / (e - s + 1)})


def sim_buy_hold(B, s, e, slip=0.0015):
    tax = _Tax()
    qty = 1.0 / (B.c[s] * (1 + slip))
    peak, mdd = 1.0, 0.0
    for i in range(s, e + 1):
        tax.roll(B.dates[i].year)
        eq = qty * B.c[i]
        peak = max(peak, eq)
        mdd = min(mdd, eq / peak - 1)
    eq_final = qty * B.c[e]
    unreal = qty * (B.c[e] * (1 - slip)) - 1.0
    return _summarize(B, s, e, eq_final, mdd, tax, unreal, [], 0, {"expo": 1.0})


def sim_track_a(B, s, e, slip=0.0015):
    """주봉 10/40 추세 규칙(0~4주 진입, CROSS_DOWN 청산). 신호는 직전 마감 주봉, 체결은 다음 거래일 시가."""
    cash, shares, avg = 1.0, 0.0, 0.0
    tax = _Tax()
    cycles = []
    cyc_i = cyc_eq = None
    peak, mdd = 1.0, 0.0
    idle = 0
    expo_sum = 0.0
    for i in range(s, e + 1):
        tax.roll(B.dates[i].year)
        if shares == 0:
            if B.track_a_entry_ok(i):
                px = B.o[i] * (1 + slip)
                shares, avg = cash / px, px
                cyc_i, cyc_eq, cash = i, cash, 0.0
            else:
                idle += 1
        elif not B.trend_above(i):
            px = B.o[i] * (1 - slip)
            tax.realized += shares * (px - avg)
            cash = shares * px
            cycles.append({"start": B.dates[cyc_i].isoformat(), "end": B.dates[i].isoformat(),
                           "ret": cash / cyc_eq - 1, "days": i - cyc_i, "reverse": False, "open": False})
            shares = 0.0
        eq = cash + shares * B.c[i]
        expo_sum += shares * B.c[i] / eq
        peak = max(peak, eq)
        mdd = min(mdd, eq / peak - 1)
    eq_final = cash + shares * B.c[e]
    unreal = shares * (B.c[e] * (1 - slip) - avg) if shares > 0 else 0.0
    if shares > 0:
        cycles.append({"start": B.dates[cyc_i].isoformat(), "end": B.dates[e].isoformat(),
                       "ret": eq_final / cyc_eq - 1, "days": e - cyc_i, "reverse": False, "open": True})
    return _summarize(B, s, e, eq_final, mdd, tax, unreal, cycles, idle, {"expo": expo_sum / (e - s + 1)})


def percentile(vals, q):
    if not vals:
        return float("nan")
    v = sorted(vals)
    k = (len(v) - 1) * q
    lo, hi = math.floor(k), math.ceil(k)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def sim_const_exposure(B, s, e, frac):
    """통제 벤치마크: 매일 종가에 노출 비중을 frac으로 재조정하는 종목+현금(수익 0) 혼합. 비용·세금 무시(벤치마크에 유리)."""
    eq, peak, mdd = 1.0, 1.0, 0.0
    for i in range(s + 1, e + 1):
        eq *= 1 + frac * (B.c[i] / B.c[i - 1] - 1)
        peak = max(peak, eq)
        mdd = min(mdd, eq / peak - 1)
    return eq, mdd

# Track A 시장 국면 필터(SPY 40주선) 검증 — 신규 진입 제한이 MDD를 줄이는가

## 배경과 목적

`track-a-entry-delay-cutoff-review.md`가 채택된 이후, Track A(SOXL/TQQQ) 미보유 후보의 `BUY` 조건은
`trend == ABOVE_LONG_AVERAGE && weeksSinceCross <= 4`다(`research/STRATEGY_ENGINE_POLICY.md`). 손절
(포지션 종료) 레이어는 고정비율·ATR·추적손절 세 접근 모두 채택 가능한 후보가 없었다
(`track-a-stoploss-drawdown-review.md`, `track-a-stoploss-revalidation-and-sizing-design.md`,
`track-a-trailing-stop-review.md`). 이 리포트는 손절 대신 **신규 진입 자체를 시장 전체 국면에 따라
제한**하는 접근을 검증한다 — 보유 종목의 강제 매도가 아니라, 미보유 후보의 `BUY` 판단에 한 겹의
필터를 추가하는 것이다.

## 사전 고정 가설 (결과를 보기 전에 고정, 세션 지시)

**SPY의 마지막 완료 주봉 종가가 SPY 40주 이동평균 위에 있을 때만 Track A의 신규 `BUY`를 허용한다.**

- Track A 자산의 기존 `BUY` 조건(`trend == ABOVE_LONG_AVERAGE && weeksSinceCross <= 4`)은 그대로
  유지한다 — 이 필터는 추가 조건이지, 기존 조건을 대체하지 않는다.
- 기존 조건을 충족해도 SPY가 40주선 아래면 그 시점은 `WATCH`로 본다(진입하지 않음).
- `weeksSinceCross` 0~4주 동안 매주 SPY 조건을 재평가하며, Track A 자산의 `trend`가
  `ABOVE_LONG_AVERAGE`로 유지되는 동안 두 조건이 처음 동시에 성립하는 주에 진입한다.
- `weeksSinceCross`가 4를 넘으면(Track A 자체 정책상 이미 `WATCH`) SPY가 그 뒤 회복돼도 그 사이클엔
  더 이상 진입하지 않는다.
- 손절·재진입·분할 매수·매도 규칙, SPY 40주선 외의 다른 시장 지표나 다른 이동평균 기간은 결과를 본
  뒤 추가하지 않았다. 이 문서는 이 하나의 가설만 검증한다.

## 방법론

### 베이스라인과 후보

- **베이스라인**: `weeksSinceCross <= 4` 정책, 시장 국면 필터 없음. 진입 시점은 항상 교차 당주
  (`delay=0`)다 — `trend == ABOVE_LONG_AVERAGE && weeksSinceCross == 0 <= 4` 조건이 교차 당주에
  이미 성립하므로, 기존 엔진은 그 주에 즉시 `BUY`를 낸다("기존 구현이 실제로 하는 그대로").
- **후보**: 베이스라인 조건에 더해, `weeksSinceCross == k`(`k = 0..4`)인 매 주마다 "SPY 마지막
  완료 주봉 종가 > SPY 40주 이동평균"을 재평가한다. 두 조건이 처음 동시에 성립하는 주에 진입하고,
  0~4주 동안 한 번도 성립하지 않으면 그 사이클은 "진입 없음(제외)"으로 기록한다.

### 사이클 재사용과 재계산

Track A 사이클(교차일→청산일) 목록은 `track-a-entry-delay-cutoff-review.md`에서 이미 검증된
**20개 사이클**(`soxl_twelvedata` 3 + `soxl_yahoo` 8 + `tqqq_yahoo` 9)을 그대로 인용했다. 다만
"필터 적용 후 실제 진입일"은 이번 리포트가 신규로 계산해야 하므로, `entry_delay_cycle_backtest.py`의
`load_candles`/`compute_signals`/`find_cycles`/`sma_series`/`mdd_close_and_intraweek` 함수를 그대로
`import`해 재사용하는 신규 스크립트(`research/data/tools/market_regime_filter_backtest.py`)로
사이클을 독립적으로 재계산했다. 재계산 결과 20개 사이클의 교차일·청산일·베이스라인 수익률이 기존
리포트와 (반올림 오차 이내로) 정확히 일치함을 확인했다(아래 "재현 검증" 절).

### 데이터 소스

| 자산 | provider | interval | adjustment | 기간 | 역할 |
|---|---|---|---|---|---|
| SOXL | Twelve Data | `1week` | splits | 2021-08-02~2026-08-03 | 신호(교차) — 기존 캐시 재사용, 프로덕션 소스 |
| SOXL | Yahoo Finance | weekly | Adjusted Close | 2010-03-08~2026-08-11 | 신호(교차) — 기존 캐시 재사용 |
| TQQQ | Yahoo Finance | weekly | Adjusted Close | 2010-02-08~2026-08-11 | 신호(교차) — 기존 캐시 재사용 |
| **SPY(신규)** | Yahoo Finance | `1wk` | close/open/high/low 미조정, adjclose만 분할+배당 조정 | 1993-01-25~2026-08-10 (완결 주봉) | 시장 국면 필터 |

SPY 신규 데이터 확보: `https://query2.finance.yahoo.com/v8/finance/chart/SPY?period1=0&period2=9999999999&interval=1wk&events=div,splits`
(로그인 불필요, curl로 직접 접근). Yahoo가 실제 상장일(`firstTradeDate=728317800`, 1993-01-29)부터
반환하는 가장 긴 주봉 이력을 받았다. `adjclose`를 종가로 사용해 40주 SMA를 계산했다. 파일:
`research/data/cache/spy-weekly-yahoo-1993-2026.csv`(+`.metadata.json`), 원본 JSON
`research/data/cache/spy-weekly-yahoo-raw.json`.

**한계**: 이 프로젝트의 프로덕션 시세 소스는 Twelve Data이지만, 이 리서치 역할은 API 키에 접근하지
않으므로 SPY는 Yahoo Finance 공개 API로 확보했다. 따라서 **SOXL/TQQQ(프로덕션 소스 일부 포함)와
SPY(전부 Yahoo)는 서로 다른 데이터 소스에 의존한다** — 두 소스의 주봉 확정 시점·조정 방식이 다를
경우(기존 리포트들이 SOXL 소스 간 1주 차이를 발견한 것처럼) 결과가 프로덕션에서 정확히 재현되지
않을 수 있다.

### 재현 검증 — 기존 사이클 목록과 대조

신규 스크립트가 재계산한 20개 사이클의 교차일·청산일·베이스라인(delay=0) 수익률을
`track-a-entry-delay-cutoff-review.md`와 대조한 결과, 사이클 수(20개), 교차일·청산일, 수익률
(소수점 첫째 자리 수준의 반올림 차이 이내)이 모두 일치했다. 예: `soxl_twelvedata` 2023-02-27→
2023-10-30 +17.2%(기존 +17.2%), `soxl_yahoo` 2019-03-25→2020-03-30 -40.4%(기존 -40.4%). 전체
20개 사이클 목록은 아래 "사이클별 결과" 표 참고.

## 핵심 결과

### 필터가 단 한 사이클도 배제하지 않았다

**20개 사이클 전부에서, 교차 당주(`weeksSinceCross == 0`, 베이스라인의 실제 진입 시점)에 SPY가
이미 40주 이동평균 위에 있었다.** 그 결과 후보(필터 적용) 시나리오의 진입 시점·진입가·수익률·MDD가
베이스라인과 **모든 사이클에서 완전히 동일**했다 — 필터가 단 하나의 진입도 지연시키거나 배제하지
않았다.

| 지표 | 베이스라인(무필터) | 후보(SPY 40주선 필터) |
|---|---|---|
| 진입 사이클 수 | 20/20 | 20/20 |
| 제외된 사이클 수 | - | 0/20 |
| 평균 수익률 | +110.0% | +110.0% (동일) |
| 중앙값 수익률 | +41.5% | +41.5% (동일) |
| 평균 MDD(종가) | -47.0% | -47.0% (동일) |
| 평균 MDD(주중저가) | -51.9% | -51.9% (동일) |
| MDD 1%p 개선당 포기 수익률 | - | **정의 불가(0.0pp ÷ 0.0pp)** — 개선도 포기도 없음 |
| 큰 수익 기회(상위 사분위, 5개) 중 놓친 비율 | - | 0/5 (0.0%) |

### 사이클별 결과 (20개 전부)

`entered=True`는 후보 시나리오에서 필터를 통과해 실제 진입했다는 뜻이고, `k`는 진입이 성사된
`weeksSinceCross`다. 20개 전부 `k=0` — 즉 베이스라인과 정확히 같은 주에 진입했다.

| 데이터셋 | 교차일 → 청산일(상태) | 베이스라인 수익률 | 후보 진입(k) |
|---|---|---|---|
| soxl_twelvedata | 2023-02-27 → 2023-10-30 (청산) | +17.2% | 진입, k=0 |
| soxl_twelvedata | 2023-12-04 → 2024-09-09 (청산) | +41.0% | 진입, k=0 |
| soxl_twelvedata | 2025-08-04 → 2026-08-03 (미청산) | +445.5% | 진입, k=0 |
| soxl_yahoo | 2012-02-20 → 2012-06-04 (청산) | -35.0% | 진입, k=0 |
| soxl_yahoo | 2013-01-21 → 2015-07-27 (청산) | +215.4% | 진입, k=0 |
| soxl_yahoo | 2016-04-18 → 2018-08-13 (청산) | +475.6% | 진입, k=0 |
| soxl_yahoo | 2019-03-25 → 2020-03-30 (청산) | -40.4% | 진입, k=0 |
| soxl_yahoo | 2020-08-10 → 2022-02-28 (청산) | +111.5% | 진입, k=0 |
| soxl_yahoo | 2023-02-27 → 2023-10-30 (청산) | +17.8% | 진입, k=0 |
| soxl_yahoo | 2023-12-04 → 2024-09-09 (청산) | +41.9% | 진입, k=0 |
| soxl_yahoo | 2025-07-28 → 2026-08-11 (미청산) | +452.5% | 진입, k=0 |
| tqqq_yahoo | 2012-01-30 → 2012-11-26 (청산) | +13.7% | 진입, k=0 |
| tqqq_yahoo | 2013-01-28 → 2015-09-21 (청산) | +215.8% | 진입, k=0 |
| tqqq_yahoo | 2015-11-23 → 2016-01-18 (청산) | -26.6% | 진입, k=0 |
| tqqq_yahoo | 2016-07-25 → 2018-11-19 (청산) | +115.5% | 진입, k=0 |
| tqqq_yahoo | 2019-04-15 → 2020-04-06 (청산) | -14.5% | 진입, k=0 |
| tqqq_yahoo | 2020-06-15 → 2022-02-14 (청산) | +114.8% | 진입, k=0 |
| tqqq_yahoo | 2023-03-27 → 2025-03-31 (청산) | +49.5% | 진입, k=0 |
| tqqq_yahoo | 2025-07-07 → 2026-03-23 (청산) | -7.0% | 진입, k=0 |
| tqqq_yahoo | 2026-05-04 → 2026-08-11 (미청산) | -4.0% | 진입, k=0 |

### 왜 필터가 한 번도 발동하지 않았는가 — 메커니즘 점검

SPY 40주 SMA 계산 자체가 정상 작동하는지 먼저 확인했다: 1993~2026년 전체 1,712개 완결 주봉 중
398개(23.2%)가 "SPY 종가 < 40주선"이었고, 2008-10(-리먼 사태), 2009-03(바닥), 2020-03(코로나
폭락), 2022-06/2022-10(2022년 약세장) 같은 알려진 하락장에서 정확히 `below`로 판정됐다 — SMA
계산 로직 자체는 정상이다.

그런데도 필터가 발동하지 않은 이유는 **10주/40주 이동평균 교차의 구조적 지연성**으로 보인다. SOXL/
TQQQ 같은 3배 레버리지 자산의 10주 평균이 40주 평균을 상향 돌파하려면 상당 기간의 지속적 반등이
필요하다 — 즉 이 신호 자체가 이미 시장 저점에서 몇 주~몇 달 지난 시점에만 발생한다. 그 시점에는
변동성이 훨씬 낮은 SPY가 이미 자신의 40주선을 회복해 있는 경우가 이번 표본에서는 항상 성립했다.
참고로(새 필터를 추가한 것이 아니라 기존 SPY 시계열의 서술적 맥락), 각 Track A 교차일 시점에 SPY가
연속으로 40주선 위에 머문 주 수(그 주 포함)를 확인하면 최소 2주(TQQQ 2015-11-23 사이클)에서 최대
21주(TQQQ 2016-07-25 사이클)였다 — 즉 여유가 거의 없었던 경우(2주)도 있었지만, 20개 전부에서
SPY가 먼저 회복해 있었다.

### 티커별 분해

| | SOXL(11개) | TQQQ(9개) |
|---|---|---|
| 베이스라인 평균 수익률 | +158.5% | +50.8% |
| 베이스라인 평균 MDD(종가) | -52.8% | -39.9% |
| 후보 제외 사이클 수 | 0/11 | 0/9 |
| 후보 평균 수익률/MDD | 베이스라인과 완전히 동일 | 베이스라인과 완전히 동일 |

효과(없음)가 SOXL·TQQQ 어느 한쪽에만 의존한다는 근거는 없다 — 둘 다 동일하게 "제외 0건"이었다.

### 워크포워드 검증 (2018/2019/2020/2021-01-01, 사전 고정)

| 분할 시점 | 학습 n | 검증 n | 학습 제외 | 검증 제외 | 학습 베이스라인 평균수익률/MDD | 검증 베이스라인 평균수익률/MDD |
|---|---|---|---|---|---|---|
| 2018-01-01 | 7 | 13 | 0 | 0 | +139.2% / -36.8% | +94.3% / -52.5% |
| 2019-01-01 | 7 | 13 | 0 | 0 | +139.2% / -36.8% | +94.3% / -52.5% |
| 2020-01-01 | 9 | 11 | 0 | 0 | +102.2% / -45.1% | +116.4% / -48.5% |
| 2021-01-01 | 11 | 9 | 0 | 0 | +104.2% / -45.3% | +117.2% / -49.0% |

(2018-01-01과 2019-01-01 분할의 학습 구간이 완전히 동일한 것은 우연이 아니라, 2018년 중 새로
시작된 교차 사이클이 없었기 때문이다 — `cross_date` 목록에 2018년 신규 진입이 없음.)

**4개 분할 전부(학습·검증 8개 블록)에서 제외 0건, 효과 0.0으로 완전히 일관됐다.** 방향이 뒤집히는
현상 자체가 없다 — 다만 이는 "효과가 안정적으로 긍정적"이라서가 아니라 "효과 자체가 관측되지 않아서"
일관된 것이라는 점을 구분해야 한다.

## 채택 기준 대조 (세션 지시의 5가지 기준)

| # | 기준 | 판정 | 근거 |
|---|---|---|---|
| 1 | 무필터 기준선보다 MDD 또는 큰 손실 노출이 의미 있게 줄어든다 | **미충족** | MDD 개선 0.0%p — 필터가 20개 사이클 중 단 하나도 배제하지 않아 측정 가능한 개선이 없음 |
| 2 | 수익 기회 포기가 과도하지 않다 | 충족(자명) | 포기한 수익률 0.0%p, 놓친 큰 기회 0/5(0.0%) — 다만 애초에 아무것도 제외되지 않았기 때문 |
| 3 | 사전 고정한 4개 워크포워드 분할에서 결과 방향이 일관된다 | 충족(자명) | 8개 학습/검증 블록 전부 제외 0건으로 일관 — 다만 "일관된 효과"가 아니라 "일관된 무효과" |
| 4 | SOXL과 TQQQ 중 한 종목에만 효과가 의존하지 않는다 | 충족(자명) | SOXL·TQQQ 둘 다 제외 0건 — 다만 애초에 아무 효과가 없어 의존할 효과 자체가 없음 |
| 5 | 장 시작 전 확정 주봉 데이터만으로 실행 가능하다 | **충족** | SPY와 Track A 자산 모두 같은 주 금요일 종가로 확정되며, 다음 거래일 장 시작 전 두 값 모두 알 수 있음. 장중 데이터·미래 정보 사용 없음 |

**5가지 중 기준 1을 충족하지 못한다.** 기준 2·3·4는 형식적으로는 충족하지만, 이는 필터가 실제로
효과적이어서가 아니라 **이 20개 사이클 표본에서 필터가 단 한 번도 발동하지 않아서** 나온 결과다.
"효과가 없다는 것을 확인했다"와 "효과가 있는지 확인할 수 없었다"는 다른 결론이며, 이 리포트는 후자에
더 가깝다.

## 종합 판정

**추가 검증 필요.** 이 필터가 SOXL/TQQQ Track A 교차 사이클의 MDD나 손실 노출을 줄인다는 근거를
이번 20개 사이클 표본에서 찾지 못했다 — 그러나 이는 필터가 "나쁘다"는 증거가 아니라, **이 표본에서
필터가 시험될 기회 자체가 없었다**는 뜻이다. 채택하지 않는 것을 권장하되(근거 부족), "채택 비추천"
(추적 손절처럼 명확히 순비용으로 확인된 경우)과는 성격이 다르다는 점을 정책 문서에 반영할 때
구분해야 한다.

**confidence: low-medium.** 방향성 판단(필터가 유해하지 않다는 것)은 표본 전체에서 일관됐지만,
정작 검증하려던 가설(필터가 MDD를 줄이는가)은 이 표본 위에서 관측 자체가 불가능했다. 실질적으로
독립적인 거시 국면은 여전히 7~8개 수준이고 레버리지 ETF 2종에 한정된다는 기존 리포트들과 동일한
한계도 남아 있다.

## Confidence와 caveats

**confidence: low-medium**

- 필터가 20개 사이클 전부에서 발동하지 않아, "SPY 40주선 필터가 MDD를 줄인다"는 가설 자체를
  이 표본으로는 지지도 반박도 할 수 없다 — 검증 불능(inconclusive)에 가깝다.
- SPY(Yahoo Finance)와 SOXL/TQQQ(Twelve Data 프로덕션 + Yahoo 확장)의 데이터 소스가 서로 다르다.
  주봉 확정 시점이나 조정 방식의 미세한 차이가 있을 수 있다(기존 리포트들이 SOXL 소스 간 1주 차이를
  발견한 사례 참고).
- 실질적으로 독립적인 거시 국면은 여전히 7~8개 수준이며, 20개 사이클 중 SOXL 2종 소스 중복(2건)이
  포함돼 있다는 기존 한계가 그대로 남아 있다.
- 레버리지 ETF 2종(SOXL, TQQQ)에 한정된 결과이며, Track B(일반 종목)나 다른 레버리지 상품에
  일반화할 근거는 없다.
- 가장 여유가 적었던 사례(TQQQ 2015-11-23 사이클, SPY가 교차 당주 포함 2주 연속으로만 40주선
  위였음)를 볼 때, 표본을 조금만 넓혀도(다른 레버리지 ETF, 다른 기간) 필터가 실제로 발동하는
  사례가 나올 가능성은 있다 — 이번 표본이 우연히 그 경계를 비껴갔을 뿐일 수 있다.
- 손절·재진입·분할 매수, SPY 외 다른 시장 지표는 세션 지시에 따라 검증하지 않았다.

## 다음 리서치 우선순위 제안

1. **표본 확장**: `track-a-stoploss-revalidation-and-sizing-design.md`에서 이미 확보한 TNA/FAS
   29개 사이클(다른 섹터의 3배 레버리지 ETF)에 같은 SPY 40주선 필터를 적용해, 필터가 실제로 발동하는
   사례가 나오는지 확인한다. TNA/FAS는 SOXL/TQQQ보다 사이클이 짧고 잦아(15개, 14개) 필터가 발동할
   여지가 더 클 수 있다.
2. 이번 리포트에서 확인된 "가장 여유가 적었던" 사례(2주)를 기준으로, SPY 40주선 대신 더 민감한
   확인 방식(예: 더 짧은 이동평균 기간)이 실제로 다른 판정을 내는 사례를 만드는지는 이번 리포트의
   범위 밖이다 — 사전 고정 가설 원칙에 따라 이번에는 다루지 않았고, 별도 세션으로 명시적 가설을
   세운 뒤 진행해야 한다.
3. Track A 손절 레이어는 여전히 미해결이다(`STRATEGY_ENGINE_POLICY.md` "현재 구현 우선순위" 1번).
   이 필터가 "추가 검증 필요"로 남은 이상, 손절 없는 Track A 주문 초안을 허용할지에 대한 설계 결정은
   여전히 다음 단계로 남아 있다.

## 재현 방법

```bash
# 1) SPY 주봉 원본 재수집 (필요시)
curl -s -A "Mozilla/5.0" \
  "https://query2.finance.yahoo.com/v8/finance/chart/SPY?period1=0&period2=9999999999&interval=1wk&events=div,splits" \
  -o research/data/cache/spy-weekly-yahoo-raw.json
python3 research/data/tools/convert_yahoo_daily.py \
  research/data/cache/spy-weekly-yahoo-raw.json research/data/cache/spy-weekly-yahoo-1993-2026.csv

# 2) 시장 국면 필터 백테스트 실행 (기존 SOXL/TQQQ 캐시 재사용)
python3 research/data/tools/market_regime_filter_backtest.py --today 2026-08-18 \
  --json-out research/data/cache/market_regime_filter_backtest_results.json
```

결과 JSON: `research/data/cache/market_regime_filter_backtest_results.json`
(`overall`, `by_ticker`, `walk_forward`, `per_dataset`에 사이클별 상세 포함).

## 구조화 데이터

`research/data/backtests.json`에 새 레코드 `track-a-market-regime-filter-2026`을 추가했다. 추가
전후로 기존 12개 레코드가 값 변경 없이 동일한지 파이썬으로 직접 대조했다 — **레코드별 완전 일치
확인, 새 레코드 1개만 순수 추가됨**(재현: 기존 JSON을 깊은 복사로 스냅샷한 뒤 신규 레코드 추가·저장·
재로드해 앞 12개 인덱스를 인덱스별로 `==` 비교, 전부 `True`).

## 출처

- 가격 데이터: 위 "데이터 소스" 표. SOXL/TQQQ는 기존 캐시 전부 재사용(신규 수집 없음), SPY는
  Yahoo Finance 공개 API로 신규 수집.
- 전략 규칙 정의(읽기 전용 참고, 수정 안 함): `src/main/java/com/tradeguide/service/strategy/tracka/WeeklyMaCrossoverStrategy.java`,
  `src/main/java/com/tradeguide/service/strategy/StrategyDecisionMaker.java`.
- 선행 리포트: `research/reports/track-a-entry-delay-cutoff-review.md`(사이클 목록·`weeksSinceCross<=4`
  채택 근거), `research/reports/track-a-stoploss-drawdown-review.md`,
  `research/reports/track-a-stoploss-revalidation-and-sizing-design.md`,
  `research/reports/track-a-trailing-stop-review.md`(손절 레이어가 아직 채택되지 않은 배경).
- 계산 스크립트(신규): `research/data/tools/market_regime_filter_backtest.py`.
- 계산 스크립트(재사용, 수정 안 함): `research/data/tools/entry_delay_cycle_backtest.py`,
  `research/data/tools/convert_yahoo_daily.py`.
- 결과 캐시(신규): `research/data/cache/market_regime_filter_backtest_results.json`.

> confidence: low-medium — SPY 40주선 필터가 SOXL/TQQQ Track A의 MDD를 줄인다는 근거를 이번 20개
> 사이클 표본에서 찾지 못했으나, 이는 필터가 검증된 무효과가 아니라 표본 안에서 필터가 발동할
> 기회 자체가 없었기 때문이다. 표본을 넓히지 않고는 이 가설의 채택 여부를 판단할 수 없다.

---

## 정책 문서 갱신 초안 (참고용 — `research/STRATEGY_ENGINE_POLICY.md` 파일 자체는 수정하지 않음)

아래는 사용자 확인 후 `research/STRATEGY_ENGINE_POLICY.md`의 "Track A: 레버리지 또는 고변동성
자산" 절에 반영을 검토할 수 있는 문구 초안이다.

```markdown
- 시장 국면 필터(SPY 마지막 완료 주봉 종가 > SPY 40주 이동평균일 때만 신규 `BUY` 허용)를 검토했다
  (research/reports/track-a-market-regime-filter-review.md). 기존에 검증된 SOXL/TQQQ 20개 사이클
  (research/reports/track-a-entry-delay-cutoff-review.md) 전부에서, 교차 당주(weeksSinceCross==0)에
  SPY가 이미 40주선 위에 있어 필터가 단 한 사이클도 배제하거나 지연시키지 않았다 — 베이스라인과
  후보 결과가 완전히 동일했다(MDD 개선 0.0%p, 포기 수익률 0.0%p). 이는 10/40주 이동평균 교차 신호가
  구조적으로 시장 저점 대비 지연되어 발생하기 때문으로 추정되며, 필터가 무해하다는 뜻이지 유효하다는
  뜻은 아니다 — 표본 안에서 필터가 시험될 기회 자체가 없었다. "추가 검증 필요"로 판정하며, 현재
  구현하지 않는다. SPY와 Track A 자산의 데이터 소스가 다르다는 한계(SPY는 Yahoo, SOXL 일부/향후는
  Twelve Data)도 구현 전 해소해야 한다.
```

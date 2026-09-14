# Track B 횡단면 모멘텀 검증 인프라

## 핵심 결론 (3~5줄)

`research/reports/track-b-strategy-catalogue.md`가 제안한 횡단면 모멘텀(직전 52주 수익률에서
최근 4주를 제외한 48주 수익률, 분기 리밸런싱, 상위/유지 이원 기준, 동일가중 포트폴리오) 후보를
**재현 가능하게 검증하기 위한 순수 계산 백테스트 코어와 고정 fixture 테스트**를 만들었다. 이
작업은 실데이터로 전략을 채택했다는 뜻이 아니다 — 외부 시세 API를 전혀 호출하지 않았고, 작고
통제된 합성(fixture) 캔들로 계산 로직 자체(형성기간 수익률, 순위, 리밸런싱 시점, 데이터 품질
정책, 비용 반영)가 올바른지만 검증했다. Track B 실행 엔진, `TradingStrategy`, API, DB, 화면은
전혀 건드리지 않았고, 여전히 Track B는 사용자가 선택할 수 없는 상태를 유지한다. 다음 단계는
이 코어를 이용해 이 회사의 실제 주봉 데이터로 워크포워드 백테스트(`track-b-strategy-catalogue.md`의
"Phase 1")를 수행하는 것이며, 그 결과가 나오기 전까지 이 문서의 계산 결과는 어떤 형태로도 전략
채택 근거로 사용해서는 안 된다.

## 구현 범위와 위치

- `src/main/java/com/tradeguide/service/backtest/MomentumFormationReturnCalculator.java`:
  형성기간 수익률(52주 전 종가 대비 4주 전 종가, 최근 4주 제외) 순수 계산.
- `src/main/java/com/tradeguide/service/backtest/MomentumRebalanceScheduler.java`:
  거래일 캘린더로부터 리밸런싱 시점(첫 시점 = 52주차, 이후 호출자가 지정한 주기마다) 계산.
- `src/main/java/com/tradeguide/service/backtest/MomentumTopCandidateSelector.java`:
  순위 목록 + 기존 보유 종목으로 상위/유지 이원 기준(buy/hold spread)을 적용한 신규 보유 구성 계산.
- `src/main/java/com/tradeguide/service/backtest/CrossSectionalMomentumBacktestEngine.java`:
  위 세 계산기를 조합해 동일가중 포트폴리오 수익을 계산하는 오케스트레이터.
- `src/main/java/com/tradeguide/domain/backtest/`: 위 계산에 필요한 값 객체
  (`MomentumFormationScore`, `MomentumDataQualityReason`, `MomentumDataQualityExclusion`,
  `MomentumTierSelectionResult`, `CrossSectionalMomentumRebalanceEvent`,
  `CrossSectionalMomentumBacktestResult`). 기존 `BacktestAssumptions`(수수료·슬리피지)를 그대로
  재사용해 새 비용 모델을 따로 만들지 않았다.
- `src/test/java/com/tradeguide/service/backtest/`: 위 네 계산기 각각의 단위 테스트
  23개(합성 fixture 기반, Gradle `test`로 실행·통과 확인).

이 코드는 어디에서도 `TradingStrategy`, `StrategySelector`, `InvestmentTrack`, Track B API,
데이터베이스, 프론트엔드와 연결되지 않는다. `PortfolioAssetBacktestService`(Track A 조회 서비스)도
수정하지 않았다 — 이 검증 코어를 호출하는 서비스/API/화면은 아직 없으며, 만들려면 별도 작업
계약과 사용자 승인이 필요하다.

## 계산 규칙 (research/reports/track-b-strategy-catalogue.md의 제안을 코드로 옮긴 것)

- **형성기간 수익률**: `(리밸런싱 시점의 4주 전 종가 / 52주 전 종가 - 1) * 100`. 리밸런싱 당일
  종가가 아니라 "4주 전" 종가를 쓰는 이유는 학술 관행(12-1개월 모멘텀)의 단기 되돌림 배제 취지를
  주봉 데이터에 맞춰 그대로 옮긴 것이다. 최소 53주(52주 전 + 4주 전 + 나머지)의 완료 주봉이
  있어야 계산 가능하며, 부족하면 예외를 던지지 않고 "이 시점에는 판단할 수 없음"(빈 값)을
  반환한다.
- **리밸런싱 시점**: 유니버스 전체 거래일의 합집합 캘린더에서 형성기간을 채울 수 있는 첫 시점부터,
  호출자가 지정한 주기(예: 13주=분기)마다 계산한다. 분기(13주)를 숨겨진 기본값으로 넣지 않았다 —
  호출자가 항상 명시해야 한다.
- **상위/유지 이원 기준**: 신규 진입은 상위 `topTierFraction` 안에서만 허용하고, 이미 보유 중인
  종목은 순위가 `holdTierFraction`(하한선) 밖으로 밀려나기 전까지 유지한다. 두 비율 값은 검증된
  상수가 아니라 호출자가 명시적으로 주입하는 파라미터로 남겨뒀다 — `track-b-strategy-catalogue.md`도
  "상위 20%/하위 40%"를 예시로만 제시했을 뿐 채택하지 않았기 때문이다.
- **동일가중 포트폴리오**: 매 리밸런싱마다 그 시점의 보유 종목(신규 진입 + 유지) 전체를 동일가중으로
  재조정한다. 이는 실제 트랙 레코드 모멘텀 지수들의 관행과 같지만, 유지 종목까지 매 분기 재조정하므로
  회전율/비용을 보수적으로(실제보다 높게) 추정하는 방향의 가정이다 — 비용을 과소평가하지 않기 위해
  의도적으로 이렇게 결정했다.

## 입력 데이터 요건

- 종목별 **완료된** 주봉 캔들 이력(시가·고가·저가·종가·거래량)이 시점 순서대로 정렬돼 있어야 한다.
  이 엔진은 마지막 캔들이 미완료(진행 중인 주)인지 여부를 스스로 판별하지 않는다 — 호출자가
  `CompletedWeeklyCandleFilter`(Track A가 이미 쓰는 컴포넌트)로 필터링한 뒤 전달해야 한다.
- 유니버스는 `Map<티커, 캔들 목록>` 형태로 전달한다. 각 종목의 캔들 개수·시작일이 서로 달라도
  된다(예: 상장일이 다른 종목) — 엔진이 종목별로 개별 검증한다.
- **point-in-time 유니버스 여부**: 엔진은 `pointInTimeUniverse`라는 boolean을 호출자가 명시적으로
  넣도록 요구하고, 이 값을 결과 메타데이터에 그대로 반영한다. **엔진 자체는 생존 편향을 계산하거나
  보정하지 않는다** — 이 플래그는 단지 "호출자가 시점별 지수 편입 데이터를 썼다고 선언했는지"를
  기록할 뿐이다. `research/reports/track-b-strategy-catalogue.md`가 이미 지적했듯, 현재 이
  코드베이스는 시점별 S&P500 편입 종목 리스트(유료 CRSP/Compustat 수준)를 갖고 있지 않으므로,
  실제 워크포워드 백테스트에서 현재 시점의 S&P500 구성을 과거에 그대로 적용하면
  `pointInTimeUniverse = false`로 정직하게 기록해야 하고, 그 결과는 생존 편향으로 낙관적으로
  왜곡됐을 수 있다는 caveat와 함께 읽어야 한다.

## 데이터 품질 정책 (테스트로 고정함)

무언가를 조용히 무시하지 않고, 아래 네 가지 사유(`MomentumDataQualityReason`)로 명시적으로 제외한다.

| 상황 | 처리 | 범위 |
|---|---|---|
| 종목의 캔들 이력에 거래일 중복 | 그 종목을 **전체 백테스트 기간에서 제외**(`DUPLICATE_TRADING_DATE`) | 전역 |
| 종목의 캔들 이력이 거래일 오름차순이 아님 | 그 종목을 **전체 백테스트 기간에서 제외**(`UNSORTED_HISTORY`) | 전역 |
| 특정 리밸런싱 시점까지 52주 형성기간을 채울 이력이 부족(예: 늦게 상장) | 그 시점의 순위 계산에서만 제외, 이력이 쌓이면 이후 시점부터 포함(`INSUFFICIENT_HISTORY`) | 시점별 |
| 특정 리밸런싱 시점(거래일)에 그 종목의 캔들 자체가 없음 | 그 시점의 순위 계산에서만 제외(`MISSING_HISTORY`) | 시점별 |

이미 보유 중인 종목이 특정 리밸런싱 시점에 가격이 없는 경우(드문 경우), 매매하지 않고 마지막
보유 수량을 그대로 이월하며 `MISSING_HISTORY` 사유로 기록한다 — 이 시점의 포트폴리오 가치
평가에서는 제외된다. 이는 검증용 단순화이며, 실제 유니버스 정기 스캔에서는 이런 결측이 생기지
않도록 별도 데이터 파이프라인 설계가 필요하다(아래 "다음 단계" 참고).

## 비용 가정

- 수수료·슬리피지는 기존 `BacktestAssumptions(feeRate, slippageRate)`를 재사용한다. **호출자가
  항상 명시적으로 주입해야 하며, 0 비용을 기본값으로 숨기지 않는다** — `run()` 메서드에 비용을
  생략할 수 있는 오버로드가 없다. 0 비용으로 실행하려면 `BacktestAssumptions.zeroCost()`를
  호출자가 명시적으로 선택해야 한다.
- 비용 모델: 매수는 목표 배분 금액 전액이 현금에서 빠져나가고 비용만큼 매수 수량이 줄어들며,
  매도는 매도 수량은 목표 감소분 전액에 해당하고 비용만큼 현금 유입이 줄어든다
  (`(feeRate + slippageRate) * 거래 대금`).
- `research/reports/track-b-strategy-catalogue.md`가 인용한 Novy-Marx & Velikov(2016)의 왕복
  20~57bp 실행비용 실측치를 그대로 이 엔진에 하드코딩하지 않았다 — 그 수치도 아직 이 프로젝트가
  채택한 값이 아니라 조사 근거일 뿐이므로, 실제 워크포워드 실행 시 호출자가 명시적으로 그 구간의
  값을 주입해야 한다.
- 테스트로 고정한 성질: 동일한 유니버스·정책으로 비용 있음/없음을 각각 실행하면, 비용이 있는
  결과의 만기 자산가치가 비용이 없는 결과보다 높을 수 없다
  (`CrossSectionalMomentumBacktestEngineTest#costAssumptionNeverProducesHigherEndingValueThanZeroCost`).

## 워크포워드 실행 방법 (이 코어를 실데이터에 적용할 때)

이 작업 자체는 실데이터를 조회하지 않았지만, 다음 단계(Phase 1)에서 이 코어를 실데이터로 실행할
때는 `research/reports/track-a-stoploss-revalidation-and-sizing-design.md`와
`research/reports/track-a-trailing-stop-review.md`가 확립한 관행을 그대로 따라야 한다.

1. 학습 구간과 검증 구간을 최소 2개의 사전 고정된 비중첩 분할로 나눈다(예: 학습 2015–2019 /
   검증 2020–2026, 그리고 뒤집은 분할 학습 2020–2022 / 검증 2023–2026).
2. 가능하면 4분할(예: 2018/2019/2020/2021 기준)로 확장해 특정 구간 의존성을 추가로 점검한다.
3. 각 분할마다 `CrossSectionalMomentumBacktestEngine.run(...)`을 독립적으로 호출하고, 한 분할의
   결과로 다른 분할의 결론을 유추하지 않는다.
4. 결과(`CrossSectionalMomentumBacktestResult`)의 `rebalanceEvents`, `universeExclusions`를
   `research/data/backtests.json` 스키마에 원자료로 기록하고, confidence와 caveats는 이 문서가
   아니라 그 실행 결과를 담은 후속 리포트가 부여한다.

## 채택/기각 기준

`research/reports/track-b-strategy-catalogue.md`가 이미 정의한 "채택 게이트(엔진 구현 착수 전
반드시 통과해야 하는 조건)" 5개를 그대로 적용한다 — 이 문서가 새로 기준을 만들지 않는다.

1. 왕복 20~57bp 거래비용·슬리피지를 반영한 뒤에도, **두 개 이상의 독립적 워크포워드 분할 모두에서**
   동일가중 매수 후 보유 벤치마크 대비 순수익이 양(+)이어야 한다. 한 분할에서만 이기면 기각.
2. 분할별 결론의 **방향(부호)이 뒤집히지 않아야** 한다.
3. 월 환산 회전율이 대략 월 50% 미만이어야 한다. 넘으면 buy/hold 이원 기준을 더 넓히거나 리밸런싱
   주기를 늘려 재시도한다.
4. 최대낙폭이 벤치마크보다 뚜렷이 나쁘지 않아야 한다(모멘텀 붕괴 위험 확인).
5. 결과는 `research/data/backtests.json`에 원자료로 기록하고, confidence와 caveats는 실제 계산이
   끝난 후속 리포트가 부여한다.

## 현재 이 코어가 계산하지 않는 것 (의도적 범위 밖)

- **주간 시가평가(mark-to-market)와 최대낙폭(MDD)**: 이 엔진은 리밸런싱 시점에서만 포트폴리오
  가치를 확정한다(만기 시점은 마지막 보유 종목의 마지막 알려진 종가로 평가). 리밸런싱 사이 구간의
  주간 평가나 MDD 계산은 하지 않는다 — 채택 게이트 4번(MDD 비교)을 실제로 판정하려면 별도로
  주간 시가평가 로직을 추가해야 한다. 이것을 "이미 해결한 문제"로 오해하지 않도록 여기 명시한다.
- **샤프비율, 승률, 회전율 지표**: `rebalanceEvents`에 담긴 원자료(편입/유지/이탈 종목,
  거래비용, 리밸런싱 전후 자산가치)로부터 계산할 수 있지만, 이 작업에서 별도 계산기를 만들지는
  않았다.
- **생존 편향 보정**: 위 "입력 데이터 요건" 절 참고. `pointInTimeUniverse` 플래그는 선언일 뿐
  보정이 아니다.
- **실제 S&P500 유니버스 스캔·429 처리**: `research/reports/track-b-strategy-catalogue.md`의
  Phase 4(데이터 인프라) 영역이며, 이 작업 범위 밖이다.

## 테스트 수용 기준 대조

| 작업 명세의 수용 기준 | 검증한 테스트 |
|---|---|
| 최근 4주 제외 수익률과 순위가 정확함 | `MomentumFormationReturnCalculatorTest#calculatesReturnFromFiftyTwoWeeksAgoExcludingLastFourWeeks`, `CrossSectionalMomentumBacktestEngineTest#ranksFormationReturnsCorrectlyAndBuysTopTierWithEqualWeight` |
| 리밸런싱 이전에는 구성 종목이 바뀌지 않음 | `CrossSectionalMomentumBacktestEngineTest#compositionDoesNotChangeUntilNextScheduledRebalance` |
| 비용 있는 결과가 무비용 결과보다 높지 않음 | `CrossSectionalMomentumBacktestEngineTest#costAssumptionNeverProducesHigherEndingValueThanZeroCost` |
| 미래 시점 가격이 과거 리밸런싱 순위에 영향을 주지 않음 | `CrossSectionalMomentumBacktestEngineTest#futureCandlesAfterFirstRebalanceDoNotAffectItsRanking` |
| 잘못된 이력 길이 거부/제외 | `MomentumFormationReturnCalculatorTest#returnsEmptyWhenHistoryIsShorterThanFiftyTwoWeeks`, `CrossSectionalMomentumBacktestEngineTest#excludesLateStartingTickerOnlyAtRebalancesWithInsufficientHistory` |
| 중복 날짜 거부/제외 | `CrossSectionalMomentumBacktestEngineTest#excludesTickerWithDuplicateTradingDateFromEveryRebalance` |
| 누락 종목 데이터 거부/제외 | `CrossSectionalMomentumBacktestEngineTest#excludesTickerMissingExactlyAtARebalanceDate` |

`./gradlew test`로 신규 테스트 23개를 포함한 전체 Gradle 테스트 스위트가 통과함을 확인했다
(기존 Track A/포트폴리오/브로커 테스트 포함, 회귀 없음).

## 실전 적용 시 유의점

- 이 문서와 코드는 **검증 인프라**다. Track B를 구현하거나 화면에 노출하지 않았고, 여전히
  `StrategySelector.select(TRACK_B)`는 지원되지 않는 상태다.
- 여기서 보인 어떤 수치(예: 단위 테스트의 형성기간 수익률, 비용 반영 결과)도 합성 fixture로 계산한
  것이며, 실제 종목의 실제 수익률이 아니다. 실데이터 워크포워드 결과가 나오기 전까지 이 문서를
  근거로 Track B 채택을 논의하지 않는다.
- 다음 단계는 `research/reports/track-b-strategy-catalogue.md`의 Phase 1(실데이터 워크포워드
  백테스트)이며, 이 작업의 결과물(계산 코어)을 그 단계의 입력으로 사용할 수 있다. Phase 1은 별도
  리서치 작업 계약과 실제 시세 조회가 필요하므로 이 작업 범위에 포함하지 않았다.

## 출처

- `research/reports/track-b-strategy-catalogue.md` (형성기간 산식, 리밸런싱 주기, 이원 기준,
  비용 가정, 채택 게이트의 원 제안)
- `research/STRATEGY_ENGINE_POLICY.md` (Track B 상태, 검증과 활성화 조건)
- 코드 확인: `src/main/java/com/tradeguide/service/backtest/WeeklyMaCrossoverBacktestEngine.java`,
  `src/main/java/com/tradeguide/domain/backtest/BacktestAssumptions.java` (기존 Track A 백테스트
  엔진의 컨벤션을 그대로 따름)

> confidence: low — 이 문서는 계산 코어의 정확성(합성 fixture 기준)만 검증했다. 실데이터
> 워크포워드 결과, 채택 게이트 통과 여부, 생존 편향 보정은 아직 없다. 이 작업의 산출물은
> "무엇을 다음에 실행할지"를 위한 재사용 가능한 계산 도구이지, 전략 채택 근거가 아니다.

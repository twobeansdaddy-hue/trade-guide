# Trade Guide 프로젝트 컨텍스트

## 사용자 목표

이 프로젝트에는 두 가지 목표가 있다.

1. 사용자가 Java, Spring Boot, REST API, JPA, 테스트, Git을 실제 서비스 기능으로 학습하며 현대적인 개발 역량을 회복한다.
2. 미국 주식 포트폴리오와 시장 데이터를 바탕으로 매수·보유·매도 판단의 근거를 제공하는 웹 서비스를 만든다.

서비스는 자동 매매 도구가 아니다. 사용자는 장 시작 전 가이드와 위험 정보를 보고 증권사에 예약 주문을 직접 등록하며, 최종 판단과 책임은 사용자에게 있다.

## 제품 방향

- 초기 대상 시장은 미국 주식이다. 이후 다른 시장으로 확장할 수 있게 `Market`과 시세 제공자 인터페이스를 분리한다.
- 보유 종목뿐 아니라 장기적으로 신규 후보도 다룬다. 신규 후보는 무제한 전체 시장이 아니라 처음에는 S&P 500처럼 제한된 유니버스에서 탐색한다.
- 가이드는 현재가, 전략 신호, 시장 상황, 외부 이벤트를 조합해 설명 가능한 근거를 제공해야 한다.
- 예약 주문에 필요한 가격·수량·유효 기간은 장기 목표지만, 전략 판단과 같은 객체로 섞지 않는다.

## 전략 방향

### Track A: 레버리지 또는 고변동성 자산

- SOXL 같은 일일 레버리지 ETF는 일반 주식과 다른 위험 특성을 가지므로 별도 Track A로 관리한다.
- 현재 구현된 첫 전략 후보는 완료된 주봉의 10주/40주 이동평균 교차다.
  - 전략은 현재 추세(`10주선`과 `40주선`의 상대 위치)와 교차 이벤트를 함께 판단한다.
  - 종목 단독 가이드: 시장 데이터만으로 계산한 `StrategySignal`을 반환한다. 행동을 결정하지 않는다.
  - `StrategyDecisionMaker`가 보유 여부를 반영한 행동을 결정한다.
    - 보유 종목: 상승 추세면 `HOLD`, 하락 추세면 `SELL`
    - 미보유 후보: 상승 추세이고 가장 최근 교차 이후 `0~4주`이면 `BUY`, 그 외에는 `WATCH`
  - `CROSS_UP`은 교차 당주에만 발생하므로, 1~4주 지연 진입은 `trend`와 `weeksSinceCross`로 판단한다.
  - 이 규칙은 SOXL/TQQQ 지연 진입 리서치의 `low-medium` 신뢰도 결론이며, Track A에만 적용한다. 5~8주 축소 진입과 `REDUCE` 의미 변경은 보류한다.
  - 가격·수량·손절 기준은 아직 포함하지 않는다.
- 주봉 신호는 미국 동부 시간 기준 금요일 장 마감 이후 확정된 데이터를 기준으로 하며, 다음 거래일 장 시작 전 가이드에 사용한다.
- MACD, RSI, 분할 매도, ATR 손절 등은 향후 비교·조사 후보이다. 사용자 질문만으로 기본 전략에 추가하지 않는다.
- 다음 전략 기능은 레버리지 종목의 노출 비중과 위험 경고이며, 이후 전략별 주문 초안과 백테스트로 확장한다.

### Track B: 일반 미국 주식과 비레버리지 ETF

- Track B는 일반 종목의 신규 후보 탐색과 보유 종목 가이드에 사용한다.
- 펀더멘털과 추세를 함께 보되, Track A의 타이밍 규칙을 그대로 복사하지 않는다.
- 초기 후보 유니버스와 세부 규칙은 `research/STRATEGY_ENGINE_POLICY.md`의 정책을 따른다.

## 핵심 도메인과 설계 결정

```text
Member -> Portfolio -> TradeTransaction -> Holding -> Valuation
```

- `TradeTransaction`은 원본 매매 기록이다.
- `Holding`은 매매 이력으로 계산하는 현재 보유 상태이며 DB에 저장하지 않는다.
- `HoldingValuation`, `PortfolioValuation`은 현재가 기반의 평가 결과다.
- `AssetProfile`은 `market + ticker`와 투자 트랙을 관리하는 시스템 전략 카탈로그다. 사용자별 목표 수익률 설정이 아니다.
- `StrategySignal`은 시장 데이터만으로 계산한 추세 상태, 교차 이벤트, 기준 가격과 근거다.
- `StrategyDecision`은 보유 여부 같은 사용자 맥락과 `StrategySignal`을 결합한 최종 행동과 근거다.
- `StrategyAction`에는 `BUY`, `HOLD`, `REDUCE`, `SELL`, `WATCH`가 있다. `StrategyDecisionMaker`는 보유 종목의 `HOLD`/`SELL`과 미보유 후보의 `BUY`/`WATCH`를 결정한다. 후보 `BUY`는 상승 추세의 교차 후 0~4주에만 허용한다. `REDUCE`는 주문 초안 생성 규칙과 부분 매도 정책이 확정될 때까지 사용하지 않는다.
- `TradePlan`은 주문 비율, 주문 유형, 지정가, 손절가, 유효 기간, 근거와 전략 메타데이터를 담는 주문 초안 도메인 모델이다. DB에 저장하거나 증권사에 전송하지 않는다.
- 주문 초안을 만드는 전략별 가격·수량·손절·유효 기간 규칙은 아직 확정하지 않았으므로 `TradePlanGenerator`는 구현하지 않았다.
- `REDUCE`는 보유 종목의 부분 매도를 뜻한다. 교차 후 5~8주인 미보유 후보의 축소 진입에는 사용하지 않으며, 해당 구간은 현재 `WATCH`를 유지한다.
- `TradePlan.quantityRatio`의 분모와 자동 산출식은 아직 채택하지 않았다. 계좌 총자산, 가용 현금, 트랙별 배정 예산, 위험 허용 비율 중 어떤 입력을 사용할지와 `RiskPolicy` 도입 여부를 먼저 결정해야 한다.
- Track A 손절 후보 중 고정 비율 `-25%`는 추가 검증 필요이며, ATR 기반 손절은 채택하지 않는다. 따라서 현재 전략 엔진과 `TradePlanGenerator`에 손절 규칙을 구현하지 않는다.
- `TradeGuideCalculator`와 `/api/trade-guide/calculate`은 초기 학습용 단순 계산 API다. 사용자가 입력한 목표 수익률·최대 손실률을 계산하며, 현재 전략 엔진의 정책이나 결과에 연결하지 않는다.

## 현재 전략 엔진 상태

- Twelve Data에서 현재가, 일봉, 주봉을 조회한다.
- 주봉 10/40 이동평균 전략은 `TRACK_A` 자산에 적용한다.
- 진행 중인 주봉이 신호에 섞이지 않도록 완료 주봉 필터를 적용했다.
- 최신 완료 주봉이 현재 시점에 기대되는 주보다 오래되면 `StaleMarketDataException`으로 전략 판단을 차단한다.
- 응답에는 전략 ID, 버전, 데이터 기준일을 포함한다.
- 응답에는 `trend`와 `signalEvent`도 포함한다. 교차가 발생한 한 주만이 아니라 현재 추세도 구분한다.
- `weeksSinceCross`는 가장 최근 교차 이후 경과한 주 수다. 미보유 종목의 추격 매수를 막는 후속 정책에서 사용한다.
- `referencePrice`는 전략 판단에 사용한 최신 완료 주봉 종가다. 주문 지정가나 목표가가 아니다.
- Track A 골든 테스트는 실행 시세 제공자인 Twelve Data의 고정 주봉 스냅샷을 사용한다. Yahoo 기반 리서치와 교차 시점이 다르면 차이를 기록하고, 실행 기준을 임의로 섞지 않는다.

### 현재 API 계약

- `GET /api/markets/{market}/stocks/{ticker}/strategy-guide`는 `StrategySignalResponse`를 반환한다.
- `GET /api/members/{memberId}/portfolios/{portfolioId}/strategy-guides`와 `GET /api/members/{memberId}/portfolios/{portfolioId}/candidate-strategy-guides`는 모두 `StrategyGuideBatchResponse`를 반환한다.
  - `guides`는 성공한 `AssetStrategyGuideResponse` 목록이다.
  - `unavailableAssets`는 시장 데이터 조회 실패, 요청 제한, 오래된 데이터로 판단하지 못한 종목과 메시지 목록이다.
  - 요청 제한(429)이 발생하면 이후 종목은 외부 API를 추가 호출하지 않고 요청 제한 메시지로 `unavailableAssets`에 기록한다.
  - 포트폴리오 자체가 없거나 보유 종목 계산에 실패하면 기존 오류 응답을 유지한다.
- 현재 후보 유니버스는 관리자가 등록한 `TRACK_A` 프로필이며, S&P 500 전체 스크리닝이나 `TRACK_B` 후보 탐색은 아직 구현하지 않았다.
- `referencePrice`는 최신 완료 주봉 종가이며, 주문 지정가·목표가·손절가는 아니다.

## 리서치와 정책 문서

- 전략 후보와 근거 데이터: `research/data/strategies.json`
- 전략 정책과 미구현 범위: `research/STRATEGY_ENGINE_POLICY.md`
- 리서치 자료는 실행 전략과 동일하지 않다. 정책에서 채택한 규칙만 구현한다.

## 현재 구현 위치

포트폴리오 노출 비중 API, Track A 골든 테스트, 주봉 데이터 신선도 가드, 시장 신호와 행동의 분리, `StrategyDecisionMaker` 기반 행동 규칙, 완료 주봉 캐시와 다종목 전략 가이드의 종목별 부분 실패 응답, `TradePlan` 도메인 모델과 기본 유효성 검증까지 구현했다. 세부 상태와 다음 작업은 `docs/LEARNING_LOG.md`를 기준으로 한다.

## 현재 제한

- 인증과 권한이 아직 없으므로 `memberId` 경로는 실제 로그인 사용자를 검증하지 않는다.
- `/api/admin/**`은 로컬 학습용 공개 API다. 운영 배포 전 인증과 관리자 권한을 적용해야 한다.
- 완료 주봉 캐시는 애플리케이션 메모리를 사용하므로 애플리케이션 재시작 시 초기화된다. 분산 캐시나 다중 인스턴스 운영은 아직 고려하지 않았다.

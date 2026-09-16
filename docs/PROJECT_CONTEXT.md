# Trade Guide 프로젝트 컨텍스트

## 사용자 목표

이 프로젝트에는 두 가지 목표가 있다.

1. 사용자가 Java, Spring Boot, REST API, JPA, 테스트, Git을 실제 서비스 기능으로 학습하며 현대적인 개발 역량을 회복한다.
2. 미국 주식 포트폴리오와 시장 데이터를 바탕으로 매수·보유·매도 판단의 근거를 제공하는 웹 서비스를 만든다.

서비스는 자동 매매 도구가 아니다. 사용자는 장 시작 전 가이드와 위험 정보를 보고 증권사에 예약 주문을 직접 등록하며, 최종 판단과 책임은 사용자에게 있다.

## 제품 방향

- 초기 대상 시장은 미국 주식이다. `Market`은 현재 `US`, `KR`을 지원하며, 이후 다른 시장도 열거형과 시세 제공자 구현을 확장해 추가한다.
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
- 포트폴리오 노출 비중과 위험 경고를 제공하며, 이후 전략별 주문 초안과 백테스트로 확장한다.

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
- `AssetListing`은 `market + ticker`, 표시명, 상장 상태를 관리하는 거래 가능한 종목 기준 엔터티다. `AssetProfile`은 하나의 상장 종목과 투자 트랙을 연결하는 시스템 전략 카탈로그이며, 사용자별 목표 수익률 설정이 아니다.
- 전략 가이드·매매 계획 초안·장전 가이드 응답에는 `AssetDisplayNameResolver`(`AssetListingRepository.findByMarketAndTicker`의 `displayName`, 없으면 티커로 대체)로 조회한 `displayName`이 항상 포함된다(2026-09-16 추가) - 이전에는 일부 응답(예: TQQQ)이 티커만 노출해 사용자가 종목명을 알아보기 어려웠다.
- 미국 종목 검색은 `AssetListing`의 활성 종목을 우선 반환하고 Twelve Data `symbol_search` 결과를 함께 사용한다. 외부 검색 결과는 조회용이며 자동으로 DB에 저장하지 않는다. 동일 시장·검색어의 외부 결과는 5분간 캐시한다.
- `StrategySignal`은 시장 데이터만으로 계산한 추세 상태, 교차 이벤트, 기준 가격과 근거다.
- `StrategyDecision`은 보유 여부 같은 사용자 맥락과 `StrategySignal`을 결합한 최종 행동과 근거다.
- `StrategyAction`에는 `BUY`, `HOLD`, `REDUCE`, `SELL`, `WATCH`가 있다. `StrategyDecisionMaker`는 보유 종목의 `HOLD`/`SELL`과 미보유 후보의 `BUY`/`WATCH`를 결정한다. 후보 `BUY`는 상승 추세의 교차 후 0~4주에만 허용한다. `REDUCE`는 주문 초안 생성 규칙과 부분 매도 정책이 확정될 때까지 사용하지 않는다.
- `TradePlan`은 주문 비율, 비율 기준, 주문 유형, 지정가, 손절가, 유효 기간, 근거와 전략 메타데이터를 담는 주문 초안 도메인 모델이다. DB에 저장하거나 증권사에 전송하지 않는다.
- `QuantityRatioBasis.PORTFOLIO_VALUE`는 포트폴리오 평가액 기준의 신규 매수 비율이고, `HOLDING_QUANTITY`는 보유 종목 수량 기준의 매도 또는 부분 매도 비율이다.
- `PortfolioRiskPolicy`는 주문당 최대 손실 비율, 종목당 최대 노출 비율, 선택적인 사용자 설정 손절 기준 비율을 검증하는 JPA 값 객체다. `Portfolio`에 포함되어 `portfolios` 테이블의 소수점 여섯 자리 컬럼으로 저장되며, 설정·조회 API와 종목별 노출 초과 경고에 사용된다.
- `GET /api/members/{memberId}/portfolios/{portfolioId}/trade-plan-preview`는 DB 저장이나 증권사 전송 없이 검토용 매매 계획을 계산한다. 후보 `BUY`는 `포트폴리오 평가액 × 주문당 최대 손실 비율 ÷ (전략 기준 가격 - 사용자 손절가)`로 위험 기준 수량을 구한 뒤 종목당 최대 노출 한도로 제한한다. 기준 가격은 실시간 주문가가 아니며, 가용 현금은 아직 공통 연동 계약이 없어 `AVAILABLE_CASH_NOT_SYNCED` 제약으로만 표시한다.
- 보유 종목은 **실시간 현재가**(`PortfolioValuationService`의 `HoldingValuation.currentPrice`, `PortfolioRiskPolicy.stopLossRatio`가 설정된 경우에만 조회)가 사용자 손절가 이하일 때 현재 보유 수량 전량의 `STOP_LOSS_EXIT_REVIEW`를 반환한다(2026-09-16 수정). 이전에는 최근 완료 주봉 종가(`referencePrice`, 최대 1주 전 가격)로만 판정해 주간 사이 하락을 놓칠 수 있었고, 주간 추세가 `HOLD`인 종목은 손절 판정 자체를 건너뛰는 구조적 결함도 있었다(실제 주가가 이미 손절가 밑이어도 주간 추세만 `HOLD`면 손절 검토가 나타나지 않음). 지금은 손절 판정을 `BUY`/`HOLD` 트렌드 분기보다 먼저 확인해, 주간 추세와 무관하게 안전 검토로 최우선 표시한다. 일반 하락 추세 `SELL_REVIEW`에는 임의의 부분 매도 수량을 만들지 않는다. 모든 계획은 사용자 최종 확인이 필요하며, `TradePlan` 저장·증권사 주문 전송과 연결하지 않는다.
- `REDUCE`는 보유 종목의 부분 매도를 뜻한다. 교차 후 5~8주인 미보유 후보의 축소 진입에는 사용하지 않으며, 해당 구간은 현재 `WATCH`를 유지한다.
- `TradePlan.quantityRatio` 자체의 영구 주문 초안 산식은 아직 채택하지 않았다. 현재 수량 공식은 위험 한도 기반의 read-only 미리보기에만 한정하며, 가용 현금·트랙별 예산·부분 매도 정책을 임의로 가정하지 않는다.
- Track A 손절 후보 중 고정 비율 `-25%`는 추가 검증 필요이며, ATR 기반 손절은 채택하지 않는다. 사용자가 포트폴리오 설정에 직접 입력한 손절 기준 비율은 검토용 가이드에만 적용하고, 전략 기본값이나 `TradePlanGenerator`의 자동 규칙으로 사용하지 않는다.
- 2026-09-16 리서치로 Track A 알고리즘 손절/청산 오버레이 후보 4종(고정비율, ATR 기반, 추적손절, Chandelier Exit)이 전부 기각됐고, 시장국면필터(SPY 40주선)도 TNA/FAS 확장 검증(49사이클)에서 구조적으로 발동하지 않음이 재확인됐다(`research/reports/track-a-chandelier-exit-review.md`, `research/reports/track-a-market-regime-filter-tna-fas-extension.md`). Track A의 손절 레이어는 현재도 사용자가 직접 입력하는 검토용 비율(위 항목)만 유효하며, 어떤 알고리즘 손절 규칙도 자동 적용하지 않는다.
- Track B는 2026-09-16 리서치로 카탈로그 후보 4종(Track A 규칙 재사용, 횡단면 모멘텀, PEG/PER 밸류에이션, 저변동성+퀄리티 팩터)과 추가 가설 3종(MACD, 단기 반전, 배당성장)을 합쳐 8개 후보를 S&P100 실데이터 워크포워드로 전부 검증했고 전부 기각됐다. 후속 진단(`research/reports/track-b-market-concentration-diagnosis.md`)은 이 반복된 기각이 전략 결함보다 2015~2026 표본이 소수 메가캡(NVDA/AMD/AVGO 등)에 의해 비대칭적으로 견인된 시장 국면 때문이라는 것을 실증했다(벤치마크에서 상위 5종목만 제외해도 격차가 68~100% 줄어듦). 이 진단은 후보 재채택 근거가 아니다 - Track B는 당분간 매수 신호가 아닌 후보 발굴 스크리닝(PEG<1 && 50일선 위) 수준을 유지하고, 규칙 기반 자동 매매로 확장하지 않는다. 전체 경과는 `research/TASKS.md` 7절과 `research/data/backtests.json`을 참고한다.
- `TradeGuideCalculator`와 `/api/trade-guide/calculate`은 초기 학습용 단순 계산 API다. 사용자가 입력한 목표 수익률·최대 손실률을 계산하며, 현재 전략 엔진의 정책이나 결과에 연결하지 않는다.

## 현재 전략 엔진 상태

- 포트폴리오의 시장 데이터 제공자 설정에 따라 현재가와 캔들을 조회한다. Twelve Data는
  서버 키 기반으로, 토스증권은 해당 포트폴리오에 연결·검증된 계좌 자격 증명으로 조회한다.
  토스증권은 일봉을 조회한 뒤 전략 엔진용 주봉으로 애플리케이션에서 집계한다.
- Twelve Data에서 미국 주식·ETF의 종목명 또는 티커 검색도 조회한다. 한국 시장 검색과 통화 표시는 별도 제공자·환율·통화 모델 설계 전까지 완전 지원으로 표시하지 않는다.
- 주봉 10/40 이동평균 전략은 `TRACK_A` 자산에 적용한다.
- 진행 중인 주봉이 신호에 섞이지 않도록 완료 주봉 필터를 적용했다.
- 최신 완료 주봉이 현재 시점에 기대되는 주보다 오래되면 `StaleMarketDataException`으로 전략 판단을 차단한다.
- 응답에는 전략 ID, 버전, 데이터 기준일을 포함한다.
- 응답에는 `trend`와 `signalEvent`도 포함한다. 교차가 발생한 한 주만이 아니라 현재 추세도 구분한다.
- `weeksSinceCross`는 가장 최근 교차 이후 경과한 주 수다. 미보유 종목의 추격 매수를 막는 후속 정책에서 사용한다.
- `referencePrice`는 전략 판단에 사용한 최신 완료 주봉 종가다. 주문 지정가나 목표가가 아니다.
- Track A 골든 테스트는 Twelve Data의 고정 주봉 스냅샷을 명시적으로 사용한다. 실제 포트폴리오
  전략 분석은 해당 포트폴리오의 캔들 제공자 설정과 캐시 키를 따른다. Yahoo 기반 리서치와
  실행 데이터의 교차 시점이 다르면 차이를 기록하고, 실행 기준을 임의로 섞지 않는다.

### 현재 API 계약

- Google OIDC 인증 기반은 `tradeguide.auth.enabled=false`가 기본이며, 이 상태에서는 기존 API와 React MVP가 인증 없이 동작한다. `enabled=true`와 `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`이 모두 제공되면 `/oauth2/authorization/google` 로그인과 `GET /api/auth/me`가 활성화된다.
- `enabled=true`일 때 `/api/members/{memberId}/...`는 로그인 사용자를 요구하며, 인증 식별자로 찾은 `Member.id`와 URL의 `memberId`가 다르면 `403 Forbidden`을 반환한다. 현재는 포트폴리오와 거래 기록 API에 적용했다.
- `enabled=true`일 때 역할 모델이 없는 관리자 API와 기존 `POST /api/members`는 외부 접근을 막는다. 관리자 권한 모델이 도입되기 전 임의 사용자 생성·관리 기능을 노출하지 않기 위해서다.
- 역할 기반 관리자 API와 경로에서 `memberId`를 제거하는 API 전환은 다음 단계다. Toss 연동은 계좌·보유 종목·주문 이력의 읽기 전용 조회와 명시적 승인 반영까지 구현되어 있으며, 운영 전제조건과 국내 시장 원장 반영은 후속 범위다.

- `GET /api/markets/{market}/stocks/{ticker}/strategy-guide`는 `StrategySignalResponse`를 반환한다.
- `GET /api/members/{memberId}/portfolios/{portfolioId}/strategy-guides`와 `GET /api/members/{memberId}/portfolios/{portfolioId}/candidate-strategy-guides`는 모두 `StrategyGuideBatchResponse`를 반환한다.
  - `guides`는 성공한 `AssetStrategyGuideResponse` 목록이다.
  - `unavailableAssets`는 시장 데이터 조회 실패, 요청 제한, 오래된 데이터로 판단하지 못한 종목과 메시지 목록이다.
  - 요청 제한(429)이 발생하면 이후 종목은 외부 API를 추가 호출하지 않고 요청 제한 메시지로 `unavailableAssets`에 기록한다.
  - 포트폴리오 자체가 없거나 보유 종목 계산에 실패하면 기존 오류 응답을 유지한다.
- `GET`/`POST /api/members/{memberId}/portfolios/{portfolioId}/premarket-guide/today`는 미국 동부시간 기준 다음 거래일의 장전 가이드를 조회·생성한다. 같은 거래일에 이미 생성된 스냅샷은 기본적으로 재사용하며, `force=true`일 때만 시장 데이터를 다시 조회한다. 결과는 보유·후보 가이드, 조회 불가 종목, 기준일과 생성 시각을 함께 보존한다.
- 현재 후보 유니버스는 관리자가 등록한 `TRACK_A` 프로필이며, S&P 500 전체 스크리닝이나 `TRACK_B` 후보 탐색은 아직 구현하지 않았다.
- `referencePrice`는 최신 완료 주봉 종가이며, 주문 지정가·목표가·손절가는 아니다.
- `decision.metadata`에는 전략 ID·버전·데이터 기준일과 함께 전략별 `confidence`, `caveats`가 포함될 수 있다. 현재 Track A는 `low-medium` 신뢰도와 과거 데이터·기술적 신호·비주문 계산 범위에 대한 주의사항을 제공한다.
- `decision.guidance`에는 매수 시점 상태·설명과 손절 상태·비율·가격·설명이 항상 포함된다. 포트폴리오에 사용자 설정 손절 비율이 있으면 보유 종목은 평균 매입가, 후보 종목은 전략 기준 가격으로 손절가를 계산한다. 비율이 없으면 손절가는 `null`이며, 어떤 경우에도 전략 가이드가 증권사 주문을 자동 전송하지 않는다.
- `POST`/`GET`/`DELETE /api/members/{memberId}/portfolios/{portfolioId}/transactions`는 수동 매매 기록을 생성·조회·삭제한다. 삭제 전에는 남은 거래 이력으로 보유 수량을 다시 계산하며, 이후 매도가 초과 매도가 되는 경우 삭제를 차단한다.

## 리서치와 정책 문서

- 전략 후보와 근거 데이터: `research/data/strategies.json`
- 전략 정책과 미구현 범위: `research/STRATEGY_ENGINE_POLICY.md`
- 리서치 자료는 실행 전략과 동일하지 않다. 정책에서 채택한 규칙만 구현한다.

## 현재 구현 위치

포트폴리오 노출 비중 API와 위험 경고 API, Track A 골든 테스트, 주봉 데이터 신선도 가드, 시장 신호와 행동의 분리, `StrategyDecisionMaker` 기반 행동 규칙, 매수 시점·손절 상태 가이드 응답, 완료 주봉 캐시와 다종목 전략 가이드의 종목별 부분 실패 응답, 전략 메타데이터의 신뢰도·주의사항 응답, 검토용 손절 선택 주문 초안 도메인 모델, `PortfolioRiskPolicy`의 포트폴리오 저장 및 설정·조회 API까지 구현했다. 세부 상태와 다음 작업은 `docs/LEARNING_LOG.md`를 기준으로 한다.

## 현재 제한

- Google OIDC 기반 로그인 사용자와 `Member` 연결, `/api/members/{memberId}/...` 소유권 검증은 구현됐다. 기본 로컬 프로필은 학습 편의를 위해 인증을 끄며, 운영 프로필에서는 `tradeguide.auth.enabled=true`와 Google 클라이언트 비밀값을 제공해야 한다.
- React는 로컬 개발에서만 `VITE_LOCAL_MEMBER_ID`를 사용한다. 이 값이 없으면 `/api/auth/me`로 로그인 사용자를 조회하고, 인증되지 않았을 때 Google 로그인 화면을 표시한다.
- `/api/admin/**`은 인증 활성화 상태에서 차단한다. 역할 기반 관리자 기능과 운영자용 자산 카탈로그 관리는 아직 구현하지 않았다.
- 완료 주봉 캐시는 애플리케이션 메모리를 사용하므로 애플리케이션 재시작 시 초기화된다. 분산 캐시나 다중 인스턴스 운영은 아직 고려하지 않았다.
- 시장 데이터 제공자 선택과 증권 계좌 연결은 별도 모델이다. 포트폴리오는 가격·캔들·자산 참조 제공자를 기록하는 `PortfolioMarketDataPreference`를 가지며, `TWELVE_DATA`(서버 전역 자격 증명, US 전용)와 `TOSS_SECURITIES`(포트폴리오에 연결된 검증된 토스증권 연결이 있어야 선택 가능, US·KR 모두 지원)를 선택할 수 있다(이 문서의 이전 버전은 `TWELVE_DATA`만 가능하다고 잘못 기록했었다 - 2026-09-16 코드 확인으로 정정). 사용자별 토스증권 자격 증명과 계좌 참조값은 암호화된 `BrokerConnection`·`BrokerAccount`로 관리하고, 연결 확인과 계좌 목록·보유 종목 읽기 전용 조회를 지원한다.
- 사용자가 명시적으로 갱신한 토스증권 보유 종목은 `PortfolioBrokerHoldingSnapshot`으로 별도 저장한다. 저장된 최신 스냅샷 조회와 Trade Guide 보유 종목 비교는 외부 증권사 API를 호출하거나 자격 증명을 복호화하지 않는다. 스냅샷은 수동 `TradeTransaction`과 파생 `Holding`을 자동 생성·수정하지 않는다. `ONLY_IN_BROKER` 항목은 사용자가 하나씩 승인할 때만 `BROKER_OPENING_BALANCE` 출처의 개시 잔고 매수 원장으로 반영한다.
- 이미 원장에 있는 종목의 `QUANTITY_MISMATCH`(수량 불일치)는 `PortfolioBrokerHoldingAdjustment` 승인으로 해소한다. 증권사 수량이 원장보다 많으면 부족분을 조정 매수로, **원장 수량이 증권사보다 많으면 초과분을 조정 매도로** 반영하며(2026-09-16 추가), 두 방향 모두 `BROKER_HOLDING_ADJUSTMENT` 출처의 매매 기록을 만들고 언제든 취소(원복)할 수 있다. 조정 매도는 실제 체결가를 알 수 없으므로 단가를 원장의 조정 전 평균 매입가와 동일하게 두어 실현손익을 0으로 만들고 평균 매입가를 그대로 유지한다. 두 수량이 이미 같으면 조정 자체를 거부한다. 어떤 방향도 실제 증권사 주문을 발생시키지 않는다.
- 토스증권 주문 이력은 읽기 전용 미리보기·정합성 대조·사용자 승인 후 `BROKER_ORDER_HISTORY` 원장 반영까지 구현되어 있다. 다만 현재 주문 어댑터의 원장 반영 시장은 미국으로 제한되고, 비매매 이벤트·개별 체결 단위·정정/취소 체인 연결은 토스 API가 제공하지 않는다. 주문 전송과 자동 매매는 구현하지 않았으며, 다중 사용자 운영에는 사용자별 자격 증명 수명주기와 운영 암호화 키 관리가 추가로 필요하다. 상세 기준은 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`를 따른다.

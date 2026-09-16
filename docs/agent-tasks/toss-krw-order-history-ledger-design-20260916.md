# 토스 국내(KRW) 주문 이력 원장 반영 - 설계 계약

> 상태: 설계 초안(구현 전). `src/**` 변경은 아직 없다. 이 문서는 구현 착수 전 사용자
> 승인을 받기 위한 작업 계약이다.

## 배경

현재 토스증권 주문 이력 가져오기는 미국(USD) 주문만 실제 매매 원장(`TradeTransaction`)
에 반영하고, KRW 주문은 "제외 건수"로만 보고한다(`TossSecuritiesOrderHistoryProvider`,
`unsupportedCurrencyCount`). `docs/LEARNING_LOG.md`가 이 확장을 "통화·시장 판정,
원화 평가 모델, 자산 카탈로그 계약을 확정한 뒤 별도 슬라이스로 진행"하도록 남겨뒀다.

## 사전 조사 결과 (구현 전 현황 확인)

- `Market` enum은 `US`/`KR`을 이미 갖고 있지만, 실제 실행 경로(종목 검색, 주문 이력
  판정, 통화 모델, 시세 제공자 라우팅) 전부가 US 전용으로 막혀 있다.
- `TradeTransaction`, `Holding`, `HoldingValuation`, `PortfolioValuation`,
  `MarketPrice` 어디에도 `currency` 필드가 없다 - 시장이 US라는 전제 하에 금액이
  암묵적으로 USD라고 가정하는 구조다.
- `TossSecuritiesOrderHistoryProvider`는 `currency != "USD"`면 그 레코드를 만들지 않고
  `unsupportedCurrencyCount`만 올린다(화이트리스트 방식, KRW 명시 지원 없음).
- `TwelveDataAssetSearchProvider`는 `market != Market.US`면 즉시 빈 결과를 반환한다 -
  KR 종목 검색 공급자가 아예 없다.
- `TossSecuritiesMarketPriceProvider`는 KR에 대해 통화 검증을 건너뛰므로(코드상
  `expectedCurrency(KR) == null`) 가격 자체는 받아올 수 있어 보이지만, 그 값이 KRW라는
  것을 아는 코드가 어디에도 없다 - 그대로 두면 KRW 숫자를 USD인 것처럼 합산하는 조용한
  오류가 날 위험이 있다.
- 기존 리서치(`research/reports/toss-transaction-history-api-contract-audit.md` U-10)가
  "환율 필드 없음, USD는 USD 그대로 저장, 원화 환산은 별도 정책"이라고 이미 결정해 뒀다 -
  이번 설계도 그 방향과 일치시킨다.

## 확정된 정책 (사용자 승인, 2026-09-16)

**환율 변환 없이 통화별로 별도 표시한다.** 포트폴리오 평가금액은 하나의 합계로
합치지 않고, USD 합계와 KRW 합계를 각각 보여준다(예: "USD $12,340 + KRW ₩5,200,000").
외부 환율 데이터 소스가 필요 없고, 환율 변동으로 인한 평가액 왜곡 문제도 생기지 않는다.

## 설계 결정 확정 (사용자 승인, 2026-09-16)

1. **KR 종목 표시명**: 이번 슬라이스에서는 KR 전용 종목 검색 공급자를 만들지 않는다.
   `AssetListing`에 미리 등록되지 않은 KR 티커는 `AssetDisplayNameResolver`가 이미
   하는 대로 티커 자체를 표시명으로 대체한다(범위 축소, 승인됨). KR 종목 검색은 별도
   후속 작업으로 분리한다.
2. **KR 시세 조회**: 이번 슬라이스에 포함한다. `TossSecuritiesMarketPriceProvider`가
   KR 가격을 실제로 정확히 반환하는지 실제 호출로 검증하고, 필요하면 통화 태깅을
   추가한다(보유 평가금액이 정확하려면 반드시 필요하다고 판단해 승인됨).
3. **전략 가이드 대상 여부**: Track A/B 전략 가이드는 지금처럼 US 종목만 대상으로
   유지하고, KR 종목은 이번 슬라이스에서 "보유 조회·매매 이력"에만 한정한다.

## 제안하는 도메인/계약 변경 (승인 시 구현)

1. `Currency` enum 신규 추가(`USD`, `KRW`), `Market`에 `getCurrency()` 파생 메서드
   추가(1:1 매핑 - 이 프로젝트 범위에서 시장당 통화가 하나뿐이므로 별도 필드 저장 대신
   파생값으로 충분, 불필요한 중복 상태를 피함).
2. `MarketPrice`에 `currency` 필드 추가(제공자가 응답에 명시한 통화를 그대로 저장,
   `Market.getCurrency()`와 불일치하면 명시적으로 예외 처리 - 조용히 무시하지 않음).
3. `TradeTransaction`은 통화를 별도로 저장하지 않고 `market.getCurrency()`로 파생한다
   (거래 시점의 시장이 이미 통화를 결정하므로 중복 저장 불필요).
4. `TossSecuritiesOrderHistoryProvider`의 시장 판정을 확장한다 - KRX 숫자 심볼 패턴
   인식을 추가하고, `currency == KRW && market == KR`이면 레코드를 생성한다(현재
   `unsupportedCurrencyCount`로만 세던 것을 실제 반영 대상으로 전환). 애매한 조합(예:
   `currency=KRW`인데 심볼이 US 패턴)은 여전히 명시적으로 제외하고 사유를 기록한다.
5. `PortfolioValuation` 응답을 통화별 하위 집계를 포함하도록 확장한다(예:
   `valuationsByCurrency: {USD: {...}, KRW: {...}}`, 기존 최상위 필드는 하위 호환을
   위해 USD 기준으로 유지하거나, 프런트엔드와 함께 새 계약으로 전환 - API 계약 변경이라
   프런트엔드 영향을 함께 설명해야 한다).
6. 프런트엔드는 대시보드/보유 종목 화면에서 통화별 합계를 나란히 표시하도록 변경
   (Antigravity 담당, 별도 작업 계약 필요).

## 구현 순서 제안 (승인 시)

1. `Currency` enum, `Market.getCurrency()`, `MarketPrice.currency` - 작고 격리된 변경,
   기존 테스트 영향 최소.
2. `TossSecuritiesOrderHistoryProvider`의 KR/KRW 판정 확장 - 계약 감사 문서(U-10 등)
   기준으로 테스트 케이스 작성.
3. `PortfolioValuation`/`HoldingValuation`의 통화별 집계 - 기존 US 단일 통화 테스트가
   깨지지 않는지 확인하며 진행.
4. 프런트엔드 반영(별도 계약).
5. (선택, 위 결정 2번에 따라) KR 시세 조회 실제 검증.

각 단계는 이 프로젝트 관행대로 API 계약, 상태·오류 처리, 테스트까지 하나의 실행
가능한 세로 기능으로 끝낸다.

## 이 문서의 범위 밖

- 환율 변환(사용자가 명시적으로 원하지 않는 한 다루지 않음).
- KR 시장 전략 가이드(Track A/B) 확장 - 별도 결정 필요.
- KR 종목 실시간 검색 UX(위 미결정 1번에 따라 범위 포함 여부 결정).

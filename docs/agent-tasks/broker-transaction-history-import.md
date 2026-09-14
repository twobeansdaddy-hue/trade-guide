# 증권사 거래 내역 가져오기 설계 (구현 준비)

- Owner: Claude research-design
- Work mode: `research | design` (구현·커밋 없음)
- Task ID: `broker-transaction-history-import-readiness`
- 근거 범위: 저장소 현재 코드와 문서만 사용했다. 외부 증권사 API, `.env`,
  `application-local.yml`, 자격 증명은 조회하지 않았다.

## 1. 목적과 이 문서의 읽는 법

증권사 계좌의 **거래 내역(체결 이력)** 을 Trade Guide 매매 원장(`trade_transactions`)에
반영하기 위한 설계다. 현재 구현된 것은 보유 종목 스냅샷과 개시 잔고 승인까지이며,
거래 내역 가져오기는 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`의 구현 순서 6단계
("Design explicit import/audit semantics before modifying the transaction ledger")에 해당한다.

이 문서는 두 종류의 정보를 **의도적으로 분리**한다.

- **확정 사실(F)**: 저장소 코드·마이그레이션·문서에서 직접 확인한 내용. 파일 경로를 함께 적는다.
- **미확인 제공자 계약(U)**: 토스증권 공식 문서로 확인해야 하는 항목. 여기서는 엔드포인트,
  필드명, 페이지네이션 규칙을 **추측해 적지 않는다.** 확인 대상 목록만 제시한다.

구현 착수 조건은 6장의 미확인 항목이 공식 문서로 채워지고, 12장의 사용자 제품 결정이
확정되는 것이다. 그 전에는 원장에 쓰는 코드를 작성하지 않는다.

## 2. 확정 사실 (F)

### 2.1 제공자 경계

- `BrokerProviderCapability`에 `TRANSACTION_HISTORY_IMPORT`가 **이미 정의되어 있으나
  어떤 제공자도 선언하지 않는다.** `BrokerProvider.TOSS_SECURITIES`는
  `CONNECTION_VERIFICATION`, `HOLDING_SNAPSHOT`만 선언한다.
  (`src/main/java/com/tradeguide/domain/broker/BrokerProviderCapability.java`,
  `.../domain/broker/BrokerProvider.java`)
- `BrokerProviderRegistry`는 현재 `BrokerConnectionVerifier`만 매핑한다.
  보유 종목 제공자(`BrokerHoldingsProvider`)는 레지스트리를 거치지 않고
  `PortfolioBrokerHoldingSnapshotService` 생성자에서 `List<BrokerHoldingsProvider>`로 직접
  주입돼 `EnumMap`으로 조립된다. 즉 **제공자 조회 경로가 두 가지로 갈라져 있다.**
  (`.../service/broker/BrokerProviderRegistry.java`,
  `.../service/broker/PortfolioBrokerHoldingSnapshotService.java`)
- 제공자 어댑터 계약은 자격 증명을 호출 인자로만 받는다:
  `BrokerHoldingsProvider.fetchHoldings(clientId, clientSecret, accountSequence)`.
  구현체는 저장·로깅하지 않는다.
- `BrokerHoldingSnapshot`은 `unsupportedMarketCount`로 **지원하지 않아 제외한 건수를 반드시
  보고한다.** 조용한 누락을 금지하는 기존 원칙이다.

### 2.2 매매 원장

- `TradeTransaction` 필드: `portfolio, market, ticker, tradeType, quantity(19,6),
  executedPrice(19,4), fee(19,4), tradedAt(Instant), source, createdAt`.
  (`.../domain/trade/TradeTransaction.java`)
- **통화 필드가 없다.** `src/main/java` 전체에 `currency` 개념이 존재하지 않는다.
- **외부 거래 식별자, 수정 시각(updatedAt), 세금 필드가 없다.**
- `createdAt`은 생성자에서 `Instant.now()`를 직접 호출한다. 반면
  `PortfolioBrokerHoldingImportWriter`는 주입된 `Clock`을 쓴다. 시간 주입 방식이 혼재한다.
- `TradeTransactionSource`는 `MANUAL`, `BROKER_OPENING_BALANCE` 두 값뿐이다.
- DB: `trade_transactions.traded_at`, `created_at`은 `TIMESTAMP(6) WITH TIME ZONE`이고
  (`V1__create_initial_schema.sql`), 증권사 계열 테이블은 `TIMESTAMP(6)`(`LocalDateTime`)이다
  (`V9`, `V10`). 시간대 표현이 계층별로 다르다.
- `source` 컬럼은 `V10__create_broker_holding_imports.sql`에서 `DEFAULT 'MANUAL'`로 추가한 뒤
  기본값을 제거했다. 최신 마이그레이션은 `V12`이므로 다음 번호는 **`V13`** 이다.

### 2.3 보유 종목 계산과 검증

- `HoldingCalculator.calculate()`는 거래를 `tradedAt` 오름차순으로 정렬해 재생한다.
  - 매수: `평단 = (기존 총원가 + 체결가*수량 + 수수료) / 새 수량`. **수수료가 취득원가에 포함된다.**
  - 매도: 수량만 차감한다. **매도 수수료는 어디에도 반영되지 않고 버려진다.**
    실현손익 도메인 자체가 없다.
  - 보유 수량보다 큰 매도는 `IllegalArgumentException("매도 수량이 보유 수량보다 많습니다.")`로
    **전체 계산을 실패시킨다.**
- `TradeTransactionService`는 생성·삭제 시 전체 이력을 다시 계산해 위 규칙을 사전 검증한다
  (`validateTransactionHistory`, `deleteTradeTransaction`).
- 삭제 보호: `source == BROKER_OPENING_BALANCE`인 행은 일반 삭제 API가 거부하고
  `TradeTransactionProtectedException`을 던진다.
- 자산 카탈로그 검증이 두 갈래다. 수동 등록은 `ensureActiveListingForTrade`,
  개시 잔고 승인은 `requireActiveListing`을 쓴다. 둘 다 **활성 상장 종목만 허용**한다.
  (`.../service/asset/AssetListingService.java`)

### 2.4 개시 잔고 승인 패턴 (재사용할 선례)

- `PortfolioBrokerHoldingImport`는 승인 시점 값(수량·평단·표시명·스냅샷 기준 시각),
  생성된 `tradeTransactionId`, 승인자, 승인 시각, 상태(`ACTIVE`/`REVOKED`)를 보존한다.
- `trade_transaction_id`는 **의도적으로 외래키를 걸지 않는다.** 취소로 원장 행이 삭제돼도
  감사 이력이 남아야 하기 때문이다 (`V10` 주석).
- 중복 승인은 `uk_broker_holding_imports_snapshot_item` 유니크 제약으로 DB에서 막고,
  동시 요청은 `PortfolioBrokerHoldingImportWriter`를 **별도 빈으로 분리한 트랜잭션 경계**에서
  제약 위반 → 롤백 → 새 트랜잭션 재조회 순으로 복구한다. 재시도는 `200 OK`로 기존 결과를 돌려준다.
- 생성되는 원장 행은 `TradeType.BUY`, `fee = 0`, `tradedAt = 승인 시각`이다.
  **실제 체결 시각이 아니다.**
- 계좌 행은 삭제하지 않고 `BrokerAccountStatus.DETACHED`로 표시해 과거 이력의 참조 무결성을 지킨다
  (`BrokerAccount`, `BrokerConnection.reconcileVerifiedAccounts`).

### 2.5 현재 API 형태

- 증권사 관련 경로는 `/api/members/{memberId}/portfolios/{portfolioId}/...` 아래에 있다
  (`PortfolioBrokerLinkController`): `broker-link-candidates`, `broker-links`,
  `broker-sync-preview`, `broker-holding-snapshots`, `broker-holding-snapshots/latest`,
  `broker-holding-snapshots/latest/comparison`, `broker-holding-imports`.
- 아키텍처 문서는 신규 민감 기능에 `/api/me`를 권장하지만, **현재 구현은 전부 `/api/members`** 다.
  이 문서는 기존 구현 일관성을 우선해 `/api/members`를 제안하고, 경로 이관은 별도 과제로 둔다.
- 오류 매핑은 `GlobalExceptionHandler`에 이미 증권사 계열 예외 6종이 등록돼 있다
  (503 unavailable, 404 not found, 409 conflict, 422 unprocessable 등).

### 2.6 시장·통화·프론트엔드 제약

- `Market`은 `US`, `KR`이다. `TossSecuritiesHoldingsProvider`는 `marketCountry`를 `US`/`KR`로만
  변환하고 나머지는 제외 후 건수만 보고한다.
- `docs/LEARNING_LOG.md` 2026-09-03 기록: **매매 기록 등록 화면은 미국 시장으로 제한**되어 있고,
  한국 시장은 "종목 검색·현재가·통화 모델이 준비될 때까지" 선택하지 않는다.
- `PortfolioValuationCalculator`는 `BigDecimal`을 **통화 구분 없이 단순 합산**한다.
  KR 거래를 원장에 넣으면 KRW와 USD가 한 숫자로 더해진다. 이는 가져오기 기능이 아니라
  **선행 과제**로 해결해야 한다.

### 2.7 토스증권 코드에서 확인된 것 (이것만이 사실이다)

`src/main/java/com/tradeguide/service/broker/Toss*.java` 기준:

| 항목 | 코드에 존재하는 값 |
| --- | --- |
| 기본 URL | `toss-securities.base-url`, 기본값 `https://openapi.tossinvest.com` |
| 토큰 | `POST /oauth2/token`, `grant_type=client_credentials`, form 인코딩, 응답 `access_token` |
| 계좌 목록 | `GET /api/v1/accounts`, `result[]`에 `accountSeq`, `accountNo`, `accountType` |
| 보유 종목 | `GET /api/v1/holdings`, 계좌 헤더 `X-Tossinvest-Account`, `result.items[]` |
| 보유 항목 필드 | `symbol`, `name`, `marketCountry`, `quantity`(문자열 decimal), `averagePurchasePrice`(문자열 decimal) |
| 알 수 없는 enum | 명세가 클라이언트에 미지 enum 허용을 요구 → 미지원 시장은 제외 후 건수 보고 |
| 액세스 토큰 | 요청 구간에서만 사용, 저장하지 않음 |

**거래 내역 엔드포인트는 이 저장소 어디에도 없다.** 아키텍처 문서의 client-credentials·계좌 헤더·
허용 IP 제한 설명도 연결/보유 기준이며 거래 내역 계약을 보장하지 않는다.

## 3. 제공자 중립 경계 설계

증권사별 분기를 서비스 계층에 넣지 않는다. 기존 `BrokerHoldingsProvider`와 동일한 형태로
**어댑터 인터페이스 하나**를 추가한다.

```java
public interface BrokerTransactionHistoryProvider {
    BrokerProvider getProvider();

    BrokerTransactionHistoryPage fetchTransactions(
            String clientId,
            String clientSecret,
            String accountSequence,
            BrokerTransactionHistoryQuery query
    );
}
```

도메인 레코드(제공자 중립):

```java
record BrokerTransactionHistoryQuery(LocalDate from, LocalDate to, String cursor, int pageSize) {}

record BrokerTransactionHistoryPage(
        List<BrokerTransactionRecord> records,
        String nextCursor,              // null 이면 마지막 페이지
        int unsupportedMarketCount,     // 제외한 미지원 시장 건수
        int unsupportedTypeCount        // 제외한 미지원 거래 유형 건수
) {}

record BrokerTransactionRecord(
        String externalTransactionId,   // 제공자 고유 체결 식별자 (U-6에서 확인)
        Market market,
        String ticker,
        String displayName,
        BrokerTransactionType type,
        BigDecimal quantity,
        BigDecimal executedPrice,
        BigDecimal fee,
        BigDecimal tax,
        String currencyCode,            // ISO 4217, 제공자 원값
        Instant executedAt,
        String providerTypeCode,        // 원본 유형 코드 (감사·미지원 유형 보고용)
        boolean canceled                // 제공자가 취소/정정으로 표시한 건 (U-9)
) {}
```

```java
enum BrokerTransactionType {
    BUY, SELL,                          // Phase 2에서 원장에 반영
    DIVIDEND, DEPOSIT, WITHDRAWAL,      // Phase 1~2에서는 건수만 보고
    TRANSFER_IN, TRANSFER_OUT,
    CORPORATE_ACTION, FX, UNKNOWN
}
```

경계 규칙:

1. 어댑터는 **HTTP·JSON·제공자 코드 체계만** 안다. `TradeTransaction`, `Portfolio`,
   `HoldingCalculator`를 참조하지 않는다.
2. 서비스는 **제공자 이름으로 분기하지 않는다.** `BrokerProviderRegistry`에
   `requireTransactionHistoryProvider(provider)`를 추가하고, 동시에 기존
   `BrokerHoldingsProvider` 조회도 레지스트리로 옮겨 조회 경로를 하나로 통일한다(F 2.1의 갈라짐 해소).
3. 제공자가 `TRANSACTION_HISTORY_IMPORT`를 선언하지 않으면 **API가 422로 즉시 거부**한다.
   토스증권의 capability 선언은 공식 계약 확인(6장) 이후에만 추가한다.
4. 어댑터는 미지원 시장·유형을 **버리지 않고 건수로 보고**한다(F 2.1 원칙 유지).
5. 어댑터는 `clientId`, `clientSecret`, 복호화된 `accountSequence`, 액세스 토큰, 원문 응답을
   저장하거나 로그·예외 메시지에 넣지 않는다.

## 4. 출처(provenance)와 원장 통합 규칙

### 4.1 출처 값

`TradeTransactionSource`에 `BROKER_TRANSACTION_HISTORY`를 추가한다. 세 값의 의미를 고정한다.

| 값 | 의미 | `tradedAt` | 수정·삭제 |
| --- | --- | --- | --- |
| `MANUAL` | 사용자가 직접 입력 | 사용자가 입력한 체결 시각 | 일반 삭제 API 허용 |
| `BROKER_OPENING_BALANCE` | 스냅샷 1건을 개시 잔고로 승인 | **승인 시각**(합성 값) | 전용 취소 API만 |
| `BROKER_TRANSACTION_HISTORY` | 증권사가 보고한 실제 체결 | **제공자 체결 시각** | 전용 취소 API만 (결정 D-10) |

`TradeTransactionResponse`는 이미 `source`를 노출하므로 프론트엔드는 배지 표시만 확장하면 된다
(`TransactionHistoryList.tsx`가 기존 두 값을 이미 다룬다).

### 4.2 원장에 절대 하지 않는 것

- 동기화가 기존 `MANUAL` 행을 수정·삭제·병합하지 않는다.
- 수량 차이를 자동 정정하지 않는다.
- 사용자의 명시적 승인 없이 원장에 어떤 행도 만들지 않는다.
- 나중 동기화가 이미 승인된 원장 행의 값을 **덮어쓰지 않는다**(정정은 8장 절차를 따른다).

### 4.3 개시 잔고와의 충돌 (설계상 가장 큰 위험)

`BROKER_OPENING_BALANCE` 행은 `tradedAt = 승인 시각`, 즉 **오늘 날짜의 합성 매수**다.
여기에 과거 체결 이력을 넣으면 두 가지가 동시에 깨진다.

1. **이중 계상**: 같은 보유분이 "개시 잔고 매수" + "과거 실제 매수"로 두 번 잡힌다.
2. **정렬 붕괴**: `HoldingCalculator`는 `tradedAt` 오름차순으로 재생하므로,
   오늘 날짜의 개시 잔고 매수보다 **앞선 과거 매도**가 들어오면 보유 수량 부족으로
   전체 계산이 예외로 실패한다. 즉 원장 전체가 조회 불가 상태가 된다.

따라서 다음 중 하나를 **정책으로 확정해야 한다**(D-1).

- (A) 활성 개시 잔고가 있는 포트폴리오는 거래 내역 가져오기를 `409`로 거부하고,
  먼저 개시 잔고를 취소하도록 안내한다. — 가장 단순하고 안전. **권장.**
- (B) 개시 잔고 승인 시각 이후 체결만 허용한다. 과거 구간은 원천 차단.
- (C) 승인 시 개시 잔고를 자동 취소한다. — 자동 원장 변경이므로 현재 원칙과 충돌. 비권장.

### 4.4 부분 이력과 초과 매도

증권사 거래 내역은 조회 가능 기간이 제한된다(U-4). 기간 앞쪽이 잘린 이력을 넣으면
매수 없는 매도가 생겨 `HoldingCalculator`가 실패한다.

규칙: 승인 전에 **기존 원장 + 승인 대상 전체**를 `HoldingCalculator`로 재생해 검증하고,
실패하면 **배치 전체를 거부**한다. 오류 메시지는 최초 실패 종목과 시각을 알려 주되,
자동으로 보정 매수를 만들지 않는다. (부분 승인은 D-8에서 결정)

### 4.5 상장 폐지·비활성 종목

`requireActiveListing`은 활성 상장만 허용한다. 과거 이력에는 상장 폐지·티커 변경 종목이
포함될 수 있으므로, 현재 규칙 그대로면 정상 이력이 `422`로 거부된다. 결정 필요(D-4).

### 4.6 수수료·세금

- 매수 수수료는 취득원가에 포함된다(F 2.3). 제공자 수수료를 그대로 넣으면 평단이 바뀐다.
- **매도 수수료·세금은 현재 계산에 전혀 반영되지 않는다.** 저장은 하되 "지금은 평가에
  영향을 주지 않는다"는 사실을 문서와 UI에 명시한다. 실현손익 도메인은 별도 과제다(D-6).
- `tax`는 `fee`에 합산하지 않는다. 합산하면 매수 취득원가 의미가 조용히 바뀐다.
  별도 컬럼으로 추가하고 계산에는 넣지 않는다.

### 4.7 통화

Phase 1~2는 `Market.US` + `USD`만 허용한다. 그 외 통화·시장 건은 **거부가 아니라 제외 후 건수 보고**로
처리한다(F 2.1 원칙). KR 지원은 통화 모델과 평가 합산 규칙이 생긴 뒤에만 연다(D-7).
어댑터가 받은 `currencyCode`가 시장 기대값과 다르면 그 건은 미지원으로 분류하고 건수에 포함한다.

### 4.8 전략 입력 영향

`PortfolioStrategyGuideService`는 `HoldingService`가 만든 `Holding` 목록을 입력으로 쓴다.
가져오기는 원장만 바꾸므로 전략 계층 변경은 필요 없다. 다만 두 가지를 지킨다.

- 승인은 원자적이다. 부분 반영된 원장 위에서 전략 가이드가 계산되면 안 된다.
- 취소(revoke)도 원자적으로 되돌린다. 취소 후 `HoldingCalculator` 재생이 실패하면
  삭제 전에 차단한다(기존 `deleteTradeTransaction`과 같은 순서).

## 5. 멱등성과 중복 방지

### 5.1 자연 키

`(broker_account_id, external_transaction_id)`에 **DB 유니크 제약**을 건다.
포트폴리오가 아니라 계좌 기준인 이유: 링크는 나중에 다른 포트폴리오로 바뀔 수 있고,
같은 체결이 두 포트폴리오에 각각 들어가면 진짜 중복이 된다.

`external_transaction_id`가 제공자에 없거나 불안정하면(U-6) **가져오기를 열지 않는다.**
콘텐츠 해시(계좌+티커+수량+가격+시각)로 대체하면 같은 초에 발생한 동일 분할 체결을
한 건으로 잘못 합치므로, 대체 키는 기본 설계로 채택하지 않는다(D-3).

### 5.2 동시성 복구

`PortfolioBrokerHoldingImportWriter`의 선례를 그대로 따른다.

- 쓰기 트랜잭션을 별도 빈(`BrokerTransactionImportWriter`)으로 분리한다.
  같은 빈 내부 호출은 프록시를 거치지 않아 트랜잭션 경계가 성립하지 않는다.
- PostgreSQL은 실패 문장 이후 같은 트랜잭션의 후속 문장을 거부하므로,
  `DataIntegrityViolationException`은 **롤백 후 새 트랜잭션에서 기존 결과 재조회**로 복구한다.
- 동일 요청 재시도는 기존 결과를 `200 OK`로 반환한다.

### 5.3 재조회 구간 겹침

같은 기간을 다시 조회하는 것은 정상 동작이어야 한다. 중복은 유니크 제약이 막고,
스테이징 단계에서 이미 존재하는 외부 식별자는 `ALREADY_IMPORTED` 상태로 표시해
승인 대상에서 제외한다. 사용자에게는 "새 건 N개 / 이미 반영 M개"로 보여 준다.

## 6. 토스증권 공식 문서로 확인해야 할 항목 (U)

**아래 항목은 확인 전까지 코드로 옮기지 않는다. 엔드포인트·필드명을 만들어 내지 않는다.**
확인 출처는 `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json`과 공식 개발자 문서다.

| # | 확인 항목 | 확인되지 않으면 |
| --- | --- | --- |
| U-1 | 체결/거래 내역 엔드포인트가 **존재하는가**, 경로·메서드·필수 헤더는 무엇인가 | 기능 자체를 착수하지 않는다 |
| U-2 | 계좌 지정 방식이 보유 종목과 동일한 `X-Tossinvest-Account`인가 | 어댑터 계약 확정 불가 |
| U-3 | 페이지네이션 방식(cursor/page/offset), 페이지 최대 크기, 정렬 순서 | 전체 이력 수집 보장 불가 |
| U-4 | 조회 가능 최대 과거 기간, 한 요청의 최대 기간 범위 | 4.4 부분 이력 정책 확정 불가 |
| U-5 | 시각 의미: 체결 시각인가 결제일인가, 타임존, **날짜만 제공되는가** | `tradedAt` 매핑 불가 (D-5) |
| U-6 | **안정적인 체결 고유 식별자**가 있는가, 재조회 시 값이 동일한가 | 멱등성 설계 성립 불가 → 착수 금지 |
| U-7 | 거래 유형 코드 체계 전체 목록(매수/매도 외 배당·입출금·대체·권리) | 미지원 유형 분류 불가 |
| U-8 | 수수료·세금 분리 여부와 각 필드 의미(위탁수수료, 거래세, 원천징수) | 4.6 저장 스키마 확정 불가 |
| U-9 | 정정·취소 표현 방식: 같은 식별자 재기술인가, 반대 부호 별건인가 | 8장 정정 절차 확정 불가 |
| U-10 | 통화 필드와 환율 제공 여부, 외화 체결 금액 표기 기준 | KR/외화 확장 불가 |
| U-11 | 수량·금액 표현: 문자열 decimal인가 숫자인가, 소수 자릿수 | 스케일 손실 위험 |
| U-12 | 미지의 enum 허용 요구가 이 응답에도 적용되는가 | 파싱 실패 위험 |
| U-13 | 레이트 리밋, 동시 호출 제한, 허용 IP 제한이 이력 조회에도 적용되는가 | 운영 실패 처리 불가 |
| U-14 | 오류 응답 코드 체계(권한 없음/기간 초과/계좌 불일치) | 오류 매핑 불가 |
| U-15 | 이 API가 **개인 사용자 계좌 이력 조회 용도로 약관상 허용**되는가 | 릴리스 게이트 미충족 |

확인 결과는 이 문서 6장에 표로 추가하고, 그 뒤에야 `BrokerProvider.TOSS_SECURITIES`에
`TRANSACTION_HISTORY_IMPORT`를 선언한다.

## 7. 제안 엔티티·마이그레이션

### 7.1 `BrokerTransactionImportRun` (동기화 실행 감사)

아키텍처 문서의 "sync runs record provider, start/end time, result, item counts, sanitized
failure code; never contain secrets or full broker responses"를 그대로 구현한다.

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `portfolio_id` | BIGINT FK | 실행 시점 포트폴리오 |
| `broker_connection_id` | BIGINT FK | |
| `broker_account_id` | BIGINT FK | 계좌 행은 삭제하지 않음(F 2.4) |
| `provider` | VARCHAR | enum 문자열 |
| `requested_from` / `requested_to` | DATE | 사용자가 요청한 구간 |
| `started_at` / `finished_at` | TIMESTAMP(6) | |
| `status` | VARCHAR | `RUNNING`, `STAGED`, `FAILED` |
| `fetched_count` | INT | 제공자가 반환한 건수 |
| `staged_count` | INT | 승인 후보로 남은 건수 |
| `already_imported_count` | INT | 유니크 키 기준 기존 반영 건수 |
| `unsupported_market_count` | INT | 제외 건수 |
| `unsupported_type_count` | INT | 제외 건수 |
| `failure_code` | VARCHAR NULL | 정제된 코드만. 원문 응답·비밀값 금지 |

### 7.2 `BrokerTransactionImportItem` (체결 1건의 스테이징 + 승인 감사)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `run_id` | BIGINT FK | 최초 수집 실행 |
| `broker_account_id` | BIGINT FK | 유니크 키 구성 |
| `external_transaction_id` | VARCHAR NOT NULL | 제공자 고유 식별자 |
| `market`, `ticker`, `display_name` | VARCHAR | |
| `transaction_type` | VARCHAR | `BUY`/`SELL` (원장 반영 대상) |
| `provider_type_code` | VARCHAR | 원본 코드 보존 |
| `quantity` | NUMERIC(19,6) | 원장과 동일 스케일 |
| `executed_price` | NUMERIC(19,4) | |
| `fee` | NUMERIC(19,4) | |
| `tax` | NUMERIC(19,4) | 계산에는 미반영(4.6) |
| `currency_code` | VARCHAR(3) | |
| `executed_at` | TIMESTAMP(6) WITH TIME ZONE | 원장 `traded_at`과 동일 타입 |
| `status` | VARCHAR | `STAGED`, `APPROVED`, `REVOKED`, `SKIPPED_UNSUPPORTED`, `ALREADY_IMPORTED`, `SUPERSEDED` |
| `trade_transaction_id` | BIGINT NULL | **FK 없음**(F 2.4 선례). 취소 후에도 값 보존 |
| `approved_by_member_id` | BIGINT FK NULL | |
| `approved_at` | TIMESTAMP(6) NULL | |
| `superseded_by_item_id` | BIGINT NULL | 정정 체인(8장) |

제약·인덱스:

- `UNIQUE (broker_account_id, external_transaction_id)` — 중복 반영 차단(5.1)
- `INDEX (run_id, executed_at)` — 스테이징 목록 조회
- `INDEX (broker_account_id, executed_at DESC)` — 계좌별 이력 조회

### 7.3 마이그레이션

- `V13__add_broker_transaction_history_source.sql`
  `trade_transactions.source` enum 값 추가는 문자열 컬럼이므로 DDL 변경이 없다.
  이 파일에는 필요한 인덱스만 둔다. 기존 행은 `MANUAL`/`BROKER_OPENING_BALANCE`를 유지한다.
- `V14__create_broker_transaction_imports.sql`
  위 두 테이블, 유니크 제약, 인덱스를 생성한다.

기존 데이터 마이그레이션은 없다. 과거 `MANUAL` 행을 소급 분류하지 않는다.

## 8. 정정·취소(correction/reversal) 처리

제공자가 정정을 표현하는 방식은 U-9에서 확인해야 한다. 두 경우 모두 **원장 행을 조용히 수정하지
않는다**는 원칙은 동일하다.

- **재기술형**(같은 식별자, 값 변경): 다음 동기화에서 값 차이를 감지하면 해당 항목을
  `SUPERSEDED`로 표시하고 **새 스테이징 항목**을 만든다. 사용자가 승인하면
  기존 원장 행을 전용 경로로 삭제하고 새 행을 생성한 뒤, 두 항목을 `superseded_by_item_id`로 잇는다.
  승인 전까지 원장은 그대로 둔다.
- **반대건형**(취소 체결이 별건으로 옴): 별건도 일반 체결처럼 스테이징한다.
  단 승인 시 `HoldingCalculator` 재생 검증을 반드시 통과해야 한다(4.4).

두 경우 모두 사용자에게 "증권사가 이미 반영한 거래를 정정했습니다"를 명시적으로 보여 주고,
자동 반영하지 않는다(D-9).

## 9. 제안 API 계약

경로는 기존 컨트롤러 관례를 따른다. 인증·소유권은 `MemberAccessService.requireMemberAccess`로
기존과 동일하게 검사한다.

```text
POST   /api/members/{memberId}/portfolios/{portfolioId}/broker-transaction-imports
GET    /api/members/{memberId}/portfolios/{portfolioId}/broker-transaction-imports
GET    /api/members/{memberId}/portfolios/{portfolioId}/broker-transaction-imports/{runId}
POST   /api/members/{memberId}/portfolios/{portfolioId}/broker-transaction-imports/{runId}/approval
DELETE /api/members/{memberId}/portfolios/{portfolioId}/broker-transaction-imports/{runId}/approval
```

| 엔드포인트 | 동작 | 성공 | 주요 실패 |
| --- | --- | --- | --- |
| `POST .../broker-transaction-imports` | 제공자 호출 → 스테이징 저장. **원장 미변경** | `201` run + 요약 | `422` capability 미지원/링크 없음, `503` 제공자 오류, `409` 실행 중 |
| `GET .../broker-transaction-imports` | 실행 이력 목록(최신순) | `200` | `404` 포트폴리오 |
| `GET .../{runId}` | 실행 요약 + 스테이징 항목 목록 | `200` | `404` |
| `POST .../{runId}/approval` | 스테이징 항목을 원장에 원자적 반영 | `201` 신규 / `200` 재시도 | `409` 초과 매도·개시 잔고 충돌·이미 승인, `422` 비활성 종목·미지원 통화 |
| `DELETE .../{runId}/approval` | 승인된 배치 전체 취소 | `204` | `409` 취소 후 계산 실패, `404` |

요청 본문:

```jsonc
// POST .../broker-transaction-imports
{ "from": "2026-01-01", "to": "2026-09-07" }

// POST .../{runId}/approval
{ "itemIds": [12, 13, 14] }   // D-8이 "전체 승인"으로 결정되면 본문 없음
```

응답에 포함하지 않는 것: 원문 계좌번호, `clientSecret`, 복호화된 `accountSequence`,
액세스 토큰, 제공자 원문 응답, 제공자 오류 원문.

새 예외와 매핑(기존 `GlobalExceptionHandler` 패턴 재사용):

| 예외 | 상태 | 용도 |
| --- | --- | --- |
| `BrokerCapabilityUnsupportedException` | `422` | 제공자가 `TRANSACTION_HISTORY_IMPORT` 미선언 |
| `BrokerTransactionImportConflictException` | `409` | 개시 잔고 충돌, 초과 매도, 중복 승인 |
| `BrokerTransactionImportUnprocessableException` | `422` | 비활성 상장, 미지원 통화·유형 |
| `BrokerTransactionImportNotFoundException` | `404` | run/item 없음 |
| 기존 `BrokerConnectionUnavailableException` | `503` | 제공자 호출 실패 |

## 10. 검증·테스트 계획

### 어댑터 (제공자 계층)

- 공식 계약 확인(6장) 후 **가짜 HTTP 응답 픽스처**로만 테스트한다. 실계좌·실키를 쓰지 않는다.
- 문자열 decimal 파싱, 스케일 보존, 미지 enum·미지원 시장·미지원 유형의 **건수 보고** 검증.
- 페이지네이션: 다중 페이지 수집, `nextCursor` 종료 조건, 중복 페이지 방지.
- 오류 응답 → `BrokerConnectionUnavailableException` 변환, 비밀값·원문 미노출.

### 스테이징 (원장 미변경)

- 스테이징 실행이 `trade_transactions`를 **한 행도 바꾸지 않음**을 명시적으로 검증.
- 같은 기간 재조회 시 기존 반영 건이 `ALREADY_IMPORTED`로 분류되는지.
- 유니크 제약 위반이 동시 요청에서 안전하게 복구되는지(개시 잔고 동시성 테스트와 동일 구조).

### 승인 (원장 변경)

- 승인 후 원장 행 수·`source` 값·`tradedAt`이 제공자 체결 시각인지.
- `HoldingCalculator` 재생 결과가 기대 보유 수량·평단과 일치하는지.
- 초과 매도 배치가 **전체 거부**되고 원장에 아무것도 남지 않는지(트랜잭션 원자성).
- 활성 개시 잔고가 있는 포트폴리오에서 `409`인지(D-1 결정 반영).
- 비활성 상장·미지원 통화 건의 거부/제외 동작(D-4, D-7 결정 반영).
- 재시도 멱등성: 같은 `runId` 재승인이 `200`으로 기존 결과 반환.

### 취소·보호

- 배치 취소가 원장 반영을 되돌리고 감사 항목은 `REVOKED`로 보존하는지.
- 취소가 이후 매도를 초과 매도로 만들면 **삭제 전 차단**되는지.
- 일반 거래 삭제 API가 `BROKER_TRANSACTION_HISTORY` 행을 거부하는지(D-10 결정 반영).
- 증권사 연결 삭제 시 참조 무결성(계좌 `DETACHED`, 감사 이력 보존) 유지.

### 보안·회귀

- API 응답·로그·예외 메시지에 비밀값이 없음을 어서션으로 확인.
- 다른 회원 포트폴리오 접근 `403/404`.
- 기존 스냅샷·개시 잔고·수동 매매 API 회귀.
- `./gradlew test` 전체 + PostgreSQL 통합 테스트, 프론트엔드 `npm run lint`·`npm run build`.
- UI 작업 시 데스크톱과 360px 폭 확인(AGENTS.md 규칙).

## 11. 단계별 범위

| 단계 | 내용 | 원장 변경 | 착수 조건 |
| --- | --- | --- | --- |
| **P0** 계약 확인 | 6장 U-1~U-15 확인, 결과를 문서화. capability 선언 여부 결정 | 없음 | 없음 |
| **P1** 제공자 경계 | `BrokerTransactionHistoryProvider` + 도메인 레코드 + 레지스트리 통합. 가짜 어댑터로 테스트 | 없음 | P0 완료, U-1·U-6 확인 |
| **P2** 스테이징 조회 | 실행/항목 테이블, `POST`·`GET` API, 미지원 건수 보고, 화면은 읽기 전용 검토 | 없음 | P1 완료, D-2·D-3 결정 |
| **P3** 승인·취소 | 원자적 승인, 멱등성, 초과 매도 차단, 배치 취소, 삭제 보호 | **있음** | P2 완료, D-1·D-4·D-5·D-8·D-10 결정 |
| **P4** 정정·확장 | 정정/취소 체인, 배당·권리 이벤트, KR·통화·실현손익 | 있음 | 통화 모델 선행, D-6·D-7·D-9·D-11 결정 |

다중 사용자 공개 운영은 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`의 릴리스 게이트
(사용자별 자격 증명 수명주기, 운영 키 관리, 허용 IP, 감사·삭제·사고 대응, 약관 승인)를
별도로 통과해야 한다. 이 설계가 그 게이트를 대체하지 않는다.

## 12. 사용자 제품 결정 목록 (승인 전 착수 금지)

| # | 결정 사항 | 선택지 | 권장 |
| --- | --- | --- | --- |
| D-1 | 활성 개시 잔고와의 공존 | (A) 거부 후 취소 유도 / (B) 승인 시각 이후만 허용 / (C) 자동 취소 | **A** |
| D-2 | 조회 구간 기본값과 최대 과거 기간 | 예: 기본 최근 3개월 / 사용자 지정 / 제공자 최대 | 제공자 최대(U-4) 내에서 사용자 지정 |
| D-3 | 안정적 외부 식별자가 없을 때 | (A) 기능 미제공 / (B) 콘텐츠 해시 대체 | **A** |
| D-4 | 상장 폐지·비활성 종목 이력 | (A) `422` 거부 / (B) 비활성 상장 허용 후 표시 / (C) 제외 후 건수 보고 | **B 또는 C** (A는 정상 이력을 막음) |
| D-5 | 제공자가 날짜만 줄 때 `tradedAt` | (A) 기능 보류 / (B) 시장 종가 시각 고정 / (C) 해당 일자 00:00 KST | **A** (임의 시각 합성 금지) |
| D-6 | 매도 수수료·세금 | (A) 저장만 하고 계산 미반영 / (B) 실현손익 도메인 선행 도입 | **A**, B는 별도 과제 |
| D-7 | KR 시장·외화 | (A) US/USD만 열고 나머지 제외 보고 / (B) 통화 모델 먼저 도입 | **A**, B는 선행 과제 |
| D-8 | 승인 단위 | (A) 실행 배치 전체 / (B) 항목 선택 | **A** (부분 승인은 원장 일관성 위험) |
| D-9 | 제공자 정정 반영 | (A) 사용자 승인 필수 / (B) 자동 반영 | **A** |
| D-10 | 가져온 행의 삭제 경로 | (A) 전용 취소만 / (B) 일반 삭제도 허용 | **A** (개시 잔고와 동일) |
| D-11 | 비매매 이벤트(배당·입출금·대체) | (A) 건수만 보고 / (B) 별도 도메인 기록 / (C) 원장 반영 | **A**, B는 이후 단계 |
| D-12 | 자동/주기 동기화 | (A) 사용자 요청 시에만 / (B) 주기 실행 | **A** (자동 원장 변경 금지 원칙) |

## 13. 착수 전 확인 체크리스트

- [ ] 6장 U-1~U-15 공식 문서 확인 결과를 이 문서에 기록했다.
- [ ] 12장 D-1~D-12를 사용자가 확정했다.
- [ ] 개시 잔고 공존 정책(D-1)이 코드 검증 규칙으로 표현 가능하다.
- [ ] `external_transaction_id` 안정성이 확인됐다(U-6). 확인되지 않으면 착수하지 않는다.
- [ ] 통화·시장 범위가 프론트엔드 US 전용 제약과 일치한다.
- [ ] 릴리스 게이트(다중 사용자 운영 조건)와의 관계를 사용자가 인지했다.

## 14. 금지

- 이 문서 작성 과정에서 `src/**`, `frontend/**`, 마이그레이션, 설정, 테스트를 수정하지 않았다.
- 확인되지 않은 토스증권 엔드포인트·필드명·응답 스키마를 코드나 문서에 기록하지 않는다.
- 자격 증명, 액세스 토큰, 계좌번호 원문, 로컬 설정 값을 기록하지 않는다.
- 커밋·푸시·병합하지 않는다.

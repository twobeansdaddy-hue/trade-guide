# 증권사 보유 종목 명시적 가져오기 설계

> **문서 상태: 채택 및 구현 완료**. 이 문서는 증권사 보유 종목을 개시 잔고로 반영하는
> v1 정책과 구현 계약을 기록한다. 실제 코드와 동작이 달라질 경우 코드·테스트를 먼저
> 확인하고 이 문서를 함께 갱신한다.

## 1. 배경과 목표

현재 저장소는 증권사 보유 종목을 **읽기 전용으로 저장·비교**하는 데까지만 구현되어 있다.

- `PortfolioBrokerHoldingSnapshotService.refreshSnapshot`은 증권사 API에서 받은 보유
  종목을 `PortfolioBrokerHoldingSnapshot` / `PortfolioBrokerHoldingSnapshotItem`으로
  저장만 하고, `TradeTransaction`이나 파생 `Holding`은 절대 만들지 않는다
  (`src/main/java/com/tradeguide/service/broker/PortfolioBrokerHoldingSnapshotService.java:71-120`).
- `BrokerHoldingPreviewCalculator.compare`는 스냅샷의 보유 종목과
  `HoldingCalculator`가 매매 기록에서 계산한 Trade Guide 보유 종목을 종목(시장+티커) 단위로
  비교해 `MATCHED` / `QUANTITY_MISMATCH` / `ONLY_IN_BROKER` / `ONLY_IN_TRADE_GUIDE` 네 가지
  상태만 산출한다
  (`src/main/java/com/tradeguide/service/broker/BrokerHoldingPreviewCalculator.java`).
- `HoldingCalculator`는 오직 `TradeTransaction` 목록(매수/매도, 수량, 체결가, 수수료,
  체결 시각)만 입력으로 받아 평단가와 잔여 수량을 계산한다
  (`src/main/java/com/tradeguide/service/holding/HoldingCalculator.java`). 즉 Trade
  Guide 보유 종목을 바꾸는 유일한 경로는 `TradeTransaction`을 추가하는 것뿐이다.
- 프론트엔드 `BrokerHoldingSnapshotSection.tsx`는 이 비교 결과를 읽기 전용 목록으로만
  보여 주고, 어떤 행에도 액션 버튼이 없다.

사용자 입장에서 "증권사에만 있는 종목"은 대부분 Trade Guide 도입 이전에 이미 보유하고
있던 자산이다. 이를 계속 수동으로 매수 기록을 입력하게 하는 대신, **개시 잔고(opening
balance)로 명시적으로 승인해 매매 기록에 편입**할 수 있게 하는 것이 이 설계의 목표다.

### 왜 v1 범위를 `ONLY_IN_BROKER`로만 제한하는가

- `QUANTITY_MISMATCH`는 어느 쪽 수치가 맞는지 시스템이 판단할 근거가 없다. 배당 재투자,
  분할, 체결 시각 차이, 이미 일부 수동 입력된 기록 등 원인이 다양해 자동/반자동 처리는
  틀린 평단가를 만들 위험이 크다.
- `ONLY_IN_TRADE_GUIDE`는 반대로 증권사 쪽에서 사라진 자산이다. 매도됐는지, 계좌 이동인지,
  증권사 응답 자체의 누락인지 구분할 수 없어 임의로 매매 기록을 지우거나 매도로 처리하면
  안 된다.
- 따라서 v1은 **"Trade Guide에 전혀 없던 자산을 증권사 수치 그대로, 사용자가 한 건씩 명시적으로
  승인해 최초 1회만 개시 잔고로 만든다"**로 범위를 좁힌다. 나머지 두 상태는 계속 사람이
  검토하는 정보 화면으로만 남긴다.

## 2. v1 정책 요약

| 비교 상태 | v1 동작 |
| --- | --- |
| `ONLY_IN_BROKER` | 사용자가 종목별로 "가져오기"를 눌러 승인하면 증권사 수량·평단가 그대로 개시 잔고 매매 기록 1건을 생성한다. |
| `MATCHED` | 액션 없음. 이미 일치하므로 손댈 이유가 없다. |
| `QUANTITY_MISMATCH` | 액션 없음. 수동 검토 대상이라는 안내만 표시한다. |
| `ONLY_IN_TRADE_GUIDE` | 액션 없음. 수동 검토 대상이라는 안내만 표시한다. |

가져오기 승인은 **자산(종목) 단위 개별 승인**만 지원한다. 스냅샷 전체를 한 번에
일괄 승인하는 기능은 v1에 포함하지 않는다 — 한 건씩 확인시켜 사용자가 실제로 각 수량과
평단가를 본 뒤 승인하게 하는 것이 오승인 위험을 줄인다.

## 3. 데이터 모델

### 3.1 신규 엔티티: `PortfolioBrokerHoldingImport`

가져오기 승인 이력과 감사 정보를 남기는 전용 테이블. 스냅샷 항목은 재조회 때마다 새
레코드(`PortfolioBrokerHoldingSnapshotItem`은 매 `refreshSnapshot` 호출마다 새 PK로
저장됨)이므로, 승인 시점의 수치를 이 테이블에 별도로 보존해 스냅샷이 이후 삭제·정리되어도
감사 기록이 남게 한다.

```
PortfolioBrokerHoldingImport
- id                              (PK)
- portfolio_id                    (FK, not null)
- broker_connection_id            (FK, not null)  -- 어느 증권사 연결에서 왔는지
- source_snapshot_item_id         (FK -> broker_holding_snapshot_items.id, unique, not null)
- market                          (enum, not null)   -- 승인 시점 값 복사
- ticker                          (varchar, not null)
- display_name                    (varchar, not null)
- imported_quantity               (numeric(19,6), not null)   -- 승인 시점 스냅샷 수량 복사
- imported_average_purchase_price (numeric(19,4), not null)   -- 승인 시점 스냅샷 평단가 복사
- as_of_date                      (date, not null)  -- 스냅샷의 synced_at 날짜
- resulting_trade_transaction_id  (FK -> trade_transactions.id, unique, not null)
- approved_by_member_id           (FK -> members.id, not null)
- approved_at                     (timestamp, not null)
- created_at                      (timestamp, not null)
```

- `source_snapshot_item_id`에 유니크 제약을 걸어 **동일 스냅샷 항목에 대한 중복 승인
  요청을 DB 레벨에서 차단**한다(4장 멱등성 참고).
- `resulting_trade_transaction_id`도 유니크로 걸어 하나의 개시 잔고 매매 기록이
  한 승인 이력에만 대응하도록 고정한다.
- 수량·평단가·표시명을 복사 보관하는 이유: 스냅샷 항목은 향후 보존 기간 정책에 따라
  정리될 수 있는 읽기 전용 이력 데이터이므로, 감사 기록(무엇을, 언제, 누가 승인했는지)은
  스냅샷 생명주기와 독립적으로 유지되어야 한다.

### 3.2 `TradeTransaction` 최소 확장

`HoldingCalculator`가 오직 `TradeTransaction`만 읽으므로, 개시 잔고를 Trade Guide
보유 종목에 반영하는 가장 낮은 위험의 방법은 **일반 매매 기록과 동일한 엔티티를 재사용**하되
출처를 구분할 수 있는 필드를 추가하는 것이다.

```
TradeTransaction (기존 필드에 추가)
- source: enum { MANUAL, BROKER_OPENING_BALANCE }  (not null, 기본값 MANUAL)
```

- 기존 행은 모두 `MANUAL`로 채운다(마이그레이션에서 기본값 지정).
- 개시 잔고로 생성되는 행은 `source = BROKER_OPENING_BALANCE`, `tradeType = BUY`,
  `quantity`/`executedPrice` = 스냅샷 값 그대로, `fee = 0`, `tradedAt` = 스냅샷
  `syncedAt`(하루 단위로 스냅샷 날짜 00:00 등 정책 필요, 5장 참고)로 생성한다.
- `source` 필드는 매매 기록 목록·거래 내역 화면에서 "개시 잔고" 배지를 표시해 실제
  체결과 혼동되지 않게 하는 데만 쓴다. 손절/전략 로직, 수수료 합계, 세금 계산 등
  다른 계산에 `source`를 조건으로 넣지 않는다 — Holding 계산 로직 자체는 그대로
  두어 회귀 위험을 최소화한다.
- 대안으로 `TradeTransaction`을 건드리지 않고 완전히 별도의 "개시 잔고" 테이블을 만들고
  `HoldingCalculator`가 그 테이블도 함께 읽게 하는 방법도 있으나, 이는 보유 종목 계산의
  입력원이 두 곳으로 늘어나 테스트·회귀 표면이 커진다. 기존 계산기를 그대로 재사용할 수
  있는 이번 방식을 권장한다.

### 3.3 자산 카탈로그 제약 상속

`TradeTransactionService.createTradeTransaction`은 `assetListingService
.ensureActiveListingForTrade(market, ticker)`를 통과해야 매매 기록을 만들 수 있다
(`src/main/java/com/tradeguide/service/trade/TradeTransactionService.java:70-71`).
개시 잔고 생성도 동일한 검증을 거친다 — 증권사가 보고하는 티커가 Trade
Guide 활성 상장 카탈로그에 없으면(신규 상장, 상장폐지, 데이터 소스 차이 등) 가져오기를
실패시키고 원인을 사용자에게 보여 준다(6장 실패 케이스 참고). 이는 스냅샷 단계의
`unsupportedMarketCount`(지원하지 않는 *시장*)와는 다른, *종목* 단위의 별도 실패
사유다.

## 4. 멱등성 설계

"스냅샷 항목 키 기준 멱등성"은 다음 두 계층으로 구현한다.

1. **요청 재시도 멱등성 (동일 항목 재제출)**
   - 승인 API는 `snapshotItemId`를 요청 본문에 받는다.
   - `PortfolioBrokerHoldingImport.source_snapshot_item_id` 유니크 제약 덕분에,
     네트워크 재시도나 더블 클릭으로 동일한 `snapshotItemId`가 두 번 승인 요청되면
     두 번째 호출은 새 매매 기록을 만들지 않고 **첫 번째 승인 결과를 그대로 반환**한다
     (200 OK, 기존 `PortfolioBrokerHoldingImport` 레코드 기반 응답).
2. **자산 단위 이중 가져오기 방지 (다른 스냅샷의 같은 종목 재제출)**
   - 스냅샷은 갱신마다 새 `PortfolioBrokerHoldingSnapshotItem` PK를 만들기 때문에,
     같은 종목이 이전 스냅샷에서 이미 승인되었더라도 최신 스냅샷의 항목 ID는 다르다.
   - 이를 막기 위해 승인 처리 시점에 **비교 상태를 서버에서 다시 계산**한다. 이미 개시
     잔고 매매 기록이 존재하는 자산은 `HoldingCalculator` 결과에 반영되어 다음 비교부터
     더 이상 `ONLY_IN_BROKER`가 아니라 `MATCHED`(수량이 그대로라면) 또는
     `QUANTITY_MISMATCH`(그 사이 실제 매매가 있었다면)로 전환된다. 승인 API는 요청 시점의
     비교 상태가 `ONLY_IN_BROKER`가 아니면 거부하므로, 결과적으로 한 자산은 v1에서
     **최초 1회만** 개시 잔고로 편입될 수 있다.
   - 클라이언트가 보낸 캐시된 비교 상태를 신뢰하지 않고 **서버가 승인 처리 트랜잭션
     안에서 최신 스냅샷과 최신 매매 기록을 다시 비교**해 재검증하는 것이 핵심이다
     (TOCTOU 방지).

## 5. 감사 필드

| 필드 | 목적 |
| --- | --- |
| `source_snapshot_item_id` | 어떤 스냅샷 조회 결과에서 파생됐는지 추적, 멱등성 키 |
| `broker_connection_id` | 어느 증권사 연결에서 온 데이터인지 |
| `imported_quantity` / `imported_average_purchase_price` | 승인 당시 증권사가 보고한 값 그대로 보존(사후 스냅샷 삭제와 무관) |
| `as_of_date` | 개시 잔고의 기준일 — 이후 손익 계산 기준일 설명에 사용 |
| `resulting_trade_transaction_id` | 실제로 생성된 매매 기록과의 1:1 연결, 삭제 이력 추적 |
| `approved_by_member_id` / `approved_at` | 누가 언제 명시적으로 승인했는지 — 자동 생성이 아님을 증명 |
| `created_at` | 감사 레코드 생성 시각 |

이 테이블은 오직 조회·감사용이며, 어떤 서비스도 이 테이블을 직접 근거로 `Holding`을
재계산하지 않는다. `Holding` 계산은 여전히 `HoldingCalculator`가 `TradeTransaction`만
읽어 수행하고, `PortfolioBrokerHoldingImport`는 "왜 이 매매 기록이 존재하는가"를 설명하는
부가 이력이다.

## 6. API 계약

### 6.1 기존 비교 응답에 항목 ID 노출 필요

`BrokerHoldingPreviewItemResponse`는 **`snapshotItemId`**를 포함한다. 스냅샷 항목이
없는 `ONLY_IN_TRADE_GUIDE` 행에서는 이 값이 `null`이며, 나머지 비교 행은 저장된
스냅샷 항목 ID를 반환한다.

### 6.2 `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-imports`

요청:
```json
{ "snapshotItemId": 4821 }
```

응답 (201 Created):
```json
{
  "id": 17,
  "market": "US",
  "ticker": "AAPL",
  "displayName": "Apple Inc.",
  "importedQuantity": 12.000000,
  "importedAveragePurchasePrice": 172.5000,
  "asOfDate": "2026-09-05",
  "resultingTradeTransactionId": 903,
  "approvedAt": "2026-09-06T09:12:00Z"
}
```

- 동일 `snapshotItemId`로 재호출 시 새 레코드를 만들지 않고 **200 OK**로 기존
  레코드를 반환한다(생성이 아니므로 201 대신 200).
- 이 엔드포인트는 매매 기록 생성 엔드포인트가 아니라 "가져오기 승인" 전용 엔드포인트로
  분리한다 — 매매 등록 화면(`TradeTransactionEntryPage`)의 일반 수동 입력 경로와
  섞이지 않게 하기 위함이다.

### 6.3 `GET /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-imports`

- 포트폴리오의 가져오기 승인 이력을 최신순으로 반환한다(감사/이력 확인용, 읽기 전용).
- 응답 필드는 6.2의 단건 응답과 동일한 배열.

### 6.4 `DELETE /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-imports/{importId}`

 - 사용자가 승인한 개시 잔고를 취소하는 전용 API다.
 - `ACTIVE` 상태의 승인 이력만 취소할 수 있으며, 연결된 개시 잔고 원장 기록을 삭제한 뒤
   이력 상태를 `REVOKED`로 남긴다.
 - 감사 이력은 거래 원장을 가리키는 ID를 보존하므로, 나중에 취소 사실과 원래 승인 대상을
   함께 확인할 수 있다.

### 6.5 v1에서 제공하지 않는 것

- 일괄(스냅샷 전체) 승인 API.
- `QUANTITY_MISMATCH`/`ONLY_IN_TRADE_GUIDE`에 대한 어떤 쓰기 API도 제공하지 않는다.

## 7. 실패 케이스

| 상황 | 응답 | 비고 |
| --- | --- | --- |
| `snapshotItemId`가 존재하지 않거나 다른 포트폴리오 소유 | 404 | 전용 예외로 처리 |
| `snapshotItemId`가 최신 스냅샷이 아닌 과거 스냅샷 소속 | 409 | 과거 스냅샷 기준 승인은 허용하지 않는다 — 최신 스냅샷만 승인 대상 |
| 서버 재계산 결과 비교 상태가 `ONLY_IN_BROKER`가 아님(`MATCHED`/`QUANTITY_MISMATCH`/`ONLY_IN_TRADE_GUIDE`) | 409 | 클라이언트 캐시가 오래됐거나 그 사이 사용자가 수동 매매를 추가한 경우 |
| 이미 동일 `snapshotItemId`로 승인 완료 | 200 (기존 레코드 반환) | 4장 멱등성 |
| 티커가 활성 상장 카탈로그에 없음(`assetListingService.ensureActiveListingForTrade` 실패) | 422 | 증권사 데이터와 Trade Guide 카탈로그 간 불일치 — 수동 등록 요청으로 안내 |
| 포트폴리오에 연결된 증권사 계좌 없음 / 연결 해제됨 | 400 | 기존 `refreshSnapshot`의 링크 검증과 동일한 전제 |
| 회원 접근 권한 없음 | 403 | 기존 `MemberAccessService.requireMemberAccess` 재사용 |
| 동시성: 같은 항목에 대해 두 요청이 동시에 도착 | 하나는 201, 하나는 200 | DB 유니크 제약이 경쟁을 해소하고, 서비스는 유니크 제약 위반을 잡아 기존 레코드 조회로 폴백 |

## 8. UI 상태 (데스크톱/모바일)

기존 `BrokerHoldingSnapshotSection`을 확장하는 것을 전제로 한다. 별도 화면을 새로
만들지 않고, 비교 목록의 `ONLY_IN_BROKER` 행에만 액션을 추가한다.

### 8.1 공통 상태 전이

1. **기본(대상)** — `comparison === "ONLY_IN_BROKER"`인 행에만 "가져오기" 버튼 노출.
   나머지 세 상태는 지금처럼 텍스트 라벨만 표시하고 액션 없음.
2. **확인 단계** — 버튼 클릭 시 즉시 API를 호출하지 않고, 종목명·시장·티커·증권사 수량·
   증권사 평단가·기준일(스냅샷 저장 시각)·"이 작업은 개시 잔고로 매매 기록에 추가되며
   실제 매매 체결이 아닙니다"라는 고지문을 담은 확인 단계를 보여 준다.
3. **제출 중** — 확인 버튼 클릭 후 로딩 상태. 중복 클릭 방지를 위해 버튼 비활성화.
4. **성공** — 해당 행이 "가져옴" 배지로 바뀌고 버튼이 사라진다(비교 상태가 서버에서
   재계산되면 자연히 `MATCHED`/`QUANTITY_MISMATCH`로 바뀌므로, 목록을 새로고침해
   최신 비교로 갱신한다).
5. **실패** — 인라인 오류 메시지. 409(이미 다른 상태로 전환됨)는 "목록을 새로고침하세요"
   문구와 함께 자동 재조회 버튼을, 422(카탈로그 없음)는 "수동 매매 기록에서 등록해
   주세요" 안내를 구분해서 보여 준다.
6. **이력 조회** — 별도의 접이식 "가져오기 내역" 목록(6.3 API 사용)으로 과거 승인
   건을 종목·수량·평단가·기준일·승인 시각과 함께 읽기 전용으로 보여 준다.

### 8.2 데스크톱

- 기존 표 형태 비교 목록의 각 `ONLY_IN_BROKER` 행 오른쪽에 보조 버튼(`quiet-action`
  스타일 재사용)으로 "가져오기"를 배치한다.
- 확인 단계는 같은 화면 내 인라인 확장(행 아래 패널) 또는 모달 중 하나로 구현할 수
  있으나, 고지문과 두 개 수치(수량·평단가)를 명확히 보여줘야 하므로 모달을 권장한다.

### 8.3 모바일

- 기존 모바일 카드형 비교 목록(`HoldingSummaryList`/`HoldingValuationList`와 동일한
  카드 패턴)에서 "가져오기"를 카드 하단 전체 폭 버튼으로 배치한다.
- 확인 단계는 모달 대신 바텀시트로 구현해 좁은 화면에서 고지문 가독성을 확보한다.
- 이력 목록은 데스크톱과 동일한 정보를 세로 카드로 표시한다.

## 9. 테스트 케이스 제안

### 9.1 백엔드

- `ONLY_IN_BROKER` 항목 승인 시 `TradeTransaction`(source=`BROKER_OPENING_BALANCE`)과
  `PortfolioBrokerHoldingImport`가 함께 생성되고, 이후 `HoldingCalculator` 결과에
  수량·평단가가 정확히 반영되는 서비스 테스트.
- 동일 `snapshotItemId` 재요청 시 두 번째 호출이 새 매매 기록을 만들지 않고 기존
  레코드를 반환하는 멱등성 테스트.
- 서버 재계산 결과가 `MATCHED`/`QUANTITY_MISMATCH`/`ONLY_IN_TRADE_GUIDE`인 항목에
  대한 승인 요청이 409로 거부되는 테스트(각 상태별 3케이스).
- 과거(최신이 아닌) 스냅샷의 항목 ID로 승인 요청 시 409 테스트.
- 활성 상장 카탈로그에 없는 티커에 대한 승인 요청이 422로 거부되는 테스트.
- 다른 회원/다른 포트폴리오의 `snapshotItemId`로 승인 요청 시 403/404 테스트.
- 동시 요청 경쟁 상태에서 유니크 제약이 중복 생성을 막는 리포지토리/통합 테스트.
- 가져오기 이력 조회 API가 승인 순서와 무관하게 최신순 정렬로 반환하는 테스트.
- 개시 잔고 매매 기록을 삭제한 뒤 `HoldingCalculator` 결과와 이력 화면이 일관되게
  갱신되는 회귀 테스트(기존 `deleteTradeTransaction` 경로 재사용 확인).

### 9.2 프론트엔드

- `ONLY_IN_BROKER` 행에만 가져오기 버튼이 노출되고 나머지 세 상태에는 노출되지
  않는 렌더링 테스트.
- 확인 단계에서 고지문과 수량·평단가·기준일이 정확히 표시되는지 확인하는 테스트.
- 성공 후 목록이 갱신되어 해당 행이 더 이상 가져오기 대상이 아님을 보여 주는지
  확인하는 테스트.
- 409/422 실패 응답에 따라 서로 다른 안내 문구가 표시되는지 확인하는 테스트.
- 데스크톱 모달과 모바일 바텀시트 각각에서 확인 단계 진입·취소·제출 흐름이 동작하는
  수동/시각 회귀 체크리스트(Antigravity 검증 대상으로 위임 가능).

## 10. 이 설계가 다루지 않는 것 (명시적 비범위)

- 자동 승인, 정기 배치 가져오기, 스냅샷 갱신 시 자동 개시 잔고 생성 — 모두 "명시적
  가져오기"라는 전제에 위배되므로 v1과 이 문서 모두에서 제외한다.
- `QUANTITY_MISMATCH`/`ONLY_IN_TRADE_GUIDE`를 자동으로 정정하는 어떤 기능도
  제안하지 않는다. 이 두 상태는 계속 "사람이 봐야 하는 정보"로만 남는다.
- 개시 잔고의 취득일을 실제 매수일로 역산하는 기능(세금/보유기간 계산 등)은 다루지
  않는다 — `as_of_date`는 스냅샷 조회일일 뿐 실제 취득일이 아님을 UI 고지문에 명시해야
  한다.

이 문서의 모든 스키마·API·필드명은 사용자 채택과 별도의 구현 작업 계약을 거치기 전까지
확정된 것이 아니다.

# 증권사 연동 백엔드·제품 구조 검토와 다음 세로 슬라이스 제안

- 작성 주체: Claude (BE/product architecture review)
- 작업 모드: 검토·설계 문서 작성. `src/**`, `frontend/**`는 수정하지 않았다.
- 근거 범위: 이 저장소의 현재 코드, 마이그레이션, 문서만 사용했다. 외부 증권사 API를 호출하지
  않았고, 자격 증명·토큰·키 값은 이 문서 어디에도 담지 않는다.
- 기준 문서: `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`, `docs/ARCHITECTURE_ROADMAP.md`,
  `docs/agent-tasks/broker-*.md`

## 0. 요약

이미 구현된 범위는 문서가 적어 둔 "구현 순서 1~6단계"를 사실상 6단계까지 밀고 나갔다. 연결
검증, 계좌 목록, 보유 종목 스냅샷 저장, 스냅샷 비교, 개시 잔고 단건·일괄 반영, 주문 이력
스테이징·대조·승인·취소, 서버 페이징까지 동작한다. 원장을 건드리는 경로는 전부 명시적 승인
뒤에만 열리고, 멱등성은 애플리케이션 조건문이 아니라 DB 제약(부분 유니크 인덱스)이 담당한다.
이 부분의 설계 수준은 유지할 가치가 있다.

남은 문제는 "기능이 없다"가 아니라 **일반화가 한 겹 모자라다**는 것이다. 세 가지로 좁힌다.

1. **자격 증명 모델이 토스 모양으로 굳어 있다.** `BrokerCredentialField`로 입력 폼은 동적으로
   선언하지만, 요청 DTO(`clientId`/`clientSecret`), 저장 테이블(`broker_connection_secrets`의
   고정 두 컬럼), 어댑터 시그니처(`(clientId, clientSecret, accountSequence)`)는 전부 2필드
   고정이다. 두 번째 증권사는 필드 수가 다른 순간 이 셋을 모두 뚫어야 한다.
2. **기능 게이트가 경로마다 다르다.** 주문 이력만 "어댑터 등록 + capability 선언" 이중 관문을
   지나고, 보유 종목·연결 검증은 어댑터 등록만 본다. `supportedMarkets`는 일괄 개시 잔고에서만
   쓰이고 단건 승인 경로에는 없다. 그 틈에서 **KR 종목이 통화 개념 없는 원장에 들어간다**(3장 D1).
3. **정합성 점검이 가져오기 실행에 종속돼 있다.** 대조(reconciliation)는 주문 이력 실행을 만들
   때만 계산되고 따로 요청할 수 없다. 사용자가 "지금 내 원장이 증권사와 맞나"를 묻는 경로가
   없다.

권고하는 다음 슬라이스는 1번과 2번을 한 번에 닫는 **제공자 자격 증명·기능 게이트 일반화(v2)**
하나다(6장). 3번은 그다음 슬라이스로 미룬다(7장).

---

## 1. 현재 구현 지도 (확정 사실)

각 줄은 저장소에서 직접 확인한 사실이다. 파일 경로를 함께 적는다.

### 1.1 제공자 카탈로그와 기능 선언

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 제공자 카탈로그 enum | 구현 | `domain/broker/BrokerProvider.java` (현재 `TOSS_SECURITIES` 1종) |
| 기능 단위 enum | 구현 | `domain/broker/BrokerProviderCapability.java` (4종) |
| 자격 증명 입력 명세 | 구현 | `domain/broker/BrokerCredentialField.java`, `BrokerCredentialFieldType` |
| 안전한 카탈로그 API | 구현 | `GET /api/broker-providers` → `BrokerProviderCapabilityResponse` |
| 어댑터 레지스트리 | 구현 | `service/broker/BrokerProviderRegistry.java` |
| 어댑터 계약 | 구현 | `BrokerConnectionVerifier`, `BrokerHoldingsProvider`, `BrokerOrderHistoryProvider` |
| 토스 어댑터 | 구현 | `TossSecuritiesConnectionVerifier`, `TossSecuritiesHoldingsProvider`, `TossSecuritiesOrderHistoryProvider`, `TossSecuritiesAccessTokenIssuer` |

`TOSS_SECURITIES`가 선언한 capability는 `CONNECTION_VERIFICATION`, `HOLDING_SNAPSHOT`,
`TRANSACTION_HISTORY_IMPORT` 셋이다. `CASH_BALANCE`는 enum에만 있고 선언·구현이 없다.

### 1.2 연결과 계좌

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 연결 생성·조회·삭제·검증 | 구현 | `BrokerConnectionController`, `BrokerConnectionService` |
| AES-256-GCM 자격 증명 암호화 | 구현 | `AesGcmBrokerCredentialCipher` (키 미설정 시 503) |
| 계좌 재검증과 `DETACHED` 수명주기 | 구현 | `BrokerConnection.reconcileVerifiedAccounts`, `BrokerAccountStatus` |
| 활성 개시 잔고가 남은 연결의 삭제 차단 | 구현 | `BrokerConnectionService.deleteBrokerConnection` |
| 포트폴리오↔계좌 링크 | 구현(포트폴리오당 1건, 서비스 제약) | `PortfolioBrokerLinkService.linkBrokerAccount` |

### 1.3 보유 종목

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 실시간 미리보기 | 구현 | `POST .../broker-sync-preview` → `BrokerHoldingPreviewService` |
| 스냅샷 저장·최신 조회 | 구현 | `POST/GET .../broker-holding-snapshots[/latest]` |
| 저장 스냅샷 비교 | 구현 | `GET .../broker-holding-snapshots/latest/comparison` |
| 개시 잔고 단건 승인·취소 | 구현 | `PortfolioBrokerHoldingImportWriter`, `PortfolioBrokerHoldingImportService` |
| 개시 잔고 일괄 승인 | 구현 | `PortfolioBrokerOpeningBalanceBatchWriter` (사유별 제외 보고) |
| 승인 이력 페이징 | 구현 | `GET .../broker-holding-imports?page&size` |

### 1.4 주문 이력

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 실행 단위 스테이징(원장 무변경) | 구현 | `BrokerOrderImportService.createPreview`, `BrokerOrderImportRunWriter.stage` |
| 커서 순회·상한·반복 커서 감지 | 구현 | `MAX_PAGES=50`, `CURSOR_REPEATED`, `PAGE_LIMIT_EXCEEDED` |
| 경계일 패딩(±2일)과 주문 식별자 중복 제거 | 구현 | `BOUNDARY_PADDING_DAYS` |
| 판정 분류 12종 + 신호 3종 | 구현 | `BrokerOrderStagingClassifier` |
| 내용 지문 교차 검증 | 구현 | `BrokerOrderFingerprint` (키가 아니라 검증값) |
| 보유 수량 재생 대조 | 구현 | `BrokerOrderReconciler` (`MATCHED`/`MISMATCHED`/`REPLAY_FAILED`/`NOT_AVAILABLE`) |
| 실패 실행 별도 커밋 | 구현 | `recordFailure` + `REQUIRES_NEW` |
| 승인·취소, 개시 잔고 기준점 | 구현 | `BrokerOrderImportApprovalWriter`(승인 직전 전체 재생 검증 포함) |
| 반영 멱등성 | 구현 | 부분 유니크 인덱스 `uk_broker_order_ledger_links_active_order` |
| 실행 목록·항목 서버 페이징과 필터 | 구현 | `BrokerHistoryPageRequest`(MAX 100), `BrokerOrderImportItemFilter`, V15/V16 인덱스 |

### 1.5 스키마

Flyway V1~V16. 증권사 관련은 V6~V16이며, 정렬 동점 기준(id)까지 인덱스에 반영돼 있다.
`spring.jpa.hibernate.ddl-auto`는 postgres 프로필에서 `validate`이고, Testcontainers 기반
`postgresIntegrationTest`가 마이그레이션과 엔티티 매핑을 대조한다.

---

## 2. 갭 분석

### A. 제공자 능력 모델 — "토스 전용"이 남아 있는 지점

| # | 갭 | 현재 | 왜 문제인가 |
| --- | --- | --- | --- |
| A1 | 연결 생성 요청이 2필드 고정 | `BrokerConnectionCreateRequest.clientId/clientSecret` | `BrokerProvider.getCredentialFields()`가 동적 명세를 내려보내는데 요청 본문은 고정이다. 3필드 제공자(예: app key + app secret + 계좌 비밀번호)는 DTO를 고쳐야만 들어온다. 카탈로그 API의 존재 이유가 반쯤 무효화된다. |
| A2 | 비밀 저장 테이블이 2필드 고정 | `broker_connection_secrets`의 `encrypted_client_id`/`encrypted_client_secret` | 제공자마다 컬럼을 추가하면 스키마가 제공자 수만큼 넓어지고, 안 쓰는 컬럼이 `NOT NULL`이라 새 제공자마다 마이그레이션이 필요하다. |
| A3 | 어댑터 시그니처가 위치 인자 3개 | `fetchHoldings(clientId, clientSecret, accountSequence)` | 필드 수가 다른 제공자를 넣으면 인터페이스 전체가 흔들린다. 같은 타입(String) 3개라 순서를 바꿔 껴도 컴파일이 통과한다. |
| A4 | 기능 게이트가 경로마다 다르다 | 주문 이력만 이중 관문(`isOrderHistoryImportable`), 보유 종목·검증은 어댑터 존재만 확인 | "빈이 등록됐다는 사실만으로 기능이 열리지 않는다"는 레지스트리의 원칙이 한 경로에만 적용돼 있다. |
| A5 | `supportedMarkets` 게이트가 한 곳에만 있다 | 일괄 개시 잔고에만 `UNSUPPORTED_MARKET` 판정 | 단건 개시 잔고 승인에는 시장 검사가 없다. 3장 D1의 직접 원인이다. |
| A6 | 카탈로그 응답이 "선언"만 노출 | `supportedCapabilities`는 enum 선언값 그대로 | 어댑터가 없어 실제로는 닫혀 있는 기능도 화면에는 지원으로 보인다. `connectable` 한 값만 실제 가용성을 반영한다. |
| A7 | `CASH_BALANCE` 미구현, javadoc 노후 | `BrokerProviderCapability.TRANSACTION_HISTORY_IMPORT` 주석이 "아직 어떤 제공자도 지원하지 않는다" | 토스가 이미 선언했다. 주석이 사실과 어긋난다(3장 D4). |

### B. 안전한 계좌/보유/주문 이력 가져오기

| # | 갭 | 비고 |
| --- | --- | --- |
| B1 | ~~외부 호출이 트랜잭션 안에서 일어나는 경로가 남아 있다~~ **(해소, S4)** | 보유 종목 경로도 `BrokerHoldingContextLoader` → 증권사 호출 → `PortfolioBrokerHoldingSnapshotWriter`로 분리해 주문 이력 경로와 같은 경계를 갖는다(3장 D2). |
| B2 | ~~미리보기·스냅샷 생성이 비멱등이고 호출 제한이 없다~~ **(해소, S6a)** | `POST broker-order-imports`, `POST broker-sync-preview`, `POST broker-holding-snapshots` 세 엔드포인트 모두 같은 포트폴리오·같은 동작의 동시 호출은 409(`BrokerDuplicateCallGuard`), 5초 이내 재호출은 429로 막는다(3장 D7). 각 호출 자체가 멱등해진 것은 아니다 — 쿨다운을 통과하면 오늘과 동일하게 새 실행/스냅샷을 만든다(의도된 동작). |
| B3 | ~~조회 상한에 걸린 실행을 이어받을 수 없다~~ **(해소, S6b)** | `fetchOrders`가 30일 창 단위로 순회하며 `MAX_PAGES=50` 예산을 창 전체가 공유한다. 창을 다 못 끝내면 그 창을 버리고 `coveredOrderedTo`/`nextOrderedFrom`으로 어디부터 다시 요청할지 응답에 노출한다(`run.coverage`). 대조가 `MATCHED`가 아닌 부분 커버 실행은 `acknowledgeIncompleteCoverage=true` 명시 확인 없이는 409(`ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED`)로 승인을 막는다. 확인 시각·주체는 실행 단위로 영속화한다(`covered_ordered_to`/`coverage_acknowledged_at`/`coverage_acknowledged_by_member_id`, V20). Antigravity UX 검토(경고 문구)는 별도 트랙으로 남는다. |
| B4 | ~~`MANUAL_OVERLAP_SUSPECTED` / `DUPLICATE_SUSPECTED`가 종착점이다~~ **(해소, S8)** | 스테이징 항목은 그대로 두고 별도 감사 표(`broker_order_import_item_overrides`, V24)에 `ALLOW_LEDGER_WRITE`/`KEEP_EXCLUDED` 재판정을 작성자·시각·사유·원래/결과 상태와 함께 남긴다. 승인은 유효 상태가 `STAGED`인 항목만 기존 검증(재생·기준 시각·커버리지·멱등)에 태운다. 계약은 `docs/agent-tasks/broker-order-import-suspected-item-override.md`. |
| B5 | 스냅샷 보존 정책이 없다 | `broker_holding_snapshots`는 갱신할 때마다 쌓이고 정리 규칙이 없다. 개시 잔고 이력이 항목을 참조하므로 단순 삭제도 불가능하다. |
| B6 | 진행 중 주문 조회가 항상 1회 추가 호출 | `countOpenOrders`가 실행마다 무조건 호출된다. 건수 표시 용도이므로 옵션화 여지가 있다. |
| B7 | 토큰 캐시가 무한 증가 | `TossSecuritiesAccessTokenIssuer.cachedTokens`는 만료 항목을 제거하지 않는다. 단일 사용자 베타에서는 무해하나 다중 사용자에서는 누수다. |

### C. 사용자 주도 원장 정합성 점검

| # | 갭 | 비고 |
| --- | --- | --- |
| C1 | 독립 실행 경로가 없다 | 재생 대조는 `BrokerOrderImportRunWriter.stage` 안에서만 계산된다. "원장과 증권사가 지금 맞는지"만 묻고 싶어도 주문 이력 실행을 새로 만들어야 하고, 그러면 증권사를 또 호출한다. |
| C2 | 스냅샷 비교와 재생 대조가 서로 다른 답을 낸다 | `GET .../latest/comparison`은 **현재 보유 수량** 비교(`ONLY_IN_BROKER` 등)이고, `BrokerOrderReconciler`는 **스테이징 반영 가정 수량** 비교다. 둘 다 "차이"를 말하지만 기준이 다르고, 화면에서 어느 쪽을 신뢰해야 하는지 계약이 없다. |
| C3 | 차이의 사유 분류가 없다 | 차이가 나와도 "부족/초과" 수량만 있고, 미승인 실행이 있어서인지, 조회 기간 밖 이력인지, 미체결분인지 구분하지 않는다. 사용자가 다음에 할 행동이 정해지지 않는다. |
| C4 | 점검 결과가 남지 않는다 | 대조 줄은 주문 이력 실행에 종속돼 저장된다. 독립 점검 이력이 없으니 "언제 맞았고 언제부터 틀어졌는지"를 추적할 수 없다. |

### D. 페이지네이션

| # | 갭 | 비고 |
| --- | --- | --- |
| P1 | ~~실행 상세가 여전히 전 항목을 담는다~~ **(해소, S2)** | 상세·미리보기 생성 응답에서 `items`를 제거하고 승인 판정 집계(`approval`)로 대체했다. 항목의 정본은 `GET .../{runId}/items` 페이징이다(3장 D3). |
| P2 | offset 페이징 | `PageRequest.of(page, size)` 기반이라 깊은 페이지에서 비용이 커진다. 현재 데이터 규모에서는 문제 없고, 커서 전환은 필요해질 때 하면 된다. |
| P3 | 페이징이 없는 목록이 남아 있다 | 연결 목록, 링크 목록, 링크 후보, 스냅샷 항목, 비교 항목은 전량 반환이다. 앞의 셋은 자연 상한이 작아 문제되지 않는다. 스냅샷/비교 항목은 보유 종목 수에 비례하므로 수백 종목 계좌에서만 문제가 된다. |
| P4 | 총 건수 계산 비용 | `BrokerHistoryPage`가 `totalElements`를 항상 계산한다(count 쿼리 1회 추가). 화면이 총 건수를 쓰지 않는다면 줄일 수 있다. |

### E. 검증과 오류 동작

| # | 갭 | 비고 |
| --- | --- | --- |
| E1 | `IllegalArgumentException`이 전부 400 | `GlobalExceptionHandler`가 일괄 매핑한다. "포트폴리오를 찾을 수 없습니다"(404가 맞다), "포트폴리오에 연결된 증권사 계좌가 없습니다"(409/412 성격), "증권사 연결을 다시 검증해야 합니다"(409) 가 모두 400 + 메시지로 나간다. 프론트엔드는 문구 문자열로 분기할 수밖에 없다. |
| E2 | `ApiErrorCode`가 증권사 쪽에 하나뿐 | `BROKER_CONNECTION_UNAVAILABLE`만 있다. 링크 없음, 재검증 필요, 대조 불일치, 기준 시각 제외, 스냅샷 없음은 코드가 없다. |
| E3 | 승인 409의 사유가 문자열에만 있다 | `BrokerOrderApprovalConflictException`이 대조 불일치·기준 시각 제외·재생 실패를 모두 409 + 서로 다른 문구로 낸다. 화면이 상황별 안내를 나누려면 문구를 파싱해야 한다. |
| E4 | ~~조회 기간 상한이 없다~~ **(해소, S6a)** | `createPreview`는 이제 양 끝 날짜를 포함해 최대 366일까지만 받는다(`IllegalArgumentException`, 400). 연결·계좌 조회나 증권사 호출 전에 거부하므로 10년 구간을 요청해도 증권사를 한 번도 호출하지 않는다. |
| E5 | 실패 코드 노출이 부분적 | `failure_code`는 저장하지만 응답 계약에 명시된 값 목록이 문서에 없다. |

---

## 3. 발견한 결함

> `src/**`는 수정하지 않았다. D1은 수정 승인을 요청한다.

### D1. (심각) KR 종목이 통화 개념 없는 원장에 개시 잔고로 들어갈 수 있다

**사실 연결:**

1. `TossSecuritiesHoldingsProvider.toMarket`은 `marketCountry`가 `KR`이면 `Market.KR`을 돌려준다.
   KR 보유 종목은 스냅샷에 그대로 저장된다.
2. `BrokerProvider.TOSS_SECURITIES.getSupportedMarkets()`는 `{US, KR}`이다.
3. `PortfolioBrokerHoldingImportWriter.createOpeningBalanceImport`(단건 승인)에는 **시장 검사가
   없다.** `ONLY_IN_BROKER` 여부와 상장 활성 여부만 본다.
4. `AssetListingService.ensureActiveListingFromBrokerSnapshot`은 시장을 제한하지 않고 `KR` 상장을
   `ACTIVE`로 새로 만든다.
5. `TradeTransaction`에는 **통화 필드가 없다**(`market`, `ticker`, `quantity`, `executedPrice`,
   `fee`, `tradedAt`, `source`).
6. `PortfolioValuationService`는 보유 종목마다 `marketPriceProvider.getCurrentPrice(market, ticker)`를
   호출하고, 하나라도 실패하면 예외가 포트폴리오 평가 전체를 막는다.

**결과:** 토스 계좌에 KR 종목이 하나라도 있으면 사용자가 그것을 개시 잔고로 승인할 수 있고,
그 순간 원화 표시 금액이 달러 원장에 섞인다. 이어서 포트폴리오 평가는 KR 티커의 현재가 조회
실패로 **화면 전체가 오류**가 된다. 되돌리려면 개시 잔고 취소 API를 찾아 실행해야 한다.

일괄 반영 경로(`PortfolioBrokerOpeningBalanceBatchWriter`)는 `supportedMarkets`를 보지만
`{US, KR}`이므로 KR을 막지 못한다. 즉 두 경로 모두 뚫려 있다.

**권고:** 6장 슬라이스의 `requireLedgerWritableMarket` 게이트로 닫는다. 원장이 다루는 시장은
현재 US 하나이므로, `supportedMarkets`(제공자가 조회할 수 있는 시장)와 **원장에 쓸 수 있는
시장**을 별도 개념으로 분리해야 한다. 지금 둘이 같은 값이라 이 구멍이 생겼다.

### D2. 보유 종목 경로가 트랜잭션 안에서 증권사를 호출한다 — **해소 (S4 반영)**

**당시 문제.** `PortfolioBrokerHoldingSnapshotService.refreshSnapshot`은 `@Transactional` 안에서
`holdingsProvider.fetchHoldings(...)`를 호출했다. `BrokerHoldingPreviewService.getHoldingPreview`도
읽기 트랜잭션 안에서 같은 일을 했다. 주문 이력 경로는 정확히 이 이유로 조회와 저장의 트랜잭션을
분리해 뒀다(`BrokerOrderImportService` javadoc). 증권사가 느려지면 DB 커넥션 풀이 먼저 마른다.

**반영한 조치.** 권고한 그대로 `...ContextLoader` → 호출 → `...Writer` 패턴으로 맞췄다.

- `BrokerHoldingContextLoader`(`@Transactional(readOnly = true)`)가 포트폴리오 소유권, 링크,
  연결 상태, 기능 관문, 복호화된 자격 증명을 **한 읽기 트랜잭션에서** 모두 확정한다.
  기능 관문은 복호화보다 앞에 둬서, 아직 열리지 않은 기능 때문에 평문이 만들어지지 않게 한다.
- 스냅샷 새로고침과 미리보기 두 서비스 메서드에서 `@Transactional`을 걷어냈다. 증권사 호출은
  이제 어떤 트랜잭션에도 속하지 않는다.
- `PortfolioBrokerHoldingSnapshotWriter`(`@Transactional`)가 **조회가 성공한 뒤에만** 열리는
  유일한 쓰기 지점이다. 조회가 실패하면 쓰기 트랜잭션은 시작조차 하지 않으므로 부분 영속화가
  생길 수 없고, 항목 저장이 실패하면 스냅샷 헤더까지 함께 롤백된다.
- 조회와 저장 사이가 벌어지면서 생긴 새 경계 조건(그 사이에 링크가 다른 계좌로 바뀌는 경우)은
  라이터가 링크 대상과 연결 상태를 다시 확인해 400으로 거부한다. 기존 상태 코드 집합 안이며
  API 계약과 프론트엔드는 그대로다.

주문 이력 경로와 달리 실패 기록(`REQUIRES_NEW`)은 두지 않았다. 스냅샷은 "증권사가 이렇게
보고했다"는 사실 한 장이라 조회가 실패하면 남길 사실이 없다.

**회귀 방어.** `PostgresBrokerHoldingSnapshotTransactionIntegrationTest`가 실제 PostgreSQL에서
컨텍스트 초기화, 단일 커밋, 조회 실패 시 무기록, 항목 제약 위반 시 헤더 롤백, 링크 변경 거부를
확인한다. 로더의 `@Transactional`을 지우면 이 테스트가 `LazyInitializationException`으로 깨진다.

### D3. 실행 상세 응답이 항목 전체를 담는다 (S2로 처리 완료)

`BrokerOrderImportRunDetailResponse`는 `items` 전량을 직렬화했다. `POST /broker-order-imports`
응답도 같은 DTO다. 실행 하나에 수천 건이 담길 수 있다는 것은 코드 주석이 스스로 밝히고 있고,
그 때문에 `/{runId}/items` 페이징을 따로 열었다. 상세에서 항목을 빼려면 승인 가능 여부 판정에
쓰이는 **집계값(반영 후보 수, 기준 시각 이전 제외 수, 승인 가능 여부)을 서버가 계산해 요약에
넣는 것**이 선행 조건이었다.

**(이후 갱신) S2에서 닫았다.** 상세 응답은 이제
`{ run, approval, reconciliation }`이고 `items`는 없다. `approval`은
`BrokerOrderImportApprovalAssessment`로, `stagedCount`·`eligibleCount`·`baselineExcludedCount`·
`alreadyLinkedCount`·`writableCount`·`excludedCount`·`suspectedCount`·`amountMismatchCount`·
`baselineAt`·`approvable`·`blocker`를 담는다. 판정 규칙은
`BrokerOrderImportApprovalWriter.approve`의 거부 조건과 같은 순서로 계산하며, 건수는 항목을
메모리로 끌어오지 않고 두 개의 count 질의로 센다(`countEligibleItems`,
`countEligibleItemsAlreadyLinked`). 항목의 정본은 `GET .../{runId}/items` 페이징이다.
대조 결과는 보유 종목 수에 비례해 상한이 있으므로 계속 상세에 담는다.

`approvable`은 조회 시점 판정이지 승인 보장이 아니다. 승인 시점에 서버가 같은 검사를 다시 한다.

### D4. `BrokerProviderCapability.TRANSACTION_HISTORY_IMPORT` javadoc이 사실과 다르다

"아직 어떤 제공자도 지원하지 않는다"고 적혀 있으나 `TOSS_SECURITIES`가 이미 선언했다.
`CASH_BALANCE`의 같은 문구는 여전히 맞다. 주석만 고치면 되는 사안이라 슬라이스에 포함한다.

### D5. 도메인 오류가 전부 400으로 평탄화된다 (E1/E2/E3와 같은 뿌리) — **해소 (S3 반영, 증권사 경로 한정)**

**반영한 조치.** 증권사 연동 경로(`service/broker/**`, `controller/broker/**`)에서 포트폴리오
부재, 포트폴리오-증권사 링크 부재, 재검증 필요 상태를 `IllegalArgumentException`(400)이 아니라
전용 예외로 분리했다.

- `PortfolioNotFoundException` → 404, `ApiErrorCode.PORTFOLIO_NOT_FOUND`.
- `PortfolioBrokerLinkNotFoundException` → 404, `ApiErrorCode.PORTFOLIO_BROKER_LINK_NOT_FOUND`.
- `BrokerConnectionReverificationRequiredException` → 409,
  `ApiErrorCode.BROKER_CONNECTION_REVERIFICATION_REQUIRED`.
- `BrokerOrderApprovalConflictException`에 선택적 `ApiErrorCode`를 추가해 대조 불일치
  (`RECONCILIATION_MISMATCH`), 기준 시각 제외(`BASELINE_EXCLUDED`), 재생 검증 실패
  (`REPLAY_VALIDATION_FAILED`)를 문구가 아니라 코드로 구분할 수 있게 했다. HTTP 상태는 409로
  그대로 두었다.

D2가 문서로 명시한 "조회 시점과 저장 시점 사이 링크 변경" 400 계약(`PortfolioBrokerHoldingSnapshotWriter`,
`BrokerOrderImportRunWriter`의 링크 변경 감지)은 의도적으로 유지했다. 그 판정은 리소스 부재나
상태 충돌이 아니라 요청 자체가 무효화된 경쟁 상태이므로 기존 400 계약을 바꾸지 않는다.

비-증권사 경로(`PortfolioService`, `TradeTransactionService`, `HoldingService` 등)의 같은 모양
평탄화는 이번 슬라이스 범위 밖이다. 성공 응답 계약과 프론트엔드는 변경하지 않았다(프론트엔드는
상태 코드가 아니라 오류 메시지 문자열로 표시하므로 영향이 없음을 확인했다).

### D6. 액세스 토큰 캐시가 만료 항목을 제거하지 않는다

### D7. 미리보기·스냅샷 생성이 비멱등이고 호출 제한이 없다 — **해소 (S6a 반영)**

> D2~D7은 이번 슬라이스 범위 밖으로 두고 7장 순서에 배치한다. D1만 즉시 처리를 권고한다.
>
> (이후 갱신) D1과 D2는 처리했다. D2는 S4로 별도 작업에서 닫았다. D7(호출 제한)은 S6a로
> 닫았다 — 세 엔드포인트 모두 동시 호출 409, 5초 쿨다운 429를 적용했다. 비멱등 자체는
> 의도된 설계이므로 유지했다(§6.5 판단과 동일).

---

## 4. 설계 원칙 (다음 슬라이스들에 공통 적용)

1. **조회 가능성과 원장 반영 가능성을 분리한다.** 제공자가 돌려줄 수 있는 시장/상품과, Trade
   Guide 원장이 정확히 표현할 수 있는 시장/상품은 다른 집합이다. 지금은 같은 값을 쓰다가 D1이
   생겼다.
2. **관문은 한 곳에 모으고 모든 경로가 그것을 지난다.** `BrokerProviderRegistry`가 이미 그
   역할을 하고 있으므로 시장·기능 검사를 모두 여기로 모은다.
3. **비밀은 키가 아니라 값으로 다룬다.** 필드 이름이 스키마가 되면 제공자마다 스키마가 늘어난다.
   필드 키를 데이터로 저장하고, 유효성은 `BrokerProvider`의 명세로 검증한다.
4. **판정과 실패를 섞지 않는다.** 이미 일괄 개시 잔고가 지키는 원칙이다. 반영할 수 없다는
   *판정*은 사유가 붙은 제외로, 시스템 *실패*는 트랜잭션 롤백으로 다룬다.
5. **원장에 쓰는 경로는 승인·멱등키·감사 링크 셋을 항상 함께 갖는다.** 현재 개시 잔고와 주문
   이력이 모두 이 형태다. 새 경로도 예외를 두지 않는다.

---

## 5. 이번에 하지 않을 것 (명시적 비목표)

- 주문 전송, 예약 주문, 자동 매매: 영구 제외.
- 자동 동기화, 스케줄 배치, 백그라운드 잡: 모든 외부 호출은 사용자 조작으로만 시작한다.
- 다중 통화 원장 도입: D1은 KR을 **차단**해서 닫는다. 통화 모델 도입은 별도 대형 설계다.
- 포트폴리오당 다중 브로커 링크 활성화: DB는 이미 0..n을 허용하지만 서비스 1건 제약을 유지한다.
- 자격 증명 키 로테이션 실행: 스키마에 `key_version`을 유지하되 로테이션 절차 구현은 후속.
- 프론트엔드 변경: 이번 슬라이스의 API는 **기존 요청 형태와 호환**되게 설계한다(6.3).

---

## 6. 권고하는 다음 슬라이스 — 제공자 자격 증명·기능 게이트 일반화 (v2)

### 6.1 목표 한 문장

두 번째 증권사를 추가할 때 **DTO·스키마·어댑터 인터페이스를 고치지 않아도 되게** 하고, 같은
작업으로 원장 반영 시장 게이트를 한 곳에 모아 D1을 닫는다.

### 6.2 범위

포함:

1. 다중 필드 자격 증명 저장 모델(`broker_connection_secret_values`)과 기존 값 백필.
2. `BrokerCredentials` 값 객체 도입, 어댑터 3종 인터페이스 시그니처 변경.
3. 연결 생성 요청의 동적 필드 수용(레거시 본문 호환 유지).
4. `BrokerProviderRegistry`에 기능 게이트 일원화(`requireCapability`)와 원장 반영 시장 게이트
   (`requireLedgerWritableMarket`) 추가, 원장 쓰기 경로 전부에 적용.
5. 카탈로그 응답에 실제 가용 기능(`availableCapabilities`) 추가.
6. D4 javadoc 정정.

제외: D2, D3, D5, D6, D7, C계열 전부.

### 6.3 API 계약

#### (1) `GET /api/broker-providers` — 필드 추가(하위 호환)

```jsonc
[
  {
    "provider": "TOSS_SECURITIES",
    "displayName": "토스증권",
    "connectable": true,
    "supportedCapabilities": ["CONNECTION_VERIFICATION", "HOLDING_SNAPSHOT", "TRANSACTION_HISTORY_IMPORT"],
    // 추가: 선언 ∩ 어댑터 등록. 화면은 이 값으로 버튼 활성화를 판단한다.
    "availableCapabilities": ["CONNECTION_VERIFICATION", "HOLDING_SNAPSHOT", "TRANSACTION_HISTORY_IMPORT"],
    "supportedMarkets": ["US", "KR"],
    // 추가: 이 제공자에서 가져온 데이터를 원장에 쓸 수 있는 시장. supportedMarkets의 부분집합이다.
    "ledgerWritableMarkets": ["US"],
    "credentialFields": [
      { "key": "clientId", "label": "Client ID", "type": "TEXT", "required": true, "placeholder": "...", "hint": "..." },
      { "key": "clientSecret", "label": "Client Secret", "type": "SECRET", "required": true, "placeholder": "...", "hint": "..." }
    ]
  }
]
```

기존 필드는 이름·의미를 바꾸지 않는다. 추가만 한다.

#### (2) `POST /api/members/{memberId}/broker-connections` — 본문 두 형태 모두 수용

새 형태(권장):

```json
{
  "provider": "TOSS_SECURITIES",
  "displayName": "내 토스 계좌",
  "credentials": { "clientId": "<입력값>", "clientSecret": "<입력값>" }
}
```

레거시 형태(현재 프론트엔드가 보내는 형태, 최소 한 릴리스 유지):

```json
{ "provider": "TOSS_SECURITIES", "displayName": "내 토스 계좌", "clientId": "...", "clientSecret": "..." }
```

서버 규칙:

- 두 형태를 동시에 보내면 `400`(모호한 요청). 어느 쪽도 없으면 `400`.
- 레거시 형태는 `{"clientId": ..., "clientSecret": ...}` 맵으로 정규화한 뒤 동일 경로를 탄다.
- 검증은 **제공자 명세 기준**이다:
  - `credentialFields`에 없는 키가 있으면 `400`("지원하지 않는 자격 증명 항목입니다"). 조용히
    무시하지 않는다. 무시하면 오타 난 키가 통과하고 연결은 검증에서야 실패한다.
  - `required=true` 항목 누락·공백이면 `400`.
  - 값 길이 상한 512자(암호화 후 `VARCHAR(4096)`에 여유 있게 들어가는 값). 초과 시 `400`.
- 응답은 지금과 동일한 `BrokerConnectionResponse`(자격 증명 미포함).
- **오류 메시지에 입력값을 절대 넣지 않는다.** 키 이름까지만 노출한다.

#### (3) 기능·시장 게이트로 인한 응답 변화

| 경로 | 조건 | 상태 | `ApiErrorCode`(신규) |
| --- | --- | --- | --- |
| `POST .../broker-holding-snapshots` | 제공자가 `HOLDING_SNAPSHOT` 미선언 또는 어댑터 없음 | 503 | `BROKER_CAPABILITY_UNAVAILABLE` |
| `POST .../broker-sync-preview` | 동일 | 503 | `BROKER_CAPABILITY_UNAVAILABLE` |
| `POST .../broker-order-imports` | `TRANSACTION_HISTORY_IMPORT` 미선언 | 503 | `BROKER_CAPABILITY_UNAVAILABLE` (기존 동작 유지, 코드만 부여) |
| `POST .../broker-holding-imports` (단건) | 항목 시장이 `ledgerWritableMarkets` 밖 | 422 | `BROKER_MARKET_NOT_LEDGER_WRITABLE` |
| `POST .../broker-holding-imports/batch` | 동일 | 200/201 + `skipped[].reason = UNSUPPORTED_MARKET` | — (기존 제외 계약 유지) |

단건이 422이고 일괄이 제외인 것은 의도적이다. 단건은 사용자가 그 종목 하나를 콕 집어 요청한
것이므로 조용히 건너뛰면 "눌렀는데 아무 일도 없다"가 된다. 일괄은 이미 사유별 제외 계약이 있다.

`BrokerOpeningBalanceSkipReason.UNSUPPORTED_MARKET`은 이미 존재하므로 새 사유를 만들지 않는다.
판정 기준만 `supportedMarkets` → `ledgerWritableMarkets`로 바꾼다.

### 6.4 도메인·서비스 변경

```java
// domain/broker/BrokerCredentials.java (신규, 불변 값 객체)
// - Map<String, String> 래핑. toString()/equals()/hashCode()를 값이 드러나지 않게 재정의한다.
// - get(String key)는 없는 키에 대해 IllegalStateException을 던진다(어댑터의 계약 위반이므로).
public record BrokerCredentials(Map<String, String> values) {
    public BrokerCredentials { values = Map.copyOf(values); }
    public String require(String fieldKey) { ... }
    @Override public String toString() { return "BrokerCredentials(keys=" + values.keySet() + ")"; }
}
```

- `BrokerProvider`에 `ledgerWritableMarkets` 필드 추가. `TOSS_SECURITIES`는 `Set.of(Market.US)`.
  주석으로 "원장에 통화 필드가 없어 US만 허용한다. 통화 모델이 생기기 전에는 넓히지 않는다"를
  남긴다.
- 어댑터 인터페이스:
  - `BrokerConnectionVerifier.verify(BrokerCredentials credentials)`
  - `BrokerHoldingsProvider.fetchHoldings(BrokerCredentials credentials, String accountSequence)`
  - `BrokerOrderHistoryProvider.fetchOrders(BrokerCredentials credentials, String accountSequence, BrokerOrderHistoryQuery query)`
  - 계좌 일련번호는 자격 증명이 아니라 계좌 식별자이므로 분리된 인자로 유지한다.
- `BrokerProviderRegistry`:
  - `requireCapability(BrokerProvider, BrokerProviderCapability)` — 선언 확인.
  - `availableCapabilities(BrokerProvider)` — 선언 ∩ 어댑터 등록.
  - `requireHoldingsProvider` / `requireConnectionVerifier`도 capability 선언을 함께 확인하도록
    통일(현재는 주문 이력만 이중 관문).
  - `requireLedgerWritableMarket(BrokerProvider, Market)` — 위반 시
    `BrokerHoldingImportUnprocessableException`(기존 422 매핑 재사용).
- 자격 증명 로드는 새 컴포넌트 `BrokerCredentialLoader`로 모은다. 지금은 복호화 코드가
  `BrokerConnectionService`, `BrokerHoldingPreviewService`,
  `PortfolioBrokerHoldingSnapshotService`, `BrokerOrderImportContextLoader` 네 곳에 복사돼 있다.
  네 곳이 같은 필드를 각자 복호화하는 구조는 필드가 늘어나는 순간 네 곳을 모두 고쳐야 한다.

### 6.5 DB / 마이그레이션

**V17__generalize_broker_connection_secrets.sql**

```sql
CREATE TABLE broker_connection_secret_values (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    broker_connection_id BIGINT NOT NULL,
    -- BrokerCredentialField.key 와 같은 값이다. 제공자 명세가 진실의 원천이며 이 표는 값만 담는다.
    field_key VARCHAR(64) NOT NULL,
    ciphertext VARCHAR(4096) NOT NULL,
    initialization_vector VARCHAR(255) NOT NULL,
    encryption_key_version INTEGER NOT NULL,
    CONSTRAINT fk_broker_connection_secret_values_connection
        FOREIGN KEY (broker_connection_id) REFERENCES broker_connections (id) ON DELETE CASCADE,
    CONSTRAINT uk_broker_connection_secret_values_field
        UNIQUE (broker_connection_id, field_key)
);

-- 기존 두 컬럼을 그대로 옮긴다. 암호문을 다시 만들지 않으므로 키가 없어도 실행된다.
INSERT INTO broker_connection_secret_values
    (broker_connection_id, field_key, ciphertext, initialization_vector, encryption_key_version)
SELECT broker_connection_id, 'clientId',
       encrypted_client_id, client_id_initialization_vector, encryption_key_version
FROM broker_connection_secrets;

INSERT INTO broker_connection_secret_values
    (broker_connection_id, field_key, ciphertext, initialization_vector, encryption_key_version)
SELECT broker_connection_id, 'clientSecret',
       encrypted_client_secret, client_secret_initialization_vector, encryption_key_version
FROM broker_connection_secrets;
```

핵심 판단:

- **암호문을 재암호화하지 않는다.** 마이그레이션이 암호화 키를 필요로 하면 키가 없는 환경에서
  기동이 막힌다. 컬럼 이동만 한다.
- **`broker_connection_secrets`는 이번 마이그레이션에서 지우지 않는다.** 롤백 여지를 남기고,
  다음 릴리스의 V18에서 드롭한다. 단 애플리케이션 코드는 V17 이후 새 표만 읽고 쓴다. 두 곳에
  동시에 쓰면 어느 쪽이 진실인지 모르게 된다.
- `field_key`에는 외래키를 걸 수 없다(제공자 명세는 enum이지 표가 아니다). 대신 서비스가 쓰기
  전에 `BrokerProvider.getCredentialFields()`로 검증한다.
- 엔티티: `BrokerConnectionSecret`는 `@OneToMany` 컬렉션을 갖는 형태로 바꾸거나, 새 엔티티
  `BrokerConnectionSecretValue`를 `BrokerConnection`에 직접 매단다. **후자를 권한다.** 기존
  `BrokerConnectionSecret`는 V18에서 통째로 사라질 대상이므로 새 코드가 그것에 의존하지 않는 편이
  정리가 쉽다.

`postgresIntegrationTest`가 `ddl-auto=validate`로 매핑을 검증하므로, 엔티티와 V17이 어긋나면
CI에서 잡힌다.

### 6.6 보안 규칙 (이 슬라이스에서 반드시 지킬 것)

1. 평문 자격 증명은 요청 처리 구간과 어댑터 호출 구간에만 존재한다. `BrokerCredentials`의
   `toString()`은 키 목록만 노출한다(값 유출 사고의 가장 흔한 경로가 로깅이다).
2. 어떤 응답에도 자격 증명·복호화된 계좌 일련번호·액세스 토큰을 담지 않는다. 기존 계약 유지.
3. 검증 오류 메시지에 입력값을 넣지 않는다. 필드 키 이름까지만.
4. `field_key`는 화이트리스트(제공자 명세)로만 허용한다. 클라이언트가 임의 키를 만들어 저장소를
   자유 사전으로 쓰지 못하게 막는다.
5. `encryption_key_version`은 값마다 저장한다. 한 연결 안에서 필드별로 키 버전이 다를 수 있어야
   무중단 로테이션이 가능하다(현재는 연결 단위 단일 버전이라 로테이션 시 전부 한 번에 바꿔야 한다).
6. 마이그레이션 로그·테스트 픽스처에 실제 자격 증명을 넣지 않는다. 테스트는 고정된 더미 문자열을
   쓴다.
7. 이 슬라이스는 자격 증명 값을 **읽어서 화면에 돌려주는 경로를 새로 만들지 않는다.**

### 6.7 멱등성 규칙

| 동작 | 멱등성 | 근거 |
| --- | --- | --- |
| `POST broker-connections` | 비멱등(현행 유지) | 같은 제공자로 연결을 두 개 만드는 것은 정상 시나리오다. 이번 슬라이스에서 바꾸지 않는다. |
| `POST .../verify` | 멱등 | 계좌 재조정은 일련번호 기준 upsert + `DETACHED` 표시라 반복 실행이 안전하다(현행). |
| V17 백필 | 1회성, 재실행 안전 | `uk_broker_connection_secret_values_field`가 중복 삽입을 막는다. Flyway가 버전 관리하므로 재실행 자체가 없다. |
| 자격 증명 저장 | 필드 키 단위 upsert | 같은 연결에 같은 키가 두 행이 되면 어느 값을 쓸지 정해지지 않는다. 유니크 제약이 이를 DB에서 막는다. |
| 원장 쓰기 경로 | 변경 없음 | 개시 잔고(스냅샷 항목 유니크)와 주문 이력(계좌+주문식별자 부분 유니크)의 기존 멱등 계약을 그대로 유지한다. 이번 슬라이스는 게이트만 추가한다. |

### 6.8 테스트 계획

**단위 — 도메인**

1. `BrokerCredentials.toString()`이 값 문자열을 포함하지 않는다.
2. `BrokerCredentials.require()`가 없는 키에 예외를 던진다.
3. `BrokerProvider.TOSS_SECURITIES.ledgerWritableMarkets()`가 `{US}`이고 `supportedMarkets`의
   부분집합이다. **모든 제공자에 대해 부분집합 불변식을 검사한다**(제공자가 늘어도 자동 적용).
4. `BrokerProviderCredentialSchemaTest`(기존) 확장: 모든 제공자의 `credentialFields` 키가
   중복되지 않고, 64자 이하이며, `SECRET` 타입 필드의 `placeholder`/`hint`가 비어 있지 않다.

**단위 — 레지스트리**

5. capability 미선언 제공자는 어댑터가 등록돼 있어도 `requireHoldingsProvider`가 거부한다.
6. `availableCapabilities`가 선언 ∩ 어댑터 등록의 교집합이다(어댑터 없는 선언은 제외).
7. `requireLedgerWritableMarket(TOSS, KR)`이 422 매핑 예외를 던지고, `US`는 통과한다.

**서비스**

8. 새 형태(`credentials` 맵) 연결 생성이 필드별 암호화 행 2개를 만든다.
9. 레거시 형태 연결 생성이 같은 결과를 만든다(정규화 경로).
10. 두 형태를 동시에 보내면 거부한다.
11. 명세에 없는 키를 보내면 거부하고 **아무 행도 만들지 않는다**(부분 저장 금지).
12. `required` 누락 시 거부한다.
13. 값이 512자를 넘으면 거부한다.
14. `BrokerCredentialLoader`가 저장된 필드 전부를 복호화해 `BrokerCredentials`로 돌려준다.
15. **D1 회귀 테스트:** KR 스냅샷 항목의 단건 개시 잔고 승인이 422로 거부되고
    `trade_transactions`에 행이 생기지 않는다.
16. **D1 회귀 테스트:** KR 항목이 섞인 일괄 반영에서 KR은 `UNSUPPORTED_MARKET`으로 제외되고
    US 항목은 정상 반영된다.

**컨트롤러(API 계약)**

17. `GET /api/broker-providers`에 `availableCapabilities`, `ledgerWritableMarkets`가 있고
    `credentialFields`에 값이 아닌 명세만 있다.
18. 연결 생성 응답에 자격 증명 관련 필드가 어떤 이름으로도 없다.
19. 오류 응답 본문에 입력한 자격 증명 문자열이 포함되지 않는다(요청에 넣은 표식 문자열이 응답
    전체 문자열에 없음을 단언).
20. 다른 회원의 연결에 접근하면 기존 소유권 검증대로 거부된다(기존 `MemberBoundaryTest` 유지).

**저장소 / 마이그레이션**

21. `broker_connection_secret_values`의 `(connection_id, field_key)` 유니크 제약이 중복을 막는다.
22. 연결 삭제 시 `ON DELETE CASCADE`로 비밀 값 행이 함께 지워진다.
23. `postgresIntegrationTest`: V1~V17 전체 적용 후 엔티티 매핑 validate 통과, 그리고 기존 연결
    2행이 백필돼 `clientId`/`clientSecret` 키로 조회된다.

**실행 명령**

```bash
./gradlew test
./gradlew postgresIntegrationTest   # Docker 필요
```

프론트엔드는 이 슬라이스에서 수정하지 않으므로 lint/build는 회귀 확인 목적으로만 실행한다.

### 6.9 완료 조건

- 위 23개 테스트가 모두 통과한다.
- 토스 어댑터 3종이 `BrokerCredentials`를 받도록 바뀌었고, 자격 증명 복호화 코드가
  `BrokerCredentialLoader` 한 곳에만 있다.
- 원장에 쓰는 두 경로(개시 잔고 단건·일괄)와 조회 경로 전부가 레지스트리 게이트를 지난다.
- `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`의 자격 증명 저장 절과
  `docs/ARCHITECTURE_ROADMAP.md` 4절에 새 저장 모델을 반영한다.
- 프론트엔드 변경 없이 기존 설정 화면이 그대로 동작한다.

---

## 7. 이후 슬라이스 순서 (권고)

| 순서 | 슬라이스 | 닫는 갭 | 규모 |
| --- | --- | --- | --- |
| ~~S2~~ | ~~실행 요약에 승인 판정 집계 추가 → 상세 응답에서 `items` 제거~~ **완료** | P1(D3) | 소 |
| ~~S3~~ | ~~증권사 오류 코드 체계화(`ApiErrorCode` 확장, 404/409 분리)~~ **완료** | E1~E3(D5) | 소 |
| ~~S4~~ | ~~보유 종목 경로 트랜잭션 분리(`...ContextLoader` 패턴 적용)~~ **완료** | B1(D2) | 소 |
| ~~S5~~ | ~~사용자 주도 원장 정합성 점검 실행(독립 API + 이력 저장 + 차이 사유 분류)~~ **완료** | C1~C4 | 중 |
| ~~S6a~~ | ~~조회 기간 상한(366일, 양 끝 포함)·미리보기/스냅샷/주문이력 3개 엔드포인트 중복 호출 보호(409/429)·브로커 HTTP connect/read timeout~~ **완료(2026-09-10, 백엔드 전용)** | E4(D7) 전체, B2/D7 | 중 |
| ~~S6b~~ | ~~서버 구간 분할(30일 창, 공유 예산 50호출, 부분 커버 + `nextOrderedFrom` 이어받기)·불완전 이력 승인 정책 B(대조 `MATCHED`가 아닌 부분 커버는 `acknowledgeIncompleteCoverage` 명시 확인 필요, 실행 단위 확인 감사 컬럼)~~ **완료(2026-09-10, 백엔드 전용)** | B3 전체 | 중 |
| ~~S7~~ | ~~자격 증명 키 로테이션 절차(필드별 `key_version` 활용)~~ **완료(2026-09-14, 백엔드 구현·운영 절차 문서화)** | ~~아키텍처 문서의 미이행 항목~~ | 중 |
| ~~S8~~ | ~~의심 판정 항목의 사용자 재판정(감사 링크 포함)~~ **완료(2026-09-14, 백엔드 전용)** | B4 | 중 |

S5(정합성 점검)의 설계 방향만 미리 적어 뒀던 절이다. 구현 계약이 아니라 다음 설계의 출발점으로
남겨 뒀고, 아래는 그 출발점을 실제로 구현하며 굳힌 내용이다.

- 새 엔드포인트 `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-reconciliations`는
  **증권사를 호출하지 않는다.** 저장된 최신 스냅샷(`PortfolioBrokerHoldingSnapshotService`)과
  현재 원장만 비교하며, 비교 계산 자체는 기존 `BrokerHoldingPreviewCalculator`를 그대로
  재사용한다. 외부 호출이 필요하면 사용자가 먼저 스냅샷을 갱신한다.
- 결과는 종목별 줄(`BrokerReconciliationLine`)과 함께 **차이 사유 후보**
  (`BrokerReconciliationReasonCode`)를 붙인다: `UNAPPROVED_RUN_EXISTS`(미승인 실행 존재),
  `BASELINE_EXCLUDED_HISTORY`(개시 잔고 기준점 이전 거래), `UNSETTLED_OR_PARTIAL_FILL`
  (미체결·부분 체결 잔량), `OUT_OF_PERIOD_HISTORY`(조회 기간 밖 이력), `LEDGER_MARKET_UNSUPPORTED`
  (D1과 같은 원장 반영 시장 게이트), `UNEXPLAINED_DIFFERENCE`(설명되지 않는 차이, 사람이 판단).
  전부 이미 저장된 주문 이력 실행·개시 잔고 승인 이력·제공자 카탈로그만으로 계산하며, 한 줄에
  여러 사유가 함께 붙을 수 있다. 판정 로직은 `BrokerReconciliationReasonResolver`에 모았다.
- 결과는 저장한다(`broker_reconciliation_runs` + `broker_reconciliation_lines` +
  `broker_reconciliation_line_reasons`, V18 마이그레이션). `GET .../broker-reconciliations`
  (페이징 목록)와 `GET .../broker-reconciliations/{runId}`(상세)로 다시 조회할 수 있다.
- 이 API는 어떤 경우에도 `TradeTransaction`이나 파생 `Holding`을 고치지 않는다. C2(두 비교
  기준의 혼선)는 이 실행이 정본 비교 기준이 되면서 정리된다.

---

## 8. 운영·보안 점검 메모 (슬라이스 밖, 사용자 확인 필요)

- `src/main/resources/application-local.yml`은 `.gitignore` 46번 줄로 제외돼 있고 Git 이력에도
  없다(`git log`로 확인). 다만 이 파일에는 외부 서비스 키와 증권사 암호화 키가 **평문으로 들어
  있다.** `docs/LEARNING_LOG.md`의 "다음 작업 3번"이 이미 키 폐기·재발급을 예정하고 있으므로 그
  항목을 유지한다. 값은 이 문서에 옮기지 않았다.
- 증권사 암호화 키를 교체하면 기존 연결은 복호화할 수 없다. `BrokerConnectionService.indexBySequence`가
  복호화 실패를 삼켜 재검증이 실패하지는 않지만, 기존 계좌는 새 행으로 다시 만들어진다. 키 교체
  전에 연결을 삭제하고 다시 등록하는 편이 상태가 깨끗하다(S7이 이 절차를 대체할 때까지).

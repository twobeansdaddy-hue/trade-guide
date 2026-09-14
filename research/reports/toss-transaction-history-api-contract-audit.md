# 토스증권 거래 내역 API 계약 감사 (공식 문서 기준)

- Owner: Claude research
- Task ID: `toss-transaction-history-contract-audit`
- 조사일: 2026-09-07 (UTC)
- 조사 방식: **공개된 공식 문서만** 조회했다. 실제 자격 증명, `.env`,
  `application-local.yml`, 라이브 계좌 엔드포인트는 호출하지 않았다.
- 대상 질문: `docs/agent-tasks/broker-transaction-history-import.md` 6장의
  미확인 항목 **U-1 ~ U-15**

## 0. 한 줄 결론

**토스증권 Open API에는 "계좌 거래 내역(체결 이력)" 전용 엔드포인트가 존재하지 않는다.**
가장 가까운 것은 **주문(order) 단위** 조회인 `GET /api/v1/orders`이며,
체결 1건 단위가 아니라 **주문 1건에 집계된 체결 결과**를 반환한다.
따라서 설계 문서가 전제한 "체결 고유 식별자 기반 멱등성"(U-6)은 **현재 스펙으로 성립하지 않는다.**
설계를 주문 단위 멱등키로 바꾸면 착수 가능성이 생기지만,
U-4(조회 가능 기간)·U-15(약관)·"앱에서 낸 주문 포함 여부"가 여전히 미확인이므로
**현 시점 구현 착수 조건은 미충족이다.**

## 1. 출처 (직접 URL)

| # | 출처 | URL | 확인한 버전/해시 |
| --- | --- | --- | --- |
| S-1 | OpenAPI 3.1 스펙 (canonical, source of truth) | `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json` | `info.version = 1.2.14`, sha256 `a7b32ba754401d13fa649ba91eebd212420eb1afab28e9c2c0d6ea8d43055fed` |
| S-2 | 공식 Overview / 연동 가이드 (Rate Limits·에러 모델·시작하기) | `https://openapi.tossinvest.com/openapi-docs/overview.md` | sha256 `dfad8c9251917daf39d2b2a9e455f0d7cadddafb42a34f47b2ee8d67bf4addd8` |
| S-3 | AsyncAPI 3.0 스펙 (실시간 웹소켓) | `https://openapi.tossinvest.com/openapi-docs/latest/asyncapi.json` | `info.version = 1.2.2`, sha256 `130251057fd9535a3e276099f9166b445f8c51f505f30540758e4b209231282e` |
| S-4 | LLM/에이전트용 문서 인덱스 | `https://developers.tossinvest.com/llms.txt` | S-1을 "source of truth"로 명시 |
| S-5 | 사람용 인터랙티브 API 레퍼런스 | `https://developers.tossinvest.com/docs` | 목차 확인 |
| S-6 | 토스증권 Open API 소개 페이지 | `https://corp.tossinvest.com/ko/open-api` | **SPA라 서버 렌더링 본문 없음. 내용 확인 실패** |

S-4가 "OpenAPI JSON을 endpoints·schemas·authentication·errors·rate limits의 source of truth로 사용하라"고
명시하므로, 이 보고서의 계약 사실은 S-1을 1차 근거로 하고 S-2를 보조 근거로 쓴다.

재현 절차:

```bash
curl -sS https://openapi.tossinvest.com/openapi-docs/latest/openapi.json -o openapi.json
curl -sS https://openapi.tossinvest.com/openapi-docs/overview.md -o overview.md
shasum -a 256 openapi.json overview.md
```

## 2. U-1 — 거래 내역 엔드포인트가 존재하는가

### 2.1 확인 결과: 전용 엔드포인트는 **없다** (확정)

S-1의 `paths` 전체(37개 오퍼레이션)를 열거해 확인했다. 계좌 관련 오퍼레이션은 다음이 전부다.

| 태그 | 오퍼레이션 |
| --- | --- |
| Account | `GET /api/v1/accounts` |
| Asset | `GET /api/v1/holdings` |
| Order | `POST /api/v1/orders`, `POST /api/v1/orders/{orderId}/modify`, `POST /api/v1/orders/{orderId}/cancel` |
| **Order History** | **`GET /api/v1/orders`**, **`GET /api/v1/orders/{orderId}`** |
| Order Info | `GET /api/v1/buying-power`, `GET /api/v1/sellable-quantity`, `GET /api/v1/commissions` |
| Conditional Order (History) | 조건주문 등록·수정·취소·목록·상세 |

"거래 내역", "체결 내역", "입출금", "배당", "예수금 원장"에 해당하는 계좌 API는 **스펙에 존재하지 않는다.**

**혼동 주의(확정):** `GET /api/v1/trades`는 태그가 `Market Data`이고 설명이
"당일 최근 체결 내역을 조회합니다", 파라미터는 `symbol`·`count`다.
이는 **시장 전체의 공개 체결 테이프**이며 본인 계좌와 무관하다.
계좌 헤더도 요구하지 않는다. **이 경로를 거래 내역으로 사용하면 안 된다.**

### 2.2 대체 후보: `GET /api/v1/orders` (Order History)

S-1의 태그 설명(확정 인용):

> "제출한 주문의 처리 상태와 체결 내역을 조회하는 그룹입니다. `status` 파라미터로 라이프사이클 그룹
> (현재 진행 중 주문 `OPEN`, 종료된 주문 `CLOSED`)을 선택해 주문 목록을 조회하고,
> 개별 `orderId`로 모든 상태의 주문 상세를 조회할 수 있습니다. (…)
> 호출 시 `X-Tossinvest-Account` 헤더가 필요합니다."

즉 **주문 원장**이지 **거래 원장**이 아니다. 이 차이가 3장·4장의 모든 제약을 만든다.

## 3. `GET /api/v1/orders` 계약 상세 (모두 S-1 확정)

### 3.1 요청

| 항목 | 값 |
| --- | --- |
| 메서드·경로 | `GET https://openapi.tossinvest.com/api/v1/orders` |
| 인증 | `oauth2ClientCredentials` — `Authorization: Bearer {access_token}` |
| 계좌 지정 | `X-Tossinvest-Account` 헤더 (**required**), 스키마 `integer/int64`, 값은 `GET /api/v1/accounts` 응답의 `accountSeq` |
| Rate Limits Group | `ORDER_HISTORY` |
| 상세 조회 | `GET /api/v1/orders/{orderId}` — 같은 헤더, 모든 상태의 주문 조회 가능, 응답은 단일 `Order` |

쿼리 파라미터:

| 이름 | 필수 | 타입 | 의미 |
| --- | --- | --- | --- |
| `status` | **필수** | `OPEN` \| `CLOSED` | 라이프사이클 **그룹** 라벨. `orders[].status`와 값 체계가 다르다 |
| `symbol` | 선택 | `^[A-Za-z0-9.\-]+$` | KRX는 6자리 숫자(`005930`), US는 영문 티커(`AAPL`) |
| `from` | 선택 | `date` | 조회 시작일(inclusive, **KST**). 기준은 **`orderedAt`(주문 생성 시각)** |
| `to` | 선택 | `date` | 조회 종료일(inclusive, KST). 기준은 `orderedAt` |
| `cursor` | 선택 | string | 페이지네이션 커서 |
| `limit` | 선택 | integer 1~100, 기본 20 | 페이지 크기 |

`status` 그룹 매핑(스펙 원문):

- `OPEN` → `orders[].status ∈ {PENDING, PARTIAL_FILLED, PENDING_CANCEL, PENDING_REPLACE}`
- `CLOSED` → `orders[].status ∈ {FILLED, CANCELED, REJECTED, REPLACED, CANCEL_REJECTED, REPLACE_REJECTED, PARTIAL_FILLED}`

`PARTIAL_FILLED`가 **양쪽 그룹 모두에 나타난다.** 부분 체결 주문은 `OPEN`으로도 `CLOSED`로도
조회될 수 있으므로, 두 그룹을 합쳐 수집하면 **같은 `orderId`가 중복될 수 있다.**

### 3.2 페이지네이션 (U-3)

| 항목 | 확인 내용 |
| --- | --- |
| 방식 | **커서 기반**. 응답 `result.nextCursor`(nullable), `result.hasNext`(boolean) |
| `status=CLOSED` | `limit`(기본 20, 최대 100), `cursor`, `from`/`to` **모두 적용** |
| `status=OPEN` | 대기 주문 **전량 반환**. `limit`·`cursor`는 **무시**되고 `nextCursor`는 항상 `null`, `hasNext`는 항상 `false`. `from`/`to`만 적용 |
| 정렬 순서 | **문서화되어 있지 않다 (미확인)**. 예시의 커서는 `{"orderedAt":..., "orderId":...}`의 base64로 보이나, 이는 예시값이며 정렬 계약이 아니다 |

과거 이력 백필은 반드시 `status=CLOSED` + `from`/`to` + 커서 순회로 해야 한다.

### 3.3 응답 스키마

성공 envelope는 `{ "result": ... }`이고 실패는 `{ "error": ... }`이며 **동시에 나타나지 않는다.**
`result`는 `PaginatedOrderResponse`:

```
result: { orders: Order[], nextCursor: string|null, hasNext: boolean }
```

`Order` (required: `orderId, symbol, side, orderType, timeInForce, status, quantity, currency, orderedAt, execution`):

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `orderId` | string | **주문** 식별자 (체결 식별자가 아님) |
| `symbol` | string | KRX 6자리 숫자 / US 영문 티커 |
| `side` | `BUY` \| `SELL` | 주문 방향 |
| `orderType` | `LIMIT` \| `MARKET` | 호가 유형 (unknown 허용 요구) |
| `timeInForce` | `DAY` \| `CLS` \| `OPG` | 유효 조건 (unknown 허용 요구) |
| `status` | `OrderStatus` 10종 | 아래 3.5 |
| `price` | string decimal \| null | 주문 가격(native currency). `MARKET`이면 null |
| `quantity` | string decimal | **주문** 수량 (체결 수량 아님) |
| `orderAmount` | string decimal \| null | **금액 기반 US 시장가 매수 주문에만** 존재(USD). 그 외 null |
| `currency` | `KRW` \| `USD` | 통화 (unknown 허용 요구) |
| `orderedAt` | date-time | 주문 시각 (ISO 8601, **KST 오프셋 포함**) |
| `canceledAt` | date-time \| null | 취소 시각 |
| `execution` | `OrderExecution` | 체결 **집계** 결과 |

`OrderExecution` (모든 필드 required, 값은 nullable):

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `filledQuantity` | string decimal | 체결 수량 (미체결 시 `"0"`) |
| `averageFilledPrice` | string decimal \| null | **평균** 체결 가격(native currency). 미체결 시 null |
| `filledAmount` | string decimal \| null | 총 체결 금액(native currency) |
| `commission` | string decimal \| null | **총 체결 수수료**(native currency) |
| `tax` | string decimal \| null | **총 체결 세금**(native currency) |
| `filledAt` | date-time \| null | **최종** 체결 시각 (ISO 8601, KST) |
| `settlementDate` | date \| null | 결제 예정일 (YYYY-MM-DD, KST). 미결제 시 null |

**모든 수량·금액은 `type: string, format: decimal, maxLength: 30`인 문자열 decimal이다** (U-11 확인).
소수 자릿수(scale) 상한은 문서화되어 있지 않다.

### 3.4 시각 의미 (U-5)

- `orderedAt` = 주문 **생성** 시각, `filledAt` = **최종 체결** 시각. 둘 다 ISO 8601 + KST 오프셋(`+09:00`)이며
  **날짜만 제공되는 형태가 아니다.**
- `settlementDate`는 **결제 예정일**(date)이며 체결 시각과 별개다.
- **한계(확정):** 부분 체결이 여러 번 일어나도 `filledAt`은 **마지막 체결 시각 하나뿐**이다.
  개별 체결 시각 목록은 제공되지 않는다.
- `from`/`to` 필터는 `filledAt`이 아니라 **`orderedAt` 기준**이다. 장 마감 후·익일 체결처럼
  주문일과 체결일이 다른 건은 기간 필터 경계에서 누락/중복될 수 있다.

### 3.5 매수/매도·정정·취소 (U-7, U-9)

`OrderStatus` 전체와 스펙 원문 설명:

| 값 | 의미 |
| --- | --- |
| `PENDING` | 체결 대기 |
| `PENDING_CANCEL` | 취소 요청 접수, 브로커 응답 대기 |
| `PENDING_REPLACE` | 정정 요청 접수, 브로커 응답 대기 |
| `PARTIAL_FILLED` | 부분 체결 |
| `FILLED` | 전량 체결 |
| `CANCELED` | 취소 완료. `execution.filledQuantity`로 부분 체결 여부 확인 |
| `REJECTED` | 브로커 거부. `execution.filledQuantity`로 부분 체결 여부 확인 |
| `CANCEL_REJECTED` | 취소 거부. **별도 주문 레코드로 생성됨.** 원주문은 이전 상태로 복귀 |
| `REPLACE_REJECTED` | 정정 거부. **별도 주문 레코드로 생성됨.** 원주문은 이전 상태로 복귀 |
| `REPLACED` | 정정 수락되어 원주문이 대체됨. `execution.filledQuantity`로 부분 체결 여부 확인 |

확정 사실:
- 매수/매도 구분은 `side`(`BUY`/`SELL`)로 명확하다.
- 취소는 **반대 부호 별건이 아니라 상태 전이 + `canceledAt`**로 표현된다. `CANCELED`여도
  `filledQuantity > 0`이면 그만큼은 실제 체결이다. **상태만 보고 버리면 체결분을 잃는다.**
- 정정은 원주문이 `REPLACED`가 되고 대체 주문이 별도 레코드로 생긴다.
- 거부는 `REJECTED`, 그리고 취소/정정 거부는 **또 다른 주문 레코드**로 생성된다.

**미확인 (중대):** 스펙의 `Order` 스키마에는 **`REPLACED` 원주문과 대체 주문을 잇는 필드가 없다**
(`parentOrderId`·`replacedByOrderId` 등 없음). 설계 문서 8장의 정정 체인
(`superseded_by_item_id`)을 **응답만으로는 복원할 수 없다.**

**범위 한계 (확정):** 이 API는 **주문 API**다. 배당, 입출금, 대체출고, 권리 배정, 주식병합 같은
비매매 거래는 애초에 반환되지 않으며, 해당 엔드포인트도 스펙에 없다.
따라서 U-7의 "매수/매도 외 유형 코드 체계"는 **문제가 성립하지 않는다 — 그런 유형 자체가 이 API에 없다.**
증권사 원장과 Trade Guide 원장을 동일시할 수 없다는 뜻이다.

### 3.6 시장·통화·수수료·세금 (U-8, U-10)

- **통화:** `currency`는 `KRW`/`USD`. 모든 금액은 **native currency** 기준이다.
- **환율:** 주문 응답에 환율 필드가 **없다**. 별도 `GET /api/v1/exchange-rate`가 있으나
  S-2가 "1분 주기 갱신, **참고용 표시 환율**"이라고 명시하므로 **체결 시점 정산 환율이 아니다.**
  원화 환산 원가를 이 값으로 계산하면 안 된다.
- **시장 구분 (중대):** `Order`에는 **`marketCountry` 필드가 없다.** `GET /api/v1/holdings`의
  `HoldingsItem`에는 있는데 주문 응답에는 없다. 즉 현재 `TossSecuritiesHoldingsProvider`가 쓰는
  `marketCountry → Market` 매핑을 그대로 재사용할 수 없고, `symbol` 표기 규칙(6자리 숫자=KR,
  영문 티커=US)과 `currency`로 **추론**해야 한다. 이는 스펙이 보장하는 매핑이 아니다.
- **수수료·세금:** `execution.commission`(총 수수료), `execution.tax`(총 세금)만 제공된다.
  위탁수수료/유관기관수수료/거래세/농특세/원천징수 같은 **세부 분해는 제공되지 않는다.**
  둘 다 nullable이다.
- 참고: `GET /api/v1/commissions`는 **계좌의 시장별 수수료율**(`marketCountry`, `commissionRate`,
  `startDate`, `endDate`)을 반환하며, 개별 체결의 실제 부과액이 아니다.

### 3.7 unknown enum 허용 (U-12)

**확인.** `orderType`, `timeInForce`, `OrderStatus`, `Currency`, `MarketCountry`, 그리고 에러 `code`가
모두 "클라이언트는 unknown code 를 허용하도록 구현해야 합니다"를 명시한다.
현재 `TossSecuritiesHoldingsProvider`가 지키는 원칙(미지원 값은 제외하고 건수 보고)이 이 응답에도 그대로 필요하다.

### 3.8 오류 응답 (U-14)

envelope: `{"error": {"requestId", "code", "message", "data"?}}`.
`requestId`는 응답 헤더 `X-Request-Id`와 같은 값이다.

`GET /api/v1/orders`에 선언된 응답: `200`, `400`, `401`, `404`, `429`, `500`.
관련 코드(S-1 예시 + S-2 에러 표):

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | `invalid-request` | 유효하지 않은 요청 (예: `status`가 OPEN/CLOSED 아님, `data.allowedValues` 동반) |
| 400 | `account-header-required` | `X-Tossinvest-Account` 헤더 누락 |
| 401 | `invalid-token` / `expired-token` / `login-user-not-found` / `edge-blocked` | 인증 실패. `WWW-Authenticate: Bearer ...` 동반 |
| 403 | `forbidden` / `edge-blocked` | 권한 부족 / 허용되지 않은 요청 |
| 404 | `account-not-found` | 계좌 헤더가 가리키는 계좌 없음 |
| 404 | `order-not-found` | 상세 조회 시 `orderId` 없음 |
| 429 | `rate-limit-exceeded` / `edge-rate-limit-exceeded` | 한도 초과. `Retry-After` 동반 |
| 500 | `internal-error` / `maintenance` | 서버 장애 / 점검 |

**미확인:** "조회 기간 초과" 전용 에러 코드는 **문서에 없다.** 기간 상한이 있는지 자체가 U-4 미확인이다.

## 4. 운영 제약 (U-13) — 확정

### 4.1 허용 IP 제한 (확정, 중대)

S-2 "시작하기" 2번:

> "**허용 IP 등록** — 설정 > Open API 메뉴 하단의 **허용 IP 관리** 에서 API 호출을 허용할 IP 를 등록합니다.
> 등록된 허용 IP 목록에 없는 IP 에서의 호출은 403 으로 차단됩니다."

S-1 `POST /oauth2/token`의 403 응답도 동일하다
(`{"error":"access_denied","error_description":"IP address not allowed"}`).
S-2의 웹소켓 절에 따르면 REST와 웹소켓이 **동일한 허용 IP 목록**을 쓴다.

→ Trade Guide를 고정 IP가 아닌 환경(로컬 개발 PC의 동적 IP, 서버리스, 오토스케일 인스턴스)에서
운영하면 **거래 내역 동기화가 상시 실패한다.** 이건 코드 문제가 아니라 배포 요건이다.

### 4.2 Rate Limits (확정)

S-2 표에서 관련 그룹만:

| Group | 한도 | 피크시간 |
| --- | --- | --- |
| `AUTH` | **초당 최대 5회** | -- |
| `ACCOUNT` | 초당 최대 1회 | -- |
| `ASSET` | 초당 최대 5회 | -- |
| **`ORDER_HISTORY`** | **초당 최대 5회** | 별도 없음 |
| `ORDER_INFO` | 초당 최대 6회 | 09:00~09:10 KST 초당 3회 |

- 한도는 **클라이언트 × API 그룹** 단위 TPS다.
- "운영 상황에 따라 **사전 공지 없이 조정**될 수 있으며, 현재 허용 한도는 `X-RateLimit-Limit`로 확인"한다고 명시한다.
  → 코드에 한도 상수를 하드코딩하면 안 된다. 헤더를 읽어야 한다.
- 응답 헤더: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, (429에만) `Retry-After`.
- 권장 대응: `Retry-After`만큼 대기 + 지수 백오프(1s→2s→4s) + jitter.
- 커서 페이지 크기 최대 100 × 초당 5회이므로, 대량 백필은 **분당 최대 30,000건** 이론치이나
  실제로는 백오프를 고려해 훨씬 낮게 설계해야 한다.

### 4.3 토큰 수명 (확정, 기존 구현에 대한 경고)

S-1 `POST /oauth2/token` 설명:

> "refresh token 은 제공되지 않습니다. 만료 시 동일 엔드포인트로 재발급합니다.
> **client 당 유효한 access token 은 1 개입니다. 재발급 시 이전에 발급된 token 은 즉시 무효화됩니다.**"

→ 현재 `TossSecuritiesAccessTokenIssuer`는 **호출 때마다 새 토큰을 발급**한다
(`TossSecuritiesConnectionVerifier`, `TossSecuritiesHoldingsProvider` 각각 발급).
거래 내역 백필처럼 **여러 페이지를 순회하는 작업 중에 다른 요청이 토큰을 재발급하면
진행 중이던 순회가 401 `invalid-token`으로 깨진다.** 이건 거래 내역 기능을 붙이기 전에
정리해야 할 **기존 결함**이다. 동시에 `AUTH` 그룹도 초당 5회 한도를 갖는다.

## 5. U-1 ~ U-15 판정표

| # | 항목 | 판정 | 근거 / 미확인 사유 |
| --- | --- | --- | --- |
| U-1 | 체결/거래 내역 엔드포인트 존재 여부 | **확정: 전용 엔드포인트 없음.** 대체로 `GET /api/v1/orders`(주문 단위) | S-1 paths 전수 확인. 3.1 |
| U-2 | 계좌 지정이 `X-Tossinvest-Account`인가 | **확정: 그렇다.** required, `integer/int64`, 값은 `accountSeq` | S-1 `components.parameters.AccountSeq` |
| U-3 | 페이지네이션 | **확정(부분):** 커서 방식, `limit` 기본 20/최대 100, `CLOSED`에만 적용. **정렬 순서는 미확인** | S-1. 3.2 |
| U-4 | 조회 가능 최대 과거 기간, 1요청 최대 범위 | **미확인.** 스펙은 "미지정 시 전체 기간"만 말하고 상한을 명시하지 않음. 기간 초과 에러 코드도 없음 | 3.2, 3.8 |
| U-5 | 시각 의미·타임존·날짜만 여부 | **확정(제약 있음):** `orderedAt`/`filledAt` 모두 ISO 8601 + KST 오프셋. `settlementDate`는 별도 date. **단 `filledAt`은 "최종" 체결 시각 1개뿐** | 3.4 |
| U-6 | 안정적인 **체결** 고유 식별자 | **확정: 없음.** `orderId`(주문 단위)만 존재. 체결 단위 ID·체결 목록 없음. `orderId`의 재조회 안정성도 문서에 명시되지 않음 | 3.3. **설계 전제 불성립** |
| U-7 | 거래 유형 코드 전체 목록 | **확정(범위 한정):** `side`는 `BUY`/`SELL` 뿐. 배당·입출금·대체·권리 유형은 **이 API에 존재하지 않음**(해당 엔드포인트 없음) | 3.5 |
| U-8 | 수수료·세금 분리 | **확정:** `execution.commission`, `execution.tax` 2개만. native currency 총액. **세부 분해 없음**, 둘 다 nullable | 3.6 |
| U-9 | 정정·취소 표현 | **확정(부분):** 상태 전이(`REPLACED`/`CANCELED`/`*_REJECTED`) + `canceledAt`. 반대 부호 별건 아님. **원주문↔대체주문 연결 필드는 없음(미확인/부재)** | 3.5 |
| U-10 | 통화·환율 | **확정:** `currency` = `KRW`/`USD`, 금액은 native currency, **환율 필드 없음**. `marketCountry` 필드도 **없음**(holdings와 다름) | 3.6 |
| U-11 | 수량·금액 표현 | **확정:** 전부 `string` + `format: decimal` + `maxLength: 30`. **소수 자릿수 상한은 미명시** | 3.3 |
| U-12 | unknown enum 허용 요구 적용 여부 | **확정: 적용된다.** `orderType`·`timeInForce`·`OrderStatus`·`Currency`·에러 `code` 모두 명시 | 3.7 |
| U-13 | 레이트 리밋·동시 호출·허용 IP | **확정:** `ORDER_HISTORY` 초당 5회, `AUTH` 초당 5회, 한도는 사전 공지 없이 변경 가능, `X-RateLimit-*`/`Retry-After` 제공. **허용 IP 등록 필수, 미등록 IP는 403** | 4.1, 4.2 |
| U-14 | 오류 응답 코드 체계 | **확정(부분):** 표 3.8 참조. **"기간 초과" 전용 코드는 없음** | 3.8 |
| U-15 | 개인 계좌 이력 조회가 **약관상 허용**되는가 | **미확인.** 공개 URL에서 Open API 이용약관 원문을 찾지 못함. `corp.tossinvest.com/ko/open-api`는 SPA라 본문 추출 불가. 발급이 WTS 로그인 후 "설정 > Open API"에서 본인 계좌 기준으로 이뤄진다는 사실만 확인됨 | S-2, S-6 |

## 6. 추가로 발견한 미확인 항목 (설계 문서에 없던 것)

| # | 항목 | 왜 중요한가 |
| --- | --- | --- |
| **N-1** | `GET /api/v1/orders`가 **다른 채널(토스증권 앱 등)에서 낸 주문을 포함하는가** | **미확인, 그리고 치명적.** `Conditional Order History` 태그는 "이 API 로 등록한 조건주문뿐 아니라 다른 채널(토스증권 앱 등)에서 등록한 조건주문도 함께 반환됩니다"라고 **명시**한다. 그런데 `Order History` 태그에는 **같은 문장이 없다.** 이 대조는 우연일 수도 있으나, 사용자는 앱으로 매매하므로 앱 주문이 빠지면 이 기능의 존재 이유가 없다. **U-1보다 먼저 확인해야 한다.** |
| N-2 | `PARTIAL_FILLED`가 `OPEN`·`CLOSED` 양쪽 그룹에 포함됨 | 두 그룹을 합쳐 수집하면 같은 `orderId`가 중복 수집된다. 수집 로직에서 dedupe 필수 |
| N-3 | `orderId` 값 안정성 | 예시값이 64자 난수 문자열이다. 재조회 시 동일하다는 **명시적 보장 문구는 스펙에 없다**. 멱등키로 쓰려면 확인 필요 |
| N-4 | 커서 유효기간·데이터 변경 시 동작 | 순회 도중 새 주문이 생기거나 상태가 바뀔 때 커서 일관성이 어떻게 되는지 문서화 없음 |
| N-5 | 기존 토큰 무효화 동작 | 4.3 참조. 거래 내역 기능 이전에 정리해야 할 기존 구현 결함 |

## 7. 웹소켓(`personal:order`)은 백필 대안이 아니다 (확정)

S-3 `realtime-order` 채널:

- `data.order`는 `GET /api/v1/orders/{orderId}` 응답과 **같은 모양이지만 `execution.filledAt`을 포함하지 않는다.**
- "무손실 보장은 **연결 세션 내부에 한정**되어 끊긴 구간의 이벤트는 다시 전달되지 않으므로,
  재연결 후 다시 선언하고 `GET /api/v1/orders` 로 주문 상태를 재동기화하세요."
- 계정당 동시 연결 최대 2개, 클라이언트 수신이 2초 이상 막히면 서버가 연결을 끊는다.
- 허용 IP 제한은 REST와 동일하게 적용된다(403).

→ **과거 이력 백필의 source of truth는 REST `GET /api/v1/orders`뿐이다.** 웹소켓은 증분 갱신용이다.

## 8. Trade Guide 설계에 대한 결론

### 8.1 설계 문서의 착수 조건 대비

`docs/agent-tasks/broker-transaction-history-import.md`는 U-6(안정적 체결 고유 식별자)이
확인되지 않으면 **"멱등성 설계 성립 불가 → 착수 금지"**라고 못 박았다.
조사 결과 **체결 단위 식별자는 존재하지 않는다.** 따라서 문서에 적힌 조건 그대로라면 **착수 금지**다.

다만 이는 "불가능"이 아니라 **"설계 단위를 바꿔야 한다"**는 뜻이다. 선택지는 둘이다.

- **(A) 주문 단위 원장으로 재설계.** 멱등키를 `(broker_account_id, orderId)`로 두고,
  주문 1건 = 원장 1행(체결 수량 `filledQuantity`, 단가 `averageFilledPrice`)으로 매핑한다.
  부분 체결 다건은 평균가로 뭉개지고, 개별 체결 시각은 사라진다.
  Trade Guide는 이동평균 기반 판단 서비스이므로 **평균 단가와 최종 체결 시각으로도 원가 계산은 성립한다.**
  이 방향을 권한다.
- **(B) 착수 보류.** N-1(앱 주문 포함 여부)과 U-4(기간 상한)를 토스증권에 문의해 답을 받은 뒤 재검토.

(A)를 택하더라도 설계 문서 7.2의 `BrokerTransactionImportItem` 스키마는 다음을 고쳐야 한다.

| 기존 컬럼 | 필요한 변경 |
| --- | --- |
| `external_transaction_id` | 의미를 **`external_order_id`**로 바꾼다. 체결 ID가 아님을 이름에 남긴다 |
| `quantity` | `Order.quantity`(주문량)가 아니라 **`execution.filledQuantity`**를 넣는다. 둘을 혼동하면 미체결 주문이 원장에 들어간다 |
| `executed_price` | `execution.averageFilledPrice`. **평균가라는 사실을 컬럼 주석에 남긴다** |
| `executed_at` | `execution.filledAt`. **최종 체결 시각**이며 부분 체결 이력은 없다 |
| `market` | **`marketCountry` 필드가 없으므로 `symbol` 표기 + `currency`로 추론**한다. 추론 불가 건은 `SKIPPED_UNSUPPORTED`로 분류하고 건수를 보고한다 |
| `provider_type_code` | `side` + `status` + `orderType`을 함께 보존한다. `status` 하나로는 부분 체결 취소를 구분할 수 없다 |
| `superseded_by_item_id` | **응답에서 채울 수 없다.** `REPLACED` 주문의 대체 주문을 연결하는 필드가 스펙에 없다. 정정 체인은 자동 구성하지 못하며, 승인 화면에서 사용자가 판단하게 두거나 이 컬럼을 빼야 한다 |

### 8.2 수집 시 반드시 지켜야 할 규칙 (근거 있는 것만)

1. `status=CLOSED`로 `from`/`to` 구간을 나눠 커서 순회한다. `status=OPEN`은 **원장에 넣지 않는다**(미체결).
2. `filledQuantity`가 `"0"`이거나 `averageFilledPrice`가 `null`인 주문은 **체결이 아니므로 제외**한다.
   `CANCELED`·`REJECTED`·`REPLACED`여도 `filledQuantity > 0`이면 **그 체결분은 유효하므로 포함**한다.
3. `orderId`로 dedupe한다(N-2).
4. `from`/`to`는 `orderedAt` 기준이므로, 구간 경계에서 `filledAt`이 밖으로 나가는 건이 생긴다.
   구간을 겹쳐 조회하고 `orderId` dedupe로 정리한다.
5. `X-RateLimit-Remaining`을 읽어 속도를 줄이고, 429는 `Retry-After` + 지수 백오프 + jitter로 재시도한다.
   **한도 수치를 코드에 하드코딩하지 않는다.**
6. unknown enum(`orderType`·`timeInForce`·`status`·`currency`)은 파싱 실패로 만들지 않는다.
   기존 `unsupportedMarketCount` 선례대로 **제외 건수를 반드시 보고**한다.
7. 환율은 이 API로 얻을 수 없다. USD 체결은 **USD 그대로 저장**하고, 원화 환산은 별도 정책으로 분리한다.
   `GET /api/v1/exchange-rate`는 "참고용 표시 환율"이므로 원가 계산에 쓰지 않는다.

### 8.3 릴리스 게이트로 남는 미충족 항목

| 항목 | 상태 | 해소 방법 |
| --- | --- | --- |
| N-1 앱에서 낸 주문 포함 여부 | **미확인 · 최우선** | 토스증권 문의, 또는 본인 계좌에서 앱 주문 1건 후 API 대조(사용자 수행) |
| U-4 조회 가능 최대 과거 기간 | **미확인** | 문의, 또는 사용자 계좌로 오래된 `from` 실측 |
| U-15 약관상 허용 범위 | **미확인** | WTS 설정 > Open API 화면의 약관 동의 문구 확인(사용자만 접근 가능) |
| U-3 정렬 순서 | **미확인** | 실측 또는 문의 |
| N-3 `orderId` 재조회 안정성 | **미확인** | 실측(같은 주문 2회 조회 대조) |
| 4.1 고정 IP 확보 | **확정 요건** | 배포 환경의 고정 IP를 WTS 허용 IP에 등록. 미확보 시 기능 자체가 동작 불가 |
| 4.3 토큰 단일화 | **기존 구현 결함** | 거래 내역 착수 전 토큰 캐싱/단일 발급 경로로 정리 |

`BrokerProvider.TOSS_SECURITIES`에 `TRANSACTION_HISTORY_IMPORT`를 선언하는 것은
위 표의 **N-1·U-15가 해소된 뒤**여야 한다. 나머지는 설계로 흡수 가능하다.

## 9. 이 조사에서 하지 않은 것

- 실제 `client_id`/`client_secret` 사용, 라이브 API 호출, 사용자 계좌 조회를 하지 않았다.
- `.env`, `application-local.yml`, 로컬 자격 증명 파일을 읽지 않았다.
- `src/**`, `frontend/**`, 마이그레이션, 설정, 테스트를 수정하지 않았다. 코드는 읽기만 했다.
- 커밋·푸시하지 않았다.
- 스펙에 없는 엔드포인트·필드명을 만들어 적지 않았다. 확인되지 않은 항목은 전부 "미확인"으로 표기했다.

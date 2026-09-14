# 토스증권 Open API 거래/체결 이력 계약 검증 (U-1~U-15 확인 결과)

- Owner: Claude research-design
- Work mode: `research` (구현·커밋 없음, `src/**`·`frontend/**`·설정·마이그레이션 미수정)
- 근거 문서: `docs/agent-tasks/broker-transaction-history-import.md`의 6장(U-1~U-15)
- 확인 출처(2026-09-14 기준, 아래 모두 토스증권 공식 도메인):
  1. `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json` — OpenAPI 3.1 스펙, `info.version = "1.2.15"`. **source of truth**.
  2. `https://openapi.tossinvest.com/openapi-docs/overview.md` — 공식 개요 문서(Rate Limits 표, 에러 코드 전체 표, 시작하기 절차 포함).
  3. `https://developers.tossinvest.com/docs` — 위 두 문서를 렌더링하는 대화형 개발자 포털(동일 소스).
- 방법: `openapi.json`을 다운로드해 `paths`, `components.schemas`, `components.responses`, `components.parameters`를 직접 파싱했다. 요약이 아니라 스펙 원문의 필드명·enum·설명을 그대로 인용한다.

이 문서는 질문에만 답한다. 결론(착수 여부, D-1~D-12 재확정)은 사용자 승인 사항으로 남긴다.

## 0. 결론 먼저

**토스증권 Open API에는 "거래 내역/체결 이력" 전용 엔드포인트가 없다.** 대신 **`GET /api/v1/orders`(목록)과 `GET /api/v1/orders/{orderId}`(상세)** — 공식 태그명 **"Order History"** — 가 체결 정보를 포함한 주문 이력을 제공한다. 원 설계 문서가 U-1에서 예상한 "체결/거래 내역 엔드포인트"는 사실상 **"주문 이력 엔드포인트이며 체결 정보가 주문에 속한 하위 객체로 붙어 있는 형태"** 다. 이는 설계상 중요한 차이이므로 3장에서 상세히 다룬다.

또한 **배당·입금·출금·대체·환전에 해당하는 API 자체가 이 스펙에 존재하지 않는다** (전체 스펙 텍스트에서 "배당"/"dividend"/"출금"/"deposit"/"withdraw"/"이체"/"transfer"/"환전" 키워드 0건, `overview.md`의 6개 카테고리 전체 목록에도 없음). D-11(비매매 이벤트)은 "API가 제공하지 않아 반영 자체가 불가능"으로 이미 사실상 결정돼 있다.

## 1. U-1~U-15 확인 결과 표

| # | 확인 항목 | 상태 | 요약 |
| --- | --- | --- | --- |
| U-1 | 엔드포인트 존재·경로·메서드·헤더 | **확인** | `GET /api/v1/orders`, `GET /api/v1/orders/{orderId}`. 헤더는 `Authorization: Bearer {token}` + `X-Tossinvest-Account`(필수) |
| U-2 | 계좌 지정 방식 | **확인** | 보유 종목과 동일하게 `X-Tossinvest-Account: {accountSeq}` (정수, `GET /api/v1/accounts` 응답의 `accountSeq`) |
| U-3 | 페이지네이션 | **확인** (상태별로 다름) | 아래 2.1 참조. `cursor`+`limit`(기본 20, 최대 100)은 `status=CLOSED`에만 적용. `status=OPEN`은 페이징 없이 전량 반환 |
| U-4 | 최대 조회 기간 | **미확인** (문서에 상한 명시 없음) | `from`/`to`는 존재하나 최대 범위·최대 과거 기간에 대한 서버 측 제약이 스펙·에러 코드 표 어디에도 없다. 아래 2.2 참조 |
| U-5 | 시각 의미 | **확인** | `orderedAt`=주문 생성 시각, `execution.filledAt`=**최종 체결 시각**(부분 체결 포함 마지막 체결), `execution.settlementDate`=결제 예정일(별도 필드, 날짜만). 날짜만 제공되는 경우 없음 — 모두 KST ISO 8601 date-time |
| U-6 | 안정적 고유 식별자 | **확인, 단 단위가 다름** | `orderId`는 서버 발급 opaque token으로 안정적. 단 **주문 단위 식별자이지 체결(fill) 단위 식별자가 아니다.** 정정·취소는 **새 `orderId`를 발급**한다(같은 식별자 재사용 아님) — 아래 3.3 |
| U-7 | 거래 유형 코드 전체 목록 | **확인 (매수/매도뿐)** | `side` ∈ `{BUY, SELL}`만 존재. 배당·입출금·대체·권리 유형 코드 자체가 없음(API 미제공) |
| U-8 | 수수료·세금 분리 | **확인** | `execution.commission`(수수료)과 `execution.tax`(세금)가 별도 필드로 분리됨. 단 세금 내부 세부 항목(거래세/농특세, US SEC fee 등)의 추가 분해는 없음 — 세금 합계 1개 값만 제공 |
| U-9 | 정정·취소 표현 방식 | **확인** | "반대건형"도 "같은 식별자 재기술형"도 아님. **정정/취소는 항상 새 `orderId`를 발급**하고, 원주문은 상태만 전이(`REPLACED`/`CANCELED` 등)한다. 원주문→신규 주문을 잇는 링크 필드는 응답에 없다 — 아래 3.3 |
| U-10 | 통화·환율 | **확인** | `currency` ∈ `{KRW, USD}`만 존재(그 외 통화 없음). 주문 응답의 금액 필드는 모두 "native currency" 표시일 뿐 환율이 임베드되지 않음. 환율은 별도 `GET /api/v1/exchange-rate`(KRW↔USD)로 조회 |
| U-11 | 수량·금액 표현 | **확인** | 모두 **문자열 decimal**(`type: ["string","null"], format: "decimal", maxLength: 30`). 수량은 KR 정수 전용, US는 시장가 매도에 한해 소수점 **최대 6자리** 허용. 가격은 US $1 미만 소수 4자리, $1 이상 소수 2자리 |
| U-12 | 미지 enum 허용 요구 | **확인 (그대로 적용됨)** | `orderType`, `timeInForce`, `status`, `currency` 스키마 설명에 모두 "클라이언트는 unknown code/enum 값을 허용하도록 구현해야 합니다"가 명시됨 — 보유 종목 API와 동일 원칙 |
| U-13 | 레이트 리밋·IP 제한 | **확인** | Rate Limits Group `ORDER_HISTORY` = **초당 최대 5회**(피크시간 별도 제한 없음). 허용 IP 등록은 **계좌·자산/주문/조건주문 카테고리 전체**(Order History 포함)에 적용 — 미등록 IP는 `403` |
| U-14 | 오류 응답 코드 체계 | **확인** | `401 invalid-token/expired-token/token-revoked/login-user-not-found`, `400 account-header-required`, `404 account-not-found`(계좌 헤더가 가리키는 계좌 없음), `404 order-not-found`(상세 조회), `429 rate-limit-exceeded`. 기간 초과 전용 코드는 없음(U-4가 미확인이기 때문) |
| U-15 | 개인 사용자 계좌 이력 조회 약관 허용 여부 | **미확인** | 개발자 포털(`openapi.json`, `overview.md`)은 기술 계약만 다루고 이용약관 전문을 포함하지 않는다. 신청 절차는 "토스증권 WTS 로그인 → 설정 > Open API 메뉴에서 즉시 발급"으로 확인되나, 개인정보/전자금융거래 관련 약관 동의 내용은 발급 시점 WTS 화면에서 직접 확인해야 한다 — 이 조사로는 확정 불가 |

## 2. 상세 근거

### 2.1 페이지네이션 (U-3)

`GET /api/v1/orders`의 `status` 파라미터는 **필수**이며 `OPEN`/`CLOSED` 두 값만 허용한다. 이 값은 개별 주문의 `status`(`PENDING`, `FILLED` 등)를 그룹화한 필터 라벨이며 값 체계가 다르다(요청 `status=OPEN` → 응답 `orders[].status` ∈ `{PENDING, PARTIAL_FILLED, PENDING_CANCEL, PENDING_REPLACE}`).

- `status=OPEN`: **`limit`/`cursor` 무시, 대기 중 주문 전량 반환.** `from`/`to`만 `orderedAt`(KST) 기준 필터로 적용.
- `status=CLOSED`: `limit`(기본 20, 최대 100), `cursor`, `from`/`to` 모두 적용. 응답은 `{orders[], nextCursor, hasNext}`이며 `nextCursor`가 `null`이면 마지막 페이지.
- 정렬 순서는 스펙에 명시적으로 문서화돼 있지 않다(예시 데이터는 최신순으로 보이나 확정 문구 없음) — **확인 필요 항목으로 남긴다.**

거래 내역 수집(가져오기)에는 `status=CLOSED`만 의미가 있다. `OPEN`(미체결)은 원장에 반영할 대상이 아니다.

### 2.2 최대 조회 기간 (U-4) — 여전히 미확인

`from`/`to` 파라미터 설명은 "조회 시작일/종료일 (inclusive, KST 기준)... 미지정 시 전체 기간"이라고만 돼 있다. 최대 범위, 최대 과거 조회 가능 기간에 대한 명시적 제약이 스펙·에러 코드 표 어디에도 없다. `overview.md`의 에러 코드 표에도 "기간 초과" 계열 코드가 없다.

참고로 이 저장소의 다른 작업 계약(`docs/agent-tasks/broker-order-import-range-limit-and-call-guard.md`)은 **366일 상한을 이미 자체 안전값으로 구현**했다고 기록돼 있으나, 그 문서 자신도 이를 "Codex가 선택한 보수적 기본값이며, 사용자나 증권사가 확인·승인한 제공자 제한이 아니다"라고 명시한다. 즉 366일은 **토스증권이 문서화한 제한이 아니라 이 저장소가 자체적으로 건 안전장치**다. U-4는 여전히 "제공자 공식 제한 없음/미문서화"로 남는다 — 실측(장기간 조회 시 실제 거절 여부·`PAGE_LIMIT_EXCEEDED` 유사 응답)으로만 확인 가능하다.

### 2.3 시각 필드와 결제일 (U-5)

`OrderExecution.filledAt` 설명: "**최종 체결 시간** (ISO 8601, KST)". 즉 부분 체결이 여러 번 일어나도 응답에는 **마지막 체결 시각 하나만** 내려온다(2.6 참고). `settlementDate`는 별도 필드로 "결제 예정일 (YYYY-MM-DD, KST 기준). 미결제 시 null"이며 예시에서 KR 체결일 2026-03-28 → 결제일 2026-03-30(T+2)로 나타난다. US 예시는 `settlementDate: null`로만 제시돼 실제 US 결제 주기(T+1 여부 등)는 이 스펙에서 확인되지 않는다.

`TradeTransaction.tradedAt`에 매핑할 값은 **`execution.filledAt`**(체결 시각)이지 `settlementDate`(결제일)가 아니다 — 원 설계 문서 D-5의 우려("날짜만 제공되는 경우")는 해당하지 않는다. 시각은 항상 date-time으로 내려온다.

### 2.4 통화 (U-10)

`Currency` enum은 `KRW`, `USD` 두 값뿐이다("클라이언트는 unknown enum 값을 허용하도록 구현해야 합니다" 문구 포함). `price`, `execution.filledAmount`, `execution.commission`, `execution.tax` 모두 설명에 "(native currency)"라고 명시돼 있어 **환전 없는 원래 통화 그대로**의 금액이다. 환율은 임베드되지 않으며, 필요하면 `GET /api/v1/exchange-rate`를 별도로 호출해야 한다. 이는 원 설계 문서 4.7(통화)의 전제("Phase 1~2는 US+USD만 허용, 그 외 제외 후 건수 보고")와 충돌하지 않는다 — 오히려 `currency` 필드가 KRW/USD 두 값뿐이므로 "그 외 통화" 케이스 자체가 이 API에는 존재하지 않는다(제외 로직은 시장·심볼 형식 불일치 대비용으로만 남는다).

### 2.5 수수료·세금 (U-8)

`OrderExecution.commission`("총 체결 수수료")과 `OrderExecution.tax`("총 체결 세금")는 별도 필드다. 두 값 모두 미체결 시 `null`이 아니라 문서상 "체결 내역이 없으면 filledQuantity=0"이며 나머지 필드(`averageFilledPrice`, `filledAmount`, `commission`, `tax`, `filledAt`, `settlementDate`)는 예시에서 `null`로 내려온다. 원 설계 문서 4.6·7.2의 `fee`/`tax` 분리 스키마와 그대로 맞는다. 다만 `tax` 내부의 세부 항목(KR 증권거래세/농특세, US SEC 수수료 등)은 이 API로는 분해되지 않는다 — 필요하면 사용자에게 "세금 합계"로만 노출해야 한다.

### 2.6 체결 단위: 주문 1건 = `execution` 객체 1개 (설계에 영향을 주는 핵심 발견)

`GET /api/v1/orders`도 `GET /api/v1/orders/{orderId}`도 **부분 체결을 개별 레코드로 나열하지 않는다.** 각 주문은 단일 `execution` 객체 하나만 가지며, 그 안의 `averageFilledPrice`("부분 체결 시 체결된 건의 평균"), `filledAmount`, `commission`, `tax`는 **그 주문에 대한 누적/가중평균 값**이다. 즉:

- 원 설계 문서(3장)의 `BrokerTransactionRecord`가 "체결 1건 = 레코드 1건"을 전제로 한다면, 토스증권 기준으로는 **"주문 1건 = 레코드 1건"이 맞는 단위**다. 한 주문이 여러 번에 걸쳐 체결됐어도 API 레벨에서는 이미 합산돼 있어 별도 처리할 필요가 없다(오히려 더 단순하다).
- 단, 이는 **평균가·합계만 제공되고 개별 체결 틱은 제공되지 않는다는 뜻**이기도 하다. 체결 시각도 "최종 체결 시각" 하나뿐이라, 같은 주문 안에서 여러 날에 걸쳐 나뉘어 체결된 경우(예: 장기 미체결 후 분할 체결) `tradedAt`이 실제로는 여러 시점의 평균 거래를 대표하는 단일 시각이 된다. 이런 케이스의 발생 빈도·영향은 이번 조사로 확인되지 않았다.

### 2.7 정정·취소 표현 방식 (U-9) — 설계 문서의 두 가설 모두 기각

원 설계 문서 8장은 "재기술형(같은 식별자, 값 변경)" 또는 "반대건형(취소 체결이 별건)" 두 가지를 가정했다. 실제로는 **세 번째 방식**이다.

- `OrderOperationResponse.orderId`(정정 `POST /api/v1/orders/{orderId}/modify`, 취소 `POST /api/v1/orders/{orderId}/cancel`의 응답)의 스펙 설명: **"정정/취소로 새로 발급된 주문 식별자. 원주문의 `orderId`와 다릅니다."**
- `OrderStatus` enum 설명: `REPLACED`="정정됨. 정정 요청이 수락되어 원주문이 대체된 상태", `CANCEL_REJECTED`/`REPLACE_REJECTED`="브로커가 취소/정정 요청을 거부한 경우 **별도 주문 레코드로 생성됨**. 원주문은 이전 상태로 복귀함."

즉 정정·취소는 **매번 새 `orderId`를 만들고, 원주문은 상태 전이만 한다.** 원주문 → 신규 주문을 연결하는 링크 필드(`replacesOrderId` 같은)는 이 스펙 어디에도 없다. `GET /api/v1/orders` 목록에서 두 레코드가 시간·심볼·수량 근접성으로만 "같은 정정 체인"임을 추정해야 하며, API가 그 관계를 보장해 주지 않는다.

이는 원 설계 문서 8장(정정·취소 처리)과 5.1(자연 키)에 직접 영향을 준다 — `superseded_by_item_id` 체인을 자동으로 채울 근거가 API에 없으므로, 정정 감지는 "같은 심볼·유사 시각·수량 차이"를 휴리스틱으로 비교하거나, **애초에 정정 체인 자동 연결 자체를 포기하고 각 `orderId`를 독립 체결 건으로 취급**하는 두 갈래로 결정해야 한다(새 결정 필요 항목, 4장 참고). 다만 `CANCELED` 상태의 주문도 부분 체결분(`execution.filledQuantity > 0`)이 있을 수 있다는 점은 명시돼 있어("`CANCELED`: 취소 완료. `execution.filledQuantity`를 통해 부분 체결 여부를 확인할 수 있음"), **취소된 주문이라도 체결분은 원장 반영 대상에서 배제하면 안 된다** — 이는 원 설계에 없던 필터링 규칙이다.

### 2.8 레이트 리밋·IP 제한 (U-13)

`overview.md`의 Rate Limits 표에서 `ORDER_HISTORY` 그룹은 **초당 최대 5회**, 피크시간 별도 제한 없음(대조: `ORDER`는 평시 10회/초, 09:00~09:10 KST 별도 10회/초 유지; `ORDER_INFO`는 같은 시간대 3회/초로 축소). 응답 헤더 `X-RateLimit-Limit`/`X-RateLimit-Remaining`/`X-RateLimit-Reset`과 429 시 `Retry-After`가 공통 제공된다.

허용 IP 제한은 "계좌·자산", "주문", "조건주문" 세 카테고리 전체(모두 `X-Tossinvest-Account` 헤더를 요구하는 카테고리)에 적용된다고 `overview.md`가 명시한다 — Order History도 이 카테고리에 속하므로 **허용 IP 미등록 시 403**이 적용된다(시세류 API는 토큰만으로 호출 가능해 IP 제한과 무관).

## 3. 원 설계 문서(broker-transaction-history-import.md) 대비 조정이 필요한 지점

이 절은 결정이 아니라 **6장 U 목록이 채워졌을 때 3~9장의 어느 부분이 바뀌어야 하는지** 나열한다.

1. **어댑터 계약(3장)의 레코드 단위**: `BrokerTransactionRecord`를 "체결 1건" 대신 **"주문 1건(체결 완료분 포함)"** 으로 재정의해야 한다. `externalTransactionId`는 `orderId`로 매핑 가능(U-6 확인). 단, 부분 체결이 여러 시점에 걸쳐 일어난 주문의 `tradedAt`이 "최종 체결 시각"이라는 근사치라는 점을 D-5 수준의 결정 사항으로 문서에 명시해야 한다.
2. **거래 유형(`BrokerTransactionType`) 열거값**: `DIVIDEND`, `TRANSFER_IN`, `TRANSFER_OUT`, `CORPORATE_ACTION`, `FX` 유형은 **토스증권 Open API가 원천적으로 제공하지 않는다.** 이 열거값들을 남겨두더라도 토스증권 어댑터는 절대 이 값들을 생성하지 못한다 — 명시적으로 "현재 토스증권 어댑터는 `BUY`/`SELL`/`UNKNOWN`만 방출한다"고 문서화해야 D-11이 오해 없이 닫힌다.
3. **정정·취소 처리(8장)**: "재기술형"/"반대건형" 이분법을 버리고, "**항상 새 식별자가 발급되며 API가 원주문-신정정 관계를 보장하지 않는다**"는 전제로 다시 써야 한다. 두 가지 선택지 중 하나를 결정 사항(D-13, 신규)으로 추가해야 한다:
   - (A) 정정 체인 자동 추정을 포기하고 각 `orderId`를 완전히 독립된 체결 건으로 취급 (원주문이 `REPLACED`로 전이되면 그 주문의 체결분은 그대로 유효한 체결로 남는지, 아니면 취소로 간주해야 하는지 자체가 별도 확인 필요 — 이번 조사로 확인 안 됨).
   - (B) 심볼+시각 근접성 휴리스틱으로 체인을 추정하되, 오탐 시 사용자가 직접 병합/분리를 승인하게 한다.
4. **취소된 주문의 부분 체결분 처리**: `CANCELED`/`REJECTED` 상태에서도 `execution.filledQuantity > 0`일 수 있다는 점을 스테이징 필터 로직에 명시적으로 추가해야 한다. "`status=CLOSED`이고 `filledQuantity=0`인 건은 제외, 그 외 `filledQuantity>0`인 모든 건은 후보"가 정확한 필터 규칙이다.
5. **마켓 구분 필드 부재**: 보유 종목 API(`GET /api/v1/holdings`)와 달리 주문 API 응답에는 `marketCountry`가 없다. `Market`(KR/US) 판정은 `currency`(KRW→KR, USD→US) 또는 `symbol` 형식(6자리 숫자 vs 영문 티커)으로 어댑터가 추론해야 한다 — 보유 종목 어댑터가 이미 쓰는 것과 동일한 추론 로직을 재사용할 수 있다.
6. **U-4(최대 조회 기간) 미해결**은 그대로 착수 차단 사유(원 설계 문서 표 그대로)로 남는다. 실측 없이는 "전체 이력 수집 보장"을 설계에 못 박을 수 없다.
7. **U-15(약관) 미해결**도 그대로 릴리스 게이트 차단 사유로 남는다.

## 4. 신규로 필요한 결정 사항 (원 설계 문서 12장에 추가 제안)

| # | 결정 사항 | 선택지 | 비고 |
| --- | --- | --- | --- |
| D-13 | 정정 체인 미보장 시 처리 | (A) 각 `orderId` 독립 체결로 취급 / (B) 휴리스틱 추정 + 사용자 승인 | U-9 확인 결과 API가 링크를 제공하지 않아 신규로 필요해진 결정 |
| D-14 | `CANCELED`/`REJECTED` 부분 체결분 반영 여부 | (A) `filledQuantity>0`이면 무조건 후보 포함 / (B) 주문 최종 상태가 `FILLED`인 것만 포함 | (A) 권장 — 실제로 발생한 체결을 상태 라벨 때문에 누락시키면 안 됨 |
| D-15 | 부분 체결이 여러 날에 걸친 주문의 `tradedAt` 근사 허용 여부 | (A) `execution.filledAt`(최종 체결 시각)을 그대로 승인 / (B) 이런 케이스를 감지해 보류 | API가 개별 체결 시각을 안 주므로 (B)는 감지 방법 자체가 없음 — 사실상 (A)만 가능 |

## 5. 착수 전 확인 체크리스트 갱신 (원 설계 문서 13장 대비)

- [x] U-1, U-2, U-3, U-5, U-6, U-7, U-8, U-9, U-10, U-11, U-12, U-13, U-14 — 공식 문서로 확인 완료(위 표).
- [ ] U-4(최대 조회 기간) — **미확인, 실제 계좌로 장기간 조회를 실측하거나 토스증권 CS/개발자 지원 채널에 별도 문의해야 한다.**
- [ ] U-15(개인 이용약관 허용 범위) — **미확인, WTS Open API 발급 화면의 약관 동의 문구를 직접 확인해야 한다.** 이 저장소의 정책상(`CLAUDE.md`) 자격 증명·개인 정보를 다루지 않으므로 이 조사에서는 화면 캡처나 약관 원문을 수집하지 않았다.
- [ ] D-13~D-15(3장·4장) — 사용자 확정 필요.
- [ ] 기존 D-1~D-12 중 U 확인으로 영향받는 항목 없음(D-5는 "날짜만 제공" 우려가 해소돼 오히려 단순해짐 — U-5 참고).

## 6. 참고: 이미 진행 중인 후속 설계 문서와의 관계

이 저장소에는 `docs/agent-tasks/broker-order-import-*.md` 계열 문서(예: `broker-order-import-range-limit-and-call-guard.md`)가 이미 존재하며, 필드명(`filledAt` 등)과 "`ORDER_HISTORY_IMPORT`" 명칭으로 미루어 볼 때 **`GET /api/v1/orders`를 대상으로 한 실제 구현이 이미 상당히 진행된 것으로 보인다.** 이 문서는 사용자 지시에 따라 `broker-transaction-history-import.md`(더 이른 단계의 설계 초안) 한 건만을 대상으로 검증했다 — `broker-order-import-*` 계열 문서 자체의 코드/구현 상태를 감사하지 않았다. 두 설계 라인 중 어느 쪽이 최신 기준인지, 이 문서의 발견 사항(특히 3.3의 정정 체인 미보장)이 이미 구현된 코드에 반영돼 있는지는 별도 확인이 필요하다.

## 7. 금지 사항 준수

- 이 문서 작성 중 `src/**`, `frontend/**`, 마이그레이션, 설정, 테스트를 수정하지 않았다.
- 이 문서에 기재된 모든 필드명·엔드포인트·enum 값은 위 1번 출처(`openapi.json` 원문) 또는 2번 출처(`overview.md` 원문)에서 직접 인용했다. 추측으로 채운 항목은 없으며, 미확인 항목은 "미확인"으로 명시했다.
- 자격 증명, 액세스 토큰, 실제 계좌번호를 조회·기록하지 않았다(공개 스펙 문서만 열람).
- 커밋·푸시·병합하지 않았다.

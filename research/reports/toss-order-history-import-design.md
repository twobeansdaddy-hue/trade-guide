# 증권사 주문 이력 가져오기 설계 (제공자 중립, 읽기 전용)

- Owner: Claude research-design
- Task ID: `toss-order-history-import-design`
- 작성일: 2026-09-08
- Work mode: `research | design` — 구현·커밋 없음
- 근거 범위: `research/reports/toss-transaction-history-api-contract-audit.md`(이하 **감사 보고서**),
  `docs/agent-tasks/broker-transaction-history-import.md`(이하 **선행 설계**), 그리고 저장소 현재 코드.
  외부 API를 호출하지 않았고, `.env`·`application-local.yml`·자격 증명을 읽지 않았다.
- 이 문서에 **토스증권 엔드포인트·필드명을 새로 만들어 적지 않았다.** 감사 보고서가 확정한 계약만 인용하고,
  그 밖은 전부 "미확인"으로 표시했다.

---

## 0. 한 줄 결론

**"거래 내역 가져오기"를 "주문 이력 가져오기"로 재정의하면 설계가 성립한다.**
`GET /api/v1/orders`는 체결 원장이 아니라 주문 원장이지만, Trade Guide의 보유·평단 계산이
쓰는 정보량은 주문 단위 집계로 **손실 없이 표현된다**(§2.3에서 증명). 따라서 선행 설계가 걸어 둔
착수 금지 조건(U-6 체결 고유 식별자 부재)은 **설계 단위 변경으로 해소 가능하다.**

다만 원장에 쓰는 단계(P3)는 여전히 열 수 없다. 남은 차단 사유는 U-6이 아니라
**N-1(앱에서 낸 주문이 응답에 포함되는가)**과 **U-15(약관)**, 그리고 **고정 IP 확보**다.
이 중 N-1은 코드를 한 줄도 쓰지 않고 사용자가 직접 1회 확인할 수 있으므로(§5),
**N-1 확인을 P1 착수 전 관문으로 두고, 그 뒤 P1~P2(원장 미변경)를 진행하는 것**을 권한다.

---

## 1. 이름부터 바꾼다 (계약 변경 1)

선행 설계의 `broker-transaction-imports`, `BrokerTransactionHistoryProvider`,
`external_transaction_id`, `BROKER_TRANSACTION_HISTORY`는 모두 **체결(transaction) 단위 충실도를
주장하는 이름**이다. 제공자가 주는 것은 주문 단위 집계이므로 이름이 계약을 과장한다.

| 선행 설계 이름 | 이 설계의 이름 | 이유 |
| --- | --- | --- |
| `BrokerTransactionHistoryProvider` | `BrokerOrderHistoryProvider` | 주문 원장임을 인터페이스 이름에 남긴다 |
| `BrokerTransactionRecord` | `BrokerOrderRecord` | 1건 = 주문 1건 |
| `external_transaction_id` | `external_order_id` | 체결 ID가 아님을 컬럼 이름에 남긴다 |
| `.../broker-transaction-imports` | `.../broker-order-imports` | API 경로도 동일 원칙 |
| `TradeTransactionSource.BROKER_TRANSACTION_HISTORY` | `TradeTransactionSource.BROKER_ORDER_HISTORY` | 원장 행의 출처 라벨 |

이름 하나가 나중에 "왜 개별 체결 시각이 없냐"는 질문을 매번 다시 만들지 않게 한다.
`TradeTransactionSource`는 문자열 컬럼이므로 값 추가에 DDL 변경이 없다(선행 설계 F 2.2 확인).

---

## 2. 주문 단위 멱등성 — 체결 ID가 없을 때

### 2.1 자연 키

```
UNIQUE (broker_account_id, external_order_id)
```

계좌 기준인 이유는 선행 설계 5.1과 같다. 링크는 나중에 다른 포트폴리오로 바뀔 수 있고,
같은 주문이 두 포트폴리오에 각각 들어가면 진짜 중복이 된다.

`orderId`는 감사 보고서 3.3에서 `Order`의 required 필드로 확정됐다. 문제는 **값의 재조회 안정성이
문서로 보장되지 않는다**는 것(N-3)이다. 여기서 두 가지를 분리해야 한다.

- **문서로 증명할 수 없다** — 맞다. 스펙에 안정성 문구가 없다.
- **런타임에 감지할 수 없다** — 틀리다. 아래 설계는 불안정성을 **자동으로 감지**한다.

### 2.2 지문(fingerprint)을 대체 키가 아니라 **교차 검증 값**으로 쓴다

선행 설계 D-3은 "콘텐츠 해시로 대체하면 같은 초의 분할 체결을 잘못 합친다"는 이유로
해시 대체를 기각했다. 그 판단은 유지한다. 다만 해시를 **키 대신**이 아니라 **키와 함께** 저장하면
기각 사유는 발생하지 않으면서 N-3을 감지 가능한 조건으로 바꿀 수 있다.

각 항목에 다음을 저장한다.

```
content_fingerprint = hash(side, symbol, currency, orderType,
                           status, filledQuantity, averageFilledPrice,
                           filledAmount, filledAt, orderedAt)
```

재조회 시 판정표:

| 상황 | 판정 | 동작 |
| --- | --- | --- |
| 같은 `orderId`, 같은 지문 | 정상 재조회 | `ALREADY_IMPORTED`로 표시하고 승인 대상에서 제외 |
| 같은 `orderId`, 다른 지문, **이전이 비종료 상태** | 주문 진행(부분 체결 → 종료 등) | 항목 값을 갱신한다. 원장 미반영 상태이므로 안전 |
| 같은 `orderId`, 다른 지문, **이전이 승인 완료** | 제공자 정정 | `SUPERSEDED` 후보로 표시. **원장은 건드리지 않고** 사용자 판단으로 넘긴다(§7) |
| 다른 `orderId`, 이미 승인된 항목과 지문 일치 | **`orderId` 불안정 의심** | `DUPLICATE_SUSPECTED`. 자동 반영 금지, 사용자에게 두 건을 나란히 제시 |

마지막 행이 핵심이다. `orderId`가 재조회마다 바뀌는 제공자였다면, 이 규칙이 **원장 중복이 생기기
전에** 잡아낸다. 그리고 P2(원장 미변경 스테이징)를 같은 구간에 두 번 실행하는 것만으로
안정성 실측이 끝난다. 즉 **N-3은 P1 착수를 막는 미확인이 아니라 P2의 인수 조건**이다.

지문 충돌 위험: 같은 계좌에서 같은 종목·같은 방향·같은 수량·같은 평균가·같은 최종 체결 시각을 갖는
서로 다른 주문 두 건은 이론상 가능하다. 그러나 그 경우 판정은 "자동 반영"이 아니라 "사용자 확인"이므로
**오탐의 대가는 확인 한 번**이고, 미탐의 대가(원장 중복)를 막는 쪽으로 비대칭이 잡혀 있다.

### 2.3 부분 체결 집계는 보유 계산에 대해 무손실이다 (핵심 근거)

감사 보고서 3.3·3.4는 부분 체결이 여러 번 일어나도 응답에는
`filledQuantity`(합계), `averageFilledPrice`(평균), `filledAt`(**최종** 1개)만 온다고 확정했다.
개별 체결 시각·개별 체결가는 복원 불가다. 선행 설계는 이를 "평균가로 뭉개진다"고 서술했는데,
**현재 Trade Guide 계산기에 한정하면 정보 손실이 없다.**

`HoldingCalculator`(직접 확인, `src/main/java/com/tradeguide/service/holding/HoldingCalculator.java`)의
매수 규칙은 다음과 같다.

```
평단 = (기존 수량 * 기존 평단 + 체결가 * 수량 + 수수료) / (기존 수량 + 수량)
```

주문 1건이 `p₁q₁, p₂q₂, …, pₙqₙ`으로 분할 체결되고 총 수수료가 `F`라면,
개별 체결 n행을 넣었을 때의 원가 기여분은 `Σ(pᵢqᵢ) + F`이다.
집계 1행을 넣었을 때의 기여분은 `averageFilledPrice × filledQuantity + F`이고,
`averageFilledPrice = Σ(pᵢqᵢ) / Σqᵢ`, `filledQuantity = Σqᵢ`이므로 **두 값은 같다.**
매도는 수량만 차감하므로 역시 동일하다.

따라서 집계로 잃는 것은 **개별 체결 시각뿐**이며, 그 값은 현재 도메인 어디에서도 쓰이지 않는다.
필요해지는 시점은 실현손익·세무 로트 도메인이 생길 때이고, 그건 이미 별도 과제다(선행 설계 D-6).

**단서 두 가지.**

1. `averageFilledPrice`의 소수 자릿수 상한이 문서화되어 있지 않다(감사 U-11).
   반올림이 있으면 `averageFilledPrice × filledQuantity ≠ filledAmount`가 될 수 있다.
   → **`filledAmount`를 스테이징에 함께 저장하고, 괴리가 허용 오차를 넘으면 항목을 검토 대상으로 표시한다.**
   원장에는 제공자가 보고한 `averageFilledPrice`를 그대로 넣는다(증권사 화면과 값이 일치해야 한다).
   자체 재계산으로 조용히 보정하지 않는다. 허용 오차 값은 승인 필요 항목이다(**D-N4**).
2. 위 등식은 `fee`에 **총 수수료**가 들어간다는 전제다. `execution.commission`이 총액이라는 점은
   감사 보고서 3.6에서 확정됐으므로 전제는 성립한다.

### 2.4 종료 상태 판정 — `PARTIAL_FILLED`를 원장에 넣지 않는다

감사 보고서 3.1이 확정한 그룹 매핑에서 `PARTIAL_FILLED`는 `OPEN`과 `CLOSED` **양쪽에 나타난다.**
그리고 "이 주문은 더 이상 체결되지 않는다"를 알려 주는 필드가 없다.
`CLOSED`로 조회됐다는 사실만으로 종료를 단정할 근거가 스펙에 없다.

규칙:

```
원장 반영 대상 = status ∈ {FILLED, CANCELED, REJECTED, REPLACED}
                 AND filledQuantity > 0
                 AND averageFilledPrice != null
                 AND filledAt != null
```

`PARTIAL_FILLED`는 **어느 그룹에서 왔든 원장에 넣지 않고** `PENDING_SETTLEMENT`로 보류한다.
`CANCELED`·`REJECTED`·`REPLACED`는 `filledQuantity > 0`이면 그 체결분이 실재하므로 포함한다
(감사 3.5 확정). 상태만 보고 버리면 실제 체결분을 잃는다.

보류가 무한정 길어지지 않는 근거: `timeInForce` enum은 `DAY`·`CLS`·`OPG` 세 값뿐이고
(감사 3.3), 셋 다 **단일 세션 유효 조건**이다. GTC류가 없으므로 부분 체결 주문은
당일 세션 종료와 함께 종료 상태로 전이한다고 기대할 수 있다.
**단 이것은 enum 목록에서 끌어낸 추론이지 스펙의 보장이 아니다.** 그러므로 코드는 이를 구조적으로
가정하지 않고, 일정 일수 이상 `PENDING_SETTLEMENT`로 남은 항목을 **경고로 노출**한다.
경고 임계 일수는 승인 필요 항목이다(**D-N5**).

### 2.5 `CANCEL_REJECTED` / `REPLACE_REJECTED`는 제외한다

감사 3.5는 이 둘이 **별도 주문 레코드로 생성**되고 원주문은 이전 상태로 복귀한다고 확정했다.
이들은 거래가 아니라 취소·정정 **요청이 거부됐다는 제어 기록**이다.
문제는 이 레코드가 자체 `execution` 값을 어떻게 채우는지가 문서에 없다는 것이다.
원주문의 체결 집계를 복제한다면 합산 시 **이중 계상**이 된다.

규칙: `CANCEL_REJECTED`, `REPLACE_REJECTED` 레코드는 **원장 반영 대상에서 제외하고 건수를 보고한다**
(`controlRecordCount`). 조용히 버리지 않는다. 이 레코드의 `execution` 값 실제 형태는
P2에서 실측으로 확인할 항목이다(**N-6**, 새로 발견).

### 2.6 `REPLACED` 체인 — 자동 복원하지 않는다

감사 3.5·U-9가 확정했듯 원주문과 대체 주문을 잇는 필드가 `Order` 스키마에 **없다.**
선행 설계 7.2의 `superseded_by_item_id` 자동 채움은 **응답만으로 불가능하다.**

그러나 원장 정확성 관점에서는 이 결손이 선행 설계가 우려한 만큼 치명적이지 않다.
`REPLACED` 원주문의 `filledQuantity`는 대체되기 전에 실제로 체결된 수량이고,
대체 주문의 `filledQuantity`는 그 이후 체결된 수량이다. 둘 다 실재하는 체결이므로
**둘을 모두 원장에 넣는 것이 옳다.** 잇는 링크는 감사·표시용 편의일 뿐 계산에 필요 없다.

**단, 이 해석 역시 스펙의 명시가 아니라 상태 설명에서 끌어낸 읽기다.**
대체 주문이 원주문 체결분을 다시 포함해 보고한다면 이중 계상이 된다.
→ 원장 반영(P3) 전에 실측으로 확인해야 하는 항목이다(**N-7**, 새로 발견).
확인 전까지는 §4.3의 스냅샷 대조가 이 오류를 잡는 안전망 역할을 한다.

`superseded_by_item_id`는 **P1~P3 범위에서 제거**하고, 정정 체인은 P4에서 사용자 판단으로만 만든다.

### 2.7 기간 경계와 커서

- `from`/`to`는 `filledAt`이 아니라 **`orderedAt` 기준**이다(감사 3.4 확정).
  미국 시장은 KST로 보면 주문일과 체결일이 **일상적으로 하루 어긋난다**(KST 23:30 주문 → 익일 새벽 체결).
  → 사용자가 요청한 체결 기간의 **앞뒤로 최소 2일씩 넓혀** `orderedAt` 구간을 조회한 뒤,
  `filledAt` 기준으로 걸러 표시한다. 중복은 `orderId` dedupe가 흡수한다.
- API 요청 파라미터는 `orderedFrom`/`orderedTo`처럼 **무엇 기준인지 이름에 드러낸다**(계약 변경).
  사용자 화면에는 "증권사는 주문일 기준으로 조회합니다. 경계일 체결은 구간을 넓혀 다시 조회하세요"를 명시한다.
- `status=CLOSED` + `from`/`to` + 커서 순회만 백필 경로로 쓴다.
  `status=OPEN`은 **원장에 넣지 않고**, "진행 중 주문 N건(원장 미반영)"이라는 표시 전용 정보로만 쓴다.
  `OPEN`은 `limit`·`cursor`가 무시되고 전량 반환되므로(감사 3.2) 호출 1회로 끝난다.
- 커서 유효기간·순회 중 데이터 변경 시 동작은 미확인(N-4)이다.
  → 순회 중 401·커서 오류가 나면 **커서를 이어붙이지 않고 해당 구간을 `from`부터 재시작**한다.
  유니크 키와 dedupe가 재시작을 안전하게 만든다.

---

## 3. 수동 기록과의 공존

`trade_transactions`에는 외부 식별자가 없고(선행 설계 F 2.2), 사용자가 이미 손으로 입력한 매매와
증권사가 보고하는 같은 주문을 **키로 이을 방법이 없다.** 자동 병합은 불가능하다.

설계 원칙은 선행 설계 4.2를 그대로 유지한다. **가져오기는 `MANUAL` 행을 수정·삭제·병합하지 않는다.**

그 위에 감지 장치를 하나 더한다.

**중복 의심 감지(`MANUAL_OVERLAP_SUSPECTED`)** — 스테이징 단계에서 각 항목에 대해
같은 포트폴리오의 `source = MANUAL` 행 중 다음을 모두 만족하는 것을 찾는다.

- `market`, `ticker`, `tradeType` 동일
- `quantity` 동일(허용 오차 없이 정확히 일치)
- `tradedAt`이 항목 `filledAt`과 같은 KST 달력일

일치가 있으면 항목을 `MANUAL_OVERLAP_SUSPECTED`로 표시하고, 승인 화면에 두 행을 나란히 보여 준다.
사용자 선택지는 셋이다.

1. 이 항목을 건너뛴다(수동 기록 유지)
2. 그대로 반영한다(수동 기록과 별개 거래였다)
3. 먼저 수동 기록을 기존 삭제 API로 지운 뒤 다시 승인한다

**자동 선택하지 않는다.** 기본 동작(1 또는 2)과 대조 창(같은 날 / ±1일)은 승인 필요 항목이다(**D-N1**).

---

## 4. 개시 잔고 스냅샷과의 공존

### 4.1 두 출처는 구조적으로 양립하지 않는다

`BROKER_OPENING_BALANCE` 행은 **승인 시각(오늘)에 현재 평단·현재 수량으로 만든 합성 매수**다
(선행 설계 F 2.4 확인). 즉 그 한 행이 **과거 매매 이력 전체를 이미 요약하고 있다.**
여기에 실제 주문 이력을 더하면 두 가지가 동시에 깨진다.

1. **이중 계상** — 같은 보유분이 합성 매수 + 실제 매수로 두 번 잡힌다.
2. **정렬 붕괴** — `HoldingCalculator`는 `tradedAt` 오름차순 재생이므로, 오늘 날짜 합성 매수보다
   **앞선 과거 매도**가 들어오면 `IllegalArgumentException("매도 수량이 보유 수량보다 많습니다.")`가
   발생하고 **포트폴리오의 보유 목록 조회 전체가 실패한다.** 소프트 에러가 아니라 화면 장애다.

### 4.2 권장: (A′) 거부 + 안내된 순차 교체

선행 설계 D-1의 (A)를 유지하되, 사용자가 막다른 길에 갇히지 않도록 절차를 명시한다.

- 활성 `BROKER_OPENING_BALANCE` 승인이 있는 포트폴리오에서 주문 이력 **승인 요청**은 `409`로 거부한다.
- 오류 응답에 기계 판독 가능한 코드와 **취소해야 할 개시 잔고 승인 ID 목록**을 담는다
  (계좌번호·비밀값은 담지 않는다).
- 화면은 "개시 잔고를 취소하고 주문 이력으로 대체" 흐름을 **3단계 명시 확인**으로 안내한다:
  개시 잔고 취소 → 주문 이력 스테이징 → 승인. 각 단계는 별도 요청이다.
- **자동 취소하지 않는다.** 자동 원장 변경 금지 원칙(선행 설계 4.2)과 충돌한다.

**스테이징(P2)은 개시 잔고가 있어도 거부하지 않는다.** 원장을 바꾸지 않으므로 위험이 없고,
오히려 사용자가 교체 여부를 판단하려면 먼저 무엇이 들어올지 봐야 한다.
거부는 **승인 시점에만** 건다.

### 4.3 잘린 이력 문제와 그 해법 — 스냅샷 대조 (이 설계의 안전망)

조회 가능한 최대 과거 기간은 미확인이다(U-4). 이력 앞쪽이 잘리면 매수 없는 매도가 생겨
승인 시 `HoldingCalculator`가 실패하거나, 실패하지 않더라도 **보유 수량이 실제보다 적게** 나온다.
후자가 더 위험하다. 조용히 틀린다.

해법은 이미 구현된 기능을 재사용하는 것이다. Trade Guide는 `GET /api/v1/holdings` 기반
보유 종목 스냅샷을 이미 저장한다(`PortfolioBrokerHoldingSnapshotService`).

**대조 규칙 (`reconciliation`)**

```
기존 원장 + 스테이징 대상 전체를 HoldingCalculator로 메모리 재생
  → 재구성 보유 수량
최신 증권사 보유 스냅샷의 종목별 수량
  → 기준 수량
두 값을 종목별로 비교해 차이를 보고한다.
```

이 대조는 **P2에서, 원장을 한 행도 바꾸지 않고** 수행할 수 있다. 얻는 것이 크다.

- **U-4가 착수 차단 요인에서 내려온다.** 최대 조회 기간을 미리 알 필요가 없다.
  이력이 부족하면 대조 차이로 드러난다.
- **N-1(앱 주문 누락)이 자동으로 드러난다.** 앱으로 매매하는 사용자에게 앱 주문이 빠졌다면
  재구성 수량이 스냅샷보다 작게 나온다.
- **N-6·N-7(제어 레코드·정정 이중 계상)도 드러난다.** 이중 계상은 재구성 수량 초과로 나타난다.

즉 **하나의 대조가 세 개의 미확인 항목을 동시에 관측 가능하게 만든다.**
이 대조 결과를 P3(원장 반영) 착수의 인수 조건으로 삼는다.

차이가 0이 아닐 때의 처리:

- **자동 보정하지 않는다.** 차이를 메우는 합성 매수를 만드는 것은
  검증되지 않은 값을 원장에 넣는 것이고 프로젝트 원칙에 어긋난다.
- 승인은 기본적으로 **차단**하고, 종목별 차이(재구성 / 스냅샷 / 차이)를 그대로 보여 준다.
- 차이가 있어도 승인을 강행할 수 있게 할지는 승인 필요 항목이다(**D-N2**). 기본값은 **차단**을 권한다.

### 4.4 참고로 검토했으나 권하지 않는 선택지

**(D) 잘린 구간을 메우는 합성 개시 잔고 자동 생성** — 가장 이른 반영 주문 직전 시각에
차이 수량만큼 합성 매수를 만든다. 재생 실패와 수량 부족을 한 번에 해결하지만,
**취득 단가를 알 수 없다.** 임의 단가를 넣으면 평단이 조용히 틀리고, 그 틀린 평단이
전략 가이드 입력이 된다. 승인 없이 채택하지 않는다. 필요하다면 별도 결정으로 올린다(**D-N3**).

---

## 5. N-1(앱에서 낸 주문 가시성)이 구현을 막는가

**결론: P0 관문이지 설계 차단 요인이 아니다. 다만 P1 착수 전에 반드시 해소해야 한다.**

근거를 나눠 본다.

- **설계 관점**: 앱 주문이 포함되든 안 되든 어댑터·스테이징·멱등성·대조 설계는 **한 글자도 바뀌지 않는다.**
  달라지는 것은 결과의 완전성뿐이다. 따라서 N-1은 아키텍처 리스크가 아니다.
- **데이터 무결성 관점**: P1·P2는 원장을 바꾸지 않는다. 앱 주문이 빠진 채 스테이징이 돌아도
  **원장은 오염되지 않고**, §4.3 대조가 누락을 즉시 드러낸다. 따라서 P2까지는 안전하다.
- **투자 대비 효과 관점**: 여기가 진짜 쟁점이다. 앱 주문이 빠진다면 이 기능은
  **API로 낸 주문만 보이는 기능**이 되고, 사용자는 앱으로 매매하므로 사실상 쓸모가 없다.
  P1~P2 구현 노력이 통째로 매몰된다.

그래서 순서를 이렇게 권한다.

**P0 관문 (코드 없음, 사용자 1회 수행)**

1. 배포·개발 환경의 고정 IP를 WTS `설정 > Open API > 허용 IP 관리`에 등록한다.
   (등록 없이는 모든 호출이 403이다 — 감사 4.1 확정)
2. 같은 화면에서 Open API 이용약관 문구를 확인해 **U-15**를 해소한다.
3. 앱에서 낸 주문이 하나라도 있는 기간으로 `GET /api/v1/orders?status=CLOSED`를 **직접 1회 호출**해
   그 주문이 응답에 있는지 확인한다 → **N-1 해소.**
4. 같은 호출을 **두 번** 실행해 `orderId` 값이 동일한지 대조한다 → **N-3 1차 관측.**
5. `from`을 계좌 개설 시점 근처까지 밀어 보고 가장 오래된 반환 건의 날짜를 기록한다 → **U-4 실측.**

3번이 "포함되지 않는다"로 나오면 **P1을 시작하지 않는다.** 이 설계 문서는 그대로 보존하되,
기능은 보류한다. 대안(웹소켓)은 없다 — 감사 7장이 백필 대안이 아님을 확정했다.

**주의**: 이 확인은 사용자 본인 계좌·본인 자격 증명으로 수행한다.
에이전트는 자격 증명을 요구하지 않고, 결과 값(포함 여부·날짜·동일 여부)만 전달받는다.

---

## 6. 필요한 API 계약 변경

선행 설계 9장 대비 변경점만 적는다. 인증·소유권 검사는 기존 `MemberAccessService.requireMemberAccess`
그대로다.

### 6.1 경로

```text
POST   /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports
GET    /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports
GET    /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}
POST   /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}/approval
DELETE /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}/approval
```

`/api/members` 유지 이유는 선행 설계 F 2.5와 같다(현재 구현 일관성). 경로 이관은 별도 과제다.
대조 결과는 **별도 엔드포인트를 만들지 않고** `GET .../{runId}` 응답에 포함한다.

### 6.2 요청

```jsonc
// POST .../broker-order-imports
{ "orderedFrom": "2026-01-01", "orderedTo": "2026-09-08" }

// POST .../{runId}/approval
{}   // D-8(A) 배치 전체 승인 유지. 단 D-N1 결과에 따라 제외 항목 ID 목록이 필요할 수 있다
```

`orderedFrom`/`orderedTo`로 이름을 바꾸는 것이 계약 변경의 핵심이다.
`from`/`to`라고 두면 프론트엔드와 사용자가 체결일 기준으로 오해한다(§2.7).

### 6.3 응답 — 제외 건수를 쪼갠다

선행 설계는 `unsupportedMarketCount`, `unsupportedTypeCount` 둘만 뒀다.
주문 원장에서는 제외 사유가 훨씬 다양하고, **사유별로 사용자 행동이 다르다.**
"조용한 누락 금지" 원칙(F 2.1)을 지키려면 사유별로 나눠야 한다.

| 필드 | 의미 | 사용자에게 뜻하는 것 |
| --- | --- | --- |
| `fetchedCount` | 제공자가 반환한 주문 수 | — |
| `stagedCount` | 승인 후보 | 반영될 건수 |
| `notFilledCount` | `filledQuantity = 0` | 체결되지 않은 주문. 정상 제외 |
| `pendingSettlementCount` | `PARTIAL_FILLED` 보류 | 나중에 다시 조회하면 들어온다 |
| `controlRecordCount` | `CANCEL_REJECTED`/`REPLACE_REJECTED` | 거래가 아닌 제어 기록 |
| `missingExecutionTimeCount` | `filledAt`이 null인데 체결분 있음 | **확인 필요.** 시각을 합성하지 않는다 |
| `unsupportedMarketCount` | 시장 추론 불가/KR | 현재 US만 지원 |
| `unsupportedCurrencyCount` | USD 아님 | 현재 USD만 지원 |
| `unknownEnumCount` | 미지의 enum 값 | 제공자 스펙 변경 신호 |
| `amountMismatchCount` | `avg × qty` vs `filledAmount` 괴리 | 검토 필요 |
| `feeUnknownCount` | `commission`이 null | 취득원가가 수수료만큼 낮게 잡힌다 |
| `alreadyImportedCount` | 유니크 키 기준 기존 반영 | 재조회 정상 동작 |
| `manualOverlapSuspectedCount` | 수동 기록 중복 의심 | 사용자 판단 필요 |
| `duplicateSuspectedCount` | `orderId` 불안정 의심 | 사용자 판단 필요 |
| `reconciliation` | 종목별 재구성 vs 스냅샷 수량·차이 | 승인 가능 여부의 핵심 근거 |

합계가 `fetchedCount`와 맞아떨어져야 한다는 불변식을 테스트로 고정한다.

### 6.4 시장·통화 매핑 (계약 변경)

`Order`에는 `marketCountry`가 **없다**(감사 3.6 확정). `HoldingsItem`에는 있어서 기존
`TossSecuritiesHoldingsProvider`의 매핑을 **그대로 재사용할 수 없다.**

현재 범위가 US/USD 전용(D-7 A)이므로 규칙은 단순해진다.

```
currency == "USD" 그리고 symbol이 영문·점·하이픈만으로 구성  → Market.US
symbol이 숫자 6자리                                        → KR (범위 밖, 제외 + 건수)
그 외 / currency가 USD 아님                                → 제외 + 건수
```

이 매핑은 **스펙이 보장하지 않는 추론**이다. 그래서 애매한 건을 통과시키지 않고 제외 쪽으로 편향시킨다.
US 전용 범위가 이 결손의 심각도를 낮춰 준다 — 판정이 사실상 `currency == USD` 검사로 수렴한다.

### 6.5 수수료·세금 (계약 변경)

`trade_transactions`에는 `fee`만 있고 `tax` 컬럼이 없다(F 2.2 확인).

- `fee ← execution.commission`. `tax`는 **스테이징에만 저장하고 원장에 넣지 않는다.**
  `fee`에 합산하면 매수 취득원가의 의미가 조용히 바뀐다(선행 설계 4.6).
- 결과: 매수 건에 세금이 붙어 있으면 그만큼 취득원가에서 빠진다.
  → 매수 세금이 0이 아닌 건수를 **반드시 보고**한다. 조용히 넘기지 않는다.
- `commission`이 null인 건은 `fee = 0`으로 두되 `feeUnknownCount`로 보고한다.
  체결이 실재하는데 수수료 미상이라는 이유로 거래를 버리는 것이 더 나쁘다.
- 위 두 처리 방식 모두 승인 필요 항목이다(**D-N6**).

### 6.6 시각

`orderedAt`·`filledAt`은 ISO 8601 + KST 오프셋(`+09:00`)이고 날짜만 오는 형태가 아니다(감사 3.4 확정).
따라서 `Instant` 변환이 모호하지 않고, `trade_transactions.traded_at`이
`TIMESTAMP(6) WITH TIME ZONE`이므로 타입도 맞는다.
선행 설계 D-5(날짜만 줄 때 보류)는 **문제 자체가 발생하지 않아 무효**다.

다만 `filledAt`이 null인데 체결분이 있는 건은 `settlementDate`로 대체하지 않는다.
결제일은 체결 시각이 아니다. 제외 + 건수 보고한다.

### 6.7 예외 매핑

기존 `GlobalExceptionHandler` 패턴을 따른다(선행 설계 F 2.5).

| 예외 | 상태 | 용도 |
| --- | --- | --- |
| `BrokerCapabilityUnsupportedException` | `422` | 제공자가 `TRANSACTION_HISTORY_IMPORT` 미선언 |
| `BrokerOrderImportConflictException` | `409` | 활성 개시 잔고, 초과 매도, 대조 불일치, 중복 승인 |
| `BrokerOrderImportUnprocessableException` | `422` | 비활성 상장, 미지원 통화·시장 |
| `BrokerOrderImportNotFoundException` | `404` | run 없음 |
| 기존 `BrokerConnectionUnavailableException` | `503` | 제공자 호출 실패·레이트 리밋 |

`409`는 **사유 코드를 본문에 담아** 프론트엔드가 분기할 수 있게 한다
(`ACTIVE_OPENING_BALANCE`, `RECONCILIATION_MISMATCH`, `OVERSOLD`, `ALREADY_APPROVED`).
증권사 원문 오류 메시지는 담지 않는다.

---

## 7. 데이터 모델 후보

### 후보 1 — 항목 1행이 주문 1건을 계속 추적 (mutable projection)

`broker_order_import_items`에 `UNIQUE (broker_account_id, external_order_id)`를 걸고,
재조회 시 같은 행을 갱신한다. 실행(run)과는 다대다 또는 `first_seen_run_id`/`last_seen_run_id`로 잇는다.

- 장점: 재조회·지문 대조가 단순하다. 행 수가 주문 수와 같다.
- 단점: 행이 변한다. 승인 시점 값을 따로 얼려 두지 않으면 **감사 이력이 사라진다.**
  저장소의 기존 원칙(`PortfolioBrokerHoldingImport`가 승인 시점 값을 복사해 보존)과 어긋난다.

### 후보 2 — 실행별 불변 항목 + 별도 반영 링크 테이블 **(권장)**

세 테이블로 나눈다.

**`broker_order_import_runs`** — 실행 감사

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `portfolio_id` / `broker_connection_id` / `broker_account_id` | BIGINT FK | 계좌 행은 삭제하지 않고 `DETACHED` (F 2.4) |
| `provider` | VARCHAR | enum 문자열 |
| `requested_ordered_from` / `requested_ordered_to` | DATE | 사용자가 요청한 구간 |
| `queried_ordered_from` / `queried_ordered_to` | DATE | 경계 보정으로 실제 조회한 구간(§2.7) |
| `started_at` / `finished_at` | TIMESTAMP(6) | |
| `status` | VARCHAR | `RUNNING`, `STAGED`, `APPROVED`, `REVOKED`, `FAILED` |
| 각종 `*_count` | INT | §6.3 표 그대로 |
| `reconciliation_status` | VARCHAR | `MATCHED`, `MISMATCHED`, `NOT_AVAILABLE`(스냅샷 없음) |
| `failure_code` | VARCHAR NULL | **정제된 코드만** |
| `provider_request_id` | VARCHAR NULL | 실패 시 `X-Request-Id`. §9에서 근거 설명 |
| `executed_by_member_id` | BIGINT | |

**`broker_order_import_items`** — 실행 시점 주문 스냅샷 (불변)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `run_id` | BIGINT FK | |
| `external_order_id` | VARCHAR NOT NULL | 주문 식별자 |
| `content_fingerprint` | VARCHAR NOT NULL | §2.2 |
| `market` / `ticker` / `display_name` | VARCHAR | `display_name`은 주문 응답에 없으므로 자산 카탈로그에서 채운다 |
| `order_side` | VARCHAR | `BUY`/`SELL` |
| `order_status` | VARCHAR | 원본 10종 보존 |
| `order_type` / `time_in_force` | VARCHAR | 원본 보존 |
| `ordered_quantity` | NUMERIC(19,6) | **주문 수량**. 원장에 쓰지 않는다 |
| `filled_quantity` | NUMERIC(19,6) | **원장 수량은 이 값** |
| `average_filled_price` | NUMERIC(19,4) | **평균가**임을 컬럼 주석에 남긴다 |
| `filled_amount` | NUMERIC(19,4) NULL | 괴리 검증용(§2.3) |
| `commission` / `tax` | NUMERIC(19,4) NULL | `tax`는 계산 미반영 |
| `currency_code` | VARCHAR(3) | |
| `ordered_at` / `filled_at` | TIMESTAMP(6) WITH TIME ZONE | 원장과 같은 타입 |
| `settlement_date` | DATE NULL | 참고용 |
| `staging_status` | VARCHAR | `STAGED`, `SKIPPED_NOT_FILLED`, `PENDING_SETTLEMENT`, `SKIPPED_CONTROL_RECORD`, `SKIPPED_UNSUPPORTED`, `ALREADY_IMPORTED`, `MANUAL_OVERLAP_SUSPECTED`, `DUPLICATE_SUSPECTED` |
| `skip_reason_code` | VARCHAR NULL | 사유별 표시용 |

인덱스: `UNIQUE (run_id, external_order_id)`, `INDEX (run_id, filled_at)`.

**`broker_order_ledger_links`** — 원장 반영 사실 (멱등성의 단일 지점)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `broker_account_id` | BIGINT FK | |
| `external_order_id` | VARCHAR NOT NULL | |
| `run_id` / `item_id` | BIGINT | 승인 근거 |
| `trade_transaction_id` | BIGINT NULL | **FK 없음** (F 2.4 선례). 취소 후에도 값 보존 |
| `approved_quantity` / `approved_price` / `approved_fee` | NUMERIC | 승인 시점 값 동결 |
| `approved_traded_at` | TIMESTAMP(6) WITH TIME ZONE | |
| `approved_by_member_id` | BIGINT | |
| `approved_at` / `revoked_at` | TIMESTAMP(6) | |
| `status` | VARCHAR | `ACTIVE`, `REVOKED` |

```
UNIQUE (broker_account_id, external_order_id) WHERE status = 'ACTIVE'
```

부분 유니크 인덱스로 두면 **취소 후 재승인이 가능**하면서 동시 반영은 막힌다.
부분 인덱스를 쓰지 않겠다면 전체 유니크로 두고 재승인을 금지하는 선택도 가능하다(**D-N7**).

- 장점: 항목 행이 불변이라 감사가 온전하다. 멱등성 제약이 가장 작고 안정적인 테이블에 있다.
  항목 행을 나중에 정리(purge)해도 링크는 남아 중복 반영이 계속 막힌다.
- 단점: 테이블 하나가 더 늘고, `ALREADY_IMPORTED` 판정에 조인이 필요하다.

### 후보 3 — 단일 테이블, 조회 시점 중복 제거

기각한다. 멱등성이 DB 제약이 아니라 애플리케이션 로직으로 내려가고,
동시 요청에서 이중 반영을 막을 수단이 없다.

### 마이그레이션

- 최신 마이그레이션은 **`V12`**(직접 확인)이므로 다음은 **`V13`**이다.
- `V13__create_broker_order_imports.sql` 하나에 세 테이블·제약·인덱스를 만든다.
- `trade_transactions.source`는 문자열 컬럼이므로 값 추가에 DDL 변경이 없다.
- 기존 데이터 마이그레이션 없음. 과거 `MANUAL` 행을 소급 분류하지 않는다.

---

## 8. 제공자 중립 경계

증권사별 분기를 서비스 계층에 넣지 않는다. 어댑터는 HTTP·JSON·제공자 코드 체계만 안다.

```java
public interface BrokerOrderHistoryProvider {
    BrokerProvider getProvider();

    BrokerOrderHistoryPage fetchOrders(
            String clientId,
            String clientSecret,
            String accountSequence,
            BrokerOrderHistoryQuery query
    );
}
```

```java
record BrokerOrderHistoryQuery(LocalDate orderedFrom, LocalDate orderedTo,
                               String cursor, int pageSize) {}

record BrokerOrderHistoryPage(List<BrokerOrderRecord> records,
                              String nextCursor,
                              boolean hasNext,
                              BrokerOrderExclusionCounts exclusions) {}
```

`BrokerOrderRecord`는 §7 항목 테이블의 필드를 제공자 중립 타입으로 옮긴 것이다.
제공자 원본 상태 문자열(`providerStatusCode`)을 보존하되, 도메인은 중립 enum
(`BrokerOrderLifecycle`: `NOT_FILLED`, `PARTIALLY_FILLED_OPEN`, `TERMINAL_WITH_FILL`,
`TERMINAL_WITHOUT_FILL`, `CONTROL_RECORD`, `UNKNOWN`)으로 판정한다.
서비스는 원본 문자열을 해석하지 않는다.

경계 규칙:

1. 어댑터는 `TradeTransaction`, `Portfolio`, `HoldingCalculator`를 참조하지 않는다.
2. `BrokerProviderRegistry`에 `requireOrderHistoryProvider(provider)`를 추가하고,
   **동시에 기존 `BrokerHoldingsProvider` 조회도 레지스트리로 옮겨** 조회 경로 이원화를 해소한다
   (선행 설계 F 2.1이 지적한 갈라짐).
3. 제공자가 `TRANSACTION_HISTORY_IMPORT`를 선언하지 않으면 API가 `422`로 즉시 거부한다.
   `BrokerProvider.TOSS_SECURITIES`에 이 capability를 선언하는 것은
   **N-1·U-15가 해소되고 P2 대조가 통과한 뒤**다.
4. 미지원 시장·통화·유형·미지의 enum은 버리지 않고 **사유별 건수로 보고**한다.
5. 어댑터는 자격 증명·토큰·복호화된 `accountSequence`·원문 응답을 저장·로그·예외 메시지에 넣지 않는다.

---

## 9. 보안·감사 경계

### 9.1 저장하지 않는 것

자격 증명(`clientId`/`clientSecret`), 액세스 토큰, 복호화된 `accountSequence`,
계좌번호 원문, 증권사 원문 응답 본문, 증권사 원문 오류 메시지.
API 응답·로그·예외 메시지·감사 테이블 어디에도 넣지 않는다.

### 9.2 의도적 예외 하나 — `provider_request_id`

실패한 실행에 한해 응답 헤더 `X-Request-Id` 값을 저장할 것을 권한다.
이 값은 비밀값도 개인정보도 아닌 **불투명한 요청 상관 id**이며,
증권사에 장애를 문의할 때 유일하게 쓸 수 있는 근거다.
저장하지 않으면 재현 불가능한 실패를 영원히 추적할 수 없다.
판단이 갈릴 수 있는 항목이라 명시해 둔다(**D-N8**).

### 9.3 레이트 리밋과 토큰

- `ORDER_HISTORY`는 초당 5회이고 **사전 공지 없이 조정될 수 있다**(감사 4.2).
  한도 수치를 코드 상수로 두지 않고 `X-RateLimit-Limit`·`X-RateLimit-Remaining`을 읽어 조절한다.
  429는 `Retry-After` + 지수 백오프(1s→2s→4s) + jitter로 재시도한다.
- **토큰 단일화는 이미 해결됐다.** 감사 4.3·N-5가 지적한 "호출마다 새 토큰 발급" 결함은
  현재 작업 트리의 `TossSecuritiesAccessTokenIssuer`가 자격 증명별 캐시와 발급 직렬화로 해소한 상태이고,
  `TossSecuritiesAccessTokenIssuerTest`도 존재한다.
  **다만 이 변경은 아직 커밋되지 않았다**(`git status` 기준 수정 상태).
  다중 페이지 순회의 전제 조건이므로 **P1 착수 전에 커밋으로 확정**해야 한다.
- 순회 중 401이 나면 캐시를 무효화하고 재발급한 뒤 **같은 커서를 1회만** 재시도한다.
  실패하면 커서를 버리고 해당 구간을 처음부터 다시 조회한다(N-4 미확인 대응, §2.7).

### 9.4 배포 요건

허용 IP 미등록 시 모든 호출이 403이다(감사 4.1 확정). 동적 IP 개발 PC·서버리스·오토스케일에서는
기능이 상시 실패한다. **코드로 해결할 수 없는 배포 전제**이며 P0 관문에 포함된다.

### 9.5 감사·접근 제어·보존

- 실행 감사에 남기는 것: 실행자, 실행 시각, 요청 구간, 실제 조회 구간, 사유별 건수,
  대조 결과, 정제된 실패 코드. 승인·취소에는 행위자와 시각.
- 접근 제어는 기존 `MemberAccessService.requireMemberAccess`를 그대로 쓴다.
  타 회원 포트폴리오 접근은 기존과 동일하게 차단한다.
- **보존 정책이 새로 필요하다.** 주문 이력은 금융 개인정보다.
  승인되지 않은 스테이징 항목을 무기한 보관할 이유가 없다.
  권장: 미승인 항목은 일정 기간 후 정리하고, `broker_order_ledger_links`는 보존한다
  (링크만 있어도 중복 반영은 계속 막힌다 — 후보 2를 권한 이유 중 하나).
  보존 기간은 승인 필요 항목이다(**D-N9**).

---

## 10. 단계별 구현·테스트 계획

| 단계 | 내용 | 원장 변경 | 착수 조건 |
| --- | --- | --- | --- |
| **P0** | 고정 IP 등록, U-15 약관 확인, **N-1 실측**, N-3 1차 관측, U-4 실측 (§5) | 없음 | 없음 |
| **P0.5** | 토큰 캐싱 변경 커밋 확정(§9.3) | 없음 | 없음 |
| **P1** | 제공자 중립 경계 + 토스 어댑터. 픽스처 테스트만. DB·원장 없음 | 없음 | **P0에서 N-1 "포함됨" 확인**, P0.5 완료 |
| **P2** | 실행/항목/링크 테이블, `POST`·`GET`, 사유별 건수, **스냅샷 대조**, 중복 의심 감지. 화면은 읽기 전용 | 없음 | P1 완료, D-N1·D-N2 결정 |
| **P3** | 원자적 승인·취소, 개시 잔고 `409`, 초과 매도 차단, 삭제 보호, 멱등 재시도 | **있음** | P2 대조 `MATCHED` 실측, N-6·N-7 해소, D-1·D-4·D-8·D-10·D-N3·D-N6·D-N7 결정 |
| **P4** | 정정 체인(사용자 판단), 비매매 이벤트, KR·통화 모델, 실현손익 | 있음 | 통화 모델 선행, D-6·D-7·D-9·D-11 결정 |

### 테스트 계획

**P1 — 어댑터 (픽스처만, 실계좌·실키 금지)**

- 문자열 decimal 파싱과 스케일 보존. `maxLength: 30` 경계값.
- 상태 10종 × `filledQuantity` 조합의 `BrokerOrderLifecycle` 판정표를 **전수 검증**한다.
  특히 `CANCELED`·`REJECTED`·`REPLACED` + `filledQuantity > 0`이 `TERMINAL_WITH_FILL`인지.
- `PARTIAL_FILLED`가 `OPEN`·`CLOSED` 어느 그룹에서 와도 `PENDING_SETTLEMENT`인지.
- 미지의 enum(`orderType`·`timeInForce`·`status`·`currency`)에서 파싱 실패가 나지 않고
  건수로 보고되는지.
- 커서 순회: 다중 페이지 수집, `hasNext=false` 종료, 같은 커서 반복 방지, `OPEN` 응답에서
  커서를 신뢰하지 않는지.
- 429 → `Retry-After` 존중, 401 → 토큰 무효화 후 1회 재시도 → 실패 시 구간 재시작.
- 오류 응답이 `BrokerConnectionUnavailableException`으로 변환되고
  **비밀값·원문 응답이 메시지에 없는지** 어서션.

**P2 — 스테이징 (원장 미변경)**

- 스테이징 실행이 `trade_transactions`를 **한 행도 바꾸지 않음**을 명시적으로 검증.
- 같은 구간 재실행 시 기존 반영 건이 `ALREADY_IMPORTED`로 분류되는지.
- 같은 `orderId` + 다른 지문(비종료 → 종료)에서 항목이 갱신되는지.
- 다른 `orderId` + 승인된 항목과 같은 지문에서 `DUPLICATE_SUSPECTED`가 뜨는지.
- 사유별 건수 합계 = `fetchedCount` 불변식.
- 수동 기록 중복 의심 감지: 같은 종목·방향·수량·같은 KST 달력일에서 탐지, 그 밖에서 미탐지.
- **집계 등가성 속성 테스트**: 같은 주문을 개별 체결 n행으로 넣은 원장과
  집계 1행으로 넣은 원장의 `HoldingCalculator` 결과(수량·평단)가 동일한지(§2.3).
- 스냅샷 대조: 일치 / 부족(이력 잘림) / 초과(이중 계상) 세 시나리오에서
  `reconciliation_status`가 각각 맞게 나오는지. 스냅샷 없을 때 `NOT_AVAILABLE`.
- 개시 잔고가 활성이어도 **스테이징은 성공**하는지(§4.2).

**P3 — 승인·취소 (원장 변경)**

- 승인 후 원장 행 수, `source = BROKER_ORDER_HISTORY`,
  `tradedAt = filledAt`, `quantity = filledQuantity`, `executedPrice = averageFilledPrice`,
  `fee = commission` 확인.
- 초과 매도 배치가 **전체 거부**되고 원장에 아무것도 남지 않는지(트랜잭션 원자성).
- 활성 개시 잔고가 있으면 `409` + 사유 코드 `ACTIVE_OPENING_BALANCE` + 취소 대상 ID 목록.
- 대조 불일치 시 `409` + `RECONCILIATION_MISMATCH` (D-N2가 차단으로 결정된 경우).
- 동시 승인: `PortfolioBrokerHoldingImportWriterTest`와 같은 구조로,
  **별도 빈**(`BrokerOrderImportWriter`)의 트랜잭션 경계에서
  `DataIntegrityViolationException` → 롤백 → 새 트랜잭션 재조회 → `200 OK` 복구.
  같은 빈 내부 호출은 프록시를 거치지 않아 경계가 성립하지 않는다는 점을 테스트로 고정.
- 취소가 원장 반영을 되돌리고 링크는 `REVOKED`로 보존되는지.
- 취소가 이후 매도를 초과 매도로 만들면 **삭제 전에 차단**되는지
  (기존 `deleteTradeTransaction`과 같은 순서).
- 일반 거래 삭제 API가 `BROKER_ORDER_HISTORY` 행을 `TradeTransactionProtectedException`으로 거부하는지.
- 증권사 연결 삭제 시 계좌 `DETACHED`·감사 이력 보존.

**공통 회귀**

- API 응답·로그·예외 메시지에 비밀값이 없음을 어서션.
- 타 회원 포트폴리오 접근 `403/404`.
- 기존 스냅샷·개시 잔고·수동 매매 API 회귀.
- `./gradlew test` 전체 + PostgreSQL 통합 테스트.
- 프론트엔드 작업 시 `npm run lint`, `npm run build`, 데스크톱과 360px 폭 확인(AGENTS.md).

---

## 11. 제품 승인이 필요한 결정

선행 설계 12장의 D-1~D-12는 그대로 유효하다. 이 설계가 근거를 더해 갱신하거나 새로 만든 것만 적는다.

### 갱신된 기존 결정

| # | 결정 | 이 설계의 권고 | 변경 근거 |
| --- | --- | --- | --- |
| D-1 | 개시 잔고 공존 | **(A′)** 승인만 `409`로 거부 + 안내된 순차 교체. 스테이징은 허용 | §4.2 |
| D-3 | 안정적 외부 식별자 부재 시 | **`orderId` 채택 + 지문 교차 검증**(선행 (A) 기능 미제공 대신) | §2.2 |
| D-5 | 날짜만 제공될 때 `tradedAt` | **무효.** `filledAt`이 KST 오프셋 포함 date-time으로 확정됨 | §6.6 |
| D-8 | 승인 단위 | **(A) 배치 전체 유지.** 단 D-N1 결과에 따라 "의심 항목 제외" 옵션이 필요할 수 있다 | §3 |

### 새로 필요한 결정

| # | 결정 | 선택지 | 권고 |
| --- | --- | --- | --- |
| **D-N1** | 수동 기록 중복 의심의 기본 처리와 대조 창 | (A) 기본 건너뛰기 / (B) 기본 반영 / 창: 같은 날 vs ±1일 | **(A) + 같은 KST 달력일.** 이중 계상보다 누락이 복구하기 쉽다 |
| **D-N2** | 스냅샷 대조 불일치 시 승인 | (A) 차단 / (B) 경고 후 강행 허용 | **(A) 차단.** 틀린 평단이 전략 입력이 되는 것을 막는다 |
| **D-N3** | 잘린 이력을 메우는 합성 개시 잔고 | (A) 만들지 않음 / (B) 사용자 입력 단가로 생성 / (C) 자동 생성 | **(A).** (C)는 임의 단가 합성이라 원칙 위배 |
| **D-N4** | `avg × qty` vs `filledAmount` 허용 오차 | 절대값 / 상대값 / 0 | 상대 오차 기준 권장, 수치는 실측 후 결정 |
| **D-N5** | `PENDING_SETTLEMENT` 경고 임계 일수 | 1영업일 / 3일 / 7일 | **1영업일.** TIF가 모두 단일 세션이므로 그 이상은 이상 신호 |
| **D-N6** | 세금·수수료 처리 | 세금: 원장 미반영 + 건수 보고 / `fee` 합산. 수수료 null: `fee=0` + 보고 / 항목 제외 | **미반영 + 보고**, **`fee=0` + 보고** |
| **D-N7** | 취소 후 재승인 허용 여부 | (A) 부분 유니크로 허용 / (B) 전체 유니크로 금지 | **(A) 허용.** 잘못 승인한 배치를 되돌린 뒤 다시 넣을 길이 필요하다 |
| **D-N8** | 실패 시 `X-Request-Id` 저장 | (A) 저장 / (B) 미저장 | **(A).** 비밀값이 아니고 장애 추적의 유일한 근거 |
| **D-N9** | 미승인 스테이징 항목 보존 기간 | 30일 / 90일 / 무기한 | 짧게 시작(30일) 권장. 링크 테이블은 영구 보존 |

---

## 12. 미확인 항목 정리

| # | 항목 | 상태 | 해소 방법 | 어느 단계를 막는가 |
| --- | --- | --- | --- | --- |
| N-1 | 앱에서 낸 주문 포함 여부 | **미확인 · 최우선** | 사용자가 본인 계좌로 1회 실측(§5) | **P1** |
| U-15 | 약관상 개인 계좌 이력 조회 허용 | **미확인** | WTS `설정 > Open API` 약관 문구 확인 | **P1** |
| 4.1 | 고정 IP 확보·등록 | **확정 요건** | 배포 환경 고정 IP를 허용 IP에 등록 | **P0 전부** |
| 4.3 | 토큰 단일화 | **해소됨(미커밋)** | 작업 트리 변경을 커밋으로 확정 | P1 |
| U-4 | 최대 조회 과거 기간 | **미확인 · 비차단** | 실측. 부족은 §4.3 대조가 드러낸다 | 없음 |
| U-3 | 정렬 순서 | **미확인 · 비차단** | 커서 순회 + dedupe로 무관해진다 | 없음 |
| N-3 | `orderId` 재조회 안정성 | **미확인 · 감지 가능** | §2.2 지문 대조. P2 재실행으로 관측 | **P3** |
| N-4 | 커서 유효기간·순회 중 변경 | **미확인 · 완화됨** | 401·오류 시 구간 재시작(§2.7) | 없음 |
| **N-6** | `CANCEL_REJECTED`/`REPLACE_REJECTED` 레코드의 `execution` 값 형태 | **미확인 (신규)** | P2 실측. 그때까지 제외 + 건수 보고 | **P3** |
| **N-7** | `REPLACED` 원주문과 대체 주문의 체결분 중복 보고 여부 | **미확인 (신규)** | P2 실측 + §4.3 대조 | **P3** |
| U-8 | 수수료·세금 세부 분해 | **확정: 없음** | 총액 2개만 저장(§6.5) | 없음 |
| U-10 | 환율 | **확정: 없음** | USD 그대로 저장. 원화 환산은 별도 정책 | 없음 |

---

## 13. 이 설계가 하지 않은 것

- 토스증권 API를 호출하지 않았다. 자격 증명·`.env`·`application-local.yml`을 읽지 않았다.
- `src/**`, `frontend/**`, `docs/**`, 마이그레이션, 설정, 테스트를 수정하지 않았다. 코드는 읽기만 했다.
- 스펙에 없는 엔드포인트·필드명·응답 스키마를 만들어 적지 않았다.
  감사 보고서가 확정하지 않은 것은 전부 "미확인" 또는 "추론"으로 표시했다.
- 커밋·푸시·병합하지 않았다.
- §11의 결정이 확정되기 전에는 이 설계를 구현 계약으로 취급하지 않는다.
  `docs/agent-tasks/broker-transaction-history-import.md`를 갱신하는 것은
  사용자가 이 설계를 수락한 뒤 별도 작업 계약에서 한다.

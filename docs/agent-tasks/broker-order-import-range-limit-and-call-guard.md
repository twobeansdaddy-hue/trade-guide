# S6 백엔드 계약 제안 — 조회 기간 상한·서버 구간 분할·중복 호출 보호

## 분리 안내 (S6a/S6b) — 2026-09-10

이 문서가 원래 하나로 묶었던 세 가지(§0의 1~3번) 중 **조회 기간 상한(E4, §3)과 중복 호출
보호(B2/D7, §6)만 S6a로 먼저 구현했다.** 서버 구간 분할(B3, §4)은 **S6b로 미룬다.**

- **완료(S6a)**: §1의 결정 1(366일 상한, 양 끝 날짜 포함)과 결정 3·4(5초 쿨다운, 즉시 409)를
  구현했다. §2.2 오류 계약 중 400(범위 초과)·409(`BROKER_CALL_IN_PROGRESS`)·429
  (`BROKER_CALL_COOLDOWN`) 세 가지가 대상이다. §6.4가 지적한 전제 조건(브로커 `RestClient`
  connect/read timeout)도 S6a에 포함해 함께 구현했다 — 이 전제 조건 없이 가드만 배포하면
  §9 최고 위험(무기한 잠금 점유)이 그대로 남기 때문이다.
- **미룸(S6b)**: §1의 결정 2(30일 분할 창), §4(서버 구간 분할 알고리즘), §4.4·§5의
  `covered_ordered_to` 컬럼과 응답 `coverage` 필드, §2.1의 응답 계약 확장. 366일 상한 안에서
  계좌가 초고밀도라 `PAGE_LIMIT_EXCEEDED`(422)에 걸리는 경우는 S6a 이후에도 오늘과 동일하게
  실패하고 사용자가 기간을 직접 좁혀야 한다(§4.3 세 번째 시나리오와 동일한 잔여 한계).

**미루는 이유.** 서버 구간 분할은 실행 하나가 "부분 커버"로 끝날 수 있다는 새 상태를
만든다(§4.4의 `coveredOrderedTo`, `fullyCovered=false`). 이 상태를 사용자가 어떻게 인지하고
이어받는지는 이 문서의 범위 밖에 있는 두 가지가 먼저 정해져야 한다.

1. **불완전 이력 승인 정책.** 부분 커버 실행에 담긴 주문은 실제로 체결된 주문이라 승인
   대상이지만(§4.4), "이 실행은 요청한 기간을 끝까지 보지 못했다"는 사실을 사용자가 승인
   시점에 알아야 하는지, 알아야 한다면 승인을 막을지 경고만 할지는 투자 데이터 정합성
   정책이지 API 설계로 대신 정할 수 없다. 이 정책이 없는 채로 구간 분할만 배포하면
   `fullyCovered=false`인 실행이 조용히 승인되는 경로가 생긴다.
2. **Claude UX 검토.** `nextOrderedFrom`으로 이어받는 흐름(§4.3 두 번째 시나리오)은
   화면에서 "어디까지 가져왔고 무엇을 다시 요청해야 하는지"를 사용자가 오해 없이 읽을 수
   있어야 실효성이 있다. 이 UX는 프론트엔드 작업이자 독립 검증 대상이므로, 정책이 정해진
   뒤 Claude의 시나리오 검토를 거쳐 설계를 굳힌다.
   담당 변경(2026-09-23): Antigravity 배제에 따라 Claude가 UX 검토를 맡는다. 교차 검증
   원칙상 S6b의 프론트엔드 구현은 Codex가 맡는다.

두 가지가 정해지기 전에는 §4의 알고리즘이 기술적으로는 동작해도 "부분 커버를 사용자가
실제로 안전하게 다룰 수 있는" 슬라이스가 아니다. 그래서 S6b는 이 문서의 §4·§4.4·§5·§8의
해당 테스트(2~7, 15, 16 일부)를 그대로 이어받되, 정책 문서화와 Claude UX 검토를 먼저
거친 뒤 별도 작업 계약으로 착수한다.

**S6a 실제 구현은 이 문서의 설계에서 다음을 조정했다** (실행 결과이지 사전 승인 대상은
아니다 — 운영 안전값 자체는 §1의 결정 그대로다):

- 가드 적용 위치를 §6.3의 컨트롤러 앞단이 아니라 **서비스 메서드 안**으로 옮겼다.
  `AGENTS.md`의 계층 원칙(Controller -> Service -> Repository -> Database)을 따르기 위해서다.
  컨트롤러가 각 엔드포인트의 유일한 진입점이라는 §6.3의 전제는 그대로 유효하므로 결과는
  동일하다.
- 쿨다운 기준 시각 갱신 시점을 호출 성공 이후로 통일했다. 미리보기(`HOLDING_PREVIEW`)는
  실패한 시도까지 기록하면 설정을 방금 고친 사용자가 남은 쿨다운 때문에 바로 재시도하지
  못하므로, 스냅샷 갱신(저장된 `syncedAt`이 성공 시에만 갱신되는 것)과 같은 규칙으로
  맞췄다. 주문 이력 가져오기(`ORDER_HISTORY_IMPORT`)는 원래 설계대로 실패한 시도도 DB에
  `startedAt`을 남기므로(§6.2) 실패 포함 쿨다운이 그대로 적용된다.
- 366일 상한을 "양 끝 날짜를 포함한 달력일 수"로 정의했다(`ChronoUnit.DAYS.between(...) + 1`).
  §3 원안의 `ChronoUnit.DAYS.between(...) > 366`은 양 끝을 포함하면 실질적으로 367일까지
  허용하는 결과였다. "366일(포함)까지, 367일이면 거부"는 Codex가 선택한 보수적 기본값이며,
  사용자나 증권사가 확인·승인한 제공자 제한이 아니다. 이 문서의 §3 코드 스니펫보다 이 조정이
  우선한다.

---

## Identity

- Task ID: `TASK-S6-RANGE-CHUNK-GUARD`
- Owner: `Claude`(이 설계 제안 및 S6a 구현) / `Codex`(독립 검토·검증, 보수적 기본값 선택)
- Work mode: `review` — 이 문서 자체는 `docs/agent-tasks/**`에만 쓴다. `src/**`, `frontend/**`는
  수정하지 않았고, 외부 증권사 API를 호출하지 않았다.
- 기준 문서: `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md` 7장의 S6(`B2, B3, E4(D7)`)
- 근거 범위: 이 저장소의 현재 코드·마이그레이션만 사용했다. 토스증권 문서에 없는 호출 빈도·조회
  기간 제한을 사실인 것처럼 가정하지 않았다. 아래에서 제안하는 상수는 전부 **우리가 정하는
  보수적 기본값**이며, "토스 정책상 제한"이 아니다.

## 0. 요약

S6은 세 가지를 닫는다.

1. **조회 기간 상한(E4)** — `BrokerOrderImportService.requireRange`가 시작일 ≤ 종료일만 보고
   폭 상한이 없다. 몇 년 치를 요청하면 `MAX_PAGES=50`에 걸려 실패할 때까지 증권사를 최대 50번
   호출한 뒤에야 실패를 안다.
2. **서버 구간 분할(B3)** — 한 실행이 5,000건(`MAX_PAGES × PAGE_SIZE`)을 넘으면
   `PAGE_LIMIT_EXCEEDED`로 실패하고, 사용자가 기간을 얼마나 좁혀야 할지 직접 추측해야 한다.
3. **중복 호출 보호(B2/D7)** — `POST broker-sync-preview`, `POST broker-holding-snapshots`,
   `POST broker-order-imports` 세 엔드포인트는 누를 때마다 조건 없이 증권사를 호출한다. 중복
   클릭·같은 화면 두 탭·빠른 재시도가 그대로 외부 호출 두 번이 된다.

세 가지 모두 **원장을 건드리지 않는 조회 경로**의 문제이므로 `TradeTransaction`, 승인·취소,
멱등키 제약(부분 유니크 인덱스) 등 기존 원장 반영 계약은 전혀 바꾸지 않는다. 변경 범위는
`BrokerOrderImportService`, `BrokerOrderImportRun`(신규 컬럼 1개), 세 컨트롤러의 앞단, 그리고
새 컴포넌트 하나(`BrokerDuplicateCallGuard`)로 좁힌다.

## 1. 결정이 필요한 사항 (사용자/Codex 승인 필요)

이 슬라이스는 임의 상수 네 개를 도입한다. 전략 손절률·수량 비율 같은 투자 정책 상수가 아니라
**운영 안전값**이지만, 여전히 우리가 근거 없이 고르는 숫자이므로 승인 없이 구현하지 않는다.
각 항목에 최소 안전 기본값과 근거를 함께 적는다.

| # | 결정 | 권장 기본값 | 근거 |
| --- | --- | --- | --- |
| 1 | 한 번의 `createPreview` 요청이 받을 수 있는 최대 조회 폭 | **366일** (`requestedOrderedFrom`~`requestedOrderedTo`, `ChronoUnit.DAYS.between` 기준) | 연말 정산·세금 신고처럼 "최근 1년 정리"가 흔한 실사용 패턴이다. 이보다 넓히면 §4의 분할로도 한 요청의 최악 지연이 늘어난다. |
| 2 | 서버 내부 분할 창 크기 | **30일** | §4에서 계산하듯, 분할 창을 좁게 잡을수록 창 하나가 `MAX_PAGES`에 단독으로 걸릴 위험이 줄어든다. 366일 상한과 곱하면 최대 13개 창이 나오는데, 전체 호출 예산은 여전히 기존 `MAX_PAGES=50`으로 고정되므로(§4) 창을 더 잘게 쪼개도 최악 지연은 늘지 않는다. |
| 3 | 중복 호출 가드의 재호출 대기 시간(쿨다운) | **5초** | 실수 더블클릭·같은 화면 새로고침 경쟁을 막는 목적이다. 토스증권의 실제 호출 빈도 제한은 이 저장소 어디에도 문서화되어 있지 않으므로 그 값을 대신한다고 주장하지 않는다. 운영 중 실제 429가 관측되면 이 값을 근거를 남기고 올린다. |
| 4 | 동시 진행 중 호출을 막을지, 대기시킬지 | **막는다(즉시 409)** | 대기열을 두려면 새 인프라(락 대기, 타임아웃)가 필요하다. 조회 API는 실패해도 사용자가 버튼을 다시 누르면 되므로, 최소 구현은 즉시 거부다. |

승인이 늦어지면 1·2번만 미룰 수 있다. 3·4번(중복 호출 보호)은 값 하나(쿨다운 초)만 정하면 되고
스키마 변경이 없어 독립적으로 먼저 착수할 수 있다.

## 2. API 계약

### 2.1 `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports`

요청 본문(`BrokerOrderImportCreateRequest`)은 바꾸지 않는다. 응답 DTO
(`BrokerOrderImportRunDetailResponse` → `run`)에 필드를 하나 추가한다.

```jsonc
{
  "run": {
    // 기존 필드 그대로 (requestedOrderedFrom, requestedOrderedTo, queriedOrderedFrom, ...)
    "coverage": {
      "fullyCovered": true,
      // fullyCovered=false일 때만 채워진다. 이 실행이 요청 구간 중 어디까지 반영했는지.
      "coveredOrderedTo": null,
      // fullyCovered=false일 때만 채워진다. 다음 요청에 그대로 넣으면 이어받을 수 있는 시작일.
      "nextOrderedFrom": null
    }
  },
  "approval": { /* 변경 없음 */ },
  "reconciliation": [ /* 변경 없음 */ ]
}
```

기존 필드는 이름·의미를 바꾸지 않는다. `coverage`만 추가한다. 프론트엔드가 이 필드를 아직
읽지 않아도 기존 파싱은 깨지지 않는다(추가 전용 변경).

### 2.2 오류 계약

| 조건 | 위치 | 상태 | 응답 |
| --- | --- | --- | --- |
| 요청 폭이 §1-1의 상한 초과 | `requireRange`, 증권사 호출 전 | 400 | 기존 `IllegalArgumentException` 그대로. 메시지에 상한 일수와 요청한 일수를 함께 적는다(예: "조회 기간은 최대 366일까지 요청할 수 있습니다. 현재 400일을 요청했습니다."). `ApiErrorCode`는 부여하지 않는다 — 같은 메서드의 null 검사·시작일>종료일 검사도 코드 없는 400이고, 프론트엔드는 이미 메시지 문자열로 표시한다(§2.3 근거). |
| 가장 작은 분할 창조차 예산 안에 안 끝남(0개 창 완료) | `fetchOrders` | 422 | 기존 `BrokerOrderImportUnprocessableException`, `failureCode=PAGE_LIMIT_EXCEEDED` 그대로 재사용. 메시지만 "가장 좁힌 조회 단위(30일)에서도 주문이 너무 많습니다. 그보다 좁은 기간으로 다시 시도하세요."로 구체화한다. |
| 같은 포트폴리오·같은 동작이 이미 진행 중 | 컨트롤러 앞단(`BrokerDuplicateCallGuard`) | 409 | 신규 `BrokerCallInProgressException` → `ApiErrorCode.BROKER_CALL_IN_PROGRESS` |
| 쿨다운 시간 내 재호출 | 컨트롤러 앞단 | 429 | 신규 `BrokerCallCooldownException` → `ApiErrorCode.BROKER_CALL_COOLDOWN`, 메시지에 잔여 초를 담는다(예: "3초 후 다시 시도할 수 있습니다.") |

`ApiErrorCode`에 두 값을 추가한다.

```java
/** 같은 포트폴리오의 같은 증권사 호출이 이미 진행 중이다. 완료 후 다시 시도할 수 있다. */
BROKER_CALL_IN_PROGRESS,

/** 직전 호출 이후 최소 대기 시간이 지나지 않았다. 잠시 후 다시 시도할 수 있다. */
BROKER_CALL_COOLDOWN
```

기존 `BrokerConnectionUnavailableException`/`BrokerHoldingImportUnprocessableException` 패턴을
그대로 따르는 두 개의 새 예외를 `exception/` 아래 추가한다.

```java
public class BrokerCallInProgressException extends RuntimeException {
    public BrokerCallInProgressException(String message) { super(message); }
}

public class BrokerCallCooldownException extends RuntimeException {
    private final long retryAfterSeconds;
    public BrokerCallCooldownException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
```

`GlobalExceptionHandler`에 두 핸들러를 추가한다(409/429, 각각 `ApiErrorCode` 부여). `Retry-After`
HTTP 헤더는 이번 슬라이스에 넣지 않는다 — 메시지 문자열에 잔여 초를 담는 것으로 충분하고, 헤더
소비는 프론트엔드 작업이 필요해 범위를 넓힌다.

### 2.3 프론트엔드 호환성 확인

`git diff`로 관련 화면을 확인한 결과, `frontend/src/`는 `runDetail.run`의 알려진 필드만 읽고
미지의 필드를 걸러내지 않으므로 `coverage` 추가는 안전하다. 400/422/409/429는 전부 기존처럼
`message` 문자열을 그대로 표시하는 공통 오류 처리 경로(`RequestError`/`apiError.ts`)를 타므로
프론트엔드 코드 변경 없이도 사용자에게 문구가 보인다. `code` 필드를 이용한 분기(예: 429 전용
안내)는 이번 슬라이스의 프론트엔드 비목표다.

## 3. 조회 기간 상한 설계 (E4)

`BrokerOrderImportService.requireRange`(L446-453)에 세 번째 검사를 추가한다.

```java
private static final long MAX_REQUESTED_RANGE_DAYS = 366;

private void requireRange(LocalDate orderedFrom, LocalDate orderedTo) {
    if (orderedFrom == null || orderedTo == null) { ... }          // 기존
    if (orderedFrom.isAfter(orderedTo)) { ... }                    // 기존
    long requestedDays = ChronoUnit.DAYS.between(orderedFrom, orderedTo);
    if (requestedDays > MAX_REQUESTED_RANGE_DAYS) {
        throw new IllegalArgumentException(
            "조회 기간은 최대 " + MAX_REQUESTED_RANGE_DAYS + "일까지 요청할 수 있습니다. "
                + "현재 " + requestedDays + "일을 요청했습니다.");
    }
}
```

이 검사는 `brokerOrderImportContextLoader.load(...)`보다 먼저 실행된다(기존 두 검사와 같은
위치). 즉 **증권사 호출은 물론 연결·링크 조회조차 하지 않고** 400을 반환한다. E4가 지적한
"10년 구간을 요청하면 상한에 걸려 실패할 때까지 증권사를 50번 호출한다"는 낭비를 완전히
제거한다.

`BrokerOrderHistoryQuery`, `BrokerConnectionCreateRequest` 등 다른 계약은 바꾸지 않는다.

## 4. 서버 구간 분할 설계 (B3)

### 4.1 원칙

`fetchOrders`(L323-376)는 지금 **하나의 조회 구간**을 커서로 끝까지 순회하다가 50페이지를
넘으면 전체를 실패시킨다. 이걸 **여러 개의 작은 요청 구간(창)을 순서대로 순회**하도록 바꾸되,
다음 두 가지는 지금과 똑같이 유지한다.

- **커서 순회 로직은 그대로 재사용한다.** 창 하나 안에서의 페이지 순회는 지금 코드와 동일하다.
- **전체 요청의 provider 호출 예산은 지금과 똑같이 `MAX_PAGES=50`으로 고정한다.** 창을
  여러 개로 나눈다고 예산을 창 개수만큼 곱하지 않는다. 곱하면(예: 13개 창 × 50페이지)
  최악의 경우 하나의 동기 HTTP 요청 안에서 provider를 수백 번 호출하게 되어, 요청 자체가
  타임아웃될 위험이 커진다. **예산을 그대로 두고 나누는 방식**을 선택했기 때문에, 이 슬라이스는
  최악 지연을 오늘과 동일하게 유지하면서 결과만 개선한다("실패 후 사용자가 직접 추측" →
  "부분 성공 + 정확한 다음 시작일").

### 4.2 알고리즘

```
sub-windows = [orderedFrom, orderedFrom+29], [orderedFrom+30, orderedFrom+59], ... (30일 단위, orderedTo에서 자름)
sharedPageBudget = MAX_PAGES  // 50, 창 전체가 공유
completedWindows = 0

for window in sub-windows (오래된 창부터):
    windowFrom = window.from - BOUNDARY_PADDING_DAYS
    windowTo   = window.to   + BOUNDARY_PADDING_DAYS
    try fetch this window fully via existing cursor loop, consuming sharedPageBudget
    if budget runs out **before this window finishes**:
        # 이 창은 절반만 가져온 상태로 버린다. "이 창까지는 끝났다"는 거짓 판정을 만들지 않기 위해서다.
        discard this window's partially-fetched records
        break
    else:
        merge window's records into the shared de-dup map (기존 recordsByOrderId, 창 경계 겹침은 여기서 자연 흡수)
        completedWindows += 1

if completedWindows == 0:
    throw PAGE_LIMIT_EXCEEDED  // 가장 좁힌 창조차 예산 안에 못 끝남 (§2.2)

fullyCovered = (completedWindows == sub-windows.size)
coveredOrderedTo = fullyCovered ? null : sub-windows[completedWindows-1].to
```

핵심 설계 판단:

1. **창 하나를 다 못 끝내면 그 창 전체를 버린다.** 절반만 가져온 창을 "이 날짜까지 커버함"으로
   기록하면, 그 창 안에서 예산 소진 시점 이후의 주문이 조용히 누락된 채 "커버했다"고 표시되는
   거짓 판정이 생긴다. 이미 이 문서가 §4.1에서 지키기로 한 "판정과 실패를 섞지 않는다" 원칙과
   같은 이유다. 대신 `coveredOrderedTo`는 항상 **완전히 끝난 마지막 창의 끝 날짜**만 가리킨다.
2. **창 경계의 padding은 창마다 독립적으로 적용한다.** 인접한 두 창의 padding이 겹치는 날짜의
   주문은 두 번 조회될 수 있지만, 기존 `recordsByOrderId`(주문 식별자 기준 `putIfAbsent`)가
   이미 이 문제를 흡수하도록 설계돼 있다(L342-346). 창을 나눠도 이 보장은 그대로 적용된다.
3. **completedWindows == 0인 경우만 완전 실패로 본다.** 가장 좁힌 창(30일)에서조차 예산이
   부족하다는 뜻이므로, 자동 분할이 더 해줄 수 있는 일이 없다. 이때는 오늘과 동일하게
   `PAGE_LIMIT_EXCEEDED`로 실패시키고, 사용자에게 더 좁은 기간을 직접 요청하게 한다(§2.2).
4. **`countOpenOrders`(진행 중 주문 집계, L381-394)는 분할하지 않는다.** 이미 한 번의 호출로
   전량을 돌려주는 별도 API이고, 창 분할과 무관하다.

### 4.3 최악 시나리오 재계산

| 시나리오 | 오늘 | 이 슬라이스 이후 |
| --- | --- | --- |
| 366일 요청, 저밀도 계좌 | 최대 50 provider 호출(대부분 안 씀), 성공 | 창 최대 13개, 공유 예산 50 호출 이내에서 대부분 조기 종료, 성공(동일 결과) |
| 366일 요청, 특정 두 달만 초고밀도(5,000건 초과) | 커서가 처음부터 그 두 달 구간을 만나는 순간 실패, "기간을 나눠 다시 시도" 메시지만 받고 어디를 나눠야 할지 모름 | 저밀도 구간은 창 단위로 정상 커버, 초고밀도 구간에서 예산 소진 시 그 창부터 버리고 `coveredOrderedTo`/`nextOrderedFrom`으로 **정확히 어디부터 다시 요청할지** 안내 |
| 30일 요청, 계좌 자체가 초고밀도 | 실패 | 동일하게 실패(창이 1개뿐이라 분할 이득 없음) — 이 경우는 사용자가 30일보다 더 좁혀야 한다 |

세 번째 줄이 "이번 슬라이스가 못 푸는 것"이다. 실사용에서 30일에 5,000건(하루 166건 이상
지속)은 개인 계좌 기준 극단값이라고 보고, 그 경우의 fallback은 오늘과 동일한 실패 메시지로
남긴다.

### 4.4 도메인 변경

`BrokerOrderImportRun`에 nullable 컬럼 하나를 추가한다. **상태 enum
(`BrokerOrderImportRunStatus`)은 건드리지 않는다** — `STAGED`/`FAILED` 두 값 그대로 두고,
"부분 커버"는 상태가 아니라 커버리지 정보로 표현한다. 이렇게 하면
`BrokerOrderImportApprovalWriter`의 `RUN_NOT_STAGED` 판정, D3의 승인 판정 집계 로직을 전혀
건드리지 않는다 — 부분 커버 실행도 그 안에 저장된 주문은 실제로 체결된 주문이므로 지금과
똑같이 승인 대상이다.

```java
// BrokerOrderImportRun.java
@Column(name = "covered_ordered_to")
private LocalDate coveredOrderedTo;   // null이면 요청 구간을 끝까지 커버했다는 뜻
```

`staged(...)` 정적 팩터리에 파라미터 하나(`LocalDate coveredOrderedTo`, nullable)를 추가한다.
`getCoveredOrderedTo()`/`isFullyCovered()`(= `coveredOrderedTo == null`) 게터를 추가한다.

`BrokerOrderImportRunResponse`(또는 그 안의 `run` 객체)에 `coverage` 필드를 추가한다.
`nextOrderedFrom`은 저장하지 않고 응답 생성 시점에 계산한다(`coveredOrderedTo.plusDays(1)`).

## 5. 마이그레이션

**V19__add_broker_order_import_coverage.sql**

```sql
-- 서버 구간 분할(S6)이 예산 부족으로 요청 구간을 끝까지 커버하지 못했을 때,
-- 어디까지 커버했는지 남긴다. NULL이면 요청 구간을 끝까지 커버했다는 뜻이다.
-- (기존 queried_ordered_to는 "얼마나 넓혀 조회하려 했는가"라는 의도를 담은 값이라
-- 의미를 바꾸지 않고, 실제 결과를 담을 컬럼을 별도로 둔다.)
ALTER TABLE broker_order_import_runs
    ADD COLUMN covered_ordered_to DATE;
```

핵심 판단:

- **기존 컬럼 어느 것도 의미를 바꾸지 않는다.** `queried_ordered_to`는 지금처럼 "요청 구간을
  boundary padding으로 넓힌 의도값"으로 유지한다. 실제로 어디까지 가져왔는지는 새 컬럼이
  담당한다. 두 개념을 한 컬럼에 겹치면 과거 행(전부 `fullyCovered`)과 이후 행을 구분할 수
  없어진다.
- **기존 행은 전부 `NULL`(=완전 커버)로 해석된다.** 백필이 필요 없다. 이 컬럼이 생기기 전의
  모든 실행은 실제로 요청 구간을 끝까지 커버한 채로 성공했거나 실패(`FAILED`)했으므로,
  `NULL` 기본 해석이 그대로 맞다.
- `NOT NULL` 제약을 걸지 않는다. 대부분의 실행은 `NULL`(완전 커버)이 정상이다.
- `postgresIntegrationTest`가 `ddl-auto=validate`로 엔티티-스키마 일치를 검증하므로, 엔티티에
  필드를 추가하지 않고 마이그레이션만 하면 CI에서 즉시 잡힌다(기존 관례, D2/D3 문서화와 동일).

## 6. 중복 호출 보호 설계 (B2/D7)

### 6.1 새 컴포넌트: `BrokerDuplicateCallGuard`

```java
@Component
public class BrokerDuplicateCallGuard {

    // 포트폴리오+동작 단위 진행 중 표시. 완료 즉시(성공/실패 모두) finally에서 제거한다.
    private final ConcurrentHashMap<Key, Boolean> inFlight = new ConcurrentHashMap<>();

    public void acquire(Long portfolioId, BrokerCallType type) {
        if (inFlight.putIfAbsent(new Key(portfolioId, type), Boolean.TRUE) != null) {
            throw new BrokerCallInProgressException(
                "같은 포트폴리오의 " + type.description() + " 조회가 이미 진행 중입니다. 잠시 후 다시 시도하세요.");
        }
    }

    public void release(Long portfolioId, BrokerCallType type) {
        inFlight.remove(new Key(portfolioId, type));
    }

    public void checkCooldown(Long portfolioId, BrokerCallType type, Instant lastCalledAt, Clock clock) {
        if (lastCalledAt == null) return;
        Duration elapsed = Duration.between(lastCalledAt, Instant.now(clock));
        Duration cooldown = type.cooldown();      // 5초, §1-3
        if (elapsed.compareTo(cooldown) < 0) {
            long remaining = cooldown.minus(elapsed).toSeconds() + 1;
            throw new BrokerCallCooldownException(
                remaining + "초 후 다시 시도할 수 있습니다.", remaining);
        }
    }

    private record Key(Long portfolioId, BrokerCallType type) {}
}

public enum BrokerCallType {
    HOLDING_PREVIEW("보유 종목 미리보기"),
    HOLDING_SNAPSHOT("보유 종목 스냅샷 갱신"),
    ORDER_HISTORY_IMPORT("주문 이력 가져오기");
    // description(), cooldown() = Duration.ofSeconds(5) 전부 동일(§1-3)
}
```

`inFlight`는 애플리케이션 메모리 상태다. 재시작 시 초기화되고 다중 인스턴스 배포에서는
인스턴스별로 따로 논다 — 완료 주봉 캐시(`docs/PROJECT_CONTEXT.md` "현재 제한" 절)와 같은 이미
받아들여진 한계이며, 이 슬라이스가 새로 만드는 위험이 아니다. 이 사실을 코드 주석과 이 문서
양쪽에 남긴다.

### 6.2 쿨다운 기준 시각의 출처 — 엔드포인트별로 다르다

| 엔드포인트 | 진행 중 잠금 키 | 쿨다운 기준 시각(`lastCalledAt`) | 비고 |
| --- | --- | --- | --- |
| `POST .../broker-sync-preview` | `(portfolioId, HOLDING_PREVIEW)` | **메모리 전용**: `ConcurrentHashMap<Long, Instant>`에 호출 직후 기록 | 이 엔드포인트는 아무것도 저장하지 않으므로(§ 기존 `BrokerHoldingPreviewService` javadoc) DB에서 "직전 호출 시각"을 구할 수 없다. 메모리 상태이므로 재시작하면 쿨다운도 초기화된다 — 완전한 보장이 아니라 실수 더블클릭 방지 목적임을 명시한다. |
| `POST .../broker-holding-snapshots` | `(portfolioId, HOLDING_SNAPSHOT)` | **DB 조회**: `portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolioId)`의 `syncedAt` (이미 존재하는 쿼리, `PortfolioBrokerHoldingSnapshotService` L96) | 재시작에도 살아남는 더 강한 보장이다. 새 코드가 필요 없다. |
| `POST .../broker-order-imports` | `(portfolioId, ORDER_HISTORY_IMPORT)` | **DB 조회(신규 쿼리 1개)**: `brokerOrderImportRunRepository.findFirstByPortfolio_IdOrderByStartedAtDesc(portfolioId)`의 `startedAt` — 상태(`STAGED`/`FAILED`) 무관하게 최신 1건 | 기존 `findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(..., STAGED)`(지문 대조용, L202-204)와 목적이 다르므로 별도 메서드로 추가한다. 실패한 시도도 이미 provider를 호출했으므로 쿨다운 계산에 포함해야 한다. |

### 6.3 컨트롤러 적용 형태

세 엔드포인트 모두 같은 형태로 감싼다(예시는 `broker-order-imports`):

```java
@PostMapping
public ResponseEntity<BrokerOrderImportRunDetailResponse> createPreview(...) {
    duplicateCallGuard.acquire(portfolioId, ORDER_HISTORY_IMPORT);
    try {
        duplicateCallGuard.checkCooldown(portfolioId, ORDER_HISTORY_IMPORT,
                lastOrderImportStartedAtLookup.find(portfolioId), clock);
        // 기존 로직 그대로
    } finally {
        duplicateCallGuard.release(portfolioId, ORDER_HISTORY_IMPORT);
    }
}
```

`acquire`를 `checkCooldown`보다 먼저 호출해 두 요청이 거의 동시에 들어와도 하나만 쿨다운 검사
이후 단계까지 진행한다. 잠금은 컨트롤러 계층에 둔다 — 서비스 메서드를 다른 경로(예: 내부
배치)에서 재사용할 계획이 없고, 컨트롤러가 이 세 엔드포인트의 유일한 진입점이기 때문이다.

### 6.4 알려진 한계 — 반드시 문서화할 것

`inFlight` 잠금은 **provider 호출이 실제로 끝나야(성공이든 실패든 예외든) `finally`에서
풀린다.** 만약 해당 호출에 쓰는 `RestClient`에 읽기 타임아웃이 설정돼 있지 않다면, 네트워크가
멈춘 채 응답이 영원히 오지 않는 극단적 상황에서 그 포트폴리오·동작의 잠금이 사실상 무기한
풀리지 않는다. 저장소를 확인한 결과 `TossSecuritiesOrderHistoryProvider` 등 브로커
`RestClient` 구성에 명시적 connect/read timeout 설정이 없다(`config/` 아래 관련 빈 없음).

**이 슬라이스의 전제 조건으로, 브로커 `RestClient`에 합리적인 연결·읽기 타임아웃을 먼저(또는
같이) 설정할 것을 권고한다.** 이건 토스가 문서화한 값이 아니라 우리 쪽 HTTP 클라이언트
설정이므로 "제공자 제한을 임의로 가정"하는 문제와 무관하다. 타임아웃 없이 이 가드만 추가하면
가드가 막으려는 문제(중복 호출로 인한 낭비)보다 더 나쁜 문제(잠금 무기한 점유)를 새로 만들 수
있다. 타임아웃 설정 자체는 이 문서의 범위가 아니므로 별도 결정 항목으로 Codex에게 넘긴다.

### 6.5 멱등성과의 관계 — 혼동 방지

이 가드는 **멱등성 장치가 아니다.** 쿨다운을 통과해 실제로 호출이 이뤄지면, 오늘과 똑같이
`POST broker-order-imports`는 새 실행 행을 만들고, `POST broker-holding-snapshots`는 새 스냅샷
행을 만든다 — 의도된 동작이며 이번 슬라이스로 바꾸지 않는다(기존 문서 6.7절의 "연결 생성은
비멱등, 현행 유지" 판단과 같은 이유: 조회·감사 기록은 반복 가능해야 정상이다). 가드가 막는 것은
"같은 사용자 조작이 짧은 시간에 겹쳐 provider를 불필요하게 여러 번 두드리는 것"뿐이다.

## 7. 멱등성·동시성 요약표

| 동작 | 이 슬라이스 이전 | 이 슬라이스 이후 |
| --- | --- | --- |
| `createPreview` 결과 | 비멱등(매번 새 실행) | 동일(비멱등 유지) |
| `createPreview` 동시 이중 클릭 | 두 요청 모두 provider 호출, 두 실행 행 생성 | 두 번째 요청 409(§6.3) |
| `createPreview` 5초 내 재요청 | 즉시 provider 재호출 | 429(§6.2) |
| 큰 구간 요청 시 부분 실패 | 전체 실패, 재시도 시 사용자가 폭을 추측 | 부분 커버 성공 + `nextOrderedFrom`(§4) |
| 승인·취소, 개시 잔고 반영 멱등키 | 부분 유니크 인덱스(`uk_broker_order_ledger_links_active_order`) | 변경 없음 — 이 슬라이스는 조회 단계만 다룬다 |

## 8. 테스트 계획

**단위 — 도메인/서비스**

1. `requireRange`: 366일 이하 통과, 367일 이상 400 + 상한/요청 일수가 메시지에 포함.
2. `fetchOrders` 창 분할: 저밀도 다중 창(3개) 전부 완주 → `coveredOrderedTo == null`.
3. 두 번째 창에서 예산 소진 → `completedWindows == 1`, `coveredOrderedTo` = 첫 창의 끝 날짜,
   두 번째 창에서 이미 받은 레코드는 결과에 포함되지 않는다(버려짐, §4.2 판단 1 검증).
4. 창 경계 padding 겹침 구간의 중복 주문이 `duplicateFetchCount`로 흡수되고 원장 항목은
   중복 저장되지 않는다(기존 `recordsByOrderId` 로직 재검증).
5. 첫 창(30일)조차 예산 부족 → `completedWindows == 0` → 기존과 동일한 `PAGE_LIMIT_EXCEEDED`.
6. `BrokerOrderImportRun.staged(...)`: `coveredOrderedTo` null/non-null 양쪽 저장·조회.
7. 응답 DTO: `coveredOrderedTo` non-null일 때 `nextOrderedFrom = coveredOrderedTo.plusDays(1)`로
   계산되고, null일 때 `coverage.fullyCovered == true`이며 나머지 필드는 없음(`null`).

**단위 — 가드**

8. `acquire` 두 번 연속 호출(같은 키) → 두 번째에서 `BrokerCallInProgressException`.
9. `release` 이후 재 `acquire` 성공.
10. `checkCooldown`: `lastCalledAt`이 5초 이내 → `BrokerCallCooldownException`(`retryAfterSeconds`
    양수 검증), 5초 이상 지남 → 통과.
11. `lastCalledAt == null`(첫 호출) → 무조건 통과.

**서비스/컨트롤러 통합**

12. `broker-order-imports` 연속 두 번 빠르게 호출 → 두 번째 429, 첫 번째 실행 행은 정상 저장.
13. 스레드 두 개로 동시에 `broker-sync-preview` 호출(테스트에서 인위적 지연 스텁 사용) → 하나는
    성공, 하나는 409.
14. `broker-holding-snapshots` 쿨다운이 실제 저장된 `syncedAt`을 기준으로 계산됨(스냅샷을 먼저
    만들고 시계를 5초 미만 전진시켜 재호출 → 429; 5초 이상 전진 → 성공).
15. 부분 커버 실행 이후 같은 계좌에 대해 `nextOrderedFrom`을 시작일로 다시 `createPreview` 호출
    → 두 실행의 항목을 합쳤을 때 원래 요청 구간의 주문이 중복·누락 없이 모두 나온다(창 경계
    padding 덕분에 이어붙임이 안전함을 검증하는 핵심 테스트).

**저장소/마이그레이션**

16. `postgresIntegrationTest`: V1~V19 적용 후 엔티티 매핑 validate 통과, 기존 행의
    `covered_ordered_to`가 `NULL`로 남아 있음을 확인.

**실행 명령**

```bash
./gradlew test
./gradlew postgresIntegrationTest   # Docker 필요
```

프론트엔드는 이번 슬라이스에서 수정하지 않으므로 `npm run lint`/`npm run build`는 회귀 확인
목적으로만 실행한다(§2.3 근거로 통과 예상).

## 9. 롤백 위험

| 위험 | 영향 | 완화 |
| --- | --- | --- |
| `MAX_REQUESTED_RANGE_DAYS`가 실사용보다 너무 좁을 수 있다 | 사용자가 여러 번 나눠 요청해야 함(불편이지 데이터 손상 아님) | 상수라 배포 없이 재컴파일만으로 조정 가능. 운영 관찰 후 조정 근거를 이 문서에 갱신 |
| `CHUNK_WINDOW_DAYS`가 실제 계좌 밀도와 안 맞으면 §4.3 세 번째 시나리오(창 하나가 단독으로 예산 초과)가 자주 발생 | 사용자 체감상 "여전히 실패한다" | 실패 메시지가 창 크기(30일)를 명시하므로 사용자가 그보다 좁게 재시도 가능. 값 자체는 상수라 조정 쉬움 |
| `covered_ordered_to` 컬럼 추가(V19) | 매우 낮음 — nullable 컬럼 추가는 기존 행에 영향 없음 | 롤백 시 컬럼을 남겨 둬도 무해(읽지 않으면 그만). 완전 롤백이 필요하면 `ALTER TABLE ... DROP COLUMN`만으로 충분, FK/인덱스 없음 |
| `inFlight` 메모리 잠금이 타임아웃 없는 호출에서 무기한 점유(§6.4) | **가장 높은 위험.** 해당 포트폴리오·동작이 사실상 영구적으로 409를 반환 | §6.4의 전제 조건(타임아웃 설정)을 이 슬라이스와 함께 또는 먼저 배포. 그전까지는 애플리케이션 재시작으로만 해소됨을 운영 메모에 남긴다 |
| 쿨다운 5초가 실제 운영에서 너무 짧아 토스 쪽 실제 제한에 걸림 | 알 수 없음(문서화된 제한 없음) | 429가 실제로 관측되면 이 문서를 갱신하고 값을 올린다. 이번 슬라이스는 "제한 준수"를 주장하지 않는다(§0) |

전체 롤백 순서: (1) 컨트롤러의 가드 호출 3곳 제거, (2) `fetchOrders`를 단일 구간 순회로 되돌림,
(3) `requireRange`의 폭 검사 제거, (4) V19는 그대로 둬도 무해(컬럼 미사용). 원장 반영 경로는
전혀 건드리지 않으므로 롤백이 데이터 정합성에 영향을 주지 않는다.

## 10. 구현 체크리스트 (범위 확정 — S6a/S6b 분리 반영)

**S6a — 완료 (2026-09-10, 백엔드 전용)**

- [x] `ApiErrorCode`에 `BROKER_CALL_IN_PROGRESS`, `BROKER_CALL_COOLDOWN` 추가
- [x] `BrokerCallInProgressException`, `BrokerCallCooldownException` 추가, `GlobalExceptionHandler`에 409/429 핸들러 추가
- [x] `BrokerOrderImportService.requireRange`에 폭 상한 검사 추가(§3, 양 끝 날짜 포함 366일로 조정 — 분리 안내 참고)
- [x] `BrokerOrderImportRunRepository`에 `findFirstByPortfolio_IdOrderByStartedAtDesc` 추가(§6.2)
- [x] `BrokerDuplicateCallGuard`, `BrokerCallType` 신규 추가(§6.1), `BrokerHoldingPreviewCallTracker` 추가(미리보기 전용 메모리 쿨다운 소스)
- [x] `BrokerOrderImportService.createPreview`, `BrokerHoldingPreviewService.getHoldingPreview`,
      `PortfolioBrokerHoldingSnapshotService.refreshSnapshot` 세 서비스 메서드에 가드 적용
      (컨트롤러가 아니라 서비스 계층 — 분리 안내의 조정 사항 참고)
- [x] 브로커 `RestClient`의 connect/read timeout 설정(`BrokerHttpClientConfig`, §6.4의 전제
      조건). 원래 "다음 슬라이스로 미룸"이었으나, 이것 없이 가드만 배포하면 §9 최고 위험이
      그대로 남으므로 S6a에 포함해 함께 구현했다.
- [x] §8 테스트 중 1(범위 상한, 같은 날/366일/367일로 구체화), 8~14(가드·쿨다운) 대응하는
      단위·서비스 테스트

**S6b — 미룸 (분리 안내 참고, 불완전 이력 승인 정책 + Claude UX 확정 후 별도 작업 계약)**

- [ ] `BrokerOrderImportService.fetchOrders`를 창 분할 루프로 재작성(§4.2), 기존 커서 순회·중복
      제거·`FetchResult` 구조는 최대한 재사용
- [ ] `BrokerOrderImportRun`에 `coveredOrderedTo` 컬럼·게터·`staged(...)` 파라미터 추가(§4.4)
- [ ] `BrokerOrderImportRunResponse`(또는 하위 DTO)에 `coverage` 응답 필드 추가(§2.1)
- [ ] 마이그레이션 `V19__add_broker_order_import_coverage.sql`(§5)
- [ ] §8의 나머지 테스트(2~7, 15, 16 — 창 분할·부분 커버·이어받기)
- [ ] `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md` 7장 S6 행을 S6a 완료/S6b 대기로 갱신 (이번에
      함께 반영함)

**포함하지 않음(S6a·S6b 모두와 무관하게 계속 미룸)**

- `Retry-After` HTTP 헤더, 프론트엔드의 `code` 기반 429/409 전용 안내 문구(§2.3) — 프론트엔드
  비목표는 S6a에서도 유지된다.
- S7(자격 증명 키 로테이션), S8(의심 판정 재판정) — 이 문서와 무관
- 다중 인스턴스 배포에서의 `inFlight`/쿨다운 공유(현재 단일 인스턴스 전제, §6.1과 동일한 이미
  받아들여진 한계)

## 11. 비목표

- 토스증권의 실제 호출 빈도·조회 기간 제한을 조사하거나 가정하지 않는다(§0).
- 자동 동기화, 스케줄 배치, 백그라운드 잡을 추가하지 않는다 — 이어받기(`nextOrderedFrom`)는
  사용자가 다시 요청 버튼을 눌러야 하는 수동 동작이다.
- 원장 반영(승인·취소) 경로의 멱등성 계약을 바꾸지 않는다.
- 프론트엔드 변경은 포함하지 않는다(§2.3에서 확인한 대로 하위 호환).

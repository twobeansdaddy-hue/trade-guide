# S6b 백엔드 계약 제안 — 서버 구간 분할 + 불완전 이력 승인 정책

## Identity

- Task ID: `TASK-S6B-CHUNK-INCOMPLETE-APPROVAL`
- Owner(이 문서): `Claude` — 설계 제안 및 검토 전용. **구현하지 않았다.**
- Work mode: `review` — 이 작업은 `docs/agent-tasks/**`만 쓴다. `src/**`, `frontend/**`, 설정,
  마이그레이션 파일을 만들거나 고치지 않았고, 증권사·DB·외부 서비스를 호출하지 않았다. 커밋·푸시도
  하지 않았다.
- 선행 문서: `docs/agent-tasks/broker-order-import-range-limit-and-call-guard.md`(이하 "S6
  원문서")의 "분리 안내" 절. 이 문서는 그 절이 미룬 두 가지 중 **정책·계약 부분**(불완전 이력
  승인 정책, 서버 구간 분할 계약)을 채운다. 나머지 하나(Antigravity UX 검토)는 이 문서의 범위가
  아니다.
- 근거 범위: 이 저장소의 현재 코드(2026-09-10 작업 트리, 커밋 전 상태 포함)만 사용했다. 토스증권
  문서에 없는 조회 제한을 사실처럼 가정하지 않는다. §2의 상수·정책은 전부 **우리가 정하는
  값**이며 외부 제공자 규정이 아니다.

## 0. 요약

S6 원문서는 세 가지(조회 기간 상한, 서버 구간 분할, 중복 호출 보호) 중 앞뒤 두 가지만
S6a로 구현했고, 가운데 것(서버 구간 분할)을 두 가지 선행 조건 — **불완전 이력 승인 정책**과
**Antigravity UX 검토** — 이 정해질 때까지 S6b로 미뤘다. 이 문서는 그 정책 쪽을 다룬다.

이 문서가 실제로 하는 일은 세 가지다.

1. **저장소 근거를 다시 확인한다**(§1). S6a가 실제로 배포된 상태인지, S6 원문서의 §4·§5
   설계가 지금 코드와 여전히 맞는지 재검증했다. 그 과정에서 원문서가 전제하지 않은 사실을
   하나 찾았다: **`BrokerOrderReconciler`의 대조 안전망이 불완전 이력의 상당수를 이미 수량
   불일치로 잡아낸다.** 이 사실은 정책 설계의 범위를 좁힌다 — "부분 커버를 어떻게 다 막을까"가
   아니라 "대조가 못 잡는 두 가지 틈새를 어떻게 메울까"가 실제 질문이다(§1.5).
2. **정책 결정 사항을 표로 낸다**(§2). 옵션과 권장값, 근거를 제시하지만 **채택하지 않는다.**
   이 판단은 `AGENTS.md`가 말하는 "투자 데이터 정합성 정책"이지 API 설계자가 대신 정할 수
   있는 값이 아니다.
3. **결정이 내려졌다고 가정했을 때의 계약**을 구체적으로 적는다(§3~§9). 결정이 바뀌어도
   계약의 뼈대(어느 필드가 어디에 추가되는지, 어느 예외가 몇 번을 반환하는지)는 대부분
   그대로이므로, 승인 이후 별도 조사 없이 바로 구현 작업 계약으로 옮길 수 있게 했다.

## 1. 저장소 근거 (사실관계 — 이 절은 결정이 아니다)

### 1.1 S6a는 실제로 배포된 상태다

코드에서 직접 확인했다.

- `BrokerOrderImportService.MAX_REQUESTED_RANGE_DAYS = 366`, `requireRange`가 이미 적용 중
  (`src/main/java/com/tradeguide/service/broker/BrokerOrderImportService.java:76,479-491`).
- `BrokerDuplicateCallGuard`, `BrokerCallType`, `BrokerHoldingPreviewCallTracker`,
  `BrokerCallInProgressException`, `BrokerCallCooldownException`이 전부 존재하고
  `ApiErrorCode.BROKER_CALL_IN_PROGRESS`/`BROKER_CALL_COOLDOWN`도 등록돼 있다.
- `BrokerHttpClientConfig`가 존재한다 — S6 원문서 §6.4가 지적한 "타임아웃 없는 잠금 무기한
  점유" 전제 조건이 채워졌다는 뜻이다.

**결론**: 이 문서는 S6a를 다시 설계하거나 검토하지 않는다. 완료된 것으로 보고 그 위에 쌓는다.

### 1.2 서버 구간 분할은 아직 코드에 없다

`fetchOrders`(`BrokerOrderImportService.java:361-403`)는 지금도 **하나의 조회 구간**을
`MAX_PAGES=50` 안에서 커서로 끝까지 순회하다가 넘으면 `PAGE_LIMIT_EXCEEDED`로 전체 실패한다.
`BrokerOrderImportRun`에는 `covered_ordered_to`에 해당하는 컬럼이 없다. S6 원문서 §4의 알고리즘
설계는 유효하지만 **미구현 상태 그대로**다. 이 문서 §5는 그 설계를 이어받되, 아래에서 찾은
어긋난 전제 하나를 고친다.

### 1.3 마이그레이션 번호가 원문서와 어긋났다

S6 원문서 §5는 `V19__add_broker_order_import_coverage.sql`을 제안했다. 그러나 현재
`src/main/resources/db/migration/`의 마지막 파일은 **`V19__create_broker_key_rotation_runs.sql`**
이다(S7 작업이 먼저 V19를 썼다). 이 문서가 실제로 구현 단계에 넘길 번호는 **V20**이다.
원문서의 SQL 스니펫은 컬럼 정의로서는 그대로 쓸 수 있지만 파일명은 갱신해야 한다(§8).

### 1.4 승인 판정의 현재 구조 — 새 정책이 끼어들 자리

`BrokerOrderImportApprovalAssessment.resolveBlocker`(정적 팩터리 내부)는 다음 순서로 거부
사유를 정한다.

```
1. runStatus != STAGED              → RUN_NOT_STAGED
2. reconciliation == MISMATCHED     → RECONCILIATION_MISMATCHED
3. reconciliation == REPLAY_FAILED  → RECONCILIATION_REPLAY_FAILED
4. eligibleCount == 0 && staged > 0 → ALL_BEFORE_BASELINE
5. eligibleCount == 0 && staged==0  → NO_STAGED_ITEMS
6. 그 외                             → approvable = true (blocker = null)
```

`BrokerOrderImportApprovalWriter.approve`도 **같은 순서**로 거부한다(대조 불일치 → 반영
후보 없음). 이 record는 `approvable == (blocker == null)` 배타 불변식을 생성자에서 강제한다
(`BrokerOrderImportApprovalAssessment.java:52-64`). 새 정책을 추가한다면 이 순서의 어디에
끼워 넣을지, 그리고 이 불변식을 계속 지킬 수 있는 형태인지가 설계의 핵심 제약이다(§3.2).

`POST .../broker-order-imports/{runId}/approval`은 **요청 본문이 없다**
(`BrokerOrderImportController.java:138-149`, `@PathVariable`만 받는다). 승인 확인(acknowledge)
같은 사용자 입력을 새로 받으려면 이 계약을 확장해야 한다(§3.3).

### 1.5 대조 안전망이 불완전 이력의 상당수를 이미 잡아낸다 — 원문서가 명시하지 않은 사실

`BrokerOrderReconciler`(클래스 주석, `BrokerOrderReconciler.java:22-38`)는 자신이 잡아내도록
설계된 세 가지 조용한 실패 중 하나로 **"조회 가능한 과거 기간이 짧아 이력 앞부분이 잘렸다 →
재구성 수량이 모자란다"**를 명시한다. 서버 구간 분할이 만드는 `fullyCovered=false` 실행은
정확히 이 실패 형태다 — 요청 구간의 뒷부분(또는 예산이 소진된 창 이후)이 통째로 빠진 이력이다.

즉 **부분 커버 실행은 스냅샷이 있는 계좌에서는 이미 `MISMATCHED`(§1.4의 2번)로 걸려 승인이
막힐 가능성이 높다.** 새 정책이 실제로 메워야 하는 틈새는 "부분 커버 전체"가 아니라 대조가
못 잡는 두 경우다.

| 틈새 | 원인 | 근거 |
| --- | --- | --- |
| ① 대조 기준이 없다 | 해당 계좌에 저장된 보유 스냅샷이 아직 없으면 `reconciliationStatus == NOT_AVAILABLE`이고, `resolveBlocker`는 이 값을 거부 사유로 보지 않는다(§1.4의 4번으로 바로 넘어간다) | `BrokerOrderReconciliationStatus.java` 주석 "저장된 스냅샷이 없어 대조할 기준이 없다"; `resolveBlocker`가 `NOT_AVAILABLE`을 별도로 처리하지 않음 |
| ② 순 상쇄로 우연히 수량이 맞는다 | 예산 소진으로 버려진 구간에 같은 종목의 매수·매도가 짝을 이뤄 들어 있으면, 그 구간을 통째로 빼도 최종 보유 수량은 우연히 일치한다 | `BrokerOrderReconciler`는 최종 수량만 비교하고 구간별 흐름은 비교하지 않는다(구현 확인, `reconcile` 시그니처가 `snapshotQuantities: Map<AssetKey, BigDecimal>`만 받음) |

이 두 틈새는 **드물지만 사라지지 않는다.** 특히 ①은 "이 계좌를 방금 연결하고 처음으로 몇 년
치 이력을 한 번에 가져오는" 흐름과 정확히 겹친다 — 스냅샷을 아직 한 번도 갱신하지 않은
상태이기 때문이다. S6 원문서 §0이 "연말 정산·세금 신고처럼 최근 1년 정리가 흔한 실사용
패턴"이라고 든 시나리오가 바로 이 틈새에 걸린다.

**이 발견이 정책 설계에 주는 함의**: 새 정책을 "대조와 별개로 항상 검사"로 설계하면 대조가
이미 잡는 사례까지 이중으로 경고해 사용자를 피로하게 만든다. "대조가 `MATCHED`일 때만 추가
확인을 건너뛴다" 정도로 좁히는 편이 대조 안전망과 새 정책의 역할을 겹치지 않게 나눈다(§3.2에서
구체화).

### 1.6 승인 멱등성은 청크 경계와 무관하게 이미 보장된다

`BrokerOrderLedgerLink`의 부분 유니크 인덱스 `uk_broker_order_ledger_links_active_order`는
`(broker_account_id, external_order_id) WHERE status = 'ACTIVE'` 기준이다(`V13` 마이그레이션,
`BrokerOrderImportApprovalWriter.approve`의 `alreadyLinkedOrderIds` 조회도 계좌 기준). **실행
(run) 단위가 아니라 계좌+주문ID 단위로 중복을 막는다.** 따라서:

- 부분 커버 실행(run #1)을 승인한 뒤 `nextOrderedFrom`으로 이어받은 후속 실행(run #2)을
  승인해도, 두 실행의 조회 구간이 경계에서 겹쳐도(§5의 padding), 같은 주문이 두 번 원장에
  들어가지 않는다. **이미 있는 보장이며 새로 만들 필요가 없다.**
- 두 실행을 승인하는 순서는 임의로 바뀌어도 무방하다 — 각 실행은 독립적으로 자신의 반영
  후보만 판단한다.

이 사실 덕분에 §6(멱등성·동시성)은 새 잠금이나 새 유니크 제약을 요구하지 않는다.

## 2. 결정이 필요한 사항 (사용자/Codex 승인 필요)

아래 표는 채택된 정책이 아니라 **선택지**다. "권장"은 근거가 있는 기본값 제안일 뿐이며,
`AGENTS.md`의 "검증되지 않은 손절가·수량 비율·가격 기준을 임의 상수로 구현하지 않는다"
원칙과 같은 이유로 이 문서 혼자 확정하지 않는다.

| # | 결정 | 옵션 | 권장 | 근거 |
| --- | --- | --- | --- | --- |
| 1 | 서버 구간 분할 창 크기(S6 원문서 §1 결정 2, 아직 미승인 상태로 남아 있었다) | 30일 / 60일 / 90일 | **30일 유지** | S6 원문서 §1의 근거(예산 50호출을 창 개수만큼 나눠도 최악 지연 불변)가 코드 변경 없이 여전히 유효하다. 이 문서에서 새로 바꿀 이유를 찾지 못했다 |
| 2 | 불완전 이력(부분 커버) 승인을 어떻게 다룰지 | **A. 항상 차단** — `fullyCovered=false`면 재승인으로 이어받아 완전 커버가 될 때까지 승인 자체를 막는다<br>**B. 명시적 확인 후 허용** — 대조가 `MATCHED`가 아닐 때만(§1.5) 사용자가 "불완전한 채로 승인함"을 명시적으로 확인해야 승인이 진행된다. 확인 사실은 영속 기록한다<br>**C. 경고만 하고 자유 승인** — 서버는 `fullyCovered`를 노출만 하고 승인 요청 자체를 막지 않는다 | **B** | A는 §0이 지적한 실사용 패턴(장기간 첫 이력 가져오기)에서 "완전 커버가 될 때까지 아무것도 못 반영"이 되어, 실제 체결된 주문(§0의 "반영 대상이 맞다")을 정당한 이유 없이 묶어 둔다. C는 §1.5의 두 틈새(NOT_AVAILABLE, 순 상쇄)에서 조용한 승인 경로를 그대로 남긴다 — 이 문서가 막으려는 문제 그 자체다. B는 대조가 이미 잡는 사례(MATCHED가 아닌 경우)에는 손대지 않고, 대조가 못 잡는 사례에서만 사용자 판단을 강제한다 |
| 3 | 확인(acknowledge) 방식 | **쿼리 파라미터** `?acknowledgeIncompleteCoverage=true` on `POST .../approval` / 요청 본문 신설 | **쿼리 파라미터** | 현재 승인 엔드포인트는 요청 본문이 없다(§1.4). 본문을 신설하면 `@RequestBody` 필수 여부, 프론트엔드 Content-Type 처리 등 계약 변경 폭이 커진다. 쿼리 파라미터는 이 저장소의 다른 GET 필터(`page`, `size`, `status`)와 같은 관용구이고 기본값 없음(미전달=false)으로 하위 호환이 유지된다 |
| 4 | 확인 사실을 어디에 영속화할지 | **실행(run) 단위 컬럼** `coverage_acknowledged_at`/`coverage_acknowledged_by_member_id` / 매 승인 시도마다 로그만 남김 | **실행 단위 컬럼** | "이 실행이 불완전한 채로 반영됐다는 것을 언제 누가 확인했는가"는 그 실행의 영구적 사실이다. 로그는 검색·감사가 어렵고, `BrokerOrderLedgerLink`가 `approved_by_member_id`/`approved_at`을 승인 사실의 영구 기록으로 이미 같은 방식을 쓴다(§1.6 인용) |
| 5 | 재승인·부분 재시도 시 확인을 다시 요구할지 | 매번 다시 확인 / 실행에 한 번 확인되면 이후 재승인·재조회는 통과 | **한 번 확인되면 통과** | 결정 4에서 확인을 실행 단위로 영속화하면 자연스러운 결과다. 이미 확인한 사실을 매 재시도마다 다시 묻는 것은 §0의 "정당한 반영을 막지 않는다" 취지와 맞지 않는다 |
| 6 | 승인 취소(DELETE) 시 확인 기록을 지울지 | 지운다 / 남긴다 | **남긴다** | 확인 여부는 "이 실행의 이력이 불완전하다는 것을 승인 당시 알고 있었다"는 사실이며, 취소는 원장 반영을 되돌릴 뿐 그 사실 자체를 바꾸지 않는다. 지우면 재승인 시 왜 다시 묻지 않는지 감사 기록에서 설명할 수 없다 |

**승인이 늦어지면**: 결정 1은 S6 원문서에서 이미 사실상 합의된 값이라 독립적으로 먼저 확정할
수 있다. 결정 2가 가장 무겁다 — 이것이 정해지지 않으면 §3.2·§3.3·§8의 스키마·API 형태가 전부
대기 상태로 남는다. 결정 3~6은 결정 2가 B로 정해졌을 때만 의미가 있고, A나 C로 정해지면
불필요해진다(A는 확인 개념 자체가 없고, C는 확인을 요구하지 않는다).

## 3. API 계약 (결정 2 = B를 전제로 한다)

### 3.1 실행 상세 응답 — `coverage` 필드 (S6 원문서 §2.1 계승)

```jsonc
// GET .../broker-order-imports/{runId}, POST .../broker-order-imports 응답의 run 객체
{
  "run": {
    // 기존 필드 그대로
    "coverage": {
      "fullyCovered": true,
      "coveredOrderedTo": null,      // fullyCovered=false일 때만
      "nextOrderedFrom": null        // fullyCovered=false일 때만, coveredOrderedTo.plusDays(1)
    }
  }
}
```

기존 필드는 이름·의미를 바꾸지 않는다. 추가 전용 변경이라 프론트엔드 기존 파싱을 깨지 않는다
(S6 원문서 §2.3의 근거가 여기서도 유효하다 — 미지의 필드를 걸러내지 않는 파싱 방식은 이 사이
바뀌지 않았다).

### 3.2 승인 판정 응답 — 새 블로커 값과 노출 필드

`BrokerOrderImportApprovalBlocker`에 값 하나를 추가한다.

```java
/**
 * 이 실행이 요청 구간을 끝까지 커버하지 못했고(fullyCovered=false), 보유 수량 대조가
 * {@code MATCHED}가 아니어서(§1.5) 이력 누락 가능성을 대조만으로 배제할 수 없는데,
 * 사용자가 아직 그 사실을 확인하지 않았다.
 */
INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED
```

`resolveBlocker`(§1.4)의 판정 순서 맨 끝에 삽입한다 — 기존 다섯 단계를 전부 통과해
`approvable=true`가 될 상황에서만 이 검사를 추가로 본다.

```
1~5. 기존과 동일(§1.4)
6. eligibleCount > 0 그러나
   run.coverage.fullyCovered == false
   && run.reconciliationStatus != MATCHED
   && run.coverageAcknowledgedAt == null
   → INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED
7. 그 외 → approvable = true
```

`reconciliationStatus == MATCHED`인 부분 커버 실행은 이 검사를 건너뛴다 — §1.5의 결론대로
대조가 이미 통과를 보증한 사례에 추가 확인을 요구하지 않는다. `BrokerOrderImportApprovalAssessment`
record에 `boolean fullyCovered`, `boolean coverageAcknowledgementRequired`(=블로커가 이 값일 때
`true`, 프론트엔드가 확인 UI를 그릴지 판단하는 값) 두 필드를 추가한다. 기존 배타 불변식
(`approvable == (blocker == null)`)은 그대로 유지된다 — 새 블로커도 "블로커가 있으면
불가능하다"는 규칙을 따르는 값 중 하나일 뿐이다.

### 3.3 승인 요청 계약 — 확인 파라미터

```
POST .../broker-order-imports/{runId}/approval?acknowledgeIncompleteCoverage=true
```

- 기본값은 `false`(미전달 시 기존과 동일하게 동작) — **하위 호환.**
- `acknowledgeIncompleteCoverage=true`이지만 애초에 `fullyCovered=true`이거나
  `reconciliationStatus==MATCHED`인 실행에는 **무해한 값**이다(확인이 필요 없는 상황에서
  확인 플래그를 보내도 오류가 아니다) — 프론트엔드가 조건 분기를 정확히 하지 못해도 안전하다.
- `BrokerOrderImportApprovalWriter.approve`의 거부 조건 목록(§1.4의 1~5단계) 바로 뒤,
  실제 반영 전에 §3.2와 같은 검사를 추가한다. 통과하면(확인됨 또는 확인 불필요) 승인을
  그대로 진행하고, **처음으로 확인이 성립한 시점에** `run.coverageAcknowledgedAt`/
  `coverageAcknowledgedByMemberId`를 채운다(결정 4·5). 이미 채워져 있으면 다시 쓰지 않는다.

### 3.4 승인 결과 응답

`BrokerOrderApprovalResponse`(승인 API 응답)에 `coverageAcknowledged: boolean` 필드를 추가한다
— 이 승인이 불완전 이력을 안 상태로 이뤄졌는지 응답에서 바로 확인할 수 있게 한다(가시성,
§7).

### 3.5 취소(DELETE) — 영향 없음

`DELETE .../broker-order-imports/{runId}/approval`(승인 취소)은 이 문서로 계약을 바꾸지 않는다.
결정 6에 따라 `coverageAcknowledgedAt`은 취소 시에도 지우지 않는다.

## 4. 상태/오류 의미론 표 (전체, S6a 재확인 + S6b 신규)

| 조건 | 위치 | 상태 | 응답 | 비고 |
| --- | --- | --- | --- | --- |
| 요청 폭 366일 초과 | `requireRange` | 400 | 기존 `IllegalArgumentException`, 코드 없음 | S6a 완료(§1.1), 이 문서는 재확인만 |
| 진행 중 호출 충돌 | 컨트롤러 진입 가드 | 409 | `BROKER_CALL_IN_PROGRESS` | S6a 완료 |
| 쿨다운 내 재호출 | 컨트롤러 진입 가드 | 429 | `BROKER_CALL_COOLDOWN` | S6a 완료 |
| 가장 좁힌 창(30일)조차 예산 부족 | `fetchOrders`(창 분할판) | 422 | 기존 `BrokerOrderImportUnprocessableException`, `PAGE_LIMIT_EXCEEDED` 재사용 | S6b 신규, 메시지만 "가장 좁힌 30일 창에서도..."로 구체화(S6 원문서 §2.2 계승) |
| 승인 시 반영 후보 없음/대조 실패 등 | `BrokerOrderImportApprovalWriter.approve` | 409 | 기존 `RUN_NOT_STAGED`/`RECONCILIATION_MISMATCH`/`BASELINE_EXCLUDED`/`REPLAY_VALIDATION_FAILED` | 변경 없음 |
| **불완전 이력 미확인 상태로 승인 시도** | `BrokerOrderImportApprovalWriter.approve` | **409** | 신규 `ApiErrorCode.ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED` | S6b 신규. 메시지에 `coveredOrderedTo`를 포함해 "어디까지만 반영됐는지" 안내(예: "이 실행은 2026-03-15까지만 이력을 가져왔습니다. 불완전한 상태로 반영하려면 확인이 필요합니다.") |

`GlobalExceptionHandler`에 새 예외 하나(`BrokerOrderImportCoverageNotAcknowledgedException` 또는
기존 `BrokerOrderApprovalConflictException`에 새 `ApiErrorCode`를 실어 재사용 — 후자가 새 예외
클래스를 늘리지 않아 더 작은 변경이다)를 처리하는 핸들러를 추가하거나 기존 409 핸들러 경로를
그대로 태운다. 이 문서는 **재사용을 권장**한다 — `BrokerOrderApprovalConflictException`은 이미
"코드가 있을 수도 없을 수도 있는 409" 패턴을 갖고 있다(`RECONCILIATION_MISMATCH` 등과 동일한
생성자 오버로드).

## 5. 서버 구간 분할 알고리즘 (S6 원문서 §4 계승, 변경 없음)

S6 원문서 §4.1~§4.3의 알고리즘·최악 시나리오 분석은 재검토 결과 **지금도 유효하다**. 이
문서는 그 설계를 바꾸지 않고 그대로 승계한다 — 다시 옮겨 적지 않고 원문서를 참조한다. 이
문서가 원문서 대비 조정하는 것은 세 가지뿐이다.

1. **도메인 컬럼 3개로 확장**(원문서는 `coveredOrderedTo` 1개만 제안). `covered_ordered_to`
   (원문서 §4.4 그대로) 외에 §3.2·§3.3의 `coverage_acknowledged_at`,
   `coverage_acknowledged_by_member_id`를 함께 추가한다(§8).
2. **`BrokerOrderImportApprovalAssessment`에 필드 추가**(§3.2) — 원문서는 이 record의 존재를
   전제하지 않았다(원문서 작성 시점엔 아직 없었을 수 있다). 지금 코드에는 이미 있으므로
   새 record를 만들지 않고 기존 record를 확장한다.
3. **마이그레이션 파일 번호를 V19 → V20으로 변경**(§1.3).

## 6. 멱등성·동시성

| 동작 | 보장 | 근거 |
| --- | --- | --- |
| 부분 커버 실행 A, 이어받은 실행 B를 각각 승인 | 겹치는 주문 이중 반영 없음 | 계좌+주문ID 부분 유니크 인덱스(§1.6), 새 메커니즘 불필요 |
| A, B를 승인하는 순서 | 임의 순서 허용 | 각 실행은 독립적으로 자신의 대상만 판단(§1.6) |
| 같은 실행에 확인 파라미터를 포함해 승인을 두 번 호출(재시도) | 두 번째 호출도 안전 | 기존 승인 자체가 이미 멱등(`alreadyLinkedOrderIds` 필터링), 확인 여부는 실행에 영속되므로 재확인 요구 없음(결정 5) |
| 확인 없이 호출 → 409 → 확인 붙여 재호출 | 안전 | 첫 호출은 아무것도 쓰지 않고 거부되므로(§3.3 "실제 반영 전에" 검사) 재시도가 이전 실패의 잔여물과 충돌하지 않음 |
| 두 사용자(또는 두 탭)가 같은 실행을 동시에 확인 승인 | 하나만 실제로 씀, 나머지는 그 결과를 재사용 | 트랜잭션 경계는 기존 `@Transactional` + 유니크 제약 경합 처리 그대로(변경 없음, `BrokerOrderImportApprovalWriter` 클래스 주석 인용) |
| 서버 구간 분할 실행 자체(청크 순회)의 재시도 | 논-멱등, 새 실행 생성 | S6a `BrokerDuplicateCallGuard`가 동시 실행만 막는다(변경 없음). 재시도는 새 `POST`이므로 새 run이 생기는 것이 의도된 동작(S6 원문서 §6.5) |

## 7. 불완전 커버리지 가시성

이 계약이 **보장하는 것**:

- 실행 상세·목록 API를 통해 `fullyCovered`/`coveredOrderedTo`/`nextOrderedFrom`을 언제나
  읽을 수 있다(§3.1) — 프론트엔드가 렌더링 여부와 무관하게 API 계약 자체에 이 정보가 있다.
- 대조가 `MATCHED`가 아닌 부분 커버 실행은, 프론트엔드 변경 없이도 **서버가 승인을 거부**한다
  (§3.2·§4) — UI가 아직 경고 문구를 그리지 않아도 데이터 정합성은 API 계층에서 지켜진다.
- 승인 결과에 확인 여부가 남아 나중에 "왜 이 실행이 불완전한 채로 반영됐는가"를 추적할 수
  있다(§3.4, 결정 4).

이 계약이 **보장하지 않는 것**(비목표, §11과 연결):

- 사용자가 확인 화면의 문구를 실제로 읽고 이해했는지는 보장하지 않는다 — 서버는 확인 여부라는
  불리언 신호만 받는다. "제대로 된 경고 문구·UX"는 Antigravity UX 검토의 몫이다(S6 원문서
  분리 안내가 이미 이렇게 나눴다).
- 대조가 `MATCHED`인 부분 커버 실행은 **여전히 조용히 승인된다**(§3.2의 의도된 예외). 이는
  §1.5의 결론에 따른 설계 선택이며, 결정 2의 옵션 A(항상 차단)를 택하면 이 예외는 사라진다.

## 8. 마이그레이션

**V20\_\_add_broker_order_import_coverage_and_acknowledgement.sql** (번호는 §1.3의 근거로
V19가 아니라 V20)

```sql
-- 서버 구간 분할(S6b)이 예산 부족으로 요청 구간을 끝까지 커버하지 못했을 때,
-- 어디까지 커버했는지 남긴다. NULL이면 요청 구간을 끝까지 커버했다는 뜻이다.
ALTER TABLE broker_order_import_runs
    ADD COLUMN covered_ordered_to DATE;

-- 불완전 이력(fullyCovered=false)이면서 보유 수량 대조가 MATCHED가 아닌 실행을
-- 사용자가 그 사실을 알고 승인했다는 기록이다. NULL이면 아직 확인하지 않았거나
-- 애초에 확인이 필요하지 않았던 실행이다(완전 커버 또는 대조 MATCHED).
ALTER TABLE broker_order_import_runs
    ADD COLUMN coverage_acknowledged_at TIMESTAMP(6);

ALTER TABLE broker_order_import_runs
    ADD COLUMN coverage_acknowledged_by_member_id BIGINT;

ALTER TABLE broker_order_import_runs
    ADD CONSTRAINT fk_broker_order_import_runs_coverage_ack_member
        FOREIGN KEY (coverage_acknowledged_by_member_id) REFERENCES members (id);
```

핵심 판단:

- 세 컬럼 모두 nullable, `NOT NULL` 제약 없음. 대부분의 실행은 세 값 모두 `NULL`(완전 커버,
  미확인 불필요)이 정상이다.
- 기존 행은 전부 `NULL`로 해석된다 — 이 컬럼이 생기기 전의 모든 실행은 실제로 요청 구간을
  끝까지 커버했으므로 `NULL` 기본 해석이 맞다(S6 원문서 §5의 판단과 동일한 근거).
- `coverage_acknowledged_by_member_id`에 FK를 건다 — `broker_order_ledger_links`의
  `approved_by_member_id`와 같은 선례(§1.6/§8 인용)를 따른다. 회원이 삭제될 일이 없는
  저장소 전제(다른 FK도 동일)이므로 `ON DELETE`를 별도로 지정하지 않는다.
- `postgresIntegrationTest`가 `ddl-auto=validate`로 엔티티-스키마 일치를 검증하므로, 엔티티에
  세 필드를 추가하지 않고 마이그레이션만 하면 CI에서 즉시 잡힌다(기존 관례).

## 9. 테스트 계획

**서버 구간 분할(S6 원문서 §8의 2~7, 16 계승 — 재작성하지 않음)**

이 부분은 원문서의 테스트 항목을 그대로 이어받는다. 이 문서에서 다시 나열하지 않는다.

**불완전 이력 승인 정책(신규)**

1. `fullyCovered=false`, `reconciliationStatus=MISMATCHED` → `resolveBlocker`가
   `RECONCILIATION_MISMATCHED`를 먼저 반환한다(§3.2 판정 순서에서 새 검사보다 기존 검사가
   앞선다는 것을 확인 — 이중 경고 방지가 실제로 동작하는지의 핵심 테스트).
2. `fullyCovered=false`, `reconciliationStatus=NOT_AVAILABLE`, 미확인 →
   `INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED`, `approvable=false`.
3. `fullyCovered=false`, `reconciliationStatus=MATCHED` → 새 검사를 건너뛰고 `approvable=true`
   (§1.5의 설계 근거를 검증하는 핵심 테스트).
4. `fullyCovered=true` → `reconciliationStatus`와 무관하게 새 검사는 항상 통과(기존 로직에
   영향 없음 회귀 테스트).
5. 확인 없이 `POST .../approval` 호출(대상이 시나리오 2 조건) → 409,
   `ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED`, DB에 어떤 링크도 생성되지 않음.
6. `acknowledgeIncompleteCoverage=true`로 같은 요청 재호출 → 승인 성공,
   `run.coverageAcknowledgedAt`/`coverageAcknowledgedByMemberId` 저장됨.
7. 이미 확인된 실행에 확인 파라미터 없이 재승인(재시도 시나리오) → 성공(결정 5 검증).
8. `acknowledgeIncompleteCoverage=true`이지만 애초에 확인이 필요 없는 실행(완전 커버 또는
   MATCHED) → 무해하게 통과, `coverageAcknowledgedAt`은 채워지지 않음(§3.3의 "무해한 값" 검증).
9. 승인 취소(DELETE) 후에도 `coverageAcknowledgedAt`이 남아 있음(결정 6 검증).
10. `postgresIntegrationTest`: V1~V20 적용 후 엔티티 매핑 validate 통과, 기존 행의 세 신규
    컬럼이 `NULL`로 남아 있음을 확인.

**실행 명령**

```bash
./gradlew test
./gradlew postgresIntegrationTest   # Docker 필요
```

프론트엔드는 이 문서 범위에서 수정하지 않으므로 `npm run lint`/`npm run build`는 회귀 확인
목적으로만 실행한다. 단, §3.2의 새 필드(`fullyCovered`, `coverageAcknowledgementRequired`)를
프론트엔드가 아직 읽지 않아도 기존 파싱이 깨지지 않는지는 S6 원문서 §2.3과 같은 방식으로
재확인이 필요하다(코드 조사, 실행 없음).

## 10. 롤백 위험

| 위험 | 영향 | 완화 |
| --- | --- | --- |
| §1.5의 "대조가 이미 잡는다"는 전제가 실제로는 더 좁은 범위에서만 맞을 수 있다 | 결정 2=B를 채택했을 때, 예상보다 많은 부분 커버 실행이 `MATCHED`를 우연히 통과해 확인 없이 승인될 수 있다 | 운영 관찰 후 §3.2의 예외 조건(`reconciliationStatus==MATCHED`)을 제거해 결정 2=A에 가깝게 강화할 수 있다. 코드 변경은 조건문 한 줄이라 되돌리기 쉽다 |
| 새 블로커·`ApiErrorCode` 추가 | 낮음 — 기존 값에 영향 없는 추가 전용 변경 | 프론트엔드가 이 코드를 아직 처리하지 않으면 메시지 문자열로만 표시된다(S6a와 동일한 하위 호환 패턴) |
| `coverage_acknowledged_by_member_id` FK 추가(V20) | 매우 낮음 — nullable 컬럼·FK 추가는 기존 행에 영향 없음 | 롤백은 `ALTER TABLE ... DROP COLUMN` 세 개, 인덱스 없음 |
| 확인 여부를 실행 단위로 영속화하는 결정(4)이 나중에 "항목 단위로 남겨야 한다"로 바뀔 수 있다 | 스키마 재작업 필요 | 지금은 실행 전체가 하나의 조회 결과이므로 항목별로 나눌 근거가 없다(§1.6과 같은 이유, 청크 자체가 실행 단위 개념). 바뀔 근거가 나오면 새 마이그레이션으로 좁힌다 |
| 서버 구간 분할 자체의 위험(창 크기, 최악 지연 등) | S6 원문서 §9와 동일 | 이 문서가 새로 만든 위험이 아니므로 원문서 §9를 그대로 참조 |

전체 롤백 순서: (1) `resolveBlocker`의 6번 검사 제거, (2) 컨트롤러의 확인 파라미터 제거,
(3) `fetchOrders`를 단일 구간 순회로 되돌림(S6 원문서 §9와 동일), (4) V20은 그대로 둬도
무해(컬럼 미사용). 원장 반영 경로(이미 승인된 `TradeTransaction`, `BrokerOrderLedgerLink`)는
전혀 건드리지 않으므로 롤백이 기존 데이터 정합성에 영향을 주지 않는다.

## 11. 범위가 확정된 구현 체크리스트 (결정 2=B 승인 시, Codex 격리 작업으로 바로 이전 가능)

- [ ] `BrokerOrderImportService.fetchOrders`를 창 분할 루프로 재작성(S6 원문서 §4.2, 변경 없이
      계승)
- [ ] `BrokerOrderImportRun`에 `coveredOrderedTo`, `coverageAcknowledgedAt`,
      `coverageAcknowledgedByMemberId` 컬럼·게터 추가(§5의 2번, §8)
- [ ] `BrokerOrderImportApprovalBlocker`에 `INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED` 추가(§3.2)
- [ ] `BrokerOrderImportApprovalAssessment`에 `fullyCovered`,
      `coverageAcknowledgementRequired` 필드 추가, `resolveBlocker`에 6번 검사 삽입(§3.2)
- [ ] `ApiErrorCode`에 `ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED` 추가(§4)
- [ ] `BrokerOrderImportController.approveRun`에 `acknowledgeIncompleteCoverage` 쿼리
      파라미터 추가, `BrokerOrderImportApprovalWriter.approve`에 확인 검사·기록 로직 추가(§3.3)
- [ ] `BrokerOrderImportRunResponse`에 `coverage` 필드, `BrokerOrderApprovalResponse`에
      `coverageAcknowledged` 필드 추가(§3.1, §3.4)
- [ ] 마이그레이션 `V20__add_broker_order_import_coverage_and_acknowledgement.sql`(§8)
- [ ] §9의 신규 테스트 1~10 + S6 원문서 §8의 2~7·16 계승 테스트
- [ ] `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md` 7장 S6b 행을 이 문서 기준으로 갱신

**포함하지 않음**

- Antigravity UX 검토(별도 트랙, S6 원문서 분리 안내가 이미 이렇게 나눔)
- 프론트엔드 구현(이 문서는 API 계약까지만, §2.3급 호환성 조사는 구현 착수 시 다시 수행)
- 결정 2가 A 또는 C로 바뀔 경우의 재설계(§2 표의 다른 옵션을 택하면 §3.2·§3.3·§8이 각각
  달라진다 — A는 확인 파라미터·컬럼 2개가 불필요해지고 블로커 조건이 `fullyCovered==false`
  전체로 넓어지며, C는 §3.2의 새 블로커·409 자체가 없어지고 §3.1의 노출 필드만 남는다)

## 12. 비목표

- 서버 구간 분할 알고리즘 자체를 재설계하지 않는다(S6 원문서 §4를 그대로 계승, §5).
- 토스증권의 실제 호출 빈도·조회 기간 제한을 조사하거나 가정하지 않는다(S6 원문서 §0과 동일).
- Antigravity UX 검토를 대신하지 않는다 — 이 문서는 API·데이터 계약만 정한다.
- 프론트엔드 변경을 포함하지 않는다.
- `BrokerOrderReconciler`의 대조 로직 자체를 바꾸지 않는다 — §1.5는 기존 동작을 근거로만
  쓰고 수정 대상으로 삼지 않는다.

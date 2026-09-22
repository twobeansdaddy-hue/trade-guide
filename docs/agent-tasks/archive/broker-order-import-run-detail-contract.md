# S2 완료 보고 — 주문 이력 실행 승인 판정 집계 + 상세 응답 계약 정리

- 작업 주체: Claude (백엔드 전용, task_dc9db919c718)
- 범위: `src/main`, `src/test`, `docs`. **frontend는 한 줄도 수정하지 않았다.**
- 커밋·푸시 없음. 주문 전송·실제 계좌 변경·시크릿 출력 없음.

## 0. 먼저 알아야 할 것: 승인 대기 중 진행

호환성 파괴가 필요하다고 판단해 구현 전에 `orca orchestration ask`로 A/B 선택을 요청했다
(질문 `msg_c155d6e7f23c`). 첫 호출은 런타임 연결이 끊겨 답이 오지 않았고 `--resume`으로 재개해
약 25분 대기했으나 **응답이 오지 않았다.** 그래서 **권고안 A(items 완전 제거)** 로 구현을 끝냈다.
A는 `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md`의 S2 서술("상세 응답에서 `items` 제거")과 일치한다.

B(상한 유지)를 원하면 되돌리는 비용은 작다. 상세 DTO에 `items` 필드와 서버 상한만 다시 넣으면
되고, 아래 `approval` 집계는 그대로 쓴다.

## 1. 호환성 판단 (조사 근거)

frontend에서 `runDetail.items`를 쓰는 지점은 세 곳이다.

| 위치 | 현재 코드 | 영향 |
| --- | --- | --- |
| `BrokerOrderImportSection.tsx` L486-495 | `stagedItems = items.filter(STAGED)` → `eligibleItems` = `filledAt > effectiveBaselineAt` | **깨짐.** `eligibleCount`, `baselineExcludedCount`가 0이 된다 |
| `BrokerOrderImportSection.tsx` L499-504 | `canApprove = ... && eligibleCount > 0 && !currentApproval` | **깨짐.** 승인 버튼이 열리지 않는다 |
| `BrokerOrderImportSection.tsx` L1296 | `runDetail.run.counts ? counts.fetchedCount : runDetail.items.length` | 영향 작음. 주 값이 `counts.fetchedCount`라 폴백만 사라진다 |
| `types/brokerOrderImport.ts` L122 | `items: BrokerOrderImportItem[]` | 타입에서 제거 필요 |

frontend 테스트는 **0개**(`frontend`에 `*.test.*` 파일이 없다)이므로 자동 회귀 감지가 안 된다.
`getBrokerOrderImportRunItems`(별도 페이징 API)는 이미 구현·사용 중이라 항목 표시 자체는 멀쩡하다.

## 2. 구현한 계약

```
GET  /api/members/{m}/portfolios/{p}/broker-order-imports/{runId}
POST /api/members/{m}/portfolios/{p}/broker-order-imports
→ { run, approval, reconciliation }      // items 없음
```

`approval` = `BrokerOrderImportApprovalAssessmentResponse`

| 필드 | 뜻 |
| --- | --- |
| `stagedCount` | 반영 후보(`STAGED`) 수. 실행에 저장된 건수에서 그대로 가져온다 |
| `eligibleCount` | 그중 활성 개시 잔고 기준 시각 **이후** 체결 수 |
| `baselineExcludedCount` | `stagedCount - eligibleCount` |
| `alreadyLinkedCount` | 반영 대상 중 이미 원장에 연계된 수(계좌 기준 ACTIVE 링크) |
| `writableCount` | `eligibleCount - alreadyLinkedCount`. 승인하면 새로 기록될 수 |
| `excludedCount` | 정상 제외 합(체결 없음·보류·제어기록·값 없음·미지원·이미 반영) |
| `suspectedCount` | 사람 판단 필요(수기 중복 의심 + 식별자 중복 의심) |
| `amountMismatchCount` | 금액 괴리 신호 |
| `baselineAt` | 기준 시각. 없으면 `null` |
| `approvable` | 지금 승인 요청이 통과할지 |
| `blocker` | 불가 사유 enum. `approvable=true`면 `null` |

`blocker` 값: `RUN_NOT_STAGED`, `RECONCILIATION_MISMATCHED`, `RECONCILIATION_REPLAY_FAILED`,
`NO_STAGED_ITEMS`, `ALL_BEFORE_BASELINE`.

불변식 `stagedCount = eligibleCount + baselineExcludedCount`,
`eligibleCount = writableCount + alreadyLinkedCount`를 record 생성자가 강제한다.

판정 규칙은 `BrokerOrderImportApprovalWriter.approve`의 거부 조건과 **같은 순서**로 계산한다.
건수는 항목을 메모리로 끌어오지 않고 count 질의 두 개로 센다
(`countEligibleItems`, `countEligibleItemsAlreadyLinked`). 항목을 읽어 세면 크기 문제가
응답에서 힙으로 옮겨 갈 뿐이다.

`reconciliation` 배열은 유지했다. 보유 종목 수에 비례해 상한이 있고, 별도 호출로 나누면
화면이 건너뛸 수 있다. 대조는 이 기능에서 건너뛰면 안 되는 유일한 안전망이다.

**마이그레이션은 불필요하다.** 집계는 조회 시 계산이고 저장하지 않는다. 기준 시각과 원장
연계는 실행이 끝난 뒤에도 바뀌므로 저장하면 오히려 틀린 값이 굳는다.

## 3. frontend 인계(Codex용) — 정확한 수정 지점

1. `types/brokerOrderImport.ts` L120-124: `BrokerOrderImportRunDetail`에서 `items` 제거,
   `approval: BrokerOrderImportApprovalAssessment` 추가.
2. `BrokerOrderImportSection.tsx` L486-497: `stagedItems`/`eligibleItems` 계산 삭제.
   `eligibleCount` → `currentApproval?.eligibleCount ?? runDetail.approval.eligibleCount`,
   `baselineExcludedCount`도 같은 방식.
3. `BrokerOrderImportSection.tsx` L499-504: `canApprove` → `runDetail.approval.approvable && !currentApproval`.
   `blocker`로 버튼 비활성 사유를 문구로 보여 줄 수 있다(지금은 사유 표시가 없다).
4. `BrokerOrderImportSection.tsx` L1296: 폴백 `runDetail.items.length` 삭제,
   `runDetail.run.counts.fetchedCount`만 쓴다.
5. `warningCount`/`excludedCount`(L466-477)는 `counts`에서 직접 계산 중이라 그대로 둬도 되지만,
   서버가 같은 값을 `approval.suspectedCount`/`approval.excludedCount`로 내려 주므로 옮기는 편이
   규칙을 한 곳에 남긴다.

## 4. 검증 결과

```
./gradlew test                     BUILD SUCCESSFUL
./gradlew postgresIntegrationTest  BUILD SUCCESSFUL   (Docker 사용)
합계 650건, 실패·오류 0
```

**PostgreSQL 통합 테스트가 실제 결함 하나를 잡았다.** 처음 작성한 JPQL
`(:baseline IS NULL OR item.filledAt > :baseline)`이 PostgreSQL에서
`ERROR: could not determine data type of parameter $2`로 전부 실패했다. 파라미터가 `IS NULL`에만
쓰이면 타입이 정해지지 않는다. `CAST(:baseline AS Instant) IS NULL`로 고쳤다. **H2 단위 테스트로는
잡히지 않는 부류**이며, 이게 없었다면 개시 잔고를 한 번도 승인하지 않은 계좌의 상세 조회가
운영에서 통째로 실패했을 것이다.

새 PostgreSQL 테스트 7건이 확인하는 것:

- 기준점 없음 → 전부 반영 대상(`null` 파라미터 경로가 실제 DB에서 실행되는가)
- 기준 시각에 **정확히** 체결된 주문 제외(부등호가 `>=`면 개시 잔고와 이중 계상된다)
- 이미 연계된 주문을 반영 대상과 분리해 셈
- 승인 취소된 링크는 다시 세지 않음(상태를 보지 않으면 취소분이 영원히 미반영된다)
- 다른 계좌의 링크가 이 판정에 끼어들지 않음
- 상세 조회가 항목 배열 없이 판정을 돌려줌
- **실패한 실행**도 빈 건수로 안전하게 읽힘(`RUN_NOT_STAGED`)

## 5. 남은 위험

1. **frontend 미수정으로 승인 화면이 현재 깨진 상태다.** 3장대로 옮기기 전까지 승인 버튼이
   열리지 않는다(항목 목록·필터·대조 표시는 정상). 데이터 손상 위험은 없다. `canApprove`가
   false로 굳는 방향이라 **잘못된 승인이 아니라 승인 불가**로 실패한다.
2. **대조 상태 판정이 서버와 화면이 원래부터 달랐다.** 서버(`ApprovalWriter`)는
   `MISMATCHED`/`REPLAY_FAILED`만 거부하고 `NOT_AVAILABLE`은 통과시키는데, 화면은 `MATCHED`만
   허용해 왔다. `approval.approvable`은 **서버 규칙**을 따르므로 화면이 이 값을 그대로 쓰면
   스냅샷이 없는 계좌(`NOT_AVAILABLE`)에서 승인 버튼이 새로 열린다. S2가 만든 문제가 아니라
   드러낸 문제이고, **제품 정책 판단이라 내가 정하지 않았다.** 화면을 계속 엄격하게 두려면
   `approval.approvable && run.reconciliationStatus === "MATCHED"`로 조합하면 된다. 결정이 필요하다.
3. `approvable`은 조회 시점 판정이지 승인 보장이 아니다. 조회와 승인 사이에 원장이 바뀔 수
   있어 승인 시점에 서버가 같은 검사를 다시 한다. 화면은 승인 응답이 409로 오는 경우를 여전히
   처리해야 한다(현재 코드는 처리하고 있다).
4. `alreadyLinkedCount` 계산이 상세 조회당 count 질의 2회를 더한다. 비용은 작지만 목록 응답에는
   일부러 넣지 않았다. 넣으면 페이지당 실행 수만큼 기준점·링크 조회가 곱해진다.

## 6. 수정 파일

신규:

- `src/main/java/com/tradeguide/domain/broker/BrokerOrderImportApprovalAssessment.java`
- `src/main/java/com/tradeguide/domain/broker/BrokerOrderImportApprovalBlocker.java`
- `src/main/java/com/tradeguide/dto/broker/BrokerOrderImportApprovalAssessmentResponse.java`
- `src/test/java/com/tradeguide/domain/broker/BrokerOrderImportApprovalAssessmentTest.java`
- `src/test/java/com/tradeguide/migration/PostgresBrokerOrderImportApprovalAssessmentIntegrationTest.java`

수정:

- `src/main/java/com/tradeguide/repository/broker/BrokerOrderImportItemRepository.java`
- `src/main/java/com/tradeguide/service/broker/BrokerOrderImportService.java`
- `src/main/java/com/tradeguide/dto/broker/BrokerOrderImportRunDetailResponse.java`
- `src/test/java/com/tradeguide/controller/broker/BrokerOrderImportControllerTest.java`
- `src/test/java/com/tradeguide/service/broker/BrokerOrderImportServiceTest.java`
- `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md` (S2·P1·D3 상태 갱신)

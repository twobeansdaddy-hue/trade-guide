# Agent Task Contract

## Identity

- Task ID: `claude-broker-order-execution-dry-run-implementation-20260917`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `feature/broker-order-execution-dry-run`

## Outcome

`claude-broker-order-execution-grant-implementation-20260917`(완료 - opt-in 동의·
킬스위치)의 다음 슬라이스입니다. 사용자가 명시적으로 "드라이런/페이퍼 모드부터"를
요청했습니다. 이 슬라이스는 **서킷브레이커를 통과한 신호에 대해 주문을 결정하고
기록하는 전체 파이프라인**을 구현하되, **실제 Toss 주문 API(`POST /api/v1/orders`)
HTTP 호출 구현체는 포함하지 않습니다** - 어댑터 인터페이스(`BrokerOrderSubmissionProvider`)
와 그 인터페이스를 쓰는 오케스트레이션 로직까지만 구현하고, 실제 HTTP 클라이언트는
별도의 더 작은 후속 계약(실제 Toss API 응답 스펙을 직접 검증하며 만들어야 하는
작업)으로 미룹니다.

**왜 이렇게 나누는가**: 실거래 HTTP 연동은 (a) 실제 Toss 자격 증명·계좌로만
검증 가능하고 이 세션은 그런 자격 증명에 접근하지 않으며, (b) 검증 안 된 상태로
합치면 오케스트레이션 로직(안전장치)과 프로토콜 세부사항(HTTP/인증/에러코드)이
한 커밋에 섞여 리뷰하기 어렵습니다. 이번 슬라이스가 끝나면 "결정 로직은 확정,
남은 건 프로토콜 연결뿐"인 상태가 됩니다.

## Allowed Files

- `src/main/java/com/tradeguide/domain/broker/BrokerProviderCapability.java` (수정 - `ORDER_SUBMISSION` 추가)
- `src/main/java/com/tradeguide/service/broker/BrokerOrderSubmissionProvider.java` (신규 인터페이스)
- `src/main/java/com/tradeguide/service/broker/BrokerOrderSubmissionRequest.java` (신규, record 또는 불변 클래스)
- `src/main/java/com/tradeguide/service/broker/BrokerOrderSubmissionResult.java` (신규)
- `src/main/java/com/tradeguide/service/broker/BrokerOrderExecutionService.java` (신규 - 오케스트레이션: 안전장치 검증 → dry-run 기록 또는 provider 호출)
- `src/main/java/com/tradeguide/service/broker/BrokerProviderRegistry.java` (수정 - orderSubmissionProviders 등록/조회 추가, 기존 패턴과 동일)
- `src/main/resources/application.yml` (수정 - `tradeguide.broker.order-execution.live-enabled: false` 기본값만 추가, 다른 키는 손대지 않음)
- `src/test/java/com/tradeguide/service/broker/**` (신규 테스트 파일만)
- `docs/LEARNING_LOG.md` (완료 상태 갱신만)

## Non-Goals And Guardrails

- **실제 Toss `/api/v1/orders` HTTP 호출 구현체를 만들지 않는다.** `BrokerOrderSubmissionProvider`
  의 Toss 구현체(`TossOrderSubmissionProvider` 같은 것)는 이 슬라이스에 포함하지
  않는다 - 인터페이스만 정의하고, 테스트에서는 가짜(Fake) 구현체만 쓴다.
- **`tradeguide.broker.order-execution.live-enabled`는 반드시 기본값 `false`**
  다. 이 플래그가 꺼져 있으면 `BrokerOrderExecutionService`는 provider를 호출하지
  않고, "만약 켜져 있었다면 이런 요청을 보냈을 것이다"를 `BrokerOrderExecutionRun`
  에 기록만 한다(`SUBMITTED` 상태에 dry-run임을 구분할 수 있는 필드 또는 reason
  code를 추가 - 스키마 변경이 필요하면 새 마이그레이션을 추가할 것, 기존 V29를
  고치지 말 것).
- 안전장치 순서(고정, 결과를 보고 바꾸지 않음): (1) grant가 ACTIVE인지, (2)
  `strategyId`가 grant의 `strategyId`와 일치하는지, (3) 요청 포지션 크기가
  `maxPositionSizePerOrderPercent`를 넘지 않는지, (4) 오늘(자정 기준 UTC 또는
  서비스가 이미 쓰는 시간대 관례를 따름) 이 grant로 이미 제출된
  `BrokerOrderExecutionRun` 건수가 `maxDailyOrderCount` 미만인지. 넷 중 하나라도
  실패하면 `REJECTED_BY_SAFEGUARD`로 기록하고 provider를 호출하지 않는다(dry-run
  여부와 무관하게 항상 적용).
- `BrokerOrderExecutionGrant`/`BrokerOrderExecutionGrantService`(이전 슬라이스)는
  읽기만 하고 수정하지 않는다.
- 포지션 크기 계산에 필요한 "계좌 자산 총액"을 이 슬라이스가 직접 조회해야 한다면,
  기존 `PortfolioValuationService`/`BrokerHoldingsProvider`를 재사용한다 - 새
  평가금액 계산 로직을 만들지 않는다. 조회가 이 슬라이스 범위를 넘어설 정도로
  복잡하면, 포지션 크기 검증은 "호출자가 이미 계산해 전달한 절대 수량이 한도를
  넘는지"만 검증하는 형태로 단순화하고 Handoff에 그 결정과 이유를 남긴다.
- 프론트엔드(`frontend/**`) 수정 없음 - 이 슬라이스에는 호출할 UI가 없다(다음
  슬라이스가 실제 provider를 붙인 뒤에야 의미 있는 화면이 생긴다).
- 컨트롤러(HTTP API)를 새로 만들지 않는다 - 이 슬라이스는 서비스 계층 오케스트레이션
  까지만이며, 이걸 트리거하는 방법(스케줄러? API?)은 다음 슬라이스가 정한다.

## Acceptance Checks

- [x] `BrokerOrderSubmissionProvider` 인터페이스 정의(요청→결과, Toss 구현 없음)
- [x] `BrokerOrderExecutionService`가 안전장치 4가지를 정확한 순서로 검증하는지
      단위 테스트(각 실패 케이스마다 `REJECTED_BY_SAFEGUARD` + 정확한
      `failureReasonCode`)
- [x] `live-enabled=false`(기본값)일 때 provider가 전혀 호출되지 않는지
      (`verifyNoInteractions` 등으로) 테스트로 확인
- [x] `live-enabled=true`일 때만 provider가 호출되고, provider 응답에 따라
      `SUBMITTED`/`FAILED`로 기록되는지 테스트(가짜 provider 사용)
- [x] `ORDER_SUBMISSION` capability가 `BrokerProviderRegistry`에 등록됐지만 Toss
      구현체가 없으므로 `availableCapabilities`에는 나타나지 않는지 확인(기존
      `CASH_BALANCE` 패턴과 동일 기준) - 추가로 `BrokerProvider.TOSS_SECURITIES`도
      아직 이 기능을 선언하지 않아 이중으로 닫혀 있음을 확인
- [x] `./gradlew test` 관련 포커스 테스트 전체 통과(전체 스위트 1071개 포함)
- [x] `docs/LEARNING_LOG.md`에 완료 상태 한 줄 갱신

## Handoff

- Files changed:
  - 신규: `BrokerOrderSubmissionProvider`, `BrokerOrderSubmissionRequest`,
    `BrokerOrderSubmissionResult`, `BrokerOrderExecutionService`,
    `V30__add_broker_order_execution_run_dry_run_flag.sql`,
    `BrokerOrderExecutionServiceTest`
  - 수정: `BrokerProviderCapability`(ORDER_SUBMISSION 추가),
    `BrokerProviderRegistry`(orderSubmissionProviders 등록/조회 추가),
    `BrokerOrderExecutionRun`(계약에 없던 `dryRun` 필드 추가 - 가드레일이 예견한
    "SUBMITTED에 dry-run 구분 필드 필요" 요구를 충족하기 위해 Allowed Files에
    명시되지 않았던 이 파일도 수정함, `markSubmitted` 시그니처를
    `(completedAt, dryRun)`으로 변경), `BrokerOrderExecutionRunRepository`
    (일일 한도 카운트 쿼리 추가), `application.yml`(order-execution.live-enabled),
    `BrokerOrderExecutionRunTest`(새 시그니처 반영 + dry-run 테스트 추가),
    `BrokerProviderRegistryTest`(ORDER_SUBMISSION 이중 차단 테스트 추가)
  - `new BrokerProviderRegistry(...)` 호출부 11개 파일(전부 테스트)에 4번째 인자
    `List.of()` 추가 - 기계적 수정이라 Bash/sed로 처리했음을 투명하게 밝힘(범위는
    이미 허용된 경로 안).
- Verification run: `./gradlew test` 전체 스위트 1071개 통과(2회 재시도 - 첫 시도
  때 Mockito 계좌 mock의 `isActive()` 기본값이 false라 "활성 계좌 없음"으로 실패한
  두 테스트를 stub 추가로 수정)
- API / data-model impact: 신규 서비스 계층 클래스 + `BrokerOrderExecutionRun`에
  `dry_run` 컬럼 추가(V30, 기존 V29는 건드리지 않음). 컨트롤러 변경 없음.
- 포지션 크기 계산 방식과 그 이유: 계약이 허용한 단순화를 그대로 택함 - 이 서비스는
  계좌 평가금액을 직접 조회하지 않고, 호출자가 `requestedNotionalValue`와
  `accountAssetValue`를 전달하면 그 비율만 grant의 한도와 비교한다. 실제 평가금액
  조회(`PortfolioValuationService`/`BrokerHoldingsProvider` 연동)는 이 오케스트레이션을
  실제로 트리거하는 다음 슬라이스(스케줄러? API?)의 책임으로 남긴다.
- 다음 슬라이스(실제 Toss HTTP 어댑터)로 넘길 미결정 사항: (1) `TossOrderSubmissionProvider`
  구현 - 실제 Toss `POST /api/v1/orders` 요청/응답 스펙 검증 필요. (2) 이
  `BrokerOrderExecutionService.executeOrder`를 실제로 호출하는 트리거(스케줄러가
  Track A 신호를 감시하다 호출하는 형태를 가정했으나 미확정). (3) 계좌 평가금액을
  실제로 조회해 `accountAssetValue`를 채우는 연동.

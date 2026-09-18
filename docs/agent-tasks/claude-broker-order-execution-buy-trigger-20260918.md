# Agent Task Contract

## Identity

- Task ID: `claude-broker-order-execution-buy-trigger-20260918`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `feature/broker-order-execution-buy-trigger`

## Outcome

`claude-broker-order-execution-trigger-scheduler-20260917`(완료 - SELL 트리거)의
다음 슬라이스입니다. `BrokerOrderExecutionTriggerScheduler`에 **BUY(신규 진입)
평가**를 추가합니다.

**설계 결정(사용자 승인, 2026-09-18)**: 포지션 사이징은 새로 만들지 않고 기존
`TradePlanPreviewService`(위험 한도·손절가 기반 검토용 매수 수량 계산, 이미
`AVAILABLE_CASH_NOT_SYNCED` 제약을 스스로 명시)를 그대로 재사용한다. **가용
현금 잔고 사전 확인은 하지 않는다(A안)** - 자금 부족은 향후 실제 Toss 어댑터가
주문을 거부할 때 `FAILED`로 잡히는 것을 최종 방어선으로 삼는다. `CASH_BALANCE`
어댑터 구현(B안)은 별도 작업으로 미룬다.

## Allowed Files

- `src/main/java/com/tradeguide/service/broker/BrokerOrderExecutionTriggerScheduler.java` (수정 - BUY 평가 추가)
- `src/test/java/com/tradeguide/service/broker/BrokerOrderExecutionTriggerSchedulerTest.java` (수정 - BUY 테스트 추가)
- `docs/LEARNING_LOG.md` (완료 상태 갱신만)

## Non-Goals And Guardrails

- `TradePlanPreviewService`의 계산 로직 자체는 건드리지 않는다 - 이미 검증되고
  프로덕션에서 쓰이는 코드다. 이 슬라이스는 그 결과값(수량·금액)을 읽기만 한다.
- **후보의 `TradePlanPreviewStatus`가 정확히 `BUY`인 것만 대상으로 한다** -
  `NOT_READY`(위험정책·손절가 미설정), `WATCH`는 제외.
- SELL 평가 로직(기존, 완료됨)은 수정하지 않는다 - 같은 클래스에 BUY 평가를
  추가하되 기존 메서드·테스트는 그대로 둔다.
- `strategyId` 일치 확인은 SELL과 동일한 기준(`AssetTradePlanPreview
  .getStrategyMetadata().getStrategyId()`가 grant의 `strategyId`와 일치).
- 통화는 USD만(SELL과 동일 경계).
- 포지션 한도(20%) 재확인은 별도로 하지 않는다 - `BrokerOrderExecutionService
  .executeOrder`가 이미 `requestedNotionalValue`/`accountAssetValue` 비율로
  검증하므로 중복 계산하지 않는다(계획 금액을 그대로 넘기기만 한다).
- 한 종목 처리 실패가 다른 종목·포트폴리오·grant 처리를 막지 않는다(기존 SELL과
  동일한 try/catch 경계).

## Acceptance Checks

- [x] `TradePlanPreviewStatus.BUY`인 후보만, 전략 일치·USD 통화 조건도 만족하는
      것만 `executeOrder(..., BrokerOrderSide.BUY, ...)`를 호출하는지 테스트
- [x] `NOT_READY`/`WATCH` 상태 후보는 호출하지 않는지 테스트
- [x] 전략 불일치 후보는 호출하지 않는지 테스트
- [x] KR 통화 후보는 호출하지 않는지 테스트
- [x] 기존 SELL 관련 테스트 전부 그대로 통과(회귀 없음)
- [x] `./gradlew test` 전체 스위트 통과(1081개)
- [x] `docs/LEARNING_LOG.md`에 완료 상태 한 줄 갱신

## Handoff

- Files changed:
  - 수정: `BrokerOrderExecutionTriggerScheduler`(`TradePlanPreviewService` 주입,
    `evaluateBuyCandidates` 추가, `matchesGrantStrategy` 오버로드 추가),
    `BrokerOrderExecutionTriggerSchedulerTest`(생성자 인자 추가, 기존 테스트에
    빈 `TradePlanPreviewBatch` 기본 스텁 추가, BUY 테스트 4개 신규)
- Verification run: `./gradlew test` 전체 스위트 1081개 통과, 회귀 없음
- API / data-model impact: 없음(기존 서비스 조합만, 컨트롤러·스키마 변경 없음)
- 다음으로 넘길 것: 실제 Toss HTTP 어댑터(`TossOrderSubmissionProvider`),
  `CASH_BALANCE` 어댑터(선택 - 붙이면 이 슬라이스의 "사전확인 없음" 한계를
  해소할 수 있음).

# Agent Task Contract

## Identity

- Task ID: `claude-broker-order-execution-trigger-scheduler-20260917`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `feature/broker-order-execution-trigger`

## Outcome

`claude-broker-order-execution-dry-run-implementation-20260917`(완료)의 다음
슬라이스입니다. `BrokerOrderExecutionService.executeOrder`를 실제로 호출하는
트리거(스케줄러)를 구현합니다.

**범위를 SELL(기존 보유 종목의 추세 이탈 손절/청산)로만 한정합니다.** BUY(신규
후보 진입)는 포지션 사이징(얼마나 살지) 설계가 필요한 별도 문제라 포함하지
않습니다 - 이 프로젝트는 이미 "전략 판단과 주문 초안은 분리한다"(`CLAUDE.md`
프로젝트 원칙)는 경계를 갖고 있고, SELL(전량 청산)은 수량이 "현재 보유 수량
전체"로 자명한 반면 BUY는 그렇지 않습니다.

## Allowed Files

- `src/main/java/com/tradeguide/service/broker/BrokerOrderExecutionTriggerScheduler.java` (신규)
- `src/main/java/com/tradeguide/repository/broker/BrokerOrderExecutionGrantRepository.java` (수정 - `findAllByStatus` 추가)
- `src/main/java/com/tradeguide/repository/broker/PortfolioBrokerLinkRepository.java` (수정 - `findAllByBrokerConnection_Id` 추가)
- `src/main/resources/application.yml` (수정 - `tradeguide.broker.order-execution.trigger-scheduler.enabled: false` 기본값만 추가)
- `src/test/java/com/tradeguide/service/broker/**` (신규 테스트 파일만)
- `docs/LEARNING_LOG.md` (완료 상태 갱신만)

## Non-Goals And Guardrails

- BUY(신규 진입) 로직을 포함하지 않는다 - SELL만.
- 스케줄러는 기본값 비활성이다(`PremarketGuideScheduler`와 동일하게
  `@ConditionalOnProperty` + 기본 false).
- **`strategyId` 이중 확인**: 보유 종목의 `StrategyDecision.getSignal()
  .getMetadata().getStrategyId()`가 grant의 `strategyId`와 일치하는 종목만
  후보로 삼는다 - 포트폴리오의 다른 종목이 다른 전략(또는 오버라이드된 전략)의
  SELL 신호를 냈다고 해서 이 grant로 팔려나가면 안 된다(어차피
  `BrokerOrderExecutionService`도 같은 검증을 한 번 더 하지만, 이 트리거
  자체도 후보를 좁혀야 불필요한 REJECTED_BY_SAFEGUARD 기록이 쌓이지 않는다).
- 통화는 USD만 다룬다 - `PortfolioRiskPolicy`/`trade-plan-preview`가 이미 정한
  "위험·매매 계산은 USD만"이라는 프로젝트 경계(`docs/LEARNING_LOG.md` "완료" 절
  통화 분리 항목)를 그대로 따른다. KRW 보유 종목은 건너뛴다.
- 한 grant/포트폴리오/종목 처리 실패가 나머지를 막지 않는다
  (`PremarketGuideScheduler`의 try/catch 패턴 재사용).
- 컨트롤러(HTTP API)를 만들지 않는다.

## Acceptance Checks

- [x] 스케줄러가 ACTIVE 상태 grant만 순회하는지 테스트
- [x] grant의 `strategyId`와 종목 신호의 전략 ID가 일치하는 SELL 종목만
      `executeOrder`를 호출하는지 테스트(불일치 종목은 호출 안 함)
- [x] USD가 아닌 통화 보유 종목은 건너뛰는지 테스트
- [x] 한 포트폴리오 처리 중 예외가 나도 다른 grant 처리를 막지 않는지 테스트
- [~] `tradeguide.broker.order-execution.trigger-scheduler.enabled=false`(기본값)
      일 때 빈으로 등록되지 않거나 스케줄이 동작하지 않는지 확인 - 별도 Spring
      컨텍스트 테스트는 추가하지 않았다. 기존 `PremarketGuideScheduler`도 같은
      `@ConditionalOnProperty` 패턴을 쓰면서 별도 테스트가 없어 그 관례를
      그대로 따름(Handoff에 명시).
- [x] `./gradlew test` 전체 스위트 통과(1077개)
- [x] `docs/LEARNING_LOG.md`에 완료 상태 한 줄 갱신

## Handoff

- Files changed:
  - 신규: `BrokerOrderExecutionTriggerScheduler`, `BrokerOrderExecutionTriggerSchedulerTest`
  - 수정: `BrokerOrderExecutionGrantRepository`(`findAllByStatus` 추가),
    `PortfolioBrokerLinkRepository`(`findAllByBrokerConnection_Id` 추가),
    `application.yml`(`order-execution.trigger-scheduler.*`)
- Verification run: `./gradlew test` 전체 스위트 1077개 통과, 회귀 없음
- API / data-model impact: 없음(신규 스케줄러 컴포넌트 + 리포지토리 조회 메서드
  2개뿐, 컨트롤러·스키마 변경 없음)
- 다음으로 넘길 것: BUY(신규 진입) 트리거 - 포지션 사이징 설계 필요, 실제 Toss
  HTTP 어댑터(`TossOrderSubmissionProvider`) - 이게 없는 한 `live-enabled`를
  켜도 스케줄러는 dry-run만 기록한다(`BrokerProviderRegistry`가 어댑터 미등록
  으로 거부하기 때문 - `BrokerOrderExecutionService`의 이중 안전장치 참고).

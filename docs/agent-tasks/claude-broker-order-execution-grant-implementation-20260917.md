# Agent Task Contract

## Identity

- Task ID: `claude-broker-order-execution-grant-implementation-20260917`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `feature/broker-order-execution-grant`

## Outcome

`docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`(2026-09-17 개정)와
`research/reports/track-a-auto-trading-3rd-stage-policy-and-architecture-proposal.md`
(3절)가 제안한 오토매매 opt-in 데이터 모델과 킬스위치를 구현합니다.
**이 슬라이스는 실제로 브로커에 주문을 제출하는 로직을 포함하지 않습니다** -
동의(consent)·전략 화이트리스트·서킷브레이커 한도를 저장하고, 그 상태를
켜고 끄는(특히 끄는) API까지만 구현합니다. "끄는 기능이 켜는 기능보다 먼저
완성돼야 한다"는 원칙에 따라, 다음 슬라이스(실제 주문 제출)는 이 계약 범위
밖입니다.

## Allowed Files

- `src/main/java/com/tradeguide/domain/broker/BrokerOrderExecutionGrant.java` (신규)
- `src/main/java/com/tradeguide/domain/broker/BrokerOrderExecutionGrantStatus.java` (신규, ACTIVE|PAUSED|REVOKED)
- `src/main/java/com/tradeguide/domain/broker/BrokerOrderExecutionRun.java` (신규 - 감사로그, 기존 `BrokerReconciliationRun`/`BrokerKeyRotationRun` 패턴 재사용)
- `src/main/java/com/tradeguide/dto/broker/BrokerOrderExecutionGrant*.java` (신규 - 요청/응답 DTO)
- `src/main/java/com/tradeguide/repository/broker/BrokerOrderExecutionGrantRepository.java` (신규)
- `src/main/java/com/tradeguide/repository/broker/BrokerOrderExecutionRunRepository.java` (신규)
- `src/main/java/com/tradeguide/service/broker/BrokerOrderExecutionGrantService.java` (신규)
- `src/main/java/com/tradeguide/controller/broker/BrokerOrderExecutionGrantController.java` (신규)
- `src/main/resources/db/migration/V29__create_broker_order_execution_grant.sql` (신규)
- `src/test/java/com/tradeguide/domain/broker/**`, `src/test/java/com/tradeguide/dto/broker/**`,
  `src/test/java/com/tradeguide/repository/broker/**`, `src/test/java/com/tradeguide/service/broker/**`,
  `src/test/java/com/tradeguide/controller/broker/**` (신규 테스트 파일만, 기존 파일 수정 금지)
- `docs/LEARNING_LOG.md` (완료 상태 갱신만, 다른 절 수정 금지)

## Non-Goals And Guardrails

- **실제 브로커 주문 제출/정정/취소 API 호출을 구현하지 않는다** - `BrokerOrderExecutionRun`은
  이번 슬라이스에서 아직 발생하지 않는 미래 이벤트를 위한 감사로그 스키마만
  준비한다(별도 계약에서 실제 제출 로직이 이 테이블에 기록하기 시작한다).
- 프론트엔드(`frontend/**`) 수정 없음 - API 계약만 먼저 확정한다.
- `BrokerConnection`/`BrokerConnectionSecretValue`(기존 암호화 자격증명 모델)는
  읽기만 하고 수정하지 않는다 - `BrokerOrderExecutionGrant`는 기존
  `BrokerConnection`을 외래키로 참조하는 새 테이블이다.
- **서킷브레이커 한도는 하드 상한을 코드에 둔다**: `maxPositionSizePerOrder`는
  계좌 자산 대비 20%를 초과하는 값을 서비스 계층에서 거부한다(검증되지 않은
  상수를 프론트가 임의로 보낼 수 없게). `maxDailyOrderCount`도 시스템 상한
  (예: 10)을 초과하면 거부한다. 정확한 상한값은 구현 중 근거와 함께 결정하고
  Handoff에 기록한다.
- **`status` 전환은 PAUSED/REVOKED로 가는 경로가 ACTIVE로 가는 경로보다 먼저**
  **테스트를 통과해야 한다** - 커밋 순서/테스트 작성 순서로 이 우선순위를
  드러낸다.
- `strategyId`는 `research/STRATEGY_ENGINE_POLICY.md`가 정의한 화이트리스트
  개념을 따른다 - 이번 슬라이스는 문자열 검증만 하고(빈 값 거부), 실제
  전략 레지스트리 연동은 범위 밖이다(TODO로 명시).
- 민감정보(브로커 자격증명, 액세스 토큰)는 이 슬라이스에서 다루지 않는다 -
  `BrokerOrderExecutionGrant`는 자격증명을 저장하지 않고 `brokerConnectionId`
  참조만 가진다.
- 데이터베이스 마이그레이션은 기존 넘버링(V29)을 따르고, 되돌리기 쉬운 형태
  (컬럼 추가가 아니라 신규 테이블)로 작성한다.
- 인증/인가: 모든 신규 API는 `/api/me/...` 경로 관례를 따르고 소유권 검증을
  포함한다(다른 회원의 grant를 읽거나 수정할 수 없어야 한다).

## Acceptance Checks

- [ ] `BrokerOrderExecutionGrant` 엔티티·마이그레이션·리포지토리 구현, 소유
      `BrokerConnection`과의 관계 및 유니크 제약(회원당 커넥션당 1개) 테스트
- [ ] `BrokerOrderExecutionRun` 감사로그 엔티티·마이그레이션·리포지토리 구현
      (민감정보 없는 사실만 기록하는지 테스트로 확인 - 기존
      `BrokerReconciliationRun` 감사 패턴과 동일 기준)
- [ ] 동의(opt-in) 생성 API: `strategyId` 화이트리스트 문자열 검증,
      `maxPositionSizePerOrder`/`maxDailyOrderCount` 하드 상한 초과 시 400
      거부, `consentedAt`/`consentVersion` 기록
- [ ] **킬스위치 API(PAUSED/REVOKED 전환)를 먼저 구현하고 테스트**, 그 다음
      ACTIVE 전환 경로 구현 - 순서를 Handoff에서 확인 가능하게 커밋/테스트
      작성 순서로 남긴다
- [ ] 소유권 검증 테스트: 다른 회원의 grant에 대한 조회/수정 시 403/404
- [ ] `./gradlew test` 관련 포커스 테스트 전체 통과
- [ ] `docs/LEARNING_LOG.md`에 완료 상태 한 줄 갱신

## Handoff

- Files changed:
- Verification run: `./gradlew test` (관련 모듈)
- API / data-model impact: 신규 테이블(V29), 신규 `/api/me/broker-order-execution-grants` 계열 엔드포인트. 기존 계약 변경 없음.
- 서킷브레이커 하드 상한값과 그 근거:
- 다음 슬라이스(실제 주문 제출)로 넘길 미결정 사항:

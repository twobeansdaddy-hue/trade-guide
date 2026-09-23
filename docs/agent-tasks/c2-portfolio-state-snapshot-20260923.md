# Agent Task Contract: 장전 가이드 포트폴리오 상태 스냅샷(C-2)

## Identity

- Task ID: `c2-portfolio-state-snapshot-20260923`
- Owner: Claude (Codex 휴업 중 대행, `docs/AI_COLLABORATION_POLICY.md`의 Codex unavailable 예외)
- Cross verifier: 사용자 (Claude 구현분의 교차 검증·최종 승인)
- Work mode: `scoped-implementation`
- Branch / worktree: `main` 작업 트리

## Outcome

장전 가이드 한 건이 사용한 포트폴리오 결정 입력을 한 시점의 스냅샷으로 읽어 보유·후보 가이드가 같은 상태를 공유하게 하고, 그 상태의 다이제스트를 가이드와 같은 트랜잭션에 저장한다. 생성 도중 원장이 커밋돼 한 종목이 보유·후보 어디에도 나오지 않는 결함(재현 테스트로 확인)을 없앤다.

설계 근거: `docs/design/PORTFOLIO_STATE_SNAPSHOT_PROPOSAL.md`. 2026-09-23 사용자 결정: D1 다이제스트만 저장, D2 `REPEATABLE READ` 읽기 + 저장 직전 재대조, D3 전역 `AssetProfile` 카탈로그 포함, D4 재현 확인 후 V34 스키마 추가 승인(범위 개방으로 승인).

## Allowed Files

- `docs/agent-tasks/c2-portfolio-state-snapshot-20260923.md`
- `src/main/java/com/tradeguide/service/strategy/**`
- `src/main/java/com/tradeguide/domain/strategy/**`
- `src/main/resources/db/migration/**` (V34 추가만)
- `src/test/java/com/tradeguide/service/strategy/**`, `src/test/java/com/tradeguide/repository/strategy/**`, `src/test/java/com/tradeguide/domain/strategy/**`, `src/test/java/com/tradeguide/migration/**`
- `docs/design/PORTFOLIO_STATE_SNAPSHOT_PROPOSAL.md`, `docs/design/GUIDE_INPUT_OBSERVATION.md`

## Non-Goals And Guardrails

- 기존 `getPortfolioStrategyGuides(memberId, portfolioId)`, `getCandidateStrategyGuides(memberId, portfolioId)`의 동작은 바꾸지 않는다(자동 주문 스케줄러·매매 계획·포트폴리오 API가 사용). 장전 가이드는 스냅샷을 받는 오버로드를 쓴다.
- 전체 입력 해시(`inputSha256`)를 채우지 않고 감사 상태를 `CAPTURED`/`VERIFIED`로 올리지 않는다. `UNVERIFIED` 유지.
- 보유 수량·평단 원문을 새 테이블에 저장하지 않는다. 다이제스트와 건수만 저장한다.
- 외부 API 계약, 프론트엔드, 인증, 증권사 연동, 투자 전략 규칙은 읽기 전용이다.
- 전역 `AssetProfile` 카탈로그는 신호 계산 시 기존처럼 다시 조회한다. 스냅샷에는 다이제스트를 기록하고 저장 직전 재대조로 변경을 감지만 한다.
- Toss 일봉 세션 범위 보정은 다루지 않는다.
- 비밀값을 기록하지 않는다. Claude는 푸시하지 않는다.

## Acceptance Checks

- [x] `PostgresPremarketGuidePortfolioStateConsistencyIntegrationTest`의 재현 테스트가 통과한다: 생성 중 원장 커밋이 있어도 종목이 정확히 한 범위에 나오고, 감사 기록에 `PORTFOLIO_STATE_CHANGED_DURING_GENERATION`이 남는다. 대조군도 통과한다.
- [x] 일관된 캡처에서는 `PORTFOLIO_STATE_NOT_CAPTURED`가 빠지고 `portfolio_state_ref`가 채워지며, 상태는 `UNVERIFIED`, `inputSha256`은 `null`이다.
- [x] 다이제스트 단위 테스트: 금액 표기 무관, 입력 순서 무관, 거래 삭제 후 같은 내용 재등록은 다른 원장 다이제스트.
- [x] 상태 행이 같은 행으로 갱신되고, 스냅샷 없이 기록하면 제거된다(H2 리포지토리 테스트). PostgreSQL에서는 V34 적용·JPA 검증과 재현 테스트의 상태 행 저장을 확인했다.
- [x] `./gradlew test`, `./gradlew postgresIntegrationTest` 통과, `git diff --check` 통과.
- [x] 한국어로 보고하고 실행하지 않은 검사를 구분한다.

## Handoff

- Files changed: V34 마이그레이션, `domain/strategy`(`PremarketGuidePortfolioState`, `PremarketGuidePortfolioStateDigests`, `PremarketGuideCandidateSource`, `PortfolioStateCapture`, `PremarketGuideSnapshot`, `PremarketGuideInputAudit`), `service/strategy`(`PortfolioDecisionInputs`, `PortfolioDecisionInputsReader`, `PortfolioStateDigest`, 두 배치 서비스 오버로드, `PremarketGuideService`), 대응 테스트와 설계 문서.
- Verification run: 백엔드 전체 1,122개 통과·3개 건너뜀, PostgreSQL 통합 39개 통과(2026-09-23 15시대, Claude 실행). 재현 테스트는 변경 전 실패·변경 후 통과.
- API / data-model / policy impact: V34 `premarket_guide_portfolio_states` 추가(additive). 외부 API·프론트엔드 변화 없음. 감사 기록의 `missing_reasons` 값이 캡처 결과에 따라 달라진다.
- Open decision or risk: `REPEATABLE READ`의 한 읽기 내부 경합 방지는 별도 재현하지 않았다. 전역 카탈로그 변경은 감지만 한다. 자동 주문 스케줄러·매매 계획은 기존 경로를 그대로 쓰므로 같은 이중 조회 구조가 남아 있다(이번 범위 밖). 교차 검증·최종 승인은 사용자.

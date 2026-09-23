# Agent Task Contract: 자동 주문 정책 문구 정합

## Identity

- Task ID: `policy-wording-auto-order-20260923`
- Owner: Claude (Codex 휴업 중 대행)
- Cross verifier: 사용자
- Work mode: `scoped-implementation` (문서 문구 정합, 리서치 문서 포함)
- Branch / worktree: `main` 작업 트리

## Outcome

2026-09-17 개정된 `CLAUDE.md` 원칙(opt-in 사용자에 한해 검증된 전략 규칙의 자동 주문 실행)과 어긋나는 오래된 문구를 맞춘다. 새 정책을 만들지 않고 이미 결정된 원칙과 구현 사실(2026-09-18 구현, 실거래 기본 비활성)에 문구만 정합시킨다. 근거: `docs/design/PORTFOLIO_STATE_SNAPSHOT_PROPOSAL.md` 부록 B, 2026-09-23 사용자 결정 및 문구 승인.

## Allowed Files

- `docs/agent-tasks/policy-wording-auto-order-20260923.md`
- `AGENTS.md` (`governanceEditApproved`)
- `docs/AGENT_WORKFLOW.md` (`governanceEditApproved`)
- `research/STRATEGY_ENGINE_POLICY.md`
- `research/TASKS.md`

## Non-Goals And Guardrails

- 승인된 문구 외의 정책·전략 규칙·투자 기준을 바꾸지 않는다.
- `research/STRATEGY_ENGINE_POLICY.md`의 TradePlan 경계(손절 없는 초안 전송 불가)는 유지한다.
- `research/TASKS.md` §9 예측엔진 방향 전환은 복구본 반영 작업에서 다룬다.
- 코드·설정·실거래 플래그는 변경하지 않는다. Claude는 푸시하지 않는다.

## Acceptance Checks

- [x] 네 위치의 문구가 승인안과 일치한다.
- [x] 변경 후 대상 파일에 "자동 주문 미실행", "never executes orders"류의 무조건 금지 문구가 남지 않는다(TradePlan 경계 문구 제외).
- [x] `git diff --check` 통과.

## Handoff

- Files changed: `AGENTS.md`, `docs/AGENT_WORKFLOW.md`, `research/STRATEGY_ENGINE_POLICY.md`(5·13·145행), `research/TASKS.md`((D) 3단계 메모).
- Verification run: 옛 금지 문구 검색 결과 0건, `git diff --check` 통과. 문서 변경이라 테스트는 실행하지 않았다.
- API / data-model / policy impact: 문구 정합만. 정책 결정 변화 없음.
- Open decision or risk:

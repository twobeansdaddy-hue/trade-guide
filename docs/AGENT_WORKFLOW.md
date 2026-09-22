# Trade Guide Agent Workflow

`docs/AI_COLLABORATION_POLICY.md` is the source of truth for agent roles,
scope, security, and handoff requirements. This document summarizes the daily
implementation workflow.

## Operating Mode

The project is in agent development mode. The immediate goal is to deliver a usable web MVP quickly while preserving sound API, domain, security, and testing decisions. Agents may implement an assigned feature end to end; they do not wait for the user to write each code change.

Investment strategy policy, authentication, authorization, data-model changes, external-provider changes, and any rule that could be interpreted as trading advice require user approval before implementation.

## Shared Sources of Truth

Read these before beginning work:

1. `AGENTS.md`
2. `docs/PROJECT_CONTEXT.md`
3. `docs/LEARNING_LOG.md`
4. This document
5. `research/STRATEGY_ENGINE_POLICY.md` when a task touches strategy behavior

Claude Design output belongs under `docs/design/`. Treat it as a proposed interface specification, not executable requirements. Compare it with the current API contract, accessibility needs, and product scope before implementation.

## Agent Roles

에이전트별 역할·범위·하네스 명령은 `docs/AI_COLLABORATION_POLICY.md`의 "Work
Modes And Ownership"와 "Default implementation allocation"을 따른다(여기서
반복하지 않는다). One active owner per feature and file set. Before
delegating work, create a task contract from `docs/agent-tasks/TEMPLATE.md`
that states the owner, allowed files, expected output, work mode, and
acceptance checks.

## Product and Architecture Guardrails

- Trade Guide is a US-stock decision-support service. It never executes orders or guarantees returns.
- Keep strategy signals, user-context decisions, and future order drafts separate.
- Keep React web UI and future Flutter UI behind stable HTTP API contracts and domain rules. Do not add web-only behavior to backend APIs without a product reason.
- Do not invent stop-loss prices, target prices, position ratios, or new investment rules. Only implement policies explicitly adopted in `research/STRATEGY_ENGINE_POLICY.md`.
- Keep API keys, tokens, personal data, and local environment files out of source control and agent prompts.

## Delivery Workflow

1. Confirm the requested outcome and inspect relevant code, API DTOs, tests, and current UI.
2. For a substantial feature, provide a concise implementation plan and identify decisions that need user approval.
3. Implement a coherent vertical slice: API contract, backend behavior when required, frontend state and UI, loading/error/empty states, and focused tests.
4. Verify relevant backend tests, frontend lint/build, and a manual UI/API flow when applicable.
5. Update README, project context, or learning log only when the implementation changes their factual content.
6. Report changed files, verification performed, remaining limitations, and a suggested commit boundary.

UI 레이아웃 변경의 수동 확인 기준(데스크톱·360px, 가로 스크롤/겹침/잘림/레이아웃
이동 금지), 세로 슬라이스 딜리버리 루프(Codex 계획·통합 -> Claude 구현 ->
Antigravity 독립 검증 -> Codex 확정), Git 경계(Claude/Antigravity는 commit 외
Git 변경 금지)는 `docs/AI_COLLABORATION_POLICY.md`의 "Required Verification",
"Efficient Delivery Model", "Git And Security Boundaries"를 따른다(여기서
반복하지 않는다).

## 사용자 언어

- 사용자에게 표시되는 작업 제목, 진행 보고, 완료 보고, 새 문서 본문은 한국어를 기본으로 한다.
- 코드 식별자, 명령어, API 이름, 외부 서비스 고유명사는 정확성을 위해 영어 표기를 유지할 수 있다.
- Claude 작업 범위 파일의 `responseLanguage` 값은 사용자 보고 언어의 실행 기준이다.

## Design System Expectations

- Prefer a calm, dense, operational financial interface over a marketing landing page.
- Use responsive layouts, semantic HTML, keyboard-accessible controls, visible focus states, and text plus color for status.
- Record reusable design tokens and component states from Claude Design before duplicating visual styles across pages.
- Add a UI library only when it solves a confirmed need. Avoid adding several overlapping frameworks.

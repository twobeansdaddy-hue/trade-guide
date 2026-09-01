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

- **Codex (GPT)**: implementation and integration lead. Owns cross-cutting repository changes, API contract checks, tests, build verification, and final code review.
- **Claude**: design exploration, strategy research, architecture review, and scoped implementation tasks whose temporary local allowlist is prepared by Codex. Do not edit the same files as another active agent.
- **Gemini**: visual/UX critique, alternate design review, documentation review, and focused research. It is read-only by default.

One active owner per feature and file set. Before delegating work, create a task contract from `docs/agent-tasks/TEMPLATE.md` that states the owner, allowed files, expected output, work mode, and acceptance checks. For Claude writes, create its matching local scope using `scripts/agent-harness.sh`.

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

Claude and Gemini must not commit, push, force-push, reset, revert user work, merge branches, or install dependencies. In agent-development mode, Codex may create a feature branch and deliver a verified feature commit; it reports the Git result and stops whenever the user requests review-only or a pause.

## Design System Expectations

- Prefer a calm, dense, operational financial interface over a marketing landing page.
- Use responsive layouts, semantic HTML, keyboard-accessible controls, visible focus states, and text plus color for status.
- Record reusable design tokens and component states from Claude Design before duplicating visual styles across pages.
- Add a UI library only when it solves a confirmed need. Avoid adding several overlapping frameworks.

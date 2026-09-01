# Agent Task Contract: Claude Web MVP Design Review

## Identity

- Task ID: `claude-web-mvp-design-review`
- Owner: `Claude`
- Work mode: `design`
- Branch / worktree: `design/web-mvp-review` in a dedicated Claude worktree

## Outcome

Review the implemented Trade Guide web MVP against the existing design brief
and produce a prioritized design-improvement proposal for a modern, calm,
operational investment dashboard. The result must improve usability without
inventing unavailable API data or changing investment policy.

## Files To Read

- `AGENTS.md`
- `CLAUDE.md`
- `docs/AI_COLLABORATION_POLICY.md`
- `docs/PROJECT_CONTEXT.md`
- `docs/design/WEB_MVP_DESIGN.md`
- `docs/ARCHITECTURE_ROADMAP.md`
- `frontend/src/**`
- relevant API types and backend response DTOs, read-only

## Allowed Files

- `docs/design/WEB_MVP_DESIGN_REVIEW.md`

Do not modify existing design specifications, application code, shared policy
documents, configuration, or task contracts.

## Required Review

1. Compare the current information architecture and routes with the product
   goals: dashboard, holdings, strategy guides, candidate discovery, and risk
   settings.
2. Identify the most important missing or weak user flows, especially first
   use, empty state, authentication transition, loading/error/retry behavior,
   ticker selection, and mobile responsiveness.
3. Evaluate visual hierarchy, density, typography, navigation, status color,
   accessibility, and whether the UI reads as a current operational financial
   product rather than a static exercise.
4. Separate recommendations into P0, P1, and P2. For each recommendation,
   state the affected screen, the user problem, proposed interaction or layout,
   required API data, and whether current APIs already support it.
5. When an API or data-model gap exists, describe the desired contract without
   inventing prices, order behavior, stop-loss values, broker integration, or
   investment advice.
6. Propose a reusable visual direction: color roles, spacing scale, typography
   roles, component states, and responsive breakpoints. Keep it implementable
   in React now and compatible with a future Flutter client.

## Non-Goals And Guardrails

- Do not change `src/**`, `frontend/**`, `research/**`, shared docs, or Git
  state.
- Do not adopt a trading strategy, add financial rules, or define automatic
  order behavior.
- Do not inspect or request environment files, API keys, personal portfolio
  values, or OAuth secrets.
- Do not propose a large UI framework unless a specific requirement cannot be
  met by the existing React and CSS setup.

## Acceptance Checks

- [ ] `docs/design/WEB_MVP_DESIGN_REVIEW.md` contains a concise current-state
  assessment and P0/P1/P2 priorities.
- [ ] Every proposed feature names its API/data dependency and marks it as
  existing or missing.
- [ ] The proposed visual system includes desktop and mobile considerations.
- [ ] The report distinguishes a design proposal from an approved product
  decision.
- [ ] No files outside the allowed path are changed.

## Handoff

- Files changed:
- Verification performed:
- Existing APIs usable without changes:
- API or data-model questions requiring Codex and user approval:
- Recommended first implementation slice:


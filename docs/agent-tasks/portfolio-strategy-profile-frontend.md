# Agent Task Contract

## Identity

- Task ID: `portfolio-strategy-profile-frontend`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: current Trade Guide worktree

## Outcome

Give each currently held asset a portfolio-scoped investment-track setting in
the web UI. The setting must make clear that it changes only this portfolio's
guide behavior and never the shared asset catalog or candidate-guide policy.

## Allowed Files

- `frontend/src/**`

## Non-Goals And Guardrails

- The backend API, database schema, authentication, broker integration, and
  global `AssetProfile` admin contract are read-only.
- Use only the established API:
  `GET /api/members/{memberId}/portfolios/{portfolioId}/strategy-profiles`,
  `PUT .../strategy-profiles/{market}/{ticker}`, and
  `DELETE .../strategy-profiles/{market}/{ticker}`.
- Do not invent a strategy recommendation when `effectiveTrack` is null.
- Do not add dependencies, modify package files, secrets, Git state, or docs.
- Preserve the existing Strategy Guides screen behavior outside this setting
  section.

## Acceptance Checks

- [ ] Show held assets with effective track, global fallback state, and a clear
  per-portfolio override control.
- [ ] Saving an override, removing it, loading, empty, and request-error states
  are handled without stale values or layout shift.
- [ ] The interaction is keyboard accessible with visible labels and a
  confirmation only for removing an existing override.
- [ ] `npm run lint`, `npm run build`, and TypeScript checking pass.
- [ ] Desktop and 360px visual checks show no horizontal overflow, clipping,
  overlap, inconsistent form-control heights, or message-driven layout shift.

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact: frontend consumer only; no contract change.
- Open decision or risk:

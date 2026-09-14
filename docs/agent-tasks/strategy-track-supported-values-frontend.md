# Agent Task Contract

## Identity

- Task ID: `strategy-track-supported-values-frontend`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: current Trade Guide worktree

## Outcome

Make the strategy-track setting truthful: expose only the currently executable
Track A, and state plainly that Track B is not yet available.

## Allowed Files

- `frontend/src/**`

## Non-Goals And Guardrails

- Do not modify backend, API contracts, strategy policy, dependencies, docs,
  secrets, or Git state.
- Do not imply that Track A is an investment recommendation or that Track B is
  executable.

## Acceptance Checks

- [ ] No form control can save Track B.
- [ ] The UI explains Track A as the weekly 10/40 moving-average guide and
  says Track B is not yet available.
- [ ] Loading, error, and success messages do not shift the form layout.
- [ ] Lint, TypeScript, build, desktop, and 360px visual checks pass.

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact:
- Open decision or risk:

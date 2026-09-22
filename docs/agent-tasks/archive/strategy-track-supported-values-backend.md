# Agent Task Contract

## Identity

- Task ID: `strategy-track-supported-values-backend`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: current Trade Guide worktree

## Outcome

Prevent an unsupported investment track from being persisted as a portfolio
override. Only a track with a real `TradingStrategy` implementation may be
accepted for guide generation.

## Allowed Files

- `src/main/java/**`
- `src/test/java/**`

## Non-Goals And Guardrails

- Do not implement or invent a Track B trading strategy.
- Do not change frontend, migrations, broker behavior, global asset profiles,
  candidate-guide policy, dependencies, secrets, or Git state.
- Existing, unsupported persisted records must fail safely with an actionable
  API error instead of an opaque strategy-selector exception.

## Acceptance Checks

- [ ] `TRACK_B` upsert is rejected with a stable 422 API response.
- [ ] `TRACK_A` upsert continues to work.
- [ ] Existing override resolution cannot produce an unhandled server error for
  an unsupported track.
- [ ] Focused tests plus full Gradle test suite pass.

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact:
- Open decision or risk:

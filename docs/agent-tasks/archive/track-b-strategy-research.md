# Agent Task Contract

## Identity

- Task ID: `track-b-strategy-research`
- Owner: `Claude`
- Work mode: `research`
- Branch / worktree: current Trade Guide worktree

## Outcome

Produce an evidence-based recommendation for whether a second strategy track
should be added, what it should be, and the validation gate before any engine
implementation begins.

## Allowed Files

- `research/reports/**`

## Non-Goals And Guardrails

- Do not implement Track B, edit application code, modify APIs, migrations,
  frontend, dependencies, secrets, or Git state.
- Do not provide personalised investment advice or claim a strategy is
  profitable without limitations.
- Treat Track A as the existing weekly 10/40 moving-average trend strategy;
  proposed Track B must have a materially distinct hypothesis and data needs.

## Acceptance Checks

- [ ] Deliver one concise report with primary academic or official sources.
- [ ] Compare at least three candidates, including a rationale to reject or
  defer unsuitable approaches for the present product.
- [ ] Recommend at most one Track B candidate, or explicitly recommend no new
  track now.
- [ ] Define the strategy rules, required market data, transaction-cost and
  survivorship assumptions, metrics, train/test or walk-forward method,
  acceptance gate, and known risks.
- [ ] State whether a separate Track C is warranted now; distinguish strategy
  tracks from portfolio risk-policy controls.

## Handoff

- Files changed:
- Sources and evidence:
- Recommendation:
- Open decision or risk:

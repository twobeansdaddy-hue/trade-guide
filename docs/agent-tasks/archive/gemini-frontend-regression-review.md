# Agent Task Contract

## Identity

- Task ID: `gemini-frontend-regression-review`
- Owner: `Gemini`
- Work mode: `review`
- Branch / worktree: current feature branch after the next frontend slice is implemented

## Outcome

Independently verify the delivered frontend slice against its backend API
contract. Report only reproducible defects, regression risks, and concrete UX
or accessibility findings so Codex can validate and correct them in one batch.

## Allowed Files

- No repository files may be modified.

Gemini may read source, documentation, browser output, and test reports. It may
run only non-destructive commands named by the requesting task.

## Non-Goals And Guardrails

- Do not implement, commit, push, merge, alter local configuration, or expose
  secrets.
- Do not invent market prices, transaction data, strategy decisions, or product
  policy.
- Keep the review in Korean. Preserve code and API identifiers in English.

## Acceptance Checks

- [ ] Inspect loading, success, empty, validation, and request-failure states
  that apply to the assigned screen.
- [ ] Check frontend types and API response/error handling against the backend
  controller and DTO contract.
- [ ] Check keyboard operation, focus visibility, text contrast, and responsive
  overflow for changed controls where practical.
- [ ] Report each finding with exact reproduction steps and evidence. Separate
  confirmed defects from suggestions.

## Handoff

- Files changed: none
- Verification run:
- Confirmed defects:
- Risks or suggestions:
- API / data-model / policy impact:

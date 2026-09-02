# Agent Task Contract

## Identity

- Task ID: `claude-asset-search-feedback-review`
- Owner: `Claude`
- Work mode: `design`
- Branch / worktree: `fix/asset-search-feedback-layout`

## Outcome

Review the transaction-entry asset search feedback and result-list states after
the layout stabilization. Record a concise visual and accessibility review for
the next implementation slice.

## Allowed Files

- `docs/design/ASSET_SEARCH_FEEDBACK_REVIEW.md`

The owner may read related frontend files and API contracts for context but may
not write outside this list.

## Non-Goals And Guardrails

- Do not modify `frontend/`, backend code, APIs, database schema, auth, broker
  integration, or investment strategy policy.
- Do not include local configuration values, credentials, or personal data.
- This is a proposal and review only; it does not approve design changes.

## Acceptance Checks

- [ ] Review empty query, loading, no-result, error, and result-list states.
- [ ] Check keyboard and screen-reader feedback behavior.
- [ ] Identify only concrete, user-visible issues with a suggested priority.
- [ ] Record the review in the allowed design document.

## Handoff

- Files changed: `docs/design/ASSET_SEARCH_FEEDBACK_REVIEW.md`
- Verification run: static code and available local screen review
- API / data-model / policy impact: none
- Open decision or risk: Codex decides whether each recommendation is adopted.

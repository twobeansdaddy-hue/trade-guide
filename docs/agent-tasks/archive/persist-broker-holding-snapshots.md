# Broker Holding Snapshot Persistence

## Identity

- Task ID: `task_3ba9b598dff6`
- Owner: `Claude`
- Work mode: `backend-implementation`
- Branch / worktree: current `main` worktree

## Outcome

A linked and verified broker account can refresh its holdings into a persisted,
read-only snapshot. The latest snapshot is retrievable without another broker
provider call and can later be compared with Trade Guide holdings.

## Allowed Files

- `src/main/**`
- `src/test/**`

## Non-Goals And Guardrails

- Preserve `TradeTransaction` as the manual investment ledger. Do not create,
  update, or delete transactions or derived `Holding` records during a broker
  refresh.
- Persist snapshots separately from the manual ledger. Include the linked
  broker account, synchronization time, market, ticker, display name, quantity,
  and average purchase price as available from the provider.
- Require portfolio ownership and a linked, verified broker account for a
  refresh. Never return credentials, tokens, full account numbers, or encrypted
  values.
- A refresh may call the existing provider; retrieving the latest persisted
  snapshot must not call the provider.
- Do not modify `frontend/**`, `docs/**` other than this existing contract,
  local configuration, secrets, Git state, or dependencies.

## Acceptance Checks

- [ ] A database migration and JPA model persist one snapshot and its holding items.
- [ ] Refresh endpoint saves a new snapshot through the existing broker adapter.
- [ ] Latest-snapshot endpoint reads the saved data without an external call.
- [ ] Ownership, missing-link, and unsupported-provider behavior remain explicit.
- [ ] Focused service, controller, repository, and migration tests pass.
- [ ] Relevant Gradle tests run and the handoff states the exact API contract.

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact:
- Open decision or risk:

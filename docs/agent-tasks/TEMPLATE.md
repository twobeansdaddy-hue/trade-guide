# Agent Task Contract

## Identity

- Task ID: `TASK-ID`
- Owner: `Codex | Claude | Gemini`
- Work mode: `research | design | scoped-implementation | review`
- Branch / worktree:

## Outcome

Describe the user-visible or decision-support result in one or two sentences.

## Allowed Files

- `path/to/owned-area/**`

The owner may read related files for context but may not write outside this
list. Add matching tests explicitly when implementation changes behavior.

## Non-Goals And Guardrails

- List what must not change.
- State whether API, database, authentication, broker, or strategy policy is
  read-only for this task.
- Never include secrets or local configuration values.

## Acceptance Checks

- [ ] Required behavior and error states
- [ ] Required focused tests
- [ ] Required lint/build/manual checks
- [ ] Required factual documentation updates

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact:
- Open decision or risk:


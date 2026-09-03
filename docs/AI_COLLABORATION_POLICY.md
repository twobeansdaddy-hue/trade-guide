# Trade Guide AI Collaboration Policy

## Purpose

Trade Guide is developed through multiple AI agents, but product decisions,
source ownership, and verification must remain clear. This policy divides work
by responsibility so agents can collaborate without overwriting each other or
silently changing investment behavior.

This document is the source of truth for AI roles. `AGENTS.md` and `CLAUDE.md`
summarize the rules that each agent needs at session start.

## Decision Authority

- **User** owns product scope, investment-policy adoption, authentication and
  personal-data decisions, broker integration, and production deployment.
- **Codex** owns implementation coordination, integration, API-contract
  consistency, test gates, and final repository review.
- **Claude** owns evidence-based research, design exploration, architecture
  review, and explicitly scoped implementation work.
- **Gemini** owns independent test design, regression and defect discovery,
  visual/UX critique, and alternative product or documentation review. It is
  read-only by default.

No agent may treat a research conclusion, a design mockup, or a review comment
as an adopted product rule without the user decision recorded in the relevant
policy or project-context document.

## Work Modes And Ownership

| Agent | Default mode | May write | Must not do |
| --- | --- | --- | --- |
| Codex | Integration and implementation | Feature-owned backend, frontend, tests, and factual documentation | Invent investment rules or expose secrets |
| Claude | Research, design, and review | `research/**` in research mode; `docs/design/**` in design mode; an explicit implementation allowlist in implementation mode | Modify unassigned files, Git history, or local secrets |
| Gemini | Independent verification and review | No repository files by default; may run non-destructive checks named by its task contract | Implement directly, change policies, or approve its own review |
| User | Product owner | Any file and final decisions | Share API keys or production credentials in prompts |

Only one active agent owns a file set. A task contract must state the owner,
work mode, allowed paths, expected output, and verification command before an
agent begins writing.

## Efficient Delivery Model

Use the agents as a small delivery team rather than asking one agent to repeat
every role. The coordinator chooses the smallest safe arrangement for each
vertical slice.

1. **Codex plans and integrates.** Confirm the product boundary, API contract,
   ownership, and acceptance checks. Codex resolves cross-cutting changes,
   validates external findings, runs the final gate, and owns Git delivery.
2. **Claude implements bounded work.** Delegate a self-contained frontend
   feature, backend package, matching tests, or design investigation through a
   scoped task contract. Claude must not share writable files with another
   active worker.
3. **Gemini verifies independently.** Give Gemini the completed feature or a
   proposed change and ask for reproducible test scenarios, API edge cases,
   regression risks, visual/accessibility defects, and concrete evidence. It
   may run the task contract's read-only checks but does not edit or approve
   code.
4. **Codex accepts or rejects findings.** A Gemini or Claude finding is not a
   defect until Codex reproduces it against the current branch. Codex either
   fixes the confirmed issue or records why it is not applicable.

Do not delegate a trivial rename, a one-line question, or a change that needs
an immediate user product decision. Delegate when independent review reduces
the chance of a regression, or when Claude can complete a clearly isolated
implementation while Codex prepares the next integration step.

## Claude Work Modes

Claude Code uses the write-scope harness under `.claude/`. Its default is
research-only, which preserves the original research workflow.

### Research mode

- Allowed path: `research/**`
- Output: reports, data, reproducible scripts, and policy wording proposals.
- Read `src/**` and `docs/**` only for context.
- Do not modify implementation, shared documentation, settings, or Git state.

### Design mode

- Allowed path: `docs/design/**`
- Output: information architecture, design tokens, screen states, and
  interaction specifications.
- A design result is a proposal. Codex checks it against API contracts,
  accessibility, and the Flutter-compatible service boundary before adoption.

### Scoped implementation mode

- The coordinator creates a local `.claude/agent-scope.json` with a task ID and
  a non-empty allowlist before Claude starts.
- Typical scope: one bounded backend package or one frontend feature area and
  its matching tests. Agent rules, shared policy files, and unrelated
  refactors remain out of scope unless explicitly included.
- Claude reports changed files, tests run, API-contract impact, and remaining
  risks. It does not commit, push, merge, install dependencies, or change its
  own scope.

## Task Contract

Use `docs/agent-tasks/TEMPLATE.md` for any multi-agent task that writes files.
The task contract lives in Git so the next machine and other agents can see the
intent. The local scope file is deliberately ignored because it is temporary
execution control, not project history.

For Orca, launch each worker from a dedicated worktree after the task contract
and its local scope are prepared. Parallel workers must receive disjoint path
allowlists. The coordinator integrates one completed change set at a time.

## Required Verification

Each delivered feature must use the smallest relevant checks and the final
integration gate.

- Backend behavior: focused Gradle tests; use the full backend test suite for
  shared contracts, security, domain models, or release candidates.
- Frontend behavior: lint, production build, and a manual browser flow that
  covers loading, success, empty, and error states affected by the change.
- API changes: controller/API tests plus frontend type and error handling
  review when the web client consumes the endpoint.
- Final integration: `./scripts/verify-feature.sh` and `git diff --check`.

The verification result must distinguish checks that passed, checks that were
not run, and manual flows that were confirmed.

### Independent Gemini verification

For a feature with meaningful UI, API, state, or validation behavior, schedule
a Gemini review before final delivery when practical. Its task contract must
include the branch or commit to inspect and request:

- happy-path, empty, loading, validation, authorization, and provider-failure
  scenarios that apply to the feature;
- exact reproduction steps, endpoint or screen, and expected versus observed
  result for each finding;
- file and line references, screenshots, or command output when available;
- a clear separation between confirmed defects, risks, and suggestions.

Codex should batch Gemini findings into one corrective slice. Do not create a
separate commit for every stylistic suggestion.

## Git And Security Boundaries

- Codex may create feature branches and prepare commits after a complete,
  verified vertical slice in agent-development mode. It reports the commit and
  remote result. The user can request review-only or pause Git changes at any
  time.
- Claude and Gemini never commit, push, merge, force-push, reset, revert, or
  alter another agent's work.
- `.env`, `application-local.yml`, API keys, OAuth secrets, local scope files,
  and IDE-local state are never committed, copied into prompts, or used as
  fixture data.
- Authentication, authorization, broker APIs, database schema changes, and
  investment-rule adoption require user approval before implementation.

## Handoff Format

Every agent handoff includes:

1. Task ID and work mode.
2. Files read and files changed.
3. Decision made versus proposal still awaiting approval.
4. Verification performed and its result.
5. API, data-model, security, or strategy-policy impact.
6. The next safe owner and any blocked decision.

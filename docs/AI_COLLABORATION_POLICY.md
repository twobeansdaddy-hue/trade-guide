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
- **Gemini** owns independent visual/UX critique and alternative product or
  documentation review. It is read-only by default.

No agent may treat a research conclusion, a design mockup, or a review comment
as an adopted product rule without the user decision recorded in the relevant
policy or project-context document.

## Work Modes And Ownership

| Agent | Default mode | May write | Must not do |
| --- | --- | --- | --- |
| Codex | Integration and implementation | Feature-owned backend, frontend, tests, and factual documentation | Invent investment rules or expose secrets |
| Claude | Research, design, and review | `research/**` in research mode; `docs/design/**` in design mode; an explicit implementation allowlist in implementation mode | Modify unassigned files, Git history, or local secrets |
| Gemini | Independent review | No repository files by default | Implement directly, change policies, or approve its own review |
| User | Product owner | Any file and final decisions | Share API keys or production credentials in prompts |

Only one active agent owns a file set. A task contract must state the owner,
work mode, allowed paths, expected output, and verification command before an
agent begins writing.

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

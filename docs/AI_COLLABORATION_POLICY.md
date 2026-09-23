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
  review, and explicitly scoped implementation work as Codex's coding
  collaborator.

Codex and Claude are the only delivery agents. Antigravity CLI was removed from
the delivery path on 2026-09-23; task contracts that name it are historical
records.

No agent may treat a research conclusion, a design mockup, or a review comment
as an adopted product rule without the user decision recorded in the relevant
policy or project-context document.

## Work Modes And Ownership

| Agent | Default mode | May write | Must not do |
| --- | --- | --- | --- |
| Codex | Integration and implementation | Feature-owned backend, frontend, tests, and factual documentation | Invent investment rules or expose secrets |
| Claude | Research, design, and review | `research/**` in research mode; `docs/design/**` in design mode; an explicit implementation allowlist in implementation mode | Modify unassigned files, Git history, or local secrets |
| User | Product owner | Any file and final decisions | Share API keys or production credentials in prompts |

Only one active agent owns a file set. A task contract must state the owner,
work mode, allowed paths, expected output, and verification command before an
agent begins writing.

Frontend work is shared by Codex and Claude. For each frontend task, one agent
owns the write scope and the other performs cross verification (see "Cross
verification"). When both work on one feature at the same time, split it into
disjoint file sets, for example Claude owns `frontend/src/components/<feature>/**`
while Codex owns shared types, the API client, and integration. No agent approves
its own visual result.

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
   active worker. Claude and Codex are coding collaborators: Claude may own an
   isolated implementation slice, while Codex owns its integration, contract
   decisions, and final acceptance.
3. **The other agent verifies independently.** The agent that did not
   implement the slice reviews it read-only: reproducible test scenarios, API
   edge cases, regression risks, visual/accessibility defects, and concrete
   evidence. It does not edit the reviewed files during the review.
4. **The implementer accepts or rejects findings.** A finding is not a defect
   until the implementing agent (or Codex as integrator) reproduces it against
   the current branch, then either fixes it or records why it is not
   applicable.

When Codex is unavailable (for example, a usage limit), the user takes over
coordination: preparing the local scope with `./scripts/agent-harness.sh`,
accepting findings, and final acceptance. Claude may then implement or verify,
but never both for the same slice. Claude's Git boundary does not change.

Do not delegate a trivial rename, a one-line question, or a change that needs
an immediate user product decision. Delegate when independent review reduces
the chance of a regression, or when Claude can complete a clearly isolated
implementation while Codex prepares the next integration step.

### Default implementation allocation

To use the available subscriptions efficiently, prefer Claude for isolated
implementation slices and reserve Codex for planning, cross-cutting contract
decisions, integration, final browser/API verification, and Git delivery.

- Use `./scripts/agent-harness.sh claude-frontend <task-id>` for a frontend
  slice confined to `frontend/src/**`.
- Use `./scripts/agent-harness.sh claude-backend <task-id>` for a backend slice
  confined to `src/main/**` and `src/test/**`.
- Keep database migration, authentication, authorization, broker-provider, and
  investment-policy decisions with Codex and the user before assigning the
  implementation. A Claude backend task may implement an already-approved
  decision, but must not choose it.
- Narrow visual fixes, read-only regression review, and second UI passes go to
  whichever of Codex or Claude did not implement the slice. Antigravity CLI and
  direct Gemini CLI are not part of the delivery path.

## Claude Work Modes

Claude Code uses the write-scope harness under `.claude/`. Its default is
research-only, which preserves the original research workflow.

### Governance file edit exception

Claude may edit a file in the hook's protected list (`CLAUDE.md`, `AGENTS.md`,
`SETUP.md`, `docs/AI_COLLABORATION_POLICY.md`, `docs/AGENT_WORKFLOW.md`) only
when the user has explicitly listed that exact path in
`.claude/agent-scope.json`'s `governanceEditApproved` array for the current
session. The user sets this array themselves (or asks Codex to); Claude never
adds a path to it. Each entry authorizes one edit turn, not standing
permission — the coordinator clears the array after the edit lands. `.claude/`
itself (including hooks and `agent-scope.json`) remains protected from Claude
writes under all circumstances; only the user or Codex may change it.

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
- UI layout changes: in addition to the frontend checks, capture or inspect
  representative desktop and 360px-width views. In each affected view, verify
  there is no unexpected horizontal page scrollbar, clipped or overlapping
  content, mismatched text/select/date control height in one form row, or
  message-driven layout shift. Header and navigation destinations must remain
  usable at the narrow width. If the assigned worker cannot operate a browser,
  it must say so; the cross-verifying agent performs this final visual gate
  before acceptance.
- API changes: controller/API tests plus frontend type and error handling
  review when the web client consumes the endpoint.
- Final integration: `./scripts/verify-feature.sh` and `git diff --check`.

The verification result must distinguish checks that passed, checks that were
not run, and manual flows that were confirmed.

### Cross verification

For a feature with meaningful UI, API, state, or validation behavior, the agent
that did not implement it (Codex or Claude) reviews it before final delivery
when practical. The review request must include the branch or commit to inspect
and ask for:

- happy-path, empty, loading, validation, authorization, and provider-failure
  scenarios that apply to the feature;
- exact reproduction steps, endpoint or screen, and expected versus observed
  result for each finding;
- file and line references, screenshots, or command output when available;
- a clear separation between confirmed defects, risks, and suggestions.

The implementer should batch cross-verification findings into one corrective
slice. Do not create a
separate commit for every stylistic suggestion.

## Git And Security Boundaries

- Codex may create feature branches and prepare commits after a complete,
  verified vertical slice in agent-development mode. It reports the commit and
  remote result. The user can request review-only or pause Git changes at any
  time.
- Claude never pushes, merges, force-pushes, resets, reverts, or alters
  another agent's work. Claude may run `git commit` only when the user's current chat
  message explicitly asks for that specific commit (not standing permission, and not
  inferred from an earlier "go ahead") and Claude has shown the exact commit message
  and file list in the same turn before running it. Push, merge, and branch changes
  remain fully prohibited for Claude regardless of user request — those stay
  Codex/user-only.
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

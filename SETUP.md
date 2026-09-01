# AI Collaboration Setup

## Before Starting Any Agent Task

1. Start from a clean, up-to-date feature branch or an isolated worktree.
2. Create a task contract from `docs/agent-tasks/TEMPLATE.md` when the agent
   will edit files.
3. Assign one owner and a disjoint path allowlist.
4. Read `docs/AI_COLLABORATION_POLICY.md` and the agent-specific instructions.

The coordinator starts an agent in the same worktree where it will edit files.
Do not point a session in one checkout at another checkout's paths.

## Claude Code Harness

The checked-in Claude hook enforces a temporary local scope file. If no local
scope exists, Claude remains in the safe research-only mode.

```bash
# Research worker
./scripts/agent-harness.sh claude-research research-stoploss-review

# Design worker
./scripts/agent-harness.sh claude-design web-dashboard-design

# Explicit backend implementation worker
./scripts/agent-harness.sh claude-implementation portfolio-api-tests \
  src/main/java/com/tradeguide/service/portfolio/ \
  src/test/java/com/tradeguide/service/portfolio/
```

Run the command in the Claude worker's own worktree immediately before the
session starts. The generated `.claude/agent-scope.json` is intentionally
ignored. Do not add secrets, broad repository roots, or another agent's active
files to its allowlist.

Use `./scripts/agent-harness.sh read-only` after the work is handed back if the
worktree will remain open.

## Gemini Review Harness

Gemini receives the task contract and relevant screenshots, API DTOs, and
design documents. It reviews only. Its output should list issues by severity,
the affected screen or contract, and a concrete recommendation. Codex decides
whether to adopt the recommendation.

## Codex Integration Harness

Codex integrates completed work after confirming its task contract, reviewing
the diff, and running:

```bash
./scripts/verify-feature.sh
```

The script runs the backend test suite, frontend lint and production build, and
Git whitespace validation. Run additional manual browser and API checks for
the states changed by the feature.

## Security And Recovery

- Never place API keys, OAuth values, local environment files, or personal
  portfolio data in task contracts or prompts.
- If the hook blocks an expected write, do not bypass it. Check the task
  contract and the generated local scope, then have the coordinator correct
  the allowlist.
- Research conclusions and design proposals remain proposals until the user
  approves their policy or product impact.

#!/usr/bin/env bash

set -euo pipefail

scope_file=".claude/agent-scope.json"

write_scope() {
  local mode="$1"
  local task_id="$2"
  shift 2

  mkdir -p "$(dirname "$scope_file")"

  python3 - "$scope_file" "$mode" "$task_id" "$@" <<'PY'
import json
import sys

scope_file, mode, task_id, *allowed_paths = sys.argv[1:]
if not allowed_paths:
    raise SystemExit("At least one allowed path is required.")

with open(scope_file, "w", encoding="utf-8") as output:
    json.dump(
        {
            "mode": mode,
            "taskId": task_id,
            "allowedPaths": allowed_paths,
        },
        output,
        indent=2,
    )
    output.write("\n")
PY

  echo "Prepared $mode scope for task '$task_id': ${*:1}"
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/agent-harness.sh claude-research <task-id>
  ./scripts/agent-harness.sh claude-design <task-id>
  ./scripts/agent-harness.sh claude-implementation <task-id> <allowed-path> [allowed-path...]
  ./scripts/agent-harness.sh read-only

The command writes only the ignored local .claude/agent-scope.json file.
Run it in the same worktree that will host the Claude Code session.
EOF
}

case "${1:-}" in
  claude-research)
    [[ $# -eq 2 ]] || { usage; exit 1; }
    write_scope "research" "$2" "research/"
    ;;
  claude-design)
    [[ $# -eq 2 ]] || { usage; exit 1; }
    write_scope "design" "$2" "docs/design/"
    ;;
  claude-implementation)
    [[ $# -ge 3 ]] || { usage; exit 1; }
    write_scope "scoped-implementation" "$2" "${@:3}"
    ;;
  read-only)
    [[ $# -eq 1 ]] || { usage; exit 1; }
    write_scope "read-only" "no-active-write-task" ".claude/no-write-marker"
    ;;
  *)
    usage
    exit 1
    ;;
esac

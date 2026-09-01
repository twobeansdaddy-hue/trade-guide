#!/usr/bin/env python3
"""Block Claude Code writes outside the temporary coordinator-defined scope."""

import json
import sys
from pathlib import Path


WORKTREE_ROOT = Path(__file__).resolve().parents[2]
SCOPE_FILE = WORKTREE_ROOT / ".claude" / "agent-scope.json"
PROTECTED_PATHS = {
    ".claude",
    "AGENTS.md",
    "CLAUDE.md",
    "SETUP.md",
    "docs/AI_COLLABORATION_POLICY.md",
    "docs/AGENT_WORKFLOW.md",
}
SECRET_FILENAMES = {
    ".env",
    ".env.local",
    "application-local.yml",
    "application-local.yaml",
}


def block(message: str) -> None:
    print(f"agent-scope hook: {message}", file=sys.stderr)
    sys.exit(2)


def load_scope() -> dict:
    if not SCOPE_FILE.exists():
        return {"mode": "research", "allowedPaths": ["research/"]}

    try:
        scope = json.loads(SCOPE_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        block(f"could not read {SCOPE_FILE.name}: {error}. Blocking to be safe.")

    if not isinstance(scope, dict) or not isinstance(scope.get("allowedPaths"), list):
        block("scope configuration is invalid. Blocking to be safe.")

    return scope


def resolve_relative_path(payload: dict) -> Path:
    tool_input = payload.get("tool_input", {}) or {}
    file_path = tool_input.get("file_path", "")
    cwd = payload.get("cwd", str(WORKTREE_ROOT))

    if not file_path:
        block("no file_path was provided for an Edit or Write request.")

    target_path = Path(file_path)
    if not target_path.is_absolute():
        target_path = Path(cwd) / target_path
        if not target_path.resolve().is_relative_to(WORKTREE_ROOT):
            target_path = WORKTREE_ROOT / file_path

    try:
        return target_path.resolve().relative_to(WORKTREE_ROOT)
    except ValueError:
        block("writes outside this worktree are not allowed.")


def is_protected(relative_path: Path) -> bool:
    path = relative_path.as_posix()
    if relative_path.name in SECRET_FILENAMES or relative_path.name.startswith(".env."):
        return True
    return any(path == protected or path.startswith(protected + "/") for protected in PROTECTED_PATHS)


def is_allowed(relative_path: Path, allowed_paths: list) -> bool:
    path = relative_path.as_posix()
    for allowed_path in allowed_paths:
        if not isinstance(allowed_path, str) or not allowed_path:
            continue
        normalized = allowed_path.rstrip("/")
        if path == normalized or path.startswith(normalized + "/"):
            return True
    return False


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        block("could not parse tool input JSON.")

    relative_path = resolve_relative_path(payload)
    scope = load_scope()
    mode = scope.get("mode", "read-only")
    allowed_paths = scope.get("allowedPaths", [])

    if is_protected(relative_path):
        block(f"'{relative_path.as_posix()}' is protected and must be changed by Codex or the user.")

    if mode == "read-only":
        block("this worktree is read-only. Ask the coordinator for a scoped task.")

    if not is_allowed(relative_path, allowed_paths):
        task_id = scope.get("taskId", "unknown-task")
        block(
            f"task '{task_id}' in {mode} mode may write only to {allowed_paths}. "
            f"Attempted path: '{relative_path.as_posix()}'."
        )


if __name__ == "__main__":
    main()

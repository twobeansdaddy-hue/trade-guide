#!/usr/bin/env python3
"""
PreToolUse hook for the Trade Guide research-only worktree.

Blocks any Edit / Write tool call whose target file is outside `research/`.
Exit code 2 = block the tool call (Claude Code shows our stderr message to
the model as the reason). Exit code 0 = allow.

Registered in .claude/settings.json under hooks.PreToolUse (matcher: "Edit|Write").
"""
import json
import sys
from pathlib import Path

ALLOWED_PREFIX = "research"  # only writes under research/** are allowed
WORKTREE_ROOT = Path(__file__).resolve().parents[2]


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        print("research-agent hook: could not parse stdin JSON, blocking to be safe", file=sys.stderr)
        sys.exit(2)

    tool_input = payload.get("tool_input", {}) or {}
    file_path = tool_input.get("file_path", "")
    cwd = payload.get("cwd", str(WORKTREE_ROOT))

    if not file_path:
        print(
            "research-agent hook: this worktree only allows writes under research/**, "
            "and no file_path was provided to check — blocking.",
            file=sys.stderr,
        )
        sys.exit(2)

    target_path = Path(file_path)

    if not target_path.is_absolute():
        target_path = Path(cwd) / target_path

        # Some nested sessions report a parent checkout as cwd. A relative
        # research path still belongs to this hook's own worktree.
        if not target_path.resolve().is_relative_to(WORKTREE_ROOT):
            target_path = WORKTREE_ROOT / file_path

    try:
        rel_path = target_path.resolve().relative_to(WORKTREE_ROOT)
    except ValueError:
        rel_path = Path("..")

    rel_path_posix = rel_path.as_posix()

    if rel_path_posix.startswith("..") or not (
        rel_path_posix == ALLOWED_PREFIX or rel_path_posix.startswith(ALLOWED_PREFIX + "/")
    ):
        print(
            "research-agent hook: writes are restricted to 'research/**' in this worktree. "
            f"Attempted path: {rel_path_posix}. This agent may read existing code for context "
            "but must never modify it. Save findings under research/reports/ or research/data/ instead.",
            file=sys.stderr,
        )
        sys.exit(2)

    sys.exit(0)


if __name__ == "__main__":
    main()

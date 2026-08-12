#!/usr/bin/env python3
"""
PreToolUse hook for the Trade Guide research-only worktree.

Blocks any Edit / Write tool call whose target file is outside `research/`.
Exit code 2 = block the tool call (Claude Code shows our stderr message to
the model as the reason). Exit code 0 = allow.

Registered in .claude/settings.json under hooks.PreToolUse (matcher: "Edit|Write").
"""
import json
import os
import sys

ALLOWED_PREFIX = "research"  # only writes under research/** are allowed


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        print("research-agent hook: could not parse stdin JSON, blocking to be safe", file=sys.stderr)
        sys.exit(2)

    tool_input = payload.get("tool_input", {}) or {}
    file_path = tool_input.get("file_path", "")
    cwd = payload.get("cwd", os.getcwd())

    if not file_path:
        print(
            "research-agent hook: this worktree only allows writes under research/**, "
            "and no file_path was provided to check — blocking.",
            file=sys.stderr,
        )
        sys.exit(2)

    abs_path = file_path if os.path.isabs(file_path) else os.path.join(cwd, file_path)
    abs_path = os.path.normpath(abs_path)
    abs_cwd = os.path.normpath(cwd)

    try:
        rel_path = os.path.relpath(abs_path, abs_cwd)
    except ValueError:
        # different drive on Windows, etc. — treat as outside
        rel_path = ".." 

    rel_path_posix = rel_path.replace(os.sep, "/")

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

#!/usr/bin/env python3
"""Fail a commit when UI/design source files are staged without any documentation update."""
from __future__ import annotations

import os
import subprocess
import sys

SOURCE_PREFIXES = (
    "app/src/main/",
    "desktop/src/main/",
    "mobix-core/src/main/",
)
WATCHED_PREFIXES = (
    "app/src/main/java/com/mobixournal/ui/",
    "desktop/src/main/java/com/mobixournal/desktop/shell/",
    "desktop/src/main/java/com/mobixournal/desktop/tools/",
    "desktop/src/main/java/com/mobixournal/desktop/settings/",
    "desktop/src/main/java/com/mobixournal/desktop/sheet/",
    "desktop/src/main/java/com/mobixournal/desktop/navigation/",
    "mobix-core/src/main/java/com/mobixournal/design/",
)
DOC_PREFIXES = ("docs/", "AGENTS.md", "README.md")


def staged_files() -> list[str]:
    proc = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACM"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
    )
    return [line.strip().replace("\\", "/") for line in proc.stdout.splitlines() if line.strip()]


def main() -> int:
    if os.environ.get("MOBIX_SKIP_DOC_CHECK") == "1":
        return 0
    files = staged_files()
    if any(f.startswith(SOURCE_PREFIXES) for f in files) and "FINISHED.toml" not in files:
        print(
            "check-doc-sync (reminder): main source files are staged without FINISHED.toml — "
            "if this completes a feature or user-facing fix, record it via `todo.py add` + `todo.py done`.",
            file=sys.stderr,
        )
    watched = [f for f in files if f.startswith(WATCHED_PREFIXES)]
    if not watched:
        return 0
    has_docs = any(f.startswith(DOC_PREFIXES) for f in files)
    if has_docs:
        return 0

    print("check-doc-sync: UI/design source files are staged without a documentation update:", file=sys.stderr)
    for path in watched:
        print(f"  - {path}", file=sys.stderr)
    print(
        "Update the owning doc (e.g. docs/chrome.md, docs/interface-contract.md, docs/desktop-ui-layout.md) "
        "or set MOBIX_SKIP_DOC_CHECK=1 for a pure internal fix.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

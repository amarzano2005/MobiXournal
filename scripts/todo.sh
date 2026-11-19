#!/usr/bin/env bash
# Thin wrapper over the todo skill's Python CLI — manages the TOML task list
# (TODO.toml / FINISHED.toml). See .agents/skills/todo/SKILL.md for the full
# command reference. Examples:
#   scripts/todo.sh list
#   scripts/todo.sh stats
#   scripts/todo.sh add --title "..." --description "..." --category feature
#   scripts/todo.sh done <id> --shipped "what shipped"
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Probe execution, not just PATH: Windows Store aliases are not interpreters.
for candidate in \
    "${TODO_PYTHON:-}" \
    python3 \
    python \
    "${LOCALAPPDATA:-/nonexistent}/Python/bin/python.exe" \
    "${LOCALAPPDATA:-/nonexistent}/Programs/Python/Python313/python.exe"; do
    if [ -n "$candidate" ] && "$candidate" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)' >/dev/null 2>&1; then
        export PYTHONUTF8=1
        exec "$candidate" "$root/scripts/todo/todo.py" "$@"
    fi
done
echo 'todo.sh: no Python 3.11+ found; set TODO_PYTHON to a real interpreter (not the Store alias).' >&2
exit 1

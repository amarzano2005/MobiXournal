#!/usr/bin/env bash
# Thin wrapper over the todo skill's Python CLI — manages the TOML task list
# (TODO.toml / FINISHED.toml). See skills/todo/SKILL.md for the full
# command reference. Examples:
#   scripts/todo.sh list
#   scripts/todo.sh stats
#   scripts/todo.sh add --title "..." --description "..." --category feature
#   scripts/todo.sh done <id>
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$root/scripts/todo/todo.py" "$@"

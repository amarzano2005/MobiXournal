#!/usr/bin/env bash
# Thin wrapper over the UI/UX Pro Max skill's Python CLI: the design checklist this project
# consults before a UI change, and the corpus behind it. The repo contract stays the law —
# `docs/interface-contract.md` decides, this only supplies rules to check against.
# See docs/tools.md (`UI/UX Pro Max`) for the domains that answer well here, and
# skills/ui-ux-pro-max/SKILL.md for the skill's own workflow. Examples:
#   scripts/design.sh --domain ux "modal keyboard focus"
#   scripts/design.sh --domain style "tool app chrome"
#   scripts/design.sh --domain typography "ui scale for a dense toolbar"
#   scripts/design.sh --design-system "tablet note-taking editor"
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cli="$root/skills/ui-ux-pro-max/scripts/search.py"
if [ ! -f "$cli" ]; then
    echo "design.sh: the skill's search script is missing at $cli" >&2
    exit 1
fi

# On Windows `python`/`python3` may be the Microsoft Store stub, which prints a Store
# prompt and exits without running anything — and on the desktop PC the real interpreter
# sits under %LOCALAPPDATA%\Python. A candidate therefore counts only once it answers a
# probe that a stub cannot fake.
runs() { "$1" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 8) else 1)' >/dev/null 2>&1; }

python=""
for candidate in \
    "${DESIGN_PYTHON:-}" \
    python3 \
    python \
    "${LOCALAPPDATA:-/nonexistent}/Python/bin/python.exe" \
    "${LOCALAPPDATA:-/nonexistent}/Programs/Python/Python313/python.exe"; do
    if [ -n "$candidate" ] && runs "$candidate"; then
        python="$candidate"
        break
    fi
done

if [ -z "$python" ]; then
    cat >&2 <<'MESSAGE'
design.sh: no Python 3.8+ found. The Microsoft Store `python` stub does not count, so point
  DESIGN_PYTHON at a real interpreter:
    scripts/design.sh --domain ux "..."        # with DESIGN_PYTHON exported
    DESIGN_PYTHON="/c/Users/<you>/AppData/Local/Python/bin/python.exe" scripts/design.sh ...
MESSAGE
    exit 1
fi

exec "$python" "$cli" "$@"

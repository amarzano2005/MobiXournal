#!/usr/bin/env sh
# Cross-platform task verification wrapper. Runs scripts/verify.py with Python 3.11+.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export PYTHONUTF8=1

has_py311() {
    "$1" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' >/dev/null 2>&1
}

if [ -n "${TODO_PYTHON:-}" ] && has_py311 "$TODO_PYTHON"; then
    exec "$TODO_PYTHON" "$SCRIPT_DIR/verify.py" "$@"
fi

for candidate in python3 python python3.14 python3.13 python3.12 python3.11; do
    resolved=$(command -v "$candidate" 2>/dev/null || true)
    case "$resolved" in
        ""|*WindowsApps*) continue ;;
    esac
    if has_py311 "$resolved"; then
        exec "$resolved" "$SCRIPT_DIR/verify.py" "$@"
    fi
done

echo "Error: Python 3.11+ not found. Install Python 3.11+ or set TODO_PYTHON." >&2
exit 1

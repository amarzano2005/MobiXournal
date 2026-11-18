#!/usr/bin/env bash
# What a release would attach, rehearsed without publishing anything (`docs/releasing.md`).
#
#   bash scripts/rehearse-release.sh [assets-directory]
#
# The release job runs on a tag and only on a tag, so its assumptions — both artifacts arrive, their names
# carry the versions the two Gradle lines hold, and the note the Release body reads exists — used to be
# first tested by the release itself. A manual CI run executes this against the artifacts it just built,
# which is the half of the release job that can break quietly: a renamed asset leaves a release page
# without one of its installers.
set -euo pipefail

assets=${1:-assets}
root=$(cd "$(dirname "$0")/.." && pwd)
python_bin=${PYTHON:-python3}

versions=$("$python_bin" "$root/scripts/todo/todo.py" versions)
android=$(printf '%s\n' "$versions" | awk '/^android/ {print $2}')
desktop=$(printf '%s\n' "$versions" | awk '/^desktop/ {print $2}')
apk="MobiXournal-$android.apk"
msi="MobiXournal-$desktop.msi"

fail() { echo "rehearsal: $*" >&2; exit 1; }

[ -n "$android" ] || fail "no Android version in app/build.gradle.kts"
[ -n "$desktop" ] || fail "no desktop version in desktop/build.gradle.kts"
[ -f "$assets/$apk" ] || fail "$assets/$apk is missing: the release would attach half its downloads"
[ -f "$assets/$msi" ] || fail "$assets/$msi is missing: the release would attach half its downloads"
[ -f "$root/docs/releases/v$android.md" ] || fail "docs/releases/v$android.md is missing: the Release body has nothing to read"

echo "rehearsal ok: $apk, $msi, docs/releases/v$android.md"
echo "  the release would be titled: Release v$android (Android) and v$desktop (Desktop)"

#!/usr/bin/env sh
# Host fallback for the build/check loop: find a JDK the Gradle wrapper can actually start on,
# point JAVA_HOME at it, locate the Android SDK, then run the given Gradle tasks (default: unit
# tests + debug APK).
#
# Why this exists: `scripts/build.sh` is the front door whenever the shared /data/android toolchain
# (or Docker) is present, but a plain host has no JDK/SDK picker — and `./gradlew` on its own is
# where a session dies with an error that never names the cause. The wrapper is Gradle 8.14.5, which
# will not start on a JDK that is too new (it evaluates the Kotlin DSL with whatever JDK starts it,
# before any of our code compiles), and a JAVA_HOME pointing at `<jdk>/bin` is silently wrong. Both
# failures land before Kotlin is reached, which is what makes them look mysterious. This script
# removes that guesswork: it validates JAVA_HOME, searches the usual install locations for a
# supported JDK, exports the one it picked, and only then hands over to the wrapper.
#
# See docs/tools.md -> "Host fallback" for the pipeline this belongs to.
#
# Usage: scripts/host-build.sh [--jdk PATH] [--sdk PATH] [--list] [gradle tasks...]
#        scripts/host-build.sh                           # unit tests + debug APK
#        scripts/host-build.sh --list                    # report the JDK/SDK it would use
#        scripts/host-build.sh connectedDebugAndroidTest # any Gradle task passes through
set -eu

REPO="$(cd "$(dirname "$0")/.." && pwd)"

# Gradle 8.14.5's supported range for the JVM that starts it; 17 is also AGP's floor. Above 24 the
# wrapper aborts with "Unsupported class file major version" while evaluating the Kotlin DSL.
PREFERRED_JDKS="21 17 22 20 23 19 18 24"

JDK_OVERRIDE=""
SDK_OVERRIDE=""
LIST_ONLY=0

usage() {
    echo "Usage: scripts/host-build.sh [--jdk PATH] [--sdk PATH] [--list] [gradle tasks...]"
    echo "  --jdk PATH  use this JDK (default: the first supported one found)"
    echo "  --sdk PATH  use this Android SDK (default: ANDROID_HOME, then the usual locations)"
    echo "  --list      print the JDK and SDK that would be used, then stop"
    echo "  (no tasks)  testDebugUnitTest assembleDebug"
}

die() {
    echo "host-build: $1" >&2
    exit 1
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --jdk)
            JDK_OVERRIDE="${2:-}"
            [ -n "$JDK_OVERRIDE" ] || die "--jdk needs a path"
            shift 2
            ;;
        --sdk)
            SDK_OVERRIDE="${2:-}"
            [ -n "$SDK_OVERRIDE" ] || die "--sdk needs a path"
            shift 2
            ;;
        --list)
            LIST_ONLY=1
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *) break ;;
    esac
done

# The major version of the JDK at $1 — from its `release` file when present (no JVM start), else
# from `java -version`. Prints nothing and fails when $1 is not a JDK root.
jdk_major() {
    [ -x "$1/bin/java" ] || [ -f "$1/bin/java.exe" ] || return 1
    version=""
    if [ -f "$1/release" ]; then
        version=$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$1/release" | head -n 1)
    fi
    if [ -z "$version" ]; then
        version=$("$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)
    fi
    [ -n "$version" ] || return 1
    major="${version#1.}"
    printf '%s\n' "${major%%.*}"
}

# Where $1 sits in the preference order, or nothing when the wrapper cannot start on it. The list
# covers exactly the supported window, so support and preference cannot drift apart.
jdk_rank() {
    position=0
    for wanted in $PREFERRED_JDKS; do
        position=$((position + 1))
        if [ "$1" = "$wanted" ]; then
            printf '%s\n' "$position"
            return 0
        fi
    done
    return 1
}

# Every JDK root worth trying, most deliberate first: an explicit request, then the environment,
# then the per-user installs (IntelliJ's ~/.jdks, SDKMAN, Android Studio's bundled JBR), then the
# system locations of the three platforms. One path per line: install paths contain spaces.
jdk_candidates() {
    for explicit in "${HOST_JDK:-}" "${JAVA_HOME:-}" "$JDK_OVERRIDE"; do
        [ -n "$explicit" ] && printf '%s\n' "${explicit%/}"
    done
    for root in \
        "$HOME/.jdks" \
        "$HOME/.sdkman/candidates/java" \
        "$HOME/.gradle/jdks" \
        "$HOME/java" \
        "$HOME/Applications" \
        "/usr/lib/jvm" \
        "/usr/java" \
        "/opt/java" \
        "/opt" \
        "/Library/Java/JavaVirtualMachines" \
        "/opt/homebrew/opt" \
        "/usr/local/opt"; do
        [ -d "$root" ] || continue
        for entry in "$root"/*; do
            [ -e "$entry" ] || continue
            # macOS bundles and Android Studio installs keep the JDK one or two levels down.
            printf '%s\n' "$entry"
            printf '%s\n' "$entry/Contents/Home"
            printf '%s\n' "$entry/jbr"
            printf '%s\n' "$entry/Contents/jbr/Contents/Home"
        done
    done
    for fixed in \
        "${LOCALAPPDATA:-}/Programs/Android Studio/jbr" \
        "/c/Program Files/Android/Android Studio/jbr" \
        "/c/Program Files/Java"/* \
        "/usr/lib/jvm/default-java" \
        "/opt/java/openjdk"; do
        [ -n "${fixed#/}" ] && [ -e "$fixed" ] && printf '%s\n' "${fixed%/}"
    done
    # Last resort: whatever `java` on PATH resolves to. It is often the one that is too new, which
    # is exactly why it is tried after everything else.
    on_path=$(command -v java 2>/dev/null || true)
    [ -n "$on_path" ] && printf '%s\n' "$(dirname "$(dirname "$on_path")")"
    return 0
}

pick_jdk() {
    if [ -n "$JDK_OVERRIDE" ]; then
        major=$(jdk_major "$JDK_OVERRIDE") || die "--jdk $JDK_OVERRIDE has no bin/java"
        jdk_rank "$major" >/dev/null ||
            die "--jdk $JDK_OVERRIDE is JDK $major; Gradle 8.14.5 needs one of $PREFERRED_JDKS"
        printf '%s\n' "${JDK_OVERRIDE%/}"
        return 0
    fi
    best=""
    best_rank=999
    while IFS= read -r candidate; do
        major=$(jdk_major "$candidate" 2>/dev/null || true)
        [ -n "$major" ] || continue
        rank=$(jdk_rank "$major" || true)
        [ -n "$rank" ] || continue
        if [ "$rank" -lt "$best_rank" ]; then
            best="$candidate"
            best_rank="$rank"
        fi
    done <<EOF
$(jdk_candidates | sort -u)
EOF
    [ -n "$best" ] || die "no JDK $PREFERRED_JDKS found (looked at \$JAVA_HOME, ~/.jdks, /usr/lib/jvm, Android Studio's jbr, …); pass --jdk <path>"
    printf '%s\n' "$best"
}

# local.properties is Android's own SDK pointer, so a path already recorded there counts as a
# candidate — reading it back means a machine set up once by Android Studio needs no flags.
sdk_candidates() {
    for explicit in "$SDK_OVERRIDE" "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [ -n "$explicit" ] && printf '%s\n' "${explicit%/}"
    done
    if [ -f "$REPO/local.properties" ]; then
        recorded=$(sed -n 's/^sdk\.dir=//p' "$REPO/local.properties" | head -n 1)
        if [ -n "$recorded" ]; then
            # Gradle escapes a Windows path as C\:\\Users\\…; undo that before using it here.
            recorded=$(printf '%s' "$recorded" | sed -e 's/\\\\/\\/g' -e 's/\\:/:/g')
            if command -v cygpath >/dev/null 2>&1; then
                recorded=$(cygpath -u "$recorded" 2>/dev/null || printf '%s' "$recorded")
            fi
            printf '%s\n' "${recorded%/}"
        fi
    fi
    for root in \
        "${LOCALAPPDATA:-}/Android/Sdk" \
        "$HOME/AppData/Local/Android/Sdk" \
        "$HOME/Library/Android/sdk" \
        "$HOME/Android/Sdk" \
        "$HOME/Android/sdk" \
        "/usr/lib/android-sdk" \
        "/opt/android-sdk" \
        "/opt/android/sdk"; do
        [ -n "${root#/}" ] && printf '%s\n' "$root"
    done
    return 0
}

# platform-tools is what Gradle, adb and the emulator all reach for first; a platforms/ directory
# without it still identifies an SDK, so either is accepted.
is_sdk() {
    [ -x "$1/platform-tools/adb" ] || [ -f "$1/platform-tools/adb.exe" ] || [ -d "$1/platforms" ]
}

pick_sdk() {
    while IFS= read -r candidate; do
        if is_sdk "$candidate"; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done <<EOF
$(sdk_candidates | sort -u)
EOF
    if [ -n "$SDK_OVERRIDE" ]; then
        die "--sdk $SDK_OVERRIDE does not look like an Android SDK (no platform-tools, no platforms)"
    fi
    die "no Android SDK found (set ANDROID_HOME or pass --sdk <path>)"
}

# Record the SDK for Gradle the way Android Studio does, but only when there is nothing there yet:
# an existing local.properties is the user's (or their IDE's) and is never rewritten.
record_sdk() {
    [ -f "$REPO/local.properties" ] && return 0
    sdk_path="$1"
    if command -v cygpath >/dev/null 2>&1; then
        sdk_path=$(cygpath -w "$sdk_path" 2>/dev/null || printf '%s' "$sdk_path")
    fi
    escaped=$(printf '%s' "$sdk_path" | sed -e 's/\\/\\\\/g' -e 's/:/\\:/g')
    printf 'sdk.dir=%s\n' "$escaped" > "$REPO/local.properties"
    echo "host-build: wrote sdk.dir to local.properties"
}

JDK=$(pick_jdk)
SDK=$(pick_sdk)
record_sdk "$SDK"

echo "host-build: JDK $(jdk_major "$JDK" || echo '?') ($JDK)"
echo "host-build: SDK $SDK"

if [ "$LIST_ONLY" -eq 1 ]; then
    exit 0
fi

JAVA_HOME="$JDK"
ANDROID_HOME="$SDK"
ANDROID_SDK_ROOT="$SDK"
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT

if [ "$#" -eq 0 ]; then
    set -- testDebugUnitTest assembleDebug
fi

if [ -x "$REPO/gradlew" ]; then
    exec "$REPO/gradlew" "$@"
fi
# A Windows checkout without the executable bit still runs through the shell.
exec sh "$REPO/gradlew" "$@"

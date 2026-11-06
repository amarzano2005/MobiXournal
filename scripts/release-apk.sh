#!/usr/bin/env sh
# Build the APK to actually install on a tablet: a **release** build, signed with the debug key.
#
# Why this exists: `assembleDebug` is the check artifact, not the one to judge the app by. A debug
# build runs `debuggable` (the debugger hooks ART keeps for it) and keeps Compose's debug checks, so
# on a page carrying ink it feels slower than the release build of the same code — which is exactly
# the difference that made the tablet look like it had lost its snap. R8 is off in both here, so the
# gain is the flag, not a shrink. This script builds the release variant and signs it with the Android
# debug keystore (~/.android/debug.keystore), the same key `assembleDebug` signs with, so the two
# install over each other in both directions and switching costs no app data.
#
# The build itself goes through this repo's own front door (scripts/build.sh when the shared
# /data/android toolchain is present, scripts/host-build.sh otherwise), so the JDK and SDK
# discovery is not repeated here.
#
# See docs/tools.md -> "Android build" -> "The APK to install: a release build".
#
# Usage: scripts/release-apk.sh
set -eu

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

die() {
    echo "release-apk: $1" >&2
    exit 1
}

# --- 1. the unsigned release APK, through the front door ------------------------------------------
if [ -x "${ANDROID_TOOLCHAIN:-/data/android}/build.sh" ]; then
    sh scripts/build.sh :app:assembleRelease
else
    sh scripts/host-build.sh :app:assembleRelease
fi

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED="app/build/outputs/apk/release/app-release.apk"
ALIGNED="app/build/outputs/apk/release/app-release-aligned.apk"
[ -f "$UNSIGNED" ] || die "no unsigned release APK at $UNSIGNED — did :app:assembleRelease run?"

# --- 2. the SDK's build-tools, where zipalign and apksigner live ----------------------------------
SDK=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
    "$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | tr -d '\r')" \
    "$HOME/AppData/Local/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then
        SDK="$candidate"
        break
    fi
done
[ -n "$SDK" ] || die "no Android SDK found — set ANDROID_HOME or sdk.dir in local.properties"

BUILD_TOOLS="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[ -n "$BUILD_TOOLS" ] || die "no build-tools under $SDK — install one with sdkmanager"

# Git Bash runs the platform wrappers (zipalign.exe, apksigner.bat); a POSIX host has the plain
# executables — the two suffixes are chosen separately because apksigner ships as a .bat, not a .exe.
ZIPALIGN="${BUILD_TOOLS}zipalign"
[ -f "$ZIPALIGN.exe" ] && ZIPALIGN="$ZIPALIGN.exe"
SIGNER="${BUILD_TOOLS}apksigner"
if [ -f "$SIGNER.bat" ]; then
    SIGNER="$SIGNER.bat"
elif [ -f "$SIGNER.exe" ]; then
    SIGNER="$SIGNER.exe"
fi

# --- 3. the debug keystore, created exactly as Android creates it if it is missing ----------------
KEYSTORE="${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    KEYTOOL="keytool"
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
        KEYTOOL="$JAVA_HOME/bin/keytool"
    fi
    "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1 ||
        die "could not create a debug keystore at $KEYSTORE"
    echo "release-apk: created $KEYSTORE (the key the debug build already uses)"
fi

# --- 4. align, sign, verify ----------------------------------------------------------------------
"$ZIPALIGN" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$SIGNER" sign \
    --ks "$KEYSTORE" --ks-pass pass:android --ks-key-alias androiddebugkey \
    --key-pass pass:android --out "$SIGNED" "$ALIGNED"
rm -f "$ALIGNED"
"$SIGNER" verify "$SIGNED" || die "the signed APK does not verify"

echo "release-apk: $SIGNED ($(wc -c <"$SIGNED" | tr -d ' ') bytes, signed with the debug key)"
echo "release-apk: install with adb install -r $SIGNED"

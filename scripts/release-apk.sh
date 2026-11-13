#!/usr/bin/env sh
# Build a release APK: debug-signed locally, or with a verified persistent certificate on CI tags.
#
# Why this exists: `assembleDebug` is the check artifact, not the one to judge the app by. A debug
# build runs `debuggable` (the debugger hooks ART keeps for it) and keeps Compose's debug checks, so
# on a page carrying ink it feels slower than the release build of the same code — which is exactly
# the difference that made the tablet look like it had lost its snap. R8 is off in both here, so the
# gain is the flag, not a shrink. By default this script signs the release variant with the Android
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

# Published APKs need one persistent certificate; never mint a replacement identity on a runner.
RELEASE_SIGNING="${MOBIX_REQUIRE_RELEASE_SIGNING:-0}"
SIGNING_FILE=""
if [ "$RELEASE_SIGNING" = "1" ]; then
    for required in MOBIX_ANDROID_KEYSTORE_BASE64 MOBIX_ANDROID_KEY_ALIAS MOBIX_ANDROID_STORE_PASSWORD MOBIX_ANDROID_KEY_PASSWORD MOBIX_ANDROID_CERT_SHA256; do
        eval 'present=${'"$required"':-}'
        [ -n "$present" ] || die "missing $required - published APK signing must be configured"
    done
    expected=$(printf '%s' "$MOBIX_ANDROID_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
    [ "${#expected}" -eq 64 ] || die "MOBIX_ANDROID_CERT_SHA256 must contain 64 hexadecimal digits"
    case "$expected" in *[!0-9a-f]*) die "MOBIX_ANDROID_CERT_SHA256 is not hexadecimal" ;; esac
    SIGNING_FILE=$(mktemp)
    trap 'rm -f "$SIGNING_FILE"' EXIT HUP INT TERM
    printf '%s' "$MOBIX_ANDROID_KEYSTORE_BASE64" | base64 --decode > "$SIGNING_FILE" || die "invalid signing keystore encoding"
    [ -s "$SIGNING_FILE" ] || die "the signing keystore is empty"
fi

# The published asset carries the app's own version, the way the MSI does: `MobiXournal-<version>.apk`.
# The number is read out of the Gradle file rather than copied here, so a version bump moves the name.
# It is read before the build, so a missing version costs nothing instead of a finished APK.
VERSION="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts 2>/dev/null | head -1 | tr -d '\r' || true)"
[ -n "$VERSION" ] || die "app/build.gradle.kts has no versionName, so the APK has no version in its name"

# --- 1. the unsigned release APK, through the front door ------------------------------------------
if [ -x "${ANDROID_TOOLCHAIN:-/data/android}/build.sh" ]; then
    sh scripts/build.sh :app:assembleRelease
else
    sh scripts/host-build.sh :app:assembleRelease
fi

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
# The published name, not Gradle's: a download and a release page should both say MobiXournal, and the
# update check only needs the `.apk` suffix (the tablet's version comes from the tag, not this name).
SIGNED="app/build/outputs/apk/release/MobiXournal-$VERSION.apk"
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
if [ "$RELEASE_SIGNING" != "1" ] && [ ! -f "$KEYSTORE" ]; then
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
if [ "$RELEASE_SIGNING" = "1" ]; then
    "$SIGNER" sign --ks "$SIGNING_FILE" --ks-pass env:MOBIX_ANDROID_STORE_PASSWORD \
        --ks-key-alias "$MOBIX_ANDROID_KEY_ALIAS" --key-pass env:MOBIX_ANDROID_KEY_PASSWORD \
        --out "$SIGNED" "$ALIGNED"
    # The digest line is read by pattern rather than by a pinned sentence: `apksigner` prints a signer
    # number and its own spacing, and a build-tools release that rewords the line must not be mistaken for
    # a certificate that changed.
    certs=$("$SIGNER" verify --print-certs "$SIGNED") || { rm -f "$SIGNED"; die "apksigner could not verify the signed APK"; }
    actual=$(printf '%s\n' "$certs" | sed -n 's/^.*SHA-256 digest:[[:space:]]*//p' | head -1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
    [ -n "$actual" ] || { rm -f "$SIGNED"; die "apksigner printed no certificate digest, so the identity cannot be checked"; }
    if [ "$actual" != "$expected" ]; then
        rm -f "$SIGNED"
        die "the APK certificate does not match MOBIX_ANDROID_CERT_SHA256 (it is $actual)"
    fi
else
    "$SIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --ks-key-alias androiddebugkey \
        --key-pass pass:android --out "$SIGNED" "$ALIGNED"
fi
rm -f "$ALIGNED"
"$SIGNER" verify "$SIGNED" || die "the signed APK does not verify"

echo "release-apk: $SIGNED ($(wc -c <"$SIGNED" | tr -d ' ') bytes, verified signing identity)"
echo "release-apk: install with adb install -r $SIGNED"

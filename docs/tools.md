# Tools & build pipelines

Authoritative home for **external tooling** this project depends on — build pipelines, deploy
scripts, device/emulator harnesses, code generators, and anything else that isn't part of the
repo's own in-tree build. `AGENTS.md` only *points* here; the specifics live in this file (or
in a dedicated `docs/<tool>.md` that this file links when the detail is large).

Add one entry per tool. Keep each entry to what an AI agent needs to *use* it: where it
lives, how to invoke it, and the non-obvious gotchas.

## Index

| Tool / pipeline | What it's for | Detail |
|-----------------|---------------|--------|
| Android build | Compile & package the app (APK/AAB) | [below](#android-build) |
| Android emulator | Run & test the app on a virtual device | [below](#android-emulator) |
| Release publishing | Freeze a version, draft its changelog, tag + publish | [below](#cutting-a-release) |

---

## Android build

The pipeline that compiles and packages the Android app. **All APKs on this box are built with
the shared Android toolchain in `/data/android`** — the single front door for building (and
running) Android apps without a JDK/SDK/Gradle on the host. Per `AGENTS.md`, the build runs
inside a container; that container is the baked `android-builder:local` image maintained in
`/data/android`, not one owned by this repo. This repo only supplies its Gradle project + the
Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties` is the authority for its version); the
toolchain supplies JDK 21 + the Android SDK (`platforms;android-34/35`, `build-tools;34.0.0/35.0.0`).

- **Where it lives:** `/data/android/` — `build.sh` (the disposable-container build front door)
  and `Dockerfile.builder` (the baked `android-builder:local` image). That directory has its
  own `README.md`/`AGENTS.md`; read them for the full contract. This repo's `scripts/build.sh`
  is a thin wrapper that calls `/data/android/build.sh` (override the location with
  `ANDROID_TOOLCHAIN`).
- **How to run it:**
  - `scripts/build.sh` — the default check loop: `testDebugUnitTest assembleDebug`.
  - `scripts/build.sh <tasks…>` — arbitrary Gradle tasks, e.g. `scripts/build.sh testDebugUnitTest`
    or `scripts/build.sh clean assembleDebug`.
  - Direct equivalent: `/data/android/build.sh <project-dir> <tasks…>`.
- **Outputs:** debug APK at `app/build/outputs/apk/debug/app-debug.apk`; unit-test reports at
  `app/build/reports/tests/testDebugUnitTest/`.
- **Gotchas:**
  - The builder mounts the project's **parent** dir as `/workspace` and runs `./gradlew` in the
    project subdir, so sibling files resolve; it keeps a **per-project Gradle cache** at
    `.gradle-cache/` (git-ignored) and sets `HOME` there for a stable debug keystore.
  - Runs as your UID (`--user`) — build outputs are owned by you, not root. (Don't run the
    builder image as root against the mount, or `build/` becomes root-owned and later
    user-mode builds can't clean it.)
  - The image is already baked; only Gradle + dependencies download on first use into
    `.gradle-cache/`. No `local.properties` needed — the SDK is baked in.
  - The parent mount is what makes the optional real-file test resolve its repo-root sample; the
    rule is documented in [`architecture.md`](architecture.md#what-the-unit-tests-cover).

### Host fallback — no `/data/android`, no Docker

`/data/android` is a **machine-local path**. On a host that doesn't have it, `scripts/build.sh`
stops immediately ("Shared Android toolchain not found at /data/android") and the build runs on the
host's own toolchain — the `./gradlew` loop `AGENTS.md` already lists for host systems. Two things
the container would otherwise supply have to be there, and they are where a host build usually
fails:

- **A supported JDK.** `./gradlew` runs on the JDK `JAVA_HOME` points at, and a Gradle version
  accepts only a range of them — newer than that range, the build dies before compiling anything
  (a JDK 25 host, for instance, fails evaluating the Kotlin DSL). Any JDK the wrapper supports works
  (17–24 for the wrapper in the tree today); Android Studio ships one at
  `<Android Studio>/jbr`, and the toolchain's own JDK 21 is the safe choice. Check `java -version`
  *and* `JAVA_HOME` — they are not necessarily the same JDK, and `JAVA_HOME` must point at the JDK
  root, not its `bin`.
- **The Android SDK**, from `local.properties`' `sdk.dir` (Android Studio installs one).

Rather than doing that discovery by hand every session, **`scripts/host-build.sh` does it**: it validates
`JAVA_HOME`/`HOST_JDK`, searches the usual install locations for a JDK the wrapper supports (17–24,
preferring 21 — `~/.jdks`, SDKMAN, Android Studio's `jbr`, `/usr/lib/jvm`, the JDKs in `PATH` as a last
resort), finds the SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT`, `local.properties`' existing `sdk.dir`, then
the platform defaults), records `sdk.dir` **only when `local.properties` doesn't exist yet**, exports
`JAVA_HOME`/`ANDROID_HOME`/`ANDROID_SDK_ROOT`, and hands over to the wrapper. `--list` reports what it
would use without building; `--jdk`/`--sdk` override the search; every other argument is a Gradle task.

```sh
scripts/host-build.sh                       # unit tests + debug APK (the loop's default)
scripts/host-build.sh --list                # which JDK and SDK it picked, then stop
scripts/host-build.sh testDebugUnitTest     # any Gradle task passes through
```

It fails loudly with the JDK window and where it looked when it finds nothing, so the failure names
the cause instead of surfacing as Gradle's "Unsupported class file major version".

## Android emulator

Running the APK on a device/emulator also goes through `/data/android` — a headless Android 14
emulator you drive over `adb`, plus physical devices on the tailnet (see its `config.yaml`).
**This emulator is the standard way to test the app: don't stop at "it compiles."** Every
change with a runtime surface must be installed and exercised on the emulator, not just built.

- **Where it lives:** `/data/android/` — `docker-compose.yml` (the emulator container) and
  the emulator driver scripts (`emulator.sh`), alongside `adb-targets.sh`
  which lists every target across the two isolated adb worlds (host adb → physical devices,
  container adb → emulator). Full details in that directory's `README.md`; don't duplicate them here.
- **How to run it:** `emulator.sh` dispatches `status | up | boot-wait | down | install <apk> |
  launch <pkg> | screenshot [png] | ui | logcat | shell | adb` — so `emulator.sh up` to boot, then
  `emulator.sh install <apk>`, `emulator.sh launch com.mobixournal`, `emulator.sh screenshot <png>`,
  `emulator.sh ui`, and `emulator.sh logcat` for logs. Run `adb-targets.sh` to see which devices and
  the emulator are reachable. Physical devices install via the host adb:
  `adb -s <ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk`.
- **Testing a change on the emulator (the expected loop):** after a green
  `scripts/build.sh`, install the fresh APK and actually drive it:
  1. `emulator.sh install app/build/outputs/apk/debug/app-debug.apk` then
     `emulator.sh launch com.mobixournal`.
  2. **Take screenshots** (`emulator.sh screenshot <png>`) and look at them to confirm the UI
     rendered as intended — this is how you *see* the change, not infer it.
  3. **Read the error logs** — `adb logcat` (filter to the app) — to catch crashes, stack
     traces, and warnings the build can't surface.
  4. **Simulate touch / stylus input** — finger presses, taps, and swipes over `adb` (e.g.
     `adb shell input tap <x> <y>` / `input swipe …`, or `emulator.sh ui`) to exercise
     drawing, tool selection, open/save, and other interactions end-to-end.
  Report what the screenshots and logs actually showed; a change isn't verified until it's
  been run this way on the emulator.
- **Promoting to physical devices — not our job.** Emulator verification is where
  our loop ends. Deployment tooling or manual installation handles moving the built APK to
  physical devices as needed. Don't `adb install` to those devices as a routine step. (Manual `adb -s <ip>:5555 install
  -r …` still works if you ever need a one-off, but it isn't part of the standard flow.)
- **Running the instrumented (`androidTest`) suite:** use `scripts/connected-test.sh`
  (wrapper over the toolchain's `connected-test.sh`), **not**
  Gradle's `connectedDebugAndroidTest`. Gradle's task starts an adb server inside the throwaway
  build container — a different adb world than the emulator — so it dies with "No connected
  devices!". The wrapper instead builds the app + `androidTest` APKs in the builder, reads the
  instrumentation component from the test APK's manifest, then installs and runs it through the
  emulator container's own adb; its exit status is the test result (non-zero if any test fails).
  - `scripts/connected-test.sh` — build + run the whole `SmokeTest` suite on the emulator.
  - `scripts/connected-test.sh -e class com.mobixournal.SmokeTest` — extra args pass through to
    `am instrument` (class/method/size filters, etc.).
- **Gotchas:** the emulator needs host KVM (`/dev/kvm`, VT-x enabled in BIOS).

### Host fallback — Android Studio's own emulator

Where `/data/android` isn't present, the SDK's own emulator is the same loop, driven by the SDK's
`adb` instead of `emulator.sh`:

```sh
$ANDROID_SDK/emulator/emulator -avd <name> -no-window &   # or start it from Android Studio
$ANDROID_SDK/platform-tools/adb wait-for-device           # then poll sys.boot_completed for 1
$ANDROID_SDK/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
$ANDROID_SDK/platform-tools/adb shell am start -n com.mobixournal/.MainActivity
$ANDROID_SDK/platform-tools/adb exec-out screencap -p > shot.png   # and: logcat, input tap/swipe
```

**The instrumented suite runs on this host too.** Gradle's `connectedDebugAndroidTest` is only
broken *inside the container*, where its adb is a different world from the emulator's; on a host the
SDK's adb **is** the emulator's adb, so the ordinary task works — and unlike `scripts/connected-test.sh`
it needs no container at all:

```sh
# Whole androidTest suite on the attached emulator/device
scripts/host-build.sh connectedDebugAndroidTest

# One class, with the runner argument AGP expects
scripts/host-build.sh -Pandroid.testInstrumentationRunnerArguments.class=com.mobixournal.StylusInputTest \
    connectedDebugAndroidTest
```

That is how the stylus paths are exercised where `adb shell input` cannot reach (`StylusInputTest`
injects real `MotionEvent`s carrying tool type, button state and per-sample pressure).

- **Check the accelerator first:** `emulator -accel-check` reports whether WHPX (Windows) / KVM is
  usable; without it an x86_64 image won't boot in reasonable time.
- **A fresh boot comes up with the screen dozing and the notification shade focused.** Screenshots
  taken then are black: `adb shell input keyevent KEYCODE_WAKEUP` (and `82` to dismiss the lock
  screen) first, then confirm with `adb shell dumpsys window | grep mCurrentFocus`.
- **A finger only draws when the app's *Finger draws* setting is on** — otherwise it pans, which
  looks exactly like "my input did nothing". `adb shell input` cannot send stylus events, so either
  turn that setting on or exercise pen-only paths another way.
- **Find a control's coordinates instead of guessing them:** `adb shell uiautomator dump` then
  `adb pull` gives every button's `content-desc` and bounds (`Tool: Pen`, `Drawing guides`, …).
- **Verify colours and geometry from the screenshot, not by eye:** crop and sample it (PIL/`magick`),
  e.g. compare a pop-up's fill with the toolbar's. Two pixels that look alike are one `assert` apart.
- **Windows/Git Bash mangles `/sdcard/…` paths** into `C:/Program Files/Git/sdcard/…`: prefix the
  command with `MSYS_NO_PATHCONV=1`, or write the remote path as `//sdcard/…`.
- **`adb devices` showing `unauthorized` usually means the AVD resumed from a snapshot**, not a bad
  key: the guest's `adb_keys` belongs to the adb of the session that saved the snapshot, so the boot
  authorization never runs. Cold-boot it (`emulator … -no-snapshot-load`, no `-wipe-data` needed) and
  the host's key is injected again — measured: 150 s to `device` on a Pixel_Tablet image, where the
  same AVD from a snapshot stayed `unauthorized` indefinitely.
- **To check both themes of a visual change**, flip the guest rather than rebuilding:
  `adb shell cmd uimode night yes|no` — chrome colours and any theme-derived edge change with it.
- **A change is only verified when the screenshot shows it.** Diff the "before" and "after" captures
  of the same screen with PIL/`magick` and read the pixels at the edge; that is how a pop-up rim was
  measured as a 2 px step from `(238,237,244)` to `(173,173,180)` in the light theme and from
  `(30,31,37)` to `(89,90,96)` in the dark one, and how an elevated menu was shown to paint *inside*
  its own top edge instead of casting a shadow.

---

## Cutting a release

Releases are produced by CI, never by hand. Pushing an annotated tag `vX.Y.Z` runs
`.github/workflows/build.yml`, which builds the debug APK and creates the GitHub Release with
that APK attached and its notes read from `docs/releases/vX.Y.Z.md` (the step's `body_path`).

**A version's contents are the *pending* set in `FINISHED.toml`** — every finished task that does
not carry a `release` stamp — so what the next release ships is a fact about the archive, not a
recollection. Freezing the version is therefore a command:

```sh
scripts/todo.sh list --finished --unreleased            # what the next version will ship
scripts/todo.sh release --version X.Y.Z --bump-gradle   # freeze + changelog draft + version bump
```

`release` stamps every pending task with that version (so the next release starts from an empty
pending set) and writes `docs/releases/vX.Y.Z.md` grouped by category. It refuses to overwrite a
changelog that already exists (`--force` to mean it), refuses a version that is not newer than the
last recorded one, and refuses to run with nothing pending. `--dry-run` prints the draft and
changes nothing; `--no-changelog` stamps without writing a file; `--through YYYY-MM-DD` is the
one-shot backfill for a release that predates the field. Implementation: `scripts/todo/release.py`.
Regression check: `python3 scripts/todo/test_release.py` (dependency-free, exits non-zero on
failure).

1. **Run the release command** (above). With `--bump-gradle` it sets `versionName` to the version
   and increments `versionCode` in `app/build.gradle.kts` — the counter is monotonic, not derived
   from the name.
2. **Curate `docs/releases/vX.Y.Z.md`.** The generated file is a *draft*: group the bullets,
   reword them, add the highlights a raw list cannot show. The file name must match the tag
   exactly.
3. **Commit and push `main`**, then create and push the tag:

   ```sh
   git push origin main
   git tag -a v1.1.0 -m "MobiXournal v1.1.0"
   git push origin v1.1.0
   ```

4. **Watch the workflow.** The release appears on GitHub with `app-debug.apk` attached.

The workflow enforces the freeze rather than trusting it: every run lints both task files and runs
`scripts/todo/test_release.py`, and a `v*` tag additionally runs
`scripts/todo/todo.py verify-release --version <tag>`, which fails the build when the archive does
not record that version as released, when `docs/releases/vX.Y.Z.md` is missing, or when
`versionName` does not match the tag — i.e. when `release` was skipped. Run the same command
locally before pushing a tag to catch it in a second instead of a CI cycle.

- **The changelog is generated, then curated.** `todo.sh release` guarantees the *collection* is
  complete and correctly scoped to the version; the curation (grouping, wording, highlights) is
  still a human pass on `docs/releases/vX.Y.Z.md`.
- **Notes are curated, so `generate_release_notes` is off.** GitHub's auto-generated commit
  list would never see this file.
- **A tag without its notes file fails the release step** — deliberately: the workflow's
  `body_path` finds nothing and errors, rather than silently publishing an empty body. Write
  `docs/releases/vX.Y.Z.md` *before* pushing the tag.

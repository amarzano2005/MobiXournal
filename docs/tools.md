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
| Release publishing | Tag a version, publish the GitHub Release + APK | [below](#cutting-a-release) |

---

## Android build

The pipeline that compiles and packages the Android app. **All APKs on this box are built with
the shared Android toolchain in `/data/android`** — the single front door for building (and
running) Android apps without a JDK/SDK/Gradle on the host. Per `AGENTS.md`, the build runs
inside a container; that container is the baked `android-builder:local` image maintained in
`/data/android`, not one owned by this repo. This repo only supplies its Gradle project + the
Gradle wrapper (pinned to Gradle 8.9); the toolchain supplies JDK 21 + the Android SDK
(`platforms;android-34/35`, `build-tools;34.0.0/35.0.0`).

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

---

## Cutting a release

Releases are produced by CI, never by hand. Pushing an annotated tag `vX.Y.Z` runs
`.github/workflows/build.yml`, which builds the debug APK and creates the GitHub Release with
that APK attached and its notes read from `docs/releases/vX.Y.Z.md` (the step's `body_path`).

1. **Bump the version** in `app/build.gradle.kts`: `versionName` is the tag without its `v`
   (`1.1.0`), `versionCode` is the previous code plus one (a monotonic counter, not derived
   from the name).
2. **Write `docs/releases/vX.Y.Z.md`** — the curated, user-facing changelog for the version.
   Draw it from `FINISHED.toml` (the completed-work archive) plus the commits since the last
   tag; the file name must match the tag exactly.
3. **Commit and push `main`**, then create and push the tag:

   ```sh
   git push origin main
   git tag -a v1.1.0 -m "MobiXournal v1.1.0"
   git push origin v1.1.0
   ```

4. **Watch the workflow.** The release appears on GitHub with `app-debug.apk` attached.

- **Notes are curated, so `generate_release_notes` is off.** GitHub's auto-generated commit
  list would never see this file.
- **A tag without its notes file fails the release step** — deliberately: the workflow's
  `body_path` finds nothing and errors, rather than silently publishing an empty body. Write
  `docs/releases/vX.Y.Z.md` *before* pushing the tag.

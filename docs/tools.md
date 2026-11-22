# Tools & build pipelines

> Read when you build, test, package or release the project, or reach for one of the tools it lists.
> For the task: [Installing on Android](guide-install-android.md), [Installing on Windows 11](guide-install-windows.md), [What works](guide-limitations.md).

Authoritative home for **external tooling** this project depends on: build pipelines, deploy scripts,
device/emulator harnesses, code generators, and anything else outside the repo's own in-tree build.
`AGENTS.md` only *points* here; a detail lives here, or in the `docs/<tool>.md` that this file links.

Add one entry per tool, kept to what an agent needs to *use* it: where it lives, how to call it, and its gotchas.

The design declaration that closes a UI change is [design-workflow.md](design-workflow.md)'s.

## Index

| Tool / pipeline | What it's for | Detail |
|-----------------|---------------|--------|
| Host selection | OS, machine profiles and local toolchain paths | [host-profiles.md](host-profiles.md) |
| Agent task workflow | `scripts/todo.sh` / `scripts/todo.cmd`, active-task validation and instruction checks | [agent-workflow.md](agent-workflow.md) |
| Doc-sync commit guard | `scripts/check-doc-sync.py` + `.githooks/pre-commit` (blocks UI/design commits missing a `docs/` update) | [writing-docs.md](writing-docs.md) |
| Task verification | Sequential validate, test, guard, and distributable runner | [below](#task-verification-wrapper) |
| Android build | Compile & package the app (APK/AAB) | [below](#android-build) |
| Android emulator | Run & test the app on a virtual device | [tools-emulator.md](tools-emulator.md) |
| Desktop shell | Run, test and package the Compose Multiplatform window (no emulator, no container) | [below](#desktop-shell) |
| Release publishing | Freeze a version, draft its changelog, tag + publish | [releasing.md](releasing.md) |
| Design declaration | The declaration that closes a UI change in `todo.sh done` | [below](design-workflow.md) |
| Mobix corpora | Regenerate the golden and hostile `.mobix` fixture corpora | [below](#mobix-corpora) |

---

## Android build

The pipeline that compiles and packages the Android app. **When `/data/android/build.sh` exists, use
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
  - `scripts/build.sh` — the default check loop: `:app:testDebugUnitTest :mobix-core:test
    :app:compileDebugAndroidTestKotlin :app:assembleDebug` — **both** suites, because
    `testDebugUnitTest` exists only in the Android module and would leave the core (codecs, model,
    `FormatDriftTest`) unrun, plus the instrumented-test **compile** (`docs/testing.md`), because no
    device executes those here and an uncompiled `androidTest` is a claim nobody checked.
  - `scripts/build.sh <tasks…>` — arbitrary Gradle tasks, e.g. `scripts/build.sh :app:testDebugUnitTest`
    or `scripts/build.sh clean assembleDebug`.
  - Direct equivalent: `/data/android/build.sh <project-dir> <tasks…>`.
- **Outputs:** debug APK at `app/build/outputs/apk/debug/app-debug.apk`; unit-test reports at
  `app/build/reports/tests/testDebugUnitTest/` and `mobix-core/build/reports/tests/test/`.
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
    rule is documented in [`testing.md`](testing.md#what-the-unit-tests-cover).

### The APK you install, and why it should be the release one

No `applicationIdSuffix` is set, so every APK installs as `com.mobixournal`, upgrading in place rather
than beside it (`emulator.sh launch com.mobixournal`, `adb logcat`) — and the build you install *is*
the app on the device, tabs and settings included. **Always generate a signed release APK via
`scripts/release-apk.sh` (on Windows via Git Bash: `& "C:\Program Files\Git\bin\bash.exe" scripts/release-apk.sh`),
never `assembleDebug` or bare `:app:assembleRelease` alone, whenever building an APK:**
`assembleDebug` runs `debuggable` and keeps Compose's debug checks, so on an inked page it is slower;
and an unsigned release APK cannot be installed on Android.
`scripts/release-apk.sh` builds `:app:assembleRelease` and signs it with the debug keystore (so it
upgrades in place, and back), writing `app/build/outputs/apk/release/MobiXournal-<version>.apk` — the
release asset's name too, with the version read out of `app/build.gradle.kts` rather than copied into
the script, so a bump moves both.

For published Android tags, CI sets `MOBIX_REQUIRE_RELEASE_SIGNING=1`. Configure repository secrets
`MOBIX_ANDROID_KEYSTORE_BASE64` (base64 of the existing signing keystore), `MOBIX_ANDROID_KEY_ALIAS`,
`MOBIX_ANDROID_STORE_PASSWORD`, `MOBIX_ANDROID_KEY_PASSWORD`, and repository variable
`MOBIX_ANDROID_CERT_SHA256` (the signing certificate's 64 hexadecimal SHA-256 digits).
Missing material fails before building; a certificate mismatch removes the output APK and fails the job.
Use the certificate of the installed APKs: generating a new key cannot preserve their upgrade path.
Local builds retain debug signing by default. Android permits an in-place update only when package and
signing identity agree; a shared application id alone does not make debug and published APKs compatible.
`scripts/test-release-signing.py` tests preflight and fingerprint enforcement with fake SDK tools:
run it with Python, without a device, keystore or Android SDK. CI runs it before the APK build.

`scripts/make-mobix-fixtures.py` regenerates the golden `.mobix` v1 corpus under
`mobix-core/src/test/resources/fixtures/mobix-v1/`: dependency-free and deterministic, so a re-run
reproduces the same bytes and a fixture change is a diff a reviewer can read. [testing.md](testing.md)
owns which test drives the corpus and what each file asserts.

### Host fallback — no `/data/android`, no Docker

`/data/android` is a **machine-local path**. On a host that doesn't have it, `scripts/build.sh`
stops immediately ("Shared Android toolchain not found at /data/android") and the build runs on the
host's own toolchain — the wrapper loop selected by [host-profiles.md](host-profiles.md). Two things
the container would otherwise supply have to be there, and they are where a host build usually fails:

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
scripts/host-build.sh                       # the loop's default: both suites, the instrumented-test
                                            # compile, then the debug APK
scripts/host-build.sh --list                # which JDK and SDK it picked, then stop
scripts/host-build.sh :app:testDebugUnitTest   # any Gradle task passes through
```

It fails loudly with the JDK window and where it looked when it finds nothing, so the failure names
the cause instead of surfacing as Gradle's "Unsupported class file major version".

The emulator, the install/launch/screenshot loop and `androidTest` are [tools-emulator.md](tools-emulator.md)'s.

---

## Desktop shell

The `desktop/` module (`docs/desktop-workstation.md`) is a plain JVM Compose
Multiplatform application: it needs **no Android SDK, no emulator and no `/data/android`**, only the
JDK selected through [host-profiles.md](host-profiles.md). Run the wrapper from the repository root:

```sh
.\gradlew.bat :desktop:run --args="udiff.xopp"
```

The `run` task's working directory is the repository root (set in `desktop/build.gradle.kts`), so a
relative document path means what it says. No argument starts an empty window with an **Open** button.

The verification that does not need a window or a display:

```sh
gradle :desktop:test    # loads udiff.xopp, renders page 1 headlessly, checks pixels
```

It writes its PNG to `desktop/build/desktop-render/page-1.png`. The module consumes `mobix-core`, the
platform-free module (document model, codecs, paper and ink geometry) — see the status section of
`docs/desktop-workstation.md`; do not add Android-dependent code to it, or the desktop build stops
compiling.

### The packaged build — the `.exe` and the installer

`.\gradlew.bat :desktop:createDistributable` (with `JAVA_HOME` as in [host-profiles.md](host-profiles.md)) writes a self-contained
folder, runtime included, to `desktop/build/compose/binaries/main/app/MobiXournal/`; its `MobiXournal.exe`
opens a document passed as an argument and an empty window without one. **Regenerate it at the end of every
completed task** (`AGENTS.md`, build flags — a stale `.exe` is a feature delivered only in source).

`scripts/desktop-package.ps1` builds the app image (`:desktop:createDistributable -PdesktopOnly`), harvests it
with `heat` and links `desktop/build/installer/MobiXournal-<version>.msi` from `desktop/installer/main.wxs` with
`candle`/`light` — not jpackage's MSI, whose one feature offers no choice, while this feature tree does:
`.mobix`/`.pmobix`/`.xopp` associations and a desktop shortcut, on by default and switchable off, per user, under
`%LOCALAPPDATA%`. It is signed whenever `MOBIX_SIGN_PFX_BASE64`+`MOBIX_SIGN_PFX_PASSWORD`, `MOBIX_SIGN_PFX` or
`MOBIX_SIGN_THUMBPRINT` is set, unsigned otherwise; CI builds it and a `v*` tag releases it.

### Measuring ink latency on screen (this window against Xournal++)

`scripts/desktop-latency.ps1 -App mobixournal|xournalpp [-Trials 20] [-Out file] [-Shot png]` drives
pointer strokes into the running app and polls the screen for the ink, reporting median / p95 / min / max
of pointer-move → visible ink. It launches the program itself, picks the pen slot (Xournal++ ignores
canvas presses until its toolbar has been used once), and measures both programs the same way — which is
what makes the two numbers comparable. The measurements and their caveats are owned by
`docs/desktop-tablet.md`; the script is a diagnostic, not part of a build.

---

## Cutting a release

Releases are produced by CI, never by hand. [releasing.md](releasing.md) owns the order: freezing the
pending set with `scripts/todo.sh release`, curating the note, `scripts/todo.sh tag` creating the tag only
for a tree `verify-release` accepts, the job that publishes both installers, and the title that page
carries. The two version lines and the rehearsal of that job are there too.

## Tab-switch probe (the running window)

`scripts/desktop-tab-switch.ps1` measures what a reader waits for when they click a tab, on the **packaged**
window rather than headless: it opens a document, makes a second tab through the strip's `+`, sends
`Ctrl+Tab` and polls the screen. It reports two numbers, because they are two different waits — the **first
pixel** (the sheet appears) and the **settled** page (the ink has stopped arriving). On this host, against
`udiff.xopp`: 59 ms and 325 ms. Run it with `--diagnose` output beside it (`mobix-diagnose.log`, one line a
second: `paints/s`, the worst gap between paints, and the paint's own cost) — a large `worst` with a
one-millisecond `paint` is a blocked UI thread, not a slow painter, which is the distinction a headless
render cannot make.

`mobix-ui-hang-<timestamp>.txt` beside it is the window's own answer to a stalled UI thread — every stack,
and the monitor a blocked one waits for with its owner. Written in every run, not just under `--diagnose`.

## Mobix corpora

Two generators and two readers, all dependency-free and deterministic (fixed ZIP timestamps, no clock
or locale), so a re-run reproduces the same bytes and a fixture can be reviewed as a diff:

- `scripts/make-mobix-fixtures.py` → the golden v1 corpus under
  `mobix-core/src/test/resources/fixtures/mobix-v1/`, driven by `MobixV1FixturesTest`.
- `scripts/make-mobix-hostile-corpus.py` → the hostile corpus under `…/fixtures/mobix-hostile/`: one file
  per threat in the limits table of [mobix-format.md](mobix-format.md), plus the smallest legal document
  (`MobixHostileCorpusTest`); re-run it after changing a cap, or it stops testing that policy.
- `scripts/verify-mobix.py` → the independent reader: the conformance rows a script can decide (`--rows`
  lists them) and the envelope against `spec/schema/`, with no `mobix-core` dependency; the save-and-reopen
  rows stay manual, so run it over the golden corpus, never the hostile one.
- `scripts/check-mobix-cli.py` → what the `cli` CI job runs: the CLI's suite, `validate` over both corpora
  (each hostile file refused for its row), the writer round-trip, and one verdict per file compared with
  `spec/corpus-verdicts.csv` (`CorpusVerdictsTest` keeps it honest) — a rule added on one side alone fails.

---

## Task verification wrapper

`scripts/verify.cmd` (Windows) and `scripts/verify.sh` (Linux/macOS) delegate to `scripts/verify.py`
to run the full closing check sequence without spawning concurrent Gradle daemons:

- Always runs `todo validate`, then the guard suites (`DocsGuardTest`, `DocClaimsTest`,
  `SourceStructureTest`, `AgentInstructionsTest`, `CiCoverageTest`, `ColorContractGuardTest`,
  `ChromeGeometryGuardTest`, `TypeContractGuardTest`, `SystemBarsGuardTest`,
  `SettingsCopyParityTest`, `ToolbarMenuGuardTest`), and refreshes `:desktop:createDistributable`
  unless `--skip-distributable` is passed.
- Pass `--ui-guards` on UI changes to also run `ShapeFeedbackTest`, `ToolGlyphCoverageTest`, and
  `SettingsGlyphParityTest` plus the 13 desktop UI contract and pixel guard suites (`:desktop:test`),
  `-- <gradle-args>` for targeted task tests, `--build-apk` when `rebuild = true`, `--todo-tests`
  for `scripts/todo/test_*.py`, and `--full` to run `:mobix-core:test`, `:app:testDebugUnitTest`,
  and `:desktop:test` sequentially. On Windows, JDK 17–24 discovery automatically sets `JAVA_HOME`
  (preferring JDK 21).

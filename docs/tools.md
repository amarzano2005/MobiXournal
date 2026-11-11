# Tools & build pipelines

> Read when you build, test, package or release the project, or reach for one of the tools it lists.
> For the task: [Installing on Android](guide-install-android.md), [Installing on Windows 11](guide-install-windows.md), [What works](guide-limitations.md).

Authoritative home for **external tooling** this project depends on: build pipelines, deploy scripts,
device/emulator harnesses, code generators, and anything else outside the repo's own in-tree build.
`AGENTS.md` only *points* here; a detail lives here, or in the `docs/<tool>.md` that this file links.

Add one entry per tool, kept to what an agent needs to *use* it: where it lives, how to call it, and its gotchas.

The vendored design skill and the design declaration that closes a UI change are [design-workflow.md](design-workflow.md)'s.

## Index

| Tool / pipeline | What it's for | Detail |
|-----------------|---------------|--------|
| Android build | Compile & package the app (APK/AAB) | [below](#android-build) |
| Android emulator | Run & test the app on a virtual device | [tools-emulator.md](tools-emulator.md) |
| Desktop shell | Run, test and package the Compose Multiplatform window (no emulator, no container) | [below](#desktop-shell) |
| Release publishing | Freeze a version, draft its changelog, tag + publish | [below](#cutting-a-release) |
| UI/UX Pro Max | Design data, its search script, and the declaration that closes a UI change | [below](design-workflow.md) |
| Mobix corpora | Regenerate the golden and hostile `.mobix` fixture corpora | [below](#mobix-corpora) |

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
the app on the device, tabs and settings included. **Judge speed on the release build:**
`assembleDebug` runs `debuggable` and keeps Compose's debug checks, so on an inked page it is slower;
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
host's own toolchain — the `./gradlew` loop `AGENTS.md` already lists for host systems. Two things
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
JDK 21 the Gradle build already uses (`JAVA_HOME` — on the Windows host,
`C:\Users\amarz\.jdks\jbr-21.0.11`; JDK 27 breaks Gradle configuration). Run it through the wrapper
from the repository root:

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

`.\gradlew.bat :desktop:createDistributable` (with `JAVA_HOME` as in `AGENTS.md`) writes a self-contained
folder, runtime included, to `desktop/build/compose/binaries/main/app/MobiXournal/`; its `MobiXournal.exe`
opens a document passed as an argument and an empty window without one. **Regenerate it at the end of every
completed task** (`AGENTS.md`, build flags — a stale `.exe` is a feature delivered only in source).

`scripts/desktop-package.ps1` builds the app image (`:desktop:createDistributable -PdesktopOnly`), harvests it
with `heat` and links `desktop/build/installer/MobiXournal-<version>.msi` from `desktop/installer/main.wxs` with
`candle`/`light` — not jpackage's MSI, whose one feature offers no choice, while this feature tree does:
`.mobix`/`.xopp` associations and a desktop shortcut, on by default and switchable off, per user, under
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

Releases are produced by CI, never by hand. Pushing an annotated tag `vX.Y.Z` runs
`.github/workflows/build.yml`, which builds the signed release APK (`scripts/release-apk.sh`) and creates the GitHub Release with
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

4. **Revisit the two pages that carry the version.** The status matrix in
   [`guide-limitations.md`](guide-limitations.md) opens by naming the builds it describes, so that line
   moves with the release, and a row whose evidence changed during it is re-checked rather than left
   standing on an older test. [`releases/index.md`](releases/index.md) then takes the new version into its
   two current-version lines and its table, so the newest note is reachable without reading the folder.
   Neither is generated: the command knows which tasks shipped, not which capability changed, and the
   matrix is a judgement about a build rather than a list of commits.

5. **Watch the workflow.** The release appears on GitHub with `MobiXournal-<version>.apk` (the Android version) and `MobiXournal-<version>.msi` (the desktop one) attached.

The workflow enforces the freeze rather than trusting it: every run lints both task files and runs
the task CLI's own regression checks — `test_store.py` for the order the file promises, `test_done.py`
for an archived entry recording what shipped rather than the plan it came from, `test_release.py` for
the pending set and the changelog — and a `v*` tag additionally runs
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

### Two versions, one product

The tablet app and the Windows window are **two artifacts with two version lines**. The app restarted
its numbering at **2.0.0**; the window starts at **1.0.0**.
Neither number is derived from the other, and neither is copied into a doc: each lives in the Gradle
file of the artifact that carries it (`versionName`/`versionCode` in `app/build.gradle.kts`,
`packageVersion` in `desktop/build.gradle.kts`). Read and check both with:

```sh
scripts/todo.sh versions
```

It prints the two values and fails when either is not a shape a release can use. The window's number
is stricter on purpose: Compose writes `packageVersion` into the MSI's version resource, which Windows
reads as a *number*, so a suffix such as `1.0.0-rc1` fails at packaging — the check says so in a second
instead.

`release --bump-gradle` owns the **app** line, because its `versionCode` has to move with it and only
the archive knows what is pending. The window's number is edited in its Gradle file, and its MSI carries
it into the same release under the app's tag; `verify-release` compares a tag with the app's
`versionName`, so a window that needs notes of its own gets a tag of its own — `desktop-vX.Y.Z`, outside
the `v*` filter and with `docs/releases/desktop-vX.Y.Z.md` — and `todo.sh versions` checks both numbers.

## Tab-switch probe (the running window)

`scripts/desktop-tab-switch.ps1` measures what a reader waits for when they click a tab, on the **packaged**
window rather than headless: it opens a document, makes a second tab through the strip's `+`, sends
`Ctrl+Tab` and polls the screen. It reports two numbers, because they are two different waits — the **first
pixel** (the sheet appears) and the **settled** page (the ink has stopped arriving). On this host, against
`udiff.xopp`: 59 ms and 325 ms. Run it with `--diagnose` output beside it (`mobix-diagnose.log`, one line a
second: `paints/s`, the worst gap between paints, and the paint's own cost) — a large `worst` with a
one-millisecond `paint` is a blocked UI thread, not a slow painter, which is the distinction a headless
render cannot make.

## Mobix corpora

Two generators and a validator, all dependency-free and deterministic (fixed ZIP timestamps, no clock or
locale), so a re-run reproduces the same bytes and a fixture can be reviewed as a diff:

- `scripts/make-mobix-fixtures.py` → the golden v1 corpus under
  `mobix-core/src/test/resources/fixtures/mobix-v1/`, driven by `MobixV1FixturesTest`.
- `scripts/make-mobix-hostile-corpus.py` → the hostile corpus under
  `mobix-core/src/test/resources/fixtures/mobix-hostile/`: one file per threat in the limits table of
  [mobix-format.md](mobix-format.md), plus the smallest legal document (`MobixHostileCorpusTest`).
  Re-run it after changing a cap — the many-entry file is sized to the entry cap and the bomb's ratio
  follows the ratio cap — or the corpus stops testing the policy it is meant to.
- `scripts/verify-mobix.py` → the independent reader: decides the conformance rows a script can decide
  (`--rows` lists them) and validates the envelope against `spec/schema/` with no dependency on `mobix-core`.
  It cannot see a rewrite, so the save-and-reopen rows stay manual; run it over the golden corpus, never
  over the hostile one, which is meant to fail.

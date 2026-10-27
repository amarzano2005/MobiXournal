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
| Android emulator | Run & test the app on a virtual device | [tools-emulator.md](tools-emulator.md) |
| Desktop shell | Run/test the Compose Multiplatform desktop preview (no emulator, no container) | [below](#desktop-shell) |
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
  - `scripts/build.sh` — the default check loop: `:app:testDebugUnitTest :mobix-core:test
    :app:assembleDebug` — **both** suites, because `testDebugUnitTest` exists only in the Android
    module and would leave the core (codecs, model, `FormatDriftTest`) unrun (`docs/testing.md`).
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

### The debug build is its own app — a test build never replaces the daily app

The released `com.mobixournal` stays the daily app, so the debug build is its **own app**:
`app/build.gradle.kts` gives the `debug` build type `applicationIdSuffix = ".v2"`, so
`scripts/build.sh`, the emulator install and the APK the release workflow attaches all land on
**`com.mobixournal.v2`**, and the `debug` source set gives that build its own launcher label
(`app/src/debug/res/values/strings.xml` → "MobiXournal v2 (dev)") so the two icons are
indistinguishable only if you want them to be.

The Android package name *is* the app's identity: a build of the same package signed with a different
key replaces the installed app — with its session cache and settings — rather than living beside it.
The suffix is what makes "install the test build" a non-destructive step.

- **Installing it:** nothing changes in the loop. `emulator.sh install
  app/build/outputs/apk/debug/app-debug.apk`, `emulator.sh launch com.mobixournal.v2` on the emulator;
  `adb install -r app/build/outputs/apk/debug/app-debug.apk` on a device. The package to launch and to
  `logcat`-filter by is `com.mobixournal.v2`.
- **Its storage is its own too** (`filesDir`/`cacheDir` are per-package), so a v2 build cannot disturb
the daily app's tabs, settings or saved documents.
- **Dropping the suffix** is deleting the `debug` block and the `debug` source set, after which the
  build is `com.mobixournal` again and upgrades the existing install in place. That is the change to make
  if the debug build should ever *become* the daily app.

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
scripts/host-build.sh                       # both unit-test suites + debug APK (the loop's default)
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

### The packaged build — a `.exe` to try the desktop app

`.\gradlew.bat :desktop:createDistributable` (with `JAVA_HOME` set as in `AGENTS.md`) writes a
self-contained folder, runtime included, to
`desktop/build/compose/binaries/main/app/MobiXournal/` (about 140 MB). Run `MobiXournal.exe` from
it; a document path after it opens that document, and no argument opens an empty window. It is **not
an installer and not signed**: Windows SmartScreen may warn on first run, and the folder is only as
current as the last build. **Regenerate it at the end of every completed task** — the rule is in
`AGENTS.md` (build flags) and it applies to every task, not only the ones that touch `desktop/`, because
the packaged window carries `mobix-core` too. A stale `.exe` is a feature delivered only in source: the
person double-clicking it cannot tell that apart from a feature nobody built. The MSI installer and a CI
artifact are the open task `desktop-packaging-installers-and-ci-artifact`.

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
its numbering at **2.0.0** when the v2 channel became the product; the window starts at **1.0.0**.
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
the archive knows what is pending. The window's number is edited in its Gradle file: its packaging, its
installer and its own release notes are `desktop-packaging-installers-and-ci-artifact`'s work, which is
why there is no second changelog to keep in step yet. Tags stay `vX.Y.Z` for an app release, and
`verify-release` compares them against the app's `versionName` — a desktop tag needs that job extended,
and that belongs to the same task.

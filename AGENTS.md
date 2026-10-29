# AGENTS.md

Guidance for AI coding agents working in this repository.

## Start here: the documentation map (hub-and-spoke)

This repo keeps documentation **de-duplicated** — every fact has exactly **one**
authoritative home. When you need to know or change something, go to its owner below;
don't restate a fact in a second file (link to the owner instead). This table is the
**hub**: read it first, then follow the spoke you need. The point of hub-and-spoke is that
the detail lives out in the spoke docs, so this file (and the context window) stays small —
you pull in a spoke only when the task touches it, instead of reading everything every turn.

| You want to know / change…                       | Authoritative home        |
|--------------------------------------------------|---------------------------|
| **What to do next** (active tasks only)          | `TODO.toml` (via the `todo` skill) |
| **What's already done** (completed-work archive) | `FINISHED.toml` (via the `todo` skill) |
| **How to work here** (conventions, decisions, host profile) | `AGENTS.md` (this file) |
| **How the system works internally** (data flow, layout, all spokes) | `docs/architecture.md` — the **index** of the architecture spokes |
| **What every new UI element must satisfy** (material, colour, states, glyphs, tests) | `docs/interface-contract.md` |
| **Native `.mobix` format & `.xopp` bridge policy** | `docs/mobix-format.md`, `docs/mobix-schema-spec.md`, `docs/mobix-elements.md` |
| **How a user runs/uses it** (setup, build & run) | `README.md`               |
| **External tools & build pipelines** (Android, deploy, device harnesses) | `docs/tools.md` |
| **What a release shipped** (per-version changelogs) | `docs/releases/[version].md` (procedure in `docs/tools.md`) |
| **[Any other spoke — protocol, commands, API…]** | `docs/[name].md` (via `docs/architecture.md`) |

**Two classes of fact:**

1. **Code-derived facts** (schemas, element mappings, wire formats, structural budgets) are
   owned by the code. Where practical, make the docs a *mirror* and add a **drift/guard test that
   fails the build** when they fall out of sync (`DocsGuardTest`, `SourceStructureTest`,
   `ManifestIntentFilterTest`), so a stale doc or structural regression is caught mechanically
   rather than by discipline.
2. **Narrative facts** (status, "verified live" notes, change history) can't be tested, so they
   live in **one** place only and the update rules below keep that single copy current.

---

## What this project is

**MobiXournal** is an **independent, unofficial note-taking and scientific drafting app** for **Android
and Windows** — the tablet editor and the `desktop/` window, over one shared `mobix-core`. It began as a
companion for [Xournal++](https://github.com/xournalpp/xournalpp) (`.xopp`) — continuing Brian Monroe's
**NeXopp** ([github.com/bamonroe/NeXopp](https://github.com/bamonroe/NeXopp)) under the label
**MobiXournal** (not affiliated with or endorsed by the Xournal++ authors; see _Branding and identity_ in
`docs/codebase.md`). Its documents live in its own native format, `.mobix`, which carries what a `.xopp`
cannot: living STEM elements, full stylus telemetry and paged streaming. Reading and writing `.xopp`
without loss is a **permanent contract** rather than a migration step — `docs/xopp-bridge.md` states it,
with the tests that enforce it.

### Two formats, one document

- **`.mobix` (Native format — `docs/mobix-format.md`, `docs/mobix-schema-spec.md`, `docs/mobix-elements.md`)**:
  A paged PKWARE ZIP container (`application/vnd.mobixournal+zip`) that stores:
  - **Living domain entities**: re-editable function plots (`PlotElement`), data tables (`TableElement`),
    parametric shapes (`ParametricShapeElement`) and circuit components (`CircuitElement`); LaTeX is
    carried as a `.xopp` `teximage` with its source kept (`docs/mobix-elements.md`).
  - **Rich stylus telemetry**: $(x, y, \text{pressure}, \text{tilt}, \text{azimuth}, \Delta t_{\text{ms}})$ for replay and diagnostics.
  - **Scalable container engines**: part-per-page LRU streaming (`PageSource` / `MobixPagedReader`),
    incremental delta-saves (`MobixIncrementalSaver` / `MobixZipRewrite`), content-hash deduplicated
    binary assets (`assets/`), and spatial chunking (`chunks/`) for chunked documents.
- **`.xopp` (Interchange format — `docs/xopp-format.md`)**:
  Gzip-compressed XML (or self-contained ZIP `.xopp`) for lossless round-trip interoperability with
  desktop Xournal++ on Linux.
- **Sticky per-document format policy**:
  A document remembers its own format. An opened `.xopp` saves back as `.xopp`; an opened `.mobix`
  saves back as `.mobix`; new documents use the user's chosen default; **"Save As"** explicitly switches
  formats in either direction. Conversion is **never** silent or automatic on open.
- **Lossless bidirectional bridge (`MobixDocument` ↔ `XoppSynthesis`)**:
  - **Export (`.mobix` → `.xopp`)**: `render/XoppSynthesis.kt` (shared, in `mobix-core`) compiles living entities into standard
    `.xopp` strokes and text boxes in place through `render/LivingInk.kt` — the **single rendering
    dispatch** shared by the canvas (`PageRenderer`), PDF export (`PdfVectorPainter`), and SVG export
    (`PageSvgWriter`) — and injects a verified `<!-- mobix:meta ... -->` XML comment block (`MobixMeta`).
  - **Import (`.xopp` → `.mobix`)**: `MobixMetaRevival` restores living entities only where the ink's
    SHA-256 digest still matches the metadata claim; if edited in desktop Xournal++, the claim is safely
    dropped and the edited strokes import as ordinary ink.
- **Scope rule — the two formats are the boundary**:
  We only build features that round-trip cleanly through `.mobix` (with deterministic synthesis/fallback
  to `.xopp` where applicable) or `.xopp`. Capabilities that cannot survive a save→reopen in either
  format are out of scope. Furthermore, drawn stroke width continues to follow desktop Xournal++'s
  pressure filter (`docs/stylus.md`) unless a written architectural decision changes it.

### The `desktop/` window

`desktop/` is a Compose Multiplatform window (Skiko) that opens the same containers, paints their pages
and **edits, saves and exports** them: tabs, a page sidebar with layers, zoom, the shared floating tool
strips and the shared design tokens — ink, figures, living elements, text and the selection. It consumes
`mobix-core`, so a codec, a ruling or an ink rule is not forked for the PC. `docs/desktop-workstation.md`
describes the window as it is, `docs/desktop-editing.md` what a drag on its page does.

### Architectural honesty in docs and code

- **Docs describe what the code does.** In spoke docs, keep the wording literal: **live** for behaviour
  wired end-to-end in `app/src/main` or `desktop/`, **library only** for tested code with no call site,
  and no entry at all for a capability that does not exist yet.
- **A new topic is a new spoke**, and the procedure is five steps in `docs/architecture.md` (*Adding a
  spoke*). A fact has one home: when you notice the same rule stated in a second doc, move it to its
  owner and leave a link — and if the two can drift apart unnoticed, add a line to `DocClaimsTest`.
- **A UI change is checked against `docs/interface-contract.md`** before it lands: one of the three
  material classes, tokens only, a glyph from the catalogue, all five states, English copy, both shells
  in agreement.
- **v2 development package (`com.mobixournal.v2`)**: Debug builds set `applicationIdSuffix = ".v2"`
  ("MobiXournal v2 (dev)") so installing and testing v2 never overwrites the daily `com.mobixournal`
  installation or its storage (`docs/tools.md`).

---

## Architecture & mechanical guards — see `docs/architecture.md`

`docs/architecture.md` is the **index**, not the content: it lists one spoke per topic, and each
spoke opens with the question it answers, so a change loads one topic instead of the whole
architecture. Start there, open the one spoke your task touches, and when you make a design
decision **record it in the owning spoke** so it isn't re-litigated later.

The JVM guard suites in `app/src/test/java/com/mobixournal/guards/` enforce structural discipline —
`docs/architecture.md` (*How this stays honest*) owns what each doc rule is for, and
`docs/testing.md` the full inventory:
- **`DocsGuardTest`**: fails the build if any `.md` file in `docs/` (outside `docs/releases/`) is
  missing from `docs/architecture.md`, if any relative markdown link or `#anchor` slug is broken, if any
  doc exceeds the **300-line budget**, if a spoke does not open with `# Title` + a `> Read when …` line,
  if two spokes claim one title or section heading, if a sentence is pasted into two docs, if a doc
  names a code file, another doc or a task id that does not exist. The two front doors it never used to
  read are held to their own rules, because they live outside `docs/`: a recorded **line budget**, no
  paragraph of running prose past 1 200 characters, their links resolving like a spoke's, and every file
  at the repository root named by a doc, a test or a script instead of sitting there as an orphan.
- **`DocClaimsTest`**: the statements no file can witness — sentences that were false once
  (`read-only` about the window), the window's Settings list derived from `DesktopSettingSection`, and
  the test classes a doc names. A new lie gets a line here.
- **`SourceStructureTest`**: enforces the code-splitting ratchet across `app/src/main` — no Kotlin
  file over **700 lines** and no brace-bodied function over **100 lines** (except recorded legacy
  ceilings, which may only shrink).
- **`XoppExportCoverageTest`**: the `.mobix` → `.xopp` export as a coverage ratchet — an exhaustive
  `when` over the sealed `Element` interface stops the build compiling when a new element family lands
  without a branch, a recorded family count makes that branch come with a sample, and the samples prove
  each family compiles to ink, reaches a `.xopp` a reader reopens, and is **reported** rather than
  dropped when it cannot be drawn. It is the enforcement behind the contract in `docs/xopp-bridge.md`.
- **`CiCoverageTest`**: every module with a test source set is named by a Gradle line in
  `.github/workflows/build.yml`. The modules do not share a task name — the app has
  `testDebugUnitTest`, the JVM ones `test` — so naming one runs one module and reports success for the
  rest; `mobix-core` (codecs, `FormatDriftTest`) had no CI run at all this way.

---

## Multi-machine workflow: detect the active host profile

The owner works across **multiple machines** (a Linux container host equipped with `/data/android`,
a Windows 11 laptop and a Windows 11 desktop PC). **Never assume which machine a session is running
on** — check the OS/shell, the host name (`$env:COMPUTERNAME`) and whether `/data/android` exists at
the start of a task, then follow the matching profile below:

| Profile | How to recognise it | Build & test front door | Emulator & `androidTest` |
|---|---|---|---|
| **A. Linux `/data/android` container host** (Primary standard when present) | Linux shell (`bash`/`sh`) and `/data/android/build.sh` exists | `scripts/build.sh` (runs inside `android-builder:local` via Docker, Podman fallback) | `/data/android` headless Android 14 container via `emulator.sh`; run `androidTest` via `scripts/connected-test.sh` |
| **B. Windows 11 laptop (`i7-12650H`)** | Windows (`pwsh`), user `amarz`, no `/data/android` | `.\gradlew.bat` directly in `pwsh`, or `& "C:\Program Files\Git\bin\bash.exe" scripts/host-build.sh` | Local SDK `Pixel_Tablet` AVD (WHPX); run `androidTest` via `.\gradlew.bat connectedDebugAndroidTest` |
| **D. Windows 11 desktop PC (`ANTONIOFISSO-PC`, `i9-12900KF`)** | Windows (`pwsh`), host name `ANTONIOFISSO-PC`, 3440×1440 monitor, no `/data/android` | same as B | same as B (SDK and `Pixel_Tablet` AVD present) |
| **C. Other Linux / macOS / Windows hosts** (without `/data/android`) | `/data/android` absent | `scripts/host-build.sh` (auto-discovers JDK 17–24 preferring 21 + Android SDK) or `./gradlew` | Host SDK `emulator` + `adb`; run `androidTest` via `scripts/host-build.sh connectedDebugAndroidTest` |

### Profile A — Linux `/data/android` container host
- **Containerized build & test**: Always use `scripts/build.sh` (which defaults to both unit-test
  suites plus `assembleDebug`) and `scripts/todo.sh <command>`. Do not rely on host-installed JDK/SDK
  when `/data/android` is available.
- **Containerized emulator**: Drive the emulator via `emulator.sh` (`up`, `install`, `launch
  com.mobixournal.v2`, `screenshot`, `ui`, `logcat`) and run instrumented tests via
  `scripts/connected-test.sh` (never plain `connectedDebugAndroidTest` inside the throwaway builder
  container, which lives in a separate `adb` world).
- Full contract and gotchas live in `docs/tools.md`.

### Profile B and D — the Windows 11 machines
Both run Windows 11 `10.0.26300` in `pwsh` with the same toolchain paths (below); they differ in
hardware. **Profile D is the machine to verify desktop-app work on**: it has the 21:9 monitor, which
Profile B lacks. Both Windows machines carry the same **Gaomon WH851** tablet — the pen they use on
the Android tablet as well.

#### Profile D — Windows 11 desktop PC (`ANTONIOFISSO-PC`)
- **Hardware**: 12th Gen Intel Core **i9-12900KF** (16 cores, 24 threads), **32 GB RAM**, NVIDIA
  **GeForce RTX 3080** (a discrete GPU, so a running AVD does not share system RAM with the display).
- **Display**: one **3440×1440 (21:9)** monitor; Windows reports **120 Hz** for it, so the 144 Hz
  target of `docs/desktop-workstation.md` needs the refresh rate raised in Windows display settings
  (or a check that the monitor supports it) before it can be measured here.
- **Tablet**: **Gaomon WH851** — reaches Compose as a mouse (pressure `1.0`, no tilt); its pen's own
  stream is **Wintab** (`docs/desktop-tablet.md` owns the readers). Express keys are the vendor's.
- **Python trap**: `python` on `PATH` resolves to the Microsoft Store stub in `WindowsApps` and prints
  a Store prompt instead of running. The real interpreter is
  `C:\Users\amarz\AppData\Local\Python\bin\python.exe` (3.14), e.g.
  `& 'C:\Users\amarz\AppData\Local\Python\bin\python.exe' scripts/todo/todo.py stats`.
- **JDKs**: `~\.jdks` holds `jbr-21.0.11` (use this) and `temurin-25.0.3`; the default `java` on `PATH`
  is **JDK 27**, which breaks Gradle — set `JAVA_HOME` as under Profile B.
- Everything else (SDK, `Pixel_Tablet` AVD, `.\gradlew.bat`, Git Bash for `.sh`) is as under Profile B.
  With 32 GB the RAM pressure is far lower, but still run **one Gradle command at a time**.

#### Profile B — Windows 11 laptop (`i7-12650H` / 16 GB RAM)
When the session is running on this Windows 11 workstation (`10.0.26300`, `pwsh`):
- **Hardware & resource budget**:
  - **CPU**: 12th Gen Intel Core i7-12650H (10 cores: 6P + 4E, **16 logical processors**).
  - **RAM**: **16 GB physical RAM** (~15.7 GB usable; ~8–9 GB typically free alongside OS/IDE).
  - **GPU & Virtualization**: Intel UHD Graphics (integrated, shared system RAM); **WHPX (`10.0.26300`)**
    active for x86_64 Android emulation.
  - **RAM protection rule**: `gradle.properties` pins `-Xmx2048m` and `org.gradle.caching=true`.
    **Never launch concurrent Gradle commands in parallel tool calls** — a second Gradle + Kotlin daemon
    alongside a running `Pixel_Tablet` AVD (which shares system RAM via Intel UHD Graphics) will cause
    swap thrashing. Run targeted JVM tests (`--tests <Class>`) during iteration before the full suite.
- **Toolchain paths & Windows shell gotchas**:
  - **No `/data/android`, Docker, Podman, or local `xournalpp` binary** on this machine (verify `.xopp`
    desktop compatibility via JVM round-trip, `XoppSynthesis`, and `MobixMetaRevival` tests).
  - **Bash stub trap (`WindowsApps\bash.exe`)**: Plain `bash` in `PATH` resolves to the non-functional
    WSL stub. To run `.sh` scripts (`scripts/host-build.sh`, `scripts/todo.sh`), explicitly call
    **Git Bash**: `& "C:\Program Files\Git\bin\bash.exe" scripts/host-build.sh ...` — or bypass bash
    overhead entirely in `pwsh` by calling `.\gradlew.bat` and `python scripts/todo/todo.py <cmd>` directly.
  - **JDK**: JetBrains Runtime **JDK 21** at `C:\Users\amarz\.jdks\jbr-21.0.11` (picked automatically by
    `scripts/host-build.sh`), plus Eclipse Temurin OpenJDK 17 (`17.0.20.1`) on `PATH`. **A JDK 27 does
    not work for Gradle here:** the machine's default JVM (e.g. `C:\Program Files\Java\jdk-27`) makes the
    Kotlin DSL fail at configuration with a bare `IllegalArgumentException: 27` from
    `JavaVersion.parse`, so export `JAVA_HOME='C:\Users\amarz\.jdks\jbr-21.0.11'` before any
    `./gradlew.bat` or `scripts/host-build.sh` run.
  - **Android SDK & AVD**: SDK at `C:\Users\amarz\AppData\Local\Android\Sdk` (`local.properties`),
    `adb.exe` in `platform-tools\`, `emulator.exe` in `emulator\` with AVD **`Pixel_Tablet`**.
  - **Emulator & `androidTest` on this host**: Use `.\gradlew.bat connectedDebugAndroidTest` (with
    `-Pandroid.testInstrumentationRunnerArguments.class=...` for single classes) — do **not** use
    `scripts/connected-test.sh` here. See `docs/tools.md` (*Host fallback — Android Studio's own
    emulator*) for `-no-snapshot-load`, `KEYCODE_WAKEUP`, and `run-as com.mobixournal.v2`.

---

## Build, run & task verification flags — see `docs/tools.md` and `README.md`

The **repository layout** (every package/module and what it does) lives in
`docs/codebase.md`; **how to build and run** it lives in `README.md`; external pipelines live in
`docs/tools.md`.

```sh
# 1. On Linux container hosts with /data/android (Profile A):
scripts/build.sh                           # both unit-test suites + debug APK inside container
scripts/build.sh :app:testDebugUnitTest :mobix-core:test   # both unit-test suites only

# 2. On hosts without /data/android via Git Bash / POSIX shell (Profiles B, C & D):
scripts/host-build.sh                      # auto-discovers JDK 21 + SDK, runs unit tests + debug APK

# 3. Direct PowerShell fast path on a Windows host (Profiles B & D):
.\gradlew.bat :app:testDebugUnitTest :mobix-core:test   # both suites (JVM; no device needed)
.\gradlew.bat assembleDebug                # build debug APK (com.mobixournal.v2)
```

**The task's `rebuild` and `emulator_debug` flags decide how far to go — they are load-bearing,
not advisory.** Every `TODO.toml` task carries these two flags (owned by the `todo` skill; see
`skills/todo/SKILL.md`), and they are the authoritative answer to two questions:
- **`rebuild`** — whether to build the APK **at all** for this task. `false` means the change
  doesn't warrant a fresh APK build (e.g. docs, guards, or `scope`/`epic` breakdowns); `true` (the
  default) means build it.
- **`emulator_debug`** — whether to **install and exercise on the emulator** (`/data/android`
  `emulator.sh` on Profile A, or the host SDK `Pixel_Tablet` AVD on Profiles B/C). `false` (the
  default) means **skip the emulator pass entirely** for this task; a clean build + unit tests is
  sufficient. `true` is the full pass: install the APK (`com.mobixournal.v2`) and actually drive
  it — screenshots to *see* the UI, `adb logcat` for error logs, and simulated finger/stylus presses.

**Honor these per-task flags over any general "always test the running app" instinct** — when a
task says `emulator_debug = false`, do **not** boot or install on the emulator, even for a
change with a runtime surface. When flags are off for a run of quick tweaks, you still build and
(if `emulator_debug` is on for any of them) verify once at the end. If a task has no flags
(e.g. ad-hoc work outside `TODO.toml`), fall back to the defaults: build and run the unit tests,
but no emulator pass unless asked.

**Every completed task finishes with a current desktop executable.** The packaged desktop build is the
artefact the owner actually opens, so closing a task means regenerating it in the same task:

```powershell
.\gradlew.bat :desktop:createDistributable   # JAVA_HOME = JDK 21 (Profile B/D)
```

`desktop/build/compose/binaries/main/app/MobiXournal/MobiXournal.exe` must contain what the task just
implemented. This holds for **all** tasks, not only ones that touch `desktop/`: the packaged window
carries `mobix-core`, so shared code lands in it too, and a stale `.exe` is a feature delivered only in
source — indistinguishable, to the person double-clicking it, from a feature that was never built. The
folder is build output (not versioned, not committed); only the rule to refresh it lives here. Owning
spoke for the command: `docs/tools.md`.

---

## Development style guide — technology defaults (standing preferences)

These are the owner's standing defaults for *how* things get built. They apply to every
project regardless of domain; deviate only when a project has a concrete, written reason to,
and record that reason in this file.

### Develop inside containers — Docker first, Podman fallback (with `host-build.sh` fallback when no container toolchain is present)

**Do development, builds, and runs inside containers whenever the container toolchain is available,
so the environment is reproducible and the host stays clean.**

- **Prefer Docker** (`scripts/build.sh` → `/data/android/build.sh` on Profile A), and **fall back to
  Podman** where Docker isn't available or appropriate (keep scripts and Compose files rootless- and
  Podman-compatible).
- **Recorded multi-machine exception**: When working on a secondary workstation that does not carry
  `/data/android` or a container runtime (such as the Profile B Windows 11 laptop), use
  `scripts/host-build.sh` or `.\gradlew.bat` against JDK 21 and the local Android SDK as documented
  in `docs/tools.md` (*Host fallback*).

### Web servers are written in Rust

**If this project needs a web server, write it in Rust.** Rust is the default for any HTTP /
web-server component — pick a mature async stack (e.g. `axum`/`tokio` or `actix-web`) and keep
the server code idiomatic. Use another language for the server only with a written reason
recorded here.

### User-facing text is English — on the tablet and on the desktop

**Every string the app shows is English**, in both shells: the Android editor and the `desktop/`
preview. Write new copy in English in the same commit as the feature — a tooltip, a status-bar word, a
dialog title, an error notice — and keep comments, commits and docs out of the rule (they may be in any
language). The reasoning and the error-copy rule (say what happened and what to do; never show an
internal class name where a person is looking) live in `docs/ui-modernization.md`.

### Keep the code split up — no mono-files, no giant functions

**Break the project into many small pieces.** This is a hard preference, enforced mechanically by
`SourceStructureTest`: it exists to keep the code navigable, documentable, and unit-testable.

- **No single mono-file ($\le 700$ lines).** Don't pour a subsystem into one giant file. Split it
  along real boundaries into separate modules/files, each with a clear, single responsibility.
- **No mono-module.** Within a file, don't stack everything into one blob — break each module
  into distinct types/classes and functions with focused jobs.
- **Keep functions small — 100 lines max.** If a function grows past ~100 lines,
  extract helpers. Smaller units are easier to name, document, and cover with unit tests.
- **Pure logic stays out of Views/Composables.** Keep geometry, codecs, synthesis, hit-testing, and
  state reducers in pure, dependency-free Kotlin functions testable on the JVM in milliseconds.

---

## Working practices (standing preferences — keep these in every project)

### Git: commit atomically, at will and frequently — and push freely

This repo is the safety net. **Commit atomically, at will, and frequently.** You have
standing authorization to commit your own work **without asking first** — don't wait to be
told, and never let work pile up uncommitted.

- **Atomic commits**: one logical change per commit. A bug fix, a feature, a doc update,
  and a refactor are separate commits — don't bundle unrelated changes. Commit the smallest
  coherent unit that builds/tests clean.
- Make the change → build/vet/test it (`DocsGuardTest` + `SourceStructureTest` + relevant unit
  tests) → **commit**. Prefer many small commits over one large one; it keeps history bisectable
  and easy to revert (`git revert <sha>`).
- Write a concise **imperative** subject that says *why*, not just *what*
  (`fix: input bar behind nav bar`, `feat: read-last command`).
- **Commit before risky or large changes**, so there's always a clean point to return to.
  It's fine to commit work-in-progress on a branch; prefer a branch for anything speculative
  so the main branch stays runnable.
- **Push freely and liberally.** You have standing authorization to `git push` without
  asking first — don't let local commits sit unpushed. Push after committing (or after a
  short run of related commits); keeping the remote current is part of "done."
- Never commit secrets, databases, or build artifacts — `.gitignore` covers the routine ones.

### A feature isn't done until it's documented — and `README.md` must stay readable for a human

**Documentation is part of the feature, not a follow-up.** Write it *during* the feature
work, or immediately after — never defer it to "later," and never ship code without it.

- **Keep `README.md` readable for a human.** `README.md` is the front door for a human user or
  operator, **not** an architectural spoke or a commit log:
  - Write from the **user's perspective** (what a feature does and how to use it in the UI), in
    clear, concise prose.
  - **Never dump implementation internals** into `README.md` — no Kotlin class/function names,
    file paths (`render/...`, `format/...`), wire-format rules, or commit-by-commit rationale.
    Those belong in the owning `docs/` spoke.
  - **Integrate, don't just append.** When updating `README.md` for a change, fold the new
    behaviour naturally into the existing bullet or section (and trim superseded phrasing) so the
    document stays scannable and well-proportioned instead of turning into a wall of micro-notes.
- Every new user-facing feature gets documented in `README.md` as part of the same work.
- Keep the single-source-of-truth spoke docs in sync in the same pass (per `docs/architecture.md`),
  respecting the **300-line spoke budget** verified by `DocsGuardTest` — a topic that does not fit is
  a second spoke, not a longer one.
- Update the wording when a **library only** capability gains its call site: the doc says what is
  wired, not what is possible (`docs/mobix-format.md`, `docs/architecture.md`).
- A new UI element must satisfy `docs/interface-contract.md`; the owning spoke is updated in the same
  commit.
- Docs land in the **same commit** as the feature (or an immediately-following commit) — a
  feature commit with no accompanying documentation is incomplete.
- **Documenting is part of the coding task, never a task of its own.** Every time you write
  code, you update its documentation *within that same task*. Don't split the docs out into a
  separate `TODO.toml` item to be picked up later, and don't mark a coding task `done` while
  its docs are stale — the task isn't finished until they're written.
- Any script or command needed to build, deploy, run, back up, or operate this project
  **must be written down** — put reusable steps in a checked-in script, and reference every
  such script from both `README.md` (how an operator runs it) and `docs/tools.md`.

### `TODO.toml` (active) + `FINISHED.toml` (archive) — keep them current

`TODO.toml` (repo root) is the single source of truth for **active** work and the **journal
for what to build next**; `FINISHED.toml` is the archive of **completed** work. Status lives in
these two files only — not here and not in `README.md` (all link to them).

Both are **TOML**, not Markdown, and each task carries structured metadata (`id`, `status`,
`level`, `category`, `urgency`, `order`, `created`/`completed`, `tags`, `rebuild`,
`emulator_debug`). **Drive them through the `todo` skill** (`python scripts/todo/todo.py <command>`
on Windows PowerShell, or `scripts/todo.sh <command>` in Git Bash; documented in
`skills/todo/SKILL.md`) rather than hand-editing, so ids, ordering, and metadata stay consistent.

- **Update both in the same commit that changes the work they describe:** add proposed
  features/tests with `add …`; drop dropped items with `remove <id> --reason "…"`.
- **When a task is fully finished** (built, tested, documented), run `done <id> --shipped "…"` to
  move it out of `TODO.toml` and into `FINISHED.toml`, newest-first and dated, then run `stats` and
  report how many open items remain. The `--shipped` text **replaces the plan** with what the task
  delivered, so the archive never reads as pending work; `done` refuses to run without it. Don't leave
  completed items sitting in `TODO.toml`.
- **A release freezes the pending set — never hand-write a changelog.** Every finished
  task carries a `release` stamp once it has shipped; the tasks without one are exactly
  what the next version ships, so `release --version X.Y.Z` turns that set into the version
  (stamping the tasks and drafting `docs/releases/vX.Y.Z.md`, procedure in `docs/tools.md`).
- **Respect a task's `level`** (`task` · `scope` · `epic`, owned by the `todo` skill):
  - **`task`**: atomic and directly implementable. Write descriptions detailed enough (naming exact
    files, functions, behaviour, and verification steps) that a small model can execute them without
    guesswork.
  - **`scope`**: investigate the code and `add` the atomic `task`s it breaks into, then mark the
    `scope` `done`. A `scope` produces **tasks, not code**.
  - **`epic`**: a milestone too large for one pass (its id may read like `step-1-mobix-format-engines`,
    a historical label rather than a plan). Never implement an epic in one pass; break out the next
    `scope`/`task` children as work progresses.
- Run `python scripts/todo/todo.py validate` if you ever hand-edit `TODO.toml` or `FINISHED.toml`.

### Token discipline — keep the context small

- **Read in slices, not whole files.** Use search/grep tools to find the target, then view files
  with line ranges around it. Only read a whole file when you genuinely need all of it.
  Never re-read a file you just edited — the edit already confirmed the new state.
- **Delegate broad searches to `research` subagents.** Any "where/how is X done" sweep across
  many files goes to a subagent that reads in its own context and returns just the
  conclusion, so the file dumps never land in this conversation.
- **Don't restate; link.** Point at the owning doc (per the map above) instead of pasting its
  content into a reply or a new file. This is the whole reason for hub-and-spoke.
- **Prefer targeted output.** Filter command output and run targeted test classes rather than
  dumping entire logs or full directory trees.

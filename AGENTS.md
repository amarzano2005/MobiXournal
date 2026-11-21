# AGENTS.md

Guidance for coding agents working in MobiXournal.

## Start with the task and its owner

Follow the user's current request and explicit corrections. Read the relevant owner below before
changing its subject; open the architecture index only to find the spoke you need. A fact has one
home: link to its owner instead of copying it into another instruction or document.

| You need to know or change | Authoritative home |
|---|---|
| Active work and completed deliveries | `TODO.toml` and `FINISHED.toml`, through [.agents/skills/todo/SKILL.md](.agents/skills/todo/SKILL.md) |
| Standing working preferences | this file |
| Machine detection, hardware and toolchain paths | [docs/host-profiles.md](docs/host-profiles.md) |
| Builds, packaging, releases and external tools | [docs/tools.md](docs/tools.md) |
| Architecture and where each topic lives | [docs/architecture.md](docs/architecture.md) |
| Module and package layout | [docs/repo-layout.md](docs/repo-layout.md) |
| Writing workflow and documentation Definition of Done | [docs/writing-docs.md](docs/writing-docs.md) |
| Documentation impact, README decisions and checks | [docs/doc-governance.md](docs/doc-governance.md) |
| Capability support by platform | [docs/guide-limitations.md](docs/guide-limitations.md) |
| Every UI change, including desktop text feedback | [docs/interface-contract.md](docs/interface-contract.md) |
| Design workflow and the closing declaration | [docs/design-workflow.md](docs/design-workflow.md) |
| Native format and interchange contract | [docs/mobix-format.md](docs/mobix-format.md), [docs/xopp-bridge.md](docs/xopp-bridge.md) |
| How a person installs and uses the app | [README.md](README.md) |
| Release history | [docs/releases/index.md](docs/releases/index.md) |
| Agent discovery, consistency checks and review scenarios | [docs/agent-workflow.md](docs/agent-workflow.md) |

`DOCUMENTATION_STRATEGY.md` is an internal historical record, not a source for user-facing claims.

## Product boundaries that guide every change

MobiXournal is an independent, unofficial note-taking and scientific drafting app for Android and
Windows. Both shells consume `mobix-core`; shared codecs, geometry, rendering rules and state logic
belong there, not in separate implementations for each shell. Identity and lineage are owned by
[docs/codebase.md](docs/codebase.md).

Only build capabilities that survive save and reopen in `.mobix` or `.xopp`, with deterministic
interchange synthesis or fallback where applicable. Lossless bidirectional `.xopp` compatibility is
permanent. Never silently convert on open: preserve the document's format until an explicit Save As.
The owning format and bridge pages in the map carry the detailed contracts.

Stroke width follows desktop Xournal++'s pressure filter unless a written architectural decision
changes it; consult [docs/stylus.md](docs/stylus.md). The install identity and signing rules belong to
[docs/tools.md](docs/tools.md), not to a package name remembered from an older task.

Every string shown in either app shell is English, including tooltips, status text and errors.
Describe what happened and what to do; keep internal class names out of error copy. Comments,
commits and documentation may use another language. See [docs/ui-modernization.md](docs/ui-modernization.md).

## Start and verify on the actual host

At task start, check OS/shell, host name and whether `/data/android/build.sh` exists, then use
[docs/host-profiles.md](docs/host-profiles.md). Prefer Docker, with Podman fallback, when the container
toolchain is available. The documented Windows/other-host exception uses the host toolchain.
Run one Gradle command at a time. On Windows set the supported JDK before invoking the wrapper;
read the host profile for the Python and Bash stub traps rather than assuming PATH is usable.

The active task's `rebuild` and `emulator_debug` are binding, not hints:

- `rebuild = false` skips a fresh Android APK; `true` builds the **release** APK (`:app:assembleRelease` / `scripts/release-apk.sh`, never debug).
- `emulator_debug = false` skips booting, installing and driving the emulator entirely.
- `emulator_debug = true` requires installing and exercising the app, screenshots, error logs and
  simulated input; a successful compile alone does not meet the requirement.
- Ad-hoc implementation defaults to a **release** APK build (`:app:assembleRelease` / `scripts/release-apk.sh`, never `assembleDebug`) and unit tests, without an emulator pass. Read-only questions and audits do not require application builds.
- Whenever generating an Android APK, always build a **signed release** APK (`:app:assembleRelease` / `scripts/release-apk.sh`), not `assembleDebug` (see [docs/tools.md](docs/tools.md)).
- For a series of quick tweaks, build once at the end if required, and exercise the emulator once
  if any task requires it. A task with no rebuild requirement does not acquire one from this rule.

If a task description contradicts its flags, resolve that contradiction before executing dependent
steps. An explicit current user instruction can change the task's flags; record that change through
the task CLI. Do not silently override a flag to make old prose executable.

Run relevant tests plus `DocsGuardTest` and `SourceStructureTest` before committing a change.
Use targeted suites during iteration and the required full checks before closing the task; commands
are in [docs/tools.md](docs/tools.md) and the inventory is [docs/testing.md](docs/testing.md).
**Every completed implementation or documentation task refreshes the desktop executable** with
`:desktop:createDistributable`, even when the Android rebuild flag is false. The packaged app is
build output and must include the delivered source; its location is owned by the tooling page.

## Keep implementation and documentation coherent

Keep source files at most 700 lines and brace-bodied functions at most 100 lines, except recorded
legacy ceilings that may only shrink. Split along real responsibilities; avoid mono-modules and
catch-all files. Pure geometry, codecs, synthesis, hit-testing and reducers stay out of Views and
Composables and remain JVM-testable. [docs/code-structure.md](docs/code-structure.md) owns placement.

Rust is the default for any new web server, using a mature async stack. A different server language
requires a concrete written reason recorded here. This project currently has no such exception.

Documentation is part of the task and lands in the same commit or an immediately following commit.
Follow [docs/writing-docs.md](docs/writing-docs.md), including its seven phases and Definition of Done.
Use [docs/doc-governance.md](docs/doc-governance.md) to decide which pages and whether the README need
changes; do not create a separate documentation task for code just implemented. Keep the README
readable for a human, with user actions and benefits instead of implementation internals.

Describe wired behaviour as live and tested code without a call site as library only. Grade support
only in the capability owner using the writing standard's status vocabulary. New topics follow the
architecture index's Adding a spoke procedure and its 300-line doc budget. Put architectural decisions
in the owning spoke. Prefer code-derived mirrors and drift tests over manually duplicated facts.

Every UI change follows the interface contract before it lands: material class, design tokens,
catalogue glyph, five states, English copy and agreement between the shells. Use the local design
workflow when applicable; the task's closing design declaration records what was verified.
Any reusable operational command belongs in a checked-in script referenced by README and tools docs.

## Task bookkeeping and delivery

Read [.agents/skills/todo/SKILL.md](.agents/skills/todo/SKILL.md) before querying or changing task files.
Use its CLI, never ad-hoc TOML edits. It owns schema, levels, flags, ordering, closing and release
commands. A `task` is implemented; a `scope` investigates and creates atomic tasks; an `epic` advances
through children rather than being implemented in one pass. Specify atomic tasks well enough for a
small model: target files/functions, desired behaviour and verification without missing decisions.

Record proposed work with `add` and dropped work with `remove --reason`; keep bookkeeping in the
commit that changes the work. After build, verification and documentation, close with
`done --shipped` describing the delivery, supply the required design declaration, then run `stats`
and report the remaining open count. Never leave completed work active. Freeze a release through
`release --version`, which stamps the pending archive set and drafts its changelog; do not hand-write
or hand-pick release notes. If task files were hand-edited, validate them before proceeding.

Commit your own work atomically, at will and frequently, with concise imperative subjects that say
why. Commit before risky changes; use a branch for speculative work so main stays runnable.
Push after a coherent commit or short related series: standing authorization covers both commit
and push. Preserve unrelated work and never commit secrets, databases or build artifacts.

## Spend context on the current decision

Read slices around search matches instead of whole files. Re-open edited material only when a check
or a real uncertainty calls for it. Delegate broad where/how searches to a `research` subagent when
available; return conclusions and relevant locations instead of dumping files into the main chat.
Filter command output, keep test output targeted, and link to authoritative detail instead of
restating it. Report what changed, what was verified and any remaining limitation.

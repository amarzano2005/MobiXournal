# Tests: what is covered, and what is enforced mechanically

> Read before you change anything you want a test to protect, and before adding a rule you want the build to enforce.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What the unit tests cover

This file is the authoritative inventory; `README.md` and [`tools.md`](tools.md) link here rather
than restating it. The `format/` tests exercise the `.xopp` round-trip: the colour codec, every
element type, XML escaping, model reserialization, a gzip round-trip, the PDF-background on-disk
shape, the fixture-driven `FormatDriftTest` asserting schema coverage, and
`XmlEqualityRoundTripTest`, which checks that the XML we emit still matches the desktop-written
source byte-for-byte once normalized. `format/mobix/` covers the native container
([mobix-format.md](mobix-format.md)): `MobixContainerTest` pins the stored typed mimetype,
byte-exact round trips, the survival of an entry this build does not model across an open-and-save
rewrite, a real ZIP-package `.xopp` being rejected as the wrong type, and the writer's refusals —
the container rules of [mobix-schema-spec.md](mobix-schema-spec.md), asserted before there is a
schema layer on top of them. `MobixManifestTest` pins the envelope codecs (the page order coming from
the manifest, unknown fields surviving, the three required fields, the `minAppVersion` floor),
`MobixElementCodecTest` pins the element and page codecs (a pressure stroke's per-point widths and
telemetry, an element type with no model surviving verbatim, an image stored once and addressed by
content, `bounds` written but never trusted), and `format/json/JsonWriterTest` pins the shared JSON
writer (escaping, integer spelling, parse round trips). `MobixLivingCodecTest` pins the four living
entities field for field (a plot keeps every expression, range and parameter the math engine needs to
redraw itself; a table's grid and not its cached box is the authority; a circuit's pins stay relative
to the component and before its rotation) and, hardest of all, the rule that a *known* family this
build cannot read whole comes back verbatim rather than half-modelled — an unreadable cell alignment
or a curve kind from a newer build must never be written back as a different document. It also holds
the `.xopp` writer's side of that boundary: a living entity, and the JSON body of any element
preserved from a `.mobix`, are never written into `.xopp` markup. `MobixDocumentTest` is the bridge's
own end-to-end claim, over the same desktop-validated fixtures: **the `.xopp` written back out of the
converted document is the same XML that went in** — creator, title, preview, pages, layers, elements,
audio links, backgrounds and rule parameters — so a conversion cannot quietly rewrite a file. It also
pins the document layer's bookkeeping (a page keeps the identity it was given across saves, a page
whose bytes did not change keeps its `modified` stamp, the same picture on two pages is one asset) and
its refusals (a page the manifest names but the archive lacks, a newer `formatVersion`, a
`canvas.json` disagreeing with the manifest, a chunked document). The `render/` tests cover the pure geometry — page layout,
gridlines, page ops, eraser hit-testing, text layout, LaTeX geometry and undo/redo history — and
the `audio/` tests cover fn/ts mapping and WAV framing. All of it runs on the JVM with no device
attached.

**The two root fixtures.** `RealFileRoundTripTest` round-trips a real desktop-generated `udiff.xopp`
end to end, read from the **repo root** (the builder mounts the project's parent dir, so the file
resolves inside the container); `bhm_prior.pdf`, beside it, is what the PDF-background and desktop-save
suites open. Both are committed at the root — the README hands `udiff.xopp` to a new user as a first
document — and the round-trip test still **skips itself when the sample is absent**, so a checkout
that drops it stays green and putting a fresh `udiff.xopp` back is all it takes to turn the extra
coverage on.

What the device-only paths (stylus events, hover, the emulator harness) are covered by lives in
[`stylus.md`](stylus.md) and [`tools.md`](tools.md) — this file owns the JVM suite.

## Rules the build enforces

Some rules cannot be asserted against a running value; they are properties of the tree. Those are
checked by tests that read the repo, in `app/src/test/java/com/mobixournal/guards/` and next to the
code they protect. Each test carries the reasoning for its rule; this is the index of them, so a
change can see what will fail it:

The desktop window has suites of its own — headless Compose renders, a real document loaded through the
codec — and [desktop-testing.md](desktop-testing.md) is their index.

- **`DocsGuardTest`** (`guards/`) — the shape of the doc set: every spoke doc is reachable from the
  index, every relative link and `#anchor` resolves, every doc fits its **300-line** budget, every
  spoke opens with `# Title` + a `> Read when …` line, no two spokes claim one title or section
  heading, no sentence is pasted into two docs, and every code file, doc and task id a doc names
  exists. So the architecture docs cannot decay back into one unaddressable document, or into prose that
  describes a file that has since been renamed. The two front doors are held to their own rules, since
  they sit outside `docs/`: a recorded line budget, no paragraph of running prose past 1 200 characters,
  their links resolving like a spoke's, and every file at the repository root named by something (a doc,
  a test or a script) rather than left there as an orphan.
- **`DocClaimsTest`** (`guards/`) — the drift no file can witness: sentences that were false once
  (`read-only` about the desktop window) are forbidden, the window's Settings list is derived from
  `DesktopSettingSection` so a new section fails until the doc names it, and every test class a doc
  names exists. A new lie gets a line here — that is the rule, not a rewrite of the guard.
- **`SourceStructureTest`** (`guards/`) — a ratchet on file and function size, measured over every
  main source set rather than only the app: `app/src/main`, `mobix-core/src/main` and
  `desktop/src/main` (keys are relative to `com/mobixournal/`, so the desktop shell's files read
  `desktop/DesktopShell.kt`). The entries listed in it may not grow, and no new file may appear past
  the budget. Splitting a listed file is what the test rewards; the remedy for a failure is a split,
  or a deliberate bump of a recorded number. The app test task declares `docs/`,
  `desktop/src/main/java`, `AGENTS.md` and `README.md` as inputs (the desktop tree is not a compile
  dependency of the app, so without the entry a desktop-only edit would leave the guard's task
  UP-TO-DATE and unrun; the two front-door files are read by `DocClaimsTest` the same way).
- **`XoppExportCoverageTest`** (`guards/`) — the `.mobix` → `.xopp` export as a coverage ratchet. An
  exhaustive `when` over the sealed `Element` interface stops the build compiling when a new element
  family lands without a branch; a recorded family count makes that branch come with a sample; and the
  walk over those samples asserts each family compiles to ink, reaches a `.xopp` a reader reopens, keeps
  its point geometry where the XML has no telemetry to keep, and is **reported** rather than dropped
  when it cannot be drawn. It is the mechanism behind the compatibility contract of
  [xopp-bridge.md](xopp-bridge.md).
- **`CiCoverageTest`** (`guards/`) — the suites CI actually runs. It reads `settings.gradle.kts` and the
  workflow's Gradle lines and fails when a module that carries a test source set is not named there,
  and it fails in the other direction too (no Gradle line found, or `:desktop:test` gone) so a rewritten
  workflow cannot blind it. The rule exists because the modules do not share a task name — the Android
  app has `testDebugUnitTest`, the plain JVM modules have `test` — so a workflow naming one of them runs
  one module and reports success for the rest.
- **`ToolbarMenuGuardTest`** (`ui/`) — only the file that defines `ToolbarMenu` may call Material3's
  `DropdownMenu`, so a pop-up cannot quietly fall back to the paler default panel.
- **`ToolGlyphWeightTest`** (`ui/`) — both toolbars draw the same `Icons.Outlined` tool set, so both
  must draw it at the one glyph size (`ToolIconSize`, half a rail slot): these vectors scale their
  stroke with the icon, and a smaller glyph in the Secondary Toolbar read as *thinner ink* than the rail
  beside it. The compact top-bar buttons, the guides button and the dock's indicator face are read out
  of the source tree, since a `Modifier.size(...)` argument is invisible to any behavioural assertion.
- **`ColorContractGuardTest`** (`guards/`) — the palette's own contract, asserted by **sweep** rather
  than by spot-check: every container and outline role of both schemes is measured for a violet wash
  ("green deficit"), the light ramp is pinned role by role, the floating material must read
  `rememberFloatingSurfaceColor` rather than a container role of its own, its ink must clear 4.5:1 over
  both extremes of backdrop, the metric itself is pinned against Material 3's baseline neutrals so it
  cannot be loosened into passing on lavender, and two further checks keep the family whole: every file
  that carries a floating surface must read that one material — directly, or through the one shared
  wrapper for its kind (`FloatingSurface` for a bar, `FloatingDialog` for a dialog), whose own fill and
  edge the guard reads out of the tree — with the container role it used to paint banned by name in the
  same file, so a second surface cannot slip in with an ad-hoc fill; and `AppShapes.extraLarge` — the
  role Material's `AlertDialog` reads — must be the menus' own corner.
- **`SystemBarsGuardTest`** (`guards/`) — mechanical guard asserting that all Kotlin assignments and
  XML theme declarations for `statusBarColor` and `navigationBarColor` use transparent values, ensuring
  the full-bleed canvas reaches the device edges without opaque system bar bands.
- **`ChromeGeometryGuardTest`** (`guards/`) — validates that the chrome bands and canvas partition the window
  across all layout combinations (rail docked Left and Right, and Full-page) without overlapping, that
  the header is a single continuous full-width Surface whose column contains both the tools and tabs, and
  that a tablet band shades its own closing edge inside itself with no elevation, so no band's shadow can
  land on the band beside it — the header's edge inset where the rail's lane begins and the lane shading
  the edge facing the canvas, so the frame's two arms close on one shadow.
- **`FormatDriftTest`** (`format/`) — the `.xopp` mapping the docs describe against what the writers
  emit, so the format documentation cannot drift from the code.
- **`DesktopColorParityTest`**, **`RealFileRoundTripTest`**, **`SelectionHandleRadiusTest`** and
  friends — the pinned desktop-parity values; see the spoke that owns the behaviour.

## The desktop suite (`:desktop:test`)

The desktop shell ([desktop-workstation.md](desktop-workstation.md)) is a Windows 11-only Compose
Multiplatform app, and its suite runs on the JVM with no emulator and no document to hand: it drives
`ImageComposeScene` to render pages and chrome headlessly and writes the PNGs to
`desktop/build/desktop-render/`, so a visual claim can be looked at rather than trusted. The module
itself depends only on `mobix-core` and Compose Desktop; the Android SDK is still what the
multi-module build needs in order to configure the app module, so the CI runner carries both.

That suite is part of CI, in its own job: `.github/workflows/build.yml` runs `:desktop:test` on
`windows-latest` with JDK 21 (Temurin) because the shell is Windows-only, and uploads
`desktop/build/desktop-render/` as the `desktop-render` artifact on every run — including a failed
one, where the render is often what explains the failure. It is a separate job rather than a step in
the Android job so Skiko's rasterising never gates or slows the APK build.

## What CI runs

`.github/workflows/build.yml` is the only place a suite is guaranteed to run, and it names its tasks
project by project: the Android job runs `:app:testDebugUnitTest :mobix-core:test` and then builds the
APK, the Windows job runs `:desktop:test` (above). That explicitness is load-bearing rather than
verbose — `testDebugUnitTest` exists only in the Android module, so a workflow naming it alone ran that
suite and left `mobix-core` unexecuted, which is exactly where the `.xopp`/`.mobix` codecs, the document
model and `FormatDriftTest` live: every step passed, and a whole module's tests were never called.
`CiCoverageTest` closes that from both sides, failing the build when a module with tests is missing from
the workflow or when the workflow stops being readable as Gradle invocations. The local front doors
(`scripts/build.sh`, `scripts/host-build.sh`) default to the same two suites, so the missing run shows up
before the push instead of after it.

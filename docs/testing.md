# Tests: what is covered, and what is enforced mechanically

> Read before you change anything you want a test to protect, and before adding a rule you want the build to enforce.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What the unit tests cover

This file is the authoritative inventory; `README.md` and [`tools.md`](tools.md) link here rather
than restating it. The `format/` tests exercise the `.xopp` round-trip: the colour codec, every
element type, XML escaping, model reserialization, a gzip round-trip, the PDF-background on-disk
shape, the fixture-driven `FormatDriftTest` asserting schema coverage, and
`XmlEqualityRoundTripTest`, which checks that the XML we emit still matches the desktop-written
source byte-for-byte once normalized. `format/mobix/` covers the native container the format roadmap
is built on ([mobix-format.md](mobix-format.md)): `MobixContainerTest` pins the stored typed mimetype,
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
`canvas.json` disagreeing with the manifest, an infinite-canvas document). The `render/` tests cover the pure geometry — page layout,
gridlines, page ops, eraser hit-testing, text layout, LaTeX geometry and undo/redo history — and
the `audio/` tests cover fn/ts mapping and WAV framing. All of it runs on the JVM with no device
attached.

**The `udiff.xopp` self-skip rule.** `RealFileRoundTripTest` round-trips a real
desktop-generated `udiff.xopp` end to end, read from the **repo root** (the builder mounts the
project's parent dir, so the file resolves inside the container). The sample is not checked in,
so the test **self-skips when it is absent** — the suite is green with or without it, and
dropping a fresh `udiff.xopp` at the repo root is all it takes to turn the extra coverage on.

What the device-only paths (stylus events, hover, the emulator harness) are covered by lives in
[`stylus.md`](stylus.md) and [`tools.md`](tools.md) — this file owns the JVM suite.

## Rules the build enforces

Some rules cannot be asserted against a running value; they are properties of the tree. Those are
checked by tests that read the repo, in `app/src/test/java/com/mobixournal/guards/` and next to the
code they protect. Each test carries the reasoning for its rule; this is the index of them, so a
change can see what will fail it:

- **`DocsGuardTest`** (`guards/`) — every spoke doc is reachable from the index, every relative link
  and `#anchor` between docs resolves, and every doc fits its line budget, so the architecture docs
  cannot decay back into one unaddressable document.
- **`SourceStructureTest`** (`guards/`) — a ratchet on file and function size: the entries listed in
  it may not grow, and no new file may appear past the budget. Splitting a listed file is what the
  test rewards; the remedy for a failure is a split, or a deliberate bump of a recorded number.
- **`ToolbarMenuGuardTest`** (`ui/`) — only the file that defines `ToolbarMenu` may call Material3's
  `DropdownMenu`, so a pop-up cannot quietly fall back to the paler default panel.
- **`ToolGlyphWeightTest`** (`ui/`) — both toolbars draw the same `Icons.Outlined` tool set, so both
  must draw it at the one glyph size (`ToolIconSize`, half a rail slot): these vectors scale their
  stroke with the icon, and a smaller glyph in the Secondary Toolbar read as *thinner ink* than the rail
  beside it. The compact top-bar buttons, the guides button and the dock's indicator face are read out
  of the source tree, since a `Modifier.size(...)` argument is invisible to any behavioural assertion.
- **`ColorContractGuardTest`** (`guards/`) — the palette's own contract, asserted by **sweep** rather
  than by spot-check: every container and outline role of both schemes is measured for a violet wash
  ("green deficit"), the light ramp is pinned role by role, the one menu material must read
  `rememberMenuSurfaceColor` rather than a container role of its own, its ink must clear 4.5:1 over
  both extremes of backdrop, and the metric itself is pinned against Material 3's baseline neutrals so
  it cannot be loosened into passing on lavender.
- **`FormatDriftTest`** (`format/`) — the `.xopp` mapping the docs describe against what the writers
  emit, so the format documentation cannot drift from the code.
- **`DesktopColorParityTest`**, **`RealFileRoundTripTest`**, **`SelectionHandleRadiusTest`** and
  friends — the pinned desktop-parity values; see the spoke that owns the behaviour.

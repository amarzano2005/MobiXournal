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
schema layer on top of them. The `render/` tests cover the pure geometry — page layout,
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
- **`FormatDriftTest`** (`format/`) — the `.xopp` mapping the docs describe against what the writers
  emit, so the format documentation cannot drift from the code.
- **`DesktopColorParityTest`**, **`RealFileRoundTripTest`**, **`SelectionHandleRadiusTest`** and
  friends — the pinned desktop-parity values; see the spoke that owns the behaviour.

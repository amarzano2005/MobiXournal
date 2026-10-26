# Testing the desktop window

> Read when you add or change a test for the `desktop/` module, or want to know what its headless renders
> actually prove.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

Every test here runs on a plain JVM host with no emulator and no device, because the window's decisions are
the core's: the geometry, the codecs and the codecs' arithmetic are `mobix-core`'s and are tested there.

## How it is run

`./gradlew :desktop:test` — the whole suite, a few minutes. Renders land under
`desktop/build/desktop-render/` and are the artefact a pixel assertion reads; they are build output, not
committed.

## What it covers

The suite loads `udiff.xopp` (6 pages, ~4 000 strokes) through `DocumentLoader`, opens a ZIP
`.xopp` and a 40-page `.mobix` (including both **misnamed**, in either direction), refuses a text file and a
ZIP with no document inside by name, renders page 1 of each headlessly via `ImageComposeScene`, and renders a
table, a parametric shape, a bundled pixmap and a PDF background — asserting the pixels, not that the call
returned (`LivingAndBackgroundTest`). `DesktopShellTest` renders the tab strip **before and after** a document
is opened and asserts the frames differ, renders the sidebar, and checks the keyboard's zoom arithmetic. `ToolStripsTest` renders the rail and the
ribbon on their own (every slot draws its glyph, the picked one wearing the active slot), then the whole window
with the strip at the left, the right and the top, reading the pixels: the sheet keeps its place and reaches
under the capsules, and a tap and two drags at the glass leave the page's elements untouched. `OpenFailureTest` pins what a failed open says
(`DesktopLaunchPathTest`: a command line put back together). `DesktopSaveTest` is the write's
round trip: an edit saved over a gzip `.xopp` and over a 40-page `.mobix`, each re-opened through
`DocumentLoader` with the container and the untouched pages asserted, a Save As that turns a `.mobix` into a
`.xopp` and re-homes the tab, a packaged `.xopp` refused with its file byte-identical, and the version the
window stamps into a `.mobix` checked against `app/build.gradle.kts`'s `versionName`. `DesktopEditingTest` is
the editing path headless: a 40-page `.mobix` opens through a `CountingEntrySource` with **zero** page
entries read, an edit on page 3 opens that page alone, the model the save writes carries it and a 40-page
repack round-trips it while the untouched pages keep their own ink; the eraser splits a bar where it crosses
it; a pressure sample becomes its filtered width, a highlighter stays one width, and the page-point mapping
undoes density and zoom. Its undo half drives the **shell's own commands**: a stroke, `Ctrl+Z` (model and
painted ink both return), a second undo that reports nothing left, a redo that repaints it, and a save that
starts a new history. `DesktopLifecycleTest` is the lifecycle headless: an edit makes a tab dirty, a save and
an undo back to the save point make it clean, closing a dirty tab asks first, a snapshot written by one shell
is adopted by the next and deleted once saved, and the strip's dot is drawn only while there are unsaved
edits. Every render lands under `desktop/build/desktop-render/`: `glass-parity.png` (the same panel in the
tablet's material and in this window's, by `GlassParityRenderTest`), and
`glass-edge.png` / `glass-over-page.png` / `glass-show-through.png`, which `GlassEdgeTest` samples — the rim
brighter at the top than at the bottom, or it is a border and not the family's edge, and a band of ink under
the panel still reading through it, or the fill is a slab. `GlassMotionTest` drives the arrival through a
scene's frame clock (`render(nanoTime)`): nothing at t=0, faint at 40 ms inside its 82 % box, the glass at
full size by 700 ms, with `GlassPresence` holding it composed through the 230 ms exit.
`PageLayersAndPaperTest` reads pixels too: `isometric-paper.png`'s line midpoints must fall on
`BackgroundGrid.isometric`'s segments, and `hidden-layer.png` is a two-layer page with one off — the band
that is gone is the check.


## Where the rest lives

The window itself is [desktop-workstation.md](desktop-workstation.md), editing is
[desktop-editing.md](desktop-editing.md), and the pen is [desktop-tablet.md](desktop-tablet.md).

# Pages, paper and backgrounds

> Read when you change a page's paper (ruling, templates, background chooser), bookmarks, or the page list.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Page bookmarks (`ui/PageBookmarks.kt`)

**Page bookmarks (`ui/PageBookmarks.kt`).** A labelled, colour-coded flag on a page, plus the list of
them that makes a long document navigable. The `.xopp` format has **no page label, name or bookmark**
— and neither has desktop Xournal++ — so per the project's scope rule the bookmarks are **app-side
navigation state**, not document content: they live in `BookmarkStore` (SharedPreferences, one entry
per document key, the tab's own document identity) and a bookmarked file stays byte-identical to an
unbookmarked one. `PageBookmarks` is the model's pure half — label/colour rules, the escaped
`page⇥colour⇥label` codec, and the page shifts that keep a flag on its page when one is inserted or
deleted — all unit-tested; `BookmarkStore` is the thin Android-backed persistence. The chrome reaches
it through `PaneState` (the pane *drives* the list, `EditorScreen` owns the store and the document
key, so nothing else has to know where bookmarks are kept), the Pages menu edits and jumps, and the
canvas paints each flag on the sheet's top-right corner from `DrawingSurfaceView.bookmarkedPages` —
view-only chrome, so it is not an undo step and never reaches the file.

## Ruling parameters and stationery (`BackgroundRuling`, `PageTemplates`)

**Ruling parameters and stationery (`BackgroundRuling`, `PageTemplates`).** A page's paper is not
just a style: desktop writes the ruling's parameters — spacing, margin, line width, bold lines — into
`<background config=…>`, and reading them is what lets a 7 mm ruled sheet or a millimetre grid look
right in both apps. `BackgroundRuling` is that attribute as an **ordered key/value map** (desktop's
own `BackgroundConfig`, split on `,` and each entry at its last `=`), so unknown parameters — the
colour keys, anything a future desktop adds — are preserved in place exactly like an element's
`extraAttrs`, while the keys we do understand are edited by key and written back with three decimals.
`BackgroundRulings` resolves the numbers a renderer needs (`r1` else the style's default from
`BackgroundGrid`, `m1`, `lw`, `bli`/`blw`), and the three consumers — `BackgroundRenderer` on screen,
`PageSvgWriter` for the SVG export (now shared in `mobix-core`), `PdfPagePlan.paper` for both shells' PDF exports — all go through it,
so a custom grid can't rule one way on screen and another in the export. `Snapping` resolves the same
numbers — as a lattice, spacing *and* phase — so *Snap to grid* and the guide's **Ruling** mode both
land on the lines the user can actually see. An **isometric** sheet is resolved as a `Snapping.Mesh`
instead: it rules no axis at all, so the lattice carries the two interleaved sub-lattices whose
crossings are the ±30° families' nodes, and a snap resolves the nearest **node** (`Lattice.snap`). Its
phase is derived from the page width the paper is ruled against, which is why `Snapping.lattice` takes
that width and why the surface passes its page's own width in. The renderer caches the
parsed ruling against the config string (one entry), because `draw` runs every frame and a fresh parse
per frame was pure churn. Isometric paper is the same story one level up: `isograph`/`isodotted` are
desktop styles, `r1` is the triangle's side, and the mesh is the pure `BackgroundGrid.isometric`
clipped to the sheet — the two ±30° families whose diagonals are what desktop paints. (A desktop page
ruled `isodotted` is drawn as that same mesh rather than as its dotted variant: a cosmetic difference
on a style we never produce, recorded here rather than guessed at.) The RGB constants in
`BackgroundGrid` carry **no alpha** (`0xRRGGBB`); a renderer must make them opaque before it paints
them. Android's `android.graphics.Color` reads the number that way already, while Compose's
`Color(Int)` wants `0xAARRGGBB` — passing a constant straight through gave the desktop's rulings an
alpha of zero, so its ruled and graph sheets were white on white until the isometric paper exposed it
(`PagePaper.rulingColour` now ORs the alpha in). **Stationery** is the menu over
all of this (`PageTemplates`): millimetre paper, 5 mm graph, 7 mm ruled and 5/10 mm isometric write a
style + `config` pair — real paper the desktop renders too — while **Cornell notes**, which no
Xournal++ version has, is drawn instead: its three rules land as ordinary strokes on a layer named
after the template, so it round-trips like any ink and can be deleted by deleting the layer.

## The page-background dialog (`PageBackgroundDialog`)

**The sheet's graphics (`PageBackgroundDialog`).** The **Pages** menu carries one row for them, right
under **Page format**, and the row opens this dialog — a submenu of its own, like the format one, so a
menu of page commands never turns into a wall of paper chips. The three groups are ordered as the sheet
is set up — *what it is* (paper style), *how it is ruled* (rule spacing), then a finished template over
the top (stationery) — and each is **one flowing row**: the six styles are chips that wrap rather than
six stacked rows, and the spacing chips carry the custom millimetre field at the end of their own row.
The stacked list had buried spacing and stationery below the fold for what is a single either/or
choice. A group that can't apply (spacing on a PDF-backed page) says so in a line of its own instead of
a row of disabled chips, which would claim space to state only that.

## Add/remove page

**Moving and pruning a run of pages.** The list is reordered at two scales. A thumbnail **dragged** down the
window's page sidebar lands on the row the pointer is over — recomputed as the list scrolls under it while
the pointer rests against an edge (`PageReorder`, so a page from the top of a scan can be dropped at the
bottom without letting go) — and **Manage pages…** in the Pages menu acts on a typed range: *pages 3 to 7*
moved so its first page starts on the page you name, or removed, as **one** undo step each through
`PageOps.moveRange`/`removeAll`. A range is refused rather than half-honoured when it would empty the
document or already starts where the move asks, and `PageRanges` owns those sentences, so the panel's dead
buttons and the commands behind them cannot disagree.

**Add/remove page** edit the page list through the pure, tested `PageOps` (`mobix-core`, the one the
window's Pages menu also calls, so neither shell can invent its own rules for a new sheet; a new page is born at the
**default page size** — the settings value `MainActivity.applyDefaultPageSize` pushes into
`DrawingSurfaceDefaults.defaultPageWidth`/`defaultPageHeight`, which the blank-sheet factories every
created sheet comes from — and keeps the paper ruling, but not the background, of the page in view). Each draw, erase, add, or remove snapshots
the whole document into the pure, tested `EditHistory`, so the top-bar **undo/redo** steps one
gesture at a time (snapshots are cheap — immutable pages/layers share structure). The stack is
bounded at `EditHistory.DEFAULT_MAX_DEPTH` (200) steps, dropping the oldest once full; pan and zoom are
view-only and not recorded. Strokes are drawn by the view; text boxes, images, and LaTeX images
are drawn by `ElementRenderer` (text baseline geometry lives in the pure, tested `TextBlock`; image
bytes are decoded once and cached by element identity). A `<teximage>` carries only its LaTeX
source in the model, so it is parsed once (cached by element identity) by the pure `LatexParser`
into a node tree and drawn as **real math** by `LatexRenderer` — fractions (numerator over
denominator with a rule), super/subscripts (smaller and shifted), square roots (radical + vinculum),
and a Unicode table for Greek letters and common operators/relations; the tree is measured at a
reference size then uniformly scaled to fit the element's box. Any parse/draw failure falls back to
the raw source text, so a malformed formula can't crash a frame.

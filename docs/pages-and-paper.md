# Pages, paper and backgrounds

> Read when you change a page's paper (ruling, templates, background chooser), bookmarks, or the page list.
> For the task: [Your first notebook](guide-first-notebook.md).

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
key, so nothing else has to know where bookmarks are kept), and the canvas paints each flag on the
sheet's top-right corner from `DrawingSurfaceView.bookmarkedPages` — view-only chrome, so it is not an
undo step and never reaches the file. The **Pages menu does not carry them**: it answers what can be done
to the page in front of you, and a bookmark is a way of finding one again, so the list is a store the
chrome keeps rather than a section of that menu.

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

## Quick page size (`ToolbarPagesPopup`)

**Two rows, not a dialog.** The Pages pop-up opens with a **Quick size** pair — **Width** and **Height**,
each with a minus and a plus — that changes one dimension of the page in view by `AppSettings.pageSizeStepMm`
(**100 mm** by default; adjustable under **Settings → Drawing → Editor**). The step is a length of **paper**,
converted once by `mobix-core`'s `PageSizeStep` at the exact **25.4 mm/inch** — never the display's density: a
step is a page size, and the same document must come out the same on a 320 dpi tablet and a 160 dpi one.

**It speaks the page format's unit.** The unit picked in **Page format** — mm, in or pt — is a *setting*
(`AppSettings.pageSizeUnit`, `DesktopSettings.pageSizeUnit`), not a mood of one dialog: the quick size prints
both the sheet's own measure and its step through one notation (`PageSizeUnit.measure`, which is also what
`PageSizeStep.label` prints). That notation is `mobix-core`'s `decimalText` — the one implementation of it, which the tablet's fields and the window's inspector call instead of repeating its arithmetic. A sheet kept in inches therefore reads `8.3 in` and steps by `3.9 in` — the
same 100 mm of paper re-expressed, never a second, unrelated number — and a sheet kept in points steps by
283.5 pt. Both shells read that one stored unit, and the window's Pages menu steps by
`PageSizeStep.DEFAULT_MM` in it. A tap is a **step, not a size**: it is summed onto the sheet as it is now
(`DesktopPageSetupState.nudge`), where handing the panel's own numbers back made a second tap step the sheet
the menu opened on — so whether the third tap grew the page depended on when a frame happened to land. The
result goes through the same `onPageSize` path as the Page format dialog, so the sheet and the stored format
cannot disagree.

## Add/remove page

**Moving and pruning a run of pages.** The list is reordered at two scales. A thumbnail **dragged** down the
window's page sidebar lands on the row the pointer is over — recomputed as the list scrolls under it while
the pointer rests against an edge (`PageReorder`, so a page from the top of a scan can be dropped at the
bottom without letting go) — and **Manage pages…** in the Pages menu acts on a typed range: *pages 3 to 7*
moved so its first page starts on the page you name, or removed, as **one** undo step each through
`PageOps.moveRange`/`removeAll`. A range is refused rather than half-honoured when it would empty the
document or already starts where the move asks, and `PageRanges` owns those sentences, so the panel's dead
buttons and the commands behind them cannot disagree.

**The menu's shape** is the tablet's, and the window keeps it: a page **navigator** at the top (the sheet
in view between the arrows that step it), two **quick sizes** that nudge the measure by the shared
`PageSizeStep` without opening a dialog (each row gives its number a fixed, trailing-aligned slot in tabular
figures — `TabularFiguresStyle` — so the two measures end on the same line and the arrows never touch the
digits), *Add page* / *Remove page*, then *Page format* and *Page
background* with their live answers printed at the end of the row and every row wearing its **glyph** in a
leading slot, as the phone's menu items do. **Manage pages** is a section of its own at the foot on
**both** shells — *Move page up*, *Move page down*, *Manage pages…* — so the rows the two shells share
(quick size, add/remove, the page's two questions) stay adjacent.

**The tablet's page list.** The window keeps a page strip down its edge; the tablet opens one from the
**capsule beside the search** in the top bar (`PageListCapsule`: the glyph and the words *Page list*), and
the lane docks **on the rail's side** — `EditorBody` places it between the rail's lane and the canvas, so
turning the toolbar to the right-hand edge takes the list with it, the way the window's strip follows its
tools. Like the rail it is a lane of the layout and never a sheet over the page, and it **expands** into
place rather than appearing at full width: it takes a slice of the page's own width, and a slice that
arrives in one frame reads as a glitch. A row is its **number** and its **preview** and the controls, with
no words beside them. The card **is the sheet's shape**: its width follows the page's own proportions at a
fixed height, so a portrait page is a tall sliver, a landscape one a wide strip, and never one vertical
block with the page floating inside. The preview is drawn from the live page (`PageThumbnail`: the sheet's
paper and its ink, on a white card so a blank page still reads as a page), and a **`pdf` page is its real
sheet** — the raster comes from the canvas's own `PdfPageCache` through
`DrawingSurfacePaint.pdfSheetFor`, the same reference rule the window's strip follows, so a PDF page's
preview is the text the annotations sit on rather than a bare card. The number and the preview **go to**
the page; the box **picks** it, because a tap that navigates is not a tap that selects; the handle
**carries** it, and while it travels the page is **lifted** above the list while the row it would land on
wears the accent line along its top edge (`PageOps.moveAll` on drop, the block rule the window's drag
uses). The gesture belongs to the **list**, not to the row's handle (`pageListDragGesture`): only the
handle's band can start a move, so a row that began a drag on any touch would still be a row that cannot
be scrolled, but a handler on the row itself would die the moment auto-scrolling recycled it — exactly
when the list is meant to keep moving. Near either end of the list the drag **scrolls it** for as long as
the finger holds in the strip (`PageListDrag`, `PageListAutoScroll`): the strip is 56 dp deep, the walking
speed grows with how far into it the page is, and the carried page's place is measured in content pixels —
the finger's travel plus the list's — so it stays under the hand however far the list walks. A drag that
starts on a **picked** row lifts the whole pick, and its card counts the pages that travel, because the
block is what moves (`PageOps.moveAll` moves it on drop). The two
arrows under the list step the pick by one place and the bin takes it out (`PageOps.removeAll`, which
refuses a pick covering the whole document), each as **one** undo step — and with **nothing picked** they
act on the page in view, which is the plain case and needs no ceremony. Either command **carries** the
page: the viewport and the pick follow it to its new place, so the sheet in front of you is the page you
just moved — and the page the next tap acts on, which is why the arrows step a page the way the eye reads
them. The lane's foot carries one door of its own: the PDF's **Contents** outline as a bare glyph at the
bottom right (`PdfContentsButton`), because a jump between pages belongs with the pages rather than in the
menu of page commands. The foot clears the system navigation bar (`navigationBarsPadding`) and its three
buttons are 48 dp: the app draws edge to edge, so a control under the gesture strip needs two taps — the
first one belongs to the system — and a 36 dp target is a target a thumb misses. The typed range is the tablet's own *Manage pages…* card (`PageRangeDialog`), which
reads the same `PageRanges` as the window's panel, so a number the tablet refuses is a number the
window's commands cannot act on either.

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

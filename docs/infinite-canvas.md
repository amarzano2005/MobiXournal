# Infinite canvas and pageless scrolling

> Read when implementing or extending canvas layout, viewport coordinate systems, continuous/infinite scrolling modes, board fitting and bounds, or pagination slicing.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: breaking the rigid page boundary

Traditional `.xopp` documents enforce rigid, fixed-dimension pages (e.g. A4 at $595.28 \times 841.89\text{ pt}$
or Letter at $612 \times 792\text{ pt}$). In modern stylus note-taking, users frequently run out of space
mid-thought, requiring artificial page breaks that fracture mathematical formulas, tables, and concept maps.

MobiXournal introduces an **Infinite Canvas Engine** supporting four distinct spatial modes,
persisted natively in `.mobix` (documented in [mobix-schema-spec.md](mobix-schema-spec.md)).

---

## Canvas geometry modes

```text
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│     Fixed Paginated     │   │    Infinite Vertical    │   │   Infinite Horizontal   │   │       Infinite 2D       │
│  (A4 / Letter / Custom) │   │     (Fixed Width W)     │   │    (Fixed Height H)     │   │      (Free Board)       │
│ ┌─────┐ ┌─────┐ ┌─────┐ │   │ ┌─────────────────────┐ │   │ ┌───────────────...───┐ │   │       -∞ < x < +∞       │
│ │ P.1 │ │ P.2 │ │ P.3 │ │   │ │       0 ≤ x ≤ W     │ │   │ │      0 ≤ y ≤ H      │ │   │       -∞ < y < +∞       │
│ └─────┘ └─────┘ └─────┘ │   │ │       0 ≤ y < +∞    │ │   │ │      0 ≤ x < +∞     │ │   │                         │
└─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘
```

### 1. Fixed Paginated (Legacy & `.xopp` compatible)
- Standard page sequence. Page widths and heights are static.
- Fully round-trippable to desktop Xournal++ via the bridge in [mobix-format.md](mobix-format.md).

### 2. Infinite Vertical (Width-locked, Continuous downwards)
- Ideal for linear lecture notes and vertical handwriting.
- Horizontal width is **locked**, and it is the board's own bound ([below](#the-bound-on-the-non-infinite-axis)):
  $0 \le x \le W$, where a document born here takes $W$ from the pane it opened in.
- The bottom edge automatically extends as the user writes near the lower boundary.

### 3. Infinite Horizontal (Height-locked, Continuous strip)
- Ideal for timeline annotations, engineering process charts, and panoramic diagrams.
- Vertical height is locked the same way ($0 \le y \le H$); horizontal space expands infinitely to the right.

### 4. Infinite 2D (Unconstrained whiteboard)
- Completely free Cartesian plane $(-\infty < x < +\infty, -\infty < y < +\infty)$: it locks nothing, so
  it has no bound at all.
- Ideal for mind mapping, brainstorming, and complex geometric graphs.

### Fitting a board to the screen

`fitBoardIfPending` makes a **strip's bound fill the canvas**: the least magnification at which the
locked axis reaches both edges (`boardFitZoom` = viewport / bound, never below 100%). A 100 mm strip on
a 1248 px canvas opens at 440%, the paper edge exactly on the screen's — no band of desk down the side,
which would be an "infinite" canvas with a visible empty margin. A bound **larger** than the view already
covers it, so it stays at 100% and the view pans across it (`BoardViewport.contentExtent`) rather than
shrinking the paper to fit: the written millimetres keep their own scale. A pane-sized bound — the
default — has nothing to magnify and opens at 100%.

The fit belongs to the **extent it was computed for** (`BoardCanvas.fittedTo`): the tab strip arriving a
frame after the document, a rotation or the split toggle re-fits for the canvas the board is actually on,
which is what stops a board from opening a few percent too large and hiding the edge the user just set.
The first fit takes the zoom; from then on the user owns it (`BoardCanvas.autoFit`) — a pinch or the
＋/− controls stop the tracking (`render/ZoomControls.kt`), so a resize cannot spend the factor they
chose, and **Reset zoom** hands the fit back.

A **2D board locks nothing and opens at 100%**: its `widthPt`/`heightPt` are the extent an export would
slice, grown by `expandBoardOnScroll` as the view travels, so fitting to them made the opening zoom
depend on how far the board had been scrolled before it was last saved.

### The bound on the non-infinite axis

The locked dimension of a strip is a real number in `canvas.json` (`widthPt` for a vertical strip,
`heightPt` for a horizontal one), and it is the only edge the canvas refuses to scroll past
(`ViewportState` clamps that axis to the sheet's own extent, `BoardViewport.contentExtent`). Three rules
make it behave the way paper does:

- **A board is born the size of the pane — or of the bound the dialog named.** A sheet whose width is
  A4 because a printer might one day see it is a narrow band of paper on a 2400 px screen — infinite
  paper that visibly ends a quarter of the way across. So a document created in a strip mode is born
  with the pane's own extent as its bound (`MainActivity.blankTab` → `paneExtentPt`), and the sheet
  covers the canvas the moment it opens. The running axis is unbounded from the first frame, so
  scrolling simply continues the same sheet. The one thing that overrides the pane is a **bound typed
  in the page-format dialog**: when the chosen geometry is *new rather than this document*, the fixed
  width (or height) the dialog was showing travels with the answer (`onCanvasMode(mode, extentPt)` →
  `blankTab(fixedExtentPt)`) and the new board is born on it — the screen is only the fallback. It is
  clamped to the paper range exactly as the live setter is, so the two paths cannot disagree.
- **The bound is settable a priori**, in the page-format dialog: *Pages ▸ Page format ▸ Fixed width* (or
  *Fixed height*), in millimetres like the paper beside it. It applies at once — the sheet, the scroll
  clamp and the cells' own pages all move off the one value — and it is offered only while the document
  has never been written to a file, for the same reason the geometry is: a saved `.mobix`'s shape is its
  own `canvas.json`'s, and changing the paper under chunks already on disk is a conversion, not a
  preference (`docs/mobix-format.md`).
- **A sheet wider than the screen is reachable.** The view may pan across the whole bound (not merely
  the part of it that fits), and a bound narrower than the pane pins the view at the paper's edge rather
  than letting it drift into the grey beside it. The clamp is the **mode's**: every pan and glide step
  goes through `ViewportState.scrollBy`, which bounds a page stack on both axes, a strip on its one
  fixed side, and a 2D board on neither. A page stack's extent describes a board's axes not at all — the
  running one goes on forever, and how far the other reaches is the bound's business — so a pan clamped
  to what a *fitted page* measures would drag the running axis home. A 2D board locks nothing, has no
  bound to offer and is untouched by the field.

---

The way a board is stored, opened, written and queried as spatial chunks is
[chunked-documents.md](chunked-documents.md)'s.

## Auto-pagination and export slicing

When an infinite canvas leaves the app as `.xopp`, PDF, PNG, or SVG (`sheetDocument` → `BoardSlicer.slice`),
the board's spatial chunks and expanded dimensions are assembled into **one single continuous page**
(`pages.size == 1`) sized to the full expanded area (`maxOf(geometry.widthPt, board.right)` /
`maxOf(geometry.heightPt, board.bottom)`), while `CanvasAutoSlicer` remains available as the pure
content-aware seam utility.

### What an export does with a board

`BoardSlicer.slice` translates every chunk's cell-local elements onto one continuous sheet:

- **Where the ink is.** A chunk stores its elements **cell-local** (`x = 0` is the tile's left edge), so
  each element is moved onto the board by `(cx * chunkSizePt, cy * chunkSizePt)` (plus any
  `origin.offsetPt`), ordered by spatial cell coordinate, and shifted if any 2D coordinate is negative so
  all coordinates on the single page are $\ge 0$.
- **Single expanded page.** The resulting document has `pages.size == 1`, with `width` and `height`
  covering both the expanded `geometry.widthPt` / `geometry.heightPt` (grown by scrolling in the editor
  and recorded via `rememberBoardShape`) and the furthest reach of all ink across the tiles.
- **Which paper the sheet is.** The board's own, taken from the first chunk (`defaultPaper`), so the
  ruling runs continuously across the entire single page without tile seams.

Pinned by JVM tests: `BoardSlicerTest` (all cells assemble onto one expanded page, spatial coordinate
ordering, paper preservation) and `InfiniteExportTest` (the written `.xopp` re-parses through
`Xopp.parseXml` as a single expanded page while the `.mobix` keeps its chunks).

# Infinite canvas and pageless scrolling

> Read when implementing or extending canvas layout, viewport coordinate systems, continuous/infinite scrolling modes, spatial chunking, or pagination slicing.

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

`fitBoardIfPending` fits a **strip** to the canvas it is on and shows its **whole bound**: the locked
axis is fitted *inside* the view and never magnified past 100% (`boardFitZoom` = min(1, viewport /
bound)). The earlier reading — zoom up until the bound *covered* the screen — made the number in the
page-format dialog invisible: a 100 mm strip came back at 484%, filling the pane with an endless-looking
subdivided grid whose paper edge sat off-screen, which reads exactly like "the size I set was ignored".
Showing the bound whole keeps the typed millimetres true, and leaves the pane-sized default (a bound
equal to the screen) at 100%, covering the canvas as before.

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

## Spatial indexing & chunking architecture

Rendering strokes across an unbounded coordinate plane cannot rely on a single continuous canvas bitmap,
as allocating a $20000 \times 20000\text{ px}$ surface would exhaust device RAM.

Instead, the Infinite Canvas partitions space into a **sparse 2D tile grid (Spatial Chunks)**:

```text
Chunk Coordinate: (cx, cy) where cx = floor(x / CHUNK_SIZE_PT), cy = floor(y / CHUNK_SIZE_PT)
CHUNK_SIZE_PT = 1024.0 pt (~14.2 inches at 72 DPI)
```

```text
                        y
                        ▲
          (-1, 1)       │   (0, 1)        (1, 1)
                        │
      ──────────────────┼─────────────────────────► x
                        │
          (-1, 0)       │   (0, 0)        (1, 0)
                        │   [Viewport]
```

### Chunk files on disk

Each occupied chunk is one JSON entry in the `.mobix` container (the envelope is
[mobix-schema-spec.md](mobix-schema-spec.md)'s; this is its chunk half). The name carries the signed
coordinates, because a 2D board goes negative and `-1` must not collide with `1`:

```text
chunks/chunk_<cx>_<cy>.json      # cx, cy signed decimal integers, no padding, no '+' sign
                                 # e.g. chunk_0_0.json, chunk_-3_2.json, chunk_2_-1.json
```

The same positive/negative split decides the file *and* the index: `manifest.json`'s `chunks[]`
lists the entries in the document's own order with the ids/hashes a merge uses
([sync-and-storage.md](sync-and-storage.md)), so nothing is derived by scanning the archive.

### `canvas.json`

One small envelope per document, written alongside `manifest.json`, fixing the mode every chunk is
interpreted in:

```json
{
  "mode": "INFINITE_VERTICAL",
  "widthPt": 595.28,
  "heightPt": 841.89,
  "chunkSizePt": 1024.0,
  "origin": { "pageNo": 1, "offsetPt": 0.0 }
}
```

- `mode` is one of `PAGINATED`, `INFINITE_VERTICAL`, `INFINITE_HORIZONTAL`, `INFINITE_2D`. Only
  `PAGINATED` uses `pages/`; the other three use `chunks/`, and a file that carries both is invalid.
- `widthPt`/`heightPt` are the **locked** dimension in the two strip modes (ignored in `INFINITE_2D`)
  and the page size in `PAGINATED`.
- `chunkSizePt` is written explicitly rather than assumed, so a future change to the default cannot
  silently re-interpret an existing document's chunk grid.
- `origin` records where document coordinates `(0, 0)` sit on the *printed* sheet — the anchor the
  auto-slicer below uses to decide where page 1 of an export begins.

### A chunked document opens, is edited and saves as chunks

What the app does with this geometry today, so the mode is not a format the editor cannot keep.

- **A chunk is a part.** The list a reader counts, ids and inflates is `pages[]` for a paginated document
  and `chunks[]` for an infinite one, and the two differ only in how a part becomes a page —
  `chunks/chunk_<cx>_<cy>.json` decodes through `MobixChunkCodec` into the page it stands for. So a
  chunked document opens **page by page** like any other (`io/DocumentIo.kt` → `MobixDocument.openPaged`),
  and the mode travels with what was loaded (`LoadedFile.Doc.canvasMode`) rather than being asked of the
  page model, which has no notion of a board.
- **Its shape is remembered, not re-derived.** A chunk is named after where it sits, so the coordinates are
  a fact of the file: `MobixOpenSessions` keeps the manifest's own chunk order and the `canvas.json` it was
  read with, and hands both to the write. A save therefore lands on `chunks/`, **at the coordinates the
  parts were at**, and writes the board geometry back as the file had it (the locked dimension, the tile
  size the names were computed against, the printed-sheet `origin`, and any field this build does not
  model). A write that derived coordinates from the page index would move every chunk of a 2D board into
  the first column, and one that rebuilt `canvas.json` from the pages would change a board's geometry —
  both are data loss dressed as a format choice.
- **An unchanged chunk is copied, not rebuilt.** The incremental save covers a chunk exactly as it covers a
  page ([mobix-format.md](mobix-format.md) → *What a `.mobix` save writes*): a chunk the archive already
  holds contributes its manifest part and no entry, and its bytes are streamed out of the source file
  instead of being decoded and re-encoded. The manifest keeps that part's `sha256` and `modified` stamp as
  the archive recorded them, which is what a merge and a sync client read
  ([sync-and-storage.md](sync-and-storage.md)). A chunk the write does *not* build is never even converted
  from its page (`LazyChunks`), so the only page such a save reads is the board's first, which carries
  `defaultPaper` into the new manifest.
- **A document is born on one, chosen with its page format.** The geometry is the second half of the one
  question a document asks about itself, beside its paper (and its format,
  [mobix-format.md](mobix-format.md) → *Which format a document is written in*): **Pages ▸ Page format**
  (`ui/ToolbarPagesPopup.kt` → `PageFormatDialog`) shows the four geometries — *Paged*, *Infinite
  vertical*, *Infinite horizontal*, *Infinite board* — beside the A4/Letter presets, because the two
  answer one question: *what is a page of this document*. On confirm the dialog **asks where the choice
  goes**: *this document*, or *only the new ones*. **This document** is offered only while the document
  has never been saved (`TabsUiState.canvasEditable`): a `.mobix` that came from a file keeps the
  geometry its own `canvas.json` declares, because moving its parts between `pages/` and `chunks/`
  converts the file, and a conversion is not a preference. *New documents only* writes
  `AppSettings.newDocumentCanvas` — the same value Settings used to host, which is now chosen here and
  nowhere else (`Settings → App & data → Documents` only says where). An unsaved document re-shapes in
  place: its tab's `OpenTab.canvasMode` is what the next save is written from, so a re-show is the whole
  conversion (`MainActivity.applyCanvasModeToDocument`), pages becoming the strip of chunks they imply
  and a board going back to the pages its cells stand for. The mode is pushed down with the format
  (`MainActivity.applyNewDocumentCanvas`, `ui/NewDocumentCanvas.kt`), and the blank document a new tab
  opens with is created **on** it. The mode rides on the **tab**
  (`OpenTab.canvasMode`, persisted with the rest of the tab record in `TabIndex`) rather than being
  re-derived per document, so the first save already writes `chunks/` — a board is never converted out
  of a paginated file — and it survives a tab switch and a restart. A `.xopp` has no chunk container, so
  a document created in one is paged whatever the setting says (`NewDocumentCanvas.modeFor`): the
  geometry is *not applicable* there, not silently ignored.
- **It is drawn as a board.** The canvas is handed the document's geometry (`io/CanvasGeometry.kt`: the
  mode, the paper's own dimensions, the tile grid and the occupied cells in document order) by the layer
  that read the file, and paints it in **one pass instead of the page stack**: one *sheet* — the paper,
  ruled across the whole board from a band aligned to the ruling grid, so the ruling does not restart at a
  tile boundary — and one box per occupied cell the viewport intersects, each placed by its own
  coordinate (`render/DrawingSurfaceBoard.kt`, `render/BoardViewport.kt`). Nothing is read ahead: a tile
  is materialised through the page source only when its cell is on screen
  (`ChunkTileManager.getOrCreateChunk`), and the tiles scrolled away are evicted on the **document's own**
  grid. No page outline, bookmark flag or page counter is drawn for a board: it has no page 1 of N.
- **It can be written on.** Ink lands in the chunk under the pen, and the eraser loads a chunk the
  viewport evicted before it rubs ([Writing on a board](#writing-on-a-board)).
- **The chrome tells the truth for it.** The position badge reads the tile's coordinate instead of a page
  count, the Pages menu becomes a tiles reading, bookmarks and the page grid are not offered, the scroll
  thumb is gone, find still works in tile space, and split view mirrors a board as a second view of the
  same grid — the decisions per surface, and why each is what it is, are
  [chrome.md](chrome.md#a-document-with-no-pages)'s (one home per fact).
- **It can leave the app as pages.** Save As `.xopp` and Export PDF run a board through the auto-slicer,
  so a board can also be read, written and navigated *and* handed to a machine that only knows pages
  ([Auto-pagination and export slicing](#auto-pagination-and-export-slicing)).
- **It has been seen on a device.** The chunked open, the canvas, the ink and the chrome that reads
  tiles were all exercised on the emulator over a 2D board with cells at `(0, 0)`, `(1, 0)` and
  `(-1, 0)` (the device-fixture generator in [tools.md](tools.md) writes it): the board opens
  **writable** and is drawn as one sheet with its tiles on it; a stroke drawn from cell `(0, 0)` runs
  past the cell's edge to `x = 1894` and is stored **whole**, with cell-local `x`, in the chunk the
  pen went down in, and a stroke started in `(1, 0)` is stored the same way with a **negative** `x`;
  the save that follows repacks `chunks/` at each cell's own coordinate and writes no `pages/` entry;
  the rail's Pages button is **Tiles**, the position badge reads a coordinate (`1, 0`) rather than
  `n / m`, and the menu behind it reads *Tile 1, 0* and *3 tiles* with no page rows, no bookmarks and
  no overview grid. **Find and split view on a board** are the two surfaces of that pass still
  unexercised (`check-find-and-split-view-on-a-board`).

### Writing on a board

The canvas engine's editing half, and the decisions it had to make — because a board is not a page
stack, and every one of them is a place where "almost a page" would have been wrong.

- **The chunk under the pen.** A gesture converts its view point to a **document** point (un-scroll,
  un-zoom) and that point names the cell: `floor(pt / chunkSizePt)` per axis, on the tile size the file
  itself declares. The page the stroke is written to is the page that cell stands for — `grid[i]` is
  `pages[i]` — and the chunk is created when the board has no chunk there yet, so an infinite canvas is
  writable past its last tile rather than only drawable inside it (`render/BoardInk.kt`).
- **Ink is stored cell-local, like every element of a chunk**: `x = 0` is the left edge of the tile it
  lives in. That is what the chunk files hold and what the drawing pass reads back, so the box a
  gesture converts through is the *cell's own* box (`render/DrawingSurfaceBoardInk.kt`) and the two can
  never disagree about where the ink is. A stroke belongs to the chunk the pen went **down** in, even
  when it runs past the cell's edge — a chunk is a storage unit, and the format has no way to split one
  stroke across two.
- **The document is the store, so an eviction cannot lose ink.** A write lands on the cell's page
  *through the page window*, which holds every edit in an overlay eviction never touches
  ([data-path.md](data-path.md) → *The page window*), and the tile cache is only a copy of that page.
  A chunk the viewport drops (`ChunkTileManager.evictOffscreen`, on the document's own grid) is read
  back **through the document** rather than out of the file the document came from, so the ink that was
  written into it is still there — the same trap `edit-a-page-the-lru-window-has-evicted` closed for
  pages. Nothing needs to be written out on eviction: by the time a chunk is evicted, its stroke is
  already in the document that will be saved.
- **The cells follow the page list, through the one place an edit lands.** A cell this session created
  is appended where the document's next page goes, and the `doc` setter reconciles the two after every
  change (`BoardGrid.fit`): an undo trims the tail, and a **redo hands the same coordinate back**
  (kept in the order it was removed, exactly as the document history keeps its own branch). Undo and
  redo therefore work on a board unchanged — they snapshot the document, and the grid is put back in
  step with it.
- **What a board can be edited with.** Drawing, highlighter, shapes, splines, the text/image/LaTeX
  placement tools and the **eraser** all work, in cell space. The eraser loads the chunk first, so
  rubbing into a chunk the viewport evicted erases the ink that is really there. Refused, with one
  message and one gate (`GestureIntent.isPageShaped`), are the gestures that *are* page-shaped: the
  rubber band and everything it feeds (the element clipboard, move, resize, rotate), the
  background-region copy, PDF text selection and the vertical-space drag. They speak page indices and
  page *bounds*, and half-working on a grid of chunks would be worse than not working.
- **A save writes the cells the board holds now.** The coordinates are a fact of the board, not of the
  pages, so the canvas hands its live grid to the session on the way into the writer
  (`rememberBoardShape`): a cell created since the open keeps its own name, and a save cannot fall back
  to the mode's strip and move the ink that was written into it.
- **A placement gives an empty cell its page.** A tap with the text, image or LaTeX tool in a cell the
  board has no chunk for creates the chunk, because the element it is about to insert needs a page to
  live on. Every other path leaves an empty cell empty: drawing, erasing and panning create nothing.

### Opening a board shows its bound and expands on scroll

**Opening an infinite canvas shows its whole bound at its own size (`fitBoardIfPending`) and expands progressively as you scroll (`expandBoardOnScroll`).**
- **Canvas mode and immediate dimension configuration in `Pages ▸ Page format`**: Selecting any of the four modes (*Paged*, *Infinite vertical*, *Infinite horizontal*, *Infinite board*) inside `PageFormatDialog` immediately switches the dialog's dimension controls to match that mode (paper presets and width/height for *Paged*; **Fixed width** or **Fixed height** for infinite strips) so the user can configure the dimensions before confirming with **Set**.
- **The whole bound on open**: When a board opens, `fitBoardIfPending()` fits the fixed axis (`widthPt` for `INFINITE_VERTICAL`, `heightPt` for `INFINITE_HORIZONTAL`) *inside* the viewport and never above 100% (`boardFitZoom()`), so a bound typed in the dialog is shown at the size it names, and a bound equal to the pane still fills the screen. It also seeds the infinite axis so the sheet covers the running direction from the first frame, and it re-runs when the view's own extent changes until the user takes the zoom by hand.
- **Progressive expansion on scroll**: Every pan or scroll step (`scrollViewportBy`) invokes `expandBoardOnScroll()`, extending `board.geometry` along the mode's infinite axis (`heightPt` for `INFINITE_VERTICAL`, `widthPt` for `INFINITE_HORIZONTAL`, or both for `INFINITE_2D`) to cover the newly scrolled viewport extent.

### Spatial query and culling
1. **Viewport intersection**: At each frame, the active viewport bounds $[x_{\min}, y_{\min}, x_{\max}, y_{\max}]$
   are converted to the integer range of visible chunk coordinates:
   $$\text{cx} \in \left[\lfloor x_{\min}/S \rfloor, \lfloor x_{\max}/S \rfloor\right], \quad \text{cy} \in \left[\lfloor y_{\min}/S \rfloor, \lfloor y_{\max}/S \rfloor\right]$$
2. **Bounding-box hierarchy**: Each chunk maintains an `R-Tree` or spatial bounding box list of its elements.
3. **Offscreen eviction**: Chunks outside the visible viewport plus a 1-chunk margin are dropped from the
   tile cache, on the **document's own** grid (the tile size its chunk names were computed against). An
   evicted chunk is not written anywhere special: its page — the document's own, edits and all — is what
   the cache was a copy of, and reading the chunk back reads it through the page window rather than out
   of the source file, so nothing is lost and nothing needs flushing
   ([Writing on a board](#writing-on-a-board)).

---

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

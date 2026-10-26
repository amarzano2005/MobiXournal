# Chunked documents: how a board is stored, opened, written and queried

> Read when implementing or extending spatial chunking, `chunks/`, `canvas.json`, writing ink on a board, or culling tiles.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This spoke owns the **storage and editing engine** of an infinite board: the tile grid, the chunk files,
the geometry envelope, how a board is opened, inked, saved and queried, and what is culled. The four
canvas **modes**, the bound on the locked axis and the export slicer are
[infinite-canvas.md](infinite-canvas.md)'s; the container's envelope is
[mobix-schema-spec.md](mobix-schema-spec.md)'s.

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
lists the entries in the document's own order with their ids and content hashes, the bookkeeping a
three-way merge in `MobixMergeEngine` keys on ([mobix-format.md](mobix-format.md) → *Merging two
copies is library code*), so nothing is derived by scanning the archive.

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
  the archive recorded them, which is what a later merge reads
  ([mobix-format.md](mobix-format.md) → *Merging two copies is library code*). A chunk the write does *not* build is never even converted
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
  [chrome-frame.md](chrome-frame.md#a-document-with-no-pages)'s (one home per fact).
- **It can leave the app as pages.** Save As `.xopp` and Export PDF run a board through the auto-slicer,
  so a board can also be read, written and navigated *and* handed to a machine that only knows pages
  ([Auto-pagination and export slicing](infinite-canvas.md#auto-pagination-and-export-slicing)).
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
- **The sheet fills the screen on open**: When a board opens, `fitBoardIfPending()` magnifies the fixed axis (`widthPt` for `INFINITE_VERTICAL`, `heightPt` for `INFINITE_HORIZONTAL`) just enough for it to reach both edges of the viewport and no further (`boardFitZoom()`), never dropping below 100% — a bound larger than the pane is scrolled, not shrunk. It also seeds the infinite axis so the sheet covers the running direction from the first frame, it re-runs when the view's own extent changes (so the fit is exact rather than computed against the first frame's canvas) until the user takes the zoom by hand, and **Reset zoom** hands the fit back.
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

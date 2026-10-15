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
- Horizontal width is locked to standard paper dimensions (e.g., standard A4 width: $595.28\text{ pt}$).
- The bottom edge automatically extends as the user writes near the lower boundary.

### 3. Infinite Horizontal (Height-locked, Continuous strip)
- Ideal for timeline annotations, engineering process charts, and panoramic diagrams.
- Vertical height is locked (e.g. $841.89\text{ pt}$); horizontal space expands infinitely to the right.

### 4. Infinite 2D (Unconstrained whiteboard)
- Completely free Cartesian plane $(-\infty < x < +\infty, -\infty < y < +\infty)$.
- Ideal for mind mapping, brainstorming, and complex geometric graphs.

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
- **What is still missing** is the *canvas engine*: the surface still lays out pages of a fixed size, so a
  board scrolls past its first chunk only once the chunk geometry reaches the renderer
  (`render-a-chunked-document-as-an-unbounded-canvas`), strokes have nowhere to be written yet
  (`write-strokes-into-a-chunk-and-keep-them-across-eviction`), the chrome still counts pages
  (`make-the-chrome-tell-the-truth-for-a-document-with-no-pages`), and export still slices nothing
  (`export-an-infinite-document-through-the-auto-slicer`).

### Spatial query and culling
1. **Viewport intersection**: At each frame, the active viewport bounds $[x_{\min}, y_{\min}, x_{\max}, y_{\max}]$
   are converted to the integer range of visible chunk coordinates:
   $$\text{cx} \in \left[\lfloor x_{\min}/S \rfloor, \lfloor x_{\max}/S \rfloor\right], \quad \text{cy} \in \left[\lfloor y_{\min}/S \rfloor, \lfloor y_{\max}/S \rfloor\right]$$
2. **Bounding-box hierarchy**: Each chunk maintains an `R-Tree` or spatial bounding box list of its elements.
3. **Offscreen eviction**: Chunks outside the visible viewport plus a 1-chunk margin are dropped from active
   GPU memory and cached to disk.

---

## Auto-pagination and export slicing

Because printing, PDF export, and interoperability with `.xopp` require discrete pages, the Infinite Canvas
includes an **Intelligent Auto-Slicer**:

```text
Infinite Strip [==================================================]
Slicing:       [--- Page 1 ---] [--- Page 2 ---] [--- Page 3 ---]
                     ▲                ▲
              Content-aware gap (avoid splitting lines of text / strokes)
```

1. **Content-aware seam detection**: Rather than slicing mechanically at rigid intervals $H, 2H, 3H$, the slicer
   searches a vertical search window $[\pm 40\text{ pt}]$ around each target cut for empty whitespace bands.
2. **Stroke splitting avoidance**: If a bounding box of a `Stroke`, `TableElement`, or `PlotElement` spans the cut line,
   the seam is shifted above or below the element to keep it intact on a single page.
3. **PDF & `.xopp` generation**: Exporting produces a standard multi-page document that opens cleanly in desktop
   Xournal++ or PDF viewers.

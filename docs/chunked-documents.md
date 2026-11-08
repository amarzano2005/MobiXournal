# Chunked documents: geometry modes, storage and the export

> Read when implementing or extending a document's geometry mode, spatial chunking, `chunks/`,
> `canvas.json`, or the slicing of a chunked document into pages on export.
> For the task: [What works, and what is not there](guide-limitations.md), [The words this guide uses](guide-glossary.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This spoke owns everything the `.mobix` container does when its content is not a stack of fixed pages: the
**geometry modes** a document can declare and what their dimensions mean, the sparse tile grid and its
files, `canvas.json`, how such a document is opened, edited, saved and queried, and how it leaves the app
as ordinary paginated files. The container's envelope is [mobix-schema-spec.md](mobix-schema-spec.md)'s.

**The app no longer offers a board canvas.** Every document the editor creates is a page stack: there is
no mode picker, no unbounded sheet, no paged/chunked toggle and no tiles reading in the chrome. A chunked
`.mobix` still **opens**, as its parts in manifest order — one page per chunk — and a save writes it back
as chunks at their own coordinates, so the format is read and kept rather than converted behind the user's
back. What follows describes the **file's** geometry and its storage path, not a surface the editor
draws.

## Canvas geometry modes

`canvas.json` records one of four modes:

```text
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│     Fixed Paginated     │   │    Infinite Vertical    │   │   Infinite Horizontal   │   │       Infinite 2D       │
│  (A4 / Letter / Custom) │   │     (Fixed Width W)     │   │    (Fixed Height H)     │   │      (Free Board)       │
│ ┌─────┐ ┌─────┐ ┌─────┐ │   │ ┌─────────────────────┐ │   │ ┌───────────────...───┐ │   │       -∞ < x < +∞       │
│ │ P.1 │ │ P.2 │ │ P.3 │ │   │ │       0 ≤ x ≤ W     │ │   │ │      0 ≤ y ≤ H      │ │   │       -∞ < y < +∞       │
│ └─────┘ └─────┘ └─────┘ │   │ │       0 ≤ y < +∞    │ │   │ │      0 ≤ x < +∞     │ │   │                         │
└─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘
```

### 1. Fixed Paginated (legacy and `.xopp` compatible)

The ordinary page sequence, stored under `pages/`. Page widths and heights are static, and the document
is fully round-trippable to desktop Xournal++ through the bridge in [mobix-format.md](mobix-format.md).

### 2. Infinite Vertical (width-locked, continuous downwards)

A vertical strip: $0 \le x \le W$ with the running axis down. `canvas.json`'s `widthPt` is the locked
side, and it is also the cut width an export uses.

### 3. Infinite Horizontal (height-locked, continuous strip)

A horizontal strip: $0 \le y \le H$ with the running axis right. `heightPt` is the locked side.

### 4. Infinite 2D (unconstrained board)

A free Cartesian plane, locking nothing and signed on both axes: chunk coordinates go negative, which
is why their names carry the sign ([*Chunk files on disk*](#chunk-files-on-disk)).

The dimensions in a strip mode are the **format's**, not a preference the app applies: the file records
them, a save writes them back untouched, and the app never resizes them.

## Spatial indexing & chunking architecture

Rendering strokes across an unbounded coordinate plane cannot rely on a single continuous canvas bitmap,
as allocating a $20000 \times 20000\text{ px}$ surface would exhaust device RAM.

Instead, the format partitions space into a **sparse 2D tile grid (Spatial Chunks)**:

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

- `mode` is one of the four modes above. Only `PAGINATED` uses `pages/`; the other three use `chunks/`,
  and a file that carries both is invalid.
- `widthPt`/`heightPt` are the **locked** dimension in the two strip modes (ignored in `INFINITE_2D`)
  and the page size in `PAGINATED`.
- `chunkSizePt` is written explicitly rather than assumed, so a future change to the default cannot
  silently re-interpret an existing document's chunk grid.
- `origin` records where document coordinates `(0, 0)` sit on the *printed* sheet — the anchor the
  export slicer uses to decide where page 1 of an export begins
  ([Export slicing](#export-slicing)).

### A chunked document opens, is edited and saves as chunks

What the app does with this geometry, so the mode is not a format the editor cannot keep.

- **A chunk is a part.** The list a reader counts, ids and inflates is `pages[]` for a paginated document
  and `chunks[]` for an infinite one, and the two differ only in how a part becomes a page —
  `chunks/chunk_<cx>_<cy>.json` decodes through `MobixChunkCodec` into the page it stands for. So a
  chunked document opens **page by page** like any other (`io/DocumentIo.kt` → `MobixDocument.openPaged`),
  and a chunk is shown as the page it stands for, in manifest order, in the ordinary page stack.
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
- **Editing a part is ordinary page editing.** A stroke drawn on the page a chunk stands for is committed
  through the page window like any other edit and written back into that chunk. The editor has no cell
  coordinate to derive, no live tile grid to keep and no placement that creates a new cell: the cells are
  the file's, in the order the manifest lists them.
- **It can leave the app as pages.** Save As `.xopp` runs a chunked document through the slicer, so a
  file written that way is one expanded page a machine that only knows pages can read
  ([Export slicing](#export-slicing)); Export PDF writes each part as the page it stands for.

### Spatial query and culling

These are the format engine's rules, kept for a future board surface — the app's page stack does not
cull chunks today.

1. **Viewport intersection**: viewport bounds $[x_{\min}, y_{\min}, x_{\max}, y_{\max}]$ are converted to
   the integer range of visible chunk coordinates:
   $$\text{cx} \in \left[\lfloor x_{\min}/S \rfloor, \lfloor x_{\max}/S \rfloor\right], \quad \text{cy} \in \left[\lfloor y_{\min}/S \rfloor, \lfloor y_{\max}/S \rfloor\right]$$
2. **Bounding-box hierarchy**: each chunk maintains an `R-Tree` or spatial bounding box list of its elements.
3. **Offscreen eviction**: chunks outside the visible viewport plus a 1-chunk margin are dropped from a
   tile cache, on the **document's own** grid (the tile size its chunk names were computed against). An
   evicted chunk is never written anywhere special: its page — the document's own, edits and all — is
   what the cache was a copy of, and reading the chunk back reads it through the page window rather than
   out of the source file.

## Export slicing

When a chunked document leaves the app as `.xopp`, PNG or SVG (`sheetDocument` → `BoardSlicer.slice`),
its spatial chunks are assembled into **one single continuous page** (`pages.size == 1`) sized to the
full area the geometry and the ink describe, while `CanvasAutoSlicer` remains available as the pure
content-aware seam utility. Export PDF takes the other road: it writes each part as the page it stands
for, one PDF page per chunk.

### What an export does with a chunked document

`BoardSlicer.slice` translates every chunk's cell-local elements onto one continuous sheet:

- **Where the ink is.** A chunk stores its elements **cell-local** (`x = 0` is the tile's left edge), so
  each element is moved onto the board by `(cx * chunkSizePt, cy * chunkSizePt)` (plus any
  `origin.offsetPt`), ordered by spatial cell coordinate, and shifted if any 2D coordinate is negative so
  all coordinates on the single page are $\ge 0$.
- **Single expanded page.** The resulting document has `pages.size == 1`, with `width` and `height`
  covering both the geometry's own extent and the furthest reach of all ink across the tiles.
- **Which paper the sheet is.** The document's own, taken from its first part (`defaultPaper`), so the
  ruling runs continuously across the entire single page without tile seams.

The app resolves that geometry from the session's record of the file it opened (`DocumentIo.openedCanvasMode`
and `canvasGeometryOf`), so an export of a chunked file is sliced correctly even though the editor shows
it one part at a time.

Pinned by JVM tests: `BoardSlicerTest` (all cells assemble onto one expanded page, spatial coordinate
ordering, paper preservation) and `InfiniteExportTest` (the written `.xopp` re-parses through
`Xopp.parseXml` as a single expanded page while the `.mobix` keeps its chunks).

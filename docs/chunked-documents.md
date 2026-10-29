# Chunked documents: how a board is stored, opened, written and queried

> Read when implementing or extending spatial chunking, `chunks/`, `canvas.json`, or the incremental save of a chunked document.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This spoke owns the **storage and I/O engine** of an infinite board: the tile grid, the chunk files, the
geometry envelope, how a chunked document is opened and saved, and how its parts are queried. The four
canvas **modes** and the export slicer are [infinite-canvas.md](infinite-canvas.md)'s; the container's
envelope is [mobix-schema-spec.md](mobix-schema-spec.md)'s.

The app has no board canvas: a chunked `.mobix` is opened as its parts in manifest order, one page per
chunk, and a save keeps it chunked at its own coordinates. What is described below is that storage and
I/O path, not a surface the editor draws.

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

- `mode` is one of `PAGINATED`, `INFINITE_VERTICAL`, `INFINITE_HORIZONTAL`, `INFINITE_2D`. Only
  `PAGINATED` uses `pages/`; the other three use `chunks/`, and a file that carries both is invalid.
- `widthPt`/`heightPt` are the **locked** dimension in the two strip modes (ignored in `INFINITE_2D`)
  and the page size in `PAGINATED`.
- `chunkSizePt` is written explicitly rather than assumed, so a future change to the default cannot
  silently re-interpret an existing document's chunk grid.
- `origin` records where document coordinates `(0, 0)` sit on the *printed* sheet — the anchor the
  export slicer uses to decide where page 1 of an export begins
  ([infinite-canvas.md](infinite-canvas.md) → *Export slicing*).

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
  ([Export slicing](infinite-canvas.md#export-slicing)); Export PDF writes each part as the page it
  stands for.

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

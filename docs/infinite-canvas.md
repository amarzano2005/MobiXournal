# Infinite canvas documents

> Read when implementing or extending the chunked-document geometry modes, their spatial coordinates, or the slicing of a chunked document into pages on export.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What this owns

The `.mobix` container can describe content that is not a stack of fixed pages. This spoke owns those
**geometry modes**, what their dimensions mean, and how a document written in one leaves the app as
ordinary paginated files. The way the parts are stored, opened, written and queried is
[chunked-documents.md](chunked-documents.md)'s; the container envelope is
[mobix-schema-spec.md](mobix-schema-spec.md)'s.

**The app no longer offers a board canvas.** Every document the editor creates is a page stack: there
is no mode picker, no unbounded sheet, no paged/chunked toggle and no tiles reading in the chrome. A
chunked `.mobix` still **opens**, as its parts in manifest order — one page per chunk — and a save
writes it back as chunks at their own coordinates, so the format is read and kept rather than converted
behind the user's back. This spoke therefore describes the **file's** geometry, not a canvas mode the
app can enter.

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
is why their names carry the sign ([chunked-documents.md](chunked-documents.md)).

The dimensions in a strip mode are the **format's**, not a preference the app applies: the file records
them, a save writes them back untouched, and the app never resizes them.

---

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

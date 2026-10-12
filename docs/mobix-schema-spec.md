# The `.mobix` format specification and dynamic entities

> Read when implementing serialization, parser models, dynamic parametric elements (tables, plots, shapes, circuits), or backward compatibility for `.mobix`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns the **container layout and the on-disk schema** of `.mobix`; the motivation, the
`.xopp` bridge and the app-level policy are in [mobix-format.md](mobix-format.md). The container is
canonical here — do not re-draw the tree in the other spoke.

## Container structure

`.mobix` is a ZIP archive, typed `application/vnd.mobixournal+zip`. The archive layout isolates
global metadata, per-page (or per-chunk) data, and binary assets:

```text
document.mobix
├── mimetype                     # Uncompressed ASCII: "application/vnd.mobixournal+zip"
├── manifest.json                # Document envelope & configuration (schema below)
├── canvas.json                  # Spatial geometry mode & dimensions (see infinite-canvas.md)
├── pages/                       # Paginated mode pages
│   ├── page-0001.json
│   └── page-0002.json
├── chunks/                      # Infinite mode spatial tiles (see infinite-canvas.md)
│   ├── chunk_0_0.json
│   └── chunk_0_1.json
├── assets/                      # Binary deduplicated assets, named by content hash
│   ├── img_<sha256>.webp        # Embedded images (WebP/PNG/JPEG)
│   └── aud_<sha256>.opus        # Bundled audio (see audio-inking-sync.md)
├── background/                  # Document-level background PDF(s), if any
│   └── background.pdf
└── xopp-bridge/                 # Interoperability sidecar (see mobix-format.md)
    └── metadata.json            # Bridge bookkeeping: source file, synthesis hashes, timestamps
```

### The rules that make it a format (not just a ZIP)

These are the parts an implementation must get right; the rest is payload.

1. **`mimetype` is the first entry, and it is STORED.** Same reasoning as ODF: a reader that only
   wants to know what the file *is* must be able to read it from the first bytes without inflating
   anything. Entries are otherwise DEFLATED.
2. **A `.mobix` is told from a ZIP-package `.xopp` by that entry, not by the file name.** Both are
   ZIP archives and both begin `PK\x03\x04`, so `FileKind`'s byte sniff only reaches "it is a ZIP"
   (see [data-path.md](data-path.md)); the discriminator is the `mimetype` entry's content, and the
   `.mobix` suffix is a hint at most (a SAF `content://` URI often has no usable name). A reader
   therefore opens the archive, reads `mimetype`, and dispatches on it:
   `application/vnd.mobixournal+zip` → this format; anything else → the `.xopp` package reader.
3. **`manifest.json` is always present**, and a file without it is not a valid `.mobix`.
4. **Unknown entries are preserved on rewrite.** A file written by a newer app can carry entries
   this build does not understand (`chunks/`, a future `index/`); rewriting the document must carry
   them through untouched, exactly like an unknown XML element in the `.xopp` path (see
   [xopp-format.md](xopp-format.md)). This is what makes "open in v2.1, save in v2.0" non-destructive.
5. **Page names are `page-%04d.json`, one-based, and the *manifest* fixes the order.** Reading a
   document never sorts file names — a page list that was reordered or spliced would come back in
   the wrong order. Names past `page-9999` keep counting (`page-10000.json`); the width is padding,
   not a limit.

---

## Manifest schema (`manifest.json`)

```json
{
  "formatVersion": 1,
  "minAppVersion": "2.0.0",
  "documentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "title": "Analisi Matematica II - Appunti",
  "created": 1775952000000,
  "modified": 1775955600000,
  "canvasMode": "INFINITE_VERTICAL",
  "pages": [
    { "id": "pg_01", "entry": "pages/page-0001.json", "modified": 1775955600000, "sha256": "9f86d081…" }
  ],
  "defaultPaper": { "kind": "RULED", "spacingPt": 24.0, "lineColor": "#1A000000" }
}
```

- `pages[]` is the document's page order and the bookkeeping the conflict-free merge needs
  ([sync-and-storage.md](sync-and-storage.md)): `sha256` is the digest of the page entry's *stored*
  bytes and `modified` its last-write time, so a merge can tell which side of a conflict moved. A
  page whose `entry` is absent from the archive is a corrupt file — fail the open, don't skip quietly.
- `defaultPaper` is what a *new* page starts on; each page carries its own ruling in
  `pages/page-N.json`, so a document may mix them (see [pages-and-paper.md](pages-and-paper.md)).
- Timestamps are Unix epoch milliseconds, UTC.
- Documents in an infinite mode use `chunks[]` (same shape, `entry` under `chunks/`) instead of
  `pages[]`; the two lists are mutually exclusive, and which one is valid follows `canvasMode`.

`canvas.json` — the geometry mode and its dimensions — is owned by
[infinite-canvas.md](infinite-canvas.md), which carries its schema.

---

## Dynamic entity model (living elements)

Every element is a JSON object with a `type` discriminator, a stable `id`, and an axis-aligned
`bounds` (`left`/`top`/`right`/`bottom`, pt, document space) that the renderer uses for culling,
hit-testing and selection without parsing the element:

```text
Element
├── stroke            → Stroke (pen/highlighter/eraser geometry, with stylus telemetry)
├── text              → TextElement (font, size, colour, box)
├── image             → ImageElement (asset reference + box)
├── texImage          → TexImageElement (LaTeX source + rendered asset)
├── table             → TableElement (living grid)
├── plot              → PlotElement (living formula)
├── parametricShape   → ParametricShapeElement (living geometry / circuit symbol)
└── raw               → RawJsonElement (an element type this build does not model)
```

- **`id` is what the merge keys on**, so it is minted once when the element is created (a UUID or a
  `<kind>_<random>` slug) and never re-minted by an edit, a copy or a page reorder. A `duplicate`
  is a *new* id; a move is the same id. It is never displayed and never reused within a document.
- **`bounds` is derived, not authored**: it is written from the geometry so a reader that does not
  model the element can still select and cull it, and it is recomputed (not trusted) whenever the
  geometry changes.
- Every element also carries the same `extraAttrs`-style preservation rule as the `.xopp` model:
  unknown fields of a known type are kept and re-emitted, so a newer minor version's additive field
  survives a round trip through an older build.

### 1. Dynamic table (`TableElement`)

A living, re-editable grid structure where cells contain rich multi-line text:

```json
{
  "type": "table",
  "id": "tbl_01",
  "bounds": { "left": 72.0, "top": 120.0, "right": 472.0, "bottom": 172.0 },
  "colWidths": [120.0, 180.0, 100.0],
  "rowHeights": [28.0, 24.0, 24.0],
  "headerRow": true,
  "borderColor": "#FF212121",
  "borderWidth": 1.0,
  "cells": [
    [
      { "text": "Parametro", "align": "CENTER", "bold": true, "fillColor": "#15000000" },
      { "text": "Formula", "align": "CENTER", "bold": true, "fillColor": "#15000000" },
      { "text": "Valore", "align": "CENTER", "bold": true, "fillColor": "#15000000" }
    ],
    [
      { "text": "Frequenza", "align": "LEFT", "bold": false, "fillColor": null },
      { "text": "f = 1 / T", "align": "CENTER", "bold": false, "fillColor": null },
      { "text": "50 Hz", "align": "RIGHT", "bold": false, "fillColor": null }
    ]
  ]
}
```

`colWidths`/`rowHeights` are the authority for the grid geometry — `bounds` is their sum, offset by
`left`/`top` — so a cell insert or a column drag is a change to those lists. `cells` is
`rowHeights.size` by `colWidths.size`; a short row is padded with empty cells rather than rejected.

**Interactive capabilities**: Tapping a dynamic table displays cell resize handles, column/row insertion
buttons, and cell inline text editing.

### 2. Parametric function plot (`PlotElement`)

Stores mathematical expressions, range bounds, and numerical analysis parameters. The fields mirror
what the math engine can actually compute ([math-engine.md](math-engine.md)), so a plot is re-opened
with every parameter the user set rather than a frozen picture:

```json
{
  "type": "plot",
  "id": "plt_01",
  "bounds": { "left": 100.0, "top": 200.0, "right": 400.0, "bottom": 380.0 },
  "xMin": -6.28,
  "xMax": 6.28,
  "samples": 300,
  "showCriticalPoints": true,
  "includeDerivative": false,
  "shadeIntegral": { "from": 0.0, "to": 3.14 },
  "curves": [
    {
      "kind": "CARTESIAN",
      "source": "sin(x) * exp(-0.1 * x)",
      "color": "#FFD32F2F",
      "strokeWidth": 1.5,
      "lineStyle": "SOLID"
    }
  ],
  "parameters": { "a": 1.0, "b": 2.0 },
  "axisColor": "#FF37474F",
  "summaryText": "Roots: x = {0.0, 3.14, -3.14}..."
}
```

- `curves[].kind` is `CARTESIAN` (`y = f(x)` over `xMin..xMax`), `PARAMETRIC` (then the curve also
  carries `tMin`/`tMax` and `source`/`sourceY`) or `POLAR` (`r(θ)` over `thetaMin`/`thetaMax`).
- `parameters` holds the named constants an expression may reference (`a`, `b`, `c`…); changing one
  re-evaluates the plot instead of editing the formula, which is what the interactive sliders move.
- `summaryText` is a **cache of what was computed** (roots, extrema, inflection points) written so a
  reader that will not run the math still shows the analysis; it is never the source of truth and is
  recomputed whenever the curve or range changes.

**Interactive capabilities**: Re-openable via the "Edit Plot" modal; dynamically adjusts upon formula
re-evaluation without re-drawing manually.

### 3. Parametric geometry shape (`ParametricShapeElement`)

Geometric constructs retaining mathematical control handles:

```json
{
  "type": "parametricShape",
  "id": "shp_01",
  "shape": "TRIANGLE_SCALENE",
  "bounds": { "left": 100.0, "top": 100.0, "right": 250.0, "bottom": 240.0 },
  "vertices": [
    { "x": 100.0, "y": 100.0 },
    { "x": 250.0, "y": 120.0 },
    { "x": 160.0, "y": 240.0 }
  ],
  "strokeColor": "#FF0D47A1",
  "strokeWidth": 2.0,
  "fillColor": "#1A2196F3",
  "showAngles": true,
  "showSideLengths": true
}
```

The `vertices` are the *authored* geometry and the handles the editor drags; a parametric kind whose
definition is an angle set (a scalene triangle or trapezoid, see [drawing-tools.md](drawing-tools.md))
keeps its exact angles by re-fitting the vertices on every drag, so the two never disagree on screen.

### 4. Circuit component (`CircuitElement`)

Active and passive electronics primitives with magnetic connection pins:

```json
{
  "type": "circuit",
  "id": "ckt_res_01",
  "symbol": "RESISTOR_IEEE",
  "value": "10 kΩ",
  "position": { "x": 300.0, "y": 150.0 },
  "rotation": 90.0,
  "pins": [
    { "id": "p1", "relX": -24.0, "relY": 0.0, "connectedTo": "net_node_A" },
    { "id": "p2", "relX": 24.0, "relY": 0.0, "connectedTo": "net_node_B" }
  ]
}
```

`pins` are the magnetic anchors (relative to `position`, before `rotation`); `connectedTo` names the
net a pin is snapped to, and is `null` for a floating pin. A circuit with no `pins` is a plain
symbol — the shape library's own drawings need no connectivity to be edited as a shape.

### 5. High-fidelity inking stroke (`Stroke`)

```json
{
  "type": "stroke",
  "id": "stk_01",
  "tool": "PEN",
  "color": "#FF1A237E",
  "lineStyle": "SOLID",
  "lineWidth": 1.4,
  "bounds": { "left": 102.1, "top": 204.9, "right": 103.9, "bottom": 207.0 },
  "points": [
    { "x": 102.4, "y": 205.1, "width": 1.2, "pressure": 0.55, "tilt": 14.2, "azimuth": 32.0, "dtMs": 0 },
    { "x": 103.8, "y": 206.8, "width": 1.4, "pressure": 0.62, "tilt": 14.8, "azimuth": 33.1, "dtMs": 6 }
  ]
}
```

`lineWidth` is the stroke's nominal width (what the `.xopp` bridge writes as the desktop `width`
attribute); each point's `width` is the *pressure-resolved* width actually drawn, so a document
renders identically without re-running the pressure filter. `tilt`/`azimuth` are degrees,
`dtMs` the delta from the previous point (its first point is 0) — the pair that makes replay,
velocity smoothing and audio sync possible ([audio-inking-sync.md](audio-inking-sync.md)).

### 6. Text, image and rendered-LaTeX elements

These three mirror the `.xopp` model one-to-one ([xopp-format.md](xopp-format.md)), so the bridge is
a field rename rather than a translation. All three carry `bounds` and `id` like every element.

```json
{ "type": "text", "id": "txt_01",
  "bounds": { "left": 72.0, "top": 90.0, "right": 300.0, "bottom": 130.0 },
  "font": "Sans Bold", "size": 12.0, "color": "#FF000000", "text": "E = mc^2" }

{ "type": "image", "id": "img_el_01",
  "bounds": { "left": 40.0, "top": 40.0, "right": 240.0, "bottom": 200.0 },
  "asset": "img_a1b2c3d4.webp" }

{ "type": "texImage", "id": "tex_01",
  "bounds": { "left": 100.0, "top": 300.0, "right": 280.0, "bottom": 330.0 },
  "latex": "\\int_0^1 x^2 dx", "color": "#FF000000",
  "asset": "img_e5f6g7h8.png" }
```

- `font` is the Pango-style description the `.xopp` stores (`format/FontDescription.kt`), kept
  verbatim so a round trip does not re-derive bold/italic from a family name.
- `asset` names an entry under `assets/` — **never an inline base64 blob**. The same picture pasted
  on ten pages is stored once and referenced ten times, which is the whole point of the container.
  A text element that is *only* a rendered formula is a `texImage`, not a `text`; the LaTeX source
  is kept so it can be re-typeset (see [latex-typst-rendering.md](latex-typst-rendering.md)).

---

## Backward and forward compatibility rules

1. **Unknown entity preservation**: a reader that encounters an element `type` it does not model
   keeps it as a `RawJsonElement` (its full JSON object, verbatim) and re-emits it unchanged on save.
   Unknown *archive entries* are preserved the same way (see container rule 4 above).
2. **Schema versioning**: `formatVersion` is incremented only for breaking structural changes. Minor
   features rely on additive fields with standard defaults, which an old reader preserves through
   rule 1 rather than understanding.
3. **`minAppVersion` is a write-time floor, and a reader enforces it**: it is the app version that
   must be present to write this file *safely* (whatever this document actually uses). A reader whose
   own version is lower than `minAppVersion` refuses to open the document normally and offers
   read-only opening, instead of silently dropping the entities it cannot keep. `formatVersion`
   answers "can I parse the shape of this?", `minAppVersion` answers "can I keep it intact?" — a
   higher `minAppVersion` with the same `formatVersion` is the case rule 1 alone would lose data on.
4. **Which version writes what is recorded here**, one line per release, so a support question
   ("why won't my tablet open this file?") has an answer in the repo:

   | App version | `formatVersion` written | `minAppVersion` written |
   |---|---|---|
   | 2.0.x | 1 | `2.0.0` |

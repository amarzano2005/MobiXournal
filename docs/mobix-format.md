# The `.mobix` native format and `.xopp` bridge

> Read when designing, reading, writing, or optimizing the native .mobix container, implementing the dual-format bridge with desktop Xournal++, or managing format-level performance optimizations.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: why evolve beyond `.xopp`

MobiXournal began as a strict companion to Xournal++ on Linux, storing everything in `.xopp`
(gzip-compressed XML, documented in [xopp-format.md](xopp-format.md)). While this achieves
interoperability, `.xopp` imposes hard architectural bottlenecks:

1. **Dead geometry**: Any complex object (function plots, geometric figures, circuit symbols, tables)
   must be flattened into raw strokes (`<stroke>`) or text boxes. The semantic intent is lost:
   a user cannot re-open a `.xopp` and adjust $f(x) = \sin(x)$ or edit a table cell.
2. **Missing stylus metadata**: `.xopp` has no schema fields for pen tilt, azimuth, barrel roll, or
   per-point timestamps. Advanced pen engines and velocity-based smoothing cannot persist their data.
3. **Monolithic I/O**: An XML file cannot be streamed or paged. Loading or saving a 500-page document
   requires decompressing and parsing the entire document tree in memory, which degrades mobile performance.
4. **Embedded binary bloat**: Images in `.xopp` are base64-encoded inside XML tags, inflating file size
   and requiring expensive string allocations.

The solution is a **two-tier architecture**: a modern native container format (`.mobix`) with an
uncompromising **lossless export/import bridge to `.xopp`**.

---

## What `.mobix` does beyond `.xopp`

| Capability | `.xopp` (Current) | `.mobix` (Native) |
|---|---|---|
| **Mathematical plots** | Baked into dead strokes & text boxes | Living `PlotElement`: formula $f(x)$, range, samples, re-editable anytime |
| **Data tables** | Static ink lines & independent text | Living `TableElement`: editable rows, cols, alignments, cell text |
| **Stylus telemetry** | $(x, y, \text{pressure})$ only | $(x, y, \text{pressure}, \text{tilt}, \text{azimuth}, \text{barrel roll}, \Delta t_{\text{ms}})$ |
| **Stroke replay & dynamics** | Impossible (no timing information) | Real-time replay & physics-driven velocity smoothing via $\Delta t$ timestamps |
| **Large notebooks (100+ pages)** | Monolithic XML; huge heap footprint & slow open | Paged container; instant open, random-access streaming via LRU cache |
| **Binary assets & photos** | Inefficient base64 inside XML tags | Raw WebP/PNG binaries in `assets/` with UUID content deduplication |
| **Audio annotations** | Sidecar `.wav` files prone to detachment | Bundled Opus/WAV in container, linked to stroke millisecond timestamps |
| **Document saving** | Entire document re-serialized | Incremental saves: writes only modified `pages/page-N.json` entries |

---

## The `.mobix` format specification

### Container layout

`.mobix` is an unencrypted, deflate-compressed ZIP container (`application/vnd.mobixournal+zip`):

```text
document.mobix (ZIP container)
├── mimetype                     # Stored uncompressed: "application/vnd.mobixournal+zip"
├── manifest.json                # Version, UUID, timestamps, page count, document settings
├── canvas.json                  # Spatial geometry mode & dimensions (see infinite-canvas.md)
├── pages/
│   ├── page-0001.json           # Per-page layers, elements, and paper rulings
│   ├── page-0002.json
│   └── ...
├── assets/                      # Binary assets referenced by hash / UUID
│   ├── img-a1b2c3d4.webp        # Embedded images (PNG, JPEG, WebP)
│   └── aud-e5f6g7h8.opus        # Audio sidecar recordings (Opus/WAV)
├── background/                  # Document-level or shared background PDF
│   └── background.pdf
└── xopp-bridge/                 # Optional synchronization metadata
    └── metadata.json
```

### Advantages of the container design

- **Streaming & fast startup**: The app reads `manifest.json` and loads only visible pages
  (`page-0001.json`). Navigating to page 200 does not parse pages 1–199.
- **Deduplication of binary assets**: If an image is pasted across multiple pages, it is stored once
  in `assets/` and referenced by UUID.
- **Atomic saves**: Changes to page 3 only require repacking `page-0003.json` inside the archive,
  saving battery and flash write cycles.

---

## Rich domain model (living entities)

In `.mobix` (formal schema documented in [mobix-schema-spec.md](mobix-schema-spec.md)), the `Element`
hierarchy treats high-level constructs as first-class citizens:

```text
Element
├── Stroke (with optional Tilt, Azimuth, DeltaTime per point)
├── TextElement
├── ImageElement
├── TexImageElement
├── PlotElement (parametric formula, bounds, samples, critical points, styles)
├── TableElement (rows, cols, cell contents, alignments, borders)
└── ParametricShapeElement (circle, rect, spline, circuit symbol, with editable control handles)
```

### High-fidelity stylus stroke
```json
{
  "type": "stroke",
  "tool": "pen",
  "color": "#FF1A237E",
  "lineStyle": "solid",
  "points": [
    { "x": 104.2, "y": 210.5, "width": 1.4, "pressure": 0.62, "tilt": 12.5, "azimuth": 45.0, "dtMs": 0 },
    { "x": 105.8, "y": 212.1, "width": 1.5, "pressure": 0.68, "tilt": 12.8, "azimuth": 46.2, "dtMs": 8 }
  ]
}
```

### Living mathematical plot (`PlotElement`)
```json
{
  "type": "plot",
  "curves": [
    { "formula": "sin(x) * cos(x/2)", "color": "#FFD32F2F", "lineStyle": "solid" }
  ],
  "bounds": { "xMin": -6.28, "xMax": 6.28, "leftPt": 100.0, "topPt": 150.0, "widthPt": 300.0, "heightPt": 180.0 },
  "showCriticalPoints": true,
  "includeDerivative": false,
  "summaryText": "Roots: x = {-3.14, 0.0, 3.14}..."
}
```
When selected in the editor, a `PlotElement` presents a **"Edit Formula"** button in its action bar,
reopening the math dialog with all parameters intact.

---

## Dual-format bridge (`.mobix` ↔ `.xopp`)

To maintain complete workflow parity with desktop Linux Xournal++, MobiXournal provides a bidirectional
bridge:

```text
┌─────────────────┐       Lossless Ingestion       ┌─────────────────┐
│   .xopp File    │ ─────────────────────────────> │  .mobix Model   │
│ (Desktop Linux) │ <───────────────────────────── │ (Living Models) │
└─────────────────┘       Synthesis Export         └─────────────────┘
```

1. **Import `.xopp` → `.mobix`**:
   - Standard strokes, texts, images, and backgrounds are ingested directly into the domain model.
   - If the `.xopp` contains MobiXournal metadata tags (see below), living elements are restored.
2. **Export `.mobix` → `.xopp` (Solution A: Zero-friction in-app export)**:
   - Available directly from MobiXournal's overflow menu / Save As dialog on both Android and Desktop.
   - `PlotElement` is compiled by `MultiFunctionPlotter` into standard `<stroke>` paths and `<text>` labels.
   - `TableElement` is compiled into border strokes and text elements.
   - Strokes with tilt/azimuth are written as regular strokes preserving their computed width.
   - **Lossless metadata tag**: The `.xopp` writer injects an application metadata comment or custom XML
     block:
     ```xml
     <!-- mobix:meta { "version": 1, "elements": [ ... ] } -->
     ```
     Desktop Xournal++ ignores XML comments, preserving normal viewing and editing on Linux. When reopened
     in MobiXournal, if the strokes were not altered, the living elements are revived.

### Opening `.mobix` directly in desktop Xournal++ (Solution C: Lua Plugin)

While desktop Xournal++ (C++/GTK3) does not natively know the `.mobix` file type, Xournal++ provides a
**Lua plugin API** (`~/.config/xournalpp/plugins/`).

MobiXournal will provide an official companion plugin (`xournalpp-mobix-plugin`):

```text
~/.config/xournalpp/plugins/mobix-importer/
├── plugin.ini                   # Declares plugin name, author, menu entry
└── main.lua                     # Unpacks .mobix, parses JSON, and injects pages into Xournal++
```

- **User workflow**: In desktop Xournal++, the user clicks **Plugin → Import MobiXournal document…**
  and selects the `.mobix` file.
- **Under the hood**: The Lua script unzips the archive into a temporary cache, translates `manifest.json`
  and `pages/*.json` into standard Xournal++ document elements using the Lua document API
  (or compiles to an internal `.xopp` and calls `app.openFile`), opening the document directly inside
  desktop Xournal++ without requiring manual pre-conversion.

---

## Engine optimizations enabled by the format

Migrating to the `.mobix` format directly enables four major performance optimizations in the engine:

1. **Page Virtualization & LRU Streaming (Instant open on huge notebooks)**:
   - Rather than loading a 200-page XML document into an in-memory DOM, the app initialises an
     active sliding window (e.g. current page $\pm 2$ adjacent pages).
   - Pages outside the viewport are evicted from RAM and re-read from the ZIP container in $< 5\text{ ms}$
     when scrolled into view.
2. **Incremental Delta-Writes (Zero save latency)**:
   - During auto-save or explicit save, the app only re-compresses modified page entries (`page-N.json`)
     into the ZIP archive. Unmodified pages, heavy background PDFs, and image assets are left untouched.
   - Saves complete in milliseconds without UI freezes or battery drain.
3. **Zero-Allocation Inking Loop (No GC stutter)**:
   - High-rate stylus events (120 Hz–240 Hz on modern tablets and stylus digitizers) can trigger frequent
     JVM Garbage Collection pauses if objects (`StrokePoint`, temporary event wrappers) are allocated per touch.
   - Point buffers are pre-allocated and pooled. The dynamic stroke is drawn directly from primitive arrays
     until touch-up commits the element.
4. **GPU Texture Layering & Path Tessellation Cache**:
   - Static elements (paper rulings, PDF page backgrounds, committed lower layers) are rendered once into
     an off-screen GPU texture (`android.graphics.Bitmap` hardware texture on Android, Skia `Image` on Desktop).
   - In-flight strokes are rendered solely on a lightweight transparent overlay, keeping per-frame redraw times
     under $4\text{ ms}$ (comfortably achieving 144 Hz / 240 Hz displays).
   - Vector paths for completed strokes are tessellated and cached in GPU memory; zooming and panning reuse
     GPU transformation matrices without re-rasterizing vector math.

# Native format (`.mobix`) and Desktop architecture

> Read when designing or implementing the native document format, extending the domain model beyond `.xopp`, bridging with desktop Xournal++, or porting MobiXournal to desktop via Compose Multiplatform.

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

## The `.mobix` format specification

### Container layout

`.mobix` is an unencrypted, deflate-compressed ZIP container (`application/vnd.mobixournal+zip`):

```text
document.mobix (ZIP container)
├── mimetype                     # Stored uncompressed: "application/vnd.mobixournal+zip"
├── manifest.json                # Version, UUID, timestamps, page count, document settings
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

In `.mobix`, the `Element` hierarchy is extended to treat high-level constructs as first-class citizens:

```text
Element
├── Stroke (with optional Tilt, Azimuth, DeltaTime per point)
├── TextElement
├── ImageElement
├── TexImageElement
├── PlotElement (parametric formula, bounds, samples, critical points, styles)
├── TableElement (rows, cols, cell contents, alignments, borders)
└── ShapeElement (circle, rect, spline, circuit symbol, with editable control handles)
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
2. **Export `.mobix` → `.xopp`**:
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

---

## Desktop architecture (Compose Multiplatform)

MobiXournal's current architecture (Kotlin + Jetpack Compose + `SurfaceView`) translates cleanly into
**Compose Multiplatform for Desktop** (running on JVM + Skia/Skiko).

### Target modularization

```text
MobiXournal
├── mobix-model/          # Pure Kotlin Multiplatform (KMP): Document, Page, Layer, Element, Bounds
├── mobix-format/         # KMP: .xopp parser/writer, .mobix ZIP/JSON serializer, PDF abstractions
├── mobix-math/           # KMP: Expression evaluator, spline math, shape generators, plotting
├── mobix-render/         # KMP with Skiko: Skia canvas rendering, selection bounds, dirty-rect logic
├── mobix-ui-common/      # KMP Compose: Toolbars, dialogs, color palette, settings UI
├── app-android/          # Android shell: MotionEvent, SurfaceView, SAF document picker, AudioRecord
└── app-desktop/          # Desktop shell: Window chrome, Wacom/Windows Ink/evdev stylus, menu bar
```

### Desktop performance vs Xournal++

| Metric / Aspect | Desktop Xournal++ (C++ / GTK3) | MobiXournal Desktop (Compose Desktop + Skia) |
|---|---|---|
| **Rendering engine** | Cairo (mostly CPU software rasterization) | Skia / Skiko (DirectX 12 on Windows, Metal on macOS, Vulkan on Linux) |
| **Refresh rates** | 60 Hz standard; high refresh can stutter on complex pages | Native 120 Hz / 144 Hz vsync-aligned hardware rendering |
| **Stylus input** | GTK3 GdkEvent with tablet sub-events | Direct Windows Ink / Linux `libinput` / Wacom native hooks |
| **UI extensibility** | GTK XML layout files + C++ callbacks | Declarative Compose Material 3 state-driven UI |
| **Cross-platform UI** | Linux-native; Windows build often has theme/input quirks | Consistent pixel-perfect UI across Linux, Windows, macOS |

### Performance pillars for Desktop

1. **Path tessellation caching**: In Skia, repeatedly creating paths each frame causes CPU overhead.
   Cache Skia `Path` objects per stroke; invalidate only when modified or transformed.
2. **Zero-allocation gesture loop**: During stylus drag, avoid allocating heap objects in the hot input
   path. Reuse coordinate buffers and dispatch directly to the render thread.
3. **Hardware-accelerated offscreen caching**: Pre-render static background layers (ruled paper, PDF)
   into Skia GPU `Image` textures. The live stylus stroke only redraws the active layer overlay.

---

## Phased implementation plan

1. **Phase 1: Domain model abstraction**:
   - Refactor `Stroke`, `PlotElement`, and `TableElement` to implement a unified `Element` hierarchy.
   - Separate `.xopp` XML writing into a compiler step from the abstract model.
2. **Phase 2: `.mobix` container reader & writer**:
   - Implement ZIP-based packaging with `manifest.json` and per-page streaming JSON.
   - Add "Save As `.mobix`" and "Export to `.xopp`" options to `MainActivity`.
3. **Phase 3: Module split (KMP)**:
   - Extract `mobix-model`, `mobix-format`, and `mobix-math` into Kotlin Multiplatform targets.
4. **Phase 4: Desktop prototype**:
   - Stand up a desktop `app-desktop` module using Compose Multiplatform with Skia canvas rendering.
   - Verify tablet stylus latency on Linux (evdev/Wacom) and Windows (Windows Ink).

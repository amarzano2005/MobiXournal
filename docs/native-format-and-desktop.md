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

### How the app is optimized accordingly (Android & Desktop)

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

---

## Graphics tablet optimization and hardware dial support

Desktop note-taking frequently pairs PC workstations with **screenless or display graphics tablets**
(Wacom Intuos/Cintiq, Huion, XP-Pen, Xencelabs). Unlike mobile touchscreens, graphics tablets rely on
external hardware controls: physical ExpressKeys, stylus barrel switches, and rotary **Touch Rings / Dial Wheels**.

### 1. Rotary dial / touch ring integration
Physical hardware dials (Wacom Touch Ring, Huion/XP-Pen dial wheel, Microsoft Surface Dial) map directly
to high-frequency analog events in the desktop shell:

- **Stepless hardware zoom**: Rotating the dial applies smooth, continuous logarithmic zoom centered at the
  pen cursor location without requiring multi-touch pinch gestures or keyboard modifiers.
- **Dynamic stroke width**: Adjusts active pen or highlighter thickness on-the-fly, accompanied by a subtle
  visual diameter ring preview around the cursor.
- **Radial Menu / Circular Palette (Pie Menu)**:
  - Pressing the dial center key or a pen barrel button summons an on-screen **Circular Palette** centered
    directly under the pen nib.
  - Rotating the dial spins the radial palette to quickly switch colors, pen tools, or eraser modes without
    moving the hand to the edge of a 4K desktop monitor.
- **Canvas rotation / Timeline scrubbing**: Rotates the page angle or scrubs through synchronized audio
  (see [audio-inking-sync.md](audio-inking-sync.md)).

### 2. Multi-protocol desktop tablet drivers
To avoid the driver inconsistencies common in desktop Linux and Windows applications, `app-desktop`
implements a low-latency input abstraction layer across three platform protocols:

- **Windows**: Dual-backend support for **Windows Ink (`WM_POINTER`)** and legacy **Wintab API**, ensuring
  full pressure, tilt, and dial compatibility across both consumer styluses and professional Wacom drivers.
- **Linux**: Direct integration with **libinput** and the Wayland tablet protocol (`zwp_tablet_manager_v2`),
  with fallback to X11 XInput2 tablet devices.
- **macOS**: Cocoa `NSEvent` tablet sub-types with native pressure and azimuth resolution.

### 3. Absolute mapping and multi-monitor aspect ratio lock
Screenless drawing tablets map tablet surface coordinates to desktop pixels. `app-desktop` provides:
- **Aspect ratio lock**: Automatically letterboxes the tablet's active area to match the monitor or window
  proportions, eliminating oval distortion when drawing circles.
- **Window-bounded tablet mapping**: Confines tablet strokes to the MobiXournal canvas while allowing the
  mouse cursor to roam freely across secondary monitors.

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

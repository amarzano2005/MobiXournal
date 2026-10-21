# Desktop workstation architecture and tablet hardware

> Read when implementing the desktop Compose Multiplatform application, Skia GPU rendering, graphics tablet input drivers (Wacom, Huion, XP-Pen), hardware rotary dials, or desktop window chrome.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Status today — the `desktop/` preview (Part live)

The first slice of this plan now builds: a **`desktop` Gradle module** (Compose Multiplatform 1.7 on
Skiko) that opens a gzip `.xopp` with the app's **own** `format` reader and paints its pages in a
native window. It is the feasibility proof this spoke was written against — the document model and
the codecs are platform-free, so a PC window can show the same page the Android editor shows.

What it does today:
- Opens a document from the toolbar or a path argument, lists every page, and zooms 25–200 %.
- Paints solid/ruled/graph/dotted paper (`config`'s `r1`, else `BackgroundGrid`'s defaults), pen
  strokes with their pressure taper, highlighter bands with multiply, dashed/dotted line styles, and
  text boxes.
- Consumes **`mobix-core`**, the platform-free module that holds the document model, the XML/JSON
  layers, the `.xopp` codecs and the shared paper/ink geometry — one codec, one ruling and one ink rule
  book rather than a fork. The native `.mobix` engine is still in `app` because its streaming API
  (paged reader, incremental save, merge) is internal to `io/`; the `TODO.toml` scope
  `extract-the-mobix-streaming-api-into-mobix-core` designs that API and moves it, and the desktop's
  `.mobix` opening waits on it.

What it does **not** do (all still governed by this spoke):
- No editing and no saving — the window is read-only.
- No `.mobix` and no self-contained ZIP `.xopp`; only gzip `.xopp`, and the other two are refused
  with an explicit message instead of a deep reader failure.
- No PDF or pixmap backgrounds, no images, no living entities (plots, tables, circuits, shapes), no
  layer visibility.
- No tablet/dial input, no dirty-rect GPU pacing, no installers. `run` is the only entry point;
  `nativeDistributions` is configured but not wired into a release job.

Verification, runnable on any JVM host (no emulator): `./gradlew :desktop:test` loads `udiff.xopp`
(6 pages, ~4 000 strokes) through `DocumentLoader`, renders page 1 headlessly via `ImageComposeScene`,
checks the painted pixels, and writes `desktop/build/desktop-render/page-1.png`.

**The execution plan lives in `TODO.toml`, not here**: the epic `step-3-desktop-workstation` carries the
phase order and the done criteria, and its children are the atomic tasks (orders 910–1030) and the
three phase scopes still to be broken down. Two decisions were taken on 2026-10-21 and recorded in
`docs/ui-modernization.md` (the owning spoke for desktop layout and materials): the chrome is the app's
own glass on a true-black desk rather than Mica/Vibrancy, and **both** tool placements ship — vertical
rail by default, horizontal ribbon as the alternative — switched in settings.

---

## Motivation: scaling from mobile stylus to PC workstation

MobiXournal Desktop is a high-performance cross-platform application for Linux, Windows, and macOS,
designed to surpass legacy Xournal++ in both responsiveness and hardware integration. While mobile
tablets rely on direct touchscreens, desktop workstations operate through **graphics tablets (Wacom,
Huion, XP-Pen, Xencelabs)**, physical ExpressKeys, rotary **Touch Rings / Dial Wheels**, and simultaneous
keyboard input.

The desktop workstation UI layout, docking tool strips, bimanual keyboard shortcuts, and radial menus
are documented in [ui-modernization.md](ui-modernization.md).

---

## Architecture & target modularization

MobiXournal adopts **Kotlin Multiplatform (KMP)** and **Compose Multiplatform** backed by **Skia/Skiko**:

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

---

## Desktop performance vs Xournal++

| Metric / Aspect | Desktop Xournal++ (C++ / GTK3) | MobiXournal Desktop (Compose Desktop + Skia) |
|---|---|---|
| **Rendering engine** | Cairo (mostly CPU software rasterization) | Skia / Skiko (DirectX 12 on Windows, Metal on macOS, Vulkan on Linux) |
| **Refresh rates** | 60 Hz standard; high refresh can stutter on complex pages | Native 120 Hz / 144 Hz vsync-aligned hardware rendering |
| **Stylus input** | GTK3 GdkEvent with tablet sub-events | Direct Windows Ink / Linux `libinput` / Wacom native hooks |
| **UI extensibility** | GTK XML layout files + C++ callbacks | Declarative Compose Material 3 state-driven UI |
| **Cross-platform UI** | Linux-native; Windows build often has theme/input quirks | Consistent pixel-perfect UI across Linux, Windows, macOS |

---

## Graphics tablet optimization and hardware dial support

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
To avoid driver inconsistencies common in desktop Linux and Windows applications, `app-desktop`
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
   - Implement ZIP-based packaging with `manifest.json` and per-page streaming JSON
     (see [mobix-format.md](mobix-format.md)).
3. **Phase 3: Module split (KMP)**:
   - Extract `mobix-model`, `mobix-format`, and `mobix-math` into Kotlin Multiplatform targets.
4. **Phase 4: Desktop prototype** (started — see *Status today*):
   - Stand up a desktop `app-desktop` module using Compose Multiplatform with Skia canvas rendering.
     The `desktop/` preview does this for read-only `.xopp` pages; the editor waits for Phase 3, since
     the Android `render`, `ui`, `tabs` and `io` packages are still Android-bound.
   - Verify tablet stylus latency on Linux (evdev/Wacom) and Windows (Windows Ink).

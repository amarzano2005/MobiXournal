# Desktop workstation architecture and tablet hardware

> Read when implementing the desktop Compose Multiplatform application, Skia GPU rendering, graphics tablet input drivers (Wacom, Huion, XP-Pen), hardware rotary dials, or desktop window chrome.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Status today — the `desktop/` preview (Part live)

The first slice of this plan now builds: a **`desktop` Gradle module** (Compose Multiplatform 1.7 on
Skiko) that opens the app's own documents and paints their pages in a native window. It is the
feasibility proof this spoke was written against — the document model and the codecs are
platform-free, so a PC window can show the same page the Android editor shows.

What it does today:
- Opens **every container the app writes** — gzip `.xopp`, self-contained ZIP `.xopp` and native
  `.mobix` — from the toolbar or a path argument, lists its pages, and zooms 25–200 %. The container is
  chosen by the file's **bytes** (`FileKind` plus a ZIP's `mimetype` entry), never by the extension, so a
  renamed notebook still opens as a notebook.
- **Navigates like a workstation, not like a phone**: a header carrying one tab per open document (each
tab keeps its own page, zoom and fit, so going back to a tab lands where you left it), a collapsible
240 dp sidebar of page thumbnails and the current page's layers, the page alone on the true-black desk,
and a 24 dp status bar with the page counter, the effective zoom, the fit mode and the container.
Wheel scrolls, `Ctrl` + wheel zooms **at the cursor**, middle-drag or `Space` + drag pans, and every one
of those actions also has a key (`docs/ui-modernization.md` owns the table). Thumbnails come from the
same `PageView` that paints the page — a preview cannot disagree with what it previews — and are cached
once per page per tab.
- Shows a `.mobix` **page by page**: the list is lazy and pages are inflated only as they scroll into
  view, so a notebook opens without reading it. A pressed-for stroke count is therefore not shown for
  those documents — the header says "pagine lette su richiesta" instead of lying.
- Paints solid/ruled/graph/dotted paper (`config`'s `r1`, else `BackgroundGrid`'s defaults), pen
  strokes with their pressure taper, highlighter bands with multiply, dashed/dotted line styles, and
  text boxes.
- **Paints living entities** — tables, function plots, parametric shapes and circuit components — by
  running the same compiler the Android canvas and every export use (`LivingInk`, now platform-free in
  `mobix-core`), then drawing the ink it produces. A table on the PC is the table on the tablet, not a
  second renderer's opinion of it; an entity this build has no drawing for is reported, not drawn blank.
- **Paints page pictures**: a pixmap background from the file the page names, and an annotated **PDF
  page** rasterised with PDFBox (about 144 dpi) and placed under the ink, so a highlighter still
  multiplies into it. A `.mobix`'s bundled bytes are preferred over the original reference, which is
  what keeps a shared notebook looking right after the picture's file has moved away.
- Consumes **`mobix-core`**, the platform-free module that holds the document model, the XML/JSON
  layers, the `.xopp` and `.mobix` codecs, and the shared paper/ink geometry — one codec, one ruling and
  one ink rule book rather than a fork.
- The `.mobix` engine now lives in `mobix-core` too, behind a published streaming contract
  ([`mobix-streaming-api.md`](mobix-streaming-api.md)): the paged reader, the asset sources, the page
  and element codecs, the incremental save and the merge are callable from this module, and
  `DesktopMobixOpenTest` opens a 40-page notebook through them without the Android app on the
  classpath. The window opens those documents through the same reader.

### Opening, and what a failed open says

`DocumentLoader` picks a file's family by its bytes and its `mimetype` entry, and `describeOpenFailure`
decides what the window says when the open is refused. Three cases, three sentences: an
`UnsupportedOperationException` is the loader's own refusal and keeps its words (`Formato non
supportato`); an `OutOfMemoryError` is reported as memory, because a page taller than the heap is not a
corrupt notebook; and a **`LinkageError`** is reported as *the window being stale*, not the file being
broken. That last case is real — a run produced `NoClassDefFoundError:
com/mobixournal/desktop/DocumentLoader$openMobix$1`, and the notebook was untouched. It is a fact of how
the preview is launched: `:desktop:run` executes `build/classes`, so a Gradle build during a session
rewrites the class files under the live JVM and any synthetic class the JVM had not loaded yet is gone.
Closing the window and relaunching is the fix; the same file opens in ~40 ms in a fresh process.

A failed open **never takes the document on screen with it**: the report is a card at the top of the
canvas, dismissed with a click or `Esc`, while the page, the tab and the sidebar stay exactly as they
were. The card's fill is opaque rather than the floating family's frosted 0.88, because it straddles the
edge where the desk meets the sheet — at 0.88 the part over the white page came out a tone lighter than
the part over the black desk, and the panel read as two materials split down its middle.

`--args` splits its value on spaces, so a notebook kept under `Il mio Drive/…` arrives as several
arguments and `File(args[0])` does not exist: `initialDocument` puts the pieces back together when the
first one is not itself a file, so a path with spaces works from the command line as it does from the
picker.

What it does **not** do (all still governed by this spoke):
- No editing and no saving — the window is read-only.
- No sketch/PDF/text import, no **images as elements** (only as page backgrounds), no isometric paper,
  no layer visibility toggles, and no editing of a living entity — the ink is drawn, not yet made
  editable.
- No tool rail or ribbon, no right-side property inspector, no search, and no page reordering or
  insertion.
- No tablet/dial input, no dirty-rect GPU pacing, no installers. `run` is the only entry point;
  `nativeDistributions` is configured but not wired into a release job.

Verification, runnable on any JVM host (no emulator): `./gradlew :desktop:test` loads `udiff.xopp`
(6 pages, ~4 000 strokes) through `DocumentLoader`, opens a ZIP `.xopp` and a 40-page `.mobix` (including
both **misnamed** — `.mobix` content in a `.xopp` file and vice versa), refuses a text file and a ZIP with
no document inside by name, renders page 1 of each headlessly via `ImageComposeScene`, and renders a
table, a parametric shape, a bundled pixmap and a PDF background — asserting the pixels, not just that
the call returned (`LivingAndBackgroundTest`). `DesktopShellTest` renders the tab strip **before and
after** a document is opened and asserts the frames differ (the regression that caught a strip which
never redrew), renders the sidebar, and checks the keyboard's fit and zoom arithmetic. `OpenFailureTest`
pins what a failed open says in each of its three cases and how a space-split command line is put back
together. Every render is
written under `desktop/build/desktop-render/` so it can be looked at.

**The execution plan lives in `TODO.toml`, not here**: the epic `step-3-desktop-workstation` carries the
phase order and the done criteria, and its children are the atomic tasks (orders 910–1030) and the
three phase scopes still to be broken down. Three decisions were taken on 2026-10-21 and recorded in
`docs/ui-modernization.md` (the owning spoke for desktop layout and materials): the chrome is the app's
own glass on a true-black desk rather than Mica/Vibrancy, **both** tool placements ship — vertical rail
by default, horizontal ribbon as the alternative — switched in settings, and the desktop's tab/view
model is Compose **snapshot state**, so the chrome is redrawn by the state it reads instead of by a
second copy kept in sync by hand.

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
2. **Phase 2: `.mobix` container reader & writer** — **reader live in the preview**:
   - The engine (ZIP packaging, `manifest.json`, per-page streaming JSON) is published from
     `mobix-core` ([mobix-streaming-api.md](mobix-streaming-api.md)) and the preview opens `.mobix`
     through it page by page. Writing from the desktop waits for the editor.
3. **Phase 3: Module split (KMP)**:
   - Extract `mobix-model`, `mobix-format`, and `mobix-math` into Kotlin Multiplatform targets.
4. **Phase 4: Desktop prototype** (started — see *Status today*):
   - Stand up a desktop `app-desktop` module using Compose Multiplatform with Skia canvas rendering.
     The `desktop/` preview does this for read-only `.xopp` and `.mobix` pages; the editor waits for Phase 3, since
     the Android `render`, `ui`, `tabs` and `io` packages are still Android-bound.
   - Verify tablet stylus latency on Linux (evdev/Wacom) and Windows (Windows Ink).

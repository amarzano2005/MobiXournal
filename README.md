<div align="center">
  <img src="docs/assets/logo.png" alt="MobiXournal Logo" width="140" height="140" />

  # MobiXournal
  **A stylus-first Xournal++ (`.xopp`) editor & STEM companion for Android — with full graphics tablet support & ExpressKey shortcuts**

  [![Android CI](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml/badge.svg)](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml)
  [![License: GPL v2+](https://img.shields.io/badge/License-GPL%20v2%2B-blue.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://android.com)
  [![Format: .xopp](https://img.shields.io/badge/Format-.xopp%20(100%25%20roundtrip)-orange)](https://github.com/xournalpp/xournalpp)
  [![Stylus & Tablet](https://img.shields.io/badge/Stylus-S--Pen%20%7C%20Active%20Pen%20%7C%20Tablet-blueviolet)]()

  <br />

  <img src="docs/assets/preview.jpg" alt="MobiXournal Tablet UI Preview" width="850" />
</div>

**MobiXournal** is an open-source Android app designed for handwritten notes, sketching, and technical documentation using the native [Xournal++](https://github.com/xournalpp/xournalpp) (`.xopp`) format, with first-class support for active styluses, graphics tablets (Wacom, Huion, XP-Pen), and configurable hardware shortcuts.

The primary goal is **100% format fidelity and round-trip safety**: files edited on Android reopen identically in desktop Xournal++ on Linux, macOS, or Windows without losing strokes, layers, backgrounds, or metadata.

In addition to core note-taking, MobiXournal features a specialized **STEM toolset** (electronic circuits, IEEE logic gates, relational schema tables, coordinate systems, parametric splines, virtual drafting instruments, and on-device LaTeX rendering) saved as standard vector strokes.

> **Credits & License**:
> - **Xournal++**: Format handling, palette, and shape recognition are derived from or matched to [Xournal++](https://github.com/xournalpp/xournalpp) (GPL-2.0-or-later).
> - **NeXopp**: MobiXournal is a continuation and rebranding of [NeXopp](https://github.com/bamonroe/NeXopp) by Brian Monroe (`bamonroe`).
> - This project is independent and unofficial, distributed under the **GPL-2.0-or-later** license. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

---

## 📚 Documentation Map

MobiXournal follows a hub-and-spoke documentation model:

| Topic | Document |
|---|---|
| **Development & Conventions** | [`AGENTS.md`](AGENTS.md) |
| **System Architecture** (the index of the spokes) | [`docs/architecture.md`](docs/architecture.md) |
| **The `.xopp` on-disk schema** | [`docs/xopp-format.md`](docs/xopp-format.md) |
| **Build Tools, Container & Emulator** | [`docs/tools.md`](docs/tools.md) |
| **Active Tasks & Roadmap** | [`TODO.toml`](TODO.toml) (via `scripts/todo.sh list`) |
| **Completed Work Archive** | [`FINISHED.toml`](FINISHED.toml) |
| **Release Notes (per version)** | [`docs/releases/`](docs/releases/) |

---

## 🛠️ Build & Requirements

### Requirements
- **Docker** (all builds run in a containerized Android toolchain; no local JDK, SDK, or Gradle needed).
- KVM support (optional, for headless emulator testing).

Without Docker (or on a machine with no `/data/android` toolchain) the same build runs on the host's
Gradle wrapper, which needs a **JDK the wrapper supports** (17–24 for the version in the tree — a
JDK 25 host fails before compiling anything) and an Android SDK at `local.properties`' `sdk.dir`.
`scripts/host-build.sh` finds both for you (and reports them with `--list`) instead of leaving it to
the wrapper's own confusing failure. See
[Host fallback](docs/tools.md#host-fallback--no-dataandroid-no-docker) in `docs/tools.md`.

### Build Commands
```sh
# Full build: unit tests + debug APK
scripts/build.sh

# Run JVM unit tests only (instant, no device needed)
scripts/build.sh testDebugUnitTest

# Clean release-path build of the debug APK
scripts/build.sh clean assembleDebug
```

On a host with no `/data/android` and no Docker, `scripts/host-build.sh` is the same loop with the
JDK/SDK discovery done for you (`--list` reports what it picked, `--jdk`/`--sdk` override it):

```sh
# Host fallback: finds a supported JDK + the SDK, then runs the wrapper
scripts/host-build.sh

# Any Gradle task passes through — including the instrumented suite, on the host's own adb
scripts/host-build.sh connectedDebugAndroidTest
```
For emulator installation, deployment, and testing harnesses, refer to [`docs/tools.md`](docs/tools.md). Cutting a release is documented there too, and it is a command rather than a chore: `scripts/todo.sh release --version <version> --bump-gradle` stamps everything finished since the last release and drafts `docs/releases/v<version>.md` from it, then you curate that draft and push the `v<version>` tag so CI builds the APK and publishes the Release.

---

## 📖 User Guide

### 📂 Workspace & Documents

- **Multi-Tab Interface**: Work with multiple documents simultaneously. Each tab preserves its own viewport, zoom, page position, and history. Tabs can be dragged to reorder, and in split view each half shows its own tab strip — with its own **+** and a numbered badge marking the half the tools are acting on — instead of one strip that switches when you touch the other pane.
- **Split View & Live Mirroring**:
  - **Split View**: View and edit two different documents side by side with an adjustable divider that follows your finger exactly and re-fits each half to its width as you drag it; its floating grip lights up while you hold it, and a double-tap recenters it at 50/50. Because a page is fitted to its half's width, dragging the divider re-fits it, but opening or closing the split keeps the page the same size on screen: the zoom is compensated for the narrower half (the badge shows that compensated value), so a page never jumps bigger or smaller when you toggle the split. Each side keeps its own tabs, viewport and zoom, with its own undo history while the split is open, and each half's tabs stay visible in a strip over that half. To hand a document to the other side, long-press its tab: the menu names the destination ("Move to pane 2", or "Move to split view" when the split is closed) and can also open a live mirror there instead. The split workspace — whether it is open, and which half has focus — comes back if Android restarts the app in the background.
  - **Mirrored View**: Open a second live view of the *same* document (e.g. view a reference diagram at the top while writing notes at the bottom). Changes sync immediately.
- **Tab Overview**: Tap the grid icon in the top bar to inspect all open tabs as thumbnail previews.
- **Session Restore**: Open tabs and unsaved drafts are cached locally and restored automatically on next launch.
- **Universal File Import**:
  - **`.xopp` / `.xopp.gz`**: Standard Xournal++ documents (both gzip and zipped single-file archives).
  - **`.mobix`**: MobiXournal's own documents. A `.mobix` and a zipped `.xopp` are both ZIP archives, so the file is recognised by the type it declares inside, never by its name — and it reopens in the format it was saved in.
  - **PDF Documents**: Open or import any PDF for annotation. Choose **Replace** or **Append** (appends merge seamlessly into a single document). Pages are drawn on screen through the raster cache and its tiles — smooth to pan, pinch and draw on, and still sharp at any zoom because the visible tiles are rasterised at the true on-screen resolution.
  - **Plain Text & Markdown (`.txt`, `.md`)**: Automatically typeset into annotatable pages with rich headings, monospace code blocks, and lists. Text remains selectable and copyable.
  - **Images (`PNG`, `JPEG`, `WebP`)**: Opened as annotatable pages with the image as the background.
  - **System Integration**: MobiXournal registers as a document handler for `.xopp`, `.mobix`, PDF, and images in the Android "Open with" / "Share" menu. Remote shares (WebDAV, Nextcloud, SSHFS) are supported via Storage Access Framework.

### 🖊️ Stylus & Drawing Experience

- **Stylus-First Input**:
  - **Palm Rejection**: Capacitive fingers only pan/zoom while the pen writes (finger drawing can be toggled on in settings).
  - **Pressure Sensitivity, Multiplier & Presets**: Dynamic line width matching desktop Xournal++ tapering curves. The two knobs are the desktop's own filter — **Minimum pressure** (the floor, 0.01 → 1.00) and **Pressure multiplier** (0.5 → 4.00×, so a light hand can thicken a stroke well past its size) — with customizable presets and editable names, adjustable on the fly via the top-bar overflow menu ("Pen parameters…") or in Settings → Stylus. With **Pressure sensitivity** switched off both controls are shown disabled with the reason, instead of silently doing nothing. **Pen diagnostics** prints the live filter and the raw pressure the tablet reports, so a value that never reaches the pen is visible rather than guessed at.
  - **Hover Preview**: S-Pen / Active Pen hover ring indicates exact tip contact point.
  - **Hold to Snap**: rest the tip on the glass for half a second and the stroke you are drawing turns into clean geometry **before you lift** — a straight line, an arc, a triangle, a rectangle. Nothing is committed by the pause: keep drawing and the recogniser simply runs again over the longer stroke, so a wrong guess costs nothing. The hold uses the same recogniser as **Shape recognition** in Settings → Drawing, and is off with it.
  - **Hardware Barrel Buttons**: Hold barrel button to erase or lasso select (supported on Android 14+ stylus buttons, Bluetooth pens, and mouse right-clicks). Double-click to undo or toggle tools.
- **Pen & Realistic Highlighter**:
  - **Pen**: Smooth vector strokes with digitizer wobble filtering and pressure dynamics.
  - **Highlighter**: Realistic multiply blending (~47% opacity) preserving ink visibility underneath. Standard desktop tip sizes (1 mm, 3 mm, 7 mm).
- **Precision Eraser**:
  - **Partial Eraser**: Splits strokes at contact boundaries.
  - **Whole-Stroke Eraser**: Deletes entire strokes touched by the tip.
  - Tip size adjusts with width slots; visual contact radius ring follows the cursor.
- **Color Palette & Stroke Width**:
  - 8 standard desktop Xournal++ colors in a fixed order — black, red, green, blue, orange, yellow, magenta, white — plus a custom HSV/hex picker.
  - **The custom slot starts empty**, not pre-filled with a colour nobody chose: it is drawn as an outline until you set one, then shows and offers that colour everywhere. The picker also offers **the colours already in the app** as a tap-to-start-from row, so a new colour can begin from an existing one instead of from a hex you would have to know.
  - **New colours from the canvas**: the Colour & size pop-up's swatch row ends with an **add-colour** swatch, which appends to the palette (and selects the new colour). The row scrolls rather than wraps, so however many colours you add the pop-up keeps the same height.
  - 3 customizable width slots per tool (`S` / `M` / `L`).
  - Tools remember their own active color and stroke width independently.
  - Line styles: **Solid**, **Dashed**, **Dash-dot**, **Dotted**.
- **Layers**:
  - Full layer stack per page: add, reorder, rename, hide/show, merge down, and move selections between layers.

### 🔬 STEM & Technical Tools

MobiXournal includes a dedicated technical toolset engineered for science, engineering, and mathematics note-taking:

- **Electronic Circuits (Passive & Active)**:
  - **Passive Circuits**: Drag to place and orient passive components: **Resistors** (zigzag), **Capacitors** (parallel plates), **Inductors** (multi-loop coils), and **Earth Ground**.
  - **Active Circuits**: Full set of semiconductor and active components: **Diodes** (solid p-n junction triangle with cathode bar), **LEDs** (same solid body plus two detached light-emitting rays), **Zener Diodes** (solid body, cathode bar bent into Z-wings), **Operational Amplifiers (Op-Amps)** (triangle body, `-`/`+` input signs clear of the pins, and output pin), **BJT NPN** and **BJT PNP Transistors** (Base, Collector, Emitter with directional arrows, base bar inside a circular envelope), **DC Voltage Sources / Batteries** (open-gap parallel plates), and **Current Sources** (circle with internal direction arrow).
- **IEEE Logic Gates**:
  - Full digital logic symbol library: **AND**, **NAND**, **OR**, **NOR**, **XOR**, **XNOR**, **NOT (Inverter)** and a **Buffer**, with standard input/output terminals and inversion bubbles.
- **Chemical Formulas**:
  - **Bonds**: drag out a **Single**, **Double** or **Triple bond** — one, two or three parallel lines along the drag, spaced off the pen's own width. Skeletal carbon chains are drawn as ordinary pen strokes; the bonds are ordinary strokes too, so they round-trip through the `.xopp` like anything else.
  - **Benzene ring**: drag out an **aromatic ring** — a regular hexagon with a vertex at the top and the inner aromatic circle, sized to the drag.
- **Dimensioning & Measuring Arrows**:
  - Drag a **dimension line** — a double-ended arrow whose gap is filled in for you: on release the measured length is written into the gap as a text box (**millimetres**, to a tenth), so the drawing carries its own numbers. The value is ordinary text, so it can be moved, restyled or edited like any other text box.
- **Switching & Magnetics**:
  - **Open** and **Closed Switches**, a **Transformer** (two coupled windings with core bars) and a **Junction** dot, alongside the passive components above.
- **Relational Database Tables**:
  - Interactive grid tables with dynamic row and column counters. The row counter counts **data** rows only; the optional header stacks one extra row above them instead of consuming a data row.
  - Optional **Relational Header** format (double line dividing attribute columns from data rows).
  - Drag to size or 1-tap center on viewport.
- **LaTeX Math Formulae**:
  - On-device formula editor with live preview.
  - Curated STEM symbol palette for fast symbol insertion: Calculus integrals/differentials, Greek letters, algebraic operators, and physics shortcuts.
- **2D Function Plotter**:
  - **Insert graph** — the chart button in the Secondary Toolbar (second from the end): type `f(x)` (sin, cos, tan, inverse and hyperbolic trig, sqrt, abs, ln, log, exp, floor/ceil/round, `pi`/`e`, `+ - * / ^`, parentheses) with the x range to plot it over, and the plot is laid on the page **where you are looking** — centred on the middle of the viewport, then pulled back inside the sheet so the frame is never left half off the page.
  - Two tabs: **Cartesian**, for one or more `y = f(x)` curves, and **Parametric**, for a single `{x(t), y(t)}` pair (`cos(x)` and `sin(x)` make a circle) swept over the same range — a parametric pair is drawn and saved as the one curve it is, not as two separate waves. Both previews are not pictures *of* the plot: they **are** the plot, compiled by the same code the page uses, so what the preview shows is what Insert places.
  - The dialog checks the formula as you type — the same parser the plotter uses, and for a parametric pair both of its expressions — so it never accepts something it can't draw.
  - **Shade integral area**: in the graph dialog, switch it on and type the two bounds you want the area under the curve measured between (they start as the plot's own x range). The area is filled on the page, and the analysis summary reports the definite integral **over that area** — switch it off and both go back to the plot's range. The bounds are part of the graph, so they survive a save, an edit and an undo like anything else.
  - The plot is inserted as a **living graph** rather than a picture of one: a frame, the axes with a tick and a number on every nice step, and the curve, broken wherever the function is undefined or leaps (a pole stays a gap, not a line drawn across it). In a `.mobix` the file keeps the formula, its range, display flags, and named parameters. A living graph can be selected, moved, resized, re-opened in the graph dialog, and manipulated via **interactive parameter sliders** anchored below the frame: dragging a parameter slider dynamically re-evaluates the curve and the calculus analysis in real-time and commits as an undoable edit on release. Saving or exporting to `.xopp` flattens the graph into vector strokes so desktop Xournal++ opens it identically.
- **Geometry & Vectors**:
  - **Geometric Polygons & Shapes**: Squares, rhombuses, trapezoids, pentagons, hexagons, ellipses/circles, and rectangles.
  - **Triangles with Variants & Custom Angles**: Submenu with 4 geometric kinds: **Equilateral** (default, equal sides/angles), **Right-angled**, **Isosceles**, and **Scalene** with fully customizable interior angles (A, B, C; sum = 180°) and live preview. Each kind is drawn as its own outline beside its name in the submenu, so the four triangles are told apart by shape as well as by label.
  - **Trapezoid with Variants & Custom Angles**: Submenu with 3 kinds: **Isosceles** (default, shorter base centred over the longer one), **Right-angled** (one leg perpendicular to the bases), and **Scalene** with its two base angles customizable — unequal angles give unequal legs, i.e. four different sides — with live preview. As with the triangles, each kind carries a small outline of the shape it draws beside its name. The figure is always fitted inside its drag, and its angles are what is kept exactly.
  - **Cartesian Coordinate Axes**: Instant oriented X/Y coordinate systems.
  - **Vectors**: Single and double-ended arrows for force diagrams and dimensioning.
  - **Parametric Splines**: Multi-point smooth curves with interactive tangent handles.
- **Virtual Drafting Instruments**:
  - **Setsquare (30°/60°/90°)**: Movable drafting triangle with edge snapping for straight lines.
  - **Compass**: Circular guide with adjustable radius for arcs and circles.
  - **Protractor**: 180° scale with 5°/15° tick marks, straight diameter ruler, rim arc snapping, and 15° radial snapping.
  - **Ruling**: no instrument at all — the page's **own ruling is the guide**, and every drawn vertex (freehand ink and shapes alike) is pulled onto the lines the paper really draws, spacing and margin included. A hand-drawn line comes out along a rule, a rectangle's corners land on the grid, and on an **isometric** sheet every vertex lands on a **node of the ±30° triangular mesh** — the same crossings the paper itself draws, so ink traces the mesh exactly. Nothing to place or hold, and nothing on plain or PDF/image paper, which rule no lattice to snap onto.
  - **Remembers where you left it**: the placed instrument's **pose** — its anchor, aim and size — is kept with your settings, so a setsquare comes back exactly where it was left after a restart instead of being dropped fresh at the middle of the viewport. The pose is dropped when you change the guide kind, since it belongs to the instrument it was taken from (the invisible rules have no pose to keep).
  - **Chemistry**: a **60°/120° bond lattice** — rows of nodes one bond length apart, each row offset half a bond, so a node's six nearest neighbours are a regular hexagon. Every drawn vertex (freehand ink and shapes alike) is pulled onto the nearest node, so a sketched **skeletal formula** comes out with exact bond angles and equal bond lengths and a ring drawn around a node is a proper benzene hexagon. The bond length is the guide's own (default 40 pt), independent of the page's background, so it works on any sheet — plain, PDF/image or ruled. Like Ruling it is an invisible rule: nothing to hold, and the guide draws only a faint mesh of the bonds it will snap onto.
- **Shape Recognition & Snapping**:
  - **Ported Recognizer**: Desktop Xournal++ algorithm snaps freehand strokes into lines, rectangles, triangles, and circles.
  - **Snap to Grid**: Snaps a shape's **endpoints** to graph, dotted, ruled or **isometric** paper rulings — the same lattice the **Ruling** guide pulls *every* vertex of any stroke onto (an isometric endpoint lands on a mesh node, both coordinates resolved together).
  - **Snap Rotation**: 15° stepping for object rotation and drafting tools.

### 🛠️ Editing & Canvas Tools

- **Selection & Manipulation**:
  - **Rectangle & Lasso Selection**: Select active-layer objects.
  - **Transformations**: Move (with auto-scrolling at screen edges), resize — **proportional from the corner handles and single-axis from the edge handles**, so a drawing can be stretched wider or taller out of proportion — and stroke rotation. **Resizing stretches the figure, not its ink**: a longer arrow, line or Cartesian diagram keeps the line and head thickness it was drawn with, so making a figure bigger never fattens it.
  - **A precise outline**: the dashed box is drawn **exactly on the selected elements' ink**, with no padding, so a selection shows what it actually holds; the ease of grabbing a handle comes from the hit radii, not from drawing the box bigger than the element. The hit zones shrink with the selection, so the **middle of even a tiny element drags it** instead of being swallowed by its own handles, while the handles stay grabbable in their own corners and edges.
  - **Edge Auto-Scroll**: Dragging either a move or the rectangle/lasso marquee into the top or bottom edge scrolls the page vertically, so a selection can reach past the viewport (matching desktop Xournal++).
  - **Action Bar**: Cut, Copy, **Paste**, Duplicate, Recolor, Change line weight, Align & distribute, and Delete — all in the one bar that **floats right under the selection** (lifting above it only when the sheet ends below the selection) instead of sitting at the bottom of the screen, so the actions stay by the hand holding the stylus. With **nothing** selected, the same bar stands in at the bottom centre carrying **Paste** alone: paste is therefore available while something *is* selected (so a second copy needs no deselecting first) and the button always sits with the actions it belongs to, rather than a lone pill that vanished on the first tap. The background-select **region** actions (Copy/Cut a marquee region) keep the bottom edge in their own bar. It is compact — 32dp buttons and no Done button, since tapping off the selection already clears it — so it covers about half the canvas it used to.
  - **Align & Distribute**: with two or more elements selected, line them up on either edge or either centre, or spread them evenly — horizontally or vertically. Each action is one undoable step, and the action bar's alignment menu shows which tools are available (alignment needs two elements, distribution three).
  - **Select Background (Flatten)**: Marquee-select a region to copy or cut a flattened raster image including all layers and page background.
- **PDF Text Selection**: Drag across vector PDF text to highlight and copy text to the system clipboard (no OCR needed).
- **PDF Contents (Table of Contents)**: When an annotated PDF carries an outline, the **Pages** menu offers **Contents…** — the PDF's own bookmark tree, indented by nesting depth with the target page number beside each row. Tapping a row jumps straight to that page. A chapter header that names no page of its own leads to its first section's page instead. The tree is read from the PDF itself, so it matches what desktop Xournal++ or any PDF reader shows; it is navigation the file carries, so it never touches the `.xopp` (whose format has nowhere to keep it).
- **Full-Document Search & Handwriting Recognition**:
  - Unified search across authored text boxes, background PDF text, and **handwritten ink strokes**.
  - **Dual Handwriting Engine**: On Android devices, powered by **Google ML Kit Digital Ink Recognition** for high-accuracy neural recognition of print and cursive handwriting across 300+ languages, backed by a robust offline pure-Kotlin fallback with ligature-based stroke segmentation and topological feature classification.
  - **Fuzzy & Multi-Candidate Search**: Tolerates handwriting variations via candidate hypothesis matching, diacritic/accent normalization, multi-word continuous phrase matching, and Levenshtein distance tolerance.
  - **Modern UI & High-Performance Search**: Material 3 search pill with dynamic match badge (`1/3` or `0/0`), auto-focus, keyboard Search action, quick clear button, AI handwriting indexing dialog when opening search (`SearchIndexingDialog`), followed by instantaneous real-time querying while typing and rounded canvas highlights with smooth navigation.
- **Vertical Space Tool**: Drag to open room on a page, exactly as desktop Xournal++ does it: **only elements that lie entirely below the grabbed line move** (one the line passes through stays put rather than being torn in half) and that is decided once, when you grab, so a block sliding upward doesn't pick up the elements it passes. Dragging down inserts and dragging up closes the gap — **and keeps going: nothing stops the block at the line**, so content can be pulled above the line it was grabbed at (and, pulled far enough, off the top of the sheet, where it stays and can be dragged back down). The amount inserted **snaps to the page ruling** when *Snap to grid* is on. Every layer moves together — a deliberate difference from the desktop, which reflows only the current layer, so no note is left behind by space opened on another layer. This is a reflow of coordinates only, so it round-trips through the `.xopp` file.
- **Text & Images**: Insert resizable text boxes (Sans, Serif, Monospace, bold, italic, custom colors) and external bitmap images.
- **Synchronized Audio Notes**:
  - Record audio while handwriting. Strokes are tagged with precise timestamps (`fn`/`ts`).
  - Use the **Play Object** tool to tap any stroke and replay the audio recorded at that exact moment.
  - In a **`.mobix`** the recording travels **inside the file**: it is bundled by content hash (one entry however many strokes replay from it) and adopted the moment the page that names it is read, so the document still plays after being moved to a folder with no `.wav` beside it. There the audio is **compressed** (Opus, about 15 MB an hour instead of about 300 MB of WAV). A **`.xopp`** is unchanged — desktop Xournal++ needs its companion `.wav` files, which is why a document written as `.xopp` records WAV in the first place, and the app writes those beside the file from your chosen audio folder. A compressed recording that ends up in a `.xopp` anyway (a `.mobix` saved as one) is written out as the file it really is and the app says so, since it cannot convert it.
- **Page Management & Overview**:
  - **Page Layout**: Single-page stack or multi-column grid overview (**1, 2, 3, or 4 pages per row**).
  - **Page Grid Edit Mode**: Select, reorder by dragging, copy, paste, or delete entire pages.
  - **Background Rulings**: Plain, Lined, Ruled, Graph, Dotted, and **Isometric** (desktop Xournal++'s triangular mesh) in standard Xournal++ colors. The background menu keeps it to three one-line sections — the styles as chips you scroll along, then the rule-spacing chips with the custom field on the same row, then the stationery presets — so nothing is pushed below the fold.
  - **Rule spacing**: the sheet's own line spacing, set from the background menu — **2, 3, 5, 7 or 10 mm** chips or any value you type, with a millimetre grid marking every centimetre bold. The spacing is written in the file as desktop Xournal++'s own ruling parameter, so a page ruled at 7 mm reopens at 7 mm on the desktop instead of quietly reverting.
  - **Stationery**: ready-made paper — **Millimetre paper**, **5 mm graph**, **7 mm ruled** and **Isometric 5 mm / 10 mm** are real Xournal++ rulings (they look right in the desktop app too), while **Cornell notes** — a layout no Xournal++ version has — is laid down as a layer of rules named after the template, so it stays normal ink you can hide, restyle or delete.
  - **Page Bookmarks**: flag any page with a label of your own and one of six colours — the flag is drawn as a tab on the sheet's own corner, both in the overview grid and while you write on it. The Pages menu bookmarks the page in view, edits or removes that bookmark, and jumps to any of them. Bookmarks are kept **alongside** the document, not inside it: the `.xopp` format has no page label at all, so a bookmarked file stays byte-identical to an unbookmarked one (and the desktop app never sees markup it would drop).
  - **Page Format**: Presets (A4, A5, Letter, Legal) or custom dimensions (mm, in, pt), plus the **space the document is written in** — Paged (the fixed sheets a `.xopp` has), **Infinite vertical** (one locked width, extending downwards), **Infinite horizontal** (one locked height, extending rightwards) or **Infinite board** (no locked dimension at all) — in one dialog, **Pages ▸ Page format…**, because the two answer one question: what is a page of this document. Choosing a canvas asks whether it applies **to the open document** (only while it has never been saved — a saved file keeps the canvas its own header declares) or **only to the new ones**, which also becomes the default for new documents. A document created on a board is written as spatial chunks from its very first save, and a `.xopp` created from scratch is always paged, since that format has no chunks to hold a board. **A document always opens at 100%**, so a board is never inherited at a zoom that left its ink off screen ([docs/infinite-canvas.md](docs/infinite-canvas.md)).
  - **A strip's fixed side is yours to set**: an infinite vertical or horizontal document is born the width (or height) of the pane it opens in, so the sheet covers the screen from the first frame instead of ending a quarter of the way across, and scrolling simply continues it. The one side it does not run on — its **bound** — is set in the same **Pages ▸ Page format…** dialog as **Fixed width** / **Fixed height**, in millimetres, and the canvas never pans past it: a sheet narrower than the screen stays put, one wider than the screen can be panned across. It is offered while the document has never been saved, since a written `.mobix` keeps the shape its own header declares. **Infinite board** (2D) locks nothing and so has no bound to set.
  - **Infinite documents are drawn as one continuous sheet**: a board opens with no page outlines and no page breaks — the ruling runs across the whole board, you can pan in every direction the mode allows (down, right, or anywhere at all), and only the tiles on screen are read, so a long board opens as fast as a short one.

### ⚙️ Hardware Optimization & Settings

- **Settings laid out like a tablet's**: on a screen at least 600dp wide the four areas — **Input**, **Drawing**, **Interface**, **App & data** — are a **permanent side menu** with the selected area's sections beside it (an opened section takes that pane, and back returns to the list); narrower screens drop the menu for the pushed index → area → section. Back steps whichever shape is on screen one level at a time, mirroring the title-bar arrow.
- **Settings search**: the field at the top of the menu (or of the index on a phone) filters live to the sections that match — by name, by what they cover, and by the words people actually type (*"momentum"*, *"wallpaper"*, *"cache"*, *"left-handed"*). Each result names the area it lives in and opens straight to the section.
- **First-launch tour (5 steps)**: stylus setup, harmonising figures with handwriting, graphics tablets and shortcuts, **handedness** — which hand writes, shown with a picture of the rail on each edge (a right hand docks the Main Toolbar on the **left**, the default; a left hand on the **right**), and **new-document format** — choosing between native `.mobix` (recommended, preserving living graphs and tables) and `.xopp` (portable desktop interchange). All of it is changeable later in Settings.
- **New document format**: under **Settings → App & data → Documents**, choose the default format for brand-new documents (`.mobix` or `.xopp`). Opened documents always remember their own format, and Save As converts at any time.
- **Canvas geometry lives with the page format**: the space a document is written in — fixed pages or an infinite canvas — is chosen in **Pages ▸ Page format…**, not in Settings, and that dialog asks whether the choice applies to the open document or only to the new ones ([docs/infinite-canvas.md](docs/infinite-canvas.md)).
- **An infinite document exports as pages**: a board has no page breaks of its own, so **Save As** to `.xopp` and **Export PDF** run it through the auto-slicer — the content is cut at the nearest clear whitespace within 40 pt of each page boundary and never through a stroke, a table or a graph, and the result is an ordinary paginated file that desktop Xournal++ and any PDF reader open. The sheets are the board's own paper and the first one starts where the document's print origin says. The `.mobix` itself is never converted: it keeps its chunks, and the pages exist only in the file being written ([docs/infinite-canvas.md](docs/infinite-canvas.md)).
- **Graphics Tablets**:
  - Plug-and-play USB OTG and Bluetooth tablet support (Wacom, Huion, XP-Pen, Gaomon).
  - **1-Click ExpressKey Detection**: Map physical tablet buttons directly to tools and colors by pressing them in **Settings → Shortcuts**.
  - **Stylus Calibration**: Independent pressure multiplier (up to 4×) and minimum pressure floor (up to 1.00) — desktop Xournal++'s own ranges — plus precision budgets (Economy to Maximum).
- **Toolbars & Interface**:
  - **One colour per job**: the *bars* are one colour and the *toolbars* another, one tonal step up. The top bar and the tab strip share the first; the Main Toolbar rail, the Secondary Toolbar's floating dock and **the surround behind the pages** share the second, so the desk a page lies on reads as the same material as the tools floating over it. **Both Android system bars are transparent**, and the chrome never sits **on** the page: the header — the top bar with the row of document tabs inside it — and the tool rail each take a band of their own and the canvas takes what is left, so the whole visible sheet stays writable and no bar ever hides the ink under it. The header is itself one unbroken slab: no rim along its edges and no divider between the tools row and the tabs row, so no grey line cuts across the top of the screen. In Standard the canvas still reaches the screen's bottom edge, where only the clock and the gesture handle are drawn over the sheet rather than a band of the app's own colour below the grid. The gesture handle stays exactly where it was, and the bars' icons still flip light or dark with the theme. A hairline traces each sheet — that is what keeps a page readable when the light theme makes chrome and paper nearly the same white. **Everything that floats over the page is cut from that same material too** — the menus and their submenus, the action bars that appear with a selection, the STEM editors, the page and zoom badges and the dialogs: the bars' own frosted fill, a shade less see-through so the rows on it stay crisp over the page, and the docks' 20dp corner — and a menu carries a hairline rim of its own along its edge, brightest on top and faintest at the bottom, so a menu reads as a lit panel lying **above** the bar it hangs from rather than dissolving into the grey behind it. Both toolbars likewise draw the same tool glyphs at the same size, so the figures' dock never reads as thinner ink than the rail beside it. The floating docks (rounded corners, squircles, pill tabs, grouped undo/redo container, frosted badges, Material 3 tonal elevation) are the only layout, and the theme is set under **Settings → Interface → Appearance**. Nothing on screen is tinted lilac or blue-grey in either theme: menus, chips, dialogs and selected rows are all built from one neutral grey ramp per theme, and the app's blue is kept for what it means — active tools, tabs and the selection.
  - **Top Bar**: Clean top bar with document title chip, a compact quick **Export PDF** button followed by the one-tap **Save** button at the far right (next to undo/redo), undo/redo, search, and **Split View** (right of the search button); the overflow menu holds the remaining file and pen actions.
  - **Main Toolbar & Secondary Toolbar (Dual Toolbar)**:
    - **Main Toolbar**: The primary tool rail (pen, highlighter, eraser, select, colour & size, zoom, layers, pages) dockable to the **Left or Right** edge — the edge opposite your writing hand (right hand → left edge, the default; left hand → right edge).
    - **Colour & Size (one slot)**: A single rail position covers the whole stroke. Its left half is the **three favourite colours** of the tool in use, stacked vertically — tap one to take that colour at once without switching tools away (so drawing shapes like lines and rectangles or the active pen continues in the picked colour), long-press one to redefine it from the palette. A tap's ripple — and the highlight a hovering stylus leaves — is the **dot's own circle**, not the rectangle the dot is tapped in — and just over a third of the slot is a **chevron button** that opens the full pop-up with the palette, the three tip sizes and the line style. The pen and the highlighter keep **separate** sets, and the slot shows the set belonging to the tool in use (reach for the highlighter and its three appear in place of the pen's), so it never spends two rail positions on colour. The favourites and the pop-up used to be two separate slots; they are one now, which gives the rail a button back. Both sets are set up under **Settings → Drawing → Colors**, and the factory sets are the ones you would reach for: the pen's **black, red, green** and the highlighter's **yellow, green, blue**. The pop-up's palette row also carries an add-colour swatch and scrolls instead of wrapping, so the menu's height is fixed.
    - **Never a half button**: The rail fills its height with a **whole number** of buttons, at the size they are meant to be. It measures the height it has and spreads as many slots as fit *whole* over it, so the next button starts exactly at the rail's bottom edge instead of being cut in half by it; a couple of dp left over become buttons a few percent larger rather than a sliver of the next one. **A button is never drawn smaller than its own size** — what doesn't fit is one scroll away — so everything down to the page settings is visible without scrolling. A rail whose buttons all fit is left exactly as it was.
    - **Secondary Toolbar**: The geometric figures and tools toolbar (lines, rectangles, shapes, tables, circuits, logic gates, guides), displayed as an adaptive floating dock in the top bar, holding the figures alone. It opens with the **active tool indicator**: the tool's icon, a swatch of the live colour and the `S`/`M`/`L` size letter on the light grey `surfaceContainerHighest` (never the blue a *picked* tool button wears, since the indicator only reports), at the dock's own 40dp height with its 20dp corner, tonal and shadow elevation and hairline border. It currently runs as the dock's own **end cap**, grey flowing into the dock's rounded border, which keeps it clear of both the screen's left edge and the rail; the earlier style — the indicator as a **button of its own** standing in the corner the rail's column and the dock leave between them, sized so the figures' toolbar begins exactly on the document tabs' line, where the `Untitled` chip begins — is still in the code behind the `INDICATOR_AS_DOCK_CAP` constant, one flip away from coming back. Tapping it opens the colour & size pop-up; with the Main Toolbar docked to the right edge it trails the dock instead, so it is always on the rail's side (rail → indicator → figures).
    - **Layout (Standard or Compact)**: **Standard** — the writing toolbar down either vertical edge, plus the top bar's two rows: the Secondary Toolbar's figures, and the document tabs as the row under them. **Compact** — a single floating bar along the **bottom** for the writing tools, with the four 1-tap STEM tools (f(x), Geometry, Table, Circuit) built into it and the Secondary Toolbar gone, which gives the top bar back to the document tabs. Chosen once under **Settings → Toolbar**, and changeable at any time.
    - **The chrome never covers the page**: each bar takes a band of its own and the canvas takes the rectangle that is left, so every pixel of the sheet you can see is a pixel you can write on — the top bar and the row of document tabs inside it take the status-bar inset, the rail gets a lane down its edge below the whole header, and the Compact bar sits in a band of its own above the navigation-bar inset, where the canvas ends. Nothing is ever clipped by the screen edge or hidden under another bar, and no bar is ever laid over the page — a popover, a dialog or the pen-tip HUD can be, because they last only as long as the gesture that opened them.
  - Reorder, hide, or show buttons in both the Main Toolbar and Secondary Toolbar under **Settings → Toolbar**.
- **Navigation & Scrolling**:
  - Configurable momentum scrolling (linear, quadratic, cubic, exponential curves) and panning sensitivity.
  - **Touch gesture shortcuts**: a **two-finger tap undoes** and a **three-finger tap redoes**, anywhere on the canvas — no trip to the toolbar with the stylus in hand. Switch them off under **Settings → Shortcuts → Gestures** if a finger tap is not how you want to undo.
  - Distraction-free **Full-Page View** (double-tap canvas center with Hand tool to hide all chrome).
  - A hairline scroll thumb flush with the canvas edge (drag it to jump, with a page-number bubble while you drag); persistent page counter and zoom indicator (tap to reset to 100%).
- **Theming & Backups**:
  - Material 3 theme (System, Light, Dark) with optional Material You dynamic wallpaper colors (Android 12+). The chrome itself stays black, grey and blue whatever the wallpaper offers — dynamic colour cannot tint a panel, a chip or a menu.
  - Complete settings and custom palette export/import to standard JSON. An import accepts only a
    compatible MobiXournal backup (older versions included) and reports an error for anything else — a
    wrong file, or a backup written by a newer app version.

### 💾 Saving & Exporting

- **Cloud & Remote Storage**: Save and export PDF to any destination the system file picker offers, including cloud providers (Google Drive, OneDrive, Dropbox) and mounted remote shares. Every write is serialised to a local staging file first and pushed across in a single pass, so a slow or failed upload can never leave a half-written `.xopp` behind.
- **Save**: Writes directly to the open file without prompts. A blocking progress overlay ("Saving …") appears the moment Save is tapped and stays up until the bytes have landed — encoding a large document no longer freezes the app before the note appears. A **`.mobix` saved back to the file it was opened from is repacked in place**: only the manifest and the pages that moved are written, every other entry — untouched pages, bundled PDFs, pictures, recordings — is copied as the bytes the file already holds, and a page nothing touched is never even opened, so one page's edit costs the same few milliseconds however long the notebook is ([docs/mobix-format.md](docs/mobix-format.md)).
- **Quick Export PDF**: The compact PDF button in the top bar (to the left of Save) exports straight away, without a trip through the overflow menu. The overflow menu's **Export PDF** entry does the same.
- **Export page as image**: The overflow menu's **Export page as PNG** and **Export page as SVG** write the active page on its own — a raster PNG for sharing or a picture slot, or an SVG that keeps the page as editable vector geometry (strokes, shapes, text and the ruling, with embedded background images).
- **Save As**:
  - **Original (`.xopp`)**: Standard gzip-compressed XML file with external PDF/image linking. Best for desktop interchange.
  - **Zipped (`.xopp`)**: Self-contained archive with PDFs and images bundled internally. Ideal for standalone sharing.
  - **MobiXournal (`.mobix`)**: This app's own format, and the one that keeps what a `.xopp` can only bake into ink — a table stays a table, a plot keeps its formula (the graph dialog now inserts one), a component keeps its pins, and a stroke keeps the stylus telemetry behind its width. Its pictures and background PDFs are bundled inside the same file, so a `.mobix` is self-contained by construction. To hand one to desktop Xournal++, pick **Original (gzip)** or **Zipped** in **Save As**: the open document stays a `.mobix` (the export is a copy on disk) and the exported file carries the living entities with it, so opening it here again gives the editable table back instead of a drawing of one. Anything the export could not draw — a symbol this version has no drawing for, a formula that will not evaluate — is reported after the save instead of being left out silently.
- **Export PDF**: Flattens annotations over vector backgrounds. Original vector PDFs are written back as real page content — crisp vectors, no ballooning rasterization — and the bundled DejaVu fonts ensure accurate rendering across all PDF readers. An imported PDF therefore stays vector through the whole document, from import to export.

---

## 🏛️ Project Structure

The Kotlin source code is organized under `app/src/main/java/com/mobixournal/`:
- `format/`: `.xopp` XML parser, writer, models, and gzip/zip container handling.
- `render/`: Canvas rendering engine, stroke geometry, and STEM shape generators.
- `ui/`: Jetpack Compose Material 3 chrome, toolbars, settings, and dialogs.
- `io/`: Android Storage Access Framework (SAF), PDF background loading, and caching.
- `audio/`: Synchronized microphone recording and playback engine.
- `tabs/` & `panes/`: Multi-tab session cache and split-view management.

Full architectural details, data flow diagrams, and test suites are documented in the architecture spokes, indexed from [`docs/architecture.md`](docs/architecture.md).

---

## 📄 License

MobiXournal is free software licensed under the **GNU General Public License, version 2 or later** ([GPL-2.0-or-later](LICENSE)), matching upstream [Xournal++](https://github.com/xournalpp/xournalpp).

```
MobiXournal - Stylus-first Xournal++ editor for Android
Copyright (C) 2024-2026 amarzano2005
Based on NeXopp, Copyright (C) 2023-2024 Brian Monroe
Based on Xournal++, Copyright (C) 2018-2026 The Xournal++ Team
```

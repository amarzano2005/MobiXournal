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
| **System Architecture & .xopp Schema** | [`docs/architecture.md`](docs/architecture.md) |
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
See [Host fallback](docs/tools.md#host-fallback--no-dataandroid-no-docker) in `docs/tools.md`.

### Build Commands
```sh
# Full build: unit tests + debug APK
scripts/build.sh

# Run JVM unit tests only (instant, no device needed)
scripts/build.sh testDebugUnitTest

# Clean release-path build of the debug APK
scripts/build.sh clean assembleDebug
```

On the host's own toolchain (no container), the two loop commands are `./gradlew testDebugUnitTest`
and `./gradlew assembleDebug`.
For emulator installation, deployment, and testing harnesses, refer to [`docs/tools.md`](docs/tools.md). Cutting a release is documented there too, and it is a command rather than a chore: `scripts/todo.sh release --version <version> --bump-gradle` stamps everything finished since the last release and drafts `docs/releases/v<version>.md` from it, then you curate that draft and push the `v<version>` tag so CI builds the APK and publishes the Release.

---

## 📖 User Guide

### 📂 Workspace & Documents

- **Multi-Tab Interface**: Work with multiple documents simultaneously. Each tab preserves its own viewport, zoom, page position, and history. Tabs can be dragged to reorder.
- **Split View & Live Mirroring**:
  - **Split View**: View and edit two different documents side by side with an adjustable divider.
  - **Mirrored View**: Open a second live view of the *same* document (e.g. view a reference diagram at the top while writing notes at the bottom). Changes sync immediately.
- **Tab Overview**: Tap the grid icon in the top bar to inspect all open tabs as thumbnail previews.
- **Session Restore**: Open tabs and unsaved drafts are cached locally and restored automatically on next launch.
- **Universal File Import**:
  - **`.xopp` / `.xopp.gz`**: Standard Xournal++ documents (both gzip and zipped single-file archives).
  - **PDF Documents**: Open or import any PDF for annotation. Choose **Replace** or **Append** (appends merge seamlessly into a single document). Pages are drawn on screen through the raster cache and its tiles — smooth to pan, pinch and draw on, and still sharp at any zoom because the visible tiles are rasterised at the true on-screen resolution.
  - **Plain Text & Markdown (`.txt`, `.md`)**: Automatically typeset into annotatable pages with rich headings, monospace code blocks, and lists. Text remains selectable and copyable.
  - **Images (`PNG`, `JPEG`, `WebP`)**: Opened as annotatable pages with the image as the background.
  - **System Integration**: MobiXournal registers as a document handler for `.xopp`, PDF, and images in the Android "Open with" / "Share" menu. Remote shares (WebDAV, Nextcloud, SSHFS) are supported via Storage Access Framework.

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
  - The dialog checks the formula as you type — the same parser the plotter uses — so it never accepts something it can't draw.
  - The plot is inserted as **ordinary strokes** and text: a frame, the axes with a tick and a number on every nice step, and the curve, broken wherever the function is undefined or leaps (a pole stays a gap, not a line drawn across it). It saves to the `.xopp` like anything you drew, opens in desktop Xournal++, and can be selected, moved, restyled or erased.
- **Geometry & Vectors**:
  - **Geometric Polygons & Shapes**: Squares, rhombuses, trapezoids, pentagons, hexagons, ellipses/circles, and rectangles.
  - **Triangles with Variants & Custom Angles**: Submenu with 4 geometric kinds: **Equilateral** (default, equal sides/angles), **Right-angled**, **Isosceles**, and **Scalene** with fully customizable interior angles (A, B, C; sum = 180°) and live preview.
  - **Trapezoid with Variants & Custom Angles**: Submenu with 3 kinds: **Isosceles** (default, shorter base centred over the longer one), **Right-angled** (one leg perpendicular to the bases), and **Scalene** with its two base angles customizable — unequal angles give unequal legs, i.e. four different sides — with live preview. The figure is always fitted inside its drag, and its angles are what is kept exactly.
  - **Cartesian Coordinate Axes**: Instant oriented X/Y coordinate systems.
  - **Vectors**: Single and double-ended arrows for force diagrams and dimensioning.
  - **Parametric Splines**: Multi-point smooth curves with interactive tangent handles.
- **Virtual Drafting Instruments**:
  - **Setsquare (30°/60°/90°)**: Movable drafting triangle with edge snapping for straight lines.
  - **Compass**: Circular guide with adjustable radius for arcs and circles.
  - **Protractor**: 180° scale with 5°/15° tick marks, straight diameter ruler, rim arc snapping, and 15° radial snapping.
  - **Ruling**: no instrument at all — the page's **own ruling is the guide**, and every drawn vertex (freehand ink and shapes alike) is pulled onto the lines the paper really draws, spacing and margin included. A hand-drawn line comes out along a rule, and a rectangle's corners land on the grid. Nothing to place or hold, and nothing on plain, PDF/image or isometric paper, which rule no lattice to snap onto.
- **Shape Recognition & Snapping**:
  - **Ported Recognizer**: Desktop Xournal++ algorithm snaps freehand strokes into lines, rectangles, triangles, and circles.
  - **Snap to Grid**: Snaps a shape's **endpoints** to graph, dotted, or ruled paper rulings — the same lattice the **Ruling** guide pulls *every* vertex of any stroke onto.
  - **Snap Rotation**: 15° stepping for object rotation and drafting tools.

### 🛠️ Editing & Canvas Tools

- **Selection & Manipulation**:
  - **Rectangle & Lasso Selection**: Select active-layer objects.
  - **Transformations**: Move (with auto-scrolling at screen edges), resize — **proportional from the corner handles and single-axis from the edge handles**, so a drawing can be stretched wider or taller out of proportion — and stroke rotation. Stroke width follows a per-axis stretch by the geometric mean of the two factors.
  - **A precise outline**: the dashed box is drawn **exactly on the selected elements' ink**, with no padding, so a selection shows what it actually holds; the ease of grabbing a handle comes from the hit radii, not from drawing the box bigger than the element.
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
  - Use the **Play Object** tool to tap any stroke and replay the audio recorded at that exact moment (stored as companion `.wav` files compatible with desktop Xournal++).
- **Page Management & Overview**:
  - **Page Layout**: Single-page stack or multi-column grid overview (**1, 2, 3, or 4 pages per row**).
  - **Page Grid Edit Mode**: Select, reorder by dragging, copy, paste, or delete entire pages.
  - **Background Rulings**: Plain, Lined, Ruled, Graph, Dotted, and **Isometric** (desktop Xournal++'s triangular mesh) in standard Xournal++ colors. The background menu keeps it to three one-line sections — the styles as chips you scroll along, then the rule-spacing chips with the custom field on the same row, then the stationery presets — so nothing is pushed below the fold.
  - **Rule spacing**: the sheet's own line spacing, set from the background menu — **2, 3, 5, 7 or 10 mm** chips or any value you type, with a millimetre grid marking every centimetre bold. The spacing is written in the file as desktop Xournal++'s own ruling parameter, so a page ruled at 7 mm reopens at 7 mm on the desktop instead of quietly reverting.
  - **Stationery**: ready-made paper — **Millimetre paper**, **5 mm graph**, **7 mm ruled** and **Isometric 5 mm / 10 mm** are real Xournal++ rulings (they look right in the desktop app too), while **Cornell notes** — a layout no Xournal++ version has — is laid down as a layer of rules named after the template, so it stays normal ink you can hide, restyle or delete.
  - **Page Bookmarks**: flag any page with a label of your own and one of six colours — the flag is drawn as a tab on the sheet's own corner, both in the overview grid and while you write on it. The Pages menu bookmarks the page in view, edits or removes that bookmark, and jumps to any of them. Bookmarks are kept **alongside** the document, not inside it: the `.xopp` format has no page label at all, so a bookmarked file stays byte-identical to an unbookmarked one (and the desktop app never sees markup it would drop).
  - **Page Dimensions**: Presets (A4, A5, Letter, Legal) or custom dimensions (mm, in, pt). Configurable default page size.

### ⚙️ Hardware Optimization & Settings

- **Settings laid out like a tablet's**: on a screen at least 600dp wide the four areas — **Input**, **Drawing**, **Interface**, **App & data** — are a **permanent side menu** with the selected area's sections beside it (an opened section takes that pane, and back returns to the list); narrower screens drop the menu for the pushed index → area → section. Back steps whichever shape is on screen one level at a time, mirroring the title-bar arrow.
- **Settings search**: the field at the top of the menu (or of the index on a phone) filters live to the sections that match — by name, by what they cover, and by the words people actually type (*"momentum"*, *"wallpaper"*, *"cache"*, *"left-handed"*). Each result names the area it lives in and opens straight to the section.
- **First-launch tour (4 steps)**: stylus setup, harmonising figures with handwriting, graphics tablets and shortcuts, and **handedness** — right-handed (the default, Main Toolbar on the left) or left-handed (toolbar on the right), chosen while the toolbar moves behind the dialog. All of it is changeable later in Settings.
- **Graphics Tablets**:
  - Plug-and-play USB OTG and Bluetooth tablet support (Wacom, Huion, XP-Pen, Gaomon).
  - **1-Click ExpressKey Detection**: Map physical tablet buttons directly to tools and colors by pressing them in **Settings → Shortcuts**.
  - **Stylus Calibration**: Independent pressure multiplier (up to 4×) and minimum pressure floor (up to 1.00) — desktop Xournal++'s own ranges — plus precision budgets (Economy to Maximum).
- **Toolbars & Interface**:
  - **One colour per job**: the *bars* are one colour and the *toolbars* another, one tonal step up. The top bar, the tab strip and both Android system bars share the first, so the frame around the document is a single field from the status bar down to the navigation bar instead of three tonal steps; the Main Toolbar rail, the Secondary Toolbar's floating dock and **the surround behind the pages** share the second, so the desk a page lies on reads as the same material as the tools floating over it. A hairline traces each sheet — that is what keeps a page readable when the light theme makes chrome and paper nearly the same white. The floating docks (rounded corners, squircles, pill tabs, grouped undo/redo container, frosted badges, Material 3 tonal elevation) are the only layout, and the theme is set under **Settings → Interface → Appearance**.
  - **Top Bar**: Clean top bar with document title chip, a compact quick **Export PDF** button followed by the one-tap **Save** button at the far right (next to undo/redo), undo/redo, search, and **Split View** (right of the search button); the overflow menu holds the remaining file and pen actions.
  - **Main Toolbar & Secondary Toolbar (Dual Toolbar)**:
    - **Main Toolbar**: The primary tool rail (pen, highlighter, eraser, select, colour & size, zoom, layers, pages) dockable to the **Left or Right** edge.
    - **Colour & Size (one slot)**: A single rail position covers the whole stroke. Its left half is the **three favourite colours** of the tool in use, stacked vertically — tap one to take that colour at once without switching tools away (so drawing shapes like lines and rectangles or the active pen continues in the picked colour), long-press one to redefine it from the palette. A tap's ripple — and the highlight a hovering stylus leaves — is the **dot's own circle**, not the rectangle the dot is tapped in — and just over a third of the slot is a **chevron button** that opens the full pop-up with the palette, the three tip sizes and the line style. The pen and the highlighter keep **separate** sets, and the slot shows the set belonging to the tool in use (reach for the highlighter and its three appear in place of the pen's), so it never spends two rail positions on colour. The favourites and the pop-up used to be two separate slots; they are one now, which gives the rail a button back. Both sets are set up under **Settings → Drawing → Colors**, and the factory sets are the ones you would reach for: the pen's **black, red, green** and the highlighter's **yellow, green, blue**. The pop-up's palette row also carries an add-colour swatch and scrolls instead of wrapping, so the menu's height is fixed.
    - **Never a half button**: The rail fills its height with a **whole number** of buttons, at the size they are meant to be. It measures the height it has and spreads as many slots as fit *whole* over it, so the next button starts exactly at the rail's bottom edge instead of being cut in half by it; a couple of dp left over become buttons a few percent larger rather than a sliver of the next one. **A button is never drawn smaller than its own size** — what doesn't fit is one scroll away — so everything down to the page settings is visible without scrolling. A rail whose buttons all fit is left exactly as it was.
    - **Secondary Toolbar**: The geometric figures and tools toolbar (lines, rectangles, shapes, tables, circuits, logic gates, guides), displayed as an adaptive floating dock in the top bar, holding the figures alone. In the corner between the Main and Secondary toolbars stands the **active tool indicator** as a **button of its own**: the tool's icon, a swatch of the live colour and the `S`/`M`/`L` size letter on the light grey `surfaceContainerHighest` (never the blue a *picked* tool button wears, since the indicator only reports), at the dock's own 40dp height with its 20dp corner, tonal and shadow elevation and hairline border — so it reads as one of the floating docks instead of as the first chip *inside* the figures' dock. It stands in the corner the two toolbars leave between them — the rail's own column — and is sized so the figures' toolbar begins exactly on the document tabs' line: the dock starts where the `Untitled` chip starts. Tapping it opens the colour & size pop-up, and with the Main Toolbar docked to the right edge it trails the dock instead, so it is always on the rail's side (rail → indicator → figures). The style it wore before — the indicator as the dock's own **end cap**, grey running the dock's full height into its rounded border — is still in the code behind the `INDICATOR_AS_DOCK_CAP` constant, one flip away from coming back.
  - Reorder, hide, or show buttons in both the Main Toolbar and Secondary Toolbar under **Settings → Toolbar**.
- **Navigation & Scrolling**:
  - Configurable momentum scrolling (linear, quadratic, cubic, exponential curves) and panning sensitivity.
  - **Touch gesture shortcuts**: a **two-finger tap undoes** and a **three-finger tap redoes**, anywhere on the canvas — no trip to the toolbar with the stylus in hand. Switch them off under **Settings → Shortcuts → Gestures** if a finger tap is not how you want to undo.
  - Distraction-free **Full-Page View** (double-tap canvas center with Hand tool to hide all chrome).
  - Fast scroll thumb with page number bubble; persistent page counter and zoom indicator (tap to reset to 100%).
- **Theming & Backups**:
  - Material 3 theme (System, Light, Dark) with optional Material You dynamic wallpaper colors (Android 12+).
  - Complete settings and custom palette export/import to standard JSON. An import accepts only a
    compatible MobiXournal backup (older versions included) and reports an error for anything else — a
    wrong file, or a backup written by a newer app version.

### 💾 Saving & Exporting

- **Cloud & Remote Storage**: Save and export PDF to any destination the system file picker offers, including cloud providers (Google Drive, OneDrive, Dropbox) and mounted remote shares. Every write is serialised to a local staging file first and pushed across in a single pass, so a slow or failed upload can never leave a half-written `.xopp` behind.
- **Save**: Writes directly to the open file without prompts. A blocking progress overlay ("Saving …") appears the moment Save is tapped and stays up until the bytes have landed — encoding a large document no longer freezes the app before the note appears.
- **Quick Export PDF**: The compact PDF button in the top bar (to the left of Save) exports straight away, without a trip through the overflow menu. The overflow menu's **Export PDF** entry does the same.
- **Export page as image**: The overflow menu's **Export page as PNG** and **Export page as SVG** write the active page on its own — a raster PNG for sharing or a picture slot, or an SVG that keeps the page as editable vector geometry (strokes, shapes, text and the ruling, with embedded background images).
- **Save As**:
  - **Original (`.xopp`)**: Standard gzip-compressed XML file with external PDF/image linking. Best for desktop interchange.
  - **Zipped (`.xopp`)**: Self-contained archive with PDFs and images bundled internally. Ideal for standalone sharing.
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

Full architectural details, data flow diagrams, and test suites are documented in [`docs/architecture.md`](docs/architecture.md).

---

## 📄 License

MobiXournal is free software licensed under the **GNU General Public License, version 2 or later** ([GPL-2.0-or-later](LICENSE)), matching upstream [Xournal++](https://github.com/xournalpp/xournalpp).

```
MobiXournal - Stylus-first Xournal++ editor for Android
Copyright (C) 2024-2026 amarzano2005
Based on NeXopp, Copyright (C) 2023-2024 Brian Monroe
Based on Xournal++, Copyright (C) 2018-2026 The Xournal++ Team
```

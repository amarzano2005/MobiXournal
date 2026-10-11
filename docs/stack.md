# The pinned stack, and the decision behind each choice

> Read when you wonder why a framework, library or dependency is here at all — or why a piece of the chrome takes the colour it does.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Stack — pinned 2026-07-30

Native Android, no cross-platform framework (the abandoned reference was Flutter; we go
native for stylus latency and platform fit).

- **Language:** Kotlin, targeting the modern Android SDK.
- **App chrome / UI:** **Jetpack Compose with Material 3** (Material You) for all app chrome —
  app bar, menus, dialogs, the tool palette. Satisfies the Material Design requirement in
  `TODO.toml`.
- **One colour scheme drives every surface, and one chrome colour paints it.** All chrome — app
  bar, rail/toolbar, dialogs, the elevated popovers — takes its colours from
  `MaterialTheme.colorScheme` (`ui/theme/Theme.kt`); no surface hardcodes a colour. The canvas is the
  one exception by construction: it's a `SurfaceView` outside the Compose tree, so
  `ui/theme/ChromeColors.kt` maps the scheme onto its four chrome colours (page backdrop and its
  hairline outline, selection marquee/handles, guide overlay) as ARGB ints and `EditorScreen` pushes
  them in via `DrawingSurfaceView.applyChromeColors`.

  The chrome then splits in two, by job, and each half has one value:

  - **`rememberChromeColor()` — the bars** (`background`): the `Scaffold`'s container under the
    transparent top bar, the tab strip, and both Android system bars (`Theme.kt` sets
    `statusBarColor`/`navigationBarColor` from it). Three dark greys stacked down the screen read as
    three unrelated surfaces, so all of them take this one.
  - **`rememberToolbarColor()` — the implement colour** (`surfaceContainer`): the Main Toolbar rail
    (`SideToolbar.kt`) and the Secondary Toolbar's floating dock (`EditorRegions.kt`) are filled with
    it, and **the canvas backdrop takes the same value**, so the desk around the page stack is literally
    the material the tools are made of rather than a darker void behind them. The docks keep their one
    tonal step over the bars, which is what makes a floating dock read as floating instead of dissolving
    into the bar it hangs from. Rail, dock and surround all read this, so they cannot drift apart.

  A new *bar* surface takes `rememberChromeColor`, a new *tool* surface takes `rememberToolbarColor`;
  neither is a `surfaceContainer*` role spelled out by hand. `pageOutline` (the scheme's
  `outlineVariant` hairline around each sheet, drawn in `DrawingSurfacePaint.drawPageOutline` — **not**
  in `BackgroundRenderer`, which the PDF exporter also calls) is what keeps a near-white page findable
  against a light-themed surround. **Ink, pen palette and page backgrounds are document data, not
  chrome, and are deliberately never themed** — they must round-trip to the file byte-for-byte.
- **Drawing surface:** a custom low-latency **`SurfaceView`** (not Compose `Canvas`) hosted in
  the Compose tree via `AndroidView`. Stylus input comes from raw **`MotionEvent`** with
  `getPressure()` / `getAxisValue(AXIS_PRESSURE)` and historical points
  (`getHistoricalX/Y/Pressure`) so fast strokes keep their samples. This is the load-bearing
  choice for "round-trip safety" — we capture pressure at the same fidelity the format stores.
- **One width source for every tool.** *Decision (2026-08-04):* `DrawingSurfaceView.widthForPressure()`
  is the single place a **tapering** stroke width is derived from the size setting: the highlighter's
  constant band and the pen's per-vertex pressure taper both go through it. *Decision (2026-10-03):*
  the pen's pressure response is desktop Xournal++'s own. A **Pressure sensitivity** switch
  (`AppSettings.pressureEnabled`) turns the taper off entirely, so a pen stroke draws at its set size
  and matches a shape exactly; when on, the width is `max(minimumPressure, pressure × multiplier)`
  (`PressureCurve.penFactor`) — the desktop's `filterPressure` — with `minimumPressure` and
  `pressureMultiplier` carried as the desktop's own two settings. *Decision (2026-10-05):* the whole
  expression lives once, in `PressureCurve.widthPt()`, which `widthForPressure()` calls — so the
  settings' effect on a drawn width is unit-tested (`PressureCurveTest`) instead of being read off a
  call site. The two sliders' **ranges are the desktop's own** (`adjustmentMinimumPressure` 0.01–1,
  `adjustmentPressureMultiplier` 0.5–4, mirrored as `PressureCurve.MINIMUM_PRESSURE_*` /
  `MULTIPLIER_*`): a narrower ceiling is what made the multiplier feel inert. And because half of a
  switched-off filter can only do nothing, both controls are shown **disabled with the reason** while
  `pressureEnabled` is false — desktop Xournal++ greys the same frame out — rather than looking broken. Shapes (line, rectangle, ellipse,
  arrow, double arrow, coordinate axis, spline) have no pressure stream, so they are drawn at the size
  setting **verbatim** (`DrawingSurfaceView.nominalShapeWidthPt()`), exactly as the desktop draws
  them: an app-invented scale would make a shape drawn here a different thickness from one drawn on
  the desktop, and the same file would look different in each app. A shape is therefore as thick as a
  fully-pressed pen and a little thicker than a light stroke — the desktop's own behaviour, which
  switching pressure off removes. A recognised freehand shape keeps the mean width of the stroke it
  replaces. The size itself is per tool family:
  `AppSettings.lastWidth` feeds the pen, `highlighterWidth` the highlighter, and `shapeWidth` the
  line/shape/spline tools — the last kept separate (and defaulting to the same middle pen-width slot
  the pen starts at, so a fresh figure and fresh handwriting come out the same weight) so selecting
  a shape neither inherits a hairline pen nor drags the pen thick. The highlighter is the only tool
  left on the wide slot. The highlighter is the
  one tool whose width is not the raw setting: `DrawingSurfaceDefaults.highlighterWidthFor` snaps it
  to desktop Xournal++'s own highlighter tips (`HIGHLIGHTER_SIZES_PT` = 2.83 / 8.50 / 19.84 pt, its
  1 mm / 3 mm / 7 mm slots from `ToolHandler::initTools`), so a highlighter band is the thickness
  the desktop app would draw however the pen's S/M/L slots happen to be configured. Which *slot* a
  width is written to follows one rule too: `AppSettings.withWidthFor(tool, width)` sends every
  width change — toolbar slider, tool preset — into the live tool's own
  field, so thickening the highlighter or a figure can never become the pen's width. Restoring on a
  switch is the other half of the same rule: `EditorUiState.switchToolTo` writes the outgoing
  tool's style into its slot and reads the incoming one's back out, and
  `DrawingSurfaceView.activateTool` is the wrapper that also pushes colour/width to the canvas and
  persists. *Decision (2026-10-01):* rail slot, keyboard shortcut and barrel button all go
  through that one function. They used to hand-roll their own save/restore, and the paths
  that restored nothing at all (the pen↔eraser shortcut, a highlighter preset
  writing `lastWidth`) left the pen drawing at the figure's or the highlighter's fat width — the
  reported bug. A related trap: the grouped rail slot's picker fired the switch **twice** (once
  from `onSelect`, once from its own `onClick`), and the second save was built on a stale settings
  snapshot, silently dropping the first; `SideToolbar` now activates from the click only.
- **`.xopp` I/O — no third-party format libraries.** Both containers use only the JDK's
  `java.util.zip`: gzip via `GZIPInputStream` / `GZIPOutputStream` (`ORIGINAL`), and the
  ZIP-package via `ZipInputStream` / `ZipOutputStream` (`ZIPPED`, `format/XoppZip.kt`). XML goes
  through Android's built-in streaming `XmlPullParser` (read) and `XmlSerializer` (write).
  Streaming keeps large documents off the heap and gives us exact control over attribute
  preservation (a fidelity requirement — see [`xopp-format.md`](xopp-format.md#fidelity-notes--round-trip-hazards)).
- **PDF export — PDFBox (`com.tom-roush:pdfbox-android`).** *Decision (2026-07-31):* the one
  non-framework runtime dependency, taken deliberately. The framework `android.graphics.pdf`
  writer (`PdfDocument`) can only paint onto a canvas, so exporting an imported PDF forced every
  page through a raster bitmap — a no-op import→export bloated files ~10× and discarded vector
  content. PDFBox is the only mature, permissively-licensed (Apache-2.0) library that can import
  an existing PDF page **preserving its vector content** and append a vector overlay; iText's
  AGPL licence ruled it out.  Scope is contained to `PdfExporter`/`PdfVectorPainter`/
  `PdfBackgroundPainter` — the *export* path only. (An experiment that also used PDFBox to draw a
  page **on screen** through its Android `PDFRenderer` was retired in 2026-10-11 — a per-frame
  content-stream replay that made interaction laggy; see the vector-PDF note in [`pdf.md`](pdf.md)). The `.xopp` I/O
  layer stays dependency-free, and the display path is the framework `PdfRenderer` alone.
- **File access:** the Storage Access Framework (`ACTION_OPEN_DOCUMENT` /
  `ACTION_CREATE_DOCUMENT`) so a `.xopp` opens/saves in place on the device — the file on disk
  is the only source of truth (per `AGENTS.md` non-goals: no cloud, no custom format).
- **Build:** Gradle (Kotlin DSL) inside a **Docker** container (Podman fallback) per
  `AGENTS.md`. Pipeline details live in `docs/tools.md`; build/run for a human lives in
  `README.md`.

# The pinned stack, and the decision behind each choice

> Read when you wonder why a framework, library or dependency is here at all — or why a piece of the chrome takes the colour it does.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Stack — pinned 2026-07-30

Native Android, no cross-platform framework (the abandoned reference was Flutter; we go
native for stylus latency and platform fit).

- **Language:** Kotlin, targeting the modern Android SDK.
- **App chrome / UI:** **Jetpack Compose with Material 3** (Material You) for all app chrome —
  app bar, menus, dialogs, the tool palette.
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
  - **`rememberGlassSurfaceColor()` — the one frosted-glass filler** (`ModernTheme.glassSurface`, so
    `rgba(22, 22, 24, 0.85)` in dark / `rgba(255, 255, 255, 0.88)` in light): the Main Toolbar rail
    (`SideToolbar.kt`), the header slab (`EditorRegions.kt` / `EditorScreen.kt`'s `RailLane`), the
    Secondary Toolbar's floating dock, the tab chips — **and every pop-up the chrome opens**    (`ToolbarMenu`). Bars read this one function, so no two bands can end up different materials; the
    canvas
    backdrop is the exception, on `rememberChromeColor()` (`background`), since the desk is the bars'
    colour and the frosted implements stand on it.
  - **`rememberFloatingSurfaceColor()` — the floating material** (`ModernTheme.floatingSurface`):
    everything the chrome puts **over** the page for a moment — the menus and submenus (`ToolbarMenu`),
    the action bars that appear with a selection and with the table/spline/PDF-text/region tools
    (`EditorActionBars`), the STEM editors' chrome, the page/zoom/scroll badges, the hand-rolled dialogs
    (`OnboardingDialog`, `KeyDetectionDialog`, the search-indexing card) and the transfer overlay. The
    bars' glass with the see-through turned down to `floatingPanelAlpha` (0.88) — same RGB, so a floating
    surface is never a different colour from the bar it appears over. The action bars and the STEM
    editors reach that fill through one wrapper each (`FloatingSurface`, `FloatingDialog` in
    `theme/FloatingGlass.kt`), so they also wear the menus' lit hairline. All of them are cut at
    `ModernTheme.panelCornerRadiusDp` (20dp), which `AppShapes.extraLarge` gives Material's own
    `AlertDialog`s too.
  - **`rememberToolbarColor()` — the recessed plates** (`surfaceContainer`): a surface *inside* a
    floating one that should read as a recess rather than a rise — the function dialog's mode-selector
    track, a thumbnail well, the search field's track, settings plates. It is *not* what the bars paint,
    whatever the name suggests; it is the neutral container role [colours.md](colours.md) pins per theme
    (`NeutralContainerDark` `#252528`, `NeutralContainerLight` `#EAEDF0`).

  A new *bar* surface takes `rememberGlassSurfaceColor`, a new *floating* surface takes
  `rememberFloatingSurfaceColor`, a new *recessed plate* takes `rememberToolbarColor`, and the desk takes
  `rememberChromeColor`; none of them is a `surfaceContainer*` role spelled out by hand, in either theme,
  and a floating one also takes `panelCornerRadiusDp`. `pageOutline` (the scheme's
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
- **JSON — the same rule, and no dependency either.** The `.mobix` container is JSON, and its model
  (`JsonNode`: object/array/string/number/bool/null), parser and writer are hand-rolled in
  `format/json/` — pure Kotlin over no runtime library, so the format layer keeps the property the
  `.xopp` layer has: what a document means is ours to read, not a library's. Only the codecs
  (`format/mobix/`) touch it; the settings backup's JSON goes through the same layer rather than its
  own private copy.
- **PDF backgrounds on the desktop — PDFBox for the JVM (`org.apache.pdfbox:pdfbox 2.0.31`).** The
  Android port cannot rasterise outside Android, so the `desktop/` module links the same 2.0 API on a
  plain JVM; the app keeps `pdfbox-android`, and the two never share a classpath. The **same split**
  covers writing: the geometry is `PdfPagePlan` in `mobix-core` and each shell replays it with its own
  library (`PdfVectorPainter` in the app, `DesktopPdfPainter` in the window), so no painter holds a
  second opinion about a pressure taper or a highlighter band. Two differences are real and stay
  shell-side: images decode with `BitmapFactory` there and `ImageIO` here, and Apache PDFBox has no
  `setStrokingArgb` (the port's helper), so the window splits the colour into three channels and keeps
  the alpha in the graphics state.
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

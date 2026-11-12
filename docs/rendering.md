# The paint pipeline and page rasterisation

> Read when a change affects how the canvas repaints, how a page is drawn, or how a page is exported as an image.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

Android exports capture a `DocumentExportSnapshot` on the UI thread, then encode PDF, PNG or SVG and
write to the provider on a worker. Each job owns its decoders and element painters; it never reads a
later tab's View. The shared background painter serializes its mutable paints across render/export
threads. `DocumentExportSnapshotTest` checks a non-first-page PNG on Android; it requires a device.

## The frame record (`FrameWatchdog`)

A stutter cannot be argued with from the outside — "it stutters" and "the surface is doing twice the work"
look identical on screen — so the canvas keeps the window's own record: `FrameWatchdog` gathers one line per
second of **painting**, with **frames/s**, the **worst** frame gap, the **paint**'s average/worst time inside
the frame, and `page=` — how many full page rasters the ink cache produced during that second. The line is
pushed into the **pen-diagnostics panel** when it is on, so the numbers appear where a stylus problem is
already being read instead of needing a second overlay.

The three rules that make the numbers mean something are the window's, and each is a test rather than a
habit (`FrameWatchdogTest`, driven by a fake clock on the JVM): a line lands **once a second**, not once a
frame; an **idle gap is not a stall** (a reader who looked away for a minute did not drop a minute of frames,
or every line after a pause reads as a catastrophe); and the paint's own cost is reported **separately** from
the frame interval, because waiting for the next vsync is the display's and only the paint is ours. `page=` is
the fourth: a stroke that rebuilds the sheet every frame shows up as a number that climbs with the frames
instead of staying still.

What is *enforced in the build* are the invariants behind those numbers (`CanvasFluidityGuardTest`), because
an instrumented timing assertion would measure an emulator and an emulator's frame budget says nothing about a
large tablet: **a pointer event never paints** (a paint from an input handler posts several buffers per vsync
— the buffer walk above), **the repaint is the Choreographer's and coalesced** (`paintPosted`/`renderPosted`),
**a page raster is never produced on the drawing thread** (`InkCache.draw` blits or answers "not cached"),
and **the paint feeds the watchdog with the cache's raster count** — a watchdog nobody calls is worse than
none.

## The paint pipeline (`render/`)

**Rendering (`render/`).** Every repaint is **paced to the display**: `DrawingSurfaceView.render()`
never paints inline, it flags a `Choreographer` frame callback that runs the actual `paint()` once
per vsync, collapsing everything requested in between. This matters because a digitiser reports far
faster than the panel refreshes (240 Hz against 120 Hz on the large tablets): painting straight from
the input handler posted several buffers per vsync, and the compositor latching whichever was newest
made the shown position walk back and forth between samples instead of advancing — the flicker seen
when zoomed in on a big screen, where a paint is slow enough to keep several buffers in flight. The
fling loop is the one exception: it is already inside a frame dispatch, so it calls `paint()` directly
rather than deferring a frame — and because it does, `render()` is a **no-op while a fling is in
flight** (and starting a fling cancels any already-queued paint). Otherwise anything that asks for a
repaint mid-glide — most often a PDF tile landing and calling back, which is constant when zoomed in —
would post a *second* buffer for the same vsync and reintroduce the very buffer-walk flicker the
pacing exists to prevent. Each frame locks the surface with **`lockHardwareCanvas()`**, not
`lockCanvas()` (falling back to the software canvas only if the GPU one is unavailable): the software
canvas rasterises and blends every window pixel on the CPU, so frame cost scaled with window *area* —
full-screen page-flicking on a large tablet crawled while the identical gesture in a half-size
split-screen window stayed smooth. On the GPU canvas fill rate is effectively free and the cached page
bitmaps are plain textured blits — and those bitmaps are produced on `InkCache`'s **own worker**, never
inside a frame, so neither a page entering the viewport during a fling nor a pinch crossing a zoom
bucket makes the canvas wait for a page to be drawn (the same shape `PdfPageCache` uses for its tiles). The `DrawingSurfaceView` holds the whole [Document] and renders every
page in a single vertical stack, each page scaled to fit the view width via `PageStacker` and
drawn with its background ruling (`BackgroundRenderer`, using the pure `BackgroundGrid` offsets and
colours — lined rules in desktop Xournal++'s `xopp_dodgerblue`, graph/dotted in its `xopp_silver`),
resolved per page through `BackgroundRuling` so a sheet rules at **its own spacing, margin and line
width** rather than at a constant (see *Ruling parameters and stationery* below)
plus all of its layers in z-order. The geometry (page placement, gridlines) is factored into
`PageStacker`/`BackgroundGrid` precisely so it's unit-testable off-device. **One finger draws**
(a new stroke lands on the top layer of the page under the touch) — or **erases** when the
Eraser tool is active, deleting every stroke the eraser disc touches (hit geometry in the pure,
tested `StrokeHitTester`), or **pans** when the Hand tool is active; **two fingers pan** in any
tool. A **zoom** factor multiplies the fit-to-width scale (`PageStacker` takes it as a parameter);
when a page is wider than the view the same pan gesture scrolls horizontally, and narrower pages
are centred in the content band (`PageBox.leftPx`). The stack is **one column**: `PageStacker` lays every
page under the one above it, each fit to the view width, so `StackedLayout.pageAt(x, y)` is the only
hit-test a touch needs and `nearestPage(x, y)` resolves a probe that lands in the gap between two pages
(e.g. the viewport centre). A cached vertical-band index binary-searches viewport and pointer candidates;
ordinary separated pages cost O(log P + V), where V is the intersecting page count. Overlapping bands
preserve original ordering and horizontal hit tests without reading page models. The Pages menu navigates — a page list, `goToPage`, add/delete of a page —
and there is no page grid, no multi-page selection and no drag-to-reorder: a page is moved by editing the
document, not by dragging its thumbnail. The same `pageAt(x, y)` hit-test drives

## One rule book for a stroke (`mobix-core/.../render/InkGeometry.kt`)

What a stroke **is** — the highlighter's forced alpha (`0x78`) and multiply, its mean band width, the
dash pattern scaled by width, the render mode, and the variable-width outline of a pressure stroke —
lives in `InkGeometry` in `mobix-core`, not in the Android painter. `render/StrokePainter.kt` keeps the
Android-facing names (`renderColor`, `bandWidth`, `dashIntervalsPt`, `uniformWidth`) as delegates, and
the desktop shell's painter converts the same geometry into Compose paths, so the tablet and the PC
cannot disagree about how ink looks. Only the raster backend differs.

**A marker is a band, not a wide pen** — and that is the same rule in both shells. The highlighter's
width is `InkGeometry.highlighterWidthFor`: the pen's S/M/L slot scaled by `HIGHLIGHTER_WIDTH_FACTOR`
(6, desktop Xournal++'s own ratio) and snapped to the three canonical tips `HIGHLIGHTER_SIZES_PT`
(2.83 / 8.5 / 19.84 pt — the 1 / 3 / 7 mm markers). The tablet reads its stored slot through it, and
the window does the same in `DesktopInkState.pageInk`: the strip's dots and the settings file keep the
slot, while the stroke, the live preview and the ring take the tip, so a highlighter on the PC is the
band it is on the phone (`DesktopInkSplitTest`, `DesktopColorParityTest`).

**One plan, two emitters.** The PDF half of a page is not a painter's opinion: `PdfPagePlan` in
`mobix-core` compiles a page into an ordered list of ops (a filled outline, a stroked polyline with the
pressure's per-segment widths, a text run at its baseline, an image at its box) and each shell replays
that list with one arm per op and `runCatching` around each, so one unencodable op is skipped instead of
losing the page. `PdfVectorPainter` is the app's replay through `pdfbox-android`; `DesktopPdfPainter` is
the window's through `org.apache.pdfbox` — arms in the same order, the same graphics state, and the only
differences the ones the platforms force (images decode with `ImageIO` rather than `BitmapFactory`, and
Apache PDFBox has no `setStrokingArgb`, so the colour is split into three channels while the alpha stays
in the state). `DesktopPdfPainterTest` reads the produced content stream, so the claim that the PC prints
the tablet's page is checked against the plan's own colours rather than against a guess.

**A page is its paper and its ink, and both come from the plan.** `PdfPagePlan.paper` compiles the sheet
fill and its ruling — the page's own spacing, bold interval, margin and widths, the dotted sheet's small
squares and the isometric mesh — into the same op list, so a ruled page cannot print blank on one shell
(`PdfPagePlanTest` counts the rules against the sheet's own spacing). `PdfBackgroundPainter` is a one-line
replay of it: the geometry has no second home to drift into.

**The window smooths its own line — but never a corner.** A page stores *samples*, and on the desktop they
are 8 ms apart at best, so joining them with `lineTo` drew the sampling rate as facets along every
diagonal — the tablet never looked that way because it paints through a spline. `InkSmoothing.kt` turns
the samples into the standard midpoint quadratics (each sample a control point, each curve ending on a
midpoint, the chain starting and ending on real samples), and `PageInk.polyline` draws those; the
deviation is half a sample spacing, and **the stored points are untouched**, so the page is identical on
Android, in Xournal++ and in the window.

A **figure is a stroke too**, though, and averaging through its vertices turned every rectangle, triangle
and arrow head into a rounded blob — the smoothing was right and the shapes were wrong. So a sample whose
turn is at least `CORNER_DEGREES` (35°) is an **anchor**: the runs either side of it are smoothed
separately and the vertex stays exactly where the geometry put it. Freehand turns a few degrees per
sample, so handwriting is smoothed whole; a 60° or 90° corner is drawn as the shape it is
(`DesktopInkSmoothingTest`: a rectangle's perimeter comes out exactly its polygon's, an arc's comes out
slightly shorter than the faceted one it replaces).

## When the pane under the canvas changes size (`render/DrawingSurfaceResize.kt`)

A resize is the one change to the canvas that **no event announces**: a split-divider drag, a pane
handed back to the single-pane layout, a rotated window — none of them is a touch, and
`SurfaceHolder.Callback.surfaceChanged` is not guaranteed for each step of a drag. What the canvas
kept instead was the frame it last drew for the old extent: a cropped copy of the old layout in the
newly exposed half, sitting there until something unrelated asked for a repaint.

The invariant that fixes it is that **the page stack always describes the frame being painted into**:
`PageStacker`/`ViewportState` are rebuilt from the pane's extent (`relayout`), and the paint pass
compares the size of the buffer it actually locked with the view's own — a frame drawn into a buffer
the surface is about to outgrow is *provisional*, and asks for one more frame. The repaint itself is
deferred to **after the next traversal** (`OnPreDrawListener` + `post`, coalesced per traversal),
because that is where the platform commits the surface's new geometry: a paint that runs any earlier
locks a buffer of the old size again and, unless something else asks for a frame, leaves the old
image on screen. `ui/EditorRegions.kt` feeds the pane's size to the canvas as the Compose box changes
(`repaintAfterResize`), and the canvas's own `onSizeChanged` is not used — the pane, not the surface,
is what knows it was resized. **Opening or closing split view** hands every pane a different width in
the same frame, so that toggle posts the same call to each surface as well (`MainActivityTabs.kt`):
the pane's Compose box announces its new size on the traversal that follows, and the posted call is
the belt to that braces — a pane cannot be left drawing the extent it had while the split was open,
where its page and ruling would be at the wrong scale.

**A width change re-fits the page; a *toggle* keeps its size.** The stack fits each page to the pane
and then scales by the zoom, so a page is drawn at `viewWidth × zoom`: re-fitting on every resize
means the same 100% draws the sheet half as wide in split view and twice as wide back in single
view — turning the split off *doubles* the page and its ruling. Dragging the divider still re-fits
(the zoom is the factor you chose, and the pane is the frame you chose it in), but the split toggle
is not a resize to re-fit: it hands the pane a canvas of another width and compensates the zoom by
`previousWidth / width` (`keptScaleZoom`) in the same relayout, so the page keeps the size it had on
screen in either direction and the toggle is exactly reversible. The compensated zoom is reported
through `onZoomChanged`, so the badge shows the value the canvas is actually drawn at.

## One page as an image (`PageSvgWriter`, `writePagePng`/`writePageSvg`)

**One page as an image (`PageSvgWriter`, `DrawingSurfaceView.writePagePng`/`writePageSvg`).** The
overflow menu's two page-export entries write the **active page alone** through a
`MainActivityDocuments` staging file, exactly like the PDF export does (so a share/cloud target can
ever see a half-written file). PNG is the page rendered at 2× its point size through the same
`PageRenderer`/`BackgroundRenderer` pair the canvas uses; SVG is generated by `PageSvgWriter`, which
walks the page's elements and emits one `<path>` per stroke (`d=`, the element's own width/colour) with
the ruling, text boxes and images alongside, and the whole document wrapped in a viewBox of the page's
pt size. It is deliberately **not** a screenshot: no Android type is involved, so the writer stays
pure and unit-testable, and the output scales without the raster's pixel ceiling.

**The picture behind a page is the caller's, and only pixels can carry it.** A sheet written over an
annotated PDF, or one backed by a photograph, is not geometry the model holds, so `PageSvgWriter` does
not resolve it: `write(page, backdrop)` takes the already-encoded bytes (PNG or JPEG, self-describing)
and puts one `<image>` over the page box between the sheet and the ink, which is where the canvas paints
the picture. A caller with nothing to hand over — the tablet's page export, a ruled sheet — passes
`null`, and the document it gets is byte for byte the one the writer made before the parameter existed;
`DesktopSvgExportTest` asserts exactly that, so the two shells cannot drift apart page by page. The
desktop's per-document driver (`DesktopSvgExport`, one file per page) is the caller that supplies them.

**A living entity is drawn as compiled ink.** `.mobix`'s table, plot, shape and circuit have no drawing
of their own: each is compiled by its family's compiler and the result painted in its place, through the
single dispatch in `LivingInk` — the same one the PDF painter and the SVG writer above use, so an entity
cannot look one way on the canvas and another way in an export. See [mobix-elements.md](mobix-elements.md).

**A page point is one pixel per point of density, in every painter.** The desktop shell records a page at
`pxPerPt = zoom × density` and asks Compose for type sizes in `sp`, so a size that is already in px has to
come back out of px (`toSp`) before it is asked for in sp: `.sp` on a page point multiplied the display
density in a second time and painted every text box — and every formula's glyphs — `density` times too large
while the position stayed right, on a 150 % screen half again as large as the place it was written.
`drawTextBox` and `TexPainter` convert, and `DesktopTextPaintTest` measures the ratio between two densities
instead of trusting it. The one element a live in-place field has taken over (the text box being retyped) is
left out of the recording (`PagePicture`'s `skip`, part of its key, so opening and closing a box re-records
once each) — otherwise the old words are painted under the new ones for the whole edit.

The compilers themselves (`LivingInk`, `TableInk`, `PlotInk`, `ShapeInk`, `CircuitInk`, and the
function/geometry helpers they use) are **platform-free and live in `mobix-core`**: they turn a recipe
into ordinary ink — strokes and text boxes — rather than painting it, so whoever paints (Android
`Canvas`, Compose on Skia in the desktop shell, a PDF or SVG writer) draws the same compiled result.

## Painter ownership and retained geometry

Android background drawing uses one `BackgroundPainter` per thread behind `BackgroundRenderer`.
Its Paint objects, destination rectangle and ruling memo are not shared by canvas and export workers.
Desktop `StrokePathCache` uses identity-keyed LRU retention: at most 4096 recorded strokes and 32 draft
identities, additionally bounded by 250000 and 200000 weighted input points respectively. A pressure
outline counts twice; these are retention budgets, not exact native-byte estimates. Oversized geometry
is drawn without caching. Eviction changes rebuild cost, never the stroke model or pressure rule.
Both desktop caches are asked from **two lanes** — the canvas on the window's thread and the page list's
thumbnails on their own — so only their maps are guarded: a stroke's outline is built and a page's
backdrop raster is rendered *outside* the lock, or a background thumbnail would hold the monitor for a
whole page and the window would stop painting for as long as the sidebar took. Two lanes that want the
same entry at once may both build it; they agree on the answer, which is cheaper than one waiting.

## Append-only draft work

Freehand Android pressure previews reuse projected points and normals through `IncrementalTaper`.
Desktop draft conversion shares immutable point roots, and `IncrementalSmoothing` recomputes only the
two terminal control groups. Its **contour is not incremental**: a desktop draft fans
`InkGeometry.taperOutline` over those points on every frame — the very builder the lift uses — because
the retained taper tail was measured against a full rebuild on live strokes (the window's `--diagnose`
probe) and answered a contour off by **69.54 px at its worst vertex on every frame**, while the points
agreed to the thousandth of a point. That is a stroke drawn in a shape the hand did not make, so the
desktop keeps the shared builder and pays an outline over a few hundred points per frame instead.
Figures and replaced/shrunk drafts reset the smoothing. The resulting contour is still one closed fill,
preserving winding, self-overlap, caps and highlighter alpha. Numeric draft caches have their own bounded
retention.
Backend path assembly/submission and the desktop bounds walk remain linear in stroke length; this is
incremental geometry computation, not a claim of constant-time rendering for arbitrarily long ink.

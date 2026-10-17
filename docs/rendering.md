# The paint pipeline and page rasterisation

> Read when a change affects how the canvas repaints, how a page is drawn, or how a page is exported as an image.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

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
bitmaps are plain textured blits. The `DrawingSurfaceView` holds the whole [Document] and renders every
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
are centred in the content band (`PageBox.leftPx`). A **column count** (`PageStacker.stack(columns=)`,
driven by `DrawingSurfaceView.setColumns` and persisted as `AppSettings.pageColumns`) turns that stack
into the **page overview**: pages are chunked into rows of N, each fit to `viewWidth/N` and the row
centred, so 1 is the plain stack and 2-4 a grid of page thumbnails. Because a row now holds several
pages, hit-testing takes both axes — `StackedLayout.pageAt(x, y)` picks the page under a touch and
`nearestPage(x, y)` resolves a probe that lands in a gap (e.g. the viewport centre). Those two hit-tests
also drive the overview's **edit mode**. The grid has two modes, held in
`PageOverview.editMode` (view-only, default off, toggled from the Pages menu via
`setPagesEditMode`): in **view mode** the grid is display/navigation only — a confirmed Hand-tap
`goToPage`s the page it hit and neither selection nor reordering is armed; **edit mode** enables the
page tooling below, and leaving it cancels any lift and clears the selection. In edit mode those
hit-tests drive **drag-to-reorder**: a finger long-press (`ViewConfiguration`'s timeout,
disarmed by touch-slop travel or a second pointer, and never armed for a stylus or at one column)
lifts the page under it, the drag tracks a drop slot with `nearestPage`, and the release commits one
undoable `PageOps.move(pages, from, to)` through `editPages`. The same `pageAt(x, y)` hit-test drives

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

**A living entity is drawn as compiled ink.** `.mobix`'s table, plot, shape and circuit have no drawing
of their own: each is compiled by its family's compiler and the result painted in its place, through the
single dispatch in `LivingInk` — the same one the PDF painter and the SVG writer above use, so an entity
cannot look one way on the canvas and another way in an export. See [mobix-elements.md](mobix-elements.md).

# The drawing tools and what they do to the page

> Read when you work on a tool: shapes and their styles, the eraser, layers, hold-to-snap, function plots, or how a page is zoomed.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Zooming the page stack

Zoom keeps the viewport-centre point roughly
fixed, and is clamped to 25%–1000% (`DrawingSurfaceDefaults.MIN_ZOOM`/`MAX_ZOOM`). Strokes and other
elements are re-rendered vectorially at the zoomed scale, so they stay sharp at any level; PDF
backgrounds are re-rasterised per zoomed width up to `BitmapLruCache.MAX_RASTER_WIDTH` (4096 px) and
never above `BitmapLruCache.PAGE_SHARE` of the cache budget for one bitmap (so the visible pages
can't evict one another and flash blank), beyond which the whole-page bitmap is upscaled to bound
memory — asynchronously, so a zoom step shows the previous resolution stretched and sharpens a
moment later rather than stalling the frame. A page with *nothing* cached is the one exception: it
rasterises inline, since an empty background reads as a blank page. Past that whole-page ceiling
the sharpness comes from **tiles**: `PdfPageCache.requestTiles` rasterises only the visible cells of
a `PdfPageCache.TILE_PX` (512 px) grid built at the true on-screen page width, each rendered 1:1 via
a `Matrix` on `PdfRenderer.Page.render`, and `BackgroundRenderer` draws them over the upscaled page
bitmap. So PDF text stays sharp to the 1000 % zoom ceiling while cost stays proportional to the
viewport, not the page; a tile that hasn't rasterised yet simply shows the coarse layer underneath.
When the tiles on hand already cover every visible pixel of the page, `BackgroundRenderer` skips the
coarse whole-page blit entirely, so those pixels aren't rasterised twice. Four things keep the tile
path off the frame budget: `requestTiles` **memoises** its answer per page and rebuilds the list only
when the visible cell block or the cache's contents change (a pan holds the same block for many
frames); it queues the **ring of cells just outside** the viewport, so a pan meets rasterised tiles
at its leading edge rather than the coarse under-layer; a viewport spanning more cells than
`PdfPageCache.MAX_TILES_PER_FRAME` caps how many new cells are *queued* instead of dropping the whole
request, so cells already rasterised still draw sharp; and `nearest` finds a stand-in bitmap through
a per-page sorted width index rather than scanning every entry, since a pinch calls it per visible
page per frame while the cache holds hundreds of tiles. Tiles landing from the worker coalesce into
a single redraw (`DrawingSurfaceView.requestRender`) instead of one full repaint each.
Eviction **pins the visible cells**. `requestTiles` records the viewport's cell block per page (and
refreshes its LRU recency) *before* the memo short-circuit, and `put` spares those keys on its first
eviction pass; the ring is only warmed while the cache is under `PdfPageCache.PREFETCH_HEADROOM` of
budget. Without this, a zoom whose visible tiles plus ring outgrow the budget evicts the very tiles
being drawn, the next frame falls back to the upscaled whole-page bitmap and re-queues them, and the
page flickers between blurry and sharp indefinitely. `DrawingSurfaceView` calls `PdfPageCache.retain`
each frame with the on-screen pages so pins don't accumulate behind a scroll, and a second eviction
pass ignores pins entirely, so a viewport too large to cache still stays memory-bounded.
Ink is culled the same way: `DrawingSurfaceView` hands `PageRenderer.drawElements` the viewport in
page-local pt, and any element whose `ElementBounds` box misses it is never submitted (boxes are
memoised by element identity, so the cull doesn't rescan stroke points each frame). At high zoom a
page spans many screens, where almost every stroke would otherwise cost thousands of canvas calls
Skia only clips away. `PdfExporter` passes no viewport and so draws everything. Above that cull sits
`InkCache`: each visible page's ink is rasterised once into an off-screen bitmap, so a pan or fling
frame is a **blit** rather than a re-submission of every stroke. The raster is keyed by a **zoom
bucket** (widths step by `InkCache.BUCKET_RATIO`, 1.19×). An entry is invalidated by page identity
(any edit rebuilds the `Page`), by its hidden-layer set, or by leaving the kept window
(`InkCache.retain`, which keeps the visible pages **plus one either side** — a flick back re-blits
rather than re-drawing the page it just left). The raster is produced on **one worker thread, never
inside the frame**: a miss queues it and answers "not cached" for that frame, so the caller draws the
page's elements directly (exact, just not a blit), and `InkCache.onReady` asks the view for one more
frame when the bitmap lands. A pinch therefore never stops to rasterise: while the worker catches up
it keeps blitting the nearest raster it already holds, as long as that is within
`InkCache.INK_STAND_IN_MAX` (1.45×) of a stretch, and the worker follows with the bucket the fingers
came to rest on. The cache **declines** two
cases and the direct element path takes over: a page whose bucket would exceed its per-entry ceiling
(`BitmapLruCache.PAGE_SHARE` of the shared budget — at deep zoom a page spans many screens and its full
raster would dwarf the screen it feeds, and the viewport cull is the better tool there), and any
gesture that rewrites the page every frame — drag, resize, rotate, erase — where caching would only thrash.
`PdfPageCache` and `ImageBackgroundCache` share their LRU core: both extend **`BitmapLruCache<K>`**,
which owns the access-ordered map, the short-held cache lock, the single background worker, the
insert-and-charge path, and eviction. A subclass supplies only what differs — `produce` (rasterise or
decode, called *without* the cache lock), `index`/`unindex` (its width index behind `nearest`),
`spared` (PdfPageCache's on-screen pinned tiles), and the `onCacheChanged`/`onDiscard` hooks.
`BitmapLruCache.MAX_RASTER_WIDTH` (4096 px), `PAGE_SHARE` (a quarter of the budget per raster) and
`bucket` (64 px width buckets) live there once for all three caches.

## The 2D function plotter (`FunctionPlot`, `MultiFunctionPlotter`, `FunctionAnalysis`)

**The 2D function plotter and function study (`FunctionPlot`).** *Insert graph* turns `f(x)` into a **living plot** that draws as ink: `FunctionPlot`
holds a recursive-descent parser (`+ - * / ^`, unary minus, parentheses, implicit multiplication
such as `2x^2` or `2(x+1)`, scientific notation `2e-3`, superscripts `x²`, standard math aliases like `tg`/`ctg`/`π`, `x`, `pi`/`e`,
piecewise expressions like `x < 0: -x; x >= 0: x^2`, and the function table), sampling, and mapping onto page points — all pure and unit-tested.
The plotter supports **multi-curve plots** (`MultiFunctionPlotter`), each with individual colors and line styles (`PLAIN`, `DASHED`, `DOTTED`),
parametric curves $\{x(t), y(t)\}$ (`AdvancedCurves`), and **function study / analysis** (`FunctionAnalysis`):
automatic detection of critical points (roots/zeros, y-intercept, local minima, local maxima, mutual curve intersections, and vertical asymptotes/poles),
numerical derivative $f'(x)$ curve plotting, an optional summary report table inserted on the sheet, and a real-time live preview inside `AdvancedFunctionGraphDialog`. The dialog's two tabs are **kinds, not two inputs**: its parametric tab asks for one `{x(t), y(t)}` curve, and the answer carries that kind through the element into the file, so a pair is sampled and drawn as the loop it is instead of being compiled into the two waves its two formulas would look like as `y = f(x)` — which is also why `MultiFunctionPlotter` refuses a curve whose kind it cannot place rather than drawing the wave. The preview is not a second renderer either: it builds the very element Insert would place and draws it through `LivingInk` (`ui/GraphDialogPreview.kt`), so a preview cannot promise a drawing the insertion will not make.
A pole or leap breaks the curve, so `1/x` comes out as two branches instead of one line through the origin.
The plot consists of frame, axes, ticks, curve strokes, and optional marker dots and summary text — and the dialog's answer now becomes a **living `PlotElement`** (`DrawingSurfacePlots.insertPlotElement`,
boxed by `PlotElementFactory.boxFor`), inserted as **one undoable edit** on the active layer. It draws
through the same compiler it always did (`LivingInk` → `PlotInk` → the plotter), so the graph on screen is
unchanged; what changed is that the recipe reaches the file, which a `.xopp` cannot hold and a `.mobix`
can ([mobix-elements.md](mobix-elements.md)). The scope rule still holds at that boundary: a `.xopp` save
flattens the element into the same strokes (`XoppSynthesis`). `insertMultiPlot` stays as the plain ink
path for a caller that wants strokes and nothing else. An unparsable formula inserts nothing at all rather than
an empty frame, and the dialog validates with the very same `compile` call, so it can't accept a graph
the plotter would refuse to draw. It is opened from the Secondary Toolbar (`RailItem` id `graph`, placed
immediately to the right of the Cartesian coordinate axis button — a dialog opener rather than a tool, so it gets a plain action button, not a
pop-up), and the plot is placed **where the user is looking**: its box is centred on
`viewportCentrePt(pageIndex)` — the page-local point under the middle of the viewport — and then pulls
it back inside the sheet, so turning the plotter on while zoomed into a corner lands the graph where
the eye already is rather than at the sheet's own middle. Same idea as `placeGuide`, and the same
fallback to the page centre before the canvas has been laid out.

## Hold-to-snap (`DrawingSurfaceHoldSnap.kt`)

**Hold-to-snap (`DrawingSurfaceHoldSnap.kt`).** The shape recogniser is also reachable *before* lift-off:
resting the stylus on the glass for `SHAPE_DWELL_MS` (300 ms) **paints** the recognised geometry in
place of the stroke in progress, so the user watches the snap happen. Each sample that moves the tip
past `SHAPE_DWELL_SLOP_PT` (3 pt in page space, so it reads the same at any zoom) re-arms a
`postDelayed` timer; the timer firing stores the figure in `holdFigure` with the stroke's sample
count, and `drawCurrent` paints that figure instead of the samples. The samples themselves are never
rewritten: `commitCurrent` keeps the held figure only while the count still matches, so lift-off on a
resting tip commits the shape (uniform-width, like a shape tool's output) and a hand that draws on
gets its own ink back the moment a sample arrives. (Rewriting `current` instead — what this used to do
— left a half-clean, half-raw stroke and made the commit re-read the recogniser's own output, turning
a held square into a circle.) The timer is cancelled by every move, commit, cancelled gesture and
stylus takeover (`abandonInProgress`/`cancelGesture`), which is why the gesture plumbing had to know
about it at all. It shares the `recognizeShapes` setting — and the pen and highlighter tool filter —
with the on-lift path, so there is one recogniser and one switch.

## Living figures

**Living figures (`ParametricShapes.kt`, `ShapeEditorDialog.kt`).** A closed polygon drawn freehand is
the one recognised figure that is kept as **geometry instead of ink**: when the recogniser's kind is a
triangle or a rectangle, `commitCurrent` commits a `ParametricShapeElement` (its corners, the pen's
colour and width) rather than the uniform-width stroke the same geometry would have become
(`docs/mobix-elements.md`). Only the pen and a plain line qualify — a highlighter's ink and a dashed
line are properties the element does not carry, so those stay strokes, and so do circles, ellipses,
arrows, arcs and lines, whose vertices would not be corners to drag.

Selecting one draws a handle on each corner. Dragging a handle deforms the figure **live** — the box,
the selection outline and the readout badges all follow the same frame — and the side leading into the
dragged corner snaps onto the page's guided directions, 0°, 30°, 45° and 90° in all four quadrants,
when it is within `VERTEX_SNAP_TOLERANCE_DEG` (7°) of one; the corner keeps the pointer's distance
from the previous corner, so a snapped drag reads as a rail under the finger rather than a jump.
**Edit shape** on the selection bar toggles the two readouts (*Show side lengths*, *Show angles*).

The maths is pure and unit-tested (`ParametricShapesTest`) — `moveVertex`, `snappedVertex`,
`sideLengthPt`, `interiorAngleDeg` — and the labels on screen are drawn by `ShapeInk` calling those
same functions, so what the sheet shows is what the `.xopp` export writes.

## Shapes, styles, partial eraser, layers

**Shapes, styles, partial eraser, layers.** The **line and shape tools** (Line/Arrow/Double arrow/Rectangle/Ellipse/Coordinate axis/Polygon/Table) turn a
one-finger drag into an ordinary constant-width pen stroke — and the `desktop/` window draws the same
set from one drag (Line, Arrow, Double arrow, Axes, Rectangle, Ellipse, and the tablet's Triangle,
Trapezoid and Polygon behind one **figures** slot), calling the
same `ShapeBuilder` and holding `Shift` to snap the drag's direction to 45°, so a figure made on the PC
is the same stroke a tablet makes (`docs/desktop-workstation.md` owns the window's slots): `ShapeBuilder` (pure, tested) converts the
drag's start/end into a vertex list, previewed live and committed as one undoable stroke, so shapes
round-trip like any stroke. A figure writes with **the pen's ink**, never the highlighter's, so the width
and colour on the strips are the ones it lands at even while the marker is in hand — and the width is
read from that ink, not from a constant of the tool's own (`DesktopGeometryTest` pins the chain from
`pickWidth` to the every-point width of the committed stroke). The **triangle tool** supports four geometric variants (`TriangleKind`):
equilateral (factory default, preserved 60° angles), right-angled, isosceles (centered apex), and scalene
(with customizable interior angles A, B, and C constrained to sum to 180° and previewed on a live canvas).
The **trapezoid tool** is the same pattern with three variants
(`TrapezoidKind`): isosceles (factory default — the shorter base centred over the longer one at half
its width), right-angled (the left leg vertical, so two angles are square), and scalene, whose two base
angles are freely customizable — a trapezoid has one pair of parallel sides, so its two base angles fix
its shape, and unequal angles are exactly what makes all four sides differ. `ShapeBuilder.trapezoidOutline()`
is the single layout both the tool and the settings dialog's preview call, so the preview cannot drift
from what a drag produces; the scalene figure is uniformly scaled to stay inside its drag (which
preserves the angles) and centred, the same fit the scalene triangle uses. The window draws both variant
figures too: its figures slot's picker lists the triangle, the trapezoid and the polygon, and its
**Settings → Figures** page writes the same
`TriangleKind`/`TrapezoidKind` answers — and a scalene one's angles — into `FigureShape`, the value the
drag reads (`FigureSettings` owns the stored keys), so a figure is the same on the PC and on the tablet
without either shell growing a second geometry.
The **table tool** creates an \(R \times C\) grid as a single continuous polyline,
with configurable rows and columns, optional relational header row (rendering a double separator line under the first row),
support for live drag-sizing and one-tap centered viewport insertion,
and figure-style stroke (inheriting and adjusting figure width, color, and line style).
The **polygon tool** is the family's generic figure, and the one whose shape count the user picks: a
single drag, and `ShapeBuilder` inscribes a regular polygon of `polygonSides` sides in the drag's box —
the same `regularPolygon` every other regular figure comes from, so the pentagon and the hexagon are
that one tool at five and six sides rather than figures of their own. The count is 3–12 (six at the
factory) and is chosen where the figure is: **the Polygons slot's own menu** is the side-count picker
(`PolygonSlotButton` — tap the slot to take the tool, tap it again or long-press for the picker, with
the live count riding the button's top-left edge, the picker's caret in the bottom-right corner and the
figure centred), and picking one also takes hold of the tool, because seven
sides is always about drawing a heptagon next. **Settings → Figures** writes the same stored value, so
the two cannot disagree; an out-of-range number is clamped rather than rejected, and the tool needs no
gesture of its own: the drag, the preview, the constant width and the undo step are the ones every
other figure uses. The window's figures submenu carries the polygon too, at the builder's default count
— a slot on the PC carries no variant of its own, the same way the triangle's kind is chosen on the
tablet. The **shape recogniser** (`ShapeRecognizer`) runs on commit for the pen and the highlighter, and a
stroke it does not name commits exactly as drawn. Its classes, thresholds and invariants are owned by
[`shape-perfection-engine.md`](shape-perfection-engine.md) — what belongs to the tools is that nothing
is simplified first (`StrokeSimplifier` only sees the strokes the recogniser rejected, since thinning
hides the detail the fit reads and can round a circle into facets), and that a recognised shape is an
ordinary constant-width stroke, so it round-trips like any ink. **Snapping** (`Snapping`, pure and tested) is
applied at the input edge rather than in the geometry builders: with **Snap to grid** on, the shape
drag's start and end points are rounded onto the page background's ruling before they reach
`ShapeBuilder`, and with **Snap rotation** on, the *swept* angle (not the absolute one) of the rotate
handle is rounded to 15° before it reaches `SelectionOps.rotate` — snapping the swept angle means a
snapped drag returns exactly to the element's original orientation. A **drawing guide** (`DrawingGuide`, pure and tested) is the same idea taken further: a setsquare
(right triangle) or compass (circle) posed in page-local pt, whose `project` pulls any point within
`GRAB_PT` onto its nearest edge and leaves anything further away untouched. `DrawingSurfaceView`
owns the live pose, paints the overlay in `paint()` (it is chrome, not ink, so it stays out of
`InkCache`), and funnels *every* drawn vertex through `guided()` — freehand samples in `point()` and
line/shape endpoints in `startStroke`/`extendStroke`, applied **after** the grid snap so the guide
wins. A finger that lands on the guide's *body* (the triangle's interior, the compass's hub) drives it on
its own pointer id, running alongside the drawing gesture rather than replacing it, which is what
lets the pen rule along a guide the other hand is holding; the edges are deliberately excluded from
the grab test so drawing against one never drags the instrument away with it. Nothing about a guide reaches the document. Which axes snap is a property of
the background style, so the pt spacings live once in `BackgroundGrid` and are shared by
`BackgroundRenderer`, `Snapping` and the `PdfPagePlan` the window's export replays. The **setsquare/compass guides**
(`DrawingGuide`, pure and tested) are a third constraint at that same input edge: the surface holds
one live pose pinned to a page, and every drawn vertex — freehand via `point()` and line/shape
endpoints alike — passes through `guided()`, which projects the point onto the guide's nearest edge
when it is within `DrawingGuide.GRAB_PT`. The guide is applied *after* grid snapping, so a placed
guide wins. Its fourth kind, the **ruling snap**, turns that same input edge around: it has no
instrument and nothing to drag — the page's own ruling *is* the guide — and it pulls **every** vertex
onto the lines the background really draws, resolved through `Snapping.lattice` so both the spacing
and the ruling's phase are the renderer's own (a graph sheet rules from its `m1` margin, not the page
corner). That reach is the whole difference from the **Snap to grid** setting, which rounds a shape's
two endpoints. Its fifth kind, the **chemistry lattice**, is a ruling snap of the guide's own making
turned on chemistry: `TriangularLattice` (pure and tested) is the 60° mesh of nodes whose six
nearest neighbours are a regular hexagon, and the guide pulls every drawn vertex onto the **nearest
node**, so a sketched skeletal formula gets exact 60°/120° bond angles and equal bond lengths and a
ring around a node is a benzene hexagon. Its spacing is its own (a bond length) rather than the
page's, so unlike the ruling snap it works on a plain or PDF sheet; and like the ruling snap it has
no instrument and no handle — the overlay it does draw is a faint `TriangularLattice.mesh` of the
bonds it will snap to (chrome, outside the ink cache), and a finger on the page keeps drawing. An *instrument* is drawn as a canvas overlay outside the ink cache (it is not page
content) and is manipulated by a finger on its own pointer id, deliberately running alongside the
drawing gesture rather than instead of it, so a hand can hold the instrument while the pen rules
along it; the ruling and chemistry snaps have neither an overlay handle nor a grab region, so a finger on the page keeps drawing. Nothing
about a guide reaches the document — only the resulting stroke does. Where an **instrument** was posed
*is* remembered, though, because losing it means re-posing by hand: `AppSettings.guidePose` holds a
`GuidePose` (the page plus the live guide) and the editor's `onGuideChanged` writes it back, so
`applySettings` can `restoreGuide` it on the next launch instead of `placeGuide`ing a fresh one. Only
the three instruments qualify (`DrawingGuide.isRememberable`) — the ruling and chemistry rules
re-derive their anchor from the page — and the pose is dropped when the kind changes or the page it
was posed on is gone, so a stored pose can never be applied to the wrong instrument. The guide chooser
(`DrawingGuidePopup`, rail and secondary top bar) groups options into Off, Instruments (Setsquare, Compass,
Protractor with 15° radial snapping) and Lattices (page ruling, 60°/120° chemistry) with dedicated vector
glyphs; tapping an active guide toggles it off, and placement operates cleanly without triggering asynchronous
settings feedback loops. A **line style** (`plain`/`dash`/`dashdot`/`dot`) and a **fill** alpha ride
on the stroke the tool draws next; `StrokePainter` paints a dashed/dotted style as a single
constant-width dashed path and floods a fill under the outline, and the window's `DesktopPdfPainter`
mirrors both for its export (a `setLineDashPattern` stroke and a `fill()` polygon). The **partial eraser** (`StrokeEraser`,
pure, tested) rubs out only the touched part of a stroke and splits it into the surviving pieces (each
inheriting the original's colour/style/fill), alongside the original whole-stroke delete
(`StrokeHitTester`). It hit-tests **segments**, not just vertices, and cuts where a segment crosses the
tip's disc — so a sparse shape stroke (a two-point line, a five-point rectangle) rubs out mid-shaft
just like densely-sampled freehand ink. `PageEraser` (pure, tested) is the page-level driver both modes go through: it
walks the page's layers, **skips hidden ones** (you only rub out ink you can see) and returns `null`
when nothing was touched, so the surface skips the document rebuild and the undo snapshot. The mode is
a view flag on the surface (`eraserMode`), set by which member of the rail's eraser slot is picked —
`EditorTool.ERASER` vs `ERASER_WHOLE`, so the choice is a tool, not a separate menu. The `desktop/`
window has the same two members as two slots (`Eraser` and `Eraser (whole)`, `Shift+E`) and passes
`DesktopTool.eraserMode()` to the same `PageEraser`, so the two shells rub out through one mode, not two
implementations. The tip size has
no scheme of its own: `DrawingSurfaceView.eraserRadiusPt` derives it from the pen's `baseWidthPt` via
`eraserRadiusPt()` (`ERASER_RADIUS_FACTOR`, floored at `ERASER_RADIUS_MIN_PT`), in **document pt** so
it is zoom-invariant, matching the desktop. **Layer management** (`LayerOps`, pure, tested) adds/
deletes/renames/reorders/merges-down layers and moves a selection between them (all undoable;
`mergeDown` appends the upper layer's elements after the lower one's so z-order survives, keeps the
lower layer's name, and drops the emptied upper layer), while the *active*
layer (where new ink lands) and per-layer *visibility* are view-only editor state on the surface —
visibility just skips a layer in `PageRenderer.drawElements`, so it never touches the file. The UI for
all four lives in the rail's **Tool** (shapes, eraser mode) and **Layers** pop-ups
(`SideToolbar`); line style sits in the **Colour & size** pop-up. Fill has no control at all any
more: nothing sets it, so a stroke drawn in this app is never flooded — while the *format* keeps
`<stroke fill>`, so a document that already carries it still reads, renders and re-saves untouched
(`XoppReader`/`XoppWriter`, `PageRenderer.drawElements` → `StrokePainter.draw(fill = …)`).

Authoring and placing non-stroke elements — text, LaTeX, images, plots, tables and circuits — and thevertical-space tool are [element-authoring.md](element-authoring.md)'s.



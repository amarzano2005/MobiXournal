# The selection tool: marquee, lasso, handles and transforms

> Read when you change how elements are picked, moved, resized, rotated, aligned or restyled, or the region (background-copy) variant.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Selecting objects (`render/`)

**Selecting objects (`render/`).** The **Select** tools (`EditorTool.SELECT`,
`EditorTool.LASSO_SELECT`, `EditorTool.TEXT_SELECT`, and `EditorTool.BG_SELECT`) share the rail's
Select group, but their gestures stay separate. Object selection (`selectMode`) mirrors desktop
Xournal++: a one-finger **drag** draws a rubber-band marquee and selects every element **wholly
enclosed** by it (desktop's rectangle-select semantics); a one-finger **tap** picks the single topmost
element under the point. Selection is **per page** —
anchored to the page the gesture started on — and elements are addressed by position, not identity, via
`ElementRef(layerIndex, elementIndex)`: a move rewrites the element objects but never reorders them, so
the refs stay valid across a live drag. The picking is pure and JVM-tested — **`ElementBounds` is the
single owner of "what rectangle does this element occupy"**: `ElementBounds.of` gives each element's pt
bounding box (strokes grown by half-width, images/teximages are their box, text a rough content-extent
metric) and `ElementBounds.TAP_PAD` is the one hit-test margin. Every consumer routes through it —
`SelectionTester` (picking), `PageRenderer` (viewport cull), `VerticalSpaceOps` (the grab line) and
`ElementEdits.hitsText` (tap-to-edit) — so selection handles, culling and vertical-space insertion agree
by construction rather than by coincidence; nothing re-derives a box locally. The four **living
entities** (`.mobix`'s table, plot, shape and circuit) answer through the same seam with a box of
their own — a table's grid, a plot's frame, a circuit's compiled ink and a shape's **vertices**, which
are recomputed on every read so a picked figure is always where its corners are — and each carries
its own handles on top (dividers, sliders, vertex dots): see [mobix-elements.md](mobix-elements.md). `SelectionTester` does
rect-containment / topmost
tap / union-bounds, and `SelectionOps` translates or deletes the addressed elements on a page list
(returning a new list; immutable pages/layers share structure so a snapshot stays cheap). Dragging inside
the selection outline translates the elements live (recomputing from the gesture-start document each
frame so there's no drift) and commits as **one undoable edit**. Dragging into the top or bottom
edge band starts a **drag auto-scroll** (`updateDragAutoScroll` + the `autoScrollCallback` frame
loop): the page scrolls one step per frame and the move is re-applied at the finger's last
position (`SelectionGestureController.moveSelectTo`), so the element rides the scrolling sheet even
under a held finger; a floating **Cut / Copy / Duplicate / Recolour / Width / Delete** bar
(`SelectionActionBar` in `ui/EditorActionBars.kt`, placed by `ui/SelectionActionAnchor.kt`) acts on the
selection (all undoable). That bar **rides with the
selection** rather than sitting at the bottom edge: the view publishes the selection's box in its own
view px (`DrawingSurfaceView.selectionScreenRect` / `onSelectionRectChanged`, reported from the render
pass next to `reportScroll`) through `PaneState.selectionRect`, and `EditorPaneView` — whose canvas
`Box` *is* the surface's coordinate space — hangs the bar just **below** the box (above it only when
the canvas ends below the selection, clamped inside the canvas either way). It carries **no Done
button**: tapping off the selection clears it, and that same tap starts the next stroke. Its controls
are 32dp squares rather than Material's 48dp (`SelectionBarButton`), which halves the bar's footprint,
and it is held at zero alpha until it has been measured so it fades in where it belongs instead of
flickering there. Pure placement geometry lives in `ui/SelectionActionAnchor.kt`'s
`selectionBarOffset`, pinned by `SelectionActionAnchorTest`. The
other mode bars (paste/region, table, spline, PDF-text selection) stay on the bottom edge. The dashed outline and marquee are drawn by the view over the page stack. Two-finger pan still
works in Select mode (it abandons the in-progress selection gesture).

The outline itself is drawn **exactly on the elements' ink box** (`SELECT_PAD_PX` = 0): a padded box
claims more than the user picked, and the handles' hit radii are what make it grabbable, not an
inflated rectangle. Beyond move/delete, the outline carries **eight resize handles**: the four
**corners** resize proportionally (a uniform scale about the opposite corner, `SelectionOps.scale`,
both factors being the pointer's distance ratio), and the four **edge midpoints** stretch **one axis
only** (`ResizeAxis.X` / `ResizeAxis.Y`, the pure `axisScaleFactor` measuring travel from the opposite
edge's midpoint, applied by `SelectionOps.scaleXY`) — the out-of-proportion resize, so a drawing can be
widened without being made taller. A resize stretches **geometry, not ink**: stroke widths are
carried through untouched (a longer arrow is not a fatter one) and a recognised arrowhead stays at
the size it was drawn, so only the shaft lengthens (`StrokeStretch`), while text sizes follow the
scale because letters are the figure there. Their grab radius is capped at a **third** of the
selection's on-screen size (`DrawingSurfaceDefaults.handleHitRadius`), because a fixed 30 px radius
swallows a small selection whole — every touch inside it lands within 30 px of a handle, turning an
intended *move* into a resize. A third is the largest cap that leaves the middle a move: the four
edge handles sit half a side out from the middle, so a cap of *half* put the middle itself inside a
handle's reach (below ~60 px of side, an element could not be dragged at all — the failing cross-page
move test was reporting exactly that). The handles stay grabbable in their own corners and edges.
`SelectionHandleRadiusTest` pins the rule. The outline also carries
— for an all-stroke selection only — a **right-edge rotate knob**
(`SelectionOps.rotate`, which bakes the angle into stroke vertices). A **lasso** marquee
(`lassoMode`) selects everything wholly inside a traced polygon (`SelectionTester.inPolygon`),
alongside the rectangle. Lasso containment tests a **stroke's own points**, not its bounding box —
a diagonal or curved stroke inside the loop has box corners outside it, so a box test would drop
exactly the strokes the user traced around; rectangular elements (image, TeX, text) still test
their four corners. Both containment tests are **tolerant by `ElementBounds.TAP_PAD`** — the same
margin a tap pick uses: a rect-select box is grown by the pad, and a lasso point counts as inside
when it is within the pad of a polygon edge, so a hairline stroke or an empty text box traced
closely isn't dropped for landing a hair outside. Both containment tests also sweep **only the
active layer** (`onlyLayer`, resolved from `resolvedActiveLayer` by `SelectionGestureController`),
matching desktop Xournal++'s current-layer selection so a marquee never grabs ink the user isn't
editing; with no layer chosen yet the sweep falls back to the whole page. (A single **tap** pick is
still page-wide — `pickTopmost` is unchanged.) **How far a lasso was traced** is measured by the
**extent of the traced path**, not by the gap from its first point to its last (`lassoExtentPx`): a
lasso closing back onto — or beside — its own start leaves that gap at zero, and reading it as the
drag distance made a real lasso look like a tap, so it picked the topmost element under the pen (or
nothing) instead of what it enclosed. The rectangle marquee, whose corners *are* its ends, keeps the
start-to-end measure. The traced path is stored in **page-local pt**
(`SelectionGestureController.lassoPoly`), converted as each sample arrives rather than on release,
and the overlay converts it back to view px with the *current* scroll each frame — so scrolling or
zooming mid-trace can't drift the shaded region away from the polygon that is tested, and the
overlay's closing segment is the same last→first wrap `inPolygon` assumes. **Cut / copy / paste / duplicate** run through a view-held element clipboard
(`SelectionOps.elementsAt` + `addToTopLayer`, which reports the pasted refs so the copies are
selected); paste lands on the visible page, **centred on the current viewport** (`SelectionOps.boundsOf`
+ the target box's `toPt`) rather than back at the copied-from spot. **Paste lives in this floating bar**
(`SelectionActionBar`, via `pasteFromActionBar`) rather than in a bar of its own: it sits beside Copy,
and since it needs no selection the same bar stands in at the bottom centre — the page-level spot —
carrying Paste alone when nothing is selected. That is what keeps it reachable *while* something is
selected (so a second copy can be pasted without deselecting first) and keeps the button with the
actions it belongs to, instead of a lone pill that vanished the moment anything was picked. `Paste`
first switches the tool to
`EditorTool.SELECT` unless a select tool is already active: only `SELECT`/`LASSO_SELECT` route
touches to `SelectionGestureController`, so pasting under `BG_SELECT` would otherwise draw the copies
as selected while they stayed undraggable — and the next touch would start a new marquee and clear
them. Dropping a move over a **different page** re-homes the
elements onto that page (`SelectionOps.moveToPage`, mapping through both pages' pt frames). The
floating action bar also **recolours / re-widths** the selection (`SelectionOps.restyle`).

## Align & distribute (`SelectionOps.align` / `distribute`)

**Align & distribute (`SelectionOps.align` / `distribute`).** With two or more elements selected the
same bar offers the six alignments — left/centre/right and top/middle/bottom — plus horizontal and
vertical distribution. Alignment translates every selected element but the **anchor**: the outermost
one on that axis, so the group lines up on its own extent and nothing moves further than it has to
(aligning left moves everything right of the leftmost element onto it, not all of them across the
page). Distribution keeps the two end elements and respaces the rest evenly between them, sorting by
centre so a straggler doesn't jump the queue. Both are pure page-list rewrites recorded as **one**
undo step, and both are no-ops — with no history entry — when the selection is too small to align
(two) or distribute (three). The scope and round-trip reasoning for what rotate/resize can touch lives in
[`stylus.md`](stylus.md) (Selection — desktop parity).

## Background-copy (region) select

The background-copy variant (`backgroundSelectMode`) is intentionally not a selection transform. A
drag records only a rectangular marquee, clips it to the page, synchronously resolves the page's
background bitmap (`PdfPageCache.render` or `ImageBackgroundCache.render`), then
`PageRegionRenderer.render` composites the page background and all layers into a capped PNG. That PNG
is wrapped as one `ImageElement` in the same view-held clipboard the normal Select tool uses, so
Paste can drop the flattened region like any other copied image. There is no lasso path for this
tool; the copied result is one flat bitmap rather than editable elements.

The marquee **survives the release**: `commitBackgroundSelect` keeps it as `backgroundRegion` (page
index + `Bounds` in page-local pt, so it stays put under scroll and zoom — `drawBackgroundRegion`
maps it back through the `PageBox`) and reports it through `onBackgroundRegionChanged`, which drives
the **Copy** and **Cut** buttons in `SelectModeBar` — now purely the *region* bar, since Paste moved
to the selection action bar above. The release itself **never writes the clipboard**
— only an explicit Copy/Cut does — so marking out a region can't silently discard what the user had
copied. Copy runs `captureBackgroundRegion`, and can be pressed again after the clipboard has moved
on; **Cut** captures and then deletes, as **one undoable edit**, the elements `SelectionTester.inRect` finds
wholly inside the region on the page's **active layer** (the same containment and active-layer rule
the rectangle marquee selects by, so a cut never takes ink the user can't currently edit). The page
background is a page attribute rather than part of the region, so it is copied but never erased. The
region is view state only — never recorded in history — and clears on a new marquee drag, on a tool
change (the `backgroundSelectMode` setter), on Back, and on `load`.

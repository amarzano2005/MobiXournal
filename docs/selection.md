# The selection tool: marquee, lasso, handles and transforms

> Read when you change how elements are picked, moved, resized, rotated, aligned or restyled, or the region (background-copy) variant.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Copying and dropping a selection

A copy leaves the canvas in **four flavours at once** (`render/ElementTransfer.kt` and
`render/SelectionTransfer.kt`): `application/vnd.mobixournal.element` carries the elements themselves
(geometry, living recipe and formula source) so another view or window can paste them; `image/svg+xml`
is a vector page of the selection (`PageSvgWriter`); `image/png` is rendered into `cache/clipboard/` and
handed out through the app's `FileProvider`; `text/plain` is a readable summary — a text box's content, a
formula's source, the element kinds. A paste prefers the element flavour, so a duplicated plot arrives
a live plot, not a picture of one.

A **long-press on the selection** lifts it as a system drag carrying that same clip (`render/SelectionDragOut.kt`);
a touch that moves before the timeout stays the ordinary move/resize gesture, and only a finger can lift
one — a stylus is drawing.

**Dropping** accepts the same flavours (plus an image, a PDF or a text file): elements land at the drop
point as one undoable edit and are selected; text is classified by `render/ExternalIngest.kt` — a
CSV/TSV grid becomes a living table whose columns divide the requested width, a short LaTeX line a math
block, prose a text box; an image becomes an `ImageElement`; a PDF is handed to the editor, which appends
its pages. Nothing is imported silently: the drop point is the element's placement.

## Selecting objects (`render/`)

**Both shells select through the same geometry.** `PageSelection` (the traced region, the `ElementRef`s it
caught, and `translate`/`scale`/`rotate`, which answer with the region moved the same way) and
`SelectionEdit` (the page rewrite: move, scale, rotate, delete — one call per committed gesture) live in
`mobix-core`, beside `SelectionOps` and `ElementShift`. The window's pointer half is its own — which handle
a press grabbed, and how far it was dragged — and is described in
[desktop-workstation.md](desktop-workstation.md); the tablet's is `SelectionGestureController`.

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
by construction rather than by coincidence; nothing re-derives a box locally. A **double-tap while a
Select tool is active** opens the editor of the living element under the point — a table cell, a plot,
a component, a figure — through one dispatch (`render/LivingEditTargets.kt`) and one UI seam
(`ui/LivingEditors.kt`), the same target the action bar's **Edit** button opens; ink is not a living
entity, so a double-tap over a plain page stays the marquee (see [mobix-elements.md](mobix-elements.md)
and `LivingEditCoverageTest`). The five **living
entities** (`.mobix`'s table, plot, shape, circuit and math block) answer through the same seam with a box of
their own — a table's grid, a plot's frame, a circuit's compiled ink and a shape's **vertices**, which
are recomputed on every read so a picked figure is always where its corners are — and each carries
its own handles on top (dividers, sliders, vertex dots): see [mobix-elements.md](mobix-elements.md). `SelectionTester` does
rect-containment / topmost
tap / union-bounds, and `SelectionOps` translates or deletes the addressed elements on a page list
(returning a new list; immutable pages/layers share structure so a snapshot stays cheap). What moving a
single element *means* per kind — a stroke's vertices, a table's origin without its column widths, a
shape's corners, a raw element left alone — is `ElementShift` in `mobix-core`, which `SelectionOps.translate`
calls and the vertical-space tool (in both shells) uses too, so a reflow cannot disagree with a drag. Dragging inside
the selection outline translates the elements live (recomputing from the gesture-start document each
frame so there's no drift) and commits as **one undoable edit**. Dragging into the top or bottom
edge band starts a **drag auto-scroll** (`updateDragAutoScroll` + the `autoScrollCallback` frame
loop): the page scrolls one step per frame and the move is re-applied at the finger's last
position (`SelectionGestureController.moveSelectTo`), so the element rides the scrolling sheet even
under a held finger; a floating **Cut / Copy / Duplicate / Recolour / Width / Delete** bar
(`SelectionActionBar` in `ui/EditorActionBars.kt`, placed by `ui/SelectionActionAnchor.kt`) acts on the
selection (all undoable). **Recolour** opens the shared palette as the *compact* form — one horizontally
scrolling row of swatches with no hint line, width-capped at about seven swatches — not the wrapping grid
the dialogs use: this menu hangs off a 32 dp bar on the selection, so it has to read as part of that bar
instead of opening a page-sized sheet of colours over the ink. The same compact row is what the toolbar's
**Colour & size** pop-up shows, so a colour is picked in one shape everywhere on the canvas. That bar **rides with the
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
other mode bars (paste/region, table, PDF-text selection) stay on the bottom edge. The dashed outline and marquee are drawn by the view over the page stack. Two-finger pan still
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

## The window's bar and its inspector lane

A selection is not only something to drag: the window draws the tablet's **action bar** under its outline —
Cut, Copy, Paste, Duplicate, Recolour, Line width, Align & distribute, Edit, Delete — and each is the shared
command, not a second implementation. The clipboard holds model elements, so a copied plot pastes as a live
plot, a paste lands **beside** what it copied and stays selected, `Cut` is the copy and the deletion in one
step, and `Ctrl+D` duplicates. Recolour and width take the value the **strip's own ink** would write, because
that is where those two are chosen. The bar rides below the outline (above it when the page ends below),
clamped inside the canvas, and stands at the page's bottom centre carrying **Paste** when nothing is
selected. A control the selection cannot support is dimmed and does nothing (`docs/interface-contract.md`
§5); the rules live in `SelectionBarUi`, so a JVM test reads them rather than a screen.

**A living element's own grips** are the on-page half of its editor (`LivingGrips.kt`): a parametric figure
wears a ring on each corner, and dragging one bends the figure — the side leading into the corner snaps onto
the 0/30/45/90 directions within the tablet's 7° tolerance, `Shift` frees it — while a table wears a ring on
each **interior** divider, and dragging one widens the column beside it without moving the box or a single
cell's text. Outer edges are not grips (the box's own corner squares scale those), a grip is caught before
those five handles, and the radius is the handles' own screen-pixel rule, so a divider is as easy to catch
zoomed out as in. A plot's interval and a component's outline are **not** grips: those numbers live in the
lane. `LivingGripsTest` pins the arithmetic, `DesktopLivingGripsSceneTest` the gesture (one undo step per
drag, a miss moving the element instead of dividing it).

A **click on an element picks it** — `SelectionTester.pickTopmost`, the topmost element under the pointer — and
the drag that follows carries the new selection, so picking one thing up no longer means boxing it; a click on
empty paper lets it go. The **pencil** on that bar opens the selected element's editor, and is offered only
for exactly one element the window has an editor for; a **double-click on any of them**, selected or not, is
the same command (`TapTracker`: 400 ms, 8 px), so the tablet's double-tap has a twin on the page.
`hasInspector` is that one rule (`LivingFamily.of`, which is the living families **and** a text box);
`DesktopSelectionBarTest` walks the families — plot, table, figure, component, formula, text — and fails if
one does not answer `EDIT`, so a family added later cannot ship uneditable.

That editor is the **inspector lane**: 300 dp of its own column on the right, so the page keeps the
rectangle that is left and nothing lies over the sheet. It is drawn in **three parts, always** — a header, a
body of cards and a footer — and it is up **only while there is something to edit**: a placement asking its
values, the one element the pencil opened, or a **text box being typed** — a box in the field has a face, a
size and an ink too, and the moment to set them is while the words are being written, so pressing with the
Text slot opens the column and committing the box (or its `Done`, its ×, `Esc`) closes both. A selection
alone opens no column, and the lane goes the
moment the selection stops naming the element it belongs to (`attachedLivingEditor`), so moving on to
another element or to empty paper leaves the page its full width again (`DesktopInspectorLaneTest` renders
the lane's own pixel with nothing selected, with a selection, while editing, and after the selection moves
away).

The **header** is the lane's identity, not just its title: the family's own catalogue glyph, the family's
name, and one line of the element's content (`sin(x) · −6…6`, `4 × 6 · header`, `BJT NPN · 10 kΩ`, a text
box's first line and its size) from `livingSummary`, because three plots on a page are all `Plot` and the
fields below write into exactly one of them. The **body** groups the fields into cards — Graph, Function, Display, Look; Grid, Rules, Cells; Component,
Placement, Stroke — so a plot's switches no longer read as one wall of 11 sp labels, and a plot's card leads
with the **graph itself**, compiled by `LivingInk` from the element as it stands, so an equation is no longer
committed blind: the same picture the placement promised follows every edit. A graph carries several
curves, so the editor offers **Add equation**, a remove glyph and a **colour swatch per row**
(`plotWithEquation`, `plotWithoutEquation`) exactly as the placement card does: a new curve arrives in the
next palette ink rather than in the same black as the one above it, and the drawn line is its row's colour. The **footer** is one row of actions shared with
the placement question — Place/Cancel while it asks, Done once the element exists — and it keeps its height,
so the fields above it do not move when a card grows.

The sections offer only what the model carries — a plot's formula, domain, resolution, the
display flags and **one colour per curve**; a table's grid, header row, rules and **cells** (a column added divides
the width rather than growing the table); a figure's side lengths and interior angles as read-outs beside its
outline; a component's part, value, placement and stroke; a formula's source and language; and a **text box's
face** (family, bold, italic), its size and its ink (`InspectorText.kt`) — the tablet's text dialog's own
controls, minus the words, which the Text tool types on the sheet (`docs/drawing-tools.md`). Underline is not
among them because the format cannot store it. A field commits on
`Enter` or when the caret leaves, as **one** undo step, and an unchanged value writes nothing; `Esc` puts a
field back, `Esc` again closes the lane, and a focused field wears the accent's ring so the keyboard user can
see where they are typing. `LivingTarget.kt` resolves the ref against the document as it is *now*, so an undo
cannot leave a panel showing a value the file no longer holds.

**Reading the element is also how the lane follows it.** A page read is not snapshot state, so the column
reads the document's **edit count** (`DesktopDocument.edits`) through `livingTarget`; without that one
read the cards kept the element they were first composed with, and every control wrote a copy of *that*
one — Bold pressed twice stayed bold, the italic after it dropped the bold, and a swatch last in the card
restored the original face. Each control also reports through a holder (`rememberUpdatedState`), because a
`pointerInput` keeps the lambda it was built with; the two halves are what make a row of controls behave
like one editor (`DesktopInspectorLaneTest` clicks the real rows: bold on, bold off, bold+italic, a chip,
a swatch, and reads the element back).

A placement never leaves the tool armed: once the element exists it is what stays selected, and the window
picks the **marquee** (`S`) the way the tablet does, so the next drag moves the element just placed instead of
placing a second one.


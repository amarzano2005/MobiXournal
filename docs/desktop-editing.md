# Desktop editing: figures, placement and the selection

> Read when you change what a drag on the desktop canvas does: a figure, a living element, a selection,
> or the reflow of a page.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

What the window *is* — its shell and its strips — is [desktop-workstation.md](desktop-workstation.md); what it
does with a file is [desktop-documents.md](desktop-documents.md); the pen and the dial are
[desktop-tablet.md](desktop-tablet.md); and what a selection means, on both shells, is
[selection.md](selection.md).

## Where a press lands

A press maps to the page through **the viewport the sheet was painted in**, and no other:
`SheetLayout.liveViewport` is `Viewport.inChrome` over the window's live scroll — the rule `paintSheet`
draws with — so ink, a marquee, a figure and a living drag all land under the pointer. A second
derivation (clamping the scroll in the *whole* window instead of centring in the room beside the rail)
put a fitted page's ink half a rail's lane away from the pointer, and the gap shrank as the reader
zoomed in, which reads as a zoom bug and is not one. `PointerMappingTest` holds the arithmetic and
`PagePressSceneTest` a stroke through Compose's own dispatch on a fitted page.

## Figures, whole-stroke erasing and vertical space

Four more gestures beside the ink, all built on `mobix-core` so the window has no geometry of its own:

- **Seven geometry slots draw figures.** Line, Arrow, Double arrow, Axes (the shared
  `ShapeKind.COORDINATE_AXIS`), Rectangle, Ellipse and Polygon each turn one drag into an ordinary
  constant-width stroke through `ShapeBuilder` — the tablet's own builder — so a figure made here
  round-trips through `.xopp` as ink. The preview is the **same draft list** the sheet already paints, and
  the outline cached for it is keyed by the **box the draft covers**, not by its last vertex: a closed
  figure's last vertex is the corner the press landed on, so a key of that handed back one frame's outline
  for the whole drag (`StrokePathCacheTest`).
  `Shift` snaps the drag's direction to 45°, a drag shorter than 4 pt draws nothing, and `Esc` cancels
  through `DesktopShellState.liveDrag`. With a geometry slot picked the tablet's **packets stand
  aside**: a pen contact must not ink under a figure being dragged.
- **The eraser's two members are two slots.** `Eraser` (`E`) cuts the disc's path out of the stroke it
  crosses; `Eraser (whole)` (`Shift+E`) removes every stroke the disc touches. They differ by
  `DesktopTool.eraserMode()` (`EraserMode.STANDARD` / `WHOLE_STROKE`), which `PageInkGesture` passes to
  the same shared `PageEraser`, and one erased gesture is still one undo step either way.
- **Vertical space reflows the page.** `Vertical space` (`Shift+V`) drops a grab line where the press
  landed, painted as a hairline across the sheet above the ink; each move re-applies
  `VerticalSpaceOps.shiftBelow` to the page **as it was at grab time**, so the block follows the pointer
  without recruiting the elements it passes, and the whole drag is one undo step. A drag that shifts
  nothing records nothing. Snapping stays **off** (`snapSpacingPt = 0.0`, a continuous drag): the tablet
  snaps to the ruling only when its Snap-to-grid setting is on, and the window has no such setting yet.
  Like a geometry slot, the drag is **not ink**, so the pen's packets stand aside for it too
  (`DesktopTool.inksByPackets()`): only the pen and the two erasers are fed from the driver. That is what
  makes the tool reachable with a stylus at all — while the packets owned the pen the Compose gesture stood
  aside, so a pen in proximity opened a contact and the grab line never moved. `DesktopVerticalSpaceTest`
  drags the grab line with a live packet stream and checks the block follows it.
  The arithmetic is shared: `VerticalSpaceOps`, its `Snapping` and the per-element move (`ElementShift`)
  live in `mobix-core`, and the app's `SelectionOps.translate` delegates to the same `ElementShift`.

## Placing a living element: Plot, Table and Circuit

Plot (`M`) and Table (`T`) place a living element instead of drawing it: a drag is its box, a click its
family's default size, then the lift **asks first** in this lane — the graph dialog's fields, or the table's
grid — before committing **one** element as **one undo step**; a cancelled question leaves nothing behind. A
**circuit component** is asked nothing: its drag's two **ends** are the part — rotated to the hand and snapped
onto the pins in reach — drawn **live** along the pen where it will land
(`DesktopCircuitPreviewTest`). The placement hands the window to the **marquee** (`S`), as the tablet does, so
the next drag moves what was just made; the **pencil**, or a **double-click** on the element, reopens the same
lane on it ([selection.md](selection.md) owns the grips). Geometry, builders and the family palettes
(`CircuitFamily`: passive `K`, active `Shift+K`, logic `Alt+K`) are shared with the tablet, and a slot's
submenu stays its **second** click, a circuit palette included (`ToolSlotClickTest` drives the scene).

## Selecting, moving, resizing and turning

The window's selection is the tablet's, cut along the seam the core already had. **Marquee** (`S`) and
**Lasso** (`V`) share one slot, so the strip still shows one "pick things" tool: a **drag** traces a region
(the straight box, or the freehand loop) which is painted as it is drawn, and the **lift** selects every
element wholly inside it; a **press inside the live outline** acts on the selection instead of starting a
new one — the body moves it, a **corner handle** scales it about the opposite corner, and the **handle above
it** turns it about its centre (`Shift` snaps the turn to 15°). Both paths recompute from the page **as it
was at grab time**, so dragging back and forth cannot compound, and one drag is one edit and **one undo
step**. `Delete` (or `Backspace`) removes what is caught, also as one step; `Esc` drops the outline before it
means anything else. A selection belongs to the page it was drawn on: turning the page lets it go, because
its refs name a layer and an index *of that page*.

The arithmetic is core, not window: `PageSelection` (the region, the caught refs, and translate/scale/rotate
that answer with the region moved the same way), `SelectionEdit` (the page rewrite) and `SelectionOps` live in
`mobix-core` and are shared with the tablet, alongside `ElementShift` and `VerticalSpaceOps`. What stays here
is the pointer half — `DesktopSelection.kt` decides *which* handle a press grabbed and *how far* it was
dragged (`selectionHandleAt`, `scaleFactors`, `rotationBetween`, all pure and JVM-tested), `SelectionGesture.kt`
walks the drag, and `SelectionOverlay.kt` paints the outline, the five handles and the traced region. A
selection drag is **not ink**, so `DesktopTool.inksByPackets()` stays false for the slots: the pen's packets
stand aside and a stylus in proximity still reaches the drag through the Compose pointer. A selection carries
the tablet's **action bar** under its outline, and the **pencil** on it opens the selected living element in
the inspector lane — both owned by [selection.md](selection.md).

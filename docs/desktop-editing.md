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

- **Six geometry slots draw figures.** Line, Arrow (its double-headed twin in the slot's menu), Axes (the shared
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
- **Vertical space reflows the page — but only on the lift.** `Vertical space` (`Shift+V`) drops a grab
  line where the press landed, painted as a hairline across the sheet above the ink. While the hand is down
  the page is **not edited at all**: it is recorded once in two halves (`ShiftPictures`) — the elements the
  line leaves and the block it carries, read at grab time with `VerticalSpaceOps.blockBelow` — and a sample
  publishes only the offset (`LiveShift`), so the sheet replays `still` and then `moving` under a translate.
  That is desktop Xournal++'s own design (a mask painted under a translate, one `MoveUndoAction` at the
  end), and it is what makes a drag cost a scroll: rewriting the page per sample measured 5.2 ms of reflow
  plus a full re-record, with the sheet re-recorded 40-plus times a second and a worst frame of 146 ms;
  the preview holds one re-record a second, 0.1 ms of paint per frame and no page copy at all. The lift
  applies `VerticalSpaceOps.shiftBlock` to the page **as it was at grab time** — one undo step, nothing
  recorded when the drag shifted nothing — so the block never recruits the elements it passes. The split is
  by element, never by pixels, so an element the line crosses stays whole; the block is then painted *over*
  the ink it passes, which is the one difference from an exact reflow and the trade the reference tool makes
  too. Snapping stays **off** (`snapSpacingPt = 0.0`, a continuous drag): the tablet
  snaps to the ruling only when its Snap-to-grid setting is on, and the window has no such setting yet.
  Like a geometry slot, the drag is **not ink**, so the pen's packets stand aside for it too
  (`DesktopTool.inksByPackets()`): only the pen and the two erasers are fed from the driver. That is what
  makes the tool reachable with a stylus at all — while the packets owned the pen the Compose gesture stood
  aside, so a pen in proximity opened a contact and the grab line never moved. `DesktopVerticalSpaceTest`
  drags the grab line with a live packet stream and checks the block follows it.
  The arithmetic is shared: `VerticalSpaceOps`, its `Snapping` and the per-element move (`ElementShift`)
  live in `mobix-core`, and the app's `SelectionOps.translate` delegates to the same `ElementShift`.

## Posing a drawing guide

A placed setsquare or compass is **not ink and not an edit**: it is view state on the tab
(`DesktopGuidePose`), and dragging it writes no page edit and no undo step. The pure half is
`GuideGesture` — `guidePress` asks the shared `DrawingGuide.hasHandle`/`grabHandle`/`grabbableBody` for the
part a press took, and `guideDragged` re-poses the instrument from the pose **as it was at grab time**, so a
drag cannot compound its own error. A press on the **body** slides it (the offset between press and anchor is
kept, so it does not jump under the hand); a press on the **handle** — the setsquare's long-leg tip, the
compass's pencil point — turns it or opens it through the tablet's own `aimedAt`/`openedTo`, and `Shift`
lands the angle on a step of the shared `Snapping.snapAngle` (0°, 45° and 90° exactly). The drawing edges are
**not** grabbable: they belong to the pen.

**Who may hold it** is the tablet's rule, not a second one: a pen draws and a hand holds. A mouse or a touch
press always poses the instrument; a stylus press does so only while the guides slot is the tool in hand,
because otherwise a stroke drawn across the instrument's body would drag it away instead of drawing along it.
`Esc` puts the grab-time pose back through `DesktopShellState.liveDrag`. The reach is a **screen** distance
(`GUIDE_HANDLE_PX` divided by zoom and density), so a handle that is easy to hit at 30 % is no coin at 300 %.

**What it does to the ink** is the tablet's own rule: every vertex drawn within `DrawingGuide.GRAB_PT`
of one of its edges is pulled onto it, in the preview and in the stroke that lands, so a freehand line ruled
along the setsquare is straight in the file. A vertex further away is the hand's own, which is what lets the pen
leave the instrument without switching it off; a guide pinned to another page constrains nothing. The stroke
written is ordinary ink of the tool's width — one undo step, and nothing about the guide reaches the document.

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
it** turns it about its centre (`Shift` snaps the turn to 15°). A **line** — a box only as thick as its pen —
wears just its two end handles, and either of them lengthens it without thickening it; a press on the band
above or below it carries it. Both paths recompute from the page **as it
was at grab time**, so dragging back and forth cannot compound, and one drag is one edit and **one undo
step**. `Delete` (or `Backspace`) removes what is caught, also as one step; `Esc` drops the outline before it
means anything else. A selection belongs to the page it was drawn on: turning the page lets it go, because
its refs name a layer and an index *of that page*.

The arithmetic is core, not window: `PageSelection` (the region, the caught refs, and translate/scale/rotate
that answer with the region moved the same way), `SelectionEdit` (the page rewrite) and `SelectionOps` live in
`mobix-core` and are shared with the tablet, alongside `ElementShift` and `VerticalSpaceOps`. What stays here
is the pointer half — `DesktopSelection.kt` decides *which* handle a press grabbed and *how far* it was
dragged (`selectionHandleAt`, `scaleFactors`, `rotationBetween`, all pure and JVM-tested), `SelectionGesture.kt`
walks the drag, and `SelectionOverlay.kt` paints the outline, the eight handles, a living element's grips and
the traced region. A
selection drag is **not ink**, so `DesktopTool.inksByPackets()` stays false for the slots: the pen's packets
stand aside and a stylus in proximity still reaches the drag through the Compose pointer. A selection carries
the tablet's **action bar** under its outline, and the **pencil** on it opens the selected living element in
the inspector lane — both owned by [selection.md](selection.md). A **double-click inside the live outline**
belongs to the selection, never to what lies beneath it: the group is never replaced by the one element the
second click landed on, and only a selection of exactly one living element opens its editor — the tablet's
double-tap, which the pencil already is.

The drag writes the page **per sample** (each sample transforms the caught elements against the grab-time
snapshot) and the sheet therefore records itself again on the frame that follows, which is the price this
gesture pays and has been measured rather than assumed: one sample costs the model ~0.2 ms and the frame
~1 ms of recording on the written sheet `DesktopWritingCostTest` uses (a frame that replays costs ~0.02 ms).
Against the vertical-space drag's 5.2 ms of reflow per sample — which re-recorded the sheet 48 times a second
— that is not the jank source, so this gesture deliberately keeps the simple write-then-record shape instead
of a second two-half preview (`ShiftPictures` exists for the tool that needed it). `DesktopSelectionCostTest`
pins the numbers: **one record per frame, never one per sample**, a replay that stays an order of magnitude
cheaper than a record, and a sample whose cost is the selection rather than the page. A change that makes the
drag copy the whole sheet per pointer event fails that test instead of reaching a hand.

## Reading a PDF page: text selection and the magnetic highlighter

Two gestures treat a PDF page as **text** rather than as paper, and neither writes to the file:

- **Select text (PDF)** (`Shift+S`) is the tablet's slot: the press anchors
  `PdfTextIndex.anchorWord` — inside the word under the pointer, else the nearest one — and the drag
  extends the focus word on the same page, so the range is the page's own reading order. The overlay
  washes each word box (`drawPdfTextSelection`), `Ctrl+C` or the selection's **Copy** bar puts
  `rangeText` on the system clipboard, and `Esc`, a click off the text or a page change drops it. It is
  **view state**: no page edit, no undo step, and a select-copy-save leaves the file byte-identical.
- **The highlighter is magnetic** over a page whose background is a PDF with a text layer: while the
  drag crosses text, `MagneticHighlighter.bands` are previewed as flat bands **in place of** the freehand
  path (`PenPointer.pdfBands`), and the lift commits `MagneticHighlighter.strokes` — ordinary two-point
  constant-width highlighter ink, one undo step. Too short, too steep, over no text or across more than
  `MAX_LINES` lines and the freehand stroke commits unchanged; a page with no text layer (a `.mobix`, a
  photograph, a scan) behaves exactly as before. Both doors — the Compose pointer and the tablet's
  packet stream — publish the same bands, so a stylus snaps like a mouse.

Both read the document's one index (`DesktopPdfIndex`, published by `PdfIndexLoader` off the UI thread)
and resolve a page's words by its `Background.Pdf.pageNo`, never by the document's page order
(`DesktopPdfTextSelectionTest`, `DesktopMagneticHighlightTest`).

## Images: insertion, clipboard, drop and aspect-ratio handles

Images are living document elements (ImageElement) on the active layer:

- **Three entry paths**:
  - **Tool strip**: The image slot (Picture icon) opens a file dialog filtering PNG and JPEG.
  - **System clipboard**: Ctrl+V (or the selection bar's paste button) ingests an image from the OS
    clipboard (DataFlavor.imageFlavor) when the internal selection clipboard is empty.
  - **Drag-and-drop**: Dropping an image file onto the window displays a hover overlay and places the
    element on the page. Dropping a PDF or notebook routes to document open or PDF append workflows.
- **Placement geometry**: Initial placement preserves the image's native aspect ratio, sized to fit
  comfortably within the visible page bounds and recorded as a single undo step.
- **Selection and aspect-ratio handles**:
  - Every selection — an image, ink, a living figure — wears the selection's eight handles: the four corner
    squares and the four edge-midpoint ones. An image gives up only the rotate handle, matching the format's
    unrotated image model.
  - **Corner handles**: a corner drag keeps the element's proportions, as the tablet's does, and **Shift**
    frees them for a free non-uniform scale.
  - **Edge handles**: a side drag stretches only that axis (anchor is the opposite edge) — the choice between
    a horizontal and a vertical stretch that the tablet's side dots offer.
  - **Non-resampling**: Scaling modifies bounding box coordinates (left, top, right, bottom) without decoding,
    re-encoding, or resampling raw image bytes, recording one undo step per gesture.
- **Round-trip fidelity**: Preserved losslessly in both .mobix and .xopp containers under the sticky
  per-document format policy.

## Searching the page, and the pick's clipboard of pages

**Search** paints its current match on the sheet as chrome — the accent wash and a hairline over the words
or formula the engine found, at the PDF text selection's translucency, never ink and never written to the
file. `Enter` and a click on a result row both go to the match's page, so the page on screen is always the
one the highlight is on ([search.md](search.md) owns the search itself).

**The sidebar's pick is a clipboard of pages**, not of elements: **Copy**, **Cut** and **Paste** in the bar
that replaces the layers list act on whole sheets, one undo step each way, and a page pasted into another tab
arrives as a page of its own — fresh page and element ids, so a `.mobix` merge cannot fold the copy into its
original (`DesktopPageClipboardTest`). A page whose sheet is a PDF can only land where that PDF is; the
status bar says so rather than leaving a reference pointing at nothing.

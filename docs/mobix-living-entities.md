# The living entities: table, plot, shape, circuit, math block

> Read when implementing or changing a re-editable `.mobix` entity — the constructs a `.xopp` can only bake into dead ink. The schema's rules and the geometry elements are [mobix-elements.md](mobix-elements.md)'s.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns **what a living element is** on disk, per family, and the one rule they share. The
element envelope and the geometry elements are [mobix-elements.md](mobix-elements.md); the code is one
codec per family — `MobixTableCodec`, `MobixPlotCodec`, `MobixShapeCodec`, `MobixCircuitCodec`,
`MobixMathCodec` — reached by `type`, over the JSON builders they share in `MobixJson.kt`.

## Living entities

The constructs `.xopp` can only bake into dead ink. Each family has its own codec — `MobixTableCodec`,
`MobixPlotCodec`, `MobixShapeCodec`, `MobixCircuitCodec`, `MobixMathCodec` — reached by `type`, and all five
obey one rule stricter than rule 1:

**A codec models an element or preserves it whole, and never half-reads one.** An unknown *element
type* survives as raw JSON (rule 1); so does a **known family this build cannot read** — a cell whose
`align` has no name here, a curve `kind` from a newer build, a `parameters` entry that is not a number.
The model has no channel for a field it did not interpret, so modelling three of a plot's fields and
writing them back would turn the user's document into a different one. The refusal is per *element*,
never per file: the document still opens, and the unreadable element is still in it, byte for byte.

Two field names are deliberately **open key sets rather than enums**, for the same reason: a shape's
`shape` and a circuit's `symbol` name a kind in a library that keeps growing. Geometry is what a reader
has to understand — the vertices, the placement, the pins — so an unrecognised kind is left to the
library that knows it, and refused by nobody.

**One definition draws them, everywhere** (decided 2026-10-12). The compilers below are the drawing — a
table's grid and cell text, a plot's frame and curves, a shape's outline, a component's symbol — and
`render/LivingInk.kt` is the single dispatch around them, used by the canvas (`PageRenderer`), the vector
PDF painter and the SVG writer. An export and the screen therefore cannot disagree about what an entity
looks like: they compile the same element with the same code. Compiled ink is memoised by element
identity, so a table is compiled once, not once per frame.

**The plot is the first family to become editable** (same decision). It is the one entity the app can
already author — the graph dialog's output *is* a list of curves and a range — so it inserts a **living
`PlotElement`** where ink used to be baked (`render/PlotElementFactory.kt`, on the placement
`insertMultiPlot` had always computed). `ElementBounds` gives it a hit-testable frame box, so marquee,
lasso and tap pick it, drag moves it (translating the frame while keeping the mathematical ranges),
handles resize it and delete removes it (`render/SelectionOps.kt`, `render/LivingEntitySelectionTest.kt`).
A single selected plot offers **Edit plot**, which opens `PlotInspectorAnchor` beside the selection,
pre-filled with its curves, range, flags and shaded area; confirming applies in place
(`PlotElementFactory.applyPlotEdit`), preserving the fields the panel has no input for (`samples`,
`parameters`, `axisColor`, `id`), undoable and re-rendered at once. The panel **owns its place**:
anchored once beside the plot it opened on, never re-anchored by an Apply, a curve row or a viewport
change, its editing state keyed to the *open* rather than the element so what was typed survives an
Apply, and its absolute canvas-bounded drag always movable. Because it is anchored the IME can cover
it, so the focused field is mirrored into a one-line cell above the keyboard (`ui/PlotInputEcho.kt`).
The dialog owns the shaded integral (`shadeIntegral`), whose from/to pair drives the wash, the
summary's integral and the exported ink ([math-engine.md](math-engine.md), section 3).
The table is the second family to become editable: a living `TableElement` authored by the table tool
(`render/TableElementFactory.kt`), with its grid box and hit-testability from `render/ElementBounds.kt` and
move/resize from `render/SelectionOps.kt` (a move shifts the origin and keeps `colWidths`/`rowHeights`
authoritative; handles rescale the grid, leaving the rule thickness alone — a resize stretches geometry,
never ink: `docs/selection.md`).

The circuit component is the third family: a living `CircuitElement` authored by circuit tools
(`render/CircuitElementFactory.kt`), with a hit-testable rotated stroke box and move/rotate/restyle from
`render/SelectionOps.kt`, edited in place via `ui/CircuitEditorDialog.kt` (value, stroke, rotation, colour,
`labelSide`, pins). Its value has a keyboard of its own instead of the system IME — see `circuit` below.

The parametric shape is the fourth family: a closed polygon the recogniser keeps as **geometry** instead
of ink (`render/ParametricShapes.kt`), with a stroke box `render/ElementBounds.kt` **recomputes from its
vertices on every read** and move/resize/restyle from `render/SelectionOps.kt`. It is edited on the page
— a handle on each corner (`render/DrawingSurfacePaint.kt`, hit-tested in
`render/SelectionGestureController.kt`), and dragging one deforms the figure live with the side leading
into it snapped to 0/30/45/90° — and **Edit shape** on the selection bar switches its two readout
badges (`ui/ShapeEditorDialog.kt`).

**A double-tap in the Select tool opens whichever family is under the finger** — a table cell, a plot, a
component, a figure — through one dispatch (`render/LivingEditTargets.kt`) and one UI seam
(`ui/LivingEditors.kt`), the same target the action bar's **Edit** buttons open (`LivingEditCoverageTest`).
Ink is not a living element, and outside Select each tool's second tap keeps its own meaning.

**A label never sits on the element it describes**: every family lays its writing out from the *drawn*
geometry — a value around the symbol, a figure's lengths outside their edges and its angles inside a
corner (or outside one too sharp to hold them), a cell's text inside its cell, a plot's numbers and
summary outside its frame — guarded by `LivingLabelClearanceTest`.

The `.xopp` synthesis export compiles living entities into standard `.xopp` ink before a file leaves
the app ([mobix-format.md](mobix-format.md)); a dragged corner is simply the figure's new outline, so
the export needs no knowledge of the edit.

### Table (`table`)

```json
{
  "type": "table", "id": "tbl_01",
  "bounds": { "left": 72.0, "top": 120.0, "right": 472.0, "bottom": 196.0 },
  "colWidths": [120.0, 180.0, 100.0], "rowHeights": [28.0, 24.0, 24.0],
  "headerRow": true, "borderColor": "#212121ff", "borderWidth": 1.0,
  "cells": [
    [ { "text": "Parametro", "align": "CENTER", "bold": true, "fillColor": "#00000015" },
      { "text": "Formula",   "align": "CENTER", "bold": true, "fillColor": "#00000015" },
      { "text": "Valore",    "align": "CENTER", "bold": true, "fillColor": "#00000015" } ],
    [ { "text": "Frequenza", "align": "LEFT",  "bold": false, "fillColor": null },
      { "text": "f = 1 / T", "align": "CENTER", "bold": false, "fillColor": null },
      { "text": "50 Hz",     "align": "RIGHT",  "bold": false, "fillColor": null } ]
  ]
}
```

`colWidths`/`rowHeights` are the authority for the grid — `bounds` is their sum offset by the table's
origin — so inserting a row or dragging a column is a change to those lists. `cells` is
`rowHeights.size` by `colWidths.size`; a short row reads as empty cells rather than a corrupt table.

`bounds.left`/`bounds.top` are that **origin**, and are a fact (nothing else states where the grid
sits); the width and height are the sums above. So a file whose cached `right`/`bottom` disagree with
the grid is read at the grid's size and rewritten with the box the grid implies — rule 3, applied to
the one box that has a stored half and a derived half.

The cells are edited through a **fixed editor dialog** (`ui/TableCellEditorDialog.kt`) — the
anchored floating inspector this replaced was fragile next to the on-screen keyboard (the app
draws edge-to-edge, so `adjustResize` gives it nothing to clamp against) and could only edit one
cell at a time. Fixed is literal: the dialog has no drag handle and nothing about the canvas may
shift it, so the cell under the stylus stays where the last tap left it. Opened by a double tap on a cell or by **Edit table** in the selection bar, the
dialog shows every cell's text at once in the table's own shape with horizontal and vertical
scrolling for wide grids, steppers to add/remove rows and columns, keyboard navigation (Tab,
Shift+Tab, Enter) with automatic row expansion on the final cell, and align/bold acting on the
focused cell. A column added mid-edit arrives with its **header cell already bold** whenever the
table has a header row (`headerRow`, or a first row whose cells are bold), so a new column never
lands plain beside a bold header. Nothing tracks the canvas: the dialog is a fixed surface, so the keyboard
can never cover it mid-edit.

**Save** is the one commit point: the whole grid is swapped in through
`DrawingSurfaceView.replaceTableElement` — one element edit, one undo step — and that is exactly
what the canvas then draws. **Cancel** (and dismiss-on-back) closes without saving, so the
pre-edit state never leaves the table in a half-committed shape; there is no per-keystroke
commit inside the dialog at all.

### Function plot (`plot`)

```json
{
  "type": "plot", "id": "plt_01",
  "bounds": { "left": 100.0, "top": 200.0, "right": 400.0, "bottom": 380.0 },
  "xMin": -6.28, "xMax": 6.28, "samples": 300,
  "showCriticalPoints": true, "includeDerivative": false,
  "shadeIntegral": { "from": 0.0, "to": 3.14 },
  "curves": [
    { "kind": "CARTESIAN", "source": "sin(x) * exp(-0.1 * x)",
      "color": "#d32f2fff", "strokeWidth": 1.5, "lineStyle": "PLAIN" }
  ],
  "parameters": { "a": 1.0, "b": 2.0 },
  "axisColor": "#37474fff",
  "summaryText": "Roots: x = {0.0, 3.14, -3.14}..."
}
```

- `curves[].kind` is `CARTESIAN` (`y = f(x)` over `xMin..xMax`), `PARAMETRIC` (the curve then also
  carries `tMin`/`tMax` and `sourceY`) or `POLAR` (`r(θ)` over `thetaMin`/`thetaMax`). The
  parameterised kinds are named by their mathematics, not by a second parser: this app writes the
  variable as the expression parser's own `x` (the graph dialog's parametric tab enters `cos(x)`,
  `sin(x)`), and a file that writes the parameter as `theta` or `t` instead is understood by renaming
  that variable before the formula is evaluated — the notation in the sentence above is the file's,
  and losing a curve over the spelling of its variable would be the reader's fault. A plot draws its own
  frame (`PlotInk`), so there is no `axes` element: the tablet's Axes tool is the coordinate-axis
  **stroke figure** (`ShapeKind.COORDINATE_AXIS`), which both shells already have, and no third one is added.
- Every curve carries **its own `color`**, edited per equation through the row's colour dot in both shells:
  a new equation takes the next palette colour, the first keeps the tool's ink (`PendingPlacement.formulaColours`).
- `parameters` holds the named constants an expression may reference; a slider re-evaluates the plot
  rather than editing the formula ([math-engine.md](math-engine.md)).
- `summaryText` is a **cache of what was computed**, never the source of truth, and recomputed on any change.

### Parametric shape (`parametricShape`)

```json
{
  "type": "parametricShape", "id": "shp_01", "shape": "TRIANGLE_SCALENE",
  "bounds": { "left": 100.0, "top": 100.0, "right": 250.0, "bottom": 240.0 },
  "vertices": [ { "x": 100.0, "y": 100.0 }, { "x": 250.0, "y": 120.0 }, { "x": 160.0, "y": 240.0 } ],
  "strokeColor": "#0d47a1ff", "strokeWidth": 2.0, "fillColor": "#2196f31a",
  "showAngles": true, "showSideLengths": true
}
```

`vertices` are the *authored* geometry and the handles the editor drags. A kind defined by an angle set
(a scalene triangle or trapezoid, [drawing-tools.md](drawing-tools.md)) will keep its exact angles by
re-fitting the vertices on every drag, so the two never disagree on screen; the kinds the recogniser
commits today (`TRIANGLE`, `RECTANGLE`) carry no angle set, so a dragged corner moves that corner and
leaves the rest exactly as drawn. `bounds` is written for a reader that only wants to cull, and is
**recomputed from the vertices** on read: a shape can never be picked where its handles are not.

`showAngles` and `showSideLengths` are its two readout badges. `ShapeInk` draws them through the same
functions the handles report (`ParametricShapes.sideLengthPt` / `interiorAngleDeg`), so a badge can
never disagree with the figure under the pen; they are ordinary text, on the sheet and in the `.xopp`
export (`XoppSynthesis`) alike.

What becomes a shape, and what does not: a **closed polygon** the pen drew (triangle, rectangle) is
committed as one `ParametricShapeElement` instead of the stroke it would have flattened into. Circles
and ellipses keep the recogniser's stroke — their "vertices" would be sampling points of an
approximation rather than corners, and this element has no centre and radius to drag instead. A
highlighter figure and a dashed one also stay strokes: a `parametricShape` carries one outline colour
and width and no line style, so neither would survive the round trip.

### Circuit component (`circuit`)

```json
{
  "type": "circuit", "id": "ckt_res_01", "symbol": "RESISTOR",
  "value": "10 kΩ", "labelSide": "below",
  "position": { "x": 300.0, "y": 150.0 }, "rotation": 90.0,
  "strokeColor": "#d84315ff", "strokeWidth": 1.6,
  "pins": [
    { "id": "p1", "relX": -24.0, "relY": 0.0, "connectedTo": "net_node_A" },
    { "id": "p2", "relX": 24.0, "relY": 0.0, "connectedTo": "net_node_B" }
  ]
}
```

`labelSide` places the `value` on one of four sides of the drawn symbol — `"above"` (the default, and
so omitted), `"below"`, `"left"` or `"right"` — a *side of the drawing*, not a coordinate: the label
is laid out **around** the symbol (centred across it for above/below, along it for left/right, with
air between), so it never lies over the drawing — which it used to do once a component was rotated
(the ink turns, a value does not). The inspector chooses the side; its **value line** is a line of text
and not a text field, so a tap anywhere on it opens the **circuit keyboard** docked at the foot of the
canvas — digits, designator letters and the circuit symbols on three pages, in place of the system IME —
and no system keyboard is ever asked for (`render/CircuitInk.kt`, `ui/CircuitEditorDialog.kt`); the key
it replaced, `labelBelow: true` is still read. The keyboard is capped at 480 dp wide — keys stay keys on a
tablet — and sits clear of the gesture bar, the panel above laid out against the space it leaves.

`pins` are the magnetic anchors, relative to `position` and before `rotation`; `connectedTo` names the
net a pin is snapped to and is omitted for a floating pin. The magnet serves **connector gestures only**:
a wire drawn with the line tool and a component being placed or moved are pulled onto a nearby **pin**.
While a connector is armed every pin is marked with a small ring (and the pin the live gesture has caught
fills in), so the target can be seen before the wire is drawn; the magnet's reach is `SNAP_THRESHOLD_PT`
in page points with a floor of `SNAP_REACH_PX` screen pixels, so a zoomed-out page can still be wired.

A **component being placed** aims at one more thing: the ends of the **wires already on the page**
(`collectPlacementTargets`) — a pin meeting a wire's end, seen from the other side, so a wire drawn before
the part it feeds still meets it. Only a wire's ends are offered, and only to a component; the line tool aims
at pins alone and a pen stroke is never a target, so drawing beside a line never wires anything. A **wire being moved** is held to that same rule — it aims at pins, never at another wire's end — so a joint the magnet makes is always circuit↔line or circuit↔circuit, and two lines beside each other stay two lines.

Placing a component is also the one gesture whose **own** pins are drawn while it is still under the
stylus: `previewTargets` runs the factory's placement rule on the live drag, so the rings the user lines
up against a wire end are the pins the commit mints (a tap previews the default-size part it drops).
Ordinary figures — rectangles, bonds, the chemist's ring, a recognised freehand shape —
keep exactly the geometry drawn; their only optional pull is the page ruling under *Snap to grid*
(`render/CircuitSnapping.kt`, `render/CircuitElementFactory.kt`). A circuit with no `pins` is a plain symbol —
the shape library's own drawings need no connectivity to be edited as a shape. A component has **no
`bounds` at all**: its outline is the symbol library's, and a copy of a drawing this format does not
own would be a copy that can go stale.

`GROUND` keeps two pins like every other part, but they are **the ends of the symbol itself**: `p1` is
the terminal a wire meets and `p2` the far end of its plates, so the drag sizes the earth exactly as it
sizes a resistor, and its ink runs from `p1` to `p2` instead of hanging under the middle of a lead
(`render/CircuitInk.kt`, `render/CircuitShapes.kt`).

`strokeColor`/`strokeWidth` **are** here, because they are the user's choice and not the library's: the
drawing is what leaves this app for a desktop, so an export that assumed an ink would turn a red
resistor black. `symbol` names a key in that library (and, like a shape's `shape`, the key set is open):
a build that does not know a symbol cannot draw it, and says so rather than inventing ink for it
(`docs/mobix-format.md`, the lossy-field rule).

### Math block (`mathBlock`)

`MathBlockElement` is the living formula: its `source` in `flavor` (`LATEX`/`TYPST`), colour and box, and
**no rendered bytes** — the drawing is a function of the source. `MobixMathCodec` spells it; the canvas
typesets it with `LatexRenderer`, the window and the PDF plan through `LatexLayout` (`TexPainter`), and the
`.xopp` bridge compiles it to a `<teximage>` (`MathBlockInk`), the ink `LivingInk` produces. **The window writes
one too**: the Text slot's Formula member (`Shift+T`) asks for the source, previews it and writes it on **Place**
(`PendingPlacement.mathSource`, `MathPreview`), and an unknown type stays verbatim (rule 1).

**A family is edited where it is drawn**: the window's selection carries the element's own grips
(`LivingGrips.kt`) — a figure's corners through `ParametricShapes.snappedVertex`, a table's dividers rewriting
`colWidths`/`rowHeights` without touching `cells`; a plot's interval and a component's outline have none.

# The `.mobix` element schema: strokes, living entities and preservation

> Read when implementing or changing any element type inside a `.mobix` document — geometry elements, living entities (tables, plots, shapes, circuits), or the rules that make an unknown element survive a round trip.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns **what an element is** on disk. The container, the manifest and the page envelope are
[mobix-schema-spec.md](mobix-schema-spec.md); why the format exists at all is
[mobix-format.md](mobix-format.md). The code is `format/mobix/MobixElementCodec.kt` — the element
envelope and the four `.xopp`-shaped elements — plus one codec per living family
(`MobixTableCodec`, `MobixPlotCodec`, `MobixShapeCodec`, `MobixCircuitCodec`), over the JSON builders
they share in `MobixJson.kt`.

## The four rules every element obeys

1. **A `type` discriminator decides everything.** A reader that knows the type models the element; a
   reader that does not **preserves it verbatim** as a `RawElement` in JSON syntax. That is the rule
   the whole format is built on: a `.mobix` written by a newer app — carrying a table, a plot, a
   `3dPlotElement` nobody has written yet — survives an open-and-save here byte-for-byte in value, so
   an older build can never quietly strip a newer document.
2. **An `id`, minted once and never re-minted.** Identity is what the merge keys on
   ([mobix-format.md](mobix-format.md) → *Merging two copies is library code*) and what lets an edit change an element without the
   element becoming a different one. A copy's *duplicate* is a new id; a move is the same id. A reader
   that finds an element without one mints it (`ElementId.mint`, `format/model/ElementIds.kt`), which
   is how a `.xopp` import — a format with no ids at all — gets its elements identified exactly once.
   The spelling is `<kind>_<12 hex>`: `stk_3f9c1a7b2e40`.
3. **`bounds` is a cache, written but never trusted.** A writer includes it when the element's own
   geometry determines it (a stroke's extents, an image's box); a reader **re-derives** rather than
   believing it, so a stale value in a file cannot become the document's truth. It exists so a reader
   that only wants to cull or hit-test does not have to parse the element.
4. **What we cannot interpret is kept.** `attrs` carries the attributes the `.xopp` bridge must not
   lose (`ts`, `fn`, anything a newer desktop writes), and an **element that only exists in `.xopp`**
   is marked with `"syntax": "xml"` so it is written back as the XML element it came from.

**What is *not* preserved, deliberately:** an unknown *field* inside a known element type. The model
has no room for one, and inventing a channel would put a second, ambiguous copy of the schema inside
`attrs`. `formatVersion` is the tool for a breaking addition — a version a reader does not know is
refused rather than half-read (see the compatibility rules in
[mobix-schema-spec.md](mobix-schema-spec.md)). The **envelope** (manifest, canvas, part entries) does
preserve unknown fields; the element layer does not.

### Vocabulary the schema uses

Values are written as the **names the app uses**, not as desktop XML spellings, and each is checked on
read: a value this build does not know is **refused** rather than silently defaulted, because a stroke
whose tool we misread is a rendering bug we want to hear about. "Refused" means the *element* is not
modelled — it goes back to the preserved channel whole, and the document itself still opens (see the
rule for the living families below).

| Field | Values |
|---|---|
| `tool` | `PEN`, `HIGHLIGHTER`, `ERASER` |
| `lineStyle` | `PLAIN`, `DASHED`, `DASH_DOT`, `DOTTED` |
| colours | `#RRGGBBAA` — lowercase hex, the same order `.xopp` uses (`XoppColor`), **not** the `0xAARRGGBB` the int is held in |
| `align` | `LEFT`, `CENTER`, `RIGHT` — fresh cells are **centred by default** (and a file cell with no `align` reads centred too); use `LEFT` explicitly when a cell reads as prose |

---

## Geometry elements

These four mirror the `.xopp` model one-to-one, so the bridge is a field rename rather than a
translation.

### Stroke

```json
{
  "type": "stroke",
  "id": "stk_01",
  "tool": "PEN",
  "color": "#1a237eff",
  "lineStyle": "PLAIN",
  "capStyle": "round",
  "bounds": { "left": 101.7, "top": 204.4, "right": 104.5, "bottom": 207.5 },
  "points": [
    { "x": 102.4, "y": 205.1, "width": 1.2, "pressure": 0.55, "tilt": 0.248, "azimuth": 0.558, "dtMs": 0 },
    { "x": 103.8, "y": 206.8, "width": 1.4, "pressure": 0.62, "tilt": 0.258, "azimuth": 0.578, "dtMs": 6 }
  ]
}
```

- **`lineWidth` is present only for a constant-width stroke, and absent for a pressure stroke** whose
  widths are per point. That is `.xopp`'s own rule — a `width` *attribute* means the constant form, no
  attribute means one width per vertex — kept because it is the fact the bridge has to re-emit.
- `points[].width` is the **pressure-resolved** width actually drawn, so the document renders the same
  without re-running the pressure filter; `.pressure`, `.tilt`, `.azimuth` and `.dtMs` are the raw
  telemetry and are absent (not zero) for a `.xopp` import, which has none of them.
- **Units and presence.** `.tilt` is radians from the screen normal and `.azimuth` is radians of the
  pen's in-plane angle, exactly as `MotionEvent.AXIS_TILT` and `AXIS_ORIENTATION` report them — the app
  stores the platform's own numbers rather than converting — and `.dtMs` is milliseconds from the
  stroke's first point. A `tilt` of `0` means a pen held upright; a field is meant to be absent only
  when the tablet declares no such axis, and today a genuine zero and a missing axis are both written
  as absent (`store-a-genuine-zero-tilt-instead-of-dropping-it`). None of the three is a width: the
  width is `points[].width`, and telemetry never feeds it ([stylus.md](stylus.md)).
- `fill` is the desktop alpha (`0`–`255`) of a closed stroke's interior; absent when unfilled.

### Text

```json
{ "type": "text", "id": "txt_01",
  "font": "Sans Bold", "size": 12.0, "x": 72.0, "y": 90.0,
  "color": "#000000ff", "content": "E = mc^2" }
```

`font` is the Pango-style description the `.xopp` stores (`format/FontDescription.kt`) kept verbatim,
so a round trip never re-derives bold/italic from a family name. A text element has **no `bounds`**:
its extent depends on the font, which only the renderer knows, and a made-up box would be worse than
none.

### Image and rendered LaTeX

```json
{ "type": "image", "id": "img_01",
  "bounds": { "left": 40.0, "top": 40.0, "right": 240.0, "bottom": 200.0 },
  "asset": "assets/img_a1b2c3d4.webp" }

{ "type": "texImage", "id": "tex_01",
  "bounds": { "left": 100.0, "top": 300.0, "right": 280.0, "bottom": 330.0 },
  "latex": "\\int_0^1 x^2 dx", "color": "#000000ff",
  "asset": "assets/img_e5f6g7h8.png" }
```

`asset` names an entry under `assets/` — **never an inline base64 blob**. The same picture pasted on
ten pages is stored once and referenced ten times; that is the point of the container. The name is the
SHA-256 of the bytes, so two elements carrying the same picture converge on the same entry without
anyone coordinating, and a name derived from content cannot go stale.

- The container is the one the bytes arrived in (a PNG stays a PNG), named from the hash plus that
  suffix — re-encoding the user's picture to save a few bytes would change the pixels they pasted.
- `texImage.asset` is the **rendered PNG** the `.xopp` carried, and is optional: a document may carry
  only the LaTeX. `latexInAttribute` records which of `.xopp`'s two on-disk shapes
  the source used, so a legacy file is not silently rewritten into the modern one.

### What an unknown or `.xopp`-only element looks like

```json
{ "type": "image2", "syntax": "xml", "attrs": { "width": "12" }, "body": "<x/>" }
```

An element that came from a `.xopp` and has no native shape carries `"syntax": "xml"`: its tag, its
attributes and its markup are wrapped, not translated, and it exports back as itself. Without the
marker it would come back indistinguishable from a native element whose type happens to be the same
word — and would then be skipped by the next `.xopp` export, which is exactly the round-trip loss this
channel exists to prevent.

---

## Living entities

The constructs `.xopp` can only bake into dead ink. Each family has its own codec — `MobixTableCodec`,
`MobixPlotCodec`, `MobixShapeCodec`, `MobixCircuitCodec` — reached from `MobixElementCodec` by its
`type`, and all four obey one rule that is stricter than rule 1:

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
`render/SelectionOps.kt`, edited in place via `ui/CircuitEditorDialog.kt` (symbol, value, stroke, rotation,
colour, and pins with `render/CircuitSnapping.kt`'s magnet). Its inspector writes the value with a **circuit
symbol keyboard** (Ω, µ, k, M, ±, °C…, inserted at the cursor) and puts it on any of the symbol's four
sides (`labelSide`, above).

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
  and losing a curve over the spelling of its variable would be the reader's fault.
- `parameters` holds the named constants an expression may reference; moving a slider re-evaluates the
  plot instead of editing the formula ([math-engine.md](math-engine.md)).
- `summaryText` is a **cache of what was computed**, written so a reader that will not run the math
  still shows the analysis. It is never the source of truth and is recomputed on any change to a curve,
  a range or a parameter.

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
(the ink turns, a value does not). The inspector chooses the side, and its value field opens the
**circuit keyboard** docked at the foot of the canvas — digits, designator letters and the circuit
symbols on three pages, in place of the system IME — moving the label at once (`render/CircuitInk.kt`,
`ui/CircuitEditorDialog.kt`); the key it replaced, `labelBelow: true` is still read.

`pins` are the magnetic anchors, relative to `position` and before `rotation`; `connectedTo` names the
net a pin is snapped to and is omitted for a floating pin. The magnet serves **connector gestures only**:
a wire drawn with the line tool and a component being placed or moved are pulled onto a nearby **pin**.
While a connector is armed every pin is marked with a small ring (and the pin the live gesture has caught
fills in), so the target can be seen before the wire is drawn; the magnet's reach is `SNAP_THRESHOLD_PT`
in page points with a floor of `SNAP_REACH_PX` screen pixels, so a zoomed-out page can still be wired.

A **component being placed** aims at one more thing: the ends of the **wires already on the page**
(`collectPlacementTargets`), because a pin meeting a wire's end is the joint the magnet exists to make —
the same joint as a wire drawn onto a pin, seen from the other side. Without it the connection closed only
when the component happened to be placed *first*: a wire drawn before the part it feeds never met it.
Only a wire's ends are offered, and only to a component, so a symbol is never pulled onto ink lying beside
it. The line tool still aims at pins alone, and a pen stroke is never a target for anyone, however
straight: drawing next to a line can never create a connection the user did not ask for.

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

### LaTeX today

`.xopp`'s `teximage` is the only math element the model carries: `TexImageElement` keeps the LaTeX source,
its colour and the rendered PNG bytes (either may be absent) and round-trips unchanged. The app draws that
source itself (`render/LatexParser.kt` → `render/LatexRenderer.kt`); `ui/LatexPalette.kt` inserts the STEM
symbols the keyboard cannot. There is no living `mathBlock` element: one from a newer build is preserved
verbatim as a `RawElement` (rule 1) rather than drawn.

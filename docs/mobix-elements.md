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
2. **An `id`, minted once and never re-minted.** Identity is what the conflict-free merge keys on
   ([sync-and-storage.md](sync-and-storage.md)) and what lets an edit change an element without the
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
- `texImage.asset` is the **rendered PNG** (per `docs/latex-typst-rendering.md`) and is optional: a
  document may carry only the LaTeX. `latexInAttribute` records which of `.xopp`'s two on-disk shapes
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
already author — the graph dialog's own output *is* a list of plot curves and a range — so the work is a
shortening rather than a new surface, and it is under way: the graph dialog now inserts a **living
`PlotElement`** where the ink used to be baked (`render/PlotElementFactory.kt` turns the dialog's answer
into the recipe, on the placement `insertMultiPlot` had always computed, so the graph on screen is the
same graph and only the file changed). `ElementBounds` provides `PlotElement` with its frame's padded
bounding box and marks it hit-testable: marquee, lasso and tap gestures pick it up, drag gestures move it
(translating its page-local frame while preserving mathematical ranges), handles resize it, and delete
removes it (`render/SelectionOps.kt`, `render/LivingEntitySelectionTest.kt`). When a single plot is selected,
the selection action bar offers **Edit plot**, which opens the anchored micro-inspector `PlotInspectorAnchor` beside
the selection on canvas, pre-filled with the element's curves, range, flags and shaded area; confirming applies the edits
in place (`PlotElementFactory.applyPlotEdit`) preserving the fields the dialog has no input for (`samples`,
`parameters`, `axisColor`, `id`), with full undo support and instant re-rendering. Because the
inspector is anchored, the on-screen keyboard can cover it; its focused field is therefore mirrored
into a one-line cell pinned to the canvas' bottom edge above the IME (`ui/PlotInputEcho.kt`), which
shows the same label and text as the field being typed in and disappears with the keyboard.
The dialog owns the
shaded integral area (`shadeIntegral`), whose from/to pair drives the wash, the integral the summary
reports and the ink an export compiles ([math-engine.md](math-engine.md), section 3).
The table is the second family to become editable: authored as a living `TableElement` by the table tool
(`render/TableElementFactory.kt`), given its real grid bounding box and hit-testability by `render/ElementBounds.kt`,
and moved and resized by `render/SelectionOps.kt` (where a move shifts its origin and keeps `colWidths`/`rowHeights`
authoritative, while handles rescale the grid columns and rows, leaving the rule thickness alone —
a resize stretches geometry, never ink: `docs/selection.md`).

The circuit component is the third family: authored as a living `CircuitElement` by circuit tools
(`render/CircuitElementFactory.kt`), given its rotated stroke bounding box and hit-testability by
`render/ElementBounds.kt`, moved, rotated, and restyled by `render/SelectionOps.kt`, and edited in place via
`ui/CircuitEditorDialog.kt` (symbol picker, value text, stroke width, rotation, color, and pin connectivity with
magnetic pin snapping via `render/CircuitSnapping.kt`).

Until its respective editor lands, the remaining living entity (parametric shape) is
**visible but not selectable**. `ElementBounds` answers an empty box (0,0,0,0) and `isHitTestable = false`
for it — a real box would let a marquee pick up something whose geometry has no editor yet — so a
marquee, move or delete leaves it alone. The `.xopp` synthesis export compiles living entities into
standard `.xopp` ink before a file leaves the app ([mobix-format.md](mobix-format.md)).

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
cell at a time. Opened by a double tap on a cell or by **Edit table** in the selection bar, the
dialog shows every cell's text at once in the table's own shape with horizontal and vertical
scrolling for wide grids, steppers to add/remove rows and columns, keyboard navigation (Tab,
Shift+Tab, Enter) with automatic row expansion on the final cell, and align/bold acting on the
focused cell. Nothing tracks the canvas: the dialog is a fixed surface, so the keyboard
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

`vertices` are the *authored* geometry and the handles the editor drags; a kind defined by an angle set
(a scalene triangle or trapezoid, [drawing-tools.md](drawing-tools.md)) keeps its exact angles by
re-fitting the vertices on every drag, so the two never disagree on screen. `bounds` is written for a
reader that only wants to cull, and is **recomputed from the vertices** on read: a shape can never be
picked where its handles are not.

### Circuit component (`circuit`)

```json
{
  "type": "circuit", "id": "ckt_res_01", "symbol": "RESISTOR",
  "value": "10 kΩ", "position": { "x": 300.0, "y": 150.0 }, "rotation": 90.0,
  "strokeColor": "#d84315ff", "strokeWidth": 1.6,
  "pins": [
    { "id": "p1", "relX": -24.0, "relY": 0.0, "connectedTo": "net_node_A" },
    { "id": "p2", "relX": 24.0, "relY": 0.0, "connectedTo": "net_node_B" }
  ]
}
```

`pins` are the magnetic anchors, relative to `position` and before `rotation`; `connectedTo` names the
net a pin is snapped to and is omitted for a floating pin. A circuit with no `pins` is a plain symbol —
the shape library's own drawings need no connectivity to be edited as a shape. A component has **no
`bounds` at all**: its outline is the symbol library's, and a copy of a drawing this format does not
own would be a copy that can go stale.

`strokeColor`/`strokeWidth` **are** here, because they are the user's choice and not the library's: the
drawing is what leaves this app for a desktop, so an export that assumed an ink would turn a red
resistor black. `symbol` names a key in that library (and, like a shape's `shape`, the key set is open):
a build that does not know a symbol cannot draw it, and says so rather than inventing ink for it
(`docs/mobix-format.md`, the lossy-field rule).

### Math block (`mathBlock`)

Defined in [latex-typst-rendering.md](latex-typst-rendering.md) and tracked in `TODO.toml` (tasks 720, 730, 740):

```json
{
  "type": "mathBlock", "id": "mth_01",
  "engine": "TYPST",
  "source": "integral_(-oo)^(+oo) e^(-x^2) dif x = sqrt(pi)",
  "bounds": { "left": 140.0, "top": 220.0, "scale": 1.2 },
  "color": "#212121ff", "fontSizePt": 16.0
}
```

A living math block element preserving raw formula markup (`LaTeX` or `Typst`), rendered as resolution-independent vector outlines, re-editable via double-tap, and exported as vector glyphs to PDF and `.xopp` `TexImage` with metadata fallback.

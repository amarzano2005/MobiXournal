# Inserting a graph: the function plotter and living figures

> Read when you work on *Insert graph*: the expression parser, the plot's rendering and its study panel, and the recognised figure that stays geometry instead of ink.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

The other drawing tools — shapes and styles, the eraser, layers, hold-to-snap and zoom — are [drawing-tools.md](drawing-tools.md)'s.

## The 2D function plotter (`FunctionPlot`, `MultiFunctionPlotter`, `FunctionAnalysis`)

**The 2D function plotter and function study (`FunctionPlot`).** *Insert graph* turns `f(x)` into a **living plot** that draws as ink: `FunctionPlot`
holds a recursive-descent parser (`+ - * / ^`, unary minus, parentheses, implicit multiplication
such as `2x^2` or `2(x+1)`, scientific notation `2e-3`, superscripts `x²`, standard math aliases like `tg`/`ctg`/`π`, `x`, `pi`/`e`,
piecewise expressions like `x < 0: -x; x >= 0: x^2`, and the function table), sampling, and mapping onto page points — all pure and unit-tested.
The plotter supports **multi-curve plots** (`MultiFunctionPlotter`), each with individual colors and line styles (`PLAIN`, `DASHED`, `DOTTED`),
parametric curves $\{x(t), y(t)\}$ (`AdvancedCurves`), and **function study / analysis** (`FunctionAnalysis`):
automatic detection of critical points (roots/zeros, y-intercept, local minima, local maxima, mutual curve intersections, and vertical asymptotes/poles),
numerical derivative $f'(x)$ curve plotting, an optional summary report table inserted on the sheet, and a real-time live preview inside `AdvancedFunctionGraphDialog`. The dialog's two tabs are **kinds, not two inputs**: its parametric tab asks for one `{x(t), y(t)}` curve, and the answer carries that kind through the element into the file, so a pair is sampled and drawn as the loop it is instead of being compiled into the two waves its two formulas would look like as `y = f(x)` — which is also why `MultiFunctionPlotter` refuses a curve whose kind it cannot place rather than drawing the wave. The preview is not a second renderer either: it builds the very element Insert would place and draws it through `LivingInk` (`ui/GraphDialogPreview.kt`), so a preview cannot promise a drawing the insertion will not make.
Both shells show the preview directly on the themed panel, with a subtle grid and contrast-adapted axes and curves (`PlotPreviewPalette`); the desktop preview starts expanded. The preview draws the ink's **tick numbers too** — the compiled text elements, at the coordinates the measured advances give them ([mobix-living-entities.md](mobix-living-entities.md)) — so a number in the card sits where the page and the export put it; the analysis summary, prose below the frame, is the one text left out. This presentation never changes document colors, saved elements or exports. A pole or leap breaks the curve, so `1/x` comes out as two branches instead of one line through the origin.
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

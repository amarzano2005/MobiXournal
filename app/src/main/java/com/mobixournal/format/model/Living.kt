package com.mobixournal.format.model

/**
 * The **living entities**: the constructs `.xopp` can only bake into dead ink and something a user
 * wants to edit again — a table whose cell text is still text, a plot whose formula is still a
 * formula, a parametric shape whose vertices are still handles, a circuit symbol whose pins still
 * connect. They are the reason the native `.mobix` format exists (`docs/mobix-format.md`), and they
 * are modelled here rather than as opaque markup because their fields are a document's data: the
 * schema is `docs/mobix-elements.md`, the element codec is `MobixElementCodec` and each family's own
 * codec (`MobixTableCodec`, `MobixPlotCodec`, `MobixShapeCodec`, `MobixCircuitCodec`).
 *
 * Two things about these types are deliberate:
 *
 *  - **They are model types, not format types.** A plot's curve is a formula with a range and a kind
 *    because the math engine evaluates it (`docs/math-engine.md`), not because JSON
 *    has fields; the codec's job is only to spell them.
 *  - **`shape` and `symbol` are open strings, not enums.** The two libraries behind them (the shape
 *    library, the electronics symbol set) grow, and a kind this build has never heard of is still the
 *    user's drawing: refusing it would throw away a document to protect a lookup table. The values the
 *    codec *does* interpret (`align`, a plot's `kind`) are enums and are refused when unknown, because
 *    misreading one changes what is drawn.
 *
 * Nothing draws or edits these yet: every consumer treats them exactly as it treats a [RawElement] —
 * preserved on disk, skipped on screen (`docs/mobix-elements.md`). Modeling them is what makes the
 * `.mobix` codec able to read and write them *now*, so the editors have a model to open later.
 */

/**
 * Whether [this] element is one of the **living entities** — a construct `.xopp` can only hold as the
 * ink it was flattened into, and which this app keeps as the recipe it is.
 *
 * Stated once, here, because three layers have to agree about the family and none of them should each
 * carry its own list of four types: the export compiles these and nothing else (`XoppSynthesis`), the
 * bridge block's claims may describe these and nothing else, and the consumers that skip them
 * (rendering, hit tests) are answering the same question in the negative.
 */
val Element.isLivingEntity: Boolean
    get() = this is TableElement || this is PlotElement || this is ParametricShapeElement || this is CircuitElement

/** A cell's horizontal alignment inside its column — the desktop's three values. */
enum class TableAlign { LEFT, CENTER, RIGHT }

/**
 * One table cell. An empty cell is the defaults, not an absent value: `cells` is always rectangular
 * ([TableElement.colWidths] by [TableElement.rowHeights]) so a row index means a row.
 *
 * @param text The cell's content: plain text (a cell may hold a formula as text, not as a plot).
 * @param align Where [text] sits in its column.
 * @param bold Whether the cell is emphasised — the header-row style, and usable anywhere.
 * @param fillColor An ARGB int behind the cell, or null for no fill.
 */
data class TableCell(
    val text: String = "",
    val align: TableAlign = TableAlign.LEFT,
    val bold: Boolean = false,
    val fillColor: Int? = null,
)

/**
 * A table: the editable grid `.xopp` can only bake into border strokes plus loose text boxes.
 *
 * [colWidths] and [rowHeights] are the **authority for the grid** — inserting a row or dragging a
 * column edge is a change to those lists, not to a bounding box — and the element's box is their sum
 * placed at ([left], [top]). [cells] is `rowHeights.size` rows of `colWidths.size` columns; a short row
 * reads as empty cells rather than corrupting the table, so a hand-edited file degrades to a usable
 * table instead of a refused one.
 *
 * @param left The grid's origin X in page points.
 * @param top The grid's origin Y in page points.
 * @param colWidths Column widths in points, left to right.
 * @param rowHeights Row heights in points, top to bottom.
 * @param cells The cell grid, row-major.
 * @param headerRow Whether the first row is a header (a stronger rule and/or a bold default).
 * @param borderColor The grid rule colour as an ARGB int.
 * @param borderWidth The grid rule width in points.
 * @param extraAttrs Attributes a later schema added, preserved for round-trip.
 * @param id The element's stable identity; see [ElementId].
 */
data class TableElement(
    val left: Double,
    val top: Double,
    val colWidths: List<Double>,
    val rowHeights: List<Double>,
    val cells: List<List<TableCell>> = emptyList(),
    val headerRow: Boolean = false,
    val borderColor: Int = DEFAULT_BORDER,
    val borderWidth: Double = 1.0,
    val extraAttrs: Map<String, String> = emptyMap(),
    val id: String? = null,
) : Element {

    /** The grid's own width in points — the columns' sum, which is what the element's box is made of. */
    val width: Double get() = colWidths.sum()

    /** The grid's own height in points. */
    val height: Double get() = rowHeights.sum()

    /** The cell at ([row], [col]), or an empty one — a short row is a row of empty cells, not an error. */
    fun cell(row: Int, col: Int): TableCell = cells.getOrNull(row)?.getOrNull(col) ?: TableCell()

    companion object {
        /** The default rule colour: the near-black a table is drawn with out of the box. */
        const val DEFAULT_BORDER: Int = 0xFF212121.toInt()
    }
}

/** A plot curve's parameterisation — which variable the expression is a function of. */
enum class PlotKind {
    /** `y = f(x)` over [PlotElement.xMin]..[PlotElement.xMax]. */
    CARTESIAN,

    /** `(x, y) = (source(t), sourceY(t))` over the curve's own `tMin`..`tMax`. */
    PARAMETRIC,

    /** `r(θ)` over the curve's own `thetaMin`..`thetaMax`, drawn as `(r cos θ, r sin θ)`. */
    POLAR,
}

/**
 * One curve of a [PlotElement]. A plot holds a list of these because overlaying `sin(x)` and `cos(x)`
 * is one *plot*, not two: the axes, the range and the parameters are shared, and the comparison is the
 * point.
 *
 * @param kind How the expression is evaluated.
 * @param source The expression: `y = f(x)`, `x = f(t)` or `r(θ)`, per [kind].
 * @param sourceY The `y(t)` expression of a [PlotKind.PARAMETRIC] curve; null otherwise.
 * @param color The curve's stroke colour as an ARGB int.
 * @param strokeWidth The curve's width in points.
 * @param lineStyle The curve's line pattern.
 * @param tMin Start of the parameter range ([PlotKind.PARAMETRIC] only).
 * @param tMax End of the parameter range ([PlotKind.PARAMETRIC] only).
 * @param thetaMin Start of the angular sweep ([PlotKind.POLAR] only).
 * @param thetaMax End of the angular sweep ([PlotKind.POLAR] only).
 */
data class PlotCurve(
    val kind: PlotKind,
    val source: String,
    val color: Int,
    val strokeWidth: Double = 1.0,
    val lineStyle: LineStyle = LineStyle.PLAIN,
    val sourceY: String? = null,
    val tMin: Double? = null,
    val tMax: Double? = null,
    val thetaMin: Double? = null,
    val thetaMax: Double? = null,
)

/** The bounds of a shaded integral: the area between the curve and the axis from [from] to [to]. */
data class PlotShading(val from: Double, val to: Double)

/**
 * A function plot: the semantic entity `.xopp` flattens into dead stroke paths plus text labels.
 *
 * Everything the math engine needs to reproduce the drawing is here — the curves with their kinds and
 * ranges, the sample count, the named parameters, the display flags and the integral bounds
 * (`docs/math-engine.md` owns what the engine promises about them). [summaryText] is the exception and
 * is marked as such: it is the engine's **rendering cache** (roots, extrema, inflections already
 * formatted), so a reader that will not run the math can still show the analysis. It is never the
 * model — recomputed on any change to a curve, a range or a parameter — and dropping it loses nothing.
 *
 * @param left The plot box's left edge in page points.
 * @param top The plot box's top edge in page points.
 * @param right The plot box's right edge in page points.
 * @param bottom The plot box's bottom edge in page points.
 * @param curves The curves drawn in this plot's shared frame, in draw order.
 * @param xMin Left end of the Cartesian domain.
 * @param xMax Right end of the Cartesian domain.
 * @param samples How many points each curve is sampled at.
 * @param showCriticalPoints Whether roots/extrema/inflections are marked.
 * @param includeDerivative Whether the first derivative is drawn alongside.
 * @param shadeIntegral The shaded area, or null for no shading.
 * @param parameters The named constants the expressions may reference and the sliders move.
 * @param axisColor The axis/frame colour as an ARGB int.
 * @param summaryText The engine's formatted analysis, a cache only.
 * @param extraAttrs Attributes a later schema added, preserved for round-trip.
 * @param id The element's stable identity; see [ElementId].
 */
data class PlotElement(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val curves: List<PlotCurve>,
    val xMin: Double = DEFAULT_X_MIN,
    val xMax: Double = DEFAULT_X_MAX,
    val samples: Int = DEFAULT_SAMPLES,
    val showCriticalPoints: Boolean = false,
    val includeDerivative: Boolean = false,
    val shadeIntegral: PlotShading? = null,
    val parameters: Map<String, Double> = emptyMap(),
    val axisColor: Int = DEFAULT_AXIS,
    val summaryText: String? = null,
    val extraAttrs: Map<String, String> = emptyMap(),
    val id: String? = null,
) : Element {

    companion object {
        /** The default axis colour: the blue-grey a fresh plot is drawn with. */
        const val DEFAULT_AXIS: Int = 0xFF37474F.toInt()

        /** Default left end of a Cartesian domain, when a file does not say. */
        const val DEFAULT_X_MIN: Double = -10.0

        /** Default right end of a Cartesian domain, when a file does not say. */
        const val DEFAULT_X_MAX: Double = 10.0

        /** Default sample count: fine enough for a smooth curve on a phone screen. */
        const val DEFAULT_SAMPLES: Int = 300
    }
}

/** One authored vertex of a [ParametricShapeElement], in page points. */
data class ShapeVertex(val x: Double, val y: Double)

/**
 * A parametric shape: a figure the editor keeps as *geometry* — vertices the handles drag — instead of
 * as the strokes a drawing would leave behind.
 *
 * [shape] names the kind (`TRIANGLE_SCALENE`, `TRAPEZOID`, …) and is an open string, because the shape
 * library grows and an unknown kind is still the user's figure. A kind defined by an angle set keeps
 * its exact angles by **re-fitting [vertices] on every drag** (`docs/drawing-tools.md`),
 * which is why the vertices — not a cached angle table — are what the file stores; the two can then
 * never disagree about what is on screen.
 *
 * @param shape The shape-library key naming the kind of figure.
 * @param vertices The authored geometry, in draw order.
 * @param strokeColor The outline colour as an ARGB int.
 * @param strokeWidth The outline width in points.
 * @param fillColor An ARGB int filling the figure, or null for an open figure.
 * @param showAngles Whether the interior angles are labelled.
 * @param showSideLengths Whether the side lengths are labelled.
 * @param extraAttrs Attributes a later schema added, preserved for round-trip.
 * @param id The element's stable identity; see [ElementId].
 */
data class ParametricShapeElement(
    val shape: String,
    val vertices: List<ShapeVertex>,
    val strokeColor: Int,
    val strokeWidth: Double = 1.0,
    val fillColor: Int? = null,
    val showAngles: Boolean = false,
    val showSideLengths: Boolean = false,
    val extraAttrs: Map<String, String> = emptyMap(),
    val id: String? = null,
) : Element

/**
 * A circuit pin: a magnetic anchor on a [CircuitElement].
 *
 * The coordinates are **relative to the component's position and before its rotation**, so turning the
 * component moves its pins with it and a wire does not have to be re-joined. [connectedTo] names the
 * net the pin is snapped to — a document-level name, not an element reference, because a wire is
 * connectivity rather than a drawing.
 *
 * @param id The pin's own name, unique within its component (`p1`, `anode`, …).
 * @param relX The pin's X offset from the component's position, in points.
 * @param relY The pin's Y offset from the component's position, in points.
 * @param connectedTo The net this pin is snapped to, or null for a floating pin.
 */
data class CircuitPin(
    val id: String,
    val relX: Double = 0.0,
    val relY: Double = 0.0,
    val connectedTo: String? = null,
)

/**
 * A circuit component: the electronics symbol `.xopp` can only store as a set of strokes.
 *
 * [symbol] is the symbol library's key (`RESISTOR_IEEE`, `CAPACITOR`, …) and an open string for the
 * same reason [ParametricShapeElement.shape] is: the library grows. A component with no [pins] is a
 * plain symbol — the shape library's own drawings need no connectivity to be placed and edited — so
 * pins are optional rather than required.
 *
 * @param symbol The symbol-library key naming the component.
 * @param positionX The component's anchor X in page points.
 * @param positionY The component's anchor Y in page points.
 * @param rotation The component's rotation in degrees, clockwise.
 * @param strokeColor The ink the component is drawn in, as an ARGB int — the user's own pen colour at
 *   the time, which is why it is here rather than assumed: an export that guessed would turn a red
 *   resistor black, and the drawing is what leaves this app for the desktop.
 * @param strokeWidth The component's line width in points.
 * @param value The component's value as the user wrote it (`10 kΩ`, `V1`) — a string, because it is a
 *   label and not a quantity this build computes with.
 * @param pins The magnetic anchors, relative to the position and before the rotation.
 * @param extraAttrs Attributes a later schema added, preserved for round-trip.
 * @param id The element's stable identity; see [ElementId].
 */
data class CircuitElement(
    val symbol: String,
    val positionX: Double,
    val positionY: Double,
    val rotation: Double = 0.0,
    val strokeColor: Int = DEFAULT_INK,
    val strokeWidth: Double = 1.0,
    val value: String? = null,
    val pins: List<CircuitPin> = emptyList(),
    val extraAttrs: Map<String, String> = emptyMap(),
    val id: String? = null,
) : Element {

    companion object {
        /** The ink a component is drawn in when nothing chose one: opaque black, like every other pen. */
        const val DEFAULT_INK: Int = 0xFF000000.toInt()
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotShading
import com.mobixournal.format.model.TableElement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A slot whose product is a **living element** rather than ink, and the family it places.
 *
 * The window's strip offers Plot, Table and Circuit as editing tools; on the tablet each of them places a
 * re-editable recipe (a `PlotElement` with its formula, a `TableElement` with its grid, a `CircuitElement`
 * with its symbol and pins) instead of strokes. This enum is the shared vocabulary between the strip, the
 * placement geometry and the commit, so a slot missing its builder cannot compile.
 */
enum class LivingSlot {
    PLOT,
    TABLE,
    CIRCUIT,
}

/** A fresh element's size on a page, in points. */
data class LivingSize(val width: Double, val height: Double)

/**
 * A press shorter than this on **either** axis is a click, not a drag: a slot that places a living element
 * still needs an answer for a plain tap (the family's default size, centred on the press), and a drag of
 * two points is a shaky hand, not a two-point table.
 */
const val LIVING_PLACEMENT_SLOP_PT: Double = 4.0

/**
 * Where a press on a STEM slot puts its element, and what it builds — the shared half of the placement,
 * so the window's canvas, its tests and any future shell agree instead of each spelling a box.
 *
 * The **drag** is the tablet's gesture: press and drag a box, and the element is written at those bounds.
 * A **click** (or a drag too short to mean anything) gets [defaultSize] centred on the press. Either way
 * the box is kept inside the page: an element placed while zoomed out must not land half off the sheet.
 *
 * The builders write the same defaults the tablet's own insertion writes, and nothing an edit could not
 * change later: the formula of a plot is editable, a table's grid is editable, a component's value is the
 * model's own `null`. Identity is left to the codec (`ElementId` mints it at the first save), exactly as
 * `PlotElementFactory` documents for the tablet.
 */
object LivingPlacement {

    /** The formula a freshly placed plot starts from — the tablet's own starter, so both shells agree. */
    const val DEFAULT_PLOT_FORMULA: String = "sin(x)"

    /** A table's default grid: a header plus two body rows, three columns. */
    const val DEFAULT_TABLE_ROWS: Int = 3
    const val DEFAULT_TABLE_COLS: Int = 3

    /** A circuit component's default symbol: the resistor, the first part of the tablet's palette. */
    val DEFAULT_CIRCUIT_KIND: ShapeKind = ShapeKind.RESISTOR

    /**
     * The size a **click** places for [slot] on a page [pageW] wide.
     *
     * A plot follows the tablet's own rule (`PlotElementFactory.boxFor`): 45 % of the page's width, kept
     * between 240 and 320 pt, and 0.6 of its width tall. A table is `TableElementFactory`'s centred
     * default, and a component is `CircuitElementFactory.DEFAULT_LENGTH_PT` long.
     */
    fun defaultSize(slot: LivingSlot, pageW: Double): LivingSize = when (slot) {
        LivingSlot.PLOT -> {
            val width = (pageW * 0.45).coerceIn(240.0, 320.0)
            LivingSize(width, width * 0.6)
        }
        LivingSlot.TABLE -> LivingSize(
            TableElementFactory.DEFAULT_WIDTH_PT,
            TableElementFactory.DEFAULT_HEIGHT_PT,
        )
        LivingSlot.CIRCUIT -> LivingSize(CircuitInk.DEFAULT_LENGTH_PT, CircuitInk.DEFAULT_LENGTH_PT / 2.0)
    }

    /**
     * The box a press at ([startX], [startY]) and a release at ([endX], [endY]) mean on a page of
     * [pageW] × [pageH] pt: the drag's own box, or [default] centred on the press, clamped inside the page.
     */
    fun bounds(
        pageW: Double,
        pageH: Double,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        default: LivingSize,
    ): Bounds {
        val dx = endX - startX
        val dy = endY - startY
        val box = if (abs(dx) < LIVING_PLACEMENT_SLOP_PT || abs(dy) < LIVING_PLACEMENT_SLOP_PT) {
            centred(startX, startY, default)
        } else {
            Bounds(min(startX, endX), min(startY, endY), max(startX, endX), max(startY, endY))
        }
        return box.inside(pageW, pageH)
    }

    /**
     * The living plot for a placed [bounds], drawn in [colour] at [strokeWidthPt] — with the same
     * questions the tablet's graph dialog asks before an insertion: the formula, the range, and the
     * display flags (critical points, derivative, summary, shaded integral).
     */
    fun plot(
        bounds: Bounds,
        colour: Int,
        strokeWidthPt: Double,
        formula: String = DEFAULT_PLOT_FORMULA,
        xMin: Double = PlotElement.DEFAULT_X_MIN,
        xMax: Double = PlotElement.DEFAULT_X_MAX,
        showCriticalPoints: Boolean = false,
        includeDerivative: Boolean = false,
        includeSummary: Boolean = false,
        shadeIntegral: PlotShading? = null,
    ): PlotElement = PlotElementFactory.plotElementIn(
        box = PlotBox(bounds.left, bounds.top, bounds.width, bounds.height),
        curves = listOf(
            PlotCurve(
                source = formula,
                color = colour,
                lineStyle = LineStyle.PLAIN,
                kind = PlotKind.CARTESIAN,
            ),
        ),
        xMin = xMin,
        xMax = xMax,
        showCriticalPoints = showCriticalPoints,
        includeDerivative = includeDerivative,
        includeSummary = includeSummary,
        shadeIntegral = shadeIntegral,
        strokeWidthPt = strokeWidthPt,
    )

    /**
     * The living table for a placed [bounds]: [rows] × [cols], with a header row.
     *
     * `TableElementFactory` divides the box's width by [cols] and its height by [rows], so a table placed
     * by a drag is exactly as wide as the drag — the grid is the box, not an estimate of it.
     */
    fun table(
        bounds: Bounds,
        rows: Int = DEFAULT_TABLE_ROWS,
        cols: Int = DEFAULT_TABLE_COLS,
        hasHeader: Boolean = true,
    ): TableElement = TableElementFactory.tableElementFor(
        startX = bounds.left,
        startY = bounds.top,
        endX = bounds.right,
        endY = bounds.bottom,
        rows = rows,
        cols = cols,
        hasHeader = hasHeader,
        borderColor = TableElement.DEFAULT_BORDER,
        borderWidth = TableElementFactory.DEFAULT_BORDER_WIDTH,
    )

    /**
     * The living component of [kind] for a placed [bounds], or null when the shape library has no such
     * symbol. The component is drawn along the box's horizontal axis with rotation 0 — a drag sets its
     * **length**, which is the one dimension the tablet's own gesture sets too — and its pins are the
     * factory's canonical anchors, so the ink and the magnet agree.
     */
    fun circuit(
        kind: ShapeKind,
        bounds: Bounds,
        colour: Int,
        strokeWidthPt: Double,
    ): CircuitElement? = CircuitElementFactory.placedElementFor(
        symbol = kind.name,
        startX = bounds.left,
        startY = (bounds.top + bounds.bottom) / 2.0,
        endX = bounds.right,
        endY = (bounds.top + bounds.bottom) / 2.0,
        strokeColor = colour,
        strokeWidth = strokeWidthPt,
    )

    /** [default] centred on ([x], [y]) in page points. */
    private fun centred(x: Double, y: Double, default: LivingSize): Bounds = Bounds(
        left = x - default.width / 2.0,
        top = y - default.height / 2.0,
        right = x + default.width / 2.0,
        bottom = y + default.height / 2.0,
    )

    /**
     * This box kept inside a [pageW] × [pageH] page: a box larger than the sheet shrinks to it, and one
     * that starts off the edge is pushed back in. A one-point floor keeps a degenerate box from becoming
     * invisible to a hit test.
     */
    private fun Bounds.inside(pageW: Double, pageH: Double): Bounds {
        val w = width.coerceAtMost(pageW).coerceAtLeast(1.0)
        val h = height.coerceAtMost(pageH).coerceAtLeast(1.0)
        val l = left.coerceIn(0.0, (pageW - w).coerceAtLeast(0.0))
        val t = top.coerceIn(0.0, (pageH - h).coerceAtLeast(0.0))
        return Bounds(l, t, l + w, t + h)
    }
}

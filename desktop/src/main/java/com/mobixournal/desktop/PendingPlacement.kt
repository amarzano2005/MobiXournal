package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.MathFlavor
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotShading
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.PlotBox
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory
import com.mobixournal.render.ShapeKind

/**
 * A placement that has its **box** but is waiting for its values — the question a living slot asks
 * *before* the element exists.
 *
 * The tablet does the same thing in the other shell: the graph dialog takes the equations, the range and
 * the display flags and then inserts, the table takes its rows and columns, and the circuit palette is
 * asked for the part. The window places nothing while this object is live, so a cancelled question leaves
 * no empty frame and no undo step behind — the page is exactly what it was before the drag.
 *
 * A plot keeps a **list** of equations, as the tablet's graph dialog does: one graph may carry several
 * curves, and a card that could only ever edit the first one was the reason a second equation could not be
 * inserted at all. [plotIn] builds the element from that list, so the lane's preview and the placement draw
 * the very same graph — a preview compiled by another path is a preview that can lie.
 */
class PendingPlacement(
    val slot: LivingSlot,
    val bounds: Bounds,
    val tabKey: String,
    /**
     * The ink the slot was pressed with — the first equation's colour and the circuit's stroke. Passed in
     * rather than read from the shell so the card is a plain object a JVM test can build.
     */
    private val defaultColour: Int = 0xFF000000.toInt(),
) {

    // --- plot: the tablet's graph dialog -----------------------------------------------------------

    /**
     * The equations the graph will carry, in the order they are drawn. It is **snapshot state** and it is
     * written on every keystroke: nothing of this is on the page yet, so the preview can follow the typing
     * the way the tablet's dialog preview does.
     */
    val formulas = mutableStateListOf(LivingPlacement.DEFAULT_PLOT_FORMULA)

    /**
     * Each equation's **own colour**, in the same order as [formulas] — a graph of two curves is read by
     * colour, so one ink for the whole graph would make the second equation the first one's twin.
     *
     * A new row takes the next colour of the pen's palette (the inspector's own rule), and the row that
     * started the card takes the tool's ink, so nothing changes colour unless the user asks.
     */
    val formulaColours = mutableStateListOf(defaultColour)

    /** The equations as the graph reads them: trimmed, blanks dropped, order kept — with their colours. */
    val curves: List<Pair<String, Int>>
        get() = formulas.indices
            .map { index -> formulas[index].trim() to (formulaColours.getOrNull(index) ?: defaultColour) }
            .filter { (source, _) -> source.isNotEmpty() }

    /** The formulas alone, for the lane's summary and its readiness rule. */
    val sources: List<String> get() = curves.map { it.first }

    /** Add an empty equation row — the card's **Add equation**, capped so the list stays readable. */
    fun addFormula() {
        if (formulas.size >= MAX_FORMULAS) return
        formulaColours += PenPalette.COLORS[formulas.size % PenPalette.COLORS.size]
        formulas += ""
    }

    /** Drop equation [index]; the last one stays, because a graph with no equation is not a graph. */
    fun removeFormula(index: Int) {
        if (formulas.size <= 1 || index !in formulas.indices) return
        formulas.removeAt(index)
        if (index in formulaColours.indices) formulaColours.removeAt(index)
    }

    /** Give equation [index] the colour [colour]. */
    fun setFormulaColour(index: Int, colour: Int) {
        if (index in formulaColours.indices) formulaColours[index] = colour
    }

    /** The domain, as the dialog's two numbers (`x from`, `x to`). */
    var xMin by mutableStateOf("-10")
    var xMax by mutableStateOf("10")

    /** The display flags of the graph dialog. */
    var showCriticalPoints by mutableStateOf(true)
    var includeDerivative by mutableStateOf(false)
    var includeSummary by mutableStateOf(false)

    /** The shaded integral, off by default like the tablet's switch. */
    var shade by mutableStateOf(false)
    var shadeFrom by mutableStateOf("")
    var shadeTo by mutableStateOf("")

    // --- formula: its source and its flavour -------------------------------------------------------

    /**
     * The formula's source, written on every keystroke so the lane's preview follows the typing. It starts
     * from the shared starter ([LivingPlacement.DEFAULT_MATH_SOURCE]) rather than an empty box, because a
     * blank preview would leave the user typing into nothing.
     */
    var mathSource by mutableStateOf(LivingPlacement.DEFAULT_MATH_SOURCE)

    /** Whether the source is Typst rather than LaTeX — the two flavours a math block carries. */
    var typst by mutableStateOf(false)

    /** The flavour the block will be written in. */
    val mathFlavor: MathFlavor get() = if (typst) MathFlavor.TYPST else MathFlavor.LATEX

    // --- table: its grid and its header ------------------------------------------------------------

    var columns by mutableStateOf(LivingPlacement.DEFAULT_TABLE_COLS)
    var rows by mutableStateOf(LivingPlacement.DEFAULT_TABLE_ROWS)
    var header by mutableStateOf(true)

    /** The domain the answers describe, or null while the two numbers do not form one. */
    val domain: Pair<Double, Double>?
        get() {
            val from = xMin.trim().toDoubleOrNull() ?: return null
            val to = xMax.trim().toDoubleOrNull() ?: return null
            return if (to > from) from to to else null
        }

    /** The shaded area the switch and its two fields describe, or null when shading is off. */
    val shading: PlotShading?
        get() {
            if (!shade) return null
            val domain = domain ?: return null
            val from = shadeFrom.trim().toDoubleOrNull() ?: domain.first
            val to = shadeTo.trim().toDoubleOrNull() ?: domain.second
            return if (to > from) PlotShading(from, to) else null
        }

    /** Whether the answers are complete enough to build the element. */
    val ready: Boolean
        get() = when (slot) {
            LivingSlot.PLOT -> sources.isNotEmpty() && domain != null && (!shade || shading != null)
            LivingSlot.TABLE -> columns in 1..30 && rows in 1..200
            // A component's part was already the palette's question; nothing is asked twice.
            LivingSlot.CIRCUIT -> true
            // A formula with no source draws nothing, so there is nothing to place.
            LivingSlot.MATH -> mathSource.isNotBlank()
        }

    /**
     * The graph these answers describe, drawn in [box] with [colour] at [strokeWidthPt] — or null while
     * there is no formula or no open domain.
     *
     * It is one function for two callers: [element] composes it into the page's box, and the lane's preview
     * composes it into a preview box, so what is promised before Place is what lands after it.
     */
    fun plotIn(box: PlotBox, strokeWidthPt: Double): PlotElement? {
        val (from, to) = domain ?: return null
        val drawn = curves
        if (drawn.isEmpty()) return null
        return PlotElementFactory.plotElementIn(
            box = box,
            curves = drawn.map { (source, ink) ->
                PlotCurve(
                    source = source,
                    color = ink,
                    lineStyle = LineStyle.PLAIN,
                    kind = PlotKind.CARTESIAN,
                )
            },
            xMin = from,
            xMax = to,
            showCriticalPoints = showCriticalPoints,
            includeDerivative = includeDerivative,
            strokeWidthPt = strokeWidthPt,
            includeSummary = includeSummary,
            shadeIntegral = shading,
        )
    }

    /**
     * The element these answers describe, in [ink] — or null for a question that is not answered yet
     * (no formula, a domain that does not open to the right, a table with no cells).
     */
    fun element(ink: DesktopInk, circuitKind: ShapeKind): Element? = when (slot) {
        LivingSlot.PLOT -> plotIn(
            box = PlotBox(bounds.left, bounds.top, bounds.width, bounds.height),
            strokeWidthPt = ink.width.toDouble(),
        )
        LivingSlot.TABLE -> LivingPlacement.table(
            bounds = bounds,
            rows = rows,
            cols = columns,
            hasHeader = header,
        )
        LivingSlot.CIRCUIT -> livingElementFor(slot, bounds, ink, circuitKind)
        LivingSlot.MATH -> mathSource.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { source -> LivingPlacement.math(bounds, source, mathFlavor, ink.colour) }
    }

    /** The line the lane's header prints under its title — the answers so far. */
    fun summary(): String = when (slot) {
        LivingSlot.PLOT -> {
            val equations = sources
            val graph = when (equations.size) {
                0 -> "no equation"
                1 -> equations.first()
                else -> "${equations.size} equations"
            }
            val range = domain?.let { "${formatNumber(it.first)}…${formatNumber(it.second)}" } ?: "range not open"
            "$graph · $range"
        }
        LivingSlot.TABLE -> "$columns × $rows" + if (header) " · header" else ""
        LivingSlot.CIRCUIT -> slot.label()
        LivingSlot.MATH -> {
            val language = mathFlavor.name.lowercase().replaceFirstChar { it.uppercase() }
            mathSource.trim().takeIf { it.isNotEmpty() }?.let { "$language · $it" } ?: "$language · no source"
        }
    }

    /** How many equations one graph may carry: enough for a comparison, few enough to read in the lane. */
    private companion object {
        const val MAX_FORMULAS = 6
    }
}

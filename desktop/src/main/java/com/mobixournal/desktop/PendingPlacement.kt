package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.PlotShading
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ShapeKind

/**
 * A placement that has its **box** but is waiting for its values — the question a living slot asks
 * *before* the element exists.
 *
 * The tablet does the same thing in the other shell: the graph dialog takes the formula and the range and
 * then inserts, the table takes its rows and columns, and the circuit palette is asked for the part. The
 * window places nothing while this object is live, so a cancelled question leaves no empty frame and no
 * undo step behind — the page is exactly what it was before the drag.
 *
 * The answers are the model's own fields (`PlotElement.curves`/`xMin`/`xMax`/the display flags/
 * `shadeIntegral`, `TableElement`'s grid and header), so [element] builds the very recipe the inspector
 * would edit afterwards. Nothing here invents an option the file cannot keep.
 */
class PendingPlacement(val slot: LivingSlot, val bounds: Bounds, val tabKey: String) {

    // --- plot: the tablet's graph dialog -----------------------------------------------------------

    /** The formula, in the same expression language the whole app plots with. */
    var formula by mutableStateOf(LivingPlacement.DEFAULT_PLOT_FORMULA)

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
            LivingSlot.PLOT -> formula.isNotBlank() && domain != null && (!shade || shading != null)
            LivingSlot.TABLE -> columns in 1..30 && rows in 1..200
            // A component's part was already the palette's question; nothing is asked twice.
            LivingSlot.CIRCUIT -> true
        }

    /**
     * The element these answers describe, in [ink] — or null for a question that is not answered yet
     * (a formula that is blank, a domain that does not open to the right, a table with no cells).
     */
    fun element(ink: DesktopInk, circuitKind: ShapeKind): Element? {
        val (from, to) = domain ?: if (slot == LivingSlot.PLOT) return null else 0.0 to 0.0
        return when (slot) {
            LivingSlot.PLOT -> {
                if (formula.isBlank()) return null
                LivingPlacement.plot(
                bounds = bounds,
                colour = ink.colour,
                strokeWidthPt = ink.width.toDouble(),
                    formula = formula.trim(),
                    xMin = from,
                    xMax = to,
                    showCriticalPoints = showCriticalPoints,
                    includeDerivative = includeDerivative,
                    includeSummary = includeSummary,
                    shadeIntegral = shading,
                )
            }
            LivingSlot.TABLE -> LivingPlacement.table(
                bounds = bounds,
                rows = rows,
                cols = columns,
                hasHeader = header,
            )
            LivingSlot.CIRCUIT -> livingElementFor(slot, bounds, ink, circuitKind)
        }
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A curve a caller asks to be plotted: its formula, display color, line style, an optional label — and
 * **how the formula is parameterised** ([kind], with [sourceY] when the curve needs a second
 * expression).
 *
 * [kind] is here because two plots can share one formula and mean different things: `cos(x)` is a wave
 * as a Cartesian curve and the x of a circle as the x of a parametric pair. A caller that cannot say
 * which of the two it means gets the wave, which is why the dialog's parametric tab states it
 * ([PlotKind.PARAMETRIC] with `source = x(t)`, `sourceY = y(t)`).
 *
 * Only [MultiFunctionPlotter] places Cartesian curves; a parametric or polar one needs
 * [AdvancedCurves.sampleParametric]'s sampling and the shared frame `PlotInk` builds, so a plotter that
 * cannot draw a kind refuses it rather than drawing the wave its formula would look like.
 */
data class PlotCurve(
    val source: String,
    val color: Int = 0xFF000000.toInt(),
    val lineStyle: LineStyle = LineStyle.PLAIN,
    val label: String = "",
    val kind: PlotKind = PlotKind.CARTESIAN,
    val sourceY: String? = null,
)

/** The kind of critical/feature point detected in mathematical function analysis. */
enum class CriticalPointType(val label: String) {
    ZERO("Root / Zero"),
    Y_INTERCEPT("y-Intercept"),
    LOCAL_MIN("Local Min"),
    LOCAL_MAX("Local Max"),
    INTERSECTION("Intersection"),
    ASYMPTOTE("Vertical Asymptote"),
}

/** A critical point located on the Cartesian plane. */
data class CriticalPoint(
    val type: CriticalPointType,
    val x: Double,
    val y: Double,
    val description: String,
)

/** A group of stroke paths sharing the same color and line style (e.g. axes, a curve, or point markers). */
data class PlotPathGroup(
    val strokes: List<List<StrokePoint>>,
    val color: Int,
    val lineStyle: LineStyle = LineStyle.PLAIN,
)

/**
 * Full multi-curve plot output with styled strokes, labels, critical points, and summary table text.
 *
 * [frame] is the fitted mapping the strokes were placed in (see [PlotFrame]); it is null when nothing
 * was drawn, so a caller that has to place something in the plot's own coordinates knows there is no
 * frame to place it in rather than inventing one.
 */
data class MultiPlotResult(
    val pathGroups: List<PlotPathGroup>,
    val labels: List<PlotLabel>,
    val criticalPoints: List<CriticalPoint>,
    val summaryText: String = "",
    val frame: PlotFrame? = null,
)

/**
 * Numerical analysis tools for mathematical functions:
 * - Numerical derivative f'(x)
 * - Critical point finder (roots/zeros, y-intercept, local minima, local maxima, mutual intersections)
 * - Summary report generation
 */
object FunctionAnalysis {

    private const val H = 1e-5
    private const val EPSILON = 1e-7

    /** Deepest an adaptive Simpson subdivision may recurse before it settles for what it has. */
    private const val MAX_INTEGRATION_DEPTH = 24

    /** Samples a curve is checked at before integrating, to refuse one that is not finite. */
    private const val FINITE_PRESCAN = 64

    /** Central difference numerical derivative: f'(x) ≈ (f(x + h) - f(x - h)) / (2h). */
    fun derivative(formula: Formula): Formula = Formula { x ->
        val yPlus = formula.at(x + H)
        val yMinus = formula.at(x - H)
        if (yPlus.isFinite() && yMinus.isFinite()) {
            (yPlus - yMinus) / (2.0 * H)
        } else {
            Double.NaN
        }
    }

    /**
     * The **definite integral** $\int_a^b f(x)\,dx$, evaluated with adaptive Simpson's rule
     * (`docs/math-engine.md`, section 3), or null when the integrand is not finite across the interval —
     * a discontinuity is refused rather than returned as a NaN that would print as a number.
     *
     * The sign follows the bounds, so integrating the other way round negates the value.
     */
    fun definiteIntegral(formula: Formula, a: Double, b: Double, tolerance: Double = EPSILON): Double? {
        if (!a.isFinite() || !b.isFinite()) return null
        if (a == b) return 0.0
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        if (!finiteAcross(formula, lo, hi)) return null
        val whole = simpson(formula, lo, hi)
        if (!whole.isFinite()) return null
        val value = adaptiveSimpson(formula, lo, hi, tolerance.coerceAtLeast(1e-12), 0, whole) ?: return null
        return if (a < b) value else -value
    }

    /** One parabola through [a], the midpoint and [b]: the elementary Simpson estimate on a slice. */
    private fun simpson(f: Formula, a: Double, b: Double): Double {
        val c = (a + b) / 2.0
        return (b - a) / 6.0 * (f.at(a) + 4.0 * f.at(c) + f.at(b))
    }

    private fun adaptiveSimpson(f: Formula, a: Double, b: Double, eps: Double, depth: Int, whole: Double): Double? {
        val c = (a + b) / 2.0
        val left = simpson(f, a, c)
        val right = simpson(f, c, b)
        if (!left.isFinite() || !right.isFinite()) return null
        val refined = left + right
        if (depth >= MAX_INTEGRATION_DEPTH || abs(refined - whole) <= 15.0 * eps) {
            return refined + (refined - whole) / 15.0
        }
        val l = adaptiveSimpson(f, a, c, eps / 2.0, depth + 1, left) ?: return null
        val r = adaptiveSimpson(f, c, b, eps / 2.0, depth + 1, right) ?: return null
        return l + r
    }

    private fun finiteAcross(f: Formula, a: Double, b: Double): Boolean {
        for (i in 0..FINITE_PRESCAN) {
            val x = a + (b - a) * i / FINITE_PRESCAN
            if (!f.at(x).isFinite()) return false
        }
        return true
    }

    /**
     * Finds key critical points for [formula] in the interval [[xMin], [xMax]]:
     * roots, y-intercept, local extrema, and vertical asymptotes.
     */
    fun findCriticalPoints(
        formula: Formula,
        xMin: Double,
        xMax: Double,
        steps: Int = 200,
        curveName: String = "f(x)",
    ): List<CriticalPoint> {
        val points = mutableListOf<CriticalPoint>()
        if (xMax <= xMin || steps <= 0) return points

        // 1. y-intercept: f(0) if 0 is in [xMin, xMax]
        if (0.0 in xMin..xMax) {
            val y0 = formula.at(0.0)
            if (y0.isFinite()) {
                points.add(CriticalPoint(CriticalPointType.Y_INTERCEPT, 0.0, y0, "$curveName crossing y-axis at (0, ${fmt(y0)})"))
            }
        }

        val step = (xMax - xMin) / steps
        var prevX = xMin
        var prevY = formula.at(prevX)
        val df = derivative(formula)
        var prevDf = df.at(prevX)

        for (i in 1..steps) {
            val x = xMin + i * step
            val y = formula.at(x)
            val currDf = df.at(x)

            // Asymptote / Discontinuity detection: huge jump or finite to NaN transition
            if ((prevY.isFinite() && !y.isFinite()) || (!prevY.isFinite() && y.isFinite())) {
                val midX = (prevX + x) / 2.0
                points.add(CriticalPoint(CriticalPointType.ASYMPTOTE, midX, Double.NaN, "Asymptote / discontinuity near x = ${fmt(midX)}"))
            }

            // Root / Zero detection: sign change of f(x)
            if (prevY.isFinite() && y.isFinite()) {
                if (abs(y) < EPSILON) {
                    addUniquePoint(points, CriticalPoint(CriticalPointType.ZERO, x, 0.0, "Zero: ($curveName = 0 at x = ${fmt(x)})"))
                } else if ((prevY < 0.0 && y > 0.0) || (prevY > 0.0 && y < 0.0)) {
                    // Refine root with bisection
                    val rootX = bisectRoot(formula, prevX, x, prevY, y)
                    if (rootX != null) {
                        addUniquePoint(points, CriticalPoint(CriticalPointType.ZERO, rootX, 0.0, "Zero: ($curveName = 0 at x = ${fmt(rootX)})"))
                    }
                }
            }

            // Local extrema detection: sign change of f'(x) or zero slope
            if (prevDf.isFinite() && currDf.isFinite() && prevY.isFinite() && y.isFinite()) {
                if (abs(currDf) < 1e-4) {
                    val extY = formula.at(x)
                    if (extY.isFinite()) {
                        val isMin = prevDf < 0.0 || df.at(x + step) > 0.0
                        val type = if (isMin) CriticalPointType.LOCAL_MIN else CriticalPointType.LOCAL_MAX
                        val label = if (isMin) "Local Min" else "Local Max"
                        addUniquePoint(points, CriticalPoint(type, x, extY, "$label: ($curveName at (${fmt(x)}, ${fmt(extY)}))"))
                    }
                } else if ((prevDf < 0.0 && currDf > 0.0) || (prevDf > 0.0 && currDf < 0.0)) {
                    val extX = bisectRoot(df, prevX, x, prevDf, currDf)
                    if (extX != null) {
                        val extY = formula.at(extX)
                        if (extY.isFinite()) {
                            val isMin = prevDf < 0.0
                            val type = if (isMin) CriticalPointType.LOCAL_MIN else CriticalPointType.LOCAL_MAX
                            val label = if (isMin) "Local Min" else "Local Max"
                            addUniquePoint(points, CriticalPoint(type, extX, extY, "$label: ($curveName at (${fmt(extX)}, ${fmt(extY)}))"))
                        }
                    }
                }
            }

            prevX = x
            prevY = y
            prevDf = currDf
        }

        return points
    }

    /** Finds mutual intersection points between two formulas [f1] and [f2] in [[xMin], [xMax]]. */
    fun findIntersections(
        f1: Formula,
        f2: Formula,
        xMin: Double,
        xMax: Double,
        steps: Int = 200,
        label1: String = "f1",
        label2: String = "f2",
    ): List<CriticalPoint> {
        val diff = Formula { x -> f1.at(x) - f2.at(x) }
        val roots = mutableListOf<CriticalPoint>()
        val step = (xMax - xMin) / steps
        var prevX = xMin
        var prevY = diff.at(prevX)

        for (i in 1..steps) {
            val x = xMin + i * step
            val y = diff.at(x)
            if (prevY.isFinite() && y.isFinite()) {
                if ((prevY < 0.0 && y > 0.0) || (prevY > 0.0 && y < 0.0)) {
                    val rootX = bisectRoot(diff, prevX, x, prevY, y)
                    if (rootX != null) {
                        val yVal = f1.at(rootX)
                        if (yVal.isFinite()) {
                            addUniquePoint(roots, CriticalPoint(CriticalPointType.INTERSECTION, rootX, yVal, "Intersection of $label1 & $label2 at (${fmt(rootX)}, ${fmt(yVal)})"))
                        }
                    }
                }
            }
            prevX = x
            prevY = y
        }
        return roots
    }

    /**
     * Formats a compact multi-line analysis report for the given curves and their critical points.
     *
     * [integrals] are the definite integrals to report, one `label to value` per curve — a null value
     * marks one that could not be evaluated, so the summary says so instead of printing a number.
     */
    fun buildSummaryReport(
        curves: List<PlotCurve>,
        points: List<CriticalPoint>,
        xMin: Double,
        xMax: Double,
        integrals: List<Pair<String, Double?>> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append("Function Study (x in [${fmt(xMin)}, ${fmt(xMax)}])\n")
        curves.forEachIndexed { i, c ->
            val label = c.label.ifEmpty { "f${i + 1}(x)" }
            sb.append("• $label = ${c.source}\n")
        }
        if (integrals.isNotEmpty()) {
            sb.append("Definite integrals over [${fmt(xMin)}, ${fmt(xMax)}]:\n")
            integrals.forEach { (label, value) ->
                sb.append(
                    if (value == null) "• ∫ $label dx: not finite over the range\n"
                    else "• ∫ $label dx = ${fmt(value)}\n",
                )
            }
        }
        if (points.isEmpty()) {
            sb.append("No critical points detected in this range.")
        } else {
            sb.append("Critical Points:\n")
            points.forEach { p ->
                val coord = if (p.y.isFinite()) "(${fmt(p.x)}, ${fmt(p.y)})" else "x ≈ ${fmt(p.x)}"
                sb.append("• [${p.type.label}] $coord: ${p.description}\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /** Helper to generate marker dot circles for critical points as StrokePoint loops. */
    fun generateMarkerStrokes(
        points: List<CriticalPoint>,
        toX: (Double) -> Double,
        toY: (Double) -> Double,
        radiusPt: Double = 3.5,
        strokeWidthPt: Double = 1.0,
    ): List<List<StrokePoint>> {
        val out = mutableListOf<List<StrokePoint>>()
        for (p in points) {
            if (!p.y.isFinite()) continue
            val cx = toX(p.x)
            val cy = toY(p.y)
            val circle = mutableListOf<StrokePoint>()
            val steps = 16
            for (step in 0..steps) {
                val angle = 2.0 * Math.PI * step / steps
                val px = cx + radiusPt * cos(angle)
                val py = cy + radiusPt * sin(angle)
                circle.add(StrokePoint(px, py, strokeWidthPt))
            }
            out.add(circle)
        }
        return out
    }

    private fun addUniquePoint(list: MutableList<CriticalPoint>, pt: CriticalPoint) {
        val exists = list.any { it.type == pt.type && abs(it.x - pt.x) < 0.05 && (it.y.isNaN() || abs(it.y - pt.y) < 0.05) }
        if (!exists) list.add(pt)
    }

    private fun bisectRoot(f: Formula, x1: Double, x2: Double, y1: Double, y2: Double, maxIter: Int = 24): Double? {
        var low = x1
        var high = x2
        var fLow = y1
        for (i in 0 until maxIter) {
            val mid = (low + high) / 2.0
            val fMid = f.at(mid)
            if (!fMid.isFinite()) return null
            if (abs(fMid) < 1e-10 || (high - low) < 1e-8) return mid
            if ((fLow < 0 && fMid < 0) || (fLow > 0 && fMid > 0)) {
                low = mid
                fLow = fMid
            } else {
                high = mid
            }
        }
        return (low + high) / 2.0
    }

    private fun fmt(d: Double): String {
        val text = String.format(java.util.Locale.US, "%.3f", d)
        return text.trimEnd('0').trimEnd('.')
    }
}

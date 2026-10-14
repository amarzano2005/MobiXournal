package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A function plot compiled into ink (`docs/mobix-format.md`, the synthesis export) — and, through `LivingInk`, the drawing on screen.
 *
 * A plot's recipe is a formula, and ink cannot hold a formula, so the export evaluates it — with the
 * app's own engine, so the exported graph is the graph that was on screen. There are two routes,
 * chosen by what the element actually holds:
 *
 *  - **Every curve Cartesian**: [MultiFunctionPlotter] draws the lot the way the app's plot tool does
 *    — frame, axes, ticks with their numbers, the curves in their own colours and line styles,
 *    critical points marked and the analysis summary below. This is the common case and the route that
 *    reproduces the screen exactly.
 *  - **At least one parametric or polar curve**: the plotter places only `y = f(x)` curves, so this
 *    compiler samples every kind itself ([AdvancedCurves.sampleParametric], and `r(θ)` turned into
 *    `(r cos θ, r sin θ)`), fits the shared frame around what came out and draws the curves inside it.
 *    The frame is drawn, the ticks are not: tick placement for an arbitrary parametric extent is a
 *    rendering decision this build has not made, and inventing axes would be worse than a plain frame.
 *
 * Either way the y-extent is **fitted to the data** rather than taken from the element: the element
 * stores the box the plot occupies, not the range its curve happened to reach, and it is the curve
 * that has to be visible.
 *
 * A curve whose formula does not parse is not drawn, and it is **named** in
 * [CompiledPlot.unparsed] — the caller reports it — rather than substituted or silently dropped.
 */
internal object PlotInk {

    /** The width a curve is drawn at when the element's own curves do not name one. */
    private const val NOMINAL_WIDTH_PT = 1.0

    /** Gap between the frame and the analysis summary, in points. */
    private const val SUMMARY_GAP_PT = 14.0

    /** Default end of a parametric range the element left unstated. */
    private const val DEFAULT_T_MAX = 2.0 * PI

    /** The one variable the expression parser has. */
    private const val PARSER_VARIABLE = "x"

    /**
     * The wash alpha of the shaded integral area: the canvas' own translucent-fill token, the one the
     * drag-select band is washed with, so a shade reads as a highlight over the graph rather than as a
     * second curve. It rides the stroke's `fill`, which is exactly how the desktop carries a fill
     * (`docs/xopp-format.md`), so the area survives a save to `.xopp`, SVG and PDF.
     */
    private const val SHADE_FILL_ALPHA = CanvasChrome.BAND_FILL_ALPHA

    /**
     * The **other** spellings a parameterised curve's variable arrives in. The app itself writes the
     * parameter as the parser's `x` — the graph dialog's parametric tab enters `cos(x)`/`sin(x)` — but
     * this format is not only written by this app: [mobix-elements.md] documents a polar curve as
     * `r(θ)` and a parametric pair as `x(t)`/`y(t)`, so a file from another implementation says
     * `theta` (or `t`) where the parser needs a variable. It is renamed, on word boundaries so `tan`,
     * `sqrt` and `atan` survive untouched, rather than losing the curve.
     */
    private val PARAMETER_VARIABLE = Regex("(?<![\\p{L}\\p{N}_])(?:theta|θ|t)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)

    fun ink(plot: PlotElement): CompiledPlot =
        if (plot.curves.all { it.kind == PlotKind.CARTESIAN }) cartesianInk(plot) else sampledInk(plot)

    // ---- the common case: the app's own plotter ------------------------------------------------

    private fun cartesianInk(plot: PlotElement): CompiledPlot {
        val compiled = plot.curves.map { it to compileFormula(it.source, plot.parameters) }
        val drawable = compiled.mapNotNull { (curve, formula) -> formula?.let { curve } }
        val unparsed = unparsed(compiled)
        if (drawable.isEmpty()) return CompiledPlot(emptyList(), unparsed)

        val result = MultiFunctionPlotter.plot(
            curves = drawable.map { PlotCurve(it.source, it.color, it.lineStyle) },
            xMin = plot.xMin,
            xMax = plot.xMax,
            leftPt = plot.left,
            topPt = plot.top,
            widthPt = plot.right - plot.left,
            heightPt = plot.bottom - plot.top,
            strokeWidthPt = nominalWidth(plot),
            samples = plot.samples,
            showCriticalPoints = plot.showCriticalPoints,
            includeDerivative = plot.includeDerivative,
            includeSummary = plot.summaryText != null,
            axisColor = plot.axisColor,
            parameters = plot.parameters,
        )
        if (result.pathGroups.isEmpty()) return CompiledPlot(emptyList(), unparsed)

        val out = mutableListOf<Element>()
        // The shaded integral area is a background wash: it goes in before the frame, the axes and the
        // curves, so it never paints over the graph it is the area *of*.
        result.frame?.let { frame -> out += shade(plot, frame) }
        for (group in result.pathGroups) {
            // A group carries its curve's colour, which is how the per-curve width survives a plotter
            // that takes one width for all of them; the axis and marker groups fall back to the nominal.
            val width = drawable.firstOrNull { it.color == group.color }?.strokeWidth ?: nominalWidth(plot)
            for (path in group.strokes) {
                if (path.size < 2) continue
                out += InkPrimitives.stroke(path, group.color, width, group.lineStyle)
            }
        }
        result.labels.forEach { out += InkPrimitives.text(it.text, it.x, it.y, plot.axisColor) }
        if (result.summaryText.isNotEmpty()) {
            out += InkPrimitives.text(
                content = result.summaryText,
                x = plot.left,
                y = plot.bottom + SUMMARY_GAP_PT,
                color = plot.axisColor,
            )
        }
        return CompiledPlot(out, unparsed)
    }

    // ---- the shaded integral area (docs/math-engine.md, section 3) -------------------------------

    /**
     * The **shaded integral area**: the region `shadeIntegral.from`..`shadeIntegral.to` between the
     * plot's curve and the x-axis (or between its two curves), as one closed polygon filled with the
     * curve's own colour at [SHADE_FILL_ALPHA].
     *
     * It is drawn **here, once**, and every surface reads it from here: the canvas through `LivingInk`,
     * SVG, PDF and the `.xopp` synthesis all walk the same elements, so they cannot disagree about the
     * area a shaded plot stands for. The polygon is placed in the frame the plotter fitted ([frame]), so
     * its edge is the curve that was drawn rather than a second approximation of it.
     *
     * The bounds are clipped to the plot's own domain. Nothing is drawn when the element does not
     * describe an area this build can draw — a curve that does not parse, a curve that leaves the plane
     * inside the bounds (an asymptote is a break, not a region), or, for a single curve, a frame that
     * does not contain the x-axis to shade against. An absent shade is a smaller loss than a filled
     * region that is not the integral.
     */
    private fun shade(plot: PlotElement, frame: PlotFrame): List<Element> {
        val shading = plot.shadeIntegral ?: return emptyList()
        val lo = minOf(shading.from, shading.to)
        val hi = maxOf(shading.from, shading.to)
        val from = maxOf(lo, plot.xMin)
        val to = minOf(hi, plot.xMax)
        // A range that never meets the domain has no area; degenerate bounds are no area either.
        if (to <= from) return emptyList()
        val polygon = when (plot.curves.size) {
            // One curve: the area between it and the x-axis. The y-window is fitted to the data and the
            // axis is only drawn when it falls inside it, so there is nothing to shade against otherwise.
            1 -> if (frame.containsXAxis) axisBand(plot.curves[0], from, to, plot, frame) else null
            // Two curves: the area between them — the region a shaded two-curve plot asks about.
            2 -> betweenCurves(plot.curves[0], plot.curves[1], from, to, plot, frame)
            // Three or more: "the area" is not one region, and guessing which one would be worse than
            // no wash at all.
            else -> null
        } ?: return emptyList()
        return listOf(InkPrimitives.stroke(polygon.points, polygon.color, polygon.widthPt, fill = SHADE_FILL_ALPHA))
    }

    /** The region between [curve] and the x-axis, closed down onto the axis at both bounds. */
    private fun axisBand(
        curve: PlotCurve,
        from: Double,
        to: Double,
        plot: PlotElement,
        frame: PlotFrame,
    ): Shade? {
        val formula = compileFormula(curve.source, plot.parameters) ?: return null
        val run = FunctionPlot.sample(formula, from, to, plot.samples).takeIf(::isUnbroken) ?: return null
        val points = ArrayList<StrokePoint>(run.size + 3)
        run.forEach { (x, y) -> points += InkPrimitives.pt(frame.toX(x), frame.toY(y), curve.strokeWidth) }
        points += InkPrimitives.pt(frame.toX(to), frame.toY(0.0), curve.strokeWidth)
        points += InkPrimitives.pt(frame.toX(from), frame.toY(0.0), curve.strokeWidth)
        points += points.first()
        return Shade(points, curve.color, curve.strokeWidth)
    }

    /**
     * The region enclosed by [a] and [b]: forward along the first, back along the second, closed. Where
     * the two cross inside the bounds the polygon crosses itself and a renderer fills both lobes, which
     * is the region between them rather than a wrong one.
     */
    private fun betweenCurves(
        a: PlotCurve,
        b: PlotCurve,
        from: Double,
        to: Double,
        plot: PlotElement,
        frame: PlotFrame,
    ): Shade? {
        val fa = compileFormula(a.source, plot.parameters) ?: return null
        val fb = compileFormula(b.source, plot.parameters) ?: return null
        val first = FunctionPlot.sample(fa, from, to, plot.samples).takeIf(::isUnbroken) ?: return null
        val second = FunctionPlot.sample(fb, from, to, plot.samples).takeIf(::isUnbroken) ?: return null
        val points = ArrayList<StrokePoint>(first.size + second.size + 1)
        first.forEach { (x, y) -> points += InkPrimitives.pt(frame.toX(x), frame.toY(y), a.strokeWidth) }
        for (index in second.indices.reversed()) {
            val (x, y) = second[index]
            points += InkPrimitives.pt(frame.toX(x), frame.toY(y), a.strokeWidth)
        }
        points += points.first()
        return Shade(points, a.color, a.strokeWidth)
    }

    /**
     * Whether [points] are one uninterrupted run: at least two, all finite. A single non-finite sample
     * means the curve leaves the plane inside the bounds, and the polygon across that break would be a
     * region the integral does not have.
     */
    private fun isUnbroken(points: List<Pair<Double, Double>>): Boolean =
        points.size >= 2 && points.all { (x, y) -> x.isFinite() && y.isFinite() }

    // ---- parametric and polar curves, which the plotter cannot place -----------------------------

    private fun sampledInk(plot: PlotElement): CompiledPlot {
        val compiled = plot.curves.map { it to sample(it, plot) }
        val sampled = compiled.mapNotNull { (curve, points) -> points?.let { curve to it } }
        val unparsed = unparsed(compiled)
        if (sampled.isEmpty()) return CompiledPlot(emptyList(), unparsed)
        val xs = sampled.flatMap { (_, points) -> points.mapNotNull { (x, _) -> x.takeIf(Double::isFinite) } }
        val ys = sampled.flatMap { (_, points) -> points.mapNotNull { (_, y) -> y.takeIf(Double::isFinite) } }
        if (xs.isEmpty() || ys.isEmpty()) return CompiledPlot(emptyList(), unparsed)

        val xMin = xs.min(); val xMax = xs.max()
        val yMin = ys.min(); val yMax = ys.max()
        val spanX = (xMax - xMin).takeIf { it > 1e-9 } ?: 1.0
        val spanY = (yMax - yMin).takeIf { it > 1e-9 } ?: 1.0
        val left = plot.left
        val top = plot.top
        val width = plot.right - plot.left
        val height = plot.bottom - plot.top
        fun toX(x: Double) = left + (x - xMin) / spanX * width
        fun toY(y: Double) = top + (yMax - y) / spanY * height

        val out = mutableListOf<Element>()
        out += frame(plot)
        for ((curve, points) in sampled) {
            for (run in finiteRuns(points)) {
                if (run.size < 2) continue
                val path = run.map { (x, y) -> InkPrimitives.pt(toX(x), toY(y), curve.strokeWidth) }
                out += InkPrimitives.stroke(path, curve.color, curve.strokeWidth, curve.lineStyle)
            }
        }
        return CompiledPlot(out, unparsed)
    }

    private fun frame(plot: PlotElement): Element {
        val width = nominalWidth(plot)
        return InkPrimitives.stroke(
            points = listOf(
                InkPrimitives.pt(plot.left, plot.top, width),
                InkPrimitives.pt(plot.right, plot.top, width),
                InkPrimitives.pt(plot.right, plot.bottom, width),
                InkPrimitives.pt(plot.left, plot.bottom, width),
                InkPrimitives.pt(plot.left, plot.top, width),
            ),
            color = plot.axisColor,
            widthPt = width,
        )
    }

    /** A curve's points over its own range: the variable the [PlotKind] makes it a function of. */
    private fun sample(curve: PlotCurve, plot: PlotElement): List<Pair<Double, Double>>? = when (curve.kind) {
        PlotKind.CARTESIAN -> compileFormula(curve.source, plot.parameters)
            ?.let { formula -> FunctionPlot.sample(formula, plot.xMin, plot.xMax, plot.samples) }

        PlotKind.PARAMETRIC -> {
            val x = compileFormula(curve.source, plot.parameters)
            val y = curve.sourceY?.let { compileFormula(it, plot.parameters) }
            if (x == null || y == null) {
                null
            } else {
                AdvancedCurves.sampleParametric(x, y, curve.tMin ?: 0.0, curve.tMax ?: DEFAULT_T_MAX, plot.samples)
            }
        }

        PlotKind.POLAR -> compileFormula(curve.source, plot.parameters)?.let { radius ->
            val from = curve.thetaMin ?: 0.0
            val to = curve.thetaMax ?: DEFAULT_T_MAX
            val steps = plot.samples.coerceIn(10, 4000)
            (0..steps).map { index ->
                val theta = from + (to - from) * index / steps
                val r = radius.at(theta)
                if (r.isFinite()) r * cos(theta) to r * sin(theta) else Double.NaN to Double.NaN
            }
        }
    }

    /**
     * [points] split where the curve leaves the plane — an asymptote, a polar radius that went to
     * infinity — so the ink is several strokes rather than a line drawn across the discontinuity.
     */
    private fun finiteRuns(points: List<Pair<Double, Double>>): List<List<Pair<Double, Double>>> {
        val out = mutableListOf<List<Pair<Double, Double>>>()
        var run = mutableListOf<Pair<Double, Double>>()
        for (point in points) {
            if (point.first.isFinite() && point.second.isFinite()) {
                run += point
            } else {
                if (run.isNotEmpty()) out += run
                run = mutableListOf()
            }
        }
        if (run.isNotEmpty()) out += run
        return out
    }

    /**
     * A curve's formula, compiled — in the parser's variable, or in a parameterised curve's own
     * spelling of it ([PARAMETER_VARIABLE]).
     */
    private fun compileFormula(source: String, parameters: Map<String, Double> = emptyMap()): Formula? =
        FunctionPlot.compile(source, parameters)
            ?: FunctionPlot.compile(PARAMETER_VARIABLE.replace(source, PARSER_VARIABLE), parameters)

    /** The sources of the curves of [compiled] whose formula would not parse. */
    private fun unparsed(compiled: List<Pair<PlotCurve, Any?>>): List<String> =
        compiled.filter { it.second == null }.map { it.first.source }

    private fun nominalWidth(plot: PlotElement): Double =
        plot.curves.firstOrNull()?.strokeWidth ?: NOMINAL_WIDTH_PT

    /** A shading polygon: its closed outline, and the curve colour and width it is washed in. */
    private data class Shade(val points: List<StrokePoint>, val color: Int, val widthPt: Double)
}

/**
 * What compiling a plot produced: the ink it became, and the sources of the curves it could not
 * evaluate. The two are reported separately because they are different losses — one curve of a
 * three-curve plot is not the same thing as a plot that drew nothing — and both go to the caller's
 * [Uncompilable] list rather than being smoothed over.
 */
internal data class CompiledPlot(val elements: List<Element>, val unparsed: List<String>)

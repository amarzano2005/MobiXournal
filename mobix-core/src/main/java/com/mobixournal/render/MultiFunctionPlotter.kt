package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs

/**
 * Pure engine that coordinates plotting multiple curves, feature/critical point detection,
 * and summary reports into styled stroke groups.
 */
object MultiFunctionPlotter {

    /**
     * Plots multiple curves [curves] over [xMin]..[xMax] within the given box.
     * Computes the bounding y range across all curves, axes, ticks, critical points, and returns
     * a [MultiPlotResult] ready to be converted into .xopp elements.
     *
     * [integralFrom]..[integralTo] is the range the summary's **definite integrals** are taken over, and
     * both the values and the header line they sit under are stated for it. It defaults to the frame,
     * and a caller that knows the plot stands for a smaller area — a shaded integral
     * (`PlotElement.shadeIntegral`) — passes that area, so the number reported is the number the wash
     * covers.
     */
    fun plot(
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        leftPt: Double,
        topPt: Double,
        widthPt: Double,
        heightPt: Double,
        strokeWidthPt: Double,
        samples: Int = FunctionPlot.DEFAULT_SAMPLES,
        showCriticalPoints: Boolean = false,
        includeDerivative: Boolean = false,
        includeSummary: Boolean = false,
        axisColor: Int = 0xFF000000.toInt(),
        parameters: Map<String, Double> = emptyMap(),
        integralFrom: Double = xMin,
        integralTo: Double = xMax,
    ): MultiPlotResult {
        if (curves.isEmpty() || xMax <= xMin || widthPt <= 0.0 || heightPt <= 0.0) {
            return MultiPlotResult(emptyList(), emptyList(), emptyList())
        }

        val compiledCurves = compileCurves(curves, includeDerivative, parameters)
        if (compiledCurves.isEmpty()) return MultiPlotResult(emptyList(), emptyList(), emptyList())

        val curveSamples = compiledCurves.map { (curve, formula) ->
            curve to FunctionPlot.sample(formula, xMin, xMax, samples)
        }
        val (yMin, yMax) = computeYBounds(curveSamples) ?: return MultiPlotResult(emptyList(), emptyList(), emptyList())

        val spanX = xMax - xMin
        val spanY = yMax - yMin
        val toX = { x: Double -> leftPt + (x - xMin) / spanX * widthPt }
        val toY = { y: Double -> topPt + (yMax - y) / spanY * heightPt }

        val labels = mutableListOf<PlotLabel>()
        val axisStrokes = buildAxesAndTicks(xMin, xMax, yMin, yMax, spanX, spanY, leftPt, topPt, widthPt, heightPt, strokeWidthPt, toX, toY, labels)

        val pathGroups = mutableListOf<PlotPathGroup>()
        pathGroups.add(PlotPathGroup(axisStrokes, axisColor, LineStyle.PLAIN))

        for ((curve, pts) in curveSamples) {
            val strokes = buildCurveStrokes(pts, spanY, toX, toY, strokeWidthPt)
            if (strokes.isNotEmpty()) pathGroups.add(PlotPathGroup(strokes, curve.color, curve.lineStyle))
        }

        var legendHeightPt = 0.0
        val criticalPoints = if (showCriticalPoints) {
            val detection = detectAllCriticalPoints(
                curves = curves,
                xMin = xMin,
                xMax = xMax,
                yMin = yMin,
                yMax = yMax,
                toX = toX,
                toY = toY,
                strokeWidthPt = strokeWidthPt,
                leftPt = leftPt,
                topPt = topPt,
                widthPt = widthPt,
                heightPt = heightPt,
                labels = labels,
                pathGroups = pathGroups,
                parameters = parameters,
            )
            legendHeightPt = detection.legendHeightPt
            detection.points
        } else emptyList()

        val summary = if (includeSummary) {
            FunctionAnalysis.buildSummaryReport(
                curves = curves,
                points = criticalPoints,
                xMin = xMin,
                xMax = xMax,
                integrals = buildIntegrals(compiledCurves, curves, integralFrom, integralTo),
                integralFrom = integralFrom,
                integralTo = integralTo,
            )
        } else ""
        return MultiPlotResult(
            pathGroups = pathGroups,
            labels = labels,
            criticalPoints = criticalPoints,
            summaryText = summary,
            frame = PlotFrame(xMin, xMax, yMin, yMax, leftPt, topPt, widthPt, heightPt),
            legendHeightPt = legendHeightPt,
        )
    }

    /**
     * The definite integral of each of the caller's own Cartesian curves over [xMin]..[xMax], paired with
     * the label the summary names it by — the frame by default, or the shaded area a plot stands for. The
     * derivative copies [compileCurves] appends are skipped — they are not the caller's curves — so the
     * report shows one line per plotted function.
     */
    private fun buildIntegrals(
        compiledCurves: List<Pair<PlotCurve, Formula>>,
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
    ): List<Pair<String, Double?>> = compiledCurves
        .filter { (curve, _) -> curves.any { it === curve } }
        .map { (curve, formula) ->
            curve.label.ifEmpty { curve.source } to FunctionAnalysis.definiteIntegral(formula, xMin, xMax)
        }

    private fun compileCurves(
        curves: List<PlotCurve>,
        includeDerivative: Boolean,
        parameters: Map<String, Double> = emptyMap(),
    ): List<Pair<PlotCurve, Formula>> {
        val out = mutableListOf<Pair<PlotCurve, Formula>>()
        for (c in curves) {
            // This plotter places `y = f(x)` and nothing else: a parametric or polar curve needs the
            // sampling and the fitted frame `PlotInk` gives it, so it is skipped rather than compiled
            // as the wave its formula would look like (which is a different drawing, not a worse one).
            if (c.kind != PlotKind.CARTESIAN) continue
            val f = FunctionPlot.compile(c.source, parameters) ?: continue
            out.add(c to f)
            if (includeDerivative) {
                val df = FunctionAnalysis.derivative(f)
                val dColor = adjustColorForDerivative(c.color)
                val dCurve = PlotCurve(
                    source = "${c.source}'",
                    color = dColor,
                    lineStyle = LineStyle.DASHED,
                    label = (c.label.ifEmpty { "f" }) + "'",
                )
                out.add(dCurve to df)
            }
        }
        return out
    }

    private fun computeYBounds(samples: List<Pair<PlotCurve, List<Pair<Double, Double>>>>): Pair<Double, Double>? {
        val allFiniteY = samples.flatMap { (_, pts) -> pts.mapNotNull { it.second.takeIf(Double::isFinite) } }
        if (allFiniteY.isEmpty()) return null
        var yMin = allFiniteY.min()
        var yMax = allFiniteY.max()
        if (yMax - yMin < 1e-9) {
            yMin -= 1.0
            yMax += 1.0
        }
        val pad = (yMax - yMin) * 0.08
        return (yMin - pad) to (yMax + pad)
    }

    private fun buildAxesAndTicks(
        xMin: Double, xMax: Double, yMin: Double, yMax: Double,
        spanX: Double, spanY: Double,
        leftPt: Double, topPt: Double, widthPt: Double, heightPt: Double,
        strokeWidthPt: Double, toX: (Double) -> Double, toY: (Double) -> Double,
        labels: MutableList<PlotLabel>,
    ): List<List<StrokePoint>> {
        val strokes = mutableListOf<List<StrokePoint>>()
        strokes.add(frame(leftPt, topPt, widthPt, heightPt, strokeWidthPt))
        if (yMin <= 0.0 && yMax >= 0.0) {
            val y = toY(0.0)
            strokes.add(listOf(StrokePoint(leftPt, y, strokeWidthPt), StrokePoint(leftPt + widthPt, y, strokeWidthPt)))
        }
        if (xMin <= 0.0 && xMax >= 0.0) {
            val x = toX(0.0)
            strokes.add(listOf(StrokePoint(x, topPt, strokeWidthPt), StrokePoint(x, topPt + heightPt, strokeWidthPt)))
        }
        strokes.addAll(ticks(xMin, xMax, FunctionPlot.niceStep(spanX, 8), leftPt, topPt, heightPt, strokeWidthPt, toX, labels, vertical = false))
        strokes.addAll(ticks(yMin, yMax, FunctionPlot.niceStep(spanY, 8), leftPt, topPt, heightPt, strokeWidthPt, toY, labels, vertical = true))
        return strokes
    }

    private fun detectAllCriticalPoints(
        curves: List<PlotCurve>,
        xMin: Double, xMax: Double, yMin: Double, yMax: Double,
        toX: (Double) -> Double, toY: (Double) -> Double,
        strokeWidthPt: Double,
        leftPt: Double, topPt: Double, widthPt: Double, heightPt: Double,
        labels: MutableList<PlotLabel>,
        pathGroups: MutableList<PlotPathGroup>,
        parameters: Map<String, Double> = emptyMap(),
    ): CriticalDetection {
        val points = mutableListOf<CriticalPoint>()
        curves.forEachIndexed { i, c ->
            val f = FunctionPlot.compile(c.source, parameters) ?: return@forEachIndexed
            points.addAll(FunctionAnalysis.findCriticalPoints(f, xMin, xMax, curveName = c.label.ifEmpty { "f${i + 1}(x)" }))
        }
        for (i in 0 until curves.size) {
            val f1 = FunctionPlot.compile(curves[i].source, parameters) ?: continue
            val l1 = curves[i].label.ifEmpty { "f${i + 1}" }
            for (j in i + 1 until curves.size) {
                val f2 = FunctionPlot.compile(curves[j].source, parameters) ?: continue
                val l2 = curves[j].label.ifEmpty { "f${j + 1}" }
                points.addAll(FunctionAnalysis.findIntersections(f1, f2, xMin, xMax, label1 = l1, label2 = l2))
            }
        }
        val inRange = points.filter { p -> p.x in xMin..xMax && p.y in yMin..yMax }
        val markers = FunctionAnalysis.generateMarkers(inRange, toX, toY, radiusPt = 3.0, strokeWidthPt = strokeWidthPt * 1.2)
        // A group per kind, so the dot's colour is the legend's colour; iterating the enum keeps the
        // drawing order stable rather than following discovery order.
        val present = mutableListOf<CriticalPointType>()
        for (type in CriticalPointType.values()) {
            val ofType = markers.filter { it.type == type }
            if (ofType.isEmpty()) continue
            present += type
            pathGroups.add(PlotPathGroup(ofType.map { it.glyph }, FunctionAnalysis.markerColor(type), LineStyle.PLAIN))
        }
        val legendHeight = if (present.isEmpty()) {
            0.0
        } else {
            markerLegend(
                types = present,
                leftPt = leftPt,
                topPt = topPt,
                widthPt = widthPt,
                heightPt = heightPt,
                strokeWidthPt = strokeWidthPt,
                labels = labels,
                pathGroups = pathGroups,
            )
        }
        return CriticalDetection(points, legendHeight)
    }

    /** What detecting the markers produced: every point for the summary, and the legend's own height. */
    private data class CriticalDetection(val points: List<CriticalPoint>, val legendHeightPt: Double)

    /**
     * The **marker legend** under the frame: the glyph and name of every kind of point that was
     * marked, laid out in one or more rows inside the plot's width.
     *
     * It starts below the x tick numbers (which own the first line under the frame) and returns the
     * distance it reaches below the frame, so [plot] can hand the summary a place under it. A legend
     * names *this* plot's marks only — a plot of `sin(x)` has no “intersection” key until a second
     * curve crosses it.
     */
    private fun markerLegend(
        types: List<CriticalPointType>,
        leftPt: Double,
        topPt: Double,
        widthPt: Double,
        heightPt: Double,
        strokeWidthPt: Double,
        labels: MutableList<PlotLabel>,
        pathGroups: MutableList<PlotPathGroup>,
    ): Double {
        val size = InkTokens.FIGURE_LABEL_SIZE_PT
        val lineHeight = size * InkTokens.FIGURE_LABEL_LINE_H
        val radius = 3.0
        val glyphTextGap = 3.0
        val entryGap = 9.0
        val top = topPt + heightPt + 4.0 + lineHeight + 3.0
        val right = leftPt + widthPt
        var x = leftPt
        var row = 0
        for (type in types) {
            val name = type.legendName()
            val entryWidth = radius * 2 + glyphTextGap + TextAdvance.pt(name, size)
            if (x > leftPt && x + entryWidth > right) {
                row++
                x = leftPt
            }
            val centreY = top + row * lineHeight + lineHeight / 2.0
            pathGroups.add(
                PlotPathGroup(
                    strokes = listOf(
                        FunctionAnalysis.markerGlyph(
                            type = type,
                            cx = x + radius,
                            cy = centreY,
                            radiusPt = radius,
                            strokeWidthPt = strokeWidthPt * 1.2,
                        ),
                    ),
                    color = FunctionAnalysis.markerColor(type),
                    lineStyle = LineStyle.PLAIN,
                ),
            )
            labels.add(PlotLabel(name, x + radius * 2 + glyphTextGap, top + row * lineHeight))
            x += entryWidth + entryGap
        }
        val rows = row + 1
        return (top - (topPt + heightPt)) + rows * lineHeight
    }

    /** The word the legend keys a kind of point under. */
    private fun CriticalPointType.legendName(): String = when (this) {
        CriticalPointType.ZERO -> "root"
        CriticalPointType.Y_INTERCEPT -> "y-intercept"
        CriticalPointType.LOCAL_MIN -> "min"
        CriticalPointType.LOCAL_MAX -> "max"
        CriticalPointType.INTERSECTION -> "intersection"
        CriticalPointType.ASYMPTOTE -> "asymptote"
    }

    private fun frame(leftPt: Double, topPt: Double, widthPt: Double, heightPt: Double, strokeWidthPt: Double): List<StrokePoint> {
        fun p(x: Double, y: Double) = StrokePoint(x, y, strokeWidthPt)
        return listOf(
            p(leftPt, topPt),
            p(leftPt + widthPt, topPt),
            p(leftPt + widthPt, topPt + heightPt),
            p(leftPt, topPt + heightPt),
            p(leftPt, topPt),
        )
    }

    private fun ticks(
        from: Double, to: Double, step: Double,
        leftPt: Double, topPt: Double, heightPt: Double,
        strokeWidthPt: Double, toPt: (Double) -> Double,
        labels: MutableList<PlotLabel>, vertical: Boolean,
    ): List<List<StrokePoint>> {
        if (step <= 0.0) return emptyList()
        val out = ArrayList<List<StrokePoint>>()
        var value = kotlin.math.ceil(from / step) * step
        val size = InkTokens.FIGURE_LABEL_SIZE_PT
        val halfHeight = size * InkTokens.FIGURE_LABEL_LINE_H / 2.0
        while (value <= to + step * 1e-6) {
            val at = toPt(value)
            val text = FunctionPlot.label(value, step)
            // The number's own measured advance (docs/mobix-living-entities.md, the width estimate):
            // a flat per-character guess right-aligned a `-0.5` as though it were a `0.5` and left the
            // x labels off the tick they name.
            val advance = TextAdvance.pt(text, size)
            if (vertical) {
                if (at in (topPt - 1e-6)..(topPt + heightPt + 1e-6)) {
                    out.add(listOf(StrokePoint(leftPt, at, strokeWidthPt), StrokePoint(leftPt + 4.0, at, strokeWidthPt)))
                    labels.add(PlotLabel(text, leftPt - 4.0 - advance, at - halfHeight))
                }
            } else {
                out.add(listOf(StrokePoint(at, topPt + heightPt, strokeWidthPt), StrokePoint(at, topPt + heightPt - 4.0, strokeWidthPt)))
                labels.add(PlotLabel(text, at - advance / 2.0, topPt + heightPt + 4.0))
            }
            value += step
        }
        return out
    }

    private fun buildCurveStrokes(
        points: List<Pair<Double, Double>>,
        spanY: Double,
        toX: (Double) -> Double,
        toY: (Double) -> Double,
        strokeWidthPt: Double,
    ): List<List<StrokePoint>> {
        val out = ArrayList<List<StrokePoint>>()
        var run = ArrayList<StrokePoint>()
        var previous: Double? = null
        val breakFraction = 0.25
        for ((x, y) in points) {
            val broken = !y.isFinite() || (previous != null && abs(y - previous) > spanY * breakFraction)
            if (broken) {
                if (run.size >= 2) out += run
                run = ArrayList()
                previous = if (y.isFinite()) y else null
                continue
            }
            run += StrokePoint(toX(x), toY(y), strokeWidthPt)
            previous = y
        }
        if (run.size >= 2) out += run
        return out
    }

    private fun adjustColorForDerivative(color: Int): Int {
        val a = (color shr 24) and 0xFF
        val r = ((color shr 16) and 0xFF) / 2
        val g = ((color shr 8) and 0xFF) / 2
        val b = (color and 0xFF) / 2
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

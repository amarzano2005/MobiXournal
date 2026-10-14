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

        val criticalPoints = if (showCriticalPoints) {
            detectAllCriticalPoints(curves, xMin, xMax, yMin, yMax, toX, toY, strokeWidthPt, pathGroups, parameters)
        } else emptyList()

        val summary = if (includeSummary) {
            FunctionAnalysis.buildSummaryReport(curves, criticalPoints, xMin, xMax, buildIntegrals(compiledCurves, curves, xMin, xMax))
        } else ""
        return MultiPlotResult(pathGroups, labels, criticalPoints, summary, PlotFrame(xMin, xMax, yMin, yMax, leftPt, topPt, widthPt, heightPt))
    }

    /**
     * The definite integral of each of the caller's own Cartesian curves over the frame, paired with the
     * label the summary names it by. The derivative copies [compileCurves] appends are skipped — they are
     * not the caller's curves — so the report shows one line per plotted function.
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
            labels.add(PlotLabel("0", leftPt - 4.0, y + 4.0))
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
        pathGroups: MutableList<PlotPathGroup>,
        parameters: Map<String, Double> = emptyMap(),
    ): List<CriticalPoint> {
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
        val markerStrokes = FunctionAnalysis.generateMarkerStrokes(inRange, toX, toY, radiusPt = 3.0, strokeWidthPt = strokeWidthPt * 1.2)
        if (markerStrokes.isNotEmpty()) {
            pathGroups.add(PlotPathGroup(markerStrokes, 0xFFD32F2F.toInt(), LineStyle.PLAIN))
        }
        return points
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
        while (value <= to + step * 1e-6) {
            val at = toPt(value)
            val text = FunctionPlot.label(value, step)
            if (vertical) {
                if (at in (topPt - 1e-6)..(topPt + heightPt + 1e-6)) {
                    out.add(listOf(StrokePoint(leftPt, at, strokeWidthPt), StrokePoint(leftPt + 4.0, at, strokeWidthPt)))
                    labels.add(PlotLabel(text, leftPt - 4.0 - text.length * 5.0, at - 2.5))
                }
            } else {
                out.add(listOf(StrokePoint(at, topPt + heightPt, strokeWidthPt), StrokePoint(at, topPt + heightPt - 4.0, strokeWidthPt)))
                labels.add(PlotLabel(text, at - text.length * 2.5, topPt + heightPt + 4.0))
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

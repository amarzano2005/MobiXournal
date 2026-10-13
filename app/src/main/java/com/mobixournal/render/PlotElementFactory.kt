package com.mobixournal.render

import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotCurve as ElementPlotCurve

/**
 * Where a freshly inserted plot sits on a page, and how big it is, in points.
 *
 * One definition of that arithmetic because two paths need the same answer: the ink insertion
 * (`DrawingSurfaceView.insertMultiPlot`) and the living element one
 * ([PlotElementFactory.plotElementFor]). They must not drift — an inserted graph sits where the pen
 * would have put it, or the same dialog answer would land in two different places depending on which
 * one ran.
 */
internal data class PlotBox(val left: Double, val top: Double, val width: Double, val height: Double) {

    /** The box's right edge in points. */
    val right: Double get() = left + width

    /** The box's bottom edge in points. */
    val bottom: Double get() = top + height
}

/**
 * The **living plot** a graph insertion produces: the recipe (curves, domain, flags), not the ink it
 * draws as.
 *
 * Why it exists: the graph dialog's answer used to be compiled into strokes and the formula thrown
 * away, so a graph could be created but never corrected — and in a `.mobix` document the formula is
 * exactly what the format exists to keep (`docs/mobix-format.md`). This factory is the conversion, in
 * one place, Android-free so it can be tested on the JVM: the caller supplies the viewport centre
 * ([DrawingSurfaceView.viewportCentrePt]) and the pen's own width and colour, which are the only
 * inputs that need a running editor.
 *
 * It places an element in a box, and nothing here decides where that box is
 * ([boxFor](PlotElementFactory.boxFor) answers that from a page and a point), so the same conversion
 * serves the editor's preview: the dialog previews the very element it is about to insert, drawn by
 * the very compiler the page uses, and cannot promise a drawing the insertion will not make.
 *
 * Three things it deliberately does **not** invent:
 *
 *  - **A summary it was not asked for.** `summaryText` is the engine's already-formatted analysis, and
 *    the model documents it as exactly that: a cache for a reader that will not run the math. When the
 *    dialog's *include summary* is on, the analysis is computed once here and stored, so the element
 *    carries it and `PlotInk` draws it (it reads the field's presence as the flag); leaving it null
 *    would quietly turn that checkbox into a no-op. It is only computed for Cartesian curves, which are
 *    the ones the analysis engine can read.
 *  - **An identity.** `id` is null; the codec mints one at the first save (`ElementId`).
 *  - **A parameterisation the caller did not state.** Each curve keeps the [PlotKind] it was asked for
 *    ([PlotCurve.kind]) and, for a parametric pair, its `y(t)`; the dialog's range becomes the
 *    *parameter* range of such a curve (t or θ) rather than a Cartesian domain it does not have. Every
 *    curve that states nothing is Cartesian, which is what the plot tool has always drawn.
 *
 * The stroke width and axis colour are parameters, not constants, because the ink this replaces was
 * drawn in the user's pen: passing the same width and colour is what makes the element draw as the
 * insertion used to.
 */
internal object PlotElementFactory {

    /** How much of the page's width a fresh plot takes, before the clamp below. */
    private const val WIDTH_FRACTION = 0.45

    /** The narrowest and widest a fresh plot is drawn, in points. */
    private const val MIN_WIDTH_PT = 240.0
    private const val MAX_WIDTH_PT = 320.0

    /** A fresh plot's height as a fraction of its width. */
    private const val HEIGHT_FRACTION = 0.6

    /**
     * The box a fresh plot takes on [page], centred on ([centreX], [centreY]) — the viewport's centre
     * in page points — and kept inside the page, so a graph inserted while zoomed out is never placed
     * half off the sheet.
     */
    fun boxFor(page: Page, centreX: Double, centreY: Double): PlotBox {
        val width = (page.width * WIDTH_FRACTION).coerceIn(MIN_WIDTH_PT, MAX_WIDTH_PT)
        val height = width * HEIGHT_FRACTION
        val left = (centreX - width / 2).coerceIn(0.0, (page.width - width).coerceAtLeast(0.0))
        val top = (centreY - height / 2).coerceIn(0.0, (page.height - height).coerceAtLeast(0.0))
        return PlotBox(left = left, top = top, width = width, height = height)
    }

    /**
     * The living plot for the dialog's answer: [curves] over [xMin]..[xMax], placed by [boxFor] around
     * ([centreX], [centreY]) on [page], drawn in the pen's [strokeWidthPt] and [axisColor].
     *
     * @param curves The curves as the dialog states them (formula, colour, line style).
     * @param xMin Left end of the domain.
     * @param xMax Right end of the domain.
     * @param centreX X of the placement centre in page points.
     * @param centreY Y of the placement centre in page points.
     * @param page The page the plot is placed on — its size is what the box is clamped to.
     * @param showCriticalPoints Whether roots/extrema/inflections are marked.
     * @param includeDerivative Whether the first derivative is drawn alongside.
     * @param strokeWidthPt The width the plot is drawn at, in points.
     * @param axisColor The frame, axes and label colour as an ARGB int.
     * @param includeSummary Whether the analysis summary is computed and stored in [PlotElement.summaryText].
     */
    fun plotElementFor(
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        centreX: Double,
        centreY: Double,
        page: Page,
        showCriticalPoints: Boolean = false,
        includeDerivative: Boolean = false,
        strokeWidthPt: Double = 1.0,
        axisColor: Int = PlotElement.DEFAULT_AXIS,
        includeSummary: Boolean = false,
    ): PlotElement = plotElementIn(
        box = boxFor(page, centreX, centreY),
        curves = curves,
        xMin = xMin,
        xMax = xMax,
        showCriticalPoints = showCriticalPoints,
        includeDerivative = includeDerivative,
        strokeWidthPt = strokeWidthPt,
        axisColor = axisColor,
        includeSummary = includeSummary,
    )

    /**
     * The living plot for [curves] placed in an already-decided [box] — the half of
     * [plotElementFor] that has no opinion about where the plot goes, so a preview can ask for the
     * same element in its own box.
     */
    fun plotElementIn(
        box: PlotBox,
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        showCriticalPoints: Boolean = false,
        includeDerivative: Boolean = false,
        strokeWidthPt: Double = 1.0,
        axisColor: Int = PlotElement.DEFAULT_AXIS,
        includeSummary: Boolean = false,
    ): PlotElement {
        val elementCurves = curves.map { curve ->
            ElementPlotCurve(
                kind = curve.kind,
                source = curve.source,
                sourceY = if (curve.kind == PlotKind.PARAMETRIC) curve.sourceY else null,
                color = curve.color,
                strokeWidth = strokeWidthPt,
                lineStyle = curve.lineStyle,
                // A parametric or polar curve sweeps its own parameter; the dialog's range is that
                // range for such a curve, not a Cartesian domain it does not have.
                tMin = xMin.takeIf { curve.kind == PlotKind.PARAMETRIC },
                tMax = xMax.takeIf { curve.kind == PlotKind.PARAMETRIC },
                thetaMin = xMin.takeIf { curve.kind == PlotKind.POLAR },
                thetaMax = xMax.takeIf { curve.kind == PlotKind.POLAR },
            )
        }
        return PlotElement(
            left = box.left,
            top = box.top,
            right = box.right,
            bottom = box.bottom,
            curves = elementCurves,
            xMin = xMin,
            xMax = xMax,
            samples = PlotElement.DEFAULT_SAMPLES,
            showCriticalPoints = showCriticalPoints,
            includeDerivative = includeDerivative,
            axisColor = axisColor,
            summaryText = summaryFor(
                curves = curves,
                xMin = xMin,
                xMax = xMax,
                box = box,
                strokeWidthPt = strokeWidthPt,
                showCriticalPoints = showCriticalPoints,
                includeDerivative = includeDerivative,
                includeSummary = includeSummary,
                axisColor = axisColor,
            ),
            id = null,
        )
    }

    /**
     * The analysis summary the dialog asked for, or null when it did not.
     *
     * It runs [MultiFunctionPlotter] — the same engine the drawing uses — so the text the element
     * carries is the text the plot was drawn with, rather than a second implementation that could
     * report different roots from the curve beside it.
     */
    private fun summaryFor(
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        box: PlotBox,
        strokeWidthPt: Double,
        showCriticalPoints: Boolean,
        includeDerivative: Boolean,
        includeSummary: Boolean,
        axisColor: Int,
        parameters: Map<String, Double> = emptyMap(),
    ): String? {
        if (!includeSummary) return null
        val plotted = MultiFunctionPlotter.plot(
            curves = curves,
            xMin = xMin,
            xMax = xMax,
            leftPt = box.left,
            topPt = box.top,
            widthPt = box.width,
            heightPt = box.height,
            strokeWidthPt = strokeWidthPt,
            showCriticalPoints = showCriticalPoints,
            includeDerivative = includeDerivative,
            includeSummary = true,
            axisColor = axisColor,
            parameters = parameters,
        )
        return plotted.summaryText.ifEmpty { null }
    }

    /**
     * Updates [paramName] to [paramValue] in [plot.parameters] and recomputes the analysis
     * summary cache if one was present.
     */
    fun updateParameter(
        plot: PlotElement,
        paramName: String,
        paramValue: Double,
    ): PlotElement {
        val updated = plot.parameters.toMutableMap()
        updated[paramName] = paramValue
        return applyParameters(plot, updated)
    }

    /**
     * Applies new [parameters] to an existing [plot], refreshing its cached [PlotElement.summaryText]
     * if one was present, through [MultiFunctionPlotter] and [FunctionAnalysis].
     */
    fun applyParameters(
        plot: PlotElement,
        parameters: Map<String, Double>,
    ): PlotElement {
        val dialogCurves = plot.curves.map { c ->
            PlotCurve(
                source = c.source,
                color = c.color,
                lineStyle = c.lineStyle,
                kind = c.kind,
                sourceY = c.sourceY,
            )
        }
        val box = PlotBox(plot.left, plot.top, plot.right - plot.left, plot.bottom - plot.top)
        val summary = if (plot.summaryText != null) {
            summaryFor(
                curves = dialogCurves,
                xMin = plot.xMin,
                xMax = plot.xMax,
                box = box,
                strokeWidthPt = plot.curves.firstOrNull()?.strokeWidth ?: 1.0,
                showCriticalPoints = plot.showCriticalPoints,
                includeDerivative = plot.includeDerivative,
                includeSummary = true,
                axisColor = plot.axisColor,
                parameters = parameters,
            )
        } else null
        return plot.copy(
            parameters = parameters,
            summaryText = summary,
        )
    }

    /**
     * Applies edited curves, domain range, and analysis flags back to an existing [PlotElement].
     *
     * **Preserved fields**: Any fields the graph dialog has no UI input for —
     * [PlotElement.samples], [PlotElement.parameters], [PlotElement.shadeIntegral],
     * [PlotElement.axisColor], [PlotElement.extraAttrs], and [PlotElement.id] — **survive the edit**
     * via [PlotElement.copy] rather than being reset to factory defaults. [PlotElement.summaryText] is
     * recomputed if [includeSummary] is true, or preserved from [original] otherwise.
     *
     * An edited formula that fails to parse is still written to the curve record rather than
     * silently dropped, and will be rendered/reported by [LivingInk] the same way as an export.
     */
    fun applyPlotEdit(
        original: PlotElement,
        editedCurves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        showCriticalPoints: Boolean,
        includeDerivative: Boolean,
        includeSummary: Boolean = false,
    ): PlotElement {
        val elementCurves = editedCurves.mapIndexed { index, curve ->
            val origCurve = original.curves.getOrNull(index)
            ElementPlotCurve(
                kind = curve.kind,
                source = curve.source,
                sourceY = if (curve.kind == PlotKind.PARAMETRIC) curve.sourceY else null,
                color = curve.color,
                strokeWidth = origCurve?.strokeWidth ?: 1.0,
                lineStyle = curve.lineStyle,
                tMin = xMin.takeIf { curve.kind == PlotKind.PARAMETRIC },
                tMax = xMax.takeIf { curve.kind == PlotKind.PARAMETRIC },
                thetaMin = xMin.takeIf { curve.kind == PlotKind.POLAR },
                thetaMax = xMax.takeIf { curve.kind == PlotKind.POLAR },
            )
        }
        val box = PlotBox(original.left, original.top, original.right, original.bottom)
        val summary = if (includeSummary) {
            summaryFor(
                curves = editedCurves,
                xMin = xMin,
                xMax = xMax,
                box = box,
                strokeWidthPt = elementCurves.firstOrNull()?.strokeWidth ?: 1.0,
                showCriticalPoints = showCriticalPoints,
                includeDerivative = includeDerivative,
                includeSummary = true,
                axisColor = original.axisColor,
            )
        } else {
            original.summaryText
        }
        return original.copy(
            curves = elementCurves,
            xMin = xMin,
            xMax = xMax,
            showCriticalPoints = showCriticalPoints,
            includeDerivative = includeDerivative,
            summaryText = summary,
        )
    }
}

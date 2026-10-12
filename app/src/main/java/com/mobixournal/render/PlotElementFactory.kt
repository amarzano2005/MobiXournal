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
 * Three things it deliberately does **not** invent:
 *
 *  - **A summary it was not asked for.** `summaryText` is the engine's already-formatted analysis, and
 *    the model documents it as exactly that: a cache for a reader that will not run the math. When the
 *    dialog's *include summary* is on, the analysis is computed once here and stored, so the element
 *    carries it and `PlotInk` draws it (it reads the field's presence as the flag); leaving it null
 *    would quietly turn that checkbox into a no-op.
 *  - **An identity.** `id` is null; the codec mints one at the first save (`ElementId`).
 *  - **A kind the dialog did not state.** The dialog's curve type carries no [PlotKind], and every
 *    curve it produces is drawn by the plotter as `y = f(x)`, so each is written as
 *    [PlotKind.CARTESIAN] — the drawing the insertion always made.
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
    ): PlotElement {
        val box = boxFor(page, centreX, centreY)
        val elementCurves = curves.map { curve ->
            ElementPlotCurve(
                kind = PlotKind.CARTESIAN,
                source = curve.source,
                color = curve.color,
                strokeWidth = strokeWidthPt,
                lineStyle = curve.lineStyle,
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
        )
        return plotted.summaryText.ifEmpty { null }
    }
}

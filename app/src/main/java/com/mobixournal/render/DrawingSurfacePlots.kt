/**
 * The **plot insertions**: the two ways a graph gets onto a page — as ordinary ink
 * ([insertMultiPlot], the single-formula [insertPlot]) and as a living [PlotElement]
 * ([insertPlotElement]) that keeps its formula.
 *
 * They are split out of `DrawingSurfaceStrokes` because they are their own topic: that file is about
 * capturing ink from events, this one is about what a plotted function becomes once it is on the page.
 * Both share the placement arithmetic ([PlotElementFactory.boxFor]), so a graph lands in the same place
 * whichever path produced it, and both are extensions on the view so they read its state directly.
 */
package com.mobixournal.render

import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement

/**
 * Insert a **plotted function** on the visible page as one undoable edit: the graph's frame, axes,
 * ticks and curve ([FunctionPlot]) in the live pen colour, plus a text box for every tick number.
 *
 * The whole plot is ordinary ink, so it selects, moves, restyles, erases and saves like anything else
 * drawn by hand — and nothing about the formula reaches the file, which has nowhere to keep it.
 *
 * @param source The formula, e.g. `sin(x)`; an unparsable one inserts nothing at all.
 * @param xMin Left edge of the x range the curve is sampled over.
 * @param xMax Right edge of the x range.
 */
fun DrawingSurfaceView.insertPlot(
    source: String,
    xMin: Double,
    xMax: Double,
    samples: Int = FunctionPlot.DEFAULT_SAMPLES,
) {
    val color = strokeColor()
    val curve = PlotCurve(source = source, color = color, lineStyle = currentLineStyle)
    insertMultiPlot(
        curves = listOf(curve),
        xMin = xMin,
        xMax = xMax,
        samples = samples,
    )
}

/**
 * Inserts one or more plotted functions onto the active page, along with optional critical points
 * and analysis summary text table.
 */
fun DrawingSurfaceView.insertMultiPlot(
    curves: List<PlotCurve>,
    xMin: Double,
    xMax: Double,
    samples: Int = FunctionPlot.DEFAULT_SAMPLES,
    showCriticalPoints: Boolean = false,
    includeDerivative: Boolean = false,
    includeSummary: Boolean = false,
) {
    val index = visiblePageIndex()
    val page = pageAt(index) ?: return
    val (centreX, centreY) = viewportCentrePt(index)
    // The box comes from the factory that also places the *element* insertion, so a graph lands in the
    // same spot whichever of the two paths produced it.
    val box = PlotElementFactory.boxFor(page, centreX, centreY)
    val widthPt = box.width
    val heightPt = box.height
    val leftPt = box.left
    val topPt = box.top
    val defaultColor = strokeColor()

    val result = MultiFunctionPlotter.plot(
        curves = curves,
        xMin = xMin,
        xMax = xMax,
        leftPt = leftPt,
        topPt = topPt,
        widthPt = widthPt,
        heightPt = heightPt,
        strokeWidthPt = baseWidthPt.toDouble().coerceAtLeast(0.5),
        samples = samples,
        showCriticalPoints = showCriticalPoints,
        includeDerivative = includeDerivative,
        includeSummary = includeSummary,
        axisColor = defaultColor,
    )
    if (result.pathGroups.isEmpty()) return

    val newStrokes = result.pathGroups.flatMap { group ->
        group.strokes.map { pts -> Stroke(tool, group.color, "round", pts, true, lineStyle = group.lineStyle) }
    }
    val newLabels = result.labels.map { (text, x, y) ->
        TextElement(FIGURE_LABEL_FONT, FIGURE_LABEL_SIZE_PT, x, y, defaultColor, text)
    }
    val summaryElements = if (result.summaryText.isNotEmpty()) {
        listOf(TextElement(FIGURE_LABEL_FONT, FIGURE_LABEL_SIZE_PT, leftPt, topPt + heightPt + 20.0, defaultColor, result.summaryText))
    } else emptyList()
    val allNewElements = newStrokes + newLabels + summaryElements

    val before = doc
    val layers = page.layers.ifEmpty { listOf(Layer(emptyList())) }.toMutableList()
    val target = resolvedActiveLayer(page).coerceIn(0, layers.lastIndex)
    val baseIndex = layers[target].elements.size
    layers[target] = Layer(layers[target].elements + allNewElements, layers[target].name)
    val pages = doc.pages.toMutableList()
    pages[index] = page.copy(layers = layers)
    doc = doc.copy(pages = pages)
    val refs = allNewElements.indices.map { ElementRef(target, baseIndex + it) }.toSet()
    selection = ActiveSelection(index, refs)
    onSelectionChanged?.invoke(true)
    history.record(before)
    notifyHistory()
    relayout()
    render()
}

/**
 * Insert a **living plot** on the visible page as one undoable edit: the element that keeps the
 * formula, its domain and its display flags, instead of the ink they compile to.
 *
 * The screen does not change — the new element draws through [LivingInk], the same compiler the ink
 * insertion runs directly — but the document now holds a graph that can be reopened and corrected
 * rather than a pile of anonymous strokes. Saving the document as a `.xopp` still flattens it
 * ([XoppSynthesis], because that format has nowhere to keep a recipe); a `.mobix` keeps it.
 *
 * The new element is deliberately **left unselected**: a living entity has no box to drag until its
 * family's editor lands, and outlining something no op can move would be a lie (`ElementBounds`).
 *
 * @param curves The curves as the graph dialog states them (formula, colour, line style).
 * @param xMin Left end of the domain.
 * @param xMax Right end of the domain.
 * @param showCriticalPoints Whether roots, extrema and inflections are marked.
 * @param includeDerivative Whether the first derivative is drawn alongside.
 * @param includeSummary Whether the analysis summary is computed and kept with the element.
 */
fun DrawingSurfaceView.insertPlotElement(
    curves: List<PlotCurve>,
    xMin: Double,
    xMax: Double,
    showCriticalPoints: Boolean = false,
    includeDerivative: Boolean = false,
    includeSummary: Boolean = false,
) {
    if (curves.isEmpty()) return
    val index = visiblePageIndex()
    val page = pageAt(index) ?: return
    val (centreX, centreY) = viewportCentrePt(index)
    val element = PlotElementFactory.plotElementFor(
        curves = curves,
        xMin = xMin,
        xMax = xMax,
        centreX = centreX,
        centreY = centreY,
        page = page,
        showCriticalPoints = showCriticalPoints,
        includeDerivative = includeDerivative,
        // The pen's own width and colour: the ink this replaces was drawn with them.
        strokeWidthPt = baseWidthPt.toDouble().coerceAtLeast(0.5),
        axisColor = strokeColor(),
        includeSummary = includeSummary,
    )
    ElementEdits.addElement(doc, index, element) { resolvedActiveLayer(it) }
        ?.let { commitElementEdit(it) }
}

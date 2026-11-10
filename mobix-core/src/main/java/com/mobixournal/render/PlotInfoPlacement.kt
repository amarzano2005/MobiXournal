package com.mobixournal.render

import com.mobixournal.format.model.PlotInfoSide
import com.mobixournal.render.InkTokens.FIGURE_LABEL_LINE_H
import com.mobixournal.render.InkTokens.FIGURE_LABEL_SIZE_PT

/** The page point a plot's analysis report starts at, in points. */
data class PlotInfoOrigin(val x: Double, val y: Double)

/**
 * Where a plot's **analysis report** is written — one definition for the four sides
 * ([com.mobixournal.format.model.PlotInfoSide]).
 *
 * It is a pure calculation on purpose: the element compiler (`PlotInk`), the plain-ink insertion
 * (`DrawingSurfacePlots.insertMultiPlot`) and both previews ask this, so a report cannot sit on one side
 * when the user asked for another, and moving the choice never changes what the report says.
 *
 * The report is measured with the same metrics its labels are drawn at ([TextAdvance], the tick labels'
 * own size and line height), so a left-side report clears the frame by the gap rather than by a guess.
 */
object PlotInfoPlacement {

    /** Gap between the frame and the report, in points. */
    const val GAP_PT: Double = 14.0

    /** The size the report is written at — the tick labels' own. */
    const val SIZE_PT: Double = FIGURE_LABEL_SIZE_PT

    /**
     * The point the report's first line starts at, for [side] and the frame
     * [left],[top]..[right],[bottom].
     *
     * [legendHeightPt] is what the marker key reaches below the frame (`MultiPlotResult.legendHeightPt`),
     * so a report under the graph starts under the key instead of through it.
     */
    fun origin(
        side: PlotInfoSide,
        summaryText: String,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        legendHeightPt: Double = 0.0,
    ): PlotInfoOrigin = when (side) {
        // Under the frame, after the x tick numbers and the marker key: the report has always lived here,
        // which is why it is the default and the one that reads as "nothing changed".
        PlotInfoSide.BELOW -> PlotInfoOrigin(left, bottom + GAP_PT + legendHeightPt)
        // Beside the frame, level with its top edge — the block reads from the graph's top line.
        PlotInfoSide.RIGHT -> PlotInfoOrigin(right + GAP_PT, top)
        PlotInfoSide.LEFT -> PlotInfoOrigin(left - GAP_PT - width(summaryText), top)
        PlotInfoSide.ABOVE -> PlotInfoOrigin(left, top - GAP_PT - height(summaryText))
    }

    /** The report's width in points: its longest line at [SIZE_PT]. */
    fun width(summaryText: String): Double = lines(summaryText).maxOfOrNull { TextAdvance.pt(it, SIZE_PT) } ?: 0.0

    /** The report's height in points: one [FIGURE_LABEL_SIZE_PT] line per line of text. */
    fun height(summaryText: String): Double = lines(summaryText).size * SIZE_PT * FIGURE_LABEL_LINE_H

    /** The report's lines — an empty report has none, so it is measured rather than given a line. */
    private fun lines(summaryText: String): List<String> =
        if (summaryText.isEmpty()) emptyList() else summaryText.lines()
}

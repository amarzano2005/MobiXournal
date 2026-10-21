package com.mobixournal.render

/**
 * The mapping a plot was actually drawn in: the data window it fitted, and the page box it was placed
 * in.
 *
 * [MultiFunctionPlotter] fits the y-window to the curves rather than to the element's box, so a caller
 * that has to place something **in the plot's own coordinates** cannot recompute the frame from the
 * element — it is handed this back instead, and the two agree by construction. `PlotInk` is the caller:
 * the shaded integral area has to be drawn in exactly the frame the curves were drawn in, or the fill
 * would not meet the curve it belongs to.
 */
data class PlotFrame(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val leftPt: Double,
    val topPt: Double,
    val widthPt: Double,
    val heightPt: Double,
) {

    /** Whether the x-axis (y = 0) falls inside the fitted window — and so is drawn at all. */
    val containsXAxis: Boolean get() = yMin <= 0.0 && yMax >= 0.0

    /** Data x in page points. */
    fun toX(x: Double): Double = leftPt + (x - xMin) / (xMax - xMin) * widthPt

    /** Data y in page points, inverted: the page's y grows downward. */
    fun toY(y: Double): Double = topPt + (yMax - y) / (yMax - yMin) * heightPt
}

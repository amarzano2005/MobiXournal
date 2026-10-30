package com.mobixournal.render

/**
 * The **quick size step** the Pages menu adds to, or takes from, a sheet — and the arithmetic that
 * turns a step in pixels into a page size in points.
 *
 * The step is written in **pixels** because that is the unit a reader thinks in ("a hundred pixels
 * wider"), while a page is in points (1/72 in). One step is therefore [PT_PER_PX] × the setting, and the
 * ratio is the fixed 96-px to the inch of a CSS pixel rather than the device's density: a step has to
 * mean the same thing on a phone, a tablet and a 4K monitor, and a density-scaled one would grow with the
 * screen it is used on.
 *
 * [step] is pure so the menu's four buttons can be tested without a canvas: it clamps a sheet to
 * [MIN_PAGE_PT] on each axis, so tapping the minus button forever shaves the page down to a sliver instead
 * of turning it inside out.
 */
object PageSizeStep {

    /** The factory step, in pixels — the "100 px" the Pages menu starts at. */
    const val DEFAULT_PX = 100

    /** The smallest step the setting accepts (a pixel or two would be a no-op on a finger). */
    const val MIN_PX = 5

    /** The largest step the setting accepts — half a screen's worth at a time. */
    const val MAX_PX = 1000

    /** Points in one CSS pixel (96 px to the inch, 72 pt to the inch). */
    const val PT_PER_PX = 72.0 / 96.0

    /** The smallest sheet the quick step will leave, in points. */
    const val MIN_PAGE_PT = 10.0

    /** [px] steps as points, the unit a page size is kept in. */
    fun pt(px: Int): Double = px * PT_PER_PX

    /** [px] held inside what the setting may write. */
    fun clampPx(px: Int): Int = px.coerceIn(MIN_PX, MAX_PX)

    /**
     * The sheet [widthPt] × [heightPt] widened by [widthSteps] and heightened by [heightSteps] steps of
     * [stepPt], each axis clamped to [MIN_PAGE_PT]. A zero step on an axis leaves it exactly as it was.
     */
    fun step(
        widthPt: Double,
        heightPt: Double,
        widthSteps: Int,
        heightSteps: Int,
        stepPt: Double,
    ): Pair<Double, Double> = (
        (widthPt + widthSteps * stepPt).coerceAtLeast(MIN_PAGE_PT) to
            (heightPt + heightSteps * stepPt).coerceAtLeast(MIN_PAGE_PT)
        )
}

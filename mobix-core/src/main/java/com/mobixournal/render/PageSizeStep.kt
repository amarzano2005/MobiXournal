package com.mobixournal.render

import kotlin.math.roundToInt

/**
 * The **quick size step** the Pages menu adds to, or takes from, a sheet — and the arithmetic that
 * turns a step in millimetres into a page size in points.
 *
 * The step is written in **millimetres** because that is the unit a sheet is judged in: 100 mm is the
 * factory step on both shells, a measure of paper rather than of screen, so the same tap widens an A4
 * by the same amount on a phone, a tablet and a 4K monitor. A page is kept in points (1/72 in), and
 * [pt] is the one conversion — 25.4 mm to the inch, exactly, not the display's density.
 *
 * The step is also **readable in any of the page format's three units**: [PageSizeUnit.defaultStep] is
 * the same 100 mm written in mm, inches or points (3.9 in, 283 pt), so a format read in inches steps the
 * sheet by the same length of paper rather than by a second, unrelated number.
 *
 * [step] is pure so the menu's four buttons can be tested without a canvas: it clamps a sheet to
 * [MIN_PAGE_PT] on each axis, so tapping the minus button forever shaves the page down to a sliver instead
 * of turning it inside out.
 */
object PageSizeStep {

    /** The factory step, in millimetres — the "100 mm" the Pages menu starts at. */
    const val DEFAULT_MM = 100.0

    /** The smallest step the setting accepts (a fraction of a millimetre would be a no-op on a finger). */
    const val MIN_MM = 1.0

    /** The largest step the setting accepts — a sheet's worth of paper at a time. */
    const val MAX_MM = 300.0

    /** Points in one millimetre (25.4 mm to the inch, 72 pt to the inch). */
    const val PT_PER_MM = 72.0 / 25.4

    /** The smallest sheet the quick step will leave, in points. */
    const val MIN_PAGE_PT = 10.0

    /** [mm] of paper as points, the unit a page size is kept in. */
    fun pt(mm: Double): Double = mm * PT_PER_MM

    /** A step already in points as the millimetres it was written in — the inverse of [pt]. */
    fun mmOf(stepPt: Double): Double = stepPt / PT_PER_MM

    /** [mm] held inside what the setting may write. */
    fun clampMm(mm: Double): Double = mm.coerceIn(MIN_MM, MAX_MM)

    /** [mm] of paper printed in [unit], to one decimal, without a trailing `.0` — "100 mm", "3.9 in". */
    fun label(mm: Double, unit: PageSizeUnit = PageSizeUnit.MM): String {
        val value = unit.fromPt(pt(mm))
        val rounded = (value * 10).roundToInt() / 10.0
        val text = if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
        return "$text ${unit.label}"
    }

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

/**
 * The page format's three units of measure, shared by both shells and by the quick size step.
 *
 * The app's page-size dialog and the window's setup section each keep their own picker; this is the
 * conversion behind them, so a step and a paper size can never disagree about what an inch is.
 */
enum class PageSizeUnit(val label: String, val perPt: Double) {
    /** Millimetres: 1 pt = 25.4/72 mm — the unit the format opens on, and the step's own. */
    MM("mm", 25.4 / 72.0),

    /** Inches: 1 pt = 1/72 in. */
    IN("in", 1.0 / 72.0),

    /** Points, the document's own unit. */
    PT("pt", 1.0);

    /** A page's points as this unit prints them. */
    fun fromPt(pt: Double): Double = pt * perPt

    /** A value in this unit as the page stores it, in points. */
    fun toPt(value: Double): Double = value / perPt

    /** The factory quick-size step in this unit — the same 100 mm of paper, re-read. */
    val defaultStep: Double get() = fromPt(PageSizeStep.pt(PageSizeStep.DEFAULT_MM))
}

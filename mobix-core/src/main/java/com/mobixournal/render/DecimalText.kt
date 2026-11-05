package com.mobixournal.render

import kotlin.math.roundToInt

/**
 * A number as the app prints it: at most one decimal, a whole number without a pointless `.0`.
 *
 * **The one home of the notation.** A page size, a page's step, a pen width and a figure's angle are all
 * measurements a reader is offered to one tenth, and two of them rounded differently would read as two
 * different numbers — `PageSizeStep.print` (a size *in a unit*) and each shell's field formatter are
 * thin names over this function rather than copies of its arithmetic.
 */
fun decimalText(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

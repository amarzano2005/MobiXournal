package com.mobixournal.ui

import kotlin.math.roundToInt

/**
 * The pen palette's **drag-to-reorder arithmetic**, kept apart from the gesture that feeds it.
 *
 * It follows the page list's own drag ([PageListDrag]): one row's measured height is the pitch, the
 * travel is rounded to a whole row so a swatch commits once it is more than half way into its
 * neighbour, and the ends stop rather than wrap — a colour that silently teleported would read as a
 * bug. Out-of-range indices and an unmeasured row answer the row that was picked up.
 */
internal fun paletteDropTarget(from: Int, offsetPx: Float, rowHeightPx: Int, count: Int): Int {
    if (from !in 0 until count || rowHeightPx <= 0) return from
    return (from + (offsetPx / rowHeightPx).roundToInt()).coerceIn(0, count - 1)
}

/**
 * [colors] with the entry at [from] moved to [to], keeping every other swatch in its relative order.
 *
 * A no-op — the same index, or an index past the end — answers the list unchanged, so a drag that
 * lands where it started writes nothing to the settings.
 */
internal fun movePaletteColor(colors: List<Int>, from: Int, to: Int): List<Int> {
    if (from !in colors.indices || to !in colors.indices || from == to) return colors
    return colors.toMutableList().also { it.add(to, it.removeAt(from)) }
}

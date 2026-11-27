package com.mobixournal.ui

import androidx.compose.ui.unit.dp

/**
 * How the rail sizes its slots to fit the available height cleanly:
 * slots are **never enlarged** past their design size (`1.0f`), and when the available height
 * tightens (for example when the split-view tab row opens), they shrink gently down to
 * [RAIL_MIN_SCALE] so every visible slot stays on screen at first glance instead of growing or
 * pushing the bottom navigation slots behind a scroll.
 */

internal val RailSpacing = 4.dp

/** The rail column's vertical padding (top and bottom). */
internal val RailColumnPadding = 6.dp

/** Never enlarge rail slots past their design size (1.0×). */
internal const val RAIL_MAX_SCALE = 1.0f

/** Floor on shrinking rail slots so touch targets remain comfortable (80% of base slot). */
internal const val RAIL_MIN_SCALE = 0.80f

/**
 * Scale factor for the rail's slots (`[minScale]..1.0f`):
 * returns `1f` whenever all [slotCount] slots fit at full size, otherwise shrinks proportionally
 * down to [minScale] so the full rail fits on screen without scrolling or enlarging.
 */
internal fun railContentScale(
    availableHeightDp: Float,
    slotCount: Int,
    slotDp: Float,
    spacingDp: Float,
    paddingDp: Float,
    extraDp: Float = 0f,
    minScale: Float = RAIL_MIN_SCALE,
): Float {
    if (slotCount <= 0 || availableHeightDp <= 0f || slotDp <= 0f) return 1f
    val usable = availableHeightDp - paddingDp * 2f - extraDp
    if (usable <= 0f) return minScale
    val needed = slotCount * slotDp + (slotCount - 1) * spacingDp
    if (needed <= 0f || usable >= needed) return 1f
    return (usable / needed).coerceIn(minScale, 1f)
}

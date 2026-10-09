package com.mobixournal.ui

import androidx.compose.ui.unit.dp

/**
 * How the rail decides how many of its slots to show, and how big.
 *
 * The rail is a **fixed column of slots**, and the complaint that started this was a *half* button at
 * its bottom edge: a scrolling viewport of arbitrary height always ends somewhere inside a button.
 * Shrinking every slot until they all fitted fixed the half button but cost what the rail is for —
 * buttons you hit without looking — so the rule is the other way round:
 *
 * **The rail shows a whole number of slots, at their full size.** [railContentScale] counts how many
 * slots fit at the size they are meant to be, then spreads *that many* over the height it has, so the
 * next slot begins exactly at the bottom edge instead of being cut in half by it. The scale it returns
 * is therefore never below 1 (nothing is ever drawn smaller than its own size) and only ever a few
 * percent above it, which spends the leftover strip of space on slightly larger buttons rather than on
 * a sliver of the next one.
 *
 * Slots the height cannot hold are a **scroll away** — the rail always shows the first of them whole.
 * Making every slot visible is not the goal; keeping the buttons the size they were is.
 */

/** Gap between two rail slots. */
internal val RailSpacing = 6.dp

/** The rail column's vertical padding (top and bottom). */
internal val RailColumnPadding = 8.dp

/**
 * The most [railContentScale] will grow the slots. Growth only happens when the leftover space below
 * the last whole slot is worth spending — a couple of dp on a tall rail — so 8% is a generous ceiling
 * and, importantly, keeps the slot inside the rail's inner width: 44dp of slots become at most 47.5dp
 * inside a 48dp rail (56dp dock minus its 2×4dp padding), and 48dp become at most 51.8dp inside 56dp.
 */
internal const val RAIL_MAX_SCALE = 1.08f

/**
 * The scale to draw the rail's slots at, so that a **whole number** of them fills the height.
 *
 * @param availableHeightDp The height the rail column may occupy, **including** its own vertical
 *   padding — the rail's own chrome (a floating dock's outer margin, the top bar) must already be
 *   subtracted, since that chrome does not scale.
 * @param slotCount How many slots the rail holds (visible ones).
 * @param slotDp One slot's height at full size.
 * @param spacingDp The gap between two slots.
 * @param paddingDp The column's vertical padding, counted at both ends.
 * @param maxScale The ceiling on the growth; see [RAIL_MAX_SCALE].
 *
 * Returns 1 when every slot fits — the rail is left exactly as it was designed. Otherwise it returns
 * the factor that makes the largest whole number of slots fill the space, which is ≥ 1 by
 * construction: the slots stay their own size or a shade larger, and the slot after them starts at the
 * bottom edge rather than being cut by it.
 */
internal fun railContentScale(
    availableHeightDp: Float,
    slotCount: Int,
    slotDp: Float,
    spacingDp: Float,
    paddingDp: Float,
    maxScale: Float = RAIL_MAX_SCALE,
): Float {
    if (slotCount <= 0 || availableHeightDp <= 0f || slotDp <= 0f) return 1f
    val usable = availableHeightDp - paddingDp * 2f
    if (usable <= 0f) return 1f
    val pitch = slotDp + spacingDp
    // The most slots that fit whole at full size: m slots take m·slot + (m−1)·spacing.
    val whole = ((usable + spacingDp) / pitch).toInt()
    if (whole <= 0 || whole >= slotCount) return 1f
    // Spread exactly `whole` slots over the space so the next one starts at the bottom edge. The
    // denominator is what `whole` slots occupy at scale 1, gaps included.
    return (usable / (whole * pitch - spacingDp)).coerceAtMost(maxScale)
}

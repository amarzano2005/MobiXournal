package com.mobixournal.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/** The air above a settings list: between it and the row that opened it. */
internal val SettingsSelectGap: Dp = 4.dp

/** The least air a settings list keeps from the window's own edge. */
internal val SettingsSelectMargin: Dp = 8.dp

/**
 * Where a settings **select**'s list opens: **under the row**, its start edge on the row's start edge, and
 * kept inside the window.
 *
 * A list that opens somewhere else is a list the reader has to find. `ToolbarMenu`'s own placement picks
 * whichever side of a toolbar slot has room and otherwise parks the panel against the window — right for a
 * pop-up hanging off a strip at the screen's edge, wrong for a row in a column of rows: the reader pressed
 * *this* row, and the answers belong against it.
 *
 * Two moves are allowed, and only these: a row too near the bottom for the list to fit under it gets the
 * list **above** instead (still against the row, never floating elsewhere), and a row near the right edge
 * pulls the list leftwards so its last letter stays on screen. [settingsSelectOffset] is that arithmetic as
 * plain integers, so `SettingsSelectPositionTest` can pin it without a device.
 */
internal fun settingsSelectOffset(
    anchor: IntRect,
    menu: IntSize,
    window: IntSize,
    gap: Int,
    margin: Int,
    leftToRight: Boolean,
): IntOffset {
    val start = if (leftToRight) anchor.left else anchor.right - menu.width
    val x = clamp(start, margin, window.width - menu.width - margin)
    val below = anchor.bottom + gap
    val above = anchor.top - gap - menu.height
    val fitsBelow = below + menu.height + margin <= window.height
    val roomAbove = above >= margin
    val y = if (fitsBelow || !roomAbove) {
        clamp(below, margin, window.height - menu.height - margin)
    } else {
        above
    }
    return IntOffset(x, y)
}

/** [value] brought into `lo..hi`, or to `lo` when the range is empty (a list wider than the window). */
private fun clamp(value: Int, lo: Int, hi: Int): Int = if (hi <= lo) lo else value.coerceIn(lo, hi)

/** One settings select's list, placed by [settingsSelectOffset] instead of the toolbar's own arithmetic. */
internal class SettingsSelectPositionProvider(private val density: Density) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = settingsSelectOffset(
        anchor = anchorBounds,
        menu = popupContentSize,
        window = windowSize,
        gap = with(density) { SettingsSelectGap.roundToPx() },
        margin = with(density) { SettingsSelectMargin.roundToPx() },
        leftToRight = layoutDirection == LayoutDirection.Ltr,
    )
}

/**
 * The tallest a settings list may be in a window [windowHeightPx] tall, so a long one scrolls inside the
 * screen instead of running past its edge — the cap is what makes [settingsSelectOffset]'s two margins
 * always enough room for the panel.
 */
internal fun settingsSelectMaxHeight(windowHeightPx: Int, density: Density): Dp = with(density) {
    (windowHeightPx - SettingsSelectMargin.roundToPx() * 2).coerceAtLeast(0).toDp()
}

package com.mobixournal.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The header's row of document tabs in split view: **one strip per pane**, each lying over its own
 * half of the canvas.
 *
 * The header still has one top bar, one rail and one tab row — but the row is now cut the way the
 * canvas is, so the documents a half holds are drawn on that half instead of swapping into a single
 * shared strip. That swap was the confusion: touching the right canvas replaced the only visible
 * strip with the right pane's tabs and left no trace of the left ones, and the trailing "+" then
 * added a document to whichever half happened to have focus last.
 *
 * The geometry is the canvas's own: a weighted first lane, the same [SPLIT_HANDLE_WIDTH] gap the
 * divider occupies, and a weighted second lane. Both rows measure the same width (the header pads
 * out the rail's lane, exactly as the canvas row does), so lane `N` spans the pixels of pane `N`
 * for every divider position — not only at 50/50.
 *
 * Each strip owns its "add" button and, in split view, a numbered badge (see `TabStrip`): the pane
 * whose badge is filled with the accent is the one the top bar and the rail drive.
 */
@Composable
internal fun SplitTabRow(
    tabs: List<TabsUiState>,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    if (tabs.size < 2) return
    val safeFraction = clampSplitFraction(fraction)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TabStrip(
            state = tabs[0],
            modifier = Modifier
                .weight(safeFraction)
                .padding(start = TAB_STRIP_LEAD_IN),
        )
        Spacer(Modifier.width(SPLIT_HANDLE_WIDTH))
        TabStrip(
            state = tabs[1],
            modifier = Modifier
                .weight(1f - safeFraction)
                .padding(start = TAB_STRIP_LEAD_IN),
        )
    }
}

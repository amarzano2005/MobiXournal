package com.mobixournal.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The geometry is the canvas's own, from the same [splitPaneSpans] the divider lays itself out with:
 * the divider is centred at [fraction] of the row, its 22dp lane ([SPLIT_HANDLE_WIDTH]) is the gap
 * between the lanes, and both rows measure the same width (the header pads out the rail's lane,
 * exactly as the canvas row does). Lane `N` therefore spans the pixels of pane `N` for every divider
 * position — not only at 50/50 — and keeps doing so while the divider is being dragged, because both
 * sides are derived from the same fraction rather than from their own weight.
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
    BoxWithConstraints(modifier = modifier) {
        val (firstSpan, secondSpan) = splitPaneSpans(
            totalSpan = maxWidth.value,
            fraction = fraction,
            handleSpan = SPLIT_HANDLE_WIDTH.value,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TabStrip(
                state = tabs[0],
                modifier = Modifier
                    .weight(firstSpan)
                    .padding(start = TAB_STRIP_LEAD_IN),
            )
            Spacer(Modifier.width(SPLIT_HANDLE_WIDTH))
            TabStrip(
                state = tabs[1],
                modifier = Modifier
                    .weight(secondSpan)
                    .padding(start = TAB_STRIP_LEAD_IN),
            )
        }
    }
}

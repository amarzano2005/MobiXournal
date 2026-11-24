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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The header's row of document tabs in split view: **one strip per pane**, each lying over its own
 * half of the canvas.
 *
 * Spans the full viewport width so [splitPaneSpans] places the [SPLIT_HANDLE_WIDTH] gap directly
 * above [SplitLayout]'s divider; [startInset] / [endInset] clear the floating rail on the outer edge
 * without shifting the center divider or Pane 1's leading tab.
 */
@Composable
internal fun SplitTabRow(
    tabs: List<TabsUiState>,
    fraction: Float,
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
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
                    .padding(start = startInset + TAB_STRIP_LEAD_IN),
            )
            Spacer(Modifier.width(SPLIT_HANDLE_WIDTH))
            TabStrip(
                state = tabs[1],
                modifier = Modifier
                    .weight(secondSpan)
                    .padding(start = TAB_STRIP_LEAD_IN, end = endInset),
            )
        }
    }
}

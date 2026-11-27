package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark

/**
 * The header's dedicated row of document tabs in split view: **one strip per pane**, each lying
 * directly above its own half of the canvas.
 *
 * Spans the full viewport width so [splitPaneSpans] places the [SPLIT_HANDLE_WIDTH] gap directly
 * above [SplitLayout]'s divider; [startInset] / [endInset] clear the floating rail on the outer edge
 * without shifting the center divider or Pane 1's leading tab. Uses [onSizeChanged] instead of
 * `BoxWithConstraints` so toggling split view never incurs a frame-0 subcomposition stall.
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
    val isDark = chromeIsDark()
    val hairline = ModernTheme.hairlineBorder(isDark)
    val density = LocalDensity.current
    var rowWidthPx by remember { mutableFloatStateOf(1f) }
    val handlePx = with(density) { SPLIT_HANDLE_WIDTH.toPx() }
    val (firstSpan, secondSpan) = splitPaneSpans(
        totalSpan = rowWidthPx.coerceAtLeast(handlePx + 2f),
        fraction = fraction,
        handleSpan = handlePx,
    )
    Column(
        modifier = modifier.onSizeChanged { if (it.width > 0) rowWidthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairline),
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TabStrip(
                state = tabs[0],
                modifier = Modifier
                    .weight(firstSpan.coerceAtLeast(1f))
                    .padding(start = startInset + TAB_STRIP_LEAD_IN),
            )
            Box(
                modifier = Modifier
                    .width(SPLIT_HANDLE_WIDTH)
                    .height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(hairline),
                )
            }
            TabStrip(
                state = tabs[1],
                modifier = Modifier
                    .weight(secondSpan.coerceAtLeast(1f))
                    .padding(start = TAB_STRIP_LEAD_IN, end = endInset),
            )
        }
    }
}

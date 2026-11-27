package com.mobixournal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.LineStyle

/** Line-style labels, paired with their [LineStyle] — the set the pop-ups offer. */
internal val LINE_STYLE_LABELS: List<Pair<LineStyle, String>> = listOf(
    LineStyle.PLAIN to "Solid",
    LineStyle.DASHED to "Dashed",
    LineStyle.DASH_DOT to "Dash-dot",
    LineStyle.DOTTED to "Dotted",
)

/**
 * The four line styles as one compact row of chips — the **Line style** section of the Colour & size
 * pop-up. There is no rail slot for style any more: line style is set where the colour and the width
 * are, so the choice is one tap away instead of behind its own button.
 *
 * The row wraps onto a second line rather than scrolling, so every style stays reachable in a menu
 * that is deliberately short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LineStyleChips(lineStyle: LineStyle, onLineStyle: (LineStyle) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((style, label) in LINE_STYLE_LABELS) {
            val selected = style == lineStyle
            val ink = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onLineStyle(style) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                LineStyleGlyph(style = style, tint = ink)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun LineStyleGlyph(style: LineStyle, tint: Color) {
    Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
        val y = size.height / 2f
        val stroke = 2.dp.toPx()
        val effect = when (style) {
            LineStyle.PLAIN -> null
            LineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()), 0f)
            LineStyle.DASH_DOT -> PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 2.5.dp.toPx(), 1.5.dp.toPx(), 2.5.dp.toPx()),
                0f,
            )
            LineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx()), 0f)
        }
        drawLine(
            color = tint,
            start = Offset(stroke / 2f, y),
            end = Offset(size.width - stroke / 2f, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = effect,
        )
    }
}


package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Shape recognition as a one-tap rail slot: no pop-up, the tap flips it and the slot tints like a
 * tool button while it's on, so a freehand circle can be snapped to a real one (or not) without
 * leaving the page for Settings. Backed by the same persisted `recognizeShapes` setting.
 *
 * The face is a **magic wand** (`AutoFixHigh`), not the triangle the button used to share with the
 * Triangle *tool*: a wand is what "the app tidies up what I drew" looks like, and it can no longer
 * be mistaken for a tool that draws triangles. The Guides slot beside it carries a ruler, so the
 * two adjacent slots read as "tidy the drawing up" vs. "rule it out" instead of as two identical
 * triangles.
 */
@Composable
internal fun ShapeRecognitionButton(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val tint =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val slot = LocalRailSlotSize.current
    Box(
        modifier = Modifier
            .size(slot)
            .clip(CircleShape)
            .then(
                if (enabled) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier,
            )
            .clickable { onEnabled(!enabled) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.AutoFixHigh,
            contentDescription = if (enabled) "Shape recognition on" else "Shape recognition off",
            tint = tint,
            modifier = Modifier.size(slot / 2),
        )
    }
}

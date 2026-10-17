package com.mobixournal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** How narrow either half may be dragged, as a fraction of the whole width. */
internal const val SPLIT_MIN_FRACTION = 0.15f

/** The balanced split: what a double-tap on the grip restores. */
internal const val SPLIT_EVEN_FRACTION = 0.5f

/** The grab area down the middle — wide enough for a finger, per the tap-target sizing in [TabStrip]. */
private val HANDLE_WIDTH = 22.dp

/** The painted rule: one hairline, so the two sheets read as two without a band between them. */
private val RULE_WIDTH = 1.dp

/** The rounded grip centred on the rule — the only thing that looks grabbable. */
private val GRIP_WIDTH = 4.dp
private val GRIP_HEIGHT = 44.dp

/** Clamp a split fraction so neither pane can be dragged away entirely. */
internal fun clampSplitFraction(fraction: Float): Float =
    if (fraction.isNaN()) SPLIT_EVEN_FRACTION
    else fraction.coerceIn(SPLIT_MIN_FRACTION, 1f - SPLIT_MIN_FRACTION)

/**
 * The fraction after dragging [dragPx] from [startFraction] across a [totalPx]-wide divider.
 *
 * The drag is measured from where the gesture *started*, not applied to the fraction of the last
 * recomposition: with incremental updates a burst of pointer events inside one frame is lost, which
 * is what made the divider lag the finger. A non-positive width is treated as one pixel so the
 * arithmetic can never divide by zero.
 */
internal fun splitFractionAfterDrag(startFraction: Float, dragPx: Float, totalPx: Float): Float =
    clampSplitFraction(startFraction + dragPx / totalPx.coerceAtLeast(1f))

/**
 * Two panes side by side with a draggable divider down the middle.
 *
 * [fraction] is the width given to [first] (0..1 of the whole, minus the handle); dragging the
 * divider reports a new value through [onFraction] so the caller owns the split position. The
 * handle is a slim glass grip on a hairline rule: it reads as a modern divider rather than a filled
 * band, fades up while dragged, and a double-tap puts it back at [SPLIT_EVEN_FRACTION].
 */
@Composable
fun SplitLayout(
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    val safeFraction = clampSplitFraction(fraction)
    val haptics = LocalHapticFeedback.current
    val latestOnFraction = rememberUpdatedState(onFraction)
    val latestFraction = rememberUpdatedState(fraction)
    var dragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf(safeFraction) }
    var dragPx by remember { mutableStateOf(0f) }

    BoxWithConstraints(modifier = modifier) {
        val totalPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        val dividerColor = MaterialTheme.colorScheme.onSurfaceVariant
        val gripAlpha by animateFloatAsState(
            targetValue = if (dragging) 0.90f else 0.42f,
            animationSpec = tween(durationMillis = 140),
            label = "split grip",
        )
        val ruleAlpha by animateFloatAsState(
            targetValue = if (dragging) 0.24f else 0.10f,
            animationSpec = tween(durationMillis = 140),
            label = "split rule",
        )
        val dragState = rememberDraggableState { delta ->
            dragPx += delta
            latestOnFraction.value(splitFractionAfterDrag(dragStart, dragPx, totalPx))
        }
        Row(modifier = Modifier.fillMaxSize()) {
            first(Modifier.fillMaxHeight().weight(safeFraction))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(HANDLE_WIDTH)
                    .semantics {
                        contentDescription = "Resize split"
                        customActions = listOf(
                            CustomAccessibilityAction("Reset split") {
                                latestOnFraction.value(SPLIT_EVEN_FRACTION)
                                true
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                latestOnFraction.value(SPLIT_EVEN_FRACTION)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = dragState,
                        onDragStarted = {
                            dragStart = clampSplitFraction(latestFraction.value)
                            dragPx = 0f
                            dragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragStopped = { dragging = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(RULE_WIDTH)
                        .background(dividerColor.copy(alpha = ruleAlpha)),
                )
                Box(
                    modifier = Modifier
                        .width(GRIP_WIDTH)
                        .height(GRIP_HEIGHT)
                        .clip(RoundedCornerShape(GRIP_WIDTH / 2))
                        .background(dividerColor.copy(alpha = gripAlpha)),
                )
            }
            second(Modifier.fillMaxHeight().weight(1f - safeFraction))
        }
    }
}

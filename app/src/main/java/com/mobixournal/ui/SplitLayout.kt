package com.mobixournal.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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

/**
 * The grab area down the middle — wide enough for a finger, per the tap-target sizing in [TabStrip].
 *
 * Shared with [SplitTabRow], which leaves the same gap between the two panes' tab lanes so a lane
 * starts exactly where its canvas does.
 */
internal val SPLIT_HANDLE_WIDTH = 22.dp

/** The painted rule: one hairline, so the two sheets read as two without a band between them. */
private val RULE_WIDTH = 1.dp

/** The floating grip: a frosted capsule around an accent core. */
private val GRIP_WIDTH = 10.dp
private val GRIP_HEIGHT = 52.dp
private val GRIP_ACTIVE_WIDTH = 12.dp
private val GRIP_ACTIVE_HEIGHT = 64.dp
private val GRIP_CORE_WIDTH = 3.dp
private val GRIP_CORE_HEIGHT = 18.dp

/** Clamp a split fraction so neither pane can be dragged away entirely. */
internal fun clampSplitFraction(fraction: Float): Float =
    if (fraction.isNaN()) SPLIT_EVEN_FRACTION
    else fraction.coerceIn(SPLIT_MIN_FRACTION, 1f - SPLIT_MIN_FRACTION)

/**
 * The two panes' widths for a divider **centred** at [fraction] of [totalSpan], in the unit of the
 * arguments (dp or px).
 *
 * The fraction is a position, not a weight: the divider's own [handleSpan] straddles it, so pane one
 * gets `fraction · total − handle/2`. That is what makes a split-drag move the divider exactly as far
 * as the finger does — a weighted split lays the panes out inside `total − handle` instead, so the
 * divider crawls behind the finger and the halves never match the width the gesture asked for.
 *
 * Returns pane one's span and pane two's; together with [handleSpan] they tile [totalSpan].
 */
internal fun splitPaneSpans(
    totalSpan: Float,
    fraction: Float,
    handleSpan: Float,
): Pair<Float, Float> {
    val total = totalSpan.coerceAtLeast(0f)
    val handle = handleSpan.coerceIn(0f, total)
    val first = (clampSplitFraction(fraction) * total - handle / 2f).coerceIn(0f, total - handle)
    return first to (total - handle - first)
}

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
 * Two panes side by side with a draggable divider down the middle (or single-pane when [splitView] is false).
 *
 * Uses a single [Row] with [onSizeChanged] rather than `BoxWithConstraints` (`SubcomposeLayout`) so that
 * toggling [splitView] off never moves the first pane's `AndroidView` (`SurfaceView`) across a
 * subcomposition boundary, ensuring it immediately expands back to the full width of the row.
 */
@Composable
fun SplitLayout(
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    splitView: Boolean = true,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    val safeFraction = clampSplitFraction(fraction)
    val haptics = LocalHapticFeedback.current
    val latestOnFraction = rememberUpdatedState(onFraction)
    val latestFraction = rememberUpdatedState(fraction)
    var dragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf(safeFraction) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(1f) }

    val density = LocalDensity.current
    val totalPx = totalWidthPx.coerceAtLeast(1f)
    val handlePx = with(density) { SPLIT_HANDLE_WIDTH.toPx() }
    val (firstPx, secondPx) = splitPaneSpans(totalPx, safeFraction, handlePx)
    val firstWeight = firstPx.coerceAtLeast(1f)
    val secondWeight = secondPx.coerceAtLeast(1f)

    val dragState = rememberDraggableState { delta ->
        dragPx += delta
        latestOnFraction.value(splitFractionAfterDrag(dragStart, dragPx, totalPx))
    }
    Row(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0) totalWidthPx = size.width.toFloat()
            },
    ) {
        first(Modifier.fillMaxHeight().weight(if (splitView) firstWeight else 1f))
        if (splitView) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(SPLIT_HANDLE_WIDTH)
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
                SplitHandleLane(dragging = dragging)
            }
            second(Modifier.fillMaxHeight().weight(secondWeight))
        }
    }
}

/**
 * What the divider looks like: a live-region wash, the rule, and the floating grip.
 *
 * It is drawn from [dragging] alone — every value it animates between is an idle/dragged pair — so the
 * composable that owns the gesture stays a gesture, not a paint pass ([SourceStructureTest]'s split
 * is the same one this file's own geometry helpers follow).
 */
@Composable
private fun SplitHandleLane(dragging: Boolean) {
    val colors = MaterialTheme.colorScheme
    val gripAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.95f else 0.55f,
        animationSpec = tween(durationMillis = 140),
        label = "split grip",
    )
    val coreAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0.42f,
        animationSpec = tween(durationMillis = 140),
        label = "split core",
    )
    val ruleAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.28f else 0.12f,
        animationSpec = tween(durationMillis = 160),
        label = "split rule",
    )
    val laneAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.06f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "split lane glow",
    )
    val gripWidth by animateDpAsState(
        targetValue = if (dragging) GRIP_ACTIVE_WIDTH else GRIP_WIDTH,
        animationSpec = tween(durationMillis = 160),
        label = "split grip width",
    )
    val gripHeight by animateDpAsState(
        targetValue = if (dragging) GRIP_ACTIVE_HEIGHT else GRIP_HEIGHT,
        animationSpec = tween(durationMillis = 160),
        label = "split grip height",
    )
    // The live region: a barely-there accent wash down the lane while the split moves, so the handle
    // reads as engaged without turning into a bar between the sheets.
    if (laneAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.primary.copy(alpha = laneAlpha)),
        )
    }
    // The rule itself, fading out at both ends instead of touching the chrome above and below — the
    // modern read is "the sheets meet here", not "a divider was drawn".
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(RULE_WIDTH)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.08f to colors.onSurfaceVariant.copy(alpha = ruleAlpha),
                    0.92f to colors.onSurfaceVariant.copy(alpha = ruleAlpha),
                    1f to Color.Transparent,
                ),
            ),
    )
    // The grip: a frosted capsule with an accent core, lifted off the page by a soft shadow only
    // while it is in hand — idle it is a quiet bead on the rule.
    Box(
        modifier = Modifier
            .width(gripWidth)
            .height(gripHeight)
            .shadow(
                elevation = if (dragging) 8.dp else 2.dp,
                shape = Capsule,
                clip = false,
            )
            .clip(Capsule)
            .background(colors.surfaceContainerHighest.copy(alpha = gripAlpha))
            .border(
                width = 1.dp,
                color = if (dragging) colors.primary.copy(alpha = 0.55f)
                else colors.outlineVariant.copy(alpha = 0.7f),
                shape = Capsule,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(GRIP_CORE_WIDTH)
                .height(GRIP_CORE_HEIGHT)
                .clip(Capsule)
                .background(colors.primary.copy(alpha = coreAlpha)),
        )
    }
}

/** A fully rounded shape, named once so the grip's three layers cannot disagree. */
private val Capsule = RoundedCornerShape(percent = 50)

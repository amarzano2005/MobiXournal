package com.mobixournal.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mobixournal.render.setChromeInsets

/** Shared duration (ms) for sliding chrome bands (top bar, side rail, multi-page sidebar). */
internal const val CHROME_TRANSITION_MS = 240

/**
 * Live pixel insets occupied by overlaid editor chrome (top bar, side rail, multi-page sidebar)
 * so [com.mobixournal.render.DrawingSurfaceView] can re-centre and re-fit pages smoothly at
 * 60–120 fps without resizing its native `Surface` on every animation frame.
 */
internal data class CanvasChromeInsets(
    val leftPx: Float = 0f,
    val topPx: Float = 0f,
    val rightPx: Float = 0f,
    val bottomPx: Float = 0f,
    val animating: Boolean = false,
)

internal val LocalCanvasChromeInsets = compositionLocalOf { CanvasChromeInsets() }

internal val LocalSplitView = compositionLocalOf { false }

/**
 * Resolve the chrome insets belonging to [paneIndex]: in split view, the left pane honours the
 * left edge and top bar, while the right pane honours the right edge and top bar.
 */
internal fun CanvasChromeInsets.forPane(paneIndex: Int, splitView: Boolean): CanvasChromeInsets {
    if (!splitView) return this
    return if (paneIndex == 0) copy(rightPx = 0f) else copy(leftPx = 0f)
}

/**
 * Consume unconsumed pointer events on chrome surfaces so empty areas of an overlaid bar or rail
 * never pass taps or stylus strokes through to the [DrawingSurfaceView] underneath.
 */
internal fun Modifier.blockCanvasPointers(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}

/**
 * Push live chrome insets to [state]'s `DrawingSurfaceView` and return the matching Compose
 * [PaddingValues] so viewport badges, scroll thumb, and diagnostics stay clear of overlaid bars.
 */
@Composable
internal fun ApplyPaneChromeInsets(
    state: PaneState,
    paneIndex: Int,
): PaddingValues {
    val paneInsets = LocalCanvasChromeInsets.current.forPane(paneIndex, LocalSplitView.current)
    SideEffect {
        state.surface?.setChromeInsets(
            leftPx = paneInsets.leftPx,
            topPx = paneInsets.topPx,
            rightPx = paneInsets.rightPx,
            bottomPx = paneInsets.bottomPx,
            animating = paneInsets.animating,
        )
    }
    val density = LocalDensity.current
    return with(density) {
        PaddingValues(
            start = paneInsets.leftPx.toDp(),
            top = paneInsets.topPx.toDp(),
            end = paneInsets.rightPx.toDp(),
            bottom = paneInsets.bottomPx.toDp(),
        )
    }
}

/**
 * Animated overlay host for the editor top bar: slides cleanly up/down while reporting its live
 * unobstructed height inset to the workspace below so pages glide smoothly in sync.
 */
@Composable
internal fun AnimatedEditorTopBarHost(
    visible: Boolean,
    onTopInset: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var measuredHeightPx by remember { mutableStateOf(0) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(CHROME_TRANSITION_MS, easing = FastOutSlowInEasing),
        label = "TopBarChrome",
    )
    val animating = progress in 0.001f..0.999f
    val insetPx = if (measuredHeightPx > 0) measuredHeightPx * progress else 0f
    SideEffect {
        onTopInset(insetPx, animating)
    }
    if (progress > 0.001f || visible) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { if (it.height > 0) measuredHeightPx = it.height }
                .graphicsLayer {
                    translationY = -(1f - progress) * measuredHeightPx
                    alpha = progress.coerceIn(0f, 1f)
                }
                .then(if (visible) Modifier.blockCanvasPointers() else Modifier),
        ) {
            content()
        }
    }
}

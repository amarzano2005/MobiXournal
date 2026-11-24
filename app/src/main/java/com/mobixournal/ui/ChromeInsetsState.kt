package com.mobixournal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.mobixournal.render.setChromeInsets
import com.mobixournal.ui.theme.rememberChromeColor
import kotlinx.coroutines.flow.collectLatest

/** Physics-based spring tuned for 120–144 Hz sliding chrome bands and split transitions. */
internal fun chromeSlideSpring() = spring<Float>(
    dampingRatio = 0.92f,
    stiffness = 520f,
    visibilityThreshold = 0.001f,
)

/**
 * Live pixel insets occupied by overlaid editor chrome (top bar, side rail, multi-page sidebar)
 * so [com.mobixournal.render.DrawingSurfaceView] can re-centre and re-fit pages smoothly at
 * 120–144 Hz without resizing its native `Surface` on every animation frame.
 */
internal data class CanvasChromeInsets(
    val leftPx: Float = 0f,
    val topPx: Float = 0f,
    val rightPx: Float = 0f,
    val bottomPx: Float = 0f,
    val animating: Boolean = false,
)

/**
 * Stable observable holder for animated and settled chrome insets. Passing this single instance
 * via [LocalChromeInsetController] avoids recomposing `EditorScreen`, `EditorBody`, and
 * `EditorPaneView` on every animation frame.
 */
@Stable
internal class ChromeInsetController {
    var topInsetPx by mutableFloatStateOf(0f)
    var topAnimating by mutableStateOf(false)
    var settledTopPx by mutableFloatStateOf(0f)

    var leftInsetPx by mutableFloatStateOf(0f)
    var rightInsetPx by mutableFloatStateOf(0f)
    var sideAnimating by mutableStateOf(false)
    var settledLeftPx by mutableFloatStateOf(0f)
    var settledRightPx by mutableFloatStateOf(0f)

    var splitAnimating by mutableStateOf(false)

    fun snapshot(): CanvasChromeInsets = CanvasChromeInsets(
        leftPx = leftInsetPx,
        topPx = topInsetPx,
        rightPx = rightInsetPx,
        bottomPx = 0f,
        animating = topAnimating || sideAnimating || splitAnimating,
    )

    fun settledSnapshot(): CanvasChromeInsets = CanvasChromeInsets(
        leftPx = settledLeftPx,
        topPx = settledTopPx,
        rightPx = settledRightPx,
        bottomPx = 0f,
        animating = false,
    )
}

internal val LocalChromeInsetController = compositionLocalOf { ChromeInsetController() }

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
 * Latch the non-zero status bar top inset so `EditorTopBar` keeps a constant height while sliding
 * out or in as Android hides/shows system bars during Focus mode transitions.
 */
@Composable
internal fun Modifier.stableStatusBarsPadding(): Modifier {
    val density = LocalDensity.current
    val topPx = WindowInsets.statusBars.getTop(density)
    var latchedTopPx by remember { mutableIntStateOf(0) }
    if (topPx > latchedTopPx) latchedTopPx = topPx
    return padding(top = with(density) { latchedTopPx.toDp() })
}

/**
 * Measure and place the child inside the remaining vertical space below [topPxProvider] in the
 * layout phase only, avoiding Compose recompositions while the top bar animates.
 */
internal fun Modifier.topInsetLayout(topPxProvider: () -> Int): Modifier = layout { measurable, constraints ->
    val top = topPxProvider().coerceIn(0, constraints.maxHeight)
    val availH = (constraints.maxHeight - top).coerceAtLeast(0)
    val childConstraints = constraints.copy(
        minHeight = constraints.minHeight.coerceAtMost(availH),
        maxHeight = availH,
    )
    val placeable = measurable.measure(childConstraints)
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(0, top)
    }
}

/**
 * Push live chrome insets to [state]'s `DrawingSurfaceView` via [snapshotFlow] (zero per-frame
 * recomposition of `EditorPaneView`) and return settled [PaddingValues] for viewport badges,
 * scroll thumb, and diagnostics.
 */
@Composable
internal fun ApplyPaneChromeInsets(
    state: PaneState,
    paneIndex: Int,
): PaddingValues {
    val controller = LocalChromeInsetController.current
    val splitView = LocalSplitView.current
    val surface = state.surface
    LaunchedEffect(controller, surface, paneIndex, splitView) {
        snapshotFlow { controller.snapshot().forPane(paneIndex, splitView) }.collectLatest { paneInsets ->
            surface?.setChromeInsets(
                leftPx = paneInsets.leftPx,
                topPx = paneInsets.topPx,
                rightPx = paneInsets.rightPx,
                bottomPx = paneInsets.bottomPx,
                animating = paneInsets.animating,
            )
        }
    }
    val settled = controller.settledSnapshot().forPane(paneIndex, splitView)
    val density = LocalDensity.current
    return with(density) {
        PaddingValues(
            start = settled.leftPx.toDp(),
            top = settled.topPx.toDp(),
            end = settled.rightPx.toDp(),
            bottom = settled.bottomPx.toDp(),
        )
    }
}

/**
 * Animated opaque overlay host for the editor top bar: composites over [rememberChromeColor] so
 * the header stays opaque over inked pages (matching `RailLane`) and slides cleanly with a spring
 * while pushing live top insets into [controller] without recomposing sibling subtrees.
 */
@Composable
internal fun AnimatedEditorTopBarHost(
    visible: Boolean,
    controller: ChromeInsetController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    val latestVisible by rememberUpdatedState(visible)
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = chromeSlideSpring(),
        label = "TopBarChrome",
    )
    LaunchedEffect(controller, visible) {
        snapshotFlow {
            val p = progress.value
            val h = measuredHeightPx
            Triple(p, h, visible)
        }.collectLatest { (p, h, vis) ->
            controller.topInsetPx = if (h > 0) h * p else 0f
            controller.topAnimating = p in 0.001f..0.999f
            controller.settledTopPx = if (vis && h > 0) h.toFloat() else 0f
        }
    }
    val showBar by remember { derivedStateOf { progress.value > 0.001f || latestVisible } }
    if (showBar) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged {
                    if (it.height > 0 && (measuredHeightPx == 0 || (latestVisible && progress.value > 0.95f))) {
                        measuredHeightPx = it.height
                    }
                }
                .graphicsLayer {
                    translationY = -(1f - progress.value) * measuredHeightPx
                }
                .background(rememberChromeColor())
                .then(if (visible) Modifier.blockCanvasPointers() else Modifier),
        ) {
            content()
        }
    }
}

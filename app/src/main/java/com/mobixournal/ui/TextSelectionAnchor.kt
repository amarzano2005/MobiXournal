package com.mobixournal.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.liquidGlassSpringAnimation

/** Gap between the PDF text bar and the words it acts on. */
private val TEXT_BAR_GAP = 10.dp

/** Smallest distance the PDF text bar keeps from the canvas edges. */
private val TEXT_BAR_MARGIN = 8.dp

/**
 * The **PDF text selection's** bar, floating against the selected words instead of sitting on the
 * bottom edge of the pane.
 *
 * It used to be a fixed bar at the foot of the screen, which is where the one thing it is for stops
 * being true: a passage near the foot of a page — the last lines of a paragraph, a footnote, a caption —
 * sat *under* its own toolbar, its highlight hidden by the buttons that were supposed to act on it.
 * Anchored to the box the surface reports ([PaneState.textSelectionRect]), the bar takes the same place
 * the element selection's bar takes ([selectionBarOffset]): centred **below** the last selected line,
 * **above** the block when the pane runs out below, and always clamped inside the pane — so Copy stays
 * reachable with the zoom up, the text enlarged, and in a narrow split-screen pane.
 *
 * The bar is measured before it is placed ([IntSize] from `onSizeChanged`), so the clamp uses the real
 * capsule rather than a guess: a wider label or a larger text scale moves the bar, not its own edges.
 */
@Composable
internal fun TextSelectionAnchor(
    pane: PaneState,
    canvasSizePx: IntSize,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rect = pane.textSelectionRect ?: return
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { TEXT_BAR_GAP.roundToPx() }
    val marginPx = with(LocalDensity.current) { TEXT_BAR_MARGIN.roundToPx() }
    val placed = selectionBarOffset(
        anchorX = rect.centerX(),
        selectionTop = rect.top,
        selectionBottom = rect.bottom,
        barSize = barSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )
    TextSelectionBar(
        onCopy = onCopy,
        onHighlight = onHighlight,
        onDeselect = onDeselect,
        modifier = modifier
            .offset { placed }
            // The same arrival as the element bar — the app's one floating-surface motion, played in
            // place, before the capsule's own fill so the layer holds the glass and not just its rows.
            .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0f))
            .onSizeChanged { barSize = it },
    )
}

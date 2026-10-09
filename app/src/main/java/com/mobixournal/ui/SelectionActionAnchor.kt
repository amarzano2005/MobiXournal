package com.mobixournal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.render.clearSelection
import com.mobixournal.render.copySelection
import com.mobixournal.render.cutSelection
import com.mobixournal.render.deleteSelection
import com.mobixournal.render.duplicateSelection
import com.mobixournal.render.restyleSelection
import kotlin.math.roundToInt

/** Gap between the floating action bar and the selection it belongs to. */
private val SELECTION_BAR_GAP = 10.dp

/** Smallest distance the floating action bar keeps from the canvas edges. */
private val SELECTION_BAR_MARGIN = 8.dp

/** How long the bar takes to fade in once it knows where it sits. */
private const val SELECTION_BAR_FADE_MS = 120

/**
 * Fades the bar in over [SELECTION_BAR_FADE_MS] while it is unmeasured.
 *
 * The placement needs the bar's own width and height, which only land a frame or two after the
 * selection appears. Drawing it before then puts it at the fallback margin and then at the selection,
 * which reads as a flicker; holding it at zero alpha until it has been measured makes it fade in once,
 * where it belongs.
 */
@Composable
private fun rememberAppearAlpha(measured: Boolean): Float =
    animateFloatAsState(
        targetValue = if (measured) 1f else 0f,
        animationSpec = tween(durationMillis = SELECTION_BAR_FADE_MS),
        label = "selectionBarAlpha",
    ).value

/**
 * The element-selection action bar (cut / copy / duplicate / recolour / width / delete), floating
 * against the selection instead of in a fixed corner of the screen: **below** it, since that is the
 * side the eye expects the controls of something to be on, and **above** it only when the canvas runs
 * out below (a selection at the foot of the page would otherwise push the bar off-screen).
 *
 * It is rendered by [EditorPaneView] **inside the canvas box** that hosts the surface, so the
 * selection box the surface reports — its own view px — maps to this composable's coordinates with
 * no further conversion, and in split view the bar lands on whichever pane holds the selection.
 *
 * The bar is not rendered when [PaneState.selectionRect] is null (nothing selected, or the
 * selection's page isn't laid out); [EditorPaneView] still guards on the same value.
 */
@Composable
fun SelectionActionAnchor(
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
) {
    val surface = pane.surface
    val rect = pane.selectionRect ?: return
    val palette = rememberColorPaletteState(settings, onSettingsChange)
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val appear = rememberAppearAlpha(measured = barSize.width > 0)
    val gapPx = with(LocalDensity.current) { SELECTION_BAR_GAP.roundToPx() }
    val marginPx = with(LocalDensity.current) { SELECTION_BAR_MARGIN.roundToPx() }
    val offset = selectionBarOffset(
        anchorX = rect.centerX(),
        selectionTop = rect.top,
        selectionBottom = rect.bottom,
        barSize = barSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )
    SelectionActionBar(
        onCut = { surface?.cutSelection() },
        onCopy = { surface?.copySelection() },
        onDuplicate = { surface?.duplicateSelection() },
        onDelete = { surface?.deleteSelection() },
        onRecolor = { c -> surface?.restyleSelection(c, null) },
        palette = palette,
        onReWidth = { w -> surface?.restyleSelection(null, w.toDouble()) },
        widthSlots = settings.penWidths,
        modifier = modifier
            .offset { IntOffset(offset.x, offset.y) }
            .alpha(appear)
            .onSizeChanged { barSize = it },
    )
}

/**
 * Where [SelectionActionBar] sits for a selection whose horizontal centre is [anchorX] and whose top
 * and bottom edges are [selectionTop]/[selectionBottom]: centred over it and **below** it when the
 * canvas has room, lifted **above** it when a bar below would fall off the bottom, and always kept
 * inside the canvas by [marginPx]. Pure geometry, so it is unit-testable without a device.
 */
internal fun selectionBarOffset(
    anchorX: Float,
    selectionTop: Float,
    selectionBottom: Float,
    barSize: IntSize,
    canvasSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): IntOffset {
    // Size is unknown for the first frame or two (measurement lands after layout); park the bar out
    // of the way at the margin rather than letting it jump to the canvas origin. [SelectionActionAnchor]
    // keeps it at zero alpha until then, so the parked position is never seen.
    if (canvasSize.width <= 0 || canvasSize.height <= 0 || barSize.width <= 0) {
        return IntOffset(marginPx, marginPx)
    }
    val maxX = (canvasSize.width - barSize.width - marginPx).coerceAtLeast(marginPx)
    val x = (anchorX - barSize.width / 2f).roundToInt().coerceIn(marginPx, maxX)
    val maxY = (canvasSize.height - barSize.height - marginPx).coerceAtLeast(marginPx)
    val below = (selectionBottom + gapPx).roundToInt()
    val above = (selectionTop - barSize.height - gapPx).roundToInt()
    val y = when {
        // The default: the bar belongs under the thing it acts on.
        below <= maxY -> below
        // The canvas ends below the selection, so the bar goes above it instead of off-screen.
        above >= marginPx -> above
        // Too tight either way (a selection taller than what's left of the canvas): stay below and
        // let the clamp pull it just inside the bottom edge, which keeps it nearest the selection.
        else -> below
    }.coerceIn(marginPx, maxY)
    return IntOffset(x, y)
}

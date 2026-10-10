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
import com.mobixournal.render.alignSelection
import com.mobixournal.render.clearSelection
import com.mobixournal.render.copySelection
import com.mobixournal.render.distributeSelection
import com.mobixournal.render.cutSelection
import com.mobixournal.render.deleteSelection
import com.mobixournal.render.duplicateSelection
import com.mobixournal.render.pasteClipboard
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
 * The element-selection action bar (cut / copy / paste / duplicate / recolour / width / delete),
 * floating against the selection instead of in a fixed corner of the screen: **below** it, since that
 * is the side the eye expects the controls of something to be on, and **above** it only when the
 * canvas runs out below (a selection at the foot of the page would otherwise push the bar off-screen).
 *
 * With no selection — but something on the clipboard, in a tool that can act on what a paste lands —
 * the same bar stands in at the **bottom centre** carrying Paste alone, rather than a second, separate
 * bar appearing for it. One bar, one place, whichever state the canvas is in.
 *
 * It is rendered by [EditorPaneView] **inside the canvas box** that hosts the surface, so the
 * selection box the surface reports — its own view px — maps to this composable's coordinates with
 * no further conversion, and in split view the bar lands on whichever pane holds the selection.
 */
@Composable
fun SelectionActionAnchor(
    pane: PaneState,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
) {
    val surface = pane.surface
    val rect = pane.selectionRect
    val canPaste = canPasteOnCanvas(pane, ui)
    if (rect == null && !canPaste) return
    val palette = rememberColorPaletteState(settings, onSettingsChange)
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val appear = rememberAppearAlpha(measured = barSize.width > 0)
    val gapPx = with(LocalDensity.current) { SELECTION_BAR_GAP.roundToPx() }
    val marginPx = with(LocalDensity.current) { SELECTION_BAR_MARGIN.roundToPx() }
    val offset = selectionBarOffset(
        // With nothing selected there is no box to hang the bar on, so it takes the page-level spot: the
        // bottom edge is passed as "below" and the existing rule lifts it just inside.
        anchorX = rect?.centerX() ?: canvasSizePx.width / 2f,
        selectionTop = rect?.top ?: canvasSizePx.height.toFloat(),
        selectionBottom = rect?.bottom ?: canvasSizePx.height.toFloat(),
        barSize = barSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )
    SelectionActionBar(
        hasSelection = rect != null,
        canPaste = canPaste,
        onPaste = { pasteFromActionBar(ui, pane, settings, onSettingsChange) },
        onCut = { surface?.cutSelection() },
        onCopy = { surface?.copySelection() },
        onDuplicate = { surface?.duplicateSelection() },
        onDelete = { surface?.deleteSelection() },
        onRecolor = { c -> surface?.restyleSelection(c, null) },
        palette = palette,
        onReWidth = { w -> surface?.restyleSelection(null, w.toDouble()) },
        widthSlots = settings.penWidths,
        onAlign = { a -> surface?.alignSelection(a) },
        onDistribute = { h -> surface?.distributeSelection(h) },
        modifier = modifier
            .offset { IntOffset(offset.x, offset.y) }
            .alpha(appear)
            .onSizeChanged { barSize = it },
    )
}

/**
 * Whether the action bar should offer **Paste** right now: something is on the clipboard, the canvas
 * is in a tool that can act on what a paste lands, and the background-region bar isn't already up —
 * pasting needs no selection, so this is the bar that carries it either way.
 */
internal fun canPasteOnCanvas(pane: PaneState, ui: EditorUiState): Boolean =
    pane.hasClipboard && !pane.hasBackgroundRegion && ui.tool in MARQUEE_TOOLS

/**
 * Paste the clipboard onto the visible page.
 *
 * A paste lands a **fresh selection**, so the canvas is switched to SELECT first: under BG_SELECT the
 * gesture layer never reaches the selection controller, and the pasted elements would draw as selected
 * yet be undraggable (and die on the next touch).
 */
internal fun pasteFromActionBar(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    if (ui.tool != EditorTool.SELECT && ui.tool != EditorTool.LASSO_SELECT) {
        ui.tool = EditorTool.SELECT
        pane.surface?.applyTool(ui.tool)
        // Point the rail's Select slot at SELECT too, or it would keep facing the tool we just left
        // and misreport what the canvas is actually in.
        groupOf(EditorTool.SELECT)?.let {
            onSettingsChange(
                settings.copy(
                    toolGroupSelections =
                        it.withSelection(settings.toolGroupSelections, EditorTool.SELECT),
                ),
            )
        }
    }
    pane.surface?.pasteClipboard()
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

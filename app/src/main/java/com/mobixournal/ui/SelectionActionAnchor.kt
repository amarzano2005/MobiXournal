package com.mobixournal.ui

import androidx.compose.animation.core.animateIntOffsetAsState
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
import com.mobixournal.render.singleSelectedCircuit
import com.mobixournal.render.singleSelectedPlot
import com.mobixournal.render.singleSelectedShape
import com.mobixournal.render.singleSelectedTable
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import kotlin.math.roundToInt

/** Gap between the floating action bar and the selection it belongs to. */
private val SELECTION_BAR_GAP = 10.dp

/** Smallest distance the floating action bar keeps from the canvas edges. */
private val SELECTION_BAR_MARGIN = 8.dp

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
    if (ui.editingTableCell != null || ui.editingPlot != null || ui.editingCircuit != null ||
        ui.editingShape != null
    ) {
        return
    }
    val palette = rememberColorPaletteState(settings, onSettingsChange)
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val measured = barSize.width > 0
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
    // The placement **glides** instead of jumping, but only once the bar has been measured. Before that
    // its target is the parked margin, and an offset animation started there carried the bar in from the
    // top of the canvas. Creating the animation state at the first real placement makes it begin *at* its
    // target, so the bar appears where it belongs; a later change of shape (Paste alone ↔ the whole set,
    // an Edit button arriving) still glides into place instead of snapping.
    val placed = if (measured) {
        val gliding by animateIntOffsetAsState(offset, label = "selectionBarPlace")
        gliding
    } else {
        offset
    }
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
        canEditShape = surface?.singleSelectedShape() != null,
        onEditShape = {
            surface?.singleSelectedShape()?.let { shape ->
                ui.editingShape = shape
            }
        },
        canEditPlot = surface?.singleSelectedPlot() != null,
        onEditPlot = {
            surface?.singleSelectedPlot()?.let { plot ->
                ui.openPlotInspector(plot)
            }
        },
        canEditTable = surface?.singleSelectedTable() != null,
        onEditTable = {
            surface?.singleSelectedTable()?.let { table ->
                ui.editingTableCell = EditingTableCell(table, 0, 0)
            }
        },
        canEditCircuit = surface?.singleSelectedCircuit() != null,
        onEditCircuit = {
            surface?.singleSelectedCircuit()?.let { circuit ->
                ui.editingCircuit = circuit
            }
        },
        modifier = modifier
            .offset { placed }
            // The arrival is the app's one floating-surface motion — the glass spring's scale and fade,
            // played **in place**. The bar is a surface over the page, so it appears where the selection
            // is instead of sliding in from an edge. Applied before the bar's own fill, so the layer
            // holds the glass and not just the rows inside it.
            .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0f))
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

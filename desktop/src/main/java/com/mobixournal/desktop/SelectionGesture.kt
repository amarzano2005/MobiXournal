package com.mobixournal.desktop

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isShiftPressed
import com.mobixournal.render.PageSelection
import com.mobixournal.render.rectRegion
import com.mobixournal.render.SelectionEdit
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.Vec2

/**
 * The selection tools' drag: trace a region, or act on the one that is already there.
 *
 * Both marquee and lasso arrive here, and the **press** decides which of the two lives the drag has:
 *
 *  - a press on one of the outline's handles (or inside it) *transforms* the live selection — the body moves
 *    it, a corner scales it about the opposite corner, the handle above it turns it about its centre;
 *  - any other press *traces* a new region: a rectangle for the marquee, the freehand loop for the lasso.
 *
 * The traced region is previewed through [region] — the sheet paints it as the marching outline the whole
 * time it is being drawn — and becomes the tab's selection on lift, or clears it when the region caught
 * nothing. A transform is applied to the page on every move (the vertical-space drag's own approach: one edit
 * per frame, one undo step for the whole gesture), always recomputed from the page **as it was at grab time**,
 * so dragging back and forth cannot compound.
 *
 * A selection drag is not ink: it never asks the pen's packets to take it (`DesktopTool.inksByPackets`), so a
 * stylus in proximity still reaches it through the Compose pointer.
 */
/**
 * The marquee or lasso taking this press, when the slot is one of them; false means the press is ink's.
 *
 * The rule it hides is one line in `pageGestures` (`SourceStructureTest` caps a function's length): a
 * selection slot only reaches its drag when the canvas has a region to write, which is why a canvas that
 * shows no preview cannot trace one.
 */
internal suspend fun AwaitPointerEventScope.selectionPress(
    down: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    density: Float,
    region: MutableState<SelectionRegion?>?,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
): Boolean {
    val selection = selectionToolOf(tool) ?: return false
    selectionDrag(
        start = down,
        tool = selection,
        tab = tab,
        viewport = viewport,
        pageW = pageW,
        pageH = pageH,
        density = density,
        region = region ?: return false,
        onLiveDrag = onLiveDrag,
        onEdited = onEdited,
    )
    return true
}

internal suspend fun AwaitPointerEventScope.selectionDrag(
    start: PointerInputChange,
    tool: SelectionTool,
    tab: DesktopTab,
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    density: Float,
    region: MutableState<SelectionRegion?>,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
) {
    val (x0, y0) = pagePointOf(start.position.x, start.position.y, density, viewport)
    if (x0 !in 0.0..pageW.toDouble() || y0 !in 0.0..pageH.toDouble()) return
    // The grab radius is ten screen pixels whatever the zoom, so a handle stays as easy to hit at 30 % as at
    // 300 %.
    val pxPerPt = viewport.zoom * density
    val tol = (HANDLE_PX / pxPerPt).toDouble()
    val gap = (ROTATE_GAP_PX / pxPerPt).toDouble()
    val live = tab.selection
    val handle = if (live == null) {
        SelectionHandle.NONE
    } else {
        selectionHandleAt(live, x0, y0, tol, rotateGapPt = gap)
    }
    if (handle.startsRegion()) {
        traceRegion(tool, x0, y0, tab, viewport, density, region, onLiveDrag)
        return
    }
    transformSelection(live!!, handle, x0, y0, tab, viewport, density, onLiveDrag, onEdited)
}

/** The trace half: collect the drag into a rectangle or a loop, and hand it to the page on lift. */
private suspend fun AwaitPointerEventScope.traceRegion(
    tool: SelectionTool,
    x0: Double,
    y0: Double,
    tab: DesktopTab,
    viewport: Viewport,
    density: Float,
    region: MutableState<SelectionRegion?>,
    onLiveDrag: ((() -> Unit)?) -> Unit,
) {
    val points = mutableListOf(Vec2(x0, y0))
    var cancelled = false
    val cancel = {
        cancelled = true
        region.value = null
    }
    onLiveDrag(cancel)
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            val (x1, y1) = pagePointOf(change.position.x, change.position.y, density, viewport)
            region.value = when (tool) {
                SelectionTool.MARQUEE -> rectRegion(x0, y0, x1, y1)
                SelectionTool.LASSO -> SelectionRegion.Polygon(points + Vec2(x1, y1))
            }
            val up = change.changedToUpIgnoreConsumed() || !change.pressed
            if (up) {
                val traced = region.value
                region.value = null
                if (!cancelled && traced != null) {
                    tab.selection = finishedSelection(traced, traced.catches(tab.document.page(tab.page)))
                }
                break
            }
            // The loop is what the lasso commits; a rectangle reads its corners off the pointer.
            if (tool == SelectionTool.LASSO) points += Vec2(x1, y1)
            change.consume()
        }
    } finally {
        region.value = null
        onLiveDrag(null)
    }
}

/** The transform half: move, scale or rotate, live, from the page as it was at grab time. */
private suspend fun AwaitPointerEventScope.transformSelection(
    selection: PageSelection,
    handle: SelectionHandle,
    x0: Double,
    y0: Double,
    tab: DesktopTab,
    viewport: Viewport,
    density: Float,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
) {
    val index = tab.page
    // The page at grab time: every move is applied to this, never to the result of the last move, so a
    // round trip returns the ink exactly where it started.
    val original = runCatching { tab.document.page(index) }.getOrNull() ?: return
    val before = tab.document.snapshot()
    var recorded = false
    var cancelled = false
    val cancel = {
        cancelled = true
        tab.document.restore(before)
        tab.selection = selection
        onEdited(index)
    }
    onLiveDrag(cancel)
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            val (x1, y1) = pagePointOf(change.position.x, change.position.y, density, viewport)
            val (edited, next) = when (handle) {
                SelectionHandle.BODY -> SelectionEdit.move(original, selection, x1 - x0, y1 - y0)
                SelectionHandle.ROTATE -> SelectionEdit.rotate(
                    original,
                    selection,
                    rotationBetween(
                        selection, x0, y0, x1, y1,
                        snap = currentEvent.keyboardModifiers.isShiftPressed,
                    ),
                )
                else -> {
                    val (sx, sy) = scaleFactors(selection.bounds, handle, x1, y1)
                    val (anchorX, anchorY) = anchorOf(selection.bounds, handle)
                    SelectionEdit.scale(original, selection, sx, sy, anchorX, anchorY)
                }
            }
            if (edited !== original) {
                if (!recorded) {
                    tab.record(before)
                    recorded = true
                }
                tab.document.editPage(index) { edited }
                tab.selection = next
                onEdited(index)
            }
            if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            change.consume()
        }
    } finally {
        onLiveDrag(null)
    }
}

/** The grab radius of a handle, in screen pixels. */
internal const val HANDLE_PX = 9f

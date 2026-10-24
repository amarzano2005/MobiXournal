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
 *
 * A drag that reaches the canvas's edge **scrolls the view** while it keeps running ([DragView]): the
 * pointer can rest against the border and the sheet keeps travelling under it, so a marquee can catch more
 * than what happened to be on screen and an element can be carried past the edge.
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
    view: DragView,
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
        view = view,
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
    view: DragView,
    density: Float,
    region: MutableState<SelectionRegion?>,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
) {
    val viewport = view.current()
    val (x0, y0) = pagePointOf(start.position.x, start.position.y, density, viewport)
    if (x0 !in 0.0..view.pageW.toDouble() || y0 !in 0.0..view.pageH.toDouble()) return
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
        traceRegion(tool, x0, y0, tab, view, density, region, onLiveDrag)
        return
    }
    transformSelection(live!!, handle, x0, y0, tab, view, density, onLiveDrag, onEdited)
}

/** The trace half: collect the drag into a rectangle or a loop, and hand it to the page on lift. */
private suspend fun AwaitPointerEventScope.traceRegion(
    tool: SelectionTool,
    x0: Double,
    y0: Double,
    tab: DesktopTab,
    view: DragView,
    density: Float,
    region: MutableState<SelectionRegion?>,
    onLiveDrag: ((() -> Unit)?) -> Unit,
) {
    val points = mutableListOf(Vec2(x0, y0))
    // Where the pointer is on screen, kept across ticks: the edge scroll needs a position to read even
    // when no event has moved it.
    val press = view.current().toScreen(x0.toFloat(), y0.toFloat())
    var lastX = press.first
    var lastY = press.second
    var cancelled = false
    val cancel = {
        cancelled = true
        region.value = null
    }
    onLiveDrag(cancel)

    /** The region the pointer at ([px], [py]) traces, against the sheet as it stands now. */
    fun trace(px: Float, py: Float) {
        val (x1, y1) = pagePointOf(px, py, density, view.current())
        region.value = when (tool) {
            SelectionTool.MARQUEE -> rectRegion(x0, y0, x1, y1)
            SelectionTool.LASSO -> SelectionRegion.Polygon(points + Vec2(x1, y1))
        }
    }

    // The canvas's ticker calls [trace] back while the pointer rests at the border, so a marquee keeps
    // growing without a single new event.
    view.beginDrag(lastX, lastY) { px, py -> trace(px, py) }
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            lastX = change.position.x
            lastY = change.position.y
            view.moved(lastX, lastY)
            trace(lastX, lastY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                val traced = region.value
                region.value = null
                if (!cancelled && traced != null) {
                    tab.selection = finishedSelection(traced, traced.catches(tab.document.page(tab.page)))
                }
                break
            }
            // The loop is what the lasso commits; a rectangle reads its corners off the pointer.
            if (tool == SelectionTool.LASSO) {
                val (x1, y1) = pagePointOf(lastX, lastY, density, view.current())
                points += Vec2(x1, y1)
            }
            change.consume()
        }
    } finally {
        view.endDrag()
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
    view: DragView,
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
    val press = view.current().toScreen(x0.toFloat(), y0.toFloat())
    var lastX = press.first
    var lastY = press.second

    /** Move, scale or turn the selection so it answers the pointer at ([px], [py]). */
    fun apply(px: Float, py: Float) {
        val (x1, y1) = pagePointOf(px, py, density, view.current())
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
    }

    // The canvas's ticker re-applies the transform while the sheet scrolls at the border, so the element
    // keeps following the pointer past the window's edge without the pointer moving at all.
    view.beginDrag(lastX, lastY) { px, py -> apply(px, py) }
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            lastX = change.position.x
            lastY = change.position.y
            view.moved(lastX, lastY)
            apply(lastX, lastY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            change.consume()
        }
    } finally {
        view.endDrag()
        onLiveDrag(null)
    }
}

/** The grab radius of a handle, in screen pixels. */
internal const val HANDLE_PX = 9f

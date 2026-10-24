package com.mobixournal.desktop

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isShiftPressed
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.isLivingEntity
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.SelectionTester
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.rectRegion
import com.mobixournal.render.SelectionEdit
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.Vec2
import kotlin.math.hypot

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
    /** What a double-click on the selected element means: open its editor (the tablet's double-tap). */
    onEditLiving: (() -> Unit)? = null,
    /** The presses so far, so the second one can be told apart from two separate clicks. */
    taps: TapTracker = TapTracker(),
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
        onEditLiving = onEditLiving,
        taps = taps,
    )
    return true
}

/**
 * Two presses in nearly the same place within [DOUBLE_TAP_MS] are one **double-click** — the window's form
 * of the tablet's double-tap, which opens a living element's editor.
 *
 * Plain Kotlin with an injected clock, because a rule about time is exactly the kind of thing a UI test
 * should not have to race: [isSecond] is a JVM test with made-up timestamps.
 */
internal class TapTracker(
    private val windowMs: Long = DOUBLE_TAP_MS,
    private val slopPx: Double = DOUBLE_TAP_SLOP_PX,
) {
    private var lastMs = 0L
    private var lastX = 0.0
    private var lastY = 0.0

    /** Whether this press, at ([px], [py]) screen pixels at [nowMs], is the second of a double-click. */
    fun isSecond(nowMs: Long, px: Double, py: Double): Boolean {
        val second = lastMs != 0L &&
            nowMs - lastMs in 0..windowMs &&
            hypot(px - lastX, py - lastY) <= slopPx
        if (second) {
            lastMs = 0L
        } else {
            lastMs = nowMs
            lastX = px
            lastY = py
        }
        return second
    }
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
    onEditLiving: (() -> Unit)? = null,
    taps: TapTracker = TapTracker(),
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
    val page = runCatching { tab.document.page(tab.page) }.getOrNull()
    val picked = page?.let { SelectionTester.pickTopmost(it, x0, y0) }
    // A **double-click** anywhere on an element: pick it, and if it is a living recipe open its editor —
    // the tablet's double-tap. Recorded for every press, so two clicks in one place are always a pair.
    if (taps.isSecond(System.currentTimeMillis(), (x0 * pxPerPt), (y0 * pxPerPt)) && page != null && picked != null) {
        tab.selection = selectionOf(page, picked)
        val element = elementAt(page, picked)
        if (element != null && element.isLivingEntity) onEditLiving?.invoke()
        return
    }
    if (handle.startsRegion()) {
        // A **click on an element** selects it, and the drag that follows carries it: the marquee used to
        // trace a zero-size region and drop the selection, so picking one thing up meant boxing it.
        if (page != null && picked != null) {
            tab.selection = selectionOf(page, picked)
            transformSelection(
                tab.selection ?: return, SelectionHandle.BODY, x0, y0, tab, view, density, onLiveDrag, onEdited,
            )
            return
        }
        traceRegion(tool, x0, y0, tab, view, density, region, onLiveDrag)
        return
    }
    // A grip on the element itself — a figure's corner, a table's divider — is caught before the
    // selection's own five handles, because it edits the recipe rather than the box.
    val selection = requireNotNull(live) { "a traced region was handled above" }
    val living = if (selection.refs.size == 1) selectedLivingElement(tab) else null
    val grip = living?.let { gripAt(it, x0, y0, tol) }
    if (living != null && grip != null) {
        dragLivingGrip(living, grip, x0, y0, tab, view, density, onLiveDrag, onEdited)
        return
    }
    transformSelection(selection, handle, x0, y0, tab, view, density, onLiveDrag, onEdited)
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

/** How long a first click stays a candidate for a double-click, in milliseconds. */
internal const val DOUBLE_TAP_MS = 400L

/** How far the second click may land from the first, in screen pixels. */
internal const val DOUBLE_TAP_SLOP_PX = 8.0

/**
 * The grip half: drag a figure's corner or a table's divider, live, from the element as it was at grab
 * time — one edit per frame and **one** undo step for the whole gesture, the same shape as a transform.
 *
 * `Esc` (through [onLiveDrag]'s cancel) puts the element back exactly, and a drag that changes nothing
 * records nothing, so a click that brushed a grip leaves the page and its history alone.
 */
private suspend fun AwaitPointerEventScope.dragLivingGrip(
    element: Element,
    grip: LivingGrip,
    x0: Double,
    y0: Double,
    tab: DesktopTab,
    view: DragView,
    density: Float,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
) {
    val index = tab.page
    val original = runCatching { tab.document.page(index) }.getOrNull() ?: return
    val ref = livingTargetOf(tab) ?: return
    val before = tab.document.snapshot()
    var recorded = false
    var cancelled = false
    val cancel = {
        cancelled = true
        tab.document.restore(before)
        tab.selection = selectionOf(element, ref)
        onEdited(index)
    }
    onLiveDrag(cancel)
    val press = view.current().toScreen(x0.toFloat(), y0.toFloat())
    var lastX = press.first
    var lastY = press.second

    /** Bend the element so [grip] answers the pointer at ([px], [py]). */
    fun apply(px: Float, py: Float) {
        val (x1, y1) = pagePointOf(px, py, density, view.current())
        val edited = dragGrip(
            element = element,
            grip = grip,
            dx = x1 - x0,
            dy = y1 - y0,
            snap = !currentEvent.keyboardModifiers.isShiftPressed,
        )
        if (edited === element) return
        if (!recorded) {
            tab.record(before)
            recorded = true
        }
        val page = withElement(original, ref, edited) ?: return
        tab.document.editPage(index) { page }
        tab.selection = selectionOf(edited, ref)
        onEdited(index)
    }

    // The edge scroll's ticker re-applies the drag while the sheet travels, so a corner keeps following
    // the pointer past the window's edge.
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

/** The selection that follows [element] after a grip moved it — the outline travels with the ink. */
private fun selectionOf(element: Element, ref: ElementRef): PageSelection {
    val bounds = ElementBounds.of(element)
    return PageSelection(rectRegion(bounds.left, bounds.top, bounds.right, bounds.bottom), setOf(ref))
}

/** The selection of one element on [page] — what a click on it means. Null if the ref has gone stale. */
private fun selectionOf(page: Page, ref: ElementRef): PageSelection? {
    val element = elementAt(page, ref) ?: return null
    return selectionOf(element, ref)
}

/** The element [ref] names on [page], or null when the page has moved on. */
private fun elementAt(page: Page, ref: ElementRef): Element? =
    page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)

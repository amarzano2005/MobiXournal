package com.mobixournal.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.positionChange
import com.mobixournal.render.EraserMode
import com.mobixournal.render.InkEvent
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.InkPointerRouter
import com.mobixournal.render.PageEraser
import com.mobixournal.render.PointerKind
import com.mobixournal.render.eraserRadiusPt
import kotlin.math.exp

/**
 * The page's **pointer path**, kept out of the composable: what a drag on the sheet means.
 *
 * Two gestures share one pointer stream, in the order the window offers them — middle-drag (or
 * space-drag) pans, and a primary drag is handed to the shared [InkPointerRouter], which decides
 * whether it inks, erases or does nothing. Nothing here re-decides input: [PageInkGesture] translates
 * Compose's pointer into the router's vocabulary and carries its answers onto the page through
 * [DesktopDocument.editPage].
 *
 * Split from `PageCanvas` because a composable that reads and draws a page should not also carry a
 * gesture state machine (`SourceStructureTest` keeps functions small for exactly this reason).
 */
internal suspend fun PointerInputScope.pageGestures(
    tab: DesktopTab,
    panMode: Boolean,
    tool: DesktopTool,
    ink: DesktopInk,
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    draft: SnapshotStateList<InkSample>,
    pen: PenContactStream? = null,
    onViewport: (Viewport) -> Unit,
    onEdited: (Int) -> Unit,
) {
    val gesture = PageInkGesture(tab, tool, ink, viewport, pageW, pageH, density, draft, onEdited, pen)
    awaitEachGesture {
        val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
        val buttons = currentEvent.buttons
        // Middle-drag always pans; a primary drag pans only while Space is held, so the pen gets the
        // primary button.
        if (buttons.isTertiaryPressed || (panMode && buttons.isPrimaryPressed)) {
            panByDrag(viewport, pageW, pageH, viewW, viewH, onViewport)
            return@awaitEachGesture
        }
        if (!buttons.isPrimaryPressed) return@awaitEachGesture
        gesture.down(down, buttonsOf(currentEvent))
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            if (change.changedToUpIgnoreConsumed()) {
                gesture.up(change, buttonsOf(currentEvent))
                break
            }
            if (change.pressed) gesture.move(change, buttonsOf(currentEvent))
            if (gesture.drawing) change.consume()
        }
        gesture.cancel()
    }
}

/** dp a wheel notch moves the page. */
internal const val WHEEL_STEP_DP = 64f

/**
 * What one wheel event does to the page: `Ctrl` zooms **at the cursor** (exponentially, so 30 % and
 * 300 % feel alike), otherwise it scrolls. Either way the viewport comes back for the caller to apply;
 * null when the event carried no change. A scroll keeps the fit, so reading down a width-fitted strip
 * does not silently turn it into a free zoom.
 *
 * [forceZoom] is the tablet dial's notch: Windows delivers it as a plain `WM_MOUSEWHEEL` with no `Ctrl`
 * held (`docs/desktop-workstation.md` measured it), so the caller says which wheel turned — identified
 * by the device that sent it (`TabletWheel`) — and the notch takes the zoom path with no modifier. It is
 * the same zoom as `Ctrl + wheel`, cursor anchor and all, because a dial has no second opinion to add.
 */
internal fun wheelViewport(
    event: PointerEvent,
    viewport: Viewport,
    zoom: Float,
    density: Float,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onFit: (FitMode) -> Unit,
    forceZoom: Boolean = false,
): Viewport? {
    val change = event.changes.firstOrNull() ?: return null
    val delta = change.scrollDelta
    val fitted = viewport.copy(zoom = zoom)
    return if (forceZoom || event.keyboardModifiers.isCtrlPressed) {
        // One notch is roughly 0.1 of a zoom step.
        val factor = exp((-delta.y * 0.12f)).coerceIn(0.5f, 2f)
        val anchor = change.position / density
        onFit(FitMode.NONE)
        fitted.zoomBy(factor, anchor.x, anchor.y, pageW, pageH, viewW, viewH)
    } else {
        fitted.pannedBy(-delta.x * WHEEL_STEP_DP, -delta.y * WHEEL_STEP_DP, pageW, pageH, viewW, viewH)
    }
}

/** Drag the page by the pointer's movement until it lifts — the pan half of the canvas input. */
private suspend fun AwaitPointerEventScope.panByDrag(
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onViewport: (Viewport) -> Unit,
) {
    var current = viewport
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: return
        if (change.changedToUpIgnoreConsumed()) return
        val drag = change.positionChange()
        if (drag != Offset.Zero) {
            current = current.pannedBy(drag.x / density, drag.y / density, pageW, pageH, viewW, viewH)
            onViewport(current)
            change.consume()
        }
    }
}

/**
 * One drag, as the shared router sees it: the samples collected so far and the page they are being
 * drawn on.
 *
 * [drawing] is the router's own "a stroke really started" state *and* the sheet check — a press that
 * lands on the desk starts nothing, because the desk is not paper. The samples are page points already,
 * so the canvas can paint them every frame and the lift can hand them straight to [strokeOf].
 */
internal class PageInkGesture(
    private val tab: DesktopTab,
    private val tool: DesktopTool,
    private val ink: DesktopInk,
    private val view: Viewport,
    pageW: Float,
    pageH: Float,
    private val density: Float,
    private val draft: SnapshotStateList<InkSample>,
    private val onEdited: (Int) -> Unit,
    /** The pen's own reading, when the window has a stream — see [inkPointer]. */
    private val pen: PenContactStream? = null,
) {
    private val pageWidth = pageW.toDouble()
    private val pageHeight = pageH.toDouble()
    private val router = InkPointerRouter(activeTool = { tool.activeTool() }, startOnDown = false)

    /** True while a stroke this gesture started is on the sheet and will be committed. */
    var drawing: Boolean = false
        private set

    /** True once this gesture has taken its one undo step: an eraser drag is one step, not one per bite. */
    private var recorded = false

    fun down(change: PointerInputChange, buttons: Int) {
        recorded = false
        feed(change, InkPointerPhase.DOWN, buttons)
    }

    fun move(change: PointerInputChange, buttons: Int) = feed(change, InkPointerPhase.MOVE, buttons)

    fun up(change: PointerInputChange, buttons: Int) = feed(change, InkPointerPhase.UP, buttons)

    /** A gesture that ended without the router saying so leaves no ink behind. */
    fun cancel() {
        drawing = false
        draft.clear()
    }

    private fun feed(change: PointerInputChange, phase: InkPointerPhase, buttons: Int) =
        handle(router.onPointer(inkPointer(change, phase, buttons)))

    /**
     * One Compose pointer, with the pen's own readings folded in where Windows Ink has them.
     *
     * The order matters and is the reason this is one adapter and not two: Windows sends `WM_POINTER`
     * **before** the mouse message it promotes the same contact to, so the newest pen contact belongs to
     * the pointer change being translated. A device that declares no pressure keeps `change.pressure`
     * (which is `1.0`, the tool's width) rather than a zero that would draw nothing, and the eraser end of
     * a pen reclassifies the pointer as [PointerKind.ERASER_TIP] — the router then rubs out, which it
     * would not do for the mouse this driver otherwise looks like.
     */
    private fun inkPointer(change: PointerInputChange, phase: InkPointerPhase, buttons: Int): InkPointer {
        val contact = pen?.contact()
        return InkPointer(
            id = change.id.value.toInt(),
            x = change.position.x,
            y = change.position.y,
            pressure = contact?.pressure ?: change.pressure,
            timeMs = change.uptimeMillis,
            tool = if (contact?.eraser == true) PointerKind.ERASER_TIP else change.pointerKind(),
            phase = phase,
            tiltRad = contact?.tiltRad,
            azimuthRad = contact?.azimuthRad,
            buttons = buttons,
        )
    }

    private fun handle(events: List<InkEvent>) {
        for (event in events) {
            when (event) {
                is InkEvent.StrokeStart -> start(event)
                is InkEvent.StrokeSample -> if (drawing) {
                    val sample = event.sample
                    val (x, y) = pagePointOf(sample.x, sample.y, density, view)
                    draft.add(InkSample(x, y, event.widthFactor, sample))
                }
                is InkEvent.StrokeEnd -> {
                    if (drawing) commit()
                    cancel()
                }
                is InkEvent.Erase -> {
                    val (x, y) = pagePointOf(event.x, event.y, density, view)
                    eraseAt(x, y)
                }
                is InkEvent.Tap, InkEvent.PanBegin, InkEvent.GestureEnd -> cancel()
                is InkEvent.Pan, is InkEvent.Zoom -> Unit
            }
        }
    }

    /** The press that crossed the tap slop: ink from here on, unless it began on the desk. */
    private fun start(event: InkEvent.StrokeStart) {
        val start = event.start
        val (x, y) = pagePointOf(start.x, start.y, density, view)
        drawing = x in 0.0..pageWidth && y in 0.0..pageHeight
        draft.clear()
        if (drawing) draft.add(InkSample(x, y, event.widthFactor, start))
    }

    /**
     * The lift: the drawn samples become one `Stroke` on the page's top layer, as one edit — and one
     * undo step, recorded with the page as it was before the stroke.
     */
    private fun commit() {
        val samples = draft.toList()
        draft.clear()
        val stroke = tool.inkTool()?.let { strokeOf(it, ink.colour, ink.width, samples) } ?: return
        val index = tab.page
        val before = tab.document.snapshot()
        tab.document.editPage(index) { page -> page.withElement(stroke) }
        tab.record(before)
        onEdited(index)
    }

    /**
     * Rub out at a page point, at the tip the ink width sizes (`eraserRadiusPt`).
     *
     * The drag's undo step is taken **before its first bite** — a snapshot of a page nobody has touched
     * is cheap, and taking it only once an edit really happened keeps a stroke past the ink from
     * leaving an empty step behind.
     */
    private fun eraseAt(x: Double, y: Double) {
        val index = tab.page
        val radius = eraserRadiusPt(ink.width)
        val before = if (recorded) null else tab.document.snapshot()
        val edited = tab.document.editPage(index) { page ->
            PageEraser.erase(
                page = page,
                px = x,
                py = y,
                radius = radius,
                mode = EraserMode.STANDARD,
                hiddenLayers = tab.hiddenOn(index),
            ) ?: page
        }
        if (edited == null) return
        if (before != null) {
            tab.record(before)
            recorded = true
        }
        onEdited(index)
    }
}

/** The device a Compose pointer reports, in the classifier's own vocabulary (`docs/stylus.md`). */
private fun PointerInputChange.pointerKind(): PointerKind = when (type) {
    PointerType.Stylus -> PointerKind.STYLUS
    PointerType.Eraser -> PointerKind.ERASER_TIP
    PointerType.Touch -> PointerKind.FINGER
    else -> PointerKind.UNKNOWN
}

/** The button bits [InkPointer] reads — the tip (or left click) and the barrel (or right click). */
private fun buttonsOf(event: PointerEvent): Int =
    (if (event.buttons.isPrimaryPressed) InkPointer.PRIMARY else 0) or
        (if (event.buttons.isSecondaryPressed) InkPointer.SECONDARY else 0)

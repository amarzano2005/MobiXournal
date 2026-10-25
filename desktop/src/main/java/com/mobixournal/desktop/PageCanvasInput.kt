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
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.MutableState
import com.mobixournal.render.ActiveTool
import com.mobixournal.render.InkEvent
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.InkPointerRouter
import com.mobixournal.render.PageEraser
import com.mobixournal.render.PointerKind
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.VerticalSpaceOps
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.render.eraserRadiusPt
import kotlin.math.exp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    /** The pen's ink for a geometry slot; [ink] is the picked tool's own. */
    shapeInk: DesktopInk = ink,
    /** The shell's answers for a press that is not ink (`PagePressTargets`). */
    targets: PagePressTargets = PagePressTargets(),
    /**
     * The viewport **as it is now**, not the one the composable was built with: a drag that reaches the
     * canvas's edge scrolls the sheet (`DragView`), so every mapping during a gesture has to read the view
     * the reader is actually looking at.
     */
    viewport: () -> Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    draft: SnapshotStateList<InkSample>,
    pen: PenContactStream? = null,
    /**
     * True while the tablet's own packets are drawing a stroke.
     *
     * The pen reaches this window twice — as the tablet's packets and as the mouse Windows emulates it
     * as — and a stroke drawn from both would land twice. The packets are authoritative
     * (`PenPacketStroke`), so a Compose gesture that begins while they own the pen does nothing, and one
     * that is already running drops its draft and leaves.
     *
     * Only while the packets could be drawing this slot's product ([DesktopTool.inksByPackets]): a slot
     * that builds its figure or reflows the page from the drag keeps the Compose pointer even with the
     * pen in proximity, which is what makes the vertical-space grab line answer a stylus at all.
     */
    penActive: () -> Boolean = { false },
    /**
     * The vertical-space tool's grab line, in page points — painted across the sheet while it is live.
     * Null everywhere else, including for every other tool.
     */
    grabLine: MutableState<Double?>? = null,
    previewRegion: MutableState<SelectionRegion?>? = null,
    onViewport: (Viewport) -> Unit,
    onEdited: (Int) -> Unit,
) {
    // The drags' shared view: the live mapping, and the eased edge scroll each of them may ask for.
    val dragView = DragView(viewport, pageW, pageH, viewW, viewH, density, onViewport)
    // One tracker per canvas, so two clicks on the same element are a double-click even across gestures.
    val taps = TapTracker()
    val gesture = PageInkGesture(
        tab = tab,
        tool = tool,
        ink = ink,
        view = viewport,
        pageW = pageW,
        pageH = pageH,
        density = density,
        draft = draft,
        onEdited = onEdited,
        pen = pen,
    )
    // The edge scroll's clock, running for as long as this canvas has a pointer handler: a drag registers
    // itself with [DragView.beginDrag] and is called back while the sheet travels. It has to live out here
    // because a drag itself runs inside Compose's restricted pointer scope, where no timer can be launched.
    coroutineScope {
        launch {
            while (true) {
                delay(EDGE_PAN_TICK_MS)
                dragView.tick()
            }
        }
        pageGestureLoop(
            tab = tab, panMode = panMode, tool = tool, ink = ink, shapeInk = shapeInk, targets = targets,
            taps = taps, dragView = dragView, gesture = gesture,
            draft = draft, grabLine = grabLine, previewRegion = previewRegion,
            onEdited = onEdited,
            penActive = penActive, pageW = pageW, pageH = pageH,
            viewW = viewW, viewH = viewH, onViewport = onViewport, viewport = viewport,
        )
    }
}

/** The gesture loop itself: one press at a time, in the order the canvas offers them. */
private suspend fun PointerInputScope.pageGestureLoop(
    tab: DesktopTab,
    panMode: Boolean,
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    targets: PagePressTargets,
    taps: TapTracker,
    dragView: DragView,
    gesture: PageInkGesture,
    draft: SnapshotStateList<InkSample>,
    grabLine: MutableState<Double?>?,
    previewRegion: MutableState<SelectionRegion?>?,
    onEdited: (Int) -> Unit,
    penActive: () -> Boolean,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onViewport: (Viewport) -> Unit,
    viewport: () -> Viewport,
) {
    awaitEachGesture {
        val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
        // The packets own the pen only for the slots they draw (`inksByPackets`): standing aside for
        // every slot would leave a vertical-space drag or a figure unanswered by the stylus.
        val packetsOwn = { penActive() && tool.inksByPackets() }
        if (packetsOwn()) return@awaitEachGesture
        val buttons = currentEvent.buttons
        // Middle-drag always pans; a primary drag pans while Space is held or the Hand slot is picked,
        // so the pen keeps the primary button for every other tool.
        val vp = viewport()
        if (buttons.isTertiaryPressed || ((panMode || tool == DesktopTool.HAND) && buttons.isPrimaryPressed)) {
            panByDrag(vp, pageW, pageH, viewW, viewH, onViewport)
            return@awaitEachGesture
        }
        if (!buttons.isPrimaryPressed) return@awaitEachGesture
        // The Text slot **types** instead of drawing: a press opens a box at that point and hands the
        // keyboard to the field it opens. The click is the whole gesture, and nothing is written until
        // the box is committed, so an abandoned one leaves no element and no undo step.
        if (tool == DesktopTool.TEXT) {
            val (tx, ty) = pagePointOf(down.position.x, down.position.y, density, vp)
            if (tx in 0.0..pageW.toDouble() && ty in 0.0..pageH.toDouble()) {
                targets.onTextPress?.invoke(tx, ty, pageW.toDouble(), pageH.toDouble())
            }
            return@awaitEachGesture
        }
        // A selection, a living element or a figure takes the drag before the ink sees it.
        if (
            preInkPress(down, tool, tab, dragView, pageW, pageH, previewRegion, onEdited, taps, targets)
        ) {
            return@awaitEachGesture
        }
        tool.shapeKind()?.let { kind ->
            shapeDrag(down, kind, tab, shapeInk, dragView, draft, targets.onLiveDrag, onEdited)
            return@awaitEachGesture
        }
        // The vertical-space tool reflows the page under a grab line instead of drawing on it.
        if (tool.activeTool() == ActiveTool.VERTICAL_SPACE && grabLine != null) {
            verticalSpaceDrag(down, tab, dragView, grabLine, onEdited)
            return@awaitEachGesture
        }
        if (inkDrag(down, gesture, packetsOwn)) return@awaitEachGesture
    }
}

/**
 * The ink half of a press: the drag goes to the shared router until it lifts.
 *
 * True when the pen's packets took the stroke over mid-gesture — the caller must then leave the gesture
 * without committing, because that stroke is the packets' to finish.
 */
private suspend fun AwaitPointerEventScope.inkDrag(
    down: PointerInputChange,
    gesture: PageInkGesture,
    packetsOwn: () -> Boolean,
): Boolean {
    gesture.down(down, buttonsOf(currentEvent))
    while (true) {
        if (packetsOwn()) {
            gesture.cancel()
            return true
        }
        val change = awaitPointerEvent().changes.firstOrNull() ?: break
        if (change.changedToUpIgnoreConsumed()) {
            gesture.up(change, buttonsOf(currentEvent))
            break
        }
        if (change.pressed) gesture.move(change, buttonsOf(currentEvent))
        if (gesture.drawing) change.consume()
    }
    gesture.cancel()
    return false
}

/**
 * The presses that are **not ink**, in the order the canvas offers them: the marquee and lasso select a
 * region, a STEM slot places a living element, and each is one edit and one undo step of its own. A
 * geometry slot is asked after this ([AwaitPointerEventScope.shapeDrag]), because a figure *is* ink.
 */
private suspend fun AwaitPointerEventScope.preInkPress(
    down: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    view: DragView,
    pageW: Float,
    pageH: Float,
    previewRegion: MutableState<SelectionRegion?>?,
    onEdited: (Int) -> Unit,
    taps: TapTracker = TapTracker(),
    targets: PagePressTargets = PagePressTargets(),
): Boolean {
    if (selectionPress(
            down, tool, tab, view, density, previewRegion, targets.onLiveDrag, onEdited, targets.onEditLiving, taps,
        )
    ) {
        return true
    }
    return livingPress(
        down, tool, view.current(), pageW, pageH, previewRegion,
        targets.livingPreview, targets.buildLivingPreview, targets.onLiveDrag, targets.onPlaceLiving,
    )
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

/**
 * A geometry drag: press, drag, lift — one figure, one undo step.
 *
 * The preview is the **same draft list** the sheet already paints, rebuilt from the drag on every move
 * (`shapeFromDrag`), so what is previewed and what the lift writes are the same points from the same
 * function. A press off the sheet starts nothing (the desk is not paper), a drag shorter than
 * [MIN_SHAPE_DRAG_PT] draws nothing, and `Esc` cancels through the shell — which is why the gesture
 * hands its cancel to [onLiveDrag] while it runs.
 *
 * A figure that reaches the canvas's border **scrolls the view** and keeps opening toward the pointer
 * ([DragView]): a box or an arrow can be drawn larger than one screenful without lifting the pen. The
 * preview is rebuilt on every tick, not only on every pointer event, so the figure follows the sheet
 * while the pointer rests against the edge.
 */
private suspend fun AwaitPointerEventScope.shapeDrag(
    start: PointerInputChange,
    kind: ShapeKind,
    tab: DesktopTab,
    ink: DesktopInk,
    view: DragView,
    draft: SnapshotStateList<InkSample>,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
) {
    val (x0, y0) = pagePointOf(start.position.x, start.position.y, density, view.current())
    if (x0 !in 0.0..view.pageW.toDouble() || y0 !in 0.0..view.pageH.toDouble()) return
    var lastX = start.position.x
    var lastY = start.position.y
    var lastChange = start
    var cancelled = false
    val cancel = {
        cancelled = true
        draft.clear()
    }
    onLiveDrag(cancel)

    /** The figure from ([x0], [y0]) to the pointer at ([px], [py]), previewed in the sheet's own draft. */
    fun preview(px: Float, py: Float): List<StrokePoint> {
        val (x1, y1) = pagePointOf(px, py, density, view.current())
        val constrained = currentEvent.keyboardModifiers.isShiftPressed
        val points = shapeFromDrag(kind, x0, y0, x1, y1, ink.width, constrained)
        draft.clear()
        draft.addAll(points.map { point -> InkSample(point.x, point.y, 1f, samplePointer(lastChange)) })
        return points
    }

    // At the border the figure keeps opening while the sheet slides under the still pointer: the canvas's
    // ticker rebuilds the very same draft, so what is previewed stays true.
    view.beginDrag(lastX, lastY) { px, py -> preview(px, py) }
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            lastX = change.position.x
            lastY = change.position.y
            lastChange = change
            view.moved(lastX, lastY)
            val points = preview(lastX, lastY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                draft.clear()
                if (!cancelled) commitShape(points, tab, ink, onEdited)
                break
            }
            change.consume()
        }
    } finally {
        view.endDrag()
        onLiveDrag(null)
    }
}

/** One shape's commit: an ordinary stroke on the page's top layer, and one undo step. */
private fun commitShape(
    points: List<StrokePoint>,
    tab: DesktopTab,
    ink: DesktopInk,
    onEdited: (Int) -> Unit,
) {
    val stroke = shapeStroke(points, ink.colour, ink.width, ink.lineStyle) ?: return
    val index = tab.page
    val before = tab.document.snapshot()
    tab.document.editPage(index) { page -> page.withElement(stroke) }
    tab.record(before)
    onEdited(index)
}

/**
 * The vertical-space tool's drag: a grab line, and everything below it following the pointer.
 *
 * Like the figure's drag, it scrolls the view at the canvas's border and re-applies itself on every tick,
 * so space can be opened at the far end of a page-long block without lifting the pen (`DragView`).
 */
private suspend fun AwaitPointerEventScope.verticalSpaceDrag(
    start: PointerInputChange,
    tab: DesktopTab,
    view: DragView,
    grabLine: MutableState<Double?>,
    onEdited: (Int) -> Unit,
) {
    val (_, y0) = pagePointOf(start.position.x, start.position.y, density, view.current())
    if (y0 !in 0.0..view.pageH.toDouble()) return
    var lastY = start.position.y
    val index = tab.page
    val original = runCatching { tab.document.page(index) }.getOrNull() ?: return
    // The state before the gesture, taken once: a drag that shifts nothing never records it, so a pass
    // over empty space leaves no undo step behind.
    val before = tab.document.snapshot()
    var recorded = false
    var cancelled = false
    grabLine.value = y0

    /** Reflow the page so everything below the grab line answers the pointer at [py]. */
    fun reflow(py: Float) {
        val (_, y1) = pagePointOf(start.position.x, py, density, view.current())
        // Always re-applied from the page as it was **at grab time**: the block is what lay below the line
        // then, so passing an element on the way down never recruits it (the tablet's rule).
        val shifted = VerticalSpaceOps.shiftBelow(listOf(original), 0, y0, y1 - y0)[0]
        if (shifted == original) return
        if (!recorded) {
            tab.record(before)
            recorded = true
        }
        tab.document.editPage(index) { shifted }
        onEdited(index)
    }

    // At the border the sheet travels and the block keeps following on the canvas's ticker, so a
    // paragraph longer than the window can be opened in one drag.
    view.beginDrag(start.position.x, lastY) { _, py -> reflow(py) }
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            lastY = change.position.y
            view.moved(start.position.x, lastY)
            reflow(lastY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            change.consume()
        }
    } finally {
        view.endDrag()
        grabLine.value = null
    }
}

/** The pointer a preview sample carries — a shape has no pressure of its own, so it reads as a pen. */
private fun samplePointer(change: PointerInputChange): InkPointer = InkPointer(
    id = change.id.value.toInt(),
    x = change.position.x,
    y = change.position.y,
    pressure = 1f,
    timeMs = change.uptimeMillis,
    tool = change.pointerKind(),
    phase = InkPointerPhase.MOVE,
    buttons = InkPointer.PRIMARY,
)

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
    /**
     * The viewport in force **now**, not the one at press time: a tablet packet can arrive after a
     * zoom, and the page point it maps to must be the one the reader is looking at.
     */
    private val view: () -> Viewport,
    private val pageW: Float,
    private val pageH: Float,
    private val density: Float,
    private val draft: SnapshotStateList<InkSample>,
    private val onEdited: (Int) -> Unit,
    /** The pen's own reading, when the window has a stream — see [inkPointer]. */
    private val pen: PenContactStream? = null,
    /**
     * Whether the press itself opens the stroke.
     *
     * False for the window's mouse: a click selects, so a drag has to cross the tap slop before it is
     * ink. **True for the tablet's packets**, because there a press is a pen on paper — a touch that
     * never travels is a dot, exactly as it is on the tablet, and a very light stroke is no longer
     * discarded for having moved less than a mouse's slop.
     */
    private val startOnDown: Boolean = false,
) {
    private val pageWidth = pageW.toDouble()
    private val pageHeight = pageH.toDouble()
    private val router = InkPointerRouter(activeTool = { tool.activeTool() }, startOnDown = startOnDown)

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

    /**
     * A sample that is already in the router's vocabulary — the tablet's own packet, in view pixels.
     *
     * This is the entry point a stroke is drawn from when the pen's stream exists: the packet carries
     * its own position and pressure, so nothing here depends on the mouse message Windows may or may not
     * have sent for the same contact.
     */
    fun down(pointer: InkPointer) {
        recorded = false
        handle(router.onPointer(pointer.copy(phase = InkPointerPhase.DOWN)))
    }

    fun move(pointer: InkPointer) = handle(router.onPointer(pointer.copy(phase = InkPointerPhase.MOVE)))

    fun up(pointer: InkPointer) = handle(router.onPointer(pointer.copy(phase = InkPointerPhase.UP)))

    /**
     * A gesture that ended without the router saying so leaves no ink behind — **its own** ink.
     *
     * [draft] is this gesture's list and no other path writes to it (`PageCanvas` gives the tablet's
     * packets a draft of their own), so a Compose gesture that stands aside for the pen cannot take a
     * sample away from the stroke the pen is still drawing.
     */
    fun cancel() {
        drawing = false
        draft.clear()
    }

    /**
     * End the stroke where it is, as the lift would — for a stroke whose lift never arrived.
     *
     * The packets' path ends a stroke on the driver's own lift; when that packet does not come, the ink
     * the hand already drew is still the user's, so it is committed at its last sample rather than thrown
     * away. Called from `PenPacketStroke` when the packet stream goes quiet.
     */
    fun finish() {
        if (drawing) commit()
        cancel()
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
                    val (x, y) = pagePointOf(sample.x, sample.y, density, view())
                    draft.add(InkSample(x, y, event.widthFactor, sample))
                }
                is InkEvent.StrokeEnd -> {
                    if (drawing) commit()
                    cancel()
                }
                is InkEvent.Erase -> {
                    val (x, y) = pagePointOf(event.x, event.y, density, view())
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
        val (x, y) = pagePointOf(start.x, start.y, density, view())
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
        val stroke = tool.inkTool()?.let {
            strokeOf(it, ink.colour, ink.width, samples, ink.lineStyle)
        } ?: return
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
                mode = tool.eraserMode(),
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

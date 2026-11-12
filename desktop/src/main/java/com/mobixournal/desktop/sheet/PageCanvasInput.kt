package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.withElement
import com.mobixournal.desktop.ink.activeTool
import com.mobixournal.desktop.ink.shapeKind
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.InkSample
import com.mobixournal.desktop.ink.MIN_SHAPE_DRAG_PT
import com.mobixournal.desktop.ink.PenContactStream
import com.mobixournal.desktop.ink.PenPacketPan
import com.mobixournal.desktop.ink.PenPacketStroke
import com.mobixournal.desktop.ink.TabletWheel
import com.mobixournal.desktop.ink.inksByPackets
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.pagePointOf
import com.mobixournal.desktop.ink.shapeFromDrag
import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.io.DesktopAudioSession
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopPdfTextNotice
import com.mobixournal.desktop.io.DesktopPdfTextSelect
import com.mobixournal.desktop.io.PdfIndexLoader
import com.mobixournal.desktop.shell.DesktopDiagnostics
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.find
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.GUIDE_TOOLS
import com.mobixournal.desktop.tools.PointerOwnership

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
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.PageEraser
import com.mobixournal.render.PointerKind
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.VerticalSpaceOps
import com.mobixournal.format.model.Page
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
    /** Where a vertical-space drag is drawing the block, or null — see [LiveShift]. */
    liveShift: MutableState<LiveShift?>? = null,
    previewRegion: MutableState<SelectionRegion?>? = null,
    /** The magnetic highlighter's live bands (`PenPointer.pdfBands`), painted in place of the freehand path. */
    pdfBands: MutableState<List<MagneticHighlighter.Band>>? = null,
    /** The instrument a pose drag is holding right now, for the readout pill and its snap tick. */
    guideAim: MutableState<GuideAim?>? = null,
    onViewport: (Viewport) -> Unit,
    onEdited: (Int) -> Unit,
    /** The pen-tip ring: while it is up, every press on the page belongs to it. */
    hud: HudUi = HudUi(),
    /** True while the tablet's packets are the pen's voice — they end a ring's contact, not the mouse copy. */
    penVoice: () -> Boolean = { false },
    /** Stops a wheel glide still in flight, when the hand takes the sheet (see `sheetGestures`). */
    cancelGlide: () -> Unit = {},
    /** Settings ▸ Scrolling's distance and glide, and where a refused edge is reported (`EdgeBump`). */
    handGain: Float = 1f,
    onFling: (Float, Float) -> Unit = { _, _ -> },
    onEdge: (EdgeHit) -> Unit = {},
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
        bands = pdfBands,
        recognizeShapes = targets.recognizeShapes,
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
            draft = draft, grabLine = grabLine, liveShift = liveShift, previewRegion = previewRegion,
            guideAim = guideAim, onEdited = onEdited,
            penActive = penActive, pageW = pageW, pageH = pageH,
            viewW = viewW, viewH = viewH, onViewport = onViewport, viewport = viewport,
            hud = hud, penVoice = penVoice, cancelGlide = cancelGlide,
            handGain = handGain, onFling = onFling, onEdge = onEdge,
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
    liveShift: MutableState<LiveShift?>?,
    previewRegion: MutableState<SelectionRegion?>?,
    guideAim: MutableState<GuideAim?>?,
    onEdited: (Int) -> Unit,
    penActive: () -> Boolean,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    onViewport: (Viewport) -> Unit,
    viewport: () -> Viewport,
    hud: HudUi = HudUi(),
    penVoice: () -> Boolean = { false },
    cancelGlide: () -> Unit = {},
    handGain: Float = 1f,
    onFling: (Float, Float) -> Unit = { _, _ -> },
    onEdge: (EdgeHit) -> Unit = {},
) {
    awaitEachGesture {
        val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
        // A surface over the page owns the contact: the press landed on its rows, not on the sheet. Nothing
        // is drained and nothing is drawn — the contact ends here, exactly as if the page were not under it.
        if (!PointerOwnership.pageIsLive) return@awaitEachGesture
        // While the ring is up every press is its own and none inks or pans; only the pen's voice ends it.
        if (hud.open() != null) {
            ringTakesThePress(hud, down.position, density, releaseOnLift = !penVoice())
            return@awaitEachGesture
        }
        // A contact the ring **already** answered is still its own after the surface is gone: Windows' copy
        // arrives a frame later with nothing on screen and is swallowed — one contact, one answer.
        if (hud.pressHeld()) {
            hud.onRelease()
            return@awaitEachGesture
        }
        // The packets own the pen only for the slots they draw (`inksByPackets`), never while Space is held:
        // that is the hand's pan (`PenPacketPan`). Standing aside for every slot would leave a
        // vertical-space drag or a figure unanswered by the stylus.
        val packetsOwn = { penActive() && tool.inksByPackets() && !panMode }
        if (packetsOwn()) return@awaitEachGesture
        val buttons = currentEvent.buttons
        // Middle-drag always pans; a primary drag pans while Space is held or the Hand slot is picked,
        // so the pen keeps the primary button for every other tool.
        val vp = viewport()
        // The hand is the **slot's** pan, not the emulation's button state: on this workstation the pen
        // reaches Compose as a mouse copy, and a contact can arrive with no button bit set at all — so a
        // pan gated on `isPrimaryPressed` left the pen in the Hand slot doing nothing, while the packets
        // (which carry the contact properly) had nothing to move. A **right** press never pans: that is
        // the context menu's, whatever slot is in hand.
        val handPan = panMode || tool == DesktopTool.HAND
        if (buttons.isTertiaryPressed || (handPan && !buttons.isSecondaryPressed)) {
            DesktopDiagnostics.panPress()
            // The wheel's eased scroll, if it is still travelling, ends here: it is the same offset, and a
            // glide that wrote its own target after this event would undo the hand's first travel.
            cancelGlide()
            // The pan is handed the **reader** of the viewport, not `vp`: the pen's packets move this same
            // sheet between two of the drag's events (and so does the edge ticker), so each event's travel
            // has to be applied to the view as it is now — measuring from the press-time copy put the page
            // back where the gesture began, which is the bounce a pan on the tablet used to show.
            panByDrag(
                viewport, pageW, pageH, viewW, viewH, onViewport, owned = penActive,
                gain = handGain, onFling = onFling, onEdge = onEdge,
            )
            return@awaitEachGesture
        }
        if (!buttons.isPrimaryPressed) return@awaitEachGesture
        pressAfterPan(
            down = down, tool = tool, tab = tab, viewport = vp, density = density,
            pageW = pageW, pageH = pageH, shapeInk = shapeInk, dragView = dragView, targets = targets,
            guideAim = guideAim, grabLine = grabLine, liveShift = liveShift,
            previewRegion = previewRegion, onEdited = onEdited, taps = taps, draft = draft,
            gesture = gesture, packetsOwn = packetsOwn,
        )
        return@awaitEachGesture
    }
}



/**
 * The two slots whose gesture is one **click**: Text opens a box and hands the keyboard to its field, Audio
 * replays the take under the point. True when the press was one of them — on the page, or off it (a click in
 * the margin opens nothing and writes nothing).
 *
 * A text box is written on commit and a replay is never ink, so neither leaves an element or an undo step
 * behind an abandoned gesture.
 */
internal fun clickSlotTakesThePress(
    down: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    viewport: Viewport,
    density: Float,
    pageW: Float,
    pageH: Float,
    targets: PagePressTargets,
): Boolean {
    if (tool != DesktopTool.TEXT && tool != DesktopTool.AUDIO) return false
    val (x, y) = pagePointOf(down.position.x, down.position.y, density, viewport)
    if (x in 0.0..pageW.toDouble() && y in 0.0..pageH.toDouble()) {
        // The box opens where it was typed on; the Audio slot is the tablet's Play object, from the touch.
        if (tool == DesktopTool.TEXT) {
            targets.onTextPress?.invoke(x, y, pageW.toDouble(), pageH.toDouble())
        } else {
            DesktopAudioSession.tapAt(tab, x, y)
        }
    }
    return true
}

/**
 * The ink half of a press: the drag goes to the shared router until it lifts.
 *
 * True when the pen's packets took the stroke over mid-gesture — the caller must then leave the gesture
 * without committing, because that stroke is the packets' to finish.
 */
internal suspend fun AwaitPointerEventScope.inkDrag(
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
 * The **guide pose**: a press on the instrument's body slides it, a press on its handle turns or opens it,
 * and Shift snaps the angle to the shared steps.
 *
 * Nothing is written to the page — a guide is view state (`DesktopGuidePose` on the tab) — so the whole
 * gesture is one field write per frame and **no** undo step. It is asked before the ink router for the
 * reason the tablet asks its finger first: the instrument is held by a hand while the pen rules along it,
 * so taking the press here is what makes it usable at all. `Esc` puts the pose back through the shell's
 * live drag, like a figure's cancel.
 *
 * **Who may hold it**, the tablet's own answer: a pen draws and a hand holds. So a mouse or a touch press
 * always poses the instrument, while a stylus press does so only when the guides slot is the tool in hand —
 * otherwise a pen stroke drawn across the instrument's body would drag it instead of drawing along it.
 *
 * False when the press missed, so the gesture falls through to the tool in hand.
 */
internal suspend fun AwaitPointerEventScope.guideDrag(
    down: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    view: DragView,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    /** Where the aim reading is published while the drag runs (`PenPointer.guideAim`). */
    aim: MutableState<GuideAim?>? = null,
): Boolean {
    val start = tab.guide ?: return false
    if (down.type == PointerType.Stylus && tool !in GUIDE_TOOLS) return false
    val (sx, sy) = pagePointOf(down.position.x, down.position.y, density, view.current())
    if (sx !in 0.0..view.pageW.toDouble() || sy !in 0.0..view.pageH.toDouble()) return false
    val zoom = view.current().zoom
    val reach = guideReachPt(zoom, density)
    val grab = guidePress(start, tab.page, sx, sy, reach)
    if (grab == GuideGrab.NONE) return false
    val grabDx = if (grab == GuideGrab.BODY) sx - start.guide.x else 0.0
    val grabDy = if (grab == GuideGrab.BODY) sy - start.guide.y else 0.0
    var cancelled = false
    onLiveDrag({
        cancelled = true
        tab.guide = start
    })
    try {
        while (!cancelled) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            val (px, py) = pagePointOf(change.position.x, change.position.y, density, view.current())
            val snapped = currentEvent.keyboardModifiers.isShiftPressed
            val posed = guideDragged(
                start = start,
                grab = grab,
                grabDx = grabDx,
                grabDy = grabDy,
                px = px,
                py = py,
                snapAngle = snapped,
            )
            tab.guide = posed
            // The readout exists for as long as the hand holds the instrument: the pill follows the tip
            // and the tick marks a snapped aim (`GuideAimPill`), and both vanish on the lift.
            aim?.value = GuideAim(posed, snapped && grab == GuideGrab.TIP)
            change.consume()
        }
    } finally {
        aim?.value = null
        onLiveDrag(null)
    }
    return true
}

/**
 * The presses that are **not ink**, in the order the canvas offers them: the marquee and lasso select a
 * region, a STEM slot places a living element, and each is one edit and one undo step of its own. A
 * geometry slot is asked after this ([AwaitPointerEventScope.shapeDrag]), because a figure *is* ink.
 */
internal suspend fun AwaitPointerEventScope.preInkPress(
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
        targets.livingPreview, targets.onLiveDrag, targets.onPlaceLiving,
    )
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
internal suspend fun AwaitPointerEventScope.shapeDrag(
    start: PointerInputChange,
    kind: ShapeKind,
    tab: DesktopTab,
    ink: DesktopInk,
    view: DragView,
    draft: SnapshotStateList<InkSample>,
    onLiveDrag: ((() -> Unit)?) -> Unit,
    onEdited: (Int) -> Unit,
    /** The triangle's and trapezoid's own answers ([PagePressTargets.figures]). */
    figures: FigureShape = FigureShape(),
    /**
     * The page the drag is drawn on, for the pin magnet a **wire** aims at. Null when it could not be
     * read, in which case the figure keeps the hand's exact geometry.
     */
    page: Page? = null,
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

    /**
     * The figure from ([x0], [y0]) to the pointer at ([px], [py]), previewed in the sheet's own draft.
     *
     * A **wire** is magnetised here as well as at the lift (`snapWireEnds`): the preview has to show the
     * line jumping onto the pin, or the commit would appear to move the stroke after it was drawn.
     */
    // The wire's two holds, for the whole drag: the preview keeps the pin it caught while the hand stays on
    // the edge of the reach, and so does the lift that writes those same points.
    val magnet = com.mobixournal.render.EndsMagnet()

    fun preview(px: Float, py: Float): List<StrokePoint> {
        val (x1, y1) = pagePointOf(px, py, density, view.current())
        val constrained = currentEvent.keyboardModifiers.isShiftPressed
        val guide = tab.guide
        val points = snapWireEnds(
            kind,
            shapeFromDrag(kind, x0, y0, x1, y1, ink.width, constrained, figures = figures),
            page,
            view.current().zoom * density,
            magnet = magnet,
        ).map { point ->
            // A placed guide rules the figure last, so it wins over the hand *and* over the pin magnet —
            // the order the tablet applies it in (`guided()` after the grid snap). Projecting here rather
            // than at the commit is what keeps the preview the same points the lift writes.
            val (gx, gy) = guide.project(tab.page, point.x, point.y)
            point.copy(x = gx, y = gy)
        }
        // The pins the wire's two ends have caught, lit while the hand is still down (the tablet paints the
        // same ring from the same hold) — the lift therefore joins a pin the user has already seen.
        tab.magnetTargets = magnet.caught.filterNotNull()
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
        tab.magnetTargets = emptyList()
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
    tab.document.editPage(index) { page -> page.withElement(stroke, tab.inkLayer(page)) }
    tab.record(before)
    onEdited(index)
}

/**
 * The vertical-space tool's drag: a grab line, and everything below it following the pointer.
 *
 * Like the figure's drag, it scrolls the view at the canvas's border and re-applies itself on every tick,
 * so space can be opened at the far end of a page-long block without lifting the pen (`DragView`).
 *
 * Unlike a figure, it **does not touch the page while the hand is down**. The block is read once, at grab
 * time (`VerticalSpaceOps.blockBelow`), the sheet is recorded once in two halves — what stays above the line
 * and what follows it — and a sample publishes only the offset ([LiveShift]). A frame is then two picture
 * replays, exactly like a scroll; the page is written **once**, on the lift. This is desktop Xournal++'s
 * design (a mask painted under a translate, one `MoveUndoAction` at the end); rewriting the page per sample
 * instead measured 5.2 ms of reflow plus a full re-record, and the watchdog caught one drag re-recording the
 * sheet 48 times a second with a worst frame of 146 ms.
 */
internal suspend fun AwaitPointerEventScope.verticalSpaceDrag(
    start: PointerInputChange,
    tab: DesktopTab,
    view: DragView,
    grabLine: MutableState<Double?>,
    liveShift: MutableState<LiveShift?>,
    onEdited: (Int) -> Unit,
) {
    val (_, y0) = pagePointOf(start.position.x, start.position.y, density, view.current())
    if (y0 !in 0.0..view.pageH.toDouble()) return
    var lastY = start.position.y
    val index = tab.page
    val original = runCatching { tab.document.page(index) }.getOrNull() ?: return
    // The block, fixed for the whole gesture: the elements below the line as it was at grab time, so passing
    // an element on the way down never recruits it, and no frame recomputes a bounding box to find out.
    val block = VerticalSpaceOps.blockBelow(original, y0)
    // The state before the gesture, taken once: a drag that shifts nothing never records it, so a pass
    // over empty space leaves no undo step behind.
    val before = tab.document.snapshot()
    grabLine.value = y0
    liveShift.value = LiveShift(y0, 0.0)

    /** How far the pointer at [py] has carried the block, in page points. */
    fun dyOf(py: Float): Double =
        pagePointOf(start.position.x, py, density, view.current()).second - y0

    /** Where the drag has carried the block: a state write the sheet replays, and nothing else. */
    fun track(py: Float) {
        liveShift.value = LiveShift(y0, dyOf(py))
    }

    /** The lift: the whole gesture as **one** edit, from the page as it was at grab time. */
    fun commit(py: Float) {
        liveShift.value = null
        val shifted = VerticalSpaceOps.shiftBlock(original, block, dyOf(py))
        if (shifted === original) return
        tab.record(before)
        tab.document.editPage(index) { shifted }
        onEdited(index)
    }

    // At the border the sheet travels and the block keeps following on the canvas's ticker, so a
    // paragraph longer than the window can be opened in one drag.
    view.beginDrag(start.position.x, lastY) { _, py -> track(py) }
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            lastY = change.position.y
            track(lastY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                commit(lastY)
                break
            }
            change.consume()
        }
    } finally {
        view.endDrag()
        liveShift.value = null
        grabLine.value = null
    }
}

/*
 * The PDF text slot's drag: press to anchor a word, drag to extend the selection, lift to keep it.
 *
 * The words come from the tab's own index (`PdfIndexLoader`), so the anchor is the shared
 * `PdfTextIndex.anchorWord` — inside the word under the pointer, or the nearest one when the press
 * lands between words — and the range is read in the page's reading order. A page with no text layer
 * says so in one sentence instead of swallowing the gesture.
 */
internal suspend fun AwaitPointerEventScope.pdfTextDrag(
    start: PointerInputChange,
    tab: DesktopTab,
    view: DragView,
) {
    val (x, y) = pagePointOf(start.position.x, start.position.y, density, view.current())
    if (tab.pdfIndex == null) {
        // Still being read off the file: a page that has text will answer in a moment, so nothing is
        // cleared and no failure is claimed.
        return
    }
    val begun = DesktopPdfTextSelect.begin(tab, x, y)
    if (begun == null) {
        tab.pdfTextSelection = null
        DesktopPdfTextNotice.noText()
        return
    }
    DesktopPdfTextNotice.clear()
    tab.pdfTextSelection = begun
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull() ?: break
        val (px, py) = pagePointOf(change.position.x, change.position.y, density, view.current())
        tab.pdfTextSelection = DesktopPdfTextSelect.extend(tab, begun, px, py)
        if (change.changedToUpIgnoreConsumed() || !change.pressed) break
        change.consume()
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

/** The device a Compose pointer reports, in the classifier's own vocabulary (`docs/stylus.md`). */
internal fun PointerInputChange.pointerKind(): PointerKind = when (type) {
    PointerType.Stylus -> PointerKind.STYLUS
    PointerType.Eraser -> PointerKind.ERASER_TIP
    PointerType.Touch -> PointerKind.FINGER
    else -> PointerKind.UNKNOWN
}

/** The button bits [InkPointer] reads — the tip (or left click) and the barrel (or right click). */
private fun buttonsOf(event: PointerEvent): Int =
    (if (event.buttons.isPrimaryPressed) InkPointer.PRIMARY else 0) or
        (if (event.buttons.isSecondaryPressed) InkPointer.SECONDARY else 0)

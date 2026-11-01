/**
 * [DrawingSurfaceView]'s touch state machine: the routing of every pointer event (`handleTouch`,
 * `handleHover`, `handleGenericMotion` — called from the thin framework overrides that must stay on
 * the view), the pointer-kind/tool classification behind it, the two-finger pan and its release,
 * and the hand-tool double-tap. Extensions on the view, so they read its state directly.
 */
package com.mobixournal.render

import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewConfiguration
import com.mobixournal.format.model.Tool
import kotlin.math.hypot

/**
 * The touch state machine, minus the framework override: null means "not ours", and the override
 * above hands those events on to [SurfaceView].
 */
internal fun DrawingSurfaceView.handleTouch(event: MotionEvent): Boolean? {
    diagnostics.motion(event)
    // The barrel latch tracks touch events too, so a button pressed — or let go — with the tip already
    // on the glass is seen just as one pressed while hovering is.
    barrelButton.onMotion(event.buttonState, buttonKindOf(event))
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            momentum.stop()
            // The open ring takes the touch. Held (a hover gesture) it is cancelled by a tip that
            // lands, and writes nothing — the task's "no stroke was written" is that branch, not a
            // promise. Opened by a double-click it is a **picker**, and the touch is how a slot is
            // chosen (`tapRadialHud`).
            val openHud = hud
            if (openHud != null) {
                if (openHud.trigger == HudTrigger.TAP) tapRadialHud(event.x, event.y)
                else dismissRadialHud(commit = false)
                return true
            }
            multiTap.onDown(
                event.eventTime, event.x, event.y, event.getPointerId(0),
                maxOf(ViewConfiguration.get(context).scaledTouchSlop.toFloat(), 12f * resources.displayMetrics.density)
            )
            beginPointer(event, 0); beginHandTap(event); armSelectionDrag(event)
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            taps.cancelHand(); cancelSelectionDrag(); onPointerDown(event)
        }
        MotionEvent.ACTION_MOVE -> {
            taps.handMove(event.x, event.y)
            multiTap.onMove(event.eventTime, event.pointerCount, event::getPointerId, event::getX, event::getY)
            trackSelectionDragArm(event)
            // The guide is dragged by its own finger and runs *alongside* the other gestures —
            // holding it steady while the pen rules along it is the whole point.
            if (guideDrag.dragging) guideDrag.move(event)
            // A held barrel is a live modifier, not only a pointer-down decision: pressing it
            // mid-stroke ends the ink drawn so far and hands the rest of the gesture to the eraser,
            // the way desktop Xournal++ switches tools under the pen.
            if (ink.current != null && !shapeDraft.active) switchStrokeToErase(event)
            // Which gesture this move belongs to is decided in one place, by priority (`CanvasGestureRouter`).
            when (GestureRouter.route(gestureSituation())) {
                CanvasGesture.PAN -> doScroll(event)
                CanvasGesture.ERASE -> if (session.routed) feedPointerSamples(event) else eraseMove(event)
                CanvasGesture.PLACE -> placeMove(event)
                CanvasGesture.BACKGROUND_SELECT -> backgroundSelectMove(event)
                CanvasGesture.SLIDE_PARAM -> gestures.slideParam(event)
                CanvasGesture.DIVIDER -> gestures.dragDivider(event)
                CanvasGesture.VERTEX -> gestures.dragVertex(event)
                CanvasGesture.RESIZE -> gestures.resizeSelect(event)
                CanvasGesture.ROTATE -> gestures.rotateSelect(event)
                CanvasGesture.MOVE -> {
                    scroll.updateDrag(event.x, event.y)
                    gestures.moveSelect(event)
                }
                CanvasGesture.VERTICAL_SPACE -> vspace.move(event.y)
                CanvasGesture.BAND -> {
                    // Dragging the marquee itself into an edge band scrolls the page too, so a
                    // selection can reach past the viewport without letting go and panning first.
                    scroll.updateDrag(event.x, event.y)
                    gestures.bandMove(event)
                }
                CanvasGesture.TEXT_SELECT -> textSelectMove(event)
                CanvasGesture.INK -> feedPointerSamples(event)
                CanvasGesture.NONE -> Unit
            }
        }
        MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
        MotionEvent.ACTION_UP -> when {
            backgroundSelecting -> commitBackgroundSelect()
            else -> {
                cancelSelectionDrag()
                if (scroll.active && finishMultiFingerTap(event)) { cancelGesture(); render(); return true }
                captureReleaseVelocity(event); handleHandTapUp()
                // The router hears the lift too, so its contact map is empty for the next gesture.
                if (session.routed) routePointer(event, event.actionIndex, InkPointerPhase.UP)
                endGesture()
            }
        }
        MotionEvent.ACTION_CANCEL -> {
            taps.cancelHand(); cancelSelectionDrag(); cancelGesture()
        }
        else -> return null
    }
    return true
}

/**
 * The canvas's modes as one value, so [GestureRouter] decides a move's gesture without reaching into the
 * surface. One line here per mode it reads, and nothing else on this path can influence the answer.
 */
internal fun DrawingSurfaceView.gestureSituation(): GestureSituation = GestureSituation(
    panning = scroll.active,
    erasing = erasing,
    placing = placing,
    backgroundSelecting = backgroundSelecting,
    slidingParam = gestures.slidingParam,
    draggingDivider = gestures.draggingDivider,
    draggingVertex = gestures.draggingVertex,
    resizing = gestures.resizing,
    rotating = gestures.rotating,
    movingSelection = gestures.moving,
    verticalSpace = vspace.active,
    banding = gestures.banding,
    textSelecting = textSelecting,
    routingInk = session.routed && ink.inProgress,
)

/** Barrel tracking plus the wheel; null means "not ours" and falls through to [SurfaceView]. */
internal fun DrawingSurfaceView.handleGenericMotion(event: MotionEvent): Boolean? {
    diagnostics.motion(event)
    trackBarrelClicks(event)
    if (event.actionMasked == MotionEvent.ACTION_SCROLL && handleWheelScroll(event)) return true
    return null
}

/**
 * If the gesture that just ended was a still, quick two- or three-finger tap, run the action the
 * user gave that tap (Settings → Input → Finger gestures) and return true when it did anything.
 */
internal fun DrawingSurfaceView.finishMultiFingerTap(event: MotionEvent): Boolean {
    val action = fingerTapQuickAction(
        multiTap.onUp(event.eventTime),
        fingerTaps.twoFingerTapAction,
        fingerTaps.threeFingerTapAction,
    )
    if (action == QuickAction.NONE) return false
    runQuickAction(action)
    return true
}

/** The hover preview's state machine; null means "not ours" and falls through to [SurfaceView]. */
internal fun DrawingSurfaceView.handleHover(event: MotionEvent): Boolean? {
    diagnostics.motion(event)
    trackBarrelClicks(event)
    if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
        // The pen left the glass's range without a release event the app could see: the ring goes
        // away rather than waiting for a release that will never arrive. A picker is not waiting for
        // a release at all — it waits for a tap — so it stays on screen.
        if (hud?.trigger != HudTrigger.TAP) dismissRadialHud(commit = false)
        barrelClicks.reset(); vendorClicks.reset(); barrelButton.reset()
    }
    val kind = pointerKindOf(event, 0)
    // The ring tracks the nib even with the hover preview switched off — it is a control being
    // pointed at, not a preview dot, so the showHover gate below is not its gate.
    if (hud != null) {
        hovering = true; hoverX = event.x; hoverY = event.y; hoverKind = kind
        trackRadialHud(event.x, event.y)
        render()
        return true
    }
    if (!showHover || (kind != PointerKind.STYLUS && kind != PointerKind.ERASER_TIP)) {
        if (hovering) { hovering = false; render() }
        return null
    }
    when (event.actionMasked) {
        MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
            hovering = true; hoverX = event.x; hoverY = event.y; hoverKind = kind; render()
        }
        MotionEvent.ACTION_HOVER_EXIT -> { hovering = false; render() }
    }
    return true
}

/** Begin a gesture for the pointer at [pointerIndex], its intent decided by [InputClassifier]. */
internal fun DrawingSurfaceView.beginPointer(event: MotionEvent, pointerIndex: Int) {
    // Cleared here and set only by the branches that hand the gesture to the shared router: a
    // gesture it does not own must never have its moves and its lift fed to it as well.
    session.routed = false
    val kind = pointerKindOf(event, pointerIndex)
    if (isReadOnly) {
        val intent = InputClassifier.classify(kind, barrelButton.held, activeTool(), inputSettings)
        if (intent == GestureIntent.PAN) {
            beginScroll(event)
        } else if (intent != GestureIntent.IGNORE) {
            notifyReadOnlyBlocked()
        }
        return
    }
    // A finger laid on the guide manipulates it, whatever the active tool — the pen keeps
    // drawing against it meanwhile, exactly as you'd hold a real setsquare down and rule along it.
    if (kind == PointerKind.FINGER && guideDrag.begin(event, pointerIndex)) return
    // Double-tap on a living element (a table cell, a plot, a component, a figure) opens its editor.
    if (tryLivingDoubleTap(event)) return
    // Touching a slider anchored to an active plot adjusts the parameter — never a stroke.
    if (gestures.tryBeginSlide(event)) return
    // Touching a divider line inside an active table resizes that column or row.
    if (gestures.tryBeginDividerDrag(event)) return
    // Play-object is a pure query — it never edits the document, so it short-circuits the whole
    // gesture machinery rather than earning a GestureIntent of its own. A finger is the one pointer
    // that is not the query: with finger-draw off it navigates the page (one finger pans, two pinch),
    // so the page stays scrollable while **only the pen** picks the stroke to replay.
    if (audioPlayMode) {
        if (InputClassifier.playModePans(kind, inputSettings)) beginScroll(event)
        else audioTap(event, pointerIndex)
        return
    }
    val intent = InputClassifier.classify(kind, barrelButton.held, activeTool(), inputSettings)
    session.penOwned = (kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP) &&
        (intent == GestureIntent.DRAW || intent == GestureIntent.ERASE)
    when (intent) {
        // Ink and the eraser go through the shared router, which decides when a stroke begins and
        // ends and reports an erase hit (`DrawingSurfaceRouter.kt`). A fresh router per gesture keeps
        // the pressure filter and the input settings the ones in force now.
        GestureIntent.ERASE -> {
            session.routed = true
            ink.pointerRouter = newPointerRouter()
            routePointer(event, pointerIndex, InkPointerPhase.DOWN)
        }
        GestureIntent.PLACE -> beginPlace(event, pointerIndex)
        GestureIntent.PAN -> beginScroll(event)
        GestureIntent.SELECT -> beginSelect(event)
        GestureIntent.BACKGROUND_SELECT -> beginBackgroundSelect(event)
        GestureIntent.SELECT_TEXT -> beginTextSelect(event)
        GestureIntent.VERTICAL_SPACE -> beginVerticalSpace(event)
        GestureIntent.DRAW -> {
            session.routed = true
            ink.pointerRouter = newPointerRouter()
            routePointer(event, pointerIndex, InkPointerPhase.DOWN)
        }
        GestureIntent.IGNORE -> Unit
    }
}

/**
 * A second pointer arrived. A stylus/eraser tip takes over any in-progress finger gesture (so a
 * palm that landed first can't block the pen); while a stylus already owns the gesture, extra
 * finger/palm pointers are ignored (palm rejection); otherwise two fingers pan.
 *
 * The first two rules are the router's on a routed gesture: it drops a finger that arrives under a
 * pen and answers [InkEvent.PanBegin] when a second contact ends the stroke. A gesture the router does
 * not own (guide, placement, select) keeps the surface's own two-finger pan below.
 */
internal fun DrawingSurfaceView.onPointerDown(event: MotionEvent) {
    val idx = event.actionIndex
    val kind = pointerKindOf(event, idx)
    when {
        kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP -> {
            abandonInProgress()
            beginPointer(event, idx)
        }
        // Palm / finger resting while the pen writes: the router drops it as well, and answers nothing.
        session.penOwned -> if (session.routed) routePointer(event, idx, InkPointerPhase.DOWN)
        // A second contact on the stroke: the router ends it (no commit — the partial ink is dropped)
        // and starts navigation, which the surface's `doScroll` carries frame by frame.
        session.routed -> {
            multiTap.onPointerDown(event.eventTime, event.getX(idx), event.getY(idx), event.getPointerId(idx), event.pointerCount)
            routePointer(event, idx, InkPointerPhase.DOWN)
        }
        else -> {
            multiTap.onPointerDown(event.eventTime, event.getX(idx), event.getY(idx), event.getPointerId(idx), event.pointerCount)
            beginScroll(event)
        }
    }
}

/** If the pointer that lifted owns the draw/erase gesture, finish it; otherwise keep panning. */
internal fun DrawingSurfaceView.onPointerUp(event: MotionEvent) {
    guideDrag.end(event)
    val upIdx = event.actionIndex
    val upId = event.getPointerId(upIdx)
    multiTap.onPointerUp(upId)
    // The router learns about every lift, so a contact it is holding (a palm, a second finger) leaves
    // its map instead of looking like a finger that never came up.
    if (session.routed) routePointer(event, upIdx, InkPointerPhase.UP)
    if (upId == session.pointerId && (ink.current != null || erasing)) {
        endGesture()
        return
    }
    // The span also jumps when a finger leaves; re-baseline it so the drop isn't read as a pinch-out,
    // mirroring the focus/velocity reset that goes with it.
    scroll.rebaseSpan()
    scroll.rebaseFocus(focusX(event, skip = upIdx), focusY(event, skip = upIdx), event.eventTime)
}

/** Drop any in-progress draw/erase/place/band/move without committing (a stylus is taking over). */
internal fun DrawingSurfaceView.abandonInProgress() {
    dismissRadialHud(commit = false)
    scroll.stopAutoScroll(); holdSnap.reset()
    ink.current = null; ink.currentStrokes = null; shapeDraft.finish(); erasing = false; placing = false
    scroll.active = false; textSelecting = false; backgroundSelecting = false; vspace.reset()
    gestures.reset(); multiTap.reset()
    // The gesture is gone, so its contacts are too: a router left holding them would read the next
    // sample as a two-finger navigation.
    ink.pointerRouter.reset(); session.routed = false
}

internal fun DrawingSurfaceView.cancelGesture() {
    dismissRadialHud(commit = false)
    momentum.stop(); scroll.stopAutoScroll(); holdSnap.reset()
    guideDrag.end(null)
    ink.current = null; ink.currentStrokes = null; shapeDraft.finish()
    scroll.active = false; erasing = false; placing = false
    backgroundSelecting = false; gestures.reset()
    textSelecting = false; vspace.reset()
    // Nothing a cancelled gesture did is undoable, and it leaves no owner behind for the next one.
    session.abort(); multiTap.reset()
    ink.pointerRouter.reset()
}

/**
 * A mouse wheel notch scrolls the document vertically. Android reports wheel-down as a negative
 * [MotionEvent.AXIS_VSCROLL], so the sign flips: wheel down moves *further down* the document,
 * matching a scrollbar drag rather than a grab-the-paper pan. Zoom is untouched, and the move is
 * clamped by the same [ViewportState] bounds the pan gesture uses.
 */
internal fun DrawingSurfaceView.handleWheelScroll(event: MotionEvent): Boolean {
    val notches = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
    if (notches == 0f) return false
    val step = DrawingSurfaceDefaults.WHEEL_SCROLL_DP * resources.displayMetrics.density
    if (!scrollViewportBy(0f, -notches * step)) return false
    render()
    return true
}

/**
 * Map one event pointer's tool type to our device-independent [PointerKind]. The mapping itself is
 * the adapter's (`MotionEventPointer.kt`), because it is the half of the input layer that cannot
 * leave the platform and the half everything else reads.
 */
internal fun DrawingSurfaceView.pointerKindOf(event: MotionEvent, pointerIndex: Int): PointerKind =
    event.inkPointerKind(pointerIndex)

/**
 * The [PointerKind] whose buttons a `MotionEvent`'s `buttonState` describes. Button state comes from
 * the *device*, so the pen wins whenever any pointer is one — and a pen the tablet only sees as a
 * mouse reports [PointerKind.UNKNOWN], which is exactly how that case wants to be classified.
 */
internal fun DrawingSurfaceView.buttonKindOf(event: MotionEvent): PointerKind {
    for (i in 0 until event.pointerCount) {
        val kind = pointerKindOf(event, i)
        if (kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP) return kind
    }
    return pointerKindOf(event, 0)
}

/** A second finger (or the Hand tool) started panning: abandon any partial stroke/erase/place. */
internal fun DrawingSurfaceView.beginScroll(event: MotionEvent) {
    ink.current = null
    erasing = false
    placing = false
    scroll.begin(
        focusX = focusX(event, skip = -1),
        focusY = focusY(event, skip = -1),
        span = spanOf(event),
        timeMs = event.eventTime,
    )
}

/**
 * One frame of the pan: the object applies the delta and the pinch, and holds everything back while the
 * gesture is still a candidate for a two-/three-finger tap.
 */
internal fun DrawingSurfaceView.doScroll(event: MotionEvent) {
    scroll.move(
        focusX = focusX(event, skip = -1),
        focusY = focusY(event, skip = -1),
        span = spanOf(event),
        timeMs = event.eventTime,
        holdForGesture = multiTapCandidate && !multiTapMoved,
    )
}


internal fun DrawingSurfaceView.endGesture() {
    guideDrag.end(null)
    // Snapshot then clear the mode flags first, so any render() inside a commit (e.g. the
    // rubber-band) sees the gesture already ended and doesn't paint a stale marquee/overlay.
    val wasScrolling = scroll.active
    val wasErasing = erasing
    val wasPlacing = placing
    val wasMoving = gestures.moving
    val wasTransforming = gestures.resizing || gestures.rotating || gestures.slidingParam ||
        gestures.draggingDivider || gestures.draggingVertex
    val wasBanding = gestures.banding
    val wasTextSelecting = textSelecting
    val wasVspacing = vspace.active
    vspace.reset()
    scroll.active = false
    erasing = false
    placing = false
    textSelecting = false
    scroll.stopAutoScroll()
    gestures.reset()
    when {
        wasPlacing -> commitPlace()
        wasMoving -> gestures.commitMove() // applied live; re-home if dropped on another page
        wasTransforming -> Unit  // resize/rotate applied live; finishGesture records them
        wasBanding -> gestures.commitBand()
        wasTextSelecting -> Unit // the word range is updated live; the selection just stays put
        // The shift is applied live and finishGesture records it as one undo step; the repaint is
        // what clears the grab-line overlay, which would otherwise stay drawn after the release.
        wasVspacing -> render()
        !wasScrolling && !wasErasing -> commitCurrent()
    }
    // Committing the session closes it too (owner and router flag cleared), so nothing of this
    // gesture reaches the next one.
    finishGesture()
    // The router holds no contact of it either.
    ink.pointerRouter.reset()
    if (wasScrolling) scroll.glide()
    if (search.query.isNotEmpty() && (wasMoving || wasTransforming || wasVspacing)) {
        rebuildSearch()
        render()
    }
}

/**
 * End the gesture's transaction: one undo step if it actually changed the document, nothing if it did
 * not — and closed either way ([GestureSession.commit]).
 */
internal fun DrawingSurfaceView.finishGesture() {
    session.commit(doc)?.let { start ->
        history.record(start)
        notifyHistory()
    }
}

/** Mean Y of all pointers except [skip] (an index being lifted), in view px. */
internal fun DrawingSurfaceView.focusY(event: MotionEvent, skip: Int): Float {
    var sum = 0f
    var n = 0
    for (i in 0 until event.pointerCount) if (i != skip) { sum += event.getY(i); n++ }
    return if (n == 0) event.y else sum / n
}

/** Mean X of all pointers except [skip] (an index being lifted), in view px. */
internal fun DrawingSurfaceView.focusX(event: MotionEvent, skip: Int): Float {
    var sum = 0f
    var n = 0
    for (i in 0 until event.pointerCount) if (i != skip) { sum += event.getX(i); n++ }
    return if (n == 0) event.x else sum / n
}

/** Mean distance of every pointer from the touch focus (view px); a pinch's "size". 0 for <2 pointers. */
internal fun DrawingSurfaceView.spanOf(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 0f
    val fx = focusX(event, skip = -1)
    val fy = focusY(event, skip = -1)
    var sum = 0f
    for (i in 0 until event.pointerCount) sum += hypot(event.getX(i) - fx, event.getY(i) - fy)
    return sum / event.pointerCount
}

/** Arm double-tap tracking for a fresh single-finger touch, but only while the Hand tool is active. */
internal fun DrawingSurfaceView.beginHandTap(event: MotionEvent) {
    taps.beginHand(armed = handMode, timeMs = event.eventTime, x = event.x, y = event.y)
}

/** On lift, confirm a tap and — if it pairs with the previous one in time and place — fire the double-tap. */
internal fun DrawingSurfaceView.handleHandTapUp() {
    // A flick is a pan, not a tap — even one whose travel stayed inside the tap slop. Without this
    // a short-but-fast swipe in the overview grid would count as a tap and jump back to the page
    // under the finger, cancelling the glide it should have started.
    if (taps.endHand(flicked = momentum.hasRelease)) onHandDoubleTap(taps.tapX)
}

/** Route a Hand-tool double-tap by horizontal zone: left third → prev page, right third → next, centre → toggle full-page. */
internal fun DrawingSurfaceView.onHandDoubleTap(x: Float) {
    val edge = width / 3f
    when {
        x < edge -> goToPage(currentPageIndex() - 1)
        x > width - edge -> goToPage(currentPageIndex() + 1)
        else -> onToggleFullPage?.invoke()
    }
}

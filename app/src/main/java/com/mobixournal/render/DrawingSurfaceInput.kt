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
    tracePenMotion(event)
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
            beginPointer(event, 0); beginHandTap(event); armPageDrag(event)
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            handTapCandidate = false; cancelPageDrag(); onPointerDown(event)
        }
        MotionEvent.ACTION_MOVE -> {
            if (handTapCandidate) trackHandTapMove(event)
            multiTap.onMove(event.eventTime, event.pointerCount, event::getPointerId, event::getX, event::getY)
            trackPageDragArm(event)
            // A lifted page owns the gesture outright — no panning, drawing or erasing underneath it.
            if (overview.dragging) { pageDragMove(event); return true }
            // The guide is dragged by its own finger and runs *alongside* the other gestures —
            // holding it steady while the pen rules along it is the whole point.
            if (guideDrag.dragging) guideDrag.move(event)
            // A held barrel is a live modifier, not only a pointer-down decision: pressing it
            // mid-stroke ends the ink drawn so far and hands the rest of the gesture to the eraser,
            // the way desktop Xournal++ switches tools under the pen.
            if (current != null && !shaping) switchStrokeToErase(event)
            when {
                scrolling -> doScroll(event)
                erasing -> if (routed) feedPointerSamples(event) else eraseMove(event)
                placing -> placeMove(event)
                backgroundSelecting -> backgroundSelectMove(event)
                gestures.slidingParam -> gestures.slideParam(event)
                gestures.draggingDivider -> gestures.dragDivider(event)
                gestures.draggingVertex -> gestures.dragVertex(event)
                gestures.resizing -> gestures.resizeSelect(event)
                gestures.rotating -> gestures.rotateSelect(event)
                gestures.moving -> {
                    dragLastX = event.x; dragLastY = event.y
                    updateDragAutoScroll(event.x, event.y)
                    gestures.moveSelect(event)
                }
                vspace.active -> vspace.move(event.y)
                gestures.banding -> {
                    // Dragging the marquee itself into an edge band scrolls the page too, so a
                    // selection can reach past the viewport without letting go and panning first.
                    dragLastX = event.x; dragLastY = event.y
                    updateDragAutoScroll(event.x, event.y)
                    gestures.bandMove(event)
                }
                textSelecting -> textSelectMove(event)
                splineDragging -> splineMove(event)
                routed && (current != null || currentStrokes != null) -> feedPointerSamples(event)
                else -> Unit
            }
        }
        MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
        // A spline node is still mid-curve on release — it must not run the commit-and-finish path.
        MotionEvent.ACTION_UP -> when {
            overview.dragging -> { overview.disarm(); removeCallbacks(pageDragArm); finishPageDrag() }
            splineDragging -> splineUp(event)
            backgroundSelecting -> { cancelPageDrag(); commitBackgroundSelect() }
            else -> {
                cancelPageDrag()
                if (scrolling && finishMultiFingerTap(event)) { cancelGesture(); render(); return true }
                captureReleaseVelocity(event); handleHandTapUp(event)
                // The router hears the lift too, so its contact map is empty for the next gesture.
                if (routed) routePointer(event, event.actionIndex, InkPointerPhase.UP)
                endGesture()
            }
        }
        MotionEvent.ACTION_CANCEL -> {
            handTapCandidate = false; cancelPageDrag(); cancelGesture()
        }
        else -> return null
    }
    return true
}

/** Barrel tracking plus the wheel; null means "not ours" and falls through to [SurfaceView]. */
internal fun DrawingSurfaceView.handleGenericMotion(event: MotionEvent): Boolean? {
    tracePenMotion(event)
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
    tracePenMotion(event)
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
    routed = false
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
    // A board is not a page list, so the page-shaped gestures are refused here, in one place, rather
    // than half-working on a grid of chunks: the rubber band and its clipboard, the background-region
    // copy, PDF text selection and the vertical-space drag all speak page indices and page bounds
    // (`docs/infinite-canvas.md` → *What a board can be edited with*). Inke works in cell space and is
    // untouched by this.
    if (board != null && intent.isPageShaped) {
        notifyBoardBlocked()
        return
    }
    stylusOwner = (kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP) &&
        (intent == GestureIntent.DRAW || intent == GestureIntent.ERASE)
    when (intent) {
        // Ink and the eraser go through the shared router, which decides when a stroke begins and
        // ends and reports an erase hit (`DrawingSurfaceRouter.kt`). A fresh router per gesture keeps
        // the pressure filter and the input settings the ones in force now.
        GestureIntent.ERASE -> {
            routed = true
            pointerRouter = newPointerRouter()
            routePointer(event, pointerIndex, InkPointerPhase.DOWN)
        }
        GestureIntent.PLACE -> beginPlace(event, pointerIndex)
        GestureIntent.PAN -> beginScroll(event)
        GestureIntent.SELECT -> beginSelect(event)
        GestureIntent.BACKGROUND_SELECT -> beginBackgroundSelect(event)
        GestureIntent.SELECT_TEXT -> beginTextSelect(event)
        GestureIntent.VERTICAL_SPACE -> beginVerticalSpace(event)
        // The spline tool is laid down over many taps, so it gets its own gesture path.
        GestureIntent.DRAW ->
            if (shapeKind == ShapeKind.SPLINE) {
                splineDown(event, pointerIndex)
            } else {
                routed = true
                pointerRouter = newPointerRouter()
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
 * not own (spline, guide, placement, select) keeps the surface's own two-finger pan below.
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
        stylusOwner -> if (routed) routePointer(event, idx, InkPointerPhase.DOWN)
        // A second contact on the stroke: the router ends it (no commit — the partial ink is dropped)
        // and starts navigation, which the surface's `doScroll` carries frame by frame.
        routed -> {
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
    if (routed) routePointer(event, upIdx, InkPointerPhase.UP)
    if (upId == gesturePointerId && (current != null || erasing)) {
        endGesture()
        return
    }
    lastFocusY = focusY(event, skip = upIdx)
    lastFocusX = focusX(event, skip = upIdx)
    // The span also jumps when a finger leaves; re-baseline it next frame so the drop isn't read as
    // a pinch-out, mirroring the focus/velocity reset below.
    lastSpan = 0f
    momentum.rebaseline(event.eventTime, lastFocusX, lastFocusY)
}

/** Drop any in-progress draw/erase/place/band/move without committing (a stylus is taking over). */
internal fun DrawingSurfaceView.abandonInProgress() {
    dismissRadialHud(commit = false)
    clearSpline(); stopAutoScroll(); cancelHoldSnap(); holdSnapped = false; holdFigure = null
    current = null; currentStrokes = null; shaping = false; erasing = false; placing = false
    scrolling = false; textSelecting = false; backgroundSelecting = false; vspace.reset()
    gestureCell = null; gestures.reset(); multiTap.reset()
    // The gesture is gone, so its contacts are too: a router left holding them would read the next
    // sample as a two-finger navigation.
    pointerRouter.reset(); routed = false
}

internal fun DrawingSurfaceView.cancelGesture() {
    dismissRadialHud(commit = false)
    momentum.stop(); stopAutoScroll(); cancelHoldSnap(); holdSnapped = false; holdFigure = null
    guideDrag.end(null); clearSpline()
    current = null; currentStrokes = null; shaping = false; scrolling = false; erasing = false; placing = false
    backgroundSelecting = false; gestures.reset(); gestureStartDoc = null
    textSelecting = false; vspace.reset(); gestureCell = null
    gesturePointerId = -1; stylusOwner = false; multiTap.reset()
    pointerRouter.reset(); routed = false
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
    current = null
    erasing = false
    placing = false
    scrolling = true
    lastFocusY = focusY(event, skip = -1)
    lastFocusX = focusX(event, skip = -1)
    lastSpan = spanOf(event)
    momentum.clearRelease()
    momentum.rebaseline(event.eventTime, lastFocusX, lastFocusY)
}

internal fun DrawingSurfaceView.doScroll(event: MotionEvent) {
    val fy = focusY(event, skip = -1)
    val fx = focusX(event, skip = -1)
    val span = spanOf(event)
    val spanChanged = lastSpan > 0f && kotlin.math.abs(span - lastSpan) > ViewConfiguration.get(context).scaledTouchSlop
    if (multiTapCandidate && !multiTapMoved && !spanChanged) {
        lastFocusY = fy
        lastFocusX = fx
        lastSpan = span
        return
    }
    // Pan gain: 1 tracks the finger one-to-one, <1 pans slower, >1 faster, 0 freezes the document.
    // The clamps are the viewport's, and they are **per mode** ([ViewportState.scrollBy]): a strip is
    // bounded on its one fixed axis and runs on in the other, a 2D board is bounded on neither, and a
    // page stack is bounded on both. Clamping both axes to the page stack's extent — which is what
    // this pan used to do — pins the running axis home the moment that extent stops describing it.
    scrollViewportBy((lastFocusX - fx) * panSensitivity, (lastFocusY - fy) * panSensitivity)
    lastFocusY = fy
    lastFocusX = fx
    // Two fingers also pinch-zoom: a change in span since the last frame scales zoom about the focus.
    if (lastSpan > DrawingSurfaceDefaults.PINCH_MIN_SPAN_PX && span > DrawingSurfaceDefaults.PINCH_MIN_SPAN_PX) zoomAbout(fx, fy, span / lastSpan)
    lastSpan = span
    momentum.track(event.eventTime, fx, fy)
    render()
}

// --- drag auto-scroll: keep scrolling while a selection is dragged into an edge band -------------

/**
 * Update the drag auto-scroll direction from a pointer's position (view px): pushing into an edge
 * band (top, bottom, left, right) starts the page scrolling that way, and moving back out stops it.
 * Idempotent, so it is safe to call on every MOVE.
 */
internal fun DrawingSurfaceView.updateDragAutoScroll(viewX: Float, viewY: Float) {
    val edgePx = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_EDGE_DP * resources.displayMetrics.density
    val dirX = when {
        viewX > width - edgePx -> 1
        viewX < edgePx -> -1
        else -> 0
    }
    val dirY = when {
        viewY > height - edgePx -> 1
        viewY < edgePx -> -1
        else -> 0
    }
    if (dirX == dragAutoScrollDirX && dirY == dragAutoScrollDirY) return
    dragAutoScrollDirX = dirX
    dragAutoScrollDirY = dirY
    if (dirX != 0 || dirY != 0) postAutoScroll() else stopAutoScroll()
}

/** Stop the drag auto-scroll repeat (a released/cancelled gesture, or the drag left the edge band). */
internal fun DrawingSurfaceView.stopAutoScroll() {
    dragAutoScrollDirX = 0
    dragAutoScrollDirY = 0
    if (autoScrollPosted) {
        choreographer.removeFrameCallback(autoScrollCallback)
        autoScrollPosted = false
    }
}

private fun DrawingSurfaceView.postAutoScroll() {
    if (autoScrollPosted) return
    autoScrollPosted = true
    choreographer.postFrameCallback(autoScrollCallback)
}

/**
 * One auto-scroll frame: scroll one step toward the edge the finger is in (horizontally and/or vertically)
 * and re-apply the gesture at the finger's last position — the moved selection, or the marquee being dragged
 * out — so it rides the scrolling sheet, then repost.
 */
internal fun DrawingSurfaceView.autoScrollFrame() {
    autoScrollPosted = false
    val dirX = dragAutoScrollDirX
    val dirY = dragAutoScrollDirY
    if (dirX == 0 && dirY == 0) return
    val step = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_STEP_DP * resources.displayMetrics.density
    if (!scrollViewportBy(dirX * step, dirY * step)) {
        stopAutoScroll() // pinned at a bound in both axes: nothing left to scroll into
        return
    }
    if (gestures.banding) gestures.bandTo(dragLastX, dragLastY)
    else gestures.moveSelectTo(dragLastX, dragLastY)
    postAutoScroll()
}

internal fun DrawingSurfaceView.endGesture() {
    guideDrag.end(null)
    // Snapshot then clear the mode flags first, so any render() inside a commit (e.g. the
    // rubber-band) sees the gesture already ended and doesn't paint a stale marquee/overlay.
    val wasScrolling = scrolling
    val wasErasing = erasing
    val wasPlacing = placing
    val wasMoving = gestures.moving
    val wasTransforming = gestures.resizing || gestures.rotating || gestures.slidingParam ||
        gestures.draggingDivider || gestures.draggingVertex
    val wasBanding = gestures.banding
    val wasTextSelecting = textSelecting
    val wasVspacing = vspace.active
    vspace.reset()
    scrolling = false
    erasing = false
    placing = false
    textSelecting = false
    stopAutoScroll()
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
    finishGesture()
    gesturePointerId = -1
    stylusOwner = false
    // The gesture is over: nothing of it may reach the next one through the router.
    pointerRouter.reset(); routed = false
    if (wasScrolling) momentum.launch(panSensitivity)
    if (searchQuery.isNotEmpty() && (wasMoving || wasTransforming || wasVspacing)) {
        rebuildSearch()
        render()
    }
}

/** Record one undo step if this gesture actually changed the document. */
internal fun DrawingSurfaceView.finishGesture() {
    gestureCell = null
    val start = gestureStartDoc ?: return
    gestureStartDoc = null
    if (doc !== start) {
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
    handTapCandidate = handMode
    handTapMoved = false
    handTapDownTime = event.eventTime
    handTapDownX = event.x
    handTapDownY = event.y
}

/** A moved-too-far touch is a pan, not a tap — disqualify it from forming a double-tap. */
internal fun DrawingSurfaceView.trackHandTapMove(event: MotionEvent) {
    if (hypot(event.x - handTapDownX, event.y - handTapDownY) > doubleTapSlopPx) handTapMoved = true
}

/** On lift, confirm a tap and — if it pairs with the previous one in time and place — fire the double-tap. */
internal fun DrawingSurfaceView.handleHandTapUp(event: MotionEvent) {
    if (!handTapCandidate) return
    handTapCandidate = false
    // A flick is a pan, not a tap — even one whose travel stayed inside the tap slop. Without this
    // a short-but-fast swipe in the overview grid would count as a tap and jump back to the page
    // under the finger, cancelling the glide it should have started.
    if (handTapMoved || momentum.hasRelease) { handFirstTapTime = 0L; return }
    // In the overview grid a tap is about pages, not paging/zooming around: in edit mode it picks
    // pages out for a bulk edit, in view mode it just jumps to the page you tapped.
    if (columns > 1) {
        handFirstTapTime = 0L
        val box = layout.pageAt(scrollX + handTapDownX, scrollY + handTapDownY) ?: return
        if (overview.editMode) overview.toggleSelection(box.index, pageSource.pageCount) else goToPage(box.index)
        return
    }
    val pairsWithPrevious = handFirstTapTime != 0L &&
        handTapDownTime - handFirstTapTime <= doubleTapTimeoutMs &&
        hypot(handTapDownX - handFirstTapX, handTapDownY - handFirstTapY) <= doubleTapSlopPx
    if (pairsWithPrevious) {
        handFirstTapTime = 0L // consume, so a third tap doesn't immediately re-fire
        onHandDoubleTap(handTapDownX)
    } else {
        handFirstTapTime = handTapDownTime
        handFirstTapX = handTapDownX
        handFirstTapY = handTapDownY
    }
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

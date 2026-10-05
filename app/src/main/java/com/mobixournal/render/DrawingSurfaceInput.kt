/**
 * [DrawingSurfaceView]'s touch state machine: the routing of every pointer event (`handleTouch`,
 * `handleHover`, `handleGenericMotion` — called from the thin framework overrides that must stay on
 * the view), the pointer-kind/tool classification behind it, the two-finger pan and its release,
 * and the hand-tool double-tap. Extensions on the view, so they read its state directly.
 */
package com.mobixournal.render

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
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
            momentum.stop(); beginPointer(event, 0); beginHandTap(event); armPageDrag(event)
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            handTapCandidate = false; cancelPageDrag(); onPointerDown(event)
        }
        MotionEvent.ACTION_MOVE -> {
            if (handTapCandidate) trackHandTapMove(event)
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
                erasing -> eraseMove(event)
                placing -> placeMove(event)
                backgroundSelecting -> backgroundSelectMove(event)
                gestures.resizing -> gestures.resizeSelect(event)
                gestures.rotating -> gestures.rotateSelect(event)
                gestures.moving -> {
                    dragLastX = event.x; dragLastY = event.y
                    updateDragAutoScroll(event.y)
                    gestures.moveSelect(event)
                }
                vspace.active -> vspace.move(event.y)
                gestures.banding -> {
                    // Dragging the marquee itself into an edge band scrolls the page too, so a
                    // selection can reach past the viewport without letting go and panning first.
                    dragLastX = event.x; dragLastY = event.y
                    updateDragAutoScroll(event.y)
                    gestures.bandMove(event)
                }
                textSelecting -> textSelectMove(event)
                splineDragging -> splineMove(event)
                (current != null || currentStrokes != null) -> extendStroke(event)
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
                captureReleaseVelocity(event); handleHandTapUp(event); endGesture()
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

/** The hover preview's state machine; null means "not ours" and falls through to [SurfaceView]. */
internal fun DrawingSurfaceView.handleHover(event: MotionEvent): Boolean? {
    tracePenMotion(event)
    trackBarrelClicks(event)
    if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
        barrelClicks.reset(); vendorClicks.reset(); barrelButton.reset()
    }
    val kind = pointerKindOf(event, 0)
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
    val kind = pointerKindOf(event, pointerIndex)
    // A finger laid on the guide manipulates it, whatever the active tool — the pen keeps
    // drawing against it meanwhile, exactly as you'd hold a real setsquare down and rule along it.
    if (kind == PointerKind.FINGER && guideDrag.begin(event, pointerIndex)) return
    // Play-object is a pure query — it never edits the document, so it short-circuits the whole
    // gesture machinery rather than earning a GestureIntent of its own.
    if (audioPlayMode) { audioTap(event, pointerIndex); return }
    val intent = InputClassifier.classify(kind, barrelButton.held, activeTool(), inputSettings)
    stylusOwner = (kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP) &&
        (intent == GestureIntent.DRAW || intent == GestureIntent.ERASE)
    when (intent) {
        GestureIntent.ERASE -> startErase(event, pointerIndex)
        GestureIntent.PLACE -> beginPlace(event, pointerIndex)
        GestureIntent.PAN -> beginScroll(event)
        GestureIntent.SELECT -> beginSelect(event)
        GestureIntent.BACKGROUND_SELECT -> beginBackgroundSelect(event)
        GestureIntent.SELECT_TEXT -> beginTextSelect(event)
        GestureIntent.VERTICAL_SPACE -> beginVerticalSpace(event)
        // The spline tool is laid down over many taps, so it gets its own gesture path.
        GestureIntent.DRAW ->
            if (shapeKind == ShapeKind.SPLINE) splineDown(event, pointerIndex)
            else startStroke(event, pointerIndex)
        GestureIntent.IGNORE -> Unit
    }
}

/**
 * A second pointer arrived. A stylus/eraser tip takes over any in-progress finger gesture (so a
 * palm that landed first can't block the pen); while a stylus already owns the gesture, extra
 * finger/palm pointers are ignored (palm rejection); otherwise two fingers pan.
 */
internal fun DrawingSurfaceView.onPointerDown(event: MotionEvent) {
    val idx = event.actionIndex
    val kind = pointerKindOf(event, idx)
    when {
        kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP -> {
            abandonInProgress()
            beginPointer(event, idx)
        }
        stylusOwner -> Unit // palm / finger resting while the pen writes: ignore
        else -> beginScroll(event) // ordinary two-finger pan
    }
}

/** If the pointer that lifted owns the draw/erase gesture, finish it; otherwise keep panning. */
internal fun DrawingSurfaceView.onPointerUp(event: MotionEvent) {
    guideDrag.end(event)
    val upId = event.getPointerId(event.actionIndex)
    if (upId == gesturePointerId && (current != null || erasing)) {
        endGesture()
        return
    }
    lastFocusY = focusY(event, skip = event.actionIndex)
    lastFocusX = focusX(event, skip = event.actionIndex)
    // The span also jumps when a finger leaves; re-baseline it next frame so the drop isn't read as
    // a pinch-out, mirroring the focus/velocity reset below.
    lastSpan = 0f
    // The focus jumps when a finger leaves, so restart the estimate from the new baseline —
    // otherwise that discontinuity would read as a huge phantom flick. A side effect is that a
    // two-finger release carries no momentum (its near-motionless single-finger tail is all that
    // survives the reset), which is the intended feel — only a one-finger pan flings.
    momentum.rebaseline(event.eventTime, lastFocusX, lastFocusY)
}

/** Drop any in-progress draw/erase/place/band/move without committing (a stylus is taking over). */
internal fun DrawingSurfaceView.abandonInProgress() {
    clearSpline()
    stopAutoScroll()
    current = null; currentStrokes = null; shaping = false; erasing = false; placing = false
    scrolling = false; textSelecting = false; backgroundSelecting = false; vspace.reset()
    gestures.reset()
}

internal fun DrawingSurfaceView.cancelGesture() {
    momentum.stop()
    stopAutoScroll()
    guideDrag.end(null)
    clearSpline()
    current = null; currentStrokes = null; shaping = false; scrolling = false; erasing = false; placing = false
    backgroundSelecting = false; gestures.reset(); gestureStartDoc = null
    textSelecting = false; vspace.reset()
    gesturePointerId = -1; stylusOwner = false
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

/** Map one event pointer's tool type to our device-independent [PointerKind]. */
internal fun DrawingSurfaceView.pointerKindOf(event: MotionEvent, pointerIndex: Int): PointerKind =
    when (event.getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> PointerKind.ERASER_TIP
        MotionEvent.TOOL_TYPE_FINGER -> PointerKind.FINGER
        else -> PointerKind.UNKNOWN
    }

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

/**
 * Treat a stylus barrel *button key* as the barrel modifier — the path a Bluetooth pen's button takes
 * on Android 14 and later, where button presses arrive as `KeyEvent`s as well as, or instead of,
 * `MotionEvent` button bits (see [BarrelButtonState]). Returns true when [event] was a barrel button,
 * so the caller can consume it rather than let it do something else.
 */
fun DrawingSurfaceView.onStylusButtonKey(event: KeyEvent): Boolean {
    // The event's own source plus the whole device's: a pen's button may be tagged on either, and the
    // broader check ([BarrelButtonState.isBarrelKey]) is what catches a vendor pen that reports its
    // button as an undocumented, characterless key.
    val source = event.source or (event.device?.sources ?: 0)
    val firmwareClick = isVendorKeycode(event.keyCode)
    if (!BarrelButtonState.isBarrelKey(event.keyCode, source, event.unicodeChar, firmwareClick)) return false
    when (event.action) {
        // Auto-repeat is dropped here rather than deeper in: a held Bluetooth key repeats, and every
        // repeat would otherwise read as a fresh click of the pen's button.
        KeyEvent.ACTION_DOWN ->
            if (event.repeatCount == 0) pressBarrelButton(event.eventTime, firmwareClick = firmwareClick)
        KeyEvent.ACTION_UP -> releaseBarrelButton()
    }
    return true
}

/**
 * True when Android has no name for [keyCode], so it can only be a **vendor** HID code and never a
 * real keyboard key. Android names every key code it defines (`KeyEvent.keyCodeToString` answers
 * `KEYCODE_<NAME>`), and falls back to the bare number for one it does not know — which is how the
 * Honor Choice Pencil's side button presents itself: key code `755`, source `SOURCE_KEYBOARD`, from a
 * device named "HONOR CHOICE Pencil" (that is the line the diagnostics panel reports). Treating those
 * as the barrel is what makes the button work on a pen whose firmware invented its own key code,
 * instead of the app having to learn one code per pen. `KEYCODE_UNKNOWN` itself is never a button.
 */
internal fun isVendorKeycode(keyCode: Int): Boolean {
    if (keyCode == 0) return false
    val name = KeyEvent.keyCodeToString(keyCode)
    return name == "KEYCODE_UNKNOWN" || name == keyCode.toString()
}

/**
 * A barrel press edge, however it was reported: latch it, repaint the preview, run the click action.
 *
 * [firmwareClick] marks a press that came from a pen whose firmware already did the recognising — an
 * unnamed vendor key code, which such pens send once for a whole physical double-click. This edge *is*
 * the gesture then: waiting for a second edge would mean waiting forever, which is exactly how the
 * button reads as dead. [VendorClickGate] keeps it to one action per gesture all the same, and two
 * further rules exist so that a press is never lost:
 *
 *  - The press does **not** depend on the latch *changing*: a pen that never sends the key up (or
 *    sends it where the app never sees it) would otherwise leave the latch held and silence every
 *    later press for good.
 *  - A click is a discrete command, not a held modifier, so it is **not** dropped while the pen is on
 *    the glass: the stroke drawn so far is finished first and then the action applies. A click made
 *    right after erasing, without lifting the pen, is exactly the click a user makes.
 */
internal fun DrawingSurfaceView.pressBarrelButton(eventTime: Long, firmwareClick: Boolean = false) {
    val changed = barrelButton.onKey(true)
    if (changed && hovering) render()
    if (firmwareClick) {
        if (!vendorClicks.fires(eventTime)) {
            notePenDebug("CLICK swallowed (same gesture)")
            return
        }
        if (current != null || erasing) endGesture()
        runBarrelClickAction()
        return
    }
    if (!changed) return
    // A double-click is a button-only gesture: with the tip down the button is the held modifier
    // instead, and firing an undo mid-stroke would be exactly wrong.
    if (current != null || erasing) return
    if (!barrelClicks.press(eventTime)) return
    runBarrelClickAction()
}

/**
 * Run the configured barrel click action and say so in the diagnostics panel: the event lines say
 * what arrived, this says what the app made of it.
 */
internal fun DrawingSurfaceView.runBarrelClickAction() {
    val action = inputSettings.barrelDoubleAction
    notePenDebug("CLICK -> " + action.name.lowercase().replace('_', ' '))
    runBarrelDoubleAction(action)
}

/** The barrel button was let go (a key-up): the held modifier is over. */
internal fun DrawingSurfaceView.releaseBarrelButton() {
    if (barrelButton.onKey(false) && hovering) render()
}

/** The on-screen tool collapsed to the classifier's [ActiveTool]. */
internal fun DrawingSurfaceView.activeTool(): ActiveTool = when {
    verticalSpaceMode -> ActiveTool.VERTICAL_SPACE
    placeKind != null -> ActiveTool.PLACE
    handMode -> ActiveTool.HAND
    backgroundSelectMode -> ActiveTool.BACKGROUND_SELECT
    textSelectMode -> ActiveTool.TEXT_SELECT
    selectMode -> ActiveTool.SELECT
    tool == Tool.ERASER -> ActiveTool.ERASER
    tool == Tool.HIGHLIGHTER -> ActiveTool.HIGHLIGHTER
    else -> ActiveTool.PEN
}

/** Feed one off-glass event's button state to [barrelClicks] and run the action it completes. */
internal fun DrawingSurfaceView.trackBarrelClicks(event: MotionEvent) {
    val changed = barrelButton.onMotion(event.buttonState, buttonKindOf(event))
    // Holding the barrel arms the eraser before the tip ever lands, so the hover preview has to
    // swap to the tip outline on the button edge — not on the first touch.
    if (changed && hovering) render()
    // Only a press *edge* is a click: a held button repeats its bit on every hover move.
    if (!changed || !barrelButton.held || !barrelClicks.press(event.eventTime)) return
    runBarrelDoubleAction(inputSettings.barrelDoubleAction)
}

/** Run the configured barrel double-click action. */
internal fun DrawingSurfaceView.runBarrelDoubleAction(action: BarrelDoubleAction) {
    when (action) {
        BarrelDoubleAction.NONE -> Unit
        BarrelDoubleAction.UNDO -> undo()
        BarrelDoubleAction.REDO -> redo()
        // TOGGLE_ERASER / TOGGLE_SELECT / TOGGLE_FULL_PAGE are applied by the editor, which owns the
        // UI state a toggle needs; the surface just reports the action.
        else -> onBarrelDoubleClick?.invoke(action)
    }
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
    // A fresh pan starts with no carried flick — otherwise a near-motionless release could keep a
    // latched velocity from the previous gesture (see [captureReleaseVelocity]).
    momentum.clearRelease()
    momentum.rebaseline(event.eventTime, lastFocusX, lastFocusY)
}

internal fun DrawingSurfaceView.doScroll(event: MotionEvent) {
    val fy = focusY(event, skip = -1)
    val fx = focusX(event, skip = -1)
    // Pan gain: 1 tracks the finger one-to-one, <1 pans slower, >1 faster, 0 freezes the document.
    scrollY = (scrollY + (lastFocusY - fy) * panSensitivity).coerceIn(0f, maxScrollY())
    scrollX = (scrollX + (lastFocusX - fx) * panSensitivity).coerceIn(0f, maxScrollX())
    lastFocusY = fy
    lastFocusX = fx
    // Two fingers also pinch-zoom: a change in span since the last frame scales zoom about the focus.
    val span = spanOf(event)
    if (lastSpan > DrawingSurfaceDefaults.PINCH_MIN_SPAN_PX && span > DrawingSurfaceDefaults.PINCH_MIN_SPAN_PX) zoomAbout(fx, fy, span / lastSpan)
    lastSpan = span
    momentum.track(event.eventTime, fx, fy)
    render()
}

// --- drag auto-scroll: keep scrolling while a selection is dragged into an edge band -------------

/**
 * Update the drag auto-scroll direction from a pointer's Y (view px): pushing into the top or bottom
 * edge band starts the page scrolling that way, and moving back out stops it. Idempotent, so it is
 * safe to call on every MOVE.
 */
internal fun DrawingSurfaceView.updateDragAutoScroll(viewY: Float) {
    val edgePx = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_EDGE_DP * resources.displayMetrics.density
    val dir = when {
        viewY > height - edgePx -> 1
        viewY < edgePx -> -1
        else -> 0
    }
    if (dir == dragAutoScrollDir) return
    dragAutoScrollDir = dir
    if (dir != 0) postAutoScroll() else stopAutoScroll()
}

/** Stop the drag auto-scroll repeat (a released/cancelled gesture, or the drag left the edge band). */
internal fun DrawingSurfaceView.stopAutoScroll() {
    dragAutoScrollDir = 0
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
 * One auto-scroll frame: scroll one step toward the edge the finger is in and re-apply the gesture at
 * the finger's last position — the moved selection, or the marquee being dragged out — so it rides the
 * scrolling sheet, then repost.
 */
internal fun DrawingSurfaceView.autoScrollFrame() {
    autoScrollPosted = false
    val dir = dragAutoScrollDir
    if (dir == 0) return
    val step = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_STEP_DP * resources.displayMetrics.density
    if (!scrollViewportBy(0f, dir * step)) {
        stopAutoScroll() // pinned at a bound: nothing left to scroll into
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
    val wasTransforming = gestures.resizing || gestures.rotating
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
    if (wasScrolling) momentum.launch(panSensitivity)
}

/** Record one undo step if this gesture actually changed the document. */
internal fun DrawingSurfaceView.finishGesture() {
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
        if (overview.editMode) overview.toggleSelection(box.index, doc.pages.size) else goToPage(box.index)
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

package com.mobixournal.render

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * The **long press that selects PDF words**, with no tool to pick first.
 *
 * Reading a PDF used to mean leaving the pen: the text slot was one of the tools, so a reader annotating a
 * paper had to tap the rail, select the words, copy, and tap back — a tool switch in the middle of the one
 * gesture that should be as direct as a finger on paper. A finger held still on a word now selects it, and the
 * tool the pen is drawing with is untouched by the gesture.
 *
 * It is a hold, not a tap, because the same finger already does other things on a page: dragging it pans,
 * two of them pinch, and a long press on an element selection lifts it as a drag. So the press is a
 * **candidate** for [TapClassifier.longPressMs] and nothing more until the timer fires:
 * - **Movement is a pan.** Past the touch slop the candidate is gone and the gesture that already began —
 *   the pan — carries on untouched, with no interruption at the moment the finger starts moving.
 * - **A second finger is a pinch.** It cancels the candidate, so two fingers still zoom rather than selecting
 *   the word under the first.
 * - **A pen is the pen.** A stylus or eraser landing cancels the candidate and keeps its tool route, so a palm
 *   that landed first cannot make the pen select text instead of drawing.
 * - **An owned gesture keeps it.** A foreground grip, an in-progress placement, a guide being dragged, the
 *   background marquee, the vertical-space grab line, or an object selection's own lift are all decided before
 *   this candidate even arms — the regardless-of-active-tool rule applies to an otherwise unclaimed word.
 *
 * The timer is a [Runnable] like [SelectionDragOut]'s, and the decision itself is [pdfTextPressAllowed], a pure
 * function of what is going on, so the priority is tested on the JVM (`PdfLongPressTest`) instead of being
 * inferred from a gesture that happened to work on a tablet.
 */
internal class PdfTextPress {
    var armed = false

    /** Where the finger went down, in view px — the point the selection is anchored at when the timer fires. */
    var downX = 0f
    var downY = 0f

    /** The pointer the candidate belongs to, so another finger's move cannot cancel it by accident. */
    var pointerId = -1

    /** The pending timer, so a release, a second finger or a pen can take it back. */
    var fire: Runnable? = null

    /**
     * Notified when the hold fired and the point hit no word (a scan, a page still being read, a failed read).
     * The pane answers with its availability pill, so the direct entry never fails silently — carried here, with
     * the press's own state, rather than as one more mutable member on the view
     * (`PackageStructureTest` caps those).
     */
    var onMissed: (() -> Unit)? = null
}

/**
 * Whether a press may become a PDF text selection, given everything that could own the gesture instead.
 *
 * Read-only documents are **not** excluded: selecting words is reading, and Copy writes nothing, so the
 * route runs ahead of the edit guard on purpose. [onPdfWord] is the hit test's own answer — a scan, a page
 * still being read or a press between words never arms, so a long press there is an ordinary hold on the page
 * and never a selection of the nearest remote word.
 */
internal fun pdfTextPressAllowed(
    kind: PointerKind,
    pointerCount: Int,
    playMode: Boolean,
    guideOwned: Boolean,
    transformOwned: Boolean,
    placementOwned: Boolean,
    backgroundOwned: Boolean,
    insideObjectSelection: Boolean,
    onPdfWord: Boolean,
): Boolean = when {
    pointerCount != 1 -> false
    kind != PointerKind.FINGER -> false
    playMode -> false
    guideOwned -> false
    transformOwned -> false
    placementOwned -> false
    backgroundOwned -> false
    insideObjectSelection -> false
    !onPdfWord -> false
    else -> true
}

/**
 * Arm the long press for a finger that landed on a PDF word.
 *
 * Called after the press has been classified, so the gesture flags it reads are the ones this very down
 * produced: a grip that took the touch, a placement that began, an object drag already under way.
 */
internal fun DrawingSurfaceView.armPdfTextPress(event: MotionEvent) {
    cancelPdfTextPress()
    // A finger on the guide holds it; the hold is the guide's (checked below through `guideOwned` too, but a
    // touch that began a guide drag has no word under it worth waiting for).
    val index = pdfTextIndex ?: return
    val pageIndex = layout.pageAt(event.x + scrollX, event.y + scrollY)?.index ?: return
    val box = layout.boxes.getOrNull(pageIndex) ?: return
    val sheet = PdfSheetRef.of(pageAt(pageIndex) ?: return) ?: return
    val x = box.toPtX(event.x, scrollX)
    val y = box.toPtY(event.y, scrollY)
    val word = index.hitWord(
        sheet.pageNo,
        x,
        y,
        PdfHitTolerance.ofScreenPx(
            PdfHitTolerance.PRESS_SLOP_DP * resources.displayMetrics.density.toDouble(),
            box.scale.toDouble(),
        ),
    )
    val allowed = pdfTextPressAllowed(
        kind = pointerKindOf(event, 0),
        pointerCount = event.pointerCount,
        playMode = audioPlayMode,
        guideOwned = guideDrag.dragging,
        transformOwned = gestures.moving || gestures.resizing || gestures.rotating ||
            gestures.slidingParam || gestures.draggingDivider || gestures.draggingVertex || gestures.banding,
        placementOwned = placing,
        backgroundOwned = backgroundSelecting || vspace.active,
        insideObjectSelection = selectMode && fingerInsideObjectSelection(event.x, event.y),
        onPdfWord = word != null,
    )
    if (!allowed) return
    val press = pdfTextPress
    press.armed = true
    press.downX = event.x
    press.downY = event.y
    press.pointerId = event.getPointerId(0)
    val runnable = Runnable { firePdfTextPress() }
    press.fire = runnable
    postDelayed(runnable, taps.longPressMs)
}

/** A touch that travels, or a second finger, ends the wait: the gesture was a pan or a pinch. */
internal fun DrawingSurfaceView.trackPdfTextPress(event: MotionEvent) {
    val press = pdfTextPress
    if (!press.armed) return
    if (event.pointerCount > 1 || hypot(event.x - press.downX, event.y - press.downY) > taps.touchSlopPx) {
        cancelPdfTextPress()
    }
}

/** Disarm any pending press (release, second finger, pen arrival, a cancelled gesture). */
internal fun DrawingSurfaceView.cancelPdfTextPress() {
    val press = pdfTextPress
    press.fire?.let(::removeCallbacks)
    press.fire = null
    press.armed = false
}

/**
 * The timer fired: start the selection, having first **dropped** whatever the press began.
 *
 * A finger down starts the active tool's gesture — a pan, or a stroke when finger drawing is on — and neither
 * may survive the long press: the pan would move the page under the words and the stroke would leave a dot and
 * an undo step. Neither is committed here, so nothing of it reaches the document.
 */
private fun DrawingSurfaceView.firePdfTextPress() {
    val press = pdfTextPress
    press.fire = null
    press.armed = false
    abandonInProgress()
    if (!beginTextSelectAt(press.downX, press.downY)) {
        // Nothing under the finger: the pane says why (reading, no text on this page, or a failed read with a
        // Try again) instead of the hold looking like a gesture the app did not hear.
        press.onMissed?.invoke()
        return
    }
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

/**
 * True when the point (view px) lies inside the outline of the object selection — the selection's own
 * long-press lift ([armSelectionDrag]) owns a finger there, so a PDF word beneath it is not this candidate's.
 */
internal fun DrawingSurfaceView.fingerInsideObjectSelection(x: Float, y: Float): Boolean {
    if (!selectMode) return false
    val sel = selection ?: return false
    val box = layout.boxes.getOrNull(sel.pageIndex) ?: return false
    val page = pageAt(sel.pageIndex) ?: return false
    val bounds = SelectionTester.boundsOf(page, sel.refs) ?: return false
    val px = box.toPtX(x, scrollX)
    val py = box.toPtY(y, scrollY)
    return px >= bounds.left && px <= bounds.right && py >= bounds.top && py <= bounds.bottom
}

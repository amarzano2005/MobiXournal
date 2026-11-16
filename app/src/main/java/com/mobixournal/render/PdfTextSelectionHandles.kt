/**
 * [DrawingSurfaceView]'s PDF text selection **handles**: where the two dots sit, the press that grabs one, the
 * drag that moves the passage's end, and the dots themselves.
 *
 * Split out of `DrawingSurfaceSelection.kt` and `DrawingSurfacePaint.kt` because a handle is a gesture and a mark
 * at once — the geometry the paint draws and the target the touch answers have to be the same call — and because
 * both of those files are at `SourceStructureTest`'s budget. Extensions on the view, so they read its state.
 */
package com.mobixournal.render

import android.graphics.Canvas
import android.view.MotionEvent

/**
 * The grab a touch is holding, as one object the view owns.
 *
 * A field of its own rather than a nullable `var` on [DrawingSurfaceView]: the canvas's mutable state is being
 * drained into objects that own it, and a handle's grab is a small cluster with a lifetime (down to up) of its
 * own. Null means no handle is being dragged.
 */
internal class PdfTextHandleDragState {
    var grab: PdfTextHandleGrab? = null
}

/**
 * The two **endpoint handles** of the current selection, in this view's own px.
 *
 * They come from [PdfTextHandles] through the page's own box, so a dot sits on the letter it would move and
 * follows a zoom and a scroll with the sheet. The dot's size is a screen quantity and is not part of this
 * value: [drawTextSelectionHandles] draws it, and [beginTextHandleDrag] hit-tests it, both at the same radius.
 */
internal fun DrawingSurfaceView.textSelectionHandlePoints(): List<PdfTextHandleScreen> {
    val index = pdfTextIndex ?: return emptyList()
    if (!hasTextSelection()) return emptyList()
    val box = layout.boxes.getOrNull(textSelPage) ?: return emptyList()
    val sheet = textSelectionSheet() ?: return emptyList()
    return PdfTextHandles.points(index, PdfTextRange(sheet, textSelAnchor, textSelFocus)).map {
        PdfTextHandles.screen(
            it,
            box.scale.toDouble(),
            (box.leftPx - scrollX).toDouble(),
            (box.topPx - scrollY).toDouble(),
        )
    }
}

/**
 * A press on one of the selection's handles: grab it and own the gesture, or leave the press to its tool.
 *
 * This is asked **before** the press is classified, so a handle's target takes precedence over the pan, the
 * tool and another selection — the same precedence the long press takes, and for the same reason: adjusting a
 * passage is not drawing on it. What the press began (a pan, a stroke) is abandoned without committing, so the
 * grab costs no page edit and no undo step.
 *
 * The grab radius is a screen size converted at this page's zoom ([PdfTextHandles.touchRadiusPt]), bounded in
 * page points, so the target is the same finger-sized grip whether the page is shown at 30 % or 300 %. A press
 * outside both dots answers false and the gesture carries on untouched.
 */
internal fun DrawingSurfaceView.beginTextHandleDrag(event: MotionEvent): Boolean {
    val index = pdfTextIndex ?: return false
    if (!hasTextSelection()) return false
    val box = layout.boxes.getOrNull(textSelPage) ?: return false
    val sheet = textSelectionSheet() ?: return false
    val grab = PdfTextHandles.grab(
        index,
        PdfTextRange(sheet, textSelAnchor, textSelFocus),
        box.toPtX(event.x, scrollX),
        box.toPtY(event.y, scrollY),
        PdfTextHandles.touchRadiusPt(
            PdfTextHandles.touchRadiusPx(resources.displayMetrics.density).toDouble(),
            box.scale.toDouble(),
        ),
    ) ?: return false
    scroll.active = false; erasing = false; placing = false; ink.current = null
    textHandleDrag.grab = grab
    // The capsule waits while a handle moves the range, exactly as it waits through the drag that made it.
    textSelecting = true
    render()
    return true
}

/**
 * Drag the grabbed handle to the word under the touch. Nothing else moves.
 *
 * The grabbed end follows the pointer word by word in reading order, so crossing the other end reverses the
 * passage while the grabbed dot stays the one in the hand ([PdfTextHandles.drag]); the wash and the report
 * update on the same frame, with no animation of the passage as a block.
 */
internal fun DrawingSurfaceView.textHandleMove(event: MotionEvent) {
    val grab = textHandleDrag.grab ?: return
    val index = pdfTextIndex ?: return
    if (!hasTextSelection()) return
    val box = layout.boxes.getOrNull(textSelPage) ?: return
    val sheet = textSelectionSheet() ?: return
    val moved = PdfTextHandles.drag(
        index,
        PdfTextRange(sheet, textSelAnchor, textSelFocus),
        grab,
        box.toPtX(event.x, scrollX),
        box.toPtY(event.y, scrollY),
    )
    if (moved.anchor != textSelAnchor || moved.focus != textSelFocus) {
        textSelAnchor = moved.anchor
        textSelFocus = moved.focus
        render()
    }
}

/** The handle was let go: the range stays where it is, and the capsule becomes due on this release. */
internal fun DrawingSurfaceView.endTextHandleDrag() {
    if (textHandleDrag.grab == null) return
    textHandleDrag.grab = null
    textSelecting = false
    render()
}

/**
 * The two **dots** that adjust the selection, at a constant screen size: one at the passage's reading start and
 * one at its end.
 *
 * The radius is [PdfTextHandles.drawnRadiusPx] — screen dp at this view's density, never scaled by the page's
 * zoom — so a passage shown at 30 % keeps a dot a finger can see and take. Each dot wears the sheet's white as
 * a halo ([CanvasChrome.pinHalo], the same treatment a component's pin gets) so it reads on black ink, on a
 * grey scan and on the theme's dark paper alike; the dot itself is the selection accent, opaque, because it is
 * a control rather than a wash.
 */
internal fun DrawingSurfaceView.drawTextSelectionHandles(canvas: Canvas) {
    val points = textSelectionHandlePoints()
    if (points.isEmpty()) return
    val density = resources.displayMetrics.density
    val radius = PdfTextHandles.drawnRadiusPx(density)
    val halo = radius + 2f * density
    for (point in points) {
        canvas.drawCircle(point.x, point.y, halo, chrome.pinHalo)
        canvas.drawCircle(point.x, point.y, radius, chrome.textSelectHandle)
    }
}

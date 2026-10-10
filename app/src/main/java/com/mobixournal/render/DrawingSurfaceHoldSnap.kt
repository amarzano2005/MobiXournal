/**
 * **Hold-to-snap**: rest the stylus tip on the canvas for a moment after drawing, and the
 * in-progress freehand stroke is replaced by recognised geometry — a straight segment, an arc, a
 * triangle, a rectangle — *before* lift-off, so the user watches the snap happen and can keep
 * drawing if it guessed wrong.
 *
 * It is the same recogniser the on-lift path uses ([ShapeRecognizer], and it shares that path's
 * `recognizeShapes` setting), just triggered by stillness rather than by release: each time the
 * stroke advances the tip by a slop, a short timer is armed; if the tip rests for [HOLD_SNAP_MS]
 * the timer fires and rewrites the stroke in place. Any further sample moves the tip, re-arms the
 * timer, and if the snapped geometry is then extended the recogniser simply runs again on the new
 * samples — a hold is a *preview* of the snap, not a lock.
 *
 * Extensions on [DrawingSurfaceView], so they read the stroke state directly; the timer fields live
 * on the view in `DrawingSurfaceView.kt`.
 */
package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import kotlin.math.hypot

/** How long the stylus must rest before an in-progress stroke snaps, in milliseconds. */
private const val HOLD_SNAP_MS = 500L

/** Movement (view px) that counts as "still moving" and re-arms the hold timer. */
private const val HOLD_SNAP_SLOP_PX = 6.0

/**
 * Called as the stroke advances: re-arm the hold timer whenever the tip has moved a slop since the
 * last sample. Idempotent, so it is safe on every MOVE.
 */
internal fun DrawingSurfaceView.maybeArmHoldSnap(points: List<StrokePoint>) {
    if (!recognizeShapes || shaping) return
    val last = points.lastOrNull() ?: return
    val scale = (layout.boxes.getOrNull(currentPage)?.scale ?: zoom).coerceAtLeast(0.01f)
    val slopPt = HOLD_SNAP_SLOP_PX / scale
    if (hypot(last.x - holdAnchorX, last.y - holdAnchorY) >= slopPt) {
        holdAnchorX = last.x
        holdAnchorY = last.y
        armHoldSnap()
    }
}

/** (Re)start the hold timer. Caller has confirmed the tip is still. */
internal fun DrawingSurfaceView.armHoldSnap() {
    cancelHoldSnap()
    val r = Runnable { fireHoldSnap() }
    holdSnapRunnable = r
    postDelayed(r, HOLD_SNAP_MS)
}

/** Drop any pending hold timer (a move, a commit, or a cancelled gesture). */
internal fun DrawingSurfaceView.cancelHoldSnap() {
    holdSnapRunnable?.let { removeCallbacks(it) }
    holdSnapRunnable = null
}

/**
 * The tip has rested long enough: replace the in-progress freehand stroke with recognised geometry,
 * if the recogniser finds any. Runs on the UI thread, so it can rewrite [DrawingSurfaceView.current]
 * safely; a stroke that was already committed, or a shape/table gesture, is left alone.
 */
internal fun DrawingSurfaceView.fireHoldSnap() {
    holdSnapRunnable = null
    if (!recognizeShapes || shaping) return
    if (tool != Tool.PEN && tool != Tool.HIGHLIGHTER) return
    val pts = current ?: return
    if (pts.size < 3) return
    val shape = ShapeRecognizer.recognize(pts, pts.map { it.width }.average()) ?: return
    current = ArrayList(shape)
    holdSnapped = true
    render()
}

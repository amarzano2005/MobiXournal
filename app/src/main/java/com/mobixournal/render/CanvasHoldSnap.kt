/**
 * **Hold-to-snap**: rest the stylus tip on the canvas for a moment after drawing, and the in-progress
 * freehand stroke is replaced by recognised geometry — a straight segment, an arc, a triangle, a
 * rectangle — *before* lift-off, so the user watches the snap happen and can keep drawing if it guessed
 * wrong.
 *
 * It is the same recogniser the on-lift path uses ([ShapeRecognizer], through [ShapeDwell.figureFor]),
 * just triggered by stillness rather than by release, and it has its **own** switch ([enabled], the
 * `recognizeOnHold` setting) because recognising both ways would re-read a clean figure as ink:
 * each time the stroke advances the tip by a slop, a short timer is armed; if the tip rests for
 * [ShapeDwell.MS] the timer fires and the recognised figure is **painted in place of the stroke**.
 *
 * A hold is a preview, not a rewrite. The samples stay exactly as drawn — [figure] is drawn *over* them
 * for as long as the stroke has not grown ([preview]) — so a hand that keeps going gets its own ink back
 * rather than a stroke made of clean geometry with raw samples stitched onto its end. Rewriting the
 * stroke instead, which is what this used to do, also made the commit re-read the recogniser's own
 * output: a held square is four corners plus the closing repeat, too few points for the polygon fit but a
 * perfectly round set for the circle fit — so a square came back a *circle*. The figure the hold found is
 * therefore carried to the commit ([take]).
 *
 * This is the state the canvas used to spread over six members that every `DrawingSurface*.kt` file could
 * reach: the timer, the anchor it re-arms from, the figure, the sample count it was found at, and the
 * "already snapped" flag. They belong to one subject, so they live in one object, which the canvas
 * constructs and hands its four inputs — where to schedule, what the stroke is, whether snapping applies,
 * and how to repaint. Everything except those inputs is testable on the JVM with a fake clock
 * (`HoldSnapTest`).
 */
package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot

internal class HoldSnap(
    /** Arm a timer for [delayMs] — the view's `postDelayed`. */
    private val schedule: (Runnable, Long) -> Unit,
    /** Drop an armed timer — the view's `removeCallbacks`. */
    private val unschedule: (Runnable) -> Unit,
    /** Whether the freehand path may snap at all: no shape gesture is in flight. */
    private val freehand: () -> Boolean,
    /** Whether the tool in force lays ink a figure may replace (pen or highlighter). */
    private val inking: () -> Boolean,
    /** The stroke in progress, read when the timer fires. */
    private val stroke: () -> List<StrokePoint>?,
    /** Repaint: what is painted changed. */
    private val onSnapped: () -> Unit,
) {

    /**
     * The user's `recognizeOnHold` switch: a hold is armed only while this is on, and switching it off
     * drops whatever the hold was carrying. The lift recogniser (`recognizeShapes`) is its exclusive
     * twin — the settings turn one off when the other goes on — so a stroke is never recognised twice.
     */
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) reset()
        }

    /** Where the tip stood when the timer was last armed, in page pt: a move out of its slop re-arms. */
    var anchorX = 0.0
        private set
    var anchorY = 0.0
        private set

    /** True once the stroke in progress was snapped by a hold, so the commit keeps the clean shape. */
    var snapped = false
        private set

    /** The hold's figure: painted instead of the ink, and the shape kept at lift-off if still. */
    var figure: RecognisedFigure? = null
        private set

    /** The stroke's sample count when [figure] fired; a grown stroke is no longer that figure. */
    private var samples = 0

    /**
     * The timer waiting to fire, or null when the tip is not being watched. Exposed because a test that
     * drives hybrid input by hand runs this callback itself: an unattached view only queues its post.
     */
    var pending: Runnable? = null
        private set

    /**
     * A stroke has started at [points]: anchor the hold at its last sample and arm the timer, so a press
     * that never moves still snaps.
     */
    fun start(points: List<StrokePoint>?) {
        anchorX = points?.lastOrNull()?.x ?: 0.0
        anchorY = points?.lastOrNull()?.y ?: 0.0
        if (mayHold()) arm()
    }

    /**
     * Called as the stroke advances: re-arm the timer whenever the tip has moved a slop since the last
     * sample. Idempotent, so it is safe on every MOVE.
     */
    fun advance(points: List<StrokePoint>) {
        // The pen has drawn on since the snap: the figure on screen was a preview of a stroke that no
        // longer exists, so the user's own ink comes back and the timer starts over.
        if (figure != null && points.size != samples) {
            figure = null
            snapped = false
        }
        if (!mayHold()) return
        val last = points.lastOrNull() ?: return
        if (hypot(last.x - anchorX, last.y - anchorY) >= ShapeDwell.SLOP_PT) {
            anchorX = last.x
            anchorY = last.y
            arm()
        }
    }

    /**
     * Drop everything the hold was carrying: the timer, the figure and the snapped flag. A cancelled
     * gesture, an abandoned stroke and a commit all end the same way.
     */
    fun reset() {
        cancel()
        snapped = false
        figure = null
    }

    /**
     * End the hold at lift-off, returning the figure the commit keeps — but only while the stroke has not
     * grown since it fired, because a stroke the user carried on drawing is theirs again and must be
     * re-read by the recogniser.
     */
    fun take(rawSize: Int): RecognisedFigure? {
        cancel()
        return figure?.takeIf { samples == rawSize }.also { reset() }
    }

    /** The figure to paint over a stroke of [count] samples, or null when the stroke has grown past it. */
    fun preview(count: Int): RecognisedFigure? = figure?.takeIf { samples == count }

    /** Whether this gesture may snap by holding: the user's switch, and no shape gesture in flight. */
    private fun mayHold(): Boolean = enabled && freehand()

    /** (Re)start the hold timer; the caller has confirmed the tip is still. */
    private fun arm() {
        cancel()
        val runnable = Runnable { fire() }
        pending = runnable
        schedule(runnable, ShapeDwell.MS)
    }

    /** Drop any pending hold timer (a move, a commit, or a cancelled gesture). */
    private fun cancel() {
        pending?.let(unschedule)
        pending = null
    }

    /**
     * The tip has rested long enough: find the figure in the in-progress stroke, if the recogniser finds
     * one. A stroke that was already committed, or a shape/table gesture, is left alone.
     */
    private fun fire() {
        pending = null
        if (!mayHold() || !inking()) return
        val points = stroke() ?: return
        if (points.size < 3) return
        val found = ShapeDwell.figureFor(points, points.map { it.width }.average()) ?: return
        // The raw samples stay exactly as drawn; only what is *painted* changes.
        figure = found
        samples = points.size
        snapped = true
        onSnapped()
    }
}

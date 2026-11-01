/**
 * The gesture in flight as a **transaction over the document**: the baseline it opened on, the pointer
 * that owns it, and the two answers a gesture has when it ends — [commit] (an undo step) or [abort]
 * (nothing to undo).
 *
 * It exists because those four facts used to be four `var`s on [DrawingSurfaceView] kept in step by
 * memory: the baseline was whichever assignment ran last, the reset order was a convention, and "one
 * gesture is one undo step" was a comment. Here it is one state with two exits, so the rule is a
 * method and
 * [GestureSessionTest] can state it without a `View`, a `MotionEvent` or an emulator.
 *
 * What it deliberately does **not** own: the *mode* flags of the gesture (`erasing`, `placing`, the
 * marquee, the shape draft). Those describe what the gesture is doing and are read by the painters and
 * the [GestureRouter] on every frame; the transaction only cares when it began and how it ended.
 */
package com.mobixournal.render

import com.mobixournal.format.model.Document

internal class GestureSession {

    /**
     * The document as it was when the open gesture began — the snapshot [commit] hands back to the
     * history. Null means no gesture is open.
     */
    var baseline: Document? = null
        private set

    /** Pointer id owning the open gesture, so a resting palm can't perturb it. -1 when none is open. */
    var pointerId: Int = -1

    /** True while a stylus/eraser tip owns the open gesture (drives palm rejection). */
    var penOwned: Boolean = false

    /** True while the open gesture's samples are fed to the shared [InkPointerRouter]. */
    var routed: Boolean = false

    /** Whether a gesture is open — it has a baseline to commit or abort. */
    val open: Boolean get() = baseline != null

    /**
     * Open the gesture on [document].
     *
     * A gesture that is **already** open keeps the baseline it opened with, so a second thing that
     * happens inside the same gesture — the barrel turning a stroke into an erase mid-gesture — stays
     * one undo step instead of resetting the baseline to the half-edited document. Returns whether
     * this call opened it.
     */
    fun open(document: Document): Boolean {
        if (baseline != null) return false
        baseline = document
        return true
    }

    /**
     * End the gesture and answer the snapshot the history should record: **null** when the document
     * never changed under it, so a tap that wrote nothing leaves no empty undo step. Either way the
     * gesture is closed — owner cleared, router flag cleared — so nothing of it reaches the next one.
     */
    fun commit(document: Document): Document? {
        val start = baseline ?: return null
        reset()
        return if (document === start) null else start
    }

    /** End the gesture with nothing to undo: a cancelled event, or a document that replaced the one it opened on. */
    fun abort() = reset()

    private fun reset() {
        baseline = null
        pointerId = -1
        penOwned = false
        routed = false
    }
}

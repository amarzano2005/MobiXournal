/**
 * The press of a live **move**, kept as the little its release needs to read it: did it go down on the
 * element's **ink**, and did the hand ever travel?
 *
 * A press inside the selection's outline is not the same as a press on the element: the outline encloses air
 * — a figure's box is mostly empty paper — and the press that lands in that air begins a move all the same.
 * Held and dragged it carries the element; **still**, it is a tap on nothing, and its release lets go of the
 * selection ([SelectionGestureController.commitMove]) instead of holding it. The marquee already answered
 * that outside the outline (`beginBand` clears before it starts tracing); this answers it inside.
 *
 * Plain floats and no Android type, so the reading is a function of two numbers rather than of a gesture.
 */
package com.mobixournal.render

import kotlin.math.hypot

internal class MovePress {

    /** Whether the press went down on an element — judged by the caller's pick, not by the outline. */
    var onInk = true
        private set

    /** Whether the hand travelled past the marquee's own tap slop since the press. */
    var travelled = false
        private set

    private var downX = 0f
    private var downY = 0f

    /** A press went down at ([atX], [atY]) in view px; [onInk] is what the page held under it. */
    fun begin(atX: Float, atY: Float, onInk: Boolean) {
        downX = atX
        downY = atY
        this.onInk = onInk
        travelled = false
    }

    /** Follow the pointer: any travel past the tap slop makes this a drag, not a tap. */
    fun track(atX: Float, atY: Float) {
        if (hypot(atX - downX, atY - downY) > DrawingSurfaceDefaults.TAP_SLOP_PX) travelled = true
    }

    /** A press on paper that holds nothing, released without travel: there is nothing to hold on to. */
    val tapOnNothing: Boolean get() = !onInk && !travelled
}

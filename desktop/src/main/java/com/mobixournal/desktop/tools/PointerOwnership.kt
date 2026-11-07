package com.mobixournal.desktop.tools

import androidx.compose.runtime.mutableStateOf

/**
 * **Who owns the pointer**: a surface shown *over* the page reports in while it is up, and the canvas stands
 * down.
 *
 * Compose delivers a press to every node under the pointer, not only to the topmost one — and the page reads
 * its own presses with `requireUnconsumed = false` on purpose, because a contact the ring already answered is
 * still the contact that arrived ([com.mobixournal.desktop.sheet.pageGestureLoop]). Consuming the event above
 * it therefore changed nothing: a panel that merely *covers* the sheet still handed it the click, and a click
 * on a Settings row also inked the page behind it.
 *
 * So the layer that has the pointer says so, and the canvas **drops its input chain** while it does
 * (`SheetCanvas`) — no press handler rather than a handler that refuses, one rule for every tool. The gesture
 * reads it again as it starts, for the frame between a surface appearing and the canvas recomposing. It is
 * **counted** rather than a flag: a surface can be shown from inside another (a dialog over the panel), and
 * the page must stay inert until the last one is gone.
 *
 * The count is a snapshot state, because the canvas's decision is made in composition: a plain `Int` would
 * silence the gesture and leave the surface's own presses unanswered, since nothing would recompose.
 */
internal object PointerOwnership {

    private val holders = mutableStateOf(0)

    /** True while nothing is over the page: a press here is the reader's drawing, not a surface's. */
    val pageIsLive: Boolean get() = holders.value == 0

    /** The surface that just appeared takes the pointer. Pair it with [release]. */
    fun take() {
        holders.value++
    }

    /** The surface closed: the page takes the pointer back, unless another surface still holds it. */
    fun release() {
        holders.value = (holders.value - 1).coerceAtLeast(0)
    }
}

package com.mobixournal.desktop

/**
 * The bites of an **eraser drag**, gathered into one page edit per frame.
 *
 * A mouse or a stylus reports several moves per displayed frame, and every bite used to be its own edit:
 * a page copy, the erase sweep and — the expensive half — a re-record of the page's whole ink picture,
 * each of which the next move then threw away. Rubbing out a dense sheet therefore paid for two or three
 * pages per frame, which is the lag a hand feels as the eraser "not keeping up".
 *
 * Gathering the bites costs nothing and changes nothing about what is erased: the points are applied in
 * order, one after another, inside a single `editPage`. The first bite of a drag is applied **at once**,
 * so the eraser starts under the pen and not a frame later.
 *
 * Pure and clock-injected, so the policy is a JVM test rather than something to feel for with a pen.
 */
internal class EraseBatch(
    /** How long a batch is gathered for: one frame at the tablet's 120 Hz target. */
    private val frameNanos: Long = FRAME_NANOS,
) {
    private val pending = mutableListOf<Pair<Double, Double>>()
    private var lastFlush = 0L

    /**
     * Queue a bite at ([x], [y]) page points, at [nowNanos].
     *
     * True when the queued bites are due — the caller applies them as one edit. The first bite always is:
     * a drag begins with a press, and a press must leave its mark immediately.
     */
    fun add(x: Double, y: Double, nowNanos: Long): Boolean {
        pending += x to y
        if (lastFlush == 0L || nowNanos - lastFlush >= frameNanos) {
            lastFlush = nowNanos
            return true
        }
        return false
    }

    /** The bites to apply now, oldest first — the batch is emptied by this call. */
    fun drain(): List<Pair<Double, Double>> {
        if (pending.isEmpty()) return emptyList()
        val out = pending.toList()
        pending.clear()
        return out
    }

    /** Whether anything is waiting to be swept. */
    fun isEmpty(): Boolean = pending.isEmpty()

    /** Forget what was gathered: the gesture was cancelled, so nothing of it is written. */
    fun clear() {
        pending.clear()
        lastFlush = 0L
    }

    companion object {
        /** 120 Hz — the refresh rate `docs/desktop-workstation.md` targets, and the tightest case. */
        const val FRAME_NANOS: Long = 1_000_000_000L / 120L
    }
}

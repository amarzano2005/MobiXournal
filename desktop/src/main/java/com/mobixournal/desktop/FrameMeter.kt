package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * **How long a frame takes**, measured at the only place that knows: the paint that just happened.
 *
 * The window already reports the display's rate and the budget one frame of it allows
 * ([refreshLabel]) — a ceiling, not an answer. A loop that paints every other frame on a 120 Hz panel is
 * at 60, and nothing on screen said so: the number existed only in the watchdog's file under `--diagnose`.
 * This keeps the measured interval beside the budget, so "capped" and "smooth" stop being a feeling.
 *
 * It is an **exponential average**, not the last frame: a single slow frame (an image decoding, a save
 * landing) would otherwise flash a number nobody could act on, and the complaint this answers is a steady
 * one. An interval longer than [IDLE_GAP_MS] is a window that was doing nothing, not a frame, so it is
 * dropped — idling at 0.2 fps must not average itself into a paint time.
 *
 * The published value is a snapshot state because the status bar reads it, and it is only written every
 * [PUBLISH_MS]: a state write per frame would recompose the status bar sixty times a second to change a
 * digit.
 */
internal object FrameMeter {

    /** How often the published average is refreshed; the measurement itself runs every frame. */
    private const val PUBLISH_MS = 250L

    /** An interval beyond this is idle time, not a frame. */
    private const val IDLE_GAP_MS = 500.0

    /** The smoothing: a tenth of the new interval, so a second of frames is the shape of the answer. */
    private const val WEIGHT = 0.1

    private var lastNanos = 0L
    private var publishedNanos = 0L
    private var average = 0.0

    /** The paint time per frame in milliseconds, or 0 before the second frame. */
    var averageMs by mutableStateOf(0.0)
        private set

    /**
     * Record a painted frame at [now] (nanoseconds, `System.nanoTime`'s clock).
     *
     * Called from the canvas's own paint, next to the watchdog's counter, so the number the status bar
     * shows is the frame the reader's eye saw rather than a timer's guess.
     */
    fun painted(now: Long = System.nanoTime()) {
        val previous = lastNanos
        lastNanos = now
        if (previous == 0L) return
        val intervalMs = (now - previous) / 1_000_000.0
        if (intervalMs <= 0.0 || intervalMs > IDLE_GAP_MS) return
        average = if (average <= 0.0) intervalMs else average * (1.0 - WEIGHT) + intervalMs * WEIGHT
        if (publishedNanos == 0L || now - publishedNanos >= PUBLISH_MS * 1_000_000L) {
            publishedNanos = now
            averageMs = average
        }
    }

    /** Forget the last frame — a new document, or a window that was left idle for a while. */
    fun reset() {
        lastNanos = 0L
        publishedNanos = 0L
        average = 0.0
        averageMs = 0.0
    }
}

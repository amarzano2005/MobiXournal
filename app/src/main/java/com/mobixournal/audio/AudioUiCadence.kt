package com.mobixournal.audio

/** The canvas keeps vsync; player chrome publishes at most ten moving positions per second. */
internal class AudioUiCadence(private val intervalNs: Long = 100_000_000L) {
    private var last: Long? = null
    fun publish(nowNs: Long, playing: Boolean): Boolean {
        val before = last
        if (!playing || before == null || nowNs - before >= intervalNs) {
            last = nowNs
            return true
        }
        return false
    }
}

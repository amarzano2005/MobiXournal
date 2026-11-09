package com.mobixournal.audio

/**
 * Where a waveform's blocks begin and end inside a recording, as pure arithmetic.
 *
 * Split out of [CompressedWaveform] so the rule that makes a long take affordable — a block is a
 * **span of the recording's time**, not a buffer the decoder happened to reach — is a JVM test with
 * no device, no codec and no file.
 */
internal object WaveformScan {

    /**
     * How long a recording may run before its envelope is **sampled** instead of decoded whole.
     *
     * A minute decodes in a blink; a half-hour or two-hour lecture decoded buffer by buffer was
     * minutes of work before one bar appeared, which reads as a waveform that does not work.
     */
    const val EXACT_LIMIT_US = 60_000_000L

    /** True when the take is too long to decode whole for a preview (`docs/audio.md`). */
    fun shouldSample(durationUs: Long): Boolean = durationUs > EXACT_LIMIT_US

    /** The instant block [index] starts, in microseconds — 0 for a recording with no length. */
    fun blockStart(index: Int, blocks: Int, durationUs: Long): Long {
        if (durationUs <= 0L || blocks <= 0) return 0L
        return durationUs * index.coerceIn(0, blocks) / blocks
    }

    /** The instant block [index] ends: the next block's start, and the take's end for the last one. */
    fun blockEnd(index: Int, blocks: Int, durationUs: Long): Long =
        blockStart(index + 1, blocks, durationUs)

    /** Which block a sample at [timeUs] belongs to, clamped into the waveform it is drawn from. */
    fun blockOf(timeUs: Long, blocks: Int, durationUs: Long): Int {
        if (durationUs <= 0L || blocks <= 0) return 0
        val at = timeUs.coerceIn(0L, durationUs)
        return (at * blocks / durationUs).toInt().coerceIn(0, blocks - 1)
    }

    /** Where the [window]th slice of block [index] starts, when a block is read in [windows] slices. */
    fun windowStart(index: Int, blocks: Int, window: Int, windows: Int, durationUs: Long): Long {
        val from = blockStart(index, blocks, durationUs)
        val to = blockEnd(index, blocks, durationUs)
        val steps = windows.coerceAtLeast(1)
        return from + (to - from) * window.coerceIn(0, steps) / steps
    }

    /** Where that window stops — the next window's start, and the block's end for the last one. */
    fun windowEnd(index: Int, blocks: Int, window: Int, windows: Int, durationUs: Long): Long =
        windowStart(index, blocks, window + 1, windows, durationUs)
}

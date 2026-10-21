package com.mobixournal.audio

/**
 * Turns a 16-bit PCM stream into **16 kHz mono**, a chunk at a time.
 *
 * A one-hour lecture is ~160 MB of 44.1 kHz mono PCM, and the speech engine wants a third of that at
 * 16 kHz. Reading the file whole, copying out its samples and resampling into a third array cost about
 * three times the recording in heap — the `OutOfMemoryError` a long recording produced right after it
 * was stopped, when transcription started. This sink keeps only its **output** (the 16 kHz mono array
 * the engine needs anyway) plus one read chunk, so a recording costs what it will be used for and no
 * more.
 *
 * The mapping is the one the old whole-array resampler used — output `i` is source sample
 * `i * srcRate / dstRate` — applied incrementally, so a stream produces exactly the bytes a whole-array
 * pass would have produced. Channels are averaged ([write] reads interleaved frames).
 */
internal class PcmResampler16k(
    private val sourceRate: Int,
    private val channels: Int,
    /** How many output bytes to expect, when the caller can say (a declared length or duration). */
    expectedBytes: Long = -1L,
    /** The rate the stream is converted to — 16 kHz for speech, and another rate for a caller that asks. */
    private val targetRate: Int = TARGET_RATE,
) {

    init {
        require(sourceRate > 0) { "a sample rate is needed to resample from" }
        require(channels > 0) { "a channel count is needed to read frames" }
    }

    private val frames = if (expectedBytes > 0) {
        expectedBytes.coerceAtMost(MAX_BYTES.toLong()).toInt() / BYTES_PER_SAMPLE
    } else {
        0
    }

    /** The pre-sized output when a length was known; chunks otherwise (an unknown duration). */
    private var output: ByteArray? = ByteArray(frames * BYTES_PER_SAMPLE).takeIf { frames > 0 }

    /** The output when its length was not known: fixed 1 MiB chunks, the last one partly filled. */
    private val chunks = ArrayList<ByteArray>()
    private var fill = 0

    /** Frames written into [output] so far. */
    private var written = 0

    /** The absolute index of the next source frame [write] will see. */
    private var sourceFrames = 0L

    /** The next output frame still to be produced. */
    private var outFrame = 0L

    /**
     * Consume [count] bytes of interleaved 16-bit PCM, starting at [offset] in [pcm], and append what
     * they produce. Reads have to end on a frame boundary — PCM from a decoder or a WAV does.
     */
    fun write(pcm: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        val frameBytes = channels * BYTES_PER_SAMPLE
        val available = count / frameBytes
        if (available == 0) return
        val start = sourceFrames
        sourceFrames += available
        while (true) {
            val source = if (sourceRate == targetRate) outFrame else outFrame * sourceRate / targetRate
            if (source >= sourceFrames) break
            val local = (source - start).toInt()
            emit(mix(pcm, offset + local * frameBytes, frameBytes))
            outFrame++
        }
    }

    /** The 16 kHz mono bytes written so far. */
    fun finish(): ByteArray {
        val sized = output
        if (sized != null) {
            val used = written * BYTES_PER_SAMPLE
            return if (used == sized.size) sized else sized.copyOf(used)
        }
        val total = chunks.dropLast(1).sumOf { it.size } + fill
        val out = ByteArray(total)
        var at = 0
        for (chunk in chunks) {
            val n = minOf(chunk.size, total - at)
            if (n <= 0) break
            chunk.copyInto(out, at, 0, n)
            at += n
        }
        return out
    }

    /** Average the channels of the frame at [offset] — mono passes through unchanged. */
    private fun mix(pcm: ByteArray, offset: Int, frameBytes: Int): Int {
        if (channels == 1) return sampleAt(pcm, offset)
        var sum = 0
        var channel = 0
        while (channel < channels) {
            sum += sampleAt(pcm, offset + channel * BYTES_PER_SAMPLE)
            channel++
        }
        return sum / channels
    }

    private fun sampleAt(pcm: ByteArray, at: Int): Int =
        (pcm[at].toInt() and 0xFF) or (pcm[at + 1].toInt() shl 8)

    private fun emit(sample: Int) {
        val sized = output
        if (sized != null && (written + 1) * BYTES_PER_SAMPLE <= sized.size) {
            sized[written * BYTES_PER_SAMPLE] = (sample and 0xFF).toByte()
            sized[written * BYTES_PER_SAMPLE + 1] = ((sample shr 8) and 0xFF).toByte()
            written++
            return
        }
        // A stream longer than its declaration (or one with no declaration at all): grow in 1 MiB
        // chunks, never by doubling one array — that would need a second copy of the whole output.
        if (sized != null) {
            chunks += sized.copyOf(written * BYTES_PER_SAMPLE)
            fill = written * BYTES_PER_SAMPLE
            output = null
        }
        if (chunks.isEmpty() || fill + BYTES_PER_SAMPLE > chunks.last().size) {
            chunks += ByteArray(CHUNK_BYTES)
            fill = 0
        }
        val chunk = chunks.last()
        chunk[fill] = (sample and 0xFF).toByte()
        chunk[fill + 1] = ((sample shr 8) and 0xFF).toByte()
        fill += BYTES_PER_SAMPLE
    }

    companion object {
        /** The rate speech recognition wants, and the default this resampler converts to. */
        const val TARGET_RATE = 16_000
        const val BYTES_PER_SAMPLE = 2

        /** The growth step for a stream whose length nobody declared. */
        private const val CHUNK_BYTES = 1024 * 1024

        /** A sanity ceiling for a declared length, so a corrupt header cannot ask for terabytes. */
        private const val MAX_BYTES = 512L * 1024 * 1024
    }
}

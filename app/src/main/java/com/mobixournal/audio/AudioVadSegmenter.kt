package com.mobixournal.audio

import kotlin.math.sqrt

/**
 * A time-bounded chunk of 16 kHz 16-bit mono PCM audio ready for discrete speech recognition.
 *
 * @property startMs Millisecond offset from the start of the audio file.
 * @property endMs Millisecond offset where this utterance finishes.
 * @property pcm Raw 16 kHz 16-bit mono PCM bytes for this slice.
 */
data class AudioChunk(
    val startMs: Int,
    val endMs: Int,
    val pcm: ByteArray,
) {
    val durationMs: Int get() = endMs - startMs

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return startMs == other.startMs && endMs == other.endMs && pcm.contentEquals(other.pcm)
    }

    override fun hashCode(): Int {
        var result = startMs
        result = 31 * result + endMs
        result = 31 * result + pcm.contentHashCode()
        return result
    }
}

/**
 * Segments raw 16 kHz 16-bit mono PCM audio into discrete spoken utterances using
 * Voice Activity Detection (VAD) and energy-based silence boundary detection.
 *
 * This turns continuous long-form audio into timestamped phrase chunks suitable for
 * [android.speech.SpeechRecognizer] without buffer overflows or silence cutoffs.
 */
object AudioVadSegmenter {

    private const val SAMPLE_RATE = 16000
    private const val BYTES_PER_SAMPLE = 2
    private const val BYTES_PER_MS = (SAMPLE_RATE * BYTES_PER_SAMPLE) / 1000 // 32 bytes/ms

    private const val FRAME_MS = 30
    private const val FRAME_SAMPLES = (SAMPLE_RATE * FRAME_MS) / 1000 // 480 samples
    private const val FRAME_BYTES = FRAME_SAMPLES * BYTES_PER_SAMPLE // 960 bytes

    private const val DEFAULT_MIN_CHUNK_MS = 1200
    private const val DEFAULT_MAX_CHUNK_MS = 6500
    private const val DEFAULT_SILENCE_GAP_MS = 450
    private const val DEFAULT_RMS_THRESHOLD = 450.0

    /**
     * Splits [pcm16kMono] into chronological [AudioChunk]s bounded by speech activity and pauses.
     */
    fun segment(
        pcm16kMono: ByteArray,
        minChunkMs: Int = DEFAULT_MIN_CHUNK_MS,
        maxChunkMs: Int = DEFAULT_MAX_CHUNK_MS,
        silenceGapMs: Int = DEFAULT_SILENCE_GAP_MS,
        rmsThreshold: Double = DEFAULT_RMS_THRESHOLD,
    ): List<AudioChunk> {
        val totalBytes = pcm16kMono.size
        if (totalBytes < FRAME_BYTES) return emptyList()

        val numFrames = totalBytes / FRAME_BYTES
        val frameRms = DoubleArray(numFrames)
        for (i in 0 until numFrames) {
            frameRms[i] = computeFrameRms(pcm16kMono, i * FRAME_BYTES, FRAME_SAMPLES)
        }

        // Adaptive threshold: at least rmsThreshold, or 25% of the 90th-percentile speech energy
        val sortedRms = frameRms.sorted()
        val p90 = sortedRms[(sortedRms.size * 0.90).toInt().coerceIn(0, sortedRms.size - 1)]
        val effectiveThreshold = maxOf(rmsThreshold, p90 * 0.20)

        val isSpeech = BooleanArray(numFrames) { frameRms[it] >= effectiveThreshold }
        val chunks = mutableListOf<AudioChunk>()

        var chunkStartFrame = -1
        var consecutiveSilenceMs = 0

        for (i in 0 until numFrames) {
            val speech = isSpeech[i]
            if (speech) {
                if (chunkStartFrame < 0) {
                    // Start of speech; include 1 padding frame before if available
                    chunkStartFrame = maxOf(0, i - 1)
                }
                consecutiveSilenceMs = 0
            } else if (chunkStartFrame >= 0) {
                consecutiveSilenceMs += FRAME_MS
            }

            if (chunkStartFrame >= 0) {
                val currentDurMs = (i - chunkStartFrame + 1) * FRAME_MS
                val shouldSplit = (consecutiveSilenceMs >= silenceGapMs && currentDurMs >= minChunkMs) ||
                    currentDurMs >= maxChunkMs

                if (shouldSplit) {
                    val endFrame = (i + 1).coerceAtMost(numFrames)
                    emitChunk(pcm16kMono, chunkStartFrame, endFrame, chunks)
                    chunkStartFrame = -1
                    consecutiveSilenceMs = 0
                }
            }
        }

        // Drain trailing speech if non-empty
        if (chunkStartFrame >= 0) {
            val trailingDurMs = (numFrames - chunkStartFrame) * FRAME_MS
            if (trailingDurMs >= minChunkMs || chunks.isEmpty()) {
                emitChunk(pcm16kMono, chunkStartFrame, numFrames, chunks)
            } else if (chunks.isNotEmpty()) {
                // Merge short trailing tail into previous chunk if not exceeding maxChunkMs * 1.3
                val prev = chunks.removeAt(chunks.size - 1)
                val newStartByte = prev.startMs * BYTES_PER_MS
                val newEndByte = totalBytes
                val mergedPcm = pcm16kMono.copyOfRange(newStartByte, newEndByte)
                chunks.add(AudioChunk(prev.startMs, totalBytes / BYTES_PER_MS, mergedPcm))
            }
        }

        return chunks
    }

    private fun emitChunk(
        pcm: ByteArray,
        startFrame: Int,
        endFrame: Int,
        out: MutableList<AudioChunk>,
    ) {
        val startByte = (startFrame * FRAME_BYTES).coerceIn(0, pcm.size)
        val endByte = (endFrame * FRAME_BYTES).coerceIn(startByte, pcm.size)
        if (endByte <= startByte) return
        val chunkBytes = pcm.copyOfRange(startByte, endByte)
        val startMs = startByte / BYTES_PER_MS
        val endMs = endByte / BYTES_PER_MS
        out.add(AudioChunk(startMs, endMs, chunkBytes))
    }

    private fun computeFrameRms(pcm: ByteArray, offset: Int, samples: Int): Double {
        var sumSquares = 0.0
        var ptr = offset
        for (i in 0 until samples) {
            if (ptr + 1 >= pcm.size) break
            val low = pcm[ptr].toInt() and 0xFF
            val high = pcm[ptr + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
            ptr += 2
        }
        return sqrt(sumSquares / samples)
    }
}

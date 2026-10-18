package com.mobixournal.audio

/**
 * A single segment of speech-to-text transcription with millisecond timestamp bounds.
 *
 * @property startTimeMs Offset in milliseconds from the start of the audio recording.
 * @property endTimeMs Offset in milliseconds when this spoken segment finished.
 * @property text The transcribed text content of the spoken words or sentence.
 * @property confidence Recognition confidence score between 0.0 and 1.0.
 */
data class TranscriptionSegment(
    val startTimeMs: Int,
    val endTimeMs: Int,
    val text: String,
    val confidence: Double = 1.0,
) {
    init {
        require(startTimeMs >= 0) { "startTimeMs must be non-negative: $startTimeMs" }
        require(endTimeMs >= startTimeMs) { "endTimeMs ($endTimeMs) must be >= startTimeMs ($startTimeMs)" }
    }

    /** Duration of this spoken segment in milliseconds. */
    val durationMs: Int get() = endTimeMs - startTimeMs

    /** Whether [timeMs] falls within this segment's active interval. */
    fun containsTime(timeMs: Int): Boolean = timeMs in startTimeMs..endTimeMs
}

/**
 * Complete transcription session of an audio recording, containing sequential segments.
 *
 * @property audioFilename Bare filename of the audio recording (e.g. `2026-10-18T10-30-00.ogg`).
 * @property segments Chronologically ordered transcription segments.
 * @property language Detected or configured language tag (e.g. `it-IT`, `en-US`).
 */
data class AudioTranscription(
    val audioFilename: String,
    val segments: List<TranscriptionSegment> = emptyList(),
    val language: String = "it-IT",
) {
    /** Combined full plain-text string of the transcription. */
    val fullText: String by lazy {
        segments.joinToString(" ") { it.text }
    }

    /** Finds the transcription segment active at [playheadMs], if any. */
    fun segmentAt(playheadMs: Int): TranscriptionSegment? =
        segments.firstOrNull { it.containsTime(playheadMs) || (playheadMs in it.endTimeMs..(it.endTimeMs + 1200)) }

    /** Finds all segments whose text contains [query] (case-insensitive). */
    fun search(query: String): List<TranscriptionSegment> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return segments.filter { it.text.contains(trimmed, ignoreCase = true) }
    }
}

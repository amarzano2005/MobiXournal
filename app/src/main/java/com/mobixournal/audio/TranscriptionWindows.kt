package com.mobixournal.audio

import kotlin.math.roundToInt

/**
 * One audio span handed to Whisper as a single decode, and the VAD chunks it was built from.
 *
 * [chunks] is kept in order so the recognised text can be given back to the phrases it came from
 * ([TranscriptionWindows.distributeText]): the window is what the model is efficient at, the chunks
 * are what the transcript, the subtitles and the karaoke replay need.
 */
class AudioWindow(
    val startMs: Int,
    val endMs: Int,
    val chunks: List<AudioChunk>,
) {
    val durationMs: Int get() = endMs - startMs

    /**
     * The window's audio, assembled when a decode asks for it.
     *
     * Deliberately not a stored array: grouping an hour of speech makes ~140 windows, and holding all
     * of their PCM would keep a second copy of the whole recording in memory for as long as the
     * transcription runs. One window is built per decode and let go with it.
     */
    fun pcm(): ByteArray {
        val bytes = ByteArray(chunks.sumOf { it.pcm.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.pcm.copyInto(bytes, offset)
            offset += chunk.pcm.size
        }
        return bytes
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is AudioWindow && startMs == other.startMs && endMs == other.endMs && chunks == other.chunks)

    override fun hashCode(): Int = 31 * (31 * startMs + endMs) + chunks.hashCode()

    override fun toString(): String = "AudioWindow($startMs..$endMs, ${chunks.size} chunks)"
}

/**
 * Groups the VAD's phrases into the windows Whisper actually decodes.
 *
 * Whisper's encoder always works on a **30-second** mel window — audio shorter than that is padded up
 * to it — so decoding a 2–6 s phrase costs almost exactly what decoding 30 s costs. The VAD cuts at
 * silences every 1.2–6.5 s ([AudioVadSegmenter][com.mobixournal.audio.AudioVadSegmenter]) because
 * those boundaries are what the transcript's timestamps need; feeding each of them to the model on
 * its own meant a one-hour recording paid for ~1,000 padded windows. Windows of [WINDOW_MS] cut only
 * between phrases, so a lecture of that length costs ~130 of them — eight to ten times fewer decodes
 * for the same bytes and the same model.
 *
 * Nothing here is platform-specific: the grouping and the text split are pure functions over
 * [AudioChunk]s, which is what makes the saving measurable in a JVM test without an encoder.
 */
object TranscriptionWindows {

    /**
     * The longest audio one decode carries — just under Whisper's own 30-second encoder window, so a
     * window never has to be truncated by the model.
     */
    const val WINDOW_MS = 28_000

    private val WHITESPACE = Regex("\\s+")

    /** A piece that is nothing but punctuation, which attaches to the word before it. */
    private val PUNCTUATION = Regex("^[\\p{Punct}]+$")

    /**
     * Group [chunks] into consecutive windows of at most [maxWindowMs], cutting only between chunks —
     * never inside a phrase. The windows cover every chunk, in order, and each chunk is in exactly one.
     */
    fun of(chunks: List<AudioChunk>, maxWindowMs: Int = WINDOW_MS): List<AudioWindow> {
        if (chunks.isEmpty()) return emptyList()
        val limit = maxWindowMs.coerceAtLeast(1_000)
        val windows = mutableListOf<AudioWindow>()
        var pending = mutableListOf<AudioChunk>()
        for (chunk in chunks) {
            val windowStart = pending.firstOrNull()?.startMs ?: chunk.startMs
            if (pending.isNotEmpty() && chunk.endMs - windowStart > limit) {
                windows += windowOf(pending)
                pending = mutableListOf()
            }
            pending += chunk
        }
        if (pending.isNotEmpty()) windows += windowOf(pending)
        return windows
    }

    /**
     * Give the text recognised for [window] back to the phrases it covers, in order: each chunk keeps
     * its own timestamps and receives its share of the words, measured by how much of the window it
     * occupies.
     *
     * This is a distribution, not a second recognition — Whisper does not report which phrase a word
     * came from here — so a phrase boundary can land a word late or early when speech speed changes
     * sharply inside a window. What it guarantees is what the rest of the app depends on: segments are
     * still chronological, still bounded by the VAD's real timestamps, none is empty, and together they
     * hold every word the model produced.
     */
    fun distributeText(
        window: AudioWindow,
        text: String,
        confidence: Double = 0.95,
    ): List<TranscriptionSegment> {
        val words = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        if (window.chunks.size <= 1) {
            val span = window.chunks.firstOrNull()
            return listOf(
                TranscriptionSegment(
                    startTimeMs = span?.startMs ?: window.startMs,
                    endTimeMs = span?.endMs ?: window.endMs,
                    text = words.joinToString(" "),
                    confidence = confidence,
                ),
            )
        }

        val totalMs = window.chunks.sumOf { it.durationMs }.coerceAtLeast(1)
        val segments = mutableListOf<TranscriptionSegment>()
        var spoken = 0
        var elapsedMs = 0
        for ((index, chunk) in window.chunks.withIndex()) {
            elapsedMs += chunk.durationMs
            val boundary = if (index == window.chunks.lastIndex) {
                words.size
            } else {
                (words.size.toDouble() * elapsedMs / totalMs).roundToInt().coerceIn(spoken, words.size)
            }
            if (boundary <= spoken) continue
            segments += TranscriptionSegment(
                startTimeMs = chunk.startMs,
                endTimeMs = chunk.endMs,
                text = words.subList(spoken, boundary).joinToString(" "),
                confidence = confidence,
            )
            spoken = boundary
        }
        return segments
    }

    /**
     * Give the tokens Whisper reported for [window] back to the phrases they were **spoken** in.
     *
     * A decode returns the words of the whole window together with when each one started (seconds
     * from the window's first sample), so each token is filed under the VAD phrase whose span
     * contains its time — clamped into the first or last phrase at the edges. Unlike [distributeText]
     * nothing is estimated here: the words a segment gets are the words said during it, which is what
     * makes a line read like the sentence it is and a tap on it land where the words were spoken.
     *
     * Returns null when the decode carries no usable timing — no tokens, a length mismatch, or the
     * flat all-zero starts a model without token timestamps reports — so the caller can fall back to
     * [distributeText].
     */
    fun segmentsFromTokens(
        window: AudioWindow,
        tokens: Array<out String>,
        timestampsSec: FloatArray,
        confidence: Double = 0.95,
    ): List<TranscriptionSegment>? {
        if (window.chunks.isEmpty() || tokens.isEmpty() || tokens.size != timestampsSec.size) return null
        if (timestampsSec.all { it <= 0f }) return null
        val originMs = window.chunks.first().startMs
        val times = IntArray(tokens.size) { index -> originMs + (timestampsSec[index] * 1_000f).roundToInt() }
        // Out-of-order starts mean the array is not what it claims to be; the distribution is safer.
        for (index in 1 until times.size) if (times[index] < times[index - 1]) return null

        val wordsIn = Array(window.chunks.size) { mutableListOf<String>() }
        var chunk = 0
        for (index in tokens.indices) {
            val time = times[index]
            while (chunk < window.chunks.lastIndex && time >= window.chunks[chunk].endMs) chunk++
            wordsIn[chunk] += tokens[index]
        }

        val segments = mutableListOf<TranscriptionSegment>()
        for ((index, words) in wordsIn.withIndex()) {
            val text = joinTokens(words)
            if (text.isEmpty()) continue
            segments += TranscriptionSegment(
                startTimeMs = window.chunks[index].startMs,
                endTimeMs = window.chunks[index].endMs,
                text = text,
                confidence = confidence,
            )
        }
        return segments.ifEmpty { null }
    }

    /**
     * Whisper's pieces joined back into text: a piece carries its own leading space in most models,
     * and one that does not still starts a word — unless it is punctuation, which attaches.
     */
    private fun joinTokens(tokens: List<String>): String {
        val out = StringBuilder()
        for (token in tokens) {
            if (token.isEmpty()) continue
            val needsSpace = out.isNotEmpty() &&
                !token.first().isWhitespace() &&
                !out.last().isWhitespace() &&
                !PUNCTUATION.matches(token)
            if (needsSpace) out.append(' ')
            out.append(token)
        }
        return out.toString().replace(WHITESPACE, " ").trim()
    }

    private fun windowOf(chunks: List<AudioChunk>): AudioWindow = AudioWindow(
        startMs = chunks.first().startMs,
        endMs = chunks.last().endMs,
        chunks = chunks.toList(),
    )
}

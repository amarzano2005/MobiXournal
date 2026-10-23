package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Whisper reports when each token was spoken, and a transcript's segments are the VAD's phrases. What
 * this pins is that a word lands in the phrase it was said in — not in the share of the window the
 * phrase happens to fill — and that a decode without usable timing is refused so the caller can fall
 * back to the proportional split.
 */
class TranscriptionTokensTest {

    private fun chunk(startMs: Int, endMs: Int) = AudioChunk(startMs, endMs, ByteArray((endMs - startMs) * 32))

    private fun window(vararg chunks: AudioChunk) = AudioWindow(
        startMs = chunks.first().startMs,
        endMs = chunks.last().endMs,
        chunks = chunks.toList(),
    )

    private val twoBars = window(chunk(0, 1_000), chunk(1_000, 2_000))

    private fun segmentsOf(window: AudioWindow, tokens: Array<String>, times: FloatArray) =
        TranscriptionWindows.segmentsFromTokens(window, tokens, times)

    @Test
    fun aWordLandsInThePhraseItWasSpokenIn() {
        val segments = segmentsOf(
            window = twoBars,
            tokens = arrayOf("Ciao", " mondo", ",", " come", " stai"),
            times = floatArrayOf(0.1f, 0.3f, 0.5f, 1.4f, 1.6f),
        )

        assertEquals(
            listOf(
                TranscriptionSegment(0, 1_000, "Ciao mondo,", 0.95),
                TranscriptionSegment(1_000, 2_000, "come stai", 0.95),
            ),
            segments,
        )
    }

    @Test
    fun piecesWithNoLeadingSpaceStillReadAsWords() {
        val segments = segmentsOf(twoBars, arrayOf("Ciao", "mondo", "!"), floatArrayOf(0.1f, 0.2f, 0.3f))
            ?: error("the words are usable")

        assertEquals("Ciao mondo!", segments.single().text)
    }

    @Test
    fun aSingleWordKeepsThePhraseItWasSpokenIn() {
        val segments = segmentsOf(window(chunk(5_000, 6_000)), arrayOf("prima"), floatArrayOf(0.4f))
            ?: error("a single timed token must still be kept")

        assertEquals(5_000, segments.single().startTimeMs)
        assertEquals(6_000, segments.single().endTimeMs)
        assertEquals("prima", segments.single().text)
    }

    @Test
    fun aWordAfterTheLastPhraseBelongsToIt() {
        val segments = segmentsOf(twoBars, arrayOf("dopo"), floatArrayOf(9.0f))
            ?: error("a token past the phrase must still be kept")

        assertEquals(1_000, segments.single().startTimeMs)
        assertEquals(2_000, segments.single().endTimeMs)
    }

    @Test
    fun aPhraseWhisperDidNotFillIsLeftOut() {
        // Three phrases, words only in the first and the last: the silent middle one gets no segment.
        val threeBars = window(chunk(0, 1_000), chunk(1_000, 2_000), chunk(2_000, 3_000))
        val segments = segmentsOf(threeBars, arrayOf("inizio", " fine"), floatArrayOf(0.2f, 2.4f))
            ?: error("the words are usable")

        assertEquals(listOf(0, 2_000), segments.map { it.startTimeMs })
    }

    @Test
    fun flatTimingsAreRefusedSoTheCallerCanFallBack() {
        assertNull(segmentsOf(twoBars, arrayOf("a", " b"), floatArrayOf(0f, 0f)))
    }

    @Test
    fun aLengthMismatchIsRefused() {
        assertNull(TranscriptionWindows.segmentsFromTokens(twoBars, arrayOf("a", " b"), floatArrayOf(0.1f)))
    }

    @Test
    fun outOfOrderTimingsAreRefused() {
        assertNull(segmentsOf(twoBars, arrayOf("a", " b"), floatArrayOf(1.6f, 0.3f)))
    }
}

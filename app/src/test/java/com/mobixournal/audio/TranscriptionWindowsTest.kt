package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TranscriptionWindows] — the grouping that turns the VAD's phrases into the windows
 * Whisper decodes in one pass, and the split that gives the recognised text back to those phrases.
 *
 * The saving is measured here as **decode windows**, which is the unit Whisper's cost is charged in:
 * one decode per window, each padded to the model's 30-second encoder window whatever it holds. The
 * wall clock on a device follows from that count, not from anything a JVM without an encoder can time.
 */
class TranscriptionWindowsTest {

    private val bytesPerMs = 32 // 16 kHz, 16-bit mono

    private fun chunk(startMs: Int, endMs: Int): AudioChunk =
        AudioChunk(startMs, endMs, ByteArray((endMs - startMs) * bytesPerMs))

    @Test
    fun `windows stay under the limit and cover every chunk in order`() {
        val chunks = (0 until 40).map { chunk(it * 1_000, (it + 1) * 1_000) }

        val windows = TranscriptionWindows.of(chunks)

        assertTrue("one window must fit many phrases", windows.size < chunks.size)
        assertTrue(windows.all { it.durationMs <= TranscriptionWindows.WINDOW_MS })
        assertEquals(chunks.size, windows.sumOf { it.chunks.size })
        assertEquals(
            chunks.map { it.startMs },
            windows.flatMap { window -> window.chunks.map { it.startMs } },
        )
    }

    @Test
    fun `a phrase longer than the limit becomes its own window rather than being cut`() {
        val long = chunk(0, TranscriptionWindows.WINDOW_MS + 5_000)
        val short = chunk(TranscriptionWindows.WINDOW_MS + 5_000, TranscriptionWindows.WINDOW_MS + 6_000)

        val windows = TranscriptionWindows.of(listOf(long, short))

        assertEquals(2, windows.size)
        assertEquals(0, windows[0].startMs)
        assertEquals(long.endMs, windows[0].endMs)
        assertEquals(1, windows[1].chunks.size)
    }

    @Test
    fun `a window's pcm is its chunks' pcm in order`() {
        val first = AudioChunk(0, 500, ByteArray(500 * bytesPerMs) { 1 })
        val second = AudioChunk(900, 1_400, ByteArray(500 * bytesPerMs) { 2 })

        val window = TranscriptionWindows.of(listOf(first, second)).single()

        val pcm = window.pcm()
        assertEquals(1_000 * bytesPerMs, pcm.size)
        assertEquals(1, pcm[0].toInt())
        assertEquals(1, pcm[500 * bytesPerMs - 1].toInt())
        assertEquals(2, pcm[500 * bytesPerMs].toInt())
        assertEquals(2, pcm[pcm.size - 1].toInt())
        assertEquals(0, window.startMs)
        assertEquals(1_400, window.endMs)
    }

    @Test
    fun `a ten minute lecture decodes in an order of magnitude fewer windows`() {
        val pcm = speechPcm(seconds = 600)
        val chunks = AudioVadSegmenter.segment(pcm)
        val windows = TranscriptionWindows.of(chunks)

        assertTrue("the VAD should have found many phrases, found ${chunks.size}", chunks.size >= 200)
        assertTrue(
            "one decode per phrase (${chunks.size}) vs per window (${windows.size})",
            chunks.size >= windows.size * 8,
        )
        assertTrue("no window may exceed the model's own", windows.all { it.durationMs <= TranscriptionWindows.WINDOW_MS })
        assertEquals(
            "every phrase belongs to exactly one window",
            chunks.map { it.startMs },
            windows.flatMap { window -> window.chunks.map { it.startMs } },
        )
        assertEquals(chunks.last().endMs, windows.last().endMs)
    }

    @Test
    fun `text is given back to the phrases by their share of the window`() {
        val window = TranscriptionWindows.of(
            listOf(chunk(0, 1_000), chunk(1_000, 2_000), chunk(2_000, 4_000)),
        ).single()

        val segments = TranscriptionWindows.distributeText(window, "uno due tre quattro cinque sei sette otto")

        assertEquals(3, segments.size)
        assertEquals("uno due", segments[0].text)
        assertEquals("tre quattro", segments[1].text)
        assertEquals("cinque sei sette otto", segments[2].text)
        assertEquals(0, segments[0].startTimeMs)
        assertEquals(1_000, segments[0].endTimeMs)
        assertEquals(1_000, segments[1].startTimeMs)
        assertEquals(2_000, segments[1].endTimeMs)
        assertEquals(2_000, segments[2].startTimeMs)
        assertEquals(4_000, segments[2].endTimeMs)
    }

    @Test
    fun `more phrases than words still yields ordered, non-empty segments holding every word`() {
        val window = TranscriptionWindows.of((0 until 5).map { chunk(it * 1_000, (it + 1) * 1_000) }).single()

        val segments = TranscriptionWindows.distributeText(window, "  uno   due  ")

        assertTrue(segments.isNotEmpty())
        assertEquals(listOf("uno", "due"), segments.flatMap { it.text.split(" ") })
        assertEquals(segments.map { it.startTimeMs }.sorted(), segments.map { it.startTimeMs })
        assertTrue(segments.all { it.endTimeMs > it.startTimeMs })
    }

    @Test
    fun `a single phrase window keeps its own timestamps`() {
        val window = TranscriptionWindows.of(listOf(chunk(2_500, 4_200))).single()

        val segments = TranscriptionWindows.distributeText(window, "teorema fondamentale")

        assertEquals(1, segments.size)
        assertEquals(2_500, segments[0].startTimeMs)
        assertEquals(4_200, segments[0].endTimeMs)
        assertEquals("teorema fondamentale", segments[0].text)
    }

    @Test
    fun `silence recognised as nothing produces no segments`() {
        val window = TranscriptionWindows.of(listOf(chunk(0, 1_000), chunk(1_000, 2_000))).single()

        assertTrue(TranscriptionWindows.distributeText(window, "   ").isEmpty())
    }

    @Test
    fun `inference uses the device's cores but never more than the cap`() {
        assertEquals(2, WhisperOfflineTranscriber.inferenceThreads(cores = 1))
        assertEquals(4, WhisperOfflineTranscriber.inferenceThreads(cores = 4))
        assertEquals(6, WhisperOfflineTranscriber.inferenceThreads(cores = 8))
        assertEquals(
            WhisperOfflineTranscriber.MAX_THREADS,
            WhisperOfflineTranscriber.inferenceThreads(cores = 16),
        )
    }

    /** [seconds] of speech-like bursts separated by short silences, 16 kHz 16-bit mono. */
    private fun speechPcm(seconds: Int, burstMs: Int = 2_000, silenceMs: Int = 600): ByteArray {
        val sampleRate = 16_000
        val pcm = ByteArray(seconds * sampleRate * 2)
        var positionMs = 0
        val totalMs = seconds * 1_000
        while (positionMs + burstMs <= totalMs) {
            val startSample = (positionMs.toLong() * sampleRate / 1_000L).toInt()
            val endSample = ((positionMs + burstMs).toLong() * sampleRate / 1_000L).toInt()
            for (sample in startSample until endSample) {
                val value = if (sample % 2 == 0) 8_000 else -8_000
                pcm[sample * 2] = (value and 0xFF).toByte()
                pcm[sample * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            positionMs += burstMs + silenceMs
        }
        return pcm
    }
}

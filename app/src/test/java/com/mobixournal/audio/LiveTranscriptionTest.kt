package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for the live pipeline's pure halves: the stream resampler that turns capture chunks into
 * 16 kHz mono, and the cutter that decides when a span can be handed to the recogniser.
 *
 * What is *not* here is inference — it needs sherpa-onnx's native libraries and a model, so only the
 * inert case (no model on disk) is exercised on the JVM.
 */
class LiveTranscriptionTest {

    private val bytesPerMs = 32

    // ---- resampler -----------------------------------------------------------------------------

    @Test
    fun `44100 hz mono becomes 16 khz at the same duration`() {
        var frames = 0
        val sink = LiveResampler16k(sourceRate = 44_100, channels = 1) { bytes, n -> frames += n / 2 }

        val oneSecond = ByteArray(44_100 * 2)
        for (chunk in oneSecond.indices step 1000) {
            sink.write(oneSecond, chunk, minOf(1000, oneSecond.size - chunk))
        }
        sink.flush()

        assertEquals(16_000, frames)
    }

    @Test
    fun `48000 hz mono becomes 16 khz at the same duration`() {
        var frames = 0
        val sink = LiveResampler16k(sourceRate = 48_000, channels = 1) { _, n -> frames += n / 2 }

        sink.write(ByteArray(48_000 * 2), 0, 48_000 * 2)
        sink.flush()

        assertEquals(16_000, frames)
    }

    @Test
    fun `stereo frames are averaged, not interleaved`() {
        val output = mutableListOf<Short>()
        val sink = LiveResampler16k(sourceRate = 32_000, channels = 2) { bytes, n ->
            var at = 0
            while (at < n) {
                output += ((bytes[at].toInt() and 0xFF) or (bytes[at + 1].toInt() shl 8)).toShort()
                at += 2
            }
        }
        // 32 kHz -> 16 kHz takes one frame in two, starting with the first:
        // frame 0 is (+1000, +3000) and averages to 2000, frame 1 is (-1000, +1000) and is skipped.
        val frames = byteArrayOf(
            0xE8.toByte(), 0x03, 0xB8.toByte(), 0x0B, 0x18, 0xFC.toByte(), 0xE8.toByte(), 0x03,
        )
        sink.write(frames, 0, frames.size)
        sink.flush()

        assertEquals(listOf<Short>(2000), output)
    }

    // ---- cutter --------------------------------------------------------------------------------

    @Test
    fun `spans cut at silences cover the stream once, in order`() {
        val cutter = LiveSpanCutter()
        val spans = mutableListOf<AudioChunk>()
        var fed = 0
        fun feed(bytes: ByteArray) {
            cutter.feed(bytes, bytes.size) { spans += it }
            fed += bytes.size
        }

        repeat(3) {
            feed(speech(2_000))
            feed(silence(700))
        }
        feed(speech(1_500))
        cutter.flush { spans += it }

        assertEquals("no byte may be lost or repeated", fed, spans.sumOf { it.pcm.size })
        var at = 0
        for (span in spans) {
            assertEquals("spans are contiguous", at, span.startMs)
            assertEquals(span.startMs + span.pcm.size / bytesPerMs, span.endMs)
            assertTrue("a span never exceeds the model's window", span.durationMs <= TranscriptionWindows.WINDOW_MS)
            at = span.endMs
        }
        assertEquals(fed / bytesPerMs, at)
        assertTrue("more than one span was produced", spans.size > 1)
    }

    @Test
    fun `a span closes only after the minimum, at a silence`() {
        val cutter = LiveSpanCutter()
        val spans = mutableListOf<AudioChunk>()
        fun feed(bytes: ByteArray) = cutter.feed(bytes, bytes.size) { spans += it }

        feed(speech(2_000))
        feed(silence(700))
        assertTrue("2 s of speech is not a span yet", spans.isEmpty())

        feed(speech(5_000))
        feed(silence(700))
        assertEquals(1, spans.size)
        assertTrue("the span reached the minimum", spans[0].durationMs >= 6_000)
        assertTrue("and ended inside the pause", spans[0].endMs <= 2_000 + 700 + 5_000 + 700)
    }

    @Test
    fun `continuous speech is cut at the window, not left to grow`() {
        val cutter = LiveSpanCutter()
        val spans = mutableListOf<AudioChunk>()

        cutter.feed(speech(90_000), 90_000 * bytesPerMs) { spans += it }

        assertTrue(spans.size >= 3)
        assertTrue(spans.all { it.durationMs <= TranscriptionWindows.WINDOW_MS })
    }

    @Test
    fun `silence before the first word does not become a span`() {
        val cutter = LiveSpanCutter()
        val spans = mutableListOf<AudioChunk>()
        fun feed(bytes: ByteArray) = cutter.feed(bytes, bytes.size) { spans += it }

        feed(silence(5_000))
        feed(speech(7_000))
        feed(silence(700))

        assertEquals(1, spans.size)
        assertTrue("the span starts near the speech, not at the recording's head", spans[0].startMs >= 4_000)
        assertTrue(spans[0].durationMs >= 6_000)
    }

    @Test
    fun `a trailing bit of speech is flushed at stop`() {
        val cutter = LiveSpanCutter()
        val spans = mutableListOf<AudioChunk>()

        cutter.feed(speech(2_000), 2_000 * bytesPerMs) { spans += it }
        assertTrue("nothing closes before the minimum", spans.isEmpty())
        cutter.flush { spans += it }

        assertEquals(1, spans.size)
        assertEquals(2_000, spans[0].durationMs)
    }

    @Test
    fun `a silent tail is dropped rather than transcribed`() {
        val cutter = LiveSpanCutter()
        val spans = mutableListOf<AudioChunk>()

        cutter.feed(silence(5_000), 5_000 * bytesPerMs) { spans += it }
        cutter.flush { spans += it }

        assertTrue(spans.isEmpty())
    }

    // ---- the job itself, without a model -------------------------------------------------------

    @Test
    fun `without a model the live job stays inert`() {
        val dir = kotlin.io.path.createTempDirectory("live-transcription").toFile()
        try {
            val live = LiveTranscription(
                fileName = "2026-10-22T10-00-00.ogg",
                dir = dir,
                language = "it-IT",
                modelDir = File(dir, "whisper-tiny"),
                isTiny = true,
            )

            assertFalse(live.start())
            live.feed(speech(2_000), 2_000 * bytesPerMs, 16_000)
            assertNull(live.stop())
            assertFalse("no transcript may be written", live.transcriptFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---- fixtures ------------------------------------------------------------------------------

    /** 16 kHz mono "speech": a constant amplitude well above the VAD's floor (8000). */
    private fun speech(ms: Int): ByteArray = ByteArray(ms * bytesPerMs) { index ->
        if (index % 2 == 0) 0x40.toByte() else 0x1F.toByte()
    }

    private fun silence(ms: Int): ByteArray = ByteArray(ms * bytesPerMs)
}

package com.mobixournal.desktop

import com.mobixournal.audio.OggOpus
import com.mobixournal.audio.WavWriter
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/**
 * The two containers a recording arrives in, read on a plain JVM with no sound card.
 *
 * The WAV is written by the **shared** writer the tablet uses for a `.xopp` sidecar, and the Opus file is
 * encoded by Concentus and framed by `OggOpus` — so these tests read a stream in exactly the shape either
 * shell produces, not a hand-written approximation of one.
 */
class DesktopAudioSourcesTest {

    private val temp = createTempDir("mobix-audio-sources")

    @Test
    fun `a wav reads its samples, its length and seeks`() {
        val file = File(temp, "take.wav")
        val samples = sine(sampleRate = 44_100, ms = 200)
        WavWriter(file, 44_100).use { writer -> writer.write(shortBytes(samples), samples.size * 2) }

        val source = DesktopAudioSources.open(file)
        try {
            assertEquals(44_100, source.sampleRate)
            assertEquals(1, source.channels)
            assertEquals("200 ms of audio", 200, source.durationMs)

            val read = ShortArray(samples.size)
            val count = source.read(read)
            assertEquals("every sample comes back", samples.size, count)
            assertEquals(samples[100], read[100])

            source.seekTo(100)
            val afterSeek = ShortArray(64)
            source.read(afterSeek)
            // 100 ms in is sample 4 410; the first sample read must be that one, not the head.
            assertTrue("a seek repositions the reader", abs(afterSeek[0].toInt() - samples[4_410].toInt()) < 8)
        } finally {
            source.close()
        }
    }

    @Test
    fun `an opus recording decodes to the audio it was encoded from`() {
        val file = File(temp, "take.ogg")
        file.writeBytes(opusFile(sine(sampleRate = 48_000, ms = 200)))

        val source = DesktopAudioSources.open(file)
        try {
            assertEquals(48_000, source.sampleRate)
            assertEquals(1, source.channels)
            assertTrue("the clip has a length", source.durationMs >= 180)

            val buffer = ShortArray(4_096)
            var total = 0
            var peak = 0
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                for (i in 0 until read) peak = maxOf(peak, abs(buffer[i].toInt()))
                total += read
                assertTrue("a clip cannot decode more than its own timer allows", total < 48_000 * 2)
            }
            assertTrue("most of the clip decodes ($total samples)", total > 48_000 * 18 / 100)
            assertTrue("and it is sound, not silence (peak $peak)", peak > 1_000)
        } finally {
            source.close()
        }
    }

    /**
     * A seek on a long Opus recording lands on the second it names.
     *
     * The clip is a tone that **changes pitch every second**, which is what makes the assertion mean
     * something: if the packet the jump started at were wrong, the sound after the seek would be the
     * pitch of another second, not merely a few samples off.
     */
    @Test
    fun `a seek on an opus recording lands on the second it names`() {
        val pitches = intArrayOf(330, 440, 550, 660)
        val file = File(temp, "four-seconds.ogg")
        file.writeBytes(opusFile(stepped(pitches, sampleRate = 48_000)))

        val source = DesktopAudioSources.open(file)
        try {
            assertEquals("the clip's length is its audio, pre-skip off", 4_000.0, source.durationMs.toDouble(), 30.0)
            val buffer = ShortArray(48_000 / 10)
            for ((second, pitch) in pitches.withIndex()) {
                source.seekTo(second * 1_000 + 500)
                val read = source.read(buffer)
                assertTrue("half a second in, the clip still reads", read > 0)
                val heard = frequencyOf(buffer, read, source.sampleRate)
                assertEquals(
                    "a seek to ${second + 1} s plays the ${pitch} Hz second, not another",
                    pitch.toDouble(),
                    heard,
                    25.0,
                )
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun `an aac recording is refused in words instead of failing silently`() {
        val file = File(temp, "take.m4a")
        file.writeBytes(ByteArray(64))

        val failure = runCatching { DesktopAudioSources.open(file) }.exceptionOrNull()

        assertTrue("it is refused as a sentence: ${failure?.message}", failure is AudioRefused)
        assertTrue("and names the format", failure?.message!!.contains("AAC"))
    }

    @Test
    fun `a recording this machine does not have says which one`() {
        val failure = runCatching { DesktopAudioSources.open(File(temp, "missing.wav")) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("missing.wav"))
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private fun sine(sampleRate: Int, ms: Int): ShortArray {
        val count = sampleRate * ms / 1000
        return ShortArray(count) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 12_000).toInt().toShort()
        }
    }

    /** [pitches] one second each, so the clip's timeline can be read back by ear (or by zero crossings). */
    private fun stepped(pitches: IntArray, sampleRate: Int): ShortArray {
        val perSecond = sampleRate
        val out = ShortArray(perSecond * pitches.size)
        for ((second, pitch) in pitches.withIndex()) {
            for (i in 0 until perSecond) {
                val phase = 2.0 * Math.PI * pitch * i / sampleRate
                out[second * perSecond + i] = (sin(phase) * 12_000).toInt().toShort()
            }
        }
        return out
    }

    /** The pitch of [samples], estimated from its zero crossings over the stretch they cover. */
    private fun frequencyOf(samples: ShortArray, count: Int, sampleRate: Int): Double {
        var crossings = 0
        for (i in 1 until count) {
            if (samples[i - 1] < 0 && samples[i] >= 0) crossings++
        }
        return crossings * sampleRate.toDouble() / count
    }

    private fun shortBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /** 20 ms Opus frames, framed by the shared Ogg writer — the file the tablet's recorder produces. */
    private fun opusFile(pcm: ShortArray): ByteArray {
        val encoder = OpusEncoder(OggOpus.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO)
        val packets = ArrayList<ByteArray>()
        val frame = OggOpus.FRAME_SAMPLES
        var at = 0
        while (at + frame <= pcm.size) {
            val out = ByteArray(4_000)
            val length = encoder.encode(pcm, at, frame, out, 0, out.size)
            packets.add(out.copyOfRange(0, length))
            at += frame
        }
        return OggOpus.write(channels = 1, preSkip = 312, packets = packets)
    }
}

package com.mobixournal.audio

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The speech extractor converts a recording to 16 kHz mono **as it reads it**, because a lecture-sized
 * recording held whole (and then resampled into a second array) is what ran a save out of memory. What
 * this pins is that the incremental mapping is exactly the whole-array one — a recording processed in
 * chunks has to be the same audio the engine would have got from a single pass — and that a WAV on
 * disk really goes through the streaming path.
 */
class PcmResamplerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val sourceRate = 44_100

    /** One second of 16-bit mono samples with a shape, so a misaligned mapping would show. */
    private fun second(): ByteArray = ByteArray(sourceRate * 2) { i ->
        val sample = (i / 2) * 7 % 30_000
        if (i % 2 == 0) (sample and 0xFF).toByte() else ((sample shr 8) and 0xFF).toByte()
    }

    @Test
    fun aStreamedResampleIsTheWholeArrayResample() {
        val pcm = second()
        val whole = AudioPcmExtractor.resamplePcm16Mono(pcm, sourceRate, 16_000)
        assertEquals("one second at 16 kHz", 16_000 * 2, whole.size)

        val streamed = PcmResampler16k(sourceRate, channels = 1, expectedBytes = whole.size.toLong())
        val chunkBytes = 1_000
        var at = 0
        while (at < pcm.size) {
            val n = minOf(chunkBytes, pcm.size - at)
            streamed.write(pcm, at, n)
            at += n
        }
        assertArrayEquals("chunked in, identical out", whole, streamed.finish())
    }

    @Test
    fun anUndeclaredLengthGrowsWithoutLosingSamples() {
        val pcm = second()
        val whole = AudioPcmExtractor.resamplePcm16Mono(pcm, sourceRate, 16_000)
        val streamed = PcmResampler16k(sourceRate, channels = 1, expectedBytes = -1L)
        var at = 0
        while (at < pcm.size) {
            val n = minOf(4_096, pcm.size - at)
            streamed.write(pcm, at, n)
            at += n
        }
        assertArrayEquals(whole, streamed.finish())
    }

    @Test
    fun stereoIsDownmixedToMono() {
        // 44.1 kHz stereo, one second: left channel 1000, right channel 3000 → 2000 mono.
        val frames = sourceRate
        val stereo = ByteArray(frames * 4)
        for (frame in 0 until frames) {
            stereo[frame * 4] = (1000 and 0xFF).toByte()
            stereo[frame * 4 + 1] = ((1000 shr 8) and 0xFF).toByte()
            stereo[frame * 4 + 2] = (3000 and 0xFF).toByte()
            stereo[frame * 4 + 3] = ((3000 shr 8) and 0xFF).toByte()
        }
        val resampler = PcmResampler16k(sourceRate, channels = 2)
        resampler.write(stereo, 0, stereo.size)
        val mono = resampler.finish()

        assertEquals("16 kHz mono", 16_000 * 2, mono.size)
        val first = (mono[0].toInt() and 0xFF) or (mono[1].toInt() shl 8)
        assertEquals("the channels are averaged", 2000, first)
    }

    @Test
    fun aWavOnDiskIsExtractedThroughTheStreamingPath() {
        val pcm = second()
        val wav = File(temp.root, "lecture.wav")
        WavWriter(wav, sourceRate).use { it.write(pcm, pcm.size) }

        val extracted = AudioPcmExtractor.extract16kMonoPcm(wav)

        assertArrayEquals(
            "the file's own samples are what the mapping produced",
            AudioPcmExtractor.resamplePcm16Mono(pcm, sourceRate, 16_000),
            extracted,
        )
    }

    @Test
    fun aWavWithAChunkBeforeItsDataIsStillRead() {
        // Desktop writers put a LIST/INFO chunk before `data`; the reader has to walk past it.
        val pcm = second()
        val plain = File(temp.root, "plain.wav")
        WavWriter(plain, sourceRate).use { it.write(pcm, pcm.size) }
        val bytes = plain.readBytes()
        val header = bytes.copyOfRange(0, 36) // RIFF…fmt chunk (12 + 8 + 16)
        val dataChunk = bytes.copyOfRange(36, bytes.size)
        val list = "LIST".toByteArray(Charsets.US_ASCII) + byteArrayOf(4, 0, 0, 0) + "INFO".toByteArray()
        val withList = File(temp.root, "annotated.wav")
        withList.writeBytes(header + list + dataChunk)

        assertArrayEquals(
            AudioPcmExtractor.extract16kMonoPcm(plain),
            AudioPcmExtractor.extract16kMonoPcm(withList),
        )
    }

    @Test
    fun aMissingOrTruncatedFileYieldsNothing() {
        assertNull(AudioPcmExtractor.extract16kMonoPcm(File(temp.root, "absent.wav")))
        val empty = File(temp.root, "empty.wav").apply { writeBytes(ByteArray(44)) }
        assertNull(AudioPcmExtractor.extract16kMonoPcm(empty))
    }
}

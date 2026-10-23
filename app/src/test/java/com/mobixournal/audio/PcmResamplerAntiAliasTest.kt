package com.mobixournal.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decimating 44.1 kHz to 16 kHz by keeping every third sample folds 8–22 kHz back into the speech
 * band; the recogniser then reads noise as if it were syllables. These pin the low-pass that now runs
 * before the pick: the band that would alias is gone, the speech band is untouched, and a constant
 * signal still comes out unchanged (DC at unity, no startup click).
 */
class PcmResamplerAntiAliasTest {

    private val sourceRate = 44_100

    /** One second of a mono tone as 16-bit PCM. */
    private fun tone(hz: Double, amplitude: Int = 10_000): ByteArray {
        val frames = sourceRate
        val pcm = ByteArray(frames * 2)
        for (frame in 0 until frames) {
            val sample = (amplitude * sin(2.0 * PI * hz * frame / sourceRate)).toInt()
            pcm[frame * 2] = (sample and 0xFF).toByte()
            pcm[frame * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun rms(pcm: ByteArray): Double {
        var sum = 0.0
        var frames = 0
        var at = 0
        while (at + 1 < pcm.size) {
            val sample = (pcm[at].toInt() and 0xFF) or (pcm[at + 1].toInt() shl 8)
            sum += sample.toDouble() * sample
            frames++
            at += 2
        }
        return if (frames == 0) 0.0 else sqrt(sum / frames)
    }

    @Test
    fun aToneAboveTheTargetNyquistIsFilteredOutInsteadOfAliased() {
        val source = tone(12_000.0)
        val resampled = AudioPcmExtractor.resamplePcm16Mono(source, sourceRate, 16_000)
        assertEquals("one second at 16 kHz", 16_000 * 2, resampled.size)

        val kept = rms(resampled) / rms(source)
        assertTrue("12 kHz should be attenuated, kept $kept of it", kept < 0.1)
    }

    @Test
    fun speechBandTonesComeThrough() {
        val source = tone(1_000.0)
        val resampled = AudioPcmExtractor.resamplePcm16Mono(source, sourceRate, 16_000)
        val kept = rms(resampled) / rms(source)
        assertTrue("1 kHz should survive, kept only $kept of it", kept > 0.85)
        assertTrue("1 kHz should not be boosted, kept $kept of it", kept < 1.15)
    }

    @Test
    fun aConstantSignalPassesAtUnityFromTheFirstSample() {
        val frames = 4_410
        val constant = ByteArray(frames * 2)
        for (frame in 0 until frames) {
            constant[frame * 2] = (2000 and 0xFF).toByte()
            constant[frame * 2 + 1] = ((2000 shr 8) and 0xFF).toByte()
        }
        val resampled = AudioPcmExtractor.resamplePcm16Mono(constant, sourceRate, 16_000)

        var at = 0
        while (at + 1 < resampled.size) {
            val sample = (resampled[at].toInt() and 0xFF) or (resampled[at + 1].toInt() shl 8)
            assertEquals("a constant must stay at 2000", 2000, sample)
            at += 2
        }
    }

    @Test
    fun theFilterRunsOnlyWhenSomethingIsBeingThrownAway() {
        // 16 kHz in, 16 kHz out: no decimation, so every sample is untouched.
        val source = tone(1_000.0).copyOfRange(0, 16_000 * 2)
        val passthrough = AudioPcmExtractor.resamplePcm16Mono(source, 16_000, 16_000)
        assertTrue("same rate, same bytes", passthrough.contentEquals(source))
    }
}

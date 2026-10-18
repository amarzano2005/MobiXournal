package com.mobixournal.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Extracts and downsamples audio amplitude levels from an audio file into a normalized
 * list of float values (0.0f..1.0f) for waveform visualization.
 */
object AudioWaveformExtractor {

    private const val DEFAULT_SAMPLE_COUNT = 80

    /** In-memory cache of extracted waveforms: audio filename -> list of normalized amplitudes. */
    private val cache = mutableMapOf<String, List<Float>>()

    /**
     * Extracts [targetSamples] normalized amplitude points from [audioFile].
     * If the file is a 16-bit PCM WAV, reads the actual sample bytes;
     * otherwise, produces an interpolated pseudo-waveform derived from file chunks.
     */
    fun extract(audioFile: File, targetSamples: Int = DEFAULT_SAMPLE_COUNT): List<Float> {
        val cacheKey = "${audioFile.absolutePath}_${audioFile.length()}_$targetSamples"
        cache[cacheKey]?.let { return it }

        if (!audioFile.exists() || audioFile.length() <= 0L) {
            return defaultWaveform(targetSamples)
        }

        val result = if (audioFile.extension.equals("wav", ignoreCase = true)) {
            extractWavAmplitudes(audioFile, targetSamples)
        } else {
            extractGenericAmplitudes(audioFile, targetSamples)
        }

        cache[cacheKey] = result
        return result
    }

    private fun extractWavAmplitudes(file: File, targetSamples: Int): List<Float> {
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return defaultWaveform(targetSamples)
            // Skip 44-byte RIFF WAV header
            val dataSize = bytes.size - 44
            val sampleCount = dataSize / 2
            if (sampleCount <= 0) return defaultWaveform(targetSamples)

            val blockSize = max(1, sampleCount / targetSamples)
            val amplitudes = mutableListOf<Float>()

            for (i in 0 until targetSamples) {
                var maxVal = 0
                val startIdx = 44 + (i * blockSize * 2)
                val endIdx = minOf(bytes.size - 2, startIdx + (blockSize * 2))

                var ptr = startIdx
                while (ptr <= endIdx) {
                    val low = bytes[ptr].toInt() and 0xFF
                    val high = bytes[ptr + 1].toInt()
                    val sample = (high shl 8) or low
                    maxVal = max(maxVal, abs(sample))
                    ptr += 2
                }
                // Normalize 16-bit signed PCM range (0..32767) to (0.1..1.0)
                val normalized = (maxVal.toFloat() / 32768f).coerceIn(0.12f, 1.0f)
                amplitudes.add(normalized)
            }
            amplitudes
        }.getOrElse { defaultWaveform(targetSamples) }
    }

    private fun extractGenericAmplitudes(file: File, targetSamples: Int): List<Float> {
        return runCatching {
            val bytes = file.readBytes()
            val blockSize = max(1, bytes.size / targetSamples)
            val amplitudes = mutableListOf<Float>()

            for (i in 0 until targetSamples) {
                val start = i * blockSize
                val end = minOf(bytes.size, start + blockSize)
                var sum = 0L
                for (j in start until end) {
                    sum += abs(bytes[j].toInt())
                }
                val avg = if (end > start) (sum / (end - start)).toFloat() else 30f
                val norm = (avg / 128f).coerceIn(0.15f, 1.0f)
                amplitudes.add(norm)
            }
            amplitudes
        }.getOrElse { defaultWaveform(targetSamples) }
    }

    private fun defaultWaveform(count: Int): List<Float> =
        List(count) { i ->
            val factor = kotlin.math.sin(i.toDouble() * 0.25).toFloat()
            0.2f + 0.3f * abs(factor)
        }
}

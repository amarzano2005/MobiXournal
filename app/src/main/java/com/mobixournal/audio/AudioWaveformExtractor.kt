package com.mobixournal.audio

import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Extracts and downsamples audio amplitude levels from an audio file into a normalized
 * list of float values (0.0f..1.0f) for waveform visualization.
 *
 * The file is **read in chunks**, not loaded: a waveform is a dozen or so numbers per second of
 * audio, and holding a lecture-sized recording (160 MB of WAV) just to draw a hundred bars was a
 * second allocation with no purpose. The extraction keeps one read buffer whatever the file weighs.
 */
object AudioWaveformExtractor {

    private const val DEFAULT_SAMPLE_COUNT = 80

    /** The chunk every pass reads through. */
    private const val READ_BYTES = 64 * 1024

    /** In-memory cache of extracted waveforms: audio filename -> list of normalized amplitudes. */
    private val cache = mutableMapOf<String, List<Float>>()

    /**
     * Extracts [targetSamples] normalized amplitude points from [audioFile].
     * If the file is a 16-bit PCM WAV, reads the actual sample bytes;
     * otherwise, produces an interpolated pseudo-waveform derived from file chunks.
     */
    fun extract(audioFile: File, targetSamples: Int = DEFAULT_SAMPLE_COUNT): List<Float> = synchronized(cache) {
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
        result
    }

    /** The loudest sample of each block of a canonical 16-bit PCM WAV, streamed block by block. */
    private fun extractWavAmplitudes(file: File, targetSamples: Int): List<Float> {
        return runCatching {
            val dataBytes = file.length() - WavWriter.HEADER_BYTES
            if (dataBytes <= 0L) return defaultWaveform(targetSamples)
            val sampleCount = dataBytes / 2
            val blockSamples = max(1L, sampleCount / targetSamples)
            val loudest = IntArray(targetSamples)

            file.inputStream().buffered(READ_BYTES).use { stream ->
                skipFully(stream, WavWriter.HEADER_BYTES.toLong())
                val buffer = ByteArray(READ_BYTES)
                var sample = 0L
                // A sample can straddle a read boundary, so the low byte is carried over.
                var pendingLow = -1
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    var at = 0
                    if (pendingLow >= 0 && n > 0) {
                        val value = (pendingLow or (buffer[0].toInt() shl 8)).toShort().toInt()
                        record(loudest, sample, blockSamples, value)
                        sample++
                        pendingLow = -1
                        at = 1
                    }
                    while (at + 1 < n) {
                        val value = ((buffer[at].toInt() and 0xFF) or (buffer[at + 1].toInt() shl 8)).toShort().toInt()
                        record(loudest, sample, blockSamples, value)
                        sample++
                        at += 2
                    }
                    if (at < n) pendingLow = buffer[at].toInt() and 0xFF
                }
            }
            loudest.map { (abs(it).toFloat() / 32768f).coerceIn(0.12f, 1.0f) }
        }.getOrElse { defaultWaveform(targetSamples) }
    }

    /** The mean byte level of each block of an unknown (compressed) container, streamed the same way. */
    private fun extractGenericAmplitudes(file: File, targetSamples: Int): List<Float> {
        return runCatching {
            val total = file.length()
            if (total <= 0L) return defaultWaveform(targetSamples)
            val blockBytes = max(1L, total / targetSamples)
            val sums = LongArray(targetSamples)
            val counts = LongArray(targetSamples)

            file.inputStream().buffered(READ_BYTES).use { stream ->
                val buffer = ByteArray(READ_BYTES)
                var position = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    for (i in 0 until n) {
                        val block = (position / blockBytes).toInt().coerceAtMost(targetSamples - 1)
                        sums[block] += abs(buffer[i].toInt())
                        counts[block]++
                        position++
                    }
                }
            }
            sums.indices.map { i ->
                val avg = if (counts[i] > 0) (sums[i] / counts[i]).toFloat() else 30f
                (avg / 128f).coerceIn(0.15f, 1.0f)
            }
        }.getOrElse { defaultWaveform(targetSamples) }
    }

    private fun record(loudest: IntArray, sample: Long, blockSamples: Long, value: Int) {
        val block = (sample / blockSamples).toInt().coerceAtMost(loudest.size - 1)
        loudest[block] = max(loudest[block], abs(value))
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped <= 0) {
                if (stream.read() < 0) return
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun defaultWaveform(count: Int): List<Float> =
        List(count) { i ->
            val factor = kotlin.math.sin(i.toDouble() * 0.25).toFloat()
            0.2f + 0.3f * abs(factor)
        }
}

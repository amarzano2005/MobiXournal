package com.mobixournal.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * The amplitude envelope of a **compressed** recording — decoded, not guessed.
 *
 * A `.mobix` bundles Opus/Ogg (AAC when the device has no Opus encoder), and a waveform has to come
 * from what the recording *sounds like*. Averaging the compressed bytes instead answers with the shape
 * of a codec's bit rate: a nearly constant number per block, which is the flat band the player was
 * drawing. So the file is decoded through the platform's own demuxer and decoder, one buffer at a
 * time, and each of [targetSamples] blocks keeps the **loudest** frame that fell in it — the same
 * reduction the WAV path does, reached through a decoder.
 *
 * Nothing is held but the block peaks: the decoder hands over one buffer at a time and it is released
 * immediately, so a lecture-sized recording costs the same as a short note. Returning `null` means
 * "this is not something I can decode" (no audio track, no codec, a hostile file) and the caller falls
 * back to its own estimate; cancellation is rethrown, never turned into a `null`.
 */
internal object CompressedWaveform {

    /** How long to wait for a buffer before looking at the clock again (and honouring cancellation). */
    private const val TIMEOUT_US = 10_000L

    fun amplitudes(file: File, targetSamples: Int, checkCancelled: () -> Unit = {}): List<Float>? {
        require(targetSamples > 0)
        return try {
            decode(file, targetSamples, checkCancelled)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // No codec, no track, a provider that vanished: the caller has a fallback, and a preview is
            // never worth taking the editor down for.
            null
        }
    }

    private fun decode(file: File, targetSamples: Int, checkCancelled: () -> Unit): List<Float>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            if (durationUs <= 0L) return null

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()

            val peaks = LongArray(targetSamples)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var elapsedUs = 0L
            while (!outputDone) {
                checkCancelled()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: sampleRate
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    }
                    else -> if (outIndex >= 0) {
                        readPeak(decoder, outIndex, info, channels)?.let { (peak, frames) ->
                            val block = (elapsedUs * targetSamples / durationUs).toInt()
                                .coerceIn(0, targetSamples - 1)
                            peaks[block] = max(peaks[block], peak.toLong())
                            elapsedUs += frames * 1_000_000L / sampleRate
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return normalise(peaks)
        } finally {
            codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
            runCatching { extractor.release() }
        }
    }

    /** The loudest 16-bit sample in one decoded PCM buffer, and how many frames it carried. */
    private fun readPeak(
        decoder: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        channels: Int,
    ): Pair<Int, Long>? {
        if (info.size <= 0) return null
        val buffer = decoder.getOutputBuffer(index) ?: return null
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val samples = buffer.asShortBuffer()
        var peak = 0
        while (samples.hasRemaining()) peak = max(peak, abs(samples.get().toInt()))
        return peak to (info.size / (2L * channels))
    }

    /**
     * Block peaks to the 0..1 band the waveform draws, **forward-filled** for a block the decoder never
     * reached: a codec that ends early must not paint a hole where the recording is audible.
     */
    private fun normalise(peaks: LongArray): List<Float> {
        var last = 0L
        return peaks.map { peak ->
            if (peak > 0) last = peak
            (last.toFloat() / 32768f).coerceIn(0.02f, 1.0f)
        }
    }
}

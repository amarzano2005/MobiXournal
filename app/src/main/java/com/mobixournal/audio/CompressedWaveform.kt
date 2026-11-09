package com.mobixournal.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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
 * A take **past a minute** is sampled instead of decoded whole: each block reads a few short windows
 * seeked to inside it ([WaveformScan]), a fixed cost per bar whatever the recording runs to. Decoding
 * a half-hour or two-hour lecture buffer by buffer was minutes of work before the first bar appeared,
 * which is a waveform that never arrives (`docs/audio.md`).
 *
 * Nothing is held but the block peaks: the decoder hands over one buffer at a time and it is released
 * immediately, so a lecture-sized recording costs the same as a short note. Returning `null` means
 * "this is not something I can decode" (no audio track, no codec, a hostile file) and the caller falls
 * back to its own estimate; cancellation is rethrown, never turned into a `null`.
 */
internal object CompressedWaveform {

    /** How long to wait for a buffer before looking at the clock again (and honouring cancellation). */
    private const val TIMEOUT_US = 10_000L

    /** Windows read inside one block of a long take — enough for a peak, cheap at any length. */
    private const val WINDOWS_PER_BLOCK = 3

    /** Buffers one window may decode before it answers: a decoder that stalls cannot hold the pass. */
    private const val BUFFERS_PER_WINDOW = 24

    /** Consecutive empty polls that end a pass — a codec with nothing to give must not spin forever. */
    private const val IDLE_LIMIT = 50

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
            val durationUs = durationOf(format, file) ?: return null

            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            if (WaveformScan.shouldSample(durationUs)) {
                sampleWholeTrack(decoder, extractor, info, durationUs, targetSamples, checkCancelled)
                    ?.let { return it }
                // A stream that cannot be seeked still deserves an envelope: decode it whole, as before.
                rewind(extractor, decoder)
            }
            return decodeWhole(decoder, extractor, info, durationUs, targetSamples, checkCancelled)
        } finally {
            codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
            runCatching { extractor.release() }
        }
    }

    /**
     * The take's length, from the track format or — a demuxer that publishes none, which an Ogg file
     * can — from the platform's own metadata reader. Null when neither knows, which is the only case
     * that leaves the caller guessing.
     */
    private fun durationOf(format: MediaFormat, file: File): Long? {
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            val published = format.getLong(MediaFormat.KEY_DURATION)
            if (published > 0L) return published
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0L }?.times(1_000L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * The envelope of a **long** take, read from short windows seeked to inside each block.
     *
     * The cost is per block, not per second: a window is a fixed number of decoded buffers, so a
     * two-hour lecture answers as fast as a two-minute note. Null means the stream refused to be
     * seeked, and the caller must decode it whole instead.
     */
    private fun sampleWholeTrack(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        info: MediaCodec.BufferInfo,
        durationUs: Long,
        targetSamples: Int,
        checkCancelled: () -> Unit,
    ): List<Float>? {
        val peaks = LongArray(targetSamples)
        for (block in 0 until targetSamples) {
            for (window in 0 until WINDOWS_PER_BLOCK) {
                checkCancelled()
                val from = WaveformScan.windowStart(block, targetSamples, window, WINDOWS_PER_BLOCK, durationUs)
                val to = WaveformScan.windowEnd(block, targetSamples, window, WINDOWS_PER_BLOCK, durationUs)
                if (!seek(extractor, decoder, from)) return null
                val peak = windowPeak(decoder, extractor, info, to, checkCancelled)
                peaks[block] = max(peaks[block], peak.toLong())
            }
        }
        return normalise(peaks)
    }

    /** The loudest sample decoded from the window's start until the stream passes [toUs]. */
    private fun windowPeak(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        info: MediaCodec.BufferInfo,
        toUs: Long,
        checkCancelled: () -> Unit,
    ): Int {
        var inputDone = false
        var peak = 0
        var outputs = 0
        var idle = 0
        while (outputs < BUFFERS_PER_WINDOW) {
            checkCancelled()
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    val size = if (buffer == null) -1 else runCatching { extractor.readSampleData(buffer, 0) }
                        .getOrDefault(-1)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex >= 0 -> {
                    idle = 0
                    outputs++
                    if (info.size > 0) peak = max(peak, peakOf(decoder, outIndex, info))
                    val last = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val at = info.presentationTimeUs
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (last || (toUs > 0L && at >= toUs)) return peak
                }
                outIndex != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    idle++
                    if (idle >= IDLE_LIMIT) return peak
                }
            }
        }
        return peak
    }

    /**
     * The whole stream, buffer by buffer — the exact envelope for a take short enough to afford it,
     * and the fallback for one whose demuxer will not seek.
     */
    private fun decodeWhole(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        info: MediaCodec.BufferInfo,
        durationUs: Long,
        targetSamples: Int,
        checkCancelled: () -> Unit,
    ): List<Float> {
        var sampleRate = 48_000
        var channels = 1
        val peaks = LongArray(targetSamples)
        var inputDone = false
        var outputDone = false
        var elapsedUs = 0L
        var idle = 0
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
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    idle++
                    if (idle >= IDLE_LIMIT) outputDone = true
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val out = decoder.outputFormat
                    runCatching {
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: sampleRate
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    }
                }
                else -> if (outIndex >= 0) {
                    idle = 0
                    if (info.size > 0) {
                        val block = if (durationUs > 0L) {
                            (elapsedUs * targetSamples / durationUs).toInt().coerceIn(0, targetSamples - 1)
                        } else {
                            0
                        }
                        peaks[block] = max(peaks[block], peakOf(decoder, outIndex, info).toLong())
                        elapsedUs += (info.size / (2L * channels)) * 1_000_000L / sampleRate
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        return normalise(peaks)
    }

    /** Move the demuxer to [timeUs] and clear the decoder, so the window starts on a decoded frame. */
    private fun seek(extractor: MediaExtractor, decoder: MediaCodec, timeUs: Long): Boolean {
        val moved = runCatching {
            extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            extractor.sampleTime >= 0
        }.getOrDefault(false)
        if (!moved) return false
        return runCatching { decoder.flush() }.isSuccess
    }

    /** Back to the first sample with a clean decoder, whatever the last pass left behind. */
    private fun rewind(extractor: MediaExtractor, decoder: MediaCodec) {
        runCatching { extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }
        runCatching { decoder.flush() }
    }

    /** The loudest 16-bit sample in one decoded PCM buffer. */
    private fun peakOf(decoder: MediaCodec, index: Int, info: MediaCodec.BufferInfo): Int {
        if (info.size <= 0) return 0
        val buffer = decoder.getOutputBuffer(index) ?: return 0
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val samples = buffer.asShortBuffer()
        var peak = 0
        while (samples.hasRemaining()) peak = max(peak, abs(samples.get().toInt()))
        return peak
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

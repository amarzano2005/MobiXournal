package com.mobixournal.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.InputStream

/**
 * Decodes audio files (WAV, Ogg/Opus, or other supported formats) into
 * standard 16 kHz 16-bit mono PCM bytes for speech recognition.
 *
 * Both paths **stream**: the recording is never read into one array to be converted — a lecture-sized
 * recording (a 30-minute WAV is ~160 MB) would need several times its own size in heap, which is what
 * an `OutOfMemoryError` right after a long recording used to be. Each pass keeps one read chunk plus
 * the 16 kHz output the engine needs anyway ([PcmResampler16k]).
 */
object AudioPcmExtractor {

    /** Bytes read from the source per pass — small enough to be free next to the recording. */
    private const val READ_BYTES = 64 * 1024

    /**
     * Extracts 16 kHz 16-bit mono PCM samples from [file].
     * Supports RIFF WAV (streamed and resampled) and compressed containers
     * (Ogg/Opus, AAC) via [MediaExtractor] and [MediaCodec].
     */
    fun extract16kMonoPcm(file: File): ByteArray? {
        if (!file.isFile || file.length() == 0L) return null
        if (file.name.endsWith(".wav", ignoreCase = true)) return wav16kMono(file)
        return media16kMono(file)
    }

    // ---- WAV ------------------------------------------------------------------------------------

    /**
     * A canonical RIFF/WAVE file, read chunk by chunk: `fmt ` says the rate and channel count, `data`
     * says how much there is, and every other chunk is skipped. The samples themselves are handed to
     * the resampler as they arrive.
     */
    private fun wav16kMono(file: File): ByteArray? = runCatching {
        file.inputStream().buffered(READ_BYTES).use { stream ->
            val header = ByteArray(12)
            if (readFully(stream, header, 12) < 12) return null
            if (ascii(header, 0) != "RIFF" || ascii(header, 8) != "WAVE") return null
            var sampleRate = 44_100
            var channels = 1
            var bits = 16
            var dataBytes = -1L
            while (dataBytes < 0) {
                val chunk = ByteArray(8)
                if (readFully(stream, chunk, 8) < 8) return null
                val size = le32(chunk, 4)
                when (ascii(chunk, 0)) {
                    "fmt " -> {
                        // The fmt chunk is 16 bytes for PCM; read what it declares, ignore padding.
                        val fmt = ByteArray(minOf(size, 64L).toInt())
                        if (readFully(stream, fmt, fmt.size) < fmt.size) return null
                        channels = le16(fmt, 2).coerceAtLeast(1)
                        sampleRate = le32(fmt, 4).toInt().takeIf { it > 0 } ?: sampleRate
                        bits = le16(fmt, 14).takeIf { it > 0 } ?: bits
                        skip(stream, size - fmt.size)
                    }
                    "data" -> dataBytes = size
                    else -> skip(stream, size)
                }
            }
            if (bits != BITS || dataBytes <= 0L) return null
            val frameBytes = channels * BYTES_PER_SAMPLE
            val resampler = PcmResampler16k(
                sourceRate = sampleRate,
                channels = channels,
                expectedBytes = dataBytes / frameBytes * BYTES_PER_SAMPLE,
            )
            val buffer = ByteArray(READ_BYTES)
            var left = dataBytes
            while (left > 0) {
                val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n <= 0) break
                resampler.write(buffer, 0, n)
                left -= n
            }
            resampler.finish()
        }
    }.getOrNull()

    private fun skip(stream: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped <= 0) {
                // A provider that cannot skip still has to be read past.
                if (stream.read() < 0) return
                left--
            } else {
                left -= skipped
            }
        }
    }

    // ---- compressed (Ogg/Opus, AAC, …) ----------------------------------------------------------

    private fun media16kMono(file: File): ByteArray? = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIdx = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIdx = i
                format = f
                break
            }
        }
        if (trackIdx < 0 || format == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(trackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            48_000
        }
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            1
        }

        // The duration lets the 16 kHz output be sized up front; without it the resampler grows in
        // 1 MiB steps as the stream arrives.
        val expectedBytes = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION) / 1_000_000L * PcmResampler16k.TARGET_RATE * BYTES_PER_SAMPLE
        } else {
            -1L
        }
        val resampler = PcmResampler16k(sourceRate = sampleRate, channels = channels, expectedBytes = expectedBytes)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIdx = codec.dequeueInputBuffer(5_000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 5_000L)
            if (outIdx >= 0) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    // Straight into the resampler: decoded PCM is never accumulated as a whole.
                    val chunk = ByteArray(info.size)
                    outBuf.get(chunk)
                    resampler.write(chunk, 0, chunk.size)
                }
                codec.releaseOutputBuffer(outIdx, false)
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        resampler.finish()
    }.getOrNull()

    // ---- whole-array form (tests, and a caller that already has the samples) ----------------------

    /**
     * The resampler as a function over an array in memory: `out[i] = in[i * srcRate / dstRate]`, mono
     * 16-bit. The file paths above stream through the same mapping ([PcmResampler16k]) — this form is
     * for samples that are already in hand, not for a recording on disk.
     */
    fun resamplePcm16Mono(srcPcm: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
        if (srcRate == dstRate || srcRate <= 0) return srcPcm
        val outSamples = (srcPcm.size / BYTES_PER_SAMPLE).toLong() * dstRate / srcRate
        val resampler = PcmResampler16k(
            sourceRate = srcRate,
            channels = 1,
            expectedBytes = outSamples * BYTES_PER_SAMPLE,
            targetRate = dstRate,
        )
        resampler.write(srcPcm, 0, srcPcm.size)
        return resampler.finish()
    }

    // ---- bytes ----------------------------------------------------------------------------------

    private const val BYTES_PER_SAMPLE = 2
    private const val BITS = 16

    private fun readFully(stream: InputStream, buffer: ByteArray, count: Int): Int {
        var read = 0
        while (read < count) {
            val n = stream.read(buffer, read, count - read)
            if (n <= 0) break
            read += n
        }
        return read
    }

    private fun ascii(bytes: ByteArray, at: Int): String =
        buildString { for (i in 0 until 4) append((bytes[at + i].toInt() and 0xFF).toChar()) }

    private fun le16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)
}

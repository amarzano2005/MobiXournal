package com.mobixournal.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Decodes and resamples audio files (WAV, Ogg/Opus, or other supported formats) into
 * standard 16 kHz 16-bit mono PCM bytes for speech recognition.
 */
object AudioPcmExtractor {

    /**
     * Extracts 16 kHz 16-bit mono PCM samples from [file].
     * Supports RIFF WAV (by stripping the header and resampling) and compressed containers
     * (Ogg/Opus, AAC) via [MediaExtractor] and [MediaCodec].
     */
    fun extract16kMonoPcm(file: File): ByteArray? {
        if (!file.isFile || file.length() == 0L) return null
        if (file.name.endsWith(".wav", ignoreCase = true)) {
            if (file.length() <= WavWriter.HEADER_BYTES) return null
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            if (bytes.size <= WavWriter.HEADER_BYTES) return null
            val rawPcm = bytes.copyOfRange(WavWriter.HEADER_BYTES, bytes.size)
            return resamplePcm16Mono(rawPcm, 44100, 16000)
        }
        val decoded = decodeMediaToPcm(file) ?: return null
        return resamplePcm16Mono(decoded.first, decoded.second, 16000)
    }

    private fun decodeMediaToPcm(file: File): Pair<ByteArray, Int>? = runCatching {
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
            48000
        }
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            1
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIdx = codec.dequeueInputBuffer(5000L)
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
            val outIdx = codec.dequeueOutputBuffer(info, 5000L)
            if (outIdx >= 0) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    outBuf.get(chunk)
                    pcmOut.write(chunk)
                }
                codec.releaseOutputBuffer(outIdx, false)
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val raw = pcmOut.toByteArray()
        Pair(if (channels > 1) downmixStereoToMono(raw) else raw, sampleRate)
    }.getOrNull()

    private fun downmixStereoToMono(stereoPcm: ByteArray): ByteArray {
        val numSamples = stereoPcm.size / 4
        val mono = ByteArray(numSamples * 2)
        var src = 0
        var dst = 0
        while (src + 3 < stereoPcm.size) {
            val left = (stereoPcm[src].toInt() and 0xFF) or (stereoPcm[src + 1].toInt() shl 8)
            val right = (stereoPcm[src + 2].toInt() and 0xFF) or (stereoPcm[src + 3].toInt() shl 8)
            val avg = ((left.toShort() + right.toShort()) / 2).toShort()
            mono[dst] = (avg.toInt() and 0xFF).toByte()
            mono[dst + 1] = ((avg.toInt() shr 8) and 0xFF).toByte()
            src += 4
            dst += 2
        }
        return mono
    }

    fun resamplePcm16Mono(srcPcm: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
        if (srcRate == dstRate || srcRate <= 0) return srcPcm
        val totalSamples = srcPcm.size / 2
        val outSamples = (totalSamples.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
        val out = ByteArray(outSamples * 2)
        for (i in 0 until outSamples) {
            val srcIdx = ((i.toLong() * srcRate) / dstRate).toInt().coerceIn(0, totalSamples - 1) * 2
            out[i * 2] = srcPcm[srcIdx]
            out[i * 2 + 1] = srcPcm[srcIdx + 1]
        }
        return out
    }
}

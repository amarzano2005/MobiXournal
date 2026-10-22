package com.mobixournal.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Converts a recording to **M4A (MP4/AAC)** — the shape a phone gallery, a browser, a video editor and
 * a cloud assistant all accept — before it leaves the app as a standalone file
 * ([AudioStandaloneExporter]).
 *
 * Inside the app nothing needs this: the recorder writes Ogg/Opus for a `.mobix` and WAV for a `.xopp`,
 * and the player decodes both ([AudioSession]). Outside the app it matters a great deal — an uploader
 * that does not recognise Ogg/Opus routinely reports that the file "was not processed by the audio
 * component" and injects the raw bytes into the conversation as text, and a lecture-length WAV is
 * hundreds of megabytes. AAC is the one encoder every Android device is required to provide, so the
 * transcode always has a destination format: decode the source with [MediaExtractor] + [MediaCodec],
 * re-encode mono at [BIT_RATE] and mux the result into an MP4 container.
 *
 * Nothing is thrown at the caller. An unsupported source, a busy codec or an encoder that refuses the
 * stream makes [toM4a] return false, and the exporter then copies the recording as it is: a plain file
 * in an awkward format is worth more than a lost recording.
 */
object AudioTranscoder {

    /** The extension of what this writes — also what a standalone export is named after. */
    const val CONTAINER_SUFFIX = "m4a"

    /** The MIME type that goes with [CONTAINER_SUFFIX] in the media store. */
    const val MIME_TYPE = "audio/mp4"

    /** ~48 kbps mono: speech stays legible for transcription at ~21 MB an hour. */
    const val BIT_RATE = 48_000

    private const val MIME_TYPE_AAC = "audio/mp4a-latm"
    private const val TIMEOUT_US = 10_000L
    private const val BYTES_PER_SAMPLE = 2
    private const val MAX_WAIT_STEPS = 200
    private const val MAX_INPUT_BYTES = 64 * 1024

    /**
     * The name a standalone copy of [sourceName] carries: `2026-08-02T09-14-33.ogg` becomes
     * `2026-08-02T09-14-33.m4a`, so an export stays sortable beside the recording it came from.
     */
    fun exportedFileName(sourceName: String): String {
        val stem = sourceName.substringBeforeLast('.', sourceName).ifBlank { "recording" }
        return "$stem.$CONTAINER_SUFFIX"
    }

    /**
     * Write [source] into [target] as MP4/AAC, returning true only when [target] holds an MP4 file
     * with audio in it. A failed attempt leaves no half-written file behind.
     */
    fun toM4a(source: File, target: File): Boolean {
        if (!source.isFile || source.length() == 0L) return false
        target.parentFile?.mkdirs()
        target.delete()
        val written = runCatching { transcode(source, target) }.getOrDefault(false)
        if (!written || !target.isFile || target.length() == 0L) {
            target.delete()
            return false
        }
        return true
    }

    // ---- decode -> encode -> mux ----------------------------------------------------------------

    private fun transcode(source: File, target: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = audioTrack(extractor) ?: return false
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inputFormat.integerOr(MediaFormat.KEY_SAMPLE_RATE, 48_000)
            val channels = inputFormat.integerOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)

            decoder = MediaCodec.createDecoderByType(mime).also {
                it.configure(inputFormat, null, null, 0)
                it.start()
            }
            encoder = MediaCodec.createEncoderByType(MIME_TYPE_AAC).also {
                it.configure(aacFormat(sampleRate, channels), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            return pipe(extractor, decoder, AacMuxer(encoder, muxer, sampleRate, channels))
        } finally {
            decoder?.runCatching { stop() }
            decoder?.release()
            encoder?.runCatching { stop() }
            encoder?.release()
            // The muxer writes its index on stop, which is what makes the file playable at all.
            muxer?.runCatching { stop() }
            muxer?.release()
            extractor.release()
        }
    }

    /** Feed the decoder from [extractor] and its PCM into [sink] until the stream ends. */
    private fun pipe(extractor: MediaExtractor, decoder: MediaCodec, sink: AacMuxer): Boolean {
        val info = MediaCodec.BufferInfo()
        var sourceExhausted = false
        var decoderDone = false
        while (!decoderDone) {
            if (!sourceExhausted) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val input = decoder.getInputBuffer(inIndex) ?: return false
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sourceExhausted = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex >= 0) {
                decoderDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                // Codec configuration is not sound: feeding it to the encoder would put a click in
                // the file.
                val audio = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                if (info.size > 0 && audio) {
                    val pcm = decoder.getOutputBuffer(outIndex) ?: return false
                    pcm.position(info.offset)
                    pcm.limit(info.offset + info.size)
                    sink.write(pcm)
                }
                decoder.releaseOutputBuffer(outIndex, false)
            }
        }
        return sink.finish()
    }

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    private fun aacFormat(sampleRate: Int, channels: Int): MediaFormat =
        MediaFormat.createAudioFormat(MIME_TYPE_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        }

    private fun MediaFormat.integerOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    // ---- the AAC half ---------------------------------------------------------------------------

    /**
     * The encoder and the muxer as one sink: [write] hands a decoded PCM chunk over in whole frames and
     * empties the encoder while it goes (an encoder with every output buffer spent stops taking input),
     * [finish] flushes the tail and reports whether the file ended up with audio in it.
     */
    private class AacMuxer(
        private val encoder: MediaCodec,
        private val muxer: MediaMuxer,
        private val sampleRate: Int,
        private val channels: Int,
    ) {
        private val info = MediaCodec.BufferInfo()
        private val frameBytes = BYTES_PER_SAMPLE * channels
        private var trackIndex = -1
        private var started = false
        private var wroteAudio = false
        private var presentationUs = 0L

        fun write(pcm: ByteBuffer) {
            val end = pcm.limit()
            var position = pcm.position()
            while (position < end) {
                val index = awaitInputBuffer()
                val input = encoder.getInputBuffer(index) ?: return
                input.clear()
                val take = minOf(end - position, input.remaining()) / frameBytes * frameBytes
                if (take <= 0) return
                val slice = pcm.duplicate()
                slice.position(position)
                slice.limit(position + take)
                input.put(slice)
                encoder.queueInputBuffer(index, 0, take, presentationUs, 0)
                presentationUs += (take / frameBytes).toLong() * 1_000_000L / sampleRate
                position += take
                drain()
            }
        }

        fun finish(): Boolean {
            val index = awaitInputBuffer()
            encoder.queueInputBuffer(index, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            repeat(MAX_WAIT_STEPS) { if (drain()) return started && wroteAudio }
            return started && wroteAudio
        }

        /** An input buffer, emptying the encoder's output while we wait for one. */
        private fun awaitInputBuffer(): Int {
            repeat(MAX_WAIT_STEPS) {
                val index = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) return index
                drain()
            }
            error("AAC encoder stopped accepting input")
        }

        /** Move whatever the encoder has produced into the muxer; true once end-of-stream is written. */
        private fun drain(): Boolean {
            while (true) {
                when (val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> startTrack()
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                    else -> {
                        if (index < 0) return false
                        val output = encoder.getOutputBuffer(index)
                        if (output != null && info.size > 0 && started) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, output, info)
                            wroteAudio = true
                        }
                        val end = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(index, false)
                        if (end) return true
                    }
                }
            }
        }

        private fun startTrack() {
            if (started) return
            trackIndex = muxer.addTrack(encoder.outputFormat)
            muxer.start()
            started = true
        }
    }
}

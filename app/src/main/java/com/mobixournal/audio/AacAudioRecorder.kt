package com.mobixournal.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-compatibility compressed audio recorder using AAC (`audio/mp4a-latm`) in an **M4A/MP4** container.
 *
 * Supported out of the box on 100% of Android devices (hardware/software AAC encoder guaranteed by CDD).
 * Records 44.1 kHz mono speech profile at ~32 kbps (~15 MB per hour, ~1.5 MB for 7 minutes),
 * providing the ultimate reliable fallback when Opus/OGG encoder or muxer is unavailable.
 */
class AacAudioRecorder {

    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private var muxerStarted = false
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    @Volatile
    private var presentationTimeUs: Long = 0L

    /** The file being written, or null when idle. */
    var file: File? = null
        private set

    /** True between a successful [start] and its [stop]. */
    val isRecording: Boolean get() = running.get()

    /** True while recording is temporarily paused. */
    val isPaused: Boolean get() = paused.get()

    /** Optional listener receiving raw 16-bit mono PCM chunks `(buffer, bytesRead, sampleRate)`. */
    @Volatile
    var onPcmData: ((ByteArray, Int, Int) -> Unit)? = null

    fun pause() {
        if (running.get()) paused.set(true)
    }

    fun resume() {
        if (running.get()) paused.set(false)
    }

    /**
     * Start capturing microphone audio to [target] using AAC compression.
     * Returns false if mic or encoder cannot be opened.
     */
    @SuppressLint("MissingPermission")
    fun start(target: File): Boolean {
        if (running.get()) return false
        val rec = initAudioRecord() ?: return false
        val cod = initMediaCodec() ?: run {
            rec.release()
            return false
        }
        val mux = initMediaMuxer(target) ?: run {
            cod.release()
            rec.release()
            return false
        }

        record = rec
        codec = cod
        muxer = mux
        file = target
        trackIndex = -1
        muxerStarted = false
        presentationTimeUs = 0L
        paused.set(false)
        running.set(true)

        rec.startRecording()
        cod.start()

        thread = Thread({ pump(rec, cod, mux) }, "aac-audio-record").also { it.start() }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun initAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuffer <= 0) return null
        val bufferBytes = minBuffer * BUFFER_MULTIPLIER
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )
        for (source in sources) {
            val rec = runCatching {
                AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufferBytes)
            }.getOrNull() ?: continue
            if (rec.state == AudioRecord.STATE_INITIALIZED) return rec
            rec.release()
        }
        return null
    }

    private fun initMediaCodec(): MediaCodec? = runCatching {
        val format = MediaFormat.createAudioFormat(MIME_TYPE_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MIME_TYPE_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder
    }.getOrNull()

    private fun initMediaMuxer(target: File): MediaMuxer? = runCatching {
        MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }.getOrNull()

    /** Milliseconds captured so far — the `ts` a stroke started right now carries. */
    fun elapsedMs(): Int = (presentationTimeUs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun pump(rec: AudioRecord, cod: MediaCodec, mux: MediaMuxer) {
        val pcmBuffer = ByteArray(2048)
        val bufferInfo = MediaCodec.BufferInfo()

        while (running.get()) {
            feedEncoder(rec, cod, pcmBuffer)
            drainEncoder(cod, mux, bufferInfo, endOfStream = false)
        }

        drainEncoder(cod, mux, bufferInfo, endOfStream = true)
    }

    private fun feedEncoder(rec: AudioRecord, cod: MediaCodec, pcmBuffer: ByteArray) {
        if (paused.get()) {
            Thread.sleep(40)
            return
        }
        val read = rec.read(pcmBuffer, 0, pcmBuffer.size)
        if (read <= 0) return
        onPcmData?.invoke(pcmBuffer, read, SAMPLE_RATE)

        val inputIndex = cod.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = cod.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(pcmBuffer, 0, read)
            cod.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0)
            val bytesPerSample = BYTES_PER_SAMPLE
            val samples = read / bytesPerSample
            val micros = (samples.toLong() * 1_000_000L) / SAMPLE_RATE
            presentationTimeUs += micros
        }
    }

    private fun drainEncoder(
        cod: MediaCodec,
        mux: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
    ) {
        if (endOfStream) {
            val inputIndex = cod.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                cod.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }

        while (true) {
            val outputIndex = cod.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = mux.addTrack(cod.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                }
                outputIndex >= 0 -> {
                    val outBuffer = cod.getOutputBuffer(outputIndex)
                    if (outBuffer != null && muxerStarted && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        mux.writeSampleData(trackIndex, outBuffer, bufferInfo)
                    }
                    cod.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
                else -> break
            }
            if (!endOfStream && outputIndex < 0) break
        }
    }

    /**
     * Stop capturing and finalise the file, returning the written [File] or null if not running.
     */
    fun stop(): File? {
        if (!running.getAndSet(false)) return null
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null

        record?.runCatching { stop() }
        record?.release()
        record = null

        codec?.runCatching { stop() }
        codec?.release()
        codec = null

        muxer?.runCatching {
            if (muxerStarted) stop()
            release()
        }
        muxer = null
        muxerStarted = false
        trackIndex = -1

        return file.also { file = null }
    }

    companion object {
        /** The extension the container this recorder writes carries — what its file is named after. */
        const val CONTAINER_SUFFIX = "m4a"

        const val SAMPLE_RATE = 44_100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BIT_RATE = 32_000
        const val MIME_TYPE_AAC = "audio/mp4a-latm"
        private const val BUFFER_MULTIPLIER = 4
        private const val TIMEOUT_US = 5_000L
        private const val JOIN_TIMEOUT_MS = 1_000L
    }
}

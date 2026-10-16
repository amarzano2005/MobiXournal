package com.mobixournal.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records the microphone to an **Ogg/Opus** file on a background thread using Android's [AudioRecord],
 * [MediaCodec] (Opus encoder) and [MediaMuxer] (`docs/audio-inking-sync.md`).
 *
 * The container is Ogg and the file's extension says so — a stroke's sidecar is named
 * `2026-08-02T09-14-33.ogg` and the bundled entry `assets/aud_<sha256>.ogg` — because the bytes really
 * are an Ogg stream (`OggS`), which is what the container name has to keep truthful. It is the codec a
 * `.mobix` records in: 48 kHz mono speech profile at 32 kbps, ~15 MB an hour against the WAV
 * recorder's ~300 MB ([OpusAudioRecorder.isAvailable]).
 *
 * If the device has no microphone, lacks permissions, or cannot initialise the Opus codec,
 * [start] returns false gracefully rather than throwing — the caller falls back to [AudioRecorder],
 * whose WAV sidecar is the one desktop Xournal++ plays.
 */
class OpusAudioRecorder {

    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private var muxerStarted = false
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    @Volatile
    private var presentationTimeUs: Long = 0L

    /** The file being written, or null when idle. */
    var file: File? = null
        private set

    /** True between a successful [start] and its [stop]. */
    val isRecording: Boolean get() = running.get()

    /**
     * Start capturing microphone audio to [target].
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
        running.set(true)

        rec.startRecording()
        cod.start()

        thread = Thread({ pump(rec, cod, mux) }, "opus-audio-record").also { it.start() }
        return true
    }

    private fun initAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuffer <= 0) return null
        val bufferBytes = minBuffer * BUFFER_MULTIPLIER
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufferBytes)
        }.getOrNull() ?: return null
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        return rec
    }

    private fun initMediaCodec(): MediaCodec? = runCatching {
        val format = MediaFormat.createAudioFormat(MIME_TYPE_OPUS, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MIME_TYPE_OPUS)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder
    }.getOrNull()

    /**
     * An Ogg muxer, and nothing else: Opus inside an MP4 is not a stream this app ever writes, and
     * naming a file after a container it is not in is the one thing an asset name must not do.
     * [isAvailable] is the gate, so a device without the muxer never gets here.
     */
    private fun initMediaMuxer(target: File): MediaMuxer? = runCatching {
        MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
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
        val read = rec.read(pcmBuffer, 0, pcmBuffer.size)
        if (read <= 0) return

        val inputIndex = cod.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = cod.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(pcmBuffer, 0, read)
            val pts = presentationTimeUs
            presentationTimeUs += (read.toLong() * 1_000_000L) / (SAMPLE_RATE * BYTES_PER_SAMPLE)
            cod.queueInputBuffer(inputIndex, 0, read, pts, 0)
        }
    }

    private fun drainEncoder(
        cod: MediaCodec,
        mux: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
    ) {
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
        /**
         * Whether this device can write an Ogg/Opus stream at all: the **OGG muxer** arrived with
         * Android 10 (API 29), and below it there is no container for the encoder's output — so an older
         * device records WAV rather than writing Opus into a file named after the wrong container.
         */
        val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /** The extension the container this recorder writes carries — what its file is named after. */
        const val CONTAINER_SUFFIX = "ogg"

        const val SAMPLE_RATE = 48_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BIT_RATE = 32_000
        const val MIME_TYPE_OPUS = "audio/opus"
        private const val BUFFER_MULTIPLIER = 4
        private const val TIMEOUT_US = 5_000L
        private const val JOIN_TIMEOUT_MS = 1_000L
    }
}

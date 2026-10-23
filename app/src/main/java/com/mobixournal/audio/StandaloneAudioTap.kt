package com.mobixournal.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Encodes the microphone stream into the standalone **`.m4a`** *while* the lecture is being written.
 *
 * The app records Opus (`.mobix`) or WAV (`.xopp`) as the document's own audio, and the standalone
 * copy a lecture gets uploaded from has to be MP4/AAC. Producing that copy at Stop meant re-encoding
 * the whole lecture right when the user closes the app — the one moment there is no time for it
 * ([AudioTranscoder]). This sink receives the same PCM the recorder is capturing
 * ([AudioSession.addPcmListener]), so the `.m4a` is complete when the recording ends and the export
 * becomes a file copy.
 *
 * [feed] runs on the recorder's capture thread, so it only queues bytes under a lock and returns;
 * one worker thread owns the encoder and the muxer. A stalled encoder drops chunks rather than
 * slowing the microphone: the recording in the document is never at the mercy of this copy.
 *
 * @param target where the `.m4a` is written; the caller names it after the recording it belongs to.
 */
internal class StandaloneAudioTap(private val target: File) {

    /** The PCM listener [AudioSession] is handed; the same shape the live transcription uses. */
    val listener: (ByteArray, Int, Int) -> Unit = { buffer, count, rate -> feed(buffer, count, rate) }

    /** A bounded hand-off: a full queue drops a chunk instead of stalling the microphone. */
    private val queue = ArrayBlockingQueue<ByteArray>(MAX_QUEUED_CHUNKS)
    private var worker: Thread? = null

    @Volatile private var accepting = false

    // Worker-owned state, read by [stop] after that worker has been joined.
    @Volatile private var wroteAudio = false
    @Volatile private var rate = 0
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var streaming = false
    private var presentationUs = 0L

    /** Begin accepting PCM; false when a tap is already running for this file. */
    fun start(): Boolean {
        if (worker != null) return false
        queue.clear()
        rate = 0
        wroteAudio = false
        accepting = true
        target.parentFile?.mkdirs()
        target.delete()
        worker = Thread(::pump, "m4a-standalone").also { it.start() }
        return true
    }

    /** Queue a captured PCM chunk. Returns at once: this runs on the recorder's capture thread. */
    fun feed(pcm: ByteArray, count: Int, sampleRate: Int) {
        if (count <= 0 || !accepting) return
        if (rate == 0) rate = sampleRate else if (sampleRate != rate) return
        queue.offer(pcm.copyOf(count))
    }

    /** Finish the file and return it, or null when it holds no audio (an encoder that never opened). */
    fun stop(): File? {
        if (worker == null) return null
        accepting = false
        worker?.join(JOIN_TIMEOUT_MS)
        val finished = worker?.isAlive == false
        worker = null
        val usable = finished && wroteAudio && target.isFile && target.length() > 0L
        if (!usable) target.delete()
        return if (usable) target else null
    }

    // ---- the worker -----------------------------------------------------------------------------

    private fun pump() {
        try {
            while (true) {
                val chunk = take() ?: break
                if (encoder == null && !openCodecs()) break
                write(chunk)
                drain(endOfStream = false)
            }
            finishStream()
        } catch (ignored: Throwable) {
            // A codec that refuses the stream leaves no file: [stop] deletes what was written and
            // the caller falls back to converting the recording itself.
        } finally {
            release()
        }
    }

    /** The next captured chunk, or null once [stop] asked for the end and the queue is empty. */
    private fun take(): ByteArray? {
        while (true) {
            queue.poll(WAIT_MS, TimeUnit.MILLISECONDS)?.let { return it }
            if (!accepting && queue.isEmpty()) return null
        }
    }

    private fun openCodecs(): Boolean {
        if (rate <= 0) return false
        val format = MediaFormat.createAudioFormat(MIME_TYPE_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AudioTranscoder.BIT_RATE)
        }
        val opened = runCatching {
            MediaCodec.createEncoderByType(MIME_TYPE_AAC).also {
                it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }
        }.getOrNull() ?: return false
        val mux = runCatching {
            MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }.getOrNull()
        if (mux == null) {
            opened.release()
            return false
        }
        encoder = opened
        muxer = mux
        return true
    }

    /** Hand [pcm] to the encoder in whole frames, draining it as the buffers fill. */
    private fun write(pcm: ByteArray) {
        val enc = encoder ?: return
        var at = 0
        while (at < pcm.size) {
            val index = enc.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return
            val input = enc.getInputBuffer(index) ?: return
            input.clear()
            val take = minOf(pcm.size - at, input.remaining()) / FRAME_BYTES * FRAME_BYTES
            if (take <= 0) return
            input.put(pcm, at, take)
            enc.queueInputBuffer(index, 0, take, presentationUs, 0)
            presentationUs += (take / FRAME_BYTES).toLong() * 1_000_000L / rate
            at += take
            drain(endOfStream = false)
        }
    }

    /** Queue the end-of-stream marker and take everything the encoder still holds. */
    private fun finishStream() {
        val enc = encoder ?: return
        val index = enc.dequeueInputBuffer(TIMEOUT_US)
        if (index >= 0) {
            enc.queueInputBuffer(index, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        repeat(MAX_DRAIN_STEPS) { if (drain(endOfStream = true)) return }
    }

    /** Move what the encoder has produced into the muxer; true once end-of-stream came through. */
    private fun drain(endOfStream: Boolean): Boolean {
        val enc = encoder ?: return false
        val mux = muxer ?: return false
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val index = enc.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!streaming) {
                        trackIndex = mux.addTrack(enc.outputFormat)
                        mux.start()
                        streaming = true
                    }
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                else -> {
                    if (index < 0) return false
                    val out = enc.getOutputBuffer(index)
                    if (out != null && info.size > 0 && streaming) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        mux.writeSampleData(trackIndex, out, info)
                        wroteAudio = true
                    }
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    enc.releaseOutputBuffer(index, false)
                    if (end) return true
                }
            }
        }
    }

    private fun release() {
        encoder?.runCatching { stop() }
        encoder?.release()
        encoder = null
        // The muxer writes its index on stop, which is what makes the file playable at all.
        muxer?.runCatching { if (streaming) stop() }
        muxer?.release()
        muxer = null
        streaming = false
        trackIndex = -1
    }

    private companion object {
        const val MIME_TYPE_AAC = "audio/mp4a-latm"
        const val FRAME_BYTES = 2
        const val TIMEOUT_US = 5_000L
        const val WAIT_MS = 100L
        const val JOIN_TIMEOUT_MS = 5_000L
        const val MAX_QUEUED_CHUNKS = 32
        const val MAX_DRAIN_STEPS = 200
    }
}

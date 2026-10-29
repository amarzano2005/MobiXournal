package com.mobixournal.desktop

import com.mobixournal.audio.WavWriter
import com.mobixournal.audio.audioFileName
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs

/**
 * The window recording from the microphone into a **`.wav` sidecar** — 44.1 kHz, mono, 16-bit signed
 * little-endian PCM, the shape desktop Xournal++ plays with no codec of its own (`docs/audio.md`).
 *
 * `javax.sound.sampled` rather than a native recorder: it is part of `java.desktop`, which the packaged
 * window already carries, so the MSI ships no second audio stack. The framing is the shared [WavWriter]
 * the tablet uses, so a recording made here is byte-for-byte the format a `.xopp` expects.
 *
 * Pausing **mutes** rather than closing: the line stays open (a re-open costs a second and clicks), and
 * only the written bytes stop — which is also why [elapsedMs] is the writer's own timeline and not wall
 * time. A stroke's `ts` has to point at the sample it was drawn over.
 *
 * [lines] is the device seam: the suite drives a fake line, because a build machine has no microphone.
 */
internal class DesktopAudioRecorder(
    private val store: DesktopAudioStore = DesktopAudioStore.shared,
    private val lines: CaptureLines = JavaSoundCapture(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** The recording in progress: its bare file name, or null when nothing is being recorded. */
    var fileName: String? = null
        private set

    /** True between [start] and [stop]; false once the take is finalised. */
    var recording: Boolean = false
        private set

    /** True while the take is muted — the pill's pause state. */
    var paused: Boolean = false
        private set

    /** The peak of the last chunk, 0..1, for the level bar. Computed once per chunk, never per sample. */
    var level: Float = 0f
        private set

    /** The one English sentence about a take that could not start, or null. */
    var message: String? = null
        private set

    private var writer: WavWriter? = null

    private var line: CaptureLine? = null

    private var worker: Thread? = null

    @Volatile
    private var stopping = false

    /**
     * Start a take; answers its file name, or null with [message] saying why there is none.
     *
     * [device] is the microphone the Audio section picked ([AudioRoute.input]); null is the platform's own
     * default. A name that has gone falls back to that default inside [openLine], so a vanished headset
     * costs a default take rather than a failure.
     */
    fun start(device: String? = AudioRoute.input): String? {
        if (recording) return fileName
        message = null
        val name = audioFileName(now(), extension = "wav")
        val capture = try {
            lines.open(SAMPLE_RATE, CHANNELS, device)
        } catch (failure: Throwable) {
            message = "No microphone is available to record with."
            return null
        }
        val target = try {
            store.target(name)
        } catch (failure: Throwable) {
            runCatching { capture.close() }
            message = "There is no folder to keep recordings in."
            return null
        }
        val sink = try {
            WavWriter(target, SAMPLE_RATE, CHANNELS)
        } catch (failure: Throwable) {
            runCatching { capture.close() }
            message = "That recording could not be created: ${failure.message ?: "unknown reason"}."
            return null
        }
        fileName = name
        recording = true
        paused = false
        level = 0f
        stopping = false
        writer = sink
        line = capture
        runCatching { capture.start() }.onFailure {                runCatching { capture.close() }
            runCatching { sink.close() }
            runCatching { target.delete() }
            recording = false
            fileName = null
            writer = null
            line = null
            message = "The microphone could not be started: it may be in use by another application."
            return null
        }
        worker = Thread({ pump() }, "mobix-record").apply {
            isDaemon = true
            start()
        }
        return name
    }

    /** Mute the capture — the line stays open, so a pause costs nothing and does not click. */
    fun pause() {
        if (recording) paused = true
    }

    /** Unmute it, continuing the same file. */
    fun resume() {
        if (recording) paused = false
    }

    /** Stop and finalise; answers the recording the take produced, or null when there was none. */
    fun stop(): String? {
        if (!recording) return null
        stopping = true
        worker?.let { thread -> runCatching { thread.join(1_000) } }
        worker = null
        runCatching { line?.stop() }
        runCatching { line?.close() }
        line = null
        val written = writer?.durationMs() ?: 0L
        runCatching { writer?.close() }
        writer = null
        // A take that captured nothing (the microphone answered silence and was stopped at once) leaves
        // no file behind: an empty recording named in the document would be worse than no take at all.
        if (written == 0L) runCatching { store.file(fileName ?: "")?.delete() }
        recording = false
        paused = false
        level = 0f
        val name = fileName
        fileName = null
        return name
    }

    /** Milliseconds of audio **written** so far — the clock a stroke is stamped with. */
    fun elapsedMs(): Int = writer?.durationMs()?.toInt() ?: 0

    /** Drop the recorder's message once it has been read. */
    fun dismissMessage() {
        message = null
    }

    /**
     * The capture loop: one ~100 ms chunk at a time, written unless the take is paused.
     *
     * The level is the chunk's peak, not its average: a level bar that reads a lecture as "quiet" while
     * the speaker is talking is worse than one that reads the room's noise between words.
     */
    private fun pump() {
        val buffer = ByteArray(CHUNK_BYTES)
        while (!stopping) {
            val read = runCatching { line?.read(buffer) ?: 0 }.getOrDefault(0)
            if (read <= 0) {
                if (stopping) break
                // Nothing captured yet (a line that is still warming up): wait for the next chunk
                // rather than spinning the CPU on a device that is not there.
                Thread.sleep(5)
                continue
            }
            level = peakOf(buffer, read)
            if (paused) continue
            val sink = writer ?: break
            val wrote = runCatching { sink.write(buffer, read) }
            if (wrote.isFailure) break
        }
    }

    /** The largest absolute sample of [length] bytes of 16-bit little-endian PCM, as 0..1. */
    private fun peakOf(buffer: ByteArray, length: Int): Float {
        var peak = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            peak = maxOf(peak, abs(sample.toShort().toInt()))
            i += 2
        }
        return (peak / 32768f).coerceIn(0f, 1f)
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 1

        /** ~93 ms at 44.1 kHz mono 16-bit: one level reading per visible bar step. */
        const val CHUNK_BYTES = 4_096
    }
}

/** The microphone, as a seam: start it, read chunks, stop it. */
internal interface CaptureLine {

    fun start()

    fun read(buffer: ByteArray): Int

    fun stop()

    fun close()
}

/** Opening a capture line — a machine with no microphone must answer a sentence, not a stack trace. */
internal fun interface CaptureLines {
    /** The microphone named [device], or the platform's default when it is null. */
    fun open(sampleRate: Int, channels: Int, device: String?): CaptureLine
}

/** The real microphone: 16-bit signed little-endian, mono, with a ~100 ms buffer. */
internal class JavaSoundCapture : CaptureLines {

    override fun open(sampleRate: Int, channels: Int, device: String?): CaptureLine {
        val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val line = openLine(device, info) as TargetDataLine
        line.open(format, sampleRate / 10 * channels * 2)
        return object : CaptureLine {
            override fun start() = line.start()

            override fun read(buffer: ByteArray): Int = line.read(buffer, 0, buffer.size)

            override fun stop() = line.stop()

            override fun close() = line.close()
        }
    }
}

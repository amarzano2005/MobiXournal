package com.mobixournal.desktop

import com.mobixournal.audio.ClipClock
import com.mobixournal.audio.Playhead
import com.mobixournal.format.model.AudioRef
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The window playing **one recording at a time**, with the transport the header's audio pill drives.
 *
 * The shape is the tablet's (`docs/audio-inking-sync.md`): sound comes from the platform, while *where
 * we are* is [Playhead]'s answer — a sounding position is the media stack's and a held one is ours. That
 * class moved into `mobix-core` for this exact reason, so the two shells cannot disagree about a seek.
 *
 * The audio is **streamed**: [DesktopAudioSource] hands the feeder thread a few kilobytes at a time, so a
 * one-hour lecture is not a `ShortArray` in the heap. A seek is a message to that thread rather than a
 * second decoder, and the clip's own position reads as the position asked for until the sound gets there
 * ([Playhead]'s rule).
 *
 * The clip that is **already open stays open**: a tap on the same recording again — the audio tap's
 * commonest gesture — is a seek on the decoder and the device the window already holds, not a second open
 * of both. Reopening cost a device handshake and a full re-read of the file before the first sample
 * sounded, which is a click that looks like it missed.
 *
 * [lines] is the device seam: a test opens a fake line and drives the whole transport with no sound card.
 */
internal class DesktopAudioPlayer(
    private val store: DesktopAudioStore = DesktopAudioStore.shared,
    private val lines: PcmLines = JavaSoundLines(),
) : AutoCloseable {

    /** Where in the recording we are — the pill's clock and the one a tap would seek. */
    val playhead = Playhead()

    /** True while sound is coming out; false while paused, stopped or at the end. */
    @Volatile
    var playing: Boolean = false
        private set

    /** True while a pause holds the clip — the pill draws it differently from "nothing on". */
    @Volatile
    var paused: Boolean = false
        private set

    /** The clip that is on, or null — a pill that shows a transport only when there is something to hear. */
    var clipName: String? = null
        private set

    /** The clip's length in ms, for the scrubber and the readout. */
    var durationMs: Int = 0
        private set

    /** One English sentence about a recording that could not play, or null. */
    var message: String? = null
        private set

    /**
     * How fast the clip plays: 1 is as recorded, and the pill cycles the steps in [PLAYBACK_SPEEDS].
     *
     * The speed is applied by the **feeder**, which resamples each chunk before it writes it, so the
     * platform's line keeps one format for the whole clip and a change takes effect on the next chunk. The
     * clock counts *source* frames, which is what keeps the playhead — and so the karaoke — in the clip's
     * own time at any speed.
     */
    @Volatile
    var speed: Float = 1f
        private set

    private var source: DesktopAudioSource? = null

    private var line: PcmLine? = null

    private var feeder: Thread? = null

    /**
     * Which run of the feeder owns the device: every start takes a number and the loop holds it, so a
     * feeder that is still finishing when the next clip starts stops **for good** instead of carrying on
     * beside the new one. (A plain "stopping" flag that gets cleared again would hand the old thread a
     * second life, and two feeders on one device is two sounds at once.)
     */
    @Volatile
    private var generation = 0

    @Volatile
    private var seekRequest: Int? = null

    /** Frames of each channel handed to the line so far; the line's own backlog is [PcmLine.queuedFrames]. */
    @Volatile
    private var fedFrames: Long = 0

    private val clock = object : ClipClock {
        override fun positionMs(): Int {
            val rate = source?.sampleRate ?: return 0
            // Frames written **minus the ones the device has not played yet**: what still sits in the
            // line's buffer has not been heard, so counting it would put the playhead ahead of the voice.
            val played = (fedFrames - (line?.queuedFrames?.toLong() ?: 0L)).coerceAtLeast(0)
            return (played * 1000 / rate).toInt()
        }

        override fun durationMs(): Int = durationMs

        override fun seekTo(ms: Int) {
            seekRequest = ms
        }
    }

    /**
     * Start [ref] — the recording named by a stroke's `fn` — from its stamp. Answers the sentence when it
     * cannot, and null when the sound is on its way.
     *
     * A second play **replaces** the first: one clip at a time, which is what the tablet's mini-player
     * is. A pause is resumed by [resume] rather than by starting the clip again. The one recording the
     * window already has open is *not* replaced: it is seeked, because a tap on another word of the same
     * take is the same clip.
     */
    fun play(ref: AudioRef): String? {
        val file = store.file(ref.filename)
            ?: return refuse("This document's recording is not on this machine: ${ref.filename}.")
        val open = source
        val output = line
        if (open != null && output != null && clipName == file.name) {
            message = null
            return start(open, output, ref.startMs)
        }
        stopQuietly()
        val opened = try {
            DesktopAudioSources.open(file)
        } catch (refused: AudioRefused) {
            return refuse(refused.message ?: "That recording cannot be read.")
        } catch (failure: Throwable) {
            return refuse("That recording cannot be read: ${failure.message ?: "unknown reason"}.")
        }
        val device = try {
            lines.open(opened.sampleRate, opened.channels, AudioRoute.output)
        } catch (failure: Throwable) {
            opened.close()
            return refuse("No audio output device is available to play the recording.")
        }
        source = opened
        line = device
        clipName = file.name
        durationMs = opened.durationMs
        message = null
        return start(opened, device, ref.startMs)
    }

    /**
     * Make [audio] come out of [output] from [atMs] — the one arm a fresh open, a re-tap and a resume
     * after the end all take.
     *
     * The seek is asked for **before** a restarted feeder exists, so the very first chunk is read at the
     * word the tap named rather than at the head with a jump after it. A feeder that is still running is
     * left alone: it applies the seek between two chunks.
     */
    private fun start(audio: DesktopAudioSource, output: PcmLine, atMs: Int): String? {
        paused = false
        playing = true
        playhead.attach(clock)
        // After `attach`, so the playhead reports the target until the sound lands there (Playhead's rule).
        playhead.seekTo(atMs)
        if (feeder?.isAlive != true) {
            fedFrames = 0
            val run = ++generation
            feeder = Thread({ feed(audio, output, run) }, "mobix-audio").apply {
                isDaemon = true
                start()
            }
        }
        runCatching { output.start() }
        return null
    }

    /** Hold the clip where it is; nothing sounds, and the position is ours. */
    fun pause() {
        if (!playing) return
        playing = false
        paused = true
        runCatching { line?.stop() }
        playhead.hold(durationMs, playhead.positionMs() ?: 0)
    }

    /** Continue from where [pause] left it — or from the top once the clip has ended. */
    fun resume() {
        if (playing || source == null) return
        val audio = source ?: return
        val output = line ?: return
        if (paused) {
            // The held position was ours, so it has to be handed to the device as a seek before the
            // clock becomes the stack's again — otherwise resuming would start at the last place the
            // feeder read from, not where the scrubber stood.
            val held = playhead.positionMs() ?: 0
            paused = false
            playing = true
            playhead.attach(clock)
            playhead.seekTo(held)
            runCatching { output.start() }
            return
        }
        // The clip ended: playing again means playing it from the top — or from wherever the transport
        // was scrubbed to after it stopped, which is a position the reader chose on purpose.
        val from = playhead.positionMs()?.takeIf { it in 0 until durationMs } ?: 0
        start(audio, output, from)
    }

    /** Play or hold, whichever the pill's button means right now. */
    fun toggle() {
        if (playing) pause() else resume()
    }

    /** Move playback to [ms]; the feeder applies it and [Playhead] reports it until the sound does. */
    fun seek(ms: Int) {
        if (source == null) return
        playhead.seekTo(ms)
    }

    /** Play at [value]×; clamped to the steps the transport offers. */
    fun setSpeed(value: Float) {
        speed = PLAYBACK_SPEEDS.minByOrNull { kotlin.math.abs(it - value) } ?: 1f
    }

    /** The next speed the pill's button asks for, wrapping after the fastest. */
    fun nextSpeed(): Float {
        val index = PLAYBACK_SPEEDS.indexOfFirst { it == speed }
        speed = PLAYBACK_SPEEDS[(index + 1) % PLAYBACK_SPEEDS.size]
        return speed
    }

    /** Where playback is, or null when no clip is on. */
    fun positionMs(): Int? = playhead.positionMs()

    /** Let the clip go: nothing is on any more, and the pill goes away. */
    fun stop() {
        stopQuietly()
        clipName = null
        durationMs = 0
        playing = false
        paused = false
        playhead.detach()
    }

    /** Drop a message the user has read (`Esc`, or the next successful play). */
    fun dismissMessage() {
        message = null
    }

    override fun close() {
        stop()
    }

    /**
     * The feeder: read, hand to the device, repeat — the one place the source is read, so a seek is a
     * message this loop applies between chunks rather than a second reader.
     *
     * The clip ends when the source answers nothing **and** the device has drained; that is the moment
     * the last word is actually heard, which is what a karaoke timeline and the transport's readout both
     * want.
     */
    private fun feed(audio: DesktopAudioSource, output: PcmLine, run: Int) {
        val channels = audio.channels
        val buffer = ShortArray(CHUNK_SAMPLES * channels)
        var bytes = ByteArray(buffer.size * 2)
        while (generation == run) {
            seekRequest?.let { target ->
                seekRequest = null
                runCatching { output.flush() }
                runCatching { audio.seekTo(target) }
                // Just flushed, so next to nothing is queued: the clock restarts from the target itself.
                fedFrames = target.toLong() * audio.sampleRate / 1000 + (output.queuedFrames.toLong())
            }
            val read = runCatching { audio.read(buffer) }.getOrDefault(0)
            if (read <= 0) break
            val frames = read / channels
            val (samples, count) = if (speed == 1f) {
                buffer to read
            } else {
                resample(buffer, read, channels, speed)
            }
            if (bytes.size < count * 2) bytes = ByteArray(count * 2)
            ByteBuffer.wrap(bytes, 0, count * 2).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().put(samples, 0, count)
            val written = writeFully(output, bytes, count * 2, run)
            // **Source** frames, not the ones written: at 2× the device is fed half as many samples for
            // the same stretch of the recording, and a clock counting the device's would run at half speed.
            fedFrames += written / 2 / channels
            // A device that took nothing at all is not going to take the next chunk either (`write`
            // blocking always fills), so the clip ends here rather than racing silently to its last word.
            if (written <= 0) break
        }
        if (generation != run) return
        runCatching { output.drain() }
        if (generation != run) return
        playing = false
        paused = false
        playhead.hold(durationMs, durationMs)
        runCatching { output.stop() }
    }

    /**
     * Hand [length] bytes to [output] until every one of them is in, answering how many were.
     *
     * `SourceDataLine.write` is allowed to accept **fewer** bytes than it is given — it stops where its
     * buffer ends — and the count used to be dropped, which is a hole in the sound every time a buffer
     * filled mid-chunk. A seek or a stopped run cuts the loop short; the frames that did get in are the
     * ones the clock counts.
     */
    private fun writeFully(output: PcmLine, bytes: ByteArray, length: Int, run: Int): Int {
        var at = 0
        while (at < length && generation == run && seekRequest == null) {
            val wrote = runCatching { output.write(bytes, at, length - at) }.getOrDefault(0)
            if (wrote <= 0) return at
            at += wrote
        }
        return at
    }

    /** Stop the feeder without forgetting the clip — the arm a second play and [stop] both take. */
    private fun stopQuietly() {
        generation++ // whatever feeder is out there is stale from here on, however long it takes to leave
        feeder?.let { thread ->
            runCatching { thread.join(500) }
        }
        feeder = null
        seekRequest = null
        runCatching { line?.flush() }
        runCatching { line?.stop() }
        runCatching { line?.close() }
        line = null
        source?.let { runCatching { it.close() } }
        source = null
        playhead.detach()
        playing = false
        paused = false
    }

    private fun refuse(sentence: String): String {
        stop()
        message = sentence
        return sentence
    }

    private companion object {
        /** 2048 frames per write: ~46 ms at 44.1 kHz, small enough that a seek feels immediate. */
        const val CHUNK_SAMPLES = 2_048

        val PLAYBACK_SPEEDS = floatArrayOf(1f, 1.25f, 1.5f, 2f)
    }
}

/**
 * [samples] of interleaved [channels] frames, played [speed]× — one linear-interpolated resample.
 *
 * Linear rather than a box filter on purpose: this is speech a person is reading along with, and a decoder
 * that costs the frame budget would be the wrong trade for a transport. The length is `frames / speed`,
 * so 2× writes half the samples for the same recording time.
 */
internal fun resample(
    source: ShortArray,
    samples: Int,
    channels: Int,
    speed: Float,
): Pair<ShortArray, Int> {
    if (speed == 1f || samples <= 0) return source to samples
    val frames = samples / channels
    if (frames <= 1) return source to samples
    val target = (frames / speed).toInt().coerceIn(1, frames)
    val out = ShortArray(target * channels)
    for (frame in 0 until target) {
        val position = frame * speed
        val index = position.toInt().coerceAtMost(frames - 1)
        val next = (index + 1).coerceAtMost(frames - 1)
        val fraction = position - index
        for (channel in 0 until channels) {
            val a = source[index * channels + channel].toFloat()
            val b = source[next * channels + channel].toFloat()
            out[frame * channels + channel] = kotlin.math.round(a + (b - a) * fraction).toInt().toShort()
        }
    }
    return out to out.size
}

/**
 * The device the player writes into — `SourceDataLine` in the window, a buffer in a test.
 *
 * Frames rather than bytes for the one number the clock needs: how far behind the sound is.
 */
internal interface PcmLine {

    /** The line's whole buffer, in frames. */
    val bufferFrames: Int

    /**
     * Frames already handed to the device that it has **not played yet** — sound still in the buffer.
     *
     * Zero for a test's buffer, which is consumed the moment it is written.
     */
    val queuedFrames: Int get() = 0

    fun start()

    fun stop()

    fun flush()

    fun drain()

    /** Write [length] bytes of [bytes] from [offset]; answers how many the device took. */
    fun write(bytes: ByteArray, offset: Int, length: Int): Int

    fun close()
}

/** Opening an output line, as a seam: a machine with no sound card must fail in words, not in a stack. */
internal fun interface PcmLines {
    /** The output named [device] (the Audio section's pick), or the platform's default when it is null. */
    fun open(sampleRate: Int, channels: Int, device: String?): PcmLine
}

/** The real device: 16-bit signed little-endian mono or stereo, with a 100 ms buffer. */
internal class JavaSoundLines : PcmLines {

    override fun open(sampleRate: Int, channels: Int, device: String?): PcmLine {
        val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
        val line = openLine(device, DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
        val frames = sampleRate / 10
        val frameBytes = channels * 2
        line.open(format, frames * frameBytes)
        return object : PcmLine {
            override val bufferFrames: Int get() = frames

            // What is in the buffer and not yet out of the speakers: the difference between what has
            // been written and what the device has played.
            override val queuedFrames: Int
                get() = ((line.bufferSize - line.available()) / frameBytes).coerceAtLeast(0)

            override fun start() = line.start()

            override fun stop() = line.stop()

            override fun flush() = line.flush()

            override fun drain() = line.drain()

            override fun write(bytes: ByteArray, offset: Int, length: Int): Int =
                line.write(bytes, offset, length)

            override fun close() = line.close()
        }
    }
}

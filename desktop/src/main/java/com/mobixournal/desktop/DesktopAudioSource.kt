package com.mobixournal.desktop

import com.mobixournal.audio.OggOpus
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusPacketInfo
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A recording as a **stream of 16-bit PCM frames** the player can read, seek and stop — never a whole
 * lecture as a `ShortArray` in the heap.
 *
 * Two containers arrive here, and the difference between them is why this is an interface:
 * a `.wav` is a seekable block of PCM, so a seek is arithmetic; an `.ogg` Opus stream is a chain of
 * frames, so a seek is a jump to the packet that holds the millisecond asked for. Both answer the same
 * three questions — rate, channels, length — and the player's clock never learns which one it has.
 */
internal interface DesktopAudioSource : AutoCloseable {

    val sampleRate: Int

    val channels: Int

    /** The clip's own length in ms. */
    val durationMs: Int

    /** Fill [buffer] with interleaved frames; answers how many **samples** were written (0 at the end). */
    fun read(buffer: ShortArray): Int

    /** Reposition to [ms] from the clip's start, clamped to its length. */
    fun seekTo(ms: Int)
}

/** What [DesktopAudioSources.open] answers: a source, or the one English sentence a person can act on. */
internal class AudioRefused(message: String) : Exception(message)

/**
 * Opening a recording from the window's store, by the **content** of its bytes rather than by its
 * extension: a file the tablet wrote is an `.ogg` full of Opus, a `.xopp` sidecar is a `.wav`, and a
 * rename must not decide which reader runs.
 *
 * An `.m4a`/AAC recording is refused in words. A plain JVM carries no AAC decoder and Concentus is
 * Opus-only, so pretending otherwise would be a silent failure — the same rule the export path states
 * (`docs/audio-inking-sync.md`): no transcode, no rename, say what is missing.
 */
internal object DesktopAudioSources {

    fun open(file: File): DesktopAudioSource {
        if (!file.isFile) throw AudioRefused("That recording is not on this machine: ${file.name}.")
        val header = ByteArray(12)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
        if (read >= 4 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII))) {
            return WavSource(file)
        }
        if (OggOpus.looksLike(header.copyOf(read))) {
            return runCatching { OpusSource(file) }
                .getOrElse { failure ->
                    throw AudioRefused(failure.message ?: "That Opus recording cannot be read.")
                }
        }
        if (file.extension.equals("m4a", ignoreCase = true) || file.extension.equals("aac", ignoreCase = true)) {
            throw AudioRefused(
                "${file.name} is an AAC recording. This window plays WAV and Opus: play it in the app that " +
                    "recorded it, or export it as WAV there.",
            )
        }
        throw AudioRefused("${file.name} is not an audio recording this window can read.")
    }
}

/**
 * A canonical RIFF/WAVE file — what the tablet writes for a `.xopp` sidecar (`WavWriter`).
 *
 * The chunk walk is deliberately small: `fmt ` says the format, channels and rate, `data` says where the
 * samples start and how many there are. A file whose samples are not 16-bit PCM is refused rather than
 * replayed as noise.
 */
private class WavSource(file: File) : DesktopAudioSource {

    private val raf = RandomAccessFile(file, "r")

    private val bytesPerSample: Int

    override val sampleRate: Int

    override val channels: Int

    private val dataAt: Long

    private val dataBytes: Long

    private var cursor: Long = 0

    override val durationMs: Int

    init {
        val header = ByteArray(12)
        raf.readFully(header)
        if (!header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII))) {
            raf.close()
            throw AudioRefused("${file.name} is not a WAV file.")
        }
        var format = 0
        var bits = 0
        var rate = 44_100
        var channelCount = 1
        var at: Long = 12
        var dataOffset = -1L
        var dataLength = 0L
        while (at + 8 <= raf.length()) {
            raf.seek(at)
            val id = ByteArray(4)
            raf.readFully(id)
            val size = readLe32(raf).toInt()
            val body = at + 8
            when (String(id, Charsets.US_ASCII)) {
                "fmt " -> {
                    raf.seek(body)
                    format = readLe16(raf)
                    channelCount = readLe16(raf)
                    rate = readLe32(raf).toInt()
                    raf.skipBytes(6)
                    bits = readLe16(raf)
                }
                "data" -> {
                    dataOffset = body
                    dataLength = size.toLong().coerceAtMost(raf.length() - body)
                }
            }
            at = body + size + (size and 1)
        }
        if (dataOffset < 0 || bits != 16 || format != 1) {
            raf.close()
            throw AudioRefused("${file.name} is a WAV this window cannot read (16-bit PCM only).")
        }
        bytesPerSample = 2
        sampleRate = rate
        channels = channelCount.coerceAtLeast(1)
        dataAt = dataOffset
        dataBytes = dataLength
        durationMs = ((dataBytes / (bytesPerSample.toLong() * this.channels)) * 1000 / rate).toInt()
    }

    override fun read(buffer: ShortArray): Int {
        val frameBytes = bytesPerSample * channels
        val wanted = minOf(buffer.size, ((dataBytes - cursor) / frameBytes).toInt() * channels)
        if (wanted <= 0) return 0
        val bytes = ByteArray(wanted * bytesPerSample)
        raf.seek(dataAt + cursor)
        raf.readFully(bytes)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(buffer, 0, wanted)
        cursor += wanted.toLong() * bytesPerSample
        return wanted
    }

    override fun seekTo(ms: Int) {
        val frame = ms.coerceIn(0, durationMs).toLong() * sampleRate / 1000
        cursor = (frame * bytesPerSample * channels).coerceIn(0, dataBytes)
    }

    override fun close() = raf.close()

    private fun readLe16(file: RandomAccessFile): Int {
        val b = ByteArray(2)
        file.readFully(b)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    private fun readLe32(file: RandomAccessFile): Long {
        val b = ByteArray(4)
        file.readFully(b)
        return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    }
}

/**
 * An Ogg/Opus recording — the container the tablet writes (`OggOpus`), decoded by Concentus.
 *
 * A seek is a **jump**: the stream's packets are all in hand, so a table of where each one starts on the
 * recording's own timeline says which packet holds the millisecond asked for, and the decoder is started
 * there. Rewinding to packet zero and decoding the minutes in between — which is what this used to do —
 * made a tap near the end of a lecture a stall of a second or more with the device starved of samples,
 * heard as a gap and a stutter.
 *
 * The jump carries a little history before the target ([PRE_ROLL_PACKETS], 40 ms) because an Opus frame
 * is decoded with the one before it; a decoder started cold at an arbitrary packet still speaks, but the
 * first frame is not the encoder's.
 */
private class OpusSource(file: File) : DesktopAudioSource {

    private val stream = OggOpus.read(file.readBytes())

    private var decoder = OpusDecoder(OggOpus.SAMPLE_RATE, stream.channels)

    /** 120 ms is Opus's longest frame, so this buffer decodes any packet the stream can hold. */
    private val decoded = ShortArray(MAX_FRAME_SAMPLES * stream.channels)

    /** Samples per channel each packet decodes to — the packet's own duration, not a guess. */
    private val packetFrames = IntArray(stream.packets.size) { index ->
        val packet = stream.packets[index]
        OpusPacketInfo.getNumSamples(packet, 0, packet.size, OggOpus.SAMPLE_RATE)
            .takeIf { it in 1..MAX_FRAME_SAMPLES } ?: OggOpus.FRAME_SAMPLES
    }

    /**
     * Where each packet begins on the recording's timeline, in samples per channel: `starts[i]` is packet
     * `i`'s first sample once the encoder's pre-skip is off the head, and `starts[size]` is the clip's end.
     */
    private val starts = run {
        val table = LongArray(packetFrames.size + 1)
        for (i in packetFrames.indices) {
            // The pre-skip is the encoder's look-ahead in the **first** packet only; every later packet
            // is whole audio, which is why the subtraction is not applied again.
            val samples = if (i == 0) (packetFrames[0] - stream.preSkip).coerceAtLeast(0) else packetFrames[i]
            table[i + 1] = table[i] + samples
        }
        table
    }

    private var packetIndex = -1

    private var decodedSamples = 0

    private var decodedAt = 0

    private var skippedPreSkip = false

    private var cursorSamples = 0L

    override val sampleRate: Int = OggOpus.SAMPLE_RATE

    override val channels: Int = stream.channels

    override val durationMs: Int = (starts.last() * 1000 / OggOpus.SAMPLE_RATE).toInt()

    override fun read(buffer: ShortArray): Int {
        var written = 0
        while (written < buffer.size) {
            if (decodedAt >= decodedSamples && !decodeNext()) break
            val take = minOf(buffer.size - written, decodedSamples - decodedAt)
            decoded.copyInto(buffer, written, decodedAt, decodedAt + take)
            decodedAt += take
            written += take
            cursorSamples += take / channels
        }
        return written
    }

    /** Decode the next packet into [decoded], dropping the encoder's pre-skip on the very first one. */
    private fun decodeNext(): Boolean {
        if (packetIndex + 1 >= stream.packets.size) return false
        packetIndex++
        val packet = stream.packets[packetIndex]
        // The frame size the decoder is asked for is the packet's own duration: libopus refuses a buffer
        // smaller than the frame, and a larger one would decode the same audio into the front of it.
        val frame = packetFrames[packetIndex]
        val samples = decoder.decode(packet, 0, packet.size, decoded, 0, frame, false)
        decodedSamples = samples * channels
        decodedAt = 0
        if (!skippedPreSkip) {
            skippedPreSkip = true
            decodedAt = (stream.preSkip * channels).coerceAtMost(decodedSamples)
            if (decodedAt >= decodedSamples) decodedSamples = 0
        }
        return decodedSamples > 0
    }

    override fun seekTo(ms: Int) {
        val target = ms.coerceIn(0, durationMs).toLong() * sampleRate / 1000
        // The packet holding the target, minus the history a clean first frame wants. Never packet 0: a
        // jump has no pre-skip to drop, and starting at 0 is the rewind this method exists to avoid.
        val from = (packetIndexFor(target) - PRE_ROLL_PACKETS).coerceAtLeast(1)
        if (target <= 0 || from >= stream.packets.size || starts[from] > target) {
            // The target is inside the first packets: the head is the only honest place to start, and
            // dropping the pre-skip is this reader's job.
            rewind()
        } else {
            // A decoder carries the last frame's state, so a jump starts a **fresh** one at the new packet
            // instead of decoding a frame against a stream position it never saw.
            decoder = OpusDecoder(OggOpus.SAMPLE_RATE, channels)
            packetIndex = from - 1
            decodedSamples = 0
            decodedAt = 0
            skippedPreSkip = true
            cursorSamples = starts[from]
        }
        while (cursorSamples < target) {
            if (decodedAt >= decodedSamples && !decodeNext()) break
            val skip = minOf(target - cursorSamples, (decodedSamples - decodedAt).toLong() / channels)
            decodedAt += (skip * channels).toInt()
            cursorSamples += skip
        }
    }

    /** Back to the head of the stream, pre-skip and all — the one seek that is not a jump. */
    private fun rewind() {
        decoder = OpusDecoder(OggOpus.SAMPLE_RATE, channels)
        packetIndex = -1
        decodedSamples = 0
        decodedAt = 0
        skippedPreSkip = false
        cursorSamples = 0
    }

    /** The index of the packet whose timeline holds [sample], by binary search over [starts]. */
    private fun packetIndexFor(sample: Long): Int {
        var low = 0
        var high = stream.packets.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (starts[mid] <= sample) low = mid else high = mid - 1
        }
        return low
    }

    override fun close() = Unit

    private companion object {
        const val MAX_FRAME_SAMPLES = 5_760

        /** Two 20 ms packets of history: enough for libopus to decode the target frame properly. */
        const val PRE_ROLL_PACKETS = 2
    }
}

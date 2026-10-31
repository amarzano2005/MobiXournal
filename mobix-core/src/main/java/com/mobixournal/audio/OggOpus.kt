package com.mobixournal.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The **container** an `.ogg` recording is: the Ogg framing around an Opus stream, read on a plain JVM
 * so the window can play what the tablet recorded (`docs/audio.md`).
 *
 * The encoder/decoder itself is not here — the tablet uses Android's own Opus stack and the window uses
 * Concentus, and both agree because the **bitstream** is the format, not the library. What no JVM library
 * offers is the page layer: the capture pattern, the segment table, the granule positions and the CRC that
 * make a file a *file*. That is this object, and it is pure Kotlin, so it is tested without an audio
 * device.
 *
 * Reading is deliberately strict about the two things a damaged recording does: a **truncated page** and a
 * **corrupt CRC** are refused with a sentence rather than decoded as noise, because a burst of static is
 * indistinguishable from a very quiet lecture and the reader must not pretend it heard one. The packets of
 * the **first logical stream** are what come back; an `OpusHead` and an `OpusTags` packet are headers, not
 * audio, and never appear in [OggOpusStream.packets].
 */
object OggOpus {

    /** The one sample rate an Opus stream always decodes at, whatever its input rate was. */
    const val SAMPLE_RATE: Int = 48_000

    /** Samples per channel in the 20 ms frame both shells record. */
    const val FRAME_SAMPLES: Int = 960

    /** A recording the reader can play, or a file it refuses by name. */
    class OggOpusException(message: String) : Exception(message)

    /**
     * An Opus stream ready to decode: its channel count, the samples the decoder must discard at the head
     * (the encoder's look-ahead), and the audio packets in order.
     */
    data class OggOpusStream(
        val channels: Int,
        val preSkip: Int,
        val packets: List<ByteArray>,
    ) {

        /** Where a decoded sample's timeline starts — the pre-skip is not part of the recording. */
        val sampleRate: Int get() = SAMPLE_RATE
    }

    /** Whether [bytes] begin like an Ogg stream — the test an extension alone cannot pass. */
    fun looksLike(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(CAPTURE.toByteArray(Charsets.US_ASCII))

    /**
     * Read the first logical stream of [bytes].
     *
     * A page whose CRC does not match, or which is cut short, is refused: those are the two ways a
     * recording arrives damaged, and either one means the rest of the file's timing cannot be trusted.
     */
    fun read(bytes: ByteArray): OggOpusStream {
        val pages = pages(bytes)
        val packets = mutableListOf<ByteArray>()
        val partial = ByteArrayOutputStream()
        var channels = 0
        var preSkip = 0
        var sawHead = false
        for (page in pages) {
            // The segment table is the packet framing: a 255-byte segment means "this packet goes on",
            // anything shorter closes it. A page can carry several packets (the two headers usually
            // share one), and a packet can run from one page into the next.
            var cursor = 0
            for (length in page.segmentEnds) {
                partial.write(page.body, cursor, length)
                cursor += length
                if (length == 255) continue
                val packet = partial.toByteArray()
                partial.reset()
                when {
                    packet.startsWith(ASCII_OPUS_HEAD) -> {
                        if (packet.size < 19) {
                            throw OggOpusException("This Opus recording's header is too short to read.")
                        }
                        channels = packet[9].toInt() and 0xFF
                        preSkip = le16(packet, 10)
                        sawHead = true
                    }
                    packet.startsWith(ASCII_OPUS_TAGS) -> Unit
                    else -> if (sawHead) packets.add(packet)
                }
            }
        }
        if (!sawHead) throw OggOpusException("This file is Ogg but carries no Opus stream to play.")
        if (channels !in 1..2) throw OggOpusException("This Opus recording has $channels channels, which this player cannot read.")
        if (packets.isEmpty()) throw OggOpusException("This Opus recording holds no audio.")
        return OggOpusStream(channels = channels, preSkip = preSkip, packets = packets)
    }

    /**
     * Frame [packets] as a single-stream Ogg/Opus file — the container half of writing a recording,
     * used by the reader's own tests and by any writer that has Opus packets in hand.
     *
     * [frameSamples] is the packet duration at 48 kHz (960 for the 20 ms frames both shells use): the
     * granule position of a page is the decoded samples up to it, which is the only timeline the format
     * has. One page per packet keeps the granule positions exact and the writer small; a real encoder
     * may page several packets together, and the reader handles both.
     */
    fun write(channels: Int, preSkip: Int, packets: List<ByteArray>, frameSamples: Int = FRAME_SAMPLES): ByteArray {
        val out = ByteArrayOutputStream()
        var sequence = 0
        fun page(packet: ByteArray, granule: Long, headerType: Int) {
            val lacing = ArrayList<Int>()
            var remaining = packet.size
            while (remaining >= 255) {
                lacing.add(255)
                remaining -= 255
            }
            lacing.add(remaining)
            val page = ByteArray(HEADER_BYTES + lacing.size + packet.size)
            CAPTURE.toByteArray(Charsets.US_ASCII).copyInto(page, 0)
            page[4] = 0 // version
            page[5] = headerType.toByte()
            for (i in 0 until 8) page[6 + i] = ((granule shr (8 * i)) and 0xFF).toByte()
            for (i in 0 until 4) page[14 + i] = ((SERIAL shr (8 * i)) and 0xFF).toByte()
            for (i in 0 until 4) page[18 + i] = ((sequence.toLong() shr (8 * i)) and 0xFF).toByte()
            // CRC left zero here and filled in below, which is also how the reader computes it.
            page[26] = lacing.size.toByte()
            lacing.forEachIndexed { index, value -> page[HEADER_BYTES + index] = value.toByte() }
            packet.copyInto(page, HEADER_BYTES + lacing.size)
            val crc = crc(page, 0, page.size)
            for (i in 0 until 4) page[22 + i] = ((crc shr (8 * i)) and 0xFF).toByte()
            out.write(page)
            sequence++
        }
        val head = ByteArray(19)
        ASCII_OPUS_HEAD.copyInto(head)
        head[8] = 1 // version
        head[9] = channels.toByte()
        head[10] = (preSkip and 0xFF).toByte()
        head[11] = ((preSkip shr 8) and 0xFF).toByte()
        for (i in 0 until 4) head[12 + i] = ((SAMPLE_RATE.toLong() shr (8 * i)) and 0xFF).toByte()
        page(head, granule = 0, headerType = BOS)
        val vendor = "MobiXournal".toByteArray(Charsets.US_ASCII)
        val tags = ByteArrayOutputStream()
        tags.write(ASCII_OPUS_TAGS)
        tags.write(ByteArray(4).also { le32Into(it, 0, vendor.size.toLong()) })
        tags.write(vendor)
        tags.write(ByteArray(4)) // no comments
        page(tags.toByteArray(), granule = 0, headerType = 0)
        packets.forEachIndexed { index, packet ->
            page(packet, granule = (index + 1).toLong() * frameSamples, headerType = 0)
        }
        return out.toByteArray()
    }

    /** One page, with what the packet assembler needs: its body and the lacing values that ended it. */
    private class Page(val body: ByteArray, val segmentEnds: List<Int>)

    /**
     * Every page of [bytes], each one verified against its own CRC.
     *
     * A page ends where its segment table says; a stream that stops before then is truncated, and the
     * capture pattern of the next page is the only thing that can say so.
     */
    private fun pages(bytes: ByteArray): List<Page> {
        val pages = mutableListOf<Page>()
        var at = 0
        while (at < bytes.size) {
            if (at + HEADER_BYTES > bytes.size) {
                throw OggOpusException("This recording is cut short: its last Ogg page has no complete header.")
            }
            if (!bytes.copyOfRange(at, at + 4).contentEquals(CAPTURE.toByteArray(Charsets.US_ASCII))) {
                throw OggOpusException("This recording is damaged: an Ogg page is missing its marker.")
            }
            val segments = bytes[at + 26].toInt() and 0xFF
            val tableAt = at + HEADER_BYTES
            val bodyAt = tableAt + segments
            if (bodyAt > bytes.size) {
                throw OggOpusException("This recording is cut short: an Ogg page's segment table runs past its end.")
            }
            var bodyLength = 0
            val lacing = ArrayList<Int>(segments)
            for (i in 0 until segments) {
                val value = bytes[tableAt + i].toInt() and 0xFF
                lacing.add(value)
                bodyLength += value
            }
            val end = bodyAt + bodyLength
            if (end > bytes.size) {
                throw OggOpusException("This recording is cut short: an Ogg page ends after the file does.")
            }
            val stored = le32(bytes, at + 22)
            if (stored != crc(bytes, at, end)) {
                throw OggOpusException("This recording is damaged: an Ogg page fails its checksum.")
            }
            pages.add(Page(bytes.copyOfRange(bodyAt, end), lacing))
            at = end
        }
        return pages
    }

    /**
     * The Ogg CRC-32 of the page from [from] to [to], with the checksum field read as zero — the
     * polynomial is `0x04c11db7` **not** reflected, which is why `java.util.zip.CRC32` cannot answer it.
     *
     * Table-driven rather than bit-by-bit: every page of the file is checksummed on open, and a
     * minute-long lecture is a few hundred thousand bytes of that, which a shift-per-bit loop charged to
     * the first tap on the recording (twelve milliseconds of it, measured on a 470 KB clip).
     */
    private fun crc(bytes: ByteArray, from: Int, to: Int): Long {
        var crc = 0L
        for (i in from until to) {
            val byte = if (i in from + 22 until from + 26) 0 else bytes[i].toInt() and 0xFF
            crc = ((crc shl 8) xor CRC_TABLE[((crc ushr 24) xor byte.toLong()).toInt() and 0xFF]) and 0xFFFFFFFFL
        }
        return crc
    }

    /** The polynomial's own lookup table: `CRC_TABLE[b]` is the CRC of the single byte `b`. */
    private val CRC_TABLE = LongArray(256) { index ->
        var value = index.toLong() shl 24
        repeat(8) {
            value = if (value and 0x80000000L != 0L) {
                ((value shl 1) xor POLYNOMIAL) and 0xFFFFFFFFL
            } else {
                (value shl 1) and 0xFFFFFFFFL
            }
        }
        value
    }

    private fun ByteArray.startsWith(marker: ByteArray): Boolean =
        size >= marker.size && copyOfRange(0, marker.size).contentEquals(marker)

    private fun le16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, at: Int): Long =
        ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    private fun le32Into(bytes: ByteArray, at: Int, value: Long) {
        for (i in 0 until 4) bytes[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }

    private const val HEADER_BYTES = 27
    private const val BOS = 2
    private const val SERIAL = 0x4D42_5855L // "MBXU": one logical stream, no collision to avoid
    private const val POLYNOMIAL = 0x04C11DB7L
    private const val CAPTURE = "OggS"
    private val ASCII_OPUS_HEAD = "OpusHead".toByteArray(Charsets.US_ASCII)
    private val ASCII_OPUS_TAGS = "OpusTags".toByteArray(Charsets.US_ASCII)
}

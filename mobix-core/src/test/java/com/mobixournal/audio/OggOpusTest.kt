package com.mobixournal.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ogg page layer, tested without an audio device: a stream this writer framed must read back as the
 * packets it held, and the two ways a recording arrives damaged must be **refused** rather than decoded
 * as noise.
 *
 * The packets here are arbitrary bytes on purpose — what an Opus packet *sounds* like is Concentus's and
 * the Android stack's business, while the container is ours.
 */
class OggOpusTest {

    private val headRoom = listOf(
        ByteArray(40) { 0x11 },
        ByteArray(300) { 0x22 },
        ByteArray(17) { 0x33 },
    )

    @Test
    fun aStreamRoundTripsItsPacketsChannelsAndPreSkip() {
        val bytes = OggOpus.write(channels = 1, preSkip = 312, packets = headRoom, frameSamples = 960)

        assertTrue("the file begins like Ogg", OggOpus.looksLike(bytes))
        val stream = OggOpus.read(bytes)
        assertEquals(1, stream.channels)
        assertEquals(312, stream.preSkip)
        assertEquals(OggOpus.SAMPLE_RATE, stream.sampleRate)
        assertEquals("the two headers are not audio", headRoom.size, stream.packets.size)
        headRoom.forEachIndexed { index, packet ->
            assertArrayEquals("packet $index survives", packet, stream.packets[index])
        }
    }

    @Test
    fun aPacketLongerThanAPageSurvivesTheSegmentTable() {
        // 700 bytes is three segments (255 + 255 + 190), which is exactly what a naive writer breaks.
        val long = ByteArray(700) { (it % 251).toByte() }
        val stream = OggOpus.read(OggOpus.write(channels = 2, preSkip = 0, packets = listOf(long)))
        assertEquals(2, stream.channels)
        assertArrayEquals(long, stream.packets.single())
    }

    @Test
    fun aTruncatedPageIsRefused() {
        val bytes = OggOpus.write(channels = 1, preSkip = 0, packets = headRoom)
        val truncated = bytes.copyOfRange(0, bytes.size - 5)

        val failure = runCatching { OggOpus.read(truncated) }.exceptionOrNull()

        assertTrue("it says the file is cut short: ${failure?.message}", failure is OggOpus.OggOpusException)
    }

    @Test
    fun aPageWithABadChecksumIsRefused() {
        val bytes = OggOpus.write(channels = 1, preSkip = 0, packets = headRoom)
        // The last page's body starts well into the file; flipping a body byte must fail its CRC.
        val damaged = bytes.copyOf()
        damaged[damaged.size - 2] = (damaged[damaged.size - 2].toInt() xor 0x5A).toByte()

        val failure = runCatching { OggOpus.read(damaged) }.exceptionOrNull()

        assertTrue("it says the page is damaged: ${failure?.message}", failure is OggOpus.OggOpusException)
    }

    @Test
    fun bytesThatAreNotOggAreNotMistakenForIt() {
        assertFalse(OggOpus.looksLike("RIFF....WAVE".toByteArray(Charsets.US_ASCII)))
        assertFalse(OggOpus.looksLike(ByteArray(0)))
    }

    @Test
    fun anOggWithoutAnOpusStreamSaysSo() {
        // A page with one packet that is neither OpusHead nor OpusTags.
        val bytes = OggOpus.write(channels = 1, preSkip = 0, packets = emptyList())
        val withoutHead = runCatching { OggOpus.read(bytes) }.exceptionOrNull()
        assertTrue(withoutHead is OggOpus.OggOpusException)
        assertTrue("and names what is missing: ${withoutHead?.message}", withoutHead?.message!!.contains("no audio"))
    }
}

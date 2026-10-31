package com.mobixournal.desktop

import com.mobixournal.audio.WavWriter
import com.mobixournal.format.model.AudioRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The window's transport against a **buffer instead of a sound card**: what a tap, a short write and a
 * resume actually hand the device.
 *
 * Three things a person hears became assertions here. A tap on a recording the window already has open is
 * a **seek** — the device is opened once, not once per tap — and it starts at the word touched rather
 * than at the head. A device that accepts fewer bytes than it is offered still receives **every** sample
 * (a dropped tail is the click and the hole in the sound). And a resume after a pause is handed the seek
 * the scrubber left the playhead at, in the same breath as the device restarting.
 *
 * The fixture encodes each sample's own index, so "where did the sound start" is a number the test reads
 * back out of the bytes rather than a race with the feeder.
 */
class DesktopAudioPlayerTest {

    private val temp = createTempDir("mobix-audio-player")
    private val store = DesktopAudioStore(temp)
    private val rate = 8_000

    @Test
    fun `a second tap on the same recording seeks it instead of reopening the device`() {
        val name = recording("take.wav", ms = 400)
        val devices = FakeDevices()
        val player = DesktopAudioPlayer(store, devices)
        try {
            assertNull("the first play starts", player.play(AudioRef(name, 0)))
            assertEquals("and it opens the output once", 1, devices.opens)
            val line = devices.lines.single()
            awaitEnd(player)
            val end = line.audio.size()

            assertNull("a tap on the same take plays again", player.play(AudioRef(name, 50)))
            assertEquals("on the device it already holds — no second open", 1, devices.opens)
            awaitEnd(player)

            assertEquals(
                "and it starts at the point tapped, not at the head",
                sampleAt(50),
                firstSample(line.audio.toByteArray(), end),
            )
        } finally {
            player.close()
        }
    }

    @Test
    fun `a device that takes short writes still receives every sample`() {
        val name = recording("short.wav", ms = 300)
        // 700 bytes a call: every chunk straddles a write that cannot take all of it.
        val devices = FakeDevices(take = 700)
        val player = DesktopAudioPlayer(store, devices)
        try {
            assertNull(player.play(AudioRef(name, 0)))
            awaitEnd(player)
            val written = devices.lines.single().audio.toByteArray()
            assertEquals("every sample of the clip reached the device", pcm.size * 2, written.size)
            assertEquals(
                "the last sample is the recording's last sample, not a hole where a buffer ended",
                (pcm.size - 1).mod(20_000).toShort(),
                lastSample(written),
            )
        } finally {
            player.close()
        }
    }

    @Test
    fun `a resume hands the device the seek the playhead was held at`() {
        val name = recording("pause.wav", ms = 400)
        val gate = CountDownLatch(1)
        val devices = FakeDevices(gate = gate)
        val player = DesktopAudioPlayer(store, devices)
        try {
            assertNull(player.play(AudioRef(name, 0)))
            val line = devices.lines.single()
            // The feeder is parked in its first write, so the pause finds the clip exactly where it began.
            player.pause()
            assertEquals("holding the clip where it stood", 0, player.positionMs() ?: -1)
            player.seek(120)
            assertEquals("a held clip moves at once", 120, player.positionMs() ?: -1)

            player.resume()
            assertEquals(
                "the sound has not got there yet, so the playhead says where it will be",
                120,
                player.positionMs() ?: -1,
            )
            gate.countDown()
            awaitEnd(player)

            // Where the flush landed depends on whether the parked first write had been handed over yet;
            // either way it is the **last** flush, and the sound after it is the seeked one.
            assertTrue("the seek reached the device as a flush", line.flushedAt >= 0)
            assertEquals(
                "and the first sample after the flush is the one 120 ms in",
                sampleAt(120),
                firstSample(line.audio.toByteArray(), line.flushedAt.coerceAtLeast(0)),
            )
        } finally {
            gate.countDown()
            player.close()
        }
    }

    @Test
    fun `playing after the end starts at the scrub rather than at the head`() {
        val name = recording("ended.wav", ms = 300)
        val devices = FakeDevices()
        val player = DesktopAudioPlayer(store, devices)
        try {
            assertNull(player.play(AudioRef(name, 0)))
            awaitEnd(player)
            assertEquals("the clip ends holding its own length", 300, player.positionMs() ?: -1)

            player.seek(60)
            player.resume()
            awaitEnd(player)
            val line = devices.lines.single()
            assertEquals(
                "the reader scrubbed before playing, so that is where it plays from",
                sampleAt(60),
                firstSample(line.audio.toByteArray(), line.flushedAt),
            )
        } finally {
            player.close()
        }
    }

    // --- fixtures -----------------------------------------------------------------------------------

    /** The clip's own samples: each one carries its index, so any byte offset is a time. */
    private var pcm: ShortArray = ShortArray(0)

    private fun recording(name: String, ms: Int): String {
        val file = store.target(name)
        val samples = ShortArray(rate * ms / 1000) { index -> (index % 20_000).toShort() }
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        WavWriter(file, rate).use { writer -> writer.write(bytes, bytes.size) }
        pcm = samples
        return name
    }

    /** The sample [ms] into the clip — what the first byte of a seek should carry. */
    private fun sampleAt(ms: Int, index: Int = ms * rate / 1000): Short = (index % 20_000).toShort()

    private fun firstSample(bytes: ByteArray, from: Int): Short =
        ((bytes[from].toInt() and 0xFF) or ((bytes[from + 1].toInt() and 0xFF) shl 8)).toShort()

    private fun lastSample(bytes: ByteArray): Short = firstSample(bytes, bytes.size - 2)

    private fun awaitEnd(player: DesktopAudioPlayer) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (player.playing && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("the clip ends instead of running on", !player.playing)
    }

    /** A device that keeps what it was given; [take] caps how much one `write` accepts. */
    private class FakeSpool(
        private val take: Int = Int.MAX_VALUE,
        private val gate: CountDownLatch?,
    ) : PcmLine {

        val audio = ByteArrayOutputStream()

        /** Where the clip stood when the line was last flushed — a seek's own mark in the stream. */
        @Volatile
        var flushedAt: Int = -1

        override val bufferFrames: Int get() = 1_024

        override fun start() = Unit

        override fun stop() = Unit

        override fun flush() {
            flushedAt = audio.size()
        }

        override fun drain() = Unit

        override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
            gate?.await()
            val accepted = minOf(take, length)
            audio.write(bytes, offset, accepted)
            return accepted
        }

        override fun close() = Unit
    }

    private class FakeDevices(
        private val take: Int = Int.MAX_VALUE,
        private val gate: CountDownLatch? = null,
    ) : PcmLines {

        var opens = 0
            private set

        val lines = mutableListOf<FakeSpool>()

        override fun open(sampleRate: Int, channels: Int, device: String?): PcmLine =
            FakeSpool(take, gate).also {
                opens++
                lines += it
            }
    }
}

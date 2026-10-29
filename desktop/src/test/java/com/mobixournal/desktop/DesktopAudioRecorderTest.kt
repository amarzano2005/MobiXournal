package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The recorder proved without a microphone: a fake line hands it PCM, and what lands on disk is a WAV
 * whose header, length and timeline are the ones a `.xopp` sidecar needs.
 *
 * The pause rule is the interesting one and is checked here rather than by ear: pausing **mutes** the
 * capture, so the clock a stroke is stamped with stops moving while the line stays open.
 */
class DesktopAudioRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A microphone that always has a quiet tone ready, and can be told to fail at opening. */
    private class FakeLine(private val readFails: Boolean = false) : CaptureLine {

        var started = false
        var closed = false
        var stopped = false

        override fun start() {
            started = true
        }

        override fun read(buffer: ByteArray): Int {
            if (readFails) return -1
            // A 440 Hz-ish square at a quarter of full scale: enough for a level reading.
            for (i in buffer.indices) buffer[i] = if (i % 2 == 0) 0x10 else 0x30
            return buffer.size
        }

        override fun stop() {
            stopped = true
        }

        override fun close() {
            closed = true
        }
    }

    private fun await(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun `a take writes a wav of the right length and reports its own timeline`() {
        val store = DesktopAudioStore(temp.newFolder("audio"))
        val line = FakeLine()
        val recorder = DesktopAudioRecorder(store, { _, _, _ -> line })

        val name = recorder.start()
        try {
            assertNotNull("the take has a name", name)
            assertTrue("a .wav name", name!!.endsWith(".wav"))
            assertTrue("the clock runs", await { recorder.elapsedMs() > 0 })
            assertTrue("and the level follows the chunk", await { recorder.level > 0f })
            assertTrue("the microphone is open", line.started)
        } finally {
            val finished = recorder.stop()
            assertEquals("stop answers the file", name, finished)
        }

        assertFalse(recorder.recording)
        val file = store.file(name!!)
        assertNotNull("the recording is in the store", file)
        val bytes = file!!.readBytes()
        assertEquals("a canonical 44-byte header", "RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertTrue("and audio after it", bytes.size > 44)
        assertTrue("the line was released", line.closed)
    }

    @Test
    fun `a pause mutes the take without closing the line`() {
        val store = DesktopAudioStore(temp.newFolder("audio"))
        val line = FakeLine()
        val recorder = DesktopAudioRecorder(store, { _, _, _ -> line })
        recorder.start()

        assertTrue(await { recorder.elapsedMs() > 0 })
        recorder.pause()
        Thread.sleep(60)
        val held = recorder.elapsedMs()
        Thread.sleep(150)
        assertEquals("nothing is written while paused", held, recorder.elapsedMs())
        assertTrue("the line is still open", !line.closed)
        assertTrue(recorder.paused)

        recorder.resume()
        assertTrue("resuming continues the same file", await { recorder.elapsedMs() > held })
        assertFalse(recorder.paused)
        recorder.stop()
    }

    @Test
    fun `a microphone that cannot open answers a sentence and leaves no file`() {
        val store = DesktopAudioStore(temp.newFolder("audio"))
        val recorder = DesktopAudioRecorder(store, { _, _, _ -> throw IllegalStateException("no device") })

        val name = recorder.start()

        assertNull("no take", name)
        assertNotNull("a sentence", recorder.message)
        assertTrue(recorder.message!!.contains("microphone"))
        assertFalse(recorder.recording)
        assertEquals("and nothing on disk", emptyList<File>(), store.folder.listFiles()?.toList().orEmpty())
    }

    @Test
    fun `a line that dies mid-take still finalises the file`() {
        val store = DesktopAudioStore(temp.newFolder("audio"))
        val line = FakeLine(readFails = true)
        val recorder = DesktopAudioRecorder(store, { _, _, _ -> line })

        val name = recorder.start()
        assertTrue(await { !recorder.recording || recorder.elapsedMs() == 0 })
        val finished = recorder.stop()

        // A take that captured nothing is not left behind as an empty file: it is reported, not saved.
        assertEquals(name, finished)
        assertTrue(store.folder.listFiles().isNullOrEmpty())
    }
}

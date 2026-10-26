package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The queue that makes closing the app after a lecture safe is only worth anything if it survives a
 * process: these pin what is written to disk and what comes back, including the lines a hand-edited
 * or half-written file can hold.
 */
class AudioPendingWorkTest {

    private val dir = Files.createTempDirectory("pending-audio").toFile()
    private val queue = AudioPendingWork.file(dir)

    private fun export(name: String) = PendingAudioWork(PendingAudioWork.Kind.EXPORT, name)

    @Test
    fun `a queue that was never written reads as empty`() {
        assertFalse(queue.exists())
        assertTrue(AudioPendingWork.read(queue).isEmpty())
    }

    @Test
    fun `a queued export is still there for the next launch`() {
        AudioPendingWork.add(queue, export("2026-10-22T09-00-00.ogg"))
        assertEquals(listOf(export("2026-10-22T09-00-00.ogg")), AudioPendingWork.read(queue))
    }

    @Test
    fun `the same job is not queued twice`() {
        AudioPendingWork.add(queue, export("lecture.ogg"))
        AudioPendingWork.add(queue, export("lecture.ogg"))
        assertEquals(listOf(export("lecture.ogg")), AudioPendingWork.read(queue))
    }

    @Test
    fun `removing a finished job leaves the others and deletes the empty queue`() {
        AudioPendingWork.add(queue, export("a.ogg"))
        AudioPendingWork.add(queue, export("b.ogg"))
        AudioPendingWork.remove(queue, export("a.ogg"))
        assertEquals(listOf(export("b.ogg")), AudioPendingWork.read(queue))
        AudioPendingWork.remove(queue, export("b.ogg"))
        assertFalse("an empty queue leaves no file behind", queue.exists())
    }

    @Test
    fun `lines it does not understand are skipped instead of failing the launch`() {
        queue.writeText(
            """
            export|good.ogg
            nonsense
            export|
            |missing-kind.ogg
            export|also-good.wav
            """.trimIndent(),
        )
        assertEquals(listOf(export("good.ogg"), export("also-good.wav")), AudioPendingWork.read(queue))
    }

    @Test
    fun `a transcription left by an older build is dropped, not retried`() {
        queue.writeText(
            """
            transcribe|old-lecture.ogg
            export|lecture.ogg
            """.trimIndent(),
        )
        assertEquals(listOf(export("lecture.ogg")), AudioPendingWork.read(queue))
    }
}

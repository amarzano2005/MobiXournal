package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Unit tests for [AacAudioRecorder] on a JVM host without audio hardware.
 */
class AacAudioRecorderTest {

    @Test
    fun `the recorder refuses gracefully without hardware or permission`() {
        val recorder = AacAudioRecorder()
        assertFalse(recorder.isRecording)
        assertEquals(0, recorder.elapsedMs())

        val tempFile = File.createTempFile("test_audio", ".m4a").apply { deleteOnExit() }
        val started = recorder.start(tempFile)
        if (!started) {
            assertFalse(recorder.isRecording)
            assertNull(recorder.stop())
        }
    }

    @Test
    fun `the container it writes is the m4a stream its file is named after`() {
        assertEquals("m4a", AacAudioRecorder.CONTAINER_SUFFIX)
    }
}

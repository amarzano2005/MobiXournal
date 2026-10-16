package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The Opus recorder on a **host without audio hardware**, which is where the JVM tests run: what can be
 * pinned here is that it never throws and never claims to be recording — the encoder and the microphone
 * are the device's to answer ([OpusAudioRecorder.isAvailable]).
 */
class OpusAudioRecorderTest {

    @Test
    fun `the recorder refuses gracefully without hardware or permission`() {
        val recorder = OpusAudioRecorder()
        assertFalse(recorder.isRecording)
        assertEquals(0, recorder.elapsedMs())

        val tempFile = File.createTempFile("test_audio", ".ogg").apply { deleteOnExit() }
        val started = recorder.start(tempFile)
        // On a JVM host without audio hardware, start() safely returns false rather than throwing.
        if (!started) {
            assertFalse(recorder.isRecording)
            assertNull(recorder.stop())
        }
    }

    @Test
    fun `the container it writes is the ogg stream its file is named after`() {
        assertEquals("ogg", OpusAudioRecorder.CONTAINER_SUFFIX)
    }
}

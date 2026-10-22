package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for the standalone export's naming and media types — the part of
 * [AudioStandaloneExporter] and [AudioTranscoder] that decides *what* lands in the user's media
 * folder. The transcode itself needs an encoder, so it is exercised on a device, not here; what is
 * pinned here is that a recording never leaves under a name that lies about its container.
 */
class AudioTranscoderTest {

    @Test
    fun `a compressed recording is exported as m4a`() {
        assertEquals("2026-08-02T09-14-33.m4a", AudioTranscoder.exportedFileName("2026-08-02T09-14-33.ogg"))
        assertEquals("2026-08-02T09-14-33.m4a", AudioTranscoder.exportedFileName("2026-08-02T09-14-33.m4a"))
        assertEquals("lecture.m4a", AudioTranscoder.exportedFileName("lecture"))
    }

    @Test
    fun `the container and media type are mp4 aac`() {
        assertEquals("m4a", AudioTranscoder.CONTAINER_SUFFIX)
        assertEquals("audio/mp4", AudioTranscoder.MIME_TYPE)
    }

    @Test
    fun `a format that cannot be transcoded is exported as itself`() {
        val ogg = File("2026-08-02T09-14-33.ogg")
        val wav = File("2026-08-02T09-14-33.wav")

        assertFalse(AudioTranscoder.toM4a(ogg, File("out.m4a")))
        assertEquals("audio/ogg", AudioStandaloneExporter.mimeTypeFor(ogg))
        assertEquals("audio/wav", AudioStandaloneExporter.mimeTypeFor(wav))
        assertEquals("audio/mp4", AudioStandaloneExporter.mimeTypeFor(File("clip.m4a")))
    }

    @Test
    fun `an already exported m4a is the file the media folder receives`() {
        assertTrue(AudioTranscoder.exportedFileName("clip.M4A").endsWith(".m4a"))
    }
}

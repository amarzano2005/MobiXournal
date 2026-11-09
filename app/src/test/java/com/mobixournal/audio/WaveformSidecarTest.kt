package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The envelope kept beside a recording: reused only for the file it was computed from, and never
 * trusted when it cannot be read (`WaveformSidecar`).
 */
class WaveformSidecarTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `an envelope comes back exactly as it was kept`() {
        val audio = temp.newFile("lecture.ogg").apply { writeBytes(ByteArray(2048)) }
        val samples = List(80) { index -> index / 80f }
        WaveformSidecar.write(audio, samples)
        assertEquals(samples, WaveformSidecar.read(audio, 80))
    }

    @Test
    fun `an envelope belongs to the file it was computed from`() {
        val audio = temp.newFile("take.ogg").apply { writeBytes(ByteArray(1024)) }
        WaveformSidecar.write(audio, List(80) { 0.5f })

        audio.writeBytes(ByteArray(2048))
        assertNull("a longer recording cannot wear the old envelope", WaveformSidecar.read(audio, 80))

        val replaced = temp.newFile("replaced.ogg").apply { writeBytes(ByteArray(1024)) }
        WaveformSidecar.write(replaced, List(80) { 0.5f })
        assertTrue(replaced.setLastModified(replaced.lastModified() + 5_000))
        assertNull("a re-recorded take cannot either", WaveformSidecar.read(replaced, 80))
    }

    @Test
    fun `an envelope kept for one sample count is not stretched to another`() {
        val audio = temp.newFile("count.ogg").apply { writeBytes(ByteArray(512)) }
        WaveformSidecar.write(audio, List(80) { 0.4f })
        assertEquals(80, WaveformSidecar.read(audio, 80)?.size)
        assertNull(WaveformSidecar.read(audio, 120))
    }

    @Test
    fun `a torn or foreign file answers nothing instead of a wrong shape`() {
        val audio = temp.newFile("torn.ogg").apply { writeBytes(ByteArray(512)) }
        WaveformSidecar.write(audio, List(80) { 0.4f })
        val sidecar = WaveformSidecar.fileFor(audio)
        sidecar.writeBytes(sidecar.readBytes().copyOf(8))
        assertNull(WaveformSidecar.read(audio, 80))
        assertNull("a recording with no sidecar is decoded", WaveformSidecar.read(temp.newFile("plain.ogg"), 80))
    }

    @Test
    fun `writing leaves no temporary file behind`() {
        val audio = temp.newFile("clean.ogg").apply { writeBytes(ByteArray(512)) }
        WaveformSidecar.write(audio, List(80) { 0.3f })
        assertTrue(WaveformSidecar.fileFor(audio).isFile)
        assertFalse(java.io.File(audio.parentFile, audio.name + ".wave.tmp").exists())
    }
}

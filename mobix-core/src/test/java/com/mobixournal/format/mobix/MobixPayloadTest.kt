package com.mobixournal.format.mobix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MobixPayloadTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun fileMetadataCacheTracksLengthAndMtimeChanges() {
        val file = temp.newFile("sample.bin")
        val pngHeader = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        file.writeBytes(pngHeader)
        file.setLastModified(1_000_000L)

        val first = MobixPayload.of(file)
        val sha1 = first.sha256
        assertEquals("png", first.suffix)

        val cached = MobixPayload.of(file)
        assertEquals(sha1, cached.sha256)
        assertEquals("png", cached.suffix)

        // Updating the file content (and mtime) must invalidate the cached metadata immediately.
        val oggHeader = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(), 9, 8, 7, 6)
        file.writeBytes(oggHeader)
        file.setLastModified(2_000_000L)

        val updated = MobixPayload.of(file)
        assertNotEquals(sha1, updated.sha256)
        assertEquals("ogg", updated.suffix)
    }
}

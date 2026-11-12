package com.mobixournal.desktop

import com.mobixournal.desktop.io.archiveEntriesOf
import com.mobixournal.desktop.io.portableCopyName
import com.mobixournal.desktop.io.referenceFile
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of **Export As ▸ .pmobix** that can be wrong without a window: the name it writes, and where
 * it looks for the bytes a reference names.
 *
 * What it cannot test here is the copy itself — `PortableCopyTest` does that, against a real notebook. This
 * file pins the window's own decisions: an export is always a `.pmobix`, a background is resolved against the
 * document's folder (the way its painter resolves it), and the archive directory is read, not assumed.
 */
class DesktopPortableCopyTest {

    private val temporary = mutableListOf<File>()

    @After
    fun cleanUp() {
        temporary.forEach { it.deleteRecursively() }
    }

    private fun folder(): File = Files.createTempDirectory("portable-copy").toFile().also(temporary::add)

    @Test
    fun anExportIsAlwaysWrittenAsPmobixWhateverTheNameSays() {
        assertEquals("notes.pmobix", portableCopyName("notes.pmobix"))
        // A name typed in capitals keeps them: the export is written under the name the reader chose.
        assertEquals("notes.PMOBIX", portableCopyName("notes.PMOBIX"))
        // A plain notebook name, or another format's, becomes the portable one.
        assertEquals("notes.pmobix", portableCopyName("notes.xopp"))
        assertEquals("notes.pmobix", portableCopyName("notes.mobix"))
        assertEquals("notes.pmobix", portableCopyName("notes"))
        assertEquals("a.b.pmobix", portableCopyName("a.b.pdf"))
    }

    @Test
    fun aBackgroundResolvesInsideTheDocumentsOwnFolder() {
        val home = folder()
        val picture = File(home, "img_42.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals(picture, referenceFile("img_42.png", home))
        assertEquals(picture, referenceFile("  img_42.png  ", home))
        // A file the folder does not hold is not a file: the copy reports it, it does not invent it.
        assertNull(referenceFile("img_missing.png", home)?.takeIf { it.isFile })
        assertNull(referenceFile("", home))
        assertNull(referenceFile("img_42.png", null))
    }

    @Test
    fun theArchiveDirectoryIsReadFromTheFileAndIsEmptyWhenThereIsNone() {
        val home = folder()
        val archive = File(home, "notes.mobix")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            for (name in listOf("manifest.json", "pages/0.json", "assets/img_42.png")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(0))
                zip.closeEntry()
            }
        }

        val entries = archiveEntriesOf(archive)
        assertTrue("the copy's audit needs the asset entries: $entries", "assets/img_42.png" in entries)
        assertEquals(3, entries.size)

        // A `.xopp` has no archive behind it, and neither has a staged copy the session let go: the audit
        // then calls every reference absent, which is exactly what a fresh copy has to embed.
        assertTrue(archiveEntriesOf(File(home, "absent.mobix")).isEmpty())
        assertTrue(archiveEntriesOf(File(home, "notes.xopp")).isEmpty())
    }
}

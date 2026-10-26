package com.mobixournal.desktop

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Opening a notebook from **Explorer**, which is where a path stops being one path.
 *
 * Windows appends the document to the executable's command line; an unquoted association hands it over
 * with its spaces as separators, and the splitter squeezes a run of them into one. The tests below use a
 * real folder tree with the shape that broke it — a `09)  21-10-2026` (two spaces) beside an emoji — and
 * check that the window still opens the document, that it refuses to guess when the name is ambiguous,
 * and that a path it cannot repair is reported in words.
 */
class DesktopLaunchPathTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File {
        val folder = File(temp.root, "Il mio Drive/01) \uD83D\uDCDA Teoria/09)  21-10-2026/Materiale")
        folder.mkdirs()
        return File(folder, "AND, OR e NOR .mobix").apply { writeText("not really a notebook") }
    }

    /** What `main` actually receives: the path joined with single spaces. */
    private fun collapsed(real: File): String = real.path.replace("09)  21", "09) 21")

    @Test
    fun `a path whose double space Windows collapsed still opens`() {
        val real = notebook()
        val damaged = collapsed(real)
        assertFalse("the damaged path is not on disk", File(damaged).isFile)

        val opened = launchDocument(damaged)

        assertEquals("the document is found by name", real.absoluteFile, opened?.absoluteFile)
    }

    @Test
    fun `the launch argument finds it too, and does not fall back to the first piece`() {
        val real = notebook()
        val opened = initialDocument(arrayOf(collapsed(real)))

        assertEquals(real.absoluteFile, opened?.absoluteFile)
    }

    @Test
    fun `an unambiguous file still wins without any repair`() {
        val real = notebook()
        assertEquals(real.absoluteFile, initialDocument(arrayOf(real.path))?.absoluteFile)
    }

    /** Two files of that name under the surviving folder: opening either one would be a guess. */
    @Test
    fun `an ambiguous name is refused`() {
        val real = notebook()
        val extra = File(real.parentFile.parentFile, "Extra").apply { mkdirs() }
        File(extra, real.name).writeText("a second notebook with the same name")

        assertNull(launchDocument(collapsed(real)))
    }

    @Test
    fun `a document that is nowhere still names the whole path`() {
        val missing = File(temp.root, "nowhere/at all/notes.mobix")

        val named = initialDocument(arrayOf(missing.path))

        assertEquals("the window names the path it tried, not a piece of it", missing.path, named?.path)
    }

    @Test
    fun `the failure is a sentence a person can act on, not a class name`() {
        val card = describeOpenFailure(FileNotFoundException("C:\\notes\\lost.mobix"))

        assertEquals("This file is not on this PC", card.title)
        assertTrue("it names the path", card.detail.contains("lost.mobix"))
        assertFalse("and no exception type", card.detail.contains("Exception"))
    }
}

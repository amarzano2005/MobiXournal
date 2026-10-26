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
        assertTrue("the cloud is the cause worth naming", card.detail.contains("available offline"))
        assertFalse(
            "and the window never sends the user off to open it by hand",
            card.detail.contains("pick it with Open", ignoreCase = true),
        )
    }

    // --- the line Windows wrote, which is the only copy of a path that is still whole ----------

    private val exe = "C:\\apps\\MobiXournal\\MobiXournal.exe"

    /** `"…\MobiXournal.exe" C:\…\x.mobix` — what GetCommandLineW answers for an unquoted association. */
    private fun rawLine(path: String, quoted: Boolean = false): String =
        "\"$exe\" " + if (quoted) "\"$path\"" else path

    /**
     * The whole point of reading the native line: argv squeezed the double space out, the line still has
     * it, so the document opens without a word of repair.
     */
    @Test
    fun `the raw command line still has the space argv squeezed out`() {
        val real = notebook()
        assertFalse("the collapsed spelling is not a file", File(collapsed(real)).isFile)

        val opened = documentFromCommandLine(rawLine(real.path))

        assertEquals(real.absoluteFile, opened?.absoluteFile)
    }

    /** A properly quoted association gives the same answer through the same read. */
    @Test
    fun `a quoted path on the raw line reads the same`() {
        val real = notebook()
        assertEquals(real.absoluteFile, documentFromCommandLine(rawLine(real.path, quoted = true))?.absoluteFile)
    }

    /** The launch takes the intact line before it tries to reassemble the pieces. */
    @Test
    fun `the raw line wins over the damaged arguments`() {
        val real = notebook()
        val pieces = collapsed(real).split(" ").toTypedArray()

        val opened = initialDocument(pieces, rawLine(real.path))

        assertEquals(real.absoluteFile, opened?.absoluteFile)
    }

    /** The document is the **end** of the line: a jar or a main class named earlier is not the notebook. */
    @Test
    fun `the document is the end of the line, not a file named earlier in it`() {
        val real = notebook()
        val jar = File(temp.root, "app.jar").apply { writeText("not a document") }

        val line = "\"C:\\jre\\bin\\java.exe\" -cp \"$jar\" com.mobixournal.desktop.MainKt ${real.path}"

        assertEquals(real.absoluteFile, documentFromCommandLine(line)?.absoluteFile)
    }

    /** Nothing on the line names a file — an app started empty, or a host that cannot read the line. */
    @Test
    fun `a line that names no document opens nothing`() {
        assertNull(documentFromCommandLine("\"$exe\""))
        assertNull(documentFromCommandLine("\"$exe\" --diagnose"))
        assertNull(documentFromCommandLine(null))
    }
}

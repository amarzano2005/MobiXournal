package com.mobixournal.desktop

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.Background
import com.mobixournal.render.ImportPdfMode
import java.io.File
import java.util.zip.ZipFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Import PDF as the window commands it** — the two answers and what a save does with the bytes.
 *
 * The card is a card; the rules that can be wrong live here, on the state, so they are ordinary JVM tests:
 * that *replace* opens the PDF in a tab of its own and never throws the open document away (the PC's
 * version of the tablet's "discard", written for a window with tabs), that *add* lands after the page in
 * view as **one** undo step and drops a single untouched blank sheet, that a second PDF is joined into the
 * first rather than referenced twice, and that a save carries the PDF into the file when the container can
 * hold it.
 */
class DesktopPdfImportCommandsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A PDF of [pages] A4 pages in [folder], under a name no other case uses. */
    private fun pdf(pages: Int, folder: File = temp.root, name: String = "pdf"): File =
        File(folder, "$name-${pages}-${System.nanoTime()}.pdf").also { file ->
            PDDocument().use { document ->
                repeat(pages) { document.addPage(PDPage(PDRectangle.A4)) }
                document.save(file)
            }
        }

    @Test
    fun `replace opens the PDF in a new tab and leaves the open document alone`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val open = state.tabs.active!!
            val file = pdf(3)

            assertTrue(state.importPdf(file, ImportPdfMode.REPLACE))

            assertEquals("the document that was open is still open", 2, state.tabs.size)
            assertSame(open, state.tabs.all[0])
            val imported = state.tabs.active!!
            assertEquals("the PDF's pages are its pages", 3, imported.document.pageCount)
            assertTrue("nothing holds them yet, so it starts dirty", imported.isDirty)
            assertEquals("and it is proposed beside the PDF", temp.root, imported.file.parentFile)
            assertTrue("with a name the settings asked for: ${imported.file}", imported.file.name.endsWith(".xopp"))
            assertNull(state.failure)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `add lands after a lone blank sheet by replacing it, as one undo step`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val tab = state.tabs.active!!
            assertEquals("a new document is one empty sheet", 1, tab.document.pageCount)

            assertTrue(state.importPdf(pdf(3), ImportPdfMode.APPEND))

            assertEquals("the blank sheet is not stranded in front of the PDF", 3, tab.document.pageCount)
            assertEquals("and the reader lands on the PDF", 0, tab.page)
            assertTrue("the tab holds work the file does not", tab.isDirty)

            assertTrue("one step, not three", tab.undo())
            assertEquals("undo takes the whole import back", 1, tab.document.pageCount)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `add onto a document continues after its last page`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val tab = state.tabs.active!!
            state.importPdf(pdf(2, name = "first"), ImportPdfMode.APPEND)
            assertEquals(2, tab.document.pageCount)

            assertTrue(state.importPdf(pdf(3, name = "second"), ImportPdfMode.APPEND))

            assertEquals("the open pages stay, the new ones follow", 5, tab.document.pageCount)
            assertEquals("and the reader lands on the first arriving page", 2, tab.page)
            assertEquals(
                "the numbers continue across the joined file",
                listOf(0, 1, 2, 3, 4),
                tab.document.model.pages.map { (it.background as Background.Pdf).pageNo },
            )
            val references = tab.document.model.pages.mapNotNull { (it.background as? Background.Pdf)?.filename }.distinct()
            assertEquals("one PDF reference, never two", 1, references.size)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a mobix save bundles the PDF the import brought`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val file = pdf(2)
            assertTrue(state.importPdf(file, ImportPdfMode.APPEND))
            val target = File(temp.root, "carried.mobix")

            state.saveAs(target, SaveFormat.MOBIX)
            assertNull(state.failure)

            ZipFile(target).use { zip ->
                assertTrue(
                    "the archive carries the PDF's bytes: ${zip.entries().toList().map { it.name }}",
                    zip.entries().toList().any { it.name.endsWith(".pdf") },
                )
            }
            DocumentLoader.load(target).use { reopened ->
                val background = reopened.model.pages.first().background
                assertTrue("and the page is still a PDF page: $background", background is Background.Pdf)
                // The strongest reading of "it travelled": the reopened document paints its own page from
                // bytes it carries, with no folder to help it.
                val source = reopened.backdrop.sourcePdf(background as Background.Pdf)
                assertNotNull("the PDF is reachable from the file itself", source)
                source!!.use { assertEquals("and it is the picked PDF", 2, it.numberOfPages) }
            }
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a zipped xopp attaches the PDF the import brought`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            assertTrue(state.importPdf(pdf(2), ImportPdfMode.APPEND))
            val zipped = File(temp.root, "attached.xopp")

            state.saveAs(zipped, SaveFormat.ZIPPED)

            assertNull(state.failure)
            ZipFile(zipped).use { zip -> assertNotNull("the package carries its PDF", zip.getEntry("bg.pdf")) }
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a plain xopp names the PDF beside it`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val file = pdf(2)
            assertTrue(state.importPdf(file, ImportPdfMode.APPEND))
            val plain = File(temp.root, "linked.xopp")

            state.saveAs(plain, SaveFormat.ORIGINAL)

            assertNull(state.failure)
            val reference = state.tabs.active!!.document.model.pages.first().background as Background.Pdf
            assertEquals("a gzip .xopp names the file and nothing more", file.name, reference.filename)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a PDF that cannot be read changes nothing and says why`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val tab = state.tabs.active!!
            val broken = File(temp.root, "broken.pdf").apply { writeText("not a PDF") }

            assertTrue("nothing lands", !state.importPdf(broken, ImportPdfMode.APPEND))
            assertEquals("the document is as it was", 1, tab.document.pageCount)
            assertEquals("and the window says why", "This PDF could not be imported", state.failure?.title)
        } finally {
            state.tabs.closeAll()
        }
    }
}

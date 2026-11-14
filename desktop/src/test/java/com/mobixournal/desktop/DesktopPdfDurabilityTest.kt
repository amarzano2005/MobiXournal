package com.mobixournal.desktop

import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.io.DesktopPdfImport
import com.mobixournal.desktop.io.DesktopPdfMerge
import com.mobixournal.desktop.io.DesktopPdfSources
import com.mobixournal.desktop.io.importPdf
import com.mobixournal.desktop.io.saveAs
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTabs

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.Background
import com.mobixournal.render.ImportPdfMode
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **A joined PDF is a document's dependency, not a tab's** — the lifetime rules the window keeps.
 *
 * Joining is what a `.xopp` forces (`docs/pdf.md`): one reference per document, so a second import is
 * merged into the first instead of referenced beside it. Two things then have to hold, and neither is
 * visible on screen. Each join is a **fresh file**, so two tabs built on one PDF cannot overwrite each
 * other's background. And a join output is scratch, so a plain `.xopp` save — the one container that
 * names the PDF by path — publishes it somewhere durable before writing the file that points at it.
 *
 * The third rule is the append's own: a document that carries its PDF **inside** it (a `.mobix` bundle)
 * has a background but no file, and reading that as "no background" appended the picked PDF while the
 * old reference stayed behind it. It joins; and a document whose reference resolves to nothing is
 * refused rather than half-rewritten.
 *
 * The two folders the production code chooses are moved into the temporary folder here, so a run never
 * writes into the installed window's `%TEMP%` or `%APPDATA%`.
 */
class DesktopPdfDurabilityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var scratch: File
    private lateinit var published: File

    @Before
    fun useOwnFolders() {
        scratch = temp.newFolder("scratch")
        published = temp.newFolder("published")
        System.setProperty(DesktopPdfMerge.SCRATCH_PROPERTY, scratch.absolutePath)
        System.setProperty(DesktopPdfSources.SOURCES_PROPERTY, published.absolutePath)
    }

    @After
    fun forgetOwnFolders() {
        System.clearProperty(DesktopPdfMerge.SCRATCH_PROPERTY)
        System.clearProperty(DesktopPdfSources.SOURCES_PROPERTY)
    }

    /** A PDF of [pages] A4 pages, written under a name no other case uses. */
    private fun pdf(pages: Int, name: String): File =
        File(temp.root, "$name-${System.nanoTime()}.pdf").also { file ->
            PDDocument().use { document ->
                repeat(pages) { document.addPage(PDPage(PDRectangle.A4)) }
                document.save(file)
            }
        }

    /** The one PDF reference a page list carries, as the file it names. */
    private fun referenceOf(state: DesktopShellState): File? = state.tabs.active!!.document.model.pages
        .firstNotNullOfOrNull { (it.background as? Background.Pdf)?.filename }
        ?.let(::File)

    private fun painted(tab: com.mobixournal.desktop.shell.DesktopTab): List<Boolean> {
        val document = tab.document
        return (0 until document.pageCount).map { index ->
            document.backdrop.image(document.page(index).background) != null
        }
    }

    @Test
    fun `two tabs on one PDF keep their own joined background`() {
        val base = pdf(2, "base")
        val state = DesktopShellState()
        try {
            state.newDocument()
            assertTrue(state.importPdf(base, ImportPdfMode.REPLACE))
            val first = state.tabs.active!!
            assertTrue(state.importPdf(base, ImportPdfMode.REPLACE))
            val second = state.tabs.active!!

            state.tabs.activate(first)
            assertTrue(state.importPdf(pdf(1, "first-extra"), ImportPdfMode.APPEND))
            state.tabs.activate(second)
            assertTrue(state.importPdf(pdf(3, "second-extra"), ImportPdfMode.APPEND))

            state.tabs.activate(first)
            val firstJoined = referenceOf(state)
            state.tabs.activate(second)
            val secondJoined = referenceOf(state)

            assertNotNull(firstJoined)
            assertNotNull(secondJoined)
            assertFalse(
                "a join output is never reused: $firstJoined / $secondJoined",
                firstJoined!!.absoluteFile == secondJoined!!.absoluteFile,
            )
            assertEquals("the first tab kept its own pages", 3, first.document.pageCount)
            assertEquals("and the second tab its own", 5, second.document.pageCount)
            assertEquals("each background holds its tab's pages", 3, DesktopPdfImport.pageCount(firstJoined))
            assertEquals(5, DesktopPdfImport.pageCount(secondJoined))
            assertEquals("and both still draw", listOf(true, true, true), painted(first))
            assertEquals(listOf(true, true, true, true, true), painted(second))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a plain xopp save publishes the joined PDF so it outlives the scratch folder`() {
        val out = temp.newFolder("out")
        val target = File(out, "linked.xopp")
        val state = DesktopShellState()
        try {
            state.newDocument()
            assertTrue(state.importPdf(pdf(2, "base"), ImportPdfMode.APPEND))
            assertTrue(state.importPdf(pdf(1, "extra"), ImportPdfMode.APPEND))
            assertEquals("the two files are joined", 3, state.tabs.active!!.document.pageCount)

            state.saveAs(target, SaveFormat.ORIGINAL)
            check(state.failure == null) { "save failed: ${state.failure?.title}" }
        } finally {
            state.tabs.closeAll()
        }

        // The OS empties its temp folder whenever it likes; the saved document has to survive that.
        scratch.walkBottomUp().forEach { if (it.isFile) it.delete() }

        val reopened = DesktopTabs().also { it.open(DocumentLoader.load(target)) }
        try {
            val document = reopened.active!!.document
            assertEquals("the saved file still knows its three pages", 3, document.pageCount)
            val reference = document.model.pages
                .firstNotNullOfOrNull { (it.background as? Background.Pdf)?.filename }
                ?.let(::File)
            assertNotNull("and still names a PDF", reference)
            assertTrue("published out of scratch: $reference", reference!!.isFile)
            assertTrue("in the window's own folder", reference.absolutePath.startsWith(published.absolutePath))
            val painted = (0 until document.pageCount).map { index ->
                document.backdrop.image(document.page(index).background) != null
            }
            assertEquals("every page draws from it", listOf(true, true, true), painted)
        } finally {
            reopened.closeAll()
        }
    }

    @Test
    fun `appending to a document that carries its PDF inside joins instead of replacing it`() {
        val notebook = temp.newFile("carried.mobix")
        val base = pdf(2, "base")
        val builder = DesktopShellState()
        try {
            builder.newDocument()
            assertTrue(builder.importPdf(base, ImportPdfMode.APPEND))
            builder.saveAs(notebook, SaveFormat.MOBIX)
            check(builder.failure == null) { "save failed: ${builder.failure?.title}" }
        } finally {
            builder.tabs.closeAll()
        }
        // The original is gone: the archive carries the PDF, and nothing external can help it.
        base.delete()

        val state = DesktopShellState()
        try {
            state.tabs.open(DocumentLoader.load(notebook))
            val tab = state.tabs.active!!
            assertEquals("the notebook's own two pages", 2, tab.document.pageCount)
            assertNotNull("it carries a PDF", tab.document.pdfBackground())
            assertNull("with no file of its own", tab.document.pdfSource())

            assertTrue("the append joins it", state.importPdf(pdf(1, "extra"), ImportPdfMode.APPEND))

            assertEquals("both PDFs are in the document", 3, tab.document.pageCount)
            val references = tab.document.model.pages
                .mapNotNull { (it.background as? Background.Pdf)?.filename }.distinct()
            assertEquals("one reference, never two", 1, references.size)
            assertEquals("the joined file holds every sheet", 3, DesktopPdfImport.pageCount(File(references.single())))
            assertEquals("and every page draws", listOf(true, true, true), painted(tab))
            assertTrue("one undo step takes the whole append back", tab.undo())
            assertEquals(2, tab.document.pageCount)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a document whose PDF background is gone is refused and left exactly as it was`() {
        val base = pdf(2, "base")
        val state = DesktopShellState()
        try {
            state.newDocument()
            assertTrue(state.importPdf(base, ImportPdfMode.APPEND))
            val tab = state.tabs.active!!
            val before = tab.document.model.pages
            base.delete()

            assertFalse("nothing lands", state.importPdf(pdf(1, "extra"), ImportPdfMode.APPEND))

            assertEquals("the pages are intact", 2, tab.document.pageCount)
            assertEquals("and so are their backgrounds", before, tab.document.model.pages)
            assertEquals("the window says what failed", "This PDF could not be imported", state.failure?.title)
            assertTrue(
                "in words that say what to put back: ${state.failure?.detail}",
                state.failure?.detail?.contains("cannot be found") == true,
            )
        } finally {
            state.tabs.closeAll()
        }
    }
}

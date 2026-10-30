package com.mobixournal.desktop

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import com.mobixournal.format.model.Background
import com.mobixournal.render.ImportPdfMode
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Import PDF · a range of pages** — bringing pages 5–20 of a scan rather than all four hundred.
 *
 * What the range changes is *which source pages* become sheets; what it must not change is the numbering:
 * a page's `pageno` is the page of the PDF it was made from, never the position of the run, so an import
 * that starts at page 5 draws page 5 of that PDF ([DesktopPdfImport.pages]).
 */
class DesktopPdfImportRangeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun pdf(count: Int, name: String = "range"): File {
        val file = File(temp.root, "$name-$count-${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            repeat(count) { doc.addPage(PDPage(PDRectangle(595f, 842f))) }
            doc.save(file)
        }
        return file
    }

    private fun pageNos(document: DesktopDocument): List<Int> =
        document.model.pages.map { (it.background as Background.Pdf).pageNo }

    private fun painted(document: DesktopDocument): List<Boolean> =
        (0 until document.pageCount).map { document.backdrop.image(document.page(it).background) != null }

    @Test
    fun `a run of source pages keeps its own numbering`() {
        val file = pdf(5)
        DesktopPdfImport.open(file).use { doc ->
            val pages = DesktopPdfImport.pages(doc, file.absolutePath, indices = 2..3)

            assertEquals("two sheets for two source pages", 2, pages.size)
            assertEquals("the numbering is the source's", listOf(2, 3), pages.map { (it.background as Background.Pdf).pageNo })
            assertEquals("the reference rides the first page of the run", file.absolutePath, (pages[0].background as Background.Pdf).filename)
            assertEquals("and only that one", null, (pages[1].background as Background.Pdf).filename)
        }
    }

    @Test
    fun `replace brings only the picked pages and draws them all`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val file = pdf(4)
            assertTrue(state.importPdf(file, ImportPdfMode.REPLACE, 1..2))

            val document = state.tabs.active!!.document
            assertEquals(2, document.pageCount)
            assertEquals(listOf(1, 2), pageNos(document))
            assertEquals("both sheets sit on the PDF", listOf(true, true), painted(document))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `add brings only the picked pages and draws them all`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val file = pdf(4)
            assertTrue(state.importPdf(file, ImportPdfMode.APPEND, 2..3))

            val document = state.tabs.active!!.document
            assertEquals(2, document.pageCount)
            assertEquals(listOf(2, 3), pageNos(document))
            assertEquals(listOf(true, true), painted(document))
        } finally {
            state.tabs.closeAll()
        }
    }

    /** A range onto a document already backed by a PDF: joined, then numbered by the **joined** file. */
    @Test
    fun `a range added to a pdf-backed document numbers the joined file`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val first = pdf(3, "first")
            val second = pdf(2, "second")
            assertTrue(state.importPdf(first, ImportPdfMode.APPEND))
            assertTrue(state.importPdf(second, ImportPdfMode.APPEND, 1..1))

            val document = state.tabs.active!!.document
            assertEquals("the base's three pages and the one picked", 4, document.pageCount)
            assertEquals("the picked page is the joined file's fourth", listOf(0, 1, 2, 4), pageNos(document))
            assertNotNull("one reference, the joined file", document.pdfSource())
            assertEquals("and every page draws", listOf(true, true, true, true), painted(document))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the card opens on the whole pdf and refuses a range outside it`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val file = pdf(3)
            state.askImportPdf(file)
            val request = state.importRequest!!
            assertEquals("every page is proposed", "1", request.first)
            assertEquals("3", request.last)
            assertTrue("and it is usable", request.range is PageRangePlan.Pages)

            state.editImportPdf { it.copy(first = "2", last = "9") }
            assertTrue("past the end is refused", state.importRequest!!.range is PageRangePlan.Invalid)
            state.cancelImportPdf()
        } finally {
            state.tabs.closeAll()
        }
    }
}

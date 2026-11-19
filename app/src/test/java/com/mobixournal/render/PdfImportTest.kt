package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Where an imported PDF's pages land** — [PdfImport.pagesFor]'s numbering, without the Android renderer.
 *
 * The one rule: a `pageno` is the page's own number in the file the document will show. An append onto a
 * PDF-backed document joins the two files, so the arriving pages start at the existing file's page count;
 * an append onto a notebook of paper names the picked file and starts at zero. The shift is the offset and
 * nothing else — a run handed over already shifted falls outside the picked file and is dropped, which is
 * how an append once seeded its Contents rows and brought no pages at all.
 */
class PdfImportTest {

    private val sheet: (Int) -> Pair<Double, Double> = { 595.276 to 841.89 }

    private fun background(page: Page) = page.background as Background.Pdf

    private fun pagenos(pages: List<Page>) = pages.map { background(it).pageNo }

    @Test
    fun `a fresh import numbers the file's own pages and names it on the first one`() {
        val pages = PdfImport.pagesFor(3, sheet, reference = "content://a.pdf")

        assertEquals(3, pages.size)
        assertEquals(listOf(0, 1, 2), pagenos(pages))
        assertEquals("content://a.pdf", background(pages[0]).filename)
        assertEquals("absolute", background(pages[0]).domain)
        assertNull("one reference for the run", background(pages[1]).filename)
        assertEquals(595.276, pages[0].width, 1e-9)
        assertEquals(841.89, pages[0].height, 1e-9)
        assertEquals(1, pages[0].layers.size)
    }

    @Test
    fun `a joined append numbers the arriving pages after the document's own`() {
        // The document already holds five pages of its PDF; the picked file has three.
        val pages = PdfImport.pagesFor(3, sheet, reference = null, pageNoOffset = 5)

        assertEquals("every picked page arrives", 3, pages.size)
        assertEquals(listOf(5, 6, 7), pagenos(pages))
        assertNull("the joined file is the document's own reference", background(pages[0]).filename)
    }

    @Test
    fun `a picked run keeps the file's own page numbers, not the run's`() {
        val pages = PdfImport.pagesFor(10, sheet, reference = "content://a.pdf", indices = listOf(4, 5, 9))

        assertEquals(listOf(4, 5, 9), pagenos(pages))
        assertEquals("content://a.pdf", background(pages[0]).filename)
        assertNull(background(pages[1]).filename)
    }

    @Test
    fun `a picked chapter of a joined append lands after the document's pages`() {
        val pages = PdfImport.pagesFor(3, sheet, reference = null, pageNoOffset = 5, indices = listOf(1, 2))

        assertEquals(2, pages.size)
        assertEquals(listOf(6, 7), pagenos(pages))
    }

    @Test
    fun `indices the picked file does not have are dropped`() {
        assertEquals(2, PdfImport.pagesFor(3, sheet, reference = null, indices = listOf(0, 2, 7)).size)
    }
}

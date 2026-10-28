package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.render.ABSOLUTE_DOMAIN
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **A PDF read as pages to write on** — the shape the tablet's importer produces, produced on the PC.
 *
 * Every case builds its own PDF with PDFBox and reads it back, because the claim is about the document a
 * reader gets: one page per PDF page, the sheet the size of the PDF page, and exactly **one** reference —
 * the first page's `filename`+`domain`, the rest naming it by `pageno` alone (`docs/pdf.md` owns why: a
 * `.xopp` can hold one PDF, and this is how the rest of the pages point into it).
 *
 * The last two cases are the ones that would otherwise be discovered on a real notebook: that the window's
 * existing painter resolves the reference this builds (an absolute path, with no document folder to help
 * it), and that a file which is not a PDF is refused in a sentence instead of an empty document.
 */
class DesktopPdfImportTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A PDF of [pages] pages of [size], written under a name no other case uses. */
    private fun pdf(pages: Int = 3, size: PDRectangle = PDRectangle.A4, name: String = "source"): File =
        File(temp.root, "$name-${System.nanoTime()}.pdf").also { file ->
            PDDocument().use { document ->
                repeat(pages) { document.addPage(PDPage(size)) }
                document.save(file)
            }
        }

    @Test
    fun `one page per PDF page, and only the first carries the reference`() {
        val file = pdf(3)
        DesktopPdfImport.open(file).use { pdf ->
            val pages = DesktopPdfImport.pages(pdf, file.absolutePath)
            assertEquals("a page for every PDF page", 3, pages.size)
            assertEquals("the sheet is the PDF page's width", PDRectangle.A4.width.toDouble(), pages[0].width, 0.01)
            assertEquals("and its height", PDRectangle.A4.height.toDouble(), pages[0].height, 0.01)
            assertTrue("a page has an empty layer to draw on", pages.all { it.layers.size == 1 })

            val first = pages[0].background as Background.Pdf
            assertEquals("the first page names the PDF", file.absolutePath, first.filename)
            assertEquals("as an absolute reference", ABSOLUTE_DOMAIN, first.domain)
            assertEquals(0, first.pageNo)

            for (index in 1 until 3) {
                val next = pages[index].background as Background.Pdf
                assertNull("the rest do not name it again", next.filename)
                assertNull(next.domain)
                assertEquals("they point into it by number", index, next.pageNo)
            }
        }
    }

    @Test
    fun `an offset renumbers the pages inside a joined file`() {
        val file = pdf(3)
        DesktopPdfImport.open(file).use { pdf ->
            val pages = DesktopPdfImport.pages(pdf, reference = null, pageNoOffset = 5)
            assertEquals("the numbers continue where the base PDF ended", listOf(5, 6, 7), pages.map {
                (it.background as Background.Pdf).pageNo
            })
            assertNull("an appended run names no reference of its own", pages.first().let {
                (it.background as Background.Pdf).filename
            })
        }
    }

    @Test
    fun `a rotated page is a turned sheet`() {
        val file = File(temp.root, "turned.pdf").also { target ->
            PDDocument().use { document ->
                val page = PDPage(PDRectangle.A4)
                page.rotation = 90
                document.addPage(page)
                document.save(target)
            }
        }
        DesktopPdfImport.open(file).use { pdf ->
            val page = DesktopPdfImport.pages(pdf, file.absolutePath).single()
            assertEquals("the sheet follows the rotation", PDRectangle.A4.height.toDouble(), page.width, 0.01)
            assertEquals(PDRectangle.A4.width.toDouble(), page.height, 0.01)
        }
    }

    @Test
    fun `the window paints what the import built`() {
        val file = pdf(2)
        DesktopPdfImport.open(file).use { pdf ->
            val background = DesktopPdfImport.document(pdf, file.absolutePath)
                .pages.first().background as Background.Pdf
            // No bundled bytes and no document folder: only the absolute reference can answer, which is
            // how a freshly imported PDF has to paint before the document is ever saved.
            val source = PageBackdrop(bundled = { null }, documentDir = null).sourcePdf(background)
            assertNotNull("the absolute reference resolves on its own", source)
            source!!.use { assertEquals("and it is the picked PDF", 2, it.numberOfPages) }
        }
    }

    @Test
    fun `a file that is not a PDF is refused in words`() {
        val notes = File(temp.root, "notes.txt").apply { writeText("this is not a PDF at all") }
        val refused = runCatching { DesktopPdfImport.open(notes) }.exceptionOrNull()
        assertTrue("the refusal is the window's own: $refused", refused is DesktopPdfImport.PdfImportRefused)
        val message = refused!!.message.orEmpty()
        assertTrue("it names the file: $message", message.contains("notes.txt"))
        assertTrue("and it says what to do: $message", message.contains("could not be read as a PDF"))
    }
}

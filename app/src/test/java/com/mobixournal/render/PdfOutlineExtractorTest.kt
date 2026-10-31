package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * That PDFBox's walk reads a real file's outline back with the right pages and nesting, into the
 * shared [PdfOutlineTree]. The flattening rules themselves are `mobix-core`'s own test: this one is
 * the Android port's glue, and the window has a twin of it over `org.apache.pdfbox`.
 */
class PdfOutlineExtractorTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Three A4 pages whose outline is Chapter 1 (p1) → Section 1.1 (p3), then Chapter 2 (p2). */
    private fun pdfWithOutline(): File {
        val file = tmp.newFile("outlined.pdf")
        PDDocument().use { doc ->
            val one = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val two = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val three = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val outline = PDDocumentOutline()
            doc.documentCatalog.documentOutline = outline
            val chapter = PDOutlineItem().apply {
                title = "Chapter 1"
                setDestination(one)
            }
            outline.addLast(chapter)
            chapter.addLast(
                PDOutlineItem().apply {
                    title = "Section 1.1"
                    setDestination(three)
                },
            )
            outline.addLast(
                PDOutlineItem().apply {
                    title = "Chapter 2"
                    setDestination(two)
                },
            )
            doc.save(file)
        }
        return file
    }

    @Test fun `an outline is read back with its destinations and nesting`() {
        val outline = PdfOutlineExtractor().extract(pdfWithOutline())
        assertEquals(
            listOf("Chapter 1" to 0, "Section 1.1" to 2, "Chapter 2" to 1),
            outline.entries.map { it.title to it.pageIndex },
        )
        assertEquals(listOf(0, 1, 0), outline.entries.map { it.depth })
    }

    @Test fun `a PDF without an outline extracts empty`() {
        val file = tmp.newFile("plain.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        }
        assertTrue(PdfOutlineExtractor().extract(file).isEmpty)
    }

    @Test fun `a file that is not a PDF extracts empty rather than throwing`() {
        val file = tmp.newFile("not.pdf").apply { writeText("definitely not a PDF") }
        assertTrue(PdfOutlineExtractor().extract(file).isEmpty)
    }
}

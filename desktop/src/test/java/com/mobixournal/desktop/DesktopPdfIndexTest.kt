package com.mobixournal.desktop

import com.mobixournal.render.PdfOutlineEntry
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's two PDF readings, on fixture files built by PDFBox — the same way `DesktopPdfImportTest`
 * builds its PDFs, so what is asserted is the real port and not a stub.
 *
 * The text half is the one with a trap in it: PDFBox's `yDirAdj` is a glyph's **bottom**, and reading it
 * as the top puts every word a line away from the ink it sits on — a selection then highlights the wrong
 * line, which looks like a hit-testing bug rather than a coordinate one.
 */
class DesktopPdfIndexTest {

    @get:Rule val temp = TemporaryFolder()

    /** Two A4 pages: page 1 says "Magnetic highlighter", page 2 says "Second page". */
    private fun textPdf(): File {
        val file = temp.newFile("text.pdf")
        PDDocument().use { doc ->
            val first = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            PDPage(PDRectangle.A4).also { doc.addPage(it) }
            PDPageContentStream(doc, first).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 760f)
                stream.showText("Magnetic highlighter")
                stream.endText()
            }
            doc.save(file)
        }
        return file
    }

    /** Three pages whose outline is Chapter 1 (p1) → Section 1.1 (p3), then Chapter 2 (p2). */
    private fun outlinedPdf(): File {
        val file = temp.newFile("outlined.pdf")
        PDDocument().use { doc ->
            val one = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val two = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val three = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val outline = PDDocumentOutline().also { doc.documentCatalog.documentOutline = it }
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

    private fun read(file: File) = DesktopPdfIndexer.read(file.readBytes())

    @Test
    fun `a word's box sits on its own line and anchors a selection there`() {
        val index = read(textPdf())
        val words = index.words(0)
        assertTrue("the fixture has a text layer", words.isNotEmpty())
        val line = words.first()
        assertEquals("Magnetic", line.text)
        // A 12 pt glyph: the box is a dozen points tall and its top is above its bottom, y-down.
        assertTrue("the box is not inverted", line.top < line.bottom)
        assertTrue("the box is about a glyph tall", line.bottom - line.top in 6.0..20.0)
        // A point inside the box resolves to that word, whichever word of the line it is.
        assertNotNull(index.text.anchorWord(0, line.centerX, line.centerY))
        // A point on the blank half of the page still answers a word of the page, not of the next one.
        assertEquals(0, index.text.anchorWord(0, line.centerX, line.centerY))
    }

    @Test
    fun `a nested outline flattens with its destinations and depths`() {
        val index = read(outlinedPdf())
        assertEquals(
            listOf("Chapter 1" to 0, "Section 1.1" to 2, "Chapter 2" to 1),
            index.outline.entries.map { it.title to it.pageIndex },
        )
        assertEquals(listOf(0, 1, 0), index.outline.entries.map { it.depth })
    }

    @Test
    fun `a text-only PDF has an empty outline`() {
        assertTrue(read(textPdf()).outline.isEmpty)
    }

    @Test
    fun `an outline-only PDF has no words`() {
        val index = read(outlinedPdf())
        assertFalse("blank pages carry no text", index.text.hasAnyText)
    }

    @Test
    fun `a corrupt file answers empty rather than throwing`() {
        val index = DesktopPdfIndexer.read("definitely not a PDF".toByteArray())
        assertTrue(index.outline.isEmpty)
        assertFalse(index.text.hasAnyText)
    }

    @Test
    fun `the cache serves a second ask without reading the file again`() {
        val bytes = textPdf().readBytes()
        val reference = com.mobixournal.format.model.Background.Pdf(
            filename = "text.pdf",
            pageNo = 0,
            domain = "absolute",
        )
        val key = DesktopPdfIndexCache.key(reference, bytes.size)
        try {
            val first = DesktopPdfIndexCache.load(key) { bytes }
            var readAgain = false
            val second = DesktopPdfIndexCache.load(key) {
                readAgain = true
                bytes
            }
            assertSame("the second ask is the first answer", first, second)
            assertFalse("and the bytes were not read again", readAgain)
        } finally {
            DesktopPdfIndexCache.forgetAll()
        }
    }

    @Test
    fun `a key includes the length so replaced bytes are a different PDF`() {
        val reference = com.mobixournal.format.model.Background.Pdf(
            filename = "text.pdf",
            pageNo = 0,
            domain = "absolute",
        )
        assertTrue(
            DesktopPdfIndexCache.key(reference, 10) != DesktopPdfIndexCache.key(reference, 11),
        )
    }

    @Test
    fun `an entry that points nowhere is not jumpable`() {
        val index = read(outlinedPdf())
        assertTrue(index.outline.entries.all { it.hasDestination })
        assertFalse(PdfOutlineEntry("header", PdfOutlineEntry.NO_DESTINATION, 0).hasDestination)
    }
}

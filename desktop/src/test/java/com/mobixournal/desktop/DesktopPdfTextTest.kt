package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopPdfText
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **PDF text layer**, and what reading it costs.
 *
 * The words are the tablet's own geometry (`PdfWordGrouper`, `glyphBoxes`), so the claims worth pinning
 * here are the two that are about this shell: an empty page in the middle must leave the pages after it
 * where they belong, and the document must be walked **once** — the loop that asked PDFBox for one page
 * at a time re-parsed the file per page, which on a long scan is the difference between a second and a
 * minute.
 */
class DesktopPdfTextTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun textPage(doc: PDDocument, word: String) {
        val page = PDPage(PDRectangle(595f, 842f))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 12f)
            cs.newLineAtOffset(72f, 700f)
            cs.showText(word)
            cs.endText()
        }
    }

    private fun document(pages: Int, word: (Int) -> String?, name: String): PDDocument {
        val file = File(temp.root, name)
        PDDocument().use { doc ->
            repeat(pages) { index -> word(index)?.let { textPage(doc, it) } ?: doc.addPage(PDPage(PDRectangle(595f, 842f))) }
            doc.save(file)
        }
        return PDDocument.load(file)
    }

    @Test
    fun anEmptyMiddlePageStaysEmptyAndTheWordsKeepTheirBoxes() {
        document(3, { index -> when (index) { 0 -> "alpha"; 2 -> "omega"; else -> null } }, "three.pdf").use { doc ->
            val index = DesktopPdfText.of(doc)

            assertTrue("the middle page has no words", index.words(1).isEmpty())
            val first = index.words(0).single()
            assertEquals("alpha", first.text)
            assertTrue("with a real box: $first", first.right > first.left && first.bottom > first.top)
            assertEquals("the page after the empty one is not shifted", "omega", index.words(2).single().text)
            assertEquals("one traversal for the whole file", 1, DesktopPdfText.traversals)
        }
    }

    @Test
    fun aLongDocumentIsWalkedOnceRatherThanOncePerPage() {
        document(40, { index -> "page$index" }, "long.pdf").use { doc ->
            val index = DesktopPdfText.of(doc)

            assertEquals("the last page still has its own words", "page39", index.words(39).single().text)
            assertEquals("one traversal for forty pages", 1, DesktopPdfText.traversals)
        }
    }
}

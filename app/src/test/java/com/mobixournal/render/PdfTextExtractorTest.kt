package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The tablet's **PDF text layer**, as the words a selection resolves on — and as the cost of reading them.
 *
 * The two claims this file holds are the ones a reader cannot see. The words must be filed under the page
 * they came from (an empty page in the middle must not shift the pages after it), and the whole document
 * must be walked **once**: asking PDFBox for each page in turn parsed the file once per page, which on a
 * scanned lecture is a minute of extraction instead of a second.
 */
class PdfTextExtractorTest {

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

    private fun blankPage(doc: PDDocument) = doc.addPage(PDPage(PDRectangle(595f, 842f)))

    @Test
    fun anEmptyMiddlePageStaysEmptyAndTheWordsKeepTheirBoxes() {
        val file = File(temp.root, "three-pages.pdf")
        PDDocument().use { doc ->
            textPage(doc, "alpha")
            blankPage(doc)
            textPage(doc, "omega")
            doc.save(file)
        }

        val extractor = PdfTextExtractor()
        val index = extractor.extract(file)

        assertTrue("the middle page has no words at all", index.words(1).isEmpty())
        val first = index.words(0).single()
        assertEquals("the first page keeps its word", "alpha", first.text)
        assertTrue("with a real box: $first", first.right > first.left && first.bottom > first.top)
        assertTrue("placed on the page", first.left > 0.0 && first.top > 0.0)
        assertEquals("and the page after the empty one is not shifted", "omega", index.words(2).single().text)
        assertEquals("one traversal for the whole file", 1, extractor.traversals)
    }

    @Test
    fun aLongDocumentIsWalkedOnceRatherThanOncePerPage() {
        val file = File(temp.root, "long.pdf")
        PDDocument().use { doc ->
            repeat(40) { page -> textPage(doc, "page$page") }
            doc.save(file)
        }

        val extractor = PdfTextExtractor()
        val index = extractor.extract(file)

        assertEquals("the last page still has its own words", "page39", index.words(39).single().text)
        assertEquals("one traversal for forty pages", 1, extractor.traversals)
    }

    @Test
    fun aFileThatIsNotAPdfAnswersNothingAndIsNotWalked() {
        val file = File(temp.root, "broken.pdf").apply { writeText("not a PDF") }

        val extractor = PdfTextExtractor()

        assertFalse("no words", extractor.extract(file).hasAnyText)
        assertEquals("and nothing was traversed", 0, extractor.traversals)
    }
}

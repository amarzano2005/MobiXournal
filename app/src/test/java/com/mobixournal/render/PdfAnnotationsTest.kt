package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The exported PDF carries **real** annotation dictionaries beside the flattened paint, so a
 * standards-compliant reader lists the marks: a highlighter over text is a `/Highlight` with
 * `/QuadPoints`, handwriting is `/Ink`, and a text box is a `/Text` note. The paint is still there —
 * these tests check the additions, not a replacement.
 */
class PdfAnnotationsTest {

    private val width = 595.0
    private val height = 842.0
    private val black = 0xFF000000.toInt()

    private fun pen() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(20.0, 20.0, 2.0), StrokePoint(120.0, 40.0, 2.0)),
        uniformWidth = true,
    )

    private fun highlighter() = Stroke(
        tool = Tool.HIGHLIGHTER,
        color = 0x7FFFFF00.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(45.0, 106.0, 16.0), StrokePoint(95.0, 106.0, 16.0)),
        uniformWidth = true,
    )

    private fun note() = TextElement("Sans", 12.0, 300.0, 300.0, black, "Remember this")

    private fun page(vararg elements: com.mobixournal.format.model.Element) = Page(
        width = width,
        height = height,
        background = Background.Pdf("notes.pdf", 0, "absolute"),
        layers = listOf(Layer(elements.toList())),
    )

    @Test
    fun aHighlighterOverTextBecomesAHighlightWithQuadPoints() {
        val words = PdfTextIndex(listOf(listOf(PdfWord("Magnetic", 50.0, 100.0, 110.0, 112.0))))
        val pdPage = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
        val transform = PdfPageTransform(0f, 0f, height.toFloat())

        PdfAnnotations.add(pdPage, page(highlighter()), 0, words, transform, null)

        val annotation = pdPage.annotations.single() as com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
        assertEquals("Highlight", annotation.subtype)
        val quads = annotation.quadPoints
        assertEquals("four corners", 8, quads.size)
        // Top-left of the word is visual (49, 98.5); flipped into PDF user space.
        assertEquals(49f, quads[0], 0.01f)
        assertEquals(height.toFloat() - 98.5f, quads[1], 0.01f)
        assertTrue("the quad spans the word", quads[2] > quads[0])
    }

    @Test
    fun handwritingBecomesAnInkAnnotationAndANoteBecomesText() {
        val pdPage = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
        val transform = PdfPageTransform(0f, 0f, height.toFloat())

        PdfAnnotations.add(pdPage, page(pen(), note()), 0, null, transform, null)

        val subtypes = pdPage.annotations.map { it.subtype }
        assertEquals(listOf("Ink", "Text"), subtypes)
        val ink = pdPage.annotations.first() as com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
        assertEquals("the stroke's two points are one ink path", 4, ink.inkList.single().size)
        assertEquals("Remember this", pdPage.annotations.last().contents)
    }

    /**
     * The file's structure: one page per document page, at the page's own size, carrying the three
     * annotation dictionaries. The flattened picture itself is a device bitmap and is not asserted
     * here — the injectable rasteriser is what keeps this suite on the JVM (`PdfExporter`).
     */
    @Test
    fun theExportedDocumentHasAPagePerPageWithItsAnnotations() {
        val document = Document(pages = listOf(page(pen(), note(), highlighter())))
        val out = ByteArrayOutputStream()
        PdfExporter(null, null, null) { null }.export(document, out)

        PDDocument.load(out.toByteArray()).use { exported ->
            assertEquals("one page per document page", 1, exported.numberOfPages)
            val exportedPage = exported.getPage(0)
            assertEquals(width.toFloat(), exportedPage.mediaBox.width, 0.01f)
            assertEquals(height.toFloat(), exportedPage.mediaBox.height, 0.01f)
            assertEquals(3, exportedPage.annotations.size)
        }
    }
}

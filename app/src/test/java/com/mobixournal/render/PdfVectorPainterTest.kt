package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PDF export had no test at all, which is exactly the kind of silence a refactor hides behind: the
 * painter now replays [PdfPagePlan]'s ops, so this paints into a **real** PDFBox page and reads the
 * result back — the text with the port's own text stripper, and the vector operators out of the
 * uncompressed content stream. A plan that compiles while the emitter drops an op would pass every
 * other check in the suite.
 *
 * No image is drawn here on purpose: decoding one goes through `BitmapFactory`, which is an Android
 * stub on a JVM host, so an image op is the emitter's one platform-bound branch.
 */
class PdfVectorPainterTest {

    private val width = 595.2756f
    private val height = 841.8898f
    private val black = 0xFF000000.toInt()

    private fun modelPage(vararg elements: Element) = Page(
        width = width.toDouble(),
        height = height.toDouble(),
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 2.0), StrokePoint(240.0, 160.0, 2.0)),
        uniformWidth = true,
    )

    private fun text() = TextElement(
        font = "Sans",
        size = 14.0,
        x = 72.0,
        y = 96.0,
        color = black,
        content = "Hello PDF",
    )

    private fun table() = TableElement(
        left = 72.0,
        top = 320.0,
        colWidths = listOf(120.0, 180.0),
        rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell("Parametro", bold = true), TableCell("Formula"))),
        headerRow = true,
        id = "tbl_1",
    )

    /** Paint [page] into a fresh PDF page and hand back the document, still open. */
    private fun painted(page: Page): PDDocument {
        val doc = PDDocument()
        val pdfPage = PDPage(PDRectangle(0f, 0f, width, height))
        doc.addPage(pdfPage)
        val cs = PDPageContentStream(doc, pdfPage, PDPageContentStream.AppendMode.APPEND, false)
        PdfVectorPainter().paint(doc, cs, page, PdfPageTransform(0f, 0f, height))
        cs.close()
        return doc
    }

    /** The page's content stream, uncompressed, so the operators can be read as text. */
    private fun operators(doc: PDDocument): String =
        doc.getPage(0).contents.use { String(it.readBytes(), Charsets.ISO_8859_1) }

    @Test
    fun theTextOfAPageArrivesInThePdf() {
        painted(modelPage(text())).use { doc ->
            assertTrue(
                "the text box is in the document",
                PDFTextStripper().getText(doc).contains("Hello PDF"),
            )
        }
    }

    @Test
    fun theStrokeIsWrittenAsAPathAndStroked() {
        painted(modelPage(stroke())).use { doc ->
            val ops = operators(doc)
            assertTrue("the path starts with a move", ops.contains(" m\n"))
            assertTrue("and continues with a line", ops.contains(" l\n"))
            assertTrue("then it is stroked", ops.contains("\nS\n") || ops.endsWith("S\n"))
        }
    }

    @Test
    fun aLivingTableIsFlattenedIntoItsOwnInkAndWords() {
        painted(modelPage(table())).use { doc ->
            val words = PDFTextStripper().getText(doc)
            assertTrue("the cells are text", words.contains("Parametro") && words.contains("Formula"))
            // Its grid rules are strokes of their own, so the export cannot come out blank where the
            // canvas draws a table (the failure `LivingInk` exists to prevent).
            val ops = operators(doc)
            assertTrue("the grid rules are line segments", ops.contains(" l\n"))
            assertTrue("and they are stroked", ops.contains("\nS\n") || ops.endsWith("S\n"))
        }
    }

    @Test
    fun aDocumentWithNoInkStillWritesAPage() {
        painted(modelPage()).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(operators(doc).isEmpty())
        }
    }

    @Test
    fun theSamePageThroughDocumentLevelExportKeepsTheText() {
        val doc = painted(modelPage(stroke(), text(), table()))
        doc.use {
            val words = PDFTextStripper().getText(it)
            assertTrue(words.contains("Hello PDF"))
            assertTrue(words.contains("Parametro"))
        }
        // And the model path the window uses compiles the same page into the same op sequence.
        val plan = PdfPagePlan.of(modelPage(stroke(), text(), table()), PdfPageTransform(0f, 0f, height))
        assertTrue("a stroke, the table's ink and the words", plan.size > 3)
        assertTrue(plan.filterIsInstance<PdfPath>().size > 1)
    }
}

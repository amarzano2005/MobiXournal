package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.mobix.PageSource
import java.awt.image.BufferedImage
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A page written over an **imported PDF** exports the source page's own **vectors**, as the tablet does.
 *
 * This is the difference that matters for a real annotation workflow: flatten the source to a picture and
 * every export grows by an order of magnitude and loses the PDF's text layer. The claim is therefore
 * checked on the text: a `PDFTextStripper` reading the *exported* file still finds the source's words,
 * which no raster flatten could produce. The rotation case is the second half of the same promise — the
 * overlay must land where it was drawn on a `/Rotate 90` page, not a quarter-turn away.
 */
class DesktopPdfExportVectorsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val black = 0xFF000000.toInt()

    private fun text(content: String, y: Double = 100.0) = TextElement(
        font = "Sans",
        size = 18.0,
        x = 72.0,
        y = y,
        color = black,
        content = content,
    )

    /** A horizontal mark near the **top** of the page it is drawn on, wide enough to find by pixel. */
    private fun topMark(height: Double) = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(
            StrokePoint(100.0, 8.0, 6.0),
            StrokePoint(300.0, 8.0, 6.0),
        ),
        uniformWidth = false,
    )

    private fun page(width: Double, height: Double, background: Background, vararg elements: com.mobixournal.format.model.Element) = Page(
        width = width,
        height = height,
        background = background,
        layers = listOf(Layer(elements.toList())),
    )

    private fun pdfBackground(file: File, pageNo: Int = 0) = Background.Pdf(
        filename = file.absolutePath,
        pageNo = pageNo,
        domain = "absolute",
    )

    private fun document(page: Page): DesktopDocument {
        val model = Document(pages = listOf(page))
        return DesktopDocument(
            file = File(temp.root, "annotated.xopp"),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
    }

    private fun export(document: DesktopDocument): PDDocument {
        val target = File(temp.root, "annotated.pdf")
        DesktopPdfExport.write(document, target, document.backdrop)
        return PDDocument.load(target)
    }

    /** A one-page source PDF carrying real text, the annotation target. */
    private fun sourceWithText(): File {
        val file = File(temp.root, "source.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(595f, 842f))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 24f)
                cs.newLineAtOffset(72f, 760f)
                cs.showText("Source words")
                cs.endText()
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun anImportedPagesTextIsStillExtractableFromTheExport() {
        val source = sourceWithText()
        val page = page(595.0, 842.0, pdfBackground(source), text("Annotated here"))

        export(document(page)).use { pdf ->
            assertEquals("the page count is the document's", 1, pdf.numberOfPages)
            assertEquals("and the sheet's own size", 595.0, pdf.getPage(0).mediaBox.width.toDouble(), 0.5)
            val words = PDFTextStripper().getText(pdf)
            assertTrue(
                "the source page's vectors survived, so its text is still text: $words",
                words.contains("Source words"),
            )
            assertTrue("with the annotation written over it", words.contains("Annotated here"))
        }
    }

    /**
     * `/Rotate 90`: the page a reader sees is landscape, and ink drawn at the top of the *visual* page must
     * come out at the top. The overlay matrix is what makes that true, and a page drawn a quarter-turn
     * away is exactly what an exporter without it produces.
     */
    @Test
    fun inkOnARotatedSourcePageLandsWhereItWasDrawn() {
        val source = File(temp.root, "rotated.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(595f, 842f))
            page.rotation = 90
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 24f)
                cs.newLineAtOffset(72f, 760f)
                cs.showText("Sideways source")
                cs.endText()
            }
            doc.save(source)
        }
        // The imported page is the page as seen: 842 wide and 595 high.
        val page = page(842.0, 595.0, pdfBackground(source), topMark(height = 595.0))

        export(document(page)).use { pdf ->
            val rendered = PDFRenderer(pdf).renderImageWithDPI(0, 72f)
            assertEquals("the visual page is landscape", 842, rendered.width)
            assertTrue("the mark is at the top: ${rendered.rgbAt(200, 10).toString(16)}", isInk(rendered.rgbAt(200, 10)))
            assertFalse(
                "and not a quarter-turn away at the bottom: ${rendered.rgbAt(200, 580).toString(16)}",
                isInk(rendered.rgbAt(200, 580)),
            )
        }
    }

    /**
     * A **scanned** source (its page is an image XObject, not just text) still exports.
     *
     * The imported page is imported *by reference*: its content and its resources are the source
     * document's own streams, so a source closed before the target is written leaves the exporter with
     * nothing to read and PDFBox fails the save ("COSStream has been closed"). A source of text alone
     * cannot show this — a base-14 font is a dictionary, not a stream — which is why the picture is the
     * fixture here rather than a second paragraph.
     */
    @Test
    fun aScannedSourcePageExportsAfterItsDocumentWasClosed() {
        val source = File(temp.root, "scan.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(595f, 842f))
            doc.addPage(page)
            val scan = BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB).also { image ->
                for (y in 0 until 60) for (x in 0 until 60) image.setRGB(x, y, 0x3366CC)
            }
            val xobject = LosslessFactory.createFromImage(doc, scan)
            PDPageContentStream(doc, page).use { cs -> cs.drawImage(xobject, 72f, 500f, 200f, 200f) }
            doc.save(source)
        }
        val page = page(595.0, 842.0, pdfBackground(source), text("Annotated here"))

        export(document(page)).use { pdf ->
            assertEquals("the document still exports its page", 1, pdf.numberOfPages)
            // Reading the source's picture back proves the stream survived the export, not just the save.
            val rendered = PDFRenderer(pdf).renderImageWithDPI(0, 72f)
            assertEquals("the scanned picture is in the export", 0x3366CC, rendered.rgbAt(150, 250))
            assertTrue("and the annotation over it", PDFTextStripper().getText(pdf).contains("Annotated here"))
        }
    }

    /** A source this window cannot open is a page, not an error: the fresh sheet carries the ink instead. */
    @Test
    fun aMissingSourcePageFallsBackToTheSheetAndKeepsTheInk() {
        val page = page(
            595.0,
            842.0,
            Background.Pdf(filename = "/nowhere/gone.pdf", pageNo = 0, domain = "absolute"),
            text("Fallback words"),
        )

        export(document(page)).use { pdf ->
            assertEquals("the document still exports its page", 1, pdf.numberOfPages)
            val words = PDFTextStripper().getText(pdf)
            assertTrue("with the ink that was drawn on it: $words", words.contains("Fallback words"))
        }
    }

    private fun BufferedImage.rgbAt(x: Int, y: Int): Int = getRGB(x, y) and 0xFFFFFF

    /** A pixel dark enough to be a black stroke drawn over paper. */
    private fun isInk(rgb: Int): Boolean = (rgb shr 16 and 0xFF) < 128
}

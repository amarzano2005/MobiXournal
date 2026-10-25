package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.render.PdfPagePlan
import com.mobixournal.render.PdfPageTransform
import com.mobixournal.render.PdfPath
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's PDF emitter, checked in a **real** PDF document rather than against a mock: it replays
 * `PdfPagePlan`'s ops through Apache PDFBox, so the test paints into a page, saves nothing, and reads the
 * result back — the words with PDFBox's own text stripper, the vector operators out of the uncompressed
 * content stream, and the pictures out of the page's resources.
 *
 * The four claims are the ones that would fail silently otherwise: the text of a page arrives, a stroke
 * is written as a path and stroked in **the plan's** colour, a pressured stroke is one stroke per segment
 * (the taper), and an image op is decoded and drawn rather than dropped. A fifth covers the emitter's
 * stated policy — one unreadable op is skipped, it does not take the page — with bytes that are not a
 * picture at all.
 */
class DesktopPdfPainterTest {

    private val width = 595.2756f
    private val height = 841.8898f

    /** A colour no fill or default would happen to produce, so the assertion means something. */
    private val ink = 0xFF112233.toInt()

    private fun modelPage(vararg elements: Element) = Page(
        width = width.toDouble(),
        height = height.toDouble(),
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = ink,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 2.0), StrokePoint(240.0, 160.0, 2.0)),
        uniformWidth = true,
    )

    /** Three samples of three different widths: the plan gives one width per segment, so two strokes. */
    private fun tapered() = Stroke(
        tool = Tool.PEN,
        color = ink,
        capStyle = "round",
        points = listOf(
            StrokePoint(20.0, 20.0, 1.0),
            StrokePoint(120.0, 90.0, 2.5),
            StrokePoint(220.0, 40.0, 4.0),
        ),
        uniformWidth = false,
    )

    private fun text() = TextElement(
        font = "Sans",
        size = 14.0,
        x = 72.0,
        y = 96.0,
        color = 0xFF000000.toInt(),
        content = "Hello PDF",
    )

    /** Four opaque pixels as a PNG — the bytes an image op carries, as a document carries them. */
    private fun png(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color(0x11, 0x22, 0x33)
        graphics.fillRect(0, 0, 4, 4)
        graphics.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun image() = ImageElement(left = 72.0, top = 72.0, right = 172.0, bottom = 172.0, data = png())

    /** Paint [page] into a fresh PDF page and hand back the document, still open. */
    private fun painted(page: Page): PDDocument {
        val doc = PDDocument()
        val pdfPage = PDPage(PDRectangle(0f, 0f, width, height))
        doc.addPage(pdfPage)
        val cs = PDPageContentStream(doc, pdfPage, PDPageContentStream.AppendMode.APPEND, false)
        DesktopPdfPainter().paint(doc, cs, page, PdfPageTransform(0f, 0f, height))
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
                "the text box is in the document, in the words it carries",
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

    /**
     * The colour is not a literal in this test: it is read back out of the plan the emitter consumed, so
     * the two shells provably paint the same shade. PDFBox writes components as 0..1 decimals, which is
     * why the assertion compares numbers with a tolerance instead of matching a printed string.
     */
    @Test
    fun theStrokeCarriesThePlansColourAndATaperIsOneStrokePerSegment() {
        val page = modelPage(tapered())
        val expected = PdfPagePlan.of(page, PdfPageTransform(0f, 0f, height))
            .filterIsInstance<PdfPath>()
            .first()
            .colour
        painted(page).use { doc ->
            val ops = operators(doc)
            val written = Regex("([0-9.]+) ([0-9.]+) ([0-9.]+) RG").find(ops)
                ?: error("the stroke states no stroking colour")
            assertEquals(
                "red channel, from the plan's own colour",
                ((expected shr 16) and 0xFF) / 255f,
                written.groupValues[1].toFloat(),
                1e-3f,
            )
            assertEquals(
                "green channel",
                ((expected shr 8) and 0xFF) / 255f,
                written.groupValues[2].toFloat(),
                1e-3f,
            )
            assertEquals(
                "blue channel",
                (expected and 0xFF) / 255f,
                written.groupValues[3].toFloat(),
                1e-3f,
            )
            assertEquals(
                "a three-sample taper is two segments, each stroked on its own",
                2,
                Regex("(?m)^S$").findAll(ops).count(),
            )
        }
    }

    @Test
    fun aPictureIsDecodedAndDrawnAsAnXObject() {
        painted(modelPage(image())).use { doc ->
            val ops = operators(doc)
            assertTrue(
                "the picture is invoked on the page",
                Regex("\\bDo\\b").containsMatchIn(ops),
            )
            assertTrue(
                "and it is a resource of the page rather than a flattened fill",
                doc.getPage(0).resources.xObjectNames.toList().isNotEmpty(),
            )
        }
    }

    /**
     * The emitter's policy, which the app's painter states and this one copies: an op that throws is
     * skipped, because half a page is better than none and the export reports its losses elsewhere.
     */
    @Test
    fun anUnreadablePictureIsSkippedAndTheRestOfThePageSurvives() {
        val broken = ImageElement(left = 10.0, top = 10.0, right = 60.0, bottom = 60.0, data = byteArrayOf(1, 2, 3))
        painted(modelPage(broken, text())).use { doc ->
            assertTrue(
                "the words after the bad op are still there",
                PDFTextStripper().getText(doc).contains("Hello PDF"),
            )
        }
    }
}

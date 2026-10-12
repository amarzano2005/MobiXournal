package com.mobixournal.render

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.isLivingEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export as the app reaches it: `DocumentIo.encode` runs [XoppSynthesis] before it hands a document
 * to the `.xopp` writer, for every `.xopp` it writes, so the writer can never see a recipe it has nowhere
 * to keep. These are the assertions behind that: a document holding living entities comes out as a real
 * `.xopp` a reader opens, a document that is already ink goes through **byte for byte** (the synthesis is
 * the identity on it), and what this build cannot draw is reported rather than dropped in silence.
 *
 * The composition tested here — synthesis, then `Xopp.save`, then `Xopp.open` — is the pure part of
 * `DocumentIo.encode`; the staging and the picker around it are exercised on the emulator.
 */
class XoppExportTest {

    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()

    private fun inkStroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(40.0, 60.0, 1.0)),
        uniformWidth = true,
        id = "stk_ink",
    )

    private fun table() = TableElement(
        left = 72.0, top = 120.0,
        colWidths = listOf(120.0, 180.0),
        rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell("Parametro", bold = true), TableCell("Formula"))),
        headerRow = true,
        id = "tbl_1",
    )

    private fun plot(vararg formulas: String = arrayOf("sin(x)")) = PlotElement(
        left = 100.0, top = 200.0, right = 300.0, bottom = 320.0,
        curves = formulas.map { PlotCurve(PlotKind.CARTESIAN, it, red, strokeWidth = 1.5) },
        xMin = -6.28, xMax = 6.28, samples = 60,
        id = "plt_1",
    )

    private fun circuit(symbol: String = "RESISTOR") = CircuitElement(
        symbol = symbol,
        positionX = 300.0, positionY = 150.0,
        strokeColor = red, strokeWidth = 1.5,
        value = "10 kΩ",
        pins = listOf(CircuitPin("p1", relX = -24.0), CircuitPin("p2", relX = 24.0)),
        id = "ckt_1",
    )

    private fun documentWith(vararg elements: Element) = Document(
        pages = listOf(
            Page(
                width = 595.27559100,
                height = 841.88976400,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(elements.toList(), name = "ink")),
            ),
        ),
    )

    /** The bytes the `.xopp` writer produces for [document] — what `DocumentIo.encode` stages. */
    private fun bytesOf(document: Document): ByteArray =
        ByteArrayOutputStream().also { Xopp.save(document, it) }.toByteArray()

    /** Those bytes as a reader gets them back. */
    private fun reopened(bytes: ByteArray): Document = ByteArrayInputStream(bytes).use { Xopp.open(it) }

    @Test
    fun aDocumentHoldingLivingEntitiesExportsAsInkAReaderCanOpen() {
        val document = documentWith(inkStroke(), table(), plot(), circuit())

        val export = XoppSynthesis.synthesize(document)
        assertTrue("this document has a drawing for everything in it", export.uncompiled.isEmpty())

        val read = reopened(bytesOf(export.document))
        assertEquals("the page survived", 1, read.pages.size)
        assertEquals("its layer did too", "ink", read.pages[0].layers[0].name)

        val elements = read.pages[0].layers[0].elements
        assertFalse("no recipe is left in the file", elements.any { it.isLivingEntity })
        assertEquals(
            "every element of the export is in the file, none dropped and none added",
            export.document.pages[0].layers[0].elements.size,
            elements.size,
        )
        assertTrue("the entities became more than one element each", elements.size > 4)
        // `.xopp` has no id attribute, so the stroke comes back without its id — what must survive is the
        // stroke itself, drawn by the same tool.
        assertTrue("and the ink stroke is still a pen stroke", elements.any { it is Stroke && it.tool == Tool.PEN })
        assertTrue("the block that can revive them rode along", read.bridge != null)
    }

    @Test
    fun anAlreadyInkDocumentIsWrittenByteForByteTheSame() {
        val document = documentWith(inkStroke())

        // The synthesis is the identity on a document with nothing to cook — including on the bridge block
        // it carries — so a plain `.xopp` save must not change by a single byte for having gone through it.
        val synthesized = XoppSynthesis.synthesize(document)
        assertTrue("nothing to report", synthesized.uncompiled.isEmpty())
        assertArrayEquals("the same bytes as the ordinary .xopp path", bytesOf(document), bytesOf(synthesized.document))
    }

    @Test
    fun anUnknownSymbolIsReportedAndTheRestOfTheExportStillOpens() {
        val document = documentWith(inkStroke(), circuit(symbol = "FLUX_CAPACITOR"))

        val export = XoppSynthesis.synthesize(document)

        assertEquals("one loss, stated once", 1, export.uncompiled.size)
        assertEquals("circuit", export.uncompiled[0].type)
        assertTrue(
            "and named in the terms the user wrote it in",
            export.uncompiled[0].reason.contains("FLUX_CAPACITOR"),
        )

        // The export is still a document: the stroke is there, the un-drawable component is not, and the
        // file opens — a reported loss is not a failed export.
        val read = reopened(bytesOf(export.document))
        val elements = read.pages[0].layers[0].elements
        assertEquals("only the ink that could be drawn", 1, elements.size)
        assertTrue(elements[0] is Stroke)
    }

    @Test
    fun aCurveThatWillNotEvaluateIsReportedWhileTheRestOfThePlotIsDrawn() {
        // One curve this build can evaluate and one it cannot: the plot is drawn, the broken curve is the
        // only loss, and the export is still a document.
        val document = documentWith(plot("sin(x)", "cos("))

        val export = XoppSynthesis.synthesize(document)

        assertEquals("the formula is the loss", 1, export.uncompiled.size)
        assertEquals("plotCurve", export.uncompiled[0].type)
        assertTrue(
            "and the plot is still drawn around it",
            export.document.pages[0].layers[0].elements.isNotEmpty(),
        )
        val read = reopened(bytesOf(export.document))
        assertEquals("so the export opens as a document", 1, read.pages.size)
        assertTrue("with the curve that could be drawn in it", read.pages[0].layers[0].elements.isNotEmpty())
    }
}

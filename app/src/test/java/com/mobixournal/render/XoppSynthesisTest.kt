package com.mobixournal.render

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The synthesis export (`docs/mobix-format.md`): a document holding living entities turned into ink
 * the `.xopp` writer can write in full.
 *
 * Two claims are being tested, and the second matters as much as the first:
 *
 *  1. **Each family becomes the drawing it stands for** — a table's grid and its cells' text, a
 *     plot's axes and its curve, a shape's closed outline with its fill and its labels, a component's
 *     symbol in the user's own ink, placed by its own pins — asserted on the geometry, not just on
 *     "some strokes came out".
 *  2. **What cannot be drawn is said, not invented.** An unknown symbol, a formula that does not
 *     parse, an element preserved from a newer build: each is reported
 *     ([SynthesisResult.uncompiled]) rather than substituted with a plausible-looking figure.
 */
class XoppSynthesisTest {

    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()

    private fun documentWith(vararg elements: Element) = Document(
        pages = listOf(
            Page(
                width = 595.27559100,
                height = 841.88976400,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(elements.toList(), name = "ink")),
                id = "pg_1",
            ),
        ),
    )

    private fun synthesize(vararg elements: Element): SynthesisResult =
        XoppSynthesis.synthesize(documentWith(*elements))

    private fun inkOf(result: SynthesisResult): List<Element> = result.document.pages[0].layers[0].elements

    private fun strokesOf(result: SynthesisResult): List<Stroke> = inkOf(result).filterIsInstance<Stroke>()

    private fun textsOf(result: SynthesisResult): List<TextElement> = inkOf(result).filterIsInstance<TextElement>()

    private fun table() = TableElement(
        left = 72.0,
        top = 120.0,
        colWidths = listOf(120.0, 180.0, 100.0),
        rowHeights = listOf(28.0, 24.0),
        cells = listOf(
            listOf(
                TableCell("Parametro", TableAlign.CENTER, bold = true, fillColor = 0x15000000),
                TableCell("Formula", TableAlign.CENTER, bold = true, fillColor = 0x15000000),
                TableCell("Valore", TableAlign.CENTER, bold = true, fillColor = 0x15000000),
            ),
            listOf(
                TableCell("Frequenza", TableAlign.LEFT),
                TableCell("f = 1 / T", TableAlign.CENTER),
                TableCell("50 Hz", TableAlign.RIGHT),
            ),
        ),
        headerRow = true,
        borderColor = 0xFF212121.toInt(),
        borderWidth = 1.0,
        id = "tbl_1",
    )

    private fun cartesianPlot() = PlotElement(
        left = 100.0, top = 200.0, right = 400.0, bottom = 380.0,
        curves = listOf(PlotCurve(PlotKind.CARTESIAN, "sin(x)", red, strokeWidth = 1.5)),
        xMin = -6.28, xMax = 6.28, samples = 120,
        axisColor = 0xFF37474F.toInt(),
        id = "plt_1",
    )

    private fun polarPlot() = PlotElement(
        left = 100.0, top = 200.0, right = 300.0, bottom = 400.0,
        curves = listOf(
            PlotCurve(PlotKind.POLAR, "2 * cos(4 * theta)", red, thetaMin = 0.0, thetaMax = 6.283185307179586),
        ),
        samples = 180,
        id = "plt_2",
    )

    private fun shape() = ParametricShapeElement(
        shape = "TRIANGLE_SCALENE",
        vertices = listOf(ShapeVertex(100.0, 100.0), ShapeVertex(250.0, 120.0), ShapeVertex(160.0, 240.0)),
        strokeColor = 0xFF0D47A1.toInt(),
        strokeWidth = 2.0,
        fillColor = 0x1A2196F3,
        showAngles = true,
        showSideLengths = true,
        id = "shp_1",
    )

    private fun circuit(rotation: Double = 0.0, symbol: String = "RESISTOR") = CircuitElement(
        symbol = symbol,
        positionX = 300.0,
        positionY = 150.0,
        rotation = rotation,
        strokeColor = 0xFFD84315.toInt(),
        strokeWidth = 1.5,
        value = "10 kΩ",
        pins = listOf(
            CircuitPin("p1", relX = -24.0, relY = 0.0, connectedTo = "net_A"),
            CircuitPin("p2", relX = 24.0, relY = 0.0),
        ),
        id = "ckt_1",
    )

    // ---- one family at a time ---------------------------------------------------------------------

    @Test
    fun aTableExportsAsItsGridAndItsCellsText() {
        val result = synthesize(table())

        // Outer border, two column dividers, one row divider, one header double rule, and the three shaded header cells.
        assertEquals("four sides, two column rules, one row rule, one header double rule, three shaded cells", 11, strokesOf(result).size)
        assertTrue(
            "the eight rules are the table's own ink",
            strokesOf(result).take(8).all { it.color == 0xFF212121.toInt() },
        )
        assertTrue(
            "and the three shaded cells carry their shading",
            strokesOf(result).drop(8).all { it.color == 0x15000000 },
        )
        assertTrue(strokesOf(result).all { it.uniformWidth && it.points.size >= 2 })

        val border = strokesOf(result).first()
        assertEquals(72.0, border.points.first().x, 1e-9)
        assertEquals(120.0, border.points.first().y, 1e-9)

        // Six cells, all of them filled in, so six labels — and each sits inside its own cell.
        assertEquals(6, textsOf(result).size)
        val cells = textsOf(result).all { it.x >= 72.0 && it.x < 472.0 && it.y >= 120.0 && it.y < 172.0 }
        assertTrue("a label outside the table would be a stray mark", cells)
    }

    @Test
    fun aFilledCellIsDrawnAsAClosedRectangleCarryingTheFillAlpha() {
        val result = synthesize(table())

        val fills = strokesOf(result).filter { it.fill != null }
        assertEquals("the three header cells are shaded", 3, fills.size)
        assertTrue(fills.all { it.fill == 0x15 })
        assertTrue("and a shaded cell is a closed figure", fills.all { it.points.first() == it.points.last() })
    }

    @Test
    fun aBoldCellIsLabelledInABoldFont() {
        val result = synthesize(table())

        val bold = textsOf(result).filter { it.content == "Parametro" }.single()
        val plain = textsOf(result).filter { it.content == "50 Hz" }.single()
        assertTrue("a header cell keeps its emphasis: ${bold.font}", bold.font.contains("Bold"))
        assertFalse("and an ordinary cell is not bold: ${plain.font}", plain.font.contains("Bold"))
    }

    @Test
    fun aCartesianPlotExportsAsItsAxesItsCurveAndItsTickLabels() {
        val result = synthesize(cartesianPlot())
        val strokes = strokesOf(result)

        val axisColor = 0xFF37474F.toInt()
        assertTrue("the frame and axes are there", strokes.any { it.color == axisColor })
        val curve = strokes.filter { it.color == red }
        assertTrue("the curve is there", curve.isNotEmpty())
        assertEquals("at the width the element gave its curve", 1.5, curve.first().points.first().width, 1e-9)

        val outside = strokes.flatMap { it.points }.filter {
            it.x < 99.0 || it.x > 401.0 || it.y < 199.0 || it.y > 381.0
        }
        assertTrue("nothing may be drawn outside the plot's own box: $outside", outside.isEmpty())
        assertTrue("the tick numbers are text boxes", textsOf(result).isNotEmpty())
    }

    @Test
    fun aPlotIsDrawnTheSameWhetherItsParameterIsWrittenThetaOrTheParserVariable() {
        // The app enters a polar radius as `2 * cos(4 * x)`; the format's own notation is `r(θ)`, so a
        // file from another implementation says `theta`. Both are the same curve.
        val asTheta = polarPlot().copy(curves = listOf(PlotCurve(PlotKind.POLAR, "2 * cos(4 * theta)", red, thetaMin = 0.0, thetaMax = 6.283185307179586)))
        val asVariable = polarPlot().copy(curves = listOf(PlotCurve(PlotKind.POLAR, "2 * cos(4 * x)", red, thetaMin = 0.0, thetaMax = 6.283185307179586)))

        val theta = synthesize(asTheta)
        val variable = synthesize(asVariable)

        assertTrue("a theta formula is not thrown away", theta.uncompiled.isEmpty())
        assertEquals(
            "and it draws the very same curve",
            strokesOf(variable).map { it.points },
            strokesOf(theta).map { it.points },
        )
    }

    @Test
    fun aCurveThatDoesNotParseCostsOnlyItself() {
        val plot = cartesianPlot().copy(
            curves = listOf(
                PlotCurve(PlotKind.CARTESIAN, "sin(x)", red, strokeWidth = 1.5),
                PlotCurve(PlotKind.CARTESIAN, "sin((", 0xFF388E3C.toInt()),
            ),
        )

        val result = synthesize(plot)

        assertEquals("the broken curve alone is reported", "plotCurve", result.uncompiled.single().type)
        assertTrue(result.uncompiled.single().reason.contains("sin(("))
        assertTrue("while the one that parses is drawn", strokesOf(result).any { it.color == red })
        assertTrue("and the broken one is not drawn black-boxed", strokesOf(result).none { it.color == 0xFF388E3C.toInt() })
    }

    @Test
    fun aPolarPlotIsSampledAndFramed() {
        val result = synthesize(polarPlot())
        val strokes = strokesOf(result)

        assertEquals("the frame plus the curve", 2, strokes.size)
        val curve = strokes.first { it.color == red }
        assertTrue("a rose curve is not a straight line", curve.points.size > 20)

        val xs = curve.points.map { it.x }
        val ys = curve.points.map { it.y }
        assertTrue("and it is placed inside the box", xs.min() >= 99.0 && xs.max() <= 301.0)
        assertTrue("in both axes", ys.min() >= 199.0 && ys.max() <= 401.0)
        assertTrue("with real extent", (xs.max() - xs.min()) > 20.0 && (ys.max() - ys.min()) > 20.0)
    }

    @Test
    fun aShapeExportsAsItsClosedOutlineWithItsFillAndItsLabels() {
        val result = synthesize(shape())
        val strokes = strokesOf(result)

        // The fill's colour differs from the outline's, so it is its own stroke underneath.
        assertEquals(2, strokes.size)
        val fill = strokes.first { it.fill != null }
        assertEquals(0x1A2196F3, fill.color)
        assertEquals(0x1A, fill.fill)
        val outline = strokes.first { it.fill == null }
        assertEquals(0xFF0D47A1.toInt(), outline.color)
        assertEquals("a figure is closed", outline.points.first(), outline.points.last())
        assertEquals("through the element's own vertices", 100.0, outline.points.first().x, 1e-9)
        assertEquals(2.0, outline.points.first().width, 1e-9)

        // Three sides and three angles were asked for.
        val labels = textsOf(result)
        assertTrue("side lengths are written as measurements: ${labels.map { it.content }}", labels.any { it.content.endsWith("mm") })
        assertTrue("angles in degrees: ${labels.map { it.content }}", labels.count { it.content.endsWith("°") } == 3)
    }

    @Test
    fun aCircuitExportsInItsOwnInkPlacedByItsPins() {
        val result = synthesize(circuit())
        val strokes = strokesOf(result)

        assertEquals(1, strokes.size)
        assertEquals(0xFFD84315.toInt(), strokes[0].color)
        assertEquals(1.5, strokes[0].points.first().width, 1e-9)
        val xs = strokes[0].points.map { it.x }
        val ys = strokes[0].points.map { it.y }
        assertEquals("the symbol is drawn between its own pins", 276.0, xs.min(), 1e-6)
        assertEquals(324.0, xs.max(), 1e-6)
        assertTrue("and along their axis", ys.all { kotlin.math.abs(it - 150.0) < 12.0 })

        assertEquals("the value is written with it", "10 kΩ", textsOf(result).single().content)
    }

    @Test
    fun aRotatedComponentTurnsWithItsPins() {
        val upright = synthesize(circuit())
        val turned = synthesize(circuit(rotation = 90.0))

        val flat = strokesOf(upright)[0].points
        val vertical = strokesOf(turned)[0].points

        assertTrue("a 90° turn makes the axis vertical", vertical.map { it.y }.max() - vertical.map { it.y }.min() > 40.0)
        assertTrue("and the flat one is horizontal", flat.map { it.x }.max() - flat.map { it.x }.min() > 40.0)
        val centred = vertical.all { kotlin.math.abs(it.x - 300.0) < 12.0 }
        assertTrue("while still anchored at the component's position", centred)
    }

    @Test
    fun aMultiPartSymbolIsWrittenAsSeparateStrokesSoNothingBridgesItsGap() {
        // A capacitor's plates are two disconnected parts: one polyline would draw a bar across the
        // dielectric, which is not a capacitor any more.
        val result = synthesize(
            CircuitElement(
                symbol = "CAPACITOR",
                positionX = 200.0, positionY = 200.0,
                strokeColor = black, strokeWidth = 1.0,
                pins = listOf(CircuitPin("p1", relX = -40.0), CircuitPin("p2", relX = 40.0)),
            ),
        )

        assertEquals("the two plates are two strokes", 2, strokesOf(result).size)
        assertTrue(strokesOf(result).all { it.points.size >= 2 })
    }

    // ---- what cannot be drawn is said out loud -----------------------------------------------------

    @Test
    fun aSymbolThisBuildHasNoDrawingForIsReportedRatherThanSubstituted() {
        val result = synthesize(circuit(symbol = "RESISTOR_IEEE"))

        assertEquals(1, result.uncompiled.size)
        assertEquals("circuit", result.uncompiled.single().type)
        assertTrue(result.uncompiled.single().reason.contains("RESISTOR_IEEE"))
        assertTrue("and no ink was invented for it", inkOf(result).isEmpty())
    }

    @Test
    fun aPlotWhoseFormulaDoesNotParseIsReported() {
        val broken = cartesianPlot().copy(
            curves = listOf(PlotCurve(PlotKind.CARTESIAN, "sin(", red)),
            id = "plt_broken",
        )

        val result = synthesize(broken)

        assertEquals("plotCurve", result.uncompiled.single().type)
        assertTrue(result.uncompiled.single().reason.contains("sin("))
        assertTrue(inkOf(result).isEmpty())
    }

    @Test
    fun anElementFromANewerBuildIsReportedWhileTheOneFromXoppRidesAlong() {
        val result = synthesize(
            RawElement(name = "3dPlotElement", body = """{"type":"3dPlotElement"}""", syntax = RawElement.Syntax.JSON),
            RawElement("vendorThing", attrs = linkedMapOf("width" to "12"), body = "<x/>"),
        )

        assertEquals("3dPlotElement", result.uncompiled.single().type)
        val kept = inkOf(result).single()
        assertEquals("and the one the .xopp writer can carry is left alone", "vendorThing", (kept as RawElement).name)
    }

    @Test
    fun anEmptyPlotFrameIsNeverExportedAsAnEmptyBox() {
        val empty = cartesianPlot().copy(curves = emptyList())

        val result = synthesize(empty)

        assertTrue(result.uncompiled.isNotEmpty())
        assertTrue(inkOf(result).isEmpty())
    }

    // ---- what the synthesis must not touch, and the normalisation check ----------------------------

    @Test
    fun nothingElseAboutTheDocumentChanges() {
        val stroke = Stroke(Tool.PEN, black, "round", listOf(StrokePoint(1.0, 2.0, 1.0), StrokePoint(3.0, 4.0, 1.0)), true, id = "stk_1")
        val text = TextElement("Sans", 12.0, 10.0, 20.0, black, "ciao", id = "txt_1")
        val image = ImageElement(0.0, 0.0, 10.0, 10.0, byteArrayOf(1, 2, 3), id = "img_1")
        val original = documentWith(stroke, table(), text, image)

        val result = XoppSynthesis.synthesize(original)

        val ink = inkOf(result)
        assertEquals("the ink that was already ink is untouched", stroke, ink.first())
        assertTrue("and its neighbours are still there", ink.contains(text) && ink.any { it == image })
        assertEquals("the layer keeps its name", "ink", result.document.pages[0].layers[0].name)
        assertEquals("the page keeps its size", original.pages[0].width, result.document.pages[0].width, 1e-9)
        assertEquals("the background is untouched", original.pages[0].background, result.document.pages[0].background)
        assertEquals("the title survives", original.title, result.document.title)
    }

    @Test
    fun theTableStandsWhereItStoodInTheLayer() {
        val before = Stroke(Tool.PEN, black, "round", listOf(StrokePoint(0.0, 0.0, 1.0), StrokePoint(1.0, 1.0, 1.0)), true)
        val after = Stroke(Tool.HIGHLIGHTER, black, "round", listOf(StrokePoint(5.0, 5.0, 8.0), StrokePoint(6.0, 6.0, 8.0)), true)

        val ink = inkOf(synthesize(before, table(), after))

        assertEquals("the first stroke is still first", before, ink.first())
        assertEquals("the last is still last", after, ink.last())
        // 8 grid rules + 3 shaded cells + 6 cell labels of the table's own ink.
        assertTrue("and the table's grid is in between: ${ink.size}", ink.size == 2 + 17)
    }

    @Test
    fun theExportedXoppReparsesToTheSameModelAndWritesStably() {
        val document = XoppSynthesis.synthesize(
            documentWith(table(), cartesianPlot(), shape(), circuit(), polarPlot()),
        ).document

        val xml = Xopp.toXml(document)
        val parsed = Xopp.parseXml(xml)

        assertTrue("no living entity survives as ink-less: they are all drawn now", parsed.pages[0].layers[0].elements.none { it is TableElement || it is PlotElement })
        assertEquals("the element count is the synthesized one", document.pages[0].layers[0].elements.size, parsed.pages[0].layers[0].elements.size)
        assertEquals("and writing the parse again is a no-op — the export is stable", xml, Xopp.toXml(parsed))
    }

    @Test
    fun reSynthesizingAnExportedDocumentChangesNothingTheWriterWouldNotice() {
        // The export's fixed point, in the spirit of XmlEqualityRoundTripTest: once the living entities
        // are ink, exporting again is a no-op — the bridge cannot reshape a document it has already
        // flattened, which is what makes exporting twice safe.
        val document = documentWith(table(), cartesianPlot(), shape(), circuit(), polarPlot())
        val exported = Xopp.parseXml(Xopp.toXml(XoppSynthesis.synthesize(document).document))

        val again = XoppSynthesis.synthesize(exported)

        assertTrue("nothing left to report: ${again.uncompiled}", again.uncompiled.isEmpty())
        assertEquals("and the re-export is the same document", Xopp.toXml(exported), Xopp.toXml(again.document))
    }

    @Test
    fun aDocumentWithNoLivingEntitiesExportsUnchanged() {
        val stroke = Stroke(Tool.PEN, red, "round", listOf(StrokePoint(0.0, 0.0, 1.0), StrokePoint(4.0, 4.0, 1.0)), true)
        val document = documentWith(stroke)

        val result = XoppSynthesis.synthesize(document)

        assertTrue(result.uncompiled.isEmpty())
        assertEquals(document, result.document)
        assertNotNull(Xopp.toXml(result.document))
    }

    @Test
    fun aStrokesTelemetryIsDroppedOnTheWayOutWhileItsInkIsNot() {
        // The one loss the format forces: `.xopp` has nowhere to put tilt, azimuth or a timestamp, so
        // the export writes the width the point was drawn at and lets the rest go.
        val pressure = Stroke(
            Tool.PEN,
            black,
            "round",
            listOf(
                StrokePoint(0.0, 0.0, 1.4, pressure = 0.6f, tilt = 14.0f, azimuth = 30.0f, dtMs = 0),
                StrokePoint(1.0, 1.0, 1.8, pressure = 0.8f, tilt = 15.0f, azimuth = 31.0f, dtMs = 6),
            ),
            uniformWidth = false,
        )

        val exported = Xopp.parseXml(Xopp.toXml(XoppSynthesis.synthesize(documentWith(pressure)).document))
        val back = exported.pages[0].layers[0].elements.single() as Stroke

        assertEquals("the drawn widths are the file's own record of the pressure", listOf(1.4, 1.8), back.points.map { it.width })
        assertNull(back.points[0].tilt)
        assertNull(back.points[0].dtMs)
    }

    @Test
    fun aLineStyleSurvivesTheExport() {
        val dashed = PlotElement(
            left = 10.0, top = 10.0, right = 210.0, bottom = 110.0,
            curves = listOf(PlotCurve(PlotKind.CARTESIAN, "x", red, lineStyle = LineStyle.DASHED)),
            xMin = -5.0, xMax = 5.0, samples = 40,
        )

        val result = synthesize(dashed)

        assertTrue(
            "a curve keeps the pattern the element gave it",
            strokesOf(result).any { it.color == red && it.lineStyle == LineStyle.DASHED },
        )
    }
}

package com.mobixournal.format.mobix

import com.mobixournal.format.Xopp
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotShading
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The living-entity codecs (`docs/mobix-elements.md`): the four constructs `.xopp` can only bake into
 * dead ink — a table, a function plot, a parametric shape, a circuit component — and the rule that
 * binds them all: **a codec models an element or preserves it whole, and never half-reads one.**
 *
 * The half-read case is the one worth testing hardest. An unknown *element type* survives as raw JSON
 * (that rule is older than this file, and `MobixElementCodecTest` holds it), but a *known* family this
 * build cannot read is the newer and subtler hazard: a plot whose `kind` is a name we have never heard
 * of, a parameter that is not a number. Modelling three of its fields and writing them back would
 * quietly turn the user's document into a different one, so these tests assert the element comes back
 * verbatim instead — and that it still round-trips byte-for-byte after that.
 */
class MobixLivingCodecTest {

    private fun encode(element: Element): JsonNode.Obj = MobixElementCodec.encode(element, MobixAssetSink())

    private fun decode(json: String): Element? =
        MobixElementCodec.decode(JsonParser.parse(json) as JsonNode.Obj, MobixAssetSource { null })

    private fun roundTrip(element: Element): Element =
        MobixElementCodec.decode(encode(element), MobixAssetSource { null })!!

    /** The doc example's table, with a grid whose numbers actually add up to its box. */
    private fun table() = TableElement(
        left = 72.0,
        top = 120.0,
        colWidths = listOf(120.0, 180.0, 100.0),
        rowHeights = listOf(28.0, 24.0, 24.0),
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
        borderWidth = 1.5,
        id = "tbl_01",
    )

    private fun plot() = PlotElement(
        left = 100.0,
        top = 200.0,
        right = 400.0,
        bottom = 380.0,
        curves = listOf(
            PlotCurve(
                kind = PlotKind.CARTESIAN,
                source = "sin(x) * exp(-0.1 * x)",
                color = 0xFFD32F2F.toInt(),
                strokeWidth = 1.5,
            ),
            PlotCurve(
                kind = PlotKind.PARAMETRIC,
                source = "sin(3 * t)",
                sourceY = "cos(2 * t)",
                color = 0xFF1976D2.toInt(),
                tMin = 0.0,
                tMax = 6.283185307179586,
            ),
            PlotCurve(
                kind = PlotKind.POLAR,
                source = "2 * cos(4 * theta)",
                color = 0xFF388E3C.toInt(),
                lineStyle = LineStyle.DASHED,
                thetaMin = 0.0,
                thetaMax = 3.141592653589793,
            ),
        ),
        xMin = -6.28,
        xMax = 6.28,
        samples = 300,
        showCriticalPoints = true,
        includeDerivative = false,
        shadeIntegral = PlotShading(from = 0.0, to = 3.14),
        parameters = linkedMapOf("a" to 1.0, "b" to 2.0),
        axisColor = 0xFF37474F.toInt(),
        summaryText = "Roots: x = {0.0, 3.14, -3.14}",
        id = "plt_01",
    )

    private fun shape() = ParametricShapeElement(
        shape = "TRIANGLE_SCALENE",
        vertices = listOf(
            ShapeVertex(100.0, 100.0),
            ShapeVertex(250.0, 120.0),
            ShapeVertex(160.0, 240.0),
        ),
        strokeColor = 0xFF0D47A1.toInt(),
        strokeWidth = 2.0,
        fillColor = 0x1A2196F3,
        showAngles = true,
        showSideLengths = true,
        id = "shp_01",
    )

    private fun circuit() = CircuitElement(
        symbol = "RESISTOR_IEEE",
        positionX = 300.0,
        positionY = 150.0,
        rotation = 90.0,
        strokeColor = 0xFFD84315.toInt(),
        strokeWidth = 1.6,
        value = "10 kΩ",
        pins = listOf(
            CircuitPin("p1", relX = -24.0, relY = 0.0, connectedTo = "net_node_A"),
            CircuitPin("p2", relX = 24.0, relY = 0.0),
        ),
        id = "ckt_res_01",
    )

    // ---- one round trip per family, field for field -------------------------------------------------

    @Test
    fun everyFamilyComesBackIdenticalToWhatWasWritten() {
        // Data-class equality is the point: every field of every family survives, so the test cannot
        // pass while one of them is quietly dropped by the codec.
        assertEquals(table(), roundTrip(table()))
        assertEquals(plot(), roundTrip(plot()))
        assertEquals(shape(), roundTrip(shape()))
        assertEquals(circuit(), roundTrip(circuit()))
    }

    @Test
    fun aPlotKeepsTheExpressionItsRangesAndItsParameters() {
        // The engine's own contract (`docs/math-engine.md`): everything needed to redraw the plot
        // without re-entering the formula is on disk. Asserted field by field, because "the element
        // round-tripped" would also hold if the codec wrote a summary the engine cannot evaluate.
        // An integral value is written without a fraction (`JsonWriter`), which is why `0`, not `0.0`,
        // is what a range that starts at zero looks like on disk.
        val written = JsonWriter.write(encode(plot()))
        for (field in listOf(
            "\"xMin\":-6.28", "\"xMax\":6.28", "\"samples\":300", "\"showCriticalPoints\":true",
            "\"includeDerivative\":false", "\"shadeIntegral\":{\"from\":0,\"to\":3.14}",
            "\"parameters\":{\"a\":1,\"b\":2}", "\"axisColor\":\"#37474fff\"",
            "\"kind\":\"CARTESIAN\"", "\"kind\":\"PARAMETRIC\"", "\"kind\":\"POLAR\"",
            "\"source\":\"sin(x) * exp(-0.1 * x)\"", "\"sourceY\":\"cos(2 * t)\"",
            "\"tMin\":0", "\"tMax\":6.283185307179586", "\"thetaMax\":3.141592653589793",
        )) {
            assertTrue("a plot must keep $field", written.contains(field))
        }

        // `summaryText` is the engine's cache, so it is written too — a reader that will not run the
        // math still shows the analysis — while never being needed to redraw the curves.
        assertTrue(written.contains("\"summaryText\":\"Roots: x = {0.0, 3.14, -3.14}\""))
    }

    @Test
    fun eachCurveIsWrittenWithOnlyTheRangeItsKindUses() {
        val curves = encode(plot()).getArr("curves")!!.list.map { it as JsonNode.Obj }
        assertFalse("a Cartesian curve has no parameter range", curves[0].has("tMin"))
        assertFalse("a Cartesian curve has no angular sweep", curves[0].has("thetaMin"))
        assertFalse("a parametric curve has no angular sweep", curves[1].has("thetaMin"))
        assertFalse("a polar curve has no y(t) expression", curves[2].has("sourceY"))
    }

    // ---- the table's own rules ----------------------------------------------------------------------

    @Test
    fun theGridIsTheAuthorityAndTheBoxIsDerivedFromIt() {
        val box = encode(table()).getObj("bounds")!!
        assertEquals(72.0, box.getDouble("left", 0.0), 1e-9)
        assertEquals(120.0, box.getDouble("top", 0.0), 1e-9)
        assertEquals("the columns' sum, not a number of its own", 472.0, box.getDouble("right", 0.0), 1e-9)
        assertEquals("the rows' sum", 196.0, box.getDouble("bottom", 0.0), 1e-9)
    }

    @Test
    fun aStaleBoxInAFileCannotMoveOrShrinkTheTable() {
        // Rule 3: `bounds` is a cache. This file claims a box four times too big; the grid is what the
        // reader believes, and the next write recomputes the box rather than passing the lie along.
        val json = """{"type":"table","id":"tbl_01","bounds":{"left":72,"top":120,"right":2000,"bottom":2000},
            "colWidths":[120,180,100],"rowHeights":[28,24,24],"cells":[]}""".trimIndent()

        val back = decode(json) as TableElement

        assertEquals(72.0, back.left, 1e-9)
        assertEquals("the columns' sum, not the box the file claimed", 400.0, back.width, 1e-9)
        assertEquals(76.0, back.height, 1e-9)
        assertEquals(
            "and the rewrite says the same thing",
            472.0,
            encode(back).getObj("bounds")!!.getDouble("right", 0.0),
            1e-9,
        )
    }

    @Test
    fun aShortRowReadsAsEmptyCellsRatherThanACorruptTable() {
        // A hand-edited file (or a merge that lost a cell) must degrade to a usable table: the grid is
        // the shape, and a missing cell is an empty one.
        val json = """{"type":"table","bounds":{"left":0,"top":0},
            "colWidths":[50,50],"rowHeights":[20,20],"cells":[[{"text":"only one","align":"RIGHT"}]]}"""

        val back = decode(json) as TableElement

        assertEquals(1, back.cells.size)
        assertEquals(TableCell("only one", TableAlign.RIGHT), back.cell(0, 0))
        assertEquals("a cell the row does not reach is empty, not an error", TableCell(), back.cell(0, 1))
        assertEquals(TableCell(), back.cell(1, 0))
    }

    @Test
    fun aTableThisBuildCannotReadIsPreservedWhole() {
        // `align` is the vocabulary rule's enum: a value we have no name for means we would be guessing
        // about the layout, so the element goes back unmodelled and keeps every byte it arrived with.
        // Only the alignment is unreadable — the grid and the box are right there — so the refusal is
        // about the value, not about the element being unrecognisable.
        val json = """{"type":"table","id":"tbl_77","bounds":{"left":0,"top":0},"colWidths":[40],"rowHeights":[20],"cells":[[{"text":"x","align":"JUSTIFIED"}]]}"""

        val preserved = decode(json) as RawElement

        assertEquals("table", preserved.name)
        assertEquals(RawElement.Syntax.JSON, preserved.syntax)
        assertEquals(json, preserved.body)
        assertEquals(
            "and a rewrite hands back the same object",
            json,
            JsonWriter.write(encode(preserved)),
        )
    }

    @Test
    fun aPlotWithAParameterThatIsNotANumberIsPreservedWhole() {
        // The model holds parameters as numbers; a string one is a schema this build does not know, and
        // reading it as 0.0 would silently change the curve the user sees.
        val json = """{"type":"plot","bounds":{"left":0,"top":0,"right":10,"bottom":10},"curves":[{"kind":"CARTESIAN","source":"a*x"}],"parameters":{"a":"one"}}"""

        assertEquals(json, (decode(json) as RawElement).body)
    }

    @Test
    fun aPlotWithACurveKindThisBuildDoesNotKnowIsPreservedWhole() {
        val json = """{"type":"plot","bounds":{"left":0,"top":0,"right":10,"bottom":10},"curves":[{"kind":"SPHERICAL","source":"r(theta, phi)"}]}"""

        assertEquals(json, (decode(json) as RawElement).body)
    }

    // ---- shapes and circuits ------------------------------------------------------------------------

    @Test
    fun aShapeTakesItsGeometryFromItsVerticesNotFromTheFile() {
        val json = """{"type":"parametricShape","id":"shp_9","shape":"TRAPEZOID_ISOSCELES",
            "bounds":{"left":-999,"top":-999,"right":-998,"bottom":-998},
            "vertices":[{"x":10,"y":20},{"x":50,"y":20},{"x":40,"y":60},{"x":20,"y":60}]}"""

        val back = decode(json) as ParametricShapeElement

        assertEquals(4, back.vertices.size)
        val box = encode(back).getObj("bounds")!!
        assertEquals("the box is the vertices' own extent", 10.0, box.getDouble("left", 0.0), 1e-9)
        assertEquals(50.0, box.getDouble("right", 0.0), 1e-9)
        assertEquals(60.0, box.getDouble("bottom", 0.0), 1e-9)
    }

    @Test
    fun aShapeKindFromANewerBuildIsStillModelled() {
        // The shape library's key set is open: an unknown kind is a figure from a newer build, and the
        // geometry that defines it is right there in the file, so refusing it would throw away a drawing.
        val json = """{"type":"parametricShape","shape":"PENTAGON_STAR",
            "vertices":[{"x":1,"y":2},{"x":3,"y":4},{"x":5,"y":6}]}"""

        val back = decode(json) as ParametricShapeElement

        assertEquals("PENTAGON_STAR", back.shape)
        assertEquals(3, back.vertices.size)
    }

    @Test
    fun aShapeWithoutVerticesIsNotAShape() {
        val json = """{"type":"parametricShape","shape":"TRIANGLE_SCALENE","vertices":[]}"""
        assertEquals(json, (decode(json) as RawElement).body)
    }

    @Test
    fun aCircuitKeepsItsPinsRelativeToItsPositionAndNamesTheNetTheyReach() {
        // The pins are stated in the component's own frame and before its rotation, which is what lets a
        // component turn without its wires coming adrift; a floating pin says so with an absent net.
        val written = encode(circuit())
        val position = written.getObj("position")!!
        assertEquals(300.0, position.getDouble("x", 0.0), 1e-9)
        assertEquals(90.0, written.getDouble("rotation", 0.0), 1e-9)
        assertEquals(
            "the ink the user drew it in travels with it",
            "#d84315ff",
            written.getStrOrNull("strokeColor"),
        )
        assertEquals(1.6, written.getDouble("strokeWidth", 0.0), 1e-9)

        val pins = written.getArr("pins")!!.list.map { it as JsonNode.Obj }
        assertEquals(-24.0, pins[0].getDouble("relX", 0.0), 1e-9)
        assertEquals("net_node_A", pins[0].getStrOrNull("connectedTo"))
        assertFalse("a floating pin names no net rather than an empty one", pins[1].has("connectedTo"))

        val back = roundTrip(circuit()) as CircuitElement
        assertEquals("10 kΩ", back.value)
        assertEquals(2, back.pins.size)
    }

    @Test
    fun aCircuitRemembersWhichSideItsLabelIsWrittenOn() {
        // The side is the reader's choice, not a layout accident, so it has to travel with the file —
        // in `.mobix` and in the `.xopp` bridge claim, which share this codec.
        for (side in CircuitLabelSide.entries) {
            val placed = circuit().copy(labelSide = side)
            assertEquals("$side survives the round trip", placed, roundTrip(placed))
        }
        assertEquals(
            "a non-default side is written as its own name",
            "right",
            encode(circuit().copy(labelSide = CircuitLabelSide.RIGHT)).getStrOrNull("labelSide"),
        )
        assertFalse(
            "the default is absence, not a stored value",
            encode(circuit()).has("labelSide"),
        )
        assertEquals(
            "and reading a file without it means above",
            CircuitLabelSide.ABOVE,
            (roundTrip(circuit()) as CircuitElement).labelSide,
        )
    }

    @Test
    fun aCircuitStillReadsTheBooleanLabelSideOlderFilesCarry() {
        // The label used to be a boolean (`labelBelow`). A document written then is still the user's
        // document: below is read as below, and the absence of the key as above.
        val legacyBelow = decode(
            """{"type":"circuit","symbol":"RESISTOR","position":{"x":10.0,"y":20.0},"labelBelow":true}""",
        ) as CircuitElement
        assertEquals(CircuitLabelSide.BELOW, legacyBelow.labelSide)
        val legacyAbove = decode(
            """{"type":"circuit","symbol":"RESISTOR","position":{"x":10.0,"y":20.0}}""",
        ) as CircuitElement
        assertEquals(CircuitLabelSide.ABOVE, legacyAbove.labelSide)
        assertTrue("and it is written back in the current spelling", encode(legacyBelow).has("labelSide"))
    }

    @Test
    fun aComponentWithNoPinsIsAPlainSymbol() {
        // The shape library's own drawings need no connectivity, so an empty pin list is written as
        // nothing at all — "no anchors" and "an empty set of anchors" are one fact, not two spellings.
        val plain = CircuitElement("CAPACITOR", 10.0, 20.0, id = "ckt_1")

        assertFalse(encode(plain).has("pins"))
        assertEquals("and a component that chose no ink is drawn in the default", 0xFF000000.toInt(), plain.strokeColor)
        assertEquals(plain, roundTrip(plain))
    }

    // ---- identity, and what the `.xopp` writer must not do ------------------------------------------

    @Test
    fun eachFamilyMintsItsOwnIdPrefixAndKeepsAnIdItIsGiven() {
        val prefixes = listOf(
            TableElement(0.0, 0.0, listOf(10.0), listOf(10.0)) to "tbl_",
            plot().copy(id = null) to "plt_",
            shape().copy(id = null) to "shp_",
            circuit().copy(id = null) to "ckt_",
        )
        for ((element, prefix) in prefixes) {
            val minted = (roundTrip(element) as Element).let {
                when (it) {
                    is TableElement -> it.id
                    is PlotElement -> it.id
                    is ParametricShapeElement -> it.id
                    is CircuitElement -> it.id
                    else -> null
                }
            }
            assertTrue("$prefix… expected, got $minted", minted!!.startsWith(prefix))
        }
        assertEquals("an id already in the file is the element's identity", "plt_01", (roundTrip(plot()) as PlotElement).id)
    }

    @Test
    fun aLivingEntityIsNotWrittenIntoAXoppItHasNoSpellingFor() {
        // A `.mobix`-only construct compiled into strokes is the synthesis export's job
        // (`docs/mobix-format.md`); until that exists, the writer skips it rather than inventing an XML
        // element desktop Xournal++ would choke on or ignore.
        val xml = Xopp.toXml(documentWith(plot(), circuit()))

        assertFalse(xml.contains("<plot"))
        assertFalse(xml.contains("<circuit"))
        assertFalse(xml.contains("RESISTOR_IEEE"))
    }

    @Test
    fun aJsonBodyIsNeverWrittenAsXoppMarkup() {
        // The other half of the same rule: an unknown element preserved from a `.mobix` carries JSON in
        // its body, and writing that as XML would put JSON inside a `.xopp`.
        val raw = RawElement(name = "3dPlotElement", body = """{"type":"3dPlotElement"}""", syntax = RawElement.Syntax.JSON)

        val xml = Xopp.toXml(documentWith(raw))

        assertFalse(xml.contains("3dPlotElement"))
        assertFalse("and no JSON leaks into the document", xml.contains("{"))
    }

    @Test
    fun anXmlCarriedElementStillExportsAsItself() {
        // The guard above must not swallow the case the channel exists for.
        val raw = RawElement("vendorThing", attrs = linkedMapOf("width" to "12"), body = "<x/>")
        val xml = Xopp.toXml(documentWith(raw))
        assertTrue(xml.contains("vendorThing"))
    }

    private fun documentWith(vararg elements: Element): Document = Document(
        pages = listOf(
            Page(
                width = 595.27559100,
                height = 841.88976400,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(elements.toList())),
            ),
        ),
    )
}

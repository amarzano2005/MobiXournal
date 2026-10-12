package com.mobixournal.format.mobix

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.render.XoppSynthesis
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **bridge block** (`docs/mobix-format.md`): the living entities an export flattened, carried in the
 * `.xopp` so the export can be re-imported as a document instead of as a drawing of one.
 *
 * Two halves, and the second is the one that matters:
 *
 *  - **The write half**: the block is emitted, it says where each entity's ink landed and what that ink
 *    hashes to, and the file stays a valid `.xopp` — a conforming XML parser accepts it, which is the
 *    check that catches an escaping bug that a round trip through our own reader would not.
 *  - **The verification rule**: the block is a *claim*. It is honoured only where the ink it names is
 *    still there and still hashes to what it says, and every other case — edited ink, missing ink, a
 *    newer schema, no block at all — comes back as the ordinary ink, which is what the user is looking
 *    at. The tests below are the whole truth table of that rule.
 */
class MobixMetaBlockTest {

    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()
    private val appVersion = "2.0.0"

    /** An ink stroke that is not a living entity: it must survive the whole round trip untouched. */
    private fun inkStroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(40.0, 60.0, 1.0)),
        uniformWidth = true,
        id = "stk_ink",
    )

    private fun table(cellText: String = "Parametro") = TableElement(
        left = 72.0, top = 120.0,
        colWidths = listOf(120.0, 180.0),
        rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell(cellText, bold = true, fillColor = 0x15000000), TableCell("Formula"))),
        headerRow = true,
        id = "tbl_1",
    )

    private fun plot() = PlotElement(
        left = 100.0, top = 200.0, right = 300.0, bottom = 320.0,
        curves = listOf(PlotCurve(PlotKind.CARTESIAN, "sin(x)", red, strokeWidth = 1.5)),
        xMin = -6.28, xMax = 6.28, samples = 60,
        id = "plt_1",
    )

    private fun circuit() = CircuitElement(
        symbol = "RESISTOR",
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
                id = "pg_1",
            ),
        ),
    )

    /** The export of [document], as a `.xopp` file read back — the state an import actually starts from. */
    private fun exported(document: Document, documentId: String? = "doc_1"): Document =
        Xopp.parseXml(Xopp.toXml(XoppSynthesis.synthesize(document, documentId).document))

    /** The same, as a `.mobix` document built from the file — the import's own output. */
    private fun imported(document: Document): MobixDocument =
        MobixDocument.of(exported(document), appVersion = appVersion, nowMillis = 1_700_000_000_000L)

    private fun elementsOf(mobix: MobixDocument, page: Int = 0, layer: Int = 0): List<Element> =
        mobix.pages[page].layers[layer].elements

    private fun layerOf(document: Document, index: Int = 0): List<Element> = document.pages[0].layers[index].elements

    // ---- the write half --------------------------------------------------------------------------

    @Test
    fun theExportWritesABlockSayingWhereEachEntitysInkLanded() {
        val exported = exported(documentWith(inkStroke(), table(), plot()))

        val block = MobixMetaBlock.decode(exported.bridge!!)!!

        assertEquals("the document's identity is recorded for a later \"which export was this?\"", "doc_1", block.doc)
        assertEquals(2, block.claims.size)
        assertEquals("the drawing stroke is not a claim", "tbl_1", (block.claims[0].element as TableElement).id)
        assertEquals("and the table's ink starts right after it", 1, block.claims[0].at)
        assertEquals("plt_1", (block.claims[1].element as PlotElement).id)
        assertEquals(
            "the claims tile the flattened ink in document order",
            block.claims[1].at,
            block.claims[0].at + block.claims[0].count,
        )
        assertEquals(
            "with nothing flattened that is not claimed",
            layerOf(exported).size - 1,
            block.claims.sumOf { it.count },
        )
        assertTrue("each claim carries a real digest", block.claims.all { it.sha256.length == 64 })
    }

    @Test
    fun theDigestMatchesTheInkTheFileHolds() {
        val exported = exported(documentWith(table()))
        val block = MobixMetaBlock.decode(exported.bridge!!)!!
        val claim = block.claims.single()

        assertEquals(
            "the import recomputes exactly this digest from the parsed ink",
            claim.sha256,
            MobixMetaDigest.of(layerOf(exported).subList(claim.at, claim.at + claim.count)),
        )
    }

    @Test
    fun aDocumentWithNothingToFlattenCarriesNoBlock() {
        assertNull(exported(documentWith(inkStroke())).bridge)
    }

    @Test
    fun theBlockLeavesTheFileValidXmlAndSurvivesOurOwnWriter() {
        // The hazard the escape exists for: a payload with two hyphens side by side, and a document
        // whose ink it describes. A conforming parser is what desktop Xournal++ is.
        val exported = exported(documentWith(inkStroke(), table("x--1"), plot()))
        val xml = Xopp.toXml(exported)

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        assertFalse(
            "the comment cannot contain a double hyphen, escaped or not",
            xml.substringAfter("mobix:meta").substringBefore(" -->").contains("--"),
        )
        assertEquals("and the block comes back byte-identical", exported.bridge, Xopp.parseXml(xml).bridge)
        assertEquals("while the writer stays a fixed point", xml, Xopp.toXml(Xopp.parseXml(xml)))
    }

    @Test
    fun aPayloadFullOfHyphensRoundTripsThroughTheFile() {
        val text = "-- x--1 -- -"

        val exported = exported(documentWith(table(text)))

        val block = MobixMetaBlock.decode(exported.bridge!!)!!
        assertEquals(
            "the cell text is unchanged after escaping and unescaping",
            text,
            (block.claims.single().element as TableElement).cells[0][0].text,
        )
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(Xopp.toXml(exported).toByteArray()))
    }

    // ---- the verification rule: honoured --------------------------------------------------------

    @Test
    fun intactInkRevivesTheEntitiesItWasFlattenedFrom() {
        val mobix = imported(documentWith(inkStroke(), table(), plot()))
        val elements = elementsOf(mobix)

        assertEquals("the drawing stroke stays a stroke, the two entities are entities again", 3, elements.size)
        val ink = elements[0] as Stroke
        assertEquals("in its own place in the layer", listOf(10.0, 40.0), ink.points.map { it.x })
        assertEquals(
            "though a .xopp has no element ids, so the ink is re-identified and only the entities keep theirs",
            ElementId.STROKE,
            ink.id!!.substringBefore('_'),
        )
        val table = elements[1] as TableElement
        assertEquals("with the identity the block recorded", "tbl_1", table.id)
        assertEquals("and its own data", "Parametro", table.cells[0][0].text)
        assertEquals("plt_1", (elements[2] as PlotElement).id)
        assertTrue("nothing of the flattened ink is left behind", elements.none { it is TextElement })
    }

    @Test
    fun theWholeBridgeRestoresADocumentThroughTheFile() {
        // The point of the whole feature: .mobix → .xopp → .mobix comes back as the same document.
        val original = documentWith(table(), circuit())
        val mobix = imported(original)
        val elements = elementsOf(mobix)

        assertEquals(2, elements.size)
        assertEquals("ckt_1", (elements[1] as CircuitElement).id)
        assertEquals("the pins travel back with it", 2, (elements[1] as CircuitElement).pins.size)
        assertEquals("and the entities are editable data, not ink", "tbl_1", (elements[0] as TableElement).id)
    }

    // ---- the verification rule: dropped ---------------------------------------------------------

    @Test
    fun editedInkIsReadAsInkAndOnlyThatClaimIsDropped() {
        val exported = exported(documentWith(table(), plot()))
        val block = MobixMetaBlock.decode(exported.bridge!!)!!
        val plotClaim = block.claims.single { (it.element as? PlotElement)?.id == "plt_1" }

        // The user (or desktop Xournal++) moved a point of one stroke of the plot's ink.
        val edited = withStrokeEdited(exported, plotClaim.at)
        val mobix = MobixDocument.of(edited, appVersion = appVersion, nowMillis = 1L)
        val elements = elementsOf(mobix)

        assertEquals("the table's claim still holds", "tbl_1", (elements[0] as TableElement).id)
        assertTrue("the plot's does not, and its ink is left alone", elements.drop(1).none { it is PlotElement })
        assertEquals(
            "which is the table plus every stroke of the plot's ink",
            plotClaim.count + 1,
            elements.size,
        )
    }

    @Test
    fun inkThatWasDeletedTakesItsClaimWithIt() {
        val exported = exported(documentWith(table()))
        val flattened = layerOf(exported)
        val emptied = exported.copy(
            pages = listOf(exported.pages[0].copy(layers = listOf(Layer(flattened.take(3))))),
        )

        val elements = elementsOf(MobixDocument.of(emptied, appVersion = appVersion, nowMillis = 1L))

        assertTrue("a claim about ink that is no longer there is not honoured", elements.none { it is TableElement })
        assertEquals("and the file imports as what it holds", 3, elements.size)
    }

    @Test
    fun aBlockFromANewerSchemaIsRefusedWhole() {
        val exported = exported(documentWith(table()))
        val newer = exported.copy(bridge = exported.bridge!!.replace("\"version\":1", "\"version\":2"))

        assertNull("a schema this build does not know is not half-read", MobixMetaBlock.decode(newer.bridge!!))

        val elements = elementsOf(MobixDocument.of(newer, appVersion = appVersion, nowMillis = 1L))
        assertTrue(elements.none { it is TableElement })
    }

    @Test
    fun aBlockThatIsNotAJsonObjectIsIgnoredRatherThanFatal() {
        val exported = exported(documentWith(table()))
        val flattened = layerOf(exported).size

        for (broken in listOf("", "not json", "[]", """{"version":1}""", """{"version":1,"elements":{}}""")) {
            val document = exported.copy(bridge = broken)
            assertNull("$broken", MobixMetaBlock.decode(broken))
            val elements = elementsOf(MobixDocument.of(document, appVersion = appVersion, nowMillis = 1L))
            assertEquals("$broken: the ink is untouched", flattened, elements.size)
        }
    }

    @Test
    fun aUsableBlockWithNoUsableEntryClaimsNothing() {
        val exported = exported(documentWith(table()))
        val entryless = """{"version":1,"elements":[{}]}"""

        assertEquals("a malformed entry does not take the block down with it", 0, MobixMetaBlock.decode(entryless)!!.claims.size)
        val elements = elementsOf(
            MobixDocument.of(exported.copy(bridge = entryless), appVersion = appVersion, nowMillis = 1L),
        )
        assertEquals("and the drawing is untouched", layerOf(exported).size, elements.size)
    }

    @Test
    fun aClaimThatNamesAPageOrRangeThatIsNotThereIsDropped() {
        val exported = exported(documentWith(table()))
        val block = MobixMetaBlock.decode(exported.bridge!!)!!
        val claim = block.claims.single()

        val wrongPage = block.copy(claims = listOf(claim.copy(page = 7)))
        val pastTheEnd = block.copy(claims = listOf(claim.copy(at = 14)))
        val nonsense = block.copy(claims = listOf(claim.copy(count = 0)))

        for (candidate in listOf(wrongPage, pastTheEnd, nonsense)) {
            val elements = elementsOf(
                MobixDocument.of(exported.copy(bridge = candidate.encode()), appVersion = appVersion, nowMillis = 1L),
            )
            assertTrue("${candidate.claims} is not a claim about this ink", elements.none { it is TableElement })
        }
    }

    @Test
    fun twoClaimsAboutTheSameInkCannotBothWin() {
        val exported = exported(documentWith(table()))
        val block = MobixMetaBlock.decode(exported.bridge!!)!!
        val claim = block.claims.single()
        // The second claims the same range: at most the first can be telling the truth.
        val doubled = block.copy(claims = listOf(claim, claim.copy(element = plot().copy(id = "plt_liar"))))

        val elements = elementsOf(
            MobixDocument.of(exported.copy(bridge = doubled.encode()), appVersion = appVersion, nowMillis = 1L),
        )

        assertTrue("the first claim wins", elements.any { (it as? TableElement)?.id == "tbl_1" })
        assertTrue("and the impossible one is refused", elements.none { it is PlotElement })
    }

    @Test
    fun aClaimAboutSomethingThatIsNotALivingEntityIsNotAClaim() {
        val exported = exported(documentWith(table()))
        val block = MobixMetaBlock.decode(exported.bridge!!)!!
        val claim = block.claims.single()
        // A block that claims ink was a *stroke* is describing nothing this bridge can do.
        val forged = block.copy(
            claims = listOf(claim.copy(element = inkStroke(), sha256 = claim.sha256)),
        )

        val decoded = MobixMetaBlock.decode(forged.encode())

        assertTrue("the entry is dropped, the block survives", decoded!!.claims.isEmpty())
    }

    @Test
    fun aXoppWithNoBlockImportsUnchanged() {
        val plain = documentWith(inkStroke(), table())
        val flattenedByHand = plain.copy(
            pages = listOf(plain.pages[0].copy(layers = listOf(Layer(listOf(inkStroke(), inkStroke()))))),
        )

        val mobix = MobixDocument.of(flattenedByHand, appVersion = appVersion, nowMillis = 1L)

        assertNull("there is nothing to verify", flattenedByHand.bridge)
        assertTrue("so nothing is invented", elementsOf(mobix).none { it is TableElement })
        assertEquals("and the drawing is what it was", 2, elementsOf(mobix).size)
    }

    @Test
    fun anImportWithNoBlockIsStillAnImport() {
        val mobix = MobixDocument.of(documentWith(inkStroke()), appVersion = appVersion, nowMillis = 1L)

        assertNotNull("the source sidecar is written as always", mobix.bridge)
        assertEquals("and the drawing imports as one stroke", 1, elementsOf(mobix).size)
        assertTrue(elementsOf(mobix).single() is Stroke)
    }

    @Test
    fun theRoundTripDoesNotResurrectTheInkABlockDescribes() {
        // The generous failure mode: exporting an already flattened document keeps its block (the ink is
        // unchanged, so the claims are still true) and importing it again revives the entities — twice
        // through the bridge is still one document, not ink plus a table.
        val once = exported(documentWith(table()))
        val twice = exported(once)
        val elements = elementsOf(MobixDocument.of(twice, appVersion = appVersion, nowMillis = 1L))

        assertEquals("the table is back once", 1, elements.count { it is TableElement })
        assertEquals("and nothing else is", 1, elements.size)
    }

    /** [document] with the stroke at [index] of its only layer nudged — the user edited it. */
    private fun withStrokeEdited(document: Document, index: Int): Document {
        val layer = document.pages[0].layers[0]
        val elements = layer.elements.mapIndexed { at, element ->
            if (at != index) {
                element
            } else {
                val stroke = element as Stroke
                stroke.copy(points = stroke.points.map { it.copy(x = it.x + 3.0) })
            }
        }
        return document.copy(
            pages = listOf(document.pages[0].copy(layers = listOf(layer.copy(elements = elements)))),
        )
    }
}

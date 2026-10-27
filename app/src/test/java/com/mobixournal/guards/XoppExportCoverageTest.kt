package com.mobixournal.guards

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.isLivingEntity
import com.mobixournal.render.LivingInk
import com.mobixournal.render.XoppSynthesis
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.mobix` → `.xopp` export as a **coverage ratchet**: every element type the native format can
 * hold has to reach a `.xopp` file, and what cannot reach it has to be *said*, not dropped.
 *
 * The promise this protects is the one that makes the native format safe to design freely: `.mobix` is
 * where the document lives, and a `.xopp` remains a complete interchange copy of it — flattened where
 * the XML has no words for a recipe, with the recipe kept beside the ink so it can be revived
 * ([`mobix-format.md`](../../../../../../docs/mobix-format.md),
 * [`xopp-bridge.md`](../../../../../../docs/xopp-bridge.md)). A new element family added to `.mobix`
 * without an export arm would break that quietly, one document at a time.
 *
 * Two mechanisms, because one is not enough:
 *
 *  - **the compiler**: [family] is an exhaustive `when` over `Element`, a sealed interface. A new
 *    subtype stops this file compiling, which is the one warning nobody can ignore.
 *  - **the recorded count**: [elementFamilies] is the number of subtypes this build has, so the
 *    developer who adds a branch to [family] is made to add a sample to [samples] as well — and a
 *    sample is what the export walk below needs to actually exercise the new family.
 *
 * What this file deliberately does not do is demand the impossible: `.xopp` has no place for stylus
 * telemetry, and no drawing for an element a *newer* build wrote. Those two losses are asserted as
 * losses — the drawing survives, and the report names what was left out.
 */
class XoppExportCoverageTest {

    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()

    /**
     * How many element types this build models. Recorded rather than derived, like the source-size
     * ceilings: [family] already fails to compile on a new one, and this number fails the *other* half of
     * adding it — a branch with no sample, which would let a family ship without its export exercised.
     */
    private val elementFamilies = 10

    /** A pen stroke carrying the telemetry `.xopp` cannot hold. */
    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(
            StrokePoint(10.0, 10.0, 1.0, pressure = 0.4f, tilt = 30f, azimuth = 12f, dtMs = 8),
            StrokePoint(40.0, 60.0, 2.5, pressure = 0.9f, tilt = 31f, azimuth = 13f, dtMs = 9),
        ),
        uniformWidth = false,
        id = "stk_1",
    )

    private fun text() = TextElement("Sans", 12.0, 40.0, 80.0, black, "hello", id = "txt_1")

    /** Markup that came from a `.xopp`: it has its own spelling, so it exports as itself. */
    private fun xoppMarkup() = RawElement("vendor", mapOf("a" to "b"), "<x/>")

    /** An element a *newer* build wrote into `.mobix`: JSON body, no `.xopp` spelling at all. */
    private fun newerBuildMarkup() =
        RawElement("futuristic", emptyMap(), """{"kind":"futuristic"}""", RawElement.Syntax.JSON)

    /** Opaque bytes as far as both formats are concerned — neither validates a picture. */
    private fun picture() =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    private fun image() = ImageElement(10.0, 10.0, 40.0, 40.0, picture(), id = "img_1")

    private fun texImage() = TexImageElement(20.0, 20.0, 120.0, 60.0, "x^2", black, id = "tex_1")

    private fun table() = TableElement(
        left = 72.0, top = 120.0,
        colWidths = listOf(120.0, 180.0),
        rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell("Parametro", bold = true), TableCell("Formula"))),
        headerRow = true,
        id = "tbl_1",
    )

    private fun plot(formula: String = "sin(x)") = PlotElement(
        left = 100.0, top = 200.0, right = 300.0, bottom = 320.0,
        curves = listOf(PlotCurve(PlotKind.CARTESIAN, formula, red, strokeWidth = 1.5)),
        xMin = -6.28, xMax = 6.28, samples = 60,
        id = "plt_1",
    )

    private fun shape() = ParametricShapeElement(
        shape = "TRIANGLE_SCALENE",
        vertices = listOf(ShapeVertex(0.0, 0.0), ShapeVertex(40.0, 0.0), ShapeVertex(20.0, 30.0)),
        strokeColor = red,
        strokeWidth = 1.5,
        id = "shp_1",
    )

    private fun circuit() = CircuitElement(
        symbol = "RESISTOR",
        positionX = 300.0, positionY = 150.0,
        strokeColor = red, strokeWidth = 1.5,
        value = "10 kΩ",
        pins = listOf(CircuitPin("p1", relX = -24.0), CircuitPin("p2", relX = 24.0)),
        id = "ckt_1",
    )

    private fun mathBlock() = MathBlockElement(60.0, 60.0, 200.0, 92.0, "\\int_0^1 x dx", black, id = "mth_1")

    /** One sample per element family, plus the second `RawElement` syntax, which is a different promise. */
    private fun samples(): List<Element> = listOf(
        stroke(),
        text(),
        xoppMarkup(),
        newerBuildMarkup(),
        image(),
        texImage(),
        table(),
        plot(),
        shape(),
        circuit(),
        mathBlock(),
    )

    /**
     * The family of an element. **This is the ratchet**: `Element` is sealed, so a new subtype stops this
     * function compiling, and a developer who names it here is sent straight to [samples] by
     * [everyElementFamilyHasASample].
     */
    private fun family(element: Element): String = when (element) {
        is Stroke -> "stroke"
        is TextElement -> "text"
        is RawElement -> "markup"
        is ImageElement -> "image"
        is TexImageElement -> "texImage"
        is TableElement -> "table"
        is PlotElement -> "plot"
        is ParametricShapeElement -> "shape"
        is CircuitElement -> "circuit"
        is MathBlockElement -> "mathBlock"
    }

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

    private fun reopened(document: Document): Document =
        ByteArrayOutputStream().also { Xopp.save(document, it) }
            .toByteArray()
            .let { bytes -> ByteArrayInputStream(bytes).use { Xopp.open(it) } }

    @Test
    fun everyElementFamilyHasASample() {
        val families = samples().map(::family).distinct()
        assertEquals(
            "this build models $elementFamilies element types and this walk exercises ${families.size} " +
                "of them ($families). A new `.mobix` element family needs its branch in `family()` **and** " +
                "a sample here, because the export below is what proves it can still leave the native " +
                "format. Update `elementFamilies` when the count really changes.",
            elementFamilies,
            families.size,
        )
        assertTrue(
            "a sample list without the newer-build markup would not prove the reporting rule",
            samples().any { it is RawElement && it.syntax == RawElement.Syntax.JSON },
        )
    }

    @Test
    fun everyLivingFamilyCompilesToInk() {
        val gaps = samples().filter { it.isLivingEntity }.mapNotNull { element ->
            val ink = LivingInk.of(element)
            when {
                ink.elements.isEmpty() -> "${family(element)}: no ink at all"
                ink.uncompiled.isNotEmpty() -> "${family(element)}: ${ink.uncompiled.joinToString { it.type }}"
                else -> null
            }
        }
        assertTrue(
            "a living entity that compiles to nothing cannot leave the native format — give it an " +
                "export arm in `LivingInk` (docs/xopp-bridge.md owns the rule):\n  " +
                gaps.joinToString("\n  "),
            gaps.isEmpty(),
        )
    }

    @Test
    fun everyFamilyReachesTheXoppFile() {
        val export = XoppSynthesis.synthesize(documentWith(*samples().toTypedArray()))
        assertEquals(
            "the only thing a complete export may leave behind is what `.xopp` has no words for, and it " +
                "must name it",
            listOf("futuristic"),
            export.uncompiled.map { it.type },
        )

        val read = reopened(export.document)
        val written = read.pages[0].layers[0].elements
        assertEquals(
            "every element of the export is in the file — none dropped on the way, none invented",
            export.document.pages[0].layers[0].elements.size,
            written.size,
        )
        assertFalse(
            "no recipe may survive into the XML: `.xopp` cannot keep one",
            written.any { it.isLivingEntity },
        )
        assertTrue("the markup from `.xopp` came back as itself", written.any { it is RawElement })
        assertNotNull("the block that can revive the flattened entities rode along", read.bridge)
    }

    @Test
    fun theDrawingSurvivesWhatTheFormatCannotCarry() {
        val source = stroke()
        // Through the writer and back, because the loss this pins is the *file's*: the synthesis hands a
        // stroke over untouched, and it is the `.xopp` schema that has no fields for the telemetry.
        val exported = reopened(XoppSynthesis.synthesize(documentWith(source)).document)
            .pages[0].layers[0].elements
            .filterIsInstance<Stroke>()
            .single()

        assertEquals(
            "a stroke's geometry is what a `.xopp` reader draws: every point and the width there",
            source.points.map { Triple(it.x, it.y, it.width) },
            exported.points.map { Triple(it.x, it.y, it.width) },
        )
        assertNull(
            "the only thing the export may drop is the telemetry the XML has no fields for",
            exported.points.firstOrNull()?.pressure,
        )
        assertTrue(
            "the sample has to carry telemetry, or this test proves nothing",
            source.points.any { it.pressure != null && it.tilt != null && it.dtMs != null },
        )
    }

    @Test
    fun whatTheFormatCannotRepresentIsReported() {
        val unreadable = XoppSynthesis.synthesize(documentWith(plot(formula = "sin(x")))
        assertTrue(
            "a curve this build cannot parse has no ink to invent, so the export must say so instead of " +
                "writing a plot with a hole in it",
            unreadable.uncompiled.any { it.type == "plotCurve" },
        )

        val newer = XoppSynthesis.synthesize(documentWith(newerBuildMarkup()))
        assertTrue(
            "an element from a newer `.mobix` has no `.xopp` spelling: dropping it silently is the one " +
                "thing the bridge must never do",
            newer.uncompiled.any { it.type == "futuristic" && "newer build" in it.reason },
        )
    }
}

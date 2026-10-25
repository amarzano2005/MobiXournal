package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan is the half of the PDF export both shells share, so it is tested where it lives: the ops it
 * produces for a page, in order, with the numbers the emitters would otherwise have to compute
 * themselves. The emitters are then a `when` over these — if the plan is right, the app's PDF and the
 * desktop's are the same drawing.
 */
class PdfPagePlanTest {

    private val height = 842.0
    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()

    private val transform = PdfPageTransform(0f, 0f, height.toFloat())

    private fun page(vararg elements: Element) = Page(
        width = 595.27559100,
        height = height,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun pen(vararg points: StrokePoint, tool: Tool = Tool.PEN, style: LineStyle = LineStyle.PLAIN) = Stroke(
        tool = tool,
        color = black,
        capStyle = "round",
        points = points.toList(),
        uniformWidth = true,
        lineStyle = style,
    )

    private fun text(content: String = "Hello", font: String = "Sans") = TextElement(
        font = font,
        size = 12.0,
        x = 72.0,
        y = 96.0,
        color = black,
        content = content,
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

    @Test
    fun thePageIsFlippedIntoPdfUserSpace() {
        val plan = PdfPagePlan.of(page(pen(StrokePoint(10.0, 10.0, 1.0), StrokePoint(40.0, 60.0, 1.0))), transform)

        val path = plan.filterIsInstance<PdfPath>().single()
        // y-down on the sheet, y-up in PDF user space: the page height is what flips it.
        assertEquals(PdfPoint(10f, 832f), path.points[0])
        assertEquals(PdfPoint(40f, 782f), path.points[1])
        assertEquals(black, path.colour)
        assertEquals(1f, path.alpha, 0f)
    }

    @Test
    fun thePenCarriesOneWidthPerSegment() {
        val plan = PdfPagePlan.of(
            page(pen(StrokePoint(1.0, 1.0, 1.0), StrokePoint(2.0, 2.0, 3.0), StrokePoint(3.0, 3.0, 5.0))),
            transform,
        )

        val path = plan.filterIsInstance<PdfPath>().single()
        assertEquals("two segments between three points", 2, path.widths.size)
        assertEquals("the midpoint of the two vertices", 2.0, path.widths[0], 1e-6)
        assertEquals(4.0, path.widths[1], 1e-6)
        assertEquals("a per-segment path has no constant width", 0.0, path.width, 1e-6)
    }

    @Test
    fun theHighlighterIsOneFlatBandWithMultiply() {
        val plan = PdfPagePlan.of(
            page(pen(StrokePoint(1.0, 1.0, 12.0), StrokePoint(40.0, 4.0, 12.0), tool = Tool.HIGHLIGHTER)),
            transform,
        )

        val path = plan.filterIsInstance<PdfPath>().single()
        assertTrue("one constant width, not a per-segment taper", path.widths.isEmpty())
        assertEquals(12.0, path.width, 1e-6)
        assertTrue("the flatten is a multiply, so the band does not darken itself", path.multiply)
        assertEquals(
            "the highlighter paints at its own render alpha, not the stored one",
            InkGeometry.renderColor(Tool.HIGHLIGHTER, black).ushr(24).and(0xFF) / 255f,
            path.alpha,
            1e-6f,
        )
    }

    @Test
    fun aFilledStrokeIsAFillUnderItsOutline() {
        // `fill` is the alpha of the wash (XoppColor.withAlpha), not a second colour.
        val filled = pen(StrokePoint(1.0, 1.0, 1.0), StrokePoint(9.0, 9.0, 1.0)).copy(fill = 0x80)
        val plan = PdfPagePlan.of(page(filled), transform)

        assertEquals("the fill comes first, so the outline sits on top", 2, plan.size)
        val fill = plan[0] as PdfFill
        assertEquals(0x80 / 255f, fill.alpha, 1e-6f)
        assertTrue(plan[1] is PdfPath)
    }

    @Test
    fun textIsLaidOutAndSanitisedWhereItIsPlanned() {
        val plan = PdfPagePlan.of(page(text("café 😀")), transform)

        val run = plan.filterIsInstance<PdfText>().single()
        // The emoji is a surrogate pair, and WinAnsi has room for neither half: one `?` per character,
        // which is exactly what the canvas's own text writer does rather than dropping the pair.
        assertEquals("one line, WinAnsi only", listOf("café ??"), run.lines)
        // The baseline sits an ascent below the box's top edge, flipped into PDF space.
        assertEquals(842f - (96.0 + 12.0 * PdfPagePlan.ASCENT_RATIO).toFloat(), run.y, 1e-4f)
        assertEquals(12.0 * PdfPagePlan.LINE_HEIGHT_RATIO, run.lineHeight, 1e-6)
        assertEquals(PdfFontFace(PdfFamily.SANS, bold = false, italic = false), run.face)
    }

    @Test
    fun aSerifAndAMonoFontAskForTheirOwnBaseFourteenFace() {
        assertEquals(
            PdfFontFace(PdfFamily.SERIF, bold = true, italic = false),
            PdfPagePlan.of(page(text(font = "Times Bold")), transform).filterIsInstance<PdfText>().single().face,
        )
        assertEquals(
            PdfFontFace(PdfFamily.MONO, bold = false, italic = true),
            PdfPagePlan.of(page(text(font = "Courier Italic")), transform).filterIsInstance<PdfText>().single().face,
        )
    }

    @Test
    fun theLivingTableArrivesAsItsOwnOpsInPlace() {
        val plan = PdfPagePlan.of(page(pen(StrokePoint(1.0, 1.0, 1.0), StrokePoint(2.0, 2.0, 1.0)), table()), transform)

        assertTrue("the hand stroke is first", plan[0] is PdfPath)
        val words = plan.filterIsInstance<PdfText>().flatMap { it.lines }
        assertTrue("the table's cells are text of their own", words.contains("Parametro"))
        assertTrue("including the header's second cell", words.contains("Formula"))
        assertTrue("and its grid is ink", plan.filterIsInstance<PdfPath>().size > 1)
    }

    @Test
    fun aPlotIsCompiledRatherThanRecomputed() {
        val plot = PlotElement(
            left = 100.0, top = 200.0, right = 300.0, bottom = 320.0,
            curves = listOf(PlotCurve(PlotKind.CARTESIAN, "sin(x)", red, strokeWidth = 1.5)),
            xMin = -6.28, xMax = 6.28, samples = 60,
            id = "plt_1",
        )

        val plan = PdfPagePlan.of(page(plot), transform)

        assertTrue("a plot draws axes, ticks and a curve", plan.filterIsInstance<PdfPath>().size >= 3)
        assertTrue("and says what it is", plan.filterIsInstance<PdfText>().isNotEmpty())
    }

    @Test
    fun anImageKeepsItsBytesAndItsBoxForTheEmitterToDecode() {
        val image = ImageElement(
            data = byteArrayOf(1, 2, 3),
            left = 10.0, top = 20.0, right = 60.0, bottom = 90.0,
        )

        val op = PdfPagePlan.of(page(image), transform).filterIsInstance<PdfImage>().single()

        assertEquals(3, op.data.size)
        assertEquals(10f, op.left, 0f)
        assertEquals("the bottom edge is flipped", 842f - 90f, op.bottom, 1e-4f)
        assertEquals(50f, op.width, 1e-4f)
        assertEquals(70f, op.height, 1e-4f)
    }

    @Test
    fun aTransparentHighlighterFillStillFloodsAtItsOwnAlpha() {
        val highlight = pen(StrokePoint(1.0, 1.0, 12.0), StrokePoint(9.0, 9.0, 12.0), tool = Tool.HIGHLIGHTER)
            .copy(fill = 0xFF)
        val plan = PdfPagePlan.of(page(highlight), transform)

        val fill = plan.filterIsInstance<PdfFill>().single()
        assertTrue("the fill blends the same way the stroke does", fill.multiply)
        assertEquals(1f, fill.alpha, 1e-6f)
    }

    /**
     * The paper is part of the page, not of a shell: a ruled sheet that exported blank on the PC while the
     * phone ruled it is what a geometry owned by one emitter produces. The fill comes first and every rule
     * after it, at the sheet's own spacing and widths.
     */
    @Test
    fun aRuledPagesPlanIsItsSheetAndOnePathPerRule() {
        val ruled = page().copy(background = Background.Solid(0xFFFFFFFF.toInt(), "ruled"))

        val paper = PdfPagePlan.paper(ruled, transform)

        val sheet = paper.first()
        assertTrue("the sheet is one filled outline, and it comes first", sheet is PdfFill)
        sheet as PdfFill
        assertEquals("the page's four corners", 4, sheet.points.size)
        assertEquals("flooded opaque white", 0xFFFFFFFF.toInt(), sheet.colour)
        assertEquals(false, sheet.multiply)
        val rules = paper.drop(1).filterIsInstance<PdfPath>()
        assertEquals("one path per rule and nothing else", paper.size - 1, rules.size)
        // 842 pt of sheet at the default 24 pt spacing: the interior rules the screen draws.
        assertEquals(
            "the sheet's own spacing, not a number of the emitter's",
            BackgroundGrid.lines(ruled.height, BackgroundRulings.defaultSpacingPt("ruled")).size + 1,
            rules.size,
        )
        val body = rules.dropLast(1)
        assertTrue(
            "each rule spans the page, at the paper's own colour",
            body.all { it.points.size == 2 && it.colour == (0xFF000000.toInt() or BackgroundGrid.LINED_RGB) },
        )
        assertEquals(
            "and a ruled sheet closes with its margin line",
            0xFF000000.toInt() or BackgroundGrid.MARGIN_RGB,
            rules.last().colour,
        )
        assertTrue("every rule has a width to stroke with", rules.all { it.width > 0.0 })
    }

    /** A plain sheet is its fill alone — no rules invented for a page that carries none. */
    @Test
    fun aPlainPagesPlanIsOnlyItsSheet() {
        val paper = PdfPagePlan.paper(page(), transform)

        assertEquals("one op: the sheet", 1, paper.size)
        assertTrue(paper.single() is PdfFill)
    }
}

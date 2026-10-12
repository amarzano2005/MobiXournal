package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversion from the graph dialog's answer to a **living plot** (`docs/mobix-elements.md`).
 *
 * The property that matters is that the graph a user inserts is the graph they were shown before the
 * insertion started keeping the recipe: the box is the one the ink insertion has always computed, and
 * the element draws as the plane ink that insertion used to append. So the tests compare against the
 * historical arithmetic and against [MultiFunctionPlotter] itself — not against a snapshot, which
 * would happily encode a regression — and one of them follows the element through the `.mobix` codec,
 * since a recipe that does not survive a save is not a recipe.
 */
class PlotElementFactoryTest {

    private val red = 0xFFD32F2F.toInt()
    private val pen = 0xFF1A237E.toInt()

    private fun page(width: Double = 800.0, height: Double = 1000.0) = Page(
        width = width,
        height = height,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(emptyList())),
    )

    private fun curves() = listOf(PlotCurve("sin(x)", red), PlotCurve("cos(x)", 0xFF388E3C.toInt()))

    private fun element(
        page: Page = page(),
        centreX: Double = 400.0,
        centreY: Double = 500.0,
        includeSummary: Boolean = false,
    ) = PlotElementFactory.plotElementFor(
        curves = curves(),
        xMin = -6.28,
        xMax = 6.28,
        centreX = centreX,
        centreY = centreY,
        page = page,
        showCriticalPoints = true,
        includeDerivative = false,
        strokeWidthPt = 1.5,
        axisColor = pen,
        includeSummary = includeSummary,
    )

    @Test
    fun theBoxIsTheOneTheInkInsertionHasAlwaysComputed() {
        val page = page()
        // The historical arithmetic, spelled out here rather than shared with the factory: if the two
        // ever disagree, every graph already on every page moves, and this is the assertion that says so.
        val expectedWidth = (page.width * 0.45).coerceIn(240.0, 320.0)
        val expectedHeight = expectedWidth * 0.6
        val expectedLeft = (400.0 - expectedWidth / 2).coerceIn(0.0, page.width - expectedWidth)
        val expectedTop = (500.0 - expectedHeight / 2).coerceIn(0.0, page.height - expectedHeight)

        val plot = element(page = page)

        assertEquals(expectedLeft, plot.left, 1e-9)
        assertEquals(expectedTop, plot.top, 1e-9)
        assertEquals(expectedLeft + expectedWidth, plot.right, 1e-9)
        assertEquals(expectedTop + expectedHeight, plot.bottom, 1e-9)
        assertEquals(320.0, plot.right - plot.left, 1e-9)
        assertEquals("and the placement is what the box is made of, not the domain", -6.28, plot.xMin, 0.0)
        assertEquals(6.28, plot.xMax, 0.0)
    }

    @Test
    fun aNarrowPageClampsThePlotUpToItsMinimumWidth() {
        val page = page(width = 400.0, height = 600.0)

        val plot = element(page = page, centreX = 200.0, centreY = 300.0)

        assertEquals("400 * 0.45 would be 180, under the floor", 240.0, plot.right - plot.left, 1e-9)
        assertEquals(144.0, plot.bottom - plot.top, 1e-9)
        assertEquals(80.0, plot.left, 1e-9)
        assertEquals(228.0, plot.top, 1e-9)
    }

    @Test
    fun aPlotInsertedAtThePagesEdgeStaysInsideIt() {
        val page = page(width = 400.0, height = 600.0)

        val atOrigin = element(page = page, centreX = 0.0, centreY = 0.0)
        val past = element(page = page, centreX = 10_000.0, centreY = 10_000.0)

        assertEquals(0.0, atOrigin.left, 0.0)
        assertEquals(0.0, atOrigin.top, 0.0)
        assertEquals("the far edge is the page's own, not a centre outside it", 160.0, past.left, 1e-9)
        assertEquals(456.0, past.top, 1e-9)
    }

    @Test
    fun theElementsDrawingIsTheInkTheInsertionUsedToBake() {
        val plot = element()
        val plotted = MultiFunctionPlotter.plot(
            curves = curves(),
            xMin = plot.xMin,
            xMax = plot.xMax,
            leftPt = plot.left,
            topPt = plot.top,
            widthPt = plot.right - plot.left,
            heightPt = plot.bottom - plot.top,
            strokeWidthPt = 1.5,
            samples = PlotElement.DEFAULT_SAMPLES,
            showCriticalPoints = true,
            includeDerivative = false,
            includeSummary = false,
            axisColor = pen,
        )

        val ink = LivingInk.of(plot)

        assertTrue("nothing was lost in the conversion", ink.uncompiled.isEmpty())
        val inkStrokes = ink.elements.filterIsInstance<Stroke>()
        val expected = plotted.pathGroups.flatMap { it.strokes }
        assertEquals("every frame stroke, tick, curve and marker is there", expected.size, inkStrokes.size)
        expected.forEachIndexed { index, points ->
            assertEquals("stroke $index is the plotter's own geometry", points, inkStrokes[index].points)
        }
        assertEquals(
            "and so are the tick numbers",
            plotted.labels.map { it.text },
            ink.elements.filterIsInstance<TextElement>().map { it.content },
        )
        assertEquals("drawn in the pen's colour, as the inserted ink was", pen, inkStrokes.first().color)
    }

    @Test
    fun theSummaryTheDialogAskedForIsKeptWithTheElement() {
        val asked = element(includeSummary = true)
        val unasked = element(includeSummary = false)

        assertNotNull("the analysis the user asked for is in the element", asked.summaryText)
        assertNull("and is not invented when the dialog did not ask", unasked.summaryText)
        assertEquals(
            "it is the engine's own report, not a second copy that could disagree with the curve",
            MultiFunctionPlotter.plot(
                curves = curves(),
                xMin = asked.xMin,
                xMax = asked.xMax,
                leftPt = asked.left,
                topPt = asked.top,
                widthPt = asked.right - asked.left,
                heightPt = asked.bottom - asked.top,
                strokeWidthPt = 1.5,
                samples = PlotElement.DEFAULT_SAMPLES,
                showCriticalPoints = true,
                includeDerivative = false,
                includeSummary = true,
                axisColor = pen,
            ).summaryText,
            asked.summaryText,
        )
    }

    @Test
    fun aPlotSurvivesTheMobixCodecWholeAndUnidentifiedUntilItIsSaved() {
        val plot = element(includeSummary = true)

        assertNull("the codec mints the identity, not the factory", plot.id)
        val decoded = MobixElementCodec.decode(
            MobixElementCodec.encode(plot, MobixAssetSink()),
            MobixAssetSource { null },
        ) as PlotElement

        assertNotNull("a saved plot has an identity", decoded.id)
        assertEquals("every field but the minted id comes back as it went in", plot.copy(id = decoded.id), decoded)
    }

    @Test
    fun theElementIsDrawnAsItsInkAndNeverAsItself() {
        val plot = element()

        // What LivingInk hands back is the compiled ink: the plot element never appears in it, which is
        // what keeps the canvas from recursing into a plot of a plot.
        assertTrue(LivingInk.of(plot).elements.none { it is PlotElement })
        assertTrue("and there is more than a frame in it", LivingInk.of(plot).elements.size > 1)
    }
}

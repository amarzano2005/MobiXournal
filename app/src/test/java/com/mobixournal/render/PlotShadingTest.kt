package com.mobixournal.render

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotShading
import com.mobixournal.format.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The **shaded integral area** (`docs/math-engine.md`, section 3): the region a shaded plot stands for,
 * compiled once in `PlotInk` so the canvas, SVG, PDF and the `.xopp` synthesis cannot disagree about it.
 */
class PlotShadingTest {

    private val red = 0xFFD32F2F.toInt()

    private fun plot(
        curves: List<PlotCurve>,
        xMin: Double = -3.0,
        xMax: Double = 3.0,
        shading: PlotShading? = null,
        samples: Int = 120,
    ) = PlotElement(
        left = 100.0, top = 200.0, right = 400.0, bottom = 380.0,
        curves = curves,
        xMin = xMin, xMax = xMax, samples = samples,
        shadeIntegral = shading,
        id = "plt_1",
    )

    private fun cartesian(source: String = "sin(x)") = PlotCurve(PlotKind.CARTESIAN, source, red, strokeWidth = 1.5)

    private fun fillsOf(element: PlotElement): List<Stroke> =
        PlotInk.ink(element).elements.filterIsInstance<Stroke>().filter { it.fill != null }

    /** The frame the plot was drawn in, asked for the same way `PlotInk` asks for it. */
    private fun frameOf(element: PlotElement): PlotFrame {
        val result = MultiFunctionPlotter.plot(
            curves = element.curves.map { PlotCurve(it.source, it.color, it.lineStyle) },
            xMin = element.xMin,
            xMax = element.xMax,
            leftPt = element.left,
            topPt = element.top,
            widthPt = element.right - element.left,
            heightPt = element.bottom - element.top,
            strokeWidthPt = 1.5,
            samples = element.samples,
            axisColor = element.axisColor,
            parameters = element.parameters,
            includeSummary = false,
        )
        return result.frame!!
    }

    @Test
    fun `a shaded plot yields a closed filled polygon spanning the integral bounds`() {
        val element = plot(listOf(cartesian()), shading = PlotShading(from = -1.0, to = 2.0))
        val frame = frameOf(element)

        val fill = fillsOf(element).single()

        assertEquals("the wash carries the curve's own colour", red, fill.color)
        assertEquals("the wash uses the canvas' translucent-fill token", CanvasChrome.BAND_FILL_ALPHA, fill.fill)
        assertEquals("the polygon is closed", fill.points.first(), fill.points.last())
        assertTrue("every vertex is on the page", fill.points.all { it.x.isFinite() && it.y.isFinite() })
        // The polygon is clipped to the element's own integral bounds: its x extent is exactly -1..2.
        assertEquals(frame.toX(-1.0), fill.points.minOf { it.x }, 1e-6)
        assertEquals(frame.toX(2.0), fill.points.maxOf { it.x }, 1e-6)
        // ... and it is bounded below by the x-axis, which is what makes it the *area under* the curve.
        val axisY = frame.toY(0.0)
        assertTrue(
            "the polygon reaches down to the axis",
            fill.points.any { abs(it.y - axisY) < 1e-6 },
        )
    }

    @Test
    fun `an unshaded plot yields no fill`() {
        val element = plot(listOf(cartesian()), shading = null)

        assertTrue("nothing is washed when the element asks for no shading", fillsOf(element).isEmpty())
        assertTrue("the plot still draws its curve", PlotInk.ink(element).elements.isNotEmpty())
    }

    @Test
    fun `a shaded two-curve plot fills the region between the curves`() {
        val element = plot(listOf(cartesian("sin(x)"), cartesian("cos(x)")), shading = PlotShading(0.0, 2.0))

        val fill = fillsOf(element).single()

        assertEquals("the polygon is closed", fill.points.first(), fill.points.last())
        // It runs along one curve and back along the other: both sampled runs are in the outline, at the
        // samples the plot itself used.
        assertEquals("the outline is the two runs plus the closing point", 2 * (element.samples + 1) + 1, fill.points.size)
    }

    @Test
    fun `a shading outside the plot's own domain draws nothing`() {
        val element = plot(listOf(cartesian()), xMin = -3.0, xMax = 3.0, shading = PlotShading(10.0, 20.0))

        assertTrue("a range that never meets the domain is not an area of this plot", fillsOf(element).isEmpty())
    }

    @Test
    fun `a shading is clipped to the domain it overlaps`() {
        val element = plot(listOf(cartesian()), xMin = -1.0, xMax = 1.0, shading = PlotShading(0.0, 5.0))
        val frame = frameOf(element)

        val fill = fillsOf(element).single()

        assertEquals(frame.toX(1.0), fill.points.maxOf { it.x }, 1e-6)
        assertEquals(frame.toX(0.0), fill.points.minOf { it.x }, 1e-6)
    }

    @Test
    fun `a curve that does not parse draws no area rather than a wrong one`() {
        val element = plot(
            listOf(cartesian("sin(x)"), cartesian("sin(x +++")),
            shading = PlotShading(0.0, 1.0),
        )

        assertTrue("half a two-curve region is not the region", fillsOf(element).isEmpty())
    }

    @Test
    fun `a frame without the x-axis in it draws nothing to shade against`() {
        // x^2 + 5 is fitted to its own range, so y = 0 is far below the frame and the axis is not drawn.
        val element = plot(listOf(cartesian("x^2 + 5")), xMin = -2.0, xMax = 2.0, shading = PlotShading(0.0, 2.0))

        assertTrue("there is no axis inside the frame", !frameOf(element).containsXAxis)
        assertTrue("so there is no area between the curve and it", fillsOf(element).isEmpty())
    }

    @Test
    fun `an asymptote inside the bounds draws nothing rather than a filled break`() {
        // 1/x leaves the plane at x = 0, which the sample grid lands on exactly.
        val element = plot(listOf(cartesian("1/x")), xMin = -1.0, xMax = 1.0, samples = 100, shading = PlotShading(-1.0, 1.0))

        assertTrue("a break is not a region", fillsOf(element).isEmpty())
    }

    @Test
    fun `a shaded plot survives the mobix codec and exports to xopp with its fill`() {
        val original = plot(listOf(cartesian()), shading = PlotShading(from = -1.0, to = 2.0))
        val sink = MobixAssetSink()
        val byName = sink.entries().associate { it.name to it.bytes }
        val source = MobixAssetSource { byName[it] }

        val back = MobixElementCodec.decode(MobixElementCodec.encode(original, sink), source)

        assertTrue("the shaded plot is modelled, not preserved raw", back is PlotElement)
        assertEquals(PlotShading(-1.0, 2.0), (back as PlotElement).shadeIntegral)
        assertTrue("the decoded plot still compiles to its wash", fillsOf(back).isNotEmpty())

        val result = XoppSynthesis.synthesize(documentWith(back))
        val written = result.document.pages[0].layers[0].elements.filterIsInstance<Stroke>()
        assertNotNull(
            "the area reaches the .xopp writer as a fill, not as a plain outline",
            written.firstOrNull { it.fill == CanvasChrome.BAND_FILL_ALPHA },
        )
    }

    @Test
    fun `the wash reaches the SVG export as a filled polygon`() {
        val element = plot(listOf(cartesian()), shading = PlotShading(from = -1.0, to = 2.0))

        val svg = PageSvgWriter.write(
            Page(
                width = 595.27559100,
                height = 841.88976400,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(element), name = "ink")),
                id = "pg_1",
            ),
        )

        assertTrue(
            "the SVG export fills what the screen fills, in the curve's own colour",
            svg.contains("<polygon fill=\"#D32F2F\" fill-opacity="),
        )
    }

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
}

package com.mobixournal.render

import com.mobixournal.format.model.PlotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The formula parser, the sampling, and the geometry a plot ends up as. */
class FunctionPlotTest {

    private fun value(source: String, x: Double): Double =
        FunctionPlot.compile(source)!!.at(x)

    @Test
    fun `arithmetic follows the usual precedence`() {
        assertEquals(7.0, value("1+2*3", 0.0), 1e-9)
        assertEquals(9.0, value("(1+2)*3", 0.0), 1e-9)
        assertEquals(2.0, value("8/4", 0.0), 1e-9)
        assertEquals(-5.0, value("-5", 0.0), 1e-9)
        assertEquals(1.0, value("2-1", 0.0), 1e-9)
        // ^ binds tighter than *, and is right-associative.
        assertEquals(18.0, value("2*3^2", 0.0), 1e-9)
        assertEquals(512.0, value("2^3^2", 0.0), 1e-9)
    }

    @Test
    fun `the variable, the constants and the functions all resolve`() {
        assertEquals(3.0, value("x", 3.0), 1e-9)
        assertEquals(3.0, value("X", 3.0), 1e-9)
        assertEquals(0.0, value("sin(pi)", 0.0), 1e-9)
        assertEquals(1.0, value("ln(e)", 0.0), 1e-9)
        assertEquals(2.0, value("log(100)", 0.0), 1e-9)
        assertEquals(3.0, value("sqrt(9)", 0.0), 1e-9)
        assertEquals(3.0, value("abs(-3)", 0.0), 1e-9)
        // A function's argument can be a power, and whitespace is free.
        assertEquals(2.0, value(" sqrt ( 4 ) ", 0.0), 1e-9)
    }

    @Test
    fun `implicit multiplication and flexible mathematical expressions resolve`() {
        assertEquals(8.0, value("2x^2", 2.0), 1e-9)
        assertEquals(18.0, value("2x²", 3.0), 1e-9)
        assertEquals(8.0, value("2(x+1)", 3.0), 1e-9)
        assertEquals(15.0, value("(x+1)(x-1)", 4.0), 1e-9)
        assertEquals(6.0, value("2 3", 0.0), 1e-9)
        assertEquals(0.002, value("2e-3", 0.0), 1e-9)
        assertEquals(0.0, value("sin(π)", 0.0), 1e-9)
        assertEquals(0.0, value("tg(0)", 0.0), 1e-9)
        assertEquals(6.0, value("2 × 3", 0.0), 1e-9)
        assertEquals(2.0, value("4 ÷ 2", 0.0), 1e-9)
        assertEquals(3.0, value("5 − 2", 0.0), 1e-9)
    }

    @Test
    fun `a formula that isn't one compiles to null instead of throwing`() {
        assertNull(FunctionPlot.compile(""))
        assertNull(FunctionPlot.compile("2*"))
        assertNull(FunctionPlot.compile("(1+2"))
        assertNull(FunctionPlot.compile("foo(x)"))
        assertNull(FunctionPlot.compile("1+2)"))
        assertNotNull(FunctionPlot.compile("2*x+1"))
        assertNotNull(FunctionPlot.compile("2x+1"))
    }

    @Test
    fun `sample walks the range and keeps NaN for undefined values`() {
        val sqrt = FunctionPlot.compile("sqrt(x)")!!
        val points = FunctionPlot.sample(sqrt, -1.0, 1.0, 4)
        assertEquals(5, points.size)
        assertEquals(-1.0, points.first().first, 1e-9)
        assertEquals(1.0, points.last().first, 1e-9)
        assertTrue("the negative half is undefined", points.first().second.isNaN())
        assertEquals(1.0, points.last().second, 1e-9)
    }

    @Test
    fun `the tick step lands on one two or five times a power of ten`() {
        assertEquals(1.0, FunctionPlot.niceStep(8.0, 8), 1e-12)
        assertEquals(2.0, FunctionPlot.niceStep(16.0, 8), 1e-12)
        assertEquals(5.0, FunctionPlot.niceStep(40.0, 8), 1e-12)
        assertEquals(0.5, FunctionPlot.niceStep(4.0, 8), 1e-12)
        assertEquals(0.0, FunctionPlot.niceStep(0.0, 8), 1e-12)
    }

    @Test
    fun `a tick label is short and readable`() {
        assertEquals("0", FunctionPlot.label(0.0, 1.0))
        assertEquals("-3", FunctionPlot.label(-3.0, 1.0))
        assertEquals("2.5", FunctionPlot.label(2.5, 0.5))
        assertEquals("0.25", FunctionPlot.label(0.25, 0.25))
    }

    @Test
    fun `a sine plot is a frame some tick marks and one continuous curve`() {
        val plot = FunctionPlot.plot(
            source = "sin(x)", xMin = -6.0, xMax = 6.0,
            leftPt = 100.0, topPt = 100.0, widthPt = 300.0, heightPt = 200.0,
            strokeWidthPt = 1.0,
        )
        assertTrue("something was plotted", plot.strokes.isNotEmpty())
        // Every point lies inside the plot box (the tick labels sit outside it, but not the lines).
        for (stroke in plot.strokes) {
            for (point in stroke) {
                assertTrue("x in the box", point.x >= 99.99 && point.x <= 400.01)
                assertTrue("y in the box", point.y >= 99.99 && point.y <= 300.01)
            }
        }
        // A sine is continuous, so exactly one long polyline carries it.
        val longest = plot.strokes.maxByOrNull { it.size }!!
        assertTrue("the curve is finely sampled", longest.size > 500)
        // Axis numbers are there, and they are readable.
        assertTrue(plot.labels.isNotEmpty())
        assertTrue(plot.labels.all { it.text.isNotEmpty() })
    }

    @Test
    fun `a pole breaks the curve instead of drawing across it`() {
        // 1/x jumps from -infinity to +infinity at zero: the plot must come back as two branches.
        val plot = FunctionPlot.plot(
            source = "1/x", xMin = -2.0, xMax = 2.0,
            leftPt = 0.0, topPt = 0.0, widthPt = 200.0, heightPt = 200.0,
            strokeWidthPt = 1.0,
        )
        // A curve is a long polyline; the frame is 5 points and an axis or tick is 2.
        val curve = plot.strokes.filter { it.size > 5 }
        assertEquals("two branches", 2, curve.size)
    }

    @Test
    fun `piecewise formulas compile and evaluate across domains`() {
        val f = FunctionPlot.compile("x < 0: -x; x >= 0: x^2")
        assertNotNull(f)
        assertEquals(3.0, f!!.at(-3.0), 1e-9)
        assertEquals(0.0, f.at(0.0), 1e-9)
        assertEquals(9.0, f.at(3.0), 1e-9)
    }

    @Test
    fun `parametric curve sampling generates expected points`() {
        val fx = FunctionPlot.compile("cos(x)")!!
        val fy = FunctionPlot.compile("sin(x)")!!
        val samples = AdvancedCurves.sampleParametric(fx, fy, 0.0, 2 * Math.PI, 100)
        assertEquals(101, samples.size)
        assertEquals(1.0, samples.first().first, 1e-3)
        assertEquals(0.0, samples.first().second, 1e-3)
    }

    @Test
    fun `critical point analysis finds roots and extrema`() {
        // f(x) = x^2 - 4 has roots at -2 and +2, minimum at (0, -4), y-intercept at -4
        val formula = FunctionPlot.compile("x^2 - 4")!!
        val points = FunctionAnalysis.findCriticalPoints(formula, -3.0, 3.0, 100)
        val roots = points.filter { it.type == CriticalPointType.ZERO }
        assertEquals(2, roots.size)
        assertTrue(roots.any { Math.abs(it.x - -2.0) < 0.05 })
        assertTrue(roots.any { Math.abs(it.x - 2.0) < 0.05 })

        val mins = points.filter { it.type == CriticalPointType.LOCAL_MIN }
        assertEquals(1, mins.size)
        assertEquals(0.0, mins.first().x, 0.05)
        assertEquals(-4.0, mins.first().y, 0.05)
    }

    @Test
    fun `a curve the plotter cannot place is refused instead of drawn as a wave`() {
        // `cos(x)` is a wave as a Cartesian curve and the x of a circle as a parametric pair. This
        // plotter places `y = f(x)` only, so the pair is *not* its picture to draw: the sampled loop
        // comes from `AdvancedCurves` through the plot compiler, and a wave here would be a different
        // drawing presented as the requested one.
        val circular = PlotCurve(
            source = "cos(x)",
            color = 0xFF0000FF.toInt(),
            kind = PlotKind.PARAMETRIC,
            sourceY = "sin(x)",
        )
        val ordinary = PlotCurve("x^2", 0xFFFF0000.toInt())

        val onlyParametric = MultiFunctionPlotter.plot(
            curves = listOf(circular), xMin = -3.0, xMax = 3.0,
            leftPt = 0.0, topPt = 0.0, widthPt = 200.0, heightPt = 200.0, strokeWidthPt = 1.0,
        )
        val mixed = MultiFunctionPlotter.plot(
            curves = listOf(circular, ordinary), xMin = -3.0, xMax = 3.0,
            leftPt = 0.0, topPt = 0.0, widthPt = 200.0, heightPt = 200.0, strokeWidthPt = 1.0,
        )

        assertTrue("nothing is invented for a kind it cannot place", onlyParametric.pathGroups.isEmpty())
        // The frame is a group of its own, so the mixed plot is: axes, then the one curve it can place.
        assertEquals("the frame and the Cartesian curve beside it", 2, mixed.pathGroups.size)
        assertEquals("and the curve group is that curve's own", ordinary.color, mixed.pathGroups[1].color)
    }

    @Test
    fun `multi function plotter produces distinct stroke groups`() {
        val c1 = PlotCurve("x^2", 0xFF0000FF.toInt())
        val c2 = PlotCurve("x + 2", 0xFFFF0000.toInt())
        val result = MultiFunctionPlotter.plot(
            curves = listOf(c1, c2),
            xMin = -3.0,
            xMax = 3.0,
            leftPt = 0.0,
            topPt = 0.0,
            widthPt = 200.0,
            heightPt = 200.0,
            strokeWidthPt = 1.0,
            showCriticalPoints = true,
            includeSummary = true,
        )
        // Group 0: axis/frame, Group 1: c1, Group 2: c2, Group 3: markers
        assertTrue(result.pathGroups.size >= 3)
        assertTrue(result.criticalPoints.isNotEmpty())
        assertTrue(result.summaryText.contains("Function Study"))
    }
}

package com.mobixournal.render

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
    fun `a formula that isn't one compiles to null instead of throwing`() {
        assertNull(FunctionPlot.compile(""))
        assertNull(FunctionPlot.compile("2*"))
        assertNull(FunctionPlot.compile("(1+2"))
        assertNull(FunctionPlot.compile("foo(x)"))
        assertNull(FunctionPlot.compile("2x")) // implicit product is a typo, not a formula
        assertNull(FunctionPlot.compile("1+2)"))
        assertNotNull(FunctionPlot.compile("2*x+1"))
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
    fun `an impossible plot inserts nothing`() {
        val empty = FunctionPlot.plot(
            source = "sqrt(-1-x^2)", xMin = -1.0, xMax = 1.0,
            leftPt = 0.0, topPt = 0.0, widthPt = 100.0, heightPt = 100.0,
            strokeWidthPt = 1.0,
        )
        assertTrue(empty.strokes.isEmpty())
        assertTrue(empty.labels.isEmpty())
        assertTrue(
            FunctionPlot.plot("nonsense", -1.0, 1.0, 0.0, 0.0, 100.0, 100.0, 1.0).strokes.isEmpty(),
        )
    }
}

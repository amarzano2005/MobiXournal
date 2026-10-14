package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The definite integral `docs/math-engine.md` promises (section 3): Adaptive Simpson at 1e-7, a value
 * reported with the rest of the analysis, and a non-finite integrand refused instead of returning NaN.
 */
class FunctionAnalysisIntegralTest {

    private fun formula(source: String): Formula = FunctionPlot.compile(source)!!

    private fun integral(source: String, a: Double, b: Double): Double? =
        FunctionAnalysis.definiteIntegral(formula(source), a, b)

    @Test
    fun `a polynomial integrates exactly`() {
        // int_0^1 x dx = 1/2, which is what a parabola through the endpoints gets exactly.
        assertEquals(0.5, integral("x", 0.0, 1.0)!!, 1e-9)
    }

    @Test
    fun `a transcendental integrand converges to the analytic value`() {
        // int_0^pi sin x dx = 2.
        assertEquals(2.0, integral("sin(x)", 0.0, Math.PI)!!, 1e-6)
    }

    @Test
    fun `integrating the other way round negates the value`() {
        assertEquals(-0.5, integral("x", 1.0, 0.0)!!, 1e-9)
    }

    @Test
    fun `an empty interval integrates to zero`() {
        assertEquals(0.0, integral("x", 2.0, 2.0)!!, 1e-12)
    }

    @Test
    fun `a non-finite integrand is refused rather than returned as NaN`() {
        // sqrt(x) is not real below zero: the prescan sees it and refuses the whole interval.
        assertNull(integral("sqrt(x)", -1.0, 1.0))
    }

    @Test
    fun `a discontinuous integrand is refused rather than returned as NaN`() {
        // 1/x blows up at the midpoint of [-1, 1]: Simpson's own sample lands on the pole.
        assertNull(integral("1/x", -1.0, 1.0))
    }

    @Test
    fun `an unbounded bound is refused`() {
        assertNull(integral("x", 0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `the plot analysis reports the integral of each curve`() {
        val result = MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve("x", color = 0xFF0000FF.toInt(), lineStyle = LineStyle.PLAIN, label = "f")),
            xMin = 0.0,
            xMax = 1.0,
            leftPt = 0.0,
            topPt = 0.0,
            widthPt = 100.0,
            heightPt = 100.0,
            strokeWidthPt = 1.0,
            includeSummary = true,
        )
        assertTrue(
            "summary should carry the integral, was:\n${result.summaryText}",
            result.summaryText.contains("∫ f dx = 0.5"),
        )
    }

    @Test
    fun `the summary names an integral it could not evaluate instead of printing a number`() {
        val result = MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve("1/x", color = 0xFF0000FF.toInt(), lineStyle = LineStyle.PLAIN, label = "g")),
            xMin = -1.0,
            xMax = 1.0,
            leftPt = 0.0,
            topPt = 0.0,
            widthPt = 100.0,
            heightPt = 100.0,
            strokeWidthPt = 1.0,
            includeSummary = true,
        )
        assertTrue(
            "summary should refuse rather than print a number, was:\n${result.summaryText}",
            result.summaryText.contains("∫ g dx: not finite over the range"),
        )
    }

    @Test
    fun `a summary that was not asked for stays empty`() {
        val result = MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve("x", color = 0xFF0000FF.toInt(), lineStyle = LineStyle.PLAIN, label = "f")),
            xMin = 0.0,
            xMax = 1.0,
            leftPt = 0.0,
            topPt = 0.0,
            widthPt = 100.0,
            heightPt = 100.0,
            strokeWidthPt = 1.0,
            includeSummary = false,
        )
        assertEquals("", result.summaryText)
    }
}

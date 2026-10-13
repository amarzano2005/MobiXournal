package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotCurve as ModelPlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for interactive plot parameter sliders:
 * - Parsing and evaluation with named parameters (e.g. f(x) = a*sin(b*x+c))
 * - Dynamic curve re-evaluation when parameters change
 * - Mathematical analysis and critical points re-evaluation through FunctionAnalysis
 * - summaryText cache refresh via PlotElementFactory
 * - Slider track geometry, hit testing, and touch value mapping
 */
class PlotParameterSlidersTest {

    @Test
    fun `formula with named parameters compiles and evaluates correctly`() {
        val params = mapOf("a" to 2.5, "b" to 3.0, "c" to 1.0)
        val formula = FunctionPlot.compile("a*sin(b*x + c)", params)
        assertNotNull("Formula with parameters must compile", formula)

        val expected = 2.5 * kotlin.math.sin(3.0 * 0.5 + 1.0)
        assertEquals(expected, formula!!.at(0.5), 1e-9)
    }

    @Test
    fun `changing a parameter changes the sampled curve`() {
        val f1 = FunctionPlot.compile("a*x^2", mapOf("a" to 1.0))!!
        val f2 = FunctionPlot.compile("a*x^2", mapOf("a" to 3.0))!!

        val samples1 = FunctionPlot.sample(f1, -2.0, 2.0, 100)
        val samples2 = FunctionPlot.sample(f2, -2.0, 2.0, 100)

        assertEquals(101, samples1.size)
        assertEquals(101, samples2.size)

        // At x = 2: 1 * 4 = 4 vs 3 * 4 = 12
        val last1 = samples1.last().second
        val last2 = samples2.last().second
        assertEquals(4.0, last1, 1e-9)
        assertEquals(12.0, last2, 1e-9)
    }

    @Test
    fun `changing parameter shifts roots and extrema in analysis report`() {
        // f(x) = x^2 - c
        val plot1 = MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve("x^2 - c", color = 0xFF000000.toInt())),
            xMin = -5.0,
            xMax = 5.0,
            leftPt = 0.0,
            topPt = 0.0,
            widthPt = 300.0,
            heightPt = 200.0,
            strokeWidthPt = 1.0,
            showCriticalPoints = true,
            includeSummary = true,
            parameters = mapOf("c" to 4.0),
        )

        val roots1 = plot1.criticalPoints.filter { it.type == CriticalPointType.ZERO }
        assertEquals("Roots of x^2 - 4 are at -2 and 2", 2, roots1.size)
        assertTrue(roots1.any { kotlin.math.abs(it.x - (-2.0)) < 1e-3 })
        assertTrue(roots1.any { kotlin.math.abs(it.x - 2.0) < 1e-3 })
        assertTrue("Summary report mentions roots near 2", plot1.summaryText.contains("2"))

        val plot2 = MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve("x^2 - c", color = 0xFF000000.toInt())),
            xMin = -5.0,
            xMax = 5.0,
            leftPt = 0.0,
            topPt = 0.0,
            widthPt = 300.0,
            heightPt = 200.0,
            strokeWidthPt = 1.0,
            showCriticalPoints = true,
            includeSummary = true,
            parameters = mapOf("c" to 9.0),
        )

        val roots2 = plot2.criticalPoints.filter { it.type == CriticalPointType.ZERO }
        assertEquals("Roots of x^2 - 9 are at -3 and 3", 2, roots2.size)
        assertTrue(roots2.any { kotlin.math.abs(it.x - (-3.0)) < 1e-3 })
        assertTrue(roots2.any { kotlin.math.abs(it.x - 3.0) < 1e-3 })
        assertTrue("Summary report mentions roots near 3", plot2.summaryText.contains("3"))
    }

    @Test
    fun `PlotElementFactory applyParameters updates parameters and refreshes summaryText`() {
        val initialElement = PlotElement(
            left = 100.0,
            top = 100.0,
            right = 400.0,
            bottom = 300.0,
            curves = listOf(
                ModelPlotCurve(
                    kind = PlotKind.CARTESIAN,
                    source = "x^2 - c",
                    color = 0xFF112233.toInt(),
                    strokeWidth = 1.0,
                    lineStyle = LineStyle.PLAIN,
                ),
            ),
            xMin = -5.0,
            xMax = 5.0,
            showCriticalPoints = true,
            parameters = mapOf("c" to 4.0),
            summaryText = "Initial summary with root 2.0",
        )

        val updated = PlotElementFactory.applyParameters(initialElement, mapOf("c" to 9.0))

        assertEquals(9.0, updated.parameters["c"])
        assertNotNull(updated.summaryText)
        assertTrue("Updated summary must reflect new parameter c=9", updated.summaryText!!.contains("3"))
    }

    @Test
    fun `PlotElementFactory updateParameter modifies a single parameter and keeps others`() {
        val initialElement = PlotElement(
            left = 50.0,
            top = 50.0,
            right = 350.0,
            bottom = 250.0,
            curves = listOf(
                ModelPlotCurve(
                    kind = PlotKind.CARTESIAN,
                    source = "a*x + b",
                    color = 0xFF000000.toInt(),
                ),
            ),
            parameters = mapOf("a" to 2.0, "b" to 5.0),
        )

        val updated = PlotElementFactory.updateParameter(initialElement, "a", 4.0)

        assertEquals(4.0, updated.parameters["a"])
        assertEquals(5.0, updated.parameters["b"])
    }

    @Test
    fun `PlotSliders computeSliderTracks creates hit-testable tracks and interpolates values`() {
        val plot = PlotElement(
            left = 100.0,
            top = 100.0,
            right = 400.0,
            bottom = 300.0,
            curves = listOf(
                ModelPlotCurve(kind = PlotKind.CARTESIAN, source = "a*sin(b*x)", color = 0),
            ),
            parameters = mapOf("a" to 2.0, "b" to -4.0),
        )

        val page = Page(width = 800.0, height = 1000.0, background = Background.Solid(0, "plain"), layers = listOf(Layer(emptyList())))
        val box = PageBox(index = 0, topPx = 50f, leftPx = 50f, heightPx = 1000f, scale = 1.0f, page = page)

        val tracks = PlotSliders.computeSliderTracks(plot, box, scrollX = 0f, scrollY = 0f)

        assertEquals(2, tracks.size)
        val trackA = tracks.first { it.paramName == "a" }
        val trackB = tracks.first { it.paramName == "b" }

        assertEquals(2.0, trackA.value, 1e-9)
        assertEquals(-4.0, trackB.value, 1e-9)

        // Hit testing
        assertTrue(trackA.hitTest(trackA.trackStart + 20f, trackA.centerY))
        assertFalse(trackA.hitTest(trackA.trackStart - 100f, trackA.centerY))

        // Value interpolation
        val midX = (trackA.trackStart + trackA.trackEnd) / 2f
        val midVal = trackA.valueForTouch(midX)
        val expectedMid = (trackA.minVal + trackA.maxVal) / 2.0
        assertEquals(expectedMid, midVal, 0.1)
    }
}

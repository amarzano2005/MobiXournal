package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **marker key** under a plot: a marked point is drawn with the glyph of its kind and the legend
 * names every kind the plot actually drew, so a dot never stands unexplained (`docs/function-plots.md`).
 *
 * The legend is placed outside the frame on purpose — the plot's label rule
 * (`LivingLabelClearanceTest`) keeps every writing off the graph it describes — and the summary below
 * it must not run through it.
 */
class PlotMarkerLegendTest {

    private fun analysis(showMarkers: Boolean = true, includeSummary: Boolean = false): MultiPlotResult =
        MultiFunctionPlotter.plot(
            curves = listOf(PlotCurve("sin(x)", color = 0xFF000000.toInt(), lineStyle = LineStyle.PLAIN, label = "f")),
            xMin = -Math.PI,
            xMax = Math.PI,
            leftPt = 100.0,
            topPt = 100.0,
            widthPt = 240.0,
            heightPt = 140.0,
            strokeWidthPt = 1.0,
            showCriticalPoints = showMarkers,
            includeSummary = includeSummary,
        )

    @Test
    fun `every kind of marked point gets its own glyph and colour`() {
        val points = listOf(
            CriticalPoint(CriticalPointType.ZERO, 0.0, 0.0, ""),
            CriticalPoint(CriticalPointType.Y_INTERCEPT, 0.0, 1.0, ""),
            CriticalPoint(CriticalPointType.LOCAL_MIN, 1.0, -1.0, ""),
            CriticalPoint(CriticalPointType.LOCAL_MAX, 2.0, 1.0, ""),
            CriticalPoint(CriticalPointType.INTERSECTION, 3.0, 0.0, ""),
        )

        val markers = FunctionAnalysis.generateMarkers(points, { it }, { it })

        assertEquals("one marker per point", points.size, markers.size)
        assertEquals("a different glyph per kind", points.size, markers.map { it.glyph }.toSet().size)
        assertEquals("a different colour per kind", points.size, points.map { FunctionAnalysis.markerColor(it.type) }.toSet().size)
    }

    @Test
    fun `a marked plot carries a legend that names every shape it draws`() {
        val result = analysis()
        val names = result.labels.map { it.text }

        assertTrue("the roots are keyed, was: $names", names.contains("root"))
        assertTrue("the extrema are keyed, was: $names", names.contains("min") && names.contains("max"))
        assertTrue("the intercept is keyed, was: $names", names.contains("y-intercept"))
        assertTrue("the legend takes height below the frame", result.legendHeightPt > 0.0)

        val legendLabels = result.labels.filter { it.text in setOf("root", "y-intercept", "min", "max") }
        assertTrue(
            "the key sits under the frame, not over the graph: $legendLabels",
            legendLabels.all { it.y > 240.0 },
        )

        val colours = result.pathGroups.map { it.color }
        assertTrue("the root glyph is drawn in the root colour", colours.contains(FunctionAnalysis.markerColor(CriticalPointType.ZERO)))
        assertTrue("and the extremum glyphs in theirs", colours.contains(FunctionAnalysis.markerColor(CriticalPointType.LOCAL_MAX)))
    }

    @Test
    fun `a plot with the markers switched off writes no legend`() {
        val result = analysis(showMarkers = false)

        assertEquals("no legend, no height", 0.0, result.legendHeightPt, 0.0)
        assertTrue(
            "and no key is invented for marks that were not drawn",
            result.labels.none { it.text in setOf("root", "y-intercept", "min", "max", "intersection") },
        )
    }

    @Test
    fun `the analysis summary is placed below the legend`() {
        val element = PlotElementFactory.plotElementIn(
            box = PlotBox(left = 100.0, top = 100.0, width = 240.0, height = 140.0),
            curves = listOf(PlotCurve("sin(x)", color = 0xFF000000.toInt(), label = "f")),
            xMin = -Math.PI,
            xMax = Math.PI,
            showCriticalPoints = true,
            includeSummary = true,
        )

        val texts = PlotInk.ink(element).elements.filterIsInstance<TextElement>()
        val legendBottom = texts.filter { it.content in setOf("root", "y-intercept", "min", "max") }
            .maxOfOrNull { it.y }
        val summary = texts.single { it.content.startsWith("Function Study") }

        assertTrue("the plot wrote a legend to clear", legendBottom != null)
        assertTrue(
            "the report starts below the key (summary y=${summary.y}, key y=$legendBottom)",
            summary.y > legendBottom!!,
        )
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CanvasAutoSlicer].
 */
class CanvasAutoSlicerTest {

    private fun makeStroke(yStart: Double, yEnd: Double): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(
            StrokePoint(100.0, yStart, width = 2.0),
            StrokePoint(100.0, yEnd, width = 2.0),
        ),
        uniformWidth = true,
    )

    @Test
    fun whitespaceSeamDetectionAvoidsSplittingStrokes() {
        // Nominal page height is 800 pt
        // A stroke sits from 790 to 820 pt, crossing the 800 pt cut line
        val spanningStroke = makeStroke(790.0, 820.0)
        // A stroke before it sits from 100 to 760 pt
        val upperStroke = makeStroke(100.0, 760.0)
        // Whitespace gap exists in [761..789] pt (within the 800 +/- 40 pt window)

        val cuts = CanvasAutoSlicer.findVerticalCuts(
            elements = listOf(upperStroke, spanningStroke),
            targetHeightPt = 800.0,
            totalHeightPt = 1600.0,
            startYPt = 0.0,
            windowPt = 40.0,
        )

        assertTrue(cuts.size >= 2)
        val cut1 = cuts[1]
        // Verify the cut is NOT inside the spanning stroke [790..820]
        assertFalse("Cut must not split stroke [790..820], got $cut1", cut1 > 790.0 && cut1 < 820.0)
        // Verify it shifted into whitespace within [760..790]
        assertTrue("Cut should be in whitespace gap before 790, got $cut1", cut1 in 760.0..790.0)
    }

    @Test
    fun whitespaceSeamDetectionAvoidsSplittingPlotAndTableElements() {
        val plot = PlotElement(
            left = 50.0,
            top = 795.0,
            right = 350.0,
            bottom = 830.0,
            curves = listOf(PlotCurve(PlotKind.CARTESIAN, "x^2", 0xFF000000.toInt())),
        )
        val table = TableElement(
            left = 50.0,
            top = 1580.0,
            colWidths = listOf(100.0, 100.0),
            rowHeights = listOf(30.0, 30.0), // height = 60, bottom = 1640
        )

        val cuts = CanvasAutoSlicer.findVerticalCuts(
            elements = listOf(plot, table),
            targetHeightPt = 800.0,
            totalHeightPt = 2400.0,
            startYPt = 0.0,
            windowPt = 40.0,
        )

        // Cut 1 should avoid splitting the plot [795..830]
        val cut1 = cuts[1]
        assertFalse("Cut 1 must not split plot [795..830], got $cut1", cut1 > 795.0 && cut1 < 830.0)

        // Cut 2 near 1600 should avoid splitting the table [1580..1640]
        val cut2 = cuts[2]
        assertFalse("Cut 2 must not split table [1580..1640], got $cut2", cut2 > 1580.0 && cut2 < 1640.0)
    }

    @Test
    fun sliceVerticalStripToPagesGeneratesDiscretePagesWithTranslatedElements() {
        val s1 = makeStroke(50.0, 100.0)
        val s2 = makeStroke(850.0, 900.0)

        val pages = CanvasAutoSlicer.sliceVerticalStripToPages(
            elements = listOf(s1, s2),
            pageWidthPt = 595.28,
            targetPageHeightPt = 800.0,
            totalHeightPt = 1600.0,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        )

        assertEquals(2, pages.size)
        assertEquals(595.28, pages[0].width, 0.001)
        assertEquals(595.28, pages[1].width, 0.001)

        // Page 1 contains s1
        val page1Strokes = pages[0].layers.flatMap { it.elements }.filterIsInstance<Stroke>()
        assertEquals(1, page1Strokes.size)
        assertEquals(50.0, page1Strokes[0].points[0].y, 0.001)

        // Page 2 contains s2 translated into page-local space
        val page2Strokes = pages[1].layers.flatMap { it.elements }.filterIsInstance<Stroke>()
        assertEquals(1, page2Strokes.size)
        // In page 2, s2 should be at ~ (850 - cut1) pt, which is >= 0
        assertTrue("Page 2 element y must be >= 0", page2Strokes[0].points[0].y >= 0.0)
    }

    @Test
    fun sliceHorizontalStripToPagesProducesValidPages() {
        val s1 = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(50.0, 100.0, 2.0), StrokePoint(150.0, 100.0, 2.0)),
            uniformWidth = true,
        )
        val s2 = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(650.0, 100.0, 2.0), StrokePoint(750.0, 100.0, 2.0)),
            uniformWidth = true,
        )

        val pages = CanvasAutoSlicer.sliceHorizontalStripToPages(
            elements = listOf(s1, s2),
            targetPageWidthPt = 500.0,
            pageHeightPt = 800.0,
            totalWidthPt = 1000.0,
        )

        assertEquals(2, pages.size)
        assertEquals(800.0, pages[0].height, 0.001)
        assertEquals(800.0, pages[1].height, 0.001)
    }
}

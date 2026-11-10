package com.mobixournal.render

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotCurve as ModelPlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotInfoSide
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The side a plot's analysis report is written on (`PlotInfoPlacement`, `PlotElement.infoSide`).
 *
 * Three things are checked, because they are three different promises:
 *  1. the arithmetic — each side puts the block where its name says, with the below side still under the
 *     marker key it has always had to clear;
 *  2. the compiled ink — `PlotInk`, the one compiler the page, both previews and every export share,
 *     draws the report on the element's side rather than at a fixed spot;
 *  3. the file — the choice survives a save and a reopen, and a plot written before the field existed
 *     reads as *below*.
 */
class PlotInfoSideTest {

    private fun plot(side: PlotInfoSide, summary: String? = "Function Study\n• root: 1") = PlotElement(
        left = 100.0,
        top = 200.0,
        right = 400.0,
        bottom = 380.0,
        curves = listOf(
            ModelPlotCurve(
                kind = PlotKind.CARTESIAN,
                source = "x^2 - 1",
                color = 0xFF1976D2.toInt(),
                lineStyle = LineStyle.PLAIN,
            ),
        ),
        xMin = -3.0,
        xMax = 3.0,
        summaryText = summary,
        infoSide = side,
    )

    /** The report the element's own compiler emitted — the text block, not a tick label. */
    private fun reportOf(element: PlotElement): TextElement =
        PlotInk.ink(element).elements
            .filterIsInstance<TextElement>()
            .single { it.content.startsWith("Function Study") }

    private fun reopen(element: PlotElement): PlotElement =
        MobixElementCodec.decode(
            MobixElementCodec.encode(element, MobixAssetSink()),
            MobixAssetSource { null },
        ) as PlotElement

    @Test
    fun belowPutsTheReportUnderTheFrameAndUnderTheMarkerKey() {
        val at = PlotInfoPlacement.origin(
            side = PlotInfoSide.BELOW,
            summaryText = "Function Study",
            left = 100.0,
            top = 200.0,
            right = 400.0,
            bottom = 380.0,
            legendHeightPt = 25.0,
        )

        assertEquals(100.0, at.x, 1e-9)
        assertEquals(380.0 + PlotInfoPlacement.GAP_PT + 25.0, at.y, 1e-9)
    }

    @Test
    fun rightAndLeftSitBesideTheFrameAndClearItByTheGap() {
        val text = "Function Study\n• root: 1"
        val right = PlotInfoPlacement.origin(PlotInfoSide.RIGHT, text, 100.0, 200.0, 400.0, 380.0)
        val left = PlotInfoPlacement.origin(PlotInfoSide.LEFT, text, 100.0, 200.0, 400.0, 380.0)

        assertEquals(400.0 + PlotInfoPlacement.GAP_PT, right.x, 1e-9)
        assertEquals("a side report reads from the frame's top", 200.0, right.y, 1e-9)
        assertEquals(100.0 - PlotInfoPlacement.GAP_PT - PlotInfoPlacement.width(text), left.x, 1e-9)
        assertEquals(200.0, left.y, 1e-9)
        assertTrue("a left report ends before the frame", left.x + PlotInfoPlacement.width(text) < 100.0)
    }

    @Test
    fun aboveSitsOverTheFrameByTheReportsOwnHeight() {
        val text = "Function Study\n• root: 1"
        val above = PlotInfoPlacement.origin(PlotInfoSide.ABOVE, text, 100.0, 200.0, 400.0, 380.0)

        assertEquals(100.0, above.x, 1e-9)
        assertEquals(200.0 - PlotInfoPlacement.GAP_PT - PlotInfoPlacement.height(text), above.y, 1e-9)
        assertTrue("the block's own bottom stays above the frame", above.y + PlotInfoPlacement.height(text) < 200.0)
    }

    @Test
    fun theCompilerWritesTheReportOnTheSideTheElementAsksFor() {
        for (side in PlotInfoSide.entries) {
            val element = plot(side)
            val report = reportOf(element)
            val at = PlotInfoPlacement.origin(
                side = side,
                summaryText = report.content,
                left = element.left,
                top = element.top,
                right = element.right,
                bottom = element.bottom,
            )
            assertEquals("$side x", at.x, report.x, 1e-9)
            assertEquals("$side y", at.y, report.y, 1e-9)
        }
    }

    @Test
    fun theSideSurvivesASaveAndAReopen() {
        for (side in PlotInfoSide.entries) {
            assertEquals(side, reopen(plot(side)).infoSide)
        }
    }

    @Test
    fun aPlotWithNoSideIsWrittenWithoutOneAndReadsAsBelow() {
        // The default is absent rather than spelled out, which is what lets a file written before the
        // choice existed read back exactly where its report used to be drawn.
        val below = plot(PlotInfoSide.BELOW)

        assertFalse(MobixElementCodec.encode(below, MobixAssetSink()).has("infoSide"))
        assertEquals(PlotInfoSide.BELOW, reopen(below).infoSide)
        assertTrue(
            "a chosen side is written, so it can be read back",
            MobixElementCodec.encode(plot(PlotInfoSide.LEFT), MobixAssetSink()).has("infoSide"),
        )
    }
}

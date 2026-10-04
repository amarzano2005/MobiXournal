package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic tests for the highlighter/pen rendering split (no Canvas needed). */
class StrokePainterTest {

    @Test fun highlighterRendersAtDesktopOpacityPreservingColour() {
        val out = StrokePainter.renderColor(Tool.HIGHLIGHTER, 0xFFFF0000.toInt())
        // Desktop paints the highlighter at OPACITY_HIGHLIGHTER = 0.47 (0x78), whatever the file says.
        assertEquals(StrokePainter.HIGHLIGHTER_RENDER_ALPHA, out ushr 24)
        assertEquals(0xFF0000, out and 0x00FFFFFF) // colour preserved
    }

    @Test fun highlighterIgnoresAStoredAlphaLikeDesktop() {
        val stored = 0x40123456
        val out = StrokePainter.renderColor(Tool.HIGHLIGHTER, stored)
        assertEquals(StrokePainter.HIGHLIGHTER_RENDER_ALPHA, out ushr 24)
        assertEquals(0x123456, out and 0x00FFFFFF)
    }

    @Test fun penColourIsLeftUntouched() {
        val stored = 0xFF112233.toInt()
        assertEquals(stored, StrokePainter.renderColor(Tool.PEN, stored))
    }

    @Test fun bandWidthIsTheMeanVertexWidth() {
        val pts = listOf(
            StrokePoint(0.0, 0.0, 8.0),
            StrokePoint(1.0, 0.0, 10.0),
            StrokePoint(2.0, 0.0, 12.0),
        )
        assertEquals(10.0, StrokePainter.bandWidth(pts), 1e-9)
    }

    @Test fun bandWidthOfEmptyIsZero() {
        assertEquals(0.0, StrokePainter.bandWidth(emptyList()), 1e-9)
    }
}

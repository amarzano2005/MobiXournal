package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules every painter shares: what colour a stroke is painted in, how wide a highlighter's band
 * is, and what pattern a dashed line uses. They are pinned here because both shells depend on them —
 * a change that makes the pen look right on the desktop must not make it look wrong on the tablet.
 */
class InkGeometryTest {

    private fun point(x: Double, y: Double, w: Double) = StrokePoint(x, y, w)

    @Test
    fun `a highlighter is forced to the desktop render alpha and keeps its rgb`() {
        val stored = 0xFFFF0000.toInt()
        val out = InkGeometry.renderColor(Tool.HIGHLIGHTER, stored)
        assertEquals(InkGeometry.HIGHLIGHTER_RENDER_ALPHA, out ushr 24)
        assertEquals(0xFF0000, out and 0xFFFFFF)
        assertEquals(0x78, InkGeometry.HIGHLIGHTER_RENDER_ALPHA)
    }

    @Test
    fun `a pen keeps the alpha the file stored`() {
        val stored = 0x8000FF00.toInt()
        assertEquals(stored, InkGeometry.renderColor(Tool.PEN, stored))
    }

    @Test
    fun `the band width is the mean of the point widths`() {
        val pts = listOf(point(0.0, 0.0, 6.0), point(1.0, 0.0, 10.0), point(2.0, 0.0, 14.0))
        assertEquals(10.0, InkGeometry.bandWidth(pts), 1e-9)
        assertEquals(0.0, InkGeometry.bandWidth(emptyList()), 1e-9)
    }

    @Test
    fun `uniform width means every point carries the same width`() {
        assertTrue(InkGeometry.uniformWidth(emptyList()))
        assertTrue(InkGeometry.uniformWidth(listOf(point(0.0, 0.0, 4.0), point(1.0, 1.0, 4.0))))
        assertFalse(InkGeometry.uniformWidth(listOf(point(0.0, 0.0, 4.0), point(1.0, 1.0, 5.0))))
    }

    @Test
    fun `dash intervals scale with the stroke width and plain has none`() {
        assertNull(InkGeometry.dashIntervalsPt(LineStyle.PLAIN, 4.0))
        assertArrayEquals(floatArrayOf(8f, 6f), InkGeometry.dashIntervalsPt(LineStyle.DASHED, 2.0), 1e-6f)
        // A hairline width is floored at 0.5 pt, so the pattern never collapses to nothing.
        assertArrayEquals(floatArrayOf(2f, 1.5f), InkGeometry.dashIntervalsPt(LineStyle.DASHED, 0.1), 1e-6f)
        assertEquals(4, InkGeometry.dashIntervalsPt(LineStyle.DASH_DOT, 1.0)!!.size)
        assertEquals(2, InkGeometry.dashIntervalsPt(LineStyle.DOTTED, 1.0)!!.size)
    }

    @Test
    fun `the render mode follows the line style before the tool`() {
        assertEquals(InkGeometry.RenderMode.Styled, InkGeometry.renderMode(Tool.HIGHLIGHTER, LineStyle.DASHED))
        assertEquals(InkGeometry.RenderMode.Band, InkGeometry.renderMode(Tool.HIGHLIGHTER, LineStyle.PLAIN))
        assertEquals(InkGeometry.RenderMode.Pressure, InkGeometry.renderMode(Tool.PEN, LineStyle.PLAIN))
    }
}

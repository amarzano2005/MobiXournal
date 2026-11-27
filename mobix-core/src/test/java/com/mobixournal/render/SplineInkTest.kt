package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.format.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplineInkTest {

    @Test
    fun singleNodeReturnsNull() {
        val spline = SplineElement(nodes = listOf(SplineNode(10.0, 20.0)))
        assertNull(SplineInk.ink(spline))
    }

    @Test
    fun openSplineSamplesSmoothlyThroughKnots() {
        val spline = SplineElement(
            nodes = listOf(
                SplineNode(0.0, 0.0),
                SplineNode(50.0, 80.0),
                SplineNode(100.0, 0.0),
            ),
            color = 0xFF1565C0.toInt(),
            width = 2.0,
            lineStyle = LineStyle.DASHED,
        )
        val ink = SplineInk.ink(spline)
        assertNotNull(ink)
        assertEquals(1, ink!!.size)
        val stroke = ink.first() as Stroke
        assertEquals(0xFF1565C0.toInt(), stroke.color)
        assertEquals(LineStyle.DASHED, stroke.lineStyle)
        assertTrue(stroke.points.size > 16)
        assertEquals(0.0, stroke.points.first().x, 1e-6)
        assertEquals(0.0, stroke.points.first().y, 1e-6)
        assertEquals(100.0, stroke.points.last().x, 1e-6)
        assertEquals(0.0, stroke.points.last().y, 1e-6)
    }

    @Test
    fun closedSplineReturnsToStartKnot() {
        val spline = SplineElement(
            nodes = listOf(
                SplineNode(0.0, 0.0),
                SplineNode(60.0, 0.0),
                SplineNode(30.0, 60.0),
            ),
            closed = true,
        )
        val pts = SplineInk.samplePoints(spline.nodes, spline.closed)!!
        assertEquals(pts.first().x, pts.last().x, 1e-6)
        assertEquals(pts.first().y, pts.last().y, 1e-6)
    }

    @Test
    fun explicitHandlesOverrideAutoTangents() {
        val nodes = listOf(
            SplineNode(0.0, 0.0, outX = 25.0, outY = 50.0),
            SplineNode(100.0, 0.0, inX = 75.0, inY = -50.0),
        )
        val (_, out0) = SplineInk.handlesFor(nodes, closed = false, index = 0)
        val (in1, _) = SplineInk.handlesFor(nodes, closed = false, index = 1)
        assertEquals(25.0, out0.first, 1e-6)
        assertEquals(50.0, out0.second, 1e-6)
        assertEquals(75.0, in1.first, 1e-6)
        assertEquals(-50.0, in1.second, 1e-6)
    }
}

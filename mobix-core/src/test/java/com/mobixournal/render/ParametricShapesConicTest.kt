package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ParametricShapesConicTest {

    private fun ellipseToolPoints(cx: Double, cy: Double, rx: Double, ry: Double): List<StrokePoint> =
        (0..24).map { i ->
            val a = 2.0 * PI * i / 24
            StrokePoint(cx + rx * cos(a), cy + ry * sin(a), 1.5)
        }

    @Test
    fun elementForShapeToolCreatesLivingCircleAndEllipse() {
        val circle = ParametricShapes.elementForShapeTool(
            kind = ShapeKind.ELLIPSE,
            points = ellipseToolPoints(100.0, 100.0, 40.0, 40.0),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 2.0,
        )
        assertNotNull(circle)
        assertEquals(ParametricShapes.CIRCLE, circle!!.shape)
        assertEquals(4, circle.vertices.size)
        val cf = ParametricShapes.conicFrame(circle)!!
        assertEquals(40.0, cf.rx, 1e-6)
        assertEquals(40.0, cf.ry, 1e-6)

        val ellipse = ParametricShapes.elementForShapeTool(
            kind = ShapeKind.ELLIPSE,
            points = ellipseToolPoints(100.0, 100.0, 60.0, 30.0),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 2.0,
        )!!
        assertEquals(ParametricShapes.ELLIPSE, ellipse.shape)
        val ef = ParametricShapes.conicFrame(ellipse)!!
        assertEquals(60.0, ef.rx, 1e-6)
        assertEquals(30.0, ef.ry, 1e-6)
    }

    @Test
    fun cardinalHandleDragMovesAxisKeepsOppositeFixedAndReclassifies() {
        val circle = ParametricShapes.elementForShapeTool(
            kind = ShapeKind.ELLIPSE,
            points = ellipseToolPoints(100.0, 100.0, 40.0, 40.0),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 2.0,
        )!!
        val westBefore = circle.vertices[3]

        // Drag East handle (index 1) right by +40 pt (radius becomes 60 while Ry stays 40)
        val stretched = ParametricShapes.moveVertex(circle, 1, 180.0, 100.0)
        assertEquals(ParametricShapes.ELLIPSE, stretched.shape)
        assertEquals(westBefore.x, stretched.vertices[3].x, 1e-6)
        val f = ParametricShapes.conicFrame(stretched)!!
        assertEquals(60.0, f.rx, 1e-6)
        assertEquals(40.0, f.ry, 1e-6)

        // Make radii equal again -> reclassifies back to CIRCLE
        val restored = ParametricShapes.setConicRadii(stretched, rxPt = 50.0, ryPt = 50.0)
        assertEquals(ParametricShapes.CIRCLE, restored.shape)
    }

    @Test
    fun conicContainsBoundsRotateAndShapeInkWorkCleanly() {
        val ellipse = ParametricShapes.elementForShapeTool(
            kind = ShapeKind.ELLIPSE,
            points = ellipseToolPoints(100.0, 100.0, 60.0, 30.0),
            strokeColor = 0xFF112233.toInt(),
            strokeWidth = 2.0,
        )!!.copy(showSideLengths = true)

        assertTrue(ParametricShapes.conicContains(ellipse, 100.0, 100.0))
        assertFalse("empty bounding-box corner misses the ellipse", ParametricShapes.conicContains(ellipse, 42.0, 72.0))

        val rotated = SelectionOps.rotate(ellipse, angle = PI / 2.0, px = 100.0, py = 100.0) as com.mobixournal.format.model.ParametricShapeElement
        val bounds = ElementBounds.of(rotated)
        assertEquals(100.0 - 31.0, bounds.left, 0.5)
        assertEquals(100.0 + 31.0, bounds.right, 0.5)
        assertEquals(100.0 - 61.0, bounds.top, 0.5)
        assertEquals(100.0 + 61.0, bounds.bottom, 0.5)

        val ink = ShapeInk.ink(ellipse)!!
        val outline = ink.first { it is Stroke } as Stroke
        assertTrue(outline.points.size >= 64)
        val labels = ink.filterIsInstance<TextElement>().map { it.content }
        assertTrue(labels.any { it.startsWith("a=") })
        assertTrue(labels.any { it.startsWith("b=") })
    }
}

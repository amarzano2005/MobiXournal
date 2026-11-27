package com.mobixournal.render

import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementHitTesterTest {

    @Test
    fun parametricTriangleHitsInteriorAndBorderButMissesEmptyBoxCorner() {
        val tri = ParametricShapeElement(
            shape = ParametricShapes.TRIANGLE,
            vertices = listOf(
                ShapeVertex(100.0, 100.0),
                ShapeVertex(200.0, 100.0),
                ShapeVertex(100.0, 200.0),
            ),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 2.0,
        )
        assertFalse("empty interior misses", ElementHitTester.hits(tri, 120.0, 120.0, padPt = 4.0))
        assertTrue("edge hits", ElementHitTester.hits(tri, 150.0, 100.0, padPt = 4.0))
        assertFalse(
            "empty corner of bounding box (190,190) is outside the triangle",
            ElementHitTester.hits(tri, 190.0, 190.0, padPt = 4.0),
        )
    }

    @Test
    fun closedEllipseStrokeHitsOnlyBorderLikeOpenDiagonal() {
        val ellipse = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = ShapeBuilder.build(ShapeKind.ELLIPSE, 100.0, 100.0, 200.0, 200.0, 2.0),
            uniformWidth = true,
            extraAttrs = FigurePins.figureAttrs(ShapeKind.ELLIPSE),
        )
        assertFalse("ellipse empty interior misses", ElementHitTester.hits(ellipse, 150.0, 150.0, padPt = 4.0))
        assertTrue("ellipse border hits", ElementHitTester.hits(ellipse, 200.0, 150.0, padPt = 4.0))

        val line = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(0.0, 0.0, 2.0), StrokePoint(100.0, 100.0, 2.0)),
            uniformWidth = true,
            extraAttrs = FigurePins.figureAttrs(ShapeKind.LINE),
        )
        assertTrue("point on diagonal hits", ElementHitTester.hits(line, 50.0, 50.0, padPt = 4.0))
        assertFalse("empty corner of diagonal box misses", ElementHitTester.hits(line, 90.0, 10.0, padPt = 4.0))
    }

    @Test
    fun matchesFigureToolAcceptsBothStrokesAndParametricShapes() {
        val pts = ShapeBuilder.build(ShapeKind.RECTANGLE, 0.0, 0.0, 80.0, 60.0, 2.0)
        val living = ParametricShapes.elementForShapeTool(ShapeKind.RECTANGLE, pts, 0xFF000000.toInt(), 2.0)!!
        assertTrue(ElementHitTester.matchesFigureTool(living, ShapeKind.RECTANGLE))
        assertFalse(ElementHitTester.matchesFigureTool(living, ShapeKind.TRIANGLE))
        // Deforming a corner turns shape into POLYGON, yet the Rectangle tool still claims it.
        val deformed = ParametricShapes.moveVertex(living, 1, 95.0, 12.0)
        assertTrue(ElementHitTester.matchesFigureTool(deformed, ShapeKind.RECTANGLE))
    }

    @Test
    fun rotateStemAnchorsToTriangleOutlineInsteadOfFloatingInBoxSpace() {
        val tri = ParametricShapeElement(
            shape = ParametricShapes.TRIANGLE,
            vertices = listOf(
                ShapeVertex(100.0, 200.0),
                ShapeVertex(200.0, 200.0),
                ShapeVertex(150.0, 100.0),
            ),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 2.0,
        )
        val bounds = ElementBounds.of(tri)
        val rightStem = ElementHitTester.rotateStemRight(tri, bounds, 20.0)
        assertTrue(
            "right stem foot must sit on triangle outline, not at empty box.right",
            ElementHitTester.nearEdge(tri.vertices.map { Vec2(it.x, it.y) }, rightStem.footX, rightStem.footY, 1e-6),
        )
        assertEquals(rightStem.footX + 20.0, rightStem.knobX, 1e-6)

        val topStem = ElementHitTester.rotateStemTop(tri, bounds, 20.0)
        assertEquals(150.0, topStem.footX, 1e-6)
        assertEquals(100.0, topStem.footY, 1e-6)
        assertEquals(80.0, topStem.knobY, 1e-6)
    }
}

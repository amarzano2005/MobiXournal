package com.mobixournal.render

import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
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
        assertTrue("interior hits", ElementHitTester.hits(tri, 120.0, 120.0, padPt = 4.0))
        assertTrue("edge hits", ElementHitTester.hits(tri, 150.0, 100.0, padPt = 4.0))
        assertFalse(
            "empty corner of bounding box (190,190) is outside the triangle",
            ElementHitTester.hits(tri, 190.0, 190.0, padPt = 4.0),
        )
    }

    @Test
    fun closedEllipseStrokeHitsInteriorWhileOpenDiagonalHitsOnlyStroke() {
        val ellipse = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = ShapeBuilder.build(ShapeKind.ELLIPSE, 100.0, 100.0, 200.0, 200.0, 2.0),
            uniformWidth = true,
            extraAttrs = FigurePins.figureAttrs(ShapeKind.ELLIPSE),
        )
        assertTrue("ellipse interior hits", ElementHitTester.hits(ellipse, 150.0, 150.0, padPt = 4.0))
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
    }
}

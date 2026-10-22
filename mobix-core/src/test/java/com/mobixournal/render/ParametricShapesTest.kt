package com.mobixournal.render

import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The vertex maths of a living figure: what a recognised polygon becomes, where a dragged corner
 * lands, and what the side/angle badges say. Pure JVM — the canvas draws exactly these values.
 */
class ParametricShapesTest {

    private fun pt(x: Double, y: Double) = StrokePoint(x, y, 2.0)

    private fun triangleFigure() = RecognisedFigure(
        FigureKind.TRIANGLE,
        listOf(pt(0.0, 0.0), pt(100.0, 0.0), pt(0.0, 100.0), pt(0.0, 0.0)),
    )

    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = ParametricShapeElement(
        shape = ParametricShapes.RECTANGLE,
        vertices = listOf(
            ShapeVertex(x0, y0), ShapeVertex(x1, y0), ShapeVertex(x1, y1), ShapeVertex(x0, y1),
        ),
        strokeColor = 0xFF000000.toInt(),
        strokeWidth = 2.0,
    )

    // ---- what a recognition becomes ------------------------------------------------------------

    @Test fun aClosedPolygonBecomesAShapeWithItsCorners() {
        val shape = ParametricShapes.elementFor(triangleFigure(), 0xFFD84315.toInt(), 2.5)
        requireNotNull(shape)
        assertEquals(ParametricShapes.TRIANGLE, shape.shape)
        assertEquals(3, shape.vertices.size)
        assertEquals(ShapeVertex(100.0, 0.0), shape.vertices[1])
        assertEquals(0xFFD84315.toInt(), shape.strokeColor)
        assertEquals(2.5, shape.strokeWidth, 1e-9)
        // Badges start off: a recognised figure is the ink the user drew, not a dimension drawing.
        assertTrue(!shape.showAngles && !shape.showSideLengths)
    }

    @Test fun openFiguresStayStrokes() {
        for (kind in listOf(FigureKind.LINE, FigureKind.ARROW, FigureKind.ARC, FigureKind.CIRCLE, FigureKind.ELLIPSE)) {
            val figure = RecognisedFigure(kind, listOf(pt(0.0, 0.0), pt(10.0, 0.0)))
            assertNull("$kind is not a polygon to drag", ParametricShapes.elementFor(figure, 0xFF000000.toInt(), 1.0))
        }
    }

    @Test fun aFigureWithTooFewCornersIsNotAShape() {
        val figure = RecognisedFigure(FigureKind.TRIANGLE, listOf(pt(0.0, 0.0), pt(10.0, 10.0), pt(0.0, 0.0)))
        assertNull(ParametricShapes.elementFor(figure, 0xFF000000.toInt(), 1.0))
    }

    // ---- moving a corner -----------------------------------------------------------------------

    @Test fun moveVertexReplacesOnlyThatCorner() {
        val shape = ParametricShapes.elementFor(triangleFigure(), 0xFF000000.toInt(), 1.0)!!
        val moved = ParametricShapes.moveVertex(shape, 1, 130.0, -20.0)
        assertEquals(ShapeVertex(130.0, -20.0), moved.vertices[1])
        assertEquals(shape.vertices[0], moved.vertices[0])
        assertEquals(shape.vertices[2], moved.vertices[2])
        // A stale handle cannot corrupt the figure.
        assertEquals(shape, ParametricShapes.moveVertex(shape, 7, 1.0, 1.0))
    }

    @Test fun aSideNearAGuidedAngleSnapsOntoIt() {
        val shape = rect(0.0, 0.0, 100.0, 50.0)
        // Vertex 3's incoming side is vertex 2 -> vertex 3. Aim it 3° off vertical.
        val radius = 80.0
        val offVertical = Math.toRadians(90.0 - 3.0)
        val (x, y) = ParametricShapes.snappedVertex(
            shape, 3,
            100.0 + radius * cos(offVertical),
            50.0 + radius * sin(offVertical),
        )
        assertEquals("the side is pulled exactly vertical", 100.0, x, 1e-6)
        assertEquals(50.0 + radius, y, 1e-6)
    }

    @Test fun aSideAwayFromEveryGuidedAngleIsLeftAlone() {
        val shape = rect(0.0, 0.0, 100.0, 50.0)
        val angle = Math.toRadians(20.0)
        val x = 100.0 + 60.0 * cos(angle)
        val y = 50.0 + 60.0 * sin(angle)
        val (sx, sy) = ParametricShapes.snappedVertex(shape, 3, x, y)
        assertEquals(x, sx, 1e-9)
        assertEquals(y, sy, 1e-9)
    }

    @Test fun theSnappedCornerKeepsThePointersDistance() {
        val shape = rect(0.0, 0.0, 100.0, 50.0)
        val angle = Math.toRadians(90.0 - 2.0)
        val reach = 37.0
        val (x, y) = ParametricShapes.snappedVertex(shape, 3, 100.0 + reach * cos(angle), 50.0 + reach * sin(angle))
        assertEquals(reach, hypot(x - 100.0, y - 50.0), 1e-6)
    }

    @Test fun everyGuidedDirectionSnapsInAllFourQuadrants() {
        assertEquals(0.0, ParametricShapes.snapDirectionDeg(2.0)!!, 1e-9)
        assertEquals(30.0, ParametricShapes.snapDirectionDeg(32.0)!!, 1e-9)
        assertEquals(45.0, ParametricShapes.snapDirectionDeg(43.0)!!, 1e-9)
        assertEquals(90.0, ParametricShapes.snapDirectionDeg(88.0)!!, 1e-9)
        // A side has no arrow: 210° is the same edge as 30°, and both are pulled onto it.
        val downLeft = ParametricShapes.snapDirectionDeg(212.0)!!
        assertEquals(30.0, ((downLeft % 180.0) + 180.0) % 180.0, 1e-9)
        assertEquals(-135.0, ParametricShapes.snapDirectionDeg(-133.0)!!, 1e-9)
        assertNull(ParametricShapes.snapDirectionDeg(70.0))
        assertNull(ParametricShapes.snapDirectionDeg(12.0))
    }

    // ---- the readouts --------------------------------------------------------------------------

    @Test fun sideLengthsAreTheDistancesBetweenNeighbouringCorners() {
        val shape = ParametricShapes.elementFor(triangleFigure(), 0xFF000000.toInt(), 1.0)!!
        assertEquals(100.0, ParametricShapes.sideLengthPt(shape, 0), 1e-9)
        assertEquals(hypot(100.0, 100.0), ParametricShapes.sideLengthPt(shape, 1), 1e-9)
        assertEquals(100.0, ParametricShapes.sideLengthPt(shape, 2), 1e-9)
    }

    @Test fun interiorAnglesAreMeasuredInsideTheFigure() {
        val shape = ParametricShapes.elementFor(triangleFigure(), 0xFF000000.toInt(), 1.0)!!
        assertEquals(90.0, ParametricShapes.interiorAngleDeg(shape, 0), 1e-9)
        assertEquals(45.0, ParametricShapes.interiorAngleDeg(shape, 1), 1e-9)
        assertEquals(45.0, ParametricShapes.interiorAngleDeg(shape, 2), 1e-9)
    }

    @Test fun aDraggedCornerChangesTheReadoutsOnTheNextRead() {
        val shape = rect(0.0, 0.0, 100.0, 50.0)
        val dragged = ParametricShapes.moveVertex(shape, 1, 140.0, 0.0)
        assertEquals(140.0, ParametricShapes.sideLengthPt(dragged, 0), 1e-9)
        // A quadrilateral's interior angles still sum to a full turn, and the corner that was pulled
        // out is no longer square — the badges read the figure that is on screen.
        val sum = (0..3).sumOf { ParametricShapes.interiorAngleDeg(dragged, it) }
        assertEquals(360.0, sum, 1e-6)
        assertTrue(ParametricShapes.interiorAngleDeg(dragged, 1) < 90.0)
    }
}

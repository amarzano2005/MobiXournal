package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionResizeMathTest {

    @Test
    fun cornerLocksAspectMatchesShellContract() {
        val shape = ParametricShapeElement(
            shape = ParametricShapes.SQUARE,
            vertices = listOf(
                ShapeVertex(0.0, 0.0),
                ShapeVertex(100.0, 0.0),
                ShapeVertex(100.0, 100.0),
                ShapeVertex(0.0, 100.0),
            ),
            strokeColor = 0xFF000000.toInt(),
        )
        val image = ImageElement(left = 0.0, top = 0.0, right = 100.0, bottom = 100.0, data = ByteArray(0))
        assertTrue(SelectionResizeMath.cornerLocksAspect(image, lockAspect = false))
        assertTrue(SelectionResizeMath.cornerLocksAspect(null, lockAspect = false))
        assertFalse(SelectionResizeMath.cornerLocksAspect(shape, lockAspect = false))
        assertTrue(SelectionResizeMath.cornerLocksAspect(shape, lockAspect = true))
        assertEquals(ResizeAxis.BOTH, SelectionResizeMath.cornerAxis(shape, lockAspect = true))
        assertEquals(ResizeAxis.FREE, SelectionResizeMath.cornerAxis(shape, lockAspect = false))
    }

    @Test
    fun cornerScaleFactorsLockAspectUniformly() {
        val bounds = Bounds(0.0, 0.0, 100.0, 100.0)
        val (sx, sy) = SelectionResizeMath.cornerScaleFactors(
            bounds = bounds,
            handleIsLeft = false,
            handleIsTop = false,
            currentX = 200.0,
            currentY = 120.0,
            anchorX = 0.0,
            anchorY = 0.0,
            lockAspect = true,
            flatX = false,
            flatY = false,
        )
        assertEquals(2.0, sx, 1e-6)
        assertEquals(2.0, sy, 1e-6)
    }

    @Test
    fun affineXYReclassifiesSquareOnlyWhenNonUniform() {
        val square = ParametricShapeElement(
            shape = ParametricShapes.SQUARE,
            vertices = listOf(
                ShapeVertex(0.0, 0.0),
                ShapeVertex(100.0, 0.0),
                ShapeVertex(100.0, 100.0),
                ShapeVertex(0.0, 100.0),
            ),
            strokeColor = 0xFF000000.toInt(),
        )
        val uniform = SelectionOps.affineXY(square, sx = 2.0, sy = 2.0, dx = 0.0, dy = 0.0, scaleWidth = false)
            as ParametricShapeElement
        assertEquals(ParametricShapes.SQUARE, uniform.shape)

        val stretched = SelectionOps.affineXY(square, sx = 2.0, sy = 1.0, dx = 0.0, dy = 0.0, scaleWidth = false)
            as ParametricShapeElement
        assertEquals(ParametricShapes.RECTANGLE, stretched.shape)
    }

    @Test
    fun strokeTransformsPreserveTelemetry() {
        val stroke = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = null,
            uniformWidth = false,
            points = listOf(
                StrokePoint(10.0, 20.0, 2.0, pressure = 0.6f, tilt = 0.2f, azimuth = 1.1f, dtMs = 8),
                StrokePoint(30.0, 40.0, 2.5, pressure = 0.8f, tilt = 0.3f, azimuth = 1.2f, dtMs = 12),
            ),
        )
        val scaled = SelectionOps.affineXY(stroke, sx = 2.0, sy = 2.0, dx = 5.0, dy = 5.0, scaleWidth = true) as Stroke
        assertEquals(0.6f, scaled.points[0].pressure)
        assertEquals(0.2f, scaled.points[0].tilt)
        assertEquals(1.1f, scaled.points[0].azimuth)
        assertEquals(8, scaled.points[0].dtMs)

        val rotated = SelectionOps.rotate(stroke, angle = Math.PI / 2.0, px = 0.0, py = 0.0) as Stroke
        assertEquals(0.8f, rotated.points[1].pressure)
        assertEquals(12, rotated.points[1].dtMs)

        val restyled = SelectionOps.restyle(stroke, color = null, widthPt = 4.0) as Stroke
        assertEquals(4.0, restyled.points[0].width, 1e-6)
        assertEquals(0.6f, restyled.points[0].pressure)
        assertEquals(0.3f, restyled.points[1].tilt)
    }

    @Test
    fun supportsAspectLockToggleFollowsElementFamilyRule() {
        val shape = ParametricShapeElement(
            shape = ParametricShapes.SQUARE,
            vertices = listOf(ShapeVertex(0.0, 0.0), ShapeVertex(10.0, 0.0), ShapeVertex(10.0, 10.0)),
            strokeColor = 0,
        )
        val stroke = Stroke(tool = Tool.PEN, color = 0, capStyle = null, uniformWidth = false, points = emptyList())
        val image = ImageElement(0.0, 0.0, 10.0, 10.0, ByteArray(0))
        val table = TableElement(0.0, 0.0, listOf(50.0), listOf(30.0))
        val plot = PlotElement(0.0, 0.0, 100.0, 80.0, emptyList())
        val circuit = CircuitElement("resistor", 10.0, 10.0)
        val math = MathBlockElement(0.0, 0.0, 40.0, 20.0, "x^2")

        assertTrue(SelectionResizeMath.supportsAspectLockToggle(listOf(shape), isShaft = false))
        assertTrue(SelectionResizeMath.supportsAspectLockToggle(listOf(stroke), isShaft = false))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(stroke), isShaft = true))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(image)))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(table)))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(plot)))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(circuit)))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(math)))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(listOf(shape, stroke)))
        assertFalse(SelectionResizeMath.supportsAspectLockToggle(emptyList()))
    }

    @Test
    fun strokeAspectLockPersistsAndPointInPolygonHitsInterior() {
        val stroke = Stroke(tool = Tool.PEN, color = 0, capStyle = null, uniformWidth = false, points = emptyList())
        assertEquals(null, stroke.storedAspectLocked())
        val unlocked = stroke.withAspectLocked(false)
        assertEquals(false, unlocked.storedAspectLocked())
        val locked = unlocked.withAspectLocked(true)
        assertEquals(true, locked.storedAspectLocked())

        val square = listOf(
            ShapeVertex(0.0, 0.0),
            ShapeVertex(100.0, 0.0),
            ShapeVertex(100.0, 100.0),
            ShapeVertex(0.0, 100.0),
        )
        assertTrue(SelectionResizeMath.pointInPolygon(square, 50.0, 50.0))
        assertFalse(SelectionResizeMath.pointInPolygon(square, 120.0, 50.0))
    }
}

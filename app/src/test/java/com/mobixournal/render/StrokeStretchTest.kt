package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * A resize lengthens an arrow's shaft and leaves its head the size it was drawn — the user-visible
 * rule in `docs/stylus.md` (*A figure is resized, not thickened*).
 */
class StrokeStretchTest {

    private fun stroke(points: List<StrokePoint>) = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = null,
        points = points,
        uniformWidth = true,
    )

    private fun resize(
        points: List<StrokePoint>,
        sx: Double,
        sy: Double,
        dx: Double = 0.0,
        dy: Double = 0.0,
    ): List<StrokePoint> =
        (SelectionOps.affineXY(stroke(points), sx, sy, dx, dy, scaleWidth = false) as Stroke).points

    private fun distance(a: StrokePoint, b: StrokePoint) = hypot(a.x - b.x, a.y - b.y)

    @Test
    fun enlargingAnArrowLengthensTheShaftAndKeepsTheHeadSize() {
        // A 100 pt horizontal arrow: its head is 0.18 × 100 = 18 pt.
        val original = ShapeBuilder.build(ShapeKind.ARROW, 10.0, 50.0, 110.0, 50.0, 2.0)
        assertEquals(18.0, distance(original[1], original[2]), 1e-9)
        assertEquals(18.0, distance(original[1], original[4]), 1e-9)

        val doubled = resize(original, 2.0, 2.0)

        // The shaft is twice as long...
        assertEquals(20.0, doubled[0].x, 1e-9)
        assertEquals(220.0, doubled[1].x, 1e-9)
        assertTrue(distance(doubled[0], doubled[1]) > 2.0 * distance(original[0], original[1]) - 1e-9)
        // ...while both barbs stay 18 pt from the tip, not 36.
        assertEquals("the arrowhead must not grow with the shaft", 18.0, distance(doubled[1], doubled[2]), 1e-9)
        assertEquals(18.0, distance(doubled[1], doubled[4]), 1e-9)
        // The other end of the shaft still carries the old, thin width.
        assertEquals(2.0, doubled[0].width, 1e-9)
    }

    @Test
    fun coordinateAxesGetLongerButBothArrowheadsKeepTheirSize() {
        // y axis: 40 pt → 8 pt head (the minimum); x axis: 100 pt → 18 pt head.
        val original = ShapeBuilder.build(ShapeKind.COORDINATE_AXIS, 100.0, 100.0, 200.0, 60.0, 2.0)
        assertEquals(8.0, distance(original[1], original[0]), 1e-9)
        assertEquals(8.0, distance(original[1], original[2]), 1e-9)
        assertEquals(18.0, distance(original[5], original[6]), 1e-9)
        assertEquals(18.0, distance(original[5], original[8]), 1e-9)

        val doubled = resize(original, 2.0, 2.0)

        // Tips travel to twice their distance from the origin...
        assertEquals(200.0, doubled[1].x, 1e-9)
        assertEquals(120.0, doubled[1].y, 1e-9)
        assertEquals(400.0, doubled[5].x, 1e-9)
        assertEquals(200.0, doubled[5].y, 1e-9)
        // ...and each head is still exactly the size it was drawn.
        assertEquals(8.0, distance(doubled[1], doubled[0]), 1e-9)
        assertEquals(8.0, distance(doubled[1], doubled[2]), 1e-9)
        assertEquals(18.0, distance(doubled[5], doubled[6]), 1e-9)
        assertEquals(18.0, distance(doubled[5], doubled[8]), 1e-9)
    }

    @Test
    fun aDoubleArrowKeepsBothHeadsWhileItsShaftStretches() {
        val original = ShapeBuilder.build(ShapeKind.DOUBLE_ARROW, 0.0, 0.0, 100.0, 0.0, 1.0)
        val stretched = resize(original, 1.5, 1.5)

        val originalHeads = listOf(distance(original[1], original[0]), distance(original[1], original[2]))
        val stretchedHeads = listOf(distance(stretched[1], stretched[0]), distance(stretched[1], stretched[2]))
        assertEquals(originalHeads, stretchedHeads)
        assertEquals(18.0, stretchedHeads[0], 1e-9)
        assertEquals(18.0, stretchedHeads[1], 1e-9)
    }

    @Test
    fun anOrdinaryStrokeStillScalesPointForPoint() {
        val points = listOf(
            StrokePoint(0.0, 0.0, 1.0),
            StrokePoint(10.0, 0.0, 1.0),
            StrokePoint(20.0, 0.0, 1.0),
        )
        val scaled = resize(points, 2.0, 3.0, dx = 5.0, dy = -5.0)

        assertEquals(5.0, scaled[0].x, 1e-9)
        assertEquals(-5.0, scaled[0].y, 1e-9)
        assertEquals(25.0, scaled[1].x, 1e-9)
        assertEquals(45.0, scaled[2].x, 1e-9)
    }

    @Test
    fun aOneAxisStretchTurnsTheHeadWithTheShaftWithoutGrowingIt() {
        val original = ShapeBuilder.build(ShapeKind.ARROW, 0.0, 0.0, 100.0, 0.0, 2.0)
        val stretched = resize(original, 2.0, 1.0)

        assertEquals(200.0, stretched[1].x, 1e-9)
        assertEquals(18.0, distance(stretched[1], stretched[2]), 1e-9)
        assertEquals(18.0, distance(stretched[1], stretched[4]), 1e-9)
        // The barbs are still mirrored about the shaft.
        assertEquals(stretched[2].y, -stretched[4].y, 1e-9)
    }
}

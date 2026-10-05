package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Pure geometry tests for the shape tools (line / arrow / rectangle / ellipse). */
class ShapeBuilderTest {

    @Test fun lineIsTwoPointsAtTheConstantWidth() {
        val pts = ShapeBuilder.build(ShapeKind.LINE, 1.0, 2.0, 5.0, 8.0, widthPt = 3.0)
        assertEquals(2, pts.size)
        assertEquals(1.0, pts[0].x, 1e-9); assertEquals(2.0, pts[0].y, 1e-9)
        assertEquals(5.0, pts[1].x, 1e-9); assertEquals(8.0, pts[1].y, 1e-9)
        assertTrue(pts.all { it.width == 3.0 })
    }

    @Test fun rectangleIsAClosedBoundingBox() {
        // Drag from bottom-right to top-left still yields the normalised box.
        val pts = ShapeBuilder.build(ShapeKind.RECTANGLE, 10.0, 20.0, 0.0, 0.0, widthPt = 1.0)
        assertEquals(5, pts.size)
        assertEquals(pts.first().x, pts.last().x, 1e-9)
        assertEquals(pts.first().y, pts.last().y, 1e-9)
        val xs = pts.map { it.x }.toSet()
        val ys = pts.map { it.y }.toSet()
        assertEquals(setOf(0.0, 10.0), xs)
        assertEquals(setOf(0.0, 20.0), ys)
    }

    @Test fun arrowHasShaftTipAndTwoBarbs() {
        val pts = ShapeBuilder.build(ShapeKind.ARROW, 0.0, 0.0, 10.0, 0.0, widthPt = 1.0)
        assertEquals(5, pts.size)          // start, tip, barb, tip, barb
        assertEquals(0.0, pts[0].x, 1e-9)  // shaft start
        assertEquals(10.0, pts[1].x, 1e-9) // tip
        assertEquals(10.0, pts[3].x, 1e-9) // tip repeated to trace the second barb
        // Barbs sit behind the tip (smaller x) for a rightward arrow.
        assertTrue(pts[2].x < 10.0)
        assertTrue(pts[4].x < 10.0)
    }

    @Test fun ellipseIsClosedAndInscribedInTheBox() {
        val pts = ShapeBuilder.build(ShapeKind.ELLIPSE, 0.0, 0.0, 20.0, 10.0, widthPt = 1.0)
        assertTrue(pts.size >= 24)
        assertEquals(pts.first().x, pts.last().x, 1e-9) // closed
        assertEquals(pts.first().y, pts.last().y, 1e-9)
        // Every vertex lies on the ellipse: ((x-cx)/rx)^2 + ((y-cy)/ry)^2 == 1.
        val cx = 10.0; val cy = 5.0; val rx = 10.0; val ry = 5.0
        for (p in pts) {
            val v = hypot((p.x - cx) / rx, (p.y - cy) / ry)
            assertEquals(1.0, v, 1e-6)
        }
    }

    @Test fun doubleArrowHasAHeadAtBothEnds() {
        val pts = ShapeBuilder.build(ShapeKind.DOUBLE_ARROW, 0.0, 0.0, 10.0, 0.0, widthPt = 1.0)
        assertEquals(8, pts.size) // barb, tail, barb, tail | tip, barb, tip, barb
        assertEquals(0.0, pts[3].x, 1e-9)  // shaft leaves the tail tip…
        assertEquals(10.0, pts[4].x, 1e-9) // …and arrives at the head tip
        // Tail barbs sit ahead of the tail; head barbs sit behind the tip.
        assertTrue(pts[0].x > 0.0 && pts[2].x > 0.0)
        assertTrue(pts[5].x < 10.0 && pts[7].x < 10.0)
    }

    @Test fun coordinateAxisMeetsAtTheDragStartWithArrowedAxes() {
        // Drag up-and-right: origin at (0,0), y axis to y=-10, x axis to x=20.
        val pts = ShapeBuilder.build(ShapeKind.COORDINATE_AXIS, 0.0, 0.0, 20.0, -10.0, widthPt = 1.0)
        assertEquals(9, pts.size) // y head (4) + origin + x head (4)
        assertEquals(0.0, pts[3].x, 1e-9)    // y-axis tip sits above the origin
        assertEquals(-10.0, pts[3].y, 1e-9)
        assertEquals(0.0, pts[4].x, 1e-9)    // the origin
        assertEquals(0.0, pts[4].y, 1e-9)
        assertEquals(20.0, pts[5].x, 1e-9)   // x-axis tip sits right of the origin
        assertEquals(0.0, pts[5].y, 1e-9)
    }

    @Test fun zeroLengthArrowFallsBackToALine() {
        val pts = ShapeBuilder.build(ShapeKind.ARROW, 4.0, 4.0, 4.0, 4.0, widthPt = 1.0)
        assertEquals(2, pts.size)
    }

    @Test fun theStemFiguresAreClosedAndKeepTheConstantWidth() {
        for (kind in listOf(
            ShapeKind.TRIANGLE, ShapeKind.SQUARE, ShapeKind.RHOMBUS, ShapeKind.TRAPEZOID,
            ShapeKind.PENTAGON, ShapeKind.HEXAGON,
        )) {
            val pts = ShapeBuilder.build(kind, 0.0, 0.0, 20.0, 10.0, widthPt = 1.5)
            assertTrue("$kind has vertices", pts.size >= 4)
            assertEquals("$kind is closed", pts.first().x, pts.last().x, 1e-9)
            assertEquals("$kind is closed", pts.first().y, pts.last().y, 1e-9)
            assertTrue("$kind keeps the constant width", pts.all { it.width == 1.5 })
        }
    }

    @Test fun theInscribedFiguresStayInsideTheirBox() {
        // Every stem figure except the square is inscribed in the drag box; the square squares off
        // the drag's longer side (see [squareSquaresOffTheLongerSide]), so it may exceed the box.
        for (kind in listOf(
            ShapeKind.TRIANGLE, ShapeKind.RHOMBUS, ShapeKind.TRAPEZOID,
            ShapeKind.PENTAGON, ShapeKind.HEXAGON,
        )) {
            val pts = ShapeBuilder.build(kind, 0.0, 0.0, 20.0, 10.0, widthPt = 1.5)
            assertTrue(
                "$kind stays inside the drag box",
                pts.all { it.x >= -1e-6 && it.x <= 20.0 + 1e-6 && it.y >= -1e-6 && it.y <= 10.0 + 1e-6 },
            )
        }
    }

    @Test fun squareSquaresOffTheLongerSide() {
        // A 20x5 drag still yields four equal sides of 20.
        val pts = ShapeBuilder.build(ShapeKind.SQUARE, 0.0, 0.0, 20.0, 5.0, widthPt = 1.0)
        val xs = pts.map { it.x }
        val ys = pts.map { it.y }
        assertEquals(20.0, xs.max() - xs.min(), 1e-9)
        assertEquals(20.0, ys.max() - ys.min(), 1e-9)
    }

    @Test fun theRegularFiguresHaveTheExpectedVertexCount() {
        fun count(kind: ShapeKind): Int = ShapeBuilder.build(kind, 0.0, 0.0, 20.0, 20.0, 1.0).size
        assertEquals(4, count(ShapeKind.TRIANGLE))   // 3 vertices + the closing repeat
        assertEquals(5, count(ShapeKind.RHOMBUS))    // 4 + closing repeat
        assertEquals(5, count(ShapeKind.TRAPEZOID))  // 4 + closing repeat
        assertEquals(6, count(ShapeKind.PENTAGON))   // 5 + closing repeat
        assertEquals(7, count(ShapeKind.HEXAGON))    // 6 + closing repeat
    }

    @Test fun tableStaysInsideBoundingBoxAndMaintainsConstantWidth() {
        val pts = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 30.0, 20.0, widthPt = 2.0, rows = 3, cols = 4)
        assertTrue("table has points", pts.isNotEmpty())
        assertTrue("all points maintain constant width", pts.all { it.width == 2.0 })
        assertTrue("all points stay inside bounding box", pts.all {
            it.x >= -1e-6 && it.x <= 30.0 + 1e-6 && it.y >= -1e-6 && it.y <= 20.0 + 1e-6
        })
        // Check that all segments are axis-aligned (either dx == 0 or dy == 0)
        for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val dx = kotlin.math.abs(p2.x - p1.x)
            val dy = kotlin.math.abs(p2.y - p1.y)
            assertTrue("segment $i is axis-aligned", dx < 1e-6 || dy < 1e-6)
        }
    }

    @Test fun tableWithHeaderIncludesDoubleSeparatorLineAndStaysAxisAligned() {
        val normal = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 30.0, 30.0, widthPt = 2.0, rows = 3, cols = 3, hasHeader = false)
        val withHeader = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 30.0, 30.0, widthPt = 2.0, rows = 3, cols = 3, hasHeader = true)
        assertTrue("table with header has more points for double separator line", withHeader.size > normal.size)
        assertTrue("all points maintain constant width", withHeader.all { it.width == 2.0 })
        assertTrue("all points stay inside bounding box", withHeader.all {
            it.x >= -1e-6 && it.x <= 30.0 + 1e-6 && it.y >= -1e-6 && it.y <= 30.0 + 1e-6
        })
        for (i in 0 until withHeader.size - 1) {
            val p1 = withHeader[i]
            val p2 = withHeader[i + 1]
            val dx = kotlin.math.abs(p2.x - p1.x)
            val dy = kotlin.math.abs(p2.y - p1.y)
            assertTrue("segment $i is axis-aligned", dx < 1e-6 || dy < 1e-6)
        }
    }

    @Test fun tableHeaderAddsAnExtraRowRatherThanConsumingOne() {
        // [rows] counts **data** rows only: a header stacks one extra row above them, so asking for
        // 2 data rows with a header lays out the same horizontal grid as 3 plain rows.
        fun horizontalYs(pts: List<StrokePoint>): Set<Double> =
            (0 until pts.size - 1)
                .filter { kotlin.math.abs(pts[it].y - pts[it + 1].y) < 1e-9 }
                .map { pts[it].y }
                .toSet()
        val plain = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 60.0, 60.0, widthPt = 1.0, rows = 3, cols = 2)
        val withHeader = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 60.0, 60.0, widthPt = 1.0, rows = 2, cols = 2, hasHeader = true)
        val expected = setOf(0.0, 20.0, 40.0, 60.0)
        assertTrue("3 plain rows divide at y = 20 and 40", horizontalYs(plain).containsAll(expected))
        assertTrue(
            "2 data rows + header divide at the same y as 3 plain rows",
            horizontalYs(withHeader).containsAll(expected),
        )
    }

    @Test fun tableWithOneRowOneColIsClosedRectangle() {
        val pts = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 10.0, 10.0, widthPt = 1.0, rows = 1, cols = 1)
        assertEquals(5, pts.size)
        assertEquals(pts.first().x, pts.last().x, 1e-9)
        assertEquals(pts.first().y, pts.last().y, 1e-9)
    }

    @Test fun zeroLengthTableFallsBackToLine() {
        val pts = ShapeBuilder.build(ShapeKind.TABLE, 5.0, 5.0, 5.0, 5.0, widthPt = 1.0)
        assertEquals(2, pts.size)
    }

    @Test fun tinyTableDimensionsDoNotThrowException() {
        // Tapping or clicking on screen can produce sub-pixel or near-zero heights
        val pts1 = ShapeBuilder.build(ShapeKind.TABLE, 10.0, 10.0, 10.5, 10.2, widthPt = 1.0, hasHeader = true)
        assertTrue(pts1.isNotEmpty())
        val pts2 = ShapeBuilder.build(ShapeKind.TABLE, 10.0, 10.0, 10.0, 10.0, widthPt = 1.0, hasHeader = true)
        assertEquals(2, pts2.size)
    }

    @Test fun relationalTableHeaderDividesAllColumnsAndHasDoubleLine() {
        // Two data rows plus a header from (0,0) to (60,60): three rows total, so each is 20pt high.
        // The header row (y=0..20) must be divided into 3 columns just like the data rows, and the
        // header row is separated by a double line.
        val pts = ShapeBuilder.build(ShapeKind.TABLE, 0.0, 0.0, 60.0, 60.0, widthPt = 1.0, rows = 2, cols = 3, hasHeader = true)

        // Verify vertical dividers at x=20 and x=40 span from y=0 to y=60 (entering the header row)
        val vertical20 = pts.filter { kotlin.math.abs(it.x - 20.0) < 1e-6 }
        val vertical40 = pts.filter { kotlin.math.abs(it.x - 40.0) < 1e-6 }
        assertTrue("vertical divider at x=20 exists", vertical20.isNotEmpty())
        assertTrue("vertical divider at x=40 exists", vertical40.isNotEmpty())
        assertTrue("vertical divider at x=20 reaches top header (y=0)", vertical20.any { kotlin.math.abs(it.y - 0.0) < 1e-6 })
        assertTrue("vertical divider at x=20 reaches bottom (y=60)", vertical20.any { kotlin.math.abs(it.y - 60.0) < 1e-6 })
        assertTrue("vertical divider at x=40 reaches top header (y=0)", vertical40.any { kotlin.math.abs(it.y - 0.0) < 1e-6 })
        assertTrue("vertical divider at x=40 reaches bottom (y=60)", vertical40.any { kotlin.math.abs(it.y - 60.0) < 1e-6 })

        // Verify the double line under the header row: horizontal segments near y=20 and y=22.5
        val y20Segments = (0 until pts.size - 1).filter {
            kotlin.math.abs(pts[it].y - 20.0) < 1e-6 && kotlin.math.abs(pts[it + 1].y - 20.0) < 1e-6
        }
        val doubleLineSegments = (0 until pts.size - 1).filter {
            val y = pts[it].y
            y > 20.5 && y < 25.0 && kotlin.math.abs(pts[it].y - pts[it + 1].y) < 1e-6
        }
        assertTrue("horizontal divider at row 1 (y=20) exists", y20Segments.isNotEmpty())
        assertTrue("double line separator below header row exists", doubleLineSegments.isNotEmpty())
    }

    @Test fun equilateralTriangleProducesThreeEqualSides() {
        val pts = ShapeBuilder.build(
            ShapeKind.TRIANGLE, 0.0, 0.0, 100.0, 100.0, widthPt = 1.0,
            triangleKind = TriangleKind.EQUILATERAL,
        )
        assertEquals(4, pts.size) // 3 vertices + closing
        val d01 = hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y)
        val d12 = hypot(pts[2].x - pts[1].x, pts[2].y - pts[1].y)
        val d20 = hypot(pts[0].x - pts[2].x, pts[0].y - pts[2].y)
        assertEquals(d01, d12, 1e-4)
        assertEquals(d12, d20, 1e-4)
    }

    @Test fun rightTriangleHasRightAngleAtCorner() {
        val pts = ShapeBuilder.build(
            ShapeKind.TRIANGLE, 10.0, 20.0, 70.0, 80.0, widthPt = 1.0,
            triangleKind = TriangleKind.RIGHT,
        )
        assertEquals(4, pts.size)
        // Vertex 0: (10, 20), Vertex 1: (10, 80), Vertex 2: (70, 80)
        assertEquals(10.0, pts[0].x, 1e-9)
        assertEquals(20.0, pts[0].y, 1e-9)
        assertEquals(10.0, pts[1].x, 1e-9)
        assertEquals(80.0, pts[1].y, 1e-9)
        assertEquals(70.0, pts[2].x, 1e-9)
        assertEquals(80.0, pts[2].y, 1e-9)
        // Angle at vertex 1 is 90 degrees
        val v01x = pts[0].x - pts[1].x
        val v01y = pts[0].y - pts[1].y
        val v21x = pts[2].x - pts[1].x
        val v21y = pts[2].y - pts[1].y
        val dot = v01x * v21x + v01y * v21y
        assertEquals(0.0, dot, 1e-9)
    }

    @Test fun isoscelesTriangleApexIsCenteredBetweenBaseVertices() {
        val pts = ShapeBuilder.build(
            ShapeKind.TRIANGLE, 0.0, 0.0, 100.0, 80.0, widthPt = 1.0,
            triangleKind = TriangleKind.ISOSCELES,
        )
        assertEquals(4, pts.size)
        // Vertex 0 (apex) should have x centered at 50.0
        assertEquals(50.0, pts[0].x, 1e-9)
        assertEquals(0.0, pts[0].y, 1e-9)
        // Base vertices
        assertEquals(100.0, pts[1].x, 1e-9)
        assertEquals(80.0, pts[1].y, 1e-9)
        assertEquals(0.0, pts[2].x, 1e-9)
        assertEquals(80.0, pts[2].y, 1e-9)
    }

    @Test fun scaleneTrianglePreservesInteriorAngles() {
        val pts = ShapeBuilder.build(
            ShapeKind.TRIANGLE, 0.0, 0.0, 200.0, 150.0, widthPt = 1.0,
            triangleKind = TriangleKind.SCALENE,
            angleA = 30.0, angleB = 60.0, angleC = 90.0,
        )
        assertEquals(4, pts.size)
        // Vertices are C, B, A, C (with closing point)
        val vC = pts[0]
        val vB = pts[1]
        val vA = pts[2]

        fun angleAt(vPrev: StrokePoint, vCurr: StrokePoint, vNext: StrokePoint): Double {
            val ux = vPrev.x - vCurr.x
            val uy = vPrev.y - vCurr.y
            val vx = vNext.x - vCurr.x
            val vy = vNext.y - vCurr.y
            val dot = ux * vx + uy * vy
            val magU = hypot(ux, uy)
            val magV = hypot(vx, vy)
            val cosTheta = (dot / (magU * magV)).coerceIn(-1.0, 1.0)
            return Math.toDegrees(kotlin.math.acos(cosTheta))
        }

        val calculatedAngleA = angleAt(vC, vA, vB)
        val calculatedAngleB = angleAt(vA, vB, vC)
        val calculatedAngleC = angleAt(vB, vC, vA)

        assertEquals(30.0, calculatedAngleA, 0.5)
        assertEquals(60.0, calculatedAngleB, 0.5)
        assertEquals(90.0, calculatedAngleC, 0.5)
    }

    // --- the trapezoid ------------------------------------------------------------------------

    @Test fun isoscelesTrapezoidSpansTheDragBaseWithAHalfWidthTopCentredOverIt() {
        val pts = ShapeBuilder.build(ShapeKind.TRAPEZOID, 0.0, 0.0, 100.0, 80.0, widthPt = 2.0)
        assertEquals("four corners plus the closing point", 5, pts.size)
        assertEquals(pts.first().x, pts.last().x, 1e-9)
        assertEquals(pts.first().y, pts.last().y, 1e-9)
        // The longer (bottom) base is the drag's whole width...
        assertEquals(0.0, pts[0].x, 1e-9); assertEquals(80.0, pts[0].y, 1e-9)
        assertEquals(100.0, pts[1].x, 1e-9); assertEquals(80.0, pts[1].y, 1e-9)
        // ...and the shorter one half of it, centred, so the two legs are equal (isosceles).
        assertEquals(75.0, pts[2].x, 1e-9); assertEquals(0.0, pts[2].y, 1e-9)
        assertEquals(25.0, pts[3].x, 1e-9); assertEquals(0.0, pts[3].y, 1e-9)
        assertTrue("every point carries the width setting", pts.all { it.width == 2.0 })
    }

    @Test fun rightTrapezoidHasAVerticalLeftLeg() {
        val pts = ShapeBuilder.build(
            ShapeKind.TRAPEZOID, 0.0, 0.0, 100.0, 80.0, widthPt = 1.0,
            trapezoidKind = TrapezoidKind.RIGHT,
        )
        assertEquals(5, pts.size)
        // The left leg joins (0,80) to (0,0): a right angle at each end of it.
        assertEquals(0.0, pts[0].x, 1e-9)
        assertEquals(0.0, pts[3].x, 1e-9)
        assertEquals(
            "the bottom-left corner is square",
            90.0,
            angleAtDegrees(vPrev = pts[3], vertex = pts[0], vNext = pts[1]),
            1e-6,
        )
        assertEquals(
            "the top-left corner is square",
            90.0,
            angleAtDegrees(vPrev = pts[2], vertex = pts[3], vNext = pts[0]),
            1e-6,
        )
        // Half-width shorter base, so it is a trapezoid and not a rectangle.
        assertEquals(50.0, pts[2].x, 1e-9)
    }

    @Test fun scaleneTrapezoidKeepsTheConfiguredBaseAngles() {
        val pts = ShapeBuilder.build(
            ShapeKind.TRAPEZOID, 0.0, 0.0, 200.0, 120.0, widthPt = 1.0,
            trapezoidKind = TrapezoidKind.SCALENE,
            trapezoidAngleA = 70.0, trapezoidAngleB = 55.0,
        )
        assertEquals(5, pts.size)
        val bl = pts[0]; val br = pts[1]; val tr = pts[2]; val tl = pts[3]
        assertEquals("left base angle", 70.0, angleAtDegrees(vPrev = tl, vertex = bl, vNext = br), 0.5)
        assertEquals("right base angle", 55.0, angleAtDegrees(vPrev = tr, vertex = br, vNext = bl), 0.5)
        // Unequal base angles mean unequal legs: four different sides, a genuine scalene trapezoid.
        assertNotEquals("the legs differ", hypot(tl.x - bl.x, tl.y - bl.y), hypot(tr.x - br.x, tr.y - br.y))
        // Both parallel sides stay horizontal, which is what makes it a trapezoid.
        assertEquals(bl.y, br.y, 1e-9)
        assertEquals(tl.y, tr.y, 1e-9)
    }

    @Test fun scaleneTrapezoidStaysInsideItsDragEvenWithObtuseBaseAngles() {
        // 150/160 degrees make legs that lean far outward: the figure must be fitted, not spilled.
        val pts = ShapeBuilder.build(
            ShapeKind.TRAPEZOID, 10.0, 20.0, 110.0, 120.0, widthPt = 1.0,
            trapezoidKind = TrapezoidKind.SCALENE,
            trapezoidAngleA = 150.0, trapezoidAngleB = 160.0,
        )
        assertTrue("x stays inside the drag", pts.all { it.x >= 10.0 - 1e-6 && it.x <= 110.0 + 1e-6 })
        assertTrue("y stays inside the drag", pts.all { it.y >= 20.0 - 1e-6 && it.y <= 120.0 + 1e-6 })
        assertEquals(
            "left base angle survives the fit",
            150.0,
            angleAtDegrees(vPrev = pts[3], vertex = pts[0], vNext = pts[1]),
            0.5,
        )
        assertEquals(
            "right base angle survives the fit",
            160.0,
            angleAtDegrees(vPrev = pts[2], vertex = pts[1], vNext = pts[0]),
            0.5,
        )
    }

    @Test fun aTrapezoidDraggedBackwardsIsTheSameFigure() {
        val forward = ShapeBuilder.build(ShapeKind.TRAPEZOID, 0.0, 0.0, 100.0, 80.0, widthPt = 1.0)
        val backward = ShapeBuilder.build(ShapeKind.TRAPEZOID, 100.0, 80.0, 0.0, 0.0, widthPt = 1.0)
        assertEquals(forward.size, backward.size)
        forward.forEachIndexed { i, p ->
            assertEquals(p.x, backward[i].x, 1e-9)
            assertEquals(p.y, backward[i].y, 1e-9)
        }
    }

    @Test fun aDegenerateDragFallsBackToALine() {
        val pts = ShapeBuilder.build(ShapeKind.TRAPEZOID, 5.0, 5.0, 5.0, 5.0, widthPt = 1.0)
        assertEquals(2, pts.size)
    }

    /** The interior angle at [vertex], in degrees, from the two neighbours. */
    private fun angleAtDegrees(vPrev: StrokePoint, vertex: StrokePoint, vNext: StrokePoint): Double {
        val ux = vPrev.x - vertex.x; val uy = vPrev.y - vertex.y
        val vx = vNext.x - vertex.x; val vy = vNext.y - vertex.y
        val cosTheta = ((ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy))).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cosTheta))
    }
}

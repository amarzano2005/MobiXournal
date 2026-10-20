package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.hypot

/**
 * The rules `FigureGeometry` states: **a head is sized by the pen, not by the shaft**.
 *
 * Every assertion here failed under the geometry this replaced, which took the head as a fraction of
 * the arrow's own length — a short arrow wearing a head as big as itself, a long one wearing a
 * different head from its neighbour, and a wide, flat coordinate diagram wearing one big head and one
 * small one.
 */
class FigureGeometryTest {

    private fun distance(a: StrokePoint, b: StrokePoint) = hypot(a.x - b.x, a.y - b.y)

    private fun headOf(pts: List<StrokePoint>, tip: Int, barb: Int) = distance(pts[tip], pts[barb])

    /** The V's opening, in degrees, from a head's tip and its two barbs (`tip, a, tip, b`). */
    private fun opening(pts: List<StrokePoint>, tip: Int, a: Int, b: Int): Double {
        val ta = pts[a].x - pts[tip].x to pts[a].y - pts[tip].y
        val tb = pts[b].x - pts[tip].x to pts[b].y - pts[tip].y
        val dot = ta.first * tb.first + ta.second * tb.second
        val len = hypot(ta.first, ta.second) * hypot(tb.first, tb.second)
        return Math.toDegrees(acos((dot / len).coerceIn(-1.0, 1.0)))
    }

    @Test
    fun aHeadIsSixTimesThePenInsideItsBounds() {
        // The pen's own nib: six widths, floored at 10 pt so a hairline still shows a head, capped at
        // 30 pt so a fat pen doesn't grow a beak.
        assertEquals(10.0, FigureGeometry.headPt(1.0, 400.0), 1e-9)
        assertEquals(12.0, FigureGeometry.headPt(2.0, 400.0), 1e-9)
        assertEquals(18.0, FigureGeometry.headPt(3.0, 400.0), 1e-9)
        assertEquals(30.0, FigureGeometry.headPt(10.0, 400.0), 1e-9)
    }

    @Test
    fun aHeadNeverTakesMoreThanItsShareOfTheShaft() {
        // A 12 pt head on a 20 pt shaft would be most of the figure; the share cap stops it.
        assertEquals(FigureGeometry.HEAD_MAX_SHARE_OF_SHAFT * 20.0, FigureGeometry.headPt(2.0, 20.0), 1e-9)
        assertEquals(0.0, FigureGeometry.headPt(2.0, 0.0), 1e-9)
    }

    @Test
    fun twoArrowsOfDifferentLengthsDrawnWithOnePenWearTheSameHead() {
        val short = FigureGeometry.arrow(0.0, 0.0, 60.0, 0.0, 2.0)
        val long = FigureGeometry.arrow(0.0, 0.0, 400.0, 0.0, 2.0)

        assertEquals(headOf(short, 1, 2), headOf(long, 1, 2), 1e-9)
        assertEquals(12.0, headOf(long, 1, 2), 1e-9)
    }

    @Test
    fun everyFigureDrawnWithOnePenWearsTheSameHead() {
        val pen = 3.0
        val arrow = FigureGeometry.arrow(0.0, 0.0, 200.0, 0.0, pen)
        val double = FigureGeometry.doubleArrow(0.0, 0.0, 200.0, 0.0, pen)
        val axes = FigureGeometry.axes(0.0, 0.0, 200.0, 0.0, 0.0, -120.0, pen)

        val arrowHead = headOf(arrow, 1, 2)
        assertEquals(arrowHead, headOf(double, 4, 5), 1e-9)
        assertEquals(arrowHead, headOf(axes, 5, 6), 1e-9)
        assertEquals(arrowHead, headOf(axes, 1, 0), 1e-9)
    }

    @Test
    fun aWideFlatDiagramWearsTwoEqualHeads() {
        // The old diagram sized each head from its own axis, so this drag (a long x, a short y) came
        // out with a 30 pt head above a 10 pt one. One pen, one head, whatever the aspect.
        val pts = FigureGeometry.axes(0.0, 0.0, 300.0, 0.0, 0.0, -20.0, 2.0)
        val xHead = headOf(pts, 5, 6)
        val yHead = headOf(pts, 1, 0)

        assertEquals(xHead, yHead, 1e-9)
        // …and the short axis sets the size, so neither head can outgrow its own shaft.
        assertEquals(FigureGeometry.headPt(2.0, 20.0), xHead, 1e-9)
    }

    @Test
    fun theFiguresKeepTheirVertexLayouts() {
        assertEquals(5, FigureGeometry.arrow(0.0, 0.0, 100.0, 0.0, 1.0).size)
        assertEquals(8, FigureGeometry.doubleArrow(0.0, 0.0, 100.0, 0.0, 1.0).size)
        val axes = FigureGeometry.axes(5.0, 5.0, 100.0, 5.0, 5.0, -50.0, 1.0)
        assertEquals(9, axes.size)
        // The origin is the vertex both axes share.
        assertEquals(5.0, axes[4].x, 1e-9)
        assertEquals(5.0, axes[4].y, 1e-9)
    }

    @Test
    fun theOpeningIsTheToolsOwnV() {
        val pts = FigureGeometry.arrow(0.0, 0.0, 100.0, 0.0, 2.0)
        assertEquals(2 * FigureGeometry.HALF_ANGLE_DEG, opening(pts, 1, 2, 4), 1e-9)
        assertTrue("a tighter V than the 56° it replaced", 2 * FigureGeometry.HALF_ANGLE_DEG < 56.0)
    }

    @Test
    fun aBarbsAreMirroredAboutTheShaft() {
        val pts = FigureGeometry.arrow(0.0, 0.0, 100.0, 0.0, 2.0)
        assertEquals(pts[2].y - pts[1].y, -(pts[4].y - pts[1].y), 1e-9)
        assertTrue("barbs sit behind the tip", pts[2].x < pts[1].x && pts[4].x < pts[1].x)
    }

    @Test
    fun aDegenerateDragIsJustTheLineLeftOfIt() {
        assertEquals(2, FigureGeometry.arrow(4.0, 4.0, 4.0, 4.0, 1.0).size)
        assertEquals(2, FigureGeometry.doubleArrow(4.0, 4.0, 4.0, 4.0, 1.0).size)
        assertEquals(2, FigureGeometry.axes(4.0, 4.0, 4.0, 4.0, 4.0, 4.0, 1.0).size)
    }

    @Test
    fun aDiagramDraggedAlongOneAxisOnlyIsASingleArrow() {
        // A horizontal drag means an x axis; there is no second axis to give a head to, and what is
        // left is exactly the tool's arrow — which is also what a resize reads it back as.
        val flat = FigureGeometry.axes(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 2.0)
        assertEquals(5, flat.size)
        assertEquals(100.0, flat[1].x, 1e-9)
        assertEquals(0.0, flat[0].y, 1e-9)
    }
}

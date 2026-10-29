package com.mobixournal.desktop

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * The live preview's cache, which is what makes a frame that only redraws cost nothing.
 *
 * Its key has to change whenever the draft's **geometry** does. That is easy for a pen (the stroke
 * grows) and for an open figure (its far end follows the pointer), and it is where a closed figure used
 * to go wrong: a rectangle's and an ellipse's last vertex is their first one — the corner the press
 * landed on — so a key reading the last sample matched every frame of the drag and the preview was the
 * first frame's few points wide until the lift. These tests drive the cache the way the canvas does and
 * ask for a rebuild on each drag.
 */
class StrokePathCacheTest {

    @Before
    fun reset() {
        StrokePathCache.clear()
    }

    /** A draft's shapes, asked for the way `PageCanvasOverlay` asks. */
    private fun shapes(points: List<StrokePoint>) = StrokePathCache.draft(
        identity = DRAFT,
        size = points.size,
        tool = Tool.PEN,
        lineStyle = LineStyle.PLAIN,
        points = points,
        scale = 1f,
    )

    private fun rectangle(toX: Double, toY: Double): List<StrokePoint> =
        ShapeBuilder.build(ShapeKind.RECTANGLE, 10.0, 10.0, toX, toY, widthPt = 2.0)

    @Test
    fun `a rectangle being dragged is rebuilt on every frame`() {
        val small = rectangle(60.0, 40.0)
        val large = rectangle(300.0, 200.0)
        assertEquals("the same length, so a length-only key would hit", small.size, large.size)

        shapes(small)
        val first = shapes(small)
        val second = shapes(large)

        assertSame("an unchanged draft is the same geometry", first, shapes(small))
        assertNotSame("a dragged rectangle is a new outline", first, second)
        assertEquals("two geometries built: the first frame and the drag", 2, StrokePathCache.builds)
    }

    @Test
    fun `an ellipse being dragged is rebuilt too`() {
        val first = shapes(ShapeBuilder.build(ShapeKind.ELLIPSE, 10.0, 10.0, 110.0, 70.0, widthPt = 2.0))
        val second = shapes(ShapeBuilder.build(ShapeKind.ELLIPSE, 10.0, 10.0, 400.0, 70.0, widthPt = 2.0))
        assertNotSame("an ellipse is closed on its first vertex as well", first, second)
    }

    @Test
    fun `an open figure follows its far end`() {
        val first = shapes(ShapeBuilder.build(ShapeKind.LINE, 10.0, 10.0, 100.0, 10.0, widthPt = 2.0))
        val second = shapes(ShapeBuilder.build(ShapeKind.LINE, 10.0, 10.0, 260.0, 10.0, widthPt = 2.0))
        assertNotSame(first, second)
    }

    private companion object {
        /** The identity a draft lives in — the canvas's own state list, in the window. */
        val DRAFT = Any()
    }
}

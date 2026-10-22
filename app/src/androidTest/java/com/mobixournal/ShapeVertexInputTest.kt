package com.mobixournal

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.ElementBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/**
 * On-device verification of the **living figure** path (`docs/drawing-tools.md` → *Living figures*):
 * a closed polygon drawn with the pen is kept as a `ParametricShapeElement` rather than as ink, and a
 * corner is then dragged by its own handle with the stylus.
 *
 * Both halves need real `MotionEvent`s — a dense freehand stroke for the recogniser, and a touch that
 * lands on a specific corner handle — so they live here rather than in a JVM test. The vertex maths
 * itself is unit-tested in `ParametricShapesTest`; this proves the canvas is wired to it.
 */
@RunWith(AndroidJUnit4::class)
class ShapeVertexInputTest {

    /** A triangle drawn with the pen lands as one living shape, with no stroke left behind. */
    @Test
    fun aClosedFigureDrawnWithThePenBecomesALivingShape() = onView { view ->
        view.tool = Tool.PEN
        drawTriangle(view)

        val elements = elementsOf(view)
        assertEquals("the ink is one element, not a pile of strokes: $elements", 1, elements.size)
        val shape = elements.single() as? ParametricShapeElement
        assertNotNull("a closed figure is kept as geometry: $elements", shape)
        assertEquals("three corners, no closing repeat", 3, shape!!.vertices.size)
        assertEquals(0, elementsOf(view).filterIsInstance<Stroke>().size)

        // The box is the corners' extent, so the figure is pickable where it is drawn.
        val bounds = ElementBounds.of(shape)
        assertTrue("the shape has a real box: $bounds", bounds.width > 100.0 && bounds.height > 100.0)
        assertTrue(ElementBounds.isHitTestable(shape))
    }

    /**
     * A figure whose stroke was **held** (dwell-snapped) before lift-off is committed as the shape the
     * hold found, not as a fresh reading of its own clean geometry.
     *
     * The snapped square is four corners plus the closing repeat; the recogniser's polygon fit treats
     * that as too few points for four sides and falls through to the circle match, so re-reading it at
     * lift-off turned a held square into a circle — a stroke, and no figure to edit.
     */
    @Test
    fun aHeldSquareIsCommittedAsTheSquareTheDwellFound() = onView { view ->
        view.tool = Tool.PEN
        drawWobblySquareAndHold(view)

        val shapes = elementsOf(view).filterIsInstance<ParametricShapeElement>()
        assertEquals("the held square is one living figure, not a circle stroke", 1, shapes.size)
        assertEquals("four corners", 4, shapes.single().vertices.size)
        assertEquals(0, elementsOf(view).filterIsInstance<Stroke>().size)
    }

    /** Dragging a corner handle moves **that** corner and leaves the other two where they were. */
    @Test
    fun draggingACornerHandleDeformsTheShape() = onView { view ->
        view.tool = Tool.PEN
        drawTriangle(view)
        val before = shapeOf(view)

        // Select it with a tap inside the figure (a marquee with no drag picks the topmost element).
        view.selectMode = true
        val inside = centroid(before)
        tap(view, pageMap().x(inside.first), pageMap().y(inside.second))

        // Grab the corner nearest the page's top-left, drag it out along the page's diagonal.
        val handleIndex = before.vertices.indices.minByOrNull { before.vertices[it].x + before.vertices[it].y }!!
        val handle = before.vertices[handleIndex]
        val grabX = pageMap().x(handle.x)
        val grabY = pageMap().y(handle.y)
        val dropX = grabX - 60f
        val dropY = grabY - 90f
        drag(view, grabX, grabY, dropX, dropY)

        val after = shapeOf(view)
        assertEquals("the figure is still a triangle", 3, after.vertices.size)
        val moved = after.vertices[handleIndex]
        assertTrue(
            "the grabbed corner followed the pen: ${handle.x},${handle.y} -> ${moved.x},${moved.y}",
            hypot(moved.x - pageMap().ptX(dropX), moved.y - pageMap().ptY(dropY)) < 12.0,
        )
        // The corners that were not grabbed did not move.
        for (i in after.vertices.indices) {
            if (i == handleIndex) continue
            assertEquals("corner $i stays put", before.vertices[i].x, after.vertices[i].x, 1e-6)
            assertEquals(before.vertices[i].y, after.vertices[i].y, 1e-6)
        }
        // And the element's box was recomputed from the new corners, not left where it was.
        assertTrue(
            "the box grew with the dragged corner",
            ElementBounds.of(after).expand(0.5).contains(moved.x, moved.y) &&
                ElementBounds.of(after) != ElementBounds.of(before),
        )
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** A triangle drawn densely enough for the recogniser, in page pt. */
    private fun drawTriangle(view: DrawingSurfaceView) {
        val corners = listOf(100.0 to 120.0, 360.0 to 120.0, 230.0 to 340.0)
        val tokens = ArrayList<Pair<Float, Float>>()
        for (c in corners.indices) {
            val (x0, y0) = corners[c]
            val (x1, y1) = corners[(c + 1) % corners.size]
            for (i in 0 until 24) {
                val t = i / 24.0
                tokens += pageMap().x(x0 + (x1 - x0) * t) to pageMap().y(y0 + (y1 - y0) * t)
            }
        }
        tokens += pageMap().x(corners[0].first) to pageMap().y(corners[0].second)
        stylus(view, tokens)
    }

    /**
     * A hand-drawn square with a **rest** before the pen leaves the glass: the stylus stays down for
     * longer than [HOLD_MS], which is what arms and fires the dwell, and the lift then commits.
     */
    private fun drawWobblySquareAndHold(view: DrawingSurfaceView) {
        val corners = listOf(120.0 to 120.0, 380.0 to 120.0, 380.0 to 330.0, 120.0 to 330.0)
        val tokens = ArrayList<Pair<Float, Float>>()
        for (c in corners.indices) {
            val (x0, y0) = corners[c]
            val (x1, y1) = corners[(c + 1) % corners.size]
            for (i in 0 until 24) {
                val t = i / 24.0
                // A millimetre of wobble, so the stroke is hand-drawn rather than exact.
                val wobble = if (i % 3 == 0) 1.5 else 0.0
                tokens += pageMap().x(x0 + (x1 - x0) * t + wobble) to
                    pageMap().y(y0 + (y1 - y0) * t + wobble)
            }
        }
        tokens += pageMap().x(corners[0].first) to pageMap().y(corners[0].second)
        stylusHeld(view, tokens)
    }

    private fun shapeOf(view: DrawingSurfaceView): ParametricShapeElement =
        elementsOf(view).filterIsInstance<ParametricShapeElement>().single()

    private fun centroid(shape: ParametricShapeElement): Pair<Double, Double> =
        shape.vertices.map { it.x }.average() to shape.vertices.map { it.y }.average()

    private fun tap(view: DrawingSurfaceView, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, SOURCE_STYLUS, 0,
            )
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    /** Down on the handle, a few moves towards the drop, then up. */
    private fun drag(view: DrawingSurfaceView, x0: Float, y0: Float, x1: Float, y1: Float) {
        val path = (1..5).map { i ->
            val t = i / 5f
            x0 + (x1 - x0) * t to y0 + (y1 - y0) * t
        }
        stylus(view, listOf(x0 to y0) + path, liftAt = path.last())
    }

    /**
     * A stylus gesture that **rests** on the glass at the end for longer than the dwell threshold
     * before lifting, which is the gesture hold-to-snap exists for.
     */
    private fun stylusHeld(view: DrawingSurfaceView, points: List<Pair<Float, Float>>) {
        val downTime = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        fun send(action: Int, x: Float, y: Float) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1f
                size = 1f
            })
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, SOURCE_STYLUS, 0,
            )
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        val (x0, y0) = points.first()
        send(MotionEvent.ACTION_DOWN, x0, y0)
        for ((x, y) in points.drop(1)) send(MotionEvent.ACTION_MOVE, x, y)
        // The tip rests: the view's `postDelayed` timer needs the main looper to run, so the wait has
        // to happen here on the test thread while it is idle.
        SystemClock.sleep(HOLD_MS)
        send(MotionEvent.ACTION_UP, points.last().first, points.last().second)
    }

    /** A whole stylus gesture through view px, ending with a lift at [liftAt] (default: the last). */
    private fun stylus(view: DrawingSurfaceView, points: List<Pair<Float, Float>>, liftAt: Pair<Float, Float>? = null) {
        val downTime = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun send(action: Int, x: Float, y: Float) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, SOURCE_STYLUS, 0,
            )
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        val (x0, y0) = points.first()
        send(MotionEvent.ACTION_DOWN, x0, y0)
        for ((x, y) in points.drop(1)) send(MotionEvent.ACTION_MOVE, x, y)
        val (xn, yn) = liftAt ?: points.last()
        send(MotionEvent.ACTION_UP, xn, yn)
    }

    private fun elementsOf(view: DrawingSurfaceView): List<Element> =
        view.toDocument().pages.flatMap { p -> p.layers.flatMap { it.elements } }

    /** Page pt -> view px for the single page fitted to the view, mirroring `PageStacker.stack`. */
    private class PageMap(val scale: Float, val left: Float, val top: Float) {
        fun x(pageX: Double): Float = left + (pageX * scale).toFloat()
        fun y(pageY: Double): Float = top + (pageY * scale).toFloat()
        fun ptX(viewX: Float): Double = (viewX - left) / scale.toDouble()
        fun ptY(viewY: Float): Double = (viewY - top) / scale.toDouble()
    }

    private fun pageMap(): PageMap {
        val scale = (VIEW_W / A4_WIDTH_PT).toFloat()
        val left = (maxOf(VIEW_W.toFloat(), A4_WIDTH_PT.toFloat() * scale) - A4_WIDTH_PT.toFloat() * scale) / 2f
        // `DrawingSurfaceDefaults.GAP_PX`, which is private to the render package.
        return PageMap(scale, left, GAP_PX)
    }

    private fun onView(body: (DrawingSurfaceView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = DrawingSurfaceView(instrumentation.targetContext)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_H, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, VIEW_W, VIEW_H)
            view.load(blankDocument())
            body(view)
        }
        instrumentation.waitForIdleSync()
    }

    private fun blankDocument(): Document = Document(
        pages = listOf(
            Page(
                A4_WIDTH_PT, A4_HEIGHT_PT,
                Background.Solid(0xFFFFFFFF.toInt(), "graph"),
                listOf(Layer(emptyList())),
            ),
        ),
    )

    private companion object {
        const val A4_WIDTH_PT = 595.276
        const val A4_HEIGHT_PT = 841.89
        const val VIEW_W = 800
        const val VIEW_H = 1400
        const val SOURCE_STYLUS = 0x00004002

        /** Comfortably past `SHAPE_DWELL_MS` (300 ms), which is internal to the render package. */
        const val HOLD_MS = 600L
        /** Mirrors `DrawingSurfaceDefaults.GAP_PX`. */
        const val GAP_PX = 24f
    }
}

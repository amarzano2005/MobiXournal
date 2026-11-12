package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A line and an arrow are the two families with **one** degree of freedom: their shaft. What is worth
 * pinning is that the shaft is found from the ink itself — a diagonal line included, which its box alone
 * would call a figure — and that its factor is read from the end that stays still.
 */
class SelectionShaftTest {

    private fun stroke(vararg pts: Pair<Double, Double>) = Stroke(
        tool = Tool.PEN,
        color = 0,
        capStyle = null,
        points = pts.map { StrokePoint(it.first, it.second, 1.0) },
        uniformWidth = true,
        lineStyle = LineStyle.PLAIN,
    )

    @Test
    fun `a diagonal line's shaft runs from one end to the other`() {
        val shaft = shaftOf(stroke(100.0 to 100.0, 200.0 to 200.0))!!
        assertEquals(100.0, shaft.tailX, 1e-9)
        assertEquals(100.0, shaft.tailY, 1e-9)
        assertEquals(200.0, shaft.tipX, 1e-9)
        assertEquals(200.0, shaft.tipY, 1e-9)
        assertEquals(kotlin.math.hypot(100.0, 100.0), shaft.length, 1e-9)
    }

    @Test
    fun `an arrow's shaft is its tail and the tip of its head`() {
        // `FigureGeometry`'s layout: tail, tip, barbA, tip, barbB.
        val arrow = stroke(
            100.0 to 100.0, 300.0 to 100.0, 280.0 to 88.0, 300.0 to 100.0, 280.0 to 112.0,
        )
        val shaft = shaftOf(arrow)!!
        assertEquals(100.0, shaft.tailX, 1e-9)
        assertEquals(300.0, shaft.tipX, 1e-9)
        assertEquals(200.0, shaft.length, 1e-9)
    }

    @Test
    fun `a closed figure is not a shaft`() {
        val box = stroke(100.0 to 100.0, 200.0 to 100.0, 200.0 to 200.0, 100.0 to 200.0, 100.0 to 100.0)
        assertNull("a closed figure has no single axis", shaftOf(box))
        assertNull("nothing selected", shaftOf(null))
    }

    @Test
    fun `a drag along the shaft only lengthens it`() {
        // Grabbed the tip 200 pt from the tail and pulled it to 400: twice as long, same direction.
        val drag = shaftDrag(300.0, 100.0, 500.0, 100.0, 100.0, 100.0)
        assertEquals(2.0, drag.factor, 1e-9)
        assertEquals(0.0, drag.angle, 1e-9)
    }

    @Test
    fun `a drag across the shaft re-aims it and keeps its length`() {
        // Grabbed the tip 100 pt right of the tail and pulled to 100 pt below it: a quarter turn.
        val drag = shaftDrag(200.0, 100.0, 100.0, 200.0, 100.0, 100.0)
        assertEquals(1.0, drag.factor, 1e-9)
        assertEquals(kotlin.math.PI / 2, drag.angle, 1e-9)
    }

    @Test
    fun `the turn ring hangs off the middle of the ink, on the upper side`() {
        val turn = shaftTurn(SelectionShaft(100.0, 300.0, 300.0, 300.0), gapPt = 20.0)
        assertEquals("the pivot is the shaft's middle", 200.0, turn.pivotX, 1e-9)
        assertEquals(300.0, turn.pivotY, 1e-9)
        assertEquals("a horizontal line's ring sits above it", 200.0, turn.ringX, 1e-9)
        assertEquals(280.0, turn.ringY, 1e-9)
    }

    @Test
    fun `a free drag maps the grabbed end onto the pointer and leaves the pen's width`() {
        val arrow = stroke(
            100.0 to 100.0, 300.0 to 100.0, 280.0 to 88.0, 300.0 to 100.0, 280.0 to 112.0,
        )
        val page = Page(
            width = 595.0,
            height = 842.0,
            background = Background.Solid(-1, "plain"),
            layers = listOf(Layer(listOf(arrow))),
        )
        val selection = PageSelection(
            SelectionRegion.Rect(ElementBounds.of(arrow)),
            setOf(ElementRef(0, 0)),
        )
        val drag = shaftDrag(300.0, 100.0, 300.0, 200.0, 100.0, 100.0)
        val (edited, _) = SelectionEdit.similarity(
            page, selection, drag.factor, drag.angle, 100.0, 100.0,
        )
        val out = edited.layers[0].elements[0] as Stroke
        assertEquals("the tail is the anchor", 100.0, out.points[0].x, 1e-6)
        assertEquals(100.0, out.points[0].y, 1e-6)
        assertEquals("the tip follows the pointer", 300.0, out.points[1].x, 1e-6)
        assertEquals(200.0, out.points[1].y, 1e-6)
        assertEquals("the pen's width is untouched", 1.0, out.points[1].width, 1e-9)
    }

    @Test
    fun `the factor is the pointer's distance from the anchored end`() {
        val shaft = SelectionShaft(0.0, 0.0, 100.0, 0.0)
        // Grabbed 100 pt from the tail, dragged to 200: twice as long.
        assertEquals(2.0, shaft.factorAt(200.0, 0.0, 0.0, 0.0, 100.0), 1e-9)
        // With the tip as the anchor, a pointer pulled to 25 pt of it halves a shaft grabbed at 50.
        assertEquals(0.5, shaft.factorAt(75.0, 0.0, 100.0, 0.0, 50.0), 1e-9)
    }
}

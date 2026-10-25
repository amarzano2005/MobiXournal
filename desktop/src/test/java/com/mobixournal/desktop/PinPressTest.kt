package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.rectRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **A part is picked up by its pin**, not only by its middle.
 *
 * A pin sits at a part's very end, which is where the hand aims when two parts have to be joined — and it
 * is the one place the ink picker can miss, because a press a few points past the end is over bare paper.
 * The window then traced a marquee and the part stayed where it was, so the reader saw pins that "did not
 * attach": the magnet was fine, but nothing had been picked up to move.
 *
 * Pure: the page and the press point, no scene.
 */
class PinPressTest {

    private fun page(elements: List<com.mobixournal.format.model.Element>): Page = Page(
        width = 595.276,
        height = 841.89,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(elements)),
    )

    private fun resistor(x: Double = 200.0, y: Double = 200.0): CircuitElement = CircuitElement(
        symbol = ShapeKind.RESISTOR.name,
        positionX = x,
        positionY = y,
        pins = listOf(CircuitPin("p1", -40.0, 0.0), CircuitPin("p2", 40.0, 0.0)),
    )

    @Test
    fun `a press past the end of a part still catches its pin`() {
        val page = page(listOf(resistor()))
        // 240 is the right-hand pin; six points past it is bare paper as far as the ink is concerned.
        assertEquals(ElementRef(0, 0), pinnedElementAt(page, 246.0, 200.0, radiusPt = 9.0))
        assertNull("out of the magnet's reach is not a catch", pinnedElementAt(page, 260.0, 200.0, radiusPt = 9.0))
    }

    @Test
    fun `ink under the pointer is still the first answer`() {
        val script = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(220.0, 300.0, 1.0), StrokePoint(260.0, 300.0, 1.0)),
            uniformWidth = true,
        )
        // The resistor's pin is at (240, 300); the stroke sits over it and must win the pick.
        val page = page(listOf(resistor(y = 300.0), script))
        val picked = pickForDrag(page, 243.0, 300.0, radiusPt = 9.0)
        assertEquals("the pen's own stroke is what is under the pointer", ElementRef(0, 1), picked)
    }

    @Test
    fun `a selected part keeps its pins marked after it is placed`() {
        val page = page(listOf(resistor()))
        assertEquals(
            ShapeKind.RESISTOR,
            selectionConnectorKind(page, PageSelection(rectRegion(160.0, 190.0, 240.0, 210.0), setOf(ElementRef(0, 0)))),
        )
        assertNull(
            "an empty selection marks nothing",
            selectionConnectorKind(page, PageSelection(rectRegion(0.0, 0.0, 10.0, 10.0), emptySet())),
        )
    }
}

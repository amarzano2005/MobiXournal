package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a circuit component's value is written: **above the drawn symbol or below it**, centred on
 * it, never across it.
 *
 * The label used to be dropped a dozen points off the axis' middle with its top-left corner on the
 * midpoint, so a rotated component — whose symbol turns but whose label does not — had its value
 * lying over the body. These tests pin the two things that fix it: the label's box clears the symbol
 * on the side it was asked for, and it stays clear once the component is turned.
 */
class CircuitLabelTest {

    private fun resistor(
        below: Boolean = false,
        rotation: Double = 0.0,
        value: String = "10 kΩ",
    ) = CircuitElement(
        symbol = "RESISTOR",
        positionX = 200.0,
        positionY = 200.0,
        rotation = rotation,
        value = value,
        labelBelow = below,
        pins = listOf(CircuitPin("p1", -30.0, 0.0), CircuitPin("p2", 30.0, 0.0)),
        id = "ckt_test",
    )

    /** The box the symbol's own strokes occupy, in pt. */
    private fun symbolBounds(circuit: CircuitElement): Bounds {
        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        for (element in CircuitInk.ink(circuit)!!) {
            if (element !is Stroke) continue
            for (p in element.points) {
                if (p.x < left) left = p.x
                if (p.x > right) right = p.x
                if (p.y < top) top = p.y
                if (p.y > bottom) bottom = p.y
            }
        }
        return Bounds(left, top, right, bottom)
    }

    private fun label(circuit: CircuitElement): TextElement =
        CircuitInk.ink(circuit)!!.filterIsInstance<TextElement>().single()

    /** The label box, as the bounds code estimates it. */
    private fun labelBox(text: TextElement): Bounds = Bounds(
        text.x,
        text.y,
        text.x + text.content.length * text.size * InkTokens.FIGURE_LABEL_CHAR_W,
        text.y + text.size * InkTokens.FIGURE_LABEL_LINE_H,
    )

    @Test
    fun `the label is above the symbol by default`() {
        val circuit = resistor()
        val symbol = symbolBounds(circuit)
        val box = labelBox(label(circuit))
        assertTrue("the label's bottom (${box.bottom}) clears the symbol top (${symbol.top})", box.bottom <= symbol.top)
        assertTrue("and sits close to it", symbol.top - box.bottom <= 4.0)
    }

    @Test
    fun `below puts the label under the symbol instead`() {
        val circuit = resistor(below = true)
        val symbol = symbolBounds(circuit)
        val box = labelBox(label(circuit))
        assertTrue("the label starts under the symbol (${box.top} vs ${symbol.bottom})", box.top >= symbol.bottom)
        assertTrue("and sits close to it", box.top - symbol.bottom <= 4.0)
    }

    @Test
    fun `the two sides differ only in which side of the symbol the label is on`() {
        val above = resistor()
        val below = resistor(below = true)
        val aboveBox = labelBox(label(above))
        val belowBox = labelBox(label(below))
        assertEquals(
            "both are centred on the symbol",
            (aboveBox.left + aboveBox.right) / 2,
            (belowBox.left + belowBox.right) / 2,
            1e-9,
        )
        assertTrue("and they are on opposite sides", aboveBox.bottom < belowBox.top)
    }

    @Test
    fun `a turned component keeps its label beside the turned symbol`() {
        // A resistor turned 90° is drawn vertically: the label must read above or below *that* box,
        // which is the case the old midpoint offset got wrong.
        for (rotation in listOf(90.0, 180.0, 270.0, 45.0)) {
            for (below in listOf(false, true)) {
                val circuit = resistor(below = below, rotation = rotation)
                val symbol = symbolBounds(circuit)
                val box = labelBox(label(circuit))
                val overlaps = box.left < symbol.right && box.right > symbol.left &&
                    box.top < symbol.bottom && box.bottom > symbol.top
                assertTrue(
                    "rotation $rotation, below=$below: the label overlaps the symbol",
                    !overlaps,
                )
                if (below) {
                    assertTrue("rotation $rotation: below means under", box.top >= symbol.bottom)
                } else {
                    assertTrue("rotation $rotation: above means over", box.bottom <= symbol.top)
                }
            }
        }
    }

    @Test
    fun `a component without a value writes no label`() {
        val plain = resistor().copy(value = null)
        assertTrue(CircuitInk.ink(plain)!!.none { it is TextElement })
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a circuit component's value is written: **above, below, left or right of the drawn symbol**,
 * never across it ([CircuitLabelSide]).
 *
 * The label used to be dropped a dozen points off the axis' middle with its top-left corner on the
 * midpoint, so a rotated component — whose symbol turns but whose label does not — had its value
 * lying over the body. These tests pin the two things that fix it: the label's box clears the symbol
 * on the side it was asked for, and it stays clear once the component is turned.
 */
class CircuitLabelTest {

    private fun resistor(
        side: CircuitLabelSide = CircuitLabelSide.ABOVE,
        rotation: Double = 0.0,
        value: String = "10 kΩ",
    ) = CircuitElement(
        symbol = "RESISTOR",
        positionX = 200.0,
        positionY = 200.0,
        rotation = rotation,
        value = value,
        labelSide = side,
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

    /** The label box, as the bounds code estimates it — what [ElementBounds] calls its box. */
    private fun labelBox(text: TextElement): Bounds = ElementBounds.of(text)

    private fun overlaps(a: Bounds, b: Bounds): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    @Test
    fun `the label is above the symbol by default`() {
        val circuit = resistor()
        val symbol = symbolBounds(circuit)
        val box = labelBox(label(circuit))
        assertEquals(CircuitLabelSide.ABOVE, circuit.labelSide)
        assertTrue("the label's bottom (${box.bottom}) clears the symbol top (${symbol.top})", box.bottom <= symbol.top)
        assertTrue("and sits close to it", symbol.top - box.bottom <= 4.0)
    }

    @Test
    fun `each side puts the label on that side of the drawn symbol`() {
        for (rotation in listOf(0.0, 45.0, 90.0, 180.0, 270.0)) {
            for (side in CircuitLabelSide.entries) {
                val circuit = resistor(side = side, rotation = rotation)
                val symbol = symbolBounds(circuit)
                val box = labelBox(label(circuit))
                assertTrue(
                    "$side at $rotation: the label overlaps the symbol",
                    !overlaps(box, symbol),
                )
                val where = "$side at $rotation"
                when (side) {
                    CircuitLabelSide.ABOVE -> assertTrue("$where: above means over the symbol", box.bottom <= symbol.top)
                    CircuitLabelSide.BELOW -> assertTrue("$where: below means under the symbol", box.top >= symbol.bottom)
                    CircuitLabelSide.LEFT -> assertTrue("$where: left means clear of it to the left", box.right <= symbol.left)
                    CircuitLabelSide.RIGHT -> assertTrue("$where: right means clear of it to the right", box.left >= symbol.right)
                }
            }
        }
    }

    @Test
    fun `above and below are centred across the symbol, left and right along it`() {
        val above = labelBox(label(resistor(CircuitLabelSide.ABOVE)))
        val below = labelBox(label(resistor(CircuitLabelSide.BELOW)))
        assertEquals(
            "above and below share the symbol's horizontal centre",
            (above.left + above.right) / 2,
            (below.left + below.right) / 2,
            1e-9,
        )
        val left = labelBox(label(resistor(CircuitLabelSide.LEFT)))
        val right = labelBox(label(resistor(CircuitLabelSide.RIGHT)))
        assertEquals(
            "left and right share the symbol's vertical centre",
            (left.top + left.bottom) / 2,
            (right.top + right.bottom) / 2,
            1e-9,
        )
        assertTrue("left is left of right", left.right < right.left)
    }

    @Test
    fun `a component without a value writes no label`() {
        val plain = resistor().copy(value = null)
        assertTrue(CircuitInk.ink(plain)!!.none { it is TextElement })
    }
}

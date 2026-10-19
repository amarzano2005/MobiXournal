package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CircuitSelectionTest {

    private fun sampleResistor(rotation: Double = 0.0): CircuitElement =
        CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            rotation = rotation,
            strokeWidth = 2.0,
            pins = listOf(
                CircuitPin("p1", -50.0, 0.0),
                CircuitPin("p2", 50.0, 0.0),
            ),
        )

    @Test
    fun circuitIsHitTestableAndComputesBoundingBox() {
        val resistor = sampleResistor(0.0)
        assertTrue(ElementBounds.isHitTestable(resistor))

        val bounds = ElementBounds.of(resistor)
        // With rotation 0, span is from ~50 to ~150 in X, centered at 100 in Y
        assertTrue("bounds encompass left pin", bounds.left <= 50.0)
        assertTrue("bounds encompass right pin", bounds.right >= 150.0)
        assertTrue("bounds centered in Y", bounds.top < 100.0 && bounds.bottom > 100.0)
    }

    @Test
    fun rotatedBoundingBoxTracksOrientation() {
        val r0 = sampleResistor(0.0)
        val r90 = sampleResistor(90.0)

        val b0 = ElementBounds.of(r0)
        val b90 = ElementBounds.of(r90)

        // b0 is wide (X-span ~100) and narrow in Y
        assertTrue(b0.width > b0.height * 2.0)

        // b90 is tall (Y-span ~100) and narrow in X
        assertTrue(b90.height > b90.width * 2.0)
        assertEquals("centers match in X", 100.0, (b90.left + b90.right) / 2.0, 1.0)
        assertEquals("centers match in Y", 100.0, (b90.top + b90.bottom) / 2.0, 1.0)
    }

    @Test
    fun tapPicksTopmostCircuit() {
        val resistor = sampleResistor(0.0)
        val page = Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(elements = listOf(resistor))),
        )

        // Tap right in the middle of the resistor
        val hit = SelectionTester.pickTopmost(page, 100.0, 100.0)
        assertNotNull("middle of the component is picked", hit)
        assertEquals(0, hit!!.layerIndex)
        assertEquals(0, hit.elementIndex)

        // Tap far away
        val miss = SelectionTester.pickTopmost(page, 300.0, 300.0)
        assertTrue("outside touch misses", miss == null)
    }

    @Test
    fun selectionOpsTranslateMovesAnchorAndKeepsPinsRelative() {
        val resistor = sampleResistor(45.0)
        val translated = SelectionOps.translate(resistor, 25.0, -15.0) as CircuitElement

        assertEquals(125.0, translated.positionX, 1e-6)
        assertEquals(85.0, translated.positionY, 1e-6)
        assertEquals(45.0, translated.rotation, 1e-6)
        assertEquals(-50.0, translated.pins[0].relX, 1e-6)
        assertEquals(50.0, translated.pins[1].relX, 1e-6)
    }

    @Test
    fun selectionOpsRotateUpdatesRotationAndPositionAboutPivot() {
        val resistor = sampleResistor(0.0)
        // Rotate 90 degrees around pivot (100, 100) -> position stays (100, 100), rotation becomes 90
        val rotated = SelectionOps.rotate(resistor, Math.toRadians(90.0), 100.0, 100.0) as CircuitElement

        assertEquals(100.0, rotated.positionX, 1e-6)
        assertEquals(100.0, rotated.positionY, 1e-6)
        assertEquals(90.0, rotated.rotation, 1e-6)

        // Rotate 90 degrees around offset pivot (0, 100):
        // (100, 100) relative to (0, 100) is rx=100, ry=0. Rotated 90 deg: rx'=0, ry'=100.
        // New position: (0, 200).
        val orbitRotated = SelectionOps.rotate(resistor, Math.toRadians(90.0), 0.0, 100.0) as CircuitElement
        assertEquals(0.0, orbitRotated.positionX, 1e-6)
        assertEquals(200.0, orbitRotated.positionY, 1e-6)
        assertEquals(90.0, orbitRotated.rotation, 1e-6)
    }

    @Test
    fun selectionOpsRestyleUpdatesColorAndStrokeWidth() {
        val resistor = sampleResistor(0.0)
        val restyled = SelectionOps.restyle(resistor, 0xFF00FF00.toInt(), 3.5) as CircuitElement

        assertEquals(0xFF00FF00.toInt(), restyled.strokeColor)
        assertEquals(3.5, restyled.strokeWidth, 1e-6)
    }

    @Test
    fun elementEditsReplacesCircuitElementInPlace() {
        val resistor = sampleResistor(0.0)
        val page = Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(elements = listOf(resistor))),
        )

        val updated = resistor.copy(value = "10 kΩ", rotation = 90.0)
        val editedPage = ElementEdits.replaceElementOn(page, resistor, updated)
        assertNotNull("replacement succeeds", editedPage)
        val replaced = editedPage!!.layers[0].elements[0] as CircuitElement
        assertEquals("10 kΩ", replaced.value)
        assertEquals(90.0, replaced.rotation, 1e-6)
    }
}

package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that turns a pen's firmware-reported *click* into exactly one action, however many press
 * edges that click is delivered as. Two edges per gesture would fire a toggle twice — back to where it
 * started — which reads as "the button does nothing at all", the very symptom this class prevents.
 */
class VendorClickGateTest {

    @Test
    fun `the first click always fires`() {
        assertTrue(VendorClickGate().fires(1_000))
    }

    @Test
    fun `a second edge of the same gesture does not`() {
        val gate = VendorClickGate(windowMs = 500)
        assertTrue(gate.fires(1_000))
        assertFalse("the same double-click, reported twice", gate.fires(1_100))
    }

    @Test
    fun `a deliberate second click after the window fires again`() {
        val gate = VendorClickGate(windowMs = 500)
        gate.fires(1_000)
        assertTrue(gate.fires(1_600))
    }

    @Test
    fun `the window is inclusive of its own length`() {
        val gate = VendorClickGate(windowMs = 500)
        gate.fires(1_000)
        assertTrue(gate.fires(1_500))
    }

    @Test
    fun `the default window is Android's own double-tap timeout`() {
        assertEquals(300L, VendorClickGate.DEFAULT_WINDOW_MS)
    }

    @Test
    fun `reset lets the very next click through`() {
        val gate = VendorClickGate(windowMs = 500)
        gate.fires(1_000)
        gate.reset()
        assertTrue(gate.fires(1_010))
    }
}

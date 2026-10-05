package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pen-diagnostics log and the text it produces: the buffer's bounds and de-duplication, and the
 * one readable line every raw event becomes. The panel is only worth anything if the line it shows
 * names the right thing — a button bit mislabelled is worse than no panel at all.
 */
class PenInputLogTest {

    private val log = PenInputLog(capacity = 3)

    // --- the buffer -----------------------------------------------------------------------------

    @Test
    fun `lines come back oldest first`() {
        log.record("first")
        log.record("second")
        assertEquals(listOf("first", "second"), log.snapshot())
    }

    @Test
    fun `the oldest line falls out once the buffer is full`() {
        log.record("a")
        log.record("b")
        log.record("c")
        log.record("d")
        assertEquals(listOf("b", "c", "d"), log.snapshot())
        assertEquals(3, log.size)
    }

    @Test
    fun `an unchanged state is recorded once`() {
        assertTrue(log.record("HOVER_MOVE", key = "hover"))
        assertFalse("a repeated identical hover is noise, not information", log.record("HOVER_MOVE", key = "hover"))
        assertEquals(listOf("HOVER_MOVE"), log.snapshot())
    }

    @Test
    fun `a changed state is recorded again`() {
        log.record("HOVER_MOVE", key = "hover")
        assertTrue(log.record("DOWN", key = "down"))
        assertTrue("the pen comes back to hovering", log.record("HOVER_MOVE", key = "hover"))
        assertEquals(listOf("HOVER_MOVE", "DOWN", "HOVER_MOVE"), log.snapshot())
    }

    @Test
    fun `a line with no key always lands`() {
        log.record("KEY down", key = null)
        assertTrue(log.record("KEY down", key = null))
        assertEquals(2, log.size)
    }

    @Test
    fun `clear empties the buffer and its memory of the last state`() {
        log.record("DOWN", key = "down")
        log.clear()
        assertEquals(emptyList<String>(), log.snapshot())
        assertTrue("the same state is news again after a clear", log.record("DOWN", key = "down"))
    }

    // --- naming what arrived --------------------------------------------------------------------

    @Test
    fun `actions are named, and an unknown one keeps its number`() {
        assertEquals("DOWN", PenEventText.actionName(0))
        assertEquals("HOVER_MOVE", PenEventText.actionName(7))
        assertEquals("HOVER_ENTER", PenEventText.actionName(9))
        assertEquals("HOVER_EXIT", PenEventText.actionName(10))
        assertEquals("BUTTON_PRESS", PenEventText.actionName(11))
        assertEquals("BUTTON_RELEASE", PenEventText.actionName(12))
        assertEquals("action(99)", PenEventText.actionName(99))
    }

    @Test
    fun `tool types name the pointer, including the mouse a pen can look like`() {
        assertEquals("finger", PenEventText.toolName(1))
        assertEquals("stylus", PenEventText.toolName(2))
        assertEquals("eraser", PenEventText.toolName(3))
        assertEquals("mouse", PenEventText.toolName(4))
        assertEquals("unknown", PenEventText.toolName(0))
    }

    @Test
    fun `button state decodes to the bits that matter`() {
        assertEquals("-", PenEventText.buttons(0))
        assertEquals("0x20:STYLUS_PRIMARY", PenEventText.buttons(BarrelButtonState.BUTTON_STYLUS_PRIMARY))
        assertEquals("0x2:SECONDARY", PenEventText.buttons(BarrelButtonState.BUTTON_SECONDARY))
        assertEquals(
            "0x22:SECONDARY|STYLUS_PRIMARY",
            PenEventText.buttons(
                BarrelButtonState.BUTTON_SECONDARY or BarrelButtonState.BUTTON_STYLUS_PRIMARY,
            ),
        )
        assertEquals("0x4:unknown", PenEventText.buttons(0x4))
    }

    @Test
    fun `source bits decode to the device the event came from`() {
        assertEquals("STYLUS", PenEventText.sourceNames(BarrelButtonState.SOURCE_STYLUS_BIT or 0x2))
        assertEquals("STYLUS|BT_STYLUS", PenEventText.sourceNames(0xc002))
        assertEquals("MOUSE", PenEventText.sourceNames(0x2002))
        assertEquals("KEYBOARD", PenEventText.sourceNames(0x101))
        assertEquals("other", PenEventText.sourceNames(1))
    }

    @Test
    fun `key codes are named, including the barrel's and the generic buttons`() {
        assertEquals(
            "KEYCODE_STYLUS_BUTTON_PRIMARY(308)",
            PenEventText.keyName(BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY),
        )
        assertEquals(
            "KEYCODE_BUTTON_STYLUS_SECONDARY(318)",
            PenEventText.keyName(BarrelButtonState.KEYCODE_BUTTON_STYLUS_SECONDARY),
        )
        assertEquals("KEYCODE_BUTTON_1(188)", PenEventText.keyName(188))
        assertEquals("KEYCODE_ENTER(66)", PenEventText.keyName(66))
        assertEquals("key(1234)", PenEventText.keyName(1234))
    }

    // --- one line per event ---------------------------------------------------------------------

    @Test
    fun `a motion line names the action, the pointer, the buttons and the pen`() {
        val line = PenEventText.motionLine(
            actionMasked = 0,
            toolType = 2,
            buttonState = BarrelButtonState.BUTTON_STYLUS_PRIMARY,
            pointerCount = 1,
            deviceName = "HONOR Choice Pencil",
        )
        assertEquals("DOWN tool=stylus btn=0x20:STYLUS_PRIMARY dev=HONOR Choice Pencil", line)
    }

    @Test
    fun `a motion line only mentions extra pointers when there are any`() {
        val one = PenEventText.motionLine(2, 1, 0, 1, null)
        assertEquals("MOVE tool=finger btn=-", one)
        assertTrue(PenEventText.motionLine(5, 1, 0, 2, null).contains("pointers=2"))
    }

    @Test
    fun `a motion line reports the pressure the width is computed from`() {
        val line = PenEventText.motionLine(2, 2, 0, 1, null, pressure = 0.42f)
        assertEquals("MOVE tool=stylus btn=- p=0.42", line)
        assertEquals(
            "a press that never happens must not print a value",
            "DOWN tool=finger btn=-",
            PenEventText.motionLine(0, 1, 0, 1, null),
        )
    }

    @Test
    fun `the dedupe key tracks the pressure, so a harder press is recorded`() {
        val soft = PenEventText.motionKey(2, 2, 0, 1, null, pressure = 0.30f)
        assertEquals(soft, PenEventText.motionKey(2, 2, 0, 1, null, pressure = 0.30f))
        assertNotEquals(soft, PenEventText.motionKey(2, 2, 0, 1, null, pressure = 0.60f))
        // Pressure is only worth two decimals, so digitiser jitter inside that stays one line.
        assertEquals(soft, PenEventText.motionKey(2, 2, 0, 1, null, pressure = 0.3004f))
    }

    @Test
    fun `the pen parameters line reports the filter and the width it produces`() {
        assertEquals(
            "PEN sens=on mult=4.00x min=0.05 base=2.00pt -> @p=0.50 w=4.00pt",
            PenEventText.penParametersLine(
                enabled = true,
                multiplier = 4f,
                minimum = 0.05f,
                baseWidthPt = 2f,
                samplePressure = 0.5f,
                sampleWidthPt = 4.0,
            ),
        )
        assertEquals(
            "PEN sens=off mult=1.00x min=0.05 base=2.00pt -> @p=0.50 w=2.00pt",
            PenEventText.penParametersLine(
                enabled = false,
                multiplier = 1f,
                minimum = 0.05f,
                baseWidthPt = 2f,
                samplePressure = 0.5f,
                sampleWidthPt = 2.0,
            ),
        )
    }

    @Test
    fun `a long device name is shortened so the line stays on one row`() {
        val line = PenEventText.motionLine(2, 2, 0, 1, "HONOR Choice Pencil 2nd generation")
        assertTrue(line.endsWith("…"))
        assertFalse(line.contains("generation"))
    }

    @Test
    fun `the dedupe key tracks every field that can change`() {
        val base = PenEventText.motionKey(2, 2, 0x20, 1, "pen")
        assertEquals(base, PenEventText.motionKey(2, 2, 0x20, 1, "pen"))
        assertNotEquals(base, PenEventText.motionKey(2, 2, 0, 1, "pen"))
        assertNotEquals(base, PenEventText.motionKey(2, 4, 0x20, 1, "pen"))
        assertNotEquals(base, PenEventText.motionKey(1, 2, 0x20, 1, "pen"))
        assertNotEquals(base, PenEventText.motionKey(2, 2, 0x20, 2, "pen"))
        assertNotEquals(base, PenEventText.motionKey(2, 2, 0x20, 1, "other"))
    }

    @Test
    fun `a key line says whether it went down and what source it came from`() {
        val line = PenEventText.keyLine(
            down = true,
            keyCode = BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY,
            source = BarrelButtonState.SOURCE_STYLUS_BIT or 0x2,
            deviceName = null,
        )
        assertEquals("KEY down KEYCODE_STYLUS_BUTTON_PRIMARY(308) src=0x4002:STYLUS", line)
    }

    @Test
    fun `a device line names the device and the sources it advertises`() {
        assertEquals(
            "dev #7 \"HONOR Choice Pencil\" src=0x4002:STYLUS",
            PenEventText.deviceLine(7, "HONOR Choice Pencil", BarrelButtonState.SOURCE_STYLUS_BIT or 0x2),
        )
    }
}

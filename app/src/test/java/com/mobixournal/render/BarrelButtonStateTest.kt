package com.mobixournal.render

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The barrel button's latched state: one physical button, however the pen and the platform happen to
 * report it — motion button bits, a stylus button key event, or a mouse-style right click from a pen
 * the tablet never promoted to a stylus. Holding the button is what makes "hold it to erase" work, so
 * which of those paths a pen takes has to stop mattering.
 */
class BarrelButtonStateTest {

    private val barrel = BarrelButtonState()

    // --- motion button bits ----------------------------------------------------------------------

    @Test
    fun `the stylus barrel bit holds the button`() {
        assertTrue(barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS))
        assertTrue("held until an event says otherwise", barrel.held)
    }

    @Test
    fun `either barrel bit counts`() {
        assertTrue(barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_SECONDARY, PointerKind.STYLUS))
        assertTrue(barrel.held)
    }

    @Test
    fun `a pen with no button pressed is not held`() {
        assertFalse(barrel.onMotion(0, PointerKind.STYLUS))
        assertFalse(barrel.held)
    }

    @Test
    fun `a release clears the latch`() {
        barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS)
        assertTrue("the release is a change", barrel.onMotion(0, PointerKind.STYLUS))
        assertFalse(barrel.held)
    }

    @Test
    fun `repeating a held button is not a change`() {
        barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS)
        assertFalse(barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS))
    }

    @Test
    fun `a finger or palm never touches the barrel state`() {
        barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS)
        assertFalse("a palm's event must not clear the pen's button", barrel.onMotion(0, PointerKind.FINGER))
        assertTrue(barrel.held)
    }

    // --- a pen the tablet only sees as a mouse ---------------------------------------------------

    @Test
    fun `a mouse-like pen reads the barrel from its secondary button`() {
        assertTrue(barrel.onMotion(BarrelButtonState.BUTTON_SECONDARY, PointerKind.UNKNOWN))
    }

    @Test
    fun `a mouse-like pen still draws with its primary button`() {
        assertFalse(
            "the left button is how a mouse draws, never the barrel",
            barrel.onMotion(BarrelButtonState.BUTTON_PRIMARY, PointerKind.UNKNOWN),
        )
    }

    @Test
    fun `holding both mouse buttons is a barrel press`() {
        assertTrue(
            barrel.onMotion(
                BarrelButtonState.BUTTON_PRIMARY or BarrelButtonState.BUTTON_SECONDARY,
                PointerKind.UNKNOWN,
            ),
        )
    }

    // --- barrel button key events ----------------------------------------------------------------

    @Test
    fun `a barrel button key holds and releases`() {
        assertTrue(barrel.onKey(true))
        assertTrue(barrel.held)
        assertTrue(barrel.onKey(false))
        assertFalse(barrel.held)
    }

    // --- a button that only ever arrives as a key -------------------------------------------------

    @Test
    fun `a key-sourced button survives the motion stream saying nothing is pressed`() {
        // A Bluetooth pen the tablet sees as a keyboard reports its button as a key and *never* in the
        // motion stream — every event of the stroke it then draws carries no buttons. Reading that as a
        // release would drop the button the instant the tip landed.
        barrel.onKey(true)
        assertFalse(
            "the tip landing is not the button being let go",
            barrel.onMotion(0, PointerKind.STYLUS),
        )
        assertTrue(barrel.held)
        assertFalse(barrel.onMotion(0, PointerKind.UNKNOWN))
        assertTrue(barrel.held)
    }

    @Test
    fun `only the key going up ends a key-sourced press`() {
        barrel.onKey(true)
        barrel.onMotion(0, PointerKind.STYLUS)
        assertTrue("the release is still a change", barrel.onKey(false))
        assertFalse(barrel.held)
    }

    @Test
    fun `a motion-reported button is still cleared by the motion stream`() {
        assertTrue(barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS))
        assertTrue("the pen really let go", barrel.onMotion(0, PointerKind.STYLUS))
        assertFalse(barrel.held)
    }

    @Test
    fun `a motion event reporting the button matches a key-sourced press`() {
        // A pen that reports the button on both streams: the bit confirms the press, and from then on
        // the motion stream is the one that owns the release, so the app can never stick in erase mode.
        barrel.onKey(true)
        assertFalse(barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS))
        assertTrue(barrel.held)
        assertTrue("the motion stream owns the release now", barrel.onMotion(0, PointerKind.STYLUS))
        assertFalse(barrel.held)
    }

    @Test
    fun `reset forgets a key-sourced press too`() {
        barrel.onKey(true)
        barrel.reset()
        assertFalse(barrel.held)
        // Nothing of the key press is left, so the motion stream is trusted again on its own terms.
        assertTrue(barrel.onMotion(BarrelButtonState.BUTTON_STYLUS_PRIMARY, PointerKind.STYLUS))
        assertTrue(barrel.held)
    }

    @Test
    fun `every stylus button key code is recognised`() {
        assertTrue(BarrelButtonState.isBarrelKeycode(BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY))
        assertTrue(BarrelButtonState.isBarrelKeycode(BarrelButtonState.KEYCODE_STYLUS_BUTTON_SECONDARY))
        assertTrue(BarrelButtonState.isBarrelKeycode(BarrelButtonState.KEYCODE_BUTTON_STYLUS_PRIMARY))
        assertTrue(BarrelButtonState.isBarrelKeycode(BarrelButtonState.KEYCODE_BUTTON_STYLUS_SECONDARY))
    }

    @Test
    fun `ordinary keys are not barrel buttons`() {
        assertFalse(BarrelButtonState.isBarrelKeycode(29)) // KEYCODE_A
        assertFalse(BarrelButtonState.isBarrelKeycode(0))
    }

    // --- a key event from a pen, whatever key code it happens to carry ----------------------------

    @Test
    fun `a documented barrel key is the barrel whatever device sent it`() {
        assertTrue(
            BarrelButtonState.isBarrelKey(
                BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY,
                source = 0,
                unicodeChar = 0,
            ),
        )
    }

    @Test
    fun `a characterless key from a stylus device is the barrel`() {
        // The vendor-pen case: the firmware picks its own key code, but the event still comes from a
        // stylus-sourced device and has no character to type — a pen button can only be that.
        assertTrue(
            BarrelButtonState.isBarrelKey(
                keyCode = 1234,
                source = BarrelButtonState.SOURCE_STYLUS_BIT or 0x2,
                unicodeChar = 0,
            ),
        )
        assertTrue(
            "a Bluetooth stylus reports the same stylus bit",
            BarrelButtonState.isBarrelKey(188, source = 0xc002, unicodeChar = 0),
        )
    }

    @Test
    fun `a key that types a character is never the barrel, even from a pen`() {
        assertFalse(
            BarrelButtonState.isBarrelKey(
                keyCode = 29,
                source = BarrelButtonState.SOURCE_STYLUS_BIT,
                unicodeChar = 'a'.code,
            ),
        )
    }

    @Test
    fun `a characterless key from a keyboard is not the barrel`() {
        assertFalse(BarrelButtonState.isBarrelKey(keyCode = 66, source = 0x101, unicodeChar = 0))
        assertFalse(BarrelButtonState.isBarrelKey(keyCode = 4, source = 0, unicodeChar = 0))
    }

    @Test
    fun `an unnamed key code is the barrel however the device describes itself`() {
        // The Honor Choice Pencil's side button: key code 755, source SOURCE_KEYBOARD, device named
        // "HONOR CHOICE Pencil". No stylus bit anywhere, so only the unnamed code identifies it.
        assertTrue(
            BarrelButtonState.isBarrelKey(
                keyCode = 755,
                source = 0x101,
                unicodeChar = 0,
                vendorKey = true,
            ),
        )
    }

    @Test
    fun `a named key is not the barrel just because a device calls it one`() {
        assertFalse(
            BarrelButtonState.isBarrelKey(
                keyCode = 66,
                source = 0x101,
                unicodeChar = 0,
                vendorKey = false,
            ),
        )
    }

    @Test
    fun `the stylus source bit covers both stylus and Bluetooth stylus`() {
        // SOURCE_STYLUS = 0x4002, SOURCE_BLUETOOTH_STYLUS = 0xc002: the flag is the shared bit.
        assertTrue((0x4002 and BarrelButtonState.SOURCE_STYLUS_BIT) != 0)
        assertTrue((0xc002 and BarrelButtonState.SOURCE_STYLUS_BIT) != 0)
        assertFalse("a mouse must not look like a pen", (0x2002 and BarrelButtonState.SOURCE_STYLUS_BIT) != 0)
    }

    @Test
    fun `reset drops the button`() {
        barrel.onKey(true)
        barrel.reset()
        assertFalse(barrel.held)
    }

    // --- the mirror of the platform's own constants ------------------------------------------------

    @Test
    fun `the button bits mirror MotionEvent`() {
        assertEquals(MotionEvent.BUTTON_PRIMARY, BarrelButtonState.BUTTON_PRIMARY)
        assertEquals(MotionEvent.BUTTON_SECONDARY, BarrelButtonState.BUTTON_SECONDARY)
        assertEquals(MotionEvent.BUTTON_STYLUS_PRIMARY, BarrelButtonState.BUTTON_STYLUS_PRIMARY)
        assertEquals(MotionEvent.BUTTON_STYLUS_SECONDARY, BarrelButtonState.BUTTON_STYLUS_SECONDARY)
    }

    @Test
    fun `the key codes mirror KeyEvent`() {
        assertEquals(308, BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY)
        assertEquals(309, BarrelButtonState.KEYCODE_STYLUS_BUTTON_SECONDARY)
        assertEquals(317, BarrelButtonState.KEYCODE_BUTTON_STYLUS_PRIMARY)
        assertEquals(318, BarrelButtonState.KEYCODE_BUTTON_STYLUS_SECONDARY)
    }
}

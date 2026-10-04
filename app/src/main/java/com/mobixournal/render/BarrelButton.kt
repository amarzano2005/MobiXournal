package com.mobixournal.render

/**
 * The stylus barrel button as the app sees it: **one** physical button that reaches an app down
 * several different paths, depending on the pen, the tablet and the Android release.
 *
 *  - The `BUTTON_STYLUS_PRIMARY` / `BUTTON_STYLUS_SECONDARY` bits on a `MotionEvent` — hover,
 *    button and touch events alike.
 *  - On **Android 14 and later** a `KeyEvent` (`KEYCODE_STYLUS_BUTTON_PRIMARY` / `_SECONDARY`): the
 *    path a Bluetooth stylus takes, because its button arrives as HID data rather than from the
 *    digitizer.
 *  - A plain **secondary** click, which is how a pen presents itself when the tablet never promoted
 *    it to a stylus (tool type `MOUSE`, so [PointerKind.UNKNOWN]): its *tip* is then the primary
 *    button, so only the right/secondary button can be the barrel.
 *  - A key event carrying an **undocumented key code** — no documented stylus code, and not even a
 *    name Android knows. This is the path of a Bluetooth pen that presents itself to the tablet as a
 *    plain **keyboard** and sends a vendor HID code for its side button: the Honor Choice Pencil
 *    sends `755` with source `SOURCE_KEYBOARD` (the diagnostics panel is what showed it). No list of
 *    key codes can be complete against firmware like that, and none has to be: Android names every
 *    key code it defines, so an unnamed one cannot be a real keyboard key and can only come from an
 *    accessory — a pen's button. See [BarrelButtonState.isBarrelKey].
 *
 * Three behaviours matter beyond plain detection:
 *
 *  - **Latching.** A pen whose press edge lands on a hover (or key) event a few tens of milliseconds
 *    earlier — typical of a Bluetooth pen — often does *not* repeat the button bit on the following
 *    `ACTION_DOWN`. Reading only that one event classifies the stroke as ordinary ink and holding the
 *    button does nothing at all, which is exactly the "my pen's button doesn't erase" symptom. The
 *    state is therefore latched from every event and read back when the tip lands.
 *  - **One stream is enough.** A pen that reports the button as a *key* does not report it in the
 *    motion stream at all: *every* event of the stroke it then draws carries no buttons, so the
 *    motion stream would clear the latch the instant the tip landed and the button would erase
 *    nothing. A press that came from a key is therefore cleared only by that key going up, and a
 *    press that came from a motion bit only by a later motion event — see [BarrelButtonState.onMotion].
 *  - **Self-correction.** Any event that reports the button's real bits *overwrites* the latch, so a
 *    pen that stops reporting it (a release during a hover, a stroke drawn without it) can never
 *    leave the app stuck in erase mode.
 *
 * Funnelling both streams through one latch has a second payoff: a single physical press that arrives
 * as *both* a motion bit and a key event is one state change, so the double-click recogniser
 * ([BarrelClickDetector]) sees exactly one edge for it instead of reading the pair as a double-click.
 *
 * Android-free by construction — plain ints and booleans — so it is unit-testable on the JVM
 * (see `BarrelButtonStateTest`).
 */
internal class BarrelButtonState {

    /** True while the barrel button is down, however it was reported. */
    var held: Boolean = false
        private set

    /**
     * True when the press now latched came from a **key** event rather than from a motion event's
     * button bits — the two streams are independent for a pen that sends its button over Bluetooth.
     */
    private var fromKey = false

    /**
     * Feed one pointer event's `buttonState` for a pointer of [kind]; returns true when the held
     * state changed, so the caller can repaint a hover preview that depends on it.
     *
     * A key-sourced press is **ignored** here unless the motion event reports barrel bits of its own:
     * such a pen's motion stream never carries the button, so reading it as a release would drop a
     * button the user is still holding — the whole point of the latch.
     */
    fun onMotion(buttonState: Int, kind: PointerKind): Boolean {
        if (!isBarrelCapable(kind)) return false
        val bits = barrelBits(buttonState, kind)
        if (fromKey && !bits) return false
        fromKey = false
        return set(bits)
    }

    /** Feed a stylus barrel *button key* event; returns true when the held state changed. */
    fun onKey(down: Boolean): Boolean {
        val changed = set(down)
        fromKey = down
        return changed
    }

    /** Forget the button — the pen left hover range, so there is nothing left to track. */
    fun reset() {
        held = false
        fromKey = false
    }

    private fun set(down: Boolean): Boolean {
        if (down == held) return false
        held = down
        return true
    }

    companion object {
        /** `MotionEvent.BUTTON_PRIMARY` — a mouse-like pointer's left button, i.e. its tip. */
        const val BUTTON_PRIMARY = 0x00000001

        /** `MotionEvent.BUTTON_SECONDARY` — the right button. */
        const val BUTTON_SECONDARY = 0x00000002

        /** `MotionEvent.BUTTON_STYLUS_PRIMARY` — the barrel button of a real stylus. */
        const val BUTTON_STYLUS_PRIMARY = 0x00000020

        /** `MotionEvent.BUTTON_STYLUS_SECONDARY` — the second (further) barrel button. */
        const val BUTTON_STYLUS_SECONDARY = 0x00000040

        /** Every bit that can mean "the barrel is down" on a pen: a pen *tip* is never a button. */
        const val ANY_BARREL_BUTTON =
            BUTTON_PRIMARY or BUTTON_SECONDARY or BUTTON_STYLUS_PRIMARY or BUTTON_STYLUS_SECONDARY

        /**
         * The bits that mean "the barrel is down" for a mouse-like pointer: its left button is how it
         * draws, so only the right/secondary buttons (and the stylus bits, should they appear) count.
         */
        const val NON_PRIMARY_BARREL_BUTTON =
            BUTTON_SECONDARY or BUTTON_STYLUS_PRIMARY or BUTTON_STYLUS_SECONDARY

        /** `KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY` (Android 14+) — the barrel button as a key. */
        const val KEYCODE_STYLUS_BUTTON_PRIMARY = 308

        /** `KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY` (Android 14+) — the far barrel button as a key. */
        const val KEYCODE_STYLUS_BUTTON_SECONDARY = 309

        /** `KeyEvent.KEYCODE_BUTTON_STYLUS_PRIMARY` — the generic first stylus button key. */
        const val KEYCODE_BUTTON_STYLUS_PRIMARY = 317

        /** `KeyEvent.KEYCODE_BUTTON_STYLUS_SECONDARY` — the generic second stylus button key. */
        const val KEYCODE_BUTTON_STYLUS_SECONDARY = 318

        /**
         * The `SOURCE_STYLUS` flag inside an `InputDevice` source mask (`SOURCE_STYLUS` and
         * `SOURCE_BLUETOOTH_STYLUS` both carry it). A key event tagged with it came from a pen, not
         * from a keyboard — see [isBarrelKey].
         */
        const val SOURCE_STYLUS_BIT = 0x00004000

        /** True for a key event that is a stylus barrel button. */
        fun isBarrelKeycode(keyCode: Int): Boolean = keyCode == KEYCODE_STYLUS_BUTTON_PRIMARY ||
            keyCode == KEYCODE_STYLUS_BUTTON_SECONDARY ||
            keyCode == KEYCODE_BUTTON_STYLUS_PRIMARY ||
            keyCode == KEYCODE_BUTTON_STYLUS_SECONDARY

        /**
         * True for a key event that should count as the barrel button, given its key code, its
         * [source] mask, its [unicodeChar] and whether Android has a name for [keyCode] at all
         * ([vendorKey], computed by the caller — see `isVendorKeycode` in `DrawingSurfaceInput.kt`).
         *
         * Three paths, in the order a pen actually takes them:
         *
         *  1. One of the documented stylus button key codes (`308`/`309`, `317`/`318`).
         *  2. An **unnamed** key code: Android names every key it defines, so a code it cannot name
         *     is firmware's own, and no real keyboard key can be it. The Honor Choice Pencil's side
         *     button is exactly this — key code `755`, source `SOURCE_KEYBOARD` — so nothing short of
         *     accepting unnamed codes can support it, and no list of codes could be complete against
         *     firmware like that. It is also what makes the detection general instead of a table to
         *     keep extending.
         *  3. A key that came from a stylus-sourced device and carries no printable character: a pen
         *     button has no character to type, so a characterless key from a pen can only be its
         *     button. This covers a pen that *does* tag its events as stylus-sourced (a Bluetooth
         *     stylus with no vendor code to match).
         *
         * Every character-producing key, and every named key from a keyboard, is left alone.
         */
        fun isBarrelKey(
            keyCode: Int,
            source: Int,
            unicodeChar: Int,
            vendorKey: Boolean = false,
        ): Boolean = isBarrelKeycode(keyCode) ||
            vendorKey ||
            (source and SOURCE_STYLUS_BIT != 0 && unicodeChar == 0)

        /** A pointer whose buttons can be the barrel: a pen, or a pen the tablet sees as a mouse. */
        private fun isBarrelCapable(kind: PointerKind): Boolean =
            kind == PointerKind.STYLUS || kind == PointerKind.ERASER_TIP || kind == PointerKind.UNKNOWN

        /** Whether [buttonState] carries a bit that means "the barrel is down" for [kind]. */
        private fun barrelBits(buttonState: Int, kind: PointerKind): Boolean = when (kind) {
            PointerKind.STYLUS, PointerKind.ERASER_TIP -> buttonState and ANY_BARREL_BUTTON != 0
            else -> buttonState and NON_PRIMARY_BARREL_BUTTON != 0
        }
    }
}

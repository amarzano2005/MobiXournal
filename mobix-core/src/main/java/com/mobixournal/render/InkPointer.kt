package com.mobixournal.render

/**
 * One pointer sample, as a **shell** hands it over — the value the two adapters produce and the one
 * the router reads.
 *
 * It exists because the decision logic was trapped behind `android.view.MotionEvent`: an Android
 * adapter can fill every field here from `getToolType`/`getPressure`/`getAxisValue`, and a Compose
 * Desktop adapter can fill the same fields from `PointerInputChange`. What appears here is therefore
 * *device* vocabulary and nothing platform-shaped — [PointerKind] is the app's own four-way
 * classification, so the vocabulary is shared instead of re-invented per shell.
 *
 * Coordinates are **view** pixels, like the finger that made them: a page-space conversion needs the
 * layout, which is the caller's, and keeping it out is what lets the router be tested with plain
 * numbers.
 *
 * @property id The platform's pointer id, stable for the whole contact — the key palm rejection and a
 *   second-finger pan both hang off.
 * @property pressure The raw reading, **unfiltered**: filtering is [PressureCurve]'s job and the router
 *   applies it, so no adapter can quietly disagree about it.
 * @property tiltRad The tablet's tilt axis in radians, or null when the device declares no such axis —
 *   a pen held upright reports `0`, which is a reading, and the two must not be confused (the rule
 *   `docs/stylus.md` states).
 * @property buttons Button bits as the platform reports them; [SECONDARY] is the barrel (or a right
 *   click), [PRIMARY] the tip of a pen the tablet presents as a mouse.
 */
data class InkPointer(
    val id: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timeMs: Long,
    val tool: PointerKind,
    val phase: InkPointerPhase,
    val tiltRad: Float? = null,
    val azimuthRad: Float? = null,
    val buttons: Int = 0,
) {

    /** True for the two pointers that are a pen's own hardware — the ones the toolbar cannot overrule. */
    val isPen: Boolean get() = tool == PointerKind.STYLUS || tool == PointerKind.ERASER_TIP

    /** True when a secondary button (a barrel, a right click) is down on this sample. */
    val hasSecondaryButton: Boolean get() = buttons and SECONDARY != 0

    companion object {
        /** The tip of a pen the tablet presents as a mouse, or a mouse's left button. */
        const val PRIMARY = 1

        /** A pen's barrel button, or a mouse's right button. */
        const val SECONDARY = 2
    }
}

/**
 * Where a contact is in its life. `HOVER_*` is the pen-in-range state a shell may skip entirely — a
 * Compose pointer has no hover stream for a pen off the glass — so nothing in the router depends on it.
 */
enum class InkPointerPhase {
    DOWN,
    MOVE,
    UP,

    /** The system took the contact away (a palm cancel, a window losing the pointer). */
    CANCEL,
}

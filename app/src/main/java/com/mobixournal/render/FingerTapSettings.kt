package com.mobixournal.render

/**
 * The canvas's two finger-tap actions, each chosen in Settings → Input → **Finger gestures**.
 *
 * Held apart from [DrawingSurfaceView] because it is pure mapping with one rule of its own: with both
 * taps switched to [QuickAction.NONE] there is nothing to recognise, so the [detector] is disarmed
 * and stays out of the gesture path entirely rather than running and then discarding what it found.
 * `Undo` and `Redo` are the defaults — what most writers want without reaching for the toolbar.
 */
class FingerTapSettings(private val detector: MultiFingerTapDetector) {
    /** What a still two-finger tap runs. */
    var twoFingerTapAction: QuickAction = QuickAction.UNDO
        set(value) { field = value; sync() }

    /** What a still three-finger tap runs. */
    var threeFingerTapAction: QuickAction = QuickAction.REDO
        set(value) { field = value; sync() }

    private fun sync() {
        detector.shortcutsEnabled =
            twoFingerTapAction != QuickAction.NONE || threeFingerTapAction != QuickAction.NONE
    }
}

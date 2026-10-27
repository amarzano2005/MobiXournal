package com.mobixournal.desktop

/**
 * **The guard that makes a second click on a button close what it opened.**
 *
 * A floating surface closes itself on any press outside its bounds, and that press is the very press the
 * button under it is about to take. The dismissal lands first (`open = false`) and the button's own tap
 * then flips the state back on, so the menu blinked shut and was up again: pressing the trigger of an open
 * menu read as a dead click on every control in the window — the select's capsule, the hamburger, the
 * strip's colour slot, the page menu, the alignment menu.
 *
 * So the dismissal is **remembered**, and the click that caused it is not allowed to open the same thing a
 * second time. The memory is short ([windowNanos], one click's worth) and per target: a quick click on a
 * *different* button still opens that one, and a deliberate click on the same button a moment later opens it
 * as any menu does.
 *
 * This is not a timing trick standing in for a mechanism — the two orderings cannot be told apart from
 * inside a tap handler (the popup's dismissal and the button's tap are separate handlers on the same
 * press), so the state that both of them write is what remembers. The clock is injected for the test.
 *
 * @property windowNanos how long after a dismissal a click still counts as *that* click.
 * @property now the clock, monotonic; a test drives it by hand.
 */
internal class DismissGuard(
    private val windowNanos: Long = REOPEN_WINDOW_NANOS,
    private val now: () -> Long = System::nanoTime,
) {
    private var target: Any? = NOTHING
    private var at = Long.MIN_VALUE / 2

    /** Remember that [target] was closed by the click still being delivered. */
    fun closed(target: Any?) {
        this.target = target
        at = now()
    }

    /** Whether a click may open [target] now — or is the click that has just closed it. */
    fun mayOpen(target: Any?): Boolean = this.target != target || now() - at > windowNanos
}

/** A value no caller passes, so a surface closed long ago does not look like one closed now. */
private val NOTHING = Any()

/**
 * One click's worth of time: long enough to cover press → dismissal → release, short enough that a reader
 * who means to open the menu again is not fighting the window.
 */
internal const val REOPEN_WINDOW_NANOS = 300_000_000L

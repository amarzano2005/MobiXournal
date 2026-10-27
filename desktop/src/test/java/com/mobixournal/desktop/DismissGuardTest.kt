package com.mobixournal.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **re-click that closes a menu**: the one piece of the fix that is arithmetic rather than layout, so it
 * is checked without a scene.
 *
 * A headless Compose scene cannot settle it — popups are not drawn in one, so neither the dismissal nor the
 * tap it races with happens there. What can be checked is the rule the two of them share: after a closure,
 * the click that caused it may not reopen the same surface, and nothing else is held back.
 */
class DismissGuardTest {

    private var clock = 1_000L

    private fun guard() = DismissGuard(now = { clock })

    @Test
    fun `the click that closed a surface cannot open it again`() {
        val guard = guard()
        guard.closed(Unit)
        clock += 90_000_000L // press, dismissal, release: one click's own span
        assertFalse(guard.mayOpen(Unit))
    }

    @Test
    fun `a later click opens it`() {
        val guard = guard()
        guard.closed(Unit)
        clock += REOPEN_WINDOW_NANOS + 1
        assertTrue(guard.mayOpen(Unit))
    }

    @Test
    fun `closing one surface leaves the others free`() {
        val guard = guard()
        guard.closed("colour")
        clock += 90_000_000L
        // A quick click on another button of the strip still opens that one.
        assertTrue(guard.mayOpen("pages"))
        assertFalse(guard.mayOpen("colour"))
    }

    @Test
    fun `a surface that never closed opens at once`() {
        assertTrue(guard().mayOpen(Unit))
    }
}

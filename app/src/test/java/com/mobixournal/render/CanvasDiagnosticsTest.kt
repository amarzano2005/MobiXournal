package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas's diagnostics as an object: what it records, when it pushes, and what it holds back.
 *
 * The Android half (`motion`, `key`, `devices`) needs real `MotionEvent`s and is not reachable here, but
 * everything that made the canvas carry eight members — the on/off gate, the coalesced push, the clear,
 * the switch-on description — is plain state and is pinned down by these four tests. The line formatting
 * itself is [PenInputLog] and [PenEventText]'s, tested next to them.
 */
class CanvasDiagnosticsTest {

    /** A stand-in for `View.post`: it records what was scheduled and runs it when the "pass" comes. */
    private class Poster {
        var scheduled = 0
            private set
        private var pending: Runnable? = null

        fun post(runnable: Runnable): Boolean {
            scheduled++
            pending = runnable
            return true
        }

        fun nextPass() {
            val runnable = pending ?: return
            pending = null
            runnable.run()
        }
    }

    private val poster = Poster()
    private val pushes = mutableListOf<List<String>>()
    private val diagnostics = CanvasDiagnostics(post = poster::post).apply {
        onSnapshot = { pushes += it }
    }

    @Test
    fun `nothing is recorded while the panel is off`() {
        diagnostics.note("CLICK -> undo")

        assertEquals(0, diagnostics.log.size)
        assertTrue("the closed panel has nothing to show", pushes.isEmpty())
    }

    @Test
    fun `a burst of lines is pushed once per UI pass`() {
        diagnostics.setEnabled(true)
        poster.nextPass()
        pushes.clear()
        val scheduledBefore = poster.scheduled

        repeat(50) { diagnostics.note("motion $it") }
        assertEquals(
            "a pen emits far faster than one push per event",
            scheduledBefore + 1,
            poster.scheduled,
        )
        poster.nextPass()

        assertEquals(1, pushes.size)
        assertEquals(
            "the push carries the log, which keeps only its newest lines",
            diagnostics.log.size,
            pushes.single().size,
        )
    }

    @Test
    fun `switching on describes the pen, switching off empties the panel`() {
        diagnostics.setEnabled(true) { note("devices listed") }
        poster.nextPass()
        assertEquals(listOf("devices listed"), pushes.last())

        diagnostics.setEnabled(false)
        assertEquals(0, diagnostics.log.size)
        assertEquals("the next run must not read as a continuation", emptyList<String>(), pushes.last())
    }

    @Test
    fun `clear keeps recording and pushes the emptied log`() {
        diagnostics.setEnabled(true)
        poster.nextPass()
        diagnostics.note("before")
        poster.nextPass()
        pushes.clear()

        diagnostics.clear()
        poster.nextPass()

        assertEquals(0, diagnostics.log.size)
        assertEquals(1, pushes.size)
        assertEquals(emptyList<String>(), pushes.single())

        diagnostics.note("after")
        assertEquals("clearing is not switching off", 1, diagnostics.log.size)
    }
}

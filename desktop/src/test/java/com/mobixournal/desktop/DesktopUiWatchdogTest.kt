package com.mobixournal.desktop

import com.mobixournal.desktop.shell.DesktopUiWatchdog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hang detector's two decisions, as tests rather than habits: **when** a stall is worth a file (the
 * threshold, the repeat and the cap), and **what** the file says (the header, the thread that beats first, and
 * the monitor a blocked thread waits for together with its owner).
 *
 * The stake is a diagnosis someone reads after a freeze they cannot reproduce. A dump written too eagerly is
 * a directory full of files nobody asked for; a dump that omits the lock's owner leaves the reader with a
 * stack that says `BLOCKED` and no name to blame.
 */
class DesktopUiWatchdogTest {

    private fun thread(
        name: String,
        state: String = "RUNNABLE",
        lock: String? = null,
        owner: String? = null,
        frames: List<String> = listOf("kotlin.Unit.kt:1"),
    ) = DesktopUiWatchdog.Snapshot(name, state, lock, owner, frames)

    @Test
    fun `a stall shorter than the threshold writes nothing`() {
        assertFalse(DesktopUiWatchdog.due(DesktopUiWatchdog.STALL_MS - 1, Long.MAX_VALUE, 0))
    }

    @Test
    fun `a stall past the threshold writes at once, then waits for the repeat`() {
        assertTrue(DesktopUiWatchdog.due(DesktopUiWatchdog.STALL_MS, Long.MAX_VALUE, 0))
        assertFalse(DesktopUiWatchdog.due(DesktopUiWatchdog.STALL_MS * 3, 1_000, 1))
        assertTrue(DesktopUiWatchdog.due(DesktopUiWatchdog.STALL_MS * 3, DesktopUiWatchdog.REPEAT_MS, 1))
    }

    @Test
    fun `one stuck window cannot fill the disk`() {
        assertFalse(DesktopUiWatchdog.due(DesktopUiWatchdog.STALL_MS * 10, Long.MAX_VALUE, DesktopUiWatchdog.MAX_DUMPS))
    }

    @Test
    fun `the report names the stall, puts the beating thread first and names the lock owner`() {
        val text = DesktopUiWatchdog.report(
            stalledMs = 5_400,
            uiThread = "AWT-EventQueue-0",
            threads = listOf(
                thread("DefaultDispatcher-worker-3", frames = listOf("com.mobixournal.desktop.sheet.PageSidebar.kt:566")),
                thread(
                    "AWT-EventQueue-0",
                    state = "BLOCKED",
                    lock = "com.mobixournal.desktop.ink.StrokePathCache",
                    owner = "DefaultDispatcher-worker-3",
                    frames = listOf("com.mobixournal.desktop.ink.StrokePathCache.of", "androidx.compose.ui.graphics.Canvas.kt:25"),
                ),
            ),
            at = "2026-11-12 12:00:00",
        )
        assertTrue("the stall is stated with its length", text.contains("stalled 5.4 s"))
        assertTrue("the threshold is stated", text.contains("threshold 4 s"))
        assertTrue("the blocked thread is named as blocked on the cache", text.contains("BLOCKED on com.mobixournal.desktop.ink.StrokePathCache"))
        assertTrue("and the owner is named", text.contains("held by \"DefaultDispatcher-worker-3\""))
        assertTrue("its frames are listed", text.contains("at com.mobixournal.desktop.ink.StrokePathCache.of"))
        val uiAt = text.indexOf("\"AWT-EventQueue-0\"")
        val workerAt = text.indexOf("\"DefaultDispatcher-worker-3\"", uiAt)
        assertTrue("the beating thread comes before the rest", uiAt in 1 until workerAt)
    }

    @Test
    fun `a stall writes one file naming the beating thread`() {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "mobix-watchdog-${System.nanoTime()}")
            .apply { mkdirs() }
        try {
            DesktopUiWatchdog.beat()
            val file = DesktopUiWatchdog.dumpInto(dir, 6_000)
            assertTrue("named for the stall it reports", file.name.startsWith("mobix-ui-hang-"))
            val text = file.readText()
            assertTrue("the header states the stall", text.contains("stalled 6.0 s"))
            assertTrue("and the dump names the thread that beats", text.contains("\"${Thread.currentThread().name}\""))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a live JVM reports its own threads`() {
        val threads = DesktopUiWatchdog.snapshot()
        assertTrue("the dump is never empty", threads.isNotEmpty())
        assertTrue(
            "and it includes the thread asking",
            threads.any { it.name == Thread.currentThread().name },
        )
        assertEquals("a state is always spelled out", true, threads.all { it.state.isNotBlank() })
    }
}

package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.displayRefreshHz
import com.mobixournal.desktop.shell.frameLabel
import com.mobixournal.desktop.shell.refreshLabel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status bar's readouts, and the rules its row keeps.
 *
 *
 * "Is the refresh unlocked" used to be answerable only by inferring it from the watchdog's `paints/s`,
 * which exists only under `--diagnose`. The display's rate is now on screen, so the formatting is pinned
 * here: a 120 Hz panel must read as 120 Hz with its own 8.3 ms budget, a 144 Hz one as 6.9 ms, and a
 * host that cannot say must say so in one word rather than divide by zero.
 */
class DesktopStatusTest {

    @Test
    fun `a refresh rate reads with the frame budget it allows`() {
        assertEquals("120 Hz · 8.3 ms frame", refreshLabel(120))
        assertEquals("144 Hz · 6.9 ms frame", refreshLabel(144))
        assertEquals("60 Hz · 16.7 ms frame", refreshLabel(60))
    }

    @Test
    fun `a host that cannot report a rate says so instead of showing a zero`() {
        assertEquals("refresh unknown", refreshLabel(null))
        assertEquals("refresh unknown", refreshLabel(0))
        assertEquals("refresh unknown", refreshLabel(-1))
    }

    /**
     * The **measured** half of the same sentence: what the window reaches against the budget above, and
     * nothing at all when there is nothing to say. A `0.0` would be a claim about a frame that has not been
     * painted, so the label is absent instead.
     */
    @Test
    fun `the paint time reads in milliseconds and stays quiet before the second frame`() {
        assertEquals("33.3 ms paint", frameLabel(33.3))
        assertEquals("8.3 ms paint", frameLabel(8.296))
        assertNull("no second frame, no claim", frameLabel(0.0))
        assertNull("and a switched-off readout says nothing", frameLabel(16.0, enabled = false))
    }

    /**
     * The reader's own machine is asked for real. A headless JVM (which is how the suite runs) has no
     * device, so the reading is either absent or a positive rate — never an exception and never `0`,
     * because both are what the guarded path is for.
     */
    @Test
    fun `the display's rate is absent or positive on this host, never an error`() {
        val hz = displayRefreshHz()
        assertTrue("a reported rate is a real one", hz == null || hz > 0)
    }

    /**
     * The band's structure, read from its source because it is structure rather than a label: it is a flat
     * band closed by **one hairline** (never a shadow), it no longer repeats what the header already shows,
     * and a refused command is a **badge inside the line** — not a `return@Row` that wiped the zoom, the fit,
     * the file and the progress bar off the band.
     */
    @Test
    fun `the band is flat, unrepeated, and a refusal does not end it`() {
        val source = File(root(), "shell/DesktopStatus.kt").readText()
        assertTrue("the band is closed by one hairline", source.contains("bandDivider(BandEdge.TOP)"))
        assertFalse("and casts no shadow", source.contains("shadow("))
        assertFalse("the search count lives in the header field", source.contains("SearchCount"))
        assertFalse("and the take lives on the pill", source.contains("AudioStatus"))
        val refusal = body(source, "refusal?.let { sentence ->")
        assertTrue("the refusal is a badge", refusal.contains("RefusalBadge(sentence)"))
        assertFalse("and does not return out of the band", refusal.contains("return@Row"))
        assertTrue(
            "its colour is the refusal red",
            body(source, "private fun RefusalBadge(").contains("DesktopTheme.danger"),
        )
        assertFalse("never the document red", source.contains("documentRed"))
    }

    /** `desktop/src/main/java/com/mobixournal/desktop`, found from wherever the test is run. */
    private fun root(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) {
                return File(dir, "desktop/src/main/java/com/mobixournal/desktop")
            }
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }

    /** A declaration's body: from its signature to the next `}` in the first column. */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val end = source.indexOf("\n}", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
    }
}

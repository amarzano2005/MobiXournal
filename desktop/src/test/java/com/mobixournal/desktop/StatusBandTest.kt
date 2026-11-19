package com.mobixournal.desktop

import com.mobixournal.desktop.ink.PenSource
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.shell.bandMetrics
import com.mobixournal.desktop.shell.fitLabel
import com.mobixournal.desktop.shell.fitTip
import com.mobixournal.desktop.shell.penTip
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the status band says at rest, and the sentence it keeps for the pointer.
 *
 * The band is short on purpose — `fit width`, `mouse`, `120 Hz · 3.2 ms` — so these are the words a reader
 * compares at a glance, with the jargon (`8.3 ms frame`, `3.2 ms paint`) in the tip.
 */
class StatusBandTest {

    @Test
    fun `the fit mode is three short words`() {
        assertEquals("free zoom", fitLabel(FitMode.NONE))
        assertEquals("fit width", fitLabel(FitMode.WIDTH))
        assertEquals("fit page", fitLabel(FitMode.PAGE))
        assertEquals("an unresolved fit names the sheet it would show", "fit page", fitLabel(FitMode.AUTO))
    }

    @Test
    fun `each fit mode explains itself in one sentence`() {
        assertEquals("The page's width fills the window", fitTip(FitMode.WIDTH))
        assertEquals("The whole page is in view", fitTip(FitMode.PAGE))
        assertEquals("Free zoom — the scale you set last", fitTip(FitMode.NONE))
    }

    @Test
    fun `the band's metrics are a rate and a frame, or nothing`() {
        assertEquals("120 Hz · 3.2 ms", bandMetrics(120, 3.24))
        assertEquals("a rate alone when nothing has been painted yet", "120 Hz", bandMetrics(120, null))
        assertNull("a host that cannot name its rate says nothing", bandMetrics(null, 3.24))
        assertNull(bandMetrics(0, 3.24))
    }

    /**
     * The accent on this band belongs to its **one door**, the zoom percentage. A report — an export, a
     * copy — is news and keeps the secondary ink, so the colour never claims a command where there is none.
     */
    @Test
    fun `the accent is the door's and news keeps the secondary ink`() {
        val source = File(root(), "shell/DesktopStatus.kt").readText()
        assertTrue("the door wears the accent", body(source, "private fun BandDoor(").contains("DesktopTheme.accent"))
        assertTrue(
            "a refused command is not the document red",
            body(source, "private fun RefusalBadge(").contains("DesktopTheme.danger"),
        )
        val export = body(source, "DesktopExportNotice.text?.let { message ->")
        assertTrue("the export's sentence is secondary ink", export.contains("DesktopTheme.inkSecondary"))
        assertFalse("not the accent, which is the door's", export.contains("DesktopTheme.accent"))
    }

    @Test
    fun `the pen's tip keeps the claim and the words the band drops`() {
        val tip = penTip(PenSource.MOUSE, dial = false, hz = 120, paintMs = 3.24)
        assertTrue("it names the stream, not its absence: $tip", tip.startsWith("pen: mouse"))
        assertTrue("and the display's own ceiling: $tip", tip.contains("120 Hz · 8.3 ms frame"))
        assertTrue("and what the window reaches of it: $tip", tip.contains("3.2 ms paint"))
        assertEquals(
            "a pen with no window to ask answers without a rate",
            "pen: Windows Ink",
            penTip(PenSource.INK, dial = false, hz = null, paintMs = null),
        )
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

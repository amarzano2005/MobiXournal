package com.mobixournal.desktop

import com.mobixournal.desktop.ink.PenSource
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.shell.bandMetrics
import com.mobixournal.desktop.shell.fitLabel
import com.mobixournal.desktop.shell.fitTip
import com.mobixournal.desktop.shell.penTip
import org.junit.Assert.assertEquals
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
}

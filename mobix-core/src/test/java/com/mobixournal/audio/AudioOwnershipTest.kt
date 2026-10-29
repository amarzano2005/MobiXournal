package com.mobixournal.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A recording belongs to the tab it was started in** — the rule both shells ask at the one place a
 * stroke is committed.
 *
 * The two ways this could go wrong are both here: a take that stamps ink drawn somewhere else (two
 * documents claiming one recording) and a staleness that makes "no owner" mean "everybody". Identity is
 * exact — the tab's own id — so a tab that was never recording never matches an owner at all.
 */
class AudioOwnershipTest {

    @Test
    fun `the tab that started the take stamps its own strokes`() {
        assertTrue(ownsRecording("tab-a", "tab-a"))
    }

    @Test
    fun `a stroke drawn in another tab carries no recording`() {
        assertFalse(ownsRecording("tab-a", "tab-b"))
        assertFalse("one tab is not the other", ownsRecording("tab", "tab-2"))
    }

    @Test
    fun `no owner stamps nothing, and no tab matches nothing`() {
        assertFalse("idle is not everybody's take", ownsRecording(null, "tab-a"))
        assertFalse("a commit with no tab to name", ownsRecording("tab-a", null))
        assertFalse(ownsRecording(null, null))
    }
}

package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings is browsed in two levels, so the areas and the sections have a **structural** invariant: the
 * areas must cover every section exactly once. Adding a section without hanging it off an area would
 * leave a page nothing can reach — invisible in code review, obvious in the app — so the drift is
 * pinned here instead of being noticed by a user who can't find a setting.
 */
class SettingsAreaTest {

    @Test
    fun `every section is reachable from exactly one area`() {
        val listed = SettingsArea.values().flatMap { it.sections }
        assertEquals("no section may appear in two areas", listed.size, listed.distinct().size)
        assertEquals(
            "every section must be listed by some area",
            SettingsSection.values().toList().sortedBy { it.ordinal },
            listed.distinct().sortedBy { it.ordinal },
        )
    }

    @Test
    fun `no area is an empty level of indirection`() {
        // A one-child area costs the user a tap and explains nothing; a new section belongs in the
        // closest existing area instead of in a fresh one of its own. The exception is a page that *is*
        // its area — About — where the single child is drawn inline: there is no door to open, so the
        // level costs nothing and the page keeps its own place in the sidebar.
        for (area in SettingsArea.values()) {
            if (area.sections.size >= 2) continue
            val only = area.sections.singleOrNull()
            assertTrue(
                "${area.title} must own at least two sections, or draw its only one inline",
                only != null && only.inline,
            )
        }
    }

    @Test
    fun `every area names itself and says what it holds`() {
        for (area in SettingsArea.values()) {
            assertTrue("${area.name} needs a title", area.title.isNotBlank())
            assertTrue("${area.name} needs a summary", area.summary.isNotBlank())
        }
    }
}

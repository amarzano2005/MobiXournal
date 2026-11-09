package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DesktopSettingArea
import com.mobixournal.desktop.settings.DesktopSettingSection as Section
import org.junit.Assert.*
import org.junit.Test

class SettingsOrganizationTest {
    @Test fun everySectionAppearsOnceAndAreasFollowTheVisibleOrder() {
        assertEquals(Section.entries.toSet(), Section.ORDERED.toSet())
        assertEquals(Section.entries.size, Section.ORDERED.size)
        assertEquals(DesktopSettingArea.entries.toList(), Section.ORDERED.map { it.area }.distinct())
        for (area in DesktopSettingArea.entries) {
            val positions = Section.ORDERED.indices.filter { Section.ORDERED[it].area == area }
            assertEquals((positions.first()..positions.last()).toList(), positions)
        }
    }

    @Test fun workspaceSearchUsesTheSameOrderAsNavigation() {
        assertEquals(listOf(Section.APPEARANCE, Section.TOOLBAR, Section.NAVIGATION), Section.search("workspace"))
        assertEquals(Section.ORDERED, Section.search(""))
    }

    @Test fun severalWordsCanFindASettingRegardlessOfTheirOrder() {
        assertTrue(Section.STYLUS in Section.search("pen pressure"))
        assertTrue(Section.STYLUS in Section.search("  pressure   PEN "))
        assertTrue(Section.APPEARANCE in Section.search("dark theme"))
        assertTrue(Section.search("pressure nonexistent").isEmpty())
    }

    @Test fun penRingAndRecordingHaveTaskAppropriateHomes() {
        assertEquals(DesktopSettingArea.INPUT, Section.RING.area)
        assertEquals(DesktopSettingArea.APP, Section.AUDIO.area)
        assertFalse(Section.BACKUP.summary.contains("recovery"))
        assertTrue(Section.NAVIGATION.summary.contains("scrolling"))
    }
}

package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings search. What matters is not that it filters, but that it *finds*: a user looking for the
 * setting that stops the page drifting types "momentum" only if they already know what the app calls
 * it, so the everyday words have to be in the aliases. These tests pin a handful of those queries,
 * which is what keeps an alias list from rotting as its page changes, and pin the two structural
 * promises the search relies on: every section has aliases, and every section can say which area it
 * lives in (a result has to be able to name its home).
 */
class SettingsSearchTest {

    @Test
    fun `an empty or blank query matches nothing`() {
        assertTrue(settingsSearchResults("").isEmpty())
        assertTrue(settingsSearchResults("   ").isEmpty())
    }

    @Test
    fun `a section is found by its own name`() {
        assertEquals(listOf(SettingsSection.COLORS), settingsSearchResults("Colors"))
        assertEquals(listOf(SettingsSection.BACKUP), settingsSearchResults("backup"))
    }

    @Test
    fun `everyday words find the section that owns the setting`() {
        // The point of the aliases: these are the words a person types, not the app's labels.
        assertTrue(SettingsSection.STYLUS in settingsSearchResults("barrel button"))
        assertTrue(SettingsSection.NAVIGATION in settingsSearchResults("momentum"))
        assertTrue(SettingsSection.APPEARANCE in settingsSearchResults("dark"))
        assertTrue(SettingsSection.APPEARANCE in settingsSearchResults("wallpaper"))
        assertTrue(SettingsSection.STORAGE in settingsSearchResults("cache"))
        assertTrue(SettingsSection.SHORTCUTS in settingsSearchResults("expresskey"))
        assertTrue(SettingsSection.TOOLBAR in settingsSearchResults("left-handed"))
        assertTrue(SettingsSection.FIGURES in settingsSearchResults("trapezoid"))
    }

    @Test
    fun `the search ignores case and surrounding blanks`() {
        assertEquals(settingsSearchResults("colors"), settingsSearchResults("  COLORS "))
    }

    @Test
    fun `a term nothing carries finds nothing`() {
        // A search that answers something for any input is a search nobody trusts.
        assertTrue(settingsSearchResults("quantum flux capacitor").isEmpty())
    }

    @Test
    fun `every section carries aliases and answers to its own title`() {
        for (section in SettingsSection.values()) {
            assertTrue("${section.name} must carry search aliases", section.keywords.isNotBlank())
            assertTrue(
                "${section.name} must be findable by its own title",
                section in settingsSearchResults(section.title),
            )
        }
    }

    @Test
    fun `every section belongs to an area, so a result can name where it lives`() {
        for (section in SettingsSection.values()) {
            assertNotNull("${section.name} is not listed by any area", areaOf(section))
        }
    }
}

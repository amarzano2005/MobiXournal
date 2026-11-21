package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutCatalogTest {

    @Test
    fun `every non-retired EditorTool belongs to exactly one family`() {
        val activeTools = EditorTool.entries.filter { it !in RETIRED_SHORTCUT_TOOLS }
        val grouped = shortcutToolGroups().flatMap { it.second }
        assertEquals("No duplicate tool in families", grouped.size, grouped.distinct().size)
        assertEquals(
            "Every non-retired tool must appear in a family",
            activeTools.toSet(),
            grouped.toSet(),
        )
        assertTrue(
            "Retired tools must stay out of default families",
            grouped.none { it in RETIRED_SHORTCUT_TOOLS },
        )
    }

    @Test
    fun `boundRetiredTools exposes only legacy tools with non-blank keys`() {
        val emptyBound = emptyMap<EditorTool, String>()
        assertTrue(boundRetiredTools(emptyBound).isEmpty())

        val boundLegacy = mapOf(
            EditorTool.SQUARE to "Q",
            EditorTool.PEN to "P",
            EditorTool.CHEM_BENZENE to " ",
        )
        assertEquals(listOf(EditorTool.SQUARE), boundRetiredTools(boundLegacy))
    }

    @Test
    fun `totalBoundShortcuts counts toggles tools and colors`() {
        val base = AppSettings(
            penEraserToggleKey = "B",
            handToggleKey = "H",
            selectToggleKey = "",
            fullPageToggleKey = "F",
            ringShortcutKey = "R",
            toolShortcutKeys = mapOf(EditorTool.PEN to "P", EditorTool.LINE to ""),
            colorShortcutKeys = mapOf(XOPP_RED to "2"),
        )
        assertEquals(6, totalBoundShortcuts(base))
    }

    @Test
    fun `duplicateShortcutKeys detects case-insensitive collisions across categories`() {
        val settings = AppSettings(
            penEraserToggleKey = "b",
            handToggleKey = "H",
            selectToggleKey = "S",
            fullPageToggleKey = "F",
            ringShortcutKey = "",
            toolShortcutKeys = mapOf(EditorTool.PEN to "B", EditorTool.HIGHLIGHTER to "1"),
            colorShortcutKeys = mapOf(XOPP_RED to "1", XOPP_BLUE to "2"),
        )
        assertEquals(setOf("B", "1"), duplicateShortcutKeys(settings))
    }

    @Test
    fun `shortcutMatches matches either label or bound key case-insensitively`() {
        assertTrue(shortcutMatches("Pen", "P", ""))
        assertTrue(shortcutMatches("Pen", "P", "pen"))
        assertTrue(shortcutMatches("Eraser (partial)", "e", "E"))
        assertFalse(shortcutMatches("Line", "", "P"))
        assertFalse(shortcutMatches("Pen", "X", "Z"))
    }

    @Test
    fun `resetShortcutsToDefaults restores toggle defaults and clears maps`() {
        val custom = AppSettings(
            penEraserToggleKey = "Q",
            handToggleKey = "",
            selectToggleKey = "Z",
            fullPageToggleKey = "",
            ringShortcutKey = "R",
            toolShortcutKeys = mapOf(EditorTool.PEN to "P"),
            colorShortcutKeys = mapOf(XOPP_BLACK to "1"),
        )
        val reset = resetShortcutsToDefaults(custom)
        val defaults = AppSettings()
        assertEquals(defaults.penEraserToggleKey, reset.penEraserToggleKey)
        assertEquals(defaults.handToggleKey, reset.handToggleKey)
        assertEquals(defaults.selectToggleKey, reset.selectToggleKey)
        assertEquals(defaults.fullPageToggleKey, reset.fullPageToggleKey)
        assertEquals(defaults.ringShortcutKey, reset.ringShortcutKey)
        assertTrue(reset.toolShortcutKeys.isEmpty())
        assertTrue(reset.colorShortcutKeys.isEmpty())
    }
}

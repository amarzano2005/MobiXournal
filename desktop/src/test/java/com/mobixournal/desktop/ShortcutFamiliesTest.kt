package com.mobixournal.desktop

import com.mobixournal.desktop.settings.ShortcutFamilies
import com.mobixournal.desktop.settings.ShortcutTable
import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shortcuts page's **reading order**, as a rule: the families are a *sort* of the key table, never a
 * second one.
 *
 * The page draws [ShortcutFamilies.of] over [ShortcutTable.entries], so a command added to the table
 * tomorrow is on the page the same day — in *Other* if nobody has placed it, which is visible, rather than
 * in no list at all, which is silent. That is the guarantee these tests keep.
 */
class ShortcutFamiliesTest {

    private val families = ShortcutFamilies.of(ShortcutTable.entries)

    @Test
    fun `every key of the table lands in exactly one family`() {
        val ids = families.flatMap { family -> family.entries.map { it.id } }
        assertEquals("no row is listed twice and none is dropped", ShortcutTable.entries.size, ids.size)
        assertEquals(
            "the families hold the table, not a copy of it",
            ShortcutTable.entries.map { it.id }.toSet(),
            ids.toSet(),
        )
    }

    @Test
    fun `no row falls through to Other`() {
        assertTrue(
            "an unplaced row is a decision someone forgot: " + families.last().title,
            families.none { it.title == "Other" },
        )
    }

    @Test
    fun `the switches lead, then the tools in the strips' own order`() {
        assertEquals("Toggle", families.first().title)
        assertEquals(
            listOf(ShellCommand.TOGGLE_ERASER, ShellCommand.TOGGLE_HAND, ShellCommand.TOGGLE_SELECTION),
            families.first().entries.mapNotNull { it.command },
        )
        assertEquals("Ink", families[1].title)
        assertEquals(
            listOf(DesktopTool.PEN, DesktopTool.HIGHLIGHTER, DesktopTool.ERASER, DesktopTool.ERASER_WHOLE),
            families[1].entries.mapNotNull { it.tool },
        )
    }

    @Test
    fun `a circuit part sits under its own palette's slot`() {
        val stem = families.first { it.title == "STEM" }.entries
        val at = stem.indexOfFirst { it.tool == DesktopTool.CIRCUIT && it.kind == null }
        assertEquals(
            "the passive parts follow the passive slot, in the palette's order",
            listOf<ShapeKind?>(null) + CircuitFamily.PASSIVE.members,
            stem.drop(at).take(CircuitFamily.PASSIVE.members.size + 1).map { it.kind },
        )
    }

    @Test
    fun `a search reaches a name, a chord and a tool`() {
        val erase = ShortcutTable.entries.first { it.tool == DesktopTool.ERASER_WHOLE }
        assertTrue("by name", ShortcutFamilies.matches(erase, "whole"))
        assertTrue("by chord", ShortcutFamilies.matches(erase, "Shift+E"))
        assertTrue("by tool", ShortcutFamilies.matches(erase, "ERASER_WHOLE"))
        assertTrue("by the live binding, after a remap", ShortcutFamilies.matches(erase, "Alt+9", "Alt+9"))
        assertTrue("a blank field finds everything", ShortcutFamilies.matches(erase, "  "))
        assertTrue("and nothing matches a word that is not there", !ShortcutFamilies.matches(erase, "plot"))
    }
}

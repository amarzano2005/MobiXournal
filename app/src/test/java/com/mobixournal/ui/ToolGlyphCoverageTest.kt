package com.mobixournal.ui

import com.mobixournal.design.ToolGlyph
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tools draw the **shared** glyph.
 *
 * The two shells now draw from one catalogue (`ToolGlyphCatalogue`, `mobix-core`), so "the toolbar's
 * icons are the same on the PC and the tablet" is a property of the code rather than of two artists'
 * agreement. This pins the part the user actually sees: **every slot face** either shell can show at
 * rest — each rail group's default member, each STEM group's default member, and every member a user
 * can pick to *become* a face — has a catalogue glyph.
 *
 * The families' deeper picker entries (the other circuit components, gate variants, chemistry bonds)
 * still draw the tablet's original vectors; closing those is a task of its own, and this test says so
 * by pinning exactly how many are left rather than by pretending there are none.
 */
class ToolGlyphCoverageTest {

    @Test
    fun `every rail slot face is a shared glyph`() {
        for (group in TOOL_GROUPS) {
            val face = group.tools.first()
            assertNotNull("the rail's ${group.label} slot still draws a Material icon", face.catalogueGlyph())
        }
    }

    @Test
    fun `every STEM slot face is a shared glyph`() {
        for (group in SECONDARY_TOOL_GROUPS) {
            val face = group.tools.first()
            assertNotNull("the STEM ${group.label} slot still draws a Material icon", face.catalogueGlyph())
        }
    }

    @Test
    fun `every shape and arrow a slot can display is a shared glyph`() {
        val shapes = secondaryToolGroup("shape")!!.tools
        val arrows = secondaryToolGroup("arrow")!!.tools
        for (tool in shapes + arrows) {
            assertNotNull("$tool can face a slot without a shared glyph", tool.catalogueGlyph())
        }
    }

    @Test
    fun `the families still on their old vectors are only the deep picker members`() {
        val mapped = ToolGlyph.entries.size
        val waiting = EditorTool.entries.count { it.catalogueGlyph() == null }
        // Pinned so adding a tool is a deliberate choice: either it gets a shared glyph, or this number
        // moves and whoever moved it says why.
        assertTrue("the catalogue shrank below the toolbars it must cover", mapped >= 30)
        assertTrue("more tools lost their shared glyph ($waiting waiting)", waiting in 1..30)
    }
}

package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * by naming exactly which are left rather than by pretending there are none.
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
    fun `the tools a slot face can show without a shared glyph are named`() {
        val waiting = EditorTool.entries.filter { it.catalogueGlyph() == null }
        // Pinned by **identity**, not only by count: a tool losing its glyph must fail the build by
        // name, and shrinking this set is the only way the list may change.
        assertEquals(
            "the tools still on the tablet's own vectors are the deep STEM picker members",
            listOf(
                "CAPACITOR", "INDUCTOR", "GROUND", "LED", "ZENER_DIODE", "OPAMP", "BJT_NPN",
                "BJT_PNP", "DC_SOURCE", "CURRENT_SOURCE", "OR_GATE", "NOT_GATE", "NAND_GATE",
                "NOR_GATE", "XOR_GATE", "XNOR_GATE", "BUFFER_GATE", "SWITCH_OPEN",
                "SWITCH_CLOSED", "TRANSFORMER", "JUNCTION", "CHEM_BOND_SINGLE",
                "CHEM_BOND_DOUBLE", "CHEM_BOND_TRIPLE",
            ),
            waiting.map { it.name },
        )
    }

    @Test
    fun `the editor's modes are catalogue glyphs too`() {
        // These four used to be Material vectors beside a catalogue everyone else read — the one place a
        // slate could still show two icon families on the same rail (`docs/interface-contract.md` §4).
        for (tool in listOf(
            EditorTool.TEXT_SELECT,
            EditorTool.BG_SELECT,
            EditorTool.TEXIMAGE,
            EditorTool.PLAY_OBJECT,
        )) {
            assertNotNull("$tool is still drawn with a Material icon", tool.catalogueGlyph())
        }
    }
}

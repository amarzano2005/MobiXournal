package com.mobixournal.ui

import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every tool face comes from the **one** catalogue.
 *
 * The two shells now draw from `ToolGlyphCatalogue` (`mobix-core`), so "the toolbar's icons are the same
 * on the PC and the tablet" is a property of the code rather than of two artists' agreement. This pins
 * the part the user actually sees: **every** tool either shell can show — the rail's slots, the STEM
 * dock's families, every member a picker row can offer, the variant faces a slot takes on, and the
 * authoring modes — has a catalogue glyph, and that glyph has art.
 *
 * The bound is **zero**. It used to name the deep circuit, gate and chemistry members that still drew
 * the tablet's own vectors; those were transcribed into the catalogue and deleted, so a tool added
 * without a glyph now fails the build instead of being tolerated.
 */
class ToolGlyphCoverageTest {

    @Test
    fun `every tool has a catalogue glyph`() {
        val missing = EditorTool.entries.filter { runCatching { it.catalogueGlyph() }.isFailure }
        assertEquals("these tools draw a second picture, not the shared glyph", emptyList<EditorTool>(), missing)
    }

    @Test
    fun `every tool's glyph has art`() {
        for (tool in EditorTool.entries) {
            assertTrue("${tool.catalogueGlyph()} draws nothing", ToolGlyphCatalogue.parts(tool.catalogueGlyph()).isNotEmpty())
        }
    }

    @Test
    fun `no two tools share a picture`() {
        // Two tools wearing one face is the worst thing a rail can do: the user picks one and gets the
        // other. Distinct entries per tool is what keeps the catalogue honest as it grows.
        val faces = EditorTool.entries.groupBy { it.catalogueGlyph() }.filterValues { it.size > 1 }
        assertEquals("these tools are indistinguishable on a rail", emptyMap<ToolGlyph, List<EditorTool>>(), faces)
    }

    @Test
    fun `the rail's slot faces and the STEM faces are catalogue glyphs`() {
        for (group in TOOL_GROUPS + SECONDARY_TOOL_GROUPS) {
            for (tool in group.tools) {
                assertTrue("$tool is on a slot without a shared glyph", ToolGlyphCatalogue.parts(tool.catalogueGlyph()).isNotEmpty())
            }
        }
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
            assertTrue("$tool has no art", ToolGlyphCatalogue.parts(tool.catalogueGlyph()).isNotEmpty())
        }
    }
}

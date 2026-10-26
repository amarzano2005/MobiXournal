package com.mobixournal.desktop

import com.mobixournal.design.DesignTokens
import com.mobixournal.design.GlyphPart
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.design.SettingsGlyphCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings glyphs, held to the **one catalogue** both shells read (`docs/interface-contract.md` §4).
 *
 * The phone's list used Material icons and the window had none, which is how one product came to show a
 * section as a picture on the tablet and a blank space on the PC. The marks are now geometry in
 * `mobix-core`; this test holds the desktop's half of the bargain:
 *
 *  - every section carries a glyph, and no two sections share one;
 *  - the glyph is named after the section, so the phone's identically-named section gets the same picture;
 *  - every part stays on the 24-unit grid, with the art inside its margin, so nothing is drawn clipped;
 *  - every glyph is more than one stroke, because a section mark that reads as a single line at 16 dp is
 *    a dash, not a picture.
 */
class DesktopSettingsGlyphTest {

    @Test
    fun `every section carries the glyph named after it, and none repeats`() {
        val glyphs = DesktopSettingSection.entries.map { it.glyph }
        assertEquals("one mark per section", DesktopSettingSection.entries.size, glyphs.size)
        assertEquals("no section borrows another's mark", glyphs.size, glyphs.toSet().size)
        for (section in DesktopSettingSection.entries) {
            assertEquals(
                "${section.title} wears its own mark, so the phone's section of the same name matches",
                SettingsGlyph.valueOf(section.name),
                section.glyph,
            )
        }
    }

    @Test
    fun `a glyph is a picture on the grid, not a dash`() {
        val grid = DesignTokens.TOOL_GLYPH_GRID
        for (glyph in SettingsGlyph.entries) {
            val parts = SettingsGlyphCatalogue.parts(glyph)
            assertTrue("$glyph is drawn", parts.size >= 2)
            for (part in parts) {
                for (point in points(part)) {
                    assertTrue(
                        "$glyph stays on the ${grid.toInt()}-unit grid: ${point.first}, ${point.second}",
                        point.first in 0f..grid && point.second in 0f..grid,
                    )
                    assertTrue(
                        "$glyph keeps its margin, so a 16 dp draw is not clipped: ${point.first}, ${point.second}",
                        point.first >= 2f && point.first <= grid - 2f &&
                            point.second >= 2f && point.second <= grid - 2f,
                    )
                }
            }
        }
    }

    /** Every coordinate a part is built from, as (x, y) — the frame's points and its controls alike. */
    private fun points(part: GlyphPart): List<Pair<Float, Float>> = when (part) {
        is GlyphPart.Line -> listOf(part.x1 to part.y1, part.x2 to part.y2)
        is GlyphPart.Polyline -> part.points.map { it.x to it.y }
        is GlyphPart.Circle -> listOf(
            part.cx - part.radius to part.cy,
            part.cx + part.radius to part.cy,
            part.cx to (part.cy - part.radius),
            part.cx to (part.cy + part.radius),
        )
        is GlyphPart.Cubic -> listOf(
            part.x0 to part.y0,
            part.cx1 to part.cy1,
            part.cx2 to part.cy2,
            part.x1 to part.y1,
        )
        is GlyphPart.Quad -> listOf(part.x0 to part.y0, part.cx to part.cy, part.x1 to part.y1)
    }
}

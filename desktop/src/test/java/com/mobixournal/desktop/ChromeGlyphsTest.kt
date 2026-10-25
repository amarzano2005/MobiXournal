package com.mobixournal.desktop

import com.mobixournal.design.GlyphPart
import com.mobixournal.design.GlyphPoint
import com.mobixournal.design.DesignTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chrome's glyphs, as art: every one of them exists, every one fits the grid it is drawn on, and no
 * two of them are the same picture.
 *
 * This is the mechanical half of *"the window's controls are one hand's work"* — the same checks the
 * tool catalogue carries, applied to the header's own set. A glyph that is empty, or that leaves the
 * 24-unit grid (and so is clipped by its slot), or that is a copy of another, fails here instead of
 * being noticed on a 3440-px-wide band by the person using it.
 */
class ChromeGlyphsTest {

    @Test
    fun `every chrome glyph has art on the grid`() {
        for (glyph in ChromeGlyph.entries) {
            val parts = chromeGlyphParts(glyph)
            assertTrue("$glyph has strokes", parts.isNotEmpty())
            for (point in parts.flatMap { it.points() }) {
                assertTrue(
                    "$glyph stays on the 24-unit grid: $point",
                    point.x in 0f..GRID && point.y in 0f..GRID,
                )
            }
        }
    }

    @Test
    fun `redo is undo read the other way`() {
        assertEquals(
            "the two history arrows are one drawing and its mirror",
            chromeGlyphParts(ChromeGlyph.UNDO).map { it.mirrored() },
            chromeGlyphParts(ChromeGlyph.REDO),
        )
    }

    @Test
    fun `no two chrome glyphs are the same picture`() {
        val signatures = ChromeGlyph.entries.associateWith { chromeGlyphParts(it).toString() }
        for (one in ChromeGlyph.entries) {
            for (other in ChromeGlyph.entries) {
                if (one >= other) continue
                assertNotEquals("$one and $other are different pictures", signatures[one], signatures[other])
            }
        }
    }

    @Test
    fun `the glyphs are drawn on the catalogue's grid and stroke`() {
        assertEquals(24f, GRID, 0f)
        assertEquals(24f, DesignTokens.TOOL_GLYPH_GRID, 0f)
        assertTrue("the stroke is the catalogue's 1.5 units", DesignTokens.TOOL_GLYPH_STROKE_UNITS == 1.5f)
    }

    /** Every coordinate a part draws at, in grid units. */
    private fun GlyphPart.points(): List<GlyphPoint> = when (this) {
        is GlyphPart.Line -> listOf(GlyphPoint(x1, y1), GlyphPoint(x2, y2))
        is GlyphPart.Polyline -> points
        is GlyphPart.Circle -> listOf(
            GlyphPoint(cx - radius, cy - radius),
            GlyphPoint(cx + radius, cy + radius),
        )
        is GlyphPart.Cubic -> listOf(
            GlyphPoint(x0, y0), GlyphPoint(cx1, cy1), GlyphPoint(cx2, cy2), GlyphPoint(x1, y1),
        )
        is GlyphPart.Quad -> listOf(GlyphPoint(x0, y0), GlyphPoint(cx, cy), GlyphPoint(x1, y1))
    }

    private companion object {
        const val GRID = 24f
    }
}

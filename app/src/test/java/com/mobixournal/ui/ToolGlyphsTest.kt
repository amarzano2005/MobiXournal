package com.mobixournal.ui

import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The triangle and trapezoid **variants**, as the rail and the pickers show them.
 *
 * These used to be a second set of hand-built vectors living beside the tool table, which is how the
 * window and the tablet could show two different pictures of one tool. They are catalogue entries now,
 * and this pins the two properties that matter: every kind maps to its **own** glyph (a shared entry
 * would make two shapes indistinguishable on the rail), and every entry it maps to has art.
 */
class ToolGlyphsTest {

    @Test
    fun `every triangle kind has its own glyph`() {
        val glyphs = TriangleKind.entries.map(::triangleGlyph)
        assertEquals("two triangle kinds share a picture", TriangleKind.entries.size, glyphs.toSet().size)
        for (glyph in glyphs) {
            assertTrue("$glyph has no art", ToolGlyphCatalogue.parts(glyph).isNotEmpty())
        }
    }

    @Test
    fun `every trapezoid kind has its own glyph`() {
        val glyphs = TrapezoidKind.entries.map(::trapezoidGlyph)
        assertEquals(
            "two trapezoid kinds share a picture",
            TrapezoidKind.entries.size,
            glyphs.toSet().size,
        )
        for (glyph in glyphs) {
            assertTrue("$glyph has no art", ToolGlyphCatalogue.parts(glyph).isNotEmpty())
        }
    }

    @Test
    fun `the kinds' faces are three-sided and four-sided figures`() {
        // A kind's silhouette is all a user sees at 18 dp, so the vertex count is the one thing that
        // must stay true: a triangle that became a four-point run would read as a trapezoid.
        for (kind in TriangleKind.entries) {
            assertEquals("$kind is not a triangle", 3, vertices(triangleGlyph(kind)))
        }
        for (kind in TrapezoidKind.entries) {
            assertEquals("$kind is not a trapezoid", 4, vertices(trapezoidGlyph(kind)))
        }
    }

    @Test
    fun `the benzene the chemistry guides show is the catalogue's ring`() {
        assertEquals(ToolGlyph.BENZENE, benzeneGlyph)
    }

    private fun vertices(glyph: ToolGlyph): Int {
        val part = ToolGlyphCatalogue.parts(glyph).single()
        return (part as com.mobixournal.design.GlyphPart.Polyline).points.size
    }
}

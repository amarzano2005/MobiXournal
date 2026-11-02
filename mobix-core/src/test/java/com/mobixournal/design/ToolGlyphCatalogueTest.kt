package com.mobixournal.design

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared tool-glyph catalogue's own rules.
 *
 * The catalogue is the one place a tool's picture is defined, and both shells draw it: the tablet by
 * building an `ImageVector`, the desktop by filling a `DrawScope`. That makes a bad coordinate a **bad
 * picture in both toolbars at once**, which is why the geometry is pinned here rather than reviewed by
 * eye: every glyph must be non-empty, must live inside its grid, and must not contain a degenerate
 * stroke that would vanish at 20 dp.
 */
class ToolGlyphCatalogueTest {

    private val grid = DesignTokens.TOOL_GLYPH_GRID

    @Test
    fun `every glyph has art`() {
        for (glyph in ToolGlyph.entries) {
            assertTrue("$glyph draws nothing", ToolGlyphCatalogue.parts(glyph).isNotEmpty())
        }
    }

    @Test
    fun `no coordinate leaves the grid`() {
        for (glyph in ToolGlyph.entries) {
            for (part in ToolGlyphCatalogue.parts(glyph)) {
                for ((x, y) in coordinates(part)) {
                    assertTrue("$glyph has x = $x outside 0..$grid", x in 0f..grid)
                    assertTrue("$glyph has y = $y outside 0..$grid", y in 0f..grid)
                }
            }
        }
    }

    @Test
    fun `no stroke is degenerate`() {
        for (glyph in ToolGlyph.entries) {
            for (part in ToolGlyphCatalogue.parts(glyph)) {
                when (part) {
                    is GlyphPart.Line -> assertTrue(
                        "$glyph has a zero-length line",
                        abs(part.x2 - part.x1) > 0.5f || abs(part.y2 - part.y1) > 0.5f,
                    )
                    is GlyphPart.Polyline -> {
                        assertTrue("$glyph has a one-point polyline", part.points.size >= 2)
                        assertTrue(
                            "$glyph repeats a point, which draws a dot by accident",
                            part.points.zipWithNext().any { (a, b) -> a != b },
                        )
                    }
                    is GlyphPart.Circle -> assertTrue("$glyph has an empty circle", part.radius > 0.5f)
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun `the marquee is a dashed boundary, not a solid frame`() {
        val dashes = ToolGlyphCatalogue.parts(ToolGlyph.MARQUEE)
        assertTrue("the marquee is a single frame again", dashes.size > 8)
        assertTrue("every part of the marquee is a dash", dashes.all { it is GlyphPart.Line })
    }

    @Test
    fun `the grid is the one the token names`() {
        assertEquals(24f, DesignTokens.TOOL_GLYPH_GRID, 0f)
        assertEquals(1.5f, DesignTokens.TOOL_GLYPH_STROKE_UNITS, 0f)
    }

    @Test
    fun `a side count wears the polygon of its count`() {
        val heptagon = ToolGlyphCatalogue.regularPolygon(7)
        val ring = heptagon.single() as GlyphPart.Polyline
        assertTrue("a side-count cell is one closed outline", ring.closed)
        assertEquals("seven vertices for seven sides", 7, ring.points.size)
        assertEquals("the first vertex sits at the top of the grid", 12f, ring.points.first().x, 0.01f)
        assertEquals("inscribed, not at the rim", 2.88f, ring.points.first().y, 0.01f)
        assertEquals(
            "a count below a triangle is a triangle, not a line",
            ToolGlyphCatalogue.regularPolygon(3),
            ToolGlyphCatalogue.regularPolygon(1),
        )
        for (part in ToolGlyphCatalogue.regularPolygon(12)) {
            for ((x, y) in coordinates(part)) {
                assertTrue("a dodecagon leaves the grid at ($x, $y)", x in 0f..grid && y in 0f..grid)
            }
        }
    }

    private fun coordinates(part: GlyphPart): List<Pair<Float, Float>> = when (part) {
        is GlyphPart.Line -> listOf(part.x1 to part.y1, part.x2 to part.y2)
        is GlyphPart.Polyline -> part.points.map { it.x to it.y }
        is GlyphPart.Circle -> listOf(
            part.cx - part.radius to part.cy - part.radius,
            part.cx + part.radius to part.cy + part.radius,
        )
        is GlyphPart.Cubic -> listOf(
            part.x0 to part.y0, part.cx1 to part.cy1, part.cx2 to part.cy2, part.x1 to part.y1,
        )
        is GlyphPart.Quad -> listOf(part.x0 to part.y0, part.cx to part.cy, part.x1 to part.y1)
    }
}

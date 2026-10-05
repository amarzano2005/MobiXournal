package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The figure tools' glyphs are drawn as outlines rather than taken from Material's filled variants,
 * ensuring all figures on the rail (Triangle, Square, Rhombus, Pentagon, Hexagon) read as
 * consistent outlines with no fill.
 */
class ToolGlyphsTest {

    @Test
    fun `the rhombus glyph is its own image on Material's grid`() {
        assertEquals("Rhombus", RhombusIcon.name)
        assertEquals(24f, RhombusIcon.viewportWidth)
        assertEquals(24f, RhombusIcon.viewportHeight)
        assertEquals(24f, RhombusIcon.defaultWidth.value)
    }

    @Test
    fun `the rhombus outline is a closed four-sided figure`() {
        assertEquals("four vertices, closed back to the first", 4, RHOMBUS_OUTLINE.size)
        for (i in RHOMBUS_OUTLINE.indices) {
            val (x1, y1) = RHOMBUS_OUTLINE[i]
            val (x2, y2) = RHOMBUS_OUTLINE[(i + 1) % RHOMBUS_OUTLINE.size]
            assertEquals(9.0, abs((x2 - x1).toDouble()), 1e-6)
            assertEquals(9.0, abs((y2 - y1).toDouble()), 1e-6)
        }
    }

    @Test
    fun `the rhombus outline is centred in the viewport`() {
        val cx = RHOMBUS_OUTLINE.sumOf { it.first.toDouble() } / RHOMBUS_OUTLINE.size
        val cy = RHOMBUS_OUTLINE.sumOf { it.second.toDouble() } / RHOMBUS_OUTLINE.size
        assertEquals(12.0, cx, 1e-6)
        assertEquals(12.0, cy, 1e-6)
        assertTrue("the points are inside the 24-unit grid", RHOMBUS_OUTLINE.all { (x, y) ->
            x in 0f..24f && y in 0f..24f
        })
    }

    @Test
    fun `square glyph is an outline of equal sides`() {
        assertEquals("Square", SquareIcon.name)
        assertEquals(4, SQUARE_OUTLINE.size)
        val cx = SQUARE_OUTLINE.sumOf { it.first.toDouble() } / 4
        val cy = SQUARE_OUTLINE.sumOf { it.second.toDouble() } / 4
        assertEquals(12.0, cx, 1e-6)
        assertEquals(12.0, cy, 1e-6)
        assertTrue(SQUARE_OUTLINE.all { (x, y) -> x in 0f..24f && y in 0f..24f })
    }

    @Test
    fun `pentagon glyph has 5 vertices centered horizontally`() {
        assertEquals("Pentagon", PentagonIcon.name)
        assertEquals(5, PENTAGON_OUTLINE.size)
        val cx = PENTAGON_OUTLINE.sumOf { it.first.toDouble() } / 5
        assertEquals(12.0, cx, 1e-4)
        assertTrue(PENTAGON_OUTLINE.all { (x, y) -> x in 0f..24f && y in 0f..24f })
    }

    @Test
    fun `hexagon glyph has 6 vertices centered in the viewport`() {
        assertEquals("Hexagon", HexagonIcon.name)
        assertEquals(6, HEXAGON_OUTLINE.size)
        val cx = HEXAGON_OUTLINE.sumOf { it.first.toDouble() } / 6
        val cy = HEXAGON_OUTLINE.sumOf { it.second.toDouble() } / 6
        assertEquals(12.0, cx, 1e-4)
        assertEquals(12.0, cy, 1e-4)
        assertTrue(HEXAGON_OUTLINE.all { (x, y) -> x in 0f..24f && y in 0f..24f })
    }

    @Test
    fun `it is not the filled diamond it replaced`() {
        assertNotEquals("Diamond", RhombusIcon.name)
    }

    @Test
    fun `table icon has proper name and dimensions`() {
        assertEquals("Table", TableIcon.name)
        assertEquals(24f, TableIcon.viewportWidth)
        assertEquals(24f, TableIcon.viewportHeight)
        assertEquals(24f, TableIcon.defaultWidth.value)
    }
}

package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stationery presets: what each one writes to the page, and that it is the format's own paper. */
class PageTemplatesTest {

    @Test
    fun `millimetre paper is a graph page ruled at one millimetre`() {
        assertEquals("graph", PageTemplates.styleOf(Stationery.MILLIMETRE))
        val ruling = BackgroundRuling.parse(PageTemplates.configOf(Stationery.MILLIMETRE))
        // Three decimals of a pt in the attribute: 0.0004 mm of rounding on a 1 mm grid.
        assertEquals(PageTemplates.MM_PT, BackgroundRulings.spacingPt("graph", ruling), 1e-3)
        // Every tenth line is a centimetre, or a millimetre grid is unreadable.
        assertEquals(10, BackgroundRulings.boldInterval(ruling))
    }

    @Test
    fun `the paper presets rule at the millimetres they name`() {
        for ((kind, mm) in listOf(
            Stationery.GRAPH_5MM to 5.0,
            Stationery.RULED_7MM to 7.0,
            Stationery.ISOMETRIC_10MM to 10.0,
            Stationery.ISOMETRIC_5MM to 5.0,
        )) {
            val style = PageTemplates.styleOf(kind)!!
            val ruling = BackgroundRuling.parse(PageTemplates.configOf(kind))
            assertEquals(kind.name, mm * PageTemplates.MM_PT, BackgroundRulings.spacingPt(style, ruling), 1e-3)
        }
    }

    @Test
    fun `the isometric presets use the desktop's isometric style`() {
        assertEquals("isograph", PageTemplates.styleOf(Stationery.ISOMETRIC_10MM))
        assertEquals("isograph", PageTemplates.styleOf(Stationery.ISOMETRIC_5MM))
    }

    @Test
    fun `cornell is drawn, so it names no background`() {
        assertNull(PageTemplates.styleOf(Stationery.CORNELL))
        assertNull(PageTemplates.configOf(Stationery.CORNELL))
        assertEquals("Cornell notes", PageTemplates.layerName(Stationery.CORNELL))
    }

    @Test
    fun `cornell rules the three lines that make the layout`() {
        val rules = PageTemplates.strokesOf(Stationery.CORNELL, 595.0, 842.0)
        assertEquals(3, rules.size)
        assertTrue("each rule is a stroke", rules.all { it.size == 2 })
        // Two horizontals at the band edges, one cue-column vertical inside them.
        val horizontals = rules.filter { it[0].y == it[1].y }
        val verticals = rules.filter { it[0].x == it[1].x }
        assertEquals(2, horizontals.size)
        assertEquals(1, verticals.size)
        val top = horizontals.minOf { it[0].y }
        val bottom = horizontals.maxOf { it[0].y }
        val cue = verticals.single()
        assertTrue("the cue rule spans the two bands", cue[0].y == top && cue[1].y == bottom)
        assertEquals(2.5 * 72.0, cue[1].x, 1e-9)
    }

    @Test
    fun `cornell fits a short page instead of overflowing it`() {
        // A5 is 420 x 595 pt: the two-inch bands would eat most of it, so they shrink to a fifth each.
        val rules = PageTemplates.strokesOf(Stationery.CORNELL, 420.0, 595.0)
        val horizontals = rules.filter { it[0].y == it[1].y }
        assertEquals(595.0 / 5.0, horizontals.minOf { it[0].y }, 1e-9)
        assertEquals(595.0 - 595.0 / 5.0, horizontals.maxOf { it[0].y }, 1e-9)
        // …and the cue column narrows with it.
        assertEquals(420.0 / 3.0, rules.first { it[0].x == it[1].x }[0].x, 1e-9)
    }

    @Test
    fun `a paper preset draws nothing`() {
        assertTrue(PageTemplates.strokesOf(Stationery.GRAPH_5MM, 595.0, 842.0).isEmpty())
        assertTrue(PageTemplates.strokesOf(Stationery.ISOMETRIC_5MM, 595.0, 842.0).isEmpty())
    }
}

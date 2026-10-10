package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `<background config=…>` grammar, its resolved defaults, and the isometric grid geometry. */
class BackgroundRulingTest {

    @Test
    fun `a config parses into keyed values`() {
        val ruling = BackgroundRuling.parse("r1=14.173,bli=10,f1=ff0000")
        assertEquals(14.173, ruling.number("r1")!!, 1e-9)
        assertEquals(10, ruling.int("bli"))
        // A key we don't interpret is still readable, and so is written back out untouched.
        assertEquals("ff0000", ruling.value("f1"))
    }

    @Test
    fun `keys we don't know survive an edit`() {
        val edited = BackgroundRuling.parse("f1=ff0000,r1=10")
            .withPt(BackgroundRuling.KEY_SPACING, 7.0 * PageTemplates.MM_PT)
        assertEquals("ff0000", edited.value("f1"))
        assertEquals("f1=ff0000,r1=19.843", edited.text())
    }

    @Test
    fun `a value is written the way desktop parses it`() {
        // Three decimals at most, trailing zeros trimmed — a 5 mm spacing is one readable number.
        assertEquals("14.173", BackgroundRuling.format(5.0 * PageTemplates.MM_PT))
        assertEquals("5", BackgroundRuling.format(5.0))
        assertEquals("0", BackgroundRuling.format(0.0))
    }

    @Test
    fun `an empty ruling drops the attribute`() {
        assertNull(BackgroundRuling.parse(null).text())
        assertNull(BackgroundRuling.parse("").text())
        // A fragment without a key is not a parameter, and is dropped rather than written back as-is.
        assertNull(BackgroundRuling.parse("nonsense").text())
        assertNull(BackgroundRuling.EMPTY.withPt(BackgroundRuling.KEY_SPACING, 5.0).withPt(BackgroundRuling.KEY_SPACING, null).text())
    }

    @Test
    fun `spacing falls back to the style's own default`() {
        assertEquals(BackgroundGrid.RULE_SPACING_PT, BackgroundRulings.spacingPt("lined", BackgroundRuling.EMPTY), 1e-9)
        assertEquals(BackgroundGrid.GRID_SPACING_PT, BackgroundRulings.spacingPt("graph", BackgroundRuling.EMPTY), 1e-9)
        assertEquals(BackgroundGrid.ISO_SPACING_PT, BackgroundRulings.spacingPt("isograph", BackgroundRuling.EMPTY), 1e-9)
        // …and the page's own r1 wins. The attribute stores three decimals (0.0004 mm of rounding
        // on a millimetre grid), so the read-back is compared at that precision.
        val mm5 = BackgroundRuling.EMPTY.withPt(BackgroundRuling.KEY_SPACING, 5.0 * PageTemplates.MM_PT)
        assertEquals(5.0 * PageTemplates.MM_PT, BackgroundRulings.spacingPt("graph", mm5), 1e-3)
    }

    @Test
    fun `a margin or line width of zero means none`() {
        assertNull(BackgroundRulings.marginPt(BackgroundRuling.parse("m1=0")))
        assertNull(BackgroundRulings.marginPt(BackgroundRuling.EMPTY))
        assertEquals(36.0, BackgroundRulings.marginPt(BackgroundRuling.parse("m1=36"))!!, 1e-9)
        assertEquals(2.0, BackgroundRulings.lineWidthPt(BackgroundRuling.parse("lw=2")), 1e-9)
        assertEquals(1.0, BackgroundRulings.lineWidthPt(BackgroundRuling.EMPTY), 1e-9)
        assertEquals(3.0, BackgroundRulings.boldWidthPt(BackgroundRuling.EMPTY, 2.0), 1e-9)
    }

    @Test
    fun `bold lines land every Nth line`() {
        assertTrue(BackgroundRulings.isBold(0, 10))
        assertTrue(BackgroundRulings.isBold(20, 10))
        assertTrue(!BackgroundRulings.isBold(3, 10))
        // No interval at all is a uniform ruling.
        assertTrue(!BackgroundRulings.isBold(0, null))
    }

    @Test
    fun `the isometric grid stays inside the sheet`() {
        val width = 595.0
        val height = 842.0
        val segments = BackgroundGrid.isometric(width, height, 28.3465)
        assertTrue("the mesh has lines", segments.size > 100)
        for (s in segments) {
            assertTrue("left edge inside the page", s[0] >= -1e-9 && s[0] <= width + 1e-9)
            assertTrue("right edge inside the page", s[2] >= -1e-9 && s[2] <= width + 1e-9)
            assertTrue("an unclipped segment", s[2] > s[0])
            assertTrue("top inside the page", s[1] >= -1e-9 && s[1] <= height + 1e-9)
            assertTrue("bottom inside the page", s[3] >= -1e-9 && s[3] <= height + 1e-9)
        }
    }

    @Test
    fun `a finer isometric side packs in more lines`() {
        val coarse = BackgroundGrid.isometric(595.0, 842.0, 28.3465).size
        val fine = BackgroundGrid.isometric(595.0, 842.0, 14.1732).size
        assertTrue("halving the triangle side roughly doubles the mesh", fine > coarse * 3 / 2)
        assertTrue(BackgroundGrid.isometric(0.0, 842.0, 28.0).isEmpty())
        assertTrue(BackgroundGrid.isometric(595.0, 842.0, 0.0).isEmpty())
    }
}

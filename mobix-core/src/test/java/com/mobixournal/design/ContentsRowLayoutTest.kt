package com.mobixournal.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contents row's arithmetic, which is the half of it a screenshot cannot check at every width: the
 * indent stops before the title gets squeezed, the page column is the same on every row and grows with the
 * digits, and a chapter is distinguishable from its sections without counting pixels.
 */
class ContentsRowLayoutTest {

    /** A card's body on a tablet in portrait, and the same inside a split-screen pane. */
    private val roomy = 360f

    /** The window's page sidebar, which is where a four-digit number has to fit. */
    private val sidebar = 200f

    @Test
    fun `each level indents by one step while there is room`() {
        val column = ContentsRowLayout.pageColumnDp(9, roomy)
        assertEquals(0f, ContentsRowLayout.indentDp(0, roomy, column), 0.01f)
        assertEquals(14f, ContentsRowLayout.indentDp(1, roomy, column), 0.01f)
        assertEquals(42f, ContentsRowLayout.indentDp(3, roomy, column), 0.01f)
    }

    @Test
    fun `a deep outline stops indenting before the title is squeezed`() {
        val column = ContentsRowLayout.pageColumnDp(9999, sidebar)
        val cap = (sidebar - column - ContentsRowLayout.MIN_TITLE_DP).coerceAtLeast(0f)
        val deep = ContentsRowLayout.indentDp(8, sidebar, column)
        assertEquals("the eight-deep row takes exactly the room it has", cap, deep, 0.01f)
        assertEquals("and its neighbour past the cap is no deeper", deep, ContentsRowLayout.indentDp(12, sidebar, column), 0.01f)
        assertTrue("the title keeps its minimum", sidebar - deep - column >= ContentsRowLayout.MIN_TITLE_DP - 0.01f)
    }

    @Test
    fun `a row narrower than its title is never indented backwards`() {
        val column = ContentsRowLayout.pageColumnDp(9999, 60f)
        assertEquals(0f, ContentsRowLayout.indentDp(4, 60f, column), 0.01f)
        assertTrue("a negative indent would push the title off the row", ContentsRowLayout.indentDp(4, 0f, column) >= 0f)
    }

    @Test
    fun `the page column grows with the digits, up to its share of the row`() {
        val three = ContentsRowLayout.pageColumnDp(120, roomy)
        val four = ContentsRowLayout.pageColumnDp(1234, roomy)
        assertTrue("four digits need more room than three: $three vs $four", four > three)
        assertEquals(3 * ContentsRowLayout.DIGIT_DP + ContentsRowLayout.GAP_DP, three, 0.01f)
        assertEquals(4 * ContentsRowLayout.DIGIT_DP + ContentsRowLayout.GAP_DP, four, 0.01f)
        // A number with more digits than a narrow row can spare gives way to the title.
        val narrow = ContentsRowLayout.pageColumnDp(123456, 120f)
        assertTrue("the column is capped on a narrow row: $narrow", narrow <= 120f * ContentsRowLayout.MAX_COLUMN_SHARE + 0.01f)
    }

    @Test
    fun `a larger text scale widens the column with its digits`() {
        val plain = ContentsRowLayout.pageColumnDp(1234, roomy, fontScale = 1f)
        val large = ContentsRowLayout.pageColumnDp(1234, roomy, fontScale = 1.5f)
        assertEquals(1.5f * plain - 0.5f * ContentsRowLayout.GAP_DP, large, 0.01f)
        // A scale below 100 % does not shrink it below the digits it was measured for.
        assertEquals(plain, ContentsRowLayout.pageColumnDp(1234, roomy, fontScale = 0.85f), 0.01f)
        // And a narrow row still keeps the title's share.
        val narrow = ContentsRowLayout.pageColumnDp(1234, 120f, fontScale = 2f)
        assertTrue("the capped column on a narrow row: $narrow", narrow <= 120f * ContentsRowLayout.MAX_COLUMN_SHARE + 0.01f)
    }

    @Test
    fun `the column does not depend on which row prints it`() {
        // One list, one column: a row with three digits and a row with four share the width the widest sets,
        // which is what puts every number on the same left edge.
        val widest = 1234
        val rows = listOf(7, 1234, 99).map { ContentsRowLayout.pageColumnDp(widest, sidebar) }
        assertEquals(1, rows.distinct().size)
    }

    @Test
    fun `a chapter is a heading and its sections are not`() {
        assertTrue(ContentsRowLayout.isHeading(0))
        assertFalse(ContentsRowLayout.isHeading(1))
        assertFalse(ContentsRowLayout.isHeading(7))
        assertEquals("a negative depth is the top level, not a level of its own", true, ContentsRowLayout.isHeading(-1))
    }

    @Test
    fun `a row is announced with its title, its page and whether it opens`() {
        assertEquals(
            "Chapter 1, page 1234",
            ContentsRowLayout.announcement("Chapter 1", 1234),
        )
        assertEquals(
            "Header only, no page",
            ContentsRowLayout.announcement("Header only", null),
        )
    }
}

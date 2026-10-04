package com.mobixournal.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the page-size dialog: which entry of the preset row — a paper size (A4 / A5 /
 * Letter / Legal) or the **Default** tab — lights up for a given page size. Tested here instead of on
 * a device because it is plain geometry.
 */
class PageSizePresetsTest {

    private val a4 = PAGE_PRESETS.first { it.name == "A4" }

    @Test
    fun `a preset matches its own size`() {
        assertTrue(matchesPageSize(a4.widthPt, a4.heightPt, a4.widthPt, a4.heightPt))
    }

    @Test
    fun `a preset also matches the swapped orientation`() {
        assertTrue(matchesPageSize(a4.heightPt, a4.widthPt, a4.widthPt, a4.heightPt))
    }

    @Test
    fun `a size within a point still matches`() {
        assertTrue(matchesPageSize(a4.widthPt + 0.9, a4.heightPt - 0.9, a4.widthPt, a4.heightPt))
    }

    @Test
    fun `a size more than a point away does not match`() {
        assertFalse(matchesPageSize(a4.widthPt + 2.0, a4.heightPt, a4.widthPt, a4.heightPt))
    }

    @Test
    fun `a custom default matches exactly that size and no preset`() {
        val customWidth = 700.0
        val customHeight = 500.0
        assertTrue(matchesPageSize(customWidth, customHeight, customWidth, customHeight))
        assertFalse(matchesPageSize(customWidth, customHeight, a4.widthPt, a4.heightPt))
    }
}

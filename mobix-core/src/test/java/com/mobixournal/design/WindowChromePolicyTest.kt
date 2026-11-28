package com.mobixournal.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowChromePolicyTest {

    @Test
    fun compactPhoneHidesSecondaryTopBarActionsAndAnchorsSheetAtBottom() {
        val policy = WindowChromePolicy.forWidthDp(360)
        assertEquals(ScreenWidthClass.COMPACT, policy.widthClass)
        assertTrue(policy.isCompact)
        assertFalse(policy.showInlineNavigation)
        assertFalse(policy.showInlinePdfExport)
        assertFalse(policy.showInlineSplitView)
        assertFalse(policy.showInlineSearchWhenClosed)
        assertTrue(policy.overflowSheetIsBottom)
        assertEquals(420, policy.overflowSheetMaxWidthDp)
        assertEquals(0.94f, policy.dialogWidthFraction, 0.001f)
    }

    @Test
    fun boundaryAt600DpEntersMediumClassWithFullTopBar() {
        assertEquals(ScreenWidthClass.COMPACT, WindowChromePolicy.widthClassFor(599))
        val medium = WindowChromePolicy.forWidthDp(600)
        assertEquals(ScreenWidthClass.MEDIUM, medium.widthClass)
        assertFalse(medium.isCompact)
        assertTrue(medium.showInlineNavigation)
        assertTrue(medium.showInlinePdfExport)
        assertTrue(medium.showInlineSplitView)
        assertTrue(medium.showInlineSearchWhenClosed)
        assertFalse(medium.overflowSheetIsBottom)
        assertEquals(328, medium.overflowSheetMaxWidthDp)
    }

    @Test
    fun boundaryAt840DpEntersExpandedClass() {
        assertEquals(ScreenWidthClass.MEDIUM, WindowChromePolicy.widthClassFor(839))
        val expanded = WindowChromePolicy.forWidthDp(840)
        assertEquals(ScreenWidthClass.EXPANDED, expanded.widthClass)
        assertFalse(expanded.isCompact)
        assertEquals(620, expanded.dialogMaxWidthDp)
    }
}

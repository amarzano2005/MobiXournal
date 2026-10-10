package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The standalone active tool indicator has to keep standing **between the two toolbars** whichever edge
 * the Main Toolbar is docked to: in the corner between the rail's column and the figures' dock on a
 * left-docked rail — the slot that is sized (`TOP_BAR_INDICATOR_WIDTH`) to leave that dock on the document
 * tabs' line — and trailing that dock when the rail is on the right. Otherwise the mirror quietly pushes it
 * to the far edge, away from the hand's own toolbar.
 *
 * Two places can draw it (the top bar's leading slot and the dock's trailing end), so the rule is one
 * function they both read — pinned here rather than by eye, the same way [topBarCapShape] is.
 */
class IndicatorSlotTest {

    @Test
    fun `left-docked rail puts the indicator in the top bar's leading slot`() {
        assertEquals(IndicatorSlot.LEADING, standaloneIndicatorSlot(railOnLeft = true))
    }

    @Test
    fun `right-docked rail mirrors it to the dock's trailing end`() {
        assertEquals(IndicatorSlot.TRAILING, standaloneIndicatorSlot(railOnLeft = false))
    }

    @Test
    fun `the two dockings never put the indicator on the same side`() {
        assertNotEquals(
            standaloneIndicatorSlot(railOnLeft = true),
            standaloneIndicatorSlot(railOnLeft = false),
        )
    }
}

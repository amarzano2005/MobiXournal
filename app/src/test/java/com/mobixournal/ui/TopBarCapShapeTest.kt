package com.mobixournal.ui

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The Secondary Toolbar's end cap has to share the dock's own corner on the end it closes: that is what
 * lets the light grey reach the toolbar's rounded border instead of leaving a crescent of the dock's
 * tone in the corner. The mirror matters too, because the Main Toolbar can be docked to the right and
 * the cap then closes the dock's right end.
 */
class TopBarCapShapeTest {

    @Test
    fun `left cap wears the dock corner on its left and the smaller corner inside`() {
        val shape = topBarCapShape(capOnLeft = true)
        assertEquals(CornerSize(TOP_BAR_DOCK_CORNER), shape.topStart)
        assertEquals(CornerSize(TOP_BAR_DOCK_CORNER), shape.bottomStart)
        assertEquals(CornerSize(TOP_BAR_CAP_INNER_CORNER), shape.topEnd)
        assertEquals(CornerSize(TOP_BAR_CAP_INNER_CORNER), shape.bottomEnd)
    }

    @Test
    fun `right cap mirrors the rule`() {
        val shape = topBarCapShape(capOnLeft = false)
        assertEquals(CornerSize(TOP_BAR_DOCK_CORNER), shape.topEnd)
        assertEquals(CornerSize(TOP_BAR_DOCK_CORNER), shape.bottomEnd)
        assertEquals(CornerSize(TOP_BAR_CAP_INNER_CORNER), shape.topStart)
        assertEquals(CornerSize(TOP_BAR_CAP_INNER_CORNER), shape.bottomStart)
    }

    @Test
    fun `the inner corner is not the dock corner, or the cap would echo the dock`() {
        assertNotEquals(CornerSize(TOP_BAR_DOCK_CORNER), CornerSize(TOP_BAR_CAP_INNER_CORNER))
    }
}

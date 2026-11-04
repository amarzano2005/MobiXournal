package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a picked run lands when the page list moves it one place ([pickedMoveTarget]) — the pure half of
 * the lateral list's two arrows, so the clamping the user feels at the ends of the document is a rule a
 * test can read rather than a number buried in a click handler.
 */
class PageListMoveTest {

    @Test
    fun aSinglePageStepsOnePlaceDown() {
        assertEquals(3, pickedMoveTarget(setOf(2), 1, 5))
    }

    @Test
    fun aSinglePageStepsOnePlaceUp() {
        assertEquals(1, pickedMoveTarget(setOf(2), -1, 5))
    }

    @Test
    fun aRunAtTheEndOfTheDocumentStopsAtTheLastWholePlace() {
        // Four of six pages picked: only two whole places are left for the run to start on.
        assertEquals(2, pickedMoveTarget(setOf(2, 3, 4, 5), 1, 6))
    }

    @Test
    fun aRunAtTheTopStaysOnTheFirstPage() {
        assertEquals(0, pickedMoveTarget(setOf(0, 1), -1, 6))
    }

    @Test
    fun nothingPickedIsNoMove() {
        assertNull(pickedMoveTarget(emptySet(), 1, 6))
    }

    @Test
    fun aPickPastTheEndOfAShrunkenDocumentIsIgnored() {
        assertNull(pickedMoveTarget(setOf(9), 1, 3))
    }
}

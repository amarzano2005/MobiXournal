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

    // --- what a command does *after* the edit: the viewport and the pick follow the page ---

    @Test
    fun carryingAPageLeavesTheViewAndThePickOnIt() {
        var moved: Pair<Set<Int>, Int>? = null
        var selection: Set<Int>? = null
        var viewed: Int? = null
        carryPages(setOf(1), -1, 4, { p, d -> moved = p to d }, { selection = it }, { viewed = it })
        assertEquals(setOf(1) to -1, moved)
        assertEquals(setOf(0), selection)
        assertEquals(0, viewed)
    }

    @Test
    fun carryingARunLeavesThePickOnEveryPageItMoved() {
        var selection: Set<Int>? = null
        var viewed: Int? = null
        carryPages(setOf(1, 2), 1, 5, { _, _ -> }, { selection = it }, { viewed = it })
        assertEquals(setOf(2, 3), selection)
        assertEquals(2, viewed)
    }

    @Test
    fun carryingNothingCallsNothing() {
        var calls = 0
        carryPages(emptySet(), 1, 3, { _, _ -> calls++ }, { calls++ }, { calls++ })
        assertEquals(0, calls)
    }

    @Test
    fun removingLandsOnThePageThatTookTheGap() {
        var deleted: Set<Int>? = null
        var selection: Set<Int>? = null
        var viewed: Int? = null
        removePages(setOf(2), 4, { deleted = it }, { selection = it }, { viewed = it })
        assertEquals(setOf(2), deleted)
        assertEquals(emptySet<Int>(), selection)
        assertEquals(2, viewed)
    }

    @Test
    fun removingTheLastPageLandsOnTheOneBeforeIt() {
        var viewed: Int? = null
        removePages(setOf(3), 4, { _ -> }, { _ -> }, { viewed = it })
        assertEquals(2, viewed)
    }

    @Test
    fun removingEveryPageDoesNothing() {
        var calls = 0
        removePages(setOf(0, 1), 2, { calls++ }, { calls++ }, { calls++ })
        assertEquals(0, calls)
    }

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

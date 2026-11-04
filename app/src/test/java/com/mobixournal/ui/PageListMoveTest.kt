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

    // --- the drag: the finger, the list scrolling under it, and where the page lands ---

    @Test
    fun aDragMeasuresItsLandingInContentPixels() {
        val drag = PageListDrag(rowHeightPx = 64f)
        drag.start(2, scrollPx = 0f)
        drag.by(128f) // two rows down
        assertEquals(4, drag.landing(scrollPx = 0f, pageCount = 10))
        // The list walked two rows under the finger, so the same page now claims two rows further.
        assertEquals(6, drag.landing(scrollPx = 128f, pageCount = 10))
    }

    @Test
    fun aLiftedPageStaysUnderTheFingerWhileTheListWalks() {
        val drag = PageListDrag(rowHeightPx = 64f)
        drag.start(0, scrollPx = 0f)
        drag.by(600f)
        // Seven rows scroll under the page while the finger rests: the strip test must read the same, or
        // the auto-scroll stops as soon as it starts (the bug this test exists for).
        assertEquals(
            "the page's screen position does not follow the list",
            drag.liftedTop(0, scrollPx = 0f),
            drag.liftedTop(0, scrollPx = 448f),
        )
    }

    @Test
    fun theStripKeepsAskingForAStepWhileThePageRestsInIt() {
        // A page held 40 px above the bottom of a 500 px list.
        val top = 500f - 64f - 40f
        val step = autoScrollStep(top, rowHeightPx = 64f, viewportPx = 500, edgePx = 56f, speedPx = 600f)
        assertEquals(true, step > 0f)
        // The same place asks for the same step every frame, which is what keeps the list walking.
        assertEquals(step, autoScrollStep(top, 64f, 500, 56f, 600f))
        assertEquals("and the middle of the list asks for nothing", 0f, autoScrollStep(200f, 64f, 500, 56f, 600f))
    }

    @Test
    fun aDragStopsAtTheDocumentEdges() {
        val drag = PageListDrag(rowHeightPx = 64f)
        drag.start(1, scrollPx = 0f)
        drag.by(-1000f)
        assertEquals(0, drag.landing(scrollPx = 0f, pageCount = 5))
        drag.by(10_000f)
        assertEquals(4, drag.landing(scrollPx = 0f, pageCount = 5))
    }

    @Test
    fun aFinishedDragClaimsNoRowAndNoShift() {
        val drag = PageListDrag(rowHeightPx = 64f)
        assertNull("nothing is being dragged yet", drag.landing(scrollPx = 0f, pageCount = 5))
        drag.start(1, scrollPx = 640f)
        drag.by(32f)
        assertEquals("a scroll back to where the drag began shifts nothing", 0f, drag.shift(640f))
        drag.cancel()
        assertNull(drag.landing(scrollPx = 0f, pageCount = 5))
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

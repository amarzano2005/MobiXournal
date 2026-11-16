package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The endpoint handles' rules, on the JVM: where the two dots sit on a multiline (and a reversed) passage, which
 * one a press grabs, what a crossing drag does to the grabbed end, the counted moves a keyboard makes, the
 * screen-space target at different zooms, and the two refusals — a press that misses both dots, and a sheet with
 * no words.
 */
class PdfTextHandlesTest {

    /**
     * Four words on two lines of one sheet: `one two` above `three four`.
     *
     * Reading order is the list's order, as PDFBox's is, and the two lines have different baselines so a handle's
     * own line can be checked rather than a union's.
     */
    private fun sheet(): PdfTextIndex = PdfTextIndex(
        listOf(
            listOf(
                PdfWord("one", 0.0, 0.0, 20.0, 12.0),
                PdfWord("two", 30.0, 0.0, 50.0, 12.0),
                PdfWord("three", 0.0, 20.0, 20.0, 32.0),
                PdfWord("four", 30.0, 20.0, 50.0, 32.0),
            ),
        ),
    )

    private val sheetRef = PdfSheetRef("notes.pdf|embedded.pdf|", 0)

    @Test fun oneDotForEachEndOfAMultilinePassage() {
        val range = PdfTextRange(sheetRef, 0, 3)
        val points = PdfTextHandles.points(sheet(), range)
        assertEquals(listOf(PdfTextHandle.START, PdfTextHandle.END), points.map { it.handle })
        // START at the first word's left edge, END at the last word's right edge, each on its own line's bottom.
        assertEquals(0.0, points[0].x, 1e-9)
        assertEquals(12.0, points[0].y, 1e-9)
        assertEquals(0, points[0].word)
        assertEquals(50.0, points[1].x, 1e-9)
        assertEquals(32.0, points[1].y, 1e-9)
        assertEquals(3, points[1].word)
    }

    @Test fun theDotsFollowTheWordsAsTheRangeShrinks() {
        val range = PdfTextRange(sheetRef, 2, 3)
        val points = PdfTextHandles.points(sheet(), range)
        // Only the second line is selected: the start dot is on `three`, not on the page's first word — and it
        // sits on that line's own bottom (32), not on the first line's.
        assertEquals(0.0, points[0].x, 1e-9)
        assertEquals(32.0, points[0].y, 1e-9)
        assertEquals(2, points[0].word)
    }

    @Test fun aPressOnADotGrabsThatEnd() {
        val index = sheet()
        val range = PdfTextRange(sheetRef, 0, 2)
        val start = PdfTextHandles.grab(index, range, 0.0, 12.0, radiusPt = 8.0)
        assertNotNull(start)
        assertEquals(PdfTextHandle.START, start!!.handle)
        assertTrue("the start of a forward drag moves `anchor`", start.movesAnchor)
        val end = PdfTextHandles.grab(index, range, 20.0, 32.0, radiusPt = 8.0)
        assertNotNull(end)
        assertEquals(PdfTextHandle.END, end!!.handle)
        assertTrue("the end of a forward drag moves `focus`", !end.movesAnchor)
    }

    @Test fun aReversedPassageKeepsItsEndsTheSameWay() {
        val range = PdfTextRange(sheetRef, 3, 1)
        assertEquals(1, range.start)
        assertEquals(3, range.end)
        val start = PdfTextHandles.grabOf(range, PdfTextHandle.START)
        val end = PdfTextHandles.grabOf(range, PdfTextHandle.END)
        // `anchor` is the reading END of a reversed range, so the END dot moves it and START moves `focus`.
        assertTrue(end.movesAnchor)
        assertTrue(!start.movesAnchor)
    }

    @Test fun grabbingOneWordRangeLetsTheTwoDotsBePulledApart() {
        val range = PdfTextRange(sheetRef, 1, 1)
        assertTrue("START takes `anchor`", PdfTextHandles.grabOf(range, PdfTextHandle.START).movesAnchor)
        assertTrue("END takes `focus`", !PdfTextHandles.grabOf(range, PdfTextHandle.END).movesAnchor)
    }

    @Test fun aPressInWhitespaceGrabsNothing() {
        val index = sheet()
        val range = PdfTextRange(sheetRef, 0, 1)
        assertNull(PdfTextHandles.grab(index, range, 25.0, 6.0, radiusPt = 4.0))
        // ... and a target that is not on the page at all is not an answer either.
        assertNull(PdfTextHandles.grab(index, range, 500.0, 500.0, radiusPt = 12.0))
    }

    @Test fun aTargetWithNoScaleGrabsNothing() {
        assertNull(PdfTextHandles.grab(sheet(), PdfTextRange(sheetRef, 0, 1), 0.0, 12.0, radiusPt = 0.0))
    }

    @Test fun draggingAnEndExtendsByReadingOrderWords() {
        val index = sheet()
        val range = PdfTextRange(sheetRef, 0, 0)
        val grab = PdfTextHandles.grabOf(range, PdfTextHandle.END)
        val grown = PdfTextHandles.drag(index, range, grab, 40.0, 28.0)
        assertEquals(0, grown.anchor)
        assertEquals(3, grown.focus)
        assertEquals(4, grown.wordCount)
    }

    @Test fun crossingTheOtherEndReversesWithoutLosingTheGrab() {
        val index = sheet()
        var range = PdfTextRange(sheetRef, 0, 0)
        val grab = PdfTextHandles.grabOf(range, PdfTextHandle.START)
        // Drag the start dot forwards, past the fixed end: the grabbed field (`anchor`) is now the reading end.
        range = PdfTextHandles.drag(index, range, grab, 40.0, 6.0)
        // The start dot passed the fixed end: the passage now runs from that end back to the dragged word.
        assertEquals(0, range.start)
        assertEquals(range.anchor, range.end)
        assertEquals(1, range.anchor)
        assertTrue(range.reversed)
        // Dragging back over the fixed end with the same grab returns the passage to one word, not to a stale
        // range: the dot in the hand is still the start, so it is the start that comes back.
        range = PdfTextHandles.drag(index, range, grab, 10.0, 6.0)
        assertEquals(PdfTextRange(sheetRef, 0, 0), range)
    }

    @Test fun draggingToAPointTheSheetCannotAnswerLeavesTheRangeAlone() {
        val empty = PdfTextIndex(listOf(emptyList()))
        val range = PdfTextRange(PdfSheetRef("scan.pdf", 0), 0, 3)
        assertEquals(range, PdfTextHandles.drag(empty, range, PdfTextHandles.grabOf(range, PdfTextHandle.END), 10.0, 10.0))
        assertEquals(emptyList<PdfTextHandlePoint>(), PdfTextHandles.points(empty, range))
    }

    @Test fun countedMovesStopAtTheSheetsEnds() {
        val index = sheet()
        val range = PdfTextRange(sheetRef, 0, 1)
        // START one word back is already the sheet's first word: nothing to do, and no reversal either.
        assertEquals(range, PdfTextHandles.nudge(index, range, PdfTextHandle.START, -1))
        // END two words forward reaches the sheet's last word, and a third step does not run past it.
        val last = PdfTextHandles.nudge(index, range, PdfTextHandle.END, 2)
        assertEquals(3, last.end)
        assertEquals(last, PdfTextHandles.nudge(index, last, PdfTextHandle.END, 1))
        // A counted move past the other end reverses, exactly as a crossing drag does.
        val forward = PdfTextRange(sheetRef, 0, 1)
        val reversed = PdfTextHandles.nudge(index, forward, PdfTextHandle.START, 2)
        // The start passed the fixed end (word 1), so the passage now runs from that end to the moved one.
        assertEquals(1, reversed.start)
        assertEquals(2, reversed.end)
        assertTrue(reversed.reversed)
        // No step at all is no change.
        assertEquals(last, PdfTextHandles.nudge(index, last, PdfTextHandle.START, 0))
    }

    @Test fun aScreenSizedTargetShrinksWithTheZoomAndIsBounded() {
        // 24 screen px over 2 px per point is 12 points; over 0.2 px per point it would be 120, so it is capped.
        assertEquals(12.0, PdfTextHandles.touchRadiusPt(24.0, 2.0), 1e-9)
        assertEquals(PdfTextHandles.MAX_TOUCH_PT, PdfTextHandles.touchRadiusPt(24.0, 0.2), 1e-9)
        assertEquals(0.0, PdfTextHandles.touchRadiusPt(24.0, 0.0), 1e-9)
    }

    @Test fun aDotMapsThroughZoomAndScroll() {
        val point = PdfTextHandlePoint(PdfTextHandle.END, 2, 20.0, 32.0)
        val at = PdfTextHandles.screen(point, pxPerPt = 2.0, originX = -30.0, originY = 40.0)
        assertEquals(10f, at.x, 1e-4f)
        assertEquals(104f, at.y, 1e-4f)
    }

    @Test fun aDotOnAnotherSheetIsNotThisRangesTarget() {
        // Two panes' sheets: the second sheet's words sit where the first sheet has none.
        val index = PdfTextIndex(
            listOf(
                listOf(PdfWord("alpha", 0.0, 0.0, 40.0, 12.0)),
                listOf(PdfWord("beta", 200.0, 300.0, 240.0, 312.0)),
            ),
        )
        val first = PdfTextRange(PdfSheetRef("first.pdf", 0), 0, 0)
        assertNull("the other pane's words are not this range's handles", PdfTextHandles.grab(index, first, 220.0, 306.0, 12.0))
        assertEquals(listOf(0.0, 40.0), PdfTextHandles.points(index, first).map { it.x })
        val second = PdfTextRange(PdfSheetRef("second.pdf", 1), 0, 0)
        assertEquals(listOf(200.0, 240.0), PdfTextHandles.points(index, second).map { it.x })
        // The two ranges are adjusted independently, and neither touches the document.
        val nudged = PdfTextHandles.nudge(index, first, PdfTextHandle.END, 1)
        assertEquals(first, nudged)
    }

    @Test fun theLabelsNameTheEndEachDotMoves() {
        assertEquals("Selection start", PdfTextHandles.label(PdfTextHandle.START))
        assertEquals("Selection end", PdfTextHandles.label(PdfTextHandle.END))
    }
}

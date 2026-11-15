package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for PDF text-layer grouping and range selection (no PDFBox/Android needed). */
class PdfTextTest {

    private fun ch(c: Char, x: Double) = CharBox(c, x, 0.0, x + 10, 12.0)

    @Test fun groupsGlyphsIntoWhitespaceDelimitedWords() {
        // "hi  yo" — two words, double space collapsed, boxes unioned.
        val chars = listOf(ch('h', 0.0), ch('i', 10.0), ch(' ', 20.0), ch(' ', 30.0), ch('y', 40.0), ch('o', 50.0))
        val words = PdfWordGrouper.group(chars)
        assertEquals(listOf("hi", "yo"), words.map { it.text })
        assertEquals(0.0, words[0].left, 1e-9)
        assertEquals(20.0, words[0].right, 1e-9)   // hi spans 0..20
        assertEquals(40.0, words[1].left, 1e-9)
    }

    @Test fun groupBreaksOnAWideGapEvenWithoutASpaceGlyph() {
        // "ab" then a 10pt gap (> 0.35*height) then "cd", no space character between them.
        val chars = listOf(ch('a', 0.0), ch('b', 10.0), ch('c', 30.0), ch('d', 40.0))
        assertEquals(listOf("ab", "cd"), PdfWordGrouper.group(chars).map { it.text })
    }

    @Test fun groupBreaksOnALineChange() {
        // Same x, but the second glyph drops a full line down → separate words.
        val a = CharBox('a', 0.0, 0.0, 10.0, 12.0)
        val b = CharBox('b', 0.0, 20.0, 10.0, 32.0)
        assertEquals(listOf("a", "b"), PdfWordGrouper.group(listOf(a, b)).map { it.text })
    }

    @Test fun groupIgnoresLeadingAndTrailingWhitespace() {
        val chars = listOf(ch(' ', 0.0), ch('a', 10.0), ch(' ', 20.0))
        assertEquals(listOf("a"), PdfWordGrouper.group(chars).map { it.text })
    }

    private fun word(text: String, left: Double) = PdfWord(text, left, 0.0, left + 20, 12.0)

    private val index = PdfTextIndex(
        listOf(listOf(word("The", 0.0), word("quick", 30.0), word("brown", 70.0), word("fox", 110.0))),
    )

    @Test fun anchorPicksTheWordUnderThePoint() {
        assertEquals(1, index.anchorWord(0, 35.0, 6.0))  // inside "quick"
    }

    @Test fun anchorFallsBackToNearestWhenBetweenWords() {
        // A point below the line, closest to "brown" horizontally.
        assertEquals(2, index.anchorWord(0, 80.0, 200.0))
    }

    @Test fun anchorIsNullOnAPageWithoutText() {
        assertNull(index.anchorWord(5, 0.0, 0.0))
    }

    @Test fun rangeSelectsInclusiveReadingOrderEitherDirection() {
        assertEquals("quick brown", index.rangeText(0, 1, 2))
        assertEquals("quick brown", index.rangeText(0, 2, 1)) // order-independent
        assertEquals(2, index.rangeBoxes(0, 1, 2).size)
    }

    @Test fun rangeClampsOutOfBoundsIndices() {
        assertEquals("The quick brown fox", index.rangeText(0, -5, 99))
    }

    private val sheet = PdfSheetRef("mobix.pdf", 0)

    @Test fun beginHitsTheWordUnderThePoint() {
        val range = PdfTextSelection.begin(index, sheet, 35.0, 6.0, tolerancePt = 0.0)!!
        assertEquals(1, range.anchor)
        assertEquals(1, range.focus)
        assertEquals("quick", PdfTextSelection.text(index, range))
    }

    @Test fun beginInWhitespaceSelectsNothing() {
        // Five points from "The" (0..20) and from "quick" (30..50): no word is under the point.
        assertNull(PdfTextSelection.begin(index, sheet, 25.0, 6.0, tolerancePt = 0.0))
        // Far below the line, in the page's empty half.
        assertNull(PdfTextSelection.begin(index, sheet, 300.0, 400.0, tolerancePt = 0.0))
        // The nearest-word query still answers there — which is exactly what extending keeps using.
        assertEquals(1, index.anchorWord(0, 26.0, 6.0))
    }

    @Test fun aPressAtAWordEdgeHitsWithAToleranceAndMissesWithoutOne() {
        assertNull(index.hitWord(0, 29.5, 6.0))
        assertEquals(1, index.hitWord(0, 29.5, 6.0, tolerancePt = 1.0))
    }

    @Test fun theToleranceIsBoundedByTheZoomAndByTheWordHeight() {
        // A screen-space radius becomes page points at the view's zoom...
        assertEquals(4.0, PdfHitTolerance.ofScreenPx(screenPx = 8.0, pxPerPt = 2.0), 1e-9)
        assertEquals(2.0, PdfHitTolerance.ofScreenPx(screenPx = 8.0, pxPerPt = 4.0), 1e-9)
        // ...is capped in page points, so zooming far out cannot select half the page...
        assertEquals(PdfHitTolerance.MAX_PT, PdfHitTolerance.ofScreenPx(screenPx = 120.0, pxPerPt = 1.0), 1e-9)
        assertEquals(0.0, PdfHitTolerance.ofScreenPx(screenPx = 8.0, pxPerPt = 0.0), 1e-9)
        // ...and each word's own slack is bounded by its height (12 pt here, so at most 4.2 pt).
        assertEquals(1, index.hitWord(0, 53.0, 6.0, tolerancePt = 100.0))
        assertNull(index.hitWord(0, 55.0, 6.0, tolerancePt = 100.0))
    }

    @Test fun crossingTheAnchorReversesTheRangeWithoutMovingTheGrab() {
        val begun = PdfTextSelection.begin(index, sheet, 35.0, 6.0, tolerancePt = 0.0)!!   // "quick"
        val forward = PdfTextSelection.extend(index, begun, sheet, 115.0, 6.0)            // to "fox"
        assertEquals(1, forward.anchor)
        assertEquals(3, forward.focus)
        assertEquals(1, forward.start)
        assertEquals(3, forward.end)
        assertFalse(forward.reversed)
        val back = PdfTextSelection.extend(index, forward, sheet, 5.0, 6.0)               // back past the grab
        assertEquals("the grab stays on the word the press took", 1, back.anchor)
        assertEquals(0, back.focus)
        assertEquals(0, back.start)
        assertEquals(1, back.end)
        assertTrue(back.reversed)
        assertEquals("The quick", PdfTextSelection.text(index, back))
        assertEquals("grabbing the other end keeps the span", 0, back.anchorOn(sheet, 0).anchor)
    }

    @Test fun anotherSheetIsRefusedRatherThanExtended() {
        val begun = PdfTextSelection.begin(index, sheet, 35.0, 6.0, tolerancePt = 0.0)!!
        val other = PdfSheetRef("mobix.pdf", 1)
        assertEquals(begun, PdfTextSelection.extend(index, begun, other, 115.0, 6.0))
        assertEquals(begun.focus, begun.focusOn(other, 3).focus)
    }

    @Test fun theRangeReadsItsOwnSheetNotItsNotebookPosition() {
        // Page 0 is a cover; the sheet the reader is on is page 1 of the PDF, drawn by the second page.
        val reordered = PdfTextIndex(
            listOf(
                listOf(word("cover", 0.0)),
                listOf(word("The", 0.0), word("quick", 30.0)),
            ),
        )
        val range = PdfTextSelection.begin(reordered, PdfSheetRef("mobix.pdf", 1), 35.0, 6.0, tolerancePt = 0.0)!!
        assertEquals(1, range.sheet.pageNo)
        assertEquals("quick", PdfTextSelection.text(reordered, range))
        assertEquals(1, PdfTextSelection.boxes(reordered, range).size)
    }

    @Test fun hasAnyTextReflectsWhetherAnyPageHadText() {
        assertTrue(index.hasAnyText)
        assertFalse(PdfTextIndex(listOf(emptyList(), emptyList())).hasAnyText)
    }
}

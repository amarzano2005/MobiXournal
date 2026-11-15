package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bands a PDF text selection is drawn with: one per line, the spaces between a line's words inside it, and
 * a hard stop at a column gutter or at the next line.
 *
 * These are the cases where per-word boxes looked wrong and a naive union looks worse: merging across a space
 * is what makes a washed line read as a passage, and merging across a gutter is what makes two columns read as
 * one sentence that was never selected.
 */
class PdfSelectionSpansTest {

    private fun word(text: String, left: Double, right: Double, top: Double = 20.0, bottom: Double = 34.0) =
        PdfWord(text, left, top, right, bottom)

    @Test fun oneWordIsOneBand() {
        val spans = PdfSelectionSpans.of(listOf(word("Magnetic", 20.0, 90.0)))
        assertEquals(1, spans.size)
        assertEquals(20.0, spans[0].left, 1e-9)
        assertEquals(90.0, spans[0].right, 1e-9)
        assertEquals(20.0, spans[0].top, 1e-9)
        assertEquals(34.0, spans[0].bottom, 1e-9)
    }

    @Test fun theOrdinarySpacesBetweenWordsAreInsideTheBand() {
        // "Magnetic bands": a 12 pt gap on a 14 pt line is a space, so the band covers it.
        val spans = PdfSelectionSpans.of(
            listOf(word("Magnetic", 20.0, 60.0), word("bands", 72.0, 110.0)),
        )
        assertEquals("one band, not two tiles", 1, spans.size)
        assertEquals(20.0, spans[0].left, 1e-9)
        assertEquals("it reaches the last word's end", 110.0, spans[0].right, 1e-9)
        assertEquals("and a point inside the space is washed", true, spans[0].left < 66.0 && 66.0 < spans[0].right)
    }

    @Test fun aColumnGutterIsNeverBridged() {
        // Two columns on one baseline: a 90 pt gap on a 14 pt line is not a space.
        val spans = PdfSelectionSpans.of(
            listOf(word("left", 20.0, 70.0), word("column", 160.0, 220.0)),
        )
        assertEquals("two bands, one per column", 2, spans.size)
        assertEquals(70.0, spans[0].right, 1e-9)
        assertEquals(160.0, spans[1].left, 1e-9)
        val gutter = 100.0
        assertTrue("the gutter stays unwashed", spans.none { gutter in it.left..it.right })
    }

    @Test fun twoLinesAreTwoBandsEvenWhenTheirWordsAreClose() {
        val spans = PdfSelectionSpans.of(
            listOf(word("one", 20.0, 60.0, top = 20.0, bottom = 34.0), word("two", 20.0, 60.0, top = 40.0, bottom = 54.0)),
        )
        assertEquals(2, spans.size)
        assertEquals(20.0, spans[0].top, 1e-9)
        assertEquals(54.0, spans[1].bottom, 1e-9)
    }

    @Test fun aLineWearsOneHeightWhateverItsWordsWeigh() {
        // A taller capital and a superscript on one baseline: one band, at the line's union height.
        val spans = PdfSelectionSpans.of(
            listOf(
                word("Capital", 20.0, 60.0, top = 15.0, bottom = 34.0),
                word("e", 62.0, 68.0, top = 22.0, bottom = 30.0),
                word("=mc", 70.0, 96.0, top = 12.0, bottom = 34.0),
            ),
        )
        assertEquals("one band for the line", 1, spans.size)
        assertEquals("the tallest top wins", 12.0, spans[0].top, 1e-9)
        assertEquals("and the lowest bottom", 34.0, spans[0].bottom, 1e-9)
        assertEquals("so every word sits inside one height", spans[0].height, 22.0, 1e-9)
    }

    @Test fun shortAndLongWordsEndWhereTheLastOneEnds() {
        val spans = PdfSelectionSpans.of(
            listOf(word("a", 20.0, 26.0), word("verylongword", 34.0, 140.0)),
        )
        assertEquals(1, spans.size)
        assertEquals(140.0, spans[0].right, 1e-9)
    }

    @Test fun aReverseDragWashesTheSameBandsAsAForwardOne() {
        val index = PdfTextIndex(
            listOf(listOf(word("The", 0.0, 20.0), word("quick", 30.0, 50.0), word("brown", 70.0, 90.0))),
        )
        val forward = index.rangeBoxes(0, 0, 2)
        val backward = index.rangeBoxes(0, 2, 0)
        assertEquals("reading order is the same list either way", forward, backward)
        assertEquals(PdfSelectionSpans.of(forward), PdfSelectionSpans.of(backward))
    }

    @Test fun anEmptySelectionPaintsNothing() {
        assertEquals(emptyList<PdfSelectionSpan>(), PdfSelectionSpans.of(emptyList()))
    }
}

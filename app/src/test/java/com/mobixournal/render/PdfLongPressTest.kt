package com.mobixournal.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Who owns a finger held on a PDF word**, as a value.
 *
 * The long press only wins an *unclaimed* background word: a grip, a placement, a guide, a marquee, the
 * grab line or an object selection's own lift all resolve first, and a pen, a second finger or a wordless
 * place on the page never arms at all. Those are the whole rule, so they are a pure function and these cases
 * — rather than a device pass that could only show the gesture that happened to work.
 */
class PdfLongPressTest {

    private fun allowed(
        kind: PointerKind = PointerKind.FINGER,
        pointerCount: Int = 1,
        playMode: Boolean = false,
        guideOwned: Boolean = false,
        transformOwned: Boolean = false,
        placementOwned: Boolean = false,
        backgroundOwned: Boolean = false,
        insideObjectSelection: Boolean = false,
        onPdfWord: Boolean = true,
    ): Boolean = pdfTextPressAllowed(
        kind = kind,
        pointerCount = pointerCount,
        playMode = playMode,
        guideOwned = guideOwned,
        transformOwned = transformOwned,
        placementOwned = placementOwned,
        backgroundOwned = backgroundOwned,
        insideObjectSelection = insideObjectSelection,
        onPdfWord = onPdfWord,
    )

    @Test
    fun `a still finger on a word may select it, with any tool active`() {
        // The tool is not an input: no parameter names it, which is the point of the gesture.
        assertTrue(allowed())
    }

    @Test
    fun `a read-only document keeps the gesture`() {
        // Also by construction: reading is not writing, so no flag here stands for read-only.
        assertTrue("selecting words writes nothing", allowed(onPdfWord = true))
    }

    @Test
    fun `the pen keeps its own route`() {
        assertFalse("a stylus draws, it does not select", allowed(kind = PointerKind.STYLUS))
        assertFalse("and the eraser erases", allowed(kind = PointerKind.ERASER_TIP))
        assertFalse("a mouse is not a finger hold", allowed(kind = PointerKind.UNKNOWN))
    }

    @Test
    fun `a second finger is a pinch`() {
        assertFalse("two contacts zoom, they do not select", allowed(pointerCount = 2))
    }

    @Test
    fun `an owned gesture keeps the press`() {
        assertFalse("a foreground grip", allowed(transformOwned = true))
        assertFalse("a placement in progress", allowed(placementOwned = true))
        assertFalse("a guide being dragged", allowed(guideOwned = true))
        assertFalse("the background marquee", allowed(backgroundOwned = true))
        assertFalse("the object selection's own long-press lift", allowed(insideObjectSelection = true))
        assertFalse("a replay being chosen", allowed(playMode = true))
    }

    @Test
    fun `a page with no word under the finger arms nothing`() {
        assertFalse("whitespace, a scan, or a page still being read", allowed(onPdfWord = false))
    }
}

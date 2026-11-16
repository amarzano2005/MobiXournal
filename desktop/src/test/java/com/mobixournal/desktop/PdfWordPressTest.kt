package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopPdfTextSelect
import com.mobixournal.desktop.sheet.PdfWordPresses
import com.mobixournal.desktop.sheet.TapTracker
import com.mobixournal.render.PdfHitTolerance
import com.mobixournal.render.PdfTextIndex
import com.mobixournal.render.PdfWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Who owns a press on a PDF page in the window**, and what the two presses of a reading double click do.
 *
 * The rule is a value (`PdfWordPresses.read`), so these cases are named the way the conditions are: the mouse
 * reads, the pen draws, a word is not whitespace, and anything in front of the text keeps its press. The
 * pairing is the window's own (`TapTracker`, with its clock injected), and the range the second press then
 * makes is the text slot's own `DesktopPdfTextSelect` — checked here across lines and backwards, because a
 * reading gesture that could only go forwards on one line would still look right in a screenshot.
 */
class PdfWordPressTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a mouse on a word reads, and the press writes nothing`() {
        val press = PdfWordPresses.read(
            isMouse = true, hasPackets = false, elementInFront = false, wordUnder = true, second = false,
        )
        assertTrue("the text owns the press", press.reading)
        assertFalse("and it is the first of a pair", press.second)
    }

    @Test
    fun `the pen keeps its own route`() {
        val pen = PdfWordPresses.read(
            isMouse = false, hasPackets = false, elementInFront = false, wordUnder = true, second = true,
        )
        assertFalse("a stylus draws, it does not read", pen.reading)
        assertFalse("and a non-reading press is never a selection", pen.second)
    }

    @Test
    fun `the tablet's own packets outrank the words they pass over`() {
        val press = PdfWordPresses.read(
            isMouse = true, hasPackets = true, elementInFront = false, wordUnder = true, second = true,
        )
        assertFalse("a stroke is arriving from the pen", press.reading)
    }

    @Test
    fun `an element in front of the text keeps the double click`() {
        val press = PdfWordPresses.read(
            isMouse = true, hasPackets = false, elementInFront = true, wordUnder = true, second = true,
        )
        assertFalse("a text box, a figure or a placed element answers first", press.reading)
        assertFalse(press.second)
    }

    @Test
    fun `whitespace is not a word, and a page with no text layer never takes the press`() {
        val press = PdfWordPresses.read(
            isMouse = true, hasPackets = false, elementInFront = false, wordUnder = false, second = true,
        )
        assertFalse("the press stays the tool's", press.reading)
        assertFalse(press.second)

        val scan = DesktopPdfFixture
        val tab = scan.tab(temp.root, words = listOf(emptyList()))
        try {
            assertEquals(null, DesktopPdfTextSelect.begin(tab, 55.0, 106.0, 6.0))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `the second press of a pair is the one that selects`() {
        val press = PdfWordPresses.read(
            isMouse = true, hasPackets = false, elementInFront = false, wordUnder = true, second = true,
        )
        assertTrue(press.reading)
        assertTrue("the range starts on this press", press.second)
    }

    @Test
    fun `two presses are a pair only while they stay put and close together`() {
        val taps = TapTracker(windowMs = 400, slopPx = 8.0)
        assertFalse("the first press never is", taps.isSecond(1_000, 100.0, 100.0))
        assertTrue("300 ms later, on the same spot", taps.isSecond(1_300, 103.0, 101.0))
        assertFalse("the pair is spent: this is a new first press", taps.isSecond(1_500, 100.0, 100.0))
        assertFalse("too long apart", taps.isSecond(2_400, 100.0, 100.0))
        assertTrue("and the next one pairs with it", taps.isSecond(2_600, 100.0, 100.0))
        assertFalse("too far apart to be one gesture", taps.isSecond(2_900, 140.0, 100.0))
    }

    @Test
    fun `the press radius follows the window's zoom and DPI, and never exceeds the bounded cap`() {
        assertEquals("8 dp at 100 % is capped at 6 pt", PdfHitTolerance.MAX_PT, PdfWordPresses.tolerancePt(1f, 1f), 1e-9)
        assertEquals("a 200 % window halves the reach in page points", 8.0 / 3.0, PdfWordPresses.tolerancePt(1f, 3f), 1e-9)
        assertEquals("DPI and zoom cancel out at 200 % of both", 4.0, PdfWordPresses.tolerancePt(2f, 2f), 1e-9)
        assertEquals("zoomed out, the cap still holds", 6.0, PdfWordPresses.tolerancePt(3f, 0.5f), 1e-9)
        val far = PdfWordPresses.tolerancePt(1f, 20f)
        assertTrue("a far zoom shrinks it to a hair ($far)", far < 1.0)
    }

    @Test
    fun `the second press reads a range across lines and back again`() {
        val lineOne = listOf(
            PdfWord("Magnetic", 50.0, 100.0, 110.0, 112.0),
            PdfWord("highlighter", 114.0, 100.0, 180.0, 112.0),
        )
        val lineTwo = listOf(
            PdfWord("snaps", 50.0, 120.0, 90.0, 132.0),
            PdfWord("every", 94.0, 120.0, 130.0, 132.0),
            PdfWord("line", 134.0, 120.0, 160.0, 132.0),
        )
        val tab = DesktopPdfFixture.tab(temp.root, words = listOf(lineOne + lineTwo))
        try {
            val begun = DesktopPdfTextSelect.begin(tab, 55.0, 106.0, 6.0)!!
            val forward = DesktopPdfTextSelect.extend(tab, begun, 150.0, 126.0)
            assertEquals("Magnetic highlighter snaps every line", DesktopPdfTextSelect.text(tab, forward))
            val back = DesktopPdfTextSelect.extend(tab, begun.copy(focus = 4), 150.0, 106.0)
            assertEquals("a range that comes back shrinks to what is left", "Magnetic highlighter", DesktopPdfTextSelect.text(tab, back))
            assertEquals("the anchor never moves", 0, forward.anchor)
            assertEquals(4, forward.focus)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `the words the gesture reads are the tab's own index`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            val words = tab.pdfIndex!!.text.words(0)
            assertEquals(listOf("Magnetic", "highlighter"), words.map { it.text })
            assertEquals(
                "and the same index, built twice, answers the same word",
                0,
                PdfTextIndex(listOf(words)).hitWord(0, 55.0, 106.0, 6.0),
            )
        } finally {
            tab.document.close()
        }
    }
}

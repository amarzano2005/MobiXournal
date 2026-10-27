package com.mobixournal.desktop

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings **select** steps through its answers with Left/Right, and wraps at both ends.
 *
 * The stepping is the only part of the control with arithmetic in it — the capsule, the chevron and the
 * menu are layout — so it is the part that can be checked without a scene. Wrapping matters: a select whose
 * Right at the last answer did nothing would look broken on the three formats, where the last one is the
 * one a reader most often wants and the first one is what `Xournal++ (.xopp)` already is.
 */
class SettingsSelectTest {

    private val formats = DesktopNewDocumentFormat.entries

    @Test
    fun `a step lands on its neighbour`() {
        assertEquals(DesktopNewDocumentFormat.ZIPPED, nextChoice(formats, DesktopNewDocumentFormat.XOPP, 1))
        assertEquals(DesktopNewDocumentFormat.MOBIX, nextChoice(formats, DesktopNewDocumentFormat.XOPP, -1))
    }

    @Test
    fun `stepping past an end wraps to the other`() {
        assertEquals(DesktopNewDocumentFormat.MOBIX, nextChoice(formats, DesktopNewDocumentFormat.ZIPPED, 1))
        assertEquals(DesktopNewDocumentFormat.ZIPPED, nextChoice(formats, DesktopNewDocumentFormat.MOBIX, -1))
    }

    /**
     * The list is **as wide as its longest answer**, inside a floor and a cap: three words open a narrow
     * menu, and three phrases open one wide enough to read whole without running into the page's edge.
     */
    @Test
    fun `the list is as wide as its longest answer`() {
        val words = selectMenuWidthFor(30.dp)
        val phrases = selectMenuWidthFor(240.dp)
        assertTrue("a list of words still opens wider than one word: $words", words >= 150.dp)
        assertTrue("a list of phrases stops short of the panel's edge: $phrases", phrases <= 340.dp)
        assertTrue("and is wider than the words: $phrases > $words", phrases > words)
        assertTrue(
            "a phrase too long for the cap is cut at the cap, not past it",
            selectMenuWidthFor(400.dp) == 340.dp,
        )
    }

    @Test
    fun `a value the list does not hold falls back to its first answer`() {
        // A preference edited by hand or written by a build that had another answer: the first one is a
        // better landing than the control showing an answer it cannot step away from.
        val without = formats.filterNot { it == DesktopNewDocumentFormat.XOPP }
        assertEquals(DesktopNewDocumentFormat.MOBIX, nextChoice(without, DesktopNewDocumentFormat.XOPP, 1))
    }
}

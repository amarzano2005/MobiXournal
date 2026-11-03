package com.mobixournal.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings list opens **under the row it belongs to**, not wherever the toolbar's placement happens to
 * fit it.
 *
 * The reader pressed one row; the answers are that row's, so the list starts at its bottom edge and on its
 * start edge. The two exceptions are the only two the layout can force: a row near the bottom lets the list
 * sit above it rather than off-screen, and a row near the right edge drags the list back inside the window.
 * Both keep the list touching its row, which is what "it appears at a random point" was about.
 */
class SettingsSelectPositionTest {

    private val window = IntSize(1080, 2000)
    private val menu = IntSize(320, 600)
    private val margin = 16
    private val gap = 8

    private fun place(anchor: IntRect) =
        settingsSelectOffset(anchor, menu, window, gap, margin, leftToRight = true)

    @Test
    fun `the list hangs under the row, starting on its start edge`() {
        val anchor = IntRect(left = 40, top = 100, right = 1040, bottom = 160)

        val offset = place(anchor)

        assertEquals("its start edge is the row's", 40, offset.x)
        assertEquals("and it begins one gap under the row", 160 + gap, offset.y)
    }

    @Test
    fun `a row near the bottom gets the list above it, not off the screen`() {
        val anchor = IntRect(left = 40, top = 1800, right = 1040, bottom = 1860)

        val offset = place(anchor)

        assertTrue("the list sits above the row", offset.y < anchor.top)
        assertEquals("exactly one gap above it", anchor.top - gap - menu.height, offset.y)
        assertTrue("and still inside the window", offset.y >= margin)
    }

    @Test
    fun `a row near the right edge pulls the list back inside`() {
        val anchor = IntRect(left = 900, top = 300, right = 1040, bottom = 360)

        val offset = place(anchor)

        assertEquals("the list's right edge keeps the window's margin", window.width - margin, offset.x + menu.width)
        assertTrue("and it still starts left of the row's end", offset.x < anchor.right)
    }

    @Test
    fun `a list wider than the window starts at the margin instead of a negative offset`() {
        val anchor = IntRect(left = 40, top = 100, right = 1040, bottom = 160)

        val offset = settingsSelectOffset(anchor, IntSize(1200, 600), window, gap, margin, leftToRight = true)

        assertEquals(margin, offset.x)
    }

    @Test
    fun `the panel is capped so the two margins are always room`() {
        val density = androidx.compose.ui.unit.Density(2f)
        val cap = settingsSelectMaxHeight(windowHeightPx = 2000, density = density)

        assertEquals("the window less one margin at each end", 984f, cap.value, 0.001f)
    }

    @Test
    fun `the list is as wide as its longest answer, and the row is its ceiling`() {
        assertEquals(
            "a short answer still opens a readable list",
            180f,
            settingsSelectWidthFor(widest = 60.dp, rowWidth = 500.dp).value,
            0.001f,
        )
        assertEquals(
            "a long one widens it by its own insets and the check's room",
            258f,
            settingsSelectWidthFor(widest = 200.dp, rowWidth = 500.dp).value,
            0.001f,
        )
        assertEquals(
            "and it never grows past the row it hangs under",
            300f,
            settingsSelectWidthFor(widest = 600.dp, rowWidth = 300.dp).value,
            0.001f,
        )
    }
}

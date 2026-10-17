package com.mobixournal

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Document
import com.mobixournal.render.blankPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one number a board takes from the screen: the size of its **bound**.
 *
 * A sheet whose width is A4 because a printer might one day see it is a narrow band of paper on a
 * 2400 px screen — infinite paper that visibly ends a quarter of the way across — so a strip is born
 * (and is converted, `applyCanvasModeToDocument`) with its locked side set to the pane's extent in pt,
 * which at 100% zoom is the rectangle the sheet has to cover ([boundToPane]). This is the pure half of
 * that rule, so the screen-vs-paper decision is pinned on the JVM rather than only on a device.
 *
 * The paper is written out rather than taken from the app's default: that default is a global a test
 * may have moved, and this test is about the *pane's* extent replacing it.
 */
class BlankBoardBindingTest {

    private val a4 = 595.276

    private val a4Tall = 841.89

    private fun document(pages: Int = 1) = Document(pages = List(pages) { blankPage(a4, a4Tall) })

    @Test
    fun `a vertical strip takes the pane's width as its bound`() {
        val bound = boundToPane(document(), CanvasMode.INFINITE_VERTICAL, 2432.0 to 1372.0)

        assertEquals(2432.0, bound.pages[0].width, 0.001)
        assertEquals("the running axis is not a screen side", a4Tall, bound.pages[0].height, 0.001)
    }

    @Test
    fun `a horizontal strip takes the pane's height, dually`() {
        val bound = boundToPane(document(), CanvasMode.INFINITE_HORIZONTAL, 2432.0 to 1372.0)

        assertEquals(1372.0, bound.pages[0].height, 0.001)
        assertEquals("the running axis keeps the paper", a4, bound.pages[0].width, 0.001)
    }

    @Test
    fun `a page list and a 2D board are left alone`() {
        val pages = document()
        assertEquals(pages.pages[0], boundToPane(pages, CanvasMode.PAGINATED, 2432.0 to 1372.0).pages[0])

        // A 2D board covers the canvas by construction and locks nothing, so there is no side to take
        // from the screen and no bound invented for it.
        assertEquals(pages.pages[0], boundToPane(pages, CanvasMode.INFINITE_2D, 2432.0 to 1372.0).pages[0])
    }

    @Test
    fun `every cell of a converted document lands on the same sheet`() {
        val bound = boundToPane(document(pages = 3), CanvasMode.INFINITE_VERTICAL, 2432.0 to 1372.0)

        assertEquals("the bound is the board's, not a tile's", List(3) { 2432.0 }, bound.pages.map { it.width })
    }
}

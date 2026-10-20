package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The zoom a board opens at (`boardFitZoom`): the least magnification that makes a strip's bound reach
 * the edges of the view.
 *
 * The bound is a paper edge, and paper that ends before the screen does leaves desk down the side — an
 * infinite canvas with a visible empty margin, and a size the user typed that reads as ignored. So the
 * bound **covers** the view, and only the part needed to do it; a bound larger than the view already
 * covers it and is shown at 100% rather than shrunk to fit.
 *
 * The view is expressed in the canvas's own pixels, the unit the sheet is drawn in at 100% — the same
 * unit `boundToPane` takes the pane's extent from, so the two halves of the rule agree on their units.
 */
class BoardFitZoomTest {

    /** A4's width in pt — what an untouched bound would be if the paper ever decided one. */
    private val a4Width = 595.276

    /** 100 mm, the bound a page-format dialog names most often. */
    private val hundredMm = 283.46

    private val viewWidth = 2432

    private val viewHeight = 1372

    @Test
    fun `a bound smaller than the canvas is magnified until the sheet fills it`() {
        // 100 mm tall strip on a 1372 px canvas: the band reaches both edges, no desk on either side.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, hundredMm, viewWidth, viewHeight)

        assertEquals(4.84f, zoom, 0.01f)
        assertEquals("the bound covers the view exactly", viewHeight.toFloat(), zoom * hundredMm.toFloat(), 0.5f)
    }

    @Test
    fun `a bound equal to the canvas opens at one hundred percent`() {
        // The default: a strip born the size of the pane has nothing to magnify.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, viewHeight.toDouble(), viewWidth, viewHeight)

        assertEquals(1f, zoom, 0.0001f)
    }

    @Test
    fun `a bound larger than the canvas opens at one hundred percent rather than shrinking`() {
        // It already covers the screen; shrinking it to fit would put the paper's own scale aside and
        // leave the written millimetres smaller than they are.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 2744.0, viewWidth, viewHeight)

        assertEquals(1f, zoom, 0.0001f)
    }

    @Test
    fun `a vertical strip covers its width, dually`() {
        assertEquals(
            8.58f,
            boardFitZoom(CanvasMode.INFINITE_VERTICAL, hundredMm, 841.89, viewWidth, viewHeight),
            0.01f,
        )
        assertEquals(
            1f,
            boardFitZoom(CanvasMode.INFINITE_VERTICAL, viewWidth.toDouble(), 841.89, viewWidth, viewHeight),
            0.0001f,
        )
    }

    @Test
    fun `a page list and a 2D board open at one hundred percent`() {
        // A 2D board's extent is what an *export* would slice, grown by scrolling, so fitting to it made
        // the zoom depend on how far the board had been panned when it was saved.
        assertEquals(1f, boardFitZoom(CanvasMode.PAGINATED, 595.276, 841.89, viewWidth, viewHeight), 0.0001f)
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_2D, 5000.0, 5000.0, viewWidth, viewHeight), 0.0001f)
    }

    @Test
    fun `nothing to fit answers one hundred percent`() {
        // Before the first layout, and for a geometry that names no bound yet.
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 0.0, 0, 0), 0.0001f)
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 0.0, viewWidth, viewHeight), 0.0001f)
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_VERTICAL, 0.0, a4Width, viewWidth, viewHeight), 0.0001f)
    }

    @Test
    fun `the fit stays inside the zoom range`() {
        // A bound of one point would otherwise ask for 1372x, which the viewport cannot hold.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 1.0, viewWidth, viewHeight)

        assertEquals(ViewportState.MAX_ZOOM, zoom, 0.0001f)
    }
}

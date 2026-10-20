package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The zoom a board opens at (`boardFitZoom`): the whole bound, never magnified.
 *
 * A strip's bound is a **measurement** — the width or height typed in the page-format dialog — and a
 * zoom that grows it to fill the screen throws that measurement away: a 100 mm strip came back at 484%,
 * its paper edge off-screen, which reads exactly like "the size I set was ignored". So a strip opens
 * with its whole locked axis inside the canvas and never past 100%, which leaves the pane-sized default
 * (a bound equal to the canvas) covering the screen exactly as before.
 *
 * The view is expressed in the canvas's own pixels, the unit the sheet is drawn in at 100% — the same
 * unit `boundToPane` takes the pane's extent from, so the two halves of the rule agree on their units.
 */
class BoardFitZoomTest {

    /** A4's width in pt — what an untouched bound would be if the paper ever decided one. */
    private val a4Width = 595.276

    @Test
    fun `a bound smaller than the canvas opens at one hundred percent, desk around it`() {
        // 100 mm tall strip on a 1372 px canvas: shown whole, at its own size — not zoomed up to 484%.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 283.46, 2432, 1372)

        assertEquals(1f, zoom, 0.0001f)
    }

    @Test
    fun `a bound larger than the canvas is shrunk to show all of it`() {
        // The default strip is born the size of the pane; on a narrower pane it has to come down.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 2744.0, 2432, 1372)

        assertEquals(0.5f, zoom, 0.0001f)
    }

    @Test
    fun `a vertical strip fits its width, dually`() {
        assertEquals(
            1f,
            boardFitZoom(CanvasMode.INFINITE_VERTICAL, 283.46, 841.89, 2432, 1372),
            0.0001f,
        )
        assertEquals(
            0.5f,
            boardFitZoom(CanvasMode.INFINITE_VERTICAL, 4864.0, 841.89, 2432, 1372),
            0.0001f,
        )
    }

    @Test
    fun `a page list and a 2D board open at one hundred percent`() {
        // A 2D board's extent is what an *export* would slice, grown by scrolling, so fitting to it made
        // the zoom depend on how far the board had been panned when it was saved.
        assertEquals(1f, boardFitZoom(CanvasMode.PAGINATED, 595.276, 841.89, 2432, 1372), 0.0001f)
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_2D, 5000.0, 5000.0, 2432, 1372), 0.0001f)
    }

    @Test
    fun `nothing to fit answers one hundred percent`() {
        // Before the first layout, and for a geometry that names no bound yet.
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 0.0, 0, 0), 0.0001f)
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 0.0, 2432, 1372), 0.0001f)
        assertEquals(1f, boardFitZoom(CanvasMode.INFINITE_VERTICAL, 0.0, a4Width, 2432, 1372), 0.0001f)
    }

    @Test
    fun `the fit stays inside the zoom range`() {
        // A bound a hundred times the pane may not ask for 1% — the viewport clamps, and a zoom it
        // cannot hold would leave the sheet half off-screen with a badge claiming otherwise.
        val zoom = boardFitZoom(CanvasMode.INFINITE_HORIZONTAL, a4Width, 137_200.0, 2432, 1372)

        assertEquals(ViewportState.MIN_ZOOM, zoom, 0.0001f)
    }
}

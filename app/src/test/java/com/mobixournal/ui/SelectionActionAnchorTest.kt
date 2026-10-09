package com.mobixournal.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The floating selection bar's placement is pure geometry, so it is pinned here rather than left to
 * a device pass: a bar that clips off an edge or lands under the tab strip is exactly the kind of
 * thing a screenshot on one screen size would miss.
 *
 * The **default is below** the selection — the bar reads as the controls *of* the thing selected —
 * and it moves above only when the canvas ends below it.
 */
class SelectionActionAnchorTest {

    private val canvas = IntSize(1080, 1920)
    private val bar = IntSize(600, 100)
    private val gap = 10
    private val margin = 8

    private fun offset(anchorX: Float, top: Float, bottom: Float) = selectionBarOffset(
        anchorX = anchorX,
        selectionTop = top,
        selectionBottom = bottom,
        barSize = bar,
        canvasSize = canvas,
        gapPx = gap,
        marginPx = margin,
    )

    @Test
    fun `sits centred below the selection when there is room`() {
        // Selection spans y 800..1000, centred at x 540: bar centred under it, just past the bottom edge.
        assertEquals(IntOffset(240, 1010), offset(anchorX = 540f, top = 800f, bottom = 1000f))
    }

    @Test
    fun `moves above the selection when the canvas ends below it`() {
        // A bar below would sit at 1900, past the last legal row (1920 - 100 - 8); it goes above the
        // top edge instead: 1700 - 100 - 10.
        assertEquals(IntOffset(240, 1590), offset(anchorX = 540f, top = 1700f, bottom = 1890f))
    }

    @Test
    fun `stays clear of the left edge`() {
        // Centred would be x -250; clamped to the margin.
        assertEquals(IntOffset(8, 1010), offset(anchorX = 50f, top = 800f, bottom = 1000f))
    }

    @Test
    fun `stays clear of the right edge`() {
        // Centred would be x 770; the widest legal x is 1080 - 600 - 8.
        assertEquals(IntOffset(472, 1010), offset(anchorX = 1070f, top = 800f, bottom = 1000f))
    }

    @Test
    fun `is pulled inside the bottom edge when neither side has room`() {
        val short = IntSize(1080, 400)
        val offset = selectionBarOffset(
            anchorX = 540f,
            selectionTop = 100f,
            selectionBottom = 380f,
            barSize = bar,
            canvasSize = short,
            gapPx = gap,
            marginPx = margin,
        )
        // Below (390) and above (-10) both miss a 400px canvas; it stays under the selection, pulled
        // up to the last legal row (400 - 100 - 8) rather than jumping to the top of the canvas.
        assertEquals(IntOffset(240, 292), offset)
    }

    @Test
    fun `parks at the margin before the bar has been measured`() {
        val offset = selectionBarOffset(
            anchorX = 540f,
            selectionTop = 800f,
            selectionBottom = 1000f,
            barSize = IntSize.Zero,
            canvasSize = canvas,
            gapPx = gap,
            marginPx = margin,
        )
        assertEquals(IntOffset(8, 8), offset)
    }
}

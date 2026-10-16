package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.io.CanvasGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a board's sheet and its tiles land — and that the canvas and the tile manager agree about what
 * is on screen.
 *
 * The two are the pair that must not drift: [ViewportState.visibleChunkRange] answers "which cells does
 * the viewport cover" from the scroll and zoom, and [ChunkTileManager.visibleChunks] answers "which of
 * them do I hold" — a board that drew the union of the two would show a tile the viewport is not on, and
 * a board that intersected them wrongly would leave a hole in the writing.
 *
 * The placement itself is pinned because it is the one thing a chunk page cannot say for itself: a
 * chunk knows its coordinate, not where that coordinate is in pixels, and a sheet has to be positioned
 * so its ruling is continuous with document `(0, 0)` rather than restarting wherever the band begins.
 */
class BoardViewportTest {

    private fun geometry(
        mode: CanvasMode,
        widthPt: Double = 595.276,
        heightPt: Double = 841.89,
        chunkSizePt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
        coords: List<Pair<Int, Int>> = emptyList(),
    ) = CanvasGeometry(mode, widthPt, heightPt, chunkSizePt, coords)

    @Test
    fun `the viewport range and the tiles held agree on what is on screen`() {
        val viewport = ViewportState().apply {
            canvasMode = CanvasMode.INFINITE_2D
            setBounds(1024f, 1024f, 1024f, 1024f)
            scrollToX(1024f)
            scrollToY(2048f)
        }
        val range = viewport.visibleChunkRange(1024.0)
        assertEquals(1, range.cxMin)
        assertEquals(2, range.cxMax)
        assertEquals(2, range.cyMin)
        assertEquals(3, range.cyMax)

        val tiles = ChunkTileManager(chunkSizePt = 1024.0)
        val held = listOf(0 to 0, 1 to 2, 2 to 3, 3 to 3, 1 to 1)
        held.forEach { (cx, cy) -> tiles.putChunk(MobixChunk(cx = cx, cy = cy)) }

        assertEquals(
            "the tiles on screen are exactly the held ones the range covers",
            listOf(1 to 2, 2 to 3),
            tiles.visibleChunks(range).map { it.cx to it.cy }.sortedWith(compareBy({ it.first }, { it.second })),
        )
    }

    @Test
    fun `a tile is drawn in the cell its coordinate names`() {
        val geometry = geometry(CanvasMode.INFINITE_2D, coords = listOf(2 to -1))
        val at = BoardViewport.tile(geometry, cx = 2, cy = -1, zoom = 2f)

        assertEquals((2 * 1024.0 * 2f).toFloat(), at.leftPx, 0.01f)
        assertEquals((-1 * 1024.0 * 2f).toFloat(), at.topPx, 0.01f)
        assertEquals((1024.0 * 2f).toFloat(), at.widthPx, 0.01f)
        assertEquals((1024.0 * 2f).toFloat(), at.heightPx, 0.01f)
    }

    @Test
    fun `a vertical strip locks its width and its sheet runs down the page`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, widthPt = 595.276)
        val at = BoardViewport.sheet(
            geometry = geometry,
            zoom = 1f,
            scrollX = 0f,
            scrollY = 500f,
            viewWidthPx = 800f,
            viewHeightPx = 1000f,
            spacingPt = 24.0,
        )

        assertEquals("the width is the paper's, not the viewport's", 0f, at.leftPx, 0.01f)
        assertEquals(595.276f, at.widthPx, 0.01f)
        assertEquals("the band starts on the ruling grid ...", 0f, at.topPx % 24.0f, 0.01f)
        assertTrue("... at or above the scroll", at.topPx <= 500f)
        assertTrue("... and covers what the viewport shows", at.topPx + at.heightPx >= 1500f)
    }

    @Test
    fun `a horizontal strip locks its height and its sheet runs right`() {
        val geometry = geometry(CanvasMode.INFINITE_HORIZONTAL, heightPt = 841.89)
        val at = BoardViewport.sheet(
            geometry = geometry,
            zoom = 1f,
            scrollX = 500f,
            scrollY = 0f,
            viewWidthPx = 800f,
            viewHeightPx = 1000f,
            spacingPt = 24.0,
        )

        assertEquals("the height is the paper's, not the viewport's", 0f, at.topPx, 0.01f)
        assertEquals(841.89f, at.heightPx, 0.01f)
        assertEquals(0f, at.leftPx % 24.0f, 0.01f)
        assertTrue(at.leftPx <= 500f)
        assertTrue(at.leftPx + at.widthPx >= 1300f)
    }

    @Test
    fun `a board with no locked axis is aligned on both axes`() {
        val geometry = geometry(CanvasMode.INFINITE_2D)
        val at = BoardViewport.sheet(
            geometry = geometry,
            zoom = 2f,
            scrollX = 100f,
            scrollY = -700f,
            viewWidthPx = 800f,
            viewHeightPx = 600f,
            spacingPt = 14.17,
        )

        // An aligned pt origin is a whole number of ruling steps from `(0, 0)` in px too.
        val step = 14.17f * 2f
        assertEquals("the band starts on a ruling line", Math.round(at.leftPx / step).toFloat(), at.leftPx / step, 0.001f)
        assertEquals(Math.round(at.topPx / step).toFloat(), at.topPx / step, 0.001f)
        assertTrue("the band covers the viewport", at.leftPx <= 100f && at.leftPx + at.widthPx >= 900f)
        assertTrue(at.topPx <= -700f && at.topPx + at.heightPx >= -100f)
    }

    @Test
    fun `a board that declares no size falls back to the default paper`() {
        val geometry = geometry(CanvasMode.INFINITE_VERTICAL, widthPt = 0.0, heightPt = 0.0)
        val at = BoardViewport.sheet(
            geometry = geometry,
            zoom = 1f,
            scrollX = 0f,
            scrollY = 0f,
            viewWidthPx = 800f,
            viewHeightPx = 1000f,
            spacingPt = 24.0,
        )
        assertEquals(DrawingSurfaceDefaults.defaultPageWidth.toFloat(), at.widthPx, 0.01f)
    }
}

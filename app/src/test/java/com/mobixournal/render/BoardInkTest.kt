package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.io.CanvasGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A board's ink: the chunk a document point belongs to, what survives an eviction, and the erase that
 * has to load a chunk first (`docs/infinite-canvas.md` → *Writing on a board*).
 *
 * The document here is a plain list of pages behind the store's own seams — the same shape the canvas
 * wires the page window and the working document into — so the tests exercise exactly the store the
 * view uses, with no Android types in the way.
 */
class BoardInkTest {

    private val white = Background.Solid(0xFFFFFFFF.toInt(), "plain")

    private class Board(val pages: MutableList<Page>, val grid: BoardGrid, val ink: BoardInk, val tiles: ChunkTileManager)

    /** A board whose document has a chunk at each of [cells], on the default tile grid. */
    private fun board(vararg cells: Pair<Int, Int>, mode: CanvasMode = CanvasMode.INFINITE_2D): Board {
        val size = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT
        val geometry = CanvasGeometry(
            mode = mode,
            widthPt = 595.28,
            heightPt = 841.89,
            chunkSizePt = size,
            chunkCoords = cells.toList(),
        )
        val pages = cells.map { Page(size, size, white, listOf(Layer(elements = emptyList()))) }.toMutableList()
        val grid = BoardGrid(cells.toList(), mode)
        val tiles = ChunkTileManager(chunkSizePt = size)
        val ink = BoardInk(
            tiles = tiles,
            grid = grid,
            geometry = geometry,
            chunkSizePt = size,
            paper = { white },
            readPage = { index -> pages.getOrNull(index) },
            writePage = { index, page -> if (index in pages.indices) { pages[index] = page; true } else false },
            appendPage = { page -> pages += page; true },
        )
        return Board(pages, grid, ink, tiles)
    }

    /** A pen stroke whose only point is at ([x], [y]) **in the chunk's own coordinates**. */
    private fun stroke(x: Double, y: Double): Stroke =
        Stroke(Tool.PEN, 0xFF000000.toInt(), "round", listOf(StrokePoint(x, y, 1.0)), false)

    /** Every stroke the page holds, flattened over its layers. */
    private fun strokes(page: Page): List<Stroke> =
        page.layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    @Test
    fun aStrokeCommittedAtADocumentPointLandsInTheChunkUnderThePen() {
        val board = board(0 to 0, 1 to 0)
        val size = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT

        // The point is inside cell (1, 0) and 30 pt in from that cell's own corner.
        val written = board.ink.writeAt(size + 30.0, 40.0) { page -> page.copy(layers = listOf(Layer(listOf(stroke(30.0, 40.0))))) }

        assertTrue(written)
        // Found through the store, under the chunk the coordinate names ...
        val chunk = board.ink.chunkAt(1 to 0)
        assertNotNull(chunk)
        assertEquals(1, chunk!!.cx)
        assertEquals(0, chunk.cy)
        assertEquals(1, strokes(chunk.toPage()).size)
        // ... and in the document, on the page that chunk stands for.
        assertEquals(1, strokes(board.pages[1]).size)
        assertEquals(0, strokes(board.pages[0]).size)
    }

    @Test
    fun anEvictedChunkIsReadBackWithItsStroke() {
        val board = board(0 to 0, 1 to 0)
        board.ink.writeAt(1100.0, 40.0) { page -> page.copy(layers = listOf(Layer(listOf(stroke(10.0, 40.0))))) }

        // The viewport scrolls far away, so the tile manager drops the chunk.
        board.tiles.evictOffscreen(ChunkRange(40, 41, 40, 41))
        assertNull(board.tiles.getChunk(1, 0))

        // Reading it back reads it through the document, so the ink is still there.
        val read = board.ink.chunkAt(1 to 0)
        assertNotNull(read)
        assertEquals(1, strokes(read!!.toPage()).size)
    }

    @Test
    fun anEraseOnAnEvictedChunkLoadsItFirst() {
        val board = board(0 to 0, 1 to 0)
        board.ink.writeAt(1100.0, 40.0) { page -> page.copy(layers = listOf(Layer(listOf(stroke(10.0, 40.0))))) }
        board.tiles.evictOffscreen(ChunkRange(40, 41, 40, 41))

        val erased = board.ink.editCell(1 to 0) { page ->
            PageEraser.erase(page, 10.0, 40.0, 2.0, EraserMode.WHOLE_STROKE, onlyLayer = 0) ?: page
        }

        assertTrue(erased)
        assertEquals(0, strokes(board.pages[1]).size)
        // The chunk the eraser loaded is the document's own page again, not a stale copy.
        assertEquals(0, strokes(board.ink.chunkAt(1 to 0)!!.toPage()).size)
    }

    @Test
    fun anEraseInACellTheBoardHasNoChunkForChangesNothing() {
        val board = board(0 to 0)

        assertFalse(board.ink.editCell(9 to 9) { page -> page.copy(layers = emptyList()) })
        assertEquals(1, board.pages.size)
    }

    @Test
    fun aWritePastTheLastChunkCreatesTheCell() {
        val board = board(0 to 0, mode = CanvasMode.INFINITE_VERTICAL)
        val size = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT

        val written = board.ink.writeAt(20.0, size + 10.0) { page -> page.copy(layers = listOf(Layer(listOf(stroke(20.0, 10.0))))) }

        assertTrue(written)
        assertEquals(2, board.pages.size)
        assertEquals(listOf(0 to 0, 0 to 1), board.grid.toList())
        assertEquals(1, board.ink.pageIndexFor(0 to 1))
        // A fresh cell is born on the board's own paper, one tile tall in a vertical strip.
        assertEquals(white, board.pages[1].background)
        assertEquals(size, board.pages[1].height, 1e-9)
    }

    @Test
    fun aWriteIntoACellTheBoardAlreadyHasDoesNotGrowTheDocument() {
        val board = board(0 to 0, 0 to 1, mode = CanvasMode.INFINITE_VERTICAL)

        board.ink.writeAt(20.0, 1500.0) { page -> page.copy(layers = listOf(Layer(listOf(stroke(20.0, 476.0))))) }

        assertEquals(2, board.pages.size)
        assertEquals(listOf(0 to 0, 0 to 1), board.grid.toList())
    }
}

package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import kotlin.math.floor

/**
 * The ink a **board** is written with: which chunk a document point belongs to, the chunk the
 * document keeps for it, and the one place a cell is created (`docs/infinite-canvas.md` → *Writing on
 * a board*).
 *
 * Three facts make this small, and each is a decision rather than an accident:
 *
 * - **A stroke is stored cell-local**, like every element of a chunk: `x = 0` is the left edge of the
 *   tile it lives in. The box a gesture converts through is the cell's own box
 *   ([boardCellBox][com.mobixournal.render.boardCellBox]), the very box the drawing pass reads the ink
 *   back with, so what is written and what is drawn cannot disagree about where the ink is.
 * - **The document is the store.** A write lands on the page the chunk stands for, through the page
 *   window, so it is held by the overlay that eviction never touches. A chunk the viewport drops from
 *   the tile cache therefore cannot lose a stroke: the cache is a copy of a page the document owns,
 *   and reading the chunk back ([chunkAt]) reads it through the document rather than out of the file
 *   the document came from. That is the trap the page window already closed for pages
 *   (`docs/data-path.md` → *The page window*), applied to a grid.
 * - **A cell the board has no chunk for is created on the spot.** An infinite canvas that stops at
 *   its first tile is not one, so [writeAt] appends the page and names the cell after the point the
 *   pen went down at ([cellPage]).
 *
 * Headless on purpose — no Android types and no view — so the arithmetic that is easy to get subtly
 * wrong (the coordinate, the cell a stroke belongs to, the load-before-erase) is unit-tested on the
 * JVM ([BoardInkTest]).
 *
 * @property grid The board's occupied cells, in document order.
 * @property chunkSizePt The document's own tile size, in pt (`canvas.json`), never this build's
 *   default: a file written with another tile size has to be read and written on its own grid.
 * @param readPage The document's page at an index, through the window that holds its edits.
 * @param writePage Replace the document's page at an index — false when the write was refused.
 * @param appendPage Append a page to the document — false when the write was refused.
 */
internal class BoardInk(
    private val tiles: ChunkTileManager,
    val grid: BoardGrid,
    private val geometry: CanvasGeometry,
    val chunkSizePt: Double,
    /** The board's paper, for a cell born here — null when the board names no solid background. */
    private val paper: () -> Background?,
    private val readPage: (Int) -> Page?,
    private val writePage: (Int, Page) -> Boolean,
    private val appendPage: (Page) -> Boolean,
) {

    /**
     * The cell the document point ([ptX], [ptY]) falls in: `floor(pt / chunkSizePt)` per axis, the
     * same reading the chunk names are written with.
     */
    fun cellAt(ptX: Double, ptY: Double): Pair<Int, Int> =
        floor(ptX / chunkSizePt).toInt() to floor(ptY / chunkSizePt).toInt()

    /** The document's page for [cell], or null when the board has no chunk there yet. */
    fun pageIndexFor(cell: Pair<Int, Int>): Int? = grid.indexOf(cell).takeIf { it >= 0 }

    /**
     * Give [cell] a page, and return it: the board's chunk there, created when it had none. Null when
     * the document refused the page.
     *
     * The form a placement uses — a text box, an image or a formula tapped down in an empty cell needs a
     * page to live on before the editor is told where it went.
     */
    fun ensureCell(cell: Pair<Int, Int>): Int? = pageIndexFor(cell) ?: createCell(cell)

    /** The cell page [index] sits at, or null when the index names no cell of this board. */
    fun cellOf(index: Int): Pair<Int, Int>? = grid.coordAt(index)

    /**
     * The chunk at [cell] — from the tile cache when it is resident, and otherwise **read back through
     * the document**, which is what makes a chunk the viewport evicted come back with the ink that was
     * written into it.
     */
    fun chunkAt(cell: Pair<Int, Int>): MobixChunk? {
        tiles.getChunk(cell.first, cell.second)?.let { return it }
        val index = pageIndexFor(cell) ?: return null
        val page = readPage(index) ?: return null
        return MobixChunk.fromPage(page, cell.first, cell.second).also { tiles.putLoaded(it) }
    }

    /**
     * Write into the chunk under a document point, creating the board's chunk there when it has none.
     *
     * [transform] returns the rewritten page, or null (or the same instance) when nothing changed, in
     * which case the document is left alone — the same no-op contract [PageWindow.edit] has.
     */
    fun writeAt(ptX: Double, ptY: Double, transform: (Page) -> Page?): Boolean =
        writeCell(cellAt(ptX, ptY), transform)

    /**
     * Write into the chunk at [cell], creating the board's chunk there when it has none — the form the
     * ink path uses, because a gesture names its cell at pointer-down and keeps it for the whole stroke
     * (a stroke is stored under the chunk the pen went down in, not under wherever it ended).
     */
    fun writeCell(cell: Pair<Int, Int>, transform: (Page) -> Page?): Boolean {
        val index = ensureCell(cell) ?: return false
        return edit(index, cell, transform)
    }

    /**
     * Rewrite the chunk at [cell], **without** creating one: an erase in an empty cell has nothing to
     * change, and a cell nobody wrote in should not appear in the file.
     */
    fun editCell(cell: Pair<Int, Int>, transform: (Page) -> Page?): Boolean {
        val index = pageIndexFor(cell) ?: return false
        return edit(index, cell, transform)
    }

    /**
     * The one edit path: materialise the chunk's page through the document, transform it, write it
     * back, and keep the tile cache agreeing with what the document now holds.
     */
    private fun edit(index: Int, cell: Pair<Int, Int>, transform: (Page) -> Page?): Boolean {
        val page = readPage(index) ?: return false
        val edited = transform(page) ?: return false
        if (edited === page) return false
        if (!writePage(index, edited)) return false
        // The cache follows the document rather than the other way round: a chunk read back later
        // reads this page, so the two cannot drift.
        tiles.putChunk(MobixChunk.fromPage(edited, cell.first, cell.second))
        return true
    }

    /**
     * Create the board's chunk at [cell]: the grid is grown **before** the page, because the canvas
     * reconciles the two by the document's page count and a page that arrived first would have no cell
     * to be named by. Returns the new page's index, or null when the document refused it.
     */
    private fun createCell(cell: Pair<Int, Int>): Int? {
        val index = grid.size
        val page = cellPage(geometry, paper())
        grid.append(cell)
        if (!appendPage(page)) {
            grid.dropLast()
            return null
        }
        return index
    }
}

/**
 * The page a cell the board has no chunk for is **born on**: the board's own paper, carrying the same
 * background style and config the sheet is ruled with, sized to the mode's locked dimension (a strip)
 * or to one tile ([CanvasMode.INFINITE_2D]).
 *
 * The size matters more than it looks: a chunk page *is* what its content measures, and a strip's
 * pages are one paper wide while a 2D board's are one tile. Ink is stored cell-local either way, so a
 * page narrower than its cell simply draws in the band it names.
 */
internal fun cellPage(geometry: CanvasGeometry, paper: Background?): Page {
    val tile = geometry.chunkSizePt.takeIf { it > 0.0 } ?: MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT
    val vertical = geometry.mode == CanvasMode.INFINITE_VERTICAL
    val horizontal = geometry.mode == CanvasMode.INFINITE_HORIZONTAL
    // No coordinate is recorded on the page: the grid names the cell, and a chunk's coordinates are
    // read out of its *name* in the archive (`MobixFormat.parseChunkEntry`), which the write derives
    // from the grid rather than from anything a page carries.
    return Page(
        width = when {
            vertical -> geometry.widthPt.takeIf { it > 0.0 } ?: DrawingSurfaceDefaults.defaultPageWidth
            else -> tile
        },
        height = when {
            horizontal -> geometry.heightPt.takeIf { it > 0.0 } ?: DrawingSurfaceDefaults.defaultPageHeight
            else -> tile
        },
        background = paper ?: Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements = emptyList())),
    )
}

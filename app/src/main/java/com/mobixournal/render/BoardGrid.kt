package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.stripCoords

/**
 * The occupied cells of a board, **in the document's own order**: index `i` here is page `i` of the
 * document, which is the fact the whole canvas engine turns on (`docs/infinite-canvas.md` → *A chunk
 * is a part*).
 *
 * The grid starts as the one the file declared ([com.mobixournal.io.CanvasGeometry.chunkCoords], or
 * the strip a brand-new board was born on) and **grows** as ink is written into cells the board has
 * no chunk for yet — a board that cannot be written past its first chunk is not an infinite canvas.
 * A cell this session created is appended at the end, exactly where the document's next page goes, so
 * `grid[i]` names `pages[i]` at every moment.
 *
 * Undo and redo move the page list, and the grid has to move with it — the history snapshots the
 * *document*, which carries no coordinates. [fit] is that reconciliation, and it is a small state
 * machine rather than a recalculation: a cell an undo took away is kept on the side, and a redo hands
 * it back in the same order, which is what makes `undo` then `redo` land every chunk exactly where it
 * was. A page that arrives with no cell recorded at all — a page-list command, which a board refuses
 * — falls back to the mode's own strip, the same guess the writer makes
 * ([MobixDocument.of][com.mobixournal.format.mobix.MobixDocument]).
 *
 * Pure: no Android types, so the append/undo/redo arithmetic is unit-tested on the JVM
 * ([BoardGridTest]).
 */
internal class BoardGrid(
    initial: List<Pair<Int, Int>>,
    /** The mode whose strip a page with no recorded cell falls back to. */
    private val mode: CanvasMode = CanvasMode.INFINITE_2D,
) {

    /** The cells, in document order. Always `pages.size` long once [fit] has been told the count. */
    private val cells = ArrayList(initial)

    /** Cells an undo took away, oldest first — a redo hands them back in that order. */
    private val undone = ArrayList<Pair<Int, Int>>()

    /** How many cells the board has — the document's page count, as long as the two are in step. */
    val size: Int get() = cells.size

    /** The cell page [index] sits at, or null when the board has fewer cells than that. */
    fun coordAt(index: Int): Pair<Int, Int>? = cells.getOrNull(index)

    /** Where [cell] sits in the document, or `-1` when the board has no chunk there. */
    fun indexOf(cell: Pair<Int, Int>): Int = cells.indexOf(cell)

    /** The cells, in document order — the shape a save records. */
    fun toList(): List<Pair<Int, Int>> = cells.toList()

    /** Walk the cells in document order, with the index each sits at — the paint pass's own loop. */
    fun forEach(action: (index: Int, cell: Pair<Int, Int>) -> Unit) {
        for (i in cells.indices) action(i, cells[i])
    }

    /**
     * A cell this session created, appended where the document's next page goes.
     *
     * The side list of undone cells is dropped: a new edit discards the redo branch, which is exactly
     * what the document history does, and a coordinate kept from a branch nothing can redo would name
     * the wrong cell.
     */
    fun append(cell: Pair<Int, Int>) {
        undone.clear()
        cells += cell
    }

    /**
     * Take a cell back off the end — the inverse of [append], for a write that could not be
     * completed after all.
     */
    fun dropLast() {
        if (cells.isNotEmpty()) cells.removeAt(cells.lastIndex)
    }

    /**
     * Follow the document's page list: trim the tail an undo removed, and hand back what a redo
     * restored. A page nothing recorded a cell for takes the mode's strip position for its index.
     */
    fun fit(pageCount: Int) {
        while (cells.size > pageCount) undone += cells.removeAt(cells.lastIndex)
        while (cells.size < pageCount) {
            cells += if (undone.isNotEmpty()) undone.removeAt(undone.lastIndex) else stripCoords(cells.size, mode)
        }
    }
}

/**
 * [DrawingSurfaceView]'s **board** ink path: where a gesture on an infinite canvas writes, what the
 * eraser loads, and the box the pen converts through.
 *
 * It is the board's half of `DrawingSurfaceStrokes.kt`, kept apart because the two halves answer
 * different questions: a page stack's ink is addressed by a **page index** in a laid-out stack
 * ([PageStacker]), while a board's is addressed by a **cell** in a grid whose coordinates are a fact
 * of the file ([BoardGrid]). The page half calls in here whenever `board` is non-null, and every
 * decision about coordinates, cells and the document page behind a chunk lives in this one file plus
 * [BoardInk], which owns the arithmetic and is unit-tested on its own.
 */
package com.mobixournal.render

import com.mobixournal.format.model.Stroke

/**
 * Where the pen is writing at the view point ([viewX], [viewY]): for a board the **cell** under the
 * pointer — its own box, in the board's coordinate space, with the document page it will be written
 * into — and for a page stack the page box the point falls on.
 *
 * A board's ink is stored **cell-local** (the box's origin is the cell's corner), which is what the
 * drawing pass reads it back with ([boardTileBox]) and what the chunk files hold, so the same box both
 * converts the point and names the chunk it commits into. The cell is remembered for the gesture
 * ([DrawingSurfaceView.gestureCell]): when the board has no chunk there yet, the page index is the one
 * that cell *will* have, and the coordinate is the only way back to it.
 */
internal fun DrawingSurfaceView.gestureBoxAt(viewX: Float, viewY: Float): PageBox? {
    val board = board ?: return layout.pageAt(viewX + scrollX, viewY + scrollY)
    val cell = inkCellAt(board, viewX, viewY)
    gestureCell = cell
    val index = board.grid.indexOf(cell).takeIf { it >= 0 } ?: board.grid.size
    return boardInkBox(board, index, cell.first, cell.second)
}

/**
 * The box the current gesture converts through, re-derived per move event: a board's cell (from the
 * grid, or from [DrawingSurfaceView.gestureCell] while the cell is one the document has no page for
 * yet), or the page box of the page the gesture started on.
 */
internal fun DrawingSurfaceView.gestureBox(): PageBox? {
    val board = board ?: return layout.boxes.getOrNull(currentPage)
    val cell = board.grid.coordAt(currentPage) ?: gestureCell ?: return null
    return boardInkBox(board, currentPage, cell.first, cell.second)
}

/**
 * Commit [stroke] into the chunk the gesture began in — creating the board's chunk when the pen went
 * down in a cell it had none for, which is what makes an infinite canvas writable rather than only
 * drawable.
 *
 * No relayout: a board draws from its grid and the viewport's own coordinates, not from the page stack,
 * so the stack the canvas happens to be holding is nobody's business.
 */
internal fun DrawingSurfaceView.appendChunkStroke(pageIndex: Int, stroke: Stroke) {
    val board = board ?: return
    // The cell the gesture began in: the page's own cell, or the one recorded while the board still had
    // no chunk there. A stroke is stored under the chunk the pen went down in, not wherever it ended.
    val cell = board.grid.coordAt(pageIndex) ?: gestureCell ?: return
    if (!board.ink.writeCell(cell) { page -> withStroke(page, stroke) }) return
    render()
}

/**
 * The eraser on a board: the chunk under the pen is **loaded first** ([BoardInk.editCell] reads it
 * through the document), so rubbing a chunk the viewport evicted erases the ink that is really there
 * rather than nothing.
 */
internal fun DrawingSurfaceView.eraseFromChunk(
    pageIndex: Int,
    px: Double,
    py: Double,
    radius: Double,
    mode: EraserMode,
): Boolean {
    val board = board ?: return false
    val cell = board.grid.coordAt(pageIndex) ?: return false
    val changed = board.ink.editCell(cell) { page ->
        val target = resolvedActiveLayer(page)
        val hidden = page.layers.indices.filterTo(HashSet()) { isLayerHidden(pageIndex, it) }
        // `erase` answers null when the disc touched nothing; handing the page straight back keeps the
        // window's edit a no-op, so a wipe that erased nothing records no undo step.
        PageEraser.erase(page, px, py, radius, mode, hidden, target) ?: page
    }
    if (changed) render()
    return changed
}

/**
 * A placement tap on a board: the cell under the tap, **created** when the board has no chunk there,
 * then handed on like any other placement.
 *
 * A placement is a deliberate tap — a text box, an image or a formula needs a page to live on — so this
 * is the one path that gives an empty cell a page without ink. The index it names is the page the cell
 * just got, so nothing downstream has to know it was new.
 */
internal fun DrawingSurfaceView.commitChunkPlace(kind: PlaceKind) {
    val board = board ?: return
    val cell = inkCellAt(board, placeDownX, placeDownY)
    val index = board.ink.ensureCell(cell) ?: return
    val box = boardInkBox(board, index, cell.first, cell.second)
    val xPt = box.toPtX(placeDownX, scrollX)
    val yPt = box.toPtY(placeDownY, scrollY)
    val existing = if (kind == PlaceKind.TEXT) textEdits.pickForEditing(index, xPt, yPt) else null
    onPlace?.invoke(kind, Placement(index, xPt, yPt, existing))
}

/**
 * The cell a view point falls in, on the board's own grid: the scroll and the zoom are the whole
 * conversion — a cell's corner is its coordinate times the tile size, so the document point is the
 * view point un-scrolled and un-zoomed ([BoardViewport]).
 */
private fun DrawingSurfaceView.inkCellAt(board: BoardCanvas, viewX: Float, viewY: Float): Pair<Int, Int> {
    val scale = if (zoom > 0f) zoom else 1f
    return board.ink.cellAt(
        ((viewX + scrollX) / scale).toDouble(),
        ((viewY + scrollY) / scale).toDouble(),
    )
}

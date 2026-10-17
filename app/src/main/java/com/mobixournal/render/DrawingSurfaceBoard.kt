package com.mobixournal.render

import android.graphics.Canvas
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page

/**
 * The **board** pass of [DrawingSurfaceView.paint]: the unbounded sheet, then the ink of the tiles the
 * viewport intersects.
 *
 * It replaces the page-stack pass rather than joining it — a board has no pages to stack, and a
 * `.mobix` whose chunk count happens to read like a page count must not be drawn as if it were one
 * page per screen. What is drawn instead is one **sheet** (the paper, ruled continuously over the
 * whole board: [BoardViewport.sheet]) plus one box per occupied cell the viewport shows, each box
 * placed by its own coordinate ([BoardViewport.tile]).
 *
 * The cells come from the board's **live** grid ([BoardGrid]) rather than from the geometry the
 * document was opened with: a stroke written into a cell the board had no chunk for grows the grid,
 * and the tile it landed in has to be drawn. Every page is still read only when its cell is on screen
 * ([ChunkTileManager.getOrCreateChunk]), so a `.mobix` opened page by page reads the handful of chunks
 * in view — the same trade the page stack makes with its page window (`docs/data-path.md`). Eviction
 * stays the tile manager's business ([ChunkTileManager.evictOffscreen], driven from the scroll).
 *
 * What the board deliberately does **not** draw is the page chrome: no page outline, no bookmark flag,
 * no page counter. There is no page 1 of N to number, and the chrome outside the canvas is
 * `make-the-chrome-tell-the-truth-for-a-document-with-no-pages`'s business.
 */
internal fun DrawingSurfaceView.paintBoard(canvas: Canvas, board: BoardCanvas) {
    drawBoardSheet(canvas, board)
    val range = viewport.visibleChunkRange(board.geometry.chunkSizePt)
    materialiseVisibleTiles(board, range)
    val drawn = board.tiles.visibleChunks(range)
    val indexes = HashSet<Int>(drawn.size)
    for (chunk in drawn) {
        // A tile is drawn under the document index it lives at, which is also the index its ink is
        // cached under ([InkCache]): the coordinate says where, the index says which page of the
        // document it is.
        val index = board.grid.indexOf(chunk.cx to chunk.cy)
        if (index < 0) continue
        indexes += index
        drawPageElements(canvas, boardTileBox(index, chunk.cx, chunk.cy, board))
    }
    // The ink cache is keyed by index, so a board has to retain its own set: without this the rasters
    // of tiles scrolled far away would sit in the cache for the rest of the session.
    inkCache.retain(indexes)
}

/**
 * Push the extent the viewport may scroll across, which is **not** the page stack's on a board.
 *
 * A board is not laid out as a page stack: the sheet is the geometry's, and how far the view may
 * scroll across a strip is the **bound**'s business ([BoardViewport.contentExtent]), not a fitting
 * page's. The stack is still built (the page-shaped chrome reads it), but its extent would strand the
 * far side of a strip wider than the screen, so a board pushes its own.
 */
internal fun DrawingSurfaceView.pushExtentToViewport() {
    val board = board
    val widthPx = width.toFloat()
    val heightPx = height.toFloat()
    if (board == null) {
        viewport.setBounds(widthPx, heightPx, layout.contentWidthPx, layout.totalHeightPx)
        return
    }
    val (contentWidthPx, contentHeightPx) = BoardViewport.contentExtent(
        geometry = board.geometry,
        zoom = zoom,
        viewWidthPx = widthPx,
        viewHeightPx = heightPx,
    )
    viewport.setBounds(widthPx, heightPx, contentWidthPx, contentHeightPx)
}

/**
 * Draw the sheet: the board's paper, ruled, laid over the area the viewport can see.
 *
 * It is drawn through [BackgroundRenderer] with a box of its own — not through the tiles — because the
 * ruling has to run **across** the board rather than restart inside every tile: a chunk is a storage
 * unit, not a sheet of paper, and a 1024 pt tile boundary ruled as a page edge would show a seam down
 * the middle of the writing.
 */
private fun DrawingSurfaceView.drawBoardSheet(canvas: Canvas, board: BoardCanvas) {
    val at = BoardViewport.sheet(
        geometry = board.geometry,
        zoom = zoom,
        scrollX = scrollX,
        scrollY = scrollY,
        viewWidthPx = width.toFloat(),
        viewHeightPx = height.toFloat(),
        spacingPt = board.rulingSpacingPt,
    )
    val box = boardSheetBox(at, board.paper, zoom)
    BackgroundRenderer.draw(
        canvas, box, scrollX, scrollY, null, emptyList(), width.toFloat(), height.toFloat(),
    )
}

/**
 * The box the sheet is drawn in: a page whose size is the **band** the sheet covers, positioned so its
 * own origin sits on the sheet's aligned corner.
 *
 * The page is rebuilt per frame (it is one small object, not one per tile) because its size is the
 * viewport's, and it carries the document's own paper — colour, style *and* config — so the ruling the
 * renderer rules from it is the paper's own ([BackgroundRuling]).
 */
private fun boardSheetBox(at: BoardPlacement, paper: Background.Solid?, zoom: Float): PageBox {
    val sheet = Page(
        width = at.pageWidthPt.coerceAtLeast(1.0),
        height = at.pageHeightPt.coerceAtLeast(1.0),
        background = paper ?: Background.Solid(android.graphics.Color.WHITE, "plain"),
        layers = emptyList(),
    )
    return PageBox(
        index = 0,
        topPx = at.topPx,
        leftPx = at.leftPx,
        widthPx = at.widthPx,
        heightPx = at.heightPx,
        scale = if (zoom > 0f) zoom else 1f,
        source = PageSource.of(listOf(sheet)),
    )
}

/**
 * The box one tile's **ink is drawn in**: placed at its cell's corner, sized to the page it resolves.
 *
 * The two are different numbers on purpose — the cell is where the content *sits* in document space
 * ([BoardViewport.tile]), the page is what the content *measures* — and the box's scale is the zoom so
 * the page's own pt map onto the board's.
 *
 * It is sized from the page rather than from the cell because of what reads it: the ink raster
 * ([InkCache]) is rendered at the page's own width and blitted into the box, so a box that claimed the
 * whole cell would stretch a strip's ink across it. A cell whose page this build does not have yet —
 * the instant before it is written — falls back to the cell's own size.
 */
internal fun DrawingSurfaceView.boardTileBox(index: Int, cx: Int, cy: Int, board: BoardCanvas): PageBox {
    val at = BoardViewport.tile(board.geometry, cx, cy, zoom)
    val size = if (index in 0 until pageSource.pageCount) pageSource.pageSize(index) else null
    return PageBox(
        index = index,
        topPx = at.topPx,
        leftPx = at.leftPx,
        widthPx = if (size != null && size.widthPt > 0.0) (size.widthPt * zoom).toFloat() else at.widthPx,
        heightPx = if (size != null && size.heightPt > 0.0) (size.heightPt * zoom).toFloat() else at.heightPx,
        scale = zoom,
        source = pageSource,
    )
}

/**
 * The box the **chrome** hangs off the document part at [index]: a board's tile ([boardTileBox]), or
 * the page box of the laid-out stack.
 *
 * It is the one seam between the two geometries — the page-shaped chrome (a search highlight, the jump
 * to a search hit, anything that needs a part's rectangle on screen) asks here rather than reaching into
 * the stacked layout, which on a board lays the chunks out in the file's own order and nowhere near
 * where they actually sit. Null when the part is not on this canvas at all (a tile the board does not
 * have, an index the stack does not reach).
 */
internal fun DrawingSurfaceView.chromeBoxFor(index: Int): PageBox? {
    val board = board ?: return layout.boxes.getOrNull(index)
    val cell = board.grid.coordAt(index) ?: return null
    return boardTileBox(index, cell.first, cell.second, board)
}

/**
 * The box a **gesture** converts through: the cell's own corner, its own scale, and the paper a cell
 * born here would have ([cellPage]).
 *
 * It is deliberately not the drawn box ([boardTileBox]): what is written is stored **cell-local**
 * (the box's origin is the cell's corner, `docs/infinite-canvas.md` → *Writing on a board*), so only
 * the placement is ever read here — and the page is synthesised rather than read because this box is
 * built on every move event, where a page read per frame would be the very cost the page window
 * exists to remove. A snap rules from the paper the board itself is drawn on, which is the sheet's
 * own ruling rather than whichever chunk happens to be under the pen.
 */
internal fun DrawingSurfaceView.boardInkBox(board: BoardCanvas, index: Int, cx: Int, cy: Int): PageBox {
    val at = BoardViewport.tile(board.geometry, cx, cy, zoom)
    return PageBox(
        index = index,
        topPx = at.topPx,
        leftPx = at.leftPx,
        widthPx = at.widthPx,
        heightPx = at.heightPx,
        scale = if (zoom > 0f) zoom else 1f,
        source = PageSource.of(listOf(cellPage(board.geometry, board.paper))),
    )
}

/**
 * Read the tiles the viewport shows — and only those.
 *
 * [ChunkTileManager.getOrCreateChunk]'s factory runs on a miss, so a chunk whose cell is off screen is
 * never built: the page behind it is not read, which is what keeps opening a two-hundred-chunk board
 * from opening two hundred entries (`MobixPagedReader`). A tile already resident is left alone.
 */
private fun DrawingSurfaceView.materialiseVisibleTiles(board: BoardCanvas, range: ChunkRange) {
    board.grid.forEach { index, (cx, cy) ->
        if (!range.contains(cx, cy)) return@forEach
        board.tiles.getOrCreateChunk(cx, cy) { x, y -> MobixChunk.fromPage(pageSource.page(index), x, y) }
    }
}

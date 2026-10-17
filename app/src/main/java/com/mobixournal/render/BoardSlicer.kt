package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.io.CanvasGeometry

/**
 * A board's way **out**: the single continuous expanded page its tiles add up to
 * (`docs/infinite-canvas.md`).
 *
 * Every chunk stores its elements **cell-local** (`x = 0` is the tile's left edge, `y = 0` is its top
 * edge), so each element is moved onto the board by its cell coordinate times the document's
 * `chunkSizePt` (plus any printed-sheet `originOffsetPt`), and assembled onto **one single page** whose
 * dimensions cover both the board's expanded extent and the furthest reach of its ink.
 */
internal object BoardSlicer {

    /** Fallback paper width when a board declares none (A4, the app's own default). */
    private const val DEFAULT_WIDTH_PT = 595.28

    /** Fallback paper height when a board declares none. */
    private const val DEFAULT_HEIGHT_PT = 841.89

    /**
     * [pages] — a board's chunk pages — assembled into **a single continuous [Page]** sized to the
     * expanded board and all ink written across its tiles.
     */
    fun slice(pages: List<Page>, geometry: CanvasGeometry): List<Page> {
        val board = spread(pages, geometry)
        val widthPt = geometry.widthPt.takeIf { it > 0.0 } ?: DEFAULT_WIDTH_PT
        val heightPt = geometry.heightPt.takeIf { it > 0.0 } ?: DEFAULT_HEIGHT_PT
        val background = pages.firstOrNull()?.background ?: Background.Solid(0xFFFFFFFF.toInt(), "plain")

        val finalWidth = when (geometry.mode) {
            CanvasMode.INFINITE_VERTICAL -> widthPt
            CanvasMode.INFINITE_HORIZONTAL, CanvasMode.INFINITE_2D, CanvasMode.PAGINATED ->
                maxOf(widthPt, board.right)
        }
        val finalHeight = when (geometry.mode) {
            CanvasMode.INFINITE_HORIZONTAL -> heightPt
            CanvasMode.INFINITE_VERTICAL, CanvasMode.INFINITE_2D, CanvasMode.PAGINATED ->
                maxOf(heightPt, board.bottom)
        }

        return listOf(
            Page(
                width = finalWidth,
                height = finalHeight,
                background = background,
                layers = listOf(Layer(board.elements)),
            ),
        )
    }

    /** A board's ink as one list of elements on board coordinates, with where the ink reaches. */
    private class Spread(
        val elements: List<Element>,
        val right: Double,
        val bottom: Double,
    )

    /**
     * Every part's elements, moved onto the board in spatial coordinate order.
     */
    private fun spread(pages: List<Page>, geometry: CanvasGeometry): Spread {
        val tile = geometry.chunkSizePt.takeIf { it > 0.0 } ?: DEFAULT_WIDTH_PT
        val offsetX = if (geometry.mode == CanvasMode.INFINITE_HORIZONTAL) geometry.originOffsetPt else 0.0
        val offsetY = if (geometry.mode == CanvasMode.INFINITE_VERTICAL) geometry.originOffsetPt else 0.0
        val ordered = pages.indices
            .mapNotNull { index -> geometry.chunkCoords.getOrNull(index)?.let { coord -> index to coord } }
            .sortedWith(
                if (geometry.mode == CanvasMode.INFINITE_HORIZONTAL) {
                    compareBy<Pair<Int, Pair<Int, Int>>> { it.second.first }.thenBy { it.second.second }
                } else {
                    compareBy<Pair<Int, Pair<Int, Int>>> { it.second.second }.thenBy { it.second.first }
                },
            )

        val minCx = ordered.minOfOrNull { it.second.first }?.coerceAtMost(0) ?: 0
        val minCy = ordered.minOfOrNull { it.second.second }?.coerceAtMost(0) ?: 0
        val shiftX = -minCx * tile
        val shiftY = -minCy * tile

        val elements = ArrayList<Element>()
        var right = 0.0
        var bottom = 0.0
        for ((index, coord) in ordered) {
            val page = pages[index]
            val dx = coord.first * tile + offsetX + shiftX
            val dy = coord.second * tile + offsetY + shiftY
            for (layer in page.layers) {
                for (element in layer.elements) {
                    val moved = CanvasAutoSlicer.translateElement(element, dx, dy)
                    val bounds = CanvasAutoSlicer.boundsOf(moved)
                    if (bounds.right > right) right = bounds.right
                    if (bounds.bottom > bottom) bottom = bounds.bottom
                    elements += moved
                }
            }
        }
        return Spread(elements, right, bottom)
    }
}

/**
 * [document] as the single-page document an **export** writes, or [document] itself when it is not a
 * board: the tiles assembled onto one continuous page ([BoardSlicer]), everything else untouched.
 */
internal fun sheetDocument(document: Document, geometry: CanvasGeometry?): Document {
    val board = geometry?.takeIf { it.isBoard } ?: return document
    return document.copy(pages = BoardSlicer.slice(document.pages, board))
}

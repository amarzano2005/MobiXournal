package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.io.CanvasGeometry

/**
 * A board's way **out**: the paginated document its tiles add up to
 * (`docs/infinite-canvas.md` → *Auto-pagination and export slicing*).
 *
 * A chunked `.mobix` is readable, writable and navigable, but printing, PDF export and desktop
 * Xournal++ all want discrete pages — so an export runs the board through
 * [CanvasAutoSlicer][com.mobixournal.render.CanvasAutoSlicer], which is the only thing that decides
 * **where the cuts go**: it searches a window around each target cut for a whitespace seam, and shifts
 * the seam past a stroke, a table or a plot rather than through it.
 *
 * What this file adds to it is the two things the slicer deliberately does not know:
 *
 * - **Where the ink is.** Every chunk stores its elements **cell-local** (the format's own reading: a
 *   chunk's page is what its content measures, and `x = 0` is the tile's left edge), so each element is
 *   moved onto the board first — its cell times the document's tile size — before anything is cut. This
 *   is also where the tile size has to be the *document's* (`canvas.json`'s `chunkSizePt`) rather than
 *   this build's default, the same reason the canvas reads its own grid apart
 *   (`docs/infinite-canvas.md` → *`canvas.json`*).
 * - **Which paper the sheets are.** A tile's own background is not the sheet's: the sheets are the
 *   board's paper — the one `canvas.json`'s `defaultPaper` records and the chunks carry — because a cut
 *   sheet is a whole page of the board rather than any one tile, and a board ruled across tiles
 *   (`BoardViewport.sheet`) must not come back with a seam at every tile edge.
 *
 * **A sheet carries one layer**, and a board's own layers do not survive the slice. That is the honest
 * reading rather than a shortcut: on a board a layer belongs to a **tile** — a chunk is an independent
 * page and its layer 1 has nothing to do with its neighbour's — so there is no document-wide layer
 * structure for a sheet to keep, and the alternatives (chunk A's layer 2 and chunk B's layer 2 merged
 * onto one sheet layer) would invent one. The ink itself is never dropped: every element of every chunk
 * lands on exactly one sheet.
 *
 * Nothing about the source document changes: the `.mobix` keeps its chunks, its geometry and its origin,
 * and the slices exist only in the file being written.
 */
internal object BoardSlicer {

    /** Fallback paper width when a board declares none (A4, the app's own default). */
    private const val DEFAULT_WIDTH_PT = 595.28

    /** Fallback paper height when a board declares none. */
    private const val DEFAULT_HEIGHT_PT = 841.89

    /**
     * [pages] — a board's chunk pages, in the document's own order — as the paginated pages an ordinary
     * reader opens: cut along the mode's own axis into sheets of the board's paper size.
     *
     * @param pages The board's parts, index `i` standing for cell `i` of [geometry].
     * @param geometry The board's geometry, whose `chunkSizePt` is how a chunk's cell-local ink is moved
     *   onto the board, whose `widthPt`/`heightPt` are the sheet, and whose origin offset is the margin
     *   the first sheet starts with.
     */
    fun slice(pages: List<Page>, geometry: CanvasGeometry): List<Page> {
        val board = spread(pages, geometry)
        val widthPt = geometry.widthPt.takeIf { it > 0.0 } ?: DEFAULT_WIDTH_PT
        val heightPt = geometry.heightPt.takeIf { it > 0.0 } ?: DEFAULT_HEIGHT_PT
        val background = pages.firstOrNull()?.background ?: Background.Solid(0xFFFFFFFF.toInt(), "plain")
        val offset = geometry.originOffsetPt

        return when (geometry.mode) {
            CanvasMode.INFINITE_HORIZONTAL -> CanvasAutoSlicer.sliceHorizontalStripToPages(
                elements = board.elements,
                targetPageWidthPt = widthPt,
                pageHeightPt = heightPt,
                totalWidthPt = board.right,
                startXPt = -offset,
                background = background,
            )
            CanvasMode.INFINITE_2D -> sliceBoard(board, widthPt, heightPt, offset, background)
            else -> CanvasAutoSlicer.sliceVerticalStripToPages(
                elements = board.elements,
                pageWidthPt = widthPt,
                targetPageHeightPt = heightPt,
                totalHeightPt = board.bottom,
                startYPt = -offset,
                background = background,
            )
        }
    }

    /**
     * A free board has no strip to run along, so its sheets are a **grid**: columns of the page's width
     * are cut first (the same seam search, on the x axis), and each column is then sliced into sheets of
     * the page's height. Two passes rather than one, because that is what keeps a stroke from being cut
     * twice over — the vertical pass sees only the band it belongs to.
     *
     * A column's own page height is the board's paper height, so the cells of a column sit in the same
     * place on their sheet as the column itself does: a 2D board is cut by coordinate, not by where the
     * ink happens to end.
     */
    private fun sliceBoard(
        board: Spread,
        widthPt: Double,
        heightPt: Double,
        offset: Double,
        background: Background,
    ): List<Page> {
        val columns = CanvasAutoSlicer.sliceHorizontalStripToPages(
            elements = board.elements,
            targetPageWidthPt = widthPt,
            pageHeightPt = heightPt,
            totalWidthPt = board.right,
            startXPt = -offset,
            background = background,
        )
        return columns.flatMap { column ->
            CanvasAutoSlicer.sliceVerticalStripToPages(
                elements = column.layers.flatMap { it.elements },
                pageWidthPt = column.width,
                targetPageHeightPt = heightPt,
                totalHeightPt = column.height,
                startYPt = 0.0,
                background = background,
            )
        }
    }

    /** A board's ink as one list of elements, on board coordinates, with where the last sheet ends. */
    private class Spread(
        val elements: List<Element>,
        /** How far right and down the ink reaches, in board pt — where the last sheet ends. */
        val right: Double,
        val bottom: Double,
    )

    /**
     * Every part's elements, moved onto the board.
     *
     * A cell's `(cx, cy)` times the document's tile size is its origin, and an element's stored
     * coordinate is relative to that: adding the origin is the whole conversion, on both axes.
     *
     * The reach is measured from the **translated** elements, so a cut is placed against where the ink
     * actually is: a chunk whose content is a single small stroke near its top-left still says its cell
     * is a tile further down the board, and the sheet that ink lands on is the one its coordinate names.
     */
    private fun spread(pages: List<Page>, geometry: CanvasGeometry): Spread {
        val tile = geometry.chunkSizePt.takeIf { it > 0.0 } ?: DEFAULT_WIDTH_PT
        val elements = ArrayList<Element>()
        var right = 0.0
        var bottom = 0.0
        pages.forEachIndexed { index, page ->
            val (cx, cy) = geometry.chunkCoords.getOrNull(index) ?: return@forEachIndexed
            val dx = cx * tile
            val dy = cy * tile
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
 * [document] as the paginated document an **export** writes, or [document] itself when it is not a
 * board: the tiles sliced into pages ([BoardSlicer]), everything else untouched.
 *
 * Only the exports go through here — the `.xopp` writer and the PDF exporter. A `.mobix` save keeps its
 * chunks: the file *is* the board, and slicing it into pages would be the rewrite
 * `docs/mobix-format.md` refuses a document over.
 */
internal fun sheetDocument(document: Document, geometry: CanvasGeometry?): Document {
    val board = geometry?.takeIf { it.isBoard } ?: return document
    return document.copy(pages = BoardSlicer.slice(document.pages, board))
}

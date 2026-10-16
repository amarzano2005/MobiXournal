package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.io.CanvasGeometry

/**
 * The board a canvas draws: the document's geometry, the tile manager that holds its chunks, and the
 * paper the sheet is ruled with — everything that only exists for a chunked `.mobix`, in one object so
 * a canvas in paginated mode carries **no** board state at all ([DrawingSurfaceView.board] is null and
 * every board path is skipped in one check).
 *
 * [tiles] is built here rather than by the caller: the grid it caches against is the document's own
 * `chunkSizePt`, not this build's default, so a file written with another tile size reads its own chunks
 * apart (`docs/infinite-canvas.md` → *`canvas.json`*).
 *
 * @property geometry The mode, paper dimensions, tile grid and occupied coordinates.
 * @property paperOf The document's paper, asked for **once, on the first frame that draws the sheet** —
 *   never at the open. A `.mobix` opened page by page materialises nothing at open, and asking for page
 *   0 while adopting the board would spend that promise on a colour and a style; the first frame that
 *   needs the sheet is drawing a page anyway.
 */
internal class BoardCanvas(
    val geometry: CanvasGeometry,
    private val paperOf: () -> Background.Solid?,
) {

    /** The chunk manager: which tiles are materialised, and the offscreen eviction ([ChunkTileManager]). */
    val tiles = ChunkTileManager(chunkSizePt = geometry.chunkSizePt)

    private var resolved = false
    private var resolvedPaper: Background.Solid? = null
    private var resolvedSpacingPt = 0.0

    /**
     * The paper the unbounded sheet is drawn on — the paper the board's pages were born with, carrying
     * the style *and* config the ruling is ruled from. Null when the first page names no solid
     * background, and the renderer then draws its plain white sheet.
     */
    val paper: Background.Solid?
        get() {
            resolve()
            return resolvedPaper
        }

    /**
     * The paper's ruling step in pt, resolved with [paper]: the sheet is drawn as a band whose origin
     * sits on this grid, which is what keeps the ruling aligned with document coordinates as the board
     * scrolls — a ruling restarted per tile would visibly jump at every chunk boundary.
     */
    val rulingSpacingPt: Double
        get() {
            resolve()
            return resolvedSpacingPt
        }

    private fun resolve() {
        if (resolved) return
        resolved = true
        val solid = paperOf()
        resolvedPaper = solid
        // No paper at all is an unruled sheet: the step is then only the band's own slack, which the
        // renderer uses as the box's page size, so any positive number does.
        resolvedSpacingPt = solid
            ?.let { BackgroundRulings.spacingPt(it.style, BackgroundRuling.parse(it.config)) }
            ?: BackgroundGrid.GRID_SPACING_PT
    }
}

/**
 * Adopt [geometry] as this canvas's board, or **drop the board** when it is null (a `.xopp`, a paginated
 * `.mobix`, a PDF or an image): the viewport's mode and the tile manager both follow from that one value.
 *
 * The board's paper is *not* read here — it is resolved on the first frame that draws the sheet, so
 * adopting a board keeps the open's own promise of touching no page (`docs/data-path.md` → *The page
 * window*).
 */
internal fun DrawingSurfaceView.setInfiniteCanvas(geometry: CanvasGeometry?) {
    val board = geometry?.takeIf { it.isBoard }
    if (board == null) {
        clearInfiniteCanvas()
        return
    }
    // The board's paper comes from its first page, where the format keeps it: the manifest's
    // `defaultPaper` is written from the board's first page (MobixDocument.of).
    this.board = BoardCanvas(geometry = board, paperOf = {
        (if (pageSource.pageCount > 0) pageSource.page(0) else null)?.background as? Background.Solid
    })
    viewport.canvasMode = board.mode
    viewport.clamp()
    requestRender()
}

/**
 * Put the canvas back in paginated mode: no tile manager, no sheet, the scroll clamps of a page stack.
 *
 * Called by every path that adopts a document which is not a board — a tab switch to a `.xopp`, a PDF or
 * an image import — so a board cannot survive on the canvas after the document it belonged to is gone.
 */
internal fun DrawingSurfaceView.clearInfiniteCanvas() {
    if (board == null && viewport.canvasMode == CanvasMode.PAGINATED) return
    board = null
    viewport.canvasMode = CanvasMode.PAGINATED
    viewport.clamp()
    requestRender()
}

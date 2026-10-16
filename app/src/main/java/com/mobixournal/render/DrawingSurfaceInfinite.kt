package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.io.CanvasGeometry

/**
 * The board a canvas draws: the document's geometry, the cells it occupies, the tile manager that holds
 * their chunks, the ink written into them, and the paper the sheet is ruled with — everything that only
 * exists for a chunked `.mobix`, in one object so a canvas in paginated mode carries **no** board state
 * at all ([DrawingSurfaceView.board] is null and every board path is skipped in one check).
 *
 * [tiles] is built here rather than by the caller: the grid it caches against is the document's own
 * `chunkSizePt`, not this build's default, so a file written with another tile size reads its own chunks
 * apart (`docs/infinite-canvas.md` → *`canvas.json`*).
 *
 * @property geometry The mode, paper dimensions, tile grid and **the coordinates the file declared**.
 * @property grid Those cells as the board's own, live list: a write into a cell the board had no chunk
 *   for appends to it, and an undo trims it ([BoardGrid]).
 * @property ink The write path — point → cell → chunk → document page ([BoardInk]).
 * @param paperOf The document's paper, asked for **once, on the first frame that draws the sheet** —
 *   never at the open. A `.mobix` opened page by page materialises nothing at open, and asking for page
 *   0 while adopting the board would spend that promise on a colour and a style; the first frame that
 *   needs the sheet is drawing a page anyway. A cell born later asks for the same paper, which is
 *   memoised by then.
 */
internal class BoardCanvas(
    val geometry: CanvasGeometry,
    private val paperOf: () -> Background.Solid?,
) {

    /** The cells the board occupies, in document order — the file's own, plus the ones written here. */
    val grid = BoardGrid(geometry.chunkCoords, geometry.mode)

    /** The chunk manager: which tiles are materialised, and the offscreen eviction ([ChunkTileManager]). */
    val tiles = ChunkTileManager(chunkSizePt = geometry.chunkSizePt)

    /**
     * The board's ink: which chunk a stroke belongs to, and the document page behind it. Built over the
     * view's own seams — the page window that holds an edit after the read cache has moved on, and the
     * document every edit lands in — so a chunk the viewport evicted comes back with its ink
     * (`docs/infinite-canvas.md` → *Writing on a board*).
     */
    lateinit var ink: BoardInk
        internal set

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
    val canvas = BoardCanvas(geometry = board, paperOf = {
        (if (pageSource.pageCount > 0) pageSource.page(0) else null)?.background as? Background.Solid
    })
    canvas.ink = boardInkOf(canvas)
    this.board = canvas
    viewport.canvasMode = board.mode
    viewport.clamp()
    requestRender()
}

/**
 * The board's write path, wired to this view: the page window (which keeps an edit after the read cache
 * has moved on), the document itself, and the append that gives a cell the board has no chunk for a page
 * of its own.
 *
 * The append is deliberately **silent** — it records no history of its own. A board's undo step is the
 * gesture's ([finishGesture] takes the snapshot taken at pointer-down), so a cell created by a stroke and
 * the stroke itself undo together, exactly as they arrived.
 */
private fun DrawingSurfaceView.boardInkOf(canvas: BoardCanvas): BoardInk = BoardInk(
    tiles = canvas.tiles,
    grid = canvas.grid,
    geometry = canvas.geometry,
    chunkSizePt = canvas.geometry.chunkSizePt,
    paper = { canvas.paper },
    readPage = { index -> pageWindow.pageOrNull(index) },
    writePage = { index, page -> windowEditPage(index) { page } != null },
    appendPage = { page -> appendChunkPage(page) },
)

/**
 * Append a page to the document for a cell the board had no chunk for: one page list behind the last
 * one, which is where the grid's own new cell sits too.
 *
 * `doc.copy(pages = …)` is the page-list form every page command uses ([PageCommands]); a chunked
 * document opened page by page pays one walk per *new cell*, which is a deliberate tap's worth of work
 * and not a per-stroke cost.
 */
private fun DrawingSurfaceView.appendChunkPage(page: Page): Boolean {
    doc = doc.copy(pages = doc.pages + page)
    return true
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

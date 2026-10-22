package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page

/**
 * A board's own reading of the view — what the page-shaped chrome asks for and cannot have on a
 * chunked document (`docs/chrome.md` → *A document with no pages*).
 *
 * [cell] is the **tile** the viewport is centred on, as its coordinate: not an index, because a board's
 * parts are ordered by the file and that order says nothing about where a view is. [tiles] is how many
 * tiles the document holds. It is reported **instead of** a page rather than beside one: a board has no
 * page 1 of N, and a surface showing both would be claiming pages it does not have.
 */
data class BoardReading(val cell: Pair<Int, Int>, val tiles: Int)

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
    geometry: CanvasGeometry,
    private val paperOf: () -> Background.Solid?,
) {

    /**
     * The board's geometry — the mode, the paper and the tile grid — as the document currently has it.
     *
     * Mutable for exactly one reason: the **fixed dimension** of a strip (its bound, the one axis that
     * does not run on) is a choice the user can make while the document is open
     * ([setBoardFixedExtent]), and the sheet must be drawn on the new one from the same frame. Nothing
     * else about a board's shape changes under it: the tile grid and the mode are the file's.
     */
    var geometry: CanvasGeometry = geometry
        internal set

    /**
     * The view extent the opening fit was last computed for, or null while it has not run.
     *
     * The extent is what the fit is **of** — "the sheet, on the screen we have" — so a canvas that
     * changes size (the tab strip arriving a frame after the document, a rotated window, the split
     * toggle) re-fits for the screen it now has. This is the whole fix for a board that opened a few
     * percent too large: its first frame was a taller canvas than the one it ended up on, and a fit
     * computed once, against a stale extent, is a zoom nobody can explain.
     */
    internal var fittedTo: Pair<Int, Int>? = null

    /**
     * True while the **fit** still owns the zoom: a resize re-fits, and the user's own zoom
     * ([DrawingSurfaceView.setZoom], [DrawingSurfaceView.zoomAbout]) takes that ownership away — the
     * factor they chose is a decision about the document, not a measurement of the pane, and a resize
     * must not undo it. **Reset zoom** hands it back.
     */
    internal var autoFit: Boolean = true

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
    fitBoardIfPending()
    viewport.clamp()
    onCanvasModeChanged?.invoke(canvasMode)
    // The bound comes from the *board*, which only exists from here: reporting it at the bind alone
    // (before any document is adopted) would tell the chrome there is none and the page-format dialog
    // would offer no fixed width for a strip.
    notifyCanvasExtent()
    reportCurrentPage()
    requestRender()
}

/**
 * The zoom factor a **board** opens at, from the board's geometry and the canvas's own extent: the
 * **least** magnification at which a strip's bound reaches both edges of the view.
 *
 * A strip's bound is a paper edge, and paper that ends before the screen does leaves a band of desk
 * down the side: "infinite" canvas with a visible empty margin, and — worse — a size the user typed
 * that then reads as *ignored*. So the bound **covers** the view (`viewport / bound`), and only the
 * part needed to do it: a 100 mm strip on a 1248 px canvas opens at 440%, not at 100% with 77% of the
 * screen in grey. The zoom is never below 100% either — a bound **larger** than the view already
 * covers it, so it is shown at its own size and scrolled across rather than shrunk to fit (the written
 * millimetres stay legible, and `BoardViewport.contentExtent` already let the view pan the whole bound).
 *
 * A page list is not a board and answers 100%; so does [CanvasMode.INFINITE_2D], whose
 * `widthPt`/`heightPt` are the size an **export** would slice — grown by [expandBoardOnScroll] as the
 * view travels — so fitting to them made the zoom depend on how far the board had been scrolled when it
 * was last saved.
 *
 * Pure (no view, no Android types) so the arithmetic is unit-tested on the JVM.
 */
internal fun boardFitZoom(
    mode: CanvasMode,
    widthPt: Double,
    heightPt: Double,
    viewWidthPx: Int,
    viewHeightPx: Int,
): Float {
    if (viewWidthPx <= 0 || viewHeightPx <= 0) return 1f
    val bound = when (mode) {
        CanvasMode.INFINITE_VERTICAL -> widthPt to viewWidthPx
        CanvasMode.INFINITE_HORIZONTAL -> heightPt to viewHeightPx
        CanvasMode.PAGINATED, CanvasMode.INFINITE_2D -> return 1f
    }
    val (extentPt, viewPx) = bound
    if (extentPt <= 0.0) return 1f
    val cover = viewPx / extentPt
    // Up to the bound's own size when it is the smaller of the two, and never past the zoom range.
    return cover.coerceAtLeast(1.0).toFloat().coerceIn(1f, ViewportState.MAX_ZOOM)
}

/** The zoom factor at which this canvas's board opens ([boardFitZoom]), read from its live view. */
internal fun DrawingSurfaceView.fitBoardZoom(): Float {
    val board = board ?: return 1f
    val g = board.geometry
    return boardFitZoom(g.mode, g.widthPt, g.heightPt, width, height)
}

/**
 * Fit the board to the canvas it is on, and **keep** it fitted while the canvas keeps its word: called
 * from every layout pass, it re-fits only when the view's extent moved (see [BoardCanvas.fittedTo]) or
 * the fit has never run, and only while the fit still owns the zoom ([BoardCanvas.autoFit]).
 *
 * The opening fit also seeds the running axis, so the sheet spans the screen from the first frame
 * instead of starting at the default paper's width.
 */
internal fun DrawingSurfaceView.fitBoardIfPending() {
    val board = board ?: return
    if (width <= 0 || height <= 0) return
    if (!board.autoFit) return
    val extent = width to height
    if (board.fittedTo == extent) return
    val opening = board.fittedTo == null
    board.fittedTo = extent
    val viewW = width.toDouble()
    val viewH = height.toDouble()
    if ((board.geometry.mode == CanvasMode.INFINITE_VERTICAL || board.geometry.mode == CanvasMode.INFINITE_2D) && board.geometry.widthPt <= 0.0) {
        board.geometry = board.geometry.copy(widthPt = viewW)
    }
    if ((board.geometry.mode == CanvasMode.INFINITE_HORIZONTAL || board.geometry.mode == CanvasMode.INFINITE_2D) && board.geometry.heightPt <= 0.0) {
        board.geometry = board.geometry.copy(heightPt = viewH)
    }
    // A re-fit keeps its place: `zoomTo` holds the point at the viewport centre, so a resize does not
    // throw the writing out of view the way "back to (0, 0)" would. Only the opening starts at the origin.
    if (opening) {
        scrollX = 0f
        scrollY = 0f
    }
    viewport.zoomTo(fitBoardZoom()) {}
    expandBoardOnScroll()
    pushExtentToViewport()
    viewport.clamp()
    notifyCanvasExtent()
    onZoomChanged?.invoke(zoom)
}

/**
 * Progressively expand the board's dimensions along its infinite direction(s) whenever the user scrolls,
 * so the resulting single-page output grows to cover the scrolled area.
 */
internal fun DrawingSurfaceView.expandBoardOnScroll() {
    val board = board ?: return
    if (width <= 0 || height <= 0) return
    val scale = (if (zoom > 0f) zoom else 1f).toDouble()
    val reachedWidthPt = ((scrollX + width).coerceAtLeast(width.toFloat())) / scale
    val reachedHeightPt = ((scrollY + height).coerceAtLeast(height.toFloat())) / scale
    val g = board.geometry
    val next = when (g.mode) {
        CanvasMode.INFINITE_VERTICAL -> g.copy(heightPt = maxOf(g.heightPt, reachedHeightPt))
        CanvasMode.INFINITE_HORIZONTAL -> g.copy(widthPt = maxOf(g.widthPt, reachedWidthPt))
        CanvasMode.INFINITE_2D -> g.copy(
            widthPt = maxOf(g.widthPt, reachedWidthPt),
            heightPt = maxOf(g.heightPt, reachedHeightPt),
        )
        CanvasMode.PAGINATED -> g
    }
    if (next != g) board.geometry = next
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
 * The **fixed dimension** of the board's non-infinite axis, in pt — the strip's width or height — or
 * null when the canvas is not a board at all.
 */
internal fun DrawingSurfaceView.boardFixedExtentPt(): Double? {
    val g = board?.geometry ?: return null
    return g.fixedExtentPt ?: if (g.mode == CanvasMode.INFINITE_2D) g.widthPt else null
}

/**
 * Set the bound on the board's primary axis: an `INFINITE_VERTICAL` or `INFINITE_2D` board becomes
 * [pt] pt wide, and an `INFINITE_HORIZONTAL` strip becomes [pt] pt tall, re-fitting the zoom so the
 * chosen dimension covers the viewport.
 */
internal fun DrawingSurfaceView.setBoardFixedExtent(pt: Double) {
    val board = board ?: return
    if (pt <= 0.0) return
    val bound = pt.coerceIn(DrawingSurfaceDefaults.PAGE_SIZE_MIN_PT, DrawingSurfaceDefaults.PAGE_SIZE_MAX_PT)
    val lockedX = board.geometry.mode != CanvasMode.INFINITE_HORIZONTAL
    board.geometry = if (board.geometry.mode == CanvasMode.INFINITE_2D) {
        board.geometry.copy(widthPt = bound)
    } else {
        board.geometry.withFixedExtent(bound)
    }
    board.ink = boardInkOf(board)
    for (index in 0 until pageWindow.pageCount) {
        windowEditPage(index) { page ->
            if (lockedX) page.copy(width = bound) else page.copy(height = bound)
        }
    }
    if (width > 0 && height > 0) {
        // The new bound is a size the user just asked for: show all of it again, even if they had
        // taken the zoom over themselves before.
        board.autoFit = true
        board.fittedTo = width to height
        viewport.zoomTo(fitBoardZoom()) {}
        expandBoardOnScroll()
        onZoomChanged?.invoke(zoom)
    }
    relayout()
    notifyCanvasExtent()
    requestRender()
}

/** Tell the chrome the bound this board has now ([boardFixedExtentPt]), or that it has none. */
private fun DrawingSurfaceView.notifyCanvasExtent() =
    onCanvasExtentChanged?.invoke(boardFixedExtentPt())

/**
 * Put the canvas back in paginated mode: no tile manager, no sheet, the scroll clamps of a page stack.
 *
 * Called by every path that adopts a document which is not a board — a tab switch to a `.xopp`, a PDF or
 * an image import — so a board cannot survive on the canvas after the document it belonged to is gone.
 */
internal fun DrawingSurfaceView.clearInfiniteCanvas() {
    if (board == null && viewport.canvasMode == CanvasMode.PAGINATED) {
        // Nothing to clear, but the chrome still has to be told this canvas locks nothing: it may have
        // been showing a strip a moment ago.
        notifyCanvasExtent()
        return
    }
    board = null
    viewport.canvasMode = CanvasMode.PAGINATED
    viewport.clamp()
    onCanvasModeChanged?.invoke(CanvasMode.PAGINATED)
    notifyCanvasExtent()
    reportCurrentPage()
    requestRender()
}

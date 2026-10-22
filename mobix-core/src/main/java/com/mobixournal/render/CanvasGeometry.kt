package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode

/**
 * Everything a **canvas** needs to lay out a chunked `.mobix` as an unbounded board, and nothing else:
 * the mode, the two paper dimensions, the tile grid the document's chunk names were computed against,
 * and those coordinates in the document's own order.
 *
 * It exists because the geometry is *not* derivable from the pages. A page says how big its content
 * is; it does not say which cell of the board that content sits in, and a chunk's place in the
 * manifest is not its place on the board (a 2D board goes negative, and a reordered chunk keeps its
 * coordinate). So the one layer that read the file — `canvas.json` plus the manifest's entry names —
 * hands the answer over, and the renderer never has to guess ([DocumentIo.canvasGeometryOf]). It is
 * platform-free because two callers need the same answer: the Android canvas, and the export that cuts
 * a board into sheets ([BoardSlicer]).
 *
 * `null` is how this is expressed for everything that is not a board: a `.xopp`, a paginated `.mobix`,
 * a PDF or an image. There is no "paginated geometry" to carry, because a paginated document is laid
 * out from its pages ([PageStacker][com.mobixournal.render.PageStacker]) and needs nothing here.
 *
 * @property mode The board's geometry mode; never [CanvasMode.PAGINATED] (that is the null case).
 * @property widthPt The board's own **paper width**, in pt: the locked dimension of
 *   [CanvasMode.INFINITE_VERTICAL] (a strip that wide running down), the sheet's width in
 *   [CanvasMode.INFINITE_2D], and the width an export cuts a horizontal strip's sheets at. `0.0` when
 *   the file declares none, and the renderer then falls back to the default paper.
 * @property heightPt The board's **paper height**, the locked dimension of
 *   [CanvasMode.INFINITE_HORIZONTAL] and a vertical strip's cut height, read the same way.
 * @property chunkSizePt The spatial tile size, in pt, the document's `chunk_<cx>_<cy>` names were
 *   computed against (`docs/infinite-canvas.md` → *`canvas.json`*).
 * @property chunkCoords The occupied cells, **in the document's order** — index `i` here is page `i`
 *   of the document (`docs/infinite-canvas.md` → *A chunk is a part*), which is what lets the renderer
 *   materialise the tile under the pen without opening the board.
 * @property originOffsetPt Where document `(0, 0)` sits on the **printed sheet**, in pt — the vertical
 *   (or, for a horizontal strip, horizontal) margin page 1 begins with. It is the one half of
 *   `canvas.json`'s `origin` an export can use: the other, its 1-based `pageNo`, numbers the sheet a
 *   document lands on inside a bigger job, and a standalone export has nothing before its page 1 to
 *   number against (`docs/infinite-canvas.md` → *Auto-pagination and export slicing*).
 */
data class CanvasGeometry(
    val mode: CanvasMode,
    val widthPt: Double,
    val heightPt: Double,
    val chunkSizePt: Double,
    val chunkCoords: List<Pair<Int, Int>>,
    val originOffsetPt: Double = 0.0,
) {
    /** True for the three modes whose content is spatial tiles rather than a page list. */
    val isBoard: Boolean get() = mode != CanvasMode.PAGINATED

    /**
     * The mode's **non-infinite** axis, in pt, or null when both axes run on forever.
     *
     * A strip locks one dimension: [CanvasMode.INFINITE_VERTICAL] fixes the width (the strip runs
     * down) and [CanvasMode.INFINITE_HORIZONTAL] fixes the height. That fixed dimension is the board's
     * *bound* — the one edge you cannot scroll past, because `ViewportState` clamps the locked axis and
     * the sheet is drawn exactly that wide ([BoardViewport.sheet]). [CanvasMode.INFINITE_2D] locks
     * nothing, and a paginated document is not a board at all.
     */
    val fixedExtentPt: Double?
        get() = when (mode) {
            CanvasMode.INFINITE_VERTICAL -> widthPt
            CanvasMode.INFINITE_HORIZONTAL -> heightPt
            CanvasMode.PAGINATED, CanvasMode.INFINITE_2D -> null
        }

    /** True when [fixedExtentPt] is the **width** (a strip running down) rather than the height. */
    val fixedExtentIsWidth: Boolean get() = mode == CanvasMode.INFINITE_VERTICAL

    /**
     * This geometry with its fixed dimension set to [pt] — the page-format dialog's *fixed width / fixed
     * height*, which is how the bound on the non-infinite axis is chosen a priori.
     *
     * Only the locked axis moves: an [CanvasMode.INFINITE_2D] board has none, so it is returned
     * unchanged rather than having one invented for it.
     */
    fun withFixedExtent(pt: Double): CanvasGeometry = when (mode) {
        CanvasMode.INFINITE_VERTICAL -> copy(widthPt = pt)
        CanvasMode.INFINITE_HORIZONTAL -> copy(heightPt = pt)
        CanvasMode.PAGINATED, CanvasMode.INFINITE_2D -> this
    }
}

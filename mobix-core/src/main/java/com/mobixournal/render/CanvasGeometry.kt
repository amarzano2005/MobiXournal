package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode

/**
 * Everything a chunked `.mobix` declares about its geometry, and nothing else: the mode, the two paper
 * dimensions, the tile grid the document's chunk names were computed against, and those coordinates in
 * the document's own order.
 *
 * It exists because the geometry is *not* derivable from the pages. A page says how big its content
 * is; it does not say which cell of the board that content sits in, and a chunk's place in the
 * manifest is not its place on the board (a 2D board goes negative, and a reordered chunk keeps its
 * coordinate). So the one layer that read the file — `canvas.json` plus the manifest's entry names —
 * hands the answer over, and the reader never has to guess ([DocumentIo.canvasGeometryOf]). It is
 * platform-free because two callers need the same answer: the save that writes the chunks back at their
 * own coordinates, and the export that assembles them onto one sheet ([BoardSlicer]).
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
 *   of the document (`docs/chunked-documents.md` → *A chunked document opens, is edited and saves as
 *   chunks*), which is what lets the write land each chunk where the file had it.
 * @property originOffsetPt Where document `(0, 0)` sits on the **printed sheet**, in pt — the vertical
 *   (or, for a horizontal strip, horizontal) margin page 1 begins with. It is the one half of
 *   `canvas.json`'s `origin` an export can use: the other, its 1-based `pageNo`, numbers the sheet a
 *   document lands on inside a bigger job, and a standalone export has nothing before its page 1 to
 *   number against (`docs/infinite-canvas.md` → *Export slicing*).
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
}

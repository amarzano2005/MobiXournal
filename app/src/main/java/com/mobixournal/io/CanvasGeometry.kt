package com.mobixournal.io

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.stripCoords
import com.mobixournal.format.model.Document

/**
 * Everything a **canvas** needs to lay out a chunked `.mobix` as an unbounded board, and nothing else:
 * the mode, the two paper dimensions, the tile grid the document's chunk names were computed against,
 * and those coordinates in the document's own order.
 *
 * It exists because the geometry is *not* derivable from the pages. A page says how big its content
 * is; it does not say which cell of the board that content sits in, and a chunk's place in the
 * manifest is not its place on the board (a 2D board goes negative, and a reordered chunk keeps its
 * coordinate). So the one layer that read the file — `canvas.json` plus the manifest's entry names —
 * hands the answer over, and the renderer never has to guess ([DocumentIo.canvasGeometryOf]).
 *
 * `null` is how this is expressed for everything that is not a board: a `.xopp`, a paginated `.mobix`,
 * a PDF or an image. There is no "paginated geometry" to carry, because a paginated document is laid
 * out from its pages ([PageStacker][com.mobixournal.render.PageStacker]) and needs nothing here.
 *
 * @property mode The board's geometry mode; never [CanvasMode.PAGINATED] (that is the null case).
 * @property widthPt The **locked** width in [CanvasMode.INFINITE_VERTICAL], the paper height in
 *   [CanvasMode.INFINITE_HORIZONTAL], and the printed sheet's own size in [CanvasMode.INFINITE_2D];
 *   `0.0` when the file declares none, and the renderer then falls back to the default paper.
 * @property heightPt The other locked dimension, with the same reading.
 * @property chunkSizePt The spatial tile size, in pt, the document's `chunk_<cx>_<cy>` names were
 *   computed against (`docs/infinite-canvas.md` → *`canvas.json`*).
 * @property chunkCoords The occupied cells, **in the document's order** — index `i` here is page `i`
 *   of the document (`docs/infinite-canvas.md` → *A chunk is a part*), which is what lets the renderer
 *   materialise the tile under the pen without opening the board.
 */
internal data class CanvasGeometry(
    val mode: CanvasMode,
    val widthPt: Double,
    val heightPt: Double,
    val chunkSizePt: Double,
    val chunkCoords: List<Pair<Int, Int>>,
) {
    /** True for the three modes whose content is spatial tiles rather than a page list. */
    val isBoard: Boolean get() = mode != CanvasMode.PAGINATED
}

/**
 * The **board** [document] is laid out as, or null when it is not one: its mode (a file this session
 * opened wins over [bornAs], exactly as the save's does), the `canvas.json` it was read with, and the
 * occupied chunk coordinates in the document's own order.
 *
 * A document this session never opened is not a reason to answer "no board": one created here is born
 * on a geometry (`AppSettings.newDocumentCanvas`) and has never been near a file, so its geometry is
 * derived exactly as the writer derives it — the strip its mode runs in, by page index
 * (`MobixDocument.of`'s `stripCoords`) — and the canvas then reads the pages through its own source. A
 * `.xopp`, and every paginated document, has no board at all and answers null.
 *
 * It lives here, beside the value it answers with, rather than in `DocumentIo` itself: the I/O class was
 * already at its size budget, and a reader of this file should find the whole of what a board *is* — the
 * value's meaning and its one source — in one place.
 */
/**
 * Hand the session the shape [document] was **edited into**, before the write that has to keep it: the
 * cells the board holds now, in the document's own order.
 *
 * A board's coordinates are a fact of the **board** — the chunk names are computed from them, and
 * neither the pages nor the mode can say where a cell sits — so a write that read only what the opened
 * file declared would drop every cell created since and move the ink written into it
 * (`docs/infinite-canvas.md` → *A chunked document opens, is edited and saves as chunks*). The canvas is
 * the layer that owns the live grid ([BoardGrid]), so the save asks it for the shape rather than
 * deriving one ([MobixOpenSessions.rememberGrid]).
 *
 * A paginated document, a `.xopp`, a PDF or an image has no board and is left alone: there is nothing
 * to record, and nothing that could go stale.
 */
internal fun DocumentIo.rememberBoardShape(document: Document, geometry: CanvasGeometry?) {
    val board = geometry?.takeIf { it.isBoard } ?: return
    openSessions.rememberGrid(document, board.chunkCoords)
}

internal fun DocumentIo.canvasGeometryOf(
    document: Document,
    bornAs: CanvasMode = CanvasMode.PAGINATED,
): CanvasGeometry? {
    val mode = openSessions.canvasModeOf(document, bornAs)
    if (mode == CanvasMode.PAGINATED) return null
    val canvas = openSessions.canvasOf(document)
    // The dimensions the file declares, or the first chunk's own page when it declares none: a
    // brand-new board has no file to ask, and the page it was born with *is* its sheet.
    val first = document.pages.firstOrNull()
    return CanvasGeometry(
        mode = mode,
        widthPt = canvas?.widthPt?.takeIf { it > 0.0 } ?: first?.width ?: 0.0,
        heightPt = canvas?.heightPt?.takeIf { it > 0.0 } ?: first?.height ?: 0.0,
        chunkSizePt = canvas?.chunkSizePt ?: MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
        chunkCoords = openSessions.chunkCoordsOf(document)
            ?: List(document.pages.size) { stripCoords(it, mode) },
    )
}

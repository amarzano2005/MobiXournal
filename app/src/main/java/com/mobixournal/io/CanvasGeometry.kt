package com.mobixournal.io

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.stripCoords
import com.mobixournal.format.model.Document
import com.mobixournal.render.CanvasGeometry

/*
 * What a board **is** is `render/CanvasGeometry.kt` in `mobix-core`: the value is platform-free, and the
 * export slices a board with it ([com.mobixournal.render.sheetDocument]). What is left here is the one
 * thing that cannot leave the app — the **session** (`MobixOpenSessions`) that remembers which board an
 * open document declared, and hands it back to the canvas and to the save.
 */

/** Remember the mode [document] was born in, so a save keeps writing the container it started as. */
internal fun DocumentIo.rememberCanvasMode(document: Document, mode: CanvasMode) {
    openSessions.rememberMode(document, mode)
}

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
    openSessions.rememberExtent(document, board.widthPt, board.heightPt)
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
 */
internal fun DocumentIo.canvasGeometryOf(
    document: Document,
    bornAs: CanvasMode = CanvasMode.PAGINATED,
): CanvasGeometry? {
    val mode = openSessions.canvasModeOf(document, bornAs)
    if (mode == CanvasMode.PAGINATED) return null
    val canvas = openSessions.canvasOf(document)
    val extent = openSessions.extentOf(document)
    // The dimensions the session expanded to, or what the file declares, or the first chunk's own page:
    // a brand-new board has no file to ask, and the page it was born with *is* its sheet.
    val first = document.pages.firstOrNull()
    return CanvasGeometry(
        mode = mode,
        widthPt = extent?.first?.takeIf { it > 0.0 } ?: canvas?.widthPt?.takeIf { it > 0.0 } ?: first?.width ?: 0.0,
        heightPt = extent?.second?.takeIf { it > 0.0 } ?: canvas?.heightPt?.takeIf { it > 0.0 } ?: first?.height ?: 0.0,
        chunkSizePt = canvas?.chunkSizePt ?: MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
        chunkCoords = openSessions.chunkCoordsOf(document)
            ?: List(document.pages.size) { stripCoords(it, mode) },
        originOffsetPt = canvas?.origin?.offsetPt ?: 0.0,
    )
}

package com.mobixournal.io

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.stripCoords
import com.mobixournal.format.model.Document
import com.mobixournal.render.CanvasGeometry

/*
 * What a board **is** is `render/CanvasGeometry.kt` in `mobix-core`: the value is platform-free, and the
 * export slices a board with it ([com.mobixournal.render.sheetDocument]). What is left here is the one
 * thing that cannot leave the app — the **session** (`MobixOpenSessions`) that remembers what an
 * opened file declared, and hands it back to the save.
 */

/**
 * The shape a **chunked** `.mobix` declares, or null for every document that is not one: its mode, the
 * `canvas.json` it was read with, and the occupied chunk coordinates in the document's own order.
 *
 * The export uses it to assemble the cells onto one sheet instead of cutting each chunk into a page of
 * its own. A `.xopp`, a paginated `.mobix`, or a document this session never opened has no board and
 * answers null.
 */
internal fun DocumentIo.canvasGeometryOf(document: Document): CanvasGeometry? {
    val mode = openSessions.canvasModeOf(document) ?: return null
    val canvas = openSessions.canvasOf(document)
    // The dimensions the file declares, or the first chunk's own page: a chunked document read in this
    // session always has one or the other.
    val first = document.pages.firstOrNull()
    return CanvasGeometry(
        mode = mode,
        widthPt = canvas?.widthPt?.takeIf { it > 0.0 } ?: first?.width ?: 0.0,
        heightPt = canvas?.heightPt?.takeIf { it > 0.0 } ?: first?.height ?: 0.0,
        chunkSizePt = canvas?.chunkSizePt ?: MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
        chunkCoords = openSessions.chunkCoordsOf(document)
            ?: List(document.pages.size) { stripCoords(it, mode) },
        originOffsetPt = canvas?.origin?.offsetPt ?: 0.0,
    )
}

/** The mode the opened file declared, or [CanvasMode.PAGINATED] for a document with no board. */
internal fun DocumentIo.openedCanvasMode(document: Document): CanvasMode =
    openSessions.canvasModeOf(document) ?: CanvasMode.PAGINATED

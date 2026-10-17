package com.mobixournal.io

import android.net.Uri
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.model.Document
import java.io.File

/**
 * What this session knows about the `.mobix` files it opened — split out of [DocumentIo] because it is
 * a **record of opens**, not document I/O: what each document was read from, where its parts sit, how
 * shaped it is, and which page reader is still backing it.
 *
 * Everything here is keyed by the document's own id ([Document.documentId]), because that is what a
 * save of it carries back: a document the session no longer holds, or one that never was a `.mobix`,
 * simply has no entry, and the save falls back to writing the whole archive.
 *
 * The lifetime is a session's, and one sweep releases it: [prune] is called with the ids every open tab
 * and live canvas still carries ([DocumentIo.prunePagedOpens]), so a document being shown keeps its
 * reader — a paged document reads its pages *through* it — while one nothing points at is let go.
 */
internal class MobixOpenSessions {

    /** Where each open `.mobix` was read from, by document id. */
    private val sources = HashMap<String, OpenMobix>()

    /**
     * An open `.mobix`'s own bytes: the staged copy the session keeps, and the URI it came in through.
     * A save landing back on that URI repacks over these bytes instead of writing the archive again.
     */
    class OpenMobix(val staged: File, val uri: String?)

    /**
     * The page readers of the `.mobix` documents opened page by page, by the document they back. A
     * document can be held by more than one surface at once, so this is a list.
     */
    private val readers = LinkedHashMap<String, MutableList<MobixPagedReader>>()

    /**
     * The canvas mode each open `.mobix` declares, by document id: what its next save must write back
     * ([canvasModeOf]). A chunked document saved as paginated is the rewrite `docs/mobix-format.md`
     * refuses a document over, and the mode is a fact of the opened *file* rather than of the page
     * model — which is why it is remembered here and not put on `Document`.
     */
    private val canvasModes = HashMap<String, CanvasMode>()

    /**
     * Where each open **chunked** document's parts sit, by document id, in manifest order: the source
     * manifest's own chunk coordinates. A chunk is named after them (`chunks/chunk_<cx>_<cy>.json`), so
     * a write that derived them from the page index would move every chunk of a 2D board to the first
     * column; keeping the grid is what lets an edited chunk be written back exactly where it was
     * ([chunkCoordsOf]).
     */
    private val chunkGrids = HashMap<String, List<Pair<Int, Int>>>()

    /**
     * The board geometry each open **chunked** document declares, by document id: `canvas.json` as the
     * file wrote it — the locked dimension, the tile size its chunk names were computed against, the
     * printed-sheet origin and whatever fields this build does not model.
     *
     * None of that is derivable from the page model, so a write that rebuilt it from the pages would
     * silently change a board's geometry (and drop the origin and the unknown fields with it). It rides
     * with [canvasModeOf] and is written back as it was read ([MobixDocument.of] `canvas`).
     */
    private val canvases = HashMap<String, MobixCanvasFile>()

    /** Expanded board dimensions `(widthPt, heightPt)` grown by scrolling in the editor, by document id. */
    private val boardExtents = HashMap<String, Pair<Double, Double>>()

    /** Record that [documentId] was read from [staged], through [uri] when the caller knows it. */
    fun remember(documentId: String, staged: File, uri: String?) {
        sources[documentId] = OpenMobix(staged, uri)
    }

    /** The staged copy [document] was read from, or null when this session does not hold one. */
    fun stagedOf(document: Document): File? =
        document.documentId?.let { sources[it] }?.takeIf { it.staged.isFile }?.staged

    /**
     * The archive a save of [document] to [target] repacks over, or null when there is none: a `.mobix`
     * written back to the very file it was opened from is the one case that can copy anything, so
     * anything else — a new document, a first save, a Save As — writes the whole archive.
     *
     * Keyed by the *incoming* document's id: a document that never was a `.mobix` has none, and one
     * that [MobixDocument.of] was about to mint has no source either. The target has to match the URI
     * the document came in through **as a string**, because that is what a session remembers it as and
     * what SAF hands back for the same file.
     */
    fun sourceFor(document: Document, target: Uri?): OpenMobix? =
        document.documentId?.let { sources[it] }
            ?.takeIf { it.staged.isFile && it.uri != null && it.uri == target?.toString() }

    /** Keep [reader] alive for as long as the session holds [documentId] — it is what its pages read. */
    fun keep(documentId: String, reader: MobixPagedReader) {
        readers.getOrPut(documentId) { mutableListOf() } += reader
    }

    /**
     * Remember how [mobixDoc] was shaped, under its own id: its canvas mode, and the grid a chunked
     * document's parts sit at.
     *
     * Both are read from what the open already has — the manifest's mode, and the chunk coordinates its
     * entry **names** carry, which is why this costs no page read on a two-hundred-page notebook. A
     * chunk entry whose name cannot be parsed leaves the whole grid unrecorded rather than a partial one
     * being trusted: a wrong grid moves content, and the derived strip the writer falls back to at least
     * keeps every part readable.
     */
    fun shape(mobixDoc: MobixDocument) {
        val id = mobixDoc.manifest.documentId
        canvasModes[id] = mobixDoc.manifest.canvasMode
        if (mobixDoc.manifest.canvasMode == CanvasMode.PAGINATED) return
        canvases[id] = mobixDoc.canvas
        val chunks = mobixDoc.manifest.chunks
        val grid = chunks.mapNotNull { MobixFormat.parseChunkEntry(it.entry) }
        if (grid.size == chunks.size) chunkGrids[id] = grid
    }

    /** Update the remembered canvas mode of [document] when the user switches mode in the Pages menu. */
    fun rememberMode(document: Document, mode: CanvasMode) {
        val id = document.documentId ?: return
        canvasModes[id] = mode
        boardExtents.remove(id)
        canvases.remove(id)
        if (mode == CanvasMode.PAGINATED) {
            chunkGrids.remove(id)
        }
    }

    /**
     * Record where [document]'s parts sit **now**, by the document's own id, before a write that has to
     * keep them.
     */
    fun rememberGrid(document: Document, coords: List<Pair<Int, Int>>) {
        val id = document.documentId ?: return
        chunkGrids[id] = coords
    }

    /** Record the expanded `(widthPt, heightPt)` of [document]'s board before saving/exporting. */
    fun rememberExtent(document: Document, widthPt: Double, heightPt: Double) {
        val id = document.documentId ?: return
        boardExtents[id] = widthPt to heightPt
    }

    /** The expanded `(widthPt, heightPt)` recorded for [document], or null if none was recorded. */
    fun extentOf(document: Document): Pair<Double, Double>? =
        document.documentId?.let { boardExtents[it] }

    /**
     * The canvas mode a write of [document] must claim: the one the file it was opened from declares,
     * and [bornAs] for a document this session never opened — a `.xopp`, or a document created here,
     * whose geometry the caller knows from the tab it was born on ([OpenTab.canvasMode]). Writing a
     * chunked document as paginated is the rewrite `docs/mobix-format.md` refuses a document to avoid,
     * so a file the session *did* open wins over anything the caller believes about it.
     */
    fun canvasModeOf(document: Document, bornAs: CanvasMode = CanvasMode.PAGINATED): CanvasMode =
        document.documentId?.let { canvasModes[it] } ?: bornAs

    /**
     * Where [document]'s parts sit on an infinite canvas, when this session opened it: the source
     * manifest's own coordinates, or null for a document with no board behind it (the writer then lays
     * its parts out as the mode's strip, `MobixDocument.of`).
     */
    fun chunkCoordsOf(document: Document): List<Pair<Int, Int>>? =
        document.documentId?.let { chunkGrids[it] }

    /**
     * The board geometry [document] was opened with, or null for a document with no board behind it — a
     * `.xopp`, a new document, or one opened before this session remembered it. Null means the writer
     * derives what it can from the pages, which is all a brand-new board has.
     */
    fun canvasOf(document: Document): MobixCanvasFile? =
        document.documentId?.let { canvases[it] }

    /**
     * Close the page readers behind documents the session no longer holds. Called with the ids every
     * open tab and live canvas still carries, so a document being shown, mirrored or kept in the
     * background keeps its reader and only the ones nothing points at are released.
     */
    fun prune(liveDocumentIds: Collection<String?>) {
        val live = liveDocumentIds.filterNotNull().toSet()
        for (id in readers.keys.filterNot { it in live }) {
            readers.remove(id)?.forEach { reader -> runCatching { reader.close() } }
        }
        // The mode and the chunk grid are per-document facts of an open, so they are let go with it:
        // an id the session no longer holds cannot be saved again ([canvasModeOf]).
        for (id in canvasModes.keys.filterNot { it in live }) {
            canvasModes.remove(id)
            chunkGrids.remove(id)
            canvases.remove(id)
            boardExtents.remove(id)
        }
    }
}

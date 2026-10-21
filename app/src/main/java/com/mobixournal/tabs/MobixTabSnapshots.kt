package com.mobixournal.tabs

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Document
import com.mobixournal.io.DocumentIo
import com.mobixournal.io.LoadedFile
import java.io.File

/**
 * The app's snapshot format: a tab whose document is a **`.mobix`** snapshots into the native
 * container, and every other tab stays gzip XML.
 *
 * This is what keeps a bundled payload — a page's picture, a stroke's recording — alive across a
 * restart. The session cache is the only copy of unsaved edits, so `docs/data-path.md` has the whole
 * argument; the short version is that a `.xopp` has nowhere to put an asset, so writing one for a
 * `.mobix` document drops the reference before any save can carry its bytes.
 *
 * The write is the **save's own**, minus the target: no archive matches a snapshot's name, so     * `DocumentIo.encode` builds the whole document and carries the payloads the model still names out of
     * the archive this session read it from (`DocumentIo` → `carriedPayloads`) — which is exactly what a
 * `.mobix` holding a picture on a page nobody has drawn yet needs. Reading goes back through the same
 * open path as a picked file (`DocumentIo.read`), so the restored document's pages adopt their
 * bundled assets when a page is read, and the snapshot is remembered as that document's source, which
 * is what lets a save *after* the restart carry the payloads into the user's own file.
 *
 * Not unit-tested as a class: it needs a [DocumentIo], which needs a device. What it decides is one
 * branch (which suffix) and two calls to the layer that owns them, and `TabStoreTest` pins the store's
 * half of the contract with a seam built on the pure format layer.
 */
class MobixTabSnapshots(
    private val io: DocumentIo,
    /** The file of the recording a stroke names, when the audio store holds it — streamed into the snapshot. */
    private val audio: (String) -> File? = { null },
) : TabSnapshots {

    override fun suffixFor(tab: OpenTab): String =
        if (tab.format == SaveFormat.MOBIX) TabSnapshots.MOBIX_SUFFIX else TabSnapshots.XOPP_SUFFIX

    override fun write(file: File, tab: OpenTab, sources: SnapshotSources) {
        if (tab.format != SaveFormat.MOBIX) {
            XoppSnapshots.write(file, tab, sources)
            return
        }
        // A board's cells are a fact of the board, not of its pages: the save hands the write the grid
        // the board holds now, and a snapshot has to do the same or a cell created since the open would
        // come back laid out on the mode's strip, with the ink written into it moved.
        sources.rememberBoardShape(tab.document)
        val encoded = io.encode(
            document = tab.document,
            pdf = sources.pdf,
            format = SaveFormat.MOBIX,
            // No target: a snapshot is never the file the document came from, so the write builds the
            // archive rather than repacking over one.
            target = null,
            images = sources.images,
            audioFiles = audio,
            canvasMode = tab.canvasMode,
        )
        try {
            encoded.file.copyTo(file, overwrite = true)
        } finally {
            encoded.file.delete()
        }
    }

    override fun read(file: File, tab: OpenTab): SnapshotRead? = runCatching {
        // The **file** decides, not the tab's current format: a snapshot keeps the shape it was written
        // in (an upgrade, or a Save As that switched the document's format), and guessing from the tab
        // would read the wrong container.
        if (file.name.endsWith(TabSnapshots.MOBIX_SUFFIX)) {
            // The open path itself, so the restored document's pages adopt their bundled assets when a
            // page is read — and the resolver that does it goes back to the tab with them. Reading the
            // container without that would draw the pages but not the pictures they name.
            (io.read(file, file.name, null) as? LoadedFile.Doc)
                ?.let { SnapshotRead(it.document, it.backgrounds, it.pdf) }
        } else {
            SnapshotRead(file.inputStream().use(Xopp::open))
        }
    }.getOrNull()
}

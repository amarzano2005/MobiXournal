package com.mobixournal.tabs

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Document
import com.mobixournal.render.BackgroundResolver
import java.io.File

/**
 * What the app's live stores can hand a snapshot writer for the tab whose document is on the canvas.
 *
 * Only the **active** tab's document is live (`EditorPane`), so these are its sources: the PDF it is
 * drawn over and the pictures its pages name. Everything else a snapshot needs — the payloads of a
 * document that was *read* from a `.mobix` — the writer takes from the archive the session still
 * holds, not from here.
 */
data class SnapshotSources(
    /** The local copy of the document's background PDF, when its store has one. */
    val pdf: File? = null,
    /** Each `pixmap` background's picture, keyed by the reference the document carries. */
    val images: Map<String, File> = emptyMap(),
)

/**
 * How one tab's document is written to, and read back from, its snapshot file under `filesDir/tabs/`.
 *
 * The session cache used to be gzip XML for every tab ([XoppSnapshots]), which is why a `.mobix`
 * document lost its bundled payloads across a restart: a `.xopp` has nowhere to put a picture or a
 * recording, so `hydrate` rebuilt the model without those references and the next save wrote the
 * archive without the bytes (`docs/data-path.md` → *The session is cached, not saved*). A document
 * whose own format is [com.mobixournal.format.SaveFormat.MOBIX] therefore snapshots in **its own
 * container** ([MobixTabSnapshots]), and every other document keeps the gzip shape.
 *
 * The seam is a parameter of [TabStore] rather than a decision inside it because the native write is
 * the document I/O layer's (it needs the stores and the open archive), while the naming, the sweep and
 * the lazy restore are the store's. [suffixFor] is part of the contract so those two halves cannot
 * disagree about which file belongs to which tab.
 */
interface TabSnapshots {

    /** The suffix of [tab]'s snapshot file — a fact of the tab's own format. */
    fun suffixFor(tab: OpenTab): String

    /**
     * Write [tab]'s document into [file]. Must leave nothing behind but [file] itself, or throw: the
     * caller writes through a temporary sibling and only renames on success.
     */
    fun write(file: File, tab: OpenTab, sources: SnapshotSources)

    /**
     * Parse [file] back into a document, or null when it cannot be read — a truncated snapshot is
     * left **unhydrated** by [TabStore.hydrate] rather than replaced by a blank placeholder, so the
     * bytes stay recoverable.
     */
    fun read(file: File, tab: OpenTab): SnapshotRead?

    companion object {
        /** The native container's suffix, and the one a `.mobix` tab's snapshot is written with. */
        const val MOBIX_SUFFIX = ".mobix"

        /** The gzip XML suffix — every other tab's snapshot, and the shape before the seam existed. */
        const val XOPP_SUFFIX = ".xopp"

        /**
         * Every suffix a snapshot file can carry, native first. The sweep and the reader both work
         * from this list, so an install that upgraded keeps finding the files it already has.
         */
        val SUFFIXES = listOf(MOBIX_SUFFIX, XOPP_SUFFIX)
    }
}

/**
 * What reading a snapshot gives back.
 *
 * The document is the obvious half; [backgrounds] is the half that is easy to lose. A `.mobix` adopts
 * a page's picture (and its PDF) **when that page is read**, so the open path hands the canvas a
 * resolver rather than a pile of adopted files, and the tab carries it because the pages it answers
 * for are read later (`OpenTab.backgrounds`). A restore that kept only the document would leave the
 * restored tab naming a picture that no longer resolves: the canvas draws a bare page and logs
 * `ImageBackgroundCache: cannot decode pixmap background` — the same loss the native snapshot prevents
 * one layer down, and the one an emulator pass caught after the container half was already right.
 */
data class SnapshotRead(
    val document: Document,
    /** How a page's background becomes a local file when the page is read; null when none is needed. */
    val backgrounds: BackgroundResolver? = null,
    /** The local copy of the document's background PDF, when the read produced one. */
    val pdf: File? = null,
)

/**
 * The shape the session cache had before a `.mobix` document could snapshot natively: every tab is
 * gzip XML, whatever its format. Still the seam for a build with no document I/O wired (a JVM test),
 * and the one that never throws away a payload a `.xopp` could not have carried in the first place.
 */
object XoppSnapshots : TabSnapshots {

    override fun suffixFor(tab: OpenTab): String = TabSnapshots.XOPP_SUFFIX

    override fun write(file: File, tab: OpenTab, sources: SnapshotSources) {
        file.outputStream().use { Xopp.save(tab.document, it) }
    }

    override fun read(file: File, tab: OpenTab): SnapshotRead? =
        runCatching { SnapshotRead(file.inputStream().use(Xopp::open)) }.getOrNull()
}

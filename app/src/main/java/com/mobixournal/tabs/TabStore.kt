package com.mobixournal.tabs

import com.mobixournal.render.blankDocument
import java.io.File

/**
 * Persists the open-tab session so closing the app and reopening it lands you back on the same set
 * of tabs, each with its unsaved edits intact.
 *
 * Layout under [dir] (an app-private folder, normally `filesDir/tabs`):
 * - `session.index` — the tab records and the selection ([TabIndex]).
 * - `<id><suffix>` — one snapshot per tab, written in the shape [snapshots] gives it: the native
 *   container for a `.mobix` document ([MobixTabSnapshots]), gzip XML for everything else
 *   ([XoppSnapshots]), so a restored tab is the document you were editing, unsaved strokes included —
 *   and, for a native one, its bundled pictures and recordings with it.
 *
 * Snapshots are *not* a substitute for saving: they are a crash/restart cache keyed by tab id, and
 * the user's real file (the tab's `uri`) is still the only thing the desktop ever sees.
 */
class TabStore(
    private val dir: File,
    /**
     * The snapshot shape this store's tabs are written in and read back from. A **parameter** rather
     * than a decision made here, and deliberately with no default: a `.mobix` document snapshotted as
     * gzip XML silently loses the payloads only its own container can hold (`TabSnapshots`), which is
     * the data loss this seam exists to make impossible to reintroduce by omission.
     */
    private val snapshots: TabSnapshots,
) {

    /**
     * Write [session] out, replacing whatever was there.
     *
     * [sources] are what the app's stores hold for the tab whose document is on the canvas — the only
     * tab whose document is live ([EditorPane]), and so the only one whose freshly imported pictures
     * or recordings exist anywhere but in the model. A snapshot of any other tab takes its payloads
     * from the archive the session read it from, which the writer does for itself.
     *
     * Snapshots for tabs that are no longer open are deleted, so a long-lived install doesn't
     * accumulate the documents of closed tabs.
     */
    fun save(session: TabSession, sources: SnapshotSources = SnapshotSources()) = runCatching {
        dir.mkdirs()
        // A snapshot belongs to a tab by its **id**, whatever suffix it carries: an install that
        // upgraded holds `<id>.xopp` for a `.mobix` tab until that tab is written natively, and it is
        // still that tab's own file. Sweeping by suffix alone would delete it before the write that
        // replaces it — which for a tab left unhydrated below is never.
        val live = session.tabs.map { it.id }.toSet()
        dir.listFiles()?.forEach { file ->
            val owner = ownerOf(file.name)
            // Stray half-writes left behind by a kill are swept too; nothing may ever reference them.
            if ((owner != null && owner !in live) || file.name.endsWith(TEMP_SUFFIX)) file.delete()
        }
        for (tab in session.tabs) {
            // A tab whose document is not the one on the canvas must never be written:
            // - unhydrated: `document` is a placeholder, its snapshot on disk is already the truth;
            // - opening: the canvas still shows the *previous* tab while the fetch runs, so writing it
            //   would stamp the wrong (or an empty) document over this tab's snapshot and bring it
            //   back that way on the next launch.
            if (!tab.hydrated || tab.opening) continue
            writeSnapshotAtomically(tab, if (tab == session.tabs.getOrNull(session.activeIndex)) sources else SnapshotSources())
        }
        indexFile().writeText(TabIndex.encode(session))
    }.isSuccess

    /**
     * Write [tab]'s document through a temporary sibling and a rename, so a process killed mid-write
     * can never leave a truncated snapshot behind. The snapshot is the only copy of the user's unsaved
     * edits; a half-written file is worse than an old one, because the reader fails to parse it exactly
     * when the app is coming back from the very crash that truncated it.
     */
    private fun writeSnapshotAtomically(tab: OpenTab, sources: SnapshotSources) {
        val file = fileFor(tab)
        val tmp = File(dir, file.name + TEMP_SUFFIX)
        runCatching {
            snapshots.write(tmp, tab, sources)
            // Same-directory rename is atomic; the delete fallback covers a filesystem that refuses
            // to replace an existing target.
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            // The tab has one snapshot, not two: the file under the other suffix is what this tab's
            // snapshot used to be (an upgrade, or a Save As that switched its format), and leaving it
            // would make the read depend on which of the two the sweep happened to leave behind.
            otherFilesFor(tab).forEach { it.delete() }
        }.onFailure { tmp.delete() }
    }

    /**
     * Read the session back **lazily**: this only reads the small index, so every tab comes back as an
     * unhydrated placeholder ([OpenTab.hydrated]) whose document is filled in by [hydrate] when it is
     * about to be shown. No document is parsed here at all — parsing a whole session's worth of
     * documents up front is what used to block the first frame long enough for Android to raise an ANR.
     *
     * Tabs whose snapshot file is missing are dropped — a lost snapshot costs that one tab, never the
     * whole restore. Returns null when there is no session on disk (first launch) or nothing survived,
     * and the caller should start with a fresh blank tab.
     */
    fun load(): TabSession? = runCatching {
        val index = indexFile().takeIf(File::isFile) ?: return@runCatching null
        val parsed = TabIndex.decode(index.readText()) { blankDocument() }
        val present = parsed.tabs.filter { existingFileFor(it) != null }
        if (present.isEmpty()) null
        else TabSession(present, parsed.activeIndex.coerceIn(0, present.lastIndex))
    }.getOrNull()

    /**
     * Copy the snapshot of [sourceId] in [from] to a tab called [newId] here, so a tab handed to the
     * other pane keeps its content without anyone parsing the document. The copy keeps its suffix —
     * it is the same document, and the reader decides by the file's own shape. Returns false when
     * there was nothing to copy.
     */
    fun adopt(from: TabStore, sourceId: String, newId: String): Boolean = runCatching {
        val src = from.existingFileById(sourceId) ?: return@runCatching false
        dir.mkdirs()
        src.copyTo(File(dir, newId + src.name.removePrefix(sourceId)), overwrite = true)
        true
    }.getOrDefault(false)

    /**
     * Parse [tab]'s snapshot and return the tab holding its real document — and the state that document
     * needs to be *shown*: a `.mobix` reads its pictures through a resolver the open path built, so the
     * resolver has to come back with the document ([SnapshotRead]).
     *
     * An already-hydrated tab is returned untouched, and an unreadable snapshot leaves the tab as it
     * was (a blank placeholder) but marks it hydrated, so the damage is one empty tab rather than a
     * re-read on every switch.
     */
    fun hydrate(tab: OpenTab): OpenTab {
        if (tab.hydrated) return tab
        val file = existingFileFor(tab)
        // No snapshot at all: there is genuinely nothing to restore, so a blank document is honest.
        if (file == null) return tab.copy(hydrated = true)
        val read = snapshots.read(file, tab)
        // Present but unreadable (truncated by a kill, say): stay **unhydrated** so [save] refuses to
        // overwrite the file. The bytes may still be recoverable; writing a blank document over them
        // is how one bad snapshot silently becomes permanent data loss.
        if (read == null) return tab
        // The state the open path would have attached to this tab comes back with the document: a
        // `.mobix` adopts a page's picture when that page is read, so a restore that dropped the
        // resolver would draw every page bare.
        return tab.copy(
            document = read.document,
            backgrounds = read.backgrounds,
            pdfPath = read.pdf?.absolutePath ?: tab.pdfPath,
            hydrated = true,
        )
    }

    /** Throw the whole cached session away (used when the user closes the last tab). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun indexFile() = File(dir, INDEX_NAME)

    /** The file [tab]'s snapshot is **written** to: the shape its current format asks for. */
    private fun fileFor(tab: OpenTab) = File(dir, tab.id + snapshots.suffixFor(tab))

    /** The snapshot [tab] can be **read** from: what its format asks for, else whatever it has. */
    private fun existingFileFor(tab: OpenTab): File? =
        fileFor(tab).takeIf(File::isFile) ?: existingFileById(tab.id)

    private fun existingFileById(id: String): File? =
        TabSnapshots.SUFFIXES.map { File(dir, id + it) }.firstOrNull(File::isFile)

    /** Every file this tab's snapshot could be under **except** the one it is being written to. */
    private fun otherFilesFor(tab: OpenTab): List<File> {
        val written = fileFor(tab).name
        return TabSnapshots.SUFFIXES.map { File(dir, tab.id + it) }.filter { it.name != written }
    }

    /** The tab id a snapshot file name belongs to, or null when the file is not a snapshot at all. */
    private fun ownerOf(name: String): String? = TabSnapshots.SUFFIXES
        .firstOrNull { name.endsWith(it) }
        ?.let { name.removeSuffix(it) }

    companion object {
        /** The index file name — the small text record of which tabs were open and which was showing. */
        private const val INDEX_NAME = "session.index"
        /** Suffix of the in-progress atomic-write sibling; never treated as a snapshot. */
        private const val TEMP_SUFFIX = ".tmp"

        /**
         * A fresh tab id. Time-based and counter-salted so ids stay unique within a run and across
         * runs, and never collide with a snapshot left behind by a previous session.
         */
        fun newId(): String = "tab-${System.currentTimeMillis()}-${counter++}"

        private var counter = 0
    }
}

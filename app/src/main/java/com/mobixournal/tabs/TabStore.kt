package com.mobixournal.tabs

import com.mobixournal.format.Xopp
import com.mobixournal.render.blankDocument
import java.io.File

/**
 * Persists the open-tab session so closing the app and reopening it lands you back on the same set
 * of tabs, each with its unsaved edits intact.
 *
 * Layout under [dir] (an app-private folder, normally `filesDir/tabs`):
 * - `session.index` — the tab records and the selection ([TabIndex]).
 * - `<id>.xopp` — one snapshot per tab, written in the app's own on-disk format (gzip XML), so a
 *   restored tab is byte-for-byte the document you were editing, unsaved strokes included.
 *
 * Snapshots are *not* a substitute for saving: they are a crash/restart cache keyed by tab id, and
 * the user's real file (the tab's `uri`) is still the only thing the desktop ever sees.
 */
class TabStore(private val dir: File) {

    /**
     * Write [session] out, replacing whatever was there. Snapshots for tabs that are no longer open
     * are deleted, so a long-lived install doesn't accumulate the documents of closed tabs.
     */
    fun save(session: TabSession) = runCatching {
        dir.mkdirs()
        val live = session.tabs.map { snapshotFile(it.id) }.toSet()
        dir.listFiles()?.forEach { file ->
            val isSnapshot = file.name.endsWith(SNAPSHOT_SUFFIX) && file !in live
            // Stray half-writes left behind by a kill are swept too; nothing may ever reference them.
            val isTemp = file.name.endsWith(TEMP_SUFFIX)
            if (isSnapshot || isTemp) file.delete()
        }
        for (tab in session.tabs) {
            // A tab whose document is not the one on the canvas must never be written:
            // - unhydrated: `document` is a placeholder, its snapshot on disk is already the truth;
            // - opening: the canvas still shows the *previous* tab while the fetch runs, so writing it
            //   would stamp the wrong (or an empty) document over this tab's snapshot and bring it
            //   back that way on the next launch.
            if (!tab.hydrated || tab.opening) continue
            writeSnapshotAtomically(snapshotFile(tab.id), tab.document)
        }
        indexFile().writeText(TabIndex.encode(session))
    }.isSuccess

    /**
     * Write [document] to [file] through a temporary sibling and a rename, so a process killed
     * mid-write can never leave a truncated snapshot behind. The snapshot is the only copy of the
     * user's unsaved edits; a half-written file is worse than an old one, because the reader fails to
     * parse it exactly when the app is coming back from the very crash that truncated it.
     */
    private fun writeSnapshotAtomically(file: File, document: com.mobixournal.format.model.Document) {
        val tmp = File(file.parentFile, file.name + TEMP_SUFFIX)
        runCatching {
            tmp.outputStream().use { Xopp.save(document, it) }
            // Same-directory rename is atomic; the delete fallback covers a filesystem that refuses
            // to replace an existing target.
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { tmp.delete() }
    }

    /**
     * Read the session back **lazily**: this only reads the small index, so every tab comes back as an
     * unhydrated placeholder ([OpenTab.hydrated]) whose document is filled in by [hydrate] when it is
     * about to be shown. No gzip XML is parsed here at all — parsing a whole session's worth of it up
     * front is what used to block the first frame long enough for Android to raise an ANR.
     *
     * Tabs whose snapshot file is missing are dropped — a lost snapshot costs that one tab, never the
     * whole restore. Returns null when there is no session on disk (first launch) or nothing survived,
     * and the caller should start with a fresh blank tab.
     */
    fun load(): TabSession? = runCatching {
        val index = indexFile().takeIf(File::isFile) ?: return@runCatching null
        val parsed = TabIndex.decode(index.readText()) { blankDocument() }
        val present = parsed.tabs.filter { snapshotFile(it.id).isFile }
        if (present.isEmpty()) null
        else TabSession(present, parsed.activeIndex.coerceIn(0, present.lastIndex))
    }.getOrNull()

    /**
     * Copy the snapshot of [sourceId] in [from] to a tab called [newId] here, so a tab handed to the
     * other pane keeps its content without anyone parsing the document. Returns false when there was
     * nothing to copy.
     */
    fun adopt(from: TabStore, sourceId: String, newId: String): Boolean = runCatching {
        val src = from.snapshotFile(sourceId).takeIf(File::isFile) ?: return@runCatching false
        dir.mkdirs()
        src.copyTo(snapshotFile(newId), overwrite = true)
        true
    }.getOrDefault(false)

    /**
     * Parse [tab]'s snapshot and return the tab holding its real document. An already-hydrated tab is
     * returned untouched, and an unreadable snapshot leaves the tab as it was (a blank placeholder)
     * but marks it hydrated, so the damage is one empty tab rather than a re-read on every switch.
     */
    fun hydrate(tab: OpenTab): OpenTab {
        if (tab.hydrated) return tab
        val file = snapshotFile(tab.id)
        // No snapshot at all: there is genuinely nothing to restore, so a blank document is honest.
        if (!file.isFile) return tab.copy(hydrated = true)
        val doc = runCatching { file.inputStream().use(Xopp::open) }.getOrNull()
        // Present but unreadable (truncated by a kill, say): stay **unhydrated** so [save] refuses to
        // overwrite the file. The bytes may still be recoverable; writing a blank document over them
        // is how one bad snapshot silently becomes permanent data loss.
        return if (doc == null) tab else tab.copy(document = doc, hydrated = true)
    }

    /** Throw the whole cached session away (used when the user closes the last tab). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun indexFile() = File(dir, INDEX_NAME)

    private fun snapshotFile(id: String) = File(dir, "$id$SNAPSHOT_SUFFIX")

    companion object {
        /** The index file name — the small text record of which tabs were open and which was showing. */
        private const val INDEX_NAME = "session.index"
        /** Suffix for tab snapshot files — each tab's document is saved as `<id>.xopp`. */
        private const val SNAPSHOT_SUFFIX = ".xopp"
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

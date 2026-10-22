package com.mobixournal.desktop

import com.mobixournal.format.SaveFormat
import com.mobixournal.io.DocumentEncoder
import java.io.File
import java.security.MessageDigest

/**
 * The window's **crash snapshots**: while a tab is dirty, its edited document is written as a
 * self-contained `.mobix` beside the settings file, and a start that finds one offers it as a recovered
 * tab instead of losing the work to a closed lid or a killed process.
 *
 * The store is deliberately dumb, and that is the point: it knows nothing about tabs, pages or saves.
 * It writes one file per **origin path** (the key is a digest of the same normalised path
 * [DesktopTabs.keyOf] dedupes opens by, so two tabs of one file share one snapshot), deletes it when the
 * shell says the edits reached the file, and lists what a previous run left behind. The tab decides
 * *when* it is dirty and the shell decides *when* to write — this class only knows *how*.
 *
 * The snapshot is a **native `.mobix`** whatever the document itself is: that container is
 * self-contained by design (`docs/mobix-format.md`), so a PDF or a picture background still paints from
 * a snapshot, and a `.xopp` tab's edits survive in a container that can carry them. It is written
 * through a temp file in the same folder and moved into place ([DesktopSaves.replace]), so a crash
 * during a snapshot leaves the previous one whole rather than a truncated file.
 *
 * A write is the notebook's size, not one page's: a `.mobix` snapshot cannot repack over the file it is
 * standing in for. That is why the shell writes it after a **quiet** period, once per burst of edits,
 * rather than per stroke ([DesktopShellState.flushRecovery]).
 *
 * The sidecar (`<key>.origin`) is two lines — the tab's label and the absolute path it was opened from —
 * because a digest cannot be turned back into a path, and a recovery prompt that cannot name the file is
 * not a recovery prompt.
 */
class DesktopRecovery(val dir: File = defaultDir()) {

    /** One snapshot on disk, and the tab it stands for. */
    class Entry(
        val snapshot: File,
        /** The path the tab was opened from, as written when the snapshot was taken. */
        val originPath: String,
        /** The label the tab had — the file's name unless the user renamed it. */
        val title: String,
    ) {
        /** The file the recovered tab saves back to. */
        val origin: File get() = File(originPath)
    }

    /**
     * Write [tab]'s document as a snapshot of its file. Answers the entry it wrote, or null when the
     * write failed — a snapshot that cannot be taken is not worth interrupting the user for; the tab
     * stays dirty and the next quiet moment tries again.
     */
    fun save(tab: DesktopTab): Entry? = runCatching {
        dir.mkdirs()
        val snapshot = snapshotFor(tab.file)
        val staged = File.createTempFile("recovery", WRITING_SUFFIX, dir)
        try {
            staged.outputStream().use { out ->
                DocumentEncoder.write(
                    document = tab.document.model,
                    format = SaveFormat.MOBIX,
                    target = DocumentEncoder.FileSaveTarget(snapshot),
                    out = out,
                    appVersion = DesktopSaves.APP_VERSION,
                    // The origin is what a `.mobix` write would carry assets from; the snapshot target is
                    // elsewhere, so the write rebuilds rather than repacks over the open file.
                    shape = tab.document.saveShapeFor(snapshot),
                )
            }
            DesktopSaves.replace(staged, snapshot)
            sidecarFor(tab.file).writeText("${tab.title}\n${tab.file.absolutePath}")
            Entry(snapshot, tab.file.absolutePath, tab.title)
        } finally {
            staged.delete()
        }
    }.getOrNull()

    /**
     * Every snapshot a previous run left behind, oldest first, so recovered tabs come back in the order
     * the session opened them. A snapshot with no readable sidecar is skipped: it cannot name the file it
     * belongs to, and guessing one would recover work into the wrong document.
     */
    fun entries(): List<Entry> {
        val snapshots = dir.listFiles { file -> file.isFile && file.name.endsWith(SNAPSHOT_SUFFIX) }
            ?: return emptyList()
        return snapshots.sortedBy { it.lastModified() }.mapNotNull(::entryOf)
    }

    /** Drop [tab]'s snapshot: its edits are in the file (a save) or were deliberately discarded. */
    fun forget(tab: DesktopTab) = forget(tab.file)

    /** Drop the snapshot kept for [origin], if any. */
    fun forget(origin: File) {
        snapshotFor(origin).delete()
        sidecarFor(origin).delete()
    }

    private fun entryOf(snapshot: File): Entry? {
        val lines = runCatching { sidecarForSnapshot(snapshot).readLines() }.getOrNull() ?: return null
        val title = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val origin = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return Entry(snapshot, origin, title)
    }

    /** The digest of the file's identity — the same normalised path two opens of one document share. */
    private fun keyOf(origin: File): String = MessageDigest.getInstance("SHA-256")
        .digest(DesktopTabs.keyOf(origin).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun snapshotFor(origin: File): File = File(dir, keyOf(origin) + SNAPSHOT_SUFFIX)

    private fun sidecarFor(origin: File): File = File(dir, keyOf(origin) + SIDECAR_SUFFIX)

    private fun sidecarForSnapshot(snapshot: File): File =
        File(dir, snapshot.name.removeSuffix(SNAPSHOT_SUFFIX) + SIDECAR_SUFFIX)

    companion object {
        /** A snapshot is a native archive, so it keeps the extension of one. */
        const val SNAPSHOT_SUFFIX = ".mobix"

        /** The two-line sidecar `<key>.origin`: the tab's label, then the path it was opened from. */
        private const val SIDECAR_SUFFIX = ".origin"

        private const val WRITING_SUFFIX = ".writing"

        /** The store's home: a `recovery/` folder beside `desktop.properties` (`DesktopSettings`). */
        fun defaultDir(): File {
            val settings = DesktopSettings.defaultFile()
            return File(settings.parentFile ?: File("."), "recovery")
        }
    }
}

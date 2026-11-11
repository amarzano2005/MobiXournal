package com.mobixournal.desktop.io

import java.io.File

/**
 * The **copy a `.mobix` is read from**, so the file the user keeps is never held open.
 *
 * A lazy notebook reads its pages through a `ZipFile`, which keeps the archive open for as long as the tab
 * lives. Windows then refuses both of the moves a save needs — replacing the file, and renaming it — because
 * the reader did not grant delete sharing (`MobixPagedReader.fromFile` opens it with `ZipFile`). Closing the
 * reader to save is what made a save stop the canvas.
 *
 * Reading a copy costs one file copy at open and removes the conflict entirely: the original is a plain file
 * the window only writes, so a save is a stage and a rename with nothing to release. The copy is the tab's
 * own, deleted when the document closes ([DesktopDocument.close]), and it lives in the system temp folder
 * rather than beside the notebook — a document's folder is the user's, not the app's.
 */
internal object DesktopReadCopies {

    /** Where the copies live: one folder under the system temp directory, created on first use. */
    private val folder: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "mobixournal-reading").apply { mkdirs() }
    }

    /**
     * [file] copied into the cache, under a short fixed-name prefix — the staging files' own lesson about
     * long names and deep paths. The copy keeps the original's bytes exactly, so the pages a reader inflates
     * from it are the pages the document was opened with.
     */
    fun of(file: File): File {
        folder.mkdirs()
        val copy = File.createTempFile("mobix-", ".reading", folder)
        file.inputStream().buffered().use { input ->
            copy.outputStream().buffered().use { out -> input.copyTo(out) }
        }
        return copy
    }
}

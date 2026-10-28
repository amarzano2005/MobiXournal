package com.mobixournal.desktop

import java.io.File

/**
 * **One file per page, into a folder** — the half the window's page exports share.
 *
 * SVG and PNG differ only in what a page's bytes are: the vector writer compiles the model, the raster
 * writer paints it through the same painter the canvas uses. Everything around that is identical, and it
 * is the part that can be got wrong quietly — a name that does not sort, a folder that cannot be written,
 * a crash halfway through a two-hundred-page notebook leaving a truncated page under a real name. So the
 * naming, the destination check and the staged write live here, once.
 */
internal object DesktopPageFolderExport {

    /**
     * Write every page of [document] into [folder] as `<document>-p<NNN>.<extension>`, and answer the
     * files in page order.
     *
     * The number is padded to at least [PAGE_DIGITS] digits and to the page count's own width, so the
     * files of a 1200-page notebook still sort in reading order in any file manager. Each page is staged
     * in a temp file in the same folder and moved over its name (`DesktopSaves.replace`), which is the
     * window's rule for every write: a crash or a full disk leaves the previous export intact instead of
     * a half-written page.
     *
     * [folder] defaults to the document's own folder, because "the pages, beside the notebook" is the
     * answer a user expects and the window has no folder-only picker to ask with. It has to exist or be
     * creatable and writable — a destination that cannot hold the pages is refused with a sentence a
     * person can act on, before anything is written.
     */
    fun write(
        document: DesktopDocument,
        folder: File = document.file.absoluteFile.parentFile ?: File("."),
        extension: String,
        bytesFor: (index: Int) -> ByteArray,
    ): List<File> {
        prepare(folder)
        // The **window's** pages, not the model's list: a paged `.mobix` describes its pages in the
        // manifest and inflates one when it is read, and an edit lives in the window's overlay, so
        // walking `model.pages` would export the file as it was opened — or nothing at all.
        val pages = document.pageCount
        if (pages <= 0) {
            throw DesktopSaves.SaveRefused("This document has no pages, so there is nothing to export.")
        }
        val base = document.file.nameWithoutExtension.ifBlank { "document" }
        val digits = maxOf(PAGE_DIGITS, pages.toString().length)
        return (0 until pages).map { index ->
            val name = "$base-p${(index + 1).toString().padStart(digits, '0')}.$extension"
            val target = File(folder, name)
            // Short on purpose: a staging name that repeated the page's own long name is what a deep
            // folder ran out of characters on (`DesktopSaves.stagingFile` owns the story).
            val temp = DesktopSaves.stagingFile(folder)
            try {
                temp.writeBytes(bytesFor(index))
                DesktopSaves.replace(temp, target)
            } catch (failure: Throwable) {
                // Nothing lands under a page's real name unless its bytes are complete.
                temp.delete()
                throw failure
            }
            target
        }
    }

    /** A folder that can receive the pages, or a refusal that says why in English. */
    private fun prepare(folder: File) {
        if (folder.exists() && !folder.isDirectory) {
            throw DesktopSaves.SaveRefused(
                "That destination is a file, and the pages have to be written into a folder. Pick a folder instead.",
            )
        }
        if (!folder.exists() && !folder.mkdirs()) {
            throw DesktopSaves.SaveRefused("Could not create a folder to export the pages into: ${folder.path}.")
        }
        if (!folder.canWrite()) {
            throw DesktopSaves.SaveRefused(
                "This folder cannot be written to, so no page was exported. Choose one you own.",
            )
        }
    }

    /** Page numbers are padded to at least this many digits so a folder of pages sorts by name. */
    private const val PAGE_DIGITS = 3
}

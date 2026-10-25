package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.render.PageSvgWriter
import java.io.File

/**
 * The window's **SVG export**: one vector file per page, written beside the document.
 *
 * The drawing is not here. `PageSvgWriter` (in `mobix-core`) already serialises a page of this model —
 * the sheet, its ruling, strokes with their alpha and fills, text boxes, images and LaTeX, every living
 * entity through its compiled ink — and it is the same writer the tablet's "Export page as SVG" calls,
 * so the PC and the phone cannot produce two different pictures of one page. What this file adds is
 * what a *document* needs and a single page does not: a name for each sheet, a folder to put them in,
 * and a write that cannot leave a truncated file under a real name.
 *
 * The **picture** a page sits on is the caller's: a page written over an annotated PDF, or one backed
 * by a photograph, is pixels rather than geometry, so the shell rasterises it (through [PageBackdrop])
 * and hands the bytes over. A page with no picture passes `null` and its file is byte for byte the one
 * the tablet would have written.
 */
internal object DesktopSvgExport {

    /**
     * Write every page of [document] into [folder] as `<document>-p<NNN>.svg`, and answer the files in
     * page order.
     *
     * The number is padded to at least [PAGE_DIGITS] digits and to the page count's own width, so the
     * files of a 1200-page notebook still sort in reading order in any file manager. Each page is staged
     * in a temp file in the same folder and moved over its name ([DesktopSaves.replace]), which is the
     * window's rule for every write: a crash or a full disk leaves the previous export intact instead of a
     * half-written page.
     *
     * [folder] defaults to the document's own folder, because "the pages, beside the notebook" is the
     * answer a user expects and the window has no folder-only picker to ask with. It has to exist or be
     * creatable and writable — a destination that cannot hold the pages is refused with a sentence a
     * person can act on, before anything is written.
     *
     * [backdrop] is asked for the bytes of the picture behind a page, or null for none.
     */
    fun write(
        document: DesktopDocument,
        folder: File = document.file.absoluteFile.parentFile ?: File("."),
        backdrop: (Background) -> ByteArray?,
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
            val page = document.page(index)
            val name = "$base-p${(index + 1).toString().padStart(digits, '0')}.svg"
            val target = File(folder, name)
            val temp = File.createTempFile(".$name.", ".writing", folder)
            try {
                temp.writeBytes(PageSvgWriter.write(page, backdrop(page.background)).toByteArray(Charsets.UTF_8))
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

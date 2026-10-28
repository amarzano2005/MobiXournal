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
 * what a *document* needs and a single page does not: the picture a page sits on.
 *
 * The **picture** is the caller's: a page written over an annotated PDF, or one backed by a photograph,
 * is pixels rather than geometry, so the shell rasterises it (through [PageBackdrop]) and hands the bytes
 * over. A page with no picture passes `null` and its file is byte for byte the one the tablet would have
 * written. The naming, the folder checks and the staged write are [DesktopPageFolderExport]'s, shared
 * with the PNG export beside it.
 */
internal object DesktopSvgExport {

    /**
     * Write every page of [document] into [folder] as `<document>-p<NNN>.svg`, and answer the files in
     * page order.
     *
     * [folder] defaults to the document's own folder, because "the pages, beside the notebook" is the
     * answer a user expects. [backdrop] is asked for the bytes of the picture behind a page, or null.
     */
    fun write(
        document: DesktopDocument,
        folder: File = document.file.absoluteFile.parentFile ?: File("."),
        backdrop: (Background) -> ByteArray?,
    ): List<File> = DesktopPageFolderExport.write(document, folder, "svg") { index ->
        val page = document.page(index)
        PageSvgWriter.write(page, backdrop(page.background)).toByteArray(Charsets.UTF_8)
    }
}

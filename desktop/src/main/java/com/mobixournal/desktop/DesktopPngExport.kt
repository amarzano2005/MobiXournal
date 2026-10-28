package com.mobixournal.desktop

import androidx.compose.ui.graphics.toAwtImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * The window's **PNG export**: one raster file per page, written beside the document — the tablet's
 * *Page as PNG*, for a whole document.
 *
 * The picture is not a screenshot of the window. Each page is painted by [renderPageBitmap], which draws
 * `drawPageContent` — the painter the canvas itself uses — onto a raster surface, with the page's PDF or
 * photograph behind it ([PageBackdrop]) and the living entities compiled by `LivingInk`, exactly as the
 * SVG writer compiles them. So a PNG here is the sheet at a fixed resolution, not the sheet at whatever
 * zoom and window size happened to be on screen.
 *
 * [SCALE] is **2 px per point**: an A4 page lands at 1191 × 1684, twice the size of a 72 dpi render and
 * still a sane file, which is what a print or a slide want. A page is rasterised once, then encoded, and
 * the write itself is [DesktopPageFolderExport]'s (naming, folder checks, staged replace).
 */
internal object DesktopPngExport {

    /** Pixels per point — 2× the page's own units, so an A4 sheet exports at 144 dpi. */
    const val SCALE: Float = 2f

    /**
     * Write every page of [document] into [folder] as `<document>-p<NNN>.png`, and answer the files in
     * page order.
     */
    fun write(
        document: DesktopDocument,
        folder: File = document.file.absoluteFile.parentFile ?: File("."),
    ): List<File> = DesktopPageFolderExport.write(document, folder, "png") { index ->
        val page = document.page(index)
        val bitmap = renderPageBitmap(page, SCALE, backdrop = document.backdrop)
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(bitmap.toAwtImage(), "png", out)) {
            throw DesktopSaves.SaveRefused("Page ${index + 1} could not be encoded as a PNG image.")
        }
        out.toByteArray()
    }
}

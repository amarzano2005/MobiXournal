package com.mobixournal.desktop.previews

import androidx.compose.ui.graphics.ImageBitmap

/**
 * **One PDFBox render at a time, in the whole window.**
 *
 * PDFBox draws on the JVM's 2D pipeline and is explicit that it is not thread-safe: a `PDFRenderer` belongs to
 * its document, and the drawing machinery behind it (the font mapper, the glyph cache, the colour spaces) is
 * shared state that two renders at once can interleave. The window has several surfaces that rasterise the
 * same file — the canvas painting a page, the page list drawing a thumbnail, the import card making previews
 * on its own worker — so without a gate they *do* overlap the moment a PDF-backed document is scrolled while
 * the card is open.
 *
 * The damage is not a crash: the shared caches answer one document's fonts or widths to the other, and the
 * page comes out with the right layout and the wrong glyphs (or the right glyphs at the wrong advances) —
 * which is exactly the kind of picture a reader reports as *the PDF opens broken* and nothing in a log can
 * witness. Rasterising is cached, so serialising it costs one lock and no visible latency.
 *
 * Every PDFBox rasterisation in this window goes through here: [com.mobixournal.desktop.sheet.PageBackdrop]'s
 * page and [DesktopPdfThumbnails]'s previews.
 */
internal object PdfRenderGate {

    private val lock = Any()

    /** Rasterise a page, with no other PDFBox render in the window running. */
    fun page(render: () -> ImageBitmap?): ImageBitmap? = synchronized(lock) { render() }

    /** Rasterise a preview's bytes (a PNG), with no other PDFBox render in the window running. */
    fun png(render: () -> ByteArray): ByteArray = synchronized(lock) { render() }
}

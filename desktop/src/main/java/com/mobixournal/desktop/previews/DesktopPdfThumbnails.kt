package com.mobixournal.desktop.previews

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.Image as SkiaImage

/** How many page previews the card keeps: a screened grid's worth, never a scan's worth. */
internal const val DESKTOP_THUMBNAILS_KEPT = 60

/** The width a preview is rasterised to, in px — a cell's own size at 2×. */
private const val THUMBNAIL_TARGET_PX = 200f

/**
 * **The previews of a PDF about to be imported**, in the window.
 *
 * The same bargain the tablet's card makes ([com.mobixournal.ui.PdfThumbnailCache]): the file is opened
 * once, a page is rendered on a worker only when a cell asks for it, and the map holds
 * [DESKTOP_THUMBNAILS_KEPT] previews — so an eight-hundred-page scan is a scroll, not a render. The raster
 * path is [PageBackdrop]'s (PDFBox → PNG → Skia), at a thumbnail's scale instead of a page's.
 *
 * [close] is the card's `DisposableEffect`: the document, the renderer and every bitmap go when the
 * question is answered or cancelled.
 */
@Stable
internal class DesktopPdfThumbnails(file: File) : Closeable {

    private val images = mutableStateMapOf<Int, ImageBitmap>()
    private val pending = HashSet<Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pdf: PDDocument? = runCatching { PDDocument.load(file) }.getOrNull()
    private val renderer: PDFRenderer? = pdf?.let { PDFRenderer(it) }
    val pageCount: Int = pdf?.numberOfPages ?: 0
    private var closed = false

    /** The preview for [page], or null while it is being made. */
    operator fun get(page: Int): ImageBitmap? = images[page]

    /** Ask for [page]'s preview; the second call for a page already rendered or queued does nothing. */
    fun request(page: Int) {
        if (closed || renderer == null || images.containsKey(page) || !pending.add(page)) return
        scope.launch {
            val bitmap = runCatching {
                // The scale is a *width*: a page is 595 pt wide in A4 portrait and far wider in a drawing.
                val box = pdf!!.getPage(page).cropBox
                val scale = (THUMBNAIL_TARGET_PX / box.width).coerceIn(0.02f, 1f)
                renderer.renderImage(page, scale).toPng()
            }.getOrNull()?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
            withContext(Dispatchers.Main) {
                pending.remove(page)
                if (closed || bitmap == null) return@withContext
                while (images.size >= DESKTOP_THUMBNAILS_KEPT) {
                    val oldest = images.keys.firstOrNull() ?: break
                    images.remove(oldest)
                }
                images[page] = bitmap
            }
        }
    }

    /** Release the document, the worker and the previews. */
    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runCatching { pdf?.close() }
        images.clear()
        pending.clear()
    }

    /** The rendered preview as PNG, for the Skia decoder — the same bridge [PageBackdrop] draws through. */
    private fun BufferedImage.toPng(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(this, "png", out)
        return out.toByteArray()
    }
}

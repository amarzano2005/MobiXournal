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
 * **One page at a time, on one worker, behind the window's one render gate.** A [PDFRenderer] is not
 * thread-safe — two cells scrolled into view together used to render the same document on two pool threads at
 * once, and PDFBox answers that with a failed or half-built raster (the cell then stayed empty, silently,
 * because a preview that cannot be made is not an error worth a card). The renders share a single thread, so
 * the queue is invisible and every raster is a whole one, and each one takes [PdfRenderGate] so it cannot
 * interleave with the canvas painting the same file behind the card.
 *
 * The images are written **from that worker** rather than hopping to a UI dispatcher: Compose's snapshot
 * state is safe to write from any thread, and the window has no reason to assume a `Main` dispatcher exists
 * (a packaged Compose/Skiko build installs neither the Swing one nor Android's — see `DesktopApp`). Hopping
 * to a dispatcher that is not there threw on every page, which is exactly how a grid of numbered, empty
 * cells happened.
 *
 * [close] is the card's `DisposableEffect`: the document, the renderer and every bitmap go when the
 * question is answered or cancelled.
 */
@Stable
internal class DesktopPdfThumbnails(file: File) : Closeable {

    private val images = mutableStateMapOf<Int, ImageBitmap>()

    /** The pages in the order they were rendered — the eviction order a state map cannot promise. */
    private val order = ArrayDeque<Int>()
    private val pending = HashSet<Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
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
            val bitmaps = runCatching {
                // The scale is a *width*: a page is 595 pt wide in A4 portrait and far wider in a drawing.
                val box = pdf!!.getPage(page).cropBox
                val scale = (THUMBNAIL_TARGET_PX / box.width).coerceIn(0.02f, 1f)
                PdfRenderGate.png { renderer.renderImage(page, scale).toPng() }
            }.getOrNull()
            val bitmap = bitmaps?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
            pending.remove(page)
            if (closed || bitmap == null) return@launch
            while (order.size >= DESKTOP_THUMBNAILS_KEPT) {
                val oldest = order.removeFirstOrNull() ?: break
                images.remove(oldest)
            }
            images[page] = bitmap
            order.addLast(page)
        }
    }

    /** Release the document, the worker and the previews. */
    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runCatching { pdf?.close() }
        images.clear()
        order.clear()
        pending.clear()
    }

    /** The rendered preview as PNG, for the Skia decoder — the same bridge [PageBackdrop] draws through. */
    private fun BufferedImage.toPng(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(this, "png", out)
        return out.toByteArray()
    }
}

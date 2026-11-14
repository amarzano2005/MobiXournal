package com.mobixournal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mobixournal.render.PdfPageCache
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many page previews the import card keeps: enough for a screened grid, never the whole file. */
internal const val PDF_THUMBNAILS_KEPT = 60

/**
 * **The previews of a PDF about to be imported**, rendered off the UI thread and kept to a handful.
 *
 * The card asks only for the pages it is showing ([request]), so a five-hundred-page scan is a scroll and a
 * few rasters, not five hundred: the grid's cells are lazy, and the cache answers the ones that exist. An
 * entry is dropped once [PDF_THUMBNAILS_KEPT] earlier pages are in, so scrolling far down a long file costs
 * the top rows rather than the heap.
 *
 * It owns the only [PdfPageCache] the card opens, and [close] is the card's `DisposableEffect`: the PDF's
 * rasteriser and every bitmap it made are released when the question is answered or cancelled, which is the
 * task's *release resources on dismissal*.
 */
@Stable
internal class PdfThumbnailCache(
    file: File,
    private val widthPx: Int,
) : Closeable {

    private val images = mutableStateMapOf<Int, ImageBitmap>()
    private val pending = HashSet<Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val source: PdfPageCache? = runCatching { PdfPageCache(file) }.getOrNull()
    private var closed = false

    /** The preview for [page], or null while it is still being made (the cell shows its number then). */
    operator fun get(page: Int): ImageBitmap? = images[page]

    /** Ask for [page]'s preview; the second call for a page already rendered or queued does nothing. */
    fun request(page: Int) {
        if (closed || source == null || images.containsKey(page) || !pending.add(page)) return
        if (page < 0 || page >= source.pageCount) return
        scope.launch {
            val bitmap: Bitmap? = runCatching { source.render(page, widthPx) }.getOrNull()
            withContext(Dispatchers.Main) {
                pending.remove(page)
                if (closed || bitmap == null) return@withContext
                while (images.size >= PDF_THUMBNAILS_KEPT) {
                    val oldest = images.keys.firstOrNull() ?: break
                    images.remove(oldest)
                }
                images[page] = bitmap.asImageBitmap()
            }
        }
    }

    /**
     * Release everything: the queued work, the rasteriser, the previews. The bitmaps belong to the cache that
     * made them, so dropping the references is all that is left to do here.
     */
    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runCatching { source?.close() }
        images.clear()
        pending.clear()
    }
}

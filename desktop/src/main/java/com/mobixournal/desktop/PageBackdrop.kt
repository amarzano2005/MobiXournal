package com.mobixournal.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mobixournal.format.model.Background
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.Image as SkiaImage

/**
 * The picture a page sits on — a pixmap or an annotated PDF page — resolved to something Skia can draw.
 *
 * Both containers carry one in two ways, and a page may need both: a `.mobix` bundles the bytes
 * (`bundledAsset`) *and* keeps the `.xopp` reference it came from, a `.xopp` only has the reference
 * (next to the document, or extracted from a ZIP package by the codec). So resolution tries the bundle
 * first and the filesystem second — a bundled document must not fail just because the original file is
 * gone.
 *
 * Rendering is cached by what the background *is* (reference plus page number): scrolling a 200-page
 * annotated PDF back and forth must not rasterise the same PDF page twice, and the cache is small and
 * bounded because these are whole page-sized bitmaps.
 *
 * A PDF page is rasterised at [PDF_SCALE] × its natural size (about 144 dpi). That is the same
 * trade-off the Android shell makes for its page cache: crisp enough that handwriting over it stays
 * readable, small enough that a dozen pages fit in memory.
 */
class PageBackdrop(
    /** The bytes of a bundled asset (`background/background.pdf`, `assets/img_….png`), or null. */
    private val bundled: (name: String) -> ByteArray?,
    /** Where the document's own folder is, for an external reference. */
    private val documentDir: File?,
    /** A file a ZIP `.xopp` package already extracted under this name, or null. */
    private val extracted: (name: String) -> File? = { null },
) {

    private val cache = LinkedHashMap<String, ImageBitmap?>()

    /** The image behind [background], or null when there is nothing to draw (or nothing to find). */
    @Synchronized
    fun image(background: Background): ImageBitmap? {
        val key = keyOf(background) ?: return null
        if (cache.containsKey(key)) return cache[key]
        val bitmap = runCatching { render(background) }.getOrNull()
        if (cache.size >= CACHE_MAX) cache.clear()
        cache[key] = bitmap
        return bitmap
    }

    private fun keyOf(background: Background): String? = when (background) {
        is Background.Pixmap -> "pixmap|${background.bundledAsset}|${background.domain}|${background.filename}"
        is Background.Pdf -> "pdf|${background.bundledAsset}|${background.domain}|${background.filename}|${background.pageNo}"
        is Background.Solid -> null
    }

    private fun render(background: Background): ImageBitmap? = when (background) {
        is Background.Pixmap -> bytesOf(background.bundledAsset, background.domain, background.filename)
            ?.let(::decode)

        is Background.Pdf -> {
            val source = bytesOf(background.bundledAsset, background.domain, background.filename)
            val pdf = when {
                source != null -> PDDocument.load(ByteArrayInputStream(source))
                else -> fileOf(background.domain, background.filename)?.let { PDDocument.load(it) }
            }
            pdf?.use { renderPdfPage(it, background.pageNo) }
        }

        is Background.Solid -> null
    }

    /** The bundle first, then the file the reference names. */
    private fun bytesOf(bundledAsset: String?, domain: String?, filename: String?): ByteArray? {
        if (bundledAsset != null) bundled(bundledAsset)?.let { return it }
        val file = fileOf(domain, filename) ?: return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    private fun fileOf(domain: String?, filename: String?): File? {
        if (filename.isNullOrBlank()) return null
        extracted(filename)?.takeIf { it.isFile }?.let { return it }
        File(filename).takeIf { it.isAbsolute && it.isFile }?.let { return it }
        val relative = documentDir?.let { File(it, filename) }
        return relative?.takeIf { it.isFile }
    }

    private fun renderPdfPage(pdf: PDDocument, pageNo: Int): ImageBitmap? {
        if (pdf.numberOfPages == 0) return null
        val index = pageNo.coerceIn(0, pdf.numberOfPages - 1)
        // PDFBox draws on the JVM's 2D pipeline; the result reaches Skia as PNG because a
        // BufferedImage's pixels are not a layout Compose can wrap directly.
        return decode(PDFRenderer(pdf).renderImage(index, PDF_SCALE).toPng())
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()

    /** The rendered PDF page, or an image's bytes, as PNG for the Skia decoder. */
    private fun BufferedImage.toPng(): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(this, "png", it) }.toByteArray()

    private companion object {
        /** 2× the PDF's natural size — about 144 dpi, the Android page cache's trade-off. */
        const val PDF_SCALE = 2f

        /** Page-sized bitmaps: a dozen is already tens of megabytes. */
        const val CACHE_MAX = 12
    }
}

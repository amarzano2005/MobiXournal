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

    /**
     * Where a page that names the PDF **only by `pageno`** finds the document's one reference.
     *
     * A document records the PDF on a single page and every other pdf page carries a page number and
     * nothing else (`docs/pdf.md`), which desktop Xournal++ reads by inheritance. [DesktopDocument] installs
     * the supplier at construction, so the whole window — the canvas, the thumbnails, the PDF and SVG
     * exporters — resolves those pages through one rule instead of each of them guessing.
     */
    var pdfReference: () -> Background.Pdf? = { null }

    /** The image behind [background], or null when there is nothing to draw (or nothing to find). */
    @Synchronized
    fun image(background: Background): ImageBitmap? {
        // A pdf page with no file of its own is drawn from the document's reference; with neither, there
        // is nothing to draw and the page keeps its paper.
        val painted = when (background) {
            is Background.Pdf -> PdfPageReferences.resolve(background, pdfReference()) ?: return null
            else -> background
        }
        val key = keyOf(painted) ?: return null
        if (cache.containsKey(key)) return cache[key]
        val bitmap = runCatching { render(painted) }.getOrNull()
        // The **oldest** entry goes, not the whole map: the page list asks for one thumbnail per page it
        // shows, and clearing twelve cached sheets every time a thirteenth row appears would send the
        // canvas back to rasterising the pages it is actually drawing.
        if (cache.size >= CACHE_MAX) cache.remove(cache.keys.first())
        cache[key] = bitmap
        return bitmap
    }

    /**
     * The **source document** behind a `pdf` background, open and ready to import a page from — the caller
     * must close it.
     *
     * The PDF export needs the page itself, not a picture of it: importing it verbatim keeps its vectors,
     * its text layer and its original size, which is the one thing a raster flatten loses. Resolution is
     * the same bundle-first-then-file one [image] uses, so a document whose bundled page still opens
     * exports exactly what it showed. An unreadable source answers null and the export falls back to the
     * rendered picture.
     */
    fun sourcePdf(background: Background.Pdf): PDDocument? {
        val source = PdfPageReferences.resolve(background, pdfReference()) ?: return null
        val bytes = bytesOf(source.bundledAsset, source.domain, source.filename)
        if (bytes != null) return runCatching { PDDocument.load(ByteArrayInputStream(bytes)) }.getOrNull()
        val file = fileOf(source.domain, source.filename) ?: return null
        return runCatching { PDDocument.load(file) }.getOrNull()
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

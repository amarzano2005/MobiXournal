package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.PdfPageReferences
import com.mobixournal.desktop.shell.search.find
import com.mobixournal.desktop.shell.thumbnail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mobixournal.format.model.Background
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors
import org.apache.pdfbox.pdmodel.PDDocument
import com.mobixournal.desktop.previews.PdfFontRepair
import com.mobixournal.desktop.previews.PdfRenderGate
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

    private val cache = LinkedHashMap<String, ImageBitmap?>(32, 0.75f, true)
    private val pdfDocs = LinkedHashMap<String, CachedPdfDoc>(4, 0.75f, true)
    private val inFlight = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "mobix-desktop-backdrop").apply { isDaemon = true }
    }

    /** Incremented when a background backdrop render completes so interactive sheets re-record cleanly. */
    var version by mutableIntStateOf(0)
        private set

    private class CachedPdfDoc(val doc: PDDocument) {
        val renderLock = Any()
        val repairedPages = HashSet<Int>()
        var refs = 0
        var evicted = false

        fun release() {
            val shouldClose = synchronized(this) {
                refs -= 1
                evicted && refs <= 0
            }
            if (shouldClose) runCatching { doc.close() }
        }
    }

    /**
     * Where a page that names the PDF **only by `pageno`** finds the document's one reference.
     *
     * A document records the PDF on a single page and every other pdf page carries a page number and
     * nothing else (`docs/pdf.md`), which desktop Xournal++ reads by inheritance. [DesktopDocument] installs
     * the supplier at construction, so the whole window — the canvas, the thumbnails, the PDF and SVG
     * exporters — resolves those pages through one rule instead of each of them guessing.
     */
    var pdfReference: () -> Background.Pdf? = { null }

    /**
     * The image behind [background], or null when there is nothing to draw (or nothing to find).
     *
     * **Rasterised outside the lock.** The canvas asks on the window's thread while the page list's
     * thumbnails ask from their own lane, and a page's rasterisation is everything a picture costs — the PDF
     * load, the render at [PDF_SCALE], the PNG, the Skia decode. A monitor held across it stopped the canvas
     * from painting at all, so the window looked hung for as long as the sidebar's page took. Only the map is
     * guarded; two lanes racing for one key do that work twice and agree on the answer.
     */
    fun image(background: Background): ImageBitmap? {
        // A pdf page with no file of its own is drawn from the document's reference; with neither, there
        // is nothing to draw and the page keeps its paper.
        val painted = when (background) {
            is Background.Pdf -> PdfPageReferences.resolve(background, pdfReference()) ?: return null
            else -> background
        }
        val key = keyOf(painted) ?: return null
        synchronized(this) { if (cache.containsKey(key)) return cache[key] }
        val bitmap = runCatching { render(painted) }.getOrNull()
        synchronized(this) { putLru(key, bitmap) }
        return bitmap
    }

    /**
     * Non-blocking lookup for interactive canvas drawing: returns a cached [ImageBitmap] immediately
     * or enqueues background rasterization and increments [version] when ready.
     */
    fun imageOrPrefetch(background: Background): ImageBitmap? {
        val painted = when (background) {
            is Background.Pdf -> PdfPageReferences.resolve(background, pdfReference()) ?: return null
            else -> background
        }
        val key = keyOf(painted) ?: return null
        synchronized(this) { if (cache.containsKey(key)) return cache[key] }
        val enqueue = synchronized(inFlight) { inFlight.add(key) }
        if (enqueue) {
            executor.execute {
                try {
                    val bitmap = runCatching { render(painted) }.getOrNull()
                    synchronized(this) { putLru(key, bitmap) }
                    version += 1
                } finally {
                    synchronized(inFlight) { inFlight.remove(key) }
                }
            }
        }
        return null
    }

    private fun putLru(key: String, bitmap: ImageBitmap?) {
        if (cache.size >= CACHE_MAX && !cache.containsKey(key)) {
            cache.remove(cache.keys.first())
        }
        cache[key] = bitmap
    }

    /**
     * The reference [background] resolves to — its own, or the document's one ([pdfReference]) — or null
     * when the page names no PDF and the document has none to lend it.
     *
     * The PDF export keys the sources it has open by this, so a document is opened **once** however many
     * pages name it, and a page that borrows the document's reference resolves to the same key.
     */
    fun reference(background: Background.Pdf): Background.Pdf? =
        PdfPageReferences.resolve(background, pdfReference())

    /**
     * The **source document** behind a `pdf` background, open and ready to import a page from — the caller
     * must close it, and must keep it open until every page imported from it has been written.
     *
     * The PDF export needs the page itself, not a picture of it: importing it verbatim keeps its vectors,
     * its text layer and its original size, which is the one thing a raster flatten loses. Resolution is
     * the same bundle-first-then-file one [image] uses, so a document whose bundled page still opens
     * exports exactly what it showed. An unreadable source answers null and the export falls back to the
     * rendered picture.
     */
    fun sourcePdf(background: Background.Pdf): PDDocument? {
        val source = reference(background) ?: return null
        val bytes = bytesOf(source.bundledAsset, source.domain, source.filename)
        if (bytes != null) return runCatching { PDDocument.load(ByteArrayInputStream(bytes)) }.getOrNull()
        val file = fileOf(source.domain, source.filename) ?: return null
        return runCatching { PDDocument.load(file) }.getOrNull()
    }

    /**
     * The PDF's **bytes** for [background], resolved the same bundle-first way [image] draws it.
     *
     * What a text/index feature needs is not the picture but the file itself: the window's PDF text
     * layer and outline are read through the JVM's PDFBox, off the UI thread, from exactly the bytes the
     * sheet was rasterised from. A page whose reference resolves to nothing answers null.
     */
    fun pdfBytes(background: Background.Pdf): ByteArray? {
        val source = reference(background) ?: return null
        return bytesOf(source.bundledAsset, source.domain, source.filename)
    }

    private fun keyOf(background: Background): String? = when (background) {
        is Background.Pixmap -> "pixmap|${background.bundledAsset}|${background.domain}|${background.filename}"
        is Background.Pdf -> "pdf|${background.bundledAsset}|${background.domain}|${background.filename}|${background.pageNo}"
        is Background.Solid -> null
    }

    private fun render(background: Background): ImageBitmap? = when (background) {
        is Background.Pixmap -> bytesOf(background.bundledAsset, background.domain, background.filename)
            ?.let(::decode)

        is Background.Pdf -> renderPdf(background)

        is Background.Solid -> null
    }

    private fun renderPdf(background: Background.Pdf): ImageBitmap? {
        val entry = acquirePdfDoc(background) ?: return null
        try {
            return synchronized(entry.renderLock) {
                renderPdfPage(entry, background.pageNo)
            }
        } finally {
            entry.release()
        }
    }

    private fun acquirePdfDoc(source: Background.Pdf): CachedPdfDoc? {
        val docKey = "pdfdoc|${source.bundledAsset}|${source.domain}|${source.filename}"
        synchronized(pdfDocs) {
            pdfDocs[docKey]?.let {
                it.refs += 1
                return it
            }
        }
        val raw = bytesOf(source.bundledAsset, source.domain, source.filename)
        val loaded = when {
            raw != null -> runCatching { PDDocument.load(ByteArrayInputStream(raw)) }.getOrNull()
            else -> fileOf(source.domain, source.filename)?.let { runCatching { PDDocument.load(it) }.getOrNull() }
        } ?: return null
        var toClose: PDDocument? = null
        val chosen = synchronized(pdfDocs) {
            val existing = pdfDocs[docKey]
            if (existing != null) {
                existing.refs += 1
                toClose = loaded
                existing
            } else {
                if (pdfDocs.size >= PDF_DOC_CACHE_MAX) {
                    val eldest = pdfDocs.remove(pdfDocs.keys.first())
                    if (eldest != null) {
                        synchronized(eldest) {
                            eldest.evicted = true
                            if (eldest.refs <= 0) toClose = eldest.doc
                        }
                    }
                }
                CachedPdfDoc(loaded).also {
                    it.refs = 1
                    pdfDocs[docKey] = it
                }
            }
        }
        toClose?.let { runCatching { it.close() } }
        return chosen
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

    private fun renderPdfPage(entry: CachedPdfDoc, pageNo: Int): ImageBitmap? {
        val pdf = entry.doc
        if (pdf.numberOfPages == 0) return null
        val index = pageNo.coerceIn(0, pdf.numberOfPages - 1)
        if (entry.repairedPages.add(index)) {
            val page = pdf.getPage(index)
            runCatching { PdfFontRepair.repair(pdf, page) }
        }
        return PdfRenderGate.page {
            PDFRenderer(pdf).renderImage(index, PDF_SCALE).toComposeImageBitmap()
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()

    private companion object {
        /** 2× the PDF's natural size — about 144 dpi, the Android page cache's trade-off. */
        const val PDF_SCALE = 2f

        /** LRU capacity for page-sized backdrop bitmaps across scrolling and thumbnails. */
        const val CACHE_MAX = 24

        /** Open PDDocument cache so page flips avoid reparsing the entire PDF file. */
        const val PDF_DOC_CACHE_MAX = 3
    }
}

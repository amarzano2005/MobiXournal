package com.mobixournal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** An export owns its document and decoder resources, never the live editor's View or painters. */
internal class DocumentExportSnapshot(
    private val document: Document,
    private val pageIndex: Int,
    private val pdf: File?,
    private val images: Map<String, File>,
    private val backgrounds: BackgroundResolver?,
    private val openExternal: (String) -> InputStream?,
) {
    fun writePdf(out: OutputStream) {
        imageCache().use { pictures -> PdfExporter(pdfFile(), pictures).export(document, out) }
    }

    fun writeSvg(out: OutputStream) {
        out.write(PageSvgWriter.write(document.pages[pageIndex]).toByteArray(Charsets.UTF_8))
    }

    fun writePng(out: OutputStream) {
        val page = document.pages[pageIndex]
        require(page.width > 0 && page.height > 0) { "The page has no drawable area" }
        val scale = 2f
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val elements = ElementRenderer()
        try {
            imageCache().use { pictures ->
                val pdfCache = (page.background as? Background.Pdf)?.let { pdf ?: backgrounds?.file(it) }
                    ?.let { PdfPageCache(it) }
                try {
                    val image = when (val bg = page.background) {
                        is Background.Pdf -> pdfCache?.render(bg.pageNo, width)
                        is Background.Pixmap -> pictures.render(bg.filename, width)
                        else -> null
                    }
                    val canvas = Canvas(bitmap)
                    val box = PageBox(index = 0, topPx = 0f, leftPx = 0f,
                        widthPx = width.toFloat(), heightPx = height.toFloat(), scale = scale,
                        source = PageSource.of(listOf(page)))
                    BackgroundRenderer.draw(canvas, box, 0f, 0f, image)
                    PageRenderer.drawElements(canvas, page, scale, 0f, 0f, StrokePainter(), elements)
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "The PNG could not be encoded" }
                } finally { pdfCache?.close() }
            }
        } finally {
            elements.close()
            bitmap.recycle()
        }
    }

    private fun pdfFile(): File? = pdf ?: document.pages.asSequence()
        .map { it.background }.filterIsInstance<Background.Pdf>()
        .mapNotNull { backgrounds?.file(it) }.firstOrNull()

    private fun imageCache() = ImageBackgroundCache(open = { reference ->
        (backgrounds?.images?.get(reference) ?: images[reference])?.takeIf(File::isFile)?.inputStream()
            ?: openExternal(reference)
    })
}

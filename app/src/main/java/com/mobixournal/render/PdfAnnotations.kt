package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import kotlin.math.max
import kotlin.math.min

/**
 * The **interoperable annotation layer** of an exported PDF: real `/Highlight`, `/Ink` and `/Text`
 * dictionaries beside the flattened drawing, so a reader such as Acrobat or Okular shows the marks in
 * its own annotation list (and can hide, reply to or export them) instead of only seeing paint.
 *
 * The page's ink is still flattened into the exported page's own picture ([PdfExporter]): an
 * annotation a viewer cannot render must never be the only copy of a stroke. Adding these
 * dictionaries is therefore additive — the visual result of the export does not change.
 *
 * Mapping is the same arithmetic the overlay uses: page points are flipped into PDF user space by
 * [PdfPageTransform], then pre-multiplied by [PdfOverlayMatrix] for a rotated source page. Quad points
 * use the PDF order — upper-left, upper-right, lower-left, lower-right — and an ink stroke is one
 * array of alternating x/y coordinates.
 *
 * A highlighter over a born-digital PDF's text layer becomes `/Highlight` quads on the words it
 * covers; a highlighter over no text, or a pen stroke, is `/Ink`; a text box is a `/Text` note. That
 * is what the marks *are*, not an approximation of them.
 */
internal object PdfAnnotations {

    /** The ink's own colour as the annotation colour, in PDF's 0..1 RGB. */
    fun add(
        pdPage: PDPage,
        page: Page,
        backgroundPdfPage: Int?,
        textIndex: PdfTextIndex?,
        t: PdfPageTransform,
        matrix: PdfOverlayMatrix?,
    ): Int {
        val point = PointMapper(t, matrix)
        val words = backgroundPdfPage?.let { textIndex?.words(it) }.orEmpty()
        val annotations = pdPage.annotations
        var added = 0
        for (layer in page.layers) {
            for (element in layer.elements) {
                val annotation = when (element) {
                    is Stroke -> annotationFor(element, words, point)
                    is TextElement -> textNote(element, point)
                    else -> null
                } ?: continue
                annotations.add(annotation)
                added++
            }
        }
        return added
    }

    private fun annotationFor(stroke: Stroke, words: List<PdfWord>, point: PointMapper): PDAnnotation? {
        if (stroke.points.size < 2) return null
        val bounds = ElementBounds.of(stroke)
        return when (stroke.tool) {
            Tool.HIGHLIGHTER -> {
                val covered = words.filter { word ->
                    word.left <= bounds.right && word.right >= bounds.left &&
                        word.top <= bounds.bottom && word.bottom >= bounds.top
                }
                // Only text can be *highlighted*; a band over a picture or a margin is handwriting.
                if (covered.isEmpty()) ink(stroke, point) else highlight(covered, stroke, point)
            }
            Tool.PEN -> ink(stroke, point)
            Tool.ERASER -> null
        }
    }

    private fun highlight(words: List<PdfWord>, stroke: Stroke, point: PointMapper): PDAnnotation {
        val left = words.minOf { it.left } - 1.0
        val right = words.maxOf { it.right } + 1.0
        val top = words.minOf { it.top } - 1.5
        val bottom = words.maxOf { it.bottom } + 1.5
        val (x1, y1) = point.map(left, top)
        val (x2, y2) = point.map(right, top)
        val (x3, y3) = point.map(left, bottom)
        val (x4, y4) = point.map(right, bottom)
        val annotation = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
        annotation.quadPoints = floatArrayOf(x1, y1, x2, y2, x3, y3, x4, y4)
        annotation.rectangle = rectOf(listOf(x1, x2, x3, x4), listOf(y1, y2, y3, y4))
        annotation.color = colour(stroke.color)
        annotation.contents = "Highlight"
        annotation.cosObject.setFloat(COSName.CA, 0.47f)
        return annotation
    }

    private fun ink(stroke: Stroke, point: PointMapper): PDAnnotation {
        val coordinates = FloatArray(stroke.points.size * 2)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        stroke.points.forEachIndexed { index, p ->
            val (x, y) = point.map(p.x, p.y)
            coordinates[index * 2] = x
            coordinates[index * 2 + 1] = y
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
        val annotation = PDAnnotationTextMarkup(PDAnnotationMarkup.SUB_TYPE_INK)
        annotation.inkList = arrayOf(coordinates)
        annotation.rectangle = PDRectangle(minX, minY, (maxX - minX).coerceAtLeast(1f), (maxY - minY).coerceAtLeast(1f))
        annotation.color = colour(stroke.color)
        annotation.contents = "Handwriting"
        return annotation
    }

    private fun textNote(text: TextElement, point: PointMapper): PDAnnotation {
        val (x, y) = point.map(text.x, text.y)
        val annotation = PDAnnotationText()
        annotation.name = PDAnnotationText.NAME_NOTE
        annotation.rectangle = PDRectangle(x, y - 20f, 20f, 20f)
        annotation.color = colour(text.color)
        annotation.contents = text.content
        return annotation
    }

    private fun rectOf(xs: List<Float>, ys: List<Float>): PDRectangle {
        val left = xs.min()
        val bottom = ys.min()
        return PDRectangle(left, bottom, (xs.max() - left).coerceAtLeast(1f), (ys.max() - bottom).coerceAtLeast(1f))
    }

    private fun colour(argb: Int): PDColor = PDColor(
        floatArrayOf(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        ),
        PDDeviceRGB.INSTANCE,
    )

    /** Visual page point → PDF user space, including a rotated source page's overlay matrix. */
    private class PointMapper(
        private val t: PdfPageTransform,
        private val matrix: PdfOverlayMatrix?,
    ) {
        fun map(x: Double, y: Double): Pair<Float, Float> {
            val px = t.x(x)
            val py = t.y(y)
            val m = matrix ?: return px to py
            return (m.a * px + m.c * py + m.e) to (m.b * px + m.d * py + m.f)
        }
    }
}

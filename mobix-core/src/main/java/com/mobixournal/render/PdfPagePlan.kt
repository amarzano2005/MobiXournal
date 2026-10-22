package com.mobixournal.render

import com.mobixournal.format.FontDescription
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool

/**
 * A page's **PDF vector plan**: the ordered ops that put a page onto a PDF content stream, described
 * without a PDF library.
 *
 * The drawing itself is the shared part — the pressure taper, the highlighter's flat translucent band,
 * the fill under a closed stroke, the text baseline arithmetic, a living entity compiled through
 * [LivingInk] — and it must not be written twice. What is *not* shared is the library: the Android app
 * carries `com.tom_roush.pdfbox` (the port), the desktop carries `org.apache.pdfbox` 3. So the page is
 * compiled here, in page coordinates flipped into PDF user space by [PdfPageTransform], and each shell
 * replays the ops through its own emitter. One geometry, two writers.
 *
 * Every coordinate is already in PDF user space and in points, every colour is ARGB, every alpha is
 * 0..1: an emitter maps ops to calls and computes nothing except decoding an image's pixels (which is
 * the one thing that cannot be platform-free — `javax.imageio` does not exist on Android).
 */
sealed interface PdfOp

/** A point of the page in PDF user space (origin bottom-left, y-up, points). */
data class PdfPoint(val x: Float, val y: Float)

/** RGB families the export can name without a font file: the base-14 sets each shell carries. */
enum class PdfFamily { SANS, SERIF, MONO }

/** One face of the base-14 families: [family], with its bold and italic member. */
data class PdfFontFace(val family: PdfFamily, val bold: Boolean, val italic: Boolean) {
    companion object {
        /**
         * The face a `.xopp` font description asks for. One mapping for both shells: a document must not
         * come out Helvetica in the app's PDF and Times on the desktop's.
         */
        fun of(font: String): PdfFontFace {
            val fd = FontDescription.parse(font)
            val family = when (fd.family.lowercase()) {
                "serif", "times", "times new roman" -> PdfFamily.SERIF
                "monospace", "mono", "courier", "courier new" -> PdfFamily.MONO
                else -> PdfFamily.SANS
            }
            return PdfFontFace(family, fd.bold, fd.italic)
        }
    }
}

/**
 * Flood the inside of a closed stroke (a shaded area, a filled figure) — drawn *under* its outline.
 *
 * [multiply] keeps the highlighter's flatten: the canvas and the two PDF writers all blend it the same
 * way, so a highlight over a diagram looks the same in the export as on screen.
 */
data class PdfFill(
    val points: List<PdfPoint>,
    val colour: Int,
    val alpha: Float,
    val multiply: Boolean,
) : PdfOp

/**
 * A stroked polyline (or closed outline) in [points].
 *
 * [widths] is the pen: one width per segment, so the line tracks pressure between vertices. Empty means
 * a constant [width] — the styled line and the highlighter band, which must be **one** path so a
 * translucent band does not bead at its own overlaps.
 */
data class PdfPath(
    val points: List<PdfPoint>,
    val colour: Int,
    val alpha: Float,
    val multiply: Boolean,
    val width: Double,
    val widths: List<Double> = emptyList(),
    val dash: List<Float>? = null,
    val close: Boolean = false,
) : PdfOp

/**
 * A block of text at the baseline of its first line.
 *
 * [lines] are already split and sanitised (the base-14 fonts only encode WinAnsi, so anything above
 * U+00FF becomes `?` here rather than throwing inside an emitter), and [y] is that first baseline in
 * PDF space — the emitter draws line by line, one [lineHeight] apart.
 */
data class PdfText(
    val lines: List<String>,
    val face: PdfFontFace,
    val size: Double,
    val colour: Int,
    val x: Float,
    val y: Float,
    val lineHeight: Double,
) : PdfOp

/**
 * An image to place, as the bytes the document carries.
 *
 * Decoding stays in the emitter (Android decodes with `BitmapFactory`, the desktop with a JVM decoder),
 * because that is the one step with no platform-free implementation; [width]/[height] are the box the
 * image is drawn into, in points.
 */
data class PdfImage(
    val data: ByteArray,
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float,
) : PdfOp

/**
 * Compiles a page into its ops. Pure: no canvas, no PDF library, no I/O.
 *
 * The walk mirrors what a page *is* — layers in order, elements in order, a living entity replaced by
 * the ink its own compiler produces — so the plan cannot disagree with the canvas about what the page
 * holds. An element the model does not interpret ([RawElement]) contributes nothing, exactly as on
 * screen.
 */
object PdfPagePlan {

    /** The base-14 ascent the text baseline arithmetic uses, as a fraction of the font size. */
    const val ASCENT_RATIO = 0.75

    /** Line advance, as a fraction of the font size. */
    const val LINE_HEIGHT_RATIO = 1.2

    /** A `<teximage>` is drawn as its source: monospace, sized from the box, clamped to be readable. */
    private const val TEX_MIN_SIZE = 6.0
    private const val TEX_MAX_SIZE = 14.0

    fun of(page: Page, transform: PdfPageTransform): List<PdfOp> {
        val ops = mutableListOf<PdfOp>()
        for (layer in page.layers) {
            for (element in layer.elements) {
                // One unencodable element must not take the page with it: the export states what it
                // could not draw elsewhere, and skips it here the way the canvas skips it.
                runCatching { plan(element, transform, ops) }
            }
        }
        return ops
    }

    private fun plan(element: Element, t: PdfPageTransform, ops: MutableList<PdfOp>) {
        when (element) {
            is Stroke -> stroke(element, t, ops)
            is TextElement -> ops += text(element, t)
            is ImageElement -> ops += image(element, t)
            is TexImageElement -> tex(element, t)?.let { ops += it }
            is RawElement -> Unit
            is TableElement, is PlotElement, is ParametricShapeElement, is CircuitElement ->
                LivingInk.of(element).elements.forEach { plan(it, t, ops) }
        }
    }

    private fun stroke(s: Stroke, t: PdfPageTransform, ops: MutableList<PdfOp>) {
        if (s.points.size < 2) return
        val multiply = s.tool == Tool.HIGHLIGHTER
        s.fill?.let { fill ->
            ops += PdfFill(
                points = s.points.map { PdfPoint(t.x(it.x), t.y(it.y)) },
                colour = s.color,
                alpha = (fill and 0xFF) / 255f,
                multiply = multiply,
            )
        }
        val argb = InkGeometry.renderColor(s.tool, s.color)
        val mode = InkGeometry.renderMode(s.tool, s.lineStyle)
        val width = if (mode != InkGeometry.RenderMode.Pressure) InkGeometry.bandWidth(s.points) else 0.0
        val dash = InkGeometry.dashIntervalsPt(s.lineStyle, width)?.toList()
        ops += PdfPath(
            points = s.points.map { PdfPoint(t.x(it.x), t.y(it.y)) },
            colour = argb,
            alpha = argb.alpha / 255f,
            multiply = multiply,
            width = width,
            widths = if (mode == InkGeometry.RenderMode.Pressure) segmentWidths(s) else emptyList(),
            dash = dash,
        )
    }

    /** The pen's per-segment widths: the midpoint of the two vertices' widths, as the canvas draws it. */
    private fun segmentWidths(s: Stroke): List<Double> =
        (1 until s.points.size).map { i ->
            (s.points[i - 1].width + s.points[i].width) / 2.0
        }

    private fun text(tx: TextElement, t: PdfPageTransform) = PdfText(
        lines = TextBlock.lines(tx.content).map(::winAnsi),
        face = PdfFontFace.of(tx.font),
        size = tx.size,
        colour = tx.color,
        x = t.x(tx.x),
        y = t.y(tx.y + tx.size * ASCENT_RATIO),
        lineHeight = tx.size * LINE_HEIGHT_RATIO,
    )

    private fun image(img: ImageElement, t: PdfPageTransform) = PdfImage(
        data = img.data,
        left = t.x(img.left),
        bottom = t.y(img.bottom),
        width = (img.right - img.left).toFloat(),
        height = (img.bottom - img.top).toFloat(),
    )

    /** A `<teximage>` with no source has nothing to say; the exporter used to skip it silently. */
    private fun tex(tex: TexImageElement, t: PdfPageTransform): PdfText? {
        if (tex.latex.isBlank()) return null
        val size = ((tex.bottom - tex.top) * 0.5).coerceIn(TEX_MIN_SIZE, TEX_MAX_SIZE)
        return PdfText(
            lines = listOf(winAnsi(tex.latex)),
            face = PdfFontFace(PdfFamily.MONO, bold = false, italic = false),
            size = size,
            colour = tex.color,
            x = t.x(tex.left),
            y = t.y(tex.top + size),
            lineHeight = size * LINE_HEIGHT_RATIO,
        )
    }

    /**
     * The base-14 fonts only encode WinAnsi, and `showText` **throws** on anything outside it. Dropping
     * the character here is what keeps one stray emoji from failing an export while the same page draws
     * fine on screen.
     */
    internal fun winAnsi(s: String): String =
        buildString(s.length) { for (c in s) append(if (c.code in 0x20..0xFF) c else '?') }
}

/** The ARGB alpha channel of a packed colour, as the emitters need it. */
private val Int.alpha: Int get() = (this ushr 24) and 0xFF

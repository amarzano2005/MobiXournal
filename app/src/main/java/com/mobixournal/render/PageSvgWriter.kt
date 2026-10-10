package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import java.util.Base64

/**
 * Serialises a single [Page] to an **SVG** document — real vector output for the "Export page as SVG"
 * action, the counterpart of [PageThumbnail]'s raster preview.
 *
 * Everything the page holds is re-emitted as vector geometry: the sheet fill and its ruling —
 * including an isometric page's triangular mesh ([BackgroundGrid.isometric]) — as
 * `<rect>`/`<line>`/`<circle>`, strokes as `<polyline>` (round caps and joins, the stroke's own colour and
 * average width, its alpha as `stroke-opacity`), text as `<text>`, and images/LaTeX as `<image>` with
 * the element's own encoded bytes inline as a `data:` URL. Elements we don't model ([Element.RawElement])
 * are skipped — SVG has no faithful translation for a shape we never interpreted.
 *
 * Kept Android-free so it is unit-testable on the JVM: it reads only the document model and the pure
 * [BackgroundGrid] geometry.
 */
object PageSvgWriter {

    fun write(page: Page): String {
        val w = page.width
        val h = page.height
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
            .append("width=\"").append(num(w)).append("\" height=\"").append(num(h))
            .append("\" viewBox=\"0 0 ").append(num(w)).append(' ').append(num(h)).append("\">\n")

        val solid = page.background as? Background.Solid
        val bg = solid?.color ?: WHITE
        sb.append("  <rect x=\"0\" y=\"0\" width=\"").append(num(w)).append("\" height=\"").append(num(h))
            .append("\" fill=\"").append(hex(bg)).append("\"/>\n")

        ruling(sb, page, solid)

        for (layer in page.layers) {
            for (element in layer.elements) emit(sb, element)
        }

        sb.append("</svg>\n")
        return sb.toString()
    }

    /**
     * The background ruling (lined / ruled / graph / dotted) as SVG geometry, matching
     * [BackgroundRenderer] — the page's own `<background config=…>` spacing, margin and line widths
     * included, so an exported sheet rules exactly like the one on screen.
     */
    private fun ruling(sb: StringBuilder, page: Page, solid: Background.Solid?) {
        val style = solid?.style ?: return
        val ruling = BackgroundRuling.parse(solid?.config)
        val widthPt = BackgroundRulings.lineWidthPt(ruling)
        val boldInterval = BackgroundRulings.boldInterval(ruling)
        val boldWidthPt = BackgroundRulings.boldWidthPt(ruling, widthPt)
        fun widthOf(offsetPt: Double, spacing: Double): Double {
            val index = if (spacing > 0.0) Math.round(offsetPt / spacing).toInt() else 0
            return if (BackgroundRulings.isBold(index, boldInterval)) boldWidthPt else widthPt
        }
        when (style) {
            "lined", "ruled" -> {
                val colour = opaque(BackgroundGrid.LINED_RGB)
                val spacing = BackgroundRulings.spacingPt(style, ruling)
                for (y in BackgroundGrid.lines(page.height, spacing)) {
                    line(sb, 0.0, y, page.width, y, colour, widthOf(y, spacing))
                }
                if (style == "ruled") {
                    val marginPt = BackgroundRulings.marginPt(ruling) ?: BackgroundGrid.MARGIN_PT
                    line(
                        sb, marginPt, 0.0, marginPt, page.height, opaque(BackgroundGrid.MARGIN_RGB),
                        BackgroundRulings.lineWidthPt(ruling, 1.5),
                    )
                }
            }
            "graph" -> {
                val colour = opaque(BackgroundGrid.GRAPH_RGB)
                val spacing = BackgroundRulings.spacingPt("graph", ruling)
                val m = BackgroundRulings.marginPt(ruling) ?: 0.0
                val x1 = (page.width - m).coerceAtLeast(m)
                val y1 = (page.height - m).coerceAtLeast(m)
                for (y in BackgroundGrid.lines(y1 - m, spacing)) {
                    val abs = m + y
                    line(sb, m, abs, x1, abs, colour, widthOf(abs, spacing))
                }
                for (x in BackgroundGrid.lines(x1 - m, spacing)) {
                    val abs = m + x
                    line(sb, abs, m, abs, y1, colour, widthOf(abs, spacing))
                }
            }
            "isograph", "isodotted" -> {
                val colour = opaque(BackgroundGrid.GRAPH_RGB)
                val size = BackgroundRulings.spacingPt(style, ruling)
                val width = BackgroundRulings.lineWidthPt(ruling)
                for (seg in BackgroundGrid.isometric(page.width, page.height, size)) {
                    line(sb, seg[0], seg[1], seg[2], seg[3], colour, width)
                }
            }
            "dotted" -> {
                val colour = opaque(BackgroundGrid.DOT_RGB)
                val spacing = BackgroundRulings.spacingPt("dotted", ruling)
                val m = BackgroundRulings.marginPt(ruling) ?: 0.0
                val x1 = (page.width - m).coerceAtLeast(m)
                val y1 = (page.height - m).coerceAtLeast(m)
                val r = 1.0
                for (y in BackgroundGrid.lines(y1 - m, spacing)) {
                    for (x in BackgroundGrid.lines(x1 - m, spacing)) {
                        dot(sb, m + x, m + y, r, colour)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun dot(sb: StringBuilder, cx: Double, cy: Double, r: Double, colour: Int) {
        sb.append("  <circle cx=\"").append(num(cx)).append("\" cy=\"").append(num(cy))
            .append("\" r=\"").append(num(r)).append("\" fill=\"").append(hex(colour)).append("\"/>\n")
    }

    private fun line(sb: StringBuilder, x1: Double, y1: Double, x2: Double, y2: Double, colour: Int, width: Double) {
        sb.append("  <line x1=\"").append(num(x1)).append("\" y1=\"").append(num(y1))
            .append("\" x2=\"").append(num(x2)).append("\" y2=\"").append(num(y2))
            .append("\" stroke=\"").append(hex(colour)).append("\" stroke-width=\"").append(num(width))
            .append("\" opacity=\"").append(num(alphaOf(colour) / 255.0)).append("\"/>\n")
    }

    private fun emit(sb: StringBuilder, element: Element) {
        when (element) {
            is Stroke -> emitStroke(sb, element)
            is TextElement -> emitText(sb, element)
            is ImageElement -> emitImage(sb, element.left, element.top, element.right - element.left, element.bottom - element.top, element.data)
            is TexImageElement -> element.data?.let {
                emitImage(sb, element.left, element.top, element.right - element.left, element.bottom - element.top, it)
            }
            else -> Unit
        }
    }

    private fun emitStroke(sb: StringBuilder, stroke: Stroke) {
        if (stroke.points.size < 2) return
        val width = stroke.points.sumOf { it.width } / stroke.points.size
        sb.append("  <polyline fill=\"none\" stroke=\"").append(hex(stroke.color))
            .append("\" stroke-width=\"").append(num(width))
            .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"")
        val alpha = alphaOf(stroke.color)
        if (alpha < 255) sb.append(" stroke-opacity=\"").append(num(alpha / 255.0)).append("\"")
        sb.append(" points=\"")
        stroke.points.forEachIndexed { i, pt ->
            if (i > 0) sb.append(' ')
            sb.append(num(pt.x)).append(',').append(num(pt.y))
        }
        sb.append("\"/>\n")
    }

    private fun emitText(sb: StringBuilder, text: TextElement) {
        // SVG's y is the text baseline; the model's is the box top, so add one ascent (~size).
        val baseline = text.y + text.size
        sb.append("  <text x=\"").append(num(text.x)).append("\" y=\"").append(num(baseline))
            .append("\" font-family=\"").append(escapeAttr(text.font))
            .append("\" font-size=\"").append(num(text.size))
            .append("\" fill=\"").append(hex(text.color)).append("\">")
            .append(escapeText(text.content)).append("</text>\n")
    }

    private fun emitImage(sb: StringBuilder, x: Double, y: Double, w: Double, h: Double, data: ByteArray) {
        if (data.isEmpty()) return
        val mime = mimeOf(data)
        val b64 = Base64.getEncoder().encodeToString(data)
        sb.append("  <image x=\"").append(num(x)).append("\" y=\"").append(num(y))
            .append("\" width=\"").append(num(w)).append("\" height=\"").append(num(h))
            .append("\" xlink:href=\"data:").append(mime).append(";base64,").append(b64).append("\"/>\n")
    }

    /** PNG or JPEG, from the byte signature; defaults to PNG (the format the editor writes). */
    private fun mimeOf(data: ByteArray): String = when {
        data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "image/jpeg"
        else -> "image/png"
    }

    private fun opaque(rgb: Int): Int = 0xFF000000.toInt() or rgb

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    private fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    private fun num(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r) && Math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    private fun escapeText(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttr(s: String): String =
        escapeText(s).replace("\"", "&quot;")

    private const val WHITE = 0xFFFFFFFF.toInt()
}

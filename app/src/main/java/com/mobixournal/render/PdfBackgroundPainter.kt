package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page

/**
 * Draws a fresh (non-PDF) page background — the sheet fill plus its ruling — onto a PDFBox content
 * stream as vector primitives, mirroring [BackgroundRenderer] at scale 1. Used only when a page has
 * no preserved PDF page behind it (a `solid` background, or a `pdf`/`pixmap` whose source isn't
 * available); PDF-backed pages keep their original vector content instead. Line spacings, colours
 * and the ruling geometry ([BackgroundGrid] plus the page's own `<background config=…>` parameters)
 * match the editor so the flatten looks the same.
 */
object PdfBackgroundPainter {

    // Shared with the editor via [BackgroundRulings] so the flatten can't drift from what's on screen.
    private const val MARGIN_PT = BackgroundGrid.MARGIN_PT
    private const val MARGIN_WIDTH_PT = 1.5
    private const val DOT_HALF_PT = 1.0f

    fun draw(cs: PDPageContentStream, page: Page, t: PdfPageTransform) {
        val solid = page.background as? Background.Solid
        fill(cs, page, t, solid?.color ?: WHITE)
        val style = solid?.style ?: return
        val ruling = BackgroundRuling.parse(solid.config)
        when (style) {
            "lined" -> horizontals(cs, page, t, style, ruling)
            "ruled" -> {
                horizontals(cs, page, t, style, ruling)
                marginLine(cs, page, t, ruling)
            }
            "graph" -> grid(cs, page, t, ruling)
            "dotted" -> dots(cs, page, t, ruling)
            "isograph", "isodotted" -> isometric(cs, page, t, style, ruling)
            else -> Unit // "plain", unknown, or non-solid: bare sheet
        }
    }

    private fun fill(cs: PDPageContentStream, page: Page, t: PdfPageTransform, color: Int) {
        cs.setNonStrokingArgb(color)
        cs.addRect(t.x(0.0), t.y(page.height), page.width.toFloat(), page.height.toFloat())
        cs.fill()
    }

    private fun horizontals(
        cs: PDPageContentStream,
        page: Page,
        t: PdfPageTransform,
        style: String,
        ruling: BackgroundRuling,
    ) {
        cs.setStrokingArgb(BackgroundGrid.LINED_RGB)
        val widthPt = BackgroundRulings.lineWidthPt(ruling)
        val boldInterval = BackgroundRulings.boldInterval(ruling)
        val boldWidthPt = BackgroundRulings.boldWidthPt(ruling, widthPt)
        for ((i, y) in BackgroundGrid.lines(
            page.height,
            BackgroundRulings.spacingPt(style, ruling),
        ).withIndex()) {
            val bold = BackgroundRulings.isBold(Math.round(y / BackgroundRulings.spacingPt(style, ruling)).toInt(), boldInterval)
            cs.setLineWidth((if (bold) boldWidthPt else widthPt).toFloat())
            cs.moveTo(t.x(0.0), t.y(y)); cs.lineTo(t.x(page.width), t.y(y)); cs.stroke()
        }
    }

    private fun marginLine(cs: PDPageContentStream, page: Page, t: PdfPageTransform, ruling: BackgroundRuling) {
        cs.setStrokingArgb(BackgroundGrid.MARGIN_RGB)
        cs.setLineWidth(BackgroundRulings.lineWidthPt(ruling, MARGIN_WIDTH_PT).toFloat())
        val xPt = BackgroundRulings.marginPt(ruling) ?: MARGIN_PT
        cs.moveTo(t.x(xPt), t.y(0.0)); cs.lineTo(t.x(xPt), t.y(page.height)); cs.stroke()
    }

    private fun grid(cs: PDPageContentStream, page: Page, t: PdfPageTransform, ruling: BackgroundRuling) {
        cs.setStrokingArgb(BackgroundGrid.GRAPH_RGB)
        val spacing = BackgroundRulings.spacingPt("graph", ruling)
        val widthPt = BackgroundRulings.lineWidthPt(ruling)
        val boldInterval = BackgroundRulings.boldInterval(ruling)
        val boldWidthPt = BackgroundRulings.boldWidthPt(ruling, widthPt)
        // A graph page's margin insets the ruled area on every side, exactly as on screen.
        val m = BackgroundRulings.marginPt(ruling) ?: 0.0
        val x1 = (page.width - m).coerceAtLeast(m)
        val y1 = (page.height - m).coerceAtLeast(m)
        for (y in BackgroundGrid.lines(y1 - m, spacing)) {
            val abs = m + y
            val bold = BackgroundRulings.isBold(Math.round(abs / spacing).toInt(), boldInterval)
            cs.setLineWidth((if (bold) boldWidthPt else widthPt).toFloat())
            cs.moveTo(t.x(m), t.y(abs)); cs.lineTo(t.x(x1), t.y(abs)); cs.stroke()
        }
        for (x in BackgroundGrid.lines(x1 - m, spacing)) {
            val abs = m + x
            val bold = BackgroundRulings.isBold(Math.round(abs / spacing).toInt(), boldInterval)
            cs.setLineWidth((if (bold) boldWidthPt else widthPt).toFloat())
            cs.moveTo(t.x(abs), t.y(m)); cs.lineTo(t.x(abs), t.y(y1)); cs.stroke()
        }
    }

    /** Desktop's isometric sheet: the ±30° mesh, from the same pure geometry the editor draws. */
    private fun isometric(
        cs: PDPageContentStream,
        page: Page,
        t: PdfPageTransform,
        style: String,
        ruling: BackgroundRuling,
    ) {
        cs.setStrokingArgb(BackgroundGrid.GRAPH_RGB)
        cs.setLineWidth(BackgroundRulings.lineWidthPt(ruling).toFloat())
        val size = BackgroundRulings.spacingPt(style, ruling)
        for (seg in BackgroundGrid.isometric(page.width, page.height, size)) {
            cs.moveTo(t.x(seg[0]), t.y(seg[1])); cs.lineTo(t.x(seg[2]), t.y(seg[3])); cs.stroke()
        }
    }

    /** Dots have no PDF primitive; approximate each with a tiny filled square at the intersection. */
    private fun dots(cs: PDPageContentStream, page: Page, t: PdfPageTransform, ruling: BackgroundRuling) {
        cs.setNonStrokingArgb(BackgroundGrid.DOT_RGB)
        val spacing = BackgroundRulings.spacingPt("dotted", ruling)
        val m = BackgroundRulings.marginPt(ruling) ?: 0.0
        val x1 = (page.width - m).coerceAtLeast(m)
        val y1 = (page.height - m).coerceAtLeast(m)
        for (y in BackgroundGrid.lines(y1 - m, spacing)) {
            for (x in BackgroundGrid.lines(x1 - m, spacing)) {
                cs.addRect(
                    t.x(m + x) - DOT_HALF_PT, t.y(m + y) - DOT_HALF_PT,
                    DOT_HALF_PT * 2, DOT_HALF_PT * 2,
                )
            }
        }
        cs.fill()
    }

    private const val WHITE = 0xFFFFFF
}

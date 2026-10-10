/**
 * **Page stationery** — the paper presets behind the background pop-up's stationery list, each one
 * expressed in the `.xopp` format rather than as an app-only effect (the project's scope rule: if it
 * can't round-trip, it doesn't ship).
 *
 * Five of the six are a **background ruling the desktop already has**, so they are written as a
 * `<background style=… config=…>` pair and reopen on Linux identically: millimetre paper (a 1 mm
 * `graph` grid with every centimetre bolded), 5 mm graph, 7 mm ruled, and 10 mm and 5 mm isometric
 * paper (`isograph`, whose `r1` is the triangle side — see [BackgroundGrid.isometric]).
 *
 * **Cornell notes** has no counterpart anywhere in Xournal++ or the file format — no desktop version
 * has a Cornell template — so it is the one preset that is **drawn**: its rules go in as ordinary
 * strokes on a layer of their own (named after the template), which round-trips like any ink, can be
 * hidden or removed by deleting the layer, and needs nothing from the format. That split is the
 * honest one: the paper templates are paper, the drawing template is drawing.
 *
 * Pure geometry and strings only — no Android types — so the presets are unit-tested on the JVM.
 */
package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint

/**
 * The stationery the background pop-up offers.
 *
 * @param label Menu label.
 */
enum class Stationery(val label: String) {
    MILLIMETRE("Millimetre paper"),
    GRAPH_5MM("5 mm graph"),
    RULED_7MM("7 mm ruled"),
    ISOMETRIC_10MM("Isometric 10 mm"),
    ISOMETRIC_5MM("Isometric 5 mm"),
    CORNELL("Cornell notes"),
}

object PageTemplates {

    /** Points per millimetre (1 pt = 25.4/72 mm), the unit every preset above is specified in. */
    const val MM_PT: Double = 72.0 / 25.4

    /** The layer the drawn presets land on, so a template is one deletable, hideable layer. */
    fun layerName(kind: Stationery): String = kind.label

    /** The `<background style=…>` a paper preset rules with, or null for a drawn one. */
    fun styleOf(kind: Stationery): String? = when (kind) {
        Stationery.MILLIMETRE, Stationery.GRAPH_5MM -> "graph"
        Stationery.RULED_7MM -> "ruled"
        Stationery.ISOMETRIC_10MM, Stationery.ISOMETRIC_5MM -> "isograph"
        Stationery.CORNELL -> null
    }

    /**
     * The `<background config=…>` a paper preset rules with, or null for a drawn one: the spacing as
     * desktop's `r1` in pt, plus a bold line every ten (a centimetre on millimetre paper).
     */
    fun configOf(kind: Stationery): String? {
        val mm = when (kind) {
            Stationery.MILLIMETRE -> 1.0
            Stationery.GRAPH_5MM -> 5.0
            Stationery.RULED_7MM -> 7.0
            Stationery.ISOMETRIC_10MM -> 10.0
            Stationery.ISOMETRIC_5MM -> 5.0
            Stationery.CORNELL -> return null
        }
        val spacing = BackgroundRuling.EMPTY
            .withPt(BackgroundRuling.KEY_SPACING, mm * MM_PT)
        // Millimetre paper is the one ruled paper that needs its centimetres marked, or it is unreadable.
        val withBold = if (kind == Stationery.MILLIMETRE) {
            spacing.withInt(BackgroundRuling.KEY_BOLD_INTERVAL, 10)
        } else {
            spacing
        }
        return withBold.text()
    }

    /** The rules a **drawn** preset puts on the page, as one stroke each. Empty for a paper preset. */
    fun strokesOf(kind: Stationery, widthPt: Double, heightPt: Double): List<List<StrokePoint>> =
        when (kind) {
            Stationery.CORNELL -> cornell(widthPt, heightPt)
            else -> emptyList()
        }

    /**
     * Cornell notes: a title band across the top, a summary band across the bottom, and the cue
     * column's vertical rule between them — the three lines that make the layout. Bands are 2 inches
     * (or a fifth of a short page), and the cue column 2.5 inches (or a third of a narrow one), so the
     * proportions survive A5 and Letter alike.
     */
    private fun cornell(widthPt: Double, heightPt: Double): List<List<StrokePoint>> {
        val band = minOf(2.0 * 72.0, heightPt / 5.0)
        val cue = minOf(2.5 * 72.0, widthPt / 3.0)
        val top = band
        val bottom = heightPt - band
        if (bottom <= top) return emptyList()
        return listOf(
            horizontal(widthPt, top),
            horizontal(widthPt, bottom),
            vertical(cue, top, bottom),
        )
    }

    private fun horizontal(widthPt: Double, y: Double): List<StrokePoint> =
        listOf(StrokePoint(0.0, y, RULE_WIDTH_PT), StrokePoint(widthPt, y, RULE_WIDTH_PT))

    private fun vertical(x: Double, topPt: Double, bottomPt: Double): List<StrokePoint> =
        listOf(StrokePoint(x, topPt, RULE_WIDTH_PT), StrokePoint(x, bottomPt, RULE_WIDTH_PT))

    /** Stroke width of a template's rules, in pt — a hairline, like the ruling it stands in for. */
    const val RULE_WIDTH_PT: Double = 1.0
}

/**
 * The **integrated value** of the dimension tool: the text box the measurement writes into the gap
 * of the dimension line, once the drag is released.
 *
 * The split is deliberate. [measurementText] is pure — length in pt in, the label (in millimetres,
 * the unit engineering drawings are annotated in) out — so the arithmetic and the rounding are
 * unit-tested on the JVM. [DrawingSurfaceView.appendDimensionLabel] is the Android half: it takes the
 * committed stroke's own endpoints, measures the label with a real [Paint] so the text can be
 * **centred** in the gap, and appends a [TextElement] on the layer the line went to.
 *
 * The label is an ordinary `<text>` element, so the measurement is editable (and deletable) like any
 * other text box and round-trips through the `.xopp` file exactly as the desktop reads it.
 */
package com.mobixournal.render

import android.graphics.Paint
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import java.util.Locale
import kotlin.math.hypot

/** Font size of the measurement label, in pt — the same size the text tool opens at. */
private const val LABEL_SIZE_PT = 10.0

/** How far above the dimension line's midpoint the label sits, in pt. */
private const val LABEL_LIFT_PT = 4.0

/** Point-per-millimetre: 1 pt = 25.4/72 mm. */
private const val MM_PER_PT = 25.4 / 72.0

/** Shorter drags than this (pt) are a stray tap, not a measurement. */
private const val MIN_MEASURED_PT = 1.0

/** The font description the label is authored with — the plain family, as the text tool writes. */
private const val LABEL_FONT = "Sans"

/** Reused for measuring the label; [measurementText] and the placement both run on the UI thread. */
private val measurer = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = LABEL_SIZE_PT.toFloat() }

/** Pure half of the label: the measured length in pt rendered in millimetres, or null when too short. */
internal fun measurementText(lengthPt: Double): String? {
    if (lengthPt < MIN_MEASURED_PT) return null
    // A tenth of a millimetre is finer than any stylus places a point; more digits would be noise.
    return String.format(Locale.US, "%.1f mm", lengthPt * MM_PER_PT)
}

/** The measurement label centred above the midpoint of the segment (sx,sy)–(ex,ey), or null. */
internal fun measurementLabel(
    sx: Double,
    sy: Double,
    ex: Double,
    ey: Double,
    colorArgb: Int,
): TextElement? {
    val text = measurementText(hypot(ex - sx, ey - sy)) ?: return null
    // Width in pt: the renderer draws text at `size * scale` px, so measuring at the size itself
    // gives the box's width in the same pt the element's coordinates are in.
    val widthPt = measurer.measureText(text).toDouble()
    val midX = (sx + ex) / 2.0
    val midY = (sy + ey) / 2.0
    return TextElement(
        font = LABEL_FONT,
        size = LABEL_SIZE_PT,
        x = midX - widthPt / 2.0,
        y = midY - LABEL_SIZE_PT - LABEL_LIFT_PT,
        color = colorArgb,
        content = text,
    )
}

/**
 * Write the measurement of the just-committed dimension line into the page, on the layer it landed
 * on. Called from the commit path, so the text joins the line in the same undo step.
 */
internal fun DrawingSurfaceView.appendDimensionLabel(raw: List<StrokePoint>, colorArgb: Int) {
    val start = raw.firstOrNull() ?: return
    val end = raw.lastOrNull() ?: return
    val label = measurementLabel(start.x, start.y, end.x, end.y, colorArgb) ?: return
    val updated = ElementEdits.addElement(doc, currentPage, label) { resolvedActiveLayer(it) } ?: return
    doc = updated
    relayout()
}

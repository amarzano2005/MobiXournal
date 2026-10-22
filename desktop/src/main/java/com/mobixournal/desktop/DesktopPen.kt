package com.mobixournal.desktop

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ActiveTool
import com.mobixournal.render.InkPointer

/**
 * The window's pen, as **values**: what a pointer stream becomes before it is a page edit.
 *
 * The decisions are not re-made here. The pressure multiplier is [com.mobixournal.render.PressureCurve]'s,
 * the "pen vs eraser vs pan" call is [com.mobixournal.render.InputClassifier]'s (through
 * [com.mobixournal.render.InkPointerRouter]), the cut geometry is
 * [com.mobixournal.render.PageEraser]'s, and the document edit is [DesktopDocument.editPage]'s. What
 * this file adds is only the translation: a page-space sample list becomes the `Stroke` the tablet
 * would have written, and a page gains it on its top layer.
 *
 * Plain Kotlin, no Compose types, so a JVM test can drive the pen without a window.
 */

/** One page-space sample collected while a stroke is being drawn. */
data class InkSample(
    val x: Double,
    val y: Double,
    /** The pressure filter's multiplier for this sample — the tool's width times this is the point's. */
    val factor: Float,
    /** The device sample it came from: pressure, tilt, azimuth and the time are kept for telemetry. */
    val pointer: InkPointer,
)

/** The document tool a strip slot inks with, or null for a slot whose tool the window does not draw yet. */
fun DesktopTool.inkTool(): Tool? = when (this) {
    DesktopTool.PEN -> Tool.PEN
    DesktopTool.HIGHLIGHTER -> Tool.HIGHLIGHTER
    else -> null
}

/**
 * The picked slot in the vocabulary the shared classifier reads, so the router — not this shell —
 * decides what a press means. A slot the window cannot draw yet reduces to `HAND`: its drag pans, and
 * nothing is committed, which is the honest behaviour for a tool with nothing behind it.
 */
fun DesktopTool.activeTool(): ActiveTool = when (this) {
    DesktopTool.PEN -> ActiveTool.PEN
    DesktopTool.HIGHLIGHTER -> ActiveTool.HIGHLIGHTER
    DesktopTool.ERASER -> ActiveTool.ERASER
    else -> ActiveTool.HAND
}

/**
 * The stroke [samples] describe, or null when there is nothing to draw (fewer than two points, which
 * is the same rule the tablet's commit uses).
 *
 * A **highlighter** is stored at one width — its band is constant — while a pen keeps each sample's
 * own width, which is what makes a pressure taper round-trip through the file. The colour is the
 * palette's opaque ARGB, the cap is `round` as Xournal++ writes it, and the per-sample `dtMs` is the
 * time since the stroke began (the same telemetry field the tablet records).
 */
fun strokeOf(tool: Tool, colour: Int, widthPt: Float, samples: List<InkSample>): Stroke? {
    if (samples.size < 2) return null
    val uniform = tool == Tool.HIGHLIGHTER
    val start = samples.first().pointer.timeMs
    val points = samples.map { sample ->
        StrokePoint(
            x = sample.x,
            y = sample.y,
            width = if (uniform) widthPt.toDouble() else widthPt * sample.factor.toDouble(),
            pressure = sample.pointer.pressure,
            tilt = sample.pointer.tiltRad,
            azimuth = sample.pointer.azimuthRad,
            dtMs = (sample.pointer.timeMs - start).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        )
    }
    return Stroke(tool = tool, color = colour, capStyle = "round", points = points, uniformWidth = uniform)
}

/**
 * The **page point** under a view position, in document points.
 *
 * [Viewport] offsets are dp and [Viewport.zoom] maps page pt to dp, while a Compose pointer reports
 * device pixels — so the density is divided out first. Kept here rather than inline in the canvas so
 * the mapping a stroke is recorded with is the one a test can check.
 */
fun pagePointOf(xPx: Float, yPx: Float, density: Float, viewport: Viewport): Pair<Double, Double> =
    ((xPx / density - viewport.offsetX) / viewport.zoom).toDouble() to
        ((yPx / density - viewport.offsetY) / viewport.zoom).toDouble()

/** [page] with [element] added on top of its **top layer** — where a new stroke lands. */
fun Page.withElement(element: Element): Page {
    val layers = layers.ifEmpty { listOf(Layer(emptyList())) }
    val top = layers.lastIndex
    val grown = layers.toMutableList().also { it[top] = Layer(it[top].elements + element, it[top].name) }
    return copy(layers = grown)
}

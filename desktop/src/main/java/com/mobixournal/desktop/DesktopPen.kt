package com.mobixournal.desktop

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ActiveTool
import com.mobixournal.render.EraserMode
import com.mobixournal.render.InkPointer
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.round

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
 * decides what a press means. The STEM slots reduce to `HAND` here because their press is answered
 * before the router (`livingPress`): a slot whose product is a living element is not ink, and nothing
 * reaches this table for it.
 */
fun DesktopTool.activeTool(): ActiveTool = when (this) {
    DesktopTool.PEN -> ActiveTool.PEN
    DesktopTool.HIGHLIGHTER -> ActiveTool.HIGHLIGHTER
    DesktopTool.ERASER, DesktopTool.ERASER_WHOLE -> ActiveTool.ERASER
    DesktopTool.HAND -> ActiveTool.HAND
    DesktopTool.VERTICAL_SPACE -> ActiveTool.VERTICAL_SPACE
    // The two marquee tools select rather than ink; the shared router's `SELECT` arm therefore never starts a
    // stroke, and the canvas hands the drag to `SelectionGesture` before it asks the router anything.
    DesktopTool.MARQUEE, DesktopTool.LASSO -> ActiveTool.SELECT
    else -> ActiveTool.HAND
}

/**
 * True when the pen's own packets lay down or rub out this slot's product.
 *
 * Only the inking and erasing slots are fed from the driver. Every other slot's drag comes from the
 * Compose pointer, and a packet contact would take the pen away from it: the window stands the Compose
 * gesture aside while the packets own the pen (`PageCanvas`'s `penActive`), which is right for a stroke
 * and wrong for a drag that is not ink. A geometry slot already refused the packets for this reason; the
 * vertical-space drag needs the same answer, or a pen in proximity reports ownership on its tip-down and
 * the grab line never moves.
 */
fun DesktopTool.inksByPackets(): Boolean = inkTool() != null || activeTool() == ActiveTool.ERASER

/**
 * Which of the eraser's two members this slot is.
 *
 * The shared [EraserMode] decides what a bite means: [EraserMode.STANDARD] cuts the disc's path out of
 * the stroke it crosses, [EraserMode.WHOLE_STROKE] removes every stroke the disc touches. Every other
 * slot answers the standard mode, so the eraser's two members differ by **their mode**, not by a second
 * eraser implementation — the window and the tablet rub out through one `PageEraser`.
 */
fun DesktopTool.eraserMode(): EraserMode =
    if (this == DesktopTool.ERASER_WHOLE) EraserMode.WHOLE_STROKE else EraserMode.STANDARD

/**
 * The tool a **live preview** is drawn with: the slot's ink tool, or the pen for a geometry slot.
 *
 * A shape is previewed with the pen's rule (one width, the palette's colour) because that is exactly
 * what its lift writes; a slot that previews nothing — the eraser, the tools that move the page — answers
 * null, and the canvas draws no draft for it.
 */
fun DesktopTool.previewTool(): Tool? = inkTool() ?: if (shapeKind() != null) Tool.PEN else null

/**
 * The shape a slot draws, or null for a slot that inks instead.
 *
 * The geometry itself is the tablet's (`mobix-core`'s [ShapeBuilder]): a shape is an ordinary stroke, so
 * it round-trips through `.xopp` with no new element and no new attribute. `AXES` is the shared
 * [ShapeKind.COORDINATE_AXIS] — desktop Xournal++ calls that figure an axes pair.
 */
fun DesktopTool.shapeKind(): ShapeKind? = when (this) {
    DesktopTool.LINE -> ShapeKind.LINE
    DesktopTool.ARROW -> ShapeKind.ARROW
    DesktopTool.DOUBLE_ARROW -> ShapeKind.DOUBLE_ARROW
    DesktopTool.AXES -> ShapeKind.COORDINATE_AXIS
    DesktopTool.RECTANGLE -> ShapeKind.RECTANGLE
    DesktopTool.ELLIPSE -> ShapeKind.ELLIPSE
    // The tablet's STEM figures, so the two shells offer the same set. A square is not among them: the
    // rectangle draws one, and the tablet dropped its button for the same reason.
    DesktopTool.TRIANGLE -> ShapeKind.TRIANGLE
    DesktopTool.RHOMBUS -> ShapeKind.RHOMBUS
    DesktopTool.TRAPEZOID -> ShapeKind.TRAPEZOID
    // The generic polygon: the builder's default side count here, since a slot carries no variant of
    // its own on this shell — the tablet's Settings ▸ Figures sets the count where the tool is drawn.
    DesktopTool.POLYGON -> ShapeKind.POLYGON
    else -> null
}

/** A click with a shape tool is not a figure: a drag shorter than this draws nothing. */
const val MIN_SHAPE_DRAG_PT = 4.0

/**
 * The vertices of the shape a drag describes, in page points — the one decision a shape tool makes.
 *
 * [constrained] is `Shift`: it snaps the drag's **direction** to 45° steps and keeps its length, which
 * is how a line, an arrow or a spline is drawn straight, at 45° or at a right angle without a guide. The
 * snapping is on the direction and not on the end point, so a long drag and a short one both land on the
 * same ray. The figure's size is then [ShapeBuilder]'s own answer for that drag (a rectangle is its
 * bounding box, an ellipse is inscribed in it, and so on), so the window has no second idea of what a
 * rectangle is.
 */
fun shapeFromDrag(
    kind: ShapeKind,
    x0: Double,
    y0: Double,
    x1: Double,
    y1: Double,
    widthPt: Float,
    constrained: Boolean,
    /** A triangle's and a trapezoid's own answers; the other figures ignore them. */
    figures: FigureShape = FigureShape(),
): List<StrokePoint> {
    val dx = x1 - x0
    val dy = y1 - y0
    val length = hypot(dx, dy)
    if (length < MIN_SHAPE_DRAG_PT) return emptyList()
    val (ex, ey) = if (constrained) {
        val step = PI / 4.0
        val angle = round(atan2(dy, dx) / step) * step
        x0 + kotlin.math.cos(angle) * length to y0 + kotlin.math.sin(angle) * length
    } else {
        x1 to y1
    }
    return ShapeBuilder.build(
        kind, x0, y0, ex, ey, widthPt.toDouble(),
        triangleKind = figures.triangleKind,
        angleA = figures.scaleneAngleA,
        angleB = figures.scaleneAngleB,
        angleC = figures.scaleneAngleC,
        trapezoidKind = figures.trapezoidKind,
        trapezoidAngleA = figures.trapezoidAngleA,
        trapezoidAngleB = figures.trapezoidAngleB,
    )
}

/**
 * A shape as the stroke the page stores: one constant width, because a geometric figure is drawn with a
 * pen of one size — the drag says where it goes, the palette says how thick it is.
 */
fun shapeStroke(
    points: List<StrokePoint>,
    colour: Int,
    widthPt: Float,
    lineStyle: LineStyle = LineStyle.PLAIN,
): Stroke? {
    if (points.size < 2) return null
    return Stroke(
        tool = Tool.PEN,
        color = colour,
        capStyle = "round",
        points = points.map { it.copy(width = widthPt.toDouble()) },
        uniformWidth = true,
        lineStyle = lineStyle,
    )
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
fun strokeOf(
    tool: Tool,
    colour: Int,
    widthPt: Float,
    samples: List<InkSample>,
    lineStyle: LineStyle = LineStyle.PLAIN,
): Stroke? {
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
    return Stroke(
        tool = tool,
        color = colour,
        capStyle = "round",
        points = points,
        uniformWidth = uniform,
        lineStyle = lineStyle,
    )
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

package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.SelectionShaft
import com.mobixournal.render.shaftOf
import com.mobixournal.render.shaftTurn

/**
 * The selection drawn **over the page**, in the window's own chrome ink — no material of its own.
 *
 * `docs/interface-contract.md` puts a selection in the same family as the rest of the chrome: a dashed
 * accent line, solid square handles on the four corners and the four **edge midpoints** (one axis each, as
 * the tablet draws them) and a round one above the box for the turn. It is not a floating surface and it
 * never covers ink: the outline is a hairline, and the handles are eight screen pixels of the accent, so what
 * is selected is still what the user sees.
 *
 * [pxPerPt] is the sheet's own scale (`zoom × density`), so the outline follows a zoom while its stroke and
 * its handles stay the same size on screen — a handle that grew with the page would be a slab at 400 %.
 */
internal fun DrawScope.drawSelection(selection: PageSelection, pxPerPt: Float, living: Element? = null) {
    val shaft = shaftOf(living)
    val singleShape = living as? ParametricShapeElement
    val singleSpline = living as? com.mobixournal.format.model.SplineElement
    // Lines/arrows and splines show no box; a single parametric figure hugs its curve or vertices instead of a rectangle.
    if (singleShape != null && singleShape.vertices.size >= 3) {
        val outline = if (com.mobixournal.render.ParametricShapes.isConic(singleShape)) {
            com.mobixournal.render.ParametricShapes.conicOutlinePoints(singleShape)
        } else {
            singleShape.vertices
        }
        drawRegion(
            SelectionRegion.Polygon(outline.map { com.mobixournal.render.Vec2(it.x, it.y) }),
            pxPerPt,
            dashed = true,
        )
    } else if (shaft == null && singleSpline == null) {
        drawRegion(selection.region, pxPerPt, dashed = true)
    }
    drawHandles(
        selection,
        pxPerPt,
        isImage = living is ImageElement,
        shaft = shaft,
        includeBoxResize = singleShape == null && singleSpline == null,
        living = living,
    )
    // The element's own grips, inside the box: a figure's corners and a table's dividers. They are round
    // where the box's handles are square, so the two never read as the same thing.
    if (living != null) drawGrips(living, pxPerPt)
}

/** A living element's grips — a hollow ring per grip, the same size at every zoom. */
private fun DrawScope.drawGrips(element: Element, pxPerPt: Float) {
    val grips = livingGripsOf(element)
    if (element is com.mobixournal.format.model.SplineElement) {
        for (point in grips) {
            val g = point.grip
            if (g is LivingGrip.SplineInHandle || g is LivingGrip.SplineOutHandle) {
                val idx = if (g is LivingGrip.SplineInHandle) g.index else (g as LivingGrip.SplineOutHandle).index
                val knot = element.nodes.getOrNull(idx) ?: continue
                drawLine(
                    color = DesktopTheme.accent.copy(alpha = 0.55f),
                    start = Offset((knot.x * pxPerPt).toFloat(), (knot.y * pxPerPt).toFloat()),
                    end = Offset((point.x * pxPerPt).toFloat(), (point.y * pxPerPt).toFloat()),
                    strokeWidth = 1.2f,
                )
            }
        }
    }
    for (point in grips) {
        val centre = Offset((point.x * pxPerPt).toFloat(), (point.y * pxPerPt).toFloat())
        val isHandle = point.grip is LivingGrip.SplineInHandle || point.grip is LivingGrip.SplineOutHandle
        val r = if (isHandle) GRIP_SIZE * 0.36f else GRIP_SIZE / 2f
        drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = r + 1f, center = centre)
        drawCircle(color = DesktopTheme.accent, radius = r, center = centre)
    }
}

/** The region being traced right now — one outline, no handles until it is committed. */
internal fun DrawScope.drawSelectionPreview(region: SelectionRegion, pxPerPt: Float) {
    drawRegion(region, pxPerPt, dashed = false)
}

/** A rectangle or a loop, scaled from page points to the canvas's pixels. */
private fun DrawScope.drawRegion(region: SelectionRegion, pxPerPt: Float, dashed: Boolean) {
    val path = Path()
    when (region) {
        is SelectionRegion.Rect -> {
            val box = region.bounds
            path.addRect(
                Rect(
                    (box.left * pxPerPt).toFloat(), (box.top * pxPerPt).toFloat(),
                    (box.right * pxPerPt).toFloat(), (box.bottom * pxPerPt).toFloat(),
                ),
            )
        }
        is SelectionRegion.Polygon -> {
            val points = region.points
            if (points.size < 2) return
            path.moveTo((points[0].x * pxPerPt).toFloat(), (points[0].y * pxPerPt).toFloat())
            for (point in points.drop(1)) path.lineTo((point.x * pxPerPt).toFloat(), (point.y * pxPerPt).toFloat())
            path.close()
        }
    }
    drawPath(
        path = path,
        color = DesktopTheme.accent,
        style = Stroke(
            width = if (dashed) 1.2f else 1.6f,
            cap = StrokeCap.Round,
            // The marching dashes say "this is a selection, not a new object"; the live trace is solid, so the
            // user can see the loop they are drawing rather than an animation of it.
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
        ),
    )
}

/**
 * The eight squares — four corners, four edge midpoints — and, when [isImage] is false, the turn's stem and ring.
 *
 * The edge squares are drawn for **every** selection, not only an image's: they are the one-axis stretch the
 * tablet has always carried, and a handle the hit test answers to must be visible or it may as well be absent
 * — which is the same rule that hides the rest on a **line**. A box with no extent on an axis wears no
 * corners (they would sit exactly on its two end handles) and no squares along that axis, so a line shows the
 * two ends a stretch uses and nothing that a press could not act on (`DesktopSelection.isFlatX`).
 */
private fun DrawScope.drawHandles(
    selection: PageSelection,
    pxPerPt: Float,
    isImage: Boolean = false,
    shaft: SelectionShaft? = null,
    includeBoxResize: Boolean = true,
    living: Element? = null,
) {
    val box = selection.bounds
    fun off(x: Double, y: Double) = Offset((x * pxPerPt).toFloat(), (y * pxPerPt).toFloat())
    fun square(at: Offset) {
        drawRect(
            color = DesktopTheme.accent,
            topLeft = Offset(at.x - HANDLE_SIZE / 2f, at.y - HANDLE_SIZE / 2f),
            size = Size(HANDLE_SIZE, HANDLE_SIZE),
        )
    }
    if (shaft != null) {
        // A line or an arrow: two squares on its own ends, and nothing on the box — its corners are not
        // places where anything may change.
        square(off(shaft.tailX, shaft.tailY))
        square(off(shaft.tipX, shaft.tipY))
    } else if (includeBoxResize) {
        if (!box.isFlatX && !box.isFlatY) {
            for (corner in listOf(
                off(box.left, box.top), off(box.right, box.top),
                off(box.right, box.bottom), off(box.left, box.bottom),
            )) {
                square(corner)
            }
        }
        val midX = (box.left + box.right) / 2.0
        val midY = (box.top + box.bottom) / 2.0
        val edges = buildList {
            if (!box.isFlatY) {
                add(off(midX, box.top))
                add(off(midX, box.bottom))
            }
            if (!box.isFlatX) {
                add(off(box.right, midY))
                add(off(box.left, midY))
            }
        }
        for (edge in edges) {
            square(edge)
        }
    }
    // The gap is a screen distance turned into page points, so the ring clears the ink at every zoom.
    val gapPt = (ROTATE_GAP_PX / pxPerPt).toDouble()
    val turn = shaft?.let { shaftTurn(it, gapPt) }
    if (turn != null) {
        // A shaft's ring is **on** the ink: the stem starts at its middle and the knob is a gap away, so the
        // turn never floats in empty paper the way a box's handle would.
        val ring = off(turn.ringX, turn.ringY)
        drawLine(DesktopTheme.accent, off(turn.pivotX, turn.pivotY), ring, strokeWidth = 1f)
        drawCircle(color = DesktopTheme.accent, radius = HANDLE_SIZE * 0.55f, center = ring)
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = HANDLE_SIZE * 0.28f, center = ring)
    } else if (!isImage) {
        // The stem anchors to the figure's top outline so it never floats in empty space.
        val stem = com.mobixournal.render.ElementHitTester.rotateStemTop(living, box, gapPt)
        val rotate = off(stem.knobX, stem.knobY)
        drawLine(DesktopTheme.accent, off(stem.footX, stem.footY), rotate, strokeWidth = 1f)
        drawCircle(color = DesktopTheme.accent, radius = HANDLE_SIZE * 0.55f, center = rotate)
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = HANDLE_SIZE * 0.28f, center = rotate)
    }
}

/** One handle's side, in **screen** pixels — the same at every zoom. */
internal const val HANDLE_SIZE = 7f

/** A grip's diameter, in screen pixels — a ring the size of the box's squares. */
internal const val GRIP_SIZE = 7f

/** How far above the outline the turn handle floats, in screen pixels. */
internal const val ROTATE_GAP_PX = 22f

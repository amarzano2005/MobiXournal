package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion

/**
 * The selection drawn **over the page**, in the window's own chrome ink — no material of its own.
 *
 * `docs/interface-contract.md` puts a selection in the same family as the rest of the chrome: a dashed
 * accent line, solid square handles at the corners and a round one above the box for the turn. It is not a
 * floating surface and it never covers ink: the outline is a hairline, and the handles are eight screen pixels
 * of the accent, so what is selected is still what the user sees.
 *
 * [pxPerPt] is the sheet's own scale (`zoom × density`), so the outline follows a zoom while its stroke and
 * its handles stay the same size on screen — a handle that grew with the page would be a slab at 400 %.
 */
internal fun DrawScope.drawSelection(selection: PageSelection, pxPerPt: Float) {
    drawRegion(selection.region, pxPerPt, dashed = true)
    drawHandles(selection, pxPerPt)
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

/** Four corner squares and the rotate handle above the top edge. */
private fun DrawScope.drawHandles(selection: PageSelection, pxPerPt: Float) {
    val box = selection.bounds
    fun off(x: Double, y: Double) = Offset((x * pxPerPt).toFloat(), (y * pxPerPt).toFloat())
    val corners = listOf(
        off(box.left, box.top), off(box.right, box.top),
        off(box.right, box.bottom), off(box.left, box.bottom),
    )
    for (corner in corners) {
        drawRect(
            color = DesktopTheme.accent,
            topLeft = Offset(corner.x - HANDLE_SIZE / 2f, corner.y - HANDLE_SIZE / 2f),
            size = Size(HANDLE_SIZE, HANDLE_SIZE),
        )
    }
    // The stem and the ring: the one handle that is not a corner, so it cannot be mistaken for a resize.
    // The gap is a screen distance turned into page points, so the ring clears the outline at every zoom.
    val (rotateX, rotateY) = rotateHandleOf(box, gapPt = (ROTATE_GAP_PX / pxPerPt).toDouble())
    val rotate = off(rotateX, rotateY)
    val topMiddle = off((box.left + box.right) / 2.0, box.top)
    drawLine(DesktopTheme.accent, topMiddle, rotate, strokeWidth = 1f)
    drawCircle(color = DesktopTheme.accent, radius = HANDLE_SIZE * 0.55f, center = rotate)
    drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = HANDLE_SIZE * 0.28f, center = rotate)
}

/** One handle's side, in **screen** pixels — the same at every zoom. */
internal const val HANDLE_SIZE = 7f

/** How far above the outline the turn handle floats, in screen pixels. */
internal const val ROTATE_GAP_PX = 22f

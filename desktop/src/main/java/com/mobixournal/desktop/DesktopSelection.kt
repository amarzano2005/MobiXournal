package com.mobixournal.desktop

import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The window's **selection geometry**: what a press on a selected page means, and the factors a drag on a
 * handle produces.
 *
 * Pure and free of Compose, so the decisions are a JVM test rather than a gesture to poke at. The editing
 * itself is not here — that is `SelectionEdit` in `mobix-core`, shared with the tablet — and neither is the
 * region: this file only answers *which* handle the pointer grabbed and *how far* it was dragged, in page
 * points. The canvas turns those answers into one `SelectionEdit` call per move.
 */

/** What a press inside a live selection grabbed. */
enum class SelectionHandle {
    /** Not on the selection: a new region starts here. */
    NONE,

    /** Inside the outline: the selection moves. */
    BODY,

    /** One of the four corner handles: the selection scales about the opposite corner. */
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,

    /** The handle above the outline: the selection turns about its centre. */
    ROTATE,
}

/** How far above the outline's top edge the rotate handle sits, in page points at 100 %. */
const val ROTATE_HANDLE_GAP_PT = 26.0

/** The smallest scale a handle drag may produce, so a flourish cannot collapse the selection to a line. */
const val MIN_SELECTION_SCALE = 0.05

/** Where the rotate handle's centre is, in page points. */
fun rotateHandleOf(bounds: Bounds, gapPt: Double = ROTATE_HANDLE_GAP_PT): Pair<Double, Double> =
    ((bounds.left + bounds.right) / 2.0) to (bounds.top - gapPt)

/**
 * What [selection]'s handle at page point (x, y) is — [SelectionHandle.NONE] when the point is off it.
 *
 * [tolPt] is the grab radius in page points, which the canvas derives from pixels so a handle is equally easy
 * to hit at every zoom. The rotate handle is checked first: it sits above the box, where nothing else can be,
 * and a press meant for it that landed a hair low should not start a new selection.
 */
fun selectionHandleAt(
    selection: PageSelection,
    x: Double,
    y: Double,
    tolPt: Double,
    rotateGapPt: Double = ROTATE_HANDLE_GAP_PT,
): SelectionHandle {
    val box = selection.bounds
    val (rotateX, rotateY) = rotateHandleOf(box, rotateGapPt)
    if (near(x, y, rotateX, rotateY, tolPt)) return SelectionHandle.ROTATE
    val corners = listOf(
        SelectionHandle.TOP_LEFT to (box.left to box.top),
        SelectionHandle.TOP_RIGHT to (box.right to box.top),
        SelectionHandle.BOTTOM_LEFT to (box.left to box.bottom),
        SelectionHandle.BOTTOM_RIGHT to (box.right to box.bottom),
    )
    for ((handle, at) in corners) {
        if (near(x, y, at.first, at.second, tolPt)) return handle
    }
    return if (selection.region.inside(x, y)) SelectionHandle.BODY else SelectionHandle.NONE
}

/** The corner a handle drag leaves **still** — the opposite one. */
fun anchorOf(bounds: Bounds, handle: SelectionHandle): Pair<Double, Double> = when (handle) {
    SelectionHandle.TOP_LEFT -> bounds.right to bounds.bottom
    SelectionHandle.TOP_RIGHT -> bounds.left to bounds.bottom
    SelectionHandle.BOTTOM_LEFT -> bounds.right to bounds.top
    SelectionHandle.BOTTOM_RIGHT -> bounds.left to bounds.top
    // A missing or moving handle has no fixed corner; the centre is the one answer that moves nothing else.
    else -> ((bounds.left + bounds.right) / 2.0) to ((bounds.top + bounds.bottom) / 2.0)
}

/**
 * The horizontal and vertical factors a corner drag describes, about the selection's opposite corner.
 *
 * Each axis is its own ratio, so a side drag stretches a figure without forcing the other axis with it; both
 * are clamped to [MIN_SELECTION_SCALE] so crossing the anchor cannot invert the selection mid-gesture.
 */
fun scaleFactors(
    bounds: Bounds,
    handle: SelectionHandle,
    x: Double,
    y: Double,
): Pair<Double, Double> {
    val (anchorX, anchorY) = anchorOf(bounds, handle)
    val startX = when (handle) {
        SelectionHandle.TOP_LEFT, SelectionHandle.BOTTOM_LEFT -> bounds.left
        SelectionHandle.TOP_RIGHT, SelectionHandle.BOTTOM_RIGHT -> bounds.right
        else -> return 1.0 to 1.0
    }
    val startY = when (handle) {
        SelectionHandle.TOP_LEFT, SelectionHandle.TOP_RIGHT -> bounds.top
        SelectionHandle.BOTTOM_LEFT, SelectionHandle.BOTTOM_RIGHT -> bounds.bottom
        else -> return 1.0 to 1.0
    }
    val spanX = startX - anchorX
    val spanY = startY - anchorY
    val sx = if (spanX == 0.0) 1.0 else (x - anchorX) / spanX
    val sy = if (spanY == 0.0) 1.0 else (y - anchorY) / spanY
    return sx.coerceAtLeast(MIN_SELECTION_SCALE) to sy.coerceAtLeast(MIN_SELECTION_SCALE)
}

/**
 * The turn a rotate drag has described so far, in radians, from the grab point to the current one.
 *
 * [snap] rounds it to 15° steps — `Shift` while dragging, the same modifier a figure drag uses to keep a line
 * straight — because a hand-turned right angle is never a right angle.
 */
fun rotationBetween(
    selection: PageSelection,
    fromX: Double,
    fromY: Double,
    toX: Double,
    toY: Double,
    snap: Boolean = false,
): Double {
    val cx = (selection.bounds.left + selection.bounds.right) / 2.0
    val cy = (selection.bounds.top + selection.bounds.bottom) / 2.0
    val from = atan2(fromY - cy, fromX - cx)
    val to = atan2(toY - cy, toX - cx)
    var delta = to - from
    while (delta > PI) delta -= 2 * PI
    while (delta < -PI) delta += 2 * PI
    if (!snap) return delta
    val step = PI / 12.0
    return (delta / step).roundToInt() * step
}

/** True when (x, y) is within [tol] of (cx, cy) — a square grab box, which is what a handle needs. */
private fun near(x: Double, y: Double, cx: Double, cy: Double, tol: Double): Boolean =
    x >= cx - tol && x <= cx + tol && y >= cy - tol && y <= cy + tol

/** The selection [tool] starts, or null for a slot that is not one of the two marquee tools. */
internal fun selectionToolOf(tool: DesktopTool): SelectionTool? = when (tool) {
    DesktopTool.MARQUEE -> SelectionTool.MARQUEE
    DesktopTool.LASSO -> SelectionTool.LASSO
    else -> null
}

/** The two ways a region is traced. */
internal enum class SelectionTool { MARQUEE, LASSO }

/** True when the press starts a fresh region rather than acting on the live one. */
fun SelectionHandle.startsRegion(): Boolean = this == SelectionHandle.NONE

/** The selection a finished trace means: `null` when it caught nothing, so no empty outline is left behind. */
fun finishedSelection(region: SelectionRegion, caught: Set<ElementRef>): PageSelection? =
    if (caught.isEmpty()) null else PageSelection(region, caught)

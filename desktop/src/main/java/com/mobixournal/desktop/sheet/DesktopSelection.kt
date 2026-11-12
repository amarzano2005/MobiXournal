package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.tools.DesktopTool

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.SelectionShaft
import com.mobixournal.render.shaftTurn
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

    /** Four edge handles: stretch a single axis. */
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,

    /**
     * The two ends of a **line or an arrow**: the only handles those families have, because their one
     * degree of freedom is the length of their shaft (`SelectionShaft`). The dragged end follows the
     * pointer and the other stays.
     */
    SHAFT_TAIL,
    SHAFT_TIP,
}

/** The one element a selection holds, or null when it holds none or several. */
fun singleElementOf(page: Page, selection: PageSelection): Element? =
    selection.refs.singleOrNull()?.let { ref ->
        page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
    }

/** How far above the outline's top edge the rotate handle sits, in page points at 100 %. */
const val ROTATE_HANDLE_GAP_PT = 26.0

/** The smallest scale a handle drag may produce, so a flourish cannot collapse the selection to a line. */
const val MIN_SELECTION_SCALE = 0.05

/**
 * The largest scale a handle drag may produce, so a stretch cannot fling the selection off the page.
 *
 * The tablet's own ceiling (`DrawingSurfaceDefaults.MAX_RESIZE`): a factor read off a drag is a ratio of a
 * span, and on a small box a hand's travel divided by a short span runs away.
 */
const val MAX_SELECTION_SCALE = 20.0

/**
 * The extent below which a box is **a line** on that axis rather than a box: the thickness a pen draws, so a
 * stroke's box is exactly that thick on its short side (a default pen, and the margin
 * `ElementBounds.TAP_PAD` gives a thin stroke so it stays pickable).
 *
 * Stretching across such an axis could only multiply it by a factor read from a point or two of travel, which
 * is why the handles that would do it are not offered there (`docs/selection.md`).
 */
const val FLAT_PT = 4.0

/** True when the box has no extent worth stretching along x — a line, not a box with a width. */
val Bounds.isFlatX: Boolean get() = width <= FLAT_PT

/** True when the box has no extent worth stretching along y — see [isFlatX]. */
val Bounds.isFlatY: Boolean get() = height <= FLAT_PT

/** Where the rotate handle's centre is, in page points. */
fun rotateHandleOf(bounds: Bounds, gapPt: Double = ROTATE_HANDLE_GAP_PT): Pair<Double, Double> =
    ((bounds.left + bounds.right) / 2.0) to (bounds.top - gapPt)

/**
 * What [selection]'s handle at page point (x, y) is — [SelectionHandle.NONE] when the point is off it.
 *
 * [tolPt] is the grab radius in page points, which the canvas derives from pixels so a handle is equally easy
 * to hit at every zoom. It is capped **per axis**, at a third of that axis's own extent, so a small selection
 * keeps a middle to be moved by. Capping each axis on its own extent is what makes a **line** behave: a
 * line's box is only as thick as the pen that drew it, and capping both axes by the smaller one left its end
 * handles with a grab radius of a point or two — a press on the end caught the handle, carried the whole line
 * or dropped the selection and started a new marquee, which reads as a tool that does something different
 * every time. An axis with no extent to speak of ([isFlatX] / [isFlatY]) has no middle to protect either, so
 * a handle there reaches the full radius.
 *
 * The rotate handle is checked first: it sits above the box, where nothing else can be, and a press meant for
 * it that landed a hair low should not start a new selection.
 *
 * Both families of handle are asked by default: the four corners and the four **edge midpoints**, which are
 * the tablet's own eight (`docs/selection.md`) — except on a box that is **flat** on an axis, which wears
 * neither the corners (they sit exactly where its two end handles are) nor the edges along that axis (a scale
 * there could only multiply by one). A line is stretched from its two ends, and the band between them carries
 * it, so a press above or below it still moves it instead of landing on nothing. [includeEdges] stays a
 * parameter only so a caller can state its intent; [includeRotate] is false for an image, whose model carries
 * no rotation.
 */
/**
 * Whether a **corner** drag keeps the selection's proportions.
 *
 * An image's does: a picture is resized, never reshaped. A figure's does not — its corner is the free
 * stretch that widens a rectangle or squashes a triangle, and it is what a corner of the box has always
 * done in this window. `Shift` locks any corner, so a figure can still be scaled proportionally by hand.
 */
fun cornerLocksAspect(element: Element?, shift: Boolean): Boolean = shift || element is ImageElement

fun selectionHandleAt(
    selection: PageSelection,
    x: Double,
    y: Double,
    tolPt: Double,
    rotateGapPt: Double = ROTATE_HANDLE_GAP_PT,
    includeEdges: Boolean = true,
    includeRotate: Boolean = true,
    /** The shaft when the selection is a single line or arrow; null for every other selection. */
    shaft: SelectionShaft? = null,
): SelectionHandle {
    val box = selection.bounds
    val (reachX, reachY) = handleReach(box, tolPt)
    // A line and an arrow are **not** figures: their shaft is the only thing a drag may change, so they
    // wear two end grips and a turn ring hung on the ink itself (`shaftTurn`) — no corners, no sides, and
    // no box, because a rectangle round a shaft promises eight handles the ink does not have.
    if (shaft != null) {
        if (includeRotate) {
            val turn = shaftTurn(shaft, rotateGapPt)
            if (near(x, y, turn.ringX, turn.ringY, tolPt, tolPt)) return SelectionHandle.ROTATE
        }
        if (near(x, y, shaft.tailX, shaft.tailY, tolPt, tolPt)) return SelectionHandle.SHAFT_TAIL
        if (near(x, y, shaft.tipX, shaft.tipY, tolPt, tolPt)) return SelectionHandle.SHAFT_TIP
        if (selection.region.inside(x, y)) return SelectionHandle.BODY
        // The band along the shaft carries it, so a press a hair off the ink moves it instead of nothing.
        if (x >= box.left - reachX && x <= box.right + reachX &&
            y >= box.top - reachY && y <= box.bottom + reachY
        ) {
            return SelectionHandle.BODY
        }
        return SelectionHandle.NONE
    }
    if (includeRotate) {
        val (rotateX, rotateY) = rotateHandleOf(box, rotateGapPt)
        if (near(x, y, rotateX, rotateY, reachX, reachY)) return SelectionHandle.ROTATE
    }
    if (!box.isFlatX && !box.isFlatY) {
        val corners = listOf(
            SelectionHandle.TOP_LEFT to (box.left to box.top),
            SelectionHandle.TOP_RIGHT to (box.right to box.top),
            SelectionHandle.BOTTOM_LEFT to (box.left to box.bottom),
            SelectionHandle.BOTTOM_RIGHT to (box.right to box.bottom),
        )
        for ((handle, at) in corners) {
            if (near(x, y, at.first, at.second, reachX, reachY)) return handle
        }
    }
    if (includeEdges) {
        val midX = (box.left + box.right) / 2.0
        val midY = (box.top + box.bottom) / 2.0
        val edges = listOf(
            SelectionHandle.TOP to (midX to box.top),
            SelectionHandle.BOTTOM to (midX to box.bottom),
            SelectionHandle.LEFT to (box.left to midY),
            SelectionHandle.RIGHT to (box.right to midY),
        )
        for ((handle, at) in edges) {
            if (handle.stretchesFlatAxis(box)) continue
            if (near(x, y, at.first, at.second, reachX, reachY)) return handle
        }
    }
    if (selection.region.inside(x, y)) return SelectionHandle.BODY
    // A flat box's own band is its body: a press just above or below a line is still the line being held, and
    // that is exactly where the edge handle that could only multiply by one used to sit and do nothing.
    if ((box.isFlatX || box.isFlatY) &&
        x >= box.left - reachX && x <= box.right + reachX &&
        y >= box.top - reachY && y <= box.bottom + reachY
    ) {
        return SelectionHandle.BODY
    }
    return SelectionHandle.NONE
}

/**
 * The grab box of a handle, in page points: [tolPt], capped on each axis at a third of that axis's own extent
 * so the middle of the box always stays a move. An axis that is flat keeps the whole radius — see
 * [selectionHandleAt].
 */
private fun handleReach(bounds: Bounds, tolPt: Double): Pair<Double, Double> =
    (if (bounds.isFlatX) tolPt else minOf(tolPt, bounds.width / 3.0)) to
        (if (bounds.isFlatY) tolPt else minOf(tolPt, bounds.height / 3.0))

/** True when this handle would stretch an axis the box has no extent on — it is not offered at all. */
private fun SelectionHandle.stretchesFlatAxis(bounds: Bounds): Boolean = when (this) {
    SelectionHandle.TOP, SelectionHandle.BOTTOM -> bounds.isFlatY
    SelectionHandle.LEFT, SelectionHandle.RIGHT -> bounds.isFlatX
    else -> false
}

/** The corner a handle drag leaves **still** — the opposite one. */
fun anchorOf(bounds: Bounds, handle: SelectionHandle): Pair<Double, Double> = when (handle) {
    SelectionHandle.TOP_LEFT -> bounds.right to bounds.bottom
    SelectionHandle.TOP_RIGHT -> bounds.left to bounds.bottom
    SelectionHandle.BOTTOM_LEFT -> bounds.right to bounds.top
    SelectionHandle.BOTTOM_RIGHT -> bounds.left to bounds.top
    SelectionHandle.TOP -> bounds.left to bounds.bottom
    SelectionHandle.BOTTOM -> bounds.left to bounds.top
    SelectionHandle.LEFT -> bounds.right to bounds.top
    SelectionHandle.RIGHT -> bounds.left to bounds.top
    // A missing or moving handle has no fixed corner; the centre is the one answer that moves nothing else.
    else -> ((bounds.left + bounds.right) / 2.0) to ((bounds.top + bounds.bottom) / 2.0)
}

/**
 * The horizontal and vertical factors a corner or edge drag describes, about the selection's opposite corner.
 *
 * Each axis is its own ratio, so a side drag stretches a figure without forcing the other axis with it; both
 * are clamped — [MIN_SELECTION_SCALE] so crossing the anchor cannot invert the selection mid-gesture, and
 * [MAX_SELECTION_SCALE] so a short span cannot turn a hand's travel into an explosion.
 *
 * An axis the box has no extent on ([isFlatX] / [isFlatY]) keeps a factor of **one**: a line stretched from
 * either end gets longer, never taller, and a **corner** drag on it is the same end handle — the aspect lock
 * has no ratio to keep there, so it is skipped rather than letting it copy a factor read from a couple of
 * points of ink.
 */
fun scaleFactors(
    bounds: Bounds,
    handle: SelectionHandle,
    x: Double,
    y: Double,
    lockAspect: Boolean = false,
): Pair<Double, Double> {
    val (anchorX, anchorY) = anchorOf(bounds, handle)
    val flatX = bounds.isFlatX
    val flatY = bounds.isFlatY
    when (handle) {
        SelectionHandle.LEFT -> {
            val spanX = bounds.left - anchorX
            val sx = if (flatX || spanX == 0.0) 1.0 else (x - anchorX) / spanX
            return clampScale(sx) to 1.0
        }
        SelectionHandle.RIGHT -> {
            val spanX = bounds.right - anchorX
            val sx = if (flatX || spanX == 0.0) 1.0 else (x - anchorX) / spanX
            return clampScale(sx) to 1.0
        }
        SelectionHandle.TOP -> {
            val spanY = bounds.top - anchorY
            val sy = if (flatY || spanY == 0.0) 1.0 else (y - anchorY) / spanY
            return 1.0 to clampScale(sy)
        }
        SelectionHandle.BOTTOM -> {
            val spanY = bounds.bottom - anchorY
            val sy = if (flatY || spanY == 0.0) 1.0 else (y - anchorY) / spanY
            return 1.0 to clampScale(sy)
        }
        SelectionHandle.TOP_LEFT,
        SelectionHandle.TOP_RIGHT,
        SelectionHandle.BOTTOM_LEFT,
        SelectionHandle.BOTTOM_RIGHT -> {
            val startX = when (handle) {
                SelectionHandle.TOP_LEFT, SelectionHandle.BOTTOM_LEFT -> bounds.left
                else -> bounds.right
            }
            val startY = when (handle) {
                SelectionHandle.TOP_LEFT, SelectionHandle.TOP_RIGHT -> bounds.top
                else -> bounds.bottom
            }
            val spanX = startX - anchorX
            val spanY = startY - anchorY
            var sx = if (flatX || spanX == 0.0) 1.0 else (x - anchorX) / spanX
            var sy = if (flatY || spanY == 0.0) 1.0 else (y - anchorY) / spanY
            if (lockAspect && !flatX && !flatY) {
                val s = if (kotlin.math.abs(sx - 1.0) >= kotlin.math.abs(sy - 1.0)) sx else sy
                sx = s
                sy = s
            }
            return clampScale(sx) to clampScale(sy)
        }
        else -> return 1.0 to 1.0
    }
}

/** A factor a handle drag may produce — see [MIN_SELECTION_SCALE] and [MAX_SELECTION_SCALE]. */
private fun clampScale(s: Double): Double = s.coerceIn(MIN_SELECTION_SCALE, MAX_SELECTION_SCALE)

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
    pivotX: Double = (selection.bounds.left + selection.bounds.right) / 2.0,
    pivotY: Double = (selection.bounds.top + selection.bounds.bottom) / 2.0,
): Double {
    val cx = pivotX
    val cy = pivotY
    val from = atan2(fromY - cy, fromX - cx)
    val to = atan2(toY - cy, toX - cx)
    var delta = to - from
    while (delta > PI) delta -= 2 * PI
    while (delta < -PI) delta += 2 * PI
    if (!snap) return delta
    val step = PI / 12.0
    return (delta / step).roundToInt() * step
}

/** True when (x, y) is within each axis's reach of (cx, cy) — the grab box of one handle. */
private fun near(x: Double, y: Double, cx: Double, cy: Double, tolX: Double, tolY: Double): Boolean =
    x >= cx - tolX && x <= cx + tolX && y >= cy - tolY && y <= cy + tolY

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

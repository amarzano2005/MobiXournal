package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Which axes a selection-resize drag scales.
 *
 * A **corner** handle of a picture, a mixed selection, or any selection with aspect-lock enabled
 * resizes proportionally ([BOTH]). A corner of a single unlocked figure scales each axis by its own
 * ratio ([FREE]). An **edge** handle stretches a single axis ([X]/[Y]). A **shaft** (line/arrow)
 * scales and turns uniformly about its fixed end ([SHAFT]).
 */
enum class ResizeAxis {
    /** Both axes by the same factor — uniform proportional scaling. */
    BOTH,

    /** Each axis by its own pointer ratio — free corner stretch. */
    FREE,

    /** Horizontal only: an element's width changes, its height does not. */
    X,

    /** Vertical only: an element's height changes, its width does not. */
    Y,

    /** A **shaft**'s free resize: dragged end follows the pointer (`SelectionEdit.similarity`). */
    SHAFT,
}

/**
 * Shared selection-resize math for both Android and Desktop shells.
 *
 * Keeps corner aspect-locking, per-axis ratio calculation, and scale clamping identical across
 * platforms without duplicating geometry rules in shell views.
 */
object SelectionResizeMath {
    const val MIN_SCALE = 0.05
    const val MAX_SCALE = 20.0
    const val FLAT_PT = 4.0
    private const val MIN_AXIS_SPAN_PT = 1e-3

    /**
     * Whether a corner drag keeps the selection's proportions (`sx == sy`).
     *
     * Pictures ([ImageElement]) always lock proportions; mixed selections (`element == null`) lock
     * proportions when [lockMixed] is true; passing [lockAspect] = true locks proportions for any
     * selection.
     */
    fun cornerLocksAspect(element: Element?, lockAspect: Boolean, lockMixed: Boolean = true): Boolean =
        lockAspect || element is ImageElement || (lockMixed && element == null)

    /** The [ResizeAxis] for a corner drag on [element]. */
    fun cornerAxis(element: Element?, lockAspect: Boolean, lockMixed: Boolean = true): ResizeAxis =
        if (cornerLocksAspect(element, lockAspect, lockMixed)) ResizeAxis.BOTH else ResizeAxis.FREE

    /**
     * Clamped scale factor along one axis, measured from [anchorPt].
     *
     * Returns `1.0` if `|startPt - anchorPt| < MIN_AXIS_SPAN_PT` to avoid noise explosions.
     */
    fun axisScaleFactor(
        startPt: Double,
        currentPt: Double,
        anchorPt: Double,
        minScale: Double = MIN_SCALE,
        maxScale: Double = MAX_SCALE,
    ): Double {
        val start = startPt - anchorPt
        if (abs(start) < MIN_AXIS_SPAN_PT) return 1.0
        return ((currentPt - anchorPt) / start).coerceIn(minScale, maxScale)
    }

    /**
     * Horizontal and vertical scale factors for a corner handle drag about ([anchorX], [anchorY]).
     *
     * When [lockAspect] is true (and neither axis is flat), both axes use the dominant scale
     * factor (`sx == sy`). Flat axes always keep a factor of `1.0`.
     */
    fun cornerScaleFactors(
        bounds: Bounds,
        handleIsLeft: Boolean,
        handleIsTop: Boolean,
        currentX: Double,
        currentY: Double,
        anchorX: Double,
        anchorY: Double,
        lockAspect: Boolean,
        flatX: Boolean,
        flatY: Boolean,
        minScale: Double = MIN_SCALE,
        maxScale: Double = MAX_SCALE,
    ): Pair<Double, Double> {
        val startX = if (handleIsLeft) bounds.left else bounds.right
        val startY = if (handleIsTop) bounds.top else bounds.bottom
        val spanX = startX - anchorX
        val spanY = startY - anchorY
        var sx = if (flatX || spanX == 0.0) 1.0 else (currentX - anchorX) / spanX
        var sy = if (flatY || spanY == 0.0) 1.0 else (currentY - anchorY) / spanY
        if (lockAspect && !flatX && !flatY) {
            val s = if (abs(sx - 1.0) >= abs(sy - 1.0)) sx else sy
            sx = s
            sy = s
        }
        return sx.coerceIn(minScale, maxScale) to sy.coerceIn(minScale, maxScale)
    }

    /**
     * Uniform radial scale factor from [anchorX]/[anchorY] to [currentX]/[currentY] relative to
     * [startDist].
     */
    fun proportionalDistanceScale(
        anchorX: Double,
        anchorY: Double,
        currentX: Double,
        currentY: Double,
        startDist: Double,
        minScale: Double = MIN_SCALE,
        maxScale: Double = MAX_SCALE,
    ): Double {
        val denom = startDist.coerceAtLeast(MIN_AXIS_SPAN_PT)
        return (hypot(currentX - anchorX, currentY - anchorY) / denom).coerceIn(minScale, maxScale)
    }
}

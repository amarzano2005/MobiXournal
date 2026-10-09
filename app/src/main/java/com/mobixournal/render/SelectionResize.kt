package com.mobixournal.render

/**
 * Which axes a selection-resize drag scales.
 *
 * A **corner** handle resizes proportionally ([BOTH]): the gesture's distance ratio drives both axes,
 * so a square stays square. An **edge** handle stretches a single axis ([X]/[Y]) — the
 * out-of-proportion resize, which is what lets a drawing be widened without being made taller.
 */
enum class ResizeAxis {
    /** Both axes by the same factor — a corner drag. */
    BOTH,

    /** Horizontal only: an element's width changes, its height does not. */
    X,

    /** Vertical only: an element's height changes, its width does not. */
    Y,
}

/**
 * The scale factor one axis of a resize drag applies, as a **ratio of pointer travel measured from the
 * fixed anchor**: `1.0` means the pointer has not moved along that axis, `2.0` means it now sits twice
 * as far from the anchor as it did when the drag started (so the selection doubles in that axis).
 *
 * Measuring from the anchor (rather than from the gesture's start point) is what keeps the opposite
 * edge pinned: the anchor *is* the fixed point of the transform, so a factor of 1 leaves that edge
 * exactly where it was however far the selection is dragged around.
 *
 * A start offset shorter than [MIN_SPAN_PT] has no trustworthy ratio (the pointer began on top of the
 * anchor), so it reports "no change" instead of an enormous factor. The result is clamped to
 * [DrawingSurfaceDefaults.MIN_RESIZE]…[DrawingSurfaceDefaults.MAX_RESIZE], which also absorbs a drag
 * that crosses the anchor: the axis collapses to the smallest allowed factor rather than flipping the
 * element inside out.
 *
 * Pure maths, so the axis behaviour is unit-testable without a `SurfaceView`.
 */
fun axisScaleFactor(startPt: Double, currentPt: Double, anchorPt: Double): Double {
    val start = startPt - anchorPt
    if (kotlin.math.abs(start) < MIN_AXIS_SPAN_PT) return 1.0
    return ((currentPt - anchorPt) / start)
        .coerceIn(DrawingSurfaceDefaults.MIN_RESIZE, DrawingSurfaceDefaults.MAX_RESIZE)
}

/** Shortest anchor-to-start offset (pt) a per-axis ratio is read from; below it the ratio is noise. */
private const val MIN_AXIS_SPAN_PT = 1e-3

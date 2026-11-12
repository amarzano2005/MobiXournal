package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement

/**
 * Which axes a selection-resize drag scales.
 *
 * A **corner** handle of a picture or a mixed selection resizes proportionally ([BOTH]): the gesture's
 * distance ratio drives both axes, so a square stays square. A corner of a single **figure** scales each
 * axis by its own ratio ([FREE]) — the proportions are the figure's to change. An **edge** handle
 * stretches a single axis ([X]/[Y]), the out-of-proportion resize.
 */
enum class ResizeAxis {
    /** Both axes by the same factor — a picture's or a mixed selection's corner drag. */
    BOTH,

    /** Each axis by its own pointer ratio — a figure's corner drag, free to change proportions. */
    FREE,

    /** Horizontal only: an element's width changes, its height does not. */
    X,

    /** Vertical only: an element's height changes, its width does not. */
    Y,

    /**
     * A **shaft**'s free resize: the dragged end follows the pointer, so the length and the inclination
     * change together about the end that stays. The pen's width is untouched (`SelectionEdit.similarity`).
     */
    SHAFT,
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

/**
 * Whether a corner drag changes the selection's proportions.
 *
 * A single **figure**'s does: a corner is how a drawing is widened without being made taller, and it is
 * what the window offers as well. A **picture**'s does not — a bitmap is resized, never reshaped — and
 * neither does a **mixed** selection ([element] null), where a free corner would tear the ink apart.
 */
fun cornerChangesProportions(element: Element?): Boolean = element != null && element !is ImageElement

/** Shortest anchor-to-start offset (pt) a per-axis ratio is read from; below it the ratio is noise. */
private const val MIN_AXIS_SPAN_PT = 1e-3

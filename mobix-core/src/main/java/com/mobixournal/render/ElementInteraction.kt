package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import kotlin.math.hypot

/** How pointer hits pick or grab an element's body. */
enum class HitMode {
    /** Only near the drawn curve/outline (hollow figures, splines, lines). */
    OUTLINE_ONLY,

    /** Anywhere inside the bounding box (images, tables, plots, text, circuits). */
    FILLED_BODY,

    /** Outline when hollow; outline or interior when the element carries a visible fill. */
    STROKE_OR_INTERIOR_IF_FILLED,
}

/** Which selection chrome and resize handles an element wears when selected alone. */
enum class ChromeMode {
    /** Bounding box with 4 corner handles + 4 X/Y edge-midpoint handles. */
    BOX_8_HANDLES,

    /** Bounding box with 4 proportional corner handles (e.g. images). */
    BOX_PROPORTIONAL_ONLY,

    /** No rectangle box; free vertex handles on the element's own corners. */
    FREE_VERTICES,

    /** No rectangle box; direct editable knots and control handles (splines). */
    DIRECT_NODES,

    /** No rectangle box; two endpoint handles on the shaft (lines and arrows). */
    SHAFT_ENDS,
}

/** Role an element plays in the magnetic pin snapping system. */
enum class PinRole {
    NONE,
    TERMINAL,
    ANCHOR,
    CONNECTOR,
}

/**
 * Unified interaction contract for every [Element] subtype across Android and Desktop.
 *
 * Adding a new [Element] subtype forces an explicit decision in [ElementInteraction.profileOf],
 * keeping hit-testing, handle chrome, aspect-lock support, and magnetic pin roles consistent
 * across both shells.
 */
data class ElementInteractionProfile(
    val hitMode: HitMode,
    val chromeMode: ChromeMode,
    val supportsAspectLock: Boolean,
    val pinRole: PinRole,
    val rotatable: Boolean,
)

/**
 * Shared selection, handle-visibility, and proximity-pin rules for both Android and Desktop.
 */
object ElementInteraction {

    /** Radius around the active pointer (in screen px) within which magnetic pins reveal. */
    const val PIN_REVEAL_RADIUS_PX = 84f

    /** Minimum box span (in screen px) required to draw X/Y edge-midpoint handles without crowding corners. */
    const val MIN_EDGE_HANDLE_SPAN_PX = 48f

    /**
     * Authoritative interaction profile for [element].
     *
     * Exhaustive over the sealed [Element] hierarchy so every future element family declares its
     * rules in one place.
     */
    fun profileOf(element: Element, aspectLocked: Boolean = true): ElementInteractionProfile = when (element) {
        is ParametricShapeElement -> ElementInteractionProfile(
            hitMode = HitMode.STROKE_OR_INTERIOR_IF_FILLED,
            chromeMode = if (aspectLocked) ChromeMode.BOX_8_HANDLES else ChromeMode.FREE_VERTICES,
            supportsAspectLock = true,
            pinRole = PinRole.ANCHOR,
            rotatable = true,
        )
        is SplineElement -> ElementInteractionProfile(
            hitMode = HitMode.STROKE_OR_INTERIOR_IF_FILLED,
            chromeMode = ChromeMode.DIRECT_NODES,
            supportsAspectLock = false,
            pinRole = PinRole.ANCHOR,
            rotatable = true,
        )
        is Stroke -> {
            val isShaft = shaftOf(element) != null
            val pinRole = when (FigurePins.pinFamilyOf(element)) {
                PinFamily.WIRE, PinFamily.ARROW -> PinRole.CONNECTOR
                PinFamily.FIGURE -> PinRole.ANCHOR
                PinFamily.CIRCUIT -> PinRole.TERMINAL
                null -> PinRole.NONE
            }
            ElementInteractionProfile(
                hitMode = if (isShaft) HitMode.OUTLINE_ONLY else HitMode.STROKE_OR_INTERIOR_IF_FILLED,
                chromeMode = if (isShaft) ChromeMode.SHAFT_ENDS else ChromeMode.BOX_8_HANDLES,
                supportsAspectLock = !isShaft,
                pinRole = pinRole,
                rotatable = true,
            )
        }
        is CircuitElement -> ElementInteractionProfile(
            hitMode = HitMode.FILLED_BODY,
            chromeMode = ChromeMode.BOX_8_HANDLES,
            supportsAspectLock = false,
            pinRole = PinRole.TERMINAL,
            rotatable = true,
        )
        is ImageElement -> ElementInteractionProfile(
            hitMode = HitMode.FILLED_BODY,
            chromeMode = ChromeMode.BOX_PROPORTIONAL_ONLY,
            supportsAspectLock = false,
            pinRole = PinRole.NONE,
            rotatable = false,
        )
        is TableElement, is PlotElement, is MathBlockElement, is TexImageElement, is TextElement -> ElementInteractionProfile(
            hitMode = HitMode.FILLED_BODY,
            chromeMode = ChromeMode.BOX_8_HANDLES,
            supportsAspectLock = false,
            pinRole = PinRole.NONE,
            rotatable = false,
        )
        is RawElement -> ElementInteractionProfile(
            hitMode = HitMode.FILLED_BODY,
            chromeMode = ChromeMode.BOX_8_HANDLES,
            supportsAspectLock = false,
            pinRole = PinRole.NONE,
            rotatable = false,
        )
    }

    /** Whether [element] carries a visible interior fill. */
    fun hasVisibleFill(element: Element): Boolean = when (element) {
        is ParametricShapeElement -> (element.fillColor?.let { (it ushr 24) and 0xFF } ?: 0) > 0
        is Stroke -> (element.fill ?: 0) > 0
        else -> false
    }

    /**
     * Whether a press at ([xPt], [yPt]) grabs [element] to move it.
     *
     * Hollow figures and strokes only grab near their outline so elements placed inside a frame
     * remain clickable without dragging the outer frame by mistake.
     */
    fun hitsMoveBody(element: Element, xPt: Double, yPt: Double, padPt: Double): Boolean {
        val profile = profileOf(element)
        return when (profile.hitMode) {
            HitMode.OUTLINE_ONLY -> ElementHitTester.hits(element, xPt, yPt, padPt)
            HitMode.FILLED_BODY -> ElementBounds.of(element)?.expand(padPt)?.contains(xPt, yPt) == true
            HitMode.STROKE_OR_INTERIOR_IF_FILLED -> {
                if (ElementHitTester.hits(element, xPt, yPt, padPt)) return true
                if (!hasVisibleFill(element)) return false
                when (element) {
                    is ParametricShapeElement -> if (ParametricShapes.isConic(element)) {
                        ParametricShapes.conicContains(element, xPt, yPt, padPt)
                    } else {
                        SelectionResizeMath.pointInPolygon(element.vertices, xPt, yPt)
                    }
                    else -> ElementBounds.of(element)?.expand(padPt)?.contains(xPt, yPt) == true
                }
            }
        }
    }

    /** Whether X/Y edge-midpoint handles should be shown for an axis of screen length [spanPx]. */
    fun showEdgeHandlesForSpan(spanPx: Float, flat: Boolean): Boolean =
        !flat && spanPx >= MIN_EDGE_HANDLE_SPAN_PX

    /** Filter [targets] to those within [revealRadiusPx] screen pixels of ([cursorXPt], [cursorYPt]). */
    fun filterTargetsByProximity(
        targets: List<CircuitSnapping.PinTarget>,
        cursorXPt: Double,
        cursorYPt: Double,
        pxPerPt: Float,
        revealRadiusPx: Float = PIN_REVEAL_RADIUS_PX,
    ): List<CircuitSnapping.PinTarget> {
        if (targets.isEmpty() || pxPerPt <= 0f) return emptyList()
        val radiusPt = (revealRadiusPx / pxPerPt).toDouble()
        return targets.filter { hypot(it.absX - cursorXPt, it.absY - cursorYPt) <= radiusPt }
    }

    /** Filter page-point pins to those within [revealRadiusPx] screen pixels of ([cursorXPt], [cursorYPt]). */
    fun filterPointsByProximity(
        points: List<Pair<Double, Double>>,
        cursorXPt: Double,
        cursorYPt: Double,
        pxPerPt: Float,
        revealRadiusPx: Float = PIN_REVEAL_RADIUS_PX,
    ): List<Pair<Double, Double>> {
        if (points.isEmpty() || pxPerPt <= 0f) return emptyList()
        val radiusPt = (revealRadiusPx / pxPerPt).toDouble()
        return points.filter { (x, y) -> hypot(x - cursorXPt, y - cursorYPt) <= radiusPt }
    }
}

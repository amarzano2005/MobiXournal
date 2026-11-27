package com.mobixournal.format.model

import com.mobixournal.format.json.JsonNode

/**
 * One knot along a [SplineElement], in page points.
 *
 * When [inX]/[inY] or [outX]/[outY] are null, `SplineInk` derives smooth Catmull-Rom tangents
 * from the neighbouring knots (or a corner when [smooth] is false). When present, the explicit
 * handle coordinates (in absolute page points) control the incoming or outgoing cubic Bézier
 * tangent directly.
 */
data class SplineNode(
    val x: Double,
    val y: Double,
    val inX: Double? = null,
    val inY: Double? = null,
    val outX: Double? = null,
    val outY: Double? = null,
    val smooth: Boolean = true,
    /** JSON keys a newer writer put on this node and this build did not read. */
    val unknown: Map<String, JsonNode> = emptyMap(),
)

/**
 * A scientific cubic Bézier / Catmull-Rom spline: a curve the editor keeps as editable knots and
 * optional Bézier handles instead of baking into sampled strokes.
 *
 * Tapping places smooth interpolating knots (`inX..outY == null`); dragging a knot or handle
 * extracts explicit cubic Bézier control points for asymmetric tension, inflection, or cusps.
 *
 * @param nodes Ordered knots along the curve, in page points.
 * @param closed Whether the last knot connects back to the first knot.
 * @param color Stroke colour as an ARGB int.
 * @param width Stroke width in points.
 * @param lineStyle Stroke pattern ([LineStyle.PLAIN] when absent on disk).
 * @param extraAttrs Attributes a later schema added, preserved for round-trip.
 * @param id The element's stable identity; see [ElementId].
 */
data class SplineElement(
    val nodes: List<SplineNode>,
    val closed: Boolean = false,
    val color: Int = DEFAULT_COLOR,
    val width: Double = DEFAULT_WIDTH,
    val lineStyle: LineStyle = LineStyle.PLAIN,
    val extraAttrs: Map<String, String> = emptyMap(),
    val id: String? = null,
    /** JSON keys a newer writer put on this spline and this build did not read. */
    val unknown: Map<String, JsonNode> = emptyMap(),
) : Element {
    companion object {
        const val DEFAULT_COLOR: Int = 0xFF000000.toInt()
        const val DEFAULT_WIDTH: Double = 1.4
    }
}

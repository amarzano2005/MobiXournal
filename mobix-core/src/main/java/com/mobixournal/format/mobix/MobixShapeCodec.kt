package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex

/**
 * The `parametricShape` element codec (`docs/mobix-elements.md`): a figure the editor keeps as
 * *geometry* instead of as the strokes a drawing would leave behind.
 *
 * Two decisions are the whole file:
 *
 *  - **The vertices are the truth and the box is derived.** `bounds` is written so a reader that only
 *    culls or hit-tests need not parse the vertices, and [decode] ignores it outright in favour of the
 *    vertices it read — a shape whose cached box disagreed with its own handles would otherwise be
 *    selectable where it is not (`docs/mobix-elements.md`, rule 3). A kind defined by an angle set
 *    keeps its exact angles by re-fitting these vertices on every drag, so there is exactly one place
 *    the figure's geometry lives.
 *  - **[ParametricShapeElement.shape] is an open string, not an enum.** The shape library grows; a kind
 *    this build has never heard of is still the user's figure, and only the vertex list has to be
 *    readable for it to be drawn and dragged. So this codec refuses on geometry and never on the kind's
 *    name.
 */
internal object MobixShapeCodec {

    /** The `type` discriminator this codec owns. */
    const val TYPE: String = "parametricShape"

    private const val VERTICES = "vertices"

    /** [shape] as JSON. */
    fun encode(shape: ParametricShapeElement): JsonNode.Obj = MobixJson.obj { out ->
        out["type"] = JsonNode.of(TYPE)
        shape.id?.let { out["id"] = JsonNode.of(it) }
        out["shape"] = JsonNode.of(shape.shape)
        extent(shape.vertices)?.let { out[MobixJson.BOUNDS] = it }
        out[VERTICES] = JsonNode.arr(shape.vertices.map(::encodeVertex))
        out["strokeColor"] = MobixJson.colour(shape.strokeColor)
        out["strokeWidth"] = JsonNode.of(shape.strokeWidth)
        shape.fillColor?.let { out["fillColor"] = MobixJson.colour(it) }
        out["showAngles"] = JsonNode.of(shape.showAngles)
        out["showSideLengths"] = JsonNode.of(shape.showSideLengths)
        if (shape.extraAttrs.isNotEmpty()) out[MobixJson.ATTRS] = MobixJson.attrsNode(shape.extraAttrs)
    }

    private fun encodeVertex(vertex: ShapeVertex): JsonNode = MobixJson.obj { out ->
        out["x"] = JsonNode.of(vertex.x)
        out["y"] = JsonNode.of(vertex.y)
    }

    /** The figure's own extent: the axis-aligned box around its vertices, or null when it has none. */
    private fun extent(vertices: List<ShapeVertex>): JsonNode? {
        if (vertices.isEmpty()) return null
        return MobixJson.box(
            left = vertices.minOf { it.x },
            top = vertices.minOf { it.y },
            right = vertices.maxOf { it.x },
            bottom = vertices.maxOf { it.y },
        )
    }

    /**
     * The shape [node] describes, or null when this build cannot read it whole — no vertices, or a
     * vertex that is not a point. An unknown `shape` name is *not* a refusal: it is a kind from a newer
     * build, and the geometry that defines it is right there.
     */
    fun decode(node: JsonNode.Obj, mint: (String) -> String = ElementId::mint): ParametricShapeElement? {
        val vertices = decodeVertices(node) ?: return null
        return ParametricShapeElement(
            shape = node.getStrOrNull("shape") ?: return null,
            vertices = vertices,
            strokeColor = MobixJson.colour(node, "strokeColor"),
            strokeWidth = node.getDouble("strokeWidth", 1.0),
            fillColor = MobixJson.colourOrNull(node, "fillColor"),
            showAngles = node.getBool("showAngles", false),
            showSideLengths = node.getBool("showSideLengths", false),
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull("id") ?: mint(ElementId.SHAPE),
        )
    }

    /** The vertices of [node]; null when the key is absent, empty, or holds anything but points. */
    private fun decodeVertices(node: JsonNode.Obj): List<ShapeVertex>? {
        val vertices = node.getArr(VERTICES)?.list ?: return null
        if (vertices.isEmpty()) return null
        val out = ArrayList<ShapeVertex>(vertices.size)
        for (vertex in vertices) {
            val obj = vertex as? JsonNode.Obj ?: return null
            out += ShapeVertex(
                x = obj.getDoubleOrNull("x") ?: return null,
                y = obj.getDoubleOrNull("y") ?: return null,
            )
        }
        return out
    }
}

package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode

/**
 * The `spline` element codec (`docs/mobix-elements.md`): a scientific cubic Bézier / Catmull-Rom
 * curve stored as editable knots and optional handle points.
 */
internal object MobixSplineCodec {

    const val TYPE: String = "spline"

    private const val NODES = "nodes"

    private val SPLINE_KEYS = setOf(
        "type", "id", NODES, "closed", "color", "width", "lineStyle", MobixJson.BOUNDS, MobixJson.ATTRS,
    )
    private val NODE_KEYS = setOf("x", "y", "inX", "inY", "outX", "outY", "smooth")

    fun encode(spline: SplineElement): JsonNode.Obj = MobixJson.obj { out ->
        out["type"] = JsonNode.of(TYPE)
        spline.id?.let { out["id"] = JsonNode.of(it) }
        extent(spline.nodes)?.let { out[MobixJson.BOUNDS] = it }
        out[NODES] = JsonNode.arr(spline.nodes.map(::encodeNode))
        if (spline.closed) out["closed"] = JsonNode.of(true)
        out["color"] = MobixJson.colour(spline.color)
        out["width"] = JsonNode.of(spline.width)
        if (spline.lineStyle != LineStyle.PLAIN) out["lineStyle"] = JsonNode.of(spline.lineStyle.name)
        if (spline.extraAttrs.isNotEmpty()) out[MobixJson.ATTRS] = MobixJson.attrsNode(spline.extraAttrs)
        MobixJson.putUnknown(out, spline.unknown)
    }

    private fun encodeNode(node: SplineNode): JsonNode = MobixJson.obj { out ->
        out["x"] = JsonNode.of(node.x)
        out["y"] = JsonNode.of(node.y)
        node.inX?.let { out["inX"] = JsonNode.of(it) }
        node.inY?.let { out["inY"] = JsonNode.of(it) }
        node.outX?.let { out["outX"] = JsonNode.of(it) }
        node.outY?.let { out["outY"] = JsonNode.of(it) }
        if (!node.smooth) out["smooth"] = JsonNode.of(false)
        MobixJson.putUnknown(out, node.unknown)
    }

    private fun extent(nodes: List<SplineNode>): JsonNode? {
        if (nodes.isEmpty()) return null
        return MobixJson.box(
            left = nodes.minOf { it.x },
            top = nodes.minOf { it.y },
            right = nodes.maxOf { it.x },
            bottom = nodes.maxOf { it.y },
        )
    }

    fun decode(node: JsonNode.Obj, mint: (String) -> String = ElementId::mint): SplineElement? {
        val nodes = decodeNodes(node) ?: return null
        val styleName = node.getStr("lineStyle", LineStyle.PLAIN.name)
        val lineStyle = LineStyle.entries.firstOrNull { it.name == styleName } ?: return null
        return SplineElement(
            nodes = nodes,
            closed = node.getBool("closed", false),
            color = MobixJson.colour(node, "color"),
            width = node.getDouble("width", SplineElement.DEFAULT_WIDTH),
            lineStyle = lineStyle,
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull("id") ?: mint(ElementId.SPLINE),
            unknown = MobixJson.unknownFields(node, SPLINE_KEYS),
        )
    }

    private fun decodeNodes(node: JsonNode.Obj): List<SplineNode>? {
        val array = node.getArr(NODES)?.list ?: return null
        if (array.isEmpty()) return null
        val out = ArrayList<SplineNode>(array.size)
        for (item in array) {
            val obj = item as? JsonNode.Obj ?: return null
            val x = obj.getDoubleOrNull("x") ?: return null
            val y = obj.getDoubleOrNull("y") ?: return null
            out += SplineNode(
                x = x,
                y = y,
                inX = obj.getDoubleOrNull("inX"),
                inY = obj.getDoubleOrNull("inY"),
                outX = obj.getDoubleOrNull("outX"),
                outY = obj.getDoubleOrNull("outY"),
                smooth = obj.getBool("smooth", true),
                unknown = MobixJson.unknownFields(obj, NODE_KEYS),
            )
        }
        return out
    }
}

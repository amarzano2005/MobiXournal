package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.PlotShading

/**
 * The `plot` element codec (`docs/mobix-elements.md`): the function plot `.xopp` can only flatten into
 * dead stroke paths plus text labels.
 *
 * What this codec owes the rest of the app is **everything the math engine needs to reproduce the
 * drawing** (`docs/math-engine.md`): each curve's kind and its own range, the sample count, the named
 * parameters a slider moves, the display flags and the integral bounds. So a formula is never
 * re-entered after a save — the file holds the expression, not the pixels it produced.
 *
 * `summaryText` is the exception, and is deliberately written *as* an exception: it is the engine's
 * already-formatted analysis (roots, extrema, inflections), a cache for a reader that will not run the
 * math. Nothing needs it to redraw the plot, so dropping it loses nothing and regenerating it costs a
 * re-evaluation.
 *
 * [decode] follows the same rule as every living-entity codec: it models a plot or preserves it whole.
 * An unreadable `kind`, a `parameters` object holding something that is not a number, a
 * `shadeIntegral` that is not a box — the element goes back to the caller unmodelled and is kept
 * verbatim, because half-reading a plot would mean saving a document whose formula changed.
 */
internal object MobixPlotCodec {

    /** The `type` discriminator this codec owns. */
    const val TYPE: String = "plot"

    private const val CURVES = "curves"
    private const val SHADE_INTEGRAL = "shadeIntegral"

    /** [plot] as JSON. */
    fun encode(plot: PlotElement): JsonNode.Obj = MobixJson.obj { out ->
        out["type"] = JsonNode.of(TYPE)
        plot.id?.let { out["id"] = JsonNode.of(it) }
        out[MobixJson.BOUNDS] = MobixJson.box(plot.left, plot.top, plot.right, plot.bottom)
        out["xMin"] = JsonNode.of(plot.xMin)
        out["xMax"] = JsonNode.of(plot.xMax)
        out["samples"] = JsonNode.of(plot.samples)
        out["showCriticalPoints"] = JsonNode.of(plot.showCriticalPoints)
        out["includeDerivative"] = JsonNode.of(plot.includeDerivative)
        plot.shadeIntegral?.let { out[SHADE_INTEGRAL] = encodeShading(it) }
        out[CURVES] = JsonNode.arr(plot.curves.map(::encodeCurve))
        // Absent rather than empty: a plot with no sliders and a plot with an empty slider map are the
        // same plot, and only one of the two spellings needs to exist.
        if (plot.parameters.isNotEmpty()) out["parameters"] = encodeParameters(plot.parameters)
        out["axisColor"] = MobixJson.colour(plot.axisColor)
        plot.summaryText?.let { out["summaryText"] = JsonNode.of(it) }
        if (plot.extraAttrs.isNotEmpty()) out[MobixJson.ATTRS] = MobixJson.attrsNode(plot.extraAttrs)
    }

    private fun encodeShading(shading: PlotShading): JsonNode = MobixJson.obj { out ->
        out["from"] = JsonNode.of(shading.from)
        out["to"] = JsonNode.of(shading.to)
    }

    private fun encodeParameters(parameters: Map<String, Double>): JsonNode = MobixJson.obj { out ->
        parameters.forEach { (name, value) -> out[name] = JsonNode.of(value) }
    }

    /**
     * A curve as JSON. Only the range the curve's [PlotKind] actually uses is written, so a Cartesian
     * curve never carries a parameter range that would mean nothing.
     */
    private fun encodeCurve(curve: PlotCurve): JsonNode = MobixJson.obj { out ->
        out["kind"] = JsonNode.of(curve.kind.name)
        out["source"] = JsonNode.of(curve.source)
        curve.sourceY?.let { out["sourceY"] = JsonNode.of(it) }
        out["color"] = MobixJson.colour(curve.color)
        out["strokeWidth"] = JsonNode.of(curve.strokeWidth)
        out["lineStyle"] = JsonNode.of(curve.lineStyle.name)
        curve.tMin?.let { out["tMin"] = JsonNode.of(it) }
        curve.tMax?.let { out["tMax"] = JsonNode.of(it) }
        curve.thetaMin?.let { out["thetaMin"] = JsonNode.of(it) }
        curve.thetaMax?.let { out["thetaMax"] = JsonNode.of(it) }
    }

    /** The plot [node] describes, or null when this build cannot read it whole. */
    fun decode(node: JsonNode.Obj, mint: (String) -> String = ElementId::mint): PlotElement? {
        val box = MobixJson.readBox(node) ?: return null
        val curves = decodeCurves(node) ?: return null
        val parameters = decodeParameters(node) ?: return null
        // Absent means "no shading"; present but not a box means the element is not one this build can
        // read — the two cases are different facts and must not collapse into the same empty answer.
        val shading = if (!node.has(SHADE_INTEGRAL)) null else {
            val obj = node.getObj(SHADE_INTEGRAL) ?: return null
            PlotShading(from = obj.getDouble("from", 0.0), to = obj.getDouble("to", 0.0))
        }
        return PlotElement(
            left = box.left,
            top = box.top,
            right = box.right,
            bottom = box.bottom,
            curves = curves,
            xMin = node.getDouble("xMin", PlotElement.DEFAULT_X_MIN),
            xMax = node.getDouble("xMax", PlotElement.DEFAULT_X_MAX),
            samples = node.getInt("samples", PlotElement.DEFAULT_SAMPLES),
            showCriticalPoints = node.getBool("showCriticalPoints", false),
            includeDerivative = node.getBool("includeDerivative", false),
            shadeIntegral = shading,
            parameters = parameters,
            axisColor = MobixJson.colourOrNull(node, "axisColor") ?: PlotElement.DEFAULT_AXIS,
            summaryText = node.getStrOrNull("summaryText"),
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull("id") ?: mint(ElementId.PLOT),
        )
    }

    /** The curves of [node]; null when one of them is not a curve this build can read. */
    private fun decodeCurves(node: JsonNode.Obj): List<PlotCurve>? {
        val curves = node.getArr(CURVES)?.list ?: return emptyList()
        val out = ArrayList<PlotCurve>(curves.size)
        for (curve in curves) {
            val obj = curve as? JsonNode.Obj ?: return null
            out += decodeCurve(obj) ?: return null
        }
        return out
    }

    private fun decodeCurve(node: JsonNode.Obj): PlotCurve? {
        val kindName = node.getStrOrNull("kind") ?: return null
        val kind = PlotKind.entries.firstOrNull { it.name == kindName } ?: return null
        val styleName = node.getStr("lineStyle", LineStyle.PLAIN.name)
        val style = LineStyle.entries.firstOrNull { it.name == styleName } ?: return null
        val source = node.getStrOrNull("source") ?: return null
        return PlotCurve(
            kind = kind,
            source = source,
            color = MobixJson.colour(node),
            strokeWidth = node.getDouble("strokeWidth", 1.0),
            lineStyle = style,
            sourceY = node.getStrOrNull("sourceY"),
            tMin = node.getDoubleOrNull("tMin"),
            tMax = node.getDoubleOrNull("tMax"),
            thetaMin = node.getDoubleOrNull("thetaMin"),
            thetaMax = node.getDoubleOrNull("thetaMax"),
        )
    }

    /** The sliders' constants; an empty map when absent, null when the values are not numbers. */
    private fun decodeParameters(node: JsonNode.Obj): Map<String, Double>? {
        if (!node.has("parameters")) return emptyMap()
        val obj = node.getObj("parameters") ?: return null
        val out = LinkedHashMap<String, Double>(obj.map.size)
        for ((name, value) in obj.map) out[name] = (value as? JsonNode.Num)?.value ?: return null
        return out
    }
}

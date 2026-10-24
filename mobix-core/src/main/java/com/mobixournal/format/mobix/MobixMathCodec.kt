package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.MathFlavor

/**
 * The `.mobix` spelling of a living math block (`docs/mobix-elements.md`): its **source** in its own
 * language, its colour and its box.
 *
 * There is deliberately no rendered payload here. A formula's drawing is a function of its source,
 * so a file that carried a bitmap would carry two answers to one question — and the stale one would
 * be the one a reader sees. The canvas (through the LaTeX renderer) and the exports (through
 * [com.mobixournal.render.MathBlockInk]) derive the drawing from [MathBlockElement.source] alone.
 *
 * `flavor` is omitted for LaTeX, the default and the language a desktop Xournal++ `<teximage>`
 * carries; a Typst source is written explicitly so a reader never guesses which language it is.
 */
object MobixMathCodec {

    /** The `type` value this codec owns. */
    const val TYPE: String = "mathBlock"

    private const val SOURCE = "source"
    private const val FLAVOR = "flavor"
    private const val COLOR = "color"

    fun encode(block: MathBlockElement): JsonNode.Obj = MobixJson.obj { out ->
        out["type"] = JsonNode.of(TYPE)
        block.id?.let { out["id"] = JsonNode.of(it) }
        out[SOURCE] = JsonNode.of(block.source)
        if (block.flavor != MathFlavor.LATEX) out[FLAVOR] = JsonNode.of(block.flavor.name)
        out[COLOR] = MobixJson.colour(block.color)
        out[MobixJson.BOUNDS] = MobixJson.box(block.left, block.top, block.right, block.bottom)
        if (block.extraAttrs.isNotEmpty()) out[MobixJson.ATTRS] = MobixJson.attrsNode(block.extraAttrs)
    }

    /**
     * The block [node] describes, or null when the object is not one this build can read **whole** —
     * a missing source or box falls back to the element codec's verbatim preservation rather than
     * inventing a formula at the page origin.
     */
    fun decode(node: JsonNode.Obj, mint: (String) -> String): MathBlockElement? {
        val source = node.getStrOrNull(SOURCE) ?: return null
        val box = MobixJson.readBox(node) ?: return null
        val flavor = node.getStrOrNull(FLAVOR)?.let { name ->
            MathFlavor.entries.firstOrNull { it.name == name } ?: return null
        } ?: MathFlavor.LATEX
        val id = node.getStrOrNull("id") ?: mint(com.mobixournal.format.model.ElementId.MATH)
        return MathBlockElement(
            left = box.left,
            top = box.top,
            right = box.right,
            bottom = box.bottom,
            source = source,
            color = MobixJson.colour(node, COLOR),
            flavor = flavor,
            extraAttrs = MobixJson.readAttrs(node),
            id = id,
        )
    }
}

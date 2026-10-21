package com.mobixournal.format.mobix

import com.mobixournal.format.XoppColor
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonWriter

/**
 * The handful of JSON shapes every `.mobix` codec builds and reads — a builder that keeps key order, a
 * `bounds` box, the preserved-`attrs` object, and a colour spelled the way `.xopp` spells it.
 *
 * It exists so five codecs do not each carry their own copy of the same four helpers: the element
 * codec, the page codec and the four living-entity codecs all write the same object shape, and the
 * escaping/order rules of the format layer have one home (`format/json/`) with the *building* rules
 * having one home here.
 */
object MobixJson {

    /** A JSON object built field by field, in the order the writer will emit it. */
    fun obj(fill: (LinkedHashMap<String, JsonNode>) -> Unit): JsonNode.Obj =
        JsonNode.Obj(LinkedHashMap<String, JsonNode>().also(fill))

    /** The `bounds` object for a rectangle, in the order the schema writes it. */
    fun box(left: Double, top: Double, right: Double, bottom: Double): JsonNode = obj { out ->
        out["left"] = JsonNode.of(left)
        out["top"] = JsonNode.of(top)
        out["right"] = JsonNode.of(right)
        out["bottom"] = JsonNode.of(bottom)
    }

    /**
     * The box [node] carries under `bounds`, or null when it has none.
     *
     * A reader **re-derives** a box wherever the element's geometry determines it (`docs/mobix-elements.md`,
     * rule 3): this only reads the one an element's placement *is*, never a cached extent.
     */
    fun readBox(node: JsonNode.Obj, key: String = BOUNDS): MobixBox? {
        val box = node.getObj(key) ?: return null
        return MobixBox(
            left = box.getDouble("left", 0.0),
            top = box.getDouble("top", 0.0),
            right = box.getDouble("right", 0.0),
            bottom = box.getDouble("bottom", 0.0),
        )
    }

    /** The `attrs` object holding the `.xopp` attributes the bridge must not lose. */
    fun attrsNode(attrs: Map<String, String>): JsonNode = obj { out ->
        attrs.forEach { (key, value) -> out[key] = JsonNode.of(value) }
    }

    /** The preserved `attrs` of [node], as strings: a non-string value is kept as its JSON text. */
    fun readAttrs(node: JsonNode.Obj): Map<String, String> =
        node.getObj(ATTRS)?.map
            ?.mapValues { (_, value) -> (value as? JsonNode.Str)?.value ?: JsonWriter.write(value) }
            ?: emptyMap()

    /** A JSON array of numbers, in order. */
    fun numbers(values: Iterable<Double>): JsonNode = JsonNode.arr(values.map { JsonNode.of(it) })

    /** The numbers of the array at [key], or null when the key is absent or is not an array. */
    fun numberList(node: JsonNode.Obj, key: String): List<Double>? =
        node.getArr(key)?.list?.mapNotNull { (it as? JsonNode.Num)?.value }

    /** The string at [key] as an array element handler-friendly list, or null when absent. */
    fun stringList(node: JsonNode.Obj, key: String): List<String>? = node.getStrList(key)

    /** A colour spelled the way `.xopp` and this format both spell it (`#RRGGBBAA`). */
    fun colour(value: Int): JsonNode = JsonNode.of(XoppColor.format(value))

    /** The ARGB colour at [key], opaque black when it is missing or unreadable. */
    fun colour(node: JsonNode.Obj, key: String = "color"): Int = XoppColor.parse(node.getStrOrNull(key))

    /**
     * The ARGB colour at [key], or null when the key is absent — which is a different fact from a
     * colour that happens to be black, and the reason the callers that have a *default* colour need
     * this rather than [colour].
     */
    fun colourOrNull(node: JsonNode.Obj, key: String): Int? =
        node.getStrOrNull(key)?.let(XoppColor::parse)

    /** The field name every element type uses for its preserved `.xopp` attributes. */
    const val ATTRS: String = "attrs"

    /** The field name every element type uses for its box. */
    const val BOUNDS: String = "bounds"
}

/**
 * A rectangle on a page, in points — the shape `bounds` takes everywhere in the schema.
 *
 * Which of its four numbers are *facts* depends on the element: an image's box is the placement the
 * user gave it, while a table's or a shape's box is derived from geometry that is stored anyway, and a
 * reader recomputes those rather than believing the numbers in the file (`docs/mobix-elements.md`,
 * rule 3).
 */
data class MobixBox(val left: Double, val top: Double, val right: Double, val bottom: Double)

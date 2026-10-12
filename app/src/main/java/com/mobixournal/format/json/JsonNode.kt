package com.mobixournal.format.json

/**
 * The app's own JSON value model: dependency-free, `internal` to the module, and the single home for
 * how a JSON document is held in memory.
 *
 * It lives here, beside the format layer, because JSON is a **format** concern: it arrived as the
 * settings-backup text format (see `ui/AppSettingsBackup.kt`, which reads it through these types) and
 * the native `.mobix` container is JSON throughout (`docs/mobix-schema-spec.md`). Two parsers for the
 * same grammar would be two places for the same escaping bug.
 *
 * [Obj] preserves **key order** (a `LinkedHashMap`), which the `.mobix` codecs rely on: the preserved
 * fields of an element are re-emitted in the order the file had them, so a round trip through this app
 * does not reshuffle a document it did not change.
 */
internal sealed interface JsonNode {
    data class Obj(val map: Map<String, JsonNode>) : JsonNode {
        fun getStr(key: String, fallback: String): String = (map[key] as? Str)?.value ?: fallback
        fun getStrOrNull(key: String): String? = (map[key] as? Str)?.value
        fun getBool(key: String, fallback: Boolean): Boolean = (map[key] as? Bool)?.value ?: fallback
        fun getInt(key: String, fallback: Int): Int = (map[key] as? Num)?.value?.toLong()?.toInt() ?: fallback
        fun getIntOrNull(key: String): Int? = (map[key] as? Num)?.value?.toLong()?.toInt()
        fun getFloat(key: String, fallback: Float): Float = (map[key] as? Num)?.value?.toFloat() ?: fallback
        fun getFloatOrNull(key: String): Float? = (map[key] as? Num)?.value?.toFloat()
        fun getDouble(key: String, fallback: Double): Double = (map[key] as? Num)?.value ?: fallback
        fun getDoubleOrNull(key: String): Double? = (map[key] as? Num)?.value
        fun getObj(key: String): Obj? = map[key] as? Obj
        fun getArr(key: String): Arr? = map[key] as? Arr
        fun getStrList(key: String): List<String>? =
            getArr(key)?.list?.mapNotNull { (it as? Str)?.value }
        fun getFloatList(key: String): List<Float>? =
            getArr(key)?.list?.mapNotNull { (it as? Num)?.value?.toFloat() }
        fun getIntList(key: String): List<Int>? =
            getArr(key)?.list?.mapNotNull { (it as? Num)?.value?.toLong()?.toInt() }

        /** The value at [key], whatever its type — null both for a missing key and for a JSON `null`. */
        fun value(key: String): JsonNode? = map[key]?.takeIf { it !is Null }

        /** Whether [key] is present at all, JSON `null` included: "absent" and "null" differ. */
        fun has(key: String): Boolean = key in map
    }

    data class Arr(val list: List<JsonNode>) : JsonNode
    data class Str(val value: String) : JsonNode
    data class Num(val value: Double) : JsonNode
    data class Bool(val value: Boolean) : JsonNode
    data object Null : JsonNode

    companion object {
        /** Shorthand for a string node. */
        fun of(value: String): JsonNode = Str(value)

        /** Shorthand for a numeric node. */
        fun of(value: Number): JsonNode = Num(value.toDouble())

        /** Shorthand for a boolean node. */
        fun of(value: Boolean): JsonNode = Bool(value)

        /** An object node from [pairs], in the order given — the order the writer will emit. */
        fun obj(vararg pairs: Pair<String, JsonNode?>): Obj =
            Obj(LinkedHashMap<String, JsonNode>().also { out ->
                for ((key, value) in pairs) if (value != null) out[key] = value
            })

        /** An array node from [values]. */
        fun arr(values: Iterable<JsonNode>): Arr = Arr(values.toList())
    }
}

package com.mobixournal.format.json

/**
 * Serialises a [JsonNode] tree — the write half of the format layer's JSON, beside the parser so the
 * escaping rules have exactly one home.
 *
 * Two deliberate decisions, both of which the `.mobix` round-trip tests depend on:
 *
 *  - **Compact, with no whitespace at all.** A `.mobix` page is data, not something a human reads in
 *    a text editor, and a document is written on every save: the bytes saved are flash cycles and
 *    battery. Key order is [JsonNode.Obj]'s own, so "compact" never means "reordered".
 *  - **An integral number is written without a fraction** (`1`, not `1.0`), because the schema mixes
 *    counts, page numbers and millisecond stamps with real coordinates, and emitting `"samples": 300.0`
 *    would be a lie about the field's type. A value that is not finite has no JSON spelling and is
 *    written as `null` rather than as the invalid `NaN`/`Infinity` tokens.
 *
 * What a re-write does **not** preserve is the source's own spelling of a number (`1.0`, `1e3`) or its
 * whitespace. Values, types and key order survive; formatting does not — so "preserved verbatim" in
 * `docs/mobix-schema-spec.md` means the subtree's *value*, and the archive-level guarantee of
 * byte-identical preservation applies to whole entries, not to the JSON inside them.
 */
object JsonWriter {

    /** [node] as compact JSON. */
    fun write(node: JsonNode): String = StringBuilder().also { append(node, it) }.toString()

    /** Append [node] to [out] as compact JSON. */
    fun append(node: JsonNode, out: StringBuilder) {
        when (node) {
            is JsonNode.Obj -> {
                out.append('{')
                var first = true
                for ((key, value) in node.map) {
                    if (!first) out.append(',')
                    first = false
                    quote(key, out)
                    out.append(':')
                    append(value, out)
                }
                out.append('}')
            }
            is JsonNode.Arr -> {
                out.append('[')
                node.list.forEachIndexed { index, value ->
                    if (index > 0) out.append(',')
                    append(value, out)
                }
                out.append(']')
            }
            is JsonNode.Str -> quote(node.value, out)
            is JsonNode.Num -> out.append(number(node.value))
            is JsonNode.Bool -> out.append(if (node.value) "true" else "false")
            JsonNode.Null -> out.append("null")
        }
    }

    /**
     * A double as a JSON number that means the same value: an integer when it is one, `null` for the
     * values JSON cannot express (NaN, ±Infinity), and otherwise the JVM's shortest round-tripping
     * spelling — which for a very small or very large magnitude is exponent notation (`1.0E-4`), legal
     * JSON that re-parses to the same number.
     */
    fun number(value: Double): String = when {
        !value.isFinite() -> "null"
        value == Math.floor(value) && Math.abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

    /** A JSON string literal, quotes included, with the escapes JSON requires. */
    fun quote(value: String, out: StringBuilder) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else ->
                    // Every other C0 control character must be escaped; anything else goes through
                    // as UTF-8, so a document's own language survives unaltered.
                    if (c < ' ') out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else out.append(c)
            }
        }
        out.append('"')
    }
}

/** [node] as compact JSON — the call site's shorthand for `JsonWriter.write(this)`. */
fun JsonNode.toJson(): String = JsonWriter.write(this)

/** [text] as a JSON string literal, quotes included. */
internal fun jsonString(text: String): String = StringBuilder().also { JsonWriter.quote(text, it) }.toString()

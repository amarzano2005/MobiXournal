package com.mobixournal.format.json

/**
 * Dependency-free JSON tokenizer and parser, producing a [JsonNode] tree.
 *
 * Deliberately **tolerant**, like the XML layer beside it: a malformed value degrades to `null` or a
 * default rather than throwing, because both of its callers (a settings backup, a `.mobix` document)
 * are files a user picked and the alternative is a crash on somebody else's bytes. Where a structural
 * answer matters — "is this even the document type I write?" — the caller checks the parsed tree
 * itself and refuses, the way `AppSettingsBackup.validate` does.
 *
 * Object keys keep their source order, which the `.mobix` writer depends on for preserved fields.
 */
internal object JsonParser {
    fun parse(input: String): JsonNode {
        var pos = 0
        val len = input.length

        fun skipWhitespace() {
            while (pos < len && input[pos].isWhitespace()) pos++
        }

        fun parseString(): String {
            pos++ // skip leading quote
            val sb = StringBuilder()
            while (pos < len) {
                val c = input[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < len) {
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= len) {
                                val hex = input.substring(pos, pos + 4)
                                pos += 4
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun parseValue(): JsonNode {
            skipWhitespace()
            if (pos >= len) return JsonNode.Null
            return when (val c = input[pos]) {
                '{' -> {
                    pos++
                    val map = LinkedHashMap<String, JsonNode>()
                    skipWhitespace()
                    if (pos < len && input[pos] == '}') {
                        pos++
                        JsonNode.Obj(map)
                    } else {
                        while (pos < len) {
                            skipWhitespace()
                            if (pos >= len || input[pos] != '"') break
                            val key = parseString()
                            skipWhitespace()
                            if (pos < len && input[pos] == ':') pos++
                            val value = parseValue()
                            map[key] = value
                            skipWhitespace()
                            if (pos < len && input[pos] == ',') {
                                pos++
                            } else if (pos < len && input[pos] == '}') {
                                pos++
                                break
                            } else {
                                pos++
                            }
                        }
                        JsonNode.Obj(map)
                    }
                }
                '[' -> {
                    pos++
                    val list = mutableListOf<JsonNode>()
                    skipWhitespace()
                    if (pos < len && input[pos] == ']') {
                        pos++
                        JsonNode.Arr(list)
                    } else {
                        while (pos < len) {
                            list += parseValue()
                            skipWhitespace()
                            if (pos < len && input[pos] == ',') {
                                pos++
                            } else if (pos < len && input[pos] == ']') {
                                pos++
                                break
                            } else {
                                pos++
                            }
                        }
                        JsonNode.Arr(list)
                    }
                }
                '"' -> JsonNode.Str(parseString())
                't', 'f' -> {
                    if (input.startsWith("true", pos)) { pos += 4; JsonNode.Bool(true) }
                    else if (input.startsWith("false", pos)) { pos += 5; JsonNode.Bool(false) }
                    else { pos++; JsonNode.Null }
                }
                'n' -> {
                    if (input.startsWith("null", pos)) pos += 4
                    else pos++
                    JsonNode.Null
                }
                else -> {
                    val start = pos
                    if (c == '-' || c == '+' || c.isDigit()) {
                        pos++
                        while (pos < len && (input[pos].isDigit() || input[pos] == '.' || input[pos] == 'e' || input[pos] == 'E' || input[pos] == '-' || input[pos] == '+')) {
                            pos++
                        }
                        val numStr = input.substring(start, pos)
                        val num = numStr.toDoubleOrNull() ?: 0.0
                        JsonNode.Num(num)
                    } else {
                        pos++
                        JsonNode.Null
                    }
                }
            }
        }

        return parseValue()
    }
}

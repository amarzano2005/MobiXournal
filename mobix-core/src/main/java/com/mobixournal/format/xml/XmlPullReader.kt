package com.mobixournal.format.xml

/**
 * A minimal pull parser for the `.xopp` subset (no namespaces, no DTD, no CDATA). Emits
 * [Event.START], [Event.END], [Event.TEXT] and [Event.EOF]. Self-closing tags emit START then
 * END. Attribute insertion order is preserved. Comments, the XML declaration, and doctype are
 * skipped. Kept dependency-free so the same code runs on-device and in JVM unit tests.
 */
class XmlPullReader(reader: java.io.Reader, private val onComment: (String) -> Unit = {}) {
    constructor(xml: String) : this(java.io.StringReader(xml))
    private val s = XmlInput(reader)

    /** Thrown when the input ends mid-tag (interrupted write, partial download). */
    class TruncatedXmlException(message: String) : IllegalArgumentException(message)

    enum class Event { START, END, TEXT, EOF }

    var event: Event = Event.EOF
        private set
    var name: String = ""
        private set
    var text: String = ""
        private set

    private val attrs = LinkedHashMap<String, String>()
    private var pos = if (s.startsWith("\uFEFF")) 1 else 0
    private var pendingEndName: String? = null
    private val openNames = ArrayDeque<String>()

    fun attr(name: String): String? = attrs[name]
    fun attributes(): Map<String, String> = attrs

    /**
     * The offset in the source just past the last event consumed. Paired with [rawSlice] it lets a
     * caller keep an unrecognised element's markup verbatim instead of re-serialising it.
     */
    val position: Int get() = pos

    /** The pinned source between marks. Call [beginCapture] before advancing beyond the first mark. */
    fun beginCapture(): Int { s.pinned = true; return pos }

    fun rawSlice(from: Int, to: Int): String {
        val result = if (from in 0..to) s.substring(from, to) else ""
        s.pinned = false
        return result
    }

    var eventStart: Int = 0
        private set

    fun next(): Event {
        s.discardBefore(pos)
        eventStart = pos
        pendingEndName?.let {
            pendingEndName = null
            name = it
            closeTag(it)
            event = Event.END
            return event
        }
        if (!s.has(pos)) {
            if (openNames.isNotEmpty()) truncated("unclosed tag '${openNames.last()}'")
            event = Event.EOF
            return event
        }
        return if (s[pos] == '<') readTag() else readText()
    }

    private fun readText(): Event {
        val lt = s.indexOf('<', pos)
        val endText = if (lt < 0) {
            var end = pos
            while (s.has(end)) end++
            end
        } else lt
        val raw = s.substring(pos, endText)
        pos = endText
        text = decodeEntities(raw)
        event = Event.TEXT
        return event
    }

    private fun readTag(): Event {
        // pos points at '<'.
        when {
            s.startsWith("<?", pos) -> { skipUntil("?>"); return next() }
            s.startsWith("<!--", pos) -> {
                val end = s.indexOf("-->", pos)
                if (end < 0) truncated("missing comment terminator")
                onComment(s.substring(pos + 4, end))
                pos = end + 3
                return next()
            }
            s.startsWith("<!", pos) -> { skipUntil(">"); return next() }
            s.startsWith("</", pos) -> return readEndTag()
            else -> return readStartTag()
        }
    }

    private fun readEndTag(): Event {
        val gt = s.indexOf('>', pos)
        if (gt < 0) truncated("unterminated end tag")
        name = s.substring(pos + 2, gt).trim()
        closeTag(name)
        pos = gt + 1
        event = Event.END
        return event
    }

    private fun readStartTag(): Event {
        attrs.clear()
        var i = pos + 1
        val nameStart = i
        while (s.has(i) && !isNameEnd(s[i])) i++
        name = s.substring(nameStart, i)
        require(name.isNotEmpty()) { "Invalid XML: empty tag name" }
        // Attributes.
        while (true) {
            while (s.has(i) && s[i].isWhitespace()) i++
            if (!s.has(i)) break
            val c = s[i]
            if (c == '/' || c == '>') break
            val attrNameStart = i
            while (s.has(i) && s[i] != '=' && !s[i].isWhitespace()) i++
            val attrName = s.substring(attrNameStart, i)
            while (s.has(i) && (s[i].isWhitespace() || s[i] == '=')) i++
            if (!s.has(i)) truncated("unterminated attribute '$attrName'")
            val quote = s[i]
            require(quote == '\'' || quote == '"') { "Invalid XML: unquoted attribute '$attrName'" }
            i++
            val valStart = i
            while (s.has(i) && s[i] != quote) i++
            if (!s.has(i)) truncated("unterminated attribute value for '$attrName'")
            val value = s.substring(valStart, i)
            i++ // consume closing quote
            attrs[attrName] = decodeEntities(value)
        }
        val selfClosing = s.has(i) && s[i] == '/'
        val gt = s.indexOf('>', i)
        if (gt < 0) truncated("unterminated start tag '$name'")
        pos = gt + 1
        openNames.addLast(name)
        if (selfClosing) pendingEndName = name
        event = Event.START
        return event
    }

    /** End parsing with a controlled error; leaves the reader at EOF so nothing loops. */
    private fun truncated(what: String): Nothing {
        event = Event.EOF
        throw TruncatedXmlException("Truncated XML: $what")
    }

    private fun skipUntil(marker: String) {
        val idx = s.indexOf(marker, pos)
        if (idx < 0) truncated("missing '$marker'")
        pos = idx + marker.length
    }

    private fun closeTag(name: String) {
        require(openNames.lastOrNull() == name) { "Invalid XML: unexpected closing tag '$name'" }
        openNames.removeLast()
    }

    private fun isNameEnd(c: Char): Boolean = c.isWhitespace() || c == '>' || c == '/'

    private companion object {
        fun decodeEntities(s: String): String {
            if ('&' !in s) return s
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '&') {
                    val semi = s.indexOf(';', i)
                    if (semi > i) {
                        when (val ent = s.substring(i + 1, semi)) {
                            "amp" -> sb.append('&')
                            "lt" -> sb.append('<')
                            "gt" -> sb.append('>')
                            "quot" -> sb.append('"')
                            "apos" -> sb.append('\'')
                            else -> {
                                // Malformed numeric entities (empty, non-numeric, overflowing,
                                // out-of-range, surrogate) fall back to the raw text, as the
                                // unknown-named-entity branch does.
                                val code = if (ent.startsWith("#")) {
                                    if (ent.startsWith("#x") || ent.startsWith("#X"))
                                        ent.substring(2).toIntOrNull(16)
                                    else ent.substring(1).toIntOrNull()
                                } else {
                                    null
                                }
                                if (code != null && code in 0..0x10FFFF && code !in 0xD800..0xDFFF) {
                                    sb.appendCodePoint(code)
                                } else {
                                    sb.append(s, i, semi + 1)
                                }
                            }
                        }
                        i = semi + 1
                        continue
                    }
                }
                sb.append(c)
                i++
            }
            return sb.toString()
        }
    }
}

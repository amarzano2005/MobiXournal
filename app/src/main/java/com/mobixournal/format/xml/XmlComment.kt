package com.mobixournal.format.xml

/**
 * The rules for reading and writing a **payload carried in an XML comment** — which is the one thing a
 * comment may not contain literally, and the reason this is code rather than a string append.
 *
 * A comment is `<!-- … -->`, and XML forbids `--` inside it: a payload holding a double hyphen (a
 * formula `x--1`, a label, any JSON string with two dashes next to each other) would produce a file
 * that a conforming parser — desktop Xournal++, on the other side of this bridge — refuses to open.
 * The payload is therefore **escaped on the way in and unescaped on the way out**; the block is a
 * payload, never text to be read as-is.
 *
 * The escape is one rule, chosen so it cannot be got wrong: **every `-` becomes `\-`**, so no `--` can
 * survive and no comment can end on a hyphen. Unescaping is its exact inverse, and it does not touch
 * the payload's own backslashes (a JSON string holding `\\-` comes back as `\\-`, because the scan
 * consumes a backslash only when a hyphen follows it).
 *
 * [extract] is a scan of the raw document text, not a parser event: the `.xopp` reader skips comments by
 * design ([XmlPullReader]), so the caller that wants one asks for it by its tag.
 */
object XmlComment {

    /** The opening of the payload's comment, up to and including the tag. */
    private const val OPEN = "<!--"

    private const val CLOSE = "-->"

    /** [text] escaped so it can sit inside a comment: every hyphen becomes `\-`. */
    fun escape(text: String): String =
        if ('-' !in text) text else text.replace("-", "\\-")

    /** The inverse of [escape]: every `\-` becomes `-`, and every other character is left alone. */
    fun unescape(text: String): String {
        if ("\\-" !in text) return text
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '\\' && text.getOrNull(index + 1) == '-') {
                out.append('-')
                index += 2
            } else {
                out.append(text[index])
                index++
            }
        }
        return out.toString()
    }

    /**
     * The payload of the first `<!-- [tag] … -->` comment in [xml], unescaped, or null when the document
     * carries no such comment (or carries one with nothing in it).
     *
     * A comment whose tag is followed by something other than whitespace is a different comment — `mobix`
     * and `mobix:meta` must not be able to claim each other's payload.
     */
    fun extract(xml: String, tag: String): String? {
        var from = 0
        while (true) {
            val start = xml.indexOf(OPEN, from) 
            if (start < 0) return null
            val bodyStart = start + OPEN.length
            val body = xml.substring(bodyStart)
            val afterTag = body.trimStart()
            val indent = body.length - afterTag.length
            if (afterTag.startsWith(tag) && afterTag.getOrNull(tag.length)?.isWhitespace() == true) {
                val payloadStart = bodyStart + indent + tag.length
                val end = xml.indexOf(CLOSE, payloadStart)
                if (end < 0) return null
                return unescape(xml.substring(payloadStart, end).trim())
            }
            from = bodyStart
        }
    }
}

package com.mobixournal.format

import com.mobixournal.format.model.Document
import com.mobixournal.format.xml.XmlComment
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Top-level `.xopp` I/O: gzip on the outside, XML within. This is the whole round-trip surface
 * the app builds on. Uses only the JDK's `java.util.zip` — no third-party format libraries (see
 * `docs/xopp-format.md`).
 */
object Xopp {

    /**
     * The tag of the comment a `.mobix` **bridge block** rides in (`Document.bridge`):
     * `<!-- mobix:meta {…} -->`. Desktop Xournal++ ignores it and may drop it on its own save, which is
     * why the block is a claim to be verified rather than trusted (`docs/mobix-format.md`).
     */
    const val BRIDGE_COMMENT: String = "mobix:meta"

    /** Read a `.xopp` stream (gzip + XML) into a [Document]. Does not close [input]. */
    fun open(input: InputStream): Document {
        val xml = GZIPInputStream(input).reader(Charsets.UTF_8).use { it.readText() }
        return parseXml(xml)
    }

    /**
     * Parse decompressed XML directly (used by tests and drift checks).
     *
     * The XML reader skips comments, so the bridge block — if the file carries one — is taken from the
     * text here: it is a payload beside the document, not part of it (`Document.bridge`).
     */
    fun parseXml(xml: String): Document =
        XoppReader(xml).read().copy(bridge = XmlComment.extract(xml, BRIDGE_COMMENT))

    /** Serialise a [Document] to `.xopp` bytes (XML + gzip) on [output]. Does not close it. */
    fun save(doc: Document, output: OutputStream) {
        GZIPOutputStream(output).use { gz ->
            gz.writer(Charsets.UTF_8).use { w -> XoppWriter(w).write(doc) }
        }
    }

    /** Serialise a [Document] to XML text (no gzip) — for tests and drift checks. */
    fun toXml(doc: Document): String = StringBuilder().also { XoppWriter(it).write(doc) }.toString()
}

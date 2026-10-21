package com.mobixournal.format.xml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/mobix-format.md` gives the bridge block an XML comment to ride in, and a payload that may hold
 * anything a user typed. The rules for that — a comment may not contain `--`, and a reader has to find
 * the right comment among whatever else a file holds — live in [XmlComment], and this is where they are
 * pinned: an escape that lets one `-` through produces a file desktop Xournal++ refuses to open, which
 * is the one failure mode here that a round trip through *our own* reader would not catch.
 */
class XmlCommentTest {

    @Test
    fun escapingLeavesNoDoubleHyphen() {
        val escaped = XmlComment.escape("f = 1 / T; x---1; --")

        assertEquals("every hyphen is escaped, so no -- survives", "f = 1 / T; x\\-\\-\\-1; \\-\\-", escaped)
        assertFalse(escaped.contains("--"))
    }

    @Test
    fun unescapingUndoesItExactly() {
        val nasty = """{"source":"x--1","label":"a-\-b","trail":"--"}"""

        assertEquals(nasty, XmlComment.unescape(XmlComment.escape(nasty)))
    }

    @Test
    fun aPayloadWithoutHyphensIsUntouched() {
        val payload = """{"version":1,"elements":[]}"""

        assertEquals(payload, XmlComment.escape(payload))
        assertEquals(payload, XmlComment.unescape(payload))
    }

    @Test
    fun extractFindsTheTaggedCommentAmongOthers() {
        val xml = """
            <?xml version="1.0" standalone="no"?>
            <xournal creator="MobiXournal" fileversion="4">
            <!-- written by something else -->
            <title>x</title>
            <!-- mobix:meta {"version":1,"elements":[]} -->
            </xournal>
        """.trimIndent()

        assertEquals("""{"version":1,"elements":[]}""", XmlComment.extract(xml, "mobix:meta"))
    }

    @Test
    fun extractUnescapesWhatItFinds() {
        val xml = "<xournal><!-- mobix:meta {\"a\":\"x\\-\\-1\"} --></xournal>"

        assertEquals("""{"a":"x--1"}""", XmlComment.extract(xml, "mobix:meta"))
    }

    @Test
    fun extractDoesNotClaimAnotherCommentsPayload() {
        val xml = "<xournal><!-- mobix:metadata {\"other\":true} --><!-- mobix {\"third\":true} --></xournal>"

        assertNull("a tag that is only a prefix of ours is not ours", XmlComment.extract(xml, "mobix:meta"))
    }

    @Test
    fun extractIsNullWhenThereIsNoTaggedComment() {
        assertNull(XmlComment.extract("<xournal><title>x</title></xournal>", "mobix:meta"))
        assertNull("an unterminated comment has no payload", XmlComment.extract("<!-- mobix:meta {", "mobix:meta"))
    }

    @Test
    fun theWriterEscapesWhatItEmits() {
        val out = StringBuilder()
        XmlWriter(out).comment("mobix:meta", """{"source":"x--1"}""")

        assertEquals("""<!-- mobix:meta {"source":"x\-\-1"} -->""", out.toString())
        assertTrue("the comment is well-formed", out.toString().startsWith("<!-- ") && out.toString().endsWith(" -->"))
        assertFalse(out.toString().substring(5, out.length - 4).contains("--"))
    }
}

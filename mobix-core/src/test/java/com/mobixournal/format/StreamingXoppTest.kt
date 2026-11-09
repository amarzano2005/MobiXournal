package com.mobixournal.format

import com.mobixournal.format.xml.XmlPullReader
import java.io.Reader
import org.junit.Assert.*
import org.junit.Test

class StreamingXoppTest {
    private class Chunked(private val source: String) : Reader() {
        var position = 0
        var closed = false
        override fun read(out: CharArray, offset: Int, length: Int): Int {
            if (position == source.length) return -1
            out[offset] = source[position++]
            return 1
        }
        override fun close() { closed = true }
    }
    @Test fun chunksPreserveUnknownMarkupUnicodeAndBridgeComments() {
        val xml = "\uFEFF<xournal><page width=\"100\" height=\"200\"><layer>" +
            "<future a='&amp;'>before<!-- nested -->\u00E9\uD83D\uDE00<child/>after<!-- tail --></future>" +
            "</layer></page></xournal><!-- mobix:meta {\"x\":1} -->"
        val reader = Chunked(xml)
        val doc = Xopp.parseXml(reader)
        assertEquals("{\"x\":1}", doc.bridge)
        val raw = doc.pages[0].layers[0].elements.single() as com.mobixournal.format.model.RawElement
        assertEquals("before<!-- nested -->\u00E9\uD83D\uDE00<child/>after<!-- tail -->", raw.body)
        assertFalse(reader.closed)
    }
    @Test fun firstEventDoesNotReadTheWholeInput() {
        val xml = "<xournal>" + "<page/>".repeat(10000) + "</xournal>"
        val reader = Chunked(xml)
        val parser = XmlPullReader(reader)
        assertEquals(XmlPullReader.Event.START, parser.next())
        assertTrue(reader.position < 100)
    }
}

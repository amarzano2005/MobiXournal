package com.mobixournal.format

import com.mobixournal.format.model.TextElement
import org.junit.Assert.*
import org.junit.Test

class AuditFormatIntegrityTest {
    @Test fun acceptsAByteOrderMarkBeforeTheXmlDeclaration() {
        assertTrue(XoppReader("\uFEFF<?xml version=\"1.0\"?><xournal/>").read().pages.isEmpty())
    }

    @Test fun refusesIncompleteAndInvalidDocuments() {
        val invalid = listOf(
            "", "<other/>", "<xournal>", "<xournal><page><layer>",
            "<xournal><page></xournal>", "<xournal/><!-- unfinished",
            "<xournal/><?unfinished", "<xournal/><xournal/>",
            "<xournal><page><layer><text>keep me",
        )
        invalid.forEach { xml -> assertThrows(xml, IllegalArgumentException::class.java) { XoppReader(xml).read() } }
    }

    @Test fun preservesWhitespaceInsideTextAcrossRoundTrip() {
        val xml = "<xournal>\n<page width=\"100\" height=\"100\"><layer>" +
            "<text font=\"Sans\" size=\"12\" x=\"0\" y=\"0\" color=\"black\">   \n\t</text>" +
            "</layer></page></xournal>"
        val document = XoppReader(xml).read()
        assertEquals("   \n\t", (document.pages.single().layers.single().elements.single() as TextElement).content)
        val written = StringBuilder().also { XoppWriter(it).write(document) }.toString()
        assertEquals(document, XoppReader(written).read())
    }
}

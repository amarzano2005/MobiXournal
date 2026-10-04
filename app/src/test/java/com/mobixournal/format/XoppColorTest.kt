package com.mobixournal.format

import org.junit.Assert.assertEquals
import org.junit.Test

class XoppColorTest {

    @Test fun parsesRrggbbaaAlphaLast() {
        assertEquals(0xFF000000.toInt(), XoppColor.parse("#000000ff"))
        assertEquals(0xFFFFFFFF.toInt(), XoppColor.parse("#ffffffff"))
        // Half-transparent pure red: RGBA=ff000080 -> ARGB=80ff0000.
        assertEquals(0x80FF0000.toInt(), XoppColor.parse("#ff000080"))
    }

    @Test fun parsesSixDigitAsOpaque() {
        assertEquals(0xFF112233.toInt(), XoppColor.parse("#112233"))
    }

    @Test fun parsesNamedColors() {
        assertEquals(0xFF000000.toInt(), XoppColor.parse("black"))
        assertEquals(0xFFFFFFFF.toInt(), XoppColor.parse("white"))
    }

    @Test fun `named colours match desktop Xournalpp's predefined colours`() {
        // Upstream PREDEFINED_COLORS (XmlParserHelper.cpp) -> Colors:: (util/Color.h).
        assertEquals(0xFF3333CC.toInt(), XoppColor.parse("blue"))       // xopp_royalblue
        assertEquals(0xFF00C0FF.toInt(), XoppColor.parse("lightblue"))  // xopp_deepskyblue
        assertEquals(0xFF00FF00.toInt(), XoppColor.parse("lightgreen")) // lime
        assertEquals(0xFFFF8000.toInt(), XoppColor.parse("orange"))     // xopp_darkorange
        assertEquals(0xFF008000.toInt(), XoppColor.parse("green"))
        assertEquals(0xFF808080.toInt(), XoppColor.parse("gray"))
        assertEquals(0xFFFF00FF.toInt(), XoppColor.parse("magenta"))
        assertEquals(0xFFFF0000.toInt(), XoppColor.parse("red"))
        assertEquals(0xFFFFFF00.toInt(), XoppColor.parse("yellow"))
    }

    @Test fun formatIsAlphaLast() {
        assertEquals("#000000ff", XoppColor.format(0xFF000000.toInt()))
        assertEquals("#ff000080", XoppColor.format(0x80FF0000.toInt()))
    }

    @Test fun malformedHexFallsBackToOpaqueBlack() {
        assertEquals(0xFF000000.toInt(), XoppColor.parse("#zzzzzz"))
        assertEquals(0xFF000000.toInt(), XoppColor.parse("#12 34 56"))
        assertEquals(0xFF000000.toInt(), XoppColor.parse("#zzzzzzzz"))
        assertEquals(0xFF000000.toInt(), XoppColor.parse("#12345 78"))
        assertEquals(0xFF000000.toInt(), XoppColor.parse("#-12345"))
    }

    @Test fun roundTripsThroughFormatAndParse() {
        for (argb in listOf(0xFF000000.toInt(), 0x8012AB34.toInt(), 0xFFABCDEF.toInt())) {
            assertEquals(argb, XoppColor.parse(XoppColor.format(argb)))
        }
    }
}

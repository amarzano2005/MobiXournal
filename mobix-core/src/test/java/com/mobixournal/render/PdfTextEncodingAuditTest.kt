package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the vector PDF export does with text it cannot encode — measured, not assumed.
 *
 * `PdfPagePlan.winAnsi` guards text with a **code range** (`0x20..0xFF`) rather than with an encoding,
 * and every text path into the export goes through it: `text`, `mathBlock` and `tex` all sanitise their
 * lines there before an emitter ever sees them. That makes the guard both too strict (WinAnsi encodes
 * `…`, `–`, `€` and their neighbours above U+00FF) and too permissive (WinAnsi's `0x80` is `€`, and
 * `showText` throws on a codepoint its font cannot map), and silent in both directions: the page
 * exports, the text is gone or wrong.
 *
 * These are **characterisation** tests. They pin today's behaviour so a fix has to change them
 * deliberately rather than drift, and they are the fixture `pdf-unicode-text-ops` must turn into a
 * round-trip assertion that the produced PDF both draws and extracts these characters.
 */
class PdfTextEncodingAuditTest {

    private val height = 842.0
    private val black = 0xFF000000.toInt()
    private val transform = PdfPageTransform(0f, 0f, height.toFloat())

    /** The lines a single text box contributes to the plan — the last thing before an emitter. */
    private fun lines(content: String): List<String> = PdfPagePlan.of(
        Page(
            width = 595.27559100,
            height = height,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(
                Layer(
                    listOf(
                        TextElement(font = "Sans", size = 12.0, x = 72.0, y = 96.0, color = black, content = content),
                    ),
                ),
            ),
        ),
        transform,
    ).filterIsInstance<PdfText>().single().lines

    @Test
    fun `latin-1 survives, accents and vulgar fractions included`() {
        assertEquals(listOf("café ñ ½ ²"), lines("café ñ ½ ²"))
    }

    @Test
    fun `scripts outside latin-1 are replaced`() {
        assertEquals("Greek", listOf("?"), lines("α"))
        assertEquals("Cyrillic", listOf("?"), lines("Ж"))
    }

    @Test
    fun `a subscript and a combining mark are lost while their base character stays`() {
        assertEquals(listOf("H?"), lines("H₂"))
        assertEquals(listOf("e?"), lines("e\u0301"))
    }

    @Test
    fun `an astral character costs one substitute per surrogate half`() {
        assertEquals(listOf("??"), lines("😀"))
    }

    @Test
    fun `every cp1252 character above U+00FF is dropped, though WinAnsi carries each one`() {
        // … – — ‘ ’ “ ” € ™ Š ž ƒ ‹ › † ‡ • ‰ : all present in WinAnsi, all lost by the range test.
        val cp1252 = "\u2026\u2013\u2014\u2018\u2019\u201C\u201D\u20AC\u2122\u0160\u017E\u0192" +
            "\u2039\u203A\u2020\u2021\u2022\u2030"
        assertEquals("?".repeat(18), lines(cp1252).single())
    }

    @Test
    fun `a control character in the cp1252 range passes through to a different glyph`() {
        // WinAnsi's 0x80 is €, so this is not a drop but a substitution the guard cannot see: the
        // emitter either draws a currency sign or throws inside showText.
        assertEquals("\u0080", PdfPagePlan.winAnsi("\u0080"))
        assertEquals(listOf("\u0080"), lines("\u0080"))
    }
}

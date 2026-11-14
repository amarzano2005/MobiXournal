package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the export does with text a **base-14** font cannot encode — which is now a choice of face rather
 * than a character filter.
 *
 * `PdfPagePlan` used to decide this with a code range (`0x20..0xFF`) and rewrite the text before an
 * emitter ever saw it, so the guard was both too strict (WinAnsi **does** carry `…`, `–`, `€`, `™` and
 * their neighbours above U+00FF) and too permissive (WinAnsi's `0x80` is `€`, so a C1 control printed as
 * a currency sign) — and silent in both directions. The plan keeps the characters and names a face
 * instead: [PdfFontRef.Base14] while every character encodes in [WinAnsi], [PdfFontRef.Bundled] the
 * moment one does not, so the run is typeset in DejaVu rather than sanitised.
 *
 * What a bundled run *draws* is the emitter's half, measured over real PDFBox output in the app's
 * `PdfUnicodeExportTest`; this is the decision, and the pin that a run WinAnsi carries plans exactly what
 * it planned before.
 */
class PdfTextEncodingAuditTest {

    private val height = 842.0
    private val black = 0xFF000000.toInt()
    private val transform = PdfPageTransform(0f, 0f, height.toFloat())

    /** The single text op a box of [content] contributes to the plan, in [font]'s face. */
    private fun run(content: String, font: String = "Sans"): PdfText = PdfPagePlan.of(
        Page(
            width = 595.27559100,
            height = height,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(
                Layer(
                    listOf(
                        TextElement(font = font, size = 12.0, x = 72.0, y = 96.0, color = black, content = content),
                    ),
                ),
            ),
        ),
        transform,
    ).filterIsInstance<PdfText>().single()

    @Test
    fun `latin-1 survives, accents and vulgar fractions included`() {
        val op = run("café ñ ½ ²")
        assertEquals("the characters reach the emitter as written", listOf("café ñ ½ ²"), op.lines)
        assertEquals(PdfFontRef.Base14(PdfFontFace(PdfFamily.SANS, bold = false, italic = false)), op.font)
    }

    @Test
    fun `every cp1252 character above U+00FF is WinAnsi, so the run keeps its base-14 face`() {
        // … – — ‘ ’ “ ” € ™ Š ž ƒ ‹ › † ‡ • ‰ : all present in WinAnsi, all lost by the old range test.
        val cp1252 = "\u2026\u2013\u2014\u2018\u2019\u201C\u201D\u20AC\u2122\u0160\u017E\u0192" +
            "\u2039\u203A\u2020\u2021\u2022\u2030"
        val op = run(cp1252)
        assertEquals("none of them becomes a question mark", listOf(cp1252), op.lines)
        assertTrue("and none of them needed the bundled face", op.font is PdfFontRef.Base14)
    }

    @Test
    fun `a script outside latin-1 asks for the bundled face instead of a substitute`() {
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), run("α").font)
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), run("Ж").font)
        assertEquals("and the character itself is still there", listOf("α"), run("α").lines)
    }

    @Test
    fun `a subscript, a combining mark and an astral character all ask for the bundled face`() {
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), run("H₂").font)
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), run("e\u0301").font)
        val emoji = run("😀")
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), emoji.font)
        assertEquals("the pair is not split into two substitutes here", listOf("😀"), emoji.lines)
    }

    @Test
    fun `a control character is not WinAnsi, so it never prints as a currency sign`() {
        assertFalse(WinAnsi.encodes('\u0080'))
        assertFalse(WinAnsi.encodes('\u007F'))
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), run("\u0080").font)
    }

    @Test
    fun `a monospace run keeps its bundle's mono face`() {
        assertEquals(
            PdfFontRef.Bundled(PdfBundledFace.MONOSPACE),
            run("α", font = "Courier").font,
        )
    }

    @Test
    fun `the face is chosen per run, not per page`() {
        val page = Page(
            width = 595.27559100,
            height = height,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(
                Layer(
                    listOf(
                        TextElement(font = "Sans", size = 12.0, x = 72.0, y = 96.0, color = black, content = "plain latin"),
                        TextElement(font = "Sans", size = 12.0, x = 72.0, y = 196.0, color = black, content = "α"),
                    ),
                ),
            ),
        )
        val runs = PdfPagePlan.of(page, transform).filterIsInstance<PdfText>()
        assertTrue("the Latin line stays base-14", runs[0].font is PdfFontRef.Base14)
        assertEquals(PdfFontRef.Bundled(PdfBundledFace.PROPORTIONAL), runs[1].font)
    }

    @Test
    fun `a pure-winansi run plans exactly what it planned before the change`() {
        // The pin: same lines, same baseline, same size — and a base-14 face, so the produced PDF carries
        // no font file for it and is the file it always was.
        val op = run("Hello")
        assertEquals(
            PdfText(
                lines = listOf("Hello"),
                font = PdfFontRef.Base14(PdfFontFace(PdfFamily.SANS, bold = false, italic = false)),
                size = 12.0,
                colour = black,
                x = 72f,
                y = 842f - (96.0 + 12.0 * PdfPagePlan.ASCENT_RATIO).toFloat(),
                lineHeight = 12.0 * PdfPagePlan.LINE_HEIGHT_RATIO,
            ),
            op,
        )
    }
}

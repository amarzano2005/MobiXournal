package com.mobixournal.desktop.previews

import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSInteger
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList

/**
 * **The letters of a PDF whose font programs are not in the file.**
 *
 * An export can name an embedded TrueType subset and then not write the program it points at — a truncated
 * download, a half-finished LibreOffice export, a file shrunk by a tool that dropped the tail objects. Such a
 * PDF keeps its text operators, its `/Widths` and its `/ToUnicode`, so every glyph sits at the right advance:
 * the page's layout comes out exactly right and the letters come out as whatever a substitute font has at
 * those codes — a slide of `#`, `%`, `&` and `(` scattered over a table that reads perfectly. Nothing is
 * wrong with the rasteriser, so no amount of rendering hygiene fixes it.
 *
 * The repair is to stop asking for a program the file does not have. Each such font becomes a **standard
 * Helvetica** whose `/Differences` names come from the font's own `/ToUnicode` (code → character → Adobe glyph
 * name), with the original `/Widths` left in place: PDFBox then draws the right characters at the original
 * advances. The typeface changes — Noto Sans becomes Helvetica — and a code the `/ToUnicode` does not name
 * stays blank, which is honest: those characters are not recoverable from this file.
 *
 * Applied per page and before any font of that page has been parsed, because PDFBox reads a font's encoding
 * when it builds the font object, not at draw time.
 */
internal object PdfFontRepair {

    private val glyphList = GlyphList.getAdobeGlyphList()

    /** Repair every damaged font of [page]; answers how many were rebuilt. */
    fun repair(page: PDPage): Int {
        val fonts = page.resources.cosObject.getDictionaryObject(COSName.FONT) as? COSDictionary ?: return 0
        var repaired = 0
        fonts.keySet().forEach { key ->
            val dict = fonts.getDictionaryObject(key) as? COSDictionary ?: return@forEach
            if (repair(dict)) repaired++
        }
        return repaired
    }

    private fun repair(dict: COSDictionary): Boolean {
        if (dict.getCOSName(COSName.SUBTYPE) != COSName.TRUE_TYPE) return false
        if (dict.containsKey(COSName.BASE_FONT).not()) return false
        val descriptor = dict.getDictionaryObject(COSName.FONT_DESC) as? COSDictionary ?: return false
        val program = listOf(COSName.FONT_FILE2, COSName.FONT_FILE, COSName.FONT_FILE3)
            .firstNotNullOfOrNull { descriptor.getDictionaryObject(it) }
        // A program that is there is a program we draw with; only a font the file promised and did not
        // deliver is ours to rebuild.
        if (program is COSStream) return false
        val toUnicode = dict.getDictionaryObject(COSName.TO_UNICODE) as? COSStream ?: return false
        val cmap = String(toUnicode.createInputStream().use { it.readBytes() }, Charsets.ISO_8859_1)
        val differences = COSArray()
        CODES.findAll(cmap).forEach { match ->
            val code = match.groupValues[1].toInt(16)
            val characters = match.groupValues[2].chunked(4)
                .mapNotNull { it.toIntOrNull(16)?.toChar() }
                .joinToString("")
            val name = characters.firstOrNull()?.let { glyphList.codePointToName(it.code) } ?: return@forEach
            differences.add(COSInteger.get(code.toLong()))
            differences.add(COSName.getPDFName(name))
        }
        if (differences.size() == 0) return false
        val encoding = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.ENCODING)
            setItem(COSName.BASE_ENCODING, COSName.WIN_ANSI_ENCODING)
            setItem(COSName.DIFFERENCES, differences)
        }
        dict.setItem(COSName.SUBTYPE, COSName.TYPE1)
        dict.setName(COSName.BASE_FONT, "Helvetica")
        dict.setItem(COSName.ENCODING, encoding)
        dict.removeItem(COSName.FONT_DESC)
        dict.removeItem(COSName.TO_UNICODE)
        return true
    }

    /** A `bfchar` pair of a ToUnicode CMap: `<code> <unicode>`. */
    private val CODES = Regex("<([0-9A-Fa-f]{2,4})>\\s*<([0-9A-Fa-f]{4,8})>")
}

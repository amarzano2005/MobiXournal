package com.mobixournal.desktop.previews

import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSInteger
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList

/**
 * **The letters of a PDF whose font programs are not in the file.**
 *
 * An export can name an embedded TrueType subset and then not write the program it points at — a truncated
 * download, a half-finished LibreOffice export, a file shrunk by a tool that dropped the tail objects. Such a
 * PDF keeps its text operators, its `/Widths` and its `/ToUnicode`, so every glyph sits at the right advance:
 * the page's layout comes out exactly right and the letters come out as whatever a substitute font has at
 * those codes — a slide of `#`, `%`, `&` and `(` scattered over a table that reads perfectly.
 *
 * **Nothing of the page is rebuilt.** The content stream, the vectors, the images and the colours are the
 * file's own; the only object this touches is the font dictionary that names a program the file does not
 * carry. That is the same move every viewer makes when it substitutes a font (the reader has no other choice:
 * the glyphs are not in the file), done with two things a plain substitution throws away:
 *
 * - the file's `/ToUnicode` names the characters, so the letters are the document's and not a guess at what
 *   some code meant in some other encoding; and
 * - the file's `/Widths` stay, so every advance is the original one and no line moves.
 *
 * The replacement face is the bundled DejaVu when the family is one we carry ([PdfDejaVuFaces]) — for this
 * kind of export, the same letterforms and metrics as the original — and a base-14 face of the same style
 * otherwise. The repair happens before PDFBox parses a font of that page, because a font's encoding is read
 * when the font object is built, not when it is drawn.
 */
internal object PdfFontRepair {

    private val glyphList = GlyphList.getAdobeGlyphList()

    /** A `bfchar` pair of a ToUnicode CMap: `<code> <unicode>`. */
    private val CODES = Regex("<([0-9A-Fa-f]{2,4})>\\s*<([0-9A-Fa-f]{4,8})>")

    /** Repair every damaged font of [page]; answers how many were rebuilt. */
    fun repair(document: PDDocument, page: PDPage): Int {
        val fonts = page.resources.cosObject.getDictionaryObject(COSName.FONT) as? COSDictionary ?: return 0
        var repaired = 0
        fonts.keySet().forEach { key ->
            val dict = fonts.getDictionaryObject(key) as? COSDictionary ?: return@forEach
            if (repair(document, dict)) repaired++
        }
        return repaired
    }

    private fun repair(document: PDDocument, dict: COSDictionary): Boolean {
        if (dict.getCOSName(COSName.SUBTYPE) != COSName.TRUE_TYPE) return false
        val baseFont = dict.getNameAsString(COSName.BASE_FONT) ?: return false
        val program = programOf(dict)
        // A program that is there is a program we draw with; only a font the file promised and did not
        // deliver is ours to rebuild.
        if (program is COSStream) return false
        val toUnicode = dict.getDictionaryObject(COSName.TO_UNICODE) as? COSStream ?: return false
        val differences = differencesOf(toUnicode)
        if (differences.size() == 0) return false
        val face = PdfDejaVuFaces.faceFor(baseFont)
        if (face != null) embed(document, dict, baseFont, face, differences) else substitute(dict, baseFont, differences)
        return true
    }

    /** Rebuild around the bundled face: the program becomes ours, the widths and the rest stay the file's. */
    private fun embed(
        document: PDDocument,
        dict: COSDictionary,
        baseFont: String,
        face: com.mobixournal.render.PdfBundledFace,
        differences: COSArray,
    ) {
        val name = PdfDejaVuFaces.nameOf(face)
        val descriptor = dict.getDictionaryObject(COSName.FONT_DESC) as? COSDictionary
            ?: COSDictionary().also { dict.setItem(COSName.FONT_DESC, it) }
        descriptor.setItem(COSName.FONT_FILE2, PdfDejaVuFaces.streamFor(document, face))
        // Nonsymbolic: the glyph is the one our Differences name, looked up by Unicode in the face's cmap.
        // The original subset was flagged symbolic because its codes were private.
        descriptor.setInt(COSName.FLAGS, 32)
        descriptor.setName(COSName.FONT_NAME, name)
        dict.setName(COSName.BASE_FONT, name)
        dict.setItem(COSName.ENCODING, encodingOf(differences))
        dict.removeItem(COSName.TO_UNICODE)
    }

    /** No face of that family here: the base-14 stand-in every viewer would pick, in the same style. */
    private fun substitute(dict: COSDictionary, baseFont: String, differences: COSArray) {
        dict.setItem(COSName.SUBTYPE, COSName.TYPE1)
        dict.setName(COSName.BASE_FONT, standIn(baseFont))
        dict.setItem(COSName.ENCODING, encodingOf(differences))
        dict.removeItem(COSName.FONT_DESC)
        dict.removeItem(COSName.TO_UNICODE)
    }

    private fun encodingOf(differences: COSArray): COSDictionary = COSDictionary().apply {
        setItem(COSName.TYPE, COSName.ENCODING)
        setItem(COSName.BASE_ENCODING, COSName.WIN_ANSI_ENCODING)
        setItem(COSName.DIFFERENCES, differences)
    }

    /** The font's declared program, or null when the descriptor names none (or there is no descriptor). */
    private fun programOf(dict: COSDictionary): Any? {
        val descriptor = dict.getDictionaryObject(COSName.FONT_DESC) as? COSDictionary ?: return null
        return listOf(COSName.FONT_FILE2, COSName.FONT_FILE, COSName.FONT_FILE3)
            .firstNotNullOfOrNull { descriptor.getDictionaryObject(it) }
    }

    /** Code → glyph name, from the font's own ToUnicode map: `<01> <0043>` becomes `1 /C`. */
    private fun differencesOf(toUnicode: COSStream): COSArray {
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
        return differences
    }

    /**
     * The base-14 face that stands in for a font of this name — a slide's weight, slant and spacing are part
     * of what it says. Rebuilding every damaged font as plain Helvetica lost exactly those: a bold heading
     * came out thin, an italic note upright, and a monospaced listing proportional and visibly stretched.
     */
    private fun standIn(baseFont: String): String {
        val name = baseFont.substringAfter('+').lowercase()
        val bold = "bold" in name || "black" in name || "heavy" in name
        val italic = "italic" in name || "oblique" in name
        val serif = "serif" in name || "times" in name || "roman" in name
        return when {
            "mono" in name || "courier" in name -> when {
                bold && italic -> "Courier-BoldOblique"
                bold -> "Courier-Bold"
                italic -> "Courier-Oblique"
                else -> "Courier"
            }

            serif && bold && italic -> "Times-BoldItalic"
            serif && bold -> "Times-Bold"
            serif && italic -> "Times-Italic"
            serif -> "Times-Roman"
            bold && italic -> "Helvetica-BoldOblique"
            bold -> "Helvetica-Bold"
            italic -> "Helvetica-Oblique"
            else -> "Helvetica"
        }
    }
}

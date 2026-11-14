package com.mobixournal.render

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.encoding.GlyphList

/**
 * **The letters of a PDF whose font programs are not in the file** — the tablet's half of
 * `desktop/.../PdfFontRepair`, which states the diagnosis and the rule in full (`docs/pdf.md`).
 *
 * An export can name an embedded TrueType subset and not write the program it points at. The text operators,
 * the `/Widths` and the `/ToUnicode` survive, so the layout is right and the letters are whatever a
 * substitute has at those codes. Nothing of the page is rebuilt: the content stream, the vectors and the
 * images are the file's own, and only the font dictionary that promises a program the file does not carry is
 * replaced — the same move every viewer makes when it substitutes, done with the file's own `/ToUnicode`
 * names and its own `/Widths`, so the letters are the document's and no advance moves.
 *
 * The replacement is the bundled DejaVu face the name asks for when the family is one the app carries
 * ([PdfBundledFace]), and a base-14 face of the same style otherwise. [program] supplies a bundled face's
 * bytes (the asset manager in the app, a file in a test); when it is null the base-14 path is the only one.
 */
internal object PdfFontRepair {

    private val glyphList = GlyphList.getAdobeGlyphList()

    /** A `bfchar` pair of a ToUnicode CMap: `<code> <unicode>`. */
    private val CODES = Regex("<([0-9A-Fa-f]{2,4})>\\s*<([0-9A-Fa-f]{4,8})>")

    /** Repair every damaged font of [page]; answers how many were rebuilt. */
    fun repair(
        document: PDDocument,
        page: PDPage,
        program: ((PdfBundledFace) -> ByteArray)?,
    ): Int {
        val fonts = page.resources.cosObject.getDictionaryObject(COSName.FONT) as? COSDictionary ?: return 0
        var repaired = 0
        for (key in fonts.keySet()) {
            val dict = fonts.getDictionaryObject(key) as? COSDictionary ?: continue
            if (repair(document, dict, program)) repaired++
        }
        return repaired
    }

    /** Whether any font of [page] names a program the file does not carry. */
    fun isDamaged(page: PDPage): Boolean {
        val fonts = page.resources.cosObject.getDictionaryObject(COSName.FONT) as? COSDictionary ?: return false
        return fonts.keySet().any { key ->
            val dict = fonts.getDictionaryObject(key) as? COSDictionary
            dict != null && needsRepair(dict)
        }
    }

    private fun needsRepair(dict: COSDictionary): Boolean {
        if (dict.getCOSName(COSName.SUBTYPE) != COSName.TRUE_TYPE) return false
        if (dict.getNameAsString(COSName.BASE_FONT) == null) return false
        if (programOf(dict) is COSStream) return false
        return dict.getDictionaryObject(COSName.TO_UNICODE) is COSStream
    }

    private fun repair(
        document: PDDocument,
        dict: COSDictionary,
        program: ((PdfBundledFace) -> ByteArray)?,
    ): Boolean {
        if (!needsRepair(dict)) return false
        val baseFont = dict.getNameAsString(COSName.BASE_FONT) ?: return false
        val toUnicode = dict.getDictionaryObject(COSName.TO_UNICODE) as COSStream
        val differences = differencesOf(toUnicode)
        if (differences.size() == 0) return false
        val face = program?.let { faceFor(baseFont) }
        if (face != null) embed(dict, face, differences, program!!) else substitute(dict, baseFont, differences)
        return true
    }

    /** Rebuild around a bundled face: the program becomes ours, the widths and the rest stay the file's. */
    private fun embed(
        dict: COSDictionary,
        face: PdfBundledFace,
        differences: COSArray,
        program: (PdfBundledFace) -> ByteArray,
    ) {
        val name = FACE_NAMES.getValue(face)
        val descriptor = dict.getDictionaryObject(COSName.FONT_DESC) as? COSDictionary
            ?: COSDictionary().also { dict.setItem(COSName.FONT_DESC, it) }
        val bytes = program(face)
        descriptor.setItem(
            COSName.FONT_FILE2,
            COSStream().apply {
                setInt(COSName.LENGTH1, bytes.size)
                createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
            },
        )
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
     * The bundled face for a font of this name, or null when the family is not one the app carries. A
     * monospaced name with a weight we do not carry still gets the mono face: spacing matters more than
     * weight.
     */
    private fun faceFor(baseFont: String): PdfBundledFace? {
        val name = baseFont.substringAfter('+').lowercase()
        if ("serif" in name || "times" in name || "roman" in name) return null
        val bold = "bold" in name || "black" in name || "heavy" in name
        val italic = "italic" in name || "oblique" in name
        return when {
            "mono" in name -> PdfBundledFace.MONOSPACE
            bold && italic -> PdfBundledFace.PROPORTIONAL_BOLD_ITALIC
            bold -> PdfBundledFace.PROPORTIONAL_BOLD
            italic -> PdfBundledFace.PROPORTIONAL_ITALIC
            "dejavu" in name -> PdfBundledFace.PROPORTIONAL
            else -> null
        }
    }

    /**
     * The base-14 face that stands in for a font of this name — a slide's weight, slant and spacing are part
     * of what it says.
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

    /** What a bundled face is called in a PDF. */
    private val FACE_NAMES = mapOf(
        PdfBundledFace.PROPORTIONAL to "DejaVuSans",
        PdfBundledFace.PROPORTIONAL_BOLD to "DejaVuSans-Bold",
        PdfBundledFace.PROPORTIONAL_ITALIC to "DejaVuSans-Oblique",
        PdfBundledFace.PROPORTIONAL_BOLD_ITALIC to "DejaVuSans-BoldOblique",
        PdfBundledFace.MONOSPACE to "DejaVuSansMono",
    )
}

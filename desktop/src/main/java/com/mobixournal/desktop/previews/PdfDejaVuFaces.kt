package com.mobixournal.desktop.previews

import com.mobixournal.render.PdfBundledFace
import java.util.WeakHashMap
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * The window's own DejaVu faces, as the font programs a repaired PDF is missing.
 *
 * A viewer that meets a PDF without a font program substitutes something, and the substitute is what the
 * reader sees — Acrobat swaps in its Multiple Master faces, poppler logs "Substituting with DejaVu Sans", a
 * browser uses a system font. The chance of looking right is highest with the family the document actually
 * used, and a LibreOffice export of a DejaVu document is the common case: this deck names `DejaVuSans`,
 * `DejaVuSans-Bold`, `DejaVuSans-Oblique`, `DejaVuSans-BoldOblique` and `DejaVuSansMono` outright, and the
 * five files the app already carries under `fonts/` are exactly those shapes. Substituting our own file for
 * one of them keeps the letterforms and the metrics, so a repaired page is the original page again — only the
 * vector and text operators never moved.
 *
 * A family we do not carry (Noto, Liberation, a serif) falls back to a base-14 face, which is the same
 * substitution every other viewer makes. One stream per document and face: a deck has dozens of font
 * dictionaries and the same face behind many of them.
 */
internal object PdfDejaVuFaces {

    /** What the name of a bundled face is called in a PDF. */
    private val NAMES = mapOf(
        PdfBundledFace.PROPORTIONAL to "DejaVuSans",
        PdfBundledFace.PROPORTIONAL_BOLD to "DejaVuSans-Bold",
        PdfBundledFace.PROPORTIONAL_ITALIC to "DejaVuSans-Oblique",
        PdfBundledFace.PROPORTIONAL_BOLD_ITALIC to "DejaVuSans-BoldOblique",
        PdfBundledFace.MONOSPACE to "DejaVuSansMono",
    )

    private val streams = WeakHashMap<PDDocument, MutableMap<PdfBundledFace, COSStream>>()

    /**
     * The bundled face standing in for a font named [baseFont] (`DAAAAA+DejaVuSans-Bold`), or null when the
     * family is not one we carry — the caller then falls back to a base-14 face.
     *
     * A monospaced name with a weight we do not carry (there is no bold DejaVu mono here) still gets the
     * mono face: keeping the spacing matters more than the weight.
     */
    fun faceFor(baseFont: String): PdfBundledFace? {
        val name = baseFont.substringAfter('+').lowercase()
        if ("dejavuserif" in name || "serif" in name || "times" in name || "roman" in name) return null
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

    /** The PDF name of [face]. */
    fun nameOf(face: PdfBundledFace): String = NAMES.getValue(face)

    /**
     * [face]'s whole program as a font-file stream belonging to [document].
     *
     * The whole face rather than a subset: this document is never written — it is the rasteriser's private
     * copy — so a subset would buy nothing, and a full file keeps every glyph the page may ask for.
     */
    fun streamFor(document: PDDocument, face: PdfBundledFace): COSStream =
        streams.getOrPut(document) { HashMap() }.getOrPut(face) {
            val bytes = javaClass.getResourceAsStream("/" + face.assetPath)?.use { it.readBytes() }
                ?: throw IllegalStateException("bundled PDF font missing: ${face.assetPath}")
            COSStream().apply {
                setInt(COSName.LENGTH1, bytes.size)
                createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
            }
        }
}

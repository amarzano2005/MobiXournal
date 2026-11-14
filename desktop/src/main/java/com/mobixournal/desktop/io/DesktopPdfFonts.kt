package com.mobixournal.desktop.io

import com.mobixournal.render.GlyphSanitizer
import com.mobixournal.render.PdfBundledFace
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font

/**
 * Loads the window's bundled Unicode faces and embeds them into a [PDDocument] — the PC's half of the
 * rule `PdfFonts` states for the tablet, so both shells typeset the same run in the same face.
 *
 * The same five DejaVu files the app carries live under `desktop/src/main/resources/fonts` (copied from
 * `app/src/main/assets/fonts`, with `LICENSE.txt` beside them: Bitstream Vera + Arev, the DejaVu changes
 * in the public domain). A [PdfBundledFace] names its file ([PdfBundledFace.assetPath]), so the plan stays
 * platform-free and this is where that path becomes a stream on the classpath.
 *
 * `PDType0Font.load(doc, stream, subset = true)` subsets the face into the export and writes the
 * `ToUnicode` map with it, so a Greek or Cyrillic run is **drawn and copyable**, not just readable on
 * screen. Fonts are embedded per document, so instances are cached by the document they were loaded into.
 */
internal class DesktopPdfFonts {

    /**
     * A face embedded in one document, paired with the [GlyphSanitizer] that keeps a codepoint this face
     * cannot draw from throwing mid-export — the same last resort the tablet uses (a visible U+FFFD,
     * never a `?`).
     */
    class Embedded(val font: PDFont, private val sanitizer: GlyphSanitizer) {

        /** [text] with every codepoint this face cannot draw replaced by the visible substitute. */
        fun sanitize(text: String): String = sanitizer.sanitize(text)
    }

    private val cache = HashMap<Key, Embedded>()

    /**
     * Embed [face] into [doc], reusing the instance if it is already there. Throws [IllegalStateException]
     * when the resource is missing — a packaging error rather than something an export can recover from.
     */
    fun load(doc: PDDocument, face: PdfBundledFace): Embedded =
        cache.getOrPut(Key(System.identityHashCode(doc), face)) {
            val stream = javaClass.getResourceAsStream("/" + face.assetPath)
                ?: throw IllegalStateException("bundled PDF font missing: ${face.assetPath}")
            val font = stream.use { PDType0Font.load(doc, it, true) }
            Embedded(font, GlyphSanitizer { s -> runCatching { font.getStringWidth(s) }.isSuccess })
        }

    private data class Key(val docId: Int, val face: PdfBundledFace)
}

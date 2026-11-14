package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * Extracts the positioned text layer of an imported PDF into a [PdfTextIndex] using PDFBox
 * ([PDFTextStripper]) — the same dependency the exporter uses, so no OCR engine is needed for
 * born-digital PDFs. A scanned (image-only) page simply yields no words ([PdfTextIndex.hasAnyText]
 * is then false); adding OCR for that case is a tracked follow-up. Any failure degrades to an empty
 * index so import never breaks. PDFBox's stripper reports boxes in a top-left origin, y-down frame
 * in points — the same frame `.xopp` geometry uses — so words map to the screen like strokes.
 *
 * **One traversal per document.** `PDFTextStripper.getText` walks the whole page range it is given, so
 * asking it for each page in turn parsed the file once per page — on a scanned lecture that is the
 * difference between one pass and two hundred. The stripper now takes the document once and files each
 * line under the page it came from ([WordStripper.pageOf]), which is also what keeps an empty page
 * empty instead of shifting the pages after it.
 */
class PdfTextExtractor {

    /**
     * How many times the last [extract] asked PDFBox to walk a document — `1` for any document with a
     * page, `0` for one that could not be opened or has none.
     *
     * The seam a test reads, because the saving this class buys is a **cost**: a one-pass and a
     * per-page extractor answer exactly the same words, and only a count can tell them apart.
     */
    internal var traversals: Int = 0
        private set

    fun extract(file: File): PdfTextIndex {
        val doc = runCatching { PDDocument.load(file) }.getOrNull() ?: return PdfTextIndex(emptyList())
        return try {
            val stripper = WordStripper(doc.numberOfPages)
            traversals = stripper.collect(doc)
            PdfTextIndex(stripper.pages())
        } catch (_: Throwable) {
            PdfTextIndex(emptyList())
        } finally {
            doc.close()
        }
    }

    /** Collects every page's glyphs into words via the pure [PdfWordGrouper], one pass for the file. */
    private class WordStripper(pageCount: Int) : PDFTextStripper() {
        /** One bucket per page, in document order — a page with no words keeps its empty bucket. */
        private val words: List<ArrayList<PdfWord>> = List(pageCount.coerceAtLeast(0)) { ArrayList() }

        init {
            sortByPosition = true
            // The whole document as one range: PDFBox walks the pages itself and reports the page each
            // line belongs to, which is both one traversal and the only way the page numbers stay true.
            startPage = 1
            endPage = pageCount.coerceAtLeast(1)
        }

        /** Walk [doc] once; answers how many traversals that was. */
        fun collect(doc: PDDocument): Int {
            if (words.isEmpty()) return 0
            getText(doc)
            return 1
        }

        fun pages(): List<List<PdfWord>> = words.map { it.toList() }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            val chars = ArrayList<CharBox>(textPositions.size)
            for (tp in textPositions) {
                val unicode = tp.unicode ?: continue
                if (unicode.isEmpty()) continue
                val height = tp.heightDir.toDouble().let { if (it > 0.0) it else tp.fontSizeInPt.toDouble() }
                // The box arithmetic — and the trap it exists for: PDFBox's yDirAdj is the glyph's
                // BOTTOM edge, so using it as the top shifts every box down a line — is `glyphBoxes` in
                // `mobix-core`, shared with the window's reader.
                chars += glyphBoxes(
                    unicode,
                    tp.xDirAdj.toDouble(),
                    tp.yDirAdj.toDouble(),
                    tp.widthDirAdj.toDouble(),
                    height,
                )
            }
            val page = currentPageNo - 1
            if (page in words.indices) words[page] += PdfWordGrouper.group(chars)
        }
    }
}

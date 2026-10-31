package com.mobixournal.desktop

import com.mobixournal.render.CharBox
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry
import com.mobixournal.render.PdfOutlineNode
import com.mobixournal.render.PdfOutlineTree
import com.mobixournal.render.PdfTextIndex
import com.mobixournal.render.PdfWord
import com.mobixournal.render.PdfWordGrouper
import java.util.IdentityHashMap
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/**
 * The window's twins of the app's PDF extractors — the same two walks over **the JVM's** PDFBox port
 * instead of `pdfbox-android` (the precedent is [`DesktopPdfMerge`] beside `PdfMerger`).
 *
 * Both answer the shared, platform-free types in `mobix-core` (`PdfTextIndex`, `PdfOutline`), so the
 * word boxes a selection resolves on and the rows a Contents list prints are the tablet's own geometry
 * and not a second reading of the same file.
 *
 * Everything here is a pure function of a loaded [PDDocument]: the loader ([DesktopPdfIndexer]) opens
 * the file **once** and hands the same document to both, because a 200-page PDF's text and outline are
 * two questions about the same bytes.
 */
internal object DesktopPdfText {

    /**
     * [doc]'s positioned text layer, page by page.
     *
     * The box convention is the app's, deliberately: PDFBox's stripper reports top-left origin, y-down,
     * in points — the frame `.xopp` geometry uses — and `yDirAdj` is the glyph's **bottom**. Reading it
     * as the top drops every word a line, which is exactly the bug `PdfTextExtractor` documents.
     */
    fun of(doc: PDDocument): PdfTextIndex {
        val stripper = WordStripper()
        val pages = ArrayList<List<PdfWord>>(doc.numberOfPages)
        for (i in 0 until doc.numberOfPages) {
            stripper.begin(i + 1) // PDFTextStripper's page range is 1-based.
            stripper.getText(doc)
            pages += stripper.take()
        }
        return PdfTextIndex(pages)
    }

    /** Collects one page's glyphs into words through the shared [PdfWordGrouper]. */
    private class WordStripper : PDFTextStripper() {
        private val collected = ArrayList<PdfWord>()

        init {
            sortByPosition = true
        }

        fun begin(page: Int) {
            collected.clear()
            startPage = page
            endPage = page
        }

        fun take(): List<PdfWord> = collected.toList()

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            val chars = ArrayList<CharBox>(textPositions.size)
            for (tp in textPositions) {
                val unicode = tp.unicode ?: continue
                if (unicode.isEmpty()) continue
                val left = tp.xDirAdj.toDouble()
                val height = tp.heightDir.toDouble().let { if (it > 0.0) it else tp.fontSizeInPt.toDouble() }
                val bottom = tp.yDirAdj.toDouble()
                val top = bottom - height
                val right = left + tp.widthDirAdj.toDouble()
                for (c in unicode) chars.add(CharBox(c, left, top, right, bottom))
            }
            collected += PdfWordGrouper.group(chars)
        }
    }
}

/**
 * The window's reader of a PDF's **outline** (its `/Outlines` bookmark tree).
 *
 * It produces the raw [PdfOutlineNode] tree and lets the shared [PdfOutlineTree] flatten it, so the
 * display rules — pre-order, whitespace, inherited pages, both ceilings — are not restated here. A
 * PDFBox outline can point an entry back at an ancestor, so a node is read once (identity, since a
 * `/First` may hand back the same dictionary twice) and the walk is bounded.
 */
internal object DesktopPdfOutline {

    fun of(doc: PDDocument): PdfOutline {
        val first = doc.documentCatalog?.documentOutline?.firstChild
        return PdfOutlineTree.flatten(Reader(doc).siblings(first))
    }

    private class Reader(private val doc: PDDocument) {
        private val visited = IdentityHashMap<COSDictionary, Boolean>()
        private var remaining = MAX_NODES

        fun siblings(first: PDOutlineItem?): List<PdfOutlineNode> {
            var item = first
            val nodes = ArrayList<PdfOutlineNode>()
            while (item != null && remaining > 0 && !visited.containsKey(item.cosObject)) {
                visited[item.cosObject] = true
                remaining--
                nodes += PdfOutlineNode(
                    title = item.title.orEmpty(),
                    pageIndex = pageIndex(item),
                    children = siblings(item.firstChild),
                )
                item = item.nextSibling
            }
            return nodes
        }

        /** The 0-based page [item] points to, or [PdfOutlineEntry.NO_DESTINATION] if it points nowhere. */
        private fun pageIndex(item: PDOutlineItem): Int {
            val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                ?: return PdfOutlineEntry.NO_DESTINATION
            val index = runCatching { doc.pages.indexOf(page) }.getOrDefault(-1)
            return if (index >= 0) index else PdfOutlineEntry.NO_DESTINATION
        }

        private companion object {
            /** Ceiling on nodes visited in one outline, against a pathological or cyclic tree. */
            const val MAX_NODES = 20_000
        }
    }
}

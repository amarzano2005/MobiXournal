package com.mobixournal.render

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import java.util.IdentityHashMap

/**
 * Reads an imported PDF's **outline** (its `/Outlines` bookmark tree) through PDFBox — already a
 * dependency for the text layer and export, so no bespoke PDF parser is needed. The walk produces a
 * plain, Android-free [PdfOutline], and every failure — a PDF with no outline at all, a corrupt or
 * cyclic tree — degrades to an empty one so that opening a document never breaks on its bookmarks.
 *
 * Only entries with a resolvable destination are given a page; a header that just groups rows keeps
 * [PdfOutlineEntry.NO_DESTINATION] and [PdfOutlineTree] fills it from its children.
 */
class PdfOutlineExtractor {

    fun extract(file: File): PdfOutline {
        val doc = runCatching { PDDocument.load(file) }.getOrNull() ?: return PdfOutline.EMPTY
        return try {
            val first = doc.documentCatalog?.documentOutline?.firstChild
            PdfOutlineTree.flatten(Reader(doc).siblings(first))
        } catch (_: Throwable) {
            PdfOutline.EMPTY
        } finally {
            doc.close()
        }
    }

    /**
     * Walks one outline level — a chain of siblings, each with its own children — recursing into the
     * children. A malformed file can point an entry back at an ancestor, which the plain sibling/
     * child walk would follow forever, so a node is read once (identity, since a `/First` may hand
     * back the same dictionary twice) and the whole walk is bounded by [MAX_NODES].
     */
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
            // findDestinationPage follows both a /Dest and a /A go-to action, and returns null for an
            // entry that is only a header (or whose named destination can't be resolved).
            val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                ?: return PdfOutlineEntry.NO_DESTINATION
            // pages.indexOf is already 0-based and -1 when the page isn't in the tree, which is the
            // same "points nowhere" answer we want (and stays put if the destination page was dropped).
            val index = runCatching { doc.pages.indexOf(page) }.getOrDefault(-1)
            return if (index >= 0) index else PdfOutlineEntry.NO_DESTINATION
        }

        private companion object {
            /** Ceiling on nodes visited in one outline, against a pathological or cyclic tree. */
            const val MAX_NODES = 20_000
        }
    }
}

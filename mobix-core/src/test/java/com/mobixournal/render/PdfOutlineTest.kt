package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PDF Contents tree's flattening rules, pinned on the pure geometry: pre-order rows with their
 * depth, a header that borrows its first descendant's page, a destinationless subtree that inherits
 * its ancestor's, titleless nodes that add no row, and both ceilings.
 *
 * The PDFBox walk that fills this tree lives with each shell's own port — `PdfOutlineExtractorTest`
 * in the app and the window's twin — because the two read the same outline through different
 * artefacts.
 */
class PdfOutlineTest {

    private fun node(page: Int, title: String, children: List<PdfOutlineNode> = emptyList()) =
        PdfOutlineNode(title, page, children)

    @Test fun `flatten walks pre-order and keeps the depth`() {
        val outline = PdfOutlineTree.flatten(
            listOf(
                node(0, "Chapter 1", listOf(node(1, "Section 1.1", listOf(node(3, "Sub 1.1.1"))))),
                node(5, "Chapter 2"),
            ),
        )
        assertEquals(
            listOf("Chapter 1" to 0, "Section 1.1" to 1, "Sub 1.1.1" to 3, "Chapter 2" to 5),
            outline.entries.map { it.title to it.pageIndex },
        )
        assertEquals(listOf(0, 1, 2, 0), outline.entries.map { it.depth })
    }

    @Test fun `a header without a destination takes its first descendant's page`() {
        val outline = PdfOutlineTree.flatten(
            listOf(node(PdfOutlineEntry.NO_DESTINATION, "Part I", listOf(node(7, "Chapter")))),
        )
        assertEquals(
            listOf("Part I" to 7, "Chapter" to 7),
            outline.entries.map { it.title to it.pageIndex },
        )
    }

    @Test fun `a destinationless subtree inherits the ancestor's page`() {
        val outline = PdfOutlineTree.flatten(
            listOf(node(2, "Chapter", listOf(node(PdfOutlineEntry.NO_DESTINATION, "Untitled section")))),
        )
        val section = outline.entries.last()
        assertEquals(2, section.pageIndex)
        assertTrue(section.hasDestination)
    }

    @Test fun `a titleless node adds no row but keeps its children at the level`() {
        val outline = PdfOutlineTree.flatten(listOf(node(0, "   ", listOf(node(1, "First")))))
        assertEquals(listOf("First" to 1), outline.entries.map { it.title to it.pageIndex })
        assertEquals(0, outline.entries.single().depth)
    }

    @Test fun `whitespace inside a title is collapsed`() {
        val outline = PdfOutlineTree.flatten(listOf(node(0, "  Chapter\n\tOne  ")))
        assertEquals("Chapter One", outline.entries.single().title)
    }

    @Test fun `nesting deeper than the cap is clamped`() {
        var nested = node(0, "leaf")
        repeat(PdfOutlineTree.MAX_DEPTH + 4) { nested = node(0, "level", listOf(nested)) }
        val rows = PdfOutlineTree.flatten(listOf(nested)).entries
        assertEquals(PdfOutlineTree.MAX_DEPTH, rows.maxOf { it.depth })
    }

    @Test fun `an entry that points nowhere is not jumpable`() {
        val outline = PdfOutlineTree.flatten(listOf(node(PdfOutlineEntry.NO_DESTINATION, "Header")))
        assertFalse(outline.entries.single().hasDestination)
    }

    @Test fun `an empty outline flattens to nothing`() {
        assertTrue(PdfOutlineTree.flatten(emptyList()).isEmpty)
    }

    @Test fun `the row count is capped`() {
        val many = List(PdfOutlineTree.MAX_ENTRIES + 50) { node(it, "Row $it") }
        assertEquals(PdfOutlineTree.MAX_ENTRIES, PdfOutlineTree.flatten(many).entries.size)
    }
}

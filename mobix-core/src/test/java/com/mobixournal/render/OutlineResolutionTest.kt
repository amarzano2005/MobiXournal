package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Where a Contents row leads**, and the one rule that makes the feature safe: a target asks for exactly one
 * page, and the answer when that page is gone is "nowhere".
 *
 * Both kinds are pinned here because they fail differently. A `sheet` target survives a page being inserted
 * before it (the sheet is still shown, on a page whose index moved) and breaks when the PDF is removed; a
 * `page` target survives a reorder and breaks when the page is deleted or duplicated. Neither is ever
 * clamped, rounded or backed off to a neighbour, which is the failure this resolver exists to prevent: a
 * bookmark that opens the wrong chapter is worse than one that says it no longer knows.
 */
class OutlineResolutionTest {

    private fun pdfPage(pageNo: Int) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Pdf(
            filename = if (pageNo == 0) "doc.pdf" else null,
            pageNo = pageNo,
            domain = if (pageNo == 0) "absolute" else null,
        ),
        layers = listOf(Layer(emptyList())),
        id = "pg_pdf$pageNo",
    )

    private fun plainPage(id: String) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(emptyList())),
        id = id,
    )

    private fun node(id: String, title: String, target: OutlineTarget?, vararg children: OutlineNode) =
        OutlineNode(id = id, title = title, target = target, children = children.toList())

    /** Two ordinary pages, then a PDF whose sheets 0 and 1 are pages 2 and 3. */
    private val pages = listOf(plainPage("pg_a"), plainPage("pg_b"), pdfPage(0), pdfPage(1))

    @Test
    fun `a sheet opens the document page showing it, and a page id opens that page`() {
        val tree = OutlineTree(
            nodes = listOf(
                node("bkm_1", "Chapter", OutlineTarget.Sheet(1)),
                node("bkm_2", "Notes", OutlineTarget.Page("pg_b")),
            ),
        )

        val rows = OutlineResolution.rows(tree, pages)

        assertEquals("the sheet is page 3 of the document", 3, rows[0].page)
        assertEquals("and the id is page 1", 1, rows[1].page)
    }

    @Test
    fun `rows come out in pre-order with their depth and their position`() {
        val tree = OutlineTree(
            nodes = listOf(
                node(
                    "bkm_1", "Chapter", OutlineTarget.Sheet(0),
                    node("bkm_2", "Section", null, node("bkm_3", "Subsection", OutlineTarget.Page("pg_a"))),
                ),
                node("bkm_4", "Appendix", null),
            ),
        )

        val rows = OutlineResolution.rows(tree, pages)

        assertEquals(
            listOf("Chapter", "Section", "Subsection", "Appendix"),
            rows.map { it.title },
        )
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
        assertEquals("the position is the row's address", listOf(0, 1, 2, 3), rows.map { it.row })
        assertEquals("an id per node, and it is the one the tree minted", listOf("bkm_1", "bkm_2", "bkm_3", "bkm_4"), rows.map { it.id })
    }

    @Test
    fun `a node with no target is a group, and its children still lead somewhere`() {
        val tree = OutlineTree(
            nodes = listOf(node("bkm_1", "Part I", null, node("bkm_2", "Chapter", OutlineTarget.Sheet(0)))),
        )

        val rows = OutlineResolution.rows(tree, pages)

        assertNull("the group opens nothing", rows[0].page)
        assertFalse(rows[0].hasDestination)
        assertEquals("its child still does", 2, rows[1].page)
    }

    @Test
    fun `a page that was deleted leaves its row in place with nowhere to go`() {
        val tree = OutlineTree(nodes = listOf(node("bkm_1", "Gone", OutlineTarget.Page("pg_deleted"))))

        val rows = OutlineResolution.rows(tree, pages)

        assertEquals("the reader's bookmark is still there", "Gone", rows.single().title)
        assertNull("it just no longer knows where it went", rows.single().page)
    }

    @Test
    fun `a duplicated page gets its own id, so the copy inherits no bookmark`() {
        val tree = OutlineTree(nodes = listOf(node("bkm_1", "Notes", OutlineTarget.Page("pg_b"))))
        val withCopy = listOf(plainPage("pg_a"), plainPage("pg_b"), plainPage("pg_b_copy"), pdfPage(0), pdfPage(1))

        val rows = OutlineResolution.rows(tree, withCopy)

        assertEquals("the bookmark stays on the original", 1, rows.single().page)
        assertTrue("and the copy is not a second answer", rows.single().page != 2)
    }

    @Test
    fun `a sheet target follows its sheet through a reorder, and dies with the PDF`() {
        val tree = OutlineTree(nodes = listOf(node("bkm_1", "Chapter", OutlineTarget.Sheet(1))))

        val reordered = listOf(pdfPage(1), plainPage("pg_a"), pdfPage(0))

        assertEquals("the page moved, the sheet did not", 0, OutlineResolution.rows(tree, reordered).single().page)
        assertNull(
            "and with no PDF left there is nothing to point at",
            OutlineResolution.rows(tree, listOf(plainPage("pg_a"))).single().page,
        )
    }

    @Test
    fun `two pages showing one sheet resolve to the first, never to a guess`() {
        val tree = OutlineTree(nodes = listOf(node("bkm_1", "Chapter", OutlineTarget.Sheet(0))))

        val duplicatedSheet = listOf(pdfPage(0), pdfPage(0))

        assertEquals(0, OutlineResolution.rows(tree, duplicatedSheet).single().page)
        assertEquals("the rule is document order, not proximity to the reader", 0, OutlineResolution.pageOfSheet(duplicatedSheet, 0))
    }

    @Test
    fun `a PDF outline resolves through the same sheet mapping, with no notebook tree in sight`() {
        val outline = PdfOutline(
            listOf(
                PdfOutlineEntry("Chapter 1", 0, 0),
                PdfOutlineEntry("Section 1.1", 1, 1),
                PdfOutlineEntry("Header only", PdfOutlineEntry.NO_DESTINATION, 1),
            ),
        )

        val rows = OutlineResolution.rows(outline, pages)

        assertEquals(listOf(2, 3, null), rows.map { it.page })
        assertEquals("a document with no tree shows the file's own rows", rows, OutlineResolution.rowsOf(OutlineTree.EMPTY, outline, pages))
    }

    @Test
    fun `the document's own tree wins over the PDF's outline, and neither makes rows up`() {
        val tree = OutlineTree(nodes = listOf(node("bkm_1", "Mine", OutlineTarget.Page("pg_a"))))
        val outline = PdfOutline(listOf(PdfOutlineEntry("Theirs", 0, 0)))

        val rows = OutlineResolution.rowsOf(tree, outline, pages)

        assertEquals("the notebook's index is what the reader edited", listOf("Mine"), rows.map { it.title })
        assertTrue(
            "an empty document with no PDF has nothing to show, rather than a made-up row",
            OutlineResolution.rowsOf(OutlineTree.EMPTY, null, pages).isEmpty(),
        )
    }
}

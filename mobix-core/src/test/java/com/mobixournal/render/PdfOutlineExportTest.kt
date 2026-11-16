package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **What an exported PDF's bookmark pane says.** The rule this pins is the one that keeps a subset export
 * honest: a row whose page was not exported never becomes a link to a different sheet, and it never takes its
 * exported sections down with it — it becomes the heading it already was.
 *
 * Every case is a shape a real notebook makes: a reorder moves destinations, two chapters may share a title,
 * a target may name a page the document no longer has, and an export may carry only some of the pages.
 */
class PdfOutlineExportTest {

    private fun pdfPage(pageNo: Int) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Pdf(filename = "book.pdf", pageNo = pageNo, domain = null),
        layers = listOf(Layer(emptyList())),
    )

    private fun node(id: String, title: String, target: OutlineTarget?, vararg children: OutlineNode) =
        OutlineNode(id = id, title = title, target = target, children = children.toList())

    private val pages = listOf(pdfPage(0), pdfPage(1), pdfPage(2), pdfPage(3))

    private fun rows(tree: OutlineTree, exported: List<Int> = pages.indices.toList()) =
        PdfOutlineExport.rows(tree, pages, exported)

    @Test
    fun `a nested tree is written as nested rows, each on its own sheet`() {
        val tree = OutlineTree(
            nodes = listOf(
                node(
                    "bkm_1", "Book", OutlineTarget.Sheet(0),
                    node("bkm_2", "Chapter", OutlineTarget.Sheet(1), node("bkm_3", "Section", OutlineTarget.Sheet(2))),
                ),
            ),
        )

        assertEquals(
            listOf(
                PdfOutlineExport.Row("Book", 0, 0),
                PdfOutlineExport.Row("Chapter", 1, 1),
                PdfOutlineExport.Row("Section", 2, 2),
            ),
            rows(tree),
        )
    }

    @Test
    fun `a reordered notebook rewrites the destinations, not the titles`() {
        val tree = OutlineTree(
            nodes = listOf(
                node("bkm_1", "Late", OutlineTarget.Sheet(3)),
                node("bkm_2", "Early", OutlineTarget.Sheet(0)),
            ),
        )

        // The export writes the notebook's pages in reverse: sheet 3 lands first, sheet 0 last.
        assertEquals(
            listOf(PdfOutlineExport.Row("Late", 0, 0), PdfOutlineExport.Row("Early", 0, 3)),
            rows(tree, exported = listOf(3, 2, 1, 0)),
        )
    }

    @Test
    fun `a chapter whose page was not exported keeps its sections, as a heading`() {
        val tree = OutlineTree(
            nodes = listOf(
                node(
                    "bkm_1", "Chapter", OutlineTarget.Sheet(0),
                    node("bkm_2", "Section", OutlineTarget.Sheet(2)),
                ),
            ),
        )

        assertEquals(
            listOf(PdfOutlineExport.Row("Chapter", 0, null), PdfOutlineExport.Row("Section", 1, 1)),
            rows(tree, exported = listOf(1, 2)),
        )
    }

    @Test
    fun `a row with no exported page and nothing under it is left out`() {
        val tree = OutlineTree(
            nodes = listOf(
                node("bkm_1", "Gone", OutlineTarget.Sheet(0)),
                node("bkm_2", "Here", OutlineTarget.Sheet(1)),
            ),
        )

        assertEquals(listOf(PdfOutlineExport.Row("Here", 0, 0)), rows(tree, exported = listOf(1)))
    }

    @Test
    fun `a target that leads nowhere is treated like a page that was not exported`() {
        val tree = OutlineTree(
            nodes = listOf(
                node("bkm_1", "Deleted page", OutlineTarget.Page("pg_gone")),
                node("bkm_2", "Book", OutlineTarget.Sheet(9)),
            ),
        )

        assertEquals("nothing exported hangs under either", emptyList<PdfOutlineExport.Row>(), rows(tree))
    }

    @Test
    fun `two chapters with one title stay two rows`() {
        val tree = OutlineTree(
            nodes = listOf(
                node("bkm_1", "Introduction", OutlineTarget.Sheet(0)),
                node("bkm_2", "Introduction", OutlineTarget.Sheet(1)),
            ),
        )

        assertEquals(
            listOf(PdfOutlineExport.Row("Introduction", 0, 0), PdfOutlineExport.Row("Introduction", 0, 1)),
            rows(tree),
        )
    }

    @Test
    fun `a page target follows its page's identity rather than a position`() {
        val named = pages.first().copy(id = "pg_a")
        val document = listOf(named, pages[1])
        val tree = OutlineTree(nodes = listOf(node("bkm_1", "Start", OutlineTarget.Page("pg_a"))))

        assertEquals(listOf(PdfOutlineExport.Row("Start", 0, 0)), PdfOutlineExport.rows(tree, document, listOf(0, 1)))
    }

    @Test
    fun `an empty tree writes no outline`() {
        assertEquals(emptyList<PdfOutlineExport.Row>(), rows(OutlineTree.EMPTY))
    }
}

package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The PDF Contents tree: how the raw tree flattens into display rows (pure), and that the PDFBox
 * walk reads a real file's outline back with the right pages and nesting.
 */
class PdfOutlineTest {

    @get:Rule val tmp = TemporaryFolder()

    // --- flattening the raw tree ---------------------------------------------------------------

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

    // --- reading a real PDF ----------------------------------------------------------------

    /** Three A4 pages whose outline is Chapter 1 (p1) → Section 1.1 (p3), then Chapter 2 (p2). */
    private fun pdfWithOutline(): File {
        val file = tmp.newFile("outlined.pdf")
        PDDocument().use { doc ->
            val one = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val two = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val three = PDPage(PDRectangle.A4).also { doc.addPage(it) }
            val outline = PDDocumentOutline()
            doc.documentCatalog.documentOutline = outline
            val chapter = PDOutlineItem().apply {
                title = "Chapter 1"
                setDestination(one)
            }
            outline.addLast(chapter)
            chapter.addLast(
                PDOutlineItem().apply {
                    title = "Section 1.1"
                    setDestination(three)
                },
            )
            outline.addLast(
                PDOutlineItem().apply {
                    title = "Chapter 2"
                    setDestination(two)
                },
            )
            doc.save(file)
        }
        return file
    }

    @Test fun `an outline is read back with its destinations and nesting`() {
        val outline = PdfOutlineExtractor().extract(pdfWithOutline())
        assertEquals(
            listOf("Chapter 1" to 0, "Section 1.1" to 2, "Chapter 2" to 1),
            outline.entries.map { it.title to it.pageIndex },
        )
        assertEquals(listOf(0, 1, 0), outline.entries.map { it.depth })
    }

    @Test fun `a PDF without an outline extracts empty`() {
        val file = tmp.newFile("plain.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        }
        assertTrue(PdfOutlineExtractor().extract(file).isEmpty)
    }

    @Test fun `a file that is not a PDF extracts empty rather than throwing`() {
        val file = tmp.newFile("not.pdf").apply { writeText("definitely not a PDF") }
        assertTrue(PdfOutlineExtractor().extract(file).isEmpty)
    }
}

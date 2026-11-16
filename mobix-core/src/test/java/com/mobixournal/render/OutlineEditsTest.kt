package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contents edits themselves (`OutlineEdits`): add, rename and delete, and what they refuse.
 *
 * These are the rules both shells run, so they are pinned where they live rather than through a shell:
 * a row's identity outlives its title, a blank title is refused instead of saved, deleting a chapter
 * leaves its sections in place, and a refused edit hands back the very tree it was given so no undo step
 * is recorded for a change that did not happen.
 */
class OutlineEditsTest {

    private fun node(id: String, title: String, target: OutlineTarget? = null, children: List<OutlineNode> = emptyList()) =
        OutlineNode(id, title, target, children)

    private fun tree() = OutlineTree(
        nodes = listOf(
            node("bkm_1", "Chapter", OutlineTarget.Sheet(3), listOf(node("bkm_2", "Section", OutlineTarget.Page("pg_1")))),
            node("bkm_3", "Notes", OutlineTarget.Page("pg_2")),
        ),
    )

    private fun paper(id: String) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(emptyList())),
        id = id,
    )

    private fun treeOf(result: OutlineEdits.Result) = (result as OutlineEdits.Result.Ok).tree

    @Test
    fun `an added row opens the page it was added on and gets its own id`() {
        val result = OutlineEdits.add(tree(), "Index", OutlineTarget.Page("pg_2"))

        val added = treeOf(result).nodes.last()
        assertEquals("appended after the existing rows", "Index", added.title)
        assertEquals(OutlineTarget.Page("pg_2"), added.target)
        assertTrue("a fresh bookmark id", added.id.startsWith("bkm_"))
        assertTrue("and it is not one of the rows already there", added.id !in setOf("bkm_1", "bkm_2", "bkm_3"))
    }

    @Test
    fun `a row can be filed under another one`() {
        val result = OutlineEdits.add(tree(), "Sub", OutlineTarget.Page("pg_2"), parentId = "bkm_2")

        val parent = treeOf(result).nodes.first().children.first()
        assertEquals("under the row that was named", "Sub", parent.children.single().title)
    }

    @Test
    fun `renaming keeps the node, its target and its children`() {
        val renamed = treeOf(OutlineEdits.rename(tree(), "bkm_1", "  Geometry  "))

        val chapter = renamed.nodes.first()
        assertEquals("the title is trimmed for the reader", "Geometry", chapter.title)
        assertEquals("the id is unchanged", "bkm_1", chapter.id)
        assertEquals("the destination survives a rename", OutlineTarget.Sheet(3), chapter.target)
        assertEquals("and so do the sections", listOf("Section"), chapter.children.map { it.title })
    }

    @Test
    fun `two rows may share a title, because identity is the id`() {
        val renamed = treeOf(OutlineEdits.rename(tree(), "bkm_3", "Chapter"))

        assertEquals("both rows say the same thing", listOf("Chapter", "Chapter"), renamed.nodes.map { it.title })
        assertEquals("and remain two rows", listOf("bkm_1", "bkm_3"), renamed.nodes.map { it.id })
    }

    @Test
    fun `a blank title is refused`() {
        val tree = tree()

        assertEquals(
            "an untitled row is never saved",
            OutlineEdits.Refusal.EMPTY_TITLE,
            refusal(OutlineEdits.add(tree, "   ", OutlineTarget.Page("pg_1"))),
        )
        assertEquals("nor renamed to nothing", OutlineEdits.Refusal.EMPTY_TITLE, refusal(OutlineEdits.rename(tree, "bkm_1", " ")))
    }

    @Test
    fun `deleting a chapter promotes its sections instead of deleting them`() {
        val edited = treeOf(OutlineEdits.delete(tree(), "bkm_1"))

        assertEquals("the chapter is gone", listOf("Section", "Notes"), edited.nodes.map { it.title })
        assertEquals("its section took its place", "Section", edited.nodes.first().title)
        assertEquals("with its own destination intact", OutlineTarget.Page("pg_1"), edited.nodes.first().target)
    }

    @Test
    fun `deleting a row never touches a page`() {
        val edited = treeOf(OutlineEdits.delete(tree(), "bkm_3"))

        assertEquals("only the row is gone", 1, edited.nodes.size)
        assertEquals("the remaining row still opens its page", OutlineTarget.Sheet(3), edited.nodes.single().target)
    }

    @Test
    fun `an edit that names a row nobody has is refused`() {
        val tree = tree()

        assertEquals(OutlineEdits.Refusal.NOT_FOUND, refusal(OutlineEdits.rename(tree, "bkm_9", "Nowhere")))
        assertEquals(OutlineEdits.Refusal.NOT_FOUND, refusal(OutlineEdits.delete(tree, "bkm_9")))
        assertEquals(
            OutlineEdits.Refusal.NOT_FOUND,
            refusal(OutlineEdits.add(tree, "Child", OutlineTarget.Sheet(0), parentId = "bkm_9")),
        )
    }

    @Test
    fun `the destination of a row is the sheet a PDF page shows or the id of paper`() {
        val sheet = Page(
            width = 400.0,
            height = 600.0,
            background = Background.Pdf(filename = "book.pdf", pageNo = 5, domain = "absolute"),
            layers = listOf(Layer(emptyList())),
            id = "pg_7",
        )

        assertEquals("a PDF page is named by its sheet", OutlineTarget.Sheet(5), OutlineEdits.targetOf(sheet))
        assertEquals("paper by its id", OutlineTarget.Page("pg_9"), OutlineEdits.targetOf(paper("pg_9")))
        assertNull("and an unidentified page has no stable handle", OutlineEdits.targetOf(paper("pg_9").copy(id = null)))
    }

    @Test
    fun `a fresh paper page is given the id a bookmark needs, and a PDF page needs none`() {
        val fresh = paper("pg_1").copy(id = null)

        val (identified, target) = OutlineEdits.identifiable(fresh)

        assertTrue("minting is what lets a row name a page that was never saved", identified.id!!.startsWith("pg_"))
        assertEquals(OutlineTarget.Page(identified.id!!), target)
        assertEquals("nothing else about the page changes", fresh.copy(id = identified.id), identified)

        val sheet = Page(
            width = 400.0,
            height = 600.0,
            background = Background.Pdf(filename = "book.pdf", pageNo = 5, domain = "absolute"),
            layers = listOf(Layer(emptyList())),
        )
        val (unchanged, sheetTarget) = OutlineEdits.identifiable(sheet)
        assertEquals("a PDF page is named by its sheet, so its id is not the tree's business", sheet, unchanged)
        assertEquals(OutlineTarget.Sheet(5), sheetTarget)
    }

    /** The reason an edit refused, failing loudly when it unexpectedly succeeded. */
    private fun refusal(result: OutlineEdits.Result): OutlineEdits.Refusal =
        (result as? OutlineEdits.Result.Refused)?.reason ?: error("expected a refusal, got $result")
}

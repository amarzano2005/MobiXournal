package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * What the page list's own edits mean for the notebook's Contents (`OutlinePageOps`).
 *
 * The two lists are joined by **identity**: a page's id travels with the page through a move and a
 * duplicate is born with a fresh one, so neither edit has an opinion about the tree. Only a delete
 * changes a target's answer — and it clears the destination without removing the row, because deleting
 * a chapter is a Contents edit, not something a page operation may decide for the reader.
 */
class OutlinePageOpsTest {

    private fun page(id: String) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(emptyList())),
        id = id,
    )

    private fun tree() = OutlineTree(
        nodes = listOf(
            OutlineNode(
                "bkm_1",
                "Chapter",
                OutlineTarget.Sheet(3),
                children = listOf(
                    OutlineNode("bkm_2", "Section", OutlineTarget.Page("pg_2")),
                    OutlineNode("bkm_3", "Notes", null),
                ),
            ),
            OutlineNode("bkm_4", "Paper", OutlineTarget.Page("pg_3")),
        ),
    )

    @Test
    fun `deleting a page keeps its row and takes its destination away`() {
        val after = OutlinePageOps.afterRemoval(tree(), setOf("pg_2"))

        val section = after.nodes.first().children.first()
        assertEquals("the row is still there, titled as the reader wrote it", "Section", section.title)
        assertNull("with nothing to open", section.target)
        assertEquals("and its id is the same node", "bkm_2", section.id)
        assertEquals("a sheet target is untouched: the PDF page did not move", OutlineTarget.Sheet(3), after.nodes.first().target)
        assertEquals("a group stays a group", null, after.nodes.first().children.last().target)
        assertEquals("and the other page keeps pointing at its page", OutlineTarget.Page("pg_3"), after.nodes.last().target)
    }

    @Test
    fun `a tree with nothing to retarget is handed back as it was`() {
        val tree = tree()

        assertSame("a commit that compares by identity must see no edit", tree, OutlinePageOps.afterRemoval(tree, setOf("pg_9")))
        assertSame("and so is a page-less tree", OutlineTree.EMPTY, OutlinePageOps.afterRemoval(OutlineTree.EMPTY, setOf("pg_2")))
    }

    @Test
    fun `a move keeps every page target, because an id does not move with its page`() {
        val pages = listOf(page("pg_1"), page("pg_2"), page("pg_3"))
        val tree = tree()
        val moved = PageOps.move(pages, from = 0, to = 2)

        assertEquals("the ids travel with their pages", listOf("pg_2", "pg_3", "pg_1"), moved.map { it.id })
        assertSame("so the tree has nothing to answer", tree, OutlinePageOps.afterRemoval(tree, emptySet()))
    }

    @Test
    fun `a duplicate is a new page, so the copy inherits no bookmark`() {
        val pages = listOf(page("pg_1"), page("pg_2"))
        val tree = tree()
        val copied = PageOps.duplicateAt(pages, 1)

        assertEquals("one more page", 3, copied.size)
        assertNotEquals("the copy is its own page", "pg_2", copied[2].id)
        assertEquals("and the original is still the one the row names", "pg_2", copied[1].id)
        assertSame(
            "so nothing in the tree changes when the copy arrives",
            tree,
            OutlinePageOps.afterRemoval(tree, setOf(copied[2].id!!)),
        )
    }

    @Test
    fun `a row a merge brought in loses its destination the same way when its page is deleted`() {
        val remoteOnly = OutlineNode("bkm_9", "Remote chapter", OutlineTarget.Page("pg_7"))
        val merged = com.mobixournal.format.mobix.MobixOutlineMerge.merge(tree(), OutlineTree(nodes = listOf(remoteOnly)))

        val after = OutlinePageOps.afterRemoval(merged, setOf("pg_7"))

        assertEquals("the merged chapter survives its page", "Remote chapter", after.nodes.last().title)
        assertNull("as a row that opens nothing", after.nodes.last().target)
    }
}

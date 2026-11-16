package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Contents trees of two branches, merged (`MobixOutlineMerge`).
 *
 * The claim is the same one the page walk makes, one level up: **a merge never deletes**. A chapter one
 * branch added is kept even when the other branch never saw it, a chapter both branches have keeps the
 * local title and the local position with the union of its children, and every id is written back as it
 * came — which is what lets a second merge land on the same rows.
 */
class MobixOutlineMergeTest {

    private fun node(
        id: String,
        title: String,
        target: OutlineTarget? = null,
        children: List<OutlineNode> = emptyList(),
        extra: Map<String, JsonNode> = emptyMap(),
    ) = OutlineNode(id, title, target, children, extra)

    private fun tree(vararg nodes: OutlineNode) = OutlineTree(nodes = nodes.toList())

    @Test
    fun `a chapter only one side has is kept`() {
        val local = tree(node("bkm_1", "Local"))
        val remote = tree(node("bkm_2", "Remote"))

        val merged = MobixOutlineMerge.merge(local, remote)

        assertEquals("both chapters, the local one first", listOf("bkm_1", "bkm_2"), merged.nodes.map { it.id })
        assertEquals(listOf("Local", "Remote"), merged.nodes.map { it.title })
    }

    @Test
    fun `a chapter a branch deleted is not deleted by the merge`() {
        val local = tree(node("bkm_1", "Kept on the other side"))
        val remote = tree(node("bkm_1", "Kept on the other side"), node("bkm_2", "Only the other side has me"))

        val merged = MobixOutlineMerge.merge(local, remote)

        assertEquals(
            "a branch cannot prove the reader deleted it, so the row comes back",
            listOf("bkm_1", "bkm_2"),
            merged.nodes.map { it.id },
        )
    }

    @Test
    fun `a shared node keeps the local title and position and gains the remote children`() {
        val local = tree(
            node("bkm_2", "Second locally"),
            node("bkm_1", "Renamed here", OutlineTarget.Sheet(4), children = listOf(node("bkm_3", "Mine"))),
        )
        val remote = tree(
            node("bkm_1", "Old name", OutlineTarget.Sheet(9), children = listOf(node("bkm_4", "Theirs"))),
        )

        val merged = MobixOutlineMerge.merge(local, remote)

        assertEquals("local order wins", listOf("bkm_2", "bkm_1"), merged.nodes.map { it.id })
        val chapter = merged.nodes.last()
        assertEquals("local title wins", "Renamed here", chapter.title)
        assertEquals("and so does the local destination", OutlineTarget.Sheet(4), chapter.target)
        assertEquals("their section arrives under it", listOf("bkm_3", "bkm_4"), chapter.children.map { it.id })
    }

    @Test
    fun `a remote chapter nested under a shared heading arrives nested`() {
        val local = tree(node("bkm_1", "Geometry"))
        val remote = tree(node("bkm_1", "Geometry", children = listOf(node("bkm_5", "Triangles"))))

        val merged = MobixOutlineMerge.merge(local, remote)

        assertEquals("still one root", 1, merged.nodes.size)
        assertEquals("with the arriving section under it", listOf("Triangles"), merged.nodes.single().children.map { it.title })
    }

    @Test
    fun `ids are written back as they came, so a second merge changes nothing`() {
        val local = tree(node("bkm_1", "A", children = listOf(node("bkm_2", "B"))))
        val remote = tree(node("bkm_1", "A", children = listOf(node("bkm_3", "C"))))

        val once = MobixOutlineMerge.merge(local, remote)
        val twice = MobixOutlineMerge.merge(once, remote)

        assertEquals(once, twice)
        assertEquals(listOf("bkm_1", "bkm_2", "bkm_3"), listOf(once.nodes.first().id) + once.nodes.first().children.map { it.id })
    }

    @Test
    fun `an empty side leaves the other alone`() {
        val tree = tree(node("bkm_1", "Only side"))

        assertEquals("a branch with no tree adds none", tree, MobixOutlineMerge.merge(tree, OutlineTree.EMPTY))
        assertEquals("and a branch with no tree loses none", tree, MobixOutlineMerge.merge(OutlineTree.EMPTY, tree))
    }

    @Test
    fun `the local tree's own extras and version are what get written`() {
        val local = OutlineTree(version = 1, nodes = listOf(node("bkm_1", "A")), extra = mapOf("note" to JsonNode.of("kept")))
        val remote = tree(node("bkm_2", "B"))

        val merged = MobixOutlineMerge.merge(local, remote)

        assertEquals("the tree's envelope is the local one", mapOf("note" to JsonNode.of("kept")), merged.extra)
        assertEquals(1, merged.version)
        assertEquals("only its rows travel", 2, merged.nodes.size)
        assertNull("neither chapter opens anything it did not before", merged.nodes.first().target)
    }
}

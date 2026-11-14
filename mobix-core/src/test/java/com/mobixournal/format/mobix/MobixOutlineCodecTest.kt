package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contents tree's codec: what a tree looks like on disk, and the four shapes it refuses rather than
 * repairs.
 *
 * Each refusal is a shape where "repair" would mean inventing an answer the reader did not give — a
 * duplicated id has no single node it names, a node with two targets says two pages are its destination,
 * a third target kind belongs to a newer writer, and a tree past the cap decides how deep every reader's
 * recursion goes. The rest is the format's usual generosity: absent means empty, and unknown fields are
 * kept and written back.
 */
class MobixOutlineCodecTest {

    private fun json(text: String): JsonNode.Obj = JsonParser.parse(text) as JsonNode.Obj

    private fun encode(tree: OutlineTree): String = JsonWriter.write(MobixOutlineCodec.encode(tree))

    private fun codec(text: String): OutlineTree = MobixOutlineCodec.decode(json(text))

    private fun refused(text: String): String = try {
        codec(text)
        error("expected the tree to be refused: $text")
    } catch (refused: MobixContainer.MalformedMobixException) {
        refused.message.orEmpty()
    }

    @Test
    fun `a tree survives the codec`() {
        val tree = OutlineTree(
            nodes = listOf(
                OutlineNode(
                    id = "bkm_1",
                    title = "Geometry",
                    target = OutlineTarget.Sheet(0),
                    children = listOf(
                        OutlineNode("bkm_2", "Angles", OutlineTarget.Page("pg_7")),
                        OutlineNode("bkm_3", "Circles", OutlineTarget.Sheet(3)),
                    ),
                ),
                OutlineNode("bkm_4", "Appendix"),
            ),
        )

        assertEquals("node for node, targets included", tree, codec(encode(tree)))
    }

    @Test
    fun `an absent nodes list is an empty tree`() {
        val tree = codec("""{"version":1}""")

        assertTrue(tree.isEmpty)
        assertEquals("and writing it back says the same thing", """{"version":1}""", encode(tree))
    }

    @Test
    fun `unknown fields come back after the known ones`() {
        val text = """{"version":1,"writerNote":"kept","nodes":[{"id":"bkm_1","futureHint":7,"title":"A"}]}"""

        val tree = codec(text)

        assertEquals(listOf("writerNote"), tree.extra.keys.toList())
        assertEquals(listOf("futureHint"), tree.nodes[0].extra.keys.toList())
        assertEquals(
            "the known fields first, the ones we do not own after them",
            """{"version":1,"nodes":[{"id":"bkm_1","title":"A","futureHint":7}],"writerNote":"kept"}""",
            encode(tree),
        )
    }

    @Test
    fun `a newer version is refused whole`() {
        val message = refused("""{"version":2,"nodes":[{"id":"bkm_1","title":"A"}]}""")

        assertTrue(
            "the refusal names the version it cannot read, not just that something was wrong: $message",
            message.contains("version 2"),
        )
    }

    @Test
    fun `one id cannot name two nodes`() {
        val message = refused(
            """
            {"version":1,"nodes":[
              {"id":"bkm_1","title":"First"},
              {"id":"bkm_2","title":"Parent","children":[{"id":"bkm_1","title":"Second"}]}
            ]}
            """.trimIndent(),
        )

        assertTrue("the message names the id: $message", message.contains("bkm_1"))
    }

    @Test
    fun `a node carries one kind of target`() {
        val both = refused("""{"nodes":[{"id":"bkm_1","target":{"sheet":0,"page":"pg_1"}}]}""")
        val neither = refused("""{"nodes":[{"id":"bkm_2","target":{"pageNo":0}}]}""")
        val foreign = refused("""{"nodes":[{"id":"bkm_3","target":{"chunk":"0,0"}}]}""")

        assertTrue("one page, not two: $both", both.contains("both"))
        assertTrue("a target key of a kind this build does not know is not a guess: $neither", neither.contains("unknown kind"))
        assertTrue("and neither is a third kind: $foreign", foreign.contains("unknown kind"))
    }

    @Test
    fun `a node without an id is corruption`() {
        val message = refused("""{"nodes":[{"title":"nameless"}]}""")

        assertTrue("the refusal says which field is missing: $message", message.contains("id"))
    }

    @Test
    fun `nesting stops at the cap`() {
        fun tree(depth: Int): String {
            val open = (0 until depth).joinToString("") { """{"id":"bkm_$it","children":[""" }
            val close = "]}".repeat(depth)
            return """{"nodes":[$open{"id":"bkm_deep"}$close]}"""
        }

        assertEquals(
            "a tree exactly at the cap is a tree",
            OutlineTree.MAX_DEPTH,
            codec(tree(OutlineTree.MAX_DEPTH - 1)).size,
        )
        val message = refused(tree(OutlineTree.MAX_DEPTH))
        assertTrue("one level past it is refused, and says where: $message", message.contains("cap"))
    }

    @Test
    fun `an empty tree is an empty tree however it is spelled`() {
        val absent = codec("""{"version":1}""")
        val empty = codec("""{"version":1,"nodes":[]}""")

        assertEquals(absent, empty)
        assertFalse("and neither pretends to have rows", absent.size > 0)
    }

    @Test
    fun `the size counts the rows under a chapter`() {
        val tree = codec(
            """{"nodes":[{"id":"a","children":[{"id":"b","children":[{"id":"c"}]}]},{"id":"d"}]}""",
        )

        assertEquals(4, tree.size)
        assertEquals(3, tree.nodes[0].size)
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.mobix.MobixOutlineCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contents an import seeds: the PDF's own rows, nested as the file nests them, each with its minted
 * `bkm_` id and its page written down — including the page a destination-less chapter inherited from its
 * first pointing descendant, because reading back inherits nothing.
 */
class PdfOutlineSeedingTest {

    private fun entry(title: String, page: Int, depth: Int) = PdfOutlineEntry(title, page, depth)

    private fun ids(tree: OutlineTree): List<String> =
        tree.nodes.flatMap { node -> listOf(node.id) + node.children.flatMap { ids(OutlineTree(nodes = listOf(it))) } }

    @Test
    fun `a nested outline becomes a nested tree, in the file's own order`() {
        val outline = PdfOutline(
            listOf(
                entry("Geometry", 0, 0),
                entry("Points", 1, 1),
                entry("Distances", 2, 2),
                entry("Appendix", 3, 0),
            ),
        )
        val tree = PdfOutlineSeeding.tree(outline)

        assertEquals(listOf("Geometry", "Appendix"), tree.nodes.map { it.title })
        assertEquals(listOf("Points"), tree.nodes[0].children.map { it.title })
        assertEquals(listOf("Distances"), tree.nodes[0].children[0].children.map { it.title })
        assertEquals("four rows in all", 4, tree.size)
        assertEquals(listOf(OutlineTarget.Sheet(0), OutlineTarget.Sheet(1), OutlineTarget.Sheet(2), OutlineTarget.Sheet(3)), tree.nodes.flatMap { node ->
            listOf(node.target) + node.children.flatMap { child -> listOf(child.target) + child.children.map { it.target } }
        })
    }

    @Test
    fun `each row mints its own bookmark id`() {
        val tree = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("One", 0, 0), entry("Two", 1, 0))))
        val minted = ids(tree)
        assertEquals(2, minted.size)
        assertTrue("ids are readable as bookmark ids: $minted", minted.all { it.startsWith("${ElementId.BOOKMARK}_") })
        assertEquals("and never shared", minted.size, minted.toSet().size)
    }

    @Test
    fun `a chapter with no destination of its own is written with the page it inherited`() {
        // The PDF's own tree: a header that points nowhere, with a section under it that does. Flattening
        // resolves the header to its first pointing descendant — which is why nothing upstream can leave the
        // row destination-less here.
        val raw = listOf(
            PdfOutlineNode(
                title = "Part one",
                pageIndex = PdfOutlineEntry.NO_DESTINATION,
                children = listOf(PdfOutlineNode("Chapter one", 4, emptyList())),
            ),
        )
        val tree = PdfOutlineSeeding.tree(PdfOutlineTree.flatten(raw))

        assertEquals("the header is a real destination, not a group", OutlineTarget.Sheet(4), tree.nodes.single().target)
        assertEquals(OutlineTarget.Sheet(4), tree.nodes.single().children.single().target)
    }

    @Test
    fun `a row that points nowhere even below itself stays a group`() {
        val tree = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("Blank", PdfOutlineEntry.NO_DESTINATION, 0))))
        assertNull("nothing to write, so nothing is claimed", tree.nodes.single().target)
    }

    @Test
    fun `a joined import shifts every destination into the joined file's numbering`() {
        val tree = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("Chapter", 0, 0), entry("Section", 2, 1))), pageOffset = 12)
        assertEquals(OutlineTarget.Sheet(12), tree.nodes.single().target)
        assertEquals(OutlineTarget.Sheet(14), tree.nodes.single().children.single().target)
    }

    @Test
    fun `an empty outline seeds nothing`() {
        assertTrue(PdfOutlineSeeding.tree(PdfOutline.EMPTY).isEmpty)
    }

    @Test
    fun `a first import is the tree itself, not a tree wrapped in its file name`() {
        val incoming = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("Chapter", 0, 0))))
        assertEquals(incoming, PdfOutlineSeeding.merged(OutlineTree.EMPTY, incoming, "chapter.pdf"))
    }

    @Test
    fun `a second import goes under one node named after the file, with no target`() {
        val first = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("Chapter", 0, 0))))
        val second = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("Chapter", 5, 0))), pageOffset = 3)
        val merged = PdfOutlineSeeding.merged(first, second, "appendix.pdf")

        assertEquals("the existing rows are untouched", first.nodes, merged.nodes.take(1))
        val group = merged.nodes.last()
        assertEquals("appendix.pdf", group.title)
        assertNull("the group opens nothing itself", group.target)
        assertEquals("it carries the arriving rows, at their joined pages", second.nodes, group.children)
        assertEquals(OutlineTarget.Sheet(8), group.children.single().target)
    }

    @Test
    fun `an import with no outline leaves an existing tree alone`() {
        val existing = PdfOutlineSeeding.tree(PdfOutline(listOf(entry("Chapter", 0, 0))))
        assertEquals(existing, PdfOutlineSeeding.merged(existing, OutlineTree.EMPTY, "scan.pdf"))
    }

    @Test
    fun `the seeded ids survive the manifest codec, so a rename still means the same row`() {
        val tree = PdfOutlineSeeding.tree(
            PdfOutline(listOf(entry("Geometry", 0, 0), entry("Points", 1, 1))),
            pageOffset = 2,
        )
        val reopened = MobixOutlineCodec.decode(MobixOutlineCodec.encode(tree))
        assertEquals("the whole tree, ids and targets included", tree, reopened)
        assertEquals(tree.nodes.single().id, reopened.nodes.single().id)
    }
}

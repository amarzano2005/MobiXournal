package com.mobixournal.format.mobix

import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppReader
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.withIdentity
import com.mobixournal.render.XoppSynthesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Contents tree through the `.xopp` bridge block (`docs/xopp-bridge.md`).
 *
 * A `.xopp` has no bookmarks and no page ids, so the export writes each row with a destination it can
 * **re-prove** on the way back in: a sheet for a page that shows one, an index plus the digest of the
 * page's ink for paper. Everything not still true when the file comes back — an edited page, a reordered
 * list, an index past the end — arrives as a row with no destination, never as one aimed at whatever page
 * happens to sit there now.
 */
class MobixMetaOutlineTest {

    private fun stroke(x: Double) = Stroke(
        points = listOf(StrokePoint(x, 10.0, 1.0), StrokePoint(x + 30.0, 40.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun paper(id: String, ink: List<Stroke> = listOf(stroke(5.0))) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(ink)),
        id = id,
    )

    private fun sheet(id: String, pageNo: Int) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Pdf(filename = "book.pdf", pageNo = pageNo, domain = "absolute"),
        layers = listOf(Layer(emptyList())),
        id = id,
    )

    private fun document(pages: List<Page> = listOf(paper("pg_1"), sheet("pg_2", 4))) = Document(
        pages = pages,
        outline = OutlineTree(
            nodes = listOf(
                OutlineNode(
                    "bkm_1",
                    "Chapter",
                    OutlineTarget.Page("pg_1"),
                    children = listOf(OutlineNode("bkm_2", "Sheet", OutlineTarget.Sheet(4))),
                ),
            ),
        ),
    ).withIdentity()

    private fun encode(document: Document): String = MobixMetaBlock(claims = emptyList(), outline = MobixMetaOutline.of(document)).encode()

    @Test
    fun `the export writes a paper target as its index and ink digest, and a sheet as its sheet`() {
        val text = encode(document())

        assertTrue("the id travels: $text", text.contains("\"id\":\"bkm_1\""))
        assertTrue("a paper page is named by position", text.contains("\"index\":0"))
        assertTrue("and by the digest of what is on it", text.contains("\"sha256\":"))
        assertTrue("a PDF page is named by its sheet", text.contains("\"sheet\":4"))
        assertTrue("the nesting is the tree's", text.contains("\"children\":"))
    }

    @Test
    fun `a block with no outline decodes with none`() {
        val block = MobixMetaBlock.decode(MobixMetaBlock(claims = emptyList()).encode())

        assertNotNull("the block itself still reads", block)
        assertNull("and says nothing about a tree", block?.outline)
    }

    @Test
    fun `a verified round trip through xopp keeps every node id and target`() {
        val source = document()
        val exported = XoppSynthesis.synthesize(source).document
        val imported = XoppReader(Xopp.toXml(exported)).read().withIdentity()

        val reopened = MobixDocument.of(imported, appVersion = "1.3.0", nowMillis = 1_775_952_000_000L).toDocument()

        assertEquals("the node ids are the file's own", listOf("bkm_1", "bkm_2"), reopened.outline.nodes.flatMap { listOf(it.id) + it.children.map { child -> child.id } })
        val chapter = reopened.outline.nodes.single()
        assertEquals("a paper row still opens its page", OutlineTarget.Page(reopened.pages[0].id!!), chapter.target)
        assertEquals("and a sheet row still opens its sheet", OutlineTarget.Sheet(4), chapter.children.single().target)
        assertEquals("the titles are unchanged", "Chapter", chapter.title)
    }

    @Test
    fun `a page edited in Xournal++ loses its destination and keeps its row`() {
        val source = document()
        val exported = XoppSynthesis.synthesize(source).document
        val imported = XoppReader(Xopp.toXml(exported)).read().withIdentity()
        // The ink at the page the row names is not the ink the block described.
        val edited = imported.copy(pages = listOf(imported.pages[0].copy(layers = listOf(Layer(listOf(stroke(90.0)))))) + imported.pages.drop(1))

        val reopened = MobixDocument.of(edited, appVersion = "1.3.0", nowMillis = 1_775_952_000_000L).toDocument()

        val chapter = reopened.outline.nodes.single()
        assertEquals("the row is still there", "Chapter", chapter.title)
        assertNull("with nothing to open", chapter.target)
        assertEquals("and its children survived", "Sheet", chapter.children.single().title)
        assertEquals("whose sheet still verifies", OutlineTarget.Sheet(4), chapter.children.single().target)
    }

    @Test
    fun `a reordered page list does not move a row to a page it never named`() {
        val source = document()
        val exported = XoppSynthesis.synthesize(source).document
        val imported = XoppReader(Xopp.toXml(exported)).read().withIdentity()
        val reordered = imported.copy(pages = imported.pages.reversed())

        val reopened = MobixDocument.of(reordered, appVersion = "1.3.0", nowMillis = 1_775_952_000_000L).toDocument()

        assertNull("the index no longer names the page it did", reopened.outline.nodes.single().target)
        assertEquals("but the sheet row still points at the PDF's own page", OutlineTarget.Sheet(4), reopened.outline.nodes.single().children.single().target)
    }

    @Test
    fun `an index past the end arrives with no destination`() {
        val page = paper("pg_1")
        val tree = MobixMetaOutline.Tree(
            nodes = listOf(
                MobixMetaOutline.Row(
                    "bkm_9",
                    "Gone",
                    MobixMetaOutline.Target.IndexedPage(7, MobixMetaDigest.of(page.layers.flatMap { it.elements })),
                ),
            ),
        )

        val (_, outline) = MobixMetaOutline.revive(tree, Document(pages = listOf(page)))

        assertEquals("the row is kept", "Gone", outline.nodes.single().title)
        assertNull("without a destination", outline.nodes.single().target)
    }

    @Test
    fun `a file desktop Xournal++ resaved has no block, so the import has no outline`() {
        val exported = XoppSynthesis.synthesize(document()).document
        val resaved = Xopp.toXml(exported).replace(Regex("<!--\\s*mobix:meta.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val imported = XoppReader(resaved).read().withIdentity()

        val reopened = MobixDocument.of(imported, appVersion = "1.3.0", nowMillis = 1_775_952_000_000L).toDocument()

        assertTrue("the ink is still the drawing it is", imported.pages.isNotEmpty())
        assertTrue("and no tree was invented", reopened.outline.isEmpty)
    }

    @Test
    fun `an outline with no rows writes no key`() {
        val tree = MobixMetaOutline.of(Document(pages = listOf(paper("pg_1"))))

        assertNull("a document with no Contents has nothing to carry", tree)
    }
}

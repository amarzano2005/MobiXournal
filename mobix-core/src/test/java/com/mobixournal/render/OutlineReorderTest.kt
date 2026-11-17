package com.mobixournal.render

import com.mobixournal.format.Xopp
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class OutlineReorderTest {
    @Test fun aMoveKeepsUnknownTreeAndNodeFields() {
        val extra = mapOf("future" to com.mobixournal.format.json.JsonNode.Str("keep"))
        val marked = first.copy(extra = extra)
        val source = tree.copy(nodes = listOf(marked, second), extra = extra)
        val moved = (OutlineEdits.moveSibling(source, marked.id, 1) as OutlineEdits.Result.Ok).tree
        assertSame(marked, moved.nodes[1])
        assertEquals(extra, moved.extra)
        assertEquals(extra, moved.nodes[1].extra)
    }
    private val section = OutlineNode("bkm_3", "Section", OutlineTarget.Sheet(4))
    private val first = OutlineNode("bkm_1", "First", OutlineTarget.Sheet(1), listOf(section))
    private val second = OutlineNode("bkm_2", "Second", OutlineTarget.Sheet(2))
    private val tree = OutlineTree(nodes = listOf(first, second))

    @Test fun movingAChapterCarriesTheIdenticalSubtreeAndPreservesTargets() {
        val moved = (OutlineEdits.moveSibling(tree, first.id, 1) as OutlineEdits.Result.Ok).tree
        assertEquals(listOf(second, first), moved.nodes)
        assertSame(first, moved.nodes[1])
        assertSame(section, moved.nodes[1].children.single())
        assertSame(tree, (OutlineEdits.moveSibling(tree, first.id, -1) as OutlineEdits.Result.Ok).tree)
        assertSame(tree, (OutlineEdits.moveSibling(tree, second.id, 1) as OutlineEdits.Result.Ok).tree)
        assertEquals(OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND), OutlineEdits.moveSibling(tree, "missing", 1))
    }

    @Test fun aChildStaysUnderItsOwnParent() {
        val next = section.copy(id = "bkm_4", title = "Next")
        val nested = tree.copy(nodes = listOf(first.copy(children = listOf(section, next)), second))
        val moved = (OutlineEdits.moveSibling(nested, next.id, -1) as OutlineEdits.Result.Ok).tree
        assertEquals(listOf(next, section), moved.nodes[0].children)
        assertEquals(second, moved.nodes[1])
        assertEquals(2, moved.nodes.size)
    }

    @Test fun reorderedTreeSurvivesBothDocumentFormats() {
        val moved = (OutlineEdits.moveSibling(tree, first.id, 1) as OutlineEdits.Result.Ok).tree
        val pages = (1..4).map { Page(595.0, 842.0, Background.Pdf("book.pdf", it, "absolute"), listOf(Layer(emptyList()))) }
        val document = Document(pages = pages, outline = moved).withIdentity()
        val native = ByteArrayOutputStream().also { MobixDocument.of(document, appVersion = "2.2.0", nowMillis = 1L).save(it, 1L) }.toByteArray()
        assertEquals(moved, MobixDocument.open(MobixContainer.open(ByteArrayInputStream(native))).toDocument().outline)
        val xopp = ByteArrayOutputStream().also { Xopp.save(XoppSynthesis.synthesize(document).document, it) }.toByteArray()
        assertEquals(moved, MobixDocument.of(Xopp.open(ByteArrayInputStream(xopp)).withIdentity(), appVersion = "2.2.0", nowMillis = 1L).toDocument().outline)
    }

    @Test fun siblingChoicesRespectTheHierarchyRatherThanFilteredRowPositions() {
        val rows = listOf(
            OutlineRow(first.id, first.title, 0, 0), OutlineRow(section.id, section.title, 1, 3),
            OutlineRow(second.id, second.title, 0, 1),
        )
        val moves = OutlineSiblingOrder.of(rows)
        assertEquals(OutlineSiblingOrder.Moves(down = true), moves[first.id])
        assertEquals(OutlineSiblingOrder.Moves(), moves[section.id])
        assertEquals(OutlineSiblingOrder.Moves(up = true), moves[second.id])
    }
}

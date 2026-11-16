package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page

/**
 * The notebook's **Contents tree** inside the bridge block (`docs/xopp-bridge.md` → *The outline in the
 * bridge block*): a `.xopp` has no bookmark tree of its own, so an export writes one beside the living
 * entities, and an import reads it back as a **claim** exactly as it reads those.
 *
 * The shape is the manifest's own ([mobix-data-model.md](../..) holds the schema), with one difference
 * forced by the container: a target cannot be written as a page id, because a `.xopp` has no ids. So it is
 * written in the form the file in front of us can still check —
 *
 *  - a page that shows a PDF sheet is named by that `sheet`, which survives a reorder because it is a fact
 *    about the PDF rather than about the page list;
 *  - a plain page is named by its `index` **and the digest of its ink** ([MobixMetaDigest]), which survives
 *    nothing: an inserted page, a reordered list or an edit at that index all make the digest stop matching.
 *
 * An import honours a target only while that proof holds, and otherwise writes the row with **no
 * destination** — title, id and children intact, un-tappable. A row that aimed at a page which merely
 * happens to be nearby would be worse than one that says it no longer knows (`docs/pdf.md`).
 */
object MobixMetaOutline {

    /** One row as the block carries it: identity, title and the checkable form of its destination. */
    data class Row(
        val id: String,
        val title: String = "",
        val target: Target? = null,
        val children: List<Row> = emptyList(),
    )

    /** A destination written so the import can re-prove it against the file it is reading. */
    sealed interface Target {

        /** A page showing PDF sheet [pageNo]; checkable as long as some page still shows it. */
        data class Sheet(val pageNo: Int) : Target

        /** The page at [index] whose ink hashes to [sha256]; checkable only while the list is untouched. */
        data class IndexedPage(val index: Int, val sha256: String) : Target
    }

    /** The tree as written in the block: the rows, in the tree's order. */
    data class Tree(val nodes: List<Row>)

    /**
     * [document]'s outline as the block carries it, or null when there is none to write.
     *
     * [pages] are the pages the export is **writing** — the compiled ones — because the digest a plain
     * target records must be the digest the import will compute over the same ink.
     */
    fun of(document: Document, pages: List<Page> = document.pages): Tree? {
        val outline = document.outline
        if (outline.isEmpty) return null
        val indexById = HashMap<String, Int>(pages.size)
        pages.forEachIndexed { index, page -> page.id?.let { indexById.putIfAbsent(it, index) } }
        return Tree(outline.nodes.map { row(it, pages, indexById) })
    }

    private fun row(node: OutlineNode, pages: List<Page>, indexById: Map<String, Int>): Row = Row(
        id = node.id,
        title = node.title,
        target = target(node.target, pages, indexById),
        children = node.children.map { row(it, pages, indexById) },
    )

    private fun target(target: OutlineTarget?, pages: List<Page>, indexById: Map<String, Int>): Target? = when (target) {
        null -> null
        is OutlineTarget.Sheet -> Target.Sheet(target.pageNo)
        // A page this export is not writing (one nothing points at any more) has no index to record, so
        // the row travels as a group rather than as a target that would not verify anyway.
        is OutlineTarget.Page -> indexById[target.pageId]?.let { index -> Target.IndexedPage(index, MobixMetaDigest.of(elements(pages[index]))) }
    }

    /** Every element a page holds, layer by layer — the ink a page target's digest covers. */
    private fun elements(page: Page) = page.layers.flatMap { it.elements }

    /** [tree] as the JSON object the block's `outline` key holds, rows in the tree's order. */
    fun encode(tree: Tree): JsonNode.Obj = MobixJson.obj { out ->
        out["nodes"] = JsonNode.arr(tree.nodes.map(::rowNode))
    }

    private fun rowNode(row: Row): JsonNode = MobixJson.obj { out ->
        out["id"] = JsonNode.of(row.id)
        if (row.title.isNotEmpty()) out["title"] = JsonNode.of(row.title)
        when (val target = row.target) {
            null -> Unit
            is Target.Sheet -> out["sheet"] = JsonNode.of(target.pageNo)
            is Target.IndexedPage -> {
                out["index"] = JsonNode.of(target.index)
                out["sha256"] = JsonNode.of(target.sha256)
            }
        }
        if (row.children.isNotEmpty()) out["children"] = JsonNode.arr(row.children.map(::rowNode))
    }

    /**
     * The tree [node] describes, or null when it is not one this build can read.
     *
     * Null covers a missing object, an `outline` without a `nodes` list and a tree nested past
     * [MAX_DEPTH]. A malformed *row* is dropped on its own, like a malformed claim: one row this build
     * cannot read is not a reason to lose the chapters beneath it.
     */
    fun decode(node: JsonNode.Obj?): Tree? {
        node ?: return null
        val nodes = node.getArr("nodes") ?: return null
        return Tree(nodes.list.mapNotNull { (it as? JsonNode.Obj)?.let { row -> row(row, 1) } })
    }

    private fun row(node: JsonNode.Obj, depth: Int): Row? {
        if (depth > MAX_DEPTH) return null
        val id = node.getStrOrNull("id")?.takeIf { it.isNotBlank() } ?: return null
        val children = node.getArr("children")?.list
            ?.mapNotNull { (it as? JsonNode.Obj)?.let { child -> row(child, depth + 1) } }
            .orEmpty()
        return Row(
            id = id,
            title = node.getStrOrNull("title").orEmpty(),
            target = target(node),
            children = children,
        )
    }

    private fun target(node: JsonNode.Obj): Target? {
        node.getIntOrNull("sheet")?.let { return Target.Sheet(it) }
        val index = node.getIntOrNull("index") ?: return null
        val digest = node.getStrOrNull("sha256")?.takeIf { it.isNotBlank() } ?: return null
        return Target.IndexedPage(index, digest)
    }

    /**
     * The tree [tree] describes, checked against [document]: every target whose proof still holds becomes a
     * real [OutlineTarget], and one whose proof does not is dropped from the row rather than re-aimed.
     *
     * A verified `index` target needs the page's **id**, and a `.xopp` has none, so the page it names is
     * given one here — the id the import then keeps. The returned document carries those pages; every
     * other page is left as the caller had it.
     */
    fun revive(tree: Tree, document: Document): Pair<Document, OutlineTree> {
        val pages = document.pages.toMutableList()
        val nodes = tree.nodes.map { row -> node(row, document.pages, pages) }
        val outlined = if (pages == document.pages) document else document.copy(pages = pages)
        return outlined to OutlineTree(nodes = nodes)
    }

    private fun node(row: Row, original: List<Page>, pages: MutableList<Page>): OutlineNode = OutlineNode(
        id = row.id,
        title = row.title,
        target = target(row.target, original, pages),
        children = row.children.map { child -> node(child, original, pages) },
    )

    private fun target(target: Target?, original: List<Page>, pages: MutableList<Page>): OutlineTarget? = when (target) {
        null -> null
        is Target.Sheet ->
            if (original.any { page -> (page.background as? Background.Pdf)?.pageNo == target.pageNo }) {
                OutlineTarget.Sheet(target.pageNo)
            } else {
                null
            }
        is Target.IndexedPage -> {
            val page = original.getOrNull(target.index)
            if (page != null && MobixMetaDigest.of(elements(page)) == target.sha256) {
                val id = page.id ?: ElementId.mint(ElementId.PAGE).also { minted ->
                    pages[target.index] = page.copy(id = minted)
                }
                OutlineTarget.Page(id)
            } else {
                null
            }
        }
    }

    /** The deepest a row may sit; a tree past it is not one this build wrote. */
    private const val MAX_DEPTH = OutlineTree.MAX_DEPTH
}

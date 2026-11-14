package com.mobixournal.format.model

import com.mobixournal.format.json.JsonNode

/**
 * What a **Contents row** opens: one page, named in the way that page can still be found later.
 *
 * Two kinds, because a notebook holds two kinds of page and they survive different edits. [Sheet] names
 * a page **inside the document's PDF**, which is what an imported bookmark means and what a `.xopp`
 * spelling can still check; [Page] names a notebook page by its identity ([ElementId.PAGE]), which is what
 * makes ordinary paper bookmarkable and what keeps a row on its page when the page list is reordered.
 *
 * Whatever names it, a target is a **question with an honest no**: the resolver looks for exactly that
 * sheet or exactly that id and answers "nowhere" when it is gone. It never clamps, never rounds and never
 * falls back to a neighbouring page — a bookmark that leads somewhere the reader did not choose is worse
 * than one that says it no longer knows (`docs/pdf.md`).
 */
sealed interface OutlineTarget {

    /**
     * A 0-based page index **inside the PDF file** the document carries — the same number a page's
     * `Background.Pdf.pageNo` holds, never a notebook position.
     */
    data class Sheet(val pageNo: Int) : OutlineTarget

    /** The identity of the notebook page itself ([ElementId.PAGE]), as `pages[].id` writes it. */
    data class Page(val pageId: String) : OutlineTarget
}

/**
 * One node of the notebook's **Contents tree**: a title, where it opens, and the nodes under it.
 *
 * [id] is the node's identity and the only thing about it that never changes — the row a reader is
 * looking at, the row an undo compares, and the row a selection remembers are all keyed by it. Two
 * chapters may share a title, be renamed, or be dragged somewhere else and stay the same node; nothing
 * here is derived from the title or from the position on purpose.
 *
 * A node with no [target] is a **group**: a chapter header, or a row whose page is gone. It is shown,
 * it folds, and it opens nothing — the rows under it keep their own targets.
 *
 * [extra] holds the JSON fields a newer writer put here that this build does not model, in their own
 * order, so a save writes them back (`docs/mobix-data-model.md`, the retention rule).
 */
data class OutlineNode(
    val id: String,
    val title: String = "",
    val target: OutlineTarget? = null,
    val children: List<OutlineNode> = emptyList(),
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    /** How many nodes this one stands for, itself included. */
    val size: Int get() = 1 + children.sumOf { it.size }
}

/**
 * The **Contents tree** a document owns (`Document.outline`), or [EMPTY] for one that has none.
 *
 * This is the notebook's own index, not the PDF's: an imported PDF seeds it once, and from then on the
 * rows are the reader's — renamed, added, moved and deleted without the file underneath changing
 * (`docs/pdf.md`). It lives in the `.mobix` manifest's `outline` key, and its node order is the tree's
 * order rather than a sort by title, which is what lets a row be dragged.
 *
 * [version] is the node schema's version: this build reads and writes [CURRENT_VERSION], and the codec
 * refuses a tree from a newer one whole rather than half-reading a shape it does not know.
 */
data class OutlineTree(
    val version: Int = CURRENT_VERSION,
    val nodes: List<OutlineNode> = emptyList(),
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    /** True when there is no row to show — the state every document is in until something seeds one. */
    val isEmpty: Boolean get() = nodes.isEmpty()

    /** How many nodes the tree holds. */
    val size: Int get() = nodes.sumOf { it.size }

    companion object {

        /** The node schema this build writes ([CURRENT_VERSION] of `docs/mobix-data-model.md`). */
        const val CURRENT_VERSION: Int = 1

        /**
         * The deepest level allowed, counting the top-level nodes as level 0.
         *
         * A cap rather than a promise: a tree is walked recursively by every reader, and a hostile file
         * should not be able to decide how deep that goes. Sixteen levels is far past any real book — and
         * deliberately inside the JSON reader's own limit, which counts two levels per row (a node object
         * and its `children` array), so that *this* rule is the one that answers and the refusal names a
         * tree rather than a parser.
         */
        const val MAX_DEPTH: Int = 16

        /** A document with no notebook outline — what every `.xopp` and every older `.mobix` opens as. */
        val EMPTY: OutlineTree = OutlineTree()
    }
}

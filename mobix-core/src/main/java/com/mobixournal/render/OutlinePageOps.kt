package com.mobixournal.render

import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree

/**
 * What a **page-list edit** does to the notebook's Contents ([OutlineTree], `docs/pdf.md`).
 *
 * The two lists are joined by identity, not by position, so most page edits need nothing from the tree:
 * a **move** keeps every `page` target because a page's id does not move with it, and a **duplicate**
 * mints a new page id, so the copy inherits no bookmark. Only a **delete** changes a target's answer,
 * and even then the row is not the page operation's to remove: a chapter the reader wrote stays in the
 * tree, with no destination (`OutlineTarget`), exactly as a row whose PDF sheet is gone is dimmed rather
 * than re-aimed at its neighbour. Deleting a bookmark is a Contents edit, not a page operation.
 */
object OutlinePageOps {

    /**
     * [tree] after the pages in [removedPageIds] leave the document: every node that pointed at one of
     * them keeps its place, title and children and loses its target. Sheets, other pages and groups are
     * untouched, and so is a tree with nothing to change — the same instance, so a commit that compares
     * by identity sees no edit.
     */
    fun afterRemoval(tree: OutlineTree, removedPageIds: Set<String>): OutlineTree {
        if (tree.isEmpty || removedPageIds.isEmpty()) return tree
        var changed = false

        fun retarget(node: OutlineNode): OutlineNode {
            val children = node.children.map(::retarget)
            val target = node.target
            val kept = if (target is OutlineTarget.Page && target.pageId in removedPageIds) {
                changed = true
                null
            } else {
                target
            }
            return if (kept === target && children == node.children) node else node.copy(target = kept, children = children)
        }

        val nodes = tree.nodes.map(::retarget)
        return if (changed) tree.copy(nodes = nodes) else tree
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page

/**
 * The notebook's **Contents edits** — add, rename, delete — as pure tree transforms (`docs/pdf.md`).
 *
 * The rules are stated once here so the tablet, the window and any test cannot disagree about them:
 *
 *  - A row's **identity is its id**, never its title, so renaming keeps both the node and its target,
 *    and two chapters may share a title.
 *  - A **title is required**: a blank one is refused rather than saved as an untitled row, because an
 *    empty row is indistinguishable from a group and would be a bookmark nobody can find again.
 *  - Adding a row writes the destination the caller resolved ([targetOf]: a page that shows a PDF sheet
 *    is named by that sheet, paper by its page id), and mints a fresh `bkm_` id.
 *  - **Deleting a chapter promotes its children** to its own position rather than deleting them with it:
 *    the reader asked to remove one row, not the sections under it, and nothing in a notebook should
 *    vanish because of a row above it. Deleting a row never touches a page.
 *  - Every refusal leaves the tree **as it was** — the same instance, so a commit that compares by
 *    identity records no edit and the undo history gains no step.
 */
object OutlineEdits {

    /** What a Contents edit did: the tree to commit, or why it changed nothing. */
    sealed interface Result {

        /** The edited tree. It may be the one handed in, when the edit was a no-op. */
        data class Ok(val tree: OutlineTree) : Result

        /** Nothing was written; [reason] is the sentence the chrome shows. */
        data class Refused(val reason: Refusal) : Result
    }

    /** Why a Contents edit changed nothing. The copy each shell shows is the shell's own wording. */
    enum class Refusal {
        /** The title was blank once trimmed. */
        EMPTY_TITLE,

        /** No row carries the id the edit names. */
        NOT_FOUND,

        /** The target page has no identity to bookmark (a page that was never given an id). */
        NO_TARGET,
    }

    /**
     * The destination a row on [page] should carry.
     *
     * A page showing a **PDF sheet** is named by that sheet: the number belongs to the file, so the row
     * survives a reordered page list. Paper is named by the page's own id ([ElementId.PAGE]), which
     * survives a move for the same reason. Null when the page has neither (an identified page is what a
     * document carries by the time it can be edited).
     */
    fun targetOf(page: Page): OutlineTarget? = when (val background = page.background) {
        is Background.Pdf -> OutlineTarget.Sheet(background.pageNo)
        else -> page.id?.let { OutlineTarget.Page(it) }
    }

    /**
     * [page] as a bookmark can name it, paired with that destination: the page itself for a sheet or an
     * already-identified page, and the same page **given an id** for paper that has none yet — a fresh
     * document has no page ids until it is saved, and a row cannot wait for that. The caller writes the
     * page back inside the same edit, so the id the row names is the page's from then on.
     */
    fun identifiable(page: Page): Pair<Page, OutlineTarget?> {
        targetOf(page)?.let { return page to it }
        if (page.background is Background.Pdf) return page to null
        val identified = page.copy(id = ElementId.mint(ElementId.PAGE))
        return identified to targetOf(identified)
    }

    /**
     * [tree] with a row titled [title] opening [target], appended after the other roots — or under the row
     * [parentId] names, when one is given.
     */
    fun add(tree: OutlineTree, title: String, target: OutlineTarget, parentId: String? = null): Result {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return Result.Refused(Refusal.EMPTY_TITLE)
        val node = OutlineNode(ElementId.mint(ElementId.BOOKMARK), trimmed, target)
        if (parentId == null) return Result.Ok(tree.copy(nodes = tree.nodes + node))
        var found = false
        fun into(nodes: List<OutlineNode>): List<OutlineNode> = nodes.map { current ->
            when {
                current.id == parentId -> {
                    found = true
                    current.copy(children = current.children + node)
                }
                else -> current.copy(children = into(current.children))
            }
        }
        val nodes = into(tree.nodes)
        return if (found) Result.Ok(tree.copy(nodes = nodes)) else Result.Refused(Refusal.NOT_FOUND)
    }

    /** [tree] with the row [id] retitled [title], keeping its node, its target and its children. */
    fun rename(tree: OutlineTree, id: String, title: String): Result {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return Result.Refused(Refusal.EMPTY_TITLE)
        var found = false
        fun into(nodes: List<OutlineNode>): List<OutlineNode> = nodes.map { current ->
            when {
                current.id == id -> {
                    found = true
                    if (current.title == trimmed) current else current.copy(title = trimmed)
                }
                else -> current.copy(children = into(current.children))
            }
        }
        val nodes = into(tree.nodes)
        return if (found) Result.Ok(tree.copy(nodes = nodes)) else Result.Refused(Refusal.NOT_FOUND)
    }

    /**
     * [tree] without the row [id]; its children take its place, in their own order.
     *
     * The page the row opened is untouched — a bookmark is a pointer, and deleting one is not a page
     * operation (`OutlinePageOps` owns the other direction).
     */
    fun delete(tree: OutlineTree, id: String): Result {
        var found = false
        fun into(nodes: List<OutlineNode>): List<OutlineNode> = nodes.flatMap { current ->
            when {
                current.id == id -> {
                    found = true
                    current.children
                }
                else -> listOf(current.copy(children = into(current.children)))
            }
        }
        val nodes = into(tree.nodes)
        return if (found) Result.Ok(tree.copy(nodes = nodes)) else Result.Refused(Refusal.NOT_FOUND)
    }
}

package com.mobixournal.format.mobix

import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTree

/**
 * The Contents trees of two branches, merged the way their pages are (`MobixMergeEngine`).
 *
 * A node is identified by its [OutlineNode.id], never by its title or its position: one side having a
 * node the other lacks keeps it, and a merge therefore **never deletes** — the same promise the page
 * walk makes, and for the same reason, since a branch that dropped a chapter cannot prove the reader on
 * its own device was the one who did. A node both sides have stays where the local side put it and keeps
 * the local title, target and extras (the local side is the document being saved), and its children are
 * merged by the same rule, so a remote chapter added under a shared heading still arrives.
 *
 * Ids are written back unchanged, which is what makes a second merge idempotent and what lets a rename
 * on either side land on the row the reader already has.
 */
object MobixOutlineMerge {

    /** The union of [local] and [remote], local order and local metadata first at every level. */
    fun merge(local: OutlineTree, remote: OutlineTree): OutlineTree {
        if (remote.isEmpty) return local
        if (local.isEmpty) return remote
        return local.copy(nodes = union(local.nodes, remote.nodes))
    }

    private fun union(local: List<OutlineNode>, remote: List<OutlineNode>): List<OutlineNode> {
        val remoteById = remote.associateBy { it.id }
        val seen = HashSet<String>(local.size + remote.size)
        val merged = ArrayList<OutlineNode>(local.size + remote.size)
        for (node in local) {
            seen += node.id
            val twin = remoteById[node.id]
            merged += if (twin == null) node else node.copy(children = union(node.children, twin.children))
        }
        // A node only the remote side has keeps the position that side gave it, after the local rows.
        for (node in remote) if (node.id !in seen) merged += node
        return merged
    }
}

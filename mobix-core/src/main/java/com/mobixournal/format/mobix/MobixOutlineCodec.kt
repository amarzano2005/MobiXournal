package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree

/**
 * The `outline` key of `manifest.json`: the notebook's Contents tree, read and written
 * (`docs/mobix-data-model.md` → *The outline tree*).
 *
 * Three things this codec is strict about, and why each is a refusal rather than a repair:
 *
 *  - **a node id appears once.** The id is what every reader, selection and undo keys a row by, so a
 *    tree naming one id twice has no single answer to "which node is this" — rewriting one of them would
 *    silently detach whatever the other referred to.
 *  - **one target per node, of a kind this build knows.** A node carrying both `sheet` and `page` says two
 *    pages are its destination; a target with a key that is neither is a reference from a newer writer,
 *    and reading it as the kind we happen to know could open the wrong page. A writer that adds a third
 *    kind bumps [OutlineTree.CURRENT_VERSION], and a tree from a newer version is refused whole.
 *  - **the tree is not arbitrarily deep.** [OutlineTree.MAX_DEPTH] is the cap, since every reader walks
 *    it recursively.
 *
 * Within those rules it is generous in the format's usual way: an absent `nodes` is an empty tree, an
 * absent or empty `title` is a node with no text yet, and every field this build does not model is kept
 * in [OutlineTree.extra] / [OutlineNode.extra] and written back after the known ones.
 */
internal object MobixOutlineCodec {

    private const val KEY_VERSION = "version"
    private const val KEY_NODES = "nodes"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_TARGET = "target"
    private const val KEY_CHILDREN = "children"
    private const val TARGET_SHEET = "sheet"
    private const val TARGET_PAGE = "page"

    private val TREE_KEYS = setOf(KEY_VERSION, KEY_NODES)
    private val NODE_KEYS = setOf(KEY_ID, KEY_TITLE, KEY_TARGET, KEY_CHILDREN)

    /**
     * The tree [node] describes.
     *
     * @throws MobixContainer.MalformedMobixException for a `version` newer than this build's, a `nodes`
     *   that is not an array, a node that is not an object or has no usable `id`, a duplicate id, a node
     *   with two targets or with a target this build does not know, and nesting past the cap.
     */
    fun decode(node: JsonNode.Obj): OutlineTree {
        val version = node.getInt(KEY_VERSION, OutlineTree.CURRENT_VERSION)
        if (version > OutlineTree.CURRENT_VERSION) {
            refuse("version $version is newer than this build's ${OutlineTree.CURRENT_VERSION}")
        }
        val seen = HashSet<String>()
        val nodes = when {
            !node.has(KEY_NODES) -> emptyList()
            node.getArr(KEY_NODES) == null -> refuse("\"$KEY_NODES\" is not an array")
            else -> node.getArr(KEY_NODES)!!.list.map { item ->
                val entry = item as? JsonNode.Obj ?: refuse("a node is not a JSON object")
                decodeNode(entry, depth = 0, seen = seen)
            }
        }
        return OutlineTree(
            version = version,
            nodes = nodes,
            extra = node.map.filterKeys { it !in TREE_KEYS },
        )
    }

    private fun decodeNode(node: JsonNode.Obj, depth: Int, seen: MutableSet<String>): OutlineNode {
        if (depth >= OutlineTree.MAX_DEPTH) {
            refuse("a node sits at level $depth, past the ${OutlineTree.MAX_DEPTH}-level cap")
        }
        val id = node.getStrOrNull(KEY_ID)?.takeIf { it.isNotBlank() }
            ?: refuse("a node has no usable \"$KEY_ID\"")
        if (!seen.add(id)) refuse("the id \"$id\" names two nodes")
        if (node.has(KEY_TITLE) && node.getStrOrNull(KEY_TITLE) == null) {
            refuse("node \"$id\": \"$KEY_TITLE\" is not a string")
        }
        val children = when {
            !node.has(KEY_CHILDREN) -> emptyList()
            node.getArr(KEY_CHILDREN) == null -> refuse("node \"$id\": \"$KEY_CHILDREN\" is not an array")
            else -> node.getArr(KEY_CHILDREN)!!.list.map { item ->
                val entry = item as? JsonNode.Obj ?: refuse("node \"$id\": a child is not a JSON object")
                decodeNode(entry, depth + 1, seen)
            }
        }
        return OutlineNode(
            id = id,
            title = node.getStr(KEY_TITLE, ""),
            target = node.getObj(KEY_TARGET)?.let { targetOf(it, id) },
            children = children,
            extra = node.map.filterKeys { it !in NODE_KEYS },
        )
    }

    /**
     * The target [node] describes, or null when a node that has a `target` key is not usable as a
     * reference — which this build treats as corruption rather than as "leads nowhere": a row written to
     * point somewhere must not come back silently pointing at nothing.
     */
    private fun targetOf(node: JsonNode.Obj, id: String): OutlineTarget {
        val sheet = node.getIntOrNull(TARGET_SHEET)
        val page = node.getStrOrNull(TARGET_PAGE)?.takeIf { it.isNotBlank() }
        val unknown = node.map.keys - setOf(TARGET_SHEET, TARGET_PAGE)
        if (unknown.isNotEmpty()) refuse("node \"$id\": target has an unknown kind ${unknown.sorted()}")
        if (sheet != null && page != null) refuse("node \"$id\": target names both a sheet and a page")
        sheet?.let { return OutlineTarget.Sheet(it) }
        page?.let { return OutlineTarget.Page(it) }
        refuse("node \"$id\": target names neither a sheet nor a page")
    }

    /** [tree] as the object the manifest's `outline` key holds, known fields first, extras last. */
    fun encode(tree: OutlineTree): JsonNode.Obj = JsonNode.Obj(
        LinkedHashMap<String, JsonNode>().also { out ->
            out[KEY_VERSION] = JsonNode.of(tree.version)
            if (tree.nodes.isNotEmpty()) out[KEY_NODES] = JsonNode.arr(tree.nodes.map(::encodeNode))
            out.putAll(tree.extra)
        },
    )

    private fun encodeNode(node: OutlineNode): JsonNode = JsonNode.Obj(
        LinkedHashMap<String, JsonNode>().also { out ->
            out[KEY_ID] = JsonNode.of(node.id)
            if (node.title.isNotEmpty()) out[KEY_TITLE] = JsonNode.of(node.title)
            node.target?.let { target ->
                out[KEY_TARGET] = when (target) {
                    is OutlineTarget.Sheet -> JsonNode.Obj(linkedMapOf(TARGET_SHEET to JsonNode.of(target.pageNo)))
                    is OutlineTarget.Page -> JsonNode.Obj(linkedMapOf(TARGET_PAGE to JsonNode.of(target.pageId)))
                }
            }
            if (node.children.isNotEmpty()) out[KEY_CHILDREN] = JsonNode.arr(node.children.map(::encodeNode))
            out.putAll(node.extra)
        },
    )

    private fun refuse(why: String): Nothing = throw MobixContainer.MalformedMobixException(
        "manifest.json: outline $why",
    )
}

package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.isLivingEntity

/**
 * One **claim** the bridge block makes about an exported `.xopp`: the living entity [element] was
 * written into that file as [count] elements of page [page], layer [layer], starting at index [at] —
 * and [sha256] is the digest of exactly those elements ([MobixMetaDigest]).
 *
 * `element` is the entity as the `.mobix` holds it (id included, because identity is the merge's key and
 * must survive the trip), and the position is stated as an **index range** rather than an id because a
 * `.xopp` has no ids at all: the flattening is the only thing that knows where an entity's ink landed,
 * and the digest is what makes the range checkable rather than merely asserted.
 */
data class MobixMetaClaim(
    val page: Int,
    val layer: Int,
    val at: Int,
    val count: Int,
    val sha256: String,
    val element: Element,
)

/**
 * The **bridge block** an exported `.xopp` carries in its `<!-- mobix:meta … -->` comment
 * (`docs/mobix-format.md`): the living entities the export flattened, each with the digest of the ink it
 * became, so the export can be re-imported as the document it was instead of as a drawing of it.
 *
 * It is written as **one payload with no structure of its own in the XML** — the comment holds this
 * JSON and nothing else — and it is read back as a *claim*, never as truth: the import honours an entry
 * only when the ink it names is still there and still hashes to what the entry says
 * ([MobixMetaRevival]). A block that is unreadable, that comes from a newer schema version, or that
 * describes elements this build no longer knows is therefore **discarded whole**, and the file opens as
 * the ordinary ink it also is. That direction is the only safe one: a claim that cannot be checked must
 * never be able to reinterpret the user's drawing behind their back.
 *
 * The schema is deliberately flat and ordered `version`, `doc`, `elements` so a diff of two exported
 * files reads as a diff of the block. `doc` is the exporting document's identity — informative, since
 * an import cannot always know which `.mobix` this came from, and the digests are what actually decide.
 */
data class MobixMetaBlock(
    /** The block's schema version; a newer one is refused rather than half-read. */
    val version: Int = CURRENT_VERSION,

    /** The `.mobix` document's identity, when the export knew it. */
    val doc: String? = null,

    /** The claims, in document order (page, then layer, then position). */
    val claims: List<MobixMetaClaim> = emptyList(),
) {

    /** This block as its JSON payload — the text that goes into the comment. */
    fun encode(): String = JsonWriter.write(
        MobixJson.obj { out ->
            out["version"] = JsonNode.of(version)
            doc?.let { out["doc"] = JsonNode.of(it) }
            out["elements"] = JsonNode.arr(claims.map(::claimNode))
        },
    )

    private fun claimNode(claim: MobixMetaClaim): JsonNode = MobixJson.obj { out ->
        out["page"] = JsonNode.of(claim.page)
        out["layer"] = JsonNode.of(claim.layer)
        out["at"] = JsonNode.of(claim.at)
        out["count"] = JsonNode.of(claim.count)
        out["sha256"] = JsonNode.of(claim.sha256)
        out["element"] = MobixElementCodec.encode(claim.element, MobixAssetSink())
    }

    companion object {

        /** The schema version this build writes. */
        const val CURRENT_VERSION: Int = 1

        /**
         * The block [text] describes, or null when it cannot be honoured.
         *
         * Null is returned for text that is not a JSON object, for a `version` this build does not know
         * (a newer export's semantics are not ours to guess), and for a block whose `elements` is not a
         * list. A malformed *entry* is dropped on its own rather than taking the block down with it:
         * claims are independent, and an entity this build cannot read is not a reason to discard the
         * ones it can.
         */
        fun decode(text: String): MobixMetaBlock? {
            val root = runCatching { JsonParser.parse(text) }.getOrNull() as? JsonNode.Obj ?: return null
            if (root.getInt("version", CURRENT_VERSION) > CURRENT_VERSION) return null
            val entries = root.getArr("elements") ?: return null
            return MobixMetaBlock(
                version = root.getInt("version", CURRENT_VERSION),
                doc = root.getStrOrNull("doc"),
                claims = entries.list.mapNotNull { entry -> (entry as? JsonNode.Obj)?.let(::claim) },
            )
        }

        /** One claim, or null when the entry is not a usable claim about a living entity. */
        private fun claim(node: JsonNode.Obj): MobixMetaClaim? {
            val at = node.getIntOrNull("at") ?: return null
            val count = node.getIntOrNull("count") ?: return null
            val digest = node.getStrOrNull("sha256")?.takeIf { it.isNotBlank() } ?: return null
            val elementNode = node.getObj("element") ?: return null
            // No assets are available here on purpose: a silent element that needs one could not have
            // been synthesized into ink, so a claim that decodes to one is not a claim about this app's
            // export. The mint is the identity in the block — a claim never invents an id.
            val element = runCatching {
                MobixElementCodec.decode(elementNode, MobixAssetSource { null }, mint = { id -> id })
            }.getOrNull() ?: return null
            if (!element.isLivingEntity) return null
            return MobixMetaClaim(
                page = node.getInt("page", 0),
                layer = node.getInt("layer", 0),
                at = at,
                count = count,
                sha256 = digest,
                element = element,
            )
        }
    }
}

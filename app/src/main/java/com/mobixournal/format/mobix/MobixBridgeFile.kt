package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter

/**
 * The `.mobix` bridge sidecar, `xopp-bridge/metadata.json`: what the document remembers about the
 * `.xopp` it came from (`docs/mobix-format.md`).
 *
 * It exists because a document converted on a phone must still *be* the file its owner had. The
 * `.xopp` root carries a `creator` and a `fileVersion`, and a thumbnail in `<preview>`; none of those
 * is a thing a `.mobix` document models — they describe Xournal++'s file, not this notebook — so
 * without this record a `.xopp` that travelled through the app would come back claiming to have been
 * written by MobiXournal, with no preview. That is a diff in someone's git history or sync client for
 * a file they never edited.
 *
 * Two rules keep it a record rather than a second schema:
 *
 *  - **Every field is optional, and absent means unknown.** A `.mobix` created in this app has no
 *    source at all (no sidecar is written), and a source that carried no preview simply says nothing.
 *  - **Unknown fields are preserved**, like every other envelope object here: the synthesis hashes and
 *    timestamps the export bridge adds (`docs/mobix-format.md`) are written by a different step, and
 *    an older build that reads and rewrites the file must carry them through untouched.
 */
internal data class MobixBridgeFile(
    /** The `.xopp` root's `creator`, verbatim (`xournalpp 1.1.1+dev`, or `MobiXournal`). */
    val creator: String? = null,

    /** The `.xopp` root's `fileversion`, verbatim — a *string*, as the desktop writes it. */
    val fileVersion: String? = null,

    /** The `.xopp`'s `<preview>` thumbnail, base64, verbatim. */
    val preview: String? = null,

    /** What the source file was called, when the caller knew (a SAF URI often does not) — for a
     *  support question ("which export is this?") and for naming a re-export. */
    val source: String? = null,

    /** Fields a later step (or a newer build) added, in source order, re-emitted unchanged. */
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    companion object {
        private val KNOWN_KEYS = setOf("creator", "fileVersion", "preview", "source")

        /**
         * Parse a bridge sidecar.
         *
         * @throws MobixContainer.MalformedMobixException when the text is not a JSON object — a
         *   sidecar that is not an object is a corrupt file, and silently ignoring it would drop the
         *   source's identity on the next write.
         */
        fun decode(text: String): MobixBridgeFile {
            val root = JsonParser.parse(text) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("xopp-bridge metadata is not a JSON object")
            return MobixBridgeFile(
                creator = root.getStrOrNull("creator"),
                fileVersion = root.getStrOrNull("fileVersion"),
                preview = root.getStrOrNull("preview"),
                source = root.getStrOrNull("source"),
                extra = root.map.filterKeys { it !in KNOWN_KEYS },
            )
        }
    }

    /** This record as compact JSON, in the format's own field order, unknown fields last. */
    fun encode(): String = JsonWriter.write(
        JsonNode.Obj(
            LinkedHashMap<String, JsonNode>().also { out ->
                creator?.let { out["creator"] = JsonNode.of(it) }
                fileVersion?.let { out["fileVersion"] = JsonNode.of(it) }
                preview?.let { out["preview"] = JsonNode.of(it) }
                source?.let { out["source"] = JsonNode.of(it) }
                out.putAll(extra)
            },
        ),
    )
}

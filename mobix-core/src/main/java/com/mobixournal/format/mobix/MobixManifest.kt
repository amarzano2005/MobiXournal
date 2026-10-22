package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background

/**
 * The `.mobix` document envelope: `manifest.json`, the one entry every document has and the only one
 * a reader needs to answer "what is this, how big is it, and where are its parts". The schema is
 * owned by `docs/mobix-schema-spec.md`; this file is the code's side of it.
 *
 * Two properties are deliberate:
 *
 *  - **The manifest fixes the page order.** A reader never sorts `pages/` names: a page list can be
 *    reordered, spliced or numbered past four digits, and only the manifest knows what the document
 *    actually says. [parts] therefore *is* the order, and every part names its own entry.
 *  - **Unknown fields survive.** A manifest written by a newer build carries keys this one does not
 *    model (a future `thumbnails`, a per-page `fontHint`); they are kept, with their values, and
 *    re-emitted. See [MobixManifest.extra].
 *
 * Only three fields are required — [formatVersion], [documentId] and [canvasMode] — because those are
 * the ones a reader cannot invent: which schema it is looking at, which document it is (the merge and
 * the sync path key on it), and whether the parts are `pages/` or `chunks/`. Everything else takes a
 * documented default, so a minimal hand-written manifest is still openable.
 */
data class MobixManifest(
    /** The structural schema version this file was written in ([CURRENT_FORMAT_VERSION] today). */
    val formatVersion: Int,

    /**
     * The app version needed to keep this document intact — see [requiresAppNewerThan]. Distinct from
     * [formatVersion]: that says "can I parse the shape of this file?", this says "can I keep every
     * part of it?".
     */
    val minAppVersion: String,

    /** The document's identity: stable across saves, and what a sync merge matches two copies by. */
    val documentId: String,

    /** The spatial geometry mode; decides which of [pages]/[chunks] is the document's content. */
    val canvasMode: CanvasMode,

    /** Unix epoch milliseconds, UTC. */
    val created: Long = 0,

    /** Unix epoch milliseconds, UTC, of the last write. */
    val modified: Long = 0,

    /** The document's title, or null for an untitled one (the app then shows its own default). */
    val title: String? = null,

    /** The page list, in document order, for [CanvasMode.PAGINATED]. */
    val pages: List<MobixPart> = emptyList(),

    /** The occupied tiles, in document order, for the infinite modes. */
    val chunks: List<MobixPart> = emptyList(),

    /**
     * What a *new* page starts on; each page then carries its own ruling. The same shape and the same
     * model type as a page's own background ([MobixPageCodec]), so the two cannot drift.
     */
    val defaultPaper: Background? = null,

    /** Fields this build does not model, in source order, re-emitted unchanged. */
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    companion object {
        /** The schema this build writes. Bumped only for a breaking structural change. */
        const val CURRENT_FORMAT_VERSION: Int = 1

        /** The keys this codec consumes; anything else in a file is kept in [extra]. */
        private val KNOWN_KEYS = setOf(
            "formatVersion", "minAppVersion", "documentId", "canvasMode",
            "created", "modified", "title", "pages", "chunks", "defaultPaper",
        )

        /**
         * Parse a `manifest.json`.
         *
         * @param assets where a bundled background's payload would be resolved from — only needed to
         *   read a `defaultPaper` that carries one; a ruling never does.
         * @throws MobixContainer.MalformedMobixException when the text is not a JSON object, or when a
         *   field this codec cannot invent is missing (`formatVersion`, `documentId`, `canvasMode`) or
         *   unusable (`canvasMode` naming a mode this build does not know).
         */
        fun decode(text: String, assets: MobixAssetSource = MobixAssetSource { null }): MobixManifest {
            val root = JsonParser.parse(text) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("manifest.json is not a JSON object")
            fun required(key: String): JsonNode = root.value(key)
                ?: throw MobixContainer.MalformedMobixException("manifest.json has no $key")
            val formatVersion = (required("formatVersion") as? JsonNode.Num)?.value?.toInt()
                ?: throw MobixContainer.MalformedMobixException("manifest.json: formatVersion is not a number")
            val documentId = (required("documentId") as? JsonNode.Str)?.value
                ?: throw MobixContainer.MalformedMobixException("manifest.json: documentId is not a string")
            val modeName = (required("canvasMode") as? JsonNode.Str)?.value
                ?: throw MobixContainer.MalformedMobixException("manifest.json: canvasMode is not a string")
            val canvasMode = CanvasMode.parse(modeName)
                ?: throw MobixContainer.MalformedMobixException(
                    "manifest.json: canvasMode \"$modeName\" is not one this build knows" +
                        " (${CanvasMode.entries.joinToString(", ") { it.name }})",
                )
            return MobixManifest(
                formatVersion = formatVersion,
                minAppVersion = root.getStr("minAppVersion", "0.0.0"),
                documentId = documentId,
                canvasMode = canvasMode,
                created = root.getDoubleOrNull("created")?.toLong() ?: 0L,
                modified = root.getDoubleOrNull("modified")?.toLong() ?: 0L,
                title = root.getStrOrNull("title"),
                pages = partsOf(root.getArr("pages")),
                chunks = partsOf(root.getArr("chunks")),
                defaultPaper = root.getObj("defaultPaper")?.let { MobixPageCodec.decodeBackground(it, assets) },
                extra = root.map.filterKeys { it !in KNOWN_KEYS },
            )
        }

        private fun partsOf(list: JsonNode.Arr?): List<MobixPart> =
            list?.list.orEmpty().mapNotNull { (it as? JsonNode.Obj)?.let(MobixPart::decode) }

        /** The manifest of a brand-new document: current schema, fresh id, empty page list. */
        fun forNewDocument(
            documentId: String,
            canvasMode: CanvasMode,
            nowMillis: Long,
            title: String? = null,
            defaultPaper: Background? = null,
            appVersion: String = "0.0.0",
        ): MobixManifest = MobixManifest(
            formatVersion = CURRENT_FORMAT_VERSION,
            minAppVersion = appVersion,
            documentId = documentId,
            canvasMode = canvasMode,
            created = nowMillis,
            modified = nowMillis,
            title = title,
            defaultPaper = defaultPaper,
        )

        /**
         * Just the envelope at [source], without decoding a single page — what an incremental save
         * diffs against ([MobixIncrementalSave]). Null when the source carries no readable manifest,
         * which is the caller's cue to write the whole archive instead of repacking over something it
         * cannot read.
         */
        fun read(source: MobixEntrySource): MobixManifest? =
            source.readBytes(MobixFormat.MANIFEST_ENTRY)?.toString(Charsets.UTF_8)?.let { text ->
                runCatching { decode(text, source.asAssetSource()) }.getOrNull()
            }
    }

    /** The parts the [canvasMode] makes authoritative — the other list is empty by construction. */
    val content: List<MobixPart>
        get() = if (canvasMode == CanvasMode.PAGINATED) pages else chunks

    /** Whether this file needs an app *newer* than [appVersion] to be kept intact. */
    fun requiresAppNewerThan(appVersion: String): Boolean =
        compareVersions(minAppVersion, appVersion) > 0

    /**
     * This manifest as compact JSON, in the format's own field order, unknown fields last.
     *
     * Nothing is bundled here: a document's payloads are written by the document layer, which owns the
     * archive. A `defaultPaper` is a ruling — plain, ruled, graph, dotted, isometric — so it has no
     * bytes to carry, and one that named a bundle keeps naming it.
     */
    fun encode(): String = JsonWriter.write(
        JsonNode.Obj(
            LinkedHashMap<String, JsonNode>().also { out ->
                out["formatVersion"] = JsonNode.of(formatVersion)
                out["minAppVersion"] = JsonNode.of(minAppVersion)
                out["documentId"] = JsonNode.of(documentId)
                out["canvasMode"] = JsonNode.of(canvasMode.name)
                out["created"] = JsonNode.of(created)
                out["modified"] = JsonNode.of(modified)
                title?.let { out["title"] = JsonNode.of(it) }
                if (pages.isNotEmpty()) out["pages"] = JsonNode.arr(pages.map { it.encode() })
                if (chunks.isNotEmpty()) out["chunks"] = JsonNode.arr(chunks.map { it.encode() })
                defaultPaper?.let {
                    out["defaultPaper"] = MobixPageCodec.encodeBackground(
                        it,
                        MobixAssetSink(),
                        backgrounds = { null },
                    )
                }
                out.putAll(extra)
            },
        ),
    )
}

/**
 * Compare two dotted version strings numerically: `2.0.0` is newer than `1.3.9`, and a trailing
 * qualifier is ignored (`1.3.0-v2` and `1.3.0` compare equal — the debug channel's suffix is a build
 * stamp, not a version). A missing component counts as zero, so `2.0` equals `2.0.0`.
 */
internal fun compareVersions(a: String, b: String): Int {
    val left = a.split('.')
    val right = b.split('.')
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val r = right.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (l != r) return l - r
    }
    return 0
}

/**
 * One part of a document: a page in `pages/`, a tile in `chunks/`. [id] is the part's stable identity
 * (a merge keys on it, and so does the session cache); [sha256] and [modified] are how a merge tells
 * which side of a conflict moved — see `docs/mobix-format.md`.
 */
data class MobixPart(
    val id: String,
    val entry: String,
    val modified: Long = 0,
    val sha256: String = "",
    /** Fields this build does not model, in source order, re-emitted unchanged. */
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    companion object {
        private val KNOWN_KEYS = setOf("id", "entry", "modified", "sha256")

        /** Decode one `pages[]`/`chunks[]` element; null when it carries no usable entry name. */
        fun decode(node: JsonNode.Obj): MobixPart? {
            val entry = node.getStrOrNull("entry") ?: return null
            return MobixPart(
                id = node.getStr("id", entry),
                entry = entry,
                modified = node.getDoubleOrNull("modified")?.toLong() ?: 0L,
                sha256 = node.getStr("sha256", ""),
                extra = node.map.filterKeys { it !in KNOWN_KEYS },
            )
        }
    }

    fun encode(): JsonNode = JsonNode.Obj(
        LinkedHashMap<String, JsonNode>().also { out ->
            out["id"] = JsonNode.of(id)
            out["entry"] = JsonNode.of(entry)
            out["modified"] = JsonNode.of(modified)
            out["sha256"] = JsonNode.of(sha256)
            out.putAll(extra)
        },
    )
}

/**
 * The spatial geometry mode of a document (`docs/infinite-canvas.md` owns what each one means).
 *
 * **Public** on purpose, unlike the rest of this layer: the app's document I/O reports the mode a
 * `.mobix` was opened with (`LoadedFile.Doc.canvasMode`) so the editor and the next save write the same
 * shape back, and a mode is the one stable name for that — a container-internal spelling would have to
 * be translated at every seam. A `.xopp` has no notion of it, which is why it rides on the loaded file
 * and not on the document model (`format/model/Document.kt`).
 */
enum class CanvasMode {
    /** Fixed pages, the `.xopp`-compatible mode: content lives in `pages/`. */
    PAGINATED,

    /** Width-locked, extends downwards: content lives in `chunks/`. */
    INFINITE_VERTICAL,

    /** Height-locked, extends rightwards: content lives in `chunks/`. */
    INFINITE_HORIZONTAL,

    /** Unconstrained board: content lives in `chunks/`. */
    INFINITE_2D,
    ;

    companion object {
        /** The mode named by [name], or null when it is not a mode this build knows. */
        fun parse(name: String?): CanvasMode? = entries.firstOrNull { it.name == name }
    }
}

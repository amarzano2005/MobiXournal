package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter

/**
 * The `.mobix` `canvas.json`: how the document's coordinate space is laid out.
 *
 * The manifest already says *which* mode the document is in — a reader needs that before it knows
 * whether to look in `pages/` or `chunks/` — and this file says what the mode's numbers are. The two
 * are separate because they are read at different moments: the manifest on every open, the geometry
 * only when a canvas is actually laid out (`docs/infinite-canvas.md` owns the modes themselves).
 *
 * The one decision worth stating: **`chunkSizePt` is written, never assumed.** The chunk grid a
 * document's tile names were computed against is part of that document, so a future change to the
 * default must not silently re-interpret files written before it — the grid of an old file stays the
 * grid it was written with.
 */
internal data class MobixCanvasFile(
    /** The mode this geometry describes; must agree with the manifest's `canvasMode`. */
    val mode: CanvasMode,

    /** The **locked** dimension in the two strip modes, and the page size in [CanvasMode.PAGINATED]. */
    val widthPt: Double,

    /** The other locked dimension, or the page height in [CanvasMode.PAGINATED]. */
    val heightPt: Double,

    /** The spatial tile size this document's `chunk_<cx>_<cy>` names were computed against, in pt. */
    val chunkSizePt: Double = DEFAULT_CHUNK_SIZE_PT,

    /** Where document `(0, 0)` sits on the printed sheet — the auto-slicer's anchor. */
    val origin: Origin = Origin(),

    /** Fields this build does not model, in source order, re-emitted unchanged. */
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    companion object {
        /**
         * The default tile size: 1024 pt, about 14.2 inches at 72 dpi — a tile that holds a screenful
         * of ink without turning a stroke into an archive entry of its own.
         */
        const val DEFAULT_CHUNK_SIZE_PT: Double = 1024.0

        private val KNOWN_KEYS = setOf("mode", "widthPt", "heightPt", "chunkSizePt", "origin")

        /**
         * Parse a `canvas.json`.
         *
         * @throws MobixContainer.MalformedMobixException when the text is not a JSON object or does
         *   not carry a `mode` this build knows — the one thing here a reader cannot invent.
         */
        fun decode(text: String): MobixCanvasFile {
            val root = JsonParser.parse(text) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("canvas.json is not a JSON object")
            val modeName = root.getStrOrNull("mode")
                ?: throw MobixContainer.MalformedMobixException("canvas.json has no mode")
            val mode = CanvasMode.parse(modeName)
                ?: throw MobixContainer.MalformedMobixException(
                    "canvas.json: mode \"$modeName\" is not one this build knows",
                )
            return MobixCanvasFile(
                mode = mode,
                widthPt = root.getDouble("widthPt", 0.0),
                heightPt = root.getDouble("heightPt", 0.0),
                chunkSizePt = root.getDouble("chunkSizePt", DEFAULT_CHUNK_SIZE_PT)
                    .takeIf { it > 0.0 } ?: DEFAULT_CHUNK_SIZE_PT,
                origin = root.getObj("origin")?.let(Origin::decode) ?: Origin(),
                extra = root.map.filterKeys { it !in KNOWN_KEYS },
            )
        }
    }

    /** This geometry as compact JSON, in the format's own field order, unknown fields last. */
    fun encode(): String = JsonWriter.write(
        JsonNode.Obj(
            LinkedHashMap<String, JsonNode>().also { out ->
                out["mode"] = JsonNode.of(mode.name)
                out["widthPt"] = JsonNode.of(widthPt)
                out["heightPt"] = JsonNode.of(heightPt)
                out["chunkSizePt"] = JsonNode.of(chunkSizePt)
                out["origin"] = origin.encode()
                out.putAll(extra)
            },
        ),
    )
}

/** Where document coordinates `(0, 0)` sit on the printed sheet. */
internal data class Origin(
    /** The 1-based page an exported sheet starts at when [offsetPt] is applied. */
    val pageNo: Int = 1,

    /** How far into that page, in pt, the document's origin sits. */
    val offsetPt: Double = 0.0,

    /** Fields this build does not model, in source order, re-emitted unchanged. */
    val extra: Map<String, JsonNode> = emptyMap(),
) {

    companion object {
        private val KNOWN_KEYS = setOf("pageNo", "offsetPt")

        fun decode(node: JsonNode.Obj): Origin = Origin(
            pageNo = node.getInt("pageNo", 1),
            offsetPt = node.getDouble("offsetPt", 0.0),
            extra = node.map.filterKeys { it !in KNOWN_KEYS },
        )
    }

    fun encode(): JsonNode = JsonNode.Obj(
        LinkedHashMap<String, JsonNode>().also { out ->
            out["pageNo"] = JsonNode.of(pageNo)
            out["offsetPt"] = JsonNode.of(offsetPt)
            out.putAll(extra)
        },
    )
}

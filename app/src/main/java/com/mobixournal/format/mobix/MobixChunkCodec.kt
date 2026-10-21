package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * A spatial tile in an infinite canvas (`chunks/chunk_<cx>_<cy>.json`).
 *
 * @param cx The horizontal chunk grid index (x / chunkSizePt).
 * @param cy The vertical chunk grid index (y / chunkSizePt).
 * @param layers The drawable layers of elements in this tile.
 * @param background Optional chunk-specific background. If null, the canvas's default paper applies.
 * @param widthPt Chunk width in points (default 1024.0 pt).
 * @param heightPt Chunk height in points (default 1024.0 pt).
 * @param extraAttrs Attributes kept in source order so unmodeled attributes round-trip.
 * @param id Stable chunk identity (e.g. "chk_...").
 */
internal data class MobixChunk(
    val cx: Int,
    val cy: Int,
    val layers: List<Layer> = emptyList(),
    val background: Background? = null,
    val widthPt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
    val heightPt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
    val extraAttrs: Map<String, String> = emptyMap(),
    val id: String? = null,
) {
    /** Convert this chunk tile to a standard [Page] representation. */
    fun toPage(): Page = Page(
        width = widthPt,
        height = heightPt,
        background = background ?: Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = layers,
        extraAttrs = extraAttrs,
        id = id,
    )

    companion object {
        /** Create a chunk from a [Page] positioned at ([cx], [cy]). */
        fun fromPage(page: Page, cx: Int, cy: Int): MobixChunk = MobixChunk(
            cx = cx,
            cy = cy,
            layers = page.layers,
            background = page.background,
            widthPt = page.width,
            heightPt = page.height,
            extraAttrs = page.extraAttrs,
            id = page.id,
        )
    }
}

/**
 * The `.mobix` chunk codec: `chunks/chunk_<cx>_<cy>.json` serialization and deserialization.
 */
internal object MobixChunkCodec {

    private const val ATTRS = "attrs"

    /** [chunk] as JSON, registering bundled payloads in [assets] — backgrounds and stroke recordings. */
    fun encode(
        chunk: MobixChunk,
        assets: MobixAssetSink,
        backgrounds: (Background) -> MobixPayload? = { null },
        audio: (String) -> MobixPayload? = { null },
    ): JsonNode.Obj = MobixJson.obj { out ->
        out["cx"] = JsonNode.of(chunk.cx)
        out["cy"] = JsonNode.of(chunk.cy)
        out["widthPt"] = JsonNode.of(chunk.widthPt)
        out["heightPt"] = JsonNode.of(chunk.heightPt)
        chunk.background?.let {
            out["background"] = MobixPageCodec.encodeBackground(it, assets, backgrounds)
        }
        out["layers"] = JsonNode.arr(chunk.layers.map { MobixPageCodec.encodeLayer(it, assets, audio) })
        if (chunk.extraAttrs.isNotEmpty()) out[ATTRS] = MobixJson.attrsNode(chunk.extraAttrs)
    }

    /** The chunk [node] describes. */
    fun decode(
        node: JsonNode.Obj,
        assets: MobixAssetSource,
        fallbackCx: Int = 0,
        fallbackCy: Int = 0,
    ): MobixChunk {
        val cx = node.getDoubleOrNull("cx")?.toInt() ?: fallbackCx
        val cy = node.getDoubleOrNull("cy")?.toInt() ?: fallbackCy
        val widthPt = node.getDouble("widthPt", MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT)
        val heightPt = node.getDouble("heightPt", MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT)
        val background = node.getObj("background")?.let { MobixPageCodec.decodeBackground(it, assets) }
        val layers = node.getArr("layers")?.list.orEmpty()
            .mapNotNull { (it as? JsonNode.Obj)?.let { l -> MobixPageCodec.decodeLayer(l, assets) } }
        val extraAttrs = MobixJson.readAttrs(node)
        return MobixChunk(
            cx = cx,
            cy = cy,
            layers = layers,
            background = background,
            widthPt = widthPt,
            heightPt = heightPt,
            extraAttrs = extraAttrs,
        )
    }
}

package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page

/**
 * The **chunk half** of a `.mobix`: building the tiles an infinite canvas writes, reading them back, and
 * the geometry a chunk's name is computed against (`docs/infinite-canvas.md`).
 *
 * It is split out of [MobixDocument] because a chunk is a *part* like a page — same manifest entry, same
 * codec shape, same reuse and content addressing — while the coordinate arithmetic, the lazy tile list and
 * the two read paths are the board's own and nothing a paginated document touches. The page half stays
 * there (`MobixPageParts.kt` owns the `pages[]`/`chunks[]` entry arithmetic both halves share).
 */

/**
 * The chunks of a document built from a page list, **one at a time**.
 *
 * A chunk is a page's own content with the coordinate it sits at, so building one means reading that
 * page — and a save that copies a chunk out of the archive it was opened from must not build it
 * ([MobixPageReuse]). A plain `List<MobixChunk>` built up front would read every page of the board on
 * every save, which is exactly the cost the incremental save exists to avoid; this builds the ones a
 * write actually encodes and leaves the rest unread. `toString` says how many there are rather than
 * printing them, the same reason `LazyPages` does.
 *
 * A chunk the writer never asks for is a chunk never built, so a `chunks` list of this kind is meant to
 * be **indexed**, not iterated: an iteration builds every chunk of the board.
 */
internal class LazyChunks(
    private val pages: List<Page>,
    private val grid: List<Pair<Int, Int>>?,
    private val canvasMode: CanvasMode,
) : AbstractList<MobixChunk>() {

    override val size: Int get() = pages.size

    override fun get(index: Int): MobixChunk {
        val (cx, cy) = grid?.get(index) ?: stripCoords(index, canvasMode)
        return MobixChunk.fromPage(pages[index], cx = cx, cy = cy)
    }

    override fun toString(): String = "LazyChunks($size chunks)"
}

/**
 * Where page [index] sits when nothing says otherwise: a strip, one part per index, in the direction the
 * mode runs — down for a vertical canvas, right for a horizontal one. `INFINITE_2D` has no implied
 * direction at all, so it gets the vertical strip: a board whose chunks are genuinely scattered has
 * coordinates in the file, and a caller that knows them passes `chunkCoords` rather than letting this
 * guess.
 */
fun stripCoords(index: Int, canvasMode: CanvasMode): Pair<Int, Int> = when (canvasMode) {
    CanvasMode.INFINITE_HORIZONTAL -> index to 0
    else -> 0 to index
}

/**
 * The chunks a manifest lists, in its own order — empty for a paginated document, which carries pages
 * instead. Both read paths ask this through [chunkIn], so the *rule* that a file may not hold both lives
 * in one place and the reader only says where the bytes come from.
 */
internal fun chunksOf(manifest: MobixManifest, read: (MobixPart) -> MobixChunk): List<MobixChunk> =
    if (manifest.canvasMode == CanvasMode.PAGINATED) emptyList() else manifest.chunks.map(read)

/**
 * Each chunk paired with the `chunks[]` entry describing it, in document order — the chunk half of
 * `MobixDocument`'s page writer, and it reuses the same way.
 *
 * A chunk is a part like any other, so a chunk the source archive still holds contributes **its part and
 * no entry**: its JSON is neither rebuilt nor hashed here, and the repack copies the bytes out of the
 * file. What makes that safe is the coordinates travelling with the plan
 * ([MobixPageReuse.of][MobixPageReuse.Companion.of] is only handed a grid that lines up), because a
 * chunk is named after where it sits rather than after its index.
 */
internal fun encodeChunks(
    manifest: MobixManifest,
    chunks: List<MobixChunk>,
    sink: MobixAssetSink,
    backgrounds: (Background) -> MobixPayload?,
    audio: (String) -> MobixPayload?,
    nowMillis: Long,
    reuse: MobixPageReuse,
): List<BuiltPart> = (0 until chunks.size).map { index ->
    // Indexed, never iterated: a chunk the source still holds must not even be *built*, and building
    // one means reading its page — the very cost a copy avoids (`LazyChunks`).
    reuse.reused[index]?.let { part -> return@map BuiltPart(part, entry = null) }
    val chunk = chunks[index]
    val json = JsonWriter.write(MobixChunkCodec.encode(chunk, sink, backgrounds, audio))
        .toByteArray(Charsets.UTF_8)
    val name = MobixFormat.chunkEntry(chunk.cx, chunk.cy)
    BuiltPart(
        part = chunkPartFor(manifest, chunk, name, MobixAssetSink.sha256(json), nowMillis),
        entry = MobixContainer.Entry(name, json),
    )
}

/** The chunk the manifest part [part] names, read on demand from a streaming [source]. */
internal fun chunkIn(
    source: MobixEntrySource,
    part: MobixPart,
    assets: MobixAssetSource,
): MobixChunk {
    val bytes = source.readBytes(part.entry)
        ?: throw MobixContainer.MalformedMobixException("chunk entry ${part.entry} is missing from the archive")
    return decodeChunk(part, bytes, assets)
}

/** The chunk the manifest part [part] names. A part with no entry in the archive is corrupt. */
internal fun chunkIn(
    archive: MobixContainer.Archive,
    part: MobixPart,
    source: MobixAssetSource,
): MobixChunk = decodeChunk(part, archive.require(part.entry).bytes, source)

/**
 * A chunk entry's JSON as the chunk it describes, taking its coordinates from the name it is filed
 * under — `chunks/chunk_<cx>_<cy>.json` is where a board's geometry is recorded, so a body that omits
 * them is read at the tile its name says rather than at the origin.
 */
private fun decodeChunk(part: MobixPart, bytes: ByteArray, assets: MobixAssetSource): MobixChunk {
    val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
        ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")
    val coords = MobixFormat.parseChunkEntry(part.entry) ?: (0 to 0)
    return MobixChunkCodec.decode(node, assets, fallbackCx = coords.first, fallbackCy = coords.second)
        .copy(id = part.id)
}

package com.mobixournal.render

import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk
import kotlin.math.floor

/**
 * Manages spatial chunks for infinite canvas modes with LRU caching,
 * viewport queries, and 1-chunk margin offscreen eviction.
 *
 * @param chunkSizePt The grid tile size in points (default 1024.0 pt).
 * @param initialChunks The initial collection of chunks loaded into memory.
 * @param cacheMargin The buffer margin of chunks kept outside visible viewport before eviction (default 1).
 * @param onEvict Optional callback invoked when an offscreen chunk is evicted from memory.
 */
internal class ChunkTileManager(
    val chunkSizePt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
    initialChunks: List<MobixChunk> = emptyList(),
    val cacheMargin: Int = 1,
    private val onEvict: ((MobixChunk) -> Unit)? = null,
) {
    private val loadedChunks = LinkedHashMap<Pair<Int, Int>, MobixChunk>()
    private val dirtyTiles = HashSet<Pair<Int, Int>>()

    init {
        for (chunk in initialChunks) {
            loadedChunks[chunk.cx to chunk.cy] = chunk
        }
    }

    /** Retrieve chunk at `(cx, cy)` if currently materialized in memory. */
    @Synchronized
    fun getChunk(cx: Int, cy: Int): MobixChunk? = loadedChunks[cx to cy]

    /** Put or update chunk at `(cx, cy)` in memory and mark dirty. */
    @Synchronized
    fun putChunk(chunk: MobixChunk) {
        val coord = chunk.cx to chunk.cy
        loadedChunks[coord] = chunk
        dirtyTiles.add(coord)
    }

    /**
     * Remember [chunk] as materialised **without** marking it dirty: it was read back from the
     * document ([BoardInk.chunkAt]), and a read is not an edit. [putChunk] is the write half, and the
     * one that says a tile moved.
     */
    @Synchronized
    fun putLoaded(chunk: MobixChunk) {
        loadedChunks[chunk.cx to chunk.cy] = chunk
    }

    /** Retrieve existing chunk or create a new empty one if not present. */
    @Synchronized
    fun getOrCreateChunk(
        cx: Int,
        cy: Int,
        factory: (Int, Int) -> MobixChunk = { x, y -> MobixChunk(cx = x, cy = y, widthPt = chunkSizePt, heightPt = chunkSizePt) },
    ): MobixChunk = loadedChunks.getOrPut(cx to cy) {
        dirtyTiles.add(cx to cy)
        factory(cx, cy)
    }

    /** All currently materialized chunks in memory. */
    @Synchronized
    fun activeChunks(): List<MobixChunk> = loadedChunks.values.toList()

    /** All currently materialized chunk coordinates in memory. */
    @Synchronized
    fun activeCoords(): Set<Pair<Int, Int>> = loadedChunks.keys.toSet()

    /** Return all loaded chunks intersecting the [visibleRange]. */
    @Synchronized
    fun visibleChunks(visibleRange: ChunkRange): List<MobixChunk> =
        loadedChunks.values.filter { visibleRange.contains(it.cx, it.cy) }

    /**
     * Query which loaded chunks intersect the given bounding box in point coordinates.
     */
    @Synchronized
    fun queryChunksIntersecting(minX: Double, minY: Double, maxX: Double, maxY: Double): List<MobixChunk> {
        val cxMin = floor(minX / chunkSizePt).toInt()
        val cxMax = floor(maxX / chunkSizePt).toInt()
        val cyMin = floor(minY / chunkSizePt).toInt()
        val cyMax = floor(maxY / chunkSizePt).toInt()
        val queryRange = ChunkRange(cxMin, cxMax, cyMin, cyMax)
        return visibleChunks(queryRange)
    }

    /**
     * Evict offscreen chunks that fall outside `visibleRange.withMargin(cacheMargin)`.
     * Returns the list of evicted coordinates.
     */
    @Synchronized
    fun evictOffscreen(visibleRange: ChunkRange): List<Pair<Int, Int>> {
        val keepRange = visibleRange.withMargin(cacheMargin)
        val toEvict = loadedChunks.keys.filter { coord -> !keepRange.contains(coord.first, coord.second) }
        for (coord in toEvict) {
            val chunk = loadedChunks.remove(coord)
            if (chunk != null) {
                onEvict?.invoke(chunk)
            }
        }
        return toEvict
    }

    /** Mark tile at `(cx, cy)` as modified. */
    @Synchronized
    fun markDirty(cx: Int, cy: Int) {
        dirtyTiles.add(cx to cy)
    }

    /** Check if tile at `(cx, cy)` has uncommitted edits. */
    @Synchronized
    fun isDirty(cx: Int, cy: Int): Boolean = dirtyTiles.contains(cx to cy)

    /** Set of coordinates with uncommitted edits. */
    @Synchronized
    fun dirtyCoords(): Set<Pair<Int, Int>> = dirtyTiles.toSet()

    /** Clear dirty flags (e.g. after save). */
    @Synchronized
    fun clearDirty() {
        dirtyTiles.clear()
    }
}

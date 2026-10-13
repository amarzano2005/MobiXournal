package com.mobixournal.render

import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.model.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChunkTileManager] (spatial querying, LRU caching, and 1-chunk margin offscreen eviction).
 */
class ChunkTileManagerTest {

    @Test
    fun getAndPutAndCreateChunks() {
        val manager = ChunkTileManager(chunkSizePt = 1024.0)
        assertNull(manager.getChunk(0, 0))

        val chunk00 = MobixChunk(cx = 0, cy = 0, layers = listOf(Layer(elements = emptyList(), name = "L1")))
        manager.putChunk(chunk00)
        assertEquals(chunk00, manager.getChunk(0, 0))
        assertTrue(manager.isDirty(0, 0))

        val created = manager.getOrCreateChunk(1, 2)
        assertEquals(1, created.cx)
        assertEquals(2, created.cy)
        assertEquals(1024.0, created.widthPt, 0.001)
        assertTrue(manager.isDirty(1, 2))
        assertEquals(created, manager.getChunk(1, 2))
    }

    @Test
    fun queryChunksIntersectingBoundingBox() {
        val manager = ChunkTileManager(chunkSizePt = 1024.0)
        val c00 = MobixChunk(cx = 0, cy = 0)
        val c10 = MobixChunk(cx = 1, cy = 0)
        val c01 = MobixChunk(cx = 0, cy = 1)
        val c11 = MobixChunk(cx = 1, cy = 1)
        val cNeg = MobixChunk(cx = -1, cy = -1)

        listOf(c00, c10, c01, c11, cNeg).forEach { manager.putChunk(it) }

        // Query box spanning [500..1500, 200..300]: intersects cx in [0, 1] and cy in [0, 0]
        val results = manager.queryChunksIntersecting(500.0, 200.0, 1500.0, 300.0)
        assertEquals(2, results.size)
        assertTrue(results.any { it.cx == 0 && it.cy == 0 })
        assertTrue(results.any { it.cx == 1 && it.cy == 0 })

        // Query box in negative coords [-1200..-500, -1200..-500]: intersects cx in [-2, -1], cy in [-2, -1]
        val negResults = manager.queryChunksIntersecting(-1200.0, -1200.0, -500.0, -500.0)
        assertEquals(1, negResults.size)
        assertEquals(-1, negResults[0].cx)
        assertEquals(-1, negResults[0].cy)
    }

    @Test
    fun evictOffscreenDropsChunksOutsideVisiblePlusMargin() {
        val evictedChunks = mutableListOf<MobixChunk>()
        val manager = ChunkTileManager(
            chunkSizePt = 1024.0,
            cacheMargin = 1,
            onEvict = { evictedChunks.add(it) },
        )

        // Populate chunks from (-3..3, -3..3)
        for (x in -3..3) {
            for (y in -3..3) {
                manager.putChunk(MobixChunk(cx = x, cy = y))
            }
        }
        assertEquals(49, manager.activeChunks().size)

        // Viewport is currently looking at tile (0, 0)
        // With 1-chunk margin, tiles within [-1..1, -1..1] (9 tiles) are retained
        // All others (40 tiles) should be evicted
        val visibleRange = ChunkRange(cxMin = 0, cxMax = 0, cyMin = 0, cyMax = 0)
        val evictedCoords = manager.evictOffscreen(visibleRange)

        assertEquals(40, evictedCoords.size)
        assertEquals(40, evictedChunks.size)
        assertEquals(9, manager.activeChunks().size)

        // Verify remaining tiles are strictly inside [-1..1, -1..1]
        for (coord in manager.activeCoords()) {
            assertTrue(coord.first in -1..1)
            assertTrue(coord.second in -1..1)
        }

        // Verify tiles outside margin are evicted
        assertNull(manager.getChunk(2, 0))
        assertNull(manager.getChunk(-2, 0))
        assertNull(manager.getChunk(0, 3))
        assertNotNull(manager.getChunk(0, 0))
        assertNotNull(manager.getChunk(1, 1))
        assertNotNull(manager.getChunk(-1, -1))
    }

    @Test
    fun dirtyTrackingLifecycle() {
        val manager = ChunkTileManager(chunkSizePt = 1024.0)
        manager.putChunk(MobixChunk(cx = 5, cy = 6))
        assertTrue(manager.isDirty(5, 6))
        assertFalse(manager.isDirty(1, 1))

        assertEquals(setOf(5 to 6), manager.dirtyCoords())
        manager.clearDirty()
        assertFalse(manager.isDirty(5, 6))
        assertTrue(manager.dirtyCoords().isEmpty())
    }
}

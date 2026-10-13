package com.mobixournal.render

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk

/**
 * Infinite canvas extensions for [DrawingSurfaceView].
 */
internal fun DrawingSurfaceView.setInfiniteCanvas(
    mode: CanvasMode,
    chunkSizePt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
    chunks: List<MobixChunk> = emptyList(),
) {
    viewport.canvasMode = mode
    chunkTileManager = if (mode != CanvasMode.PAGINATED) {
        ChunkTileManager(
            chunkSizePt = chunkSizePt,
            initialChunks = chunks,
        )
    } else {
        null
    }
    requestRender()
}

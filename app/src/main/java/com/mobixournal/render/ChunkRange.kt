package com.mobixournal.render

/**
 * A rectangular range of spatial chunk tile grid coordinates `[cxMin..cxMax, cyMin..cyMax]`.
 */
data class ChunkRange(
    val cxMin: Int,
    val cxMax: Int,
    val cyMin: Int,
    val cyMax: Int,
) {
    /** Expand this range by [margin] chunks on all sides. */
    fun withMargin(margin: Int = 1): ChunkRange = ChunkRange(
        cxMin = cxMin - margin,
        cxMax = cxMax + margin,
        cyMin = cyMin - margin,
        cyMax = cyMax + margin,
    )

    /** Whether the chunk coordinate `(cx, cy)` falls within this range. */
    fun contains(cx: Int, cy: Int): Boolean =
        cx in cxMin..cxMax && cy in cyMin..cyMax

    /** Sequence of all `(cx, cy)` coordinate pairs in this range. */
    fun allCoords(): List<Pair<Int, Int>> {
        val list = ArrayList<Pair<Int, Int>>((cxMax - cxMin + 1) * (cyMax - cyMin + 1))
        for (cx in cxMin..cxMax) {
            for (cy in cyMin..cyMax) {
                list.add(cx to cy)
            }
        }
        return list
    }
}

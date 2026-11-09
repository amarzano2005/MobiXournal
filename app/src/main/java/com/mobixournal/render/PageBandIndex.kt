package com.mobixournal.render

/** Immutable connected vertical bands: binary search skips all rows outside a viewport or pointer. */
internal class PageBandIndex(private val boxes: List<PageBox>) {
    private data class Placed(val order: Int, val box: PageBox)
    private data class Band(val top: Float, var bottom: Float, val items: MutableList<Placed>)
    private val valid = boxes.all { it.topPx.isFinite() && it.bottomPx.isFinite() && it.bottomPx >= it.topPx }
    private val bands = buildBands()
    internal var examinedBoxes = 0
        private set

    private fun buildBands(): List<Band> {
        if (!valid) return emptyList()
        val result = ArrayList<Band>()
        for (item in boxes.mapIndexed(::Placed).sortedBy { it.box.topPx }) {
            val last = result.lastOrNull()
            if (last != null && (item.box.topPx < last.bottom || item.box.topPx == last.top)) {
                last.bottom = maxOf(last.bottom, item.box.bottomPx)
                last.items += item
            } else result += Band(item.box.topPx, item.box.bottomPx, mutableListOf(item))
        }
        result.forEach { it.items.sortBy(Placed::order) }
        return result
    }

    fun at(y: Float): List<PageBox> {
        examinedBoxes = 0
        if (!valid || !y.isFinite()) return boxes.filter { examinedBoxes++; y >= it.topPx && y < it.bottomPx }
        val index = firstEndingAfter(y)
        val band = bands.getOrNull(index) ?: return emptyList()
        if (band.top > y) return emptyList()
        return band.items.mapNotNull {
            examinedBoxes++
            it.box.takeIf { box -> y >= box.topPx && y < box.bottomPx }
        }
    }

    fun visible(y: Float, height: Float): List<PageBox> {
        examinedBoxes = 0
        val end = y + height
        if (!valid || !y.isFinite() || !end.isFinite() || height < 0) {
            return boxes.filter { examinedBoxes++; it.bottomPx > y && it.topPx < end }
        }
        val found = ArrayList<Placed>()
        var index = firstEndingAfter(y)
        while (index < bands.size && bands[index].top < end) {
            for (item in bands[index].items) {
                examinedBoxes++
                if (item.box.bottomPx > y && item.box.topPx < end) found += item
            }
            index++
        }
        return found.sortedBy(Placed::order).map(Placed::box)
    }

    private fun firstEndingAfter(y: Float): Int {
        var low = 0; var high = bands.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (bands[middle].bottom <= y) low = middle + 1 else high = middle
        }
        return low
    }
}

package com.mobixournal.render

/** Resolves a flattened drop to a sibling move, carrying descendants without reparenting them. */
class OutlineDragOrder(rows: List<OutlineRow>) {
    private val positions = rows.withIndex().associate { it.value.id to it.index }
    private val siblings = HashMap<String, List<String>>()

    init {
        val ancestors = ArrayList<OutlineRow>()
        val groups = LinkedHashMap<String?, MutableList<String>>()
        for (row in rows) {
            while (ancestors.isNotEmpty() && ancestors.last().depth >= row.depth) ancestors.removeAt(ancestors.lastIndex)
            groups.getOrPut(ancestors.lastOrNull()?.id) { ArrayList() }.add(row.id)
            ancestors.add(row)
        }
        for (group in groups.values) for (id in group) siblings[id] = group
    }

    data class Move(val id: String, val targetId: String, val delta: Int)

    fun move(from: String, over: String): Move? {
        val group = siblings[from] ?: return null
        val position = positions[over] ?: return null
        val target = group.indexOfLast { positions.getValue(it) <= position }.coerceAtLeast(0)
        val delta = target - group.indexOf(from)
        return if (delta == 0) null else Move(from, group[target], delta)
    }
}

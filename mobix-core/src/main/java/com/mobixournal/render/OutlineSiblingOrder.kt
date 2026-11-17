package com.mobixournal.render

/** Available sibling moves for resolved rows, computed once per tree rather than for every visible row. */
object OutlineSiblingOrder {
    data class Moves(val up: Boolean = false, val down: Boolean = false)

    fun of(rows: List<OutlineRow>): Map<String, Moves> {
        val result = HashMap<String, Moves>(rows.size)
        val ancestors = ArrayList<OutlineRow>()
        val lastChild = HashMap<String?, String>()
        for (row in rows) {
            while (ancestors.isNotEmpty() && ancestors.last().depth >= row.depth) ancestors.removeAt(ancestors.lastIndex)
            val parent = ancestors.lastOrNull()?.id
            val previous = lastChild.put(parent, row.id)
            result[row.id] = Moves(up = previous != null)
            if (previous != null) result[previous] = result.getValue(previous).copy(down = true)
            ancestors += row
        }
        return result
    }
}

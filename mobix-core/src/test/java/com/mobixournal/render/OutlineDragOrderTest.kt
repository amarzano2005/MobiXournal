package com.mobixournal.render

import org.junit.Assert.*
import org.junit.Test

class OutlineDragOrderTest {
    private val rows = listOf(
        OutlineRow("a", "Chapter", 0, 0),
        OutlineRow("a1", "Section", 1, 0),
        OutlineRow("a11", "Detail", 2, 0),
        OutlineRow("a2", "Section", 1, 1),
        OutlineRow("b", "Chapter", 0, 2),
        OutlineRow("b1", "Section", 1, 2),
        OutlineRow("c", "Chapter", 0, 3),
    )
    private val order = OutlineDragOrder(rows)

    @Test fun draggingOverDescendantsUsesSiblingOrdinals() {
        assertEquals(OutlineDragOrder.Move("a", "b", 1), order.move("a", "b1"))
        assertEquals(OutlineDragOrder.Move("c", "a", -2), order.move("c", "a11"))
        assertNull(order.move("a", "a11"))
    }

    @Test fun draggingOutsideAParentClampsWithoutReparenting() {
        assertEquals(OutlineDragOrder.Move("a1", "a2", 1), order.move("a1", "c"))
        assertEquals(OutlineDragOrder.Move("a2", "a1", -1), order.move("a2", "a"))
        assertNull(order.move("b1", "a1"))
        assertNull(order.move("missing", "a"))
    }
}

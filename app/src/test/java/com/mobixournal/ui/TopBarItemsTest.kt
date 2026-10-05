package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopBarItemsTest {

    @Test
    fun `top bar item ids are unique`() {
        val ids = TOP_BAR_ITEMS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `empty order is factory default order`() {
        val ordered = orderedTopBarItems(emptyList()).map { it.id }
        assertEquals(DEFAULT_TOP_BAR_ORDER, ordered)
    }

    @Test
    fun `single tools resolve to their respective EditorTool`() {
        assertEquals(EditorTool.LINE, singleToolForTopBarId("line"))
        assertEquals(EditorTool.RECTANGLE, singleToolForTopBarId("rectangle"))
        assertEquals(EditorTool.ELLIPSE, singleToolForTopBarId("ellipse"))
        assertEquals(EditorTool.TRIANGLE, singleToolForTopBarId("triangle"))
        assertEquals(EditorTool.SQUARE, singleToolForTopBarId("square"))
        assertEquals(EditorTool.RHOMBUS, singleToolForTopBarId("rhombus"))
        assertEquals(EditorTool.PENTAGON, singleToolForTopBarId("pentagon"))
        assertEquals(EditorTool.HEXAGON, singleToolForTopBarId("hexagon"))
        assertEquals(EditorTool.COORDINATE_AXIS, singleToolForTopBarId("axis"))
        assertEquals(EditorTool.SPLINE, singleToolForTopBarId("spline"))
        assertEquals(EditorTool.TABLE, singleToolForTopBarId("table"))
        // Multi-tool groups and popups do not resolve to a single tool
        assertNull(singleToolForTopBarId("arrow"))
        assertNull(singleToolForTopBarId("circuit"))
        assertNull(singleToolForTopBarId("logic"))
        assertNull(singleToolForTopBarId("guides"))
        assertNull(singleToolForTopBarId("color"))
    }

    @Test
    fun `hidden items are dropped from visible items`() {
        val visible = visibleTopBarItems(emptyList(), setOf("hexagon", "axis"))
        assertTrue(visible.none { it.id == "hexagon" || it.id == "axis" })
        assertEquals(TOP_BAR_ITEMS.size - 2, visible.size)
    }

    @Test
    fun `moveTopBarItem swaps item with neighbour`() {
        val moved = moveTopBarItem(emptyList(), 0, 1)
        val factory = orderedTopBarItems(emptyList()).map { it.id }
        assertEquals(listOf(factory[1], factory[0]), moved.take(2))
    }

    @Test
    fun `ids round-trip through encode and decode`() {
        val ids = listOf("line", "rectangle", "arrow")
        assertEquals(ids, decodeTopBarIds(encodeTopBarIds(ids)))
        assertEquals(emptyList<String>(), decodeTopBarIds(null))
        assertEquals(listOf("line"), decodeTopBarIds("line,unknown"))
    }
}

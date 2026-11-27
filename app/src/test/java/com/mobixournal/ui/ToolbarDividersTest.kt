package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarDividersTest {

    @Test
    fun `nextDividerId picks smallest unused div_N`() {
        assertEquals("div_1", nextDividerId(emptyList()))
        assertEquals("div_2", nextDividerId(listOf("pen", "div_1", "div_3")))
    }

    @Test
    fun `cleanVisibleDividers strips leading trailing and consecutive dividers`() {
        val raw = listOf(
            dividerRailItem("div_1"),
            RailItem("line", "Line"),
            dividerRailItem("div_2"),
            dividerRailItem("div_3"),
            RailItem("axis", "Axes"),
            dividerRailItem("div_4"),
        )
        val cleaned = cleanVisibleDividers(raw)
        assertEquals(listOf("line", "div_2", "axis"), cleaned.map { it.id })
    }

    @Test
    fun `top bar dividers round-trip and collapse when adjacent tools are hidden`() {
        val encoded = encodeTopBarIds(FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS)
        val decoded = decodeTopBarIds(encoded)
        assertEquals(FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS, decoded)

        val visible = visibleTopBarItems(
            order = FACTORY_TOP_BAR_ORDER_WITH_DIVIDERS,
            hidden = setOf("arrow", "axis", "graph", "table", "guides"),
        )
        assertFalse(visible.any { it.id == "div_4" })
        assertTrue(visible.count { isDividerId(it.id) } == 1)
        assertEquals("div_1", visible.first { isDividerId(it.id) }.id)
    }

    @Test
    fun `rail dividers stay in the reorderable stretch above pinned navigation`() {
        val order = listOf("pen", "highlighter", "div_1", "eraser", "pan")
        val decoded = decodeRailIds(encodeRailIds(order))
        assertEquals(order, decoded)

        val visible = visibleRailItems(order, emptySet())
        assertEquals(listOf("pages", "layers"), visible.takeLast(2).map { it.id })
        assertEquals("div_1", visible[2].id)
    }
}

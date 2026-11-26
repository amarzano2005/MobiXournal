package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactTopBarGroupingTest {

    @Test
    fun `shouldCollapseTopBar keeps 14 items separate on tablet width and collapses on phone`() {
        assertFalse(shouldCollapseTopBar(availableWidthDp = 800f, itemCount = TOP_BAR_ITEMS.size))
        assertTrue(shouldCollapseTopBar(availableWidthDp = 320f, itemCount = TOP_BAR_ITEMS.size))
        assertFalse(shouldCollapseTopBar(availableWidthDp = 0f, itemCount = TOP_BAR_ITEMS.size))
        assertFalse(shouldCollapseTopBar(availableWidthDp = 200f, itemCount = 0))
    }

    @Test
    fun `compactTopBarEntries collapses default 14 items into 7 coherent entries`() {
        val entries = compactTopBarEntries(TOP_BAR_ITEMS)
        assertEquals(7, entries.size)

        val groups = entries.filterIsInstance<CompactTopBarEntry.Group>().associate { it.group.id to it.group }
        val singles = entries.filterIsInstance<CompactTopBarEntry.Single>().map { it.item.id }

        assertEquals(listOf("table", "logic", "guides"), singles)
        assertEquals(
            setOf(EditorTool.LINE, EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.DIMENSION),
            groups.getValue("compact_lines").tools.toSet(),
        )
        assertEquals(
            setOf(
                EditorTool.RECTANGLE,
                EditorTool.ELLIPSE,
                EditorTool.TRIANGLE,
                EditorTool.TRAPEZOID,
                EditorTool.POLYGON,
            ),
            groups.getValue("compact_shapes").tools.toSet(),
        )
        assertEquals(
            setOf(EditorTool.COORDINATE_AXIS, EditorTool.PLOT),
            groups.getValue("compact_math").tools.toSet(),
        )
        val circuitTools = groups.getValue("compact_circuits").tools.toSet()
        assertTrue(EditorTool.RESISTOR in circuitTools)
        assertTrue(EditorTool.OPAMP in circuitTools)
    }

    @Test
    fun `compactTopBarEntries honours hidden items and degrades single-member families`() {
        val visible = visibleTopBarItems(
            order = DEFAULT_TOP_BAR_ORDER,
            hidden = setOf("arrow", "graph", "circuit_active", "polygons"),
            shapeHidden = setOf(
                EditorTool.RECTANGLE.name,
                EditorTool.TRIANGLE.name,
                EditorTool.TRAPEZOID.name,
            ),
        )
        val entries = compactTopBarEntries(visible)
        assertTrue("all families reduced to 1 item become Single", entries.all { it is CompactTopBarEntry.Single })
        assertEquals(
            listOf("line", "ellipse", "axis", "table", "circuit", "logic", "guides"),
            entries.map { (it as CompactTopBarEntry.Single).item.id },
        )
    }
}

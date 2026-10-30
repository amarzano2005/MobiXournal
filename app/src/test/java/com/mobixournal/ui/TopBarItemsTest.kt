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
    fun `the square and the chemistry slot are no longer buttons`() {
        assertTrue("square" !in DEFAULT_TOP_BAR_ORDER)
        assertTrue("chemistry" !in DEFAULT_TOP_BAR_ORDER)
        assertTrue(TOP_BAR_ITEMS.none { it.id == "square" || it.id == "chemistry" })
    }

    @Test
    fun `the polygon slot sits where the two polygon buttons were`() {
        val polygons = DEFAULT_TOP_BAR_ORDER.indexOf("polygons")
        assertEquals(DEFAULT_TOP_BAR_ORDER.indexOf("trapezoid") + 1, polygons)
    }

    @Test
    fun `the function graph sits immediately to the right of the coordinate axis`() {
        val axis = DEFAULT_TOP_BAR_ORDER.indexOf("axis")
        val graph = DEFAULT_TOP_BAR_ORDER.indexOf("graph")
        assertEquals(axis + 1, graph)
    }

    @Test
    fun `a saved order that is still an old factory order follows the factory reorder`() {
        // The whole settings object is written on any save, so a row the user never touched sits on
        // disk as a full copy of the *old* factory list. Without this, the axis and the arrow group
        // would keep the order that was current when the file was written, for ever.
        val legacyNoGraph = listOf(
            "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
            "trapezoid", "pentagon", "hexagon", "spline", "axis", "arrow",
            "table", "circuit", "circuit_active", "logic", "guides",
        )
        assertEquals(DEFAULT_TOP_BAR_ORDER, migrateTopBarOrder(legacyNoGraph))
        // ...and the same thing for a save written once the graph button already existed before guides.
        val legacyWithGraph =
            legacyNoGraph.dropLast(1) + "graph" + legacyNoGraph.last()
        assertEquals(DEFAULT_TOP_BAR_ORDER, migrateTopBarOrder(legacyWithGraph))
        val legacyWithChem = listOf(
            "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
            "trapezoid", "pentagon", "hexagon", "spline", "arrow", "axis",
            "table", "circuit", "circuit_active", "logic", "chemistry", "graph", "guides",
        )
        assertEquals(DEFAULT_TOP_BAR_ORDER, migrateTopBarOrder(legacyWithChem))
        // ...and for the row as it stood with the graph on the axis' right and the chemistry slot.
        val legacySquareAndChem = listOf(
            "line", "rectangle", "square", "ellipse", "triangle", "rhombus",
            "trapezoid", "pentagon", "hexagon", "spline", "arrow", "axis", "graph",
            "table", "circuit", "circuit_active", "logic", "chemistry", "guides",
        )
        assertEquals(DEFAULT_TOP_BAR_ORDER, migrateTopBarOrder(legacySquareAndChem))
    }

    @Test
    fun `a hand-arranged order is never rewritten`() {
        val mine = listOf("guides", "line", "axis", "arrow", "table")
        assertEquals(mine, migrateTopBarOrder(mine))
        // Even a row that only names a few slots is a choice, not a stale default.
        assertEquals(listOf("line"), migrateTopBarOrder(listOf("line")))
    }

    @Test
    fun `an item added since a saved order lands at its factory position, not the end`() {
        // A saved order written before the graph existed: the new slot goes in ahead of table
        // (its factory neighbour after axis) rather than being appended past everything.
        val legacy = DEFAULT_TOP_BAR_ORDER - "graph"
        val ordered = orderedTopBarItems(legacy).map { it.id }
        assertEquals(ordered.indexOf("axis") + 1, ordered.indexOf("graph"))
    }

    @Test
    fun `a hand-arranged order keeps its own sequence and still places the new item`() {
        val ordered = orderedTopBarItems(listOf("line", "rectangle", "table")).map { it.id }
        assertEquals(listOf("line", "rectangle"), ordered.take(2))
        // Nothing the order names follows the graph in factory order except table,
        // so the graph sits directly ahead of table.
        assertEquals("graph", ordered[ordered.indexOf("table") - 1])
    }

    @Test
    fun `single tools resolve to their respective EditorTool`() {
        assertEquals(EditorTool.LINE, singleToolForTopBarId("line"))
        assertEquals(EditorTool.RECTANGLE, singleToolForTopBarId("rectangle"))
        assertEquals(EditorTool.ELLIPSE, singleToolForTopBarId("ellipse"))
        assertEquals(EditorTool.RHOMBUS, singleToolForTopBarId("rhombus"))
        assertEquals(EditorTool.COORDINATE_AXIS, singleToolForTopBarId("axis"))
        assertEquals(EditorTool.SPLINE, singleToolForTopBarId("spline"))
        assertEquals(EditorTool.TABLE, singleToolForTopBarId("table"))
        // Multi-tool groups, submenus, and popups do not resolve to a single tool
        assertNull(singleToolForTopBarId("triangle"))
        // The polygon slot is a family too: its menu offers the pentagon and the hexagon.
        assertNull(singleToolForTopBarId("polygons"))
        // Triangle and trapezoid carry their own isosceles/right/scalene variant picker, so they
        // must not resolve to a plain single-tool button (that would hide the menu).
        assertNull(singleToolForTopBarId("trapezoid"))
        assertNull(singleToolForTopBarId("arrow"))
        assertNull(singleToolForTopBarId("circuit"))
        assertNull(singleToolForTopBarId("circuit_active"))
        assertNull(singleToolForTopBarId("logic"))
        assertNull(singleToolForTopBarId("guides"))
        // The graph is a dialog opener, not a tool, so it must not resolve to a pen-like button.
        assertNull(singleToolForTopBarId("graph"))
        assertNull(singleToolForTopBarId("color"))
    }

    @Test
    fun `hidden items are dropped from visible items`() {
        val visible = visibleTopBarItems(emptyList(), setOf("polygons", "axis"))
        assertTrue(visible.none { it.id == "polygons" || it.id == "axis" })
        assertEquals(TOP_BAR_ITEMS.size - 2, visible.size)
    }

    @Test
    fun `figures hidden in shapeHidden are also dropped from visible top bar items`() {
        val visible = visibleTopBarItems(emptyList(), emptySet(), setOf("LINE", "RECTANGLE", "TRIANGLE"))
        assertTrue(visible.none { it.id == "line" || it.id == "rectangle" || it.id == "triangle" })
        assertEquals(TOP_BAR_ITEMS.size - 3, visible.size)
    }

    @Test
    fun `all figure tools map bidirectionally to top bar ids`() {
        for ((tool, id) in FIGURE_TOOL_TO_TOP_BAR_ID) {
            assertTrue("$tool is a figure", tool in FIGURE_TOOLS)
            assertEquals(tool, TOP_BAR_ID_TO_FIGURE_TOOL[id])
        }
        // The two regular polygons share the "polygons" slot instead of a button each, so they have no
        // id of their own — but they are still figures, and their slot is the top bar's menu.
        for (tool in listOf(EditorTool.PENTAGON, EditorTool.HEXAGON)) {
            assertTrue("$tool is a figure", tool in FIGURE_TOOLS)
            assertNull("no top bar id for $tool", FIGURE_TOOL_TO_TOP_BAR_ID[tool])
        }
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

    @Test
    fun `sizeLetterFor maps pen width to S, M, or L`() {
        val slots = listOf(0.42f, 0.85f, 1.41f)
        assertEquals("S", sizeLetterFor(0.42f, slots))
        assertEquals("M", sizeLetterFor(0.85f, slots))
        assertEquals("L", sizeLetterFor(1.41f, slots))
        // Closest match for custom values
        assertEquals("S", sizeLetterFor(0.5f, slots))
        assertEquals("M", sizeLetterFor(1.0f, slots))
        assertEquals("L", sizeLetterFor(2.0f, slots))
    }
}

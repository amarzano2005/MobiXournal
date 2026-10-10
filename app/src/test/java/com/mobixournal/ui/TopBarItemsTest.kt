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
    fun `square appears immediately to the right of rectangle in default top bar order`() {
        val rectIndex = DEFAULT_TOP_BAR_ORDER.indexOf("rectangle")
        val squareIndex = DEFAULT_TOP_BAR_ORDER.indexOf("square")
        assertEquals(rectIndex + 1, squareIndex)
    }

    @Test
    fun `single tools resolve to their respective EditorTool`() {
        assertEquals(EditorTool.LINE, singleToolForTopBarId("line"))
        assertEquals(EditorTool.RECTANGLE, singleToolForTopBarId("rectangle"))
        assertEquals(EditorTool.SQUARE, singleToolForTopBarId("square"))
        assertEquals(EditorTool.ELLIPSE, singleToolForTopBarId("ellipse"))
        assertEquals(EditorTool.RHOMBUS, singleToolForTopBarId("rhombus"))
        assertEquals(EditorTool.PENTAGON, singleToolForTopBarId("pentagon"))
        assertEquals(EditorTool.HEXAGON, singleToolForTopBarId("hexagon"))
        assertEquals(EditorTool.COORDINATE_AXIS, singleToolForTopBarId("axis"))
        assertEquals(EditorTool.SPLINE, singleToolForTopBarId("spline"))
        assertEquals(EditorTool.TABLE, singleToolForTopBarId("table"))
        // Multi-tool groups, submenus, and popups do not resolve to a single tool
        assertNull(singleToolForTopBarId("triangle"))
        // Triangle and trapezoid carry their own isosceles/right/scalene variant picker, so they
        // must not resolve to a plain single-tool button (that would hide the menu).
        assertNull(singleToolForTopBarId("trapezoid"))
        assertNull(singleToolForTopBarId("arrow"))
        assertNull(singleToolForTopBarId("circuit"))
        assertNull(singleToolForTopBarId("circuit_active"))
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
    fun `figures hidden in shapeHidden are also dropped from visible top bar items`() {
        val visible = visibleTopBarItems(emptyList(), emptySet(), setOf("LINE", "RECTANGLE", "TRIANGLE"))
        assertTrue(visible.none { it.id == "line" || it.id == "rectangle" || it.id == "triangle" })
        assertEquals(TOP_BAR_ITEMS.size - 3, visible.size)
    }

    @Test
    fun `all figure tools map bidirectionally to top bar ids`() {
        for (tool in FIGURE_TOOLS) {
            val id = FIGURE_TOOL_TO_TOP_BAR_ID[tool]
            assertNotNull("top bar id for $tool", id)
            assertEquals(tool, TOP_BAR_ID_TO_FIGURE_TOOL[id])
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

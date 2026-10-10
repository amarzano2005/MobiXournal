package com.mobixournal.ui

import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolGroupsTest {

    @Test
    fun `every tool belongs to exactly one group`() {
        for (tool in EditorTool.entries) {
            assertEquals("group count for $tool", 1, TOOL_GROUPS.count { tool in it.tools })
            assertNotNull(groupOf(tool))
        }
    }

    @Test
    fun `every tool has a label and an icon`() {
        // The rail and every picker resolve a tool's name and glyph through `EditorTool.label` /
        // `.icon`, which look the tool up in the TOOLS table — so a tool added to the enum but not to
        // that table would only blow up while drawing the UI. This is the guard for that.
        for (tool in EditorTool.entries) {
            assertTrue("label for $tool", tool.label.isNotBlank())
            assertNotNull("icon for $tool", tool.icon)
        }
    }

    @Test
    fun `every stem figure is a shape tool`() {
        for (tool in listOf(
            EditorTool.TRIANGLE, EditorTool.SQUARE, EditorTool.RHOMBUS, EditorTool.TRAPEZOID,
            EditorTool.PENTAGON, EditorTool.HEXAGON,
        )) {
            assertTrue("$tool draws a figure", tool in SHAPE_TOOLS)
        }
    }

    @Test
    fun `group ids are unique`() {
        assertEquals(TOOL_GROUPS.size, TOOL_GROUPS.map { it.id }.toSet().size)
    }

    @Test
    fun `background select lives in the select group`() {
        assertEquals("select", groupOf(EditorTool.BG_SELECT)?.id)
    }

    @Test
    fun `line and rectangle each get a single-tool slot`() {
        val line = groupOf(EditorTool.LINE)!!
        assertEquals(listOf(EditorTool.LINE), line.tools)
        val rectangle = groupOf(EditorTool.RECTANGLE)!!
        assertEquals(listOf(EditorTool.RECTANGLE), rectangle.tools)
    }

    @Test
    fun `the shapes group defaults to the ellipse`() {
        val shapes = groupOf(EditorTool.ELLIPSE)!!
        assertEquals(EditorTool.ELLIPSE, shapes.tools.first())
        assertEquals(EditorTool.ELLIPSE, shapes.selected(emptyMap()))
        // The STEM figures share that slot.
        assertTrue(
            listOf(
                EditorTool.TRIANGLE, EditorTool.SQUARE, EditorTool.COORDINATE_AXIS,
                EditorTool.SPLINE,
            ).all { it in shapes.tools }
        )
    }

    @Test
    fun `the arrows group defaults to the arrow`() {
        val arrows = groupOf(EditorTool.ARROW)!!
        assertEquals("arrow", arrows.id)
        assertEquals(EditorTool.ARROW, arrows.tools.first())
        // The measuring arrow shares the slot: it is an arrow with a gap for the value.
        assertEquals(
            listOf(EditorTool.ARROW, EditorTool.DOUBLE_ARROW, EditorTool.DIMENSION),
            arrows.tools,
        )
    }

    @Test
    fun `selection defaults to the first member`() {
        val shapes = groupOf(EditorTool.ELLIPSE)!!
        assertEquals(EditorTool.ELLIPSE, shapes.selected(emptyMap()))
        assertEquals(EditorTool.TRIANGLE, shapes.selected(mapOf(shapes.id to EditorTool.TRIANGLE)))
    }

    @Test
    fun `a non-member selection is ignored on both write and read`() {
        val pen = groupOf(EditorTool.PEN)!!
        assertEquals(emptyMap<String, EditorTool>(), pen.withSelection(emptyMap(), EditorTool.ARROW))
        assertEquals(EditorTool.PEN, pen.selected(mapOf(pen.id to EditorTool.ARROW)))
    }

    @Test
    fun `selections round-trip through the preference encoding`() {
        val highlighter = groupOf(EditorTool.HIGHLIGHTER)!!
        val insert = groupOf(EditorTool.TEXIMAGE)!!
        val selections =
            mapOf(highlighter.id to EditorTool.HIGHLIGHTER, insert.id to EditorTool.TEXIMAGE)
        assertEquals(selections, decodeToolGroupSelections(encodeToolGroupSelections(selections)))
    }

    @Test
    fun `corrupt or stale preference entries are dropped`() {
        val shapes = groupOf(EditorTool.ELLIPSE)!!
        val pen = groupOf(EditorTool.PEN)!!
        // TRIANGLE is not a member of the pen group, so that entry has to be dropped too.
        val raw = "nosuchgroup:PEN,${shapes.id}:NOSUCHTOOL,${pen.id}:TRIANGLE,${shapes.id}:TRIANGLE,junk"
        assertEquals(mapOf(shapes.id to EditorTool.TRIANGLE), decodeToolGroupSelections(raw))
    }

    @Test
    fun `the starting tool honours the default tool's persisted group choice`() {
        val eraser = groupOf(EditorTool.ERASER_WHOLE)!!
        assertEquals(EditorTool.ERASER_WHOLE, startingTool(EditorTool.ERASER_WHOLE, emptyMap()))
        assertEquals(
            EditorTool.ERASER,
            startingTool(EditorTool.ERASER_WHOLE, mapOf(eraser.id to EditorTool.ERASER)),
        )
        // A choice in another group doesn't move the starting tool.
        val select = groupOf(EditorTool.SELECT)!!
        assertEquals(
            EditorTool.ERASER_WHOLE,
            startingTool(EditorTool.ERASER_WHOLE, mapOf(select.id to EditorTool.LASSO_SELECT)),
        )
    }

    @Test
    fun `the eraser a toggle switches to is the rail's own eraser`() {
        val eraser = groupOf(EditorTool.ERASER_WHOLE)!!
        // Whole-stroke by default: a toggle is reached for to delete, not to nibble.
        assertEquals(EditorTool.ERASER_WHOLE, preferredEraser(emptyMap()))
        // ... but it follows the rail when the user has picked the partial eraser from its menu.
        assertEquals(EditorTool.ERASER, preferredEraser(mapOf(eraser.id to EditorTool.ERASER)))
        // A selection in another slot can't move it, and neither can a corrupt one.
        val select = groupOf(EditorTool.SELECT)!!
        assertEquals(EditorTool.ERASER_WHOLE, preferredEraser(mapOf(select.id to EditorTool.LASSO_SELECT)))
        assertEquals(EditorTool.ERASER_WHOLE, preferredEraser(mapOf(eraser.id to EditorTool.PEN)))
    }

    @Test
    fun `similar members share a picker row`() {
        val active = groupOf(EditorTool.BJT_NPN)!!
        val rows = pickerRows(active.pickerLayout, active.tools)
        assertTrue(
            "the two transistors share a row",
            rows.any { EditorTool.BJT_NPN in it && EditorTool.BJT_PNP in it },
        )
        assertTrue(
            "the diode family shares a row",
            rows.any { row ->
                row.containsAll(listOf(EditorTool.DIODE, EditorTool.LED, EditorTool.ZENER_DIODE))
            },
        )
        assertTrue(
            "the two sources share a row",
            rows.any { row ->
                row.containsAll(listOf(EditorTool.DC_SOURCE, EditorTool.CURRENT_SOURCE))
            },
        )
    }

    @Test
    fun `every picker layout places each of its members once and keeps rows menu-width`() {
        for (group in TOOL_GROUPS) {
            val rows = pickerRows(group.pickerLayout, group.tools)
            val flat = rows.flatten()
            assertEquals("members for ${group.id}", group.tools.toSet(), flat.toSet())
            assertEquals("no repeats for ${group.id}", group.tools.size, flat.size)
            assertTrue(
                "rows fit a phone's menu for ${group.id}",
                rows.all { it.size <= PICKER_ROW_SIZE },
            )
        }
    }

    @Test
    fun `members a layout does not name still get a row of their own`() {
        // The safety net: a tool added to a group before its layout is updated (or a picker shown
        // a list the layout knows nothing about) must still be reachable.
        val members = listOf(
            EditorTool.ARROW, EditorTool.DOUBLE_ARROW,
            EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.TEXT, EditorTool.IMAGE,
        )
        val rows = pickerRows(listOf(listOf(EditorTool.DOUBLE_ARROW, EditorTool.ARROW)), members)
        assertEquals(listOf(EditorTool.DOUBLE_ARROW, EditorTool.ARROW), rows.first())
        // The declared row leads in the layout's own order; the unnamed tail keeps the caller's.
        assertEquals(
            listOf(
                EditorTool.DOUBLE_ARROW, EditorTool.ARROW,
                EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.TEXT, EditorTool.IMAGE,
            ),
            rows.flatten(),
        )
        assertEquals(
            listOf(
                listOf(EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.TEXT),
                listOf(EditorTool.IMAGE),
            ),
            rows.drop(1),
        )
    }

    @Test
    fun `the shapes slot declares no layout and wraps the user's order`() {
        val shapes = groupOf(EditorTool.ELLIPSE)!!
        assertTrue("the Shapes slot is user-ordered, so it declares no grouping", shapes.pickerLayout.isEmpty())
        val ordered = listOf(
            EditorTool.HEXAGON, EditorTool.TRIANGLE,
            EditorTool.ELLIPSE, EditorTool.SPLINE,
        )
        assertEquals(
            listOf(
                listOf(EditorTool.HEXAGON, EditorTool.TRIANGLE, EditorTool.ELLIPSE),
                listOf(EditorTool.SPLINE),
            ),
            pickerRows(shapes.pickerLayout, ordered),
        )
    }

    @Test
    fun `a hidden member drops out of its declared row without unseating the rest`() {
        val active = groupOf(EditorTool.BJT_NPN)!!
        val members = active.tools - EditorTool.LED
        val rows = pickerRows(active.pickerLayout, members)
        assertEquals(members.toSet(), rows.flatten().toSet())
        assertTrue(rows.none { EditorTool.LED in it })
        assertEquals(
            "the diode row closes up instead of leaving a gap",
            listOf(EditorTool.DIODE, EditorTool.ZENER_DIODE),
            rows.first(),
        )
    }

    @Test
    fun `a variant picker wraps its kinds two to a row without losing one`() {
        // The pure row layout behind `ToolVariantPicker`, which the triangle's and the trapezoid's
        // submenus both use: a kind must never fall out of the menu however many there are.
        for (count in 0..7) {
            val rows = variantRows(count)
            assertEquals("kinds for $count", (0 until count).toList(), rows.flatten())
            assertTrue("rows fit the menu for $count", rows.all { it.size <= VARIANT_ROW_SIZE })
        }
        // The two figures that ship variants, laid out the way their pickers show them.
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), variantRows(TriangleKind.values().size))
        assertEquals(listOf(listOf(0, 1), listOf(2)), variantRows(TrapezoidKind.values().size))
    }

    @Test
    fun `an empty preference decodes to no selections`() {
        assertEquals(emptyMap<String, EditorTool>(), decodeToolGroupSelections(null))
        assertEquals(emptyMap<String, EditorTool>(), decodeToolGroupSelections(""))
    }
}

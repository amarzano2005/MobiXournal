package com.mobixournal.ui

import com.mobixournal.render.ShapeBuilder

import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolGroupsTest {

    @Test
    fun `every writing tool belongs to exactly one rail group`() {
        for (tool in WRITING_RAIL_TOOLS) {
            assertEquals("rail group count for $tool", 1, TOOL_GROUPS.count { tool in it.tools })
            assertNotNull(groupOf(tool))
        }
    }

    @Test
    fun `the STEM tools are not rail slots but keep their top-bar families`() {
        // Their one home is the Secondary Toolbar in the top bar; a rail group for them would be the
        // second copy of the same buttons on one screen.
        for (tool in listOf(
            EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.ELLIPSE, EditorTool.TRIANGLE,
            EditorTool.ARROW, EditorTool.TABLE, EditorTool.RESISTOR, EditorTool.BJT_NPN,
            EditorTool.AND_GATE, EditorTool.PLAY_OBJECT,
        )) {
            assertEquals("no rail group for $tool", 0, TOOL_GROUPS.count { tool in it.tools })
        }
        // ... and the top bar still resolves its family buttons through those groups.
        assertEquals("circuit", secondaryToolGroup("circuit")?.id)
        assertEquals("circuit_active", secondaryToolGroup("circuit_active")?.id)
        assertEquals("logic", secondaryToolGroup("logic")?.id)
        assertEquals("polygons", secondaryToolGroup("polygons")?.id)
        assertEquals("arrow", secondaryToolGroup("arrow")?.id)
        // The chemistry section is gone: no top-bar family, and no picker to reach its bonds.
        assertEquals(null, secondaryToolGroup("chemistry"))
        assertNotNull("the transistor family is still a picker", groupOf(EditorTool.BJT_NPN))
        assertEquals(null, secondaryToolGroup("zoom"))
    }

    private companion object {
        /** The tools the rail offers: the writing/selection/authoring set, nothing else. */
        val WRITING_RAIL_TOOLS = listOf(
            EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.ERASER_WHOLE,
            EditorTool.HAND, EditorTool.SELECT, EditorTool.LASSO_SELECT,
            EditorTool.TEXT, EditorTool.TEXIMAGE, EditorTool.IMAGE, EditorTool.VERTICAL_SPACE,
        )
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
            EditorTool.TRIANGLE, EditorTool.RHOMBUS, EditorTool.TRAPEZOID,
        )) {
            assertTrue("$tool draws a figure", tool in SHAPE_TOOLS)
        }
        // The square is not offered any more: the rectangle draws one.
        assertTrue(EditorTool.SQUARE !in SHAPE_TOOLS)
    }

    @Test
    fun `the polygons slot is one tool, whose menu is its side count`() {
        val polygons = secondaryToolGroup("polygons")
        assertEquals("polygons", polygons?.id)
        // One member, not a family: the side count is a parameter of the tool, so a pentagon and a
        // hexagon are entries in its menu rather than buttons of their own.
        assertEquals(listOf(EditorTool.POLYGON), polygons?.tools)
    }

    @Test
    fun `the polygon picker offers every side count the builder can draw`() {
        assertEquals(
            listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            POLYGON_SIDE_CHOICES,
        )
        // The menu, the setting and the figure share one range: an entry outside it is impossible.
        assertEquals(ShapeBuilder.MIN_POLYGON_SIDES, POLYGON_SIDE_CHOICES.first())
        assertEquals(ShapeBuilder.MAX_POLYGON_SIDES, POLYGON_SIDE_CHOICES.last())
    }

    @Test
    fun `a picked row names the side count it stores`() {
        for ((index, sides) in POLYGON_SIDE_CHOICES.withIndex()) {
            assertEquals(sides, polygonSidesAt(index))
            assertEquals(index, polygonSideIndex(sides))
        }
        // A stale or hand-edited count still opens the picker on something it can show.
        assertEquals(polygonSideIndex(ShapeBuilder.MIN_POLYGON_SIDES), polygonSideIndex(0))
        assertEquals(polygonSideIndex(ShapeBuilder.MAX_POLYGON_SIDES), polygonSideIndex(99))
        assertEquals(ShapeBuilder.MIN_POLYGON_SIDES, polygonSidesAt(-4))
        assertEquals(ShapeBuilder.MAX_POLYGON_SIDES, polygonSidesAt(400))
    }

    @Test
    fun `group ids are unique`() {
        assertEquals(TOOL_GROUPS.size, TOOL_GROUPS.map { it.id }.toSet().size)
    }

    @Test
    fun `select group contains select and lasso`() {
        val select = groupOf(EditorTool.SELECT)
        assertEquals("select", select?.id)
        assertEquals(listOf(EditorTool.SELECT, EditorTool.LASSO_SELECT), select?.tools)
    }

    @Test
    fun `the single-member slots are plain buttons`() {
        for (tool in listOf(EditorTool.HAND, EditorTool.IMAGE, EditorTool.VERTICAL_SPACE)) {
            assertEquals("$tool has a slot of its own", listOf(tool), groupOf(tool)?.tools)
        }
    }

    @Test
    fun `selection defaults to the first member`() {
        val text = groupOf(EditorTool.TEXT)!!
        assertEquals(EditorTool.TEXT, text.selected(emptyMap()))
        assertEquals(EditorTool.TEXIMAGE, text.selected(mapOf(text.id to EditorTool.TEXIMAGE)))
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
        val text = groupOf(EditorTool.TEXT)!!
        val pen = groupOf(EditorTool.PEN)!!
        // TRIANGLE is not a member of the pen group, so that entry has to be dropped too.
        val raw = "nosuchgroup:PEN,${text.id}:NOSUCHTOOL,${pen.id}:TRIANGLE,${text.id}:TEXIMAGE,junk"
        assertEquals(mapOf(text.id to EditorTool.TEXIMAGE), decodeToolGroupSelections(raw))
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
        // The mechanism the STEM pickers were laid out with; the rail keeps it for any future group.
        val members = listOf(EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.ELLIPSE)
        val rows = pickerRows(listOf(listOf(EditorTool.LINE, EditorTool.RECTANGLE)), members)
        assertEquals(listOf(EditorTool.LINE, EditorTool.RECTANGLE), rows.first())
        assertEquals(listOf(EditorTool.ELLIPSE), rows.drop(1).flatten())
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
    fun `a group with no layout wraps its members`() {
        val text = groupOf(EditorTool.TEXT)!!
        assertTrue("the Text slot declares no grouping", text.pickerLayout.isEmpty())
        assertEquals(listOf(listOf(EditorTool.TEXT, EditorTool.TEXIMAGE)), pickerRows(text.pickerLayout, text.tools))
    }

    @Test
    fun `a hidden member drops out of its declared row without unseating the rest`() {
        val members = listOf(EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.ELLIPSE)
        val rows = pickerRows(listOf(listOf(EditorTool.LINE, EditorTool.RECTANGLE)), members - EditorTool.RECTANGLE)
        assertEquals(members.toSet() - EditorTool.RECTANGLE, rows.flatten().toSet())
        assertTrue(rows.none { EditorTool.RECTANGLE in it })
        assertEquals(
            "the row closes up instead of leaving a gap",
            listOf(EditorTool.LINE),
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

package com.mobixournal.ui

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
        assertEquals(listOf(EditorTool.ARROW, EditorTool.DOUBLE_ARROW), arrows.tools)
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
    fun `an empty preference decodes to no selections`() {
        assertEquals(emptyMap<String, EditorTool>(), decodeToolGroupSelections(null))
        assertEquals(emptyMap<String, EditorTool>(), decodeToolGroupSelections(""))
    }
}

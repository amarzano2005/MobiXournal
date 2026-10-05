package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Figures settings section's pure helpers: the order and hidden set the Shapes submenu uses must
 * degrade gracefully — an unknown name ignored, a member the saved order omits still shown.
 */
class ShapeSubmenuTest {

    @Test fun anEmptyOrderIsTheFactorySubmenu() {
        assertEquals(SHAPE_GROUP.tools, orderedShapeTools(emptyList()))
    }

    @Test fun aPartialOrderLeadsAndTheRestIsAppended() {
        val ordered = orderedShapeTools(listOf("HEXAGON", "TRIANGLE"))
        assertEquals(listOf(EditorTool.HEXAGON, EditorTool.TRIANGLE), ordered.take(2))
        assertEquals(SHAPE_GROUP.tools.size, ordered.size)
        assertEquals(SHAPE_GROUP.tools.toSet(), ordered.toSet())
    }

    @Test fun unknownAndDuplicateNamesAreIgnored() {
        val ordered = orderedShapeTools(listOf("NOPE", "HEXAGON", "HEXAGON"))
        assertEquals(EditorTool.HEXAGON, ordered.first())
        assertEquals(SHAPE_GROUP.tools.size, ordered.size)
    }

    @Test fun hiddenMembersAreDroppedFromTheVisibleSubmenu() {
        val visible = visibleShapeTools(emptyList(), setOf("PENTAGON", "HEXAGON"))
        assertEquals(SHAPE_GROUP.tools.size - 2, visible.size)
        assertTrue(visible.none { it.name == "PENTAGON" || it.name == "HEXAGON" })
    }

    @Test fun theStemFiguresAreInTheShapesGroup() {
        val members = SHAPE_GROUP.tools
        assertTrue(
            listOf("TRIANGLE", "SQUARE", "RHOMBUS", "TRAPEZOID", "PENTAGON", "HEXAGON")
                .all { name -> members.any { it.name == name } },
        )
    }

    @Test fun movingAnIdSwapsItWithItsNeighbour() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveId(ids, 0, 1))
        assertEquals(listOf("a", "c", "b"), moveId(ids, 2, -1))
        assertEquals(ids, moveId(ids, 0, -1))
        assertEquals(ids, moveId(ids, 2, 1))
    }

    @Test fun namesRoundTripThroughEncodeAndDecode() {
        val names = listOf("TRIANGLE", "HEXAGON")
        assertEquals(names, decodeToolNames(encodeToolNames(names), SHAPE_GROUP.tools))
        assertEquals(emptyList<String>(), decodeToolNames(null, SHAPE_GROUP.tools))
        assertEquals(listOf("TRIANGLE"), decodeToolNames("TRIANGLE,GONE", SHAPE_GROUP.tools))
    }
}

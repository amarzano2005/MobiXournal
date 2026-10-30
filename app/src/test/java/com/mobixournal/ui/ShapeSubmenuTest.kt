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
        assertEquals(FIGURE_TOOLS, orderedShapeTools(emptyList()))
    }

    @Test fun aPartialOrderLeadsAndTheRestIsAppended() {
        val ordered = orderedShapeTools(listOf("POLYGON", "TRIANGLE"))
        assertEquals(listOf(EditorTool.POLYGON, EditorTool.TRIANGLE), ordered.take(2))
        assertEquals(FIGURE_TOOLS.size, ordered.size)
        assertEquals(FIGURE_TOOLS.toSet(), ordered.toSet())
    }

    @Test fun unknownAndDuplicateNamesAreIgnored() {
        val ordered = orderedShapeTools(listOf("NOPE", "POLYGON", "POLYGON"))
        assertEquals(EditorTool.POLYGON, ordered.first())
        assertEquals(FIGURE_TOOLS.size, ordered.size)
    }

    @Test fun hiddenFiguresAreDroppedFromTheSecondaryToolbar() {
        // The Shapes submenu is gone with the rail's figure slots; the hidden set still governs what
        // the top bar's Secondary Toolbar draws.
        val visible = visibleTopBarItems(emptyList(), emptySet(), setOf("TRIANGLE", "RHOMBUS"))
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.none { it.id == "triangle" || it.id == "rhombus" })
    }

    @Test fun simpleLineAndRectangleAreInTheFigures() {
        val members = FIGURE_TOOLS
        assertTrue(members.contains(EditorTool.LINE))
        assertTrue(members.contains(EditorTool.RECTANGLE))
    }

    @Test fun theStemFiguresAreInTheShapesGroup() {
        val members = FIGURE_TOOLS
        assertTrue(
            listOf("TRIANGLE", "RHOMBUS", "TRAPEZOID", "POLYGON")
                .all { name -> members.any { it.name == name } },
        )
        // A square is a rectangle: the figure left with its button.
        assertTrue(members.none { it.name == "SQUARE" })
    }

    @Test fun movingAnIdSwapsItWithItsNeighbour() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveId(ids, 0, 1))
        assertEquals(listOf("a", "c", "b"), moveId(ids, 2, -1))
        assertEquals(ids, moveId(ids, 0, -1))
        assertEquals(ids, moveId(ids, 2, 1))
    }

    @Test fun namesRoundTripThroughEncodeAndDecode() {
        val names = listOf("LINE", "RECTANGLE", "TRIANGLE", "POLYGON")
        assertEquals(names, decodeToolNames(encodeToolNames(names), FIGURE_TOOLS))
        assertEquals(emptyList<String>(), decodeToolNames(null, FIGURE_TOOLS))
        assertEquals(listOf("TRIANGLE"), decodeToolNames("TRIANGLE,GONE", FIGURE_TOOLS))
    }
}

package com.mobixournal.desktop

import com.mobixournal.desktop.tools.opensSubmenu
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.shapeFromDrag
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.FigureShape
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.FIGURE_TOOLS
import com.mobixournal.desktop.tools.FigureSettings
import com.mobixournal.desktop.tools.SlotClick
import com.mobixournal.desktop.tools.ToolSelection
import com.mobixournal.desktop.tools.figureMenu
import com.mobixournal.desktop.tools.figureMenuRowSize
import com.mobixournal.desktop.tools.figureSlotGlyph
import com.mobixournal.desktop.tools.groupOf
import com.mobixournal.desktop.tools.slotClick

import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **figures menus**: one slot per figure, and the tablet's own answers behind each.
 *
 * The claim this file protects is the parity the tablet sets: a triangle's four kinds, a trapezoid's
 * three, and the polygon's count are chosen **where the figure is drawn** — the slot's menu — with the
 * chosen one on the slot's face, not buried in a settings page. The other half is that a pick reaches
 * the page: the variant and the count are read by the drag, so the menu and the geometry cannot
 * disagree about which triangle is in hand.
 */
class DesktopFigureMenuTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A window whose answers land in a scratch file, never in the owner's settings. */
    private fun scratchState(): DesktopShellState =
        DesktopShellState(DesktopSettings(temp.newFile("figures.properties")))

    @Test
    fun `each figure is a slot of its own and opens its own menu`() {
        for (tool in FIGURE_TOOLS) {
            val group = groupOf(tool)
            assertNotNull("$tool has no slot", group)
            assertEquals("$tool shares its slot", listOf(tool), group!!.members)
            assertTrue("$tool's menu is its submenu", group.opensSubmenu())
            assertEquals(
                "the first click only arms the slot",
                SlotClick.Pick(tool),
                slotClick(group, ToolSelection(DesktopTool.PEN)),
            )
            assertEquals(
                "the second click opens the figure's own answers",
                SlotClick.OpenPicker,
                slotClick(group, ToolSelection(tool)),
            )
            assertNotNull("$tool has a menu", figureMenu(tool, FigureShape()))
        }
        assertNull("a rectangle has nothing to ask", figureMenu(DesktopTool.RECTANGLE, FigureShape()))
    }

    @Test
    fun `the triangle menu is the tablet's kinds, each with its own picture`() {
        val options = figureMenu(DesktopTool.TRIANGLE, FigureShape())!!
        assertEquals("every kind the tablet offers", TriangleKind.entries.size, options.size)
        assertEquals(
            "in the enum's order, named as the tablet names them",
            TriangleKind.entries.map { it.label },
            options.map { it.label },
        )
        assertEquals(
            "no two kinds share a picture",
            options.size,
            options.mapNotNull { it.glyph }.toSet().size,
        )
        assertEquals(
            "each cell writes its own kind",
            TriangleKind.entries.toList(),
            options.map { it.shape.triangleKind },
        )
        assertEquals(
            "the factory kind is the live one",
            listOf(TriangleKind.EQUILATERAL),
            options.filter { it.selected }.map { it.shape.triangleKind },
        )
        // Opening it on a right-angled answer moves the tick, so the menu says what the page will draw.
        val right = figureMenu(DesktopTool.TRIANGLE, FigureShape(triangleKind = TriangleKind.RIGHT))!!
        assertEquals(ToolGlyph.TRIANGLE_RIGHT, right.single { it.selected }.glyph)
    }

    @Test
    fun `the trapezoid menu is its three kinds`() {
        val options = figureMenu(DesktopTool.TRAPEZOID, FigureShape())!!
        assertEquals(TrapezoidKind.entries.size, options.size)
        assertEquals(TrapezoidKind.entries.map { it.label }, options.map { it.label })
        assertEquals(options.size, options.mapNotNull { it.glyph }.toSet().size)
        assertEquals(
            "the isosceles trapezoid is the factory answer, as on the tablet",
            listOf(TrapezoidKind.ISOSCELES),
            options.filter { it.selected }.map { it.shape.trapezoidKind },
        )
    }

    @Test
    fun `the polygon menu is every count the builder draws`() {
        val options = figureMenu(DesktopTool.POLYGON, FigureShape())!!
        assertEquals(
            "three to twelve, one cell each",
            ShapeBuilder.MAX_POLYGON_SIDES - ShapeBuilder.MIN_POLYGON_SIDES + 1,
            options.size,
        )
        assertEquals(
            "the counts in order, as the cell prints them",
            (ShapeBuilder.MIN_POLYGON_SIDES..ShapeBuilder.MAX_POLYGON_SIDES).map { "$it" },
            options.map { it.label },
        )
        for (option in options) {
            assertEquals(
                "${option.label} writes the count it names",
                option.label.toInt(),
                option.shape.polygonSides,
            )
            assertNull("the polygon draws its own count, not a catalogue picture", option.glyph)
        }
        assertEquals(
            "six sides at the factory, as on the tablet",
            listOf(ShapeBuilder.DEFAULT_POLYGON_SIDES),
            options.filter { it.selected }.map { it.shape.polygonSides },
        )
        assertEquals("the numbers sit five to a row", 5, figureMenuRowSize(DesktopTool.POLYGON))
        assertEquals("the kinds read two to a row", 2, figureMenuRowSize(DesktopTool.TRIANGLE))
    }

    @Test
    fun `a pick is the shape the drag reads, and it closes the menu`() {
        val state = scratchState()
        state.prefs.figures.showMenu(DesktopTool.TRIANGLE)
        assertEquals(DesktopTool.TRIANGLE, state.prefs.figures.menu)

        val equilateral = shapeFromDrag(
            ShapeKind.TRIANGLE, 0.0, 0.0, 100.0, 80.0, 2f, constrained = false,
            figures = state.prefs.figures.shape,
        )
        val right = figureMenu(DesktopTool.TRIANGLE, state.prefs.figures.shape)!!
            .single { it.shape.triangleKind == TriangleKind.RIGHT }
        state.prefs.figures.chooseShape(right.shape)
        assertNull("answering the menu puts it away", state.prefs.figures.menu)
        assertNotEquals(
            "the page draws the kind the menu answered",
            equilateral,
            shapeFromDrag(
                ShapeKind.TRIANGLE, 0.0, 0.0, 100.0, 80.0, 2f, constrained = false,
                figures = state.prefs.figures.shape,
            ),
        )

        val heptagon = figureMenu(DesktopTool.POLYGON, state.prefs.figures.shape)!!
            .single { it.label == "7" }
        state.prefs.figures.chooseShape(heptagon.shape)
        assertEquals(
            "seven sides plus the closing point",
            8,
            shapeFromDrag(
                ShapeKind.POLYGON, 0.0, 0.0, 100.0, 100.0, 2f, constrained = false,
                figures = state.prefs.figures.shape,
            ).size,
        )
    }

    @Test
    fun `the slot wears the variant it will draw`() {
        assertEquals(
            ToolGlyph.TRIANGLE_RIGHT,
            figureSlotGlyph(DesktopTool.TRIANGLE, FigureShape(triangleKind = TriangleKind.RIGHT)),
        )
        assertEquals(
            ToolGlyph.TRAPEZOID_SCALENE,
            figureSlotGlyph(DesktopTool.TRAPEZOID, FigureShape(trapezoidKind = TrapezoidKind.SCALENE)),
        )
        assertEquals(
            "the polygon keeps the family's glyph, and prints its count beside it",
            ToolGlyph.POLYGON,
            figureSlotGlyph(DesktopTool.POLYGON, FigureShape(polygonSides = 9)),
        )
        assertEquals(
            "a figure that is not one of the three keeps its own face",
            ToolGlyph.RECTANGLE,
            figureSlotGlyph(DesktopTool.RECTANGLE, FigureShape()),
        )
    }

    @Test
    fun `the answers survive a restart, and a hand-edited count is clamped`() {
        val properties = Properties()
        FigureSettings.write(properties, FigureShape(polygonSides = 9))
        val reopened = FigureSettings.read(properties)
        assertEquals("the polygon reopens at the count it was left on", 9, reopened.polygonSides)

        properties.setProperty("figure.polygonSides", "40")
        assertEquals(
            "past the builder's range is clamped, not drawn as a circle",
            ShapeBuilder.MAX_POLYGON_SIDES,
            FigureSettings.read(properties).polygonSides,
        )
        properties.setProperty("figure.polygonSides", "not a number")
        assertEquals(
            "a value this build cannot read falls back to the factory count",
            ShapeBuilder.DEFAULT_POLYGON_SIDES,
            FigureSettings.read(properties).polygonSides,
        )
        assertEquals(
            "and the shape itself refuses an out-of-range count",
            ShapeBuilder.MIN_POLYGON_SIDES,
            FigureShape().withPolygonSides(-3).polygonSides,
        )
    }
}

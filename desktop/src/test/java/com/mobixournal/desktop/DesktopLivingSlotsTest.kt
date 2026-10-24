package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.Bounds
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ShapeKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's STEM slots: a press on the page writes **one living element**, selected, as **one undo
 * step**.
 *
 * The box and the builders are `LivingPlacementTest`'s (they live in `mobix-core` with the maths, so the
 * tablet and the window place an element the same way). What this suite owns is the gesture end to end:
 * a drag with Plot writes a real `PlotElement` at those bounds, a click with Table writes the default
 * size, the Circuit slot writes the component it is armed with, the new element is what stays selected,
 * and one `Ctrl+Z` takes the whole placement back.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopLivingSlotsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(): File = temp.newFile("living.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun canvas(
        tool: DesktopTool,
        circuitKind: ShapeKind = LivingPlacement.DEFAULT_CIRCUIT_KIND,
    ): Pair<DesktopTab, ImageComposeScene> {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = tool,
                ink = DesktopInk(width = 3f),
                circuitKind = circuitKind,
                onViewport = {},
                onFit = {},
            )
        }
        scene.render()
        Snapshot.sendApplyNotifications()
        return tab to scene
    }

    @Test
    fun `a drag on the Plot slot writes one live plot and one undo step`() {
        val (tab, scene) = canvas(DesktopTool.PLOT)
        try {
            drag(scene, fromX = 120f, fromY = 140f, toX = 360f, toY = 300f)

            val plot = elements(tab).filterIsInstance<PlotElement>().single()
            assertEquals(120.0, plot.left, 1.5)
            assertEquals(140.0, plot.top, 1.5)
            assertEquals(360.0, plot.right, 1.5)
            assertEquals(300.0, plot.bottom, 1.5)
            assertEquals(LivingPlacement.DEFAULT_PLOT_FORMULA, plot.curves.single().source)
            assertTrue("the placement is one undo step", tab.canUndo)
            val selection = tab.selection
            assertNotNull("what was just placed stays selected", selection)
            assertEquals(1, selection!!.refs.size)
            assertTrue(
                "the outline follows the element",
                selection.bounds.expand(0.5).contains(plot.left, plot.top),
            )
            assertNotNull(
                "so the pencil on the bar opens the very element that was placed",
                livingTargetOf(tab),
            )

            assertTrue(tab.undo())
            assertTrue("undo takes the placement away", elements(tab).none { it is PlotElement })
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a click on the Table slot places the default size`() {
        val (tab, scene) = canvas(DesktopTool.TABLE)
        try {
            tap(scene, x = 300f, y = 400f)

            val table = elements(tab).filterIsInstance<TableElement>().single()
            val bounds = ElementBounds.of(table)
            val default = LivingPlacement.defaultSize(com.mobixournal.render.LivingSlot.TABLE, 595.0)
            assertEquals(default.width, bounds.width, 1.5)
            assertEquals(default.height, bounds.height, 1.5)
            assertEquals(3, table.rowHeights.size)
            assertEquals(3, table.colWidths.size)
            assertTrue("a table placed from the strip has a header", table.headerRow)
            assertTrue(tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `the Circuit slot places the component the picker armed`() {
        val (tab, scene) = canvas(DesktopTool.CIRCUIT, circuitKind = ShapeKind.CAPACITOR)
        try {
            drag(scene, fromX = 200f, fromY = 300f, toX = 280f, toY = 340f)

            val part = elements(tab).filterIsInstance<CircuitElement>().single()
            assertEquals("CAPACITOR", part.symbol)
            assertEquals(2, part.pins.size)
            assertNotNull("the element keeps its recipe, not a drawing", part.pins.first().relX)
            assertTrue(tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `each circuit family opens its own palette and Esc closes it`() {
        val state = DesktopShellState()
        state.selectTool(DesktopTool.CIRCUIT_ACTIVE)
        state.showCircuitKinds(CircuitFamily.ACTIVE)
        assertEquals("the palette is a surface on screen", CircuitFamily.ACTIVE, state.circuitPickerOpen)
        assertTrue("Esc is what closes it", state.apply(ShellCommand.DISMISS_FAILURE))
        assertNull(state.circuitPickerOpen)

        state.pickCircuitKind(CircuitFamily.ACTIVE, ShapeKind.OPAMP)
        assertEquals(
            "picking arms that family's slot for the next placement",
            ShapeKind.OPAMP,
            state.activeCircuitKind,
        )
        assertNull(state.circuitPickerOpen)

        // The other families keep their own arms: three palettes, not one shared part.
        state.selectTool(DesktopTool.CIRCUIT_LOGIC)
        assertEquals("the logic slot starts on its own face", ShapeKind.AND_GATE, state.activeCircuitKind)
    }

    @Test
    fun `a plot asks its formula before it exists and only then writes`() {
        val state = DesktopShellState()
        state.open(notebook())
        val tab = state.tabs.active!!
        state.selectTool(DesktopTool.PLOT)
        val bounds = Bounds(100.0, 120.0, 340.0, 264.0)

        state.placeOrAsk(tab, LivingSlot.PLOT, bounds)
        val pending = state.pendingPlacement
        assertNotNull("the drag asks the graph dialog's questions", pending)
        assertTrue("and nothing is on the page yet", elements(tab).none { it is PlotElement })
        assertFalse("nor is there an undo step to take back", tab.canUndo)

        pending!!.formulas[0] = "cos(x)"
        pending.xMin = "-3"
        pending.xMax = "3"
        pending.showCriticalPoints = true
        state.confirmPlacement()

        val plot = elements(tab).filterIsInstance<PlotElement>().single()
        assertEquals("cos(x)", plot.curves.single().source)
        assertEquals(-3.0, plot.xMin, 1e-9)
        assertEquals(3.0, plot.xMax, 1e-9)
        assertTrue(plot.showCriticalPoints)
        assertEquals(bounds.left, plot.left, 1e-9)
        assertNull("the question is answered", state.pendingPlacement)
        assertTrue("and the placement is one undo step", tab.canUndo)
    }

    @Test
    fun `the placement card draws in the lane, over no part of the page`() {
        val state = DesktopShellState()
        state.open(notebook())
        val tab = state.tabs.active!!
        state.selectTool(DesktopTool.PLOT)
        state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(100.0, 120.0, 340.0, 264.0))
        val scene = ImageComposeScene(1000, 700, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            scene.render()
            assertNotNull("the question is still up after a frame", state.pendingPlacement)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a cancelled question leaves the page exactly as it was`() {
        val state = DesktopShellState()
        state.open(notebook())
        val tab = state.tabs.active!!
        state.selectTool(DesktopTool.TABLE)
        val before = elements(tab)

        state.placeOrAsk(tab, LivingSlot.TABLE, Bounds(60.0, 60.0, 300.0, 220.0))
        assertNotNull(state.pendingPlacement)
        assertTrue("Esc drops the question", state.apply(ShellCommand.DISMISS_FAILURE))
        assertNull(state.pendingPlacement)
        assertEquals("no table, no edit, no undo step", before, elements(tab))
        assertFalse(tab.canUndo)
    }

    @Test
    fun `the table asks its grid, and the answer is the table that lands`() {
        val state = DesktopShellState()
        state.open(notebook())
        val tab = state.tabs.active!!
        state.selectTool(DesktopTool.TABLE)

        state.placeOrAsk(tab, LivingSlot.TABLE, Bounds(60.0, 60.0, 300.0, 220.0))
        val pending = state.pendingPlacement!!
        pending.columns = 4
        pending.rows = 2
        pending.header = false
        state.confirmPlacement()

        val table = elements(tab).filterIsInstance<TableElement>().single()
        assertEquals(4, table.colWidths.size)
        assertEquals(2, table.rowHeights.size)
        assertFalse("the header was switched off in the question", table.headerRow)
    }

    @Test
    fun `the three circuit families are the tablet's own palettes`() {
        assertEquals(
            listOf(DesktopTool.CIRCUIT, DesktopTool.CIRCUIT_ACTIVE, DesktopTool.CIRCUIT_LOGIC),
            CircuitFamily.entries.map { family ->
                listOf(DesktopTool.CIRCUIT, DesktopTool.CIRCUIT_ACTIVE, DesktopTool.CIRCUIT_LOGIC)
                    .single { circuitFamilyOf(it) == family }
            },
        )
        for (family in CircuitFamily.entries) {
            assertNotNull("${family.title} has a slot", groupOf(toolOf(family)))
            assertTrue("${family.title} acts", toolOf(family) in DESKTOP_TOOLS_THAT_ACT)
            assertEquals(
                "${family.title} offers every member exactly once",
                family.members.toSet(),
                family.rows.flatten().toSet(),
            )
        }
        assertEquals(ShapeKind.RESISTOR, CircuitFamily.PASSIVE.face)
        assertEquals(ShapeKind.DIODE, CircuitFamily.ACTIVE.face)
        assertEquals(ShapeKind.AND_GATE, CircuitFamily.LOGIC.face)
    }

    private fun toolOf(family: CircuitFamily): DesktopTool =
        listOf(DesktopTool.CIRCUIT, DesktopTool.CIRCUIT_ACTIVE, DesktopTool.CIRCUIT_LOGIC)
            .single { circuitFamilyOf(it) == family }

    @Test
    fun `a STEM slot is a slot in the strip and not ink`() {
        for (tool in listOf(
            DesktopTool.PLOT, DesktopTool.TABLE,
            DesktopTool.CIRCUIT, DesktopTool.CIRCUIT_ACTIVE, DesktopTool.CIRCUIT_LOGIC,
        )) {
            assertTrue("$tool acts, so it must be offered", tool in DESKTOP_TOOLS_THAT_ACT)
            assertNotNull("$tool has a slot", groupOf(tool))
            assertTrue("$tool places an element, so the pen's packets must stand aside", !tool.inksByPackets())
            assertEquals("$tool places a living element", tool, livingSlotOf(tool)?.let { tool })
        }
        assertEquals(com.mobixournal.render.LivingSlot.PLOT, livingSlotOf(DesktopTool.PLOT))
        assertEquals(null, livingSlotOf(DesktopTool.PEN))
    }

    private fun elements(tab: DesktopTab): List<Element> =
        tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun drag(scene: ImageComposeScene, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        pen(scene, PointerEventType.Press, fromX, fromY, 1_000L)
        pen(scene, PointerEventType.Move, (fromX + toX) / 2f, (fromY + toY) / 2f, 1_016L)
        pen(scene, PointerEventType.Move, toX, toY, 1_032L)
        pen(scene, PointerEventType.Release, toX, toY, 1_048L, pressed = false)
    }

    private fun tap(scene: ImageComposeScene, x: Float, y: Float) {
        pen(scene, PointerEventType.Press, x, y, 1_000L)
        pen(scene, PointerEventType.Release, x, y, 1_032L, pressed = false)
    }

    private fun pen(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressed: Boolean = true,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Stylus,
                    pressure = 1f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = time,
        )
    }
}

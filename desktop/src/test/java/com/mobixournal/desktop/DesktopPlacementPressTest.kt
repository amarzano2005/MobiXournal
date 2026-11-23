package com.mobixournal.desktop

import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.io.DesktopRecovery
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.confirmPlacement
import com.mobixournal.desktop.shell.placeOrAsk
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.selectTool

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.PageSelection
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.rectRegion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **placement press**, driven as the gesture a hand makes: a figure slot on a figure it drew
 * selects that figure instead of stacking a second one on top, paper that holds none still draws the next
 * one, and a tap on empty paper only lets go of the selection.
 *
 * The rule itself is `PlacementPickTest`'s (pure page geometry); this file answers the half a person sees —
 * what the sheet writes and what stays selected when a press lands with the slot still in hand.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopPlacementPressTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 595
    private val height = 700

    /** A rectangle at [left]/[top], minted the way the Rectangle slot mints it. */
    private fun figure(left: Double = 100.0, top: Double = 100.0): Stroke = shapeStroke(
        ShapeBuilder.build(ShapeKind.RECTANGLE, left, top, left + 120.0, top + 80.0, 2.0),
        0xFF000000.toInt(),
        2f,
        kind = ShapeKind.RECTANGLE,
    )!!

    private fun tab(elements: List<Element>, selectLast: Boolean = false): DesktopTab {
        val document = DesktopDocuments.blank(File(temp.root, "placement-${elements.size}-$selectLast.xopp"))
        document.editPage(0) { page -> page.copy(layers = listOf(Layer(elements))) }
        val tab = DesktopTabs().open(document)
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        if (selectLast) {
            tab.selection = PageSelection(
                region = rectRegion(98.0, 98.0, 222.0, 182.0),
                refs = setOf(ElementRef(0, elements.lastIndex)),
            )
        }
        return tab
    }

    /** A shell whose settings and recovery live in the test's own folder, so nothing touches the reader's. */
    private fun state(): DesktopShellState = DesktopShellState(
        settings = DesktopSettings(File(temp.root, "placement-shell.properties")),
        recovery = DesktopRecovery(temp.newFolder()),
    )

    private fun scene(
        tab: DesktopTab,
        tool: DesktopTool,
        onEditLiving: (() -> Unit)? = null,
    ): ImageComposeScene = ImageComposeScene(width, height, Density(1f)) {
        PageCanvas(
            tab = tab,
            panMode = false,
            tool = tool,
            onViewport = {},
            onFit = {},
            onEditLiving = onEditLiving,
        )
    }

    @Test
    fun `a press on a rectangle picks it instead of drawing a second one`() {
        val tab = tab(listOf(figure()))
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 160f, 100f, 1_000L)
            pointer(scene, PointerEventType.Move, 180f, 120f, 1_016L)
            pointer(scene, PointerEventType.Move, 200f, 140f, 1_032L)
            pointer(scene, PointerEventType.Release, 200f, 140f, 1_048L, pressed = false)

            val page = tab.document.page(0)
            assertEquals("no second figure was written", 1, page.layers.sumOf { it.elements.size })
            assertEquals(
                "and the press selected the figure it landed on",
                setOf(ElementRef(0, 0)),
                tab.selection?.refs,
            )
            val moved = page.layers[0].elements[0] as Stroke
            assertEquals("the same press carried it", 140.0, moved.points.first().x, 1.0)
            assertEquals(140.0, moved.points.first().y, 1.0)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a press on empty paper still draws the next figure`() {
        val tab = tab(listOf(figure()))
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 100f, 300f, 1_000L)
            pointer(scene, PointerEventType.Move, 200f, 380f, 1_016L)
            pointer(scene, PointerEventType.Release, 200f, 380f, 1_032L, pressed = false)

            assertEquals("a second figure landed", 2, tab.document.page(0).layers.sumOf { it.elements.size })
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a tap on empty paper only lets go of the selection`() {
        val tab = tab(listOf(figure()), selectLast = true)
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 420f, 620f, 1_000L)
            pointer(scene, PointerEventType.Release, 421f, 621f, 1_016L, pressed = false)

            assertNull("the selection was let go", tab.selection)
            assertEquals("and the tap wrote nothing", 1, tab.document.page(0).layers.sumOf { it.elements.size })
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * The tablet's own rule, on the window: drawing a figure **leaves its slot in hand**, so a row of boxes
     * is one gesture. The figure is selected on the lift (`commitShape`), and the slot's press is what picks
     * it up again (`placementPress`), which is why keeping the slot costs nothing.
     */
    @Test
    fun `a drawn figure leaves its slot in hand`() {
        val state = state()
        state.start(initial = null)
        state.newDocument()
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        state.selectTool(DesktopTool.RECTANGLE)
        val scene = ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = state.tools.current, onViewport = {}, onFit = {})
        }
        try {
            pointer(scene, PointerEventType.Press, 100f, 250f, 1_000L)
            pointer(scene, PointerEventType.Move, 200f, 320f, 2_016L)
            pointer(scene, PointerEventType.Release, 200f, 320f, 2_032L, pressed = false)

            assertEquals(
                "the figure landed",
                1,
                tab.document.page(tab.page).layers.sumOf { it.elements.size },
            )
            assertEquals("and its slot is still in hand", DesktopTool.RECTANGLE, state.tools.current)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * The two living slots the tablet's own pick does not cover: a press with the Plot or Formula slot on what
     * it just placed **selects and carries it**. The window keeps every slot in hand, so without this the click
     * that meant to select stacked a second plot or formula on the first.
     */
    @Test
    fun `a press on a plot picks it instead of placing a second one`() {
        val state = state()
        state.start(initial = null)
        state.newDocument()
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        state.selectTool(DesktopTool.PLOT)
        state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(100.0, 200.0, 400.0, 360.0))
        state.confirmPlacement()
        val before = (tab.document.page(tab.page).layers.flatMap { it.elements }.single() as PlotElement).left
        val scene = scene(tab, state.tools.current)
        try {
            pointer(scene, PointerEventType.Press, 200f, 300f, 1_000L)
            pointer(scene, PointerEventType.Move, 230f, 320f, 1_016L)
            pointer(scene, PointerEventType.Release, 230f, 320f, 1_032L, pressed = false)

            val drawn = tab.document.page(tab.page).layers.flatMap { it.elements }
            assertEquals("no second plot was placed", 1, drawn.size)
            assertEquals("the press selected the plot", setOf(ElementRef(0, 0)), tab.selection?.refs)
            assertTrue("and carried it", (drawn.single() as PlotElement).left > before + 1.0)
            assertEquals("with the slot still in hand", DesktopTool.PLOT, state.tools.current)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a press on a formula picks it instead of placing a second one`() {
        val state = state()
        state.start(initial = null)
        state.newDocument()
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        state.selectTool(DesktopTool.MATH)
        state.placeOrAsk(tab, LivingSlot.MATH, Bounds(100.0, 200.0, 400.0, 260.0))
        state.confirmPlacement()
        val scene = scene(tab, state.tools.current)
        try {
            pointer(scene, PointerEventType.Press, 160f, 230f, 1_000L)
            pointer(scene, PointerEventType.Release, 161f, 231f, 1_016L, pressed = false)

            val drawn = tab.document.page(tab.page).layers.flatMap { it.elements }
            assertEquals("no second formula was placed", 1, drawn.size)
            assertEquals(
                "the press selected the formula",
                setOf(ElementRef(0, 0)),
                tab.selection?.refs,
            )
            assertTrue("and it is the formula", drawn.single() is MathBlockElement)
            assertEquals("with the slot still in hand", DesktopTool.MATH, state.tools.current)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * A plot's **editor**, with its own slot still in hand: the second click of the pair has to reach the
     * canvas' tracker and the canvas' editor through the pick. A tracker made under each press forgot the
     * first click, so two clicks read as two moves and the lane never opened while the slot was in hand.
     */
    @Test
    fun `a double click on a plot with its slot in hand still opens the editor`() {
        var edits = 0
        val state = state()
        state.start(initial = null)
        state.newDocument()
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        state.selectTool(DesktopTool.PLOT)
        state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(100.0, 200.0, 400.0, 360.0))
        state.confirmPlacement()
        val scene = scene(tab, state.tools.current) { edits++ }
        try {
            clicks(scene, 200f, 300f)
            assertEquals("the first click opens nothing", 0, edits)
            clicks(scene, 200f, 300f)
            assertEquals("the second click opens the plot's editor", 1, edits)
            assertEquals(
                "and no second plot was placed",
                1,
                tab.document.page(tab.page).layers.sumOf { it.elements.size },
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /** The same for a **formula**, whose slot is the other one the pick now claims. */
    @Test
    fun `a double click on a formula with its slot in hand still opens the editor`() {
        var edits = 0
        val state = state()
        state.start(initial = null)
        state.newDocument()
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        state.selectTool(DesktopTool.MATH)
        state.placeOrAsk(tab, LivingSlot.MATH, Bounds(100.0, 200.0, 400.0, 260.0))
        state.confirmPlacement()
        val scene = scene(tab, state.tools.current) { edits++ }
        try {
            clicks(scene, 160f, 230f)
            assertEquals("the first click opens nothing", 0, edits)
            clicks(scene, 160f, 230f)
            assertEquals("the second click opens the formula's editor", 1, edits)
            assertEquals(
                "and no second formula was placed",
                1,
                tab.document.page(tab.page).layers.sumOf { it.elements.size },
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `switching away from a figure tool clears its selection`() {
        val state = state()
        state.start(initial = null)
        state.newDocument()
        val tab = state.tabs.active!!
        try {
            state.selectTool(DesktopTool.RECTANGLE)
            tab.selection = PageSelection(
                region = rectRegion(100.0, 100.0, 200.0, 180.0),
                refs = emptySet(),
            )
            state.selectTool(DesktopTool.RECTANGLE)
            assertTrue("re-picking the same figure keeps selection", tab.selection != null)

            state.selectTool(DesktopTool.ELLIPSE)
            assertEquals("switching to another figure clears selection", null, tab.selection)

            state.selectTool(DesktopTool.RECTANGLE)
            tab.selection = PageSelection(
                region = rectRegion(100.0, 100.0, 200.0, 180.0),
                refs = emptySet(),
            )
            state.selectTool(DesktopTool.MARQUEE)
            assertEquals("switching from a figure to marquee clears selection", null, tab.selection)
        } finally {
            tab.document.close()
        }
    }

    /** One click at ([x], [y]): the press and its lift in the same place, as a hand's click is. */
    private fun clicks(scene: ImageComposeScene, x: Float, y: Float) {
        pointer(scene, PointerEventType.Press, x, y, 1_000L)
        pointer(scene, PointerEventType.Release, x, y, 1_016L, pressed = false)
    }

    private fun pointer(
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
                    type = PointerType.Mouse,
                    pressure = 1f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = time,
        )
    }
}

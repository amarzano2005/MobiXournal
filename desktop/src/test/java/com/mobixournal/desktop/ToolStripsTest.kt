package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import com.mobixournal.design.DesignTokens
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The tool strips, their placement setting and the keyboard that picks a tool.
 *
 * Three claims, each one that could quietly stop being true:
 *
 *  1. **One tool list.** The rail and the ribbon draw [DesktopTool], so both offer the documented tools,
 *     once each, in the documented order, with the shortcuts the doc's table prints — asserted against
 *     the table's own text rather than against the enum's, so a typo in one is a failing test.
 *  2. **The placement is a setting, not a state.** It defaults to the rail, survives a restart through
 *     [DesktopSettings], and an unknown or unreadable file falls back to the default instead of stopping
 *     the window from opening.
 *  3. **A floating bar over the page.** The headless renders put a zoomed document on screen in all
 *     three placements: the sheet reaches the window's edges — nothing is taken out of the canvas for a
 *     lane — and a pen drag begun on a capsule leaves the page's ink alone. The PNGs are kept under
 *     `build/desktop-render/` so the strips can be looked at.
 */
class ToolStripsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** The tool table of `docs/ui-modernization.md` §3, as the doc spells it. */
    private val documented = listOf(
        "Pen" to "B",
        "Highlighter" to "H",
        "Hand" to "G",
        "Eraser" to "E",
        "Eraser (whole)" to "Shift+E",
        "Vertical space" to "Shift+V",
        "Lasso" to "V",
        "Marquee" to "S",
        "Text" to "X",
        // The tablet's Play object, between the Text group and the figures, as the strip offers it.
        "Audio tap" to "Y",
        "Formula" to "Shift+T",
        "Line" to "L",
        "Arrow" to "A",
        "Double arrow" to "Shift+A",
        "Rectangle" to "R",
        "Ellipse" to "C",
        // The tablet's STEM figures, which the window gained: a triangle and a trapezoid draw the
        // variant Settings ▸ Figures names, the three regular polygons are one drag each.
        "Triangle" to "Alt+R",
        "Rhombus" to "Alt+J",
        "Trapezoid" to "Alt+Z",
        "Pentagon" to "Alt+P",
        "Hexagon" to "Alt+H",
        "Spline" to "P",
        "Plot" to "M",
        "Axes" to "Shift+X",
        "Table" to "T",
        "Passive circuits" to "K",
        "Active circuits" to "Shift+K",
        "Logic gates" to "Alt+K",
    )

    @Test
    fun `the strips offer the documented tools, once each, in order`() {
        assertEquals(documented, DesktopTool.entries.map { it.label to it.shortcut })
        assertEquals(
            "no tool is listed twice",
            DesktopTool.entries.size,
            DesktopTool.entries.map { it.label }.toSet().size,
        )
    }

    @Test
    fun `every shortcut is unique and names its own tool`() {
        val shortcuts = DesktopTool.entries.map { it.shortcut }
        assertEquals("two tools share a shortcut", shortcuts.size, shortcuts.toSet().size)
        for (tool in DesktopTool.entries) {
            assertSame("${tool.shortcut} must name ${tool.label}", tool, toolForShortcut(tool.shortcut))
        }
        assertNull(toolForShortcut("Q"))
        assertNull(toolForShortcut("Shift+Q"))
    }

    /**
     * The keyboard and the pill read the same strings: each tool is reachable by pressing the shortcut
     * it prints, and `Ctrl` never steals a letter from the shell's own commands.
     */
    @Test
    fun `the keyboard picks every tool by the shortcut it prints`() {
        for (tool in DesktopTool.entries) {
            val alt = tool.shortcut.startsWith("Alt+")
            val shift = tool.shortcut.startsWith("Shift+")
            val letter = tool.shortcut.removePrefix("Alt+").removePrefix("Shift+")
            val key = ShortcutTable.keyFor(letter) ?: error("no key prints $letter")
            assertSame(tool.label, tool, toolForKey(key, shift = shift, ctrl = false, alt = alt))
        }
        assertEquals("a bare X is the text box", DesktopTool.TEXT, toolForKey(Key.X, shift = false, ctrl = false))
        assertEquals("Shift+X is the axes tool", DesktopTool.AXES, toolForKey(Key.X, shift = true, ctrl = false))
        assertEquals("Alt+K is the logic-gate palette", DesktopTool.CIRCUIT_LOGIC, toolForKey(Key.K, false, false, alt = true))
        assertNull("Ctrl+B stays the sidebar", toolForKey(Key.B, shift = false, ctrl = true))
        assertNull("a key no tool claims picks nothing", toolForKey(Key.Q, shift = false, ctrl = false))
    }

    @Test
    fun `picking a tool is a value change, not a mutation`() {
        val start = ToolSelection()
        assertEquals(DesktopTool.PEN, start.current)
        assertSame("picking the picked tool is a no-op", start, start.select(DesktopTool.PEN))

        val table = start.select(DesktopTool.TABLE)
        assertEquals(DesktopTool.TABLE, table.current)
        assertEquals("the old selection is untouched", DesktopTool.PEN, start.current)
    }

    /**
     * The picker belongs to the shell, so the chevron and `Alt + Down` open the *same* one: this walks
     * the state those two paths share — open, pick a row, close — without a window in the way.
     */
    @Test
    fun `the group picker opens, re-faces the slot and closes`() {
        val state = DesktopShellState(DesktopSettings(File(temp.root, "groups.properties")))
        val erasers = DESKTOP_TOOL_GROUPS.indexOfFirst { DesktopTool.ERASER in it.members }
        assertNull("nothing is open at rest", state.openGroup)

        state.selectTool(DesktopTool.ERASER)
        state.toggleToolPicker()
        assertEquals("Alt+Down opens the picked tool's group", erasers, state.openGroup)

        // A row of the picker is a pick: the slot re-faces and the picker has done its job.
        state.selectTool(DesktopTool.ERASER_WHOLE)
        assertNull("picking a member closes the picker", state.openGroup)
        assertEquals(
            "the slot now draws the member that was picked",
            DesktopTool.ERASER_WHOLE,
            DESKTOP_TOOL_GROUPS[erasers].faceFor(state.tools),
        )

        state.selectTool(DesktopTool.PEN)
        state.toggleToolPicker()
        assertNull("the pen has nothing to pick from", state.openGroup)
    }

    /**
     * The slot's picture *is* the picked member's: both faces are catalogue glyphs, and the strip has to
     * take the group's answer rather than always drawing the first member.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the eraser slot draws whichever member is picked`() {
        fun slotPixels(selection: DesktopTool): java.awt.image.BufferedImage {
            val scene = ImageComposeScene(ToolStripMetrics.railWidth.value.toInt(), 700, Density(1f)) {
                ToolRail(ToolStripState(selection = ToolSelection(selection)), onSelect = {})
            }
            return try {
                Snapshot.sendApplyNotifications()
                ImageIO.read(ByteArrayInputStream(scene.render().encodeToData()!!.bytes))
            } finally {
                scene.close()
            }
        }

        val group = DESKTOP_TOOL_GROUPS.indexOfFirst { DesktopTool.ERASER in it.members }
        val x0 = ToolStripMetrics.railSlotLeft.value.toInt()
        val y0 = ToolStripMetrics.railSlotTop.value.toInt() + group * slotStride
        val slot = ToolStripMetrics.slotSize.value.toInt()
        val partial = slotPixels(DesktopTool.ERASER)
        val whole = slotPixels(DesktopTool.ERASER_WHOLE)
        var different = 0
        for (x in x0 until x0 + slot) {
            for (y in y0 until y0 + slot) {
                if (difference(partial.getRGB(x, y) and 0xFFFFFF, whole.getRGB(x, y) and 0xFFFFFF) > 24) {
                    different++
                }
            }
        }
        assertTrue("the slot drew the same glyph for both erasers", different > 20)
    }

    @Test
    fun `the placement defaults to the left rail and survives a restart`() {
        val file = File(temp.root, "desktop.properties")
        assertEquals(StripPlacement.LEFT, DesktopSettings(file).stripPlacement)
        assertTrue(StripPlacement.LEFT.showsRail)
        assertFalse("the rail is not the ribbon", StripPlacement.LEFT.showsRibbon)

        DesktopSettings(file).stripPlacement = StripPlacement.TOP
        assertEquals(StripPlacement.TOP, DesktopSettings(file).stripPlacement)
        assertFalse("the ribbon alone leaves no rail", StripPlacement.TOP.showsRail)
        assertTrue("and it is the ribbon", StripPlacement.TOP.showsRibbon)
        assertEquals("a ribbon has no side", null, StripPlacement.TOP.railSide)

        // The edge is one of the three answers now, not a second question: Right is the right-hand rail.
        DesktopSettings(file).stripPlacement = StripPlacement.RIGHT
        assertEquals(StripPlacement.RIGHT, DesktopSettings(file).stripPlacement)
        assertEquals(RailSide.RIGHT, DesktopSettings(file).stripPlacement.railSide)
    }

    @Test
    fun `an unknown or unreadable file falls back to the default`() {
        assertEquals(StripPlacement.LEFT, StripPlacement.of("Diagonal"))
        assertEquals(StripPlacement.LEFT, StripPlacement.of(null))
        assertEquals(StripPlacement.TOP, StripPlacement.of("both"))
        // The names the file held before the row merged still answer, so an install that had already
        // picked one does not find its strips moved.
        assertEquals(StripPlacement.LEFT, StripPlacement.of("Rail"))
        assertEquals(StripPlacement.TOP, StripPlacement.of("Ribbon"))

        val garbage = File(temp.root, "garbage.properties")
        garbage.writeText("interface.toolPlacement=\\uZZZZ")
        assertEquals(
            "a hand-edited file must not stop the window opening",
            StripPlacement.LEFT,
            DesktopSettings(garbage).stripPlacement,
        )
        DesktopSettings(garbage).stripPlacement = StripPlacement.RIGHT
        assertEquals(StripPlacement.RIGHT, DesktopSettings(garbage).stripPlacement)
    }

    /** Both placements draw every slot's glyph, and both mark the picked slot with the steel. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the rail and the ribbon draw every slot`() {
        val railPng = save(
            // Tall enough for every slot: the figure submenu is one more slot than the strip had. In a
            // shorter window the rail scrolls (`verticalScroll` in `ToolRail`), which this does not test.
            ImageComposeScene(ToolStripMetrics.railWidth.value.toInt(), 1000, Density(1f)) {
                ToolRail(ToolStripState(), onSelect = {})
            },
            "tools-rail",
        )
        assertEverySlotInked(railPng, vertical = true)
        val rail = ImageIO.read(ByteArrayInputStream(railPng))
        assertEquals(
            "the rail marks the picked tool with the active slot",
            activeSlotArgb,
            rail.getRGB(
                (ToolStripMetrics.railSlotLeft + ToolStripMetrics.slotSize / 2f).value.toInt(),
                ToolStripMetrics.railSlotTop.value.toInt() + 2,
            ) and 0xFFFFFF,
        )

        val ribbonPng = save(
            // Wide enough for every slot: the figure submenu added one, and the ribbon is a row.
            ImageComposeScene(1100, ToolStripMetrics.ribbonHeight.value.toInt(), Density(1f)) {
                ToolRibbon(
                    state = ToolStripState(selection = ToolSelection(DesktopTool.SPLINE)),
                    onSelect = {},
                )
            },
            "tools-ribbon",
        )
        assertEverySlotInked(ribbonPng, vertical = false)
        val ribbon = ImageIO.read(ByteArrayInputStream(ribbonPng))
        // The slot that faces Spline is the figures group's — not the last slot, which is the Circuit
        // slot since the STEM slots joined the strip.
        val figures = DESKTOP_TOOL_GROUPS.indexOfFirst { DesktopTool.SPLINE in it.members }
        val pickedSlotX = ToolStripMetrics.ribbonSlotLeft.value.toInt() + (figures * slotStride) + 2
        assertEquals(
            "the ribbon marks the picked tool with the active slot",
            activeSlotArgb,
            ribbon.getRGB(pickedSlotX, ribbon.height / 2) and 0xFFFFFF,
        )
    }

    /**
     * The rail's **capsule is a bar**, not a pane as tall as the window: it ends under the last of its slots
     * and its own band shows below it.
     *
     * The band runs the whole edge — that is a lane, on the window as on the tablet — so what is measured is
     * the capsule's own span: the rows carrying its nearly-solid fill rather than the band's lighter one.
     */
    @Test
    fun `the rail's capsule ends under its last slot`() {
        val lane = ToolStripMetrics.railWidth.value.toInt()
        val png = save(
            ImageComposeScene(lane, 1400, Density(1f)) { ToolRail(ToolStripState(), onSelect = {}) },
            "tools-rail-tall",
        )
        val image = ImageIO.read(ByteArrayInputStream(png))
        fun alphaAt(y: Int) = image.getRGB(lane / 2, y) ushr 24
        // The capsule's fill, read in the 4 dp of its top padding — above slot 0, inside the capsule.
        val capsuleAlpha = alphaAt(ToolStripMetrics.railSlotTop.value.toInt() - 2)
        val bottom = (0 until image.height).lastOrNull { alphaAt(it) == capsuleAlpha }
            ?: error("the rail's capsule drew nothing")
        assertTrue(
            "the capsule is as tall as the window ($bottom of ${image.height}), not as tall as its tools",
            bottom < image.height - 200,
        )
    }

    /**
     * The strips **take a lane**, and a press that lands on their capsule is the strip's, not a stroke
     * underneath.
     *
     * The document is zoomed past the window on purpose: that is the case a lane has to hold, because a
     * sheet larger than its canvas fills it to both edges — so the pixels just inside the lane are the
     * band's tone and not paper, which is the opposite of what the floating layout gave. The drag is a
     * pen's and starts in the air between two slots — inside the capsule, outside every button — which is
     * where an unblocked drag would ink the page. The page's elements must be untouched after it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the strips take a lane and swallow their own presses`() {
        val document = temp.newFile("floating.mobix").also {
            SyntheticNotebook.write(it, SyntheticNotebook.pages(6))
        }
        val windowW = 1000
        val windowH = 700
        val header = DesktopTheme.headerHeight.value.toInt()
        val lane = ToolStripMetrics.railWidth.value.toInt()
        val slot = ToolStripMetrics.slotSize.value.toInt()

        for (placement in StripPlacement.entries) {
            val settings = DesktopSettings(File(temp.root, "float-${placement.name}.properties"))
            val state = DesktopShellState(settings)
            state.open(document)
            // The page list would take the outer edge; this measures the strip, not the sidebar.
            state.toggleSidebar()
            state.prefs.chooseStripPlacement(placement)
            assertEquals("the choice was written to the settings file", placement, settings.stripPlacement)
            val tab = state.tabs.active!!
            tab.fit = FitMode.NONE
            tab.viewport = Viewport(zoom = 2f, offsetX = 0f, offsetY = 0f)
            state.selectTool(DesktopTool.PEN)
            val before = tab.document.page(tab.page).layers.sumOf { it.elements.size }

            // The point the drag starts from: the air between the first two slots of whichever strip is
            // on screen — inside the capsule, over no button.
            val x: Int
            val y: Int
            when {
                placement.showsRail -> {
                    x = if (placement.railSide == RailSide.LEFT) lane / 2 else windowW - lane / 2
                    y = header + ToolStripMetrics.railSlotTop.value.toInt() + slot + gapPx / 2
                }
                else -> {
                    x = ToolStripMetrics.ribbonSlotLeft.value.toInt() + slot + gapPx / 2
                    y = header + ToolStripMetrics.ribbonSlotTop.value.toInt() + slot / 2
                }
            }

            val scene = ImageComposeScene(windowW, windowH, Density(1f)) {
                DesktopShell(state = state, onOpenPicker = {})
            }
            val image = try {
                Snapshot.sendApplyNotifications()
                val png = scene.render().encodeToData()!!.bytes
                val name = "tools-shell-${placement.name.lowercase()}"
                File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png)
                // A pen **tap** on the strip's own glass, then a drag across it and one down it: a tap
                // is the one gesture no scroller answers, so a surface that is not opaque to the pointer
                // leaves a dot on the page under it, and one direction of the two drags is the strip's
                // own scroller's and the other nobody's.
                pointer(scene, PointerEventType.Press, x.toFloat(), y.toFloat(), pressed = true, at = 900L)
                pointer(scene, PointerEventType.Release, x.toFloat(), y.toFloat(), pressed = false, at = 950L)
                for ((index, move) in listOf(200 to 0, 0 to 200).withIndex()) {
                    val at = 1_000L + index * 200L
                    pointer(scene, PointerEventType.Press, x.toFloat(), y.toFloat(), pressed = true, at = at)
                    pointer(
                        scene = scene,
                        type = PointerEventType.Move,
                        x = (x + move.first).toFloat(),
                        y = (y + move.second).toFloat(),
                        pressed = true,
                        at = at + 50,
                    )
                    pointer(
                        scene = scene,
                        type = PointerEventType.Release,
                        x = (x + move.first).toFloat(),
                        y = (y + move.second).toFloat(),
                        pressed = false,
                        at = at + 100,
                    )
                }
                ImageIO.read(ByteArrayInputStream(png))
            } finally {
                scene.close()
            }

            // Near-white, not the desk: a sheet's own edge carries its hairline and a little shading,
            // so exact white would fail on a page that is plainly there.
            fun sheetAt(px: Int, py: Int) = (image.getRGB(px, py) and 0xFF) >= 0xD0
            val ribbon = ToolStripMetrics.ribbonHeight.value.toInt()
            // The lane's first pixels, and the sheet just past the board: the page is zoomed past the
            // window, so paper at the first of these would mean the strip lies over the sheet again.
            val (bandAt, paperAt) = when {
                placement.showsRail && placement.railSide == RailSide.LEFT ->
                    (2 to windowH / 2) to (lane + 8 to windowH / 2)
                placement.showsRail ->
                    (windowW - 3 to windowH / 2) to (windowW - lane - 8 to windowH / 2)
                else -> (windowW / 2 to header + 2) to (windowW / 2 to header + ribbon + 8)
            }
            assertTrue(
                "$placement: the sheet is under the strip's band — it is meant to take its own lane",
                !sheetAt(bandAt.first, bandAt.second),
            )
            assertEquals(
                "$placement: and the lane wears the window's band",
                DesktopTheme.bandRgb,
                image.getRGB(bandAt.first, bandAt.second) and 0xFFFFFF,
            )
            assertTrue(
                "$placement: the canvas begins past the lane, and the sheet is at $paperAt",
                sheetAt(paperAt.first, paperAt.second),
            )
            assertEquals(
                "$placement: a drag begun on the strip drew on the page under it",
                before,
                tab.document.page(tab.page).layers.sumOf { it.elements.size },
            )
            state.tabs.closeAll()
        }
    }

    /** One mouse event at ([x], [y]) in window pixels. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun pointer(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressed: Boolean,
        at: Long,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Mouse,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    // --- rendering ------------------------------------------------------------------------------

    private val PAPER = 0xFFFFFF

    private val gapPx: Int get() = ToolStripMetrics.gap.value.toInt()

    private val slotStride: Int get() = ToolStripMetrics.slotSize.value.toInt() + gapPx

    private val activeSlotArgb: Int get() = DesktopTheme.activeSlot.toArgb() and 0xFFFFFF

    /**
     * A slot under the pointer wears the steel at the hover step.
     *
     * The rail had no hover fill at all — only the tooltip — so a resting pointer got no answer about
     * which slot it was on, while the contract asks every interactive element to draw hover and picked
     * as two steps of one colour (`docs/interface-contract.md` §6). The pixel is compared against the
     * same slot in an unhovered render, so the test fails if the fill is dropped or painted the picked
     * steel by mistake.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a hovered slot shows the hover step, not the picked one`() {
        val width = ToolStripMetrics.railWidth.value.toInt()
        val slot = ToolStripMetrics.slotSize.value.toInt()
        val x = (ToolStripMetrics.railSlotLeft + ToolStripMetrics.slotSize / 2f).value.toInt()
        val y = ToolStripMetrics.railSlotTop.value.toInt() + slotStride + slot / 2

        fun render(hover: Boolean): java.awt.image.BufferedImage {
            val scene = ImageComposeScene(width, 700, Density(1f)) {
                ToolRail(ToolStripState(), onSelect = {})
            }
            return try {
                if (hover) scene.sendPointerEvent(PointerEventType.Enter, Offset(x.toFloat(), y.toFloat()))
                Snapshot.sendApplyNotifications()
                val bytes = scene.render().encodeToData()!!.bytes
                ImageIO.read(ByteArrayInputStream(bytes))
            } finally {
                scene.close()
            }
        }

        val probeX = ToolStripMetrics.railSlotLeft.value.toInt() + 3
        val probeY = ToolStripMetrics.railSlotTop.value.toInt() + slotStride + 3
        val rest = render(hover = false).getRGB(probeX, probeY) and 0xFFFFFF
        val hovered = render(hover = true).getRGB(probeX, probeY) and 0xFFFFFF
        assertTrue(
            "hovering a slot changed nothing (${"#%06X".format(rest)})",
            difference(rest, hovered) > 4,
        )
        assertTrue(
            "a hovered slot painted the picked steel instead of the hover step",
            difference(hovered, activeSlotArgb) > 4,
        )
    }

    /**
     * The glass composited over the desk at the **window's** alpha: `#0C0C0D`, one tone above black.
     *
     * It is not the tablet's `#131314`: the window's bands are deliberately blacker (2026-10-25,
     * `docs/ui-modernization.md`), and this is the number that decision lands on.
     */
    private val windowGlass: Int get() =
        DesktopTheme.bandRgb

    /**
     * Every slot must have drawn its glyph: the slot is sampled against its own background (the steel
     * for the picked one, the glass for the rest) and a slot with no ink would leave it uniform.
     */
    private fun assertEverySlotInked(png: ByteArray, vertical: Boolean) {
        val image = ImageIO.read(ByteArrayInputStream(png))
        val slot = ToolStripMetrics.slotSize.value.toInt()
        val centre = (if (vertical) ToolStripMetrics.railWidth.value.toInt() else image.height) / 2
        for (index in DESKTOP_TOOL_GROUPS.indices) {
            val x0 = if (vertical) {
                ToolStripMetrics.railSlotLeft.value.toInt()
            } else {
                ToolStripMetrics.ribbonSlotLeft.value.toInt() + index * slotStride
            }
            val y0 = if (vertical) {
                ToolStripMetrics.railSlotTop.value.toInt() + index * slotStride
            } else {
                ToolStripMetrics.ribbonSlotTop.value.toInt()
            }
            val background = image.getRGB(x0 + 2, y0 + 2) and 0xFFFFFF
            var ink = 0
            for (x in x0 until x0 + slot) {
                for (y in y0 until y0 + slot) {
                    if (difference(image.getRGB(x, y) and 0xFFFFFF, background) > 24) ink++
                }
            }
            assertTrue(
                "${DESKTOP_TOOL_GROUPS[index].face.label} drew nothing in the " +
                    "${if (vertical) "rail" else "ribbon"}" +
                    " (slot ${if (vertical) "at y = $y0, lane centre x = $centre" else "at x = $x0"})",
                ink > 20,
            )
        }
    }

    private fun difference(a: Int, b: Int): Int {
        fun channel(rgb: Int, shift: Int) = (rgb shr shift) and 0xFF
        return maxOf(
            kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)),
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun save(scene: ImageComposeScene, name: String): ByteArray {
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        requireNotNull(png) { "Skia produced no PNG for $name" }
        File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        return png
    }
}

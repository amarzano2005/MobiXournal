package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
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
 *  3. **A band, never a bar over the page.** The headless renders put a document on screen in all three
 *     placements and check where the paper actually starts: after the rail's lane, below the ribbon's
 *     band. The PNGs are kept under `build/desktop-render/` so the strips can be looked at.
 */
class ToolStripsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** The tool table of `docs/ui-modernization.md` §3, as the doc spells it. */
    private val documented = listOf(
        "Pen" to "B",
        "Highlighter" to "H",
        "Eraser" to "E",
        "Eraser (whole)" to "Shift+E",
        "Vertical space" to "Shift+V",
        "Lasso" to "V",
        "Marquee" to "S",
        "Text" to "X",
        "Formula" to "Shift+T",
        "Line" to "L",
        "Arrow" to "A",
        "Double arrow" to "Shift+A",
        "Rectangle" to "R",
        "Ellipse" to "C",
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
            val key = toolKeyLetters.entries.single { it.value == letter }.key
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
    fun `the placement defaults to the rail and survives a restart`() {
        val file = File(temp.root, "desktop.properties")
        assertEquals(ToolPlacement.RAIL, DesktopSettings(file).toolPlacement)
        assertTrue(ToolPlacement.RAIL.showsRail)

        DesktopSettings(file).toolPlacement = ToolPlacement.RIBBON
        assertEquals(ToolPlacement.RIBBON, DesktopSettings(file).toolPlacement)
        assertFalse("the ribbon alone leaves no rail", ToolPlacement.RIBBON.showsRail)
        assertTrue(ToolPlacement.BOTH.showsRail)
        assertTrue(ToolPlacement.BOTH.showsRibbon)
    }

    @Test
    fun `an unknown or unreadable file falls back to the default`() {
        assertEquals(ToolPlacement.RAIL, ToolPlacement.of("Diagonal"))
        assertEquals(ToolPlacement.RAIL, ToolPlacement.of(null))
        assertEquals(ToolPlacement.BOTH, ToolPlacement.of("both"))

        val garbage = File(temp.root, "garbage.properties")
        garbage.writeText("interface.toolPlacement=\\uZZZZ")
        assertEquals(
            "a hand-edited file must not stop the window opening",
            ToolPlacement.RAIL,
            DesktopSettings(garbage).toolPlacement,
        )
        DesktopSettings(garbage).toolPlacement = ToolPlacement.BOTH
        assertEquals(ToolPlacement.BOTH, DesktopSettings(garbage).toolPlacement)
    }

    /** Both placements draw every slot's glyph, and both mark the picked slot with the steel. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the rail and the ribbon draw every slot`() {
        val railPng = save(
            ImageComposeScene(ToolStripMetrics.railWidth.value.toInt(), 900, Density(1f)) {
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
            ImageComposeScene(1000, ToolStripMetrics.ribbonHeight.value.toInt(), Density(1f)) {
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
     * The structural claim of the whole task: whichever placement is chosen, the bands take their own
     * lanes and the page begins after them — a strip is never laid over the sheet.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the strips are lanes beside the page, never bands over it`() {
        val document = temp.newFile("strips.mobix").also {
            SyntheticNotebook.write(it, SyntheticNotebook.pages(6))
        }
        val header = DesktopTheme.headerHeight.value.toInt()
        val railLane = ToolStripMetrics.railWidth.value.toInt()
        val ribbonBand = ToolStripMetrics.ribbonHeight.value.toInt()

        for (placement in ToolPlacement.entries) {
            val settings = DesktopSettings(File(temp.root, "settings-${placement.name}.properties"))
            val state = DesktopShellState(settings)
            state.open(document)
            // The sidebar would sit between the window edge and the rail's lane, so this measurement
            // takes it out of the picture; what it measures is the strip, not the sidebar.
            state.toggleSidebar()
            state.chooseToolPlacement(placement)
            assertEquals("the choice was written to the settings file", placement, settings.toolPlacement)

            val scene = ImageComposeScene(1000, 700, Density(1f)) {
                DesktopShell(state = state, onOpenPicker = {})
            }
            val png = try {
                Snapshot.sendApplyNotifications()
                scene.render().encodeToData()?.bytes
            } finally {
                scene.close()
            }
            val name = "tools-shell-${placement.name.lowercase()}"
            java.io.File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png!!)
            val image = ImageIO.read(ByteArrayInputStream(png))

            fun paperAt(x: Int, y: Int) = (image.getRGB(x, y) and 0xFFFFFF) == PAPER
            // The longest **unbroken** run of paper along a line, not a count of paper pixels in it: the
            // tool glyphs are drawn in the chrome ink (pure white, as on the tablet), and twenty glyphs
            // summed along the ribbon's row add up to a page-wide-looking count. No glyph is forty pixels
            // wide, while the sheet is hundreds.
            fun longestPaperRun(lines: IntRange, at: (Int) -> Boolean): Int {
                var best = 0
                var run = 0
                for (index in lines) {
                    if (at(index)) {
                        run++
                        if (run > best) best = run
                    } else {
                        run = 0
                    }
                }
                return best
            }
            val minRun = 40
            val paperColumns = (0 until image.width).filter { x ->
                longestPaperRun(header until image.height) { y -> paperAt(x, y) } >= minRun
            }
            val paperRows = (header until image.height).filter { y ->
                longestPaperRun(0 until image.width) { x -> paperAt(x, y) } >= minRun
            }
            assertTrue("$placement: the page is on screen", paperColumns.isNotEmpty() && paperRows.isNotEmpty())
            val lane = if (placement.showsRail) railLane else 0
            assertTrue(
                "$placement: the page starts after the rail's lane (first paper column ${paperColumns.min()})",
                paperColumns.min() >= lane,
            )
            val band = header + if (placement.showsRibbon) ribbonBand else 0
            assertTrue(
                "$placement: the page starts below the ribbon's band (first paper row ${paperRows.min()})",
                paperRows.min() >= band,
            )
            if (placement.showsRail) {
                // One layer of glass, as on the tablet. A lane painted as a band as well put 0.85 over
                // 0.85 (0.98) and the rail came out #161618 instead of the tablet's #131314.
                // Under BOTH the rail's strip starts below the ribbon, so its top is the ribbon's band.
                val midGap = band + ToolStripMetrics.railSlotTop.value.toInt() +
                    ToolStripMetrics.slotSize.value.toInt() + gapPx / 2
                val capsule = image.getRGB(ToolStripMetrics.railWidth.value.toInt() / 2, midGap) and 0xFFFFFF
                assertTrue(
                    "$placement: the rail's fill is the tablet's glass, not a lane's band under it " +
                        "(sampled #%06X, expected #%06X)".format(capsule, tabletGlass),
                    difference(capsule, tabletGlass) <= 3,
                )
            }
            state.tabs.closeAll()
        }
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

    /** The glass composited over the desk, as the tablet's rail measures it: #131314. */
    private val tabletGlass: Int get() {
        fun channel(shift: Int) =
            (((DesignTokens.GLASS_DARK_RGB shr shift) and 0xFF) * DesignTokens.GLASS_DARK_ALPHA).toInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

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

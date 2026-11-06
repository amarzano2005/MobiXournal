package com.mobixournal.desktop

import com.mobixournal.desktop.settings.ShortcutTable
import com.mobixournal.desktop.shell.DesktopRing
import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.shell.shellCommand
import com.mobixournal.desktop.sheet.DesktopHud
import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.desktop.tools.DESKTOP_TOOLS_THAT_ACT
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Tool
import com.mobixournal.desktop.sheet.RingFace
import com.mobixournal.desktop.sheet.ringWidthDotDiameter
import com.mobixournal.render.RadialHud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's **pen-tip ring**, as state: what it holds, where a press commits and where it does not, and
 * which entry a slot runs.
 *
 * The geometry is `RadialHud`'s and is tested beside it in `mobix-core`; this suite is the window's half —
 * that its contents are the strip's own palette and widths with the three writing tools last, that every
 * commit is the entry the rail and the flyouts already use, and that the two ways of closing it (a picker's
 * press, a held ring's release) cannot commit each other's slot. No window and no scene is involved: the
 * ring is a value and four functions, which is why it can be checked this way at all.
 */
class DesktopRingTest {

    /** What a slot did, in the order it was committed. */
    private class Sink {
        val colours = mutableListOf<Int>()
        val widths = mutableListOf<Float>()
        val tools = mutableListOf<DesktopTool>()
    }

    private val density = 2f
    private val nib = Offset(300f, 400f)

    @Test
    fun `the three shipped widths draw three different dots`() {
        val smallest = ringWidthDotDiameter(PenPalette.DEFAULT_WIDTHS[0])
        val middle = ringWidthDotDiameter(PenPalette.DEFAULT_WIDTHS[1])
        val largest = ringWidthDotDiameter(PenPalette.DEFAULT_WIDTHS[2])
        assertEquals("S is six dp across", 6f, smallest, 1e-4f)
        assertEquals("L is fourteen dp across", 14f, largest, 1e-4f)
        // The middle slot must be visibly its own size, not a rounding of its neighbours: measuring
        // against the pointer's whole range put all three within two dp of each other.
        assertTrue("M sits well between S and L", middle > smallest + 2.5f && middle < largest - 2.5f)
    }

    private fun ring(
        palette: List<Int> = PenPalette.COLORS,
        custom: Int? = null,
        sink: Sink = Sink(),
    ): DesktopRing = DesktopRing(
        palette = { palette },
        custom = { custom },
        onColour = { sink.colours += it },
        onWidth = { sink.widths += it },
        onTool = { sink.tools += it },
    )

    /** The canvas's pixels for slot [index] of an open ring at [nib]. */
    private fun slotCentre(index: Int, count: Int): Offset {
        val (x, y) = RadialHud.slotCenter(
            nib.x, nib.y, index, count, RadialHud.RING_RADIUS_DP * density,
        )
        return Offset(x, y)
    }

    @Test
    fun `the ring holds five colours, then the widths, then the six tools`() {
        val slots = RingContents.Auto.slots(PenPalette.COLORS, null)
        assertEquals(
            RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size + RingContents.TOOLS.size,
            slots.size,
        )
        assertEquals("black is at the top", RingFace.Colour(PenPalette.COLORS.first()), slots.first())
        assertEquals(
            RingFace.Width(PenPalette.DEFAULT_WIDTHS.first()),
            slots[RadialHud.FACTORY_COLOUR_SLOTS],
        )
        assertEquals(
            "the figure is the first tool",
            RingFace.Tool(DesktopTool.LINE),
            slots[RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size],
        )
        assertEquals("and the pen closes the ring", RingFace.Tool(DesktopTool.PEN), slots.last())
    }

    @Test
    fun `the editable custom slot is the last of the five, and a long palette changes nothing`() {
        val custom = 0xFF123456.toInt()
        val short = RingContents.Auto.slots(PenPalette.COLORS, custom)
        assertEquals(
            "the custom colour takes the fifth place",
            RingFace.Colour(custom),
            short[RadialHud.FACTORY_COLOUR_SLOTS - 1],
        )

        val long = RingContents.Auto.slots((1..PenPalette.MAX_COLORS).map { 0xFF000000.toInt() or it }, custom)
        assertEquals("the factory ring whatever the palette holds", short.size, long.size)
        assertEquals(RingFace.Tool(DesktopTool.PEN), long.last())
    }

    /**
     * The strips' own rule, held for the ring: **a slot that does nothing is worse than an absent one**.
     * The six faces the factory ring offers are six tools the canvas acts on, so no member can be a promise.
     */
    @Test
    fun `every tool the ring offers is one the canvas acts on`() {
        assertEquals("the factory six", 6, RingContents.TOOLS.size)
        for (tool in RingContents.TOOLS) {
            assertTrue("$tool is offered by the ring but does not act", tool in DESKTOP_TOOLS_THAT_ACT)
        }
        assertEquals(
            setOf(
                DesktopTool.LINE,
                DesktopTool.LASSO,
                DesktopTool.MARQUEE,
                DesktopTool.HIGHLIGHTER,
                DesktopTool.ERASER,
                DesktopTool.PEN,
            ),
            RingContents.TOOLS.toSet(),
        )
        assertEquals("the tablet's order, ending on the pen", DesktopTool.PEN, RingContents.TOOLS.last())
    }

    @Test
    fun `the keyboard opens the ring where the nib is and a second press puts it away`() {
        val ring = ring()
        assertNull("nothing exists until it is summoned", ring.open)

        ring.notePointer(nib, density)
        assertTrue(ring.summon())
        val open = ring.open
        assertNotNull(open)
        assertEquals(nib.x, open!!.cx, 1e-4f)
        assertEquals(nib.y, open.cy, 1e-4f)

        assertTrue("the second press closes it", ring.summon())
        assertNull(ring.open)
    }

    /**
     * An empty palette is not an empty ring: the widths and the three tools are always there, so a document
     * whose swatches were all removed still has a working surface — the tablet's ring holds the same six.
     */
    @Test
    fun `an empty palette still leaves the widths and the tools`() {
        val ring = ring(palette = emptyList(), custom = null)
        assertEquals(
            PenPalette.DEFAULT_WIDTHS.size + RingContents.TOOLS.size,
            RingContents.Auto.slots(emptyList(), null).size,
        )
        assertTrue(ring.summon())
        assertNotNull(ring.open)
    }

    @Test
    fun `a press on a swatch commits that colour through the inks own entry`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        ring.summon()
        val count = ring.slots.size

        assertTrue(ring.press(slotCentre(2, count).x, slotCentre(2, count).y, density))
        assertEquals(listOf(PenPalette.COLORS[2]), sink.colours)
        assertNull("committing closes the ring", ring.open)
        assertTrue(sink.widths.isEmpty())
        assertTrue(sink.tools.isEmpty())
    }

    @Test
    fun `a press on a width or a tool runs the entry the strips use`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        val count = ring.slots.size

        ring.summon()
        val widthAt = slotCentre(RadialHud.FACTORY_COLOUR_SLOTS + 1, count)
        assertTrue(ring.press(widthAt.x, widthAt.y, density))
        assertEquals(listOf(PenPalette.DEFAULT_WIDTHS[1]), sink.widths)

        ring.summon()
        // The ring closes on the pen, so the eraser is the face before it.
        val toolAt = slotCentre(count - 2, count)
        assertTrue(ring.press(toolAt.x, toolAt.y, density))
        assertEquals(listOf(DesktopTool.ERASER), sink.tools)
        assertTrue(sink.colours.isEmpty())
    }

    @Test
    fun `a press in the dead zone commits nothing and leaves the ring up`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        ring.summon()

        assertTrue(ring.press(nib.x, nib.y, density))
        assertNotNull("a press on the nib is aimed at the ring, not through it", ring.open)
        assertTrue(sink.colours.isEmpty())
        assertTrue(sink.widths.isEmpty())
        assertTrue(sink.tools.isEmpty())
    }

    @Test
    fun `a press well outside the ring puts it away and commits nothing`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        ring.summon()

        assertTrue(ring.press(nib.x + 400f * density, nib.y, density))
        assertNull(ring.open)
        assertTrue(sink.colours.isEmpty())
    }

    @Test
    fun `a press with no ring open is the pages`() {
        val ring = ring()
        assertFalse("nothing is open, so the press is not the ring's", ring.press(nib.x, nib.y, density))
    }

    /**
     * A press the ring answered is **held until its lift**: the pen's packets commit and close the surface on
     * the frame thread, while the same contact still reaches the page as the mouse Windows emulates it as. After
     * the lift the next press is the page's again.
     */
    @Test
    fun `a press the ring answered is held until the contact lifts`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        ring.summon()
        val count = ring.slots.size
        assertFalse("nothing is held before a press is answered", ring.pressHeld)

        val at = slotCentre(0, count)
        assertTrue(ring.press(at.x, at.y, density))
        assertTrue("the contact is still the ring's after the surface has gone", ring.pressHeld)
        assertEquals(listOf(PenPalette.COLORS[0]), sink.colours)

        ring.release()
        assertFalse("its lift hands the page the next one", ring.pressHeld)
        assertFalse("a press with no ring open is the page's", ring.press(nib.x, nib.y, density))
    }

    @Test
    fun `the pick follows the pointer and a dismissal commits nothing`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        ring.summon()
        val count = ring.slots.size

        val at = slotCentre(4, count)
        ring.notePointer(at, density)
        assertEquals(4, ring.open?.active)

        assertTrue(ring.dismiss())
        assertNull(ring.open)
        assertFalse("nothing left to dismiss", ring.dismiss())
        assertTrue(sink.colours.isEmpty())
    }

    @Test
    fun `alt b is the ring's key and it is remappable`() {
        val table = ShortcutTable.defaults()
        assertEquals("Alt+B", table[ShellCommand.RADIAL_HUD.name])
        assertEquals(
            ShellCommand.RADIAL_HUD,
            shellCommand(table, Key.B, ctrl = false, shift = false, alt = true),
        )
        assertNull(
            "a bare B is still the pen",
            shellCommand(table, Key.B, ctrl = false, shift = false, alt = false),
        )
    }

    @Test
    fun `a ring given its own faces carries exactly those, in that order`() {
        val faces = listOf(
            RingFace.Colour(PenPalette.RED),
            RingFace.Tool(DesktopTool.HIGHLIGHTER),
            RingFace.Width(2.5f),
        )
        assertEquals(faces, RingContents.Fixed(faces).slots(PenPalette.COLORS, null))

        val auto = RingContents.Auto.slots(PenPalette.COLORS, null)
        assertEquals(
            "the automatic ring is the palette, the widths and the three tools",
            PenPalette.COLORS.size + PenPalette.DEFAULT_WIDTHS.size + 3,
            auto.size,
        )
        assertTrue(
            "a fixed ring with no faces offers nothing",
            RingContents.Fixed(emptyList()).slots(PenPalette.COLORS, null).isEmpty(),
        )
    }

    @Test
    fun `a ring with no faces does not open at all`() {
        val hollow = DesktopRing(
            palette = { PenPalette.COLORS },
            custom = { null },
            onColour = {},
            onWidth = {},
            onTool = {},
            contents = { RingContents.Fixed(emptyList()) },
        )
        assertFalse("the key has nothing to offer", hollow.summon())
        assertNull(hollow.open)
    }

    @Test
    fun `the faces survive the round trip through the stored line`() {
        val faces = listOf(
            RingFace.Colour(PenPalette.BLUE),
            RingFace.Width(1.25f),
            RingFace.Tool(DesktopTool.ERASER),
        )
        assertEquals(faces, RingContents.decode(RingContents.encode(faces)))
        assertNull("a line naming nothing this build reads is the automatic ring", RingContents.decode("nonsense"))
        assertNull(RingContents.decode(null))
    }

    /**
     * The Ring page's chooser is gated on **the pick**, not on the face it holds across its exit.
     *
     * `rememberClosingValue` keeps the last picked face so the closing spring has something to draw, and it
     * never clears. Reading it as the chooser's presence kept the menu up after an answer had dismissed it,
     * so every later answer landed on the face that had opened it — the ring appeared to replace the same
     * position every time. The gate is the pick, which is null the moment the answer is taken.
     */
    @Test
    fun `the ring page's chooser follows the pick, not the face it holds for the exit`() {
        val source = File(
            repoRoot(),
            "desktop/src/main/java/com/mobixournal/desktop/settings/RingSettings.kt",
        ).readText()
        assertTrue(
            "the chooser's presence must be the pick",
            source.contains("GlassPresence(picked != null)"),
        )
        assertFalse(
            "a held face outlives the dismissal, so it cannot be the presence flag",
            source.contains("GlassPresence(shown != null)"),
        )
    }

    /** The repository root, found the way the guard suites find it. */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }

}

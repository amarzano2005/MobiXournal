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
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
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
    fun `the ring holds the palette, then the widths, then the three writing tools`() {
        val slots = DesktopHud.slots(PenPalette.COLORS, null)
        assertEquals(PenPalette.COLORS.size + PenPalette.DEFAULT_WIDTHS.size + 3, slots.size)
        assertEquals(HudSlot.Colour(PenPalette.COLORS.first()), slots.first())
        assertEquals(
            HudSlot.Width(PenPalette.DEFAULT_WIDTHS.first()),
            slots[PenPalette.COLORS.size],
        )
        assertEquals(HudSlot.ToolFace(Tool.PEN), slots[slots.size - 3])
        assertEquals(HudSlot.ToolFace(Tool.ERASER), slots.last())
    }

    @Test
    fun `the editable custom slot is the last swatch, and a long palette still keeps the widths and tools`() {
        val custom = 0xFF123456.toInt()
        val short = DesktopHud.slots(PenPalette.COLORS, custom)
        assertEquals(HudSlot.Colour(custom), short[PenPalette.COLORS.size])

        val long = DesktopHud.slots((1..PenPalette.MAX_COLORS).map { 0xFF000000.toInt() or it }, custom)
        assertEquals(RadialHud.MAX_SLOTS, long.size)
        assertEquals("the custom slot keeps its place at the palette's cap", HudSlot.Colour(custom), long[9])
        assertEquals(HudSlot.ToolFace(Tool.ERASER), long.last())
    }

    /**
     * The strips' own rule, held for the ring: **a slot that does nothing is worse than an absent one**.
     * The three faces the ring offers are three tools the canvas acts on, so no member can be a promise.
     */
    @Test
    fun `every tool the ring offers is one the canvas acts on`() {
        assertEquals(3, DesktopHud.TOOLS.size)
        for (tool in DesktopHud.TOOLS) {
            assertTrue("$tool is offered by the ring but does not act", tool in DESKTOP_TOOLS_THAT_ACT)
        }
        assertEquals(
            setOf(DesktopTool.PEN, DesktopTool.HIGHLIGHTER, DesktopTool.ERASER),
            DesktopHud.TOOLS.toSet(),
        )
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
        assertTrue("a key-opened ring is a picker", open.picker)

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
            PenPalette.DEFAULT_WIDTHS.size + DesktopHud.TOOLS.size,
            DesktopHud.slots(emptyList(), null).size,
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
        val widthAt = slotCentre(PenPalette.COLORS.size + 1, count)
        assertTrue(ring.press(widthAt.x, widthAt.y, density))
        assertEquals(listOf(PenPalette.DEFAULT_WIDTHS[1]), sink.widths)

        ring.summon()
        val toolAt = slotCentre(count - 1, count)
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
     * A **picker** ignores a release: the release of the click that opened it is that click's own release,
     * and reading it as the pick is what made the tablet's ring flash open and shut.
     */
    @Test
    fun `a picker ignores the release of the click that opened it`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        ring.summon(picker = true)
        val count = ring.slots.size

        assertTrue(ring.release(slotCentre(1, count).x, slotCentre(1, count).y, density))
        assertNotNull(ring.open)
        assertTrue(sink.colours.isEmpty())
    }

    /** A **held** ring is the pen's: the release is its commit, and no slot under it means nothing runs. */
    @Test
    fun `a held ring commits on the release and nothing when it is let go over no slot`() {
        val sink = Sink()
        val ring = ring(sink = sink)
        ring.notePointer(nib, density)
        val count = ring.slots.size

        ring.summon(picker = false)
        assertTrue(ring.release(slotCentre(3, count).x, slotCentre(3, count).y, density))
        assertEquals(listOf(PenPalette.COLORS[3]), sink.colours)
        assertNull(ring.open)

        ring.summon(picker = false)
        assertTrue(ring.release(nib.x, nib.y, density))
        assertEquals("the dead zone runs nothing", 1, sink.colours.size)
        assertNull(ring.open)
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
    fun `the ring carries only the families the Ring page leaves on`() {
        val narrow = RingContents(swatches = false, widths = true, tools = setOf(DesktopTool.HIGHLIGHTER))
        val slots = narrow.slots(PenPalette.COLORS, null)
        assertEquals(PenPalette.DEFAULT_WIDTHS.size + 1, slots.size)
        assertEquals(HudSlot.Width(PenPalette.DEFAULT_WIDTHS.first()), slots.first())
        assertEquals(HudSlot.ToolFace(Tool.HIGHLIGHTER), slots.last())

        val hollow = RingContents(swatches = false, widths = false, tools = emptySet())
        assertTrue("nothing on the ring, nothing to flick into", hollow.slots(PenPalette.COLORS, null).isEmpty())
    }

    @Test
    fun `a ring the page emptied does not open at all`() {
        val hollow = DesktopRing(
            palette = { PenPalette.COLORS },
            custom = { null },
            onColour = {},
            onWidth = {},
            onTool = {},
            contents = { RingContents(swatches = false, widths = false, tools = emptySet()) },
        )
        assertFalse("the key has nothing to offer", hollow.summon())
        assertNull(hollow.open)
    }

}

package com.mobixournal.ui

import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet's ring vocabular: the ring a fresh install draws (the **same factory ring** the window has), the
 * names a stored face may carry, and the three edits the Ring page makes.
 *
 * What must not drift is the order: both shells read it from `RadialHud`, so the circle a reader arranges on
 * the tablet is the circle the window draws. A face this build cannot act on is dropped, which is why a name
 * from another version cannot leave a position that does nothing.
 */
class RingToolsTest {

    private val factorySize =
        RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size + RadialHud.FACTORY_TOOLS.size

    @Test
    fun `a fresh install draws the factory ring`() {
        val faces = ringFaces(AppSettings())
        assertEquals(factorySize, faces.size)
        assertEquals("black is at the top", HudSlot.Colour(PenPalette.BLACK), faces.first())
        assertEquals("and the pen closes the ring", HudSlot.ToolFace("PEN"), faces.last())
        assertEquals(
            "the figure is the first tool, right after the widths",
            HudSlot.ToolFace("LINE"),
            faces[RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size],
        )
    }

    @Test
    fun `a name becomes the rail's tool, aliases included`() {
        assertEquals(EditorTool.PEN, ringToolFor("PEN"))
        assertEquals("a stored line may be read back in any case", EditorTool.LINE, ringToolFor("line"))
        assertEquals("the ring's shared name for the marquee", EditorTool.SELECT, ringToolFor("MARQUEE"))
        assertEquals("and for the lasso", EditorTool.LASSO_SELECT, ringToolFor("LASSO"))
        assertNull("a tool this build has not got", ringToolFor("TELEPORTER"))
        assertNull(ringToolFor("  "))
    }

    @Test
    fun `every tool a face may hold is one the rail offers`() {
        assertTrue("the retired entries are not offered", EditorTool.SQUARE !in RING_TOOL_CHOICES)
        assertTrue(EditorTool.PEN in RING_TOOL_CHOICES)
        assertTrue("the eraser's second member acts too", EditorTool.ERASER_WHOLE in RING_TOOL_CHOICES)
        assertEquals("one row per tool, no twins", RING_TOOL_CHOICES.size, RING_TOOL_CHOICES.toSet().size)
    }

    @Test
    fun `a face is given, added and taken off`() {
        val changed = ringWithFace(AppSettings(), 0, HudSlot.ToolFace("LINE"))
        assertEquals(HudSlot.ToolFace("LINE"), ringFaces(changed).first())
        assertNotNull("the first edit pins the ring", changed.ringSlots)

        val grown = ringWithPosition(changed)
        assertEquals(ringFaces(changed).size + 1, ringFaces(grown).size)

        val removed = ringWithFace(grown, 0, null)
        assertEquals(ringFaces(grown).size - 1, ringFaces(removed).size)
        assertNotNull("a position can go and the ring stays the reader's", removed.ringSlots)
        assertEquals("the factory ring is untouched by any of it", HudSlot.Colour(PenPalette.BLACK), ringFaces(AppSettings()).first())
    }

    @Test
    fun `the last face left brings the factory ring back`() {
        var settings = AppSettings()
        while (ringFaces(settings).size > 1) settings = ringWithFace(settings, 0, null)
        assertEquals("one face is still a ring", 1, ringFaces(settings).size)

        val emptied = ringWithFace(settings, 0, null)
        assertNull("an emptied ring is not remembered", emptied.ringSlots)
        assertEquals("the factory ring is what it means", ringFaces(AppSettings()), ringFaces(emptied))
    }

    @Test
    fun `an edit out of range changes nothing`() {
        val fresh = AppSettings()
        assertEquals(fresh, ringWithFace(fresh, 99, HudSlot.ToolFace("PEN")))
    }
}

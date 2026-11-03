package com.mobixournal.render

import com.mobixournal.design.PenPalette
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The radial HUD's arithmetic, pinned off-device: the ring's order and cap, each slot's centre, the
 * boundary halfway between two slots, the seam where the ring closes, and the centre dead zone that
 * commits nothing. The drawing and the touch plumbing are each shell's; this is what decides *what* a
 * flick means, and it is the half a screenshot cannot check.
 *
 * It lives in `mobix-core` beside the geometry it tests because **both** shells ring the same way: the
 * tablet's canvas and the desktop window's painter are two adapters over this one set of angles, the
 * way `ToolGlyphCatalogue` gives two shells one set of glyphs. The suite therefore reads
 * `PenPalette`'s factory values rather than the app's `AppSettings`, which delegates to them.
 */
class RadialHudTest {

    private val widths = listOf(0.85f, 1.5f, 2.6f)
    private val tools = listOf("PEN", "HIGHLIGHTER", "ERASER")
    private val ringRadius = RadialHud.RING_RADIUS_DP
    private val slotRadius = RadialHud.SLOT_DIAMETER_DP / 2f

    @Test
    fun paletteComesFirstThenWidthsThenTools() {
        val slots = RadialHud.slots(
            colors = listOf(0xFF000000.toInt(), 0xFFFF0000.toInt()),
            customColor = 0xFF123456.toInt(),
            widths = widths,
            tools = tools,
        )
        assertEquals(
            listOf(
                HudSlot.Colour(0xFF000000.toInt()),
                HudSlot.Colour(0xFFFF0000.toInt()),
                HudSlot.Colour(0xFF123456.toInt()),
                HudSlot.Width(0.85f),
                HudSlot.Width(1.5f),
                HudSlot.Width(2.6f),
                HudSlot.ToolFace("PEN"),
                HudSlot.ToolFace("HIGHLIGHTER"),
                HudSlot.ToolFace("ERASER"),
            ),
            slots,
        )
    }

    @Test
    fun aCustomColourAlreadyInThePaletteIsNotRepeated() {
        val black = 0xFF000000.toInt()
        val slots = RadialHud.slots(listOf(black), black, emptyList(), emptyList())
        assertEquals(listOf(HudSlot.Colour(black)), slots)
    }

    @Test
    fun theCustomSlotSurvivesAFullPaletteAndOnlyTheLastSwatchMakesRoom() {
        val colors = (1..10).map { 0xFF000000.toInt() or it }
        val custom = 0xFF123456.toInt()
        val slots = RadialHud.slots(colors, custom, widths, tools)
        assertEquals(RadialHud.MAX_SLOTS, slots.size)
        assertEquals(HudSlot.Colour(custom), slots[9])
        // The last stored swatch made room; the eight before it are untouched.
        assertEquals(HudSlot.Colour(colors[8]), slots[8])
        assertEquals(HudSlot.Width(0.85f), slots[10])
        assertEquals(HudSlot.ToolFace("ERASER"), slots[15])
    }

    @Test
    fun aLargePaletteDoesNotCrowdOutTheWidthsAndTools() {
        val slots = RadialHud.slots(
            colors = (0..23).map { 0xFF000000.toInt() or it },
            customColor = 0xFFABCDEF.toInt(),
            widths = listOf(0.5f, 1f, 2f),
            tools = listOf("PEN", "HIGHLIGHTER", "ERASER"),
        )
        assertEquals(RadialHud.MAX_SLOTS, slots.size)
        assertEquals(HudSlot.Width(0.5f), slots[10])
        assertEquals(HudSlot.Width(2f), slots[12])
        assertEquals(HudSlot.ToolFace("PEN"), slots[13])
        assertEquals(HudSlot.ToolFace("ERASER"), slots[15])
    }

    /**
     * The call the editor makes when it pushes Settings onto the canvas: the **factory ring** alone fills
     * it (the palette's first five, three widths, six tools), so a first launch has a working HUD and —
     * because the window asks for the same call — the same ring as the desktop's Settings preview. The
     * app's `AppSettings` is seeded from these very values (`penColors = PenPalette.COLORS`,
     * `penWidths = PenPalette.DEFAULT_WIDTHS`), which is why the suite can state the same fact without
     * reaching into the app.
     */
    @Test
    fun theEditorsDefaultSettingsFillTheRing() {
        val slots = RadialHud.slots(
            colors = RadialHud.factoryColours(PenPalette.COLORS),
            customColor = null,
            widths = PenPalette.DEFAULT_WIDTHS,
            tools = RadialHud.FACTORY_TOOLS,
        )
        assertEquals(
            RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size + RadialHud.FACTORY_TOOLS.size,
            slots.size,
        )
        assertEquals("the first swatch is the factory black", HudSlot.Colour(PenPalette.COLORS.first()), slots.first())
        assertEquals("and the ring closes on the pen", HudSlot.ToolFace("PEN"), slots.last())
    }

    /**
     * **The window's ring is the tablet's ring.** The desktop has no `AppSettings` and no width list of
     * its own: it reads `PenPalette` through `DesktopInkState` and asks for exactly this — the user's
     * palette in their stored order, the editable custom slot last among the swatches, the three
     * widths, the three writing tools — so the same call answers both shells and a change to one is a
     * change to the other.
     */
    @Test
    fun theWindowsContentsFitTheSameRing() {
        val custom = 0xFF123456.toInt()
        val slots = RadialHud.factorySlots(PenPalette.COLORS, custom, PenPalette.DEFAULT_WIDTHS)
        assertEquals(
            RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size + RadialHud.FACTORY_TOOLS.size,
            slots.size,
        )
        assertTrue("the ring has room for the window's contents", slots.size <= RadialHud.MAX_SLOTS)
        // Five swatches — the palette's own order — with the custom slot kept among them, last of the five.
        assertEquals(HudSlot.Colour(PenPalette.COLORS.first()), slots.first())
        assertEquals(HudSlot.Colour(custom), slots[RadialHud.FACTORY_COLOUR_SLOTS - 1])
        // Then the widths, then the six tools: the two halves the ring exists to keep one flick away.
        assertEquals(HudSlot.Width(PenPalette.DEFAULT_WIDTHS.first()), slots[RadialHud.FACTORY_COLOUR_SLOTS])
        assertEquals(
            "the figure comes first among the tools",
            HudSlot.ToolFace("LINE"),
            slots[RadialHud.FACTORY_COLOUR_SLOTS + PenPalette.DEFAULT_WIDTHS.size],
        )
        assertEquals("and the pen closes the ring", HudSlot.ToolFace("PEN"), slots.last())
    }

    @Test
    fun slotZeroIsStraightUpAndTheRingRunsClockwise() {
        val count = 8
        val (topX, topY) = RadialHud.slotCenter(0f, 0f, 0, count, ringRadius)
        assertEquals(0f, topX, 0.001f)
        assertEquals(-ringRadius, topY, 0.001f)
        val (rightX, rightY) = RadialHud.slotCenter(0f, 0f, count / 4, count, ringRadius)
        assertEquals(ringRadius, rightX, 0.001f)
        assertEquals(0f, rightY, 0.001f)
        assertEquals(90f, RadialHud.slotAngleDegrees(count / 4, count), 0.001f)
    }

    @Test
    fun everySlotCentreMapsBackToItsOwnSlot() {
        for (count in 1..RadialHud.MAX_SLOTS) {
            for (index in 0 until count) {
                val (x, y) = RadialHud.slotCenter(120f, 240f, index, count, ringRadius)
                assertEquals(
                    "slot $index of $count",
                    index,
                    RadialHud.slotIndexAt(120f, 240f, x, y, count, ringRadius, slotRadius),
                )
            }
        }
    }

    /**
     * Every slot of every count the ring can hold lands **on** the ring — one radius, no slot drifting
     * into the dead zone or off the glass — which is the unit test the HUD's drawing task asks for.
     */
    @Test
    fun everySlotOfTheDocumentedCountLandsOnTheRing() {
        for (count in 1..RadialHud.MAX_SLOTS) {
            for (index in 0 until count) {
                val (x, y) = RadialHud.slotCenter(0f, 0f, index, count, ringRadius)
                assertEquals("slot $index of $count", ringRadius, hypot(x, y), 0.001f)
                assertTrue(hypot(x, y) > RadialHud.DEAD_ZONE_DP)
            }
        }
    }

    @Test
    fun aBoundaryBelongsToTheNearerOfItsTwoSlots() {
        val count = 8
        val step = 360f / count
        val boundary = Math.toRadians((step / 2f - 90f).toDouble())
        val justBefore = Math.toRadians((step / 2f - 0.5f - 90f).toDouble())
        val justAfter = Math.toRadians((step / 2f + 0.5f - 90f).toDouble())
        fun at(radians: Double) =
            RadialHud.slotIndexAt(
                0f, 0f,
                (cos(radians) * ringRadius).toFloat(),
                (sin(radians) * ringRadius).toFloat(),
                count, ringRadius, slotRadius,
            )
        assertEquals(0, at(justBefore))
        assertEquals(1, at(justAfter))
        // Exactly on the boundary rounds up to the next slot — one answer, never two.
        assertEquals(1, at(boundary))
    }

    @Test
    fun theSeamWrapsFromTheLastSlotToTheFirst() {
        val count = 8
        val step = 360f / count
        // Just on the last slot's side of the boundary halfway between slot 7 and slot 0.
        val justPastTheSeam = Math.toRadians((-step / 2f - 0.5f - 90f).toDouble())
        val index = RadialHud.slotIndexAt(
            0f, 0f,
            (cos(justPastTheSeam) * ringRadius).toFloat(),
            (sin(justPastTheSeam) * ringRadius).toFloat(),
            count, ringRadius, slotRadius,
        )
        assertEquals(count - 1, index)
    }

    @Test
    fun theCentreDeadZoneCommitsNothing() {
        assertNull(RadialHud.slotIndexAt(0f, 0f, 0f, 0f, 8, ringRadius, slotRadius))
        assertNull(RadialHud.slotIndexAt(0f, 0f, 10f, 10f, 8, ringRadius, slotRadius))
        assertNull(
            RadialHud.slotIndexAt(
                0f, 0f, 0f, RadialHud.DEAD_ZONE_DP - 1f, 8, ringRadius, slotRadius,
            )
        )
    }

    @Test
    fun aReleaseOffTheSlotBandCommitsNothing() {
        // Inside the ring, past the dead zone, but short of the slot band.
        assertNull(RadialHud.slotIndexAt(0f, 0f, 0f, -(ringRadius - slotRadius - 1f), 8, ringRadius, slotRadius))
        // Past the band's outer edge.
        assertNull(RadialHud.slotIndexAt(0f, 0f, 0f, -(ringRadius + slotRadius + 1f), 8, ringRadius, slotRadius))
    }

    /**
     * The stored line is **one format for both shells**: what a ring is written as is what it is read back as,
     * and a line this build cannot read leaves the factory ring rather than an empty one.
     */
    @Test
    fun theRingSurvivesItsStoredLine() {
        val ring = listOf(
            HudSlot.Colour(PenPalette.BLACK),
            HudSlot.Width(1.5f),
            HudSlot.ToolFace("LASSO"),
        )
        assertEquals(ring, RadialHud.decodeSlots(RadialHud.encodeSlots(ring)))
        assertEquals("a blank line is the factory ring", null, RadialHud.decodeSlots(null))
        assertEquals("and so is an empty one", null, RadialHud.decodeSlots("nonsense,,"))
        assertEquals(
            "an unreadable face is dropped and the rest are kept",
            listOf(HudSlot.Colour(PenPalette.RED)),
            RadialHud.decodeSlots("c:ffff0000,w:not-a-width"),
        )
        // A tool name is kept as written: only the shell knows which tools it has, and it drops the rest.
        assertEquals(
            listOf(HudSlot.ToolFace("something-beta")),
            RadialHud.decodeSlots("t:something-beta"),
        )
    }
}

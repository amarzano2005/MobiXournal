package com.mobixournal.render

import com.mobixournal.format.model.Tool
import com.mobixournal.ui.AppSettings
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
 * commits nothing. The drawing and the touch plumbing are the view's; this is what decides *what* a
 * flick means, and it is the half a screenshot cannot check.
 */
class RadialHudTest {

    private val widths = listOf(0.85f, 1.5f, 2.6f)
    private val tools = listOf(Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER)
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
                HudSlot.ToolFace(Tool.PEN),
                HudSlot.ToolFace(Tool.HIGHLIGHTER),
                HudSlot.ToolFace(Tool.ERASER),
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
        assertEquals(HudSlot.ToolFace(Tool.ERASER), slots[15])
    }

    @Test
    fun aLargePaletteDoesNotCrowdOutTheWidthsAndTools() {
        val slots = RadialHud.slots(
            colors = (0..23).map { 0xFF000000.toInt() or it },
            customColor = 0xFFABCDEF.toInt(),
            widths = listOf(0.5f, 1f, 2f),
            tools = listOf(Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER),
        )
        assertEquals(RadialHud.MAX_SLOTS, slots.size)
        assertEquals(HudSlot.Width(0.5f), slots[10])
        assertEquals(HudSlot.Width(2f), slots[12])
        assertEquals(HudSlot.ToolFace(Tool.PEN), slots[13])
        assertEquals(HudSlot.ToolFace(Tool.ERASER), slots[15])
    }

    /**
     * The exact call the editor makes when it pushes Settings onto the canvas: the defaults alone fill
     * the ring (eight swatches, three widths, three tools), so a first launch has a working HUD even
     * before anything is customised.
     */
    @Test
    fun theEditorsDefaultSettingsFillTheRing() {
        val settings = AppSettings()
        val slots = RadialHud.slots(
            colors = settings.penColors,
            customColor = settings.customColor,
            widths = settings.penWidths,
            tools = listOf(Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER),
        )
        assertEquals(settings.penColors.distinct().size + settings.penWidths.size + 3, slots.size)
        assertEquals(HudSlot.Colour(settings.penColors.first()), slots.first())
        assertEquals(HudSlot.ToolFace(Tool.ERASER), slots.last())
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
}

package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rail's sizing is pure arithmetic, so the two promises it makes are pinned here rather than
 * eyeballed on one screen: **a button is never drawn smaller than its own size**, and the slots it does
 * show **fill the height exactly** — which is what puts the next slot's top edge at the rail's bottom
 * edge instead of cutting it in half.
 */
class RailSizingTest {

    private val slot = 44f
    private val spacing = 6f
    private val padding = 8f

    private fun scale(available: Float, count: Int = 18) = railContentScale(
        availableHeightDp = available,
        slotCount = count,
        slotDp = slot,
        spacingDp = spacing,
        paddingDp = padding,
    )

    /** How many slots `scale` puts in `available`, counting the gaps between them. */
    private fun shown(scale: Float, available: Float): Int {
        val usable = available - padding * 2f
        val pitch = (slot + spacing) * scale
        // The count lands on an exact integer when the slots fill the space, so round off the float
        // noise rather than letting 13.999999 read as 13.
        return ((usable + spacing * scale) / pitch + 1e-3f).toInt()
    }

    @Test
    fun `a rail that already fits is left at its own size`() {
        // 18 slots at full size: 18 × 44 + 17 × 6 = 894, inside 910.
        assertEquals(1f, scale(910f), 1e-6f)
        assertEquals(1f, scale(1200f), 1e-6f)
    }

    @Test
    fun `an overflowing rail never shrinks its slots`() {
        for (available in listOf(500f, 700f, 800f, 890f)) {
            assertTrue(
                "scale $available went below 1",
                scale(available) >= 1f,
            )
        }
    }

    @Test
    fun `the whole slots shown fill the height exactly`() {
        // 750dp: 14 slots of 44 + 13 gaps need 694, and a 15th would need 744 — so 14 are shown, and
        // they are spread to fill 734 (750 minus the column's 16dp of padding).
        val s = scale(750f)
        assertEquals(14, shown(s, 750f))
        assertEquals(734f, s * (14 * (slot + spacing) - spacing), 1e-3f)
    }

    @Test
    fun `the leftover strip goes into slightly larger buttons, not into a cut one`() {
        val s = scale(750f)
        // ~6% larger, which is what closes the 40dp that 14 slots leave over.
        assertTrue("expected a small growth, got $s", s > 1f && s < RAIL_MAX_SCALE)
        assertTrue(slot * s <= 48f)
    }

    @Test
    fun `growth is capped so a slot cannot outgrow the rail`() {
        // A 76dp rail holds one slot and a lot of air; the cap keeps that slot at 1.08.
        assertEquals(RAIL_MAX_SCALE, scale(76f), 1e-6f)
    }

    @Test
    fun `a rail too short for even one slot is not scaled against it`() {
        assertEquals(1f, scale(10f), 1e-6f)
        assertEquals(1f, scale(0f), 1e-6f)
    }

    @Test
    fun `a short rail is judged on how many slots it holds, not how tall it is`() {
        // Ten slots in 700dp all fit, so nothing is touched.
        assertEquals(1f, scale(700f, count = 10), 1e-6f)
        // Eighteen do not: 13 whole slots fill the 684dp of usable height and the rest is a scroll
        // away, rather than a 14th button left showing only its top half.
        assertEquals(13, shown(scale(700f, count = 18), 700f))
    }

    @Test
    fun `an empty rail needs no sizing`() {
        assertEquals(1f, scale(500f, count = 0), 1e-6f)
    }
}

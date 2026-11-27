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

    private val slot = 40f
    private val spacing = 4f
    private val padding = 6f

    private fun scale(available: Float, count: Int = 11) = railContentScale(
        availableHeightDp = available,
        slotCount = count,
        slotDp = slot,
        spacingDp = spacing,
        paddingDp = padding,
    )

    @Test
    fun `a rail that already fits is left at its own size`() {
        // 11 slots at full size: 11 × 40 + 10 × 4 = 480, plus 12dp padding = 492.
        assertEquals(1f, scale(492f), 1e-6f)
        assertEquals(1f, scale(800f), 1e-6f)
    }

    @Test
    fun `the rail never enlarges its slots`() {
        for (available in listOf(300f, 420f, 450f, 490f, 600f, 900f)) {
            assertTrue("scale $available exceeded 1.0", scale(available) <= 1f)
        }
    }

    @Test
    fun `a slightly tighter height shrinks slots proportionally to keep all visible`() {
        // 452dp total -> 440dp usable vs 480dp needed -> 440/480 = 0.91667.
        val s = scale(452f)
        assertEquals(440f / 480f, s, 1e-4f)
        assertTrue(s in RAIL_MIN_SCALE..1f)
    }

    @Test
    fun `shrinking is floored at RAIL_MIN_SCALE so targets stay comfortable`() {
        assertEquals(RAIL_MIN_SCALE, scale(200f), 1e-6f)
        assertEquals(RAIL_MIN_SCALE, scale(10f), 1e-6f)
    }

    @Test
    fun `non-positive height or empty rail returns 1`() {
        assertEquals(1f, scale(0f), 1e-6f)
        assertEquals(1f, scale(500f, count = 0), 1e-6f)
    }
}

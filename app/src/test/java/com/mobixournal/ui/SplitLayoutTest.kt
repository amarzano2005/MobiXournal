package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SplitLayoutTest {

    @Test
    fun `fraction is clamped so neither pane can disappear`() {
        assertEquals(SPLIT_MIN_FRACTION, clampSplitFraction(0f), EPSILON)
        assertEquals(1f - SPLIT_MIN_FRACTION, clampSplitFraction(1f), EPSILON)
        assertEquals(0.5f, clampSplitFraction(0.5f), EPSILON)
        assertEquals(0.5f, clampSplitFraction(SPLIT_EVEN_FRACTION), EPSILON)
    }

    @Test
    fun `a NaN fraction falls back to the balanced split`() {
        assertEquals(SPLIT_EVEN_FRACTION, clampSplitFraction(Float.NaN), EPSILON)
    }

    @Test
    fun `dragging right grows the first pane by the distance crossed`() {
        assertEquals(0.6f, splitFractionAfterDrag(0.5f, 100f, 1_000f), EPSILON)
        assertEquals(0.35f, splitFractionAfterDrag(0.5f, -150f, 1_000f), EPSILON)
    }

    @Test
    fun `drag distance is accumulated from the start of the gesture`() {
        // Ten 10 px deltas are one 100 px drag, not ten percentages of a stale fraction.
        assertEquals(0.6f, splitFractionAfterDrag(0.5f, 10f * 10, 1_000f), EPSILON)
    }

    @Test
    fun `a drag past either edge stops at the minimum`() {
        assertEquals(
            1f - SPLIT_MIN_FRACTION,
            splitFractionAfterDrag(0.5f, 10_000f, 1_000f),
            EPSILON,
        )
        assertEquals(
            SPLIT_MIN_FRACTION,
            splitFractionAfterDrag(0.5f, -10_000f, 1_000f),
            EPSILON,
        )
    }

    @Test
    fun `a zero or negative width cannot divide by zero`() {
        val still = splitFractionAfterDrag(0.5f, 0f, 0f)
        assertFalse(still.isNaN())
        assertEquals(0.5f, still, EPSILON)

        val clamped = splitFractionAfterDrag(0.5f, 1_000f, 0f)
        assertFalse(clamped.isNaN())
        assertEquals(1f - SPLIT_MIN_FRACTION, clamped, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}

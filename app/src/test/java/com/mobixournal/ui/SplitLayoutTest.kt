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

    @Test
    fun `the panes and the handle tile the whole width`() {
        for (fraction in listOf(0.15f, 0.3f, 0.5f, 0.72f, 0.85f)) {
            val (first, second) = splitPaneSpans(1_600f, fraction, 56f)
            assertEquals(1_600f, first + 56f + second, EPSILON)
        }
    }

    @Test
    fun `the divider is centred where the fraction says`() {
        val (first, _) = splitPaneSpans(1_600f, 0.5f, 56f)
        assertEquals(800f, first + 28f, EPSILON)
        val (shifted, _) = splitPaneSpans(1_600f, 0.6f, 56f)
        assertEquals(960f, shifted + 28f, EPSILON)
    }

    @Test
    fun `the divider moves exactly as far as the finger drags`() {
        val total = 1_600f
        val handle = 56f
        val start = 0.5f
        val (startFirst, _) = splitPaneSpans(total, start, handle)
        for (drag in listOf(-200f, -60f, 0f, 45f, 260f)) {
            val moved = splitFractionAfterDrag(start, drag, total)
            val (movedFirst, _) = splitPaneSpans(total, moved, handle)
            assertEquals(drag, movedFirst - startFirst, EPSILON)
        }
    }

    @Test
    fun `a width narrower than the handle never yields a negative pane`() {
        val (first, second) = splitPaneSpans(12f, 0.5f, 56f)
        assertEquals(0f, first, EPSILON)
        assertEquals(0f, second, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}

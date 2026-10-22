package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hold-to-snap's timing is a product decision, not an implementation detail: it is what decides
 * whether a deliberate pause snaps and whether a pause *inside a letter* does not. It is pinned here
 * so it cannot drift back to the old half-second without someone changing a test that says why.
 */
class HoldSnapDwellTest {

    @Test
    fun `the dwell is 300 ms`() {
        assertEquals(300L, SHAPE_DWELL_MS)
    }

    @Test
    fun `the tip counts as resting under 3 pt of travel`() {
        assertEquals(3.0, SHAPE_DWELL_SLOP_PT, 1e-9)
    }
}

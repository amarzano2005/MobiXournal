package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [measurementText]: the dimension tool's value, in millimetres, at a tenth's precision. */
class DimensionLabelTest {

    @Test
    fun `a length in points is reported in millimetres`() {
        // 72 pt is exactly 1 inch, i.e. 25.4 mm.
        assertEquals("25.4 mm", measurementText(72.0))
    }

    @Test
    fun `the value keeps one decimal place`() {
        assertEquals("10.0 mm", measurementText(28.3464566929))
    }

    @Test
    fun `a stray tap measures nothing`() {
        assertNull(measurementText(0.0))
        assertNull(measurementText(0.9))
    }

    @Test
    fun `a long dimension stays readable`() {
        assertEquals("705.6 mm", measurementText(2000.0))
    }
}

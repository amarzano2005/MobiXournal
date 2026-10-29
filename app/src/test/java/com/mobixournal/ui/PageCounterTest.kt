package com.mobixournal.ui

import androidx.compose.ui.Alignment
import org.junit.Assert.assertEquals
import org.junit.Test

/** The two canvas badges are pure text mappers; pin their formatting down. */
class PageCounterTest {

    @Test
    fun `zoom label is a whole percentage`() {
        assertEquals("100%", zoomLabel(1f))
        assertEquals("125%", zoomLabel(1.25f))
        assertEquals("25%", zoomLabel(0.25f))
        // Rounds rather than truncates: 96.1% reads as 96%, not 96.1 -> 96.00000.
        assertEquals("96%", zoomLabel(0.961f))
        assertEquals("400%", zoomLabel(4f))
    }

    @Test
    fun `page label counts from one`() {
        assertEquals("1 / 12", pageLabel(0, 12))
        assertEquals("12 / 12", pageLabel(11, 12))
    }

    @Test
    fun `page label never throws on an empty document`() {
        assertEquals("0 / 0", pageLabel(0, 0))
    }

    @Test
    fun `a label that fits its slot keeps its size`() {
        // "41%" in the rail's slot: nothing to shrink, so the base size comes back untouched.
        assertEquals(14f, fittedLabelSp(baseSp = 14f, measuredWidthPx = 54f, availablePx = 66f), 0.001f)
        assertEquals(14f, fittedLabelSp(baseSp = 14f, measuredWidthPx = 66f, availablePx = 66f), 0.001f)
    }

    @Test
    fun `a three digit percentage shrinks to fit the slot`() {
        // "100%" needs 78 px in a slot that leaves 66 px: the font comes down by the same ratio, so
        // the digits stay whole instead of being cropped by the circle.
        val fitted = fittedLabelSp(baseSp = 14f, measuredWidthPx = 78f, availablePx = 66f)
        assertEquals(11.85f, fitted, 0.01f)
        assertEquals(66f, 78f * fitted / 14f, 0.01f)
    }

    @Test
    fun `the shrink stops before the label becomes a speck`() {
        assertEquals(14f * ZOOM_LABEL_MIN_SCALE, fittedLabelSp(14f, 8_000f, 66f), 0.001f)
    }

    @Test
    fun `an unmeasurable label or slot is left alone`() {
        assertEquals(14f, fittedLabelSp(14f, 0f, 66f), 0.001f)
        assertEquals(14f, fittedLabelSp(14f, 78f, 0f), 0.001f)
    }

    @Test
    fun `the badges take the edge opposite the rail`() {
        // A left-docked rail (right-handed writing) leaves the right edge free, and the mirror for a
        // left hand. There is no placement setting behind this, so this is the whole contract.
        assertEquals(Alignment.BottomEnd, pageCounterAlignment(ToolbarPosition.LEFT))
        assertEquals(Alignment.BottomStart, pageCounterAlignment(ToolbarPosition.RIGHT))
    }
}

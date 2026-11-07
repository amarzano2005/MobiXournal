package com.mobixournal.desktop

import com.mobixournal.desktop.settings.FitOnOpen
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PAGE_MARGIN_DP
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.zoomLabel
import com.mobixournal.desktop.sheet.zoomPercentOfWidthFit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The window's zoom scale: **100 % is the page's width filling the window**, the scale a phone opens a sheet
 * at, and a document arrives at it (`zoomPercentOfWidthFit`, `FitOnOpen.DEFAULT`).
 *
 * The zoom itself stays the ordinary absolute scale — these tests pin the *reading* of it, which is what the
 * status bar prints and what the reader compares the sheet against.
 */
class ZoomCalibrationTest {

    private val pageW = 794f
    private val viewW = 1400f

    @Test
    fun `the width fit reads exactly one hundred percent`() {
        val fit = Viewport.fitWidthZoom(pageW, viewW)
        assertEquals("100%", zoomLabel(fit, fit))
        assertEquals(100, zoomPercentOfWidthFit(fit, fit))
    }

    @Test
    fun `twice the width fit reads two hundred percent`() {
        val fit = Viewport.fitWidthZoom(pageW, viewW)
        assertEquals("200%", zoomLabel(fit * 2f, fit))
    }

    @Test
    fun `a width fit leaves the page's width filling the room it was measured in`() {
        val fit = Viewport.fitWidthZoom(pageW, viewW)
        assertEquals(viewW - PAGE_MARGIN_DP * 2f, pageW * fit, 0.01f)
    }

    @Test
    fun `a page the canvas has not measured yet keeps the absolute scale`() {
        assertEquals("250%", zoomLabel(2.5f, 0f))
        assertEquals("250%", zoomLabel(2.5f))
    }

    @Test
    fun `a document opens at the width fit`() {
        assertEquals(FitOnOpen.WIDTH, FitOnOpen.DEFAULT)
        assertEquals(FitMode.WIDTH, FitOnOpen.DEFAULT.fit)
    }
}

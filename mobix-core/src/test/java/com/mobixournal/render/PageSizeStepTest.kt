package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Pages menu's quick size step: pixels to points, and a sheet that never turns itself over. */
class PageSizeStepTest {

    @Test fun `one hundred pixels is seventy-five points`() {
        assertEquals(75.0, PageSizeStep.pt(100), 1e-9)
        assertEquals(PageSizeStep.DEFAULT_PX, 100)
    }

    @Test fun `a step widens and heightens the sheet by that many points`() {
        val (w, h) = PageSizeStep.step(595.276, 841.89, 1, 0, PageSizeStep.pt(100))
        assertEquals(670.276, w, 1e-9)
        assertEquals(841.89, h, 1e-9)
    }

    @Test fun `a step takes from the sheet as well as adding to it`() {
        val (w, h) = PageSizeStep.step(595.276, 841.89, 0, -1, PageSizeStep.pt(100))
        assertEquals(595.276, w, 1e-9)
        assertEquals(766.89, h, 1e-9)
    }

    @Test fun `the sheet never shrinks below a sliver`() {
        val (w, h) = PageSizeStep.step(80.0, 80.0, -4, -4, PageSizeStep.pt(100))
        assertEquals(PageSizeStep.MIN_PAGE_PT, w, 1e-9)
        assertEquals(PageSizeStep.MIN_PAGE_PT, h, 1e-9)
    }

    @Test fun `the setting is kept inside its own range`() {
        assertEquals(PageSizeStep.MIN_PX, PageSizeStep.clampPx(0))
        assertEquals(PageSizeStep.MAX_PX, PageSizeStep.clampPx(5_000))
        assertEquals(250, PageSizeStep.clampPx(250))
    }
}

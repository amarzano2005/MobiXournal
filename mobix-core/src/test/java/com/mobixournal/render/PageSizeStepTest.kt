package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Pages menu's quick size step: millimetres to points, and a sheet that never turns itself over. */
class PageSizeStepTest {

    @Test fun `one hundred millimetres is the factory step, and 283 points`() {
        assertEquals(PageSizeStep.DEFAULT_MM, 100.0, 1e-9)
        // 100 mm is 3.937 in, and an inch is 72 pt: 283.46 pt, the length of paper one tap adds.
        assertEquals(283.46456692913387, PageSizeStep.pt(100.0), 1e-6)
    }

    @Test fun `the step in inches and points is the same hundred millimetres`() {
        assertEquals(3.937007874015748, PageSizeUnit.IN.defaultStep, 1e-9)
        assertEquals(283.46456692913387, PageSizeUnit.PT.defaultStep, 1e-6)
        assertEquals(100.0, PageSizeUnit.MM.defaultStep, 1e-9)
        // All three are one length of paper, whichever unit the format is read in.
        assertEquals(
            PageSizeStep.pt(PageSizeStep.DEFAULT_MM),
            PageSizeUnit.IN.toPt(PageSizeUnit.IN.defaultStep),
            1e-9,
        )
    }

    @Test fun `a step prints in the unit it is read in`() {
        assertEquals("100 mm", PageSizeStep.label(PageSizeStep.DEFAULT_MM))
        assertEquals("3.9 in", PageSizeStep.label(PageSizeStep.DEFAULT_MM, PageSizeUnit.IN))
        assertEquals("283.5 pt", PageSizeStep.label(PageSizeStep.DEFAULT_MM, PageSizeUnit.PT))
    }

    @Test fun `a step widens and heightens the sheet by that many points`() {
        val (w, h) = PageSizeStep.step(595.276, 841.89, 1, 0, PageSizeStep.pt(100.0))
        assertEquals(878.74, w, 1e-9)
        assertEquals(841.89, h, 1e-9)
    }

    @Test fun `a step takes from the sheet as well as adding to it`() {
        val (w, h) = PageSizeStep.step(595.276, 841.89, 0, -1, PageSizeStep.pt(100.0))
        assertEquals(595.276, w, 1e-9)
        assertEquals(558.43, h, 1e-9)
    }

    @Test fun `the sheet never shrinks below a sliver`() {
        val (w, h) = PageSizeStep.step(80.0, 80.0, -4, -4, PageSizeStep.pt(100.0))
        assertEquals(PageSizeStep.MIN_PAGE_PT, w, 1e-9)
        assertEquals(PageSizeStep.MIN_PAGE_PT, h, 1e-9)
    }

    @Test fun `the setting is kept inside its own range`() {
        assertEquals(PageSizeStep.MIN_MM, PageSizeStep.clampMm(0.0), 1e-9)
        assertEquals(PageSizeStep.MAX_MM, PageSizeStep.clampMm(5_000.0), 1e-9)
        assertEquals(250.0, PageSizeStep.clampMm(250.0), 1e-9)
    }

    @Test fun `a step in points reads back as the millimetres it was written in`() {
        assertEquals(100.0, PageSizeStep.mmOf(PageSizeStep.pt(100.0)), 1e-9)
    }
}

package com.mobixournal.render

import com.mobixournal.format.model.Background
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blank-sheet factories every *created* sheet comes from — a new document, a new tab, the blank
 * document a pane falls back to, and Add page. All of them must be born at the page size the user set
 * as the default (Settings ▸ Editor), which is the wiring `setDefaultPageSize` provides; the page-size
 * dialog's **Default** tab only reads that same value back.
 */
class BlankPageDefaultsTest {

    @After
    fun restoreFactoryDefault() {
        // The default is process-wide, so leave the factories as A4 for whatever test runs next.
        setDefaultPageSize(DrawingSurfaceDefaults.A4_WIDTH_PT, DrawingSurfaceDefaults.A4_HEIGHT_PT)
    }

    @Test
    fun `a blank page and a blank document follow the default page size`() {
        setDefaultPageSize(400.0, 600.0)
        assertEquals(400.0, blankPage().width, 1e-9)
        assertEquals(600.0, blankPage().height, 1e-9)
        assertEquals(400.0, blankDocument().pages.single().width, 1e-9)
        assertEquals(600.0, blankDocument().pages.single().height, 1e-9)
    }

    @Test
    fun `an explicit size still overrides the default`() {
        setDefaultPageSize(400.0, 600.0)
        val explicit = blankPage(100.0, 200.0)
        assertEquals(100.0, explicit.width, 1e-9)
        assertEquals(200.0, explicit.height, 1e-9)
    }

    @Test
    fun `a blank page is empty and ruled graph`() {
        val fresh = blankPage()
        assertTrue("no ink on a fresh sheet", fresh.layers.single().elements.isEmpty())
        assertEquals("graph", (fresh.background as Background.Solid).style)
    }

    @Test
    fun `the default is clamped to the range the dialog allows`() {
        setDefaultPageSize(1.0, 1_000_000.0)
        assertEquals(DrawingSurfaceDefaults.PAGE_SIZE_MIN_PT, blankPage().width, 1e-9)
        assertEquals(DrawingSurfaceDefaults.PAGE_SIZE_MAX_PT, blankPage().height, 1e-9)
    }
}

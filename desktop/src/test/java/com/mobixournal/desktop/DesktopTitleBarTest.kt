package com.mobixournal.desktop

import com.mobixournal.design.DesignTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers the native title bar is painted with: the band's **composite** colour and its Win32 spelling.
 *
 * The caption is the one surface in the window the system paints, so it cannot take the glass' alpha; what it
 * takes instead is the glass already composited over the desk. Two things must hold for the title bar not to
 * become a second material: the composite is a few units above black (it is the band over the true-black
 * desk, not the band's raw RGB), and `COLORREF` really is byte-reversed — `0xRRGGBB` in, `0x00BBGGRR` out,
 * which is the mistake that would paint a blue caption red.
 */
class DesktopTitleBarTest {

    @Test
    fun `the band's colour is the glass composited over the desk`() {
        val band = DesktopTheme.bandRgb
        // The window's own alpha (`DESKTOP_BAND_ALPHA`): a blacker band than the tablet's `#131314`, by
        // the owner's decision of 2026-10-25 (`docs/ui-modernization.md`).
        assertEquals("the glass keeps the desk's red channel", 0x0C, (band shr 16) and 0xFF)
        assertEquals("and its own slight warmth on blue", 0x0D, band and 0xFF)
        assertTrue(
            "and the window's band is darker than the tablet's composite",
            ((band shr 8) and 0xFF) <
                (DesktopTheme.blendRgb(
                    DesignTokens.GLASS_DARK_RGB,
                    DesignTokens.GLASS_DARK_ALPHA,
                    DesignTokens.DESK_RGB,
                ) shr 8) and 0xFF,
        )
        assertEquals(
            "an opaque band with no alpha is the glass itself",
            DesignTokens.GLASS_DARK_RGB,
            DesktopTheme.blendRgb(DesignTokens.GLASS_DARK_RGB, 1f, DesignTokens.DESK_RGB),
        )
        assertEquals(
            "and a transparent band is the desk",
            DesignTokens.DESK_RGB,
            DesktopTheme.blendRgb(DesignTokens.GLASS_DARK_RGB, 0f, DesignTokens.DESK_RGB),
        )
    }

    @Test
    fun `a colour reaches Win32 in COLORREF order`() {
        assertEquals("#RRGGBB 0x123456 becomes 0x563412", 0x563412, DesktopTheme.colourRef(0x123456))
        assertEquals("black is black either way", 0, DesktopTheme.colourRef(DesignTokens.DESK_RGB))
        assertEquals(
            "the chrome ink is white either way",
            0xFFFFFF,
            DesktopTheme.colourRef(DesignTokens.CHROME_INK_DARK_RGB),
        )
    }
}

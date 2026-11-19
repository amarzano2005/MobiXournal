package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme

import com.mobixournal.design.DesignTokens
import org.junit.Assert.assertEquals
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
    fun `the band's colour is the flat band token`() {
        val band = DesktopTheme.bandRgb
        // The flat contract's band, opaque: the caption is the band's own tone, not a composite — there
        // is no alpha left to composite (`docs/interface-contract.md` §1).
        assertEquals("the caption takes the band's tone", DesignTokens.DESKTOP_BAND_DARK_RGB, band)
        assertEquals("its red channel", 0x14, (band shr 16) and 0xFF)
        assertEquals("its blue channel", 0x17, band and 0xFF)
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

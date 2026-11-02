package com.mobixournal.desktop

import androidx.compose.ui.graphics.Color
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.design.DesignTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The light theme's material rule (`docs/interface-contract.md` §1): **on paper a band and its control
 * swap places**.
 *
 * The dark recipe is one material for both — a capsule and the band it stands in are the same glass, and
 * the rim's white hairline is what makes the capsule an object. Carried onto paper unchanged, that
 * produced white discs on a white band and a menu whose chips could not be seen at all (the owner's
 * screenshot, 2026-11-02). So the light theme inverts the pair: the band is the tinted surface, the
 * control is the white one, and the edge is more of the same ink. This test holds the three answers —
 * and the dark ones unchanged, which is the part a future light-theme tweak is most likely to disturb.
 */
class LightMaterialTest {

    @Test
    fun `on paper a control is the light surface and its band the tinted one`() {
        DesktopTheme.use(true)
        try {
            val band = luminance(DesktopTheme.tokenColour(DesktopTheme.bandRgb))
            val control = luminance(DesktopTheme.control)
            assertTrue("a control is the lighter of the two on paper ($control vs $band)", control > band + 0.01f)
            assertTrue(
                "the desk is greyer than the band it carries (${luminance(DesktopTheme.desk)} vs $band)",
                luminance(DesktopTheme.desk) < band,
            )
            assertTrue(
                "the desk must not be a second white: a sheet has to end somewhere",
                luminance(DesktopTheme.desk) < 0.95f,
            )
            assertTrue(
                "the rim carries more ink on paper",
                DesktopTheme.rimAlphaTop > DesignTokens.RIM_ALPHA_TOP,
            )
            assertTrue(
                "a card is a tone under the white panel, not a ghost",
                luminance(DesktopTheme.card) < luminance(DesktopTheme.panel) - 0.01f,
            )
        } finally {
            DesktopTheme.use(false)
        }
    }

    @Test
    fun `on black a control is the band's own glass`() {
        DesktopTheme.use(false)
        try {
            assertEquals("the black desk moves nothing", DesktopTheme.glass, DesktopTheme.control)
            assertEquals(DesktopTheme.glass, DesktopTheme.panelField)
            assertEquals(DesignTokens.RIM_ALPHA_TOP, DesktopTheme.rimAlphaTop, 1e-4f)
            assertEquals(DesignTokens.RIM_ALPHA_BOTTOM, DesktopTheme.rimAlphaBottom, 1e-4f)
            assertEquals(DesktopTheme.activeSlot.copy(alpha = DesktopTheme.CARD_ALPHA), DesktopTheme.card)
        } finally {
            DesktopTheme.use(false)
        }
    }

    /** Perceived lightness, enough to say "this one is the lighter surface". */
    private fun luminance(colour: Color): Float =
        0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue
}

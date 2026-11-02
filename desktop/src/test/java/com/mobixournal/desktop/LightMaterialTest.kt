package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.design.DesignTokens
import javax.imageio.ImageIO
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
            assertEquals("a floating surface is solid on paper", DesktopTheme.panel, DesktopTheme.floating)
            assertTrue(
                "and a band casts a hairline, never a smudge across the sheet",
                DesktopTheme.bandShadow <= DesignTokens.BAND_SHADOW_LIGHT_DP.dp,
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
            assertEquals(DesignTokens.FLOATING_PANEL_ALPHA, DesktopTheme.floating.alpha, 0.005f)
            assertEquals(DesignTokens.BAND_SHADOW_DP.dp, DesktopTheme.bandShadow)
        } finally {
            DesktopTheme.use(false)
        }
    }

    /** Perceived lightness, enough to say "this one is the lighter surface". */
    private fun luminance(colour: Color): Float =
        0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue

    /**
     * The panel's half of the rule, in **pixels**: a floating surface over inked paper shows no ink.
     *
     * The solid fill is the whole claim. The dark theme's 0.88 is right over a black desk, where what is
     * behind a menu is a faint lift; on paper the same 12 % is the page's handwriting read *through* the
     * menu, which is what the owner saw (2026-11-02). This draws two black lines of words under a panel
     * and fails if anything darker than a hair of paper survives inside it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `on paper a floating surface hides the ink it covers`() {
        DesktopTheme.use(true)
        val scene = ImageComposeScene(240, 140, Density(1f)) {
            DesktopTheme.Apply {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Text(
                        text = "#### #### ####\n#### #### ####",
                        color = Color.Black,
                        modifier = Modifier.padding(8.dp),
                    )
                    Box(Modifier.fillMaxSize().glassSurface())
                }
            }
        }
        try {
            Snapshot.sendApplyNotifications()
            val image = ImageIO.read(scene.render().encodeToData()!!.bytes.inputStream())
            var darkest = 0xFF
            // Inside the panel, away from its own drawn edge: only the fill is left to answer.
            for (x in 24 until image.width - 24) {
                for (y in 24 until image.height - 24) {
                    val channel = image.getRGB(x, y) and 0xFF
                    if (channel < darkest) darkest = channel
                }
            }
            assertTrue(
                "the panel hides the ink behind it (darkest 0x%02X)".format(darkest),
                darkest >= 0xF0,
            )
        } finally {
            scene.close()
            DesktopTheme.use(false)
        }
    }
}

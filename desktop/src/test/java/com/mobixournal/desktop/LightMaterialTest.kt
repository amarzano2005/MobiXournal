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
 * The light theme's material rule (`docs/interface-contract.md` §1).
 *
 * A flat workstation separates its surfaces by tone: the band is the light tinted tone, a surface is the
 * sheet's white, a control is the white again and a field steps back to the band's tone. The pointer and
 * the picked slot are two steps down that ramp, and no surface casts a shadow or shades its canvas edge.
 * This test holds those answers, and the dark ones, which is the part a future light-theme tweak is most
 * likely to disturb.
 */
class LightMaterialTest {

    @Test
    fun `on paper the band is the sheet's white, edged on the canvas side`() {
        DesktopTheme.use(true)
        try {
            val band = luminance(DesktopTheme.glass)
            assertEquals(
                "the band is the flat light tone",
                DesktopTheme.tokenColour(DesignTokens.DESKTOP_BAND_LIGHT_RGB),
                DesktopTheme.glass,
            )
            assertEquals(
                "and the system caption is that tone too",
                DesignTokens.DESKTOP_BAND_LIGHT_RGB,
                DesktopTheme.bandRgb,
            )
            assertEquals(
                "a surface is the sheet's white",
                DesktopTheme.tokenColour(DesignTokens.GLASS_LIGHT_RGB),
                DesktopTheme.panel,
            )
            val hovered = onPaper(DesktopTheme.hoverSlot)
            val picked = onPaper(DesktopTheme.activeSlot)
            assertTrue("the pointer is a shade on it ($hovered)", hovered < band - 0.01f)
            assertTrue("and the picked slot deeper still ($picked)", picked < hovered)
            assertEquals(
                "a flat band carries no canvas edge shade",
                0f,
                DesktopTheme.bandEdgeShadow.alpha,
                0f,
            )
            assertTrue(
                "the desk is greyer than the band it frames (${luminance(DesktopTheme.desk)} vs $band)",
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
            // A floating surface is the surface tone and it is **opaque**: a flat popup covers what is
            // behind it, which is the desktop's own recipe (`docs/interface-contract.md` §1).
            assertEquals("a floating surface is opaque", 1f, DesktopTheme.floating.alpha, 0.005f)
            assertEquals("and it is the panel's own white", DesktopTheme.panel, DesktopTheme.floating)
            assertEquals("a flat band casts no shadow", 0.dp, DesktopTheme.bandShadow)
        } finally {
            DesktopTheme.use(false)
        }
    }

    @Test
    fun `on black nothing of the light recipe is left behind`() {
        DesktopTheme.use(false)
        try {
            assertEquals(
                "a control is one tone above the band",
                DesktopTheme.tokenColour(DesignTokens.DESKTOP_CONTROL_DARK_RGB),
                DesktopTheme.control,
            )
            assertEquals("and a field wears that control tone", DesktopTheme.control, DesktopTheme.panelField)
            assertEquals(DesignTokens.RIM_ALPHA_TOP, DesktopTheme.rimAlphaTop, 1e-4f)
            assertEquals(DesignTokens.RIM_ALPHA_BOTTOM, DesktopTheme.rimAlphaBottom, 1e-4f)
            assertEquals(DesktopTheme.activeSlot.copy(alpha = DesktopTheme.CARD_ALPHA), DesktopTheme.card)
            assertEquals("a floating surface is opaque on black too", 1f, DesktopTheme.floating.alpha, 0.005f)
            assertEquals("and a flat band casts nothing", 0.dp, DesktopTheme.bandShadow)
            assertEquals("and no canvas edge shade on black", Color.Transparent, DesktopTheme.bandEdgeShadow)
        } finally {
            DesktopTheme.use(false)
        }
    }

    /** Desktop popup bodies must completely cover the writing beneath them. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `on paper a desktop popup completely covers the underlying ink`() {
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
            assertEquals("no underlying text leaks through the popup", 0xFF, darkest)
        } finally {
            scene.close()
            DesktopTheme.use(false)
        }
    }

    /** Perceived lightness, enough to say "this one is the lighter surface". */
    private fun luminance(colour: Color): Float =
        0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue

    /**
     * [colour] as it lands **on the light band**, which is white: an alpha fill is not a tone until
     * something is behind it, and [luminance] alone read resting and hover as the same colour.
     */
    private fun onPaper(colour: Color): Float = luminance(
        Color(
            red = colour.red * colour.alpha + (1f - colour.alpha),
            green = colour.green * colour.alpha + (1f - colour.alpha),
            blue = colour.blue * colour.alpha + (1f - colour.alpha),
        ),
    )
}

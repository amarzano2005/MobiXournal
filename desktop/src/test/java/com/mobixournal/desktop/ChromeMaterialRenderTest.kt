package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The raised glass, **as pixels** — the half of "the PC looks like the tablet" that a token test cannot
 * see.
 *
 * `DesktopThemeTest` proves the numbers are the documented ones; this file proves the window *uses*
 * them. Over the true-black desk the glass' alpha is invisible, so the only depth cues are the lift
 * under a band and a capsule, the lit edge along their top, and the rim. Each one has a pixel
 * consequence, and each consequence is asserted here:
 *
 *  1. a band darkens what lies under it — a cast shadow, not a flat rectangle;
 *  2. a capsule is brighter along its top edge than in its fill — the drawn rim;
 *  3. and both keep the shared fill: no gradient of the window's own, which is what made the PC's
 *     bands a shade lighter than the tablet's.
 *
 * The renders are written to `build/desktop-render/` so the claim can be looked at as well as checked.
 */
class ChromeMaterialRenderTest {

    private val width = 220
    private val height = 160

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        under: Color = Color.White,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): BufferedImage {
        val png = ImageComposeScene(width, height, Density(1f)) {
            Box(Modifier.fillMaxSize().background(under)) {
                Canvas(Modifier.fillMaxSize()) { drawRect(under) }
                content()
            }
        }.use { scene ->
            val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(bytes)
            bytes
        }
        return ImageIO.read(ByteArrayInputStream(png))
    }

    /**
     * A band casts a shadow.
     *
     * The band is drawn over **white** on purpose: against the desk the shadow is black on black, which
     * is exactly why the first, flat version of the window looked like it had no lift at all.
     */
    @Test
    fun aBandCastsAShadowUnderItself() {
        val image = render("chrome-band-shadow") {
            Box(
                Modifier
                    .width(220.dp)
                    .height(40.dp)
                    .bandSurface(),
            )
        }
        // The shadow's darkest ring sits in the first few pixels under the band and falls away fast; a
        // 4 dp lift over white is a soft grey, not a black line, so the band's own bottom row is skipped
        // (it carries the fill) and the tail is read instead.
        val darkest = (41..48).minOf { y -> luminance(image.getRGB(width / 2, y)) }
        val farBelow = luminance(image.getRGB(width / 2, height - 4))
        assertTrue(
            "a band must darken the surface under it: $darkest vs $farBelow",
            darkest < farBelow - 10,
        )
    }

    /**
     * A band's face is the **shared glass fill**, flat: the tablet's bands have no gradient, and a sheen
     * here made the same material a shade lighter on the PC.
     */
    @Test
    fun aBandIsTheFlatSharedFill() {
        val image = render("chrome-band-flat") {
            Box(
                Modifier
                    .width(220.dp)
                    .height(40.dp)
                    .bandSurface(),
            )
        }
        // Inside the band, away from the two rows the graphics layer's own shadow tints: the face is one
        // tone, which is what tells a shared fill from a gradient of the window's own.
        val upper = luminance(image.getRGB(width / 2, 10))
        val lower = luminance(image.getRGB(width / 2, 34))
        assertTrue("a band must not carry a gradient of its own: upper $upper, lower $lower", kotlin.math.abs(upper - lower) <= 2)
        // The glass at 0.85 over the white underlay: 22·0.85 + 255·0.15 ≈ 57 on every channel.
        val token = DesignTokens.GLASS_DARK_RGB
        val expected = (((token shr 16) and 0xFF) * DesignTokens.GLASS_DARK_ALPHA +
            255f * (1f - DesignTokens.GLASS_DARK_ALPHA))
        assertTrue(
            "the fill must be the glass token over the underlay, not a lighter charcoal: $upper vs $expected",
            kotlin.math.abs(upper - expected) <= 4f,
        )
    }

    /** A capsule wears the family's edge: brighter at its top than its own fill. */
    @Test
    fun aCapsuleWearsALitRim() {
        val image = render("chrome-capsule-rim") {
            Box(
                Modifier
                    .offset(60.dp, 40.dp)
                    .width(100.dp)
                    .height(80.dp)
                    .glassCapsule(),
            )
        }
        // The rim is one hairline on the capsule's top edge, so take the brightest of its first rows;
        // sampling two pixels in lands on the fill, which is what the first version of this test did.
        val rim = (40..44).maxOf { y -> luminance(image.getRGB(width / 2, y)) }
        val fill = luminance(image.getRGB(width / 2, 80))
        assertTrue("the rim is the lit edge of the capsule: rim $rim, fill $fill", rim > fill + 8)
    }

    /** The capsule casts one too — a smaller one than a band, but visible over a light backdrop. */
    @Test
    fun aCapsuleCastsItsOwnShadow() {
        val image = render("chrome-capsule-shadow") {
            Box(
                Modifier
                    .offset(60.dp, 40.dp)
                    .width(100.dp)
                    .height(80.dp)
                    .glassCapsule(),
            )
        }
        val darkest = (121..130).minOf { y -> luminance(image.getRGB(width / 2, y)) }
        val farBelow = luminance(image.getRGB(width / 2, height - 3))
        assertTrue(
            "a capsule must lift off the surface: $darkest vs $farBelow",
            darkest < farBelow - 10,
        )
    }

    /** The desk is untouched outside the surfaces: no shadow leaks across the whole window. */
    @Test
    fun theDeskStaysBlackAroundTheChrome() {
        val image = render("chrome-desk-clean", under = DesktopTheme.desk) {
            Box(Modifier.width(220.dp).height(40.dp).bandSurface())
        }
        val desk = DesktopTheme.desk.toArgb() and 0xFFFFFF
        val corner = image.getRGB(width - 2, height - 2) and 0xFFFFFF
        assertTrue(
            "the desk at the far corner must still be the desk: ${Integer.toHexString(corner)}",
            difference(corner, desk) <= 4,
        )
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun difference(a: Int, b: Int): Int {
        fun channel(rgb: Int, shift: Int) = (rgb shr shift) and 0xFF
        return maxOf(
            kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)),
        )
    }

    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    /** The lift is the token's, so a test can say what "raised" means. */
    @Test
    fun theLiftIsTheDocumentedOne() {
        assertTrue(DesignTokens.BAND_SHADOW_DP in 1..8)
        assertTrue(DesignTokens.CAPSULE_SHADOW_DP in DesignTokens.BAND_SHADOW_DP..10)
    }
}

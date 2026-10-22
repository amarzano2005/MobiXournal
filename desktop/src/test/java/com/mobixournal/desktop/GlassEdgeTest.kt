package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

/**
 * The floating glass's edge, as pixels.
 *
 * A flat border and a shaded rim differ by one property a test can see: the border is the same tone all
 * the way round, the rim is brighter at its top than at its bottom. That is the assertion this file is
 * built on — plus the two facts that keep it from passing for the wrong reason: the corner still cuts
 * (the panel's fill is not there), and the fill is still the floating alpha rather than an opaque slab.
 */
class GlassEdgeTest {

    private val panelWidth = 160
    private val panelHeight = 100
    private val inset = 20

    @OptIn(ExperimentalComposeUiApi::class)
    private fun panel(
        fill: Color = DesktopTheme.floating,
        under: Color = DesktopTheme.desk,
        name: String = "glass-edge",
        underContent: DrawScope.() -> Unit = {},
    ): BufferedImage {
        val png = ImageComposeScene(panelWidth + inset * 2, panelHeight + inset * 2, Density(1f)) {
            Box(Modifier.fillMaxSize().background(under)) {
                Canvas(Modifier.fillMaxSize()) { underContent() }
                Box(
                    Modifier
                        .offset(inset.dp, inset.dp)
                        .size(panelWidth.dp, panelHeight.dp)
                        .glassSurface(fill),
                )
            }
        }.use { scene ->
            val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(bytes)
            bytes
        }
        return ImageIO.read(ByteArrayInputStream(png))
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    @Test
    fun theRimIsBrighterAtTheTopThanAtTheBottom() {
        val image = panel()
        val x = inset + panelWidth / 2
        // One pixel in from the edge: the rim is drawn a hairline inside it.
        val top = luminance(image.getRGB(x, inset + 1))
        val bottom = luminance(image.getRGB(x, inset + panelHeight - 2))

        assertTrue(
            "the rim is lit from above: top $top must clear bottom $bottom",
            top > bottom + 4,
        )
    }

    @Test
    fun theEdgeFeathersIntoTheFillInsteadOfStoppingAtIt() {
        val image = panel()
        val x = inset + panelWidth / 2
        val rim = luminance(image.getRGB(x, inset + 1))
        val fill = luminance(image.getRGB(x, inset + panelHeight / 2))

        assertTrue("the rim is brighter than the panel's own fill", rim > fill)
        assertTrue(
            "and the feather sits between the two, not on the fill's own tone",
            luminance(image.getRGB(x, inset + 3)) in fill..rim,
        )
    }

    @Test
    fun theFillIsStillTheFloatingAlphaAndNotASlab() {
        val image = panel()
        val centre = image.getRGB(inset + panelWidth / 2, inset + panelHeight / 2) and 0xFFFFFF
        // The glass over the true-black desk: the token's RGB at the floating alpha, nothing more.
        val expected = composite(DesignTokens.GLASS_DARK_RGB, DesignTokens.FLOATING_PANEL_ALPHA, 0x000000)
        assertTrue(
            "centre ${Integer.toHexString(centre)} should sit at ${Integer.toHexString(expected)}",
            difference(centre, expected) <= 6,
        )
    }

    @Test
    fun theCornerIsCutAtThePanelsRadius() {
        val image = panel()
        val corner = image.getRGB(inset, inset) and 0xFFFFFF
        val desk = DesktopTheme.desk.toArgb() and 0xFFFFFF
        assertTrue(
            "a ${DesignTokens.PANEL_CORNER_RADIUS_DP} dp corner leaves the desk showing at the corner",
            difference(corner, desk) <= 4,
        )
    }

    /**
     * The one property a solid card cannot fake: what lies **under** the panel changes what it looks like.
     *
     * This is the check that would have caught the window's panels being painted with the opaque `panel`
     * colour — over the black desk the two fills are the same picture, and it was only over the sheet that
     * the glass was missing.
     */
    @Test
    fun thePanelIsTranslucentOverThePage() {
        val white = Color.White
        val overPage = panel(under = white, name = "glass-over-page")
            .getRGB(inset + panelWidth / 2, inset + panelHeight / 2) and 0xFFFFFF
        val overDesk = panel().getRGB(inset + panelWidth / 2, inset + panelHeight / 2) and 0xFFFFFF

        val expected = composite(
            DesignTokens.GLASS_DARK_RGB,
            DesignTokens.FLOATING_PANEL_ALPHA,
            white.toArgb() and 0xFFFFFF,
        )
        assertTrue(
            "over the page the fill is the glass over white (${Integer.toHexString(expected)}), " +
                "not a solid card: got ${Integer.toHexString(overPage)}",
            difference(overPage, expected) <= 6,
        )
        assertTrue(
            "and it is visibly lighter than the same panel over the desk ($overDesk)",
            (overPage and 0xFF) > (overDesk and 0xFF) + 20,
        )
    }

    /**
     * What the eye is owed when it asks for glass: the page **reads through** the panel.
     *
     * The ruling, the ink and the margin of the sheet must be visible under the panel, dimmed by the one
     * alpha the ink allows, rather than covered by a card. A solid fill passes every other test in this
     * file over the black desk (the two fills are the same picture there), so this is the one that has to
     * look: it draws a band of black ink beneath the panel and reads the panel's own pixels above and
     * below it.
     */
    @Test
    fun thePageReadsThroughThePanel() {
        val bandTop = inset + panelHeight / 2 - 6
        val bandBottom = inset + panelHeight / 2 + 6
        val image = panel(name = "glass-show-through", under = Color.White) {
            drawRect(Color.White)
            drawRect(
                color = Color(0xFF1A1A1A),
                topLeft = Offset(0f, bandTop.toFloat()),
                size = Size(size.width, (bandBottom - bandTop).toFloat()),
            )
        }
        val x = inset + panelWidth / 2
        val onInk = image.getRGB(x, inset + panelHeight / 2) and 0xFF
        val onPaper = image.getRGB(x, inset + 20) and 0xFF

        assertTrue(
            "the ink under the panel must still tell it is there: on ink $onInk, on paper $onPaper",
            onPaper > onInk + 15,
        )
    }

    /** The desk itself shows nothing of the panel: the geometry is the panel, not the scene. */
    @Test
    fun theDeskAroundThePanelIsUntouched() {
        val image = panel()
        val desk = DesktopTheme.desk.toArgb() and 0xFFFFFF
        assertEquals(desk, image.getRGB(2, 2) and 0xFFFFFF)
    }

    private fun composite(rgb: Int, alpha: Float, over: Int): Int {
        fun channel(shift: Int): Int {
            val a = ((rgb shr shift) and 0xFF) * alpha
            val b = ((over shr shift) and 0xFF) * (1f - alpha)
            return (a + b).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun difference(a: Int, b: Int): Int {
        fun channel(rgb: Int, shift: Int) = (rgb shr shift) and 0xFF
        return maxOf(
            kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)),
        )
    }

    /** `use` on a scene, so a failing assertion still tears the window down. */
    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}

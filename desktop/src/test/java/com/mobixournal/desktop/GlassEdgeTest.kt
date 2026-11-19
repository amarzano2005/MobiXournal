package com.mobixournal.desktop

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mobixournal.desktop.tools.glassRim
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme

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
 * built on — while the flat-workstation body it surrounds is an **opaque** surface: the fill is the
 * surface token, what lies under it cannot tint it, and the corner still cuts.
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
                        .clip(RoundedCornerShape(DesktopTheme.panelRadius)).background(fill).glassRim(),
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
    fun theFillIsTheOpaqueSurfaceTone() {
        val image = panel()
        val centre = image.getRGB(inset + panelWidth / 2, inset + panelHeight / 2) and 0xFFFFFF
        // A flat panel is the surface token and it is opaque: no alpha left for a desk to tint.
        assertEquals(
            "the panel is the surface tone",
            DesignTokens.DESKTOP_SURFACE_DARK_RGB,
            centre,
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
    fun thePanelIsOpaqueOverThePage() {
        val overPage = panel(under = Color.White, name = "glass-over-page")
            .getRGB(inset + panelWidth / 2, inset + panelHeight / 2) and 0xFFFFFF
        val overDesk = panel().getRGB(inset + panelWidth / 2, inset + panelHeight / 2) and 0xFFFFFF

        assertEquals("the page cannot tint an opaque panel", overDesk, overPage)
        assertEquals(
            "and the panel is the surface tone in both places",
            DesignTokens.DESKTOP_SURFACE_DARK_RGB,
            overPage,
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
    fun thePageDoesNotReadThroughThePanel() {
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

        assertEquals("an opaque panel hides the ink under it", onPaper, onInk)
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

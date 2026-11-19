package com.mobixournal.desktop

import com.mobixournal.desktop.tools.glassRim
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

/** The shared capsule rim still matches the tablet art; desktop popup bodies have a separate recipe. */
class GlassParityRenderTest {

    private val panel = 180
    private val height = 110
    private val gap = 48
    private val margin = 32

    /** The tablet's recipe: the tokens applied straight, with no desktop modifier involved. */
    private fun Modifier.tabletMaterial(): Modifier {
        val fill = DesktopTheme.tokenColour(DesignTokens.GLASS_DARK_RGB, DesignTokens.FLOATING_PANEL_ALPHA)
        return clip(RoundedCornerShape(DesignTokens.PANEL_CORNER_RADIUS_DP.dp))
            .background(fill)
            .drawWithContent {
                drawContent()
                val width = DesignTokens.RIM_WIDTH_DP.dp.toPx()
                val ink = DesktopTheme.ink
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            ink.copy(alpha = DesignTokens.RIM_ALPHA_TOP),
                            ink.copy(alpha = DesignTokens.RIM_ALPHA_BOTTOM),
                        ),
                        0f,
                        size.height,
                    ),
                    topLeft = Offset(width / 2f, width / 2f),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(DesignTokens.PANEL_CORNER_RADIUS_DP.dp.toPx()),
                    style = Stroke(width),
                )
                val innerWidth = width * DesignTokens.RIM_INNER_WIDTH_SCALE
                val inset = width + innerWidth / 2f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            ink.copy(alpha = DesignTokens.RIM_ALPHA_TOP * DesignTokens.RIM_INNER_ALPHA_SCALE),
                            ink.copy(alpha = DesignTokens.RIM_ALPHA_BOTTOM * DesignTokens.RIM_INNER_ALPHA_SCALE),
                        ),
                        0f,
                        size.height,
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(DesignTokens.PANEL_CORNER_RADIUS_DP.dp.toPx() - width),
                    style = Stroke(innerWidth),
                )
            }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(): BufferedImage {
        val width = margin * 2 + panel * 2 + gap
        val sceneHeight = margin * 2 + height + 24
        val png = ImageComposeScene(width, sceneHeight, Density(1f)) {
            Row(
                Modifier.fillMaxSize().background(DesktopTheme.desk).padding(margin.dp),
                horizontalArrangement = Arrangement.spacedBy(gap.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(panel.dp, height.dp).tabletMaterial())
                    Text("Tablet material", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(panel.dp, height.dp).clip(RoundedCornerShape(DesktopTheme.panelRadius)).background(DesktopTheme.floating).glassRim())
                    Text("Desktop material", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                }
            }
        }.let { scene ->
            try {
                requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            } finally {
                scene.close()
            }
        }
        File("build/desktop-render/glass-parity.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        return ImageIO.read(ByteArrayInputStream(png))
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun compositeOf(rgb: Int, alpha: Float, under: Int): Int {
        fun channel(shift: Int) = (((rgb shr shift) and 0xFF) * alpha + ((under shr shift) and 0xFF) * (1f - alpha))
            .toInt().coerceIn(0, 255)
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

    @Test
    fun theTwoShellsWearTheirOwnMaterials() {
        val image = render()
        // The panels begin after the margin; each one is `panel` wide with `gap` between them.
        val leftX = margin + panel / 2
        val rightX = margin + panel + gap + panel / 2
        val topY = margin + 1
        val bottomY = margin + height - 2
        val middleY = margin + height / 2

        val tablet = image.getRGB(leftX, middleY) and 0xFFFFFF
        val desktop = image.getRGB(rightX, middleY) and 0xFFFFFF
        val tabletFill = compositeOf(DesignTokens.GLASS_DARK_RGB, DesignTokens.FLOATING_PANEL_ALPHA, DesignTokens.DESK_RGB)
        assertEquals(
            "the tablet's fill is its glass over the desk: ${Integer.toHexString(tablet)}",
            tabletFill,
            tablet,
        )
        assertEquals(
            "the window's fill is the flat surface tone: ${Integer.toHexString(desktop)}",
            DesignTokens.DESKTOP_SURFACE_DARK_RGB,
            desktop,
        )
        assertTrue(
            "the two shells wear two materials, by decision",
            difference(tablet, desktop) > 4,
        )
        // The rim is drawn from the same ink at the same alphas over each body, so what matches is the
        // **step** it takes from that body — an absolute tone would differ with the fill, and the fills
        // are deliberately two materials now.
        val tabletTop = image.getRGB(leftX, topY) and 0xFFFFFF
        val desktopTop = image.getRGB(rightX, topY) and 0xFFFFFF
        val tabletBottom = image.getRGB(leftX, bottomY) and 0xFFFFFF
        val desktopBottom = image.getRGB(rightX, bottomY) and 0xFFFFFF
        assertTrue(
            "the rim's top edge takes one step from its body: " +
                "${difference(tabletTop, tablet)} against ${difference(desktopTop, desktop)}",
            kotlin.math.abs(difference(tabletTop, tablet) - difference(desktopTop, desktop)) <= 2,
        )
        assertTrue(
            "and so does its bottom edge: " +
                "${difference(tabletBottom, tablet)} against ${difference(desktopBottom, desktop)}",
            kotlin.math.abs(difference(tabletBottom, tablet) - difference(desktopBottom, desktop)) <= 2,
        )
    }

    @Test
    fun bothPanelsAreLitFromAbove() {
        val image = render()
        val topY = margin + 1
        val bottomY = margin + height - 2
        for (left in listOf(true, false)) {
            val x = if (left) margin + panel / 2 else margin + panel + gap + panel / 2
            val top = luminance(image.getRGB(x, topY))
            val bottom = luminance(image.getRGB(x, bottomY))
            assertTrue(
                "the ${if (left) "tablet's" else "desktop's"} rim is brighter at the top ($top) than at the bottom ($bottom)",
                top > bottom + 4,
            )
        }
    }

    @Test
    fun theRenderIsKeptAsTheArtefactToLookAt() {
        render()
        val file = File("build/desktop-render/glass-parity.png")
        assertTrue("the parity render must exist to be looked at", file.isFile)
        // Two panels and their labels fit inside it, and the scene is not a blank frame.
        val image = ImageIO.read(file)
        assertTrue(
            "the image holds both panels: ${image.width} x ${image.height}",
            image.width >= margin * 2 + panel * 2 + gap && image.height >= margin * 2 + height,
        )
        val middle = image.getRGB(margin + panel / 2, margin + height / 2)
        assertTrue("the left panel was drawn", difference(middle, 0) > 0)
    }
}
